package ar.param.advisor.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

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
 * Convierte voz hablada en susurro: conserva la envolvente espectral de cada fragmento
 * corto (lo que hace entendibles las palabras) y reemplaza la fase por ruido, así
 * desaparece la vibración de las cuerdas vocales. También quita graves y realza un poco
 * los agudos, como en un susurro real.
 */
internal object Whisperizer {
    private const val LOW_CUT_HZ = 280.0
    private const val TILT_TOP_HZ = 3_500.0

    fun process(input: ShortArray, sampleRate: Int, seed: Long = 7L, peak: Double = 0.55): ShortArray {
        if (input.isEmpty() || sampleRate <= 0) return input.copyOf()
        val n = frameSizeFor(sampleRate)
        val hop = n / 8
        val half = n / 2
        val window = DoubleArray(n) { 0.5 - 0.5 * cos(2 * PI * it / n) }
        val total = input.size + 2 * n
        val acc = DoubleArray(total)
        val norm = DoubleArray(total)
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        val random = Random(seed)
        val lowCutBin = ceil(LOW_CUT_HZ * n / sampleRate).toInt()
        val gains = DoubleArray(half + 1) { k ->
            if (k < lowCutBin) 0.0 else 0.75 + 0.5 * min(1.0, k.toDouble() * sampleRate / n / TILT_TOP_HZ)
        }

        var start = 0
        while (start + n <= total) {
            for (i in 0 until n) {
                val src = start + i - n
                re[i] = (if (src in input.indices) input[src].toDouble() else 0.0) * window[i]
                im[i] = 0.0
            }
            fft(re, im, inverse = false)
            for (k in 0..half) {
                val mag = hypot(re[k], im[k]) * gains[k]
                if (k == 0 || k == half) {
                    re[k] = mag
                    im[k] = 0.0
                } else {
                    val phase = random.nextDouble() * 2 * PI
                    re[k] = mag * cos(phase)
                    im[k] = mag * sin(phase)
                    re[n - k] = re[k]
                    im[n - k] = -im[k]
                }
            }
            fft(re, im, inverse = true)
            for (i in 0 until n) {
                acc[start + i] += re[i] * window[i]
                norm[start + i] += window[i] * window[i]
            }
            start += hop
        }

        val out = DoubleArray(input.size) { i ->
            val j = i + n
            if (norm[j] > 1e-6) acc[j] / norm[j] else 0.0
        }
        val max = out.maxOf { abs(it) }
        if (max < 1e-6) return ShortArray(input.size)
        val gain = peak * Short.MAX_VALUE / max
        return ShortArray(input.size) {
            (out[it] * gain).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /** Ventanas de ~12 ms: lo bastante cortas para no separar los armónicos de la voz. */
    internal fun frameSizeFor(sampleRate: Int): Int =
        Integer.highestOneBit((sampleRate * 0.012).toInt()).coerceIn(64, 1024)

    private fun fft(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val angle = 2 * PI / len * if (inverse) 1 else -1
            val wRe = cos(angle)
            val wIm = sin(angle)
            var i = 0
            while (i < n) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until len / 2) {
                    val a = i + k
                    val b = a + len / 2
                    val tRe = re[b] * curRe - im[b] * curIm
                    val tIm = re[b] * curIm + im[b] * curRe
                    re[b] = re[a] - tRe
                    im[b] = im[a] - tIm
                    re[a] += tRe
                    im[a] += tIm
                    val next = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = next
                }
                i += len
            }
            len = len shl 1
        }
        if (inverse) {
            for (i in 0 until n) {
                re[i] /= n
                im[i] /= n
            }
        }
    }
}
