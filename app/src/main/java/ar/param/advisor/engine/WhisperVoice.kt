package ar.param.advisor.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal class PcmClip(val samples: ShortArray, val sampleRate: Int)

/** Lee el WAV PCM de 16 bits que genera `TextToSpeech.synthesizeToFile`. */
internal object WavPcm {
    fun parse(bytes: ByteArray): PcmClip? {
        if (bytes.size < 12) return null
        if (ascii(bytes, 0) != "RIFF" || ascii(bytes, 8) != "WAVE") return null
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var format = 0
        var channels = 0
        var rate = 0
        var bits = 0
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = ascii(bytes, pos)
            val size = bb.getInt(pos + 4)
            val body = pos + 8
            when (id) {
                "fmt " -> {
                    if (size < 16 || body + 16 > bytes.size) return null
                    format = bb.getShort(body).toInt() and 0xFFFF
                    channels = bb.getShort(body + 2).toInt()
                    rate = bb.getInt(body + 4)
                    bits = bb.getShort(body + 14).toInt()
                }
                "data" -> {
                    if (format != 1 || bits != 16 || channels !in 1..2 || rate <= 0) return null
                    val available = bytes.size - body
                    // Algunos motores dejan el tamaño en 0 o mal calculado: se usa lo que hay.
                    val length = if (size in 1..available) size else available
                    val frames = length / (2 * channels)
                    val out = ShortArray(frames) { f ->
                        if (channels == 1) {
                            bb.getShort(body + f * 2)
                        } else {
                            ((bb.getShort(body + f * 4) + bb.getShort(body + f * 4 + 2)) / 2).toShort()
                        }
                    }
                    return PcmClip(out, rate)
                }
            }
            if (size < 0) return null
            pos = body + size + (size and 1)
        }
        return null
    }

    private fun ascii(bytes: ByteArray, at: Int) = String(bytes, at, 4, Charsets.US_ASCII)
}

/**
 * Convierte voz hablada en susurro con LPC: en cada fragmento de 25 ms se estima la forma
 * del tracto vocal (lo que hace entendibles las palabras) y se la excita con soplido (ruido)
 * en lugar de la vibración de las cuerdas vocales. Así suena como un susurro real, sin
 * el sonido granulado de mezclar fases al azar.
 */
internal object Whisperizer {
    private const val FRAME_SECONDS = 0.025
    private const val PRE_EMPHASIS = 0.94
    private const val DE_EMPHASIS = 0.9
    private const val BANDWIDTH_EXPANSION = 0.985
    private const val GATE_DB = -55.0
    private const val HIGH_PASS_HZ = 250.0

    fun process(input: ShortArray, sampleRate: Int, seed: Long = 7L, peak: Double = 0.5): ShortArray {
        if (input.isEmpty() || sampleRate <= 0) return input.copyOf()
        val n = frameSizeFor(sampleRate)
        val hop = n / 2
        val order = lpcOrderFor(sampleRate)
        // Ventana de Hann periódica: con salto del 50 % suma exactamente 1.
        val window = DoubleArray(n) { 0.5 - 0.5 * cos(2 * PI * (it + 0.5) / n) }
        val windowEnergy = window.sumOf { it * it }

        val padded = DoubleArray(input.size + 2 * n)
        var previous = input[0].toDouble()
        for (i in input.indices) {
            val v = input[i].toDouble()
            padded[i + n] = if (i == 0) v else v - PRE_EMPHASIS * previous
            previous = v
        }

        val starts = (0..padded.size - n step hop).toList()
        val energies = DoubleArray(starts.size) { f ->
            var e = 0.0
            for (i in 0 until n) {
                val v = padded[starts[f] + i] * window[i]
                e += v * v
            }
            e
        }
        val gate = (energies.maxOrNull() ?: 0.0) * 10.0.pow(GATE_DB / 10)
        val random = Random(seed)
        val out = DoubleArray(padded.size)
        val frame = DoubleArray(n)
        val noise = DoubleArray(n)
        val shaped = DoubleArray(n)

        for ((f, start) in starts.withIndex()) {
            if (energies[f] <= gate || energies[f] <= 0.0) continue
            for (i in 0 until n) frame[i] = padded[start + i] * window[i]
            val (a, err) = lpc(frame, order) ?: continue
            var g = 1.0
            for (k in 0..order) {
                a[k] *= g
                g *= BANDWIDTH_EXPANSION
            }
            val sigma = sqrt(max(err, 0.0) / windowEnergy)
            for (i in 0 until n) noise[i] = random.nextGaussian() * sigma
            allPole(a, noise, shaped)
            for (i in 0 until n) out[start + i] += shaped[i] * window[i]
        }

        // Deshace casi todo el realce de agudos (si no, suena a siseo áspero) y quita graves.
        val c = exp(-2 * PI * HIGH_PASS_HZ / sampleRate)
        val result = DoubleArray(input.size)
        var de = 0.0
        var hpPrevIn = 0.0
        var hpPrevOut = 0.0
        for (i in input.indices) {
            de = out[i + n] + DE_EMPHASIS * de
            hpPrevOut = c * (hpPrevOut + de - hpPrevIn)
            hpPrevIn = de
            result[i] = hpPrevOut
        }

        val maxAbs = result.maxOf { abs(it) }
        if (maxAbs < 1e-6) return ShortArray(input.size)
        val gain = peak * Short.MAX_VALUE / maxAbs
        return ShortArray(input.size) {
            (result[it] * gain).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    internal fun frameSizeFor(sampleRate: Int): Int = ((sampleRate * FRAME_SECONDS).toInt() and 1.inv()).coerceAtLeast(64)

    internal fun lpcOrderFor(sampleRate: Int): Int = min(32, sampleRate / 1000 + 4)

    /** Levinson-Durbin sobre la autocorrelación. Devuelve coeficientes (a[0] = 1) y error residual. */
    private fun lpc(frame: DoubleArray, order: Int): Pair<DoubleArray, Double>? {
        val r = DoubleArray(order + 1) { k ->
            var s = 0.0
            for (i in 0 until frame.size - k) s += frame[i] * frame[i + k]
            s
        }
        if (r[0] <= 1e-9) return null
        r[0] *= 1.0001
        val a = DoubleArray(order + 1).also { it[0] = 1.0 }
        val tmp = DoubleArray(order + 1)
        var err = r[0]
        for (i in 1..order) {
            var acc = r[i]
            for (j in 1 until i) acc += a[j] * r[i - j]
            val k = -acc / err
            a.copyInto(tmp)
            for (j in 1 until i) a[j] = tmp[j] + k * tmp[i - j]
            a[i] = k
            err *= 1 - k * k
            if (err <= 0.0) return null
        }
        return a to err
    }

    private fun allPole(a: DoubleArray, x: DoubleArray, y: DoubleArray) {
        val order = a.size - 1
        for (i in x.indices) {
            var s = x[i]
            for (k in 1..min(order, i)) s -= a[k] * y[i - k]
            y[i] = s
        }
    }
}
