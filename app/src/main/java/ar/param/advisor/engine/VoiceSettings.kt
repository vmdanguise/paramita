package ar.param.advisor.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Ajustes de la voz que lee las prácticas. Se guardan en el teléfono (ManiSettings). */
data class VoiceSettings(
    /** Nombre de la voz del motor TTS, o null para la voz en español por defecto. */
    val voiceName: String? = null,
    val pitch: Float = 1f,
    val volume: Float = 1f,
    val bassDb: Float = 0f,
    val midDb: Float = 0f,
    val trebleDb: Float = 0f,
) {
    val isFlatEq: Boolean get() = abs(bassDb) < 0.25f && abs(midDb) < 0.25f && abs(trebleDb) < 0.25f

    /** Clave para la caché de clips: cambia si cambia algo que altera el sonido. */
    val cacheKey: String get() = "$voiceName|$pitch|$bassDb|$midDb|$trebleDb"

    fun normalized() = copy(
        pitch = pitch.coerceIn(MIN_PITCH, MAX_PITCH),
        volume = volume.coerceIn(MIN_VOLUME, 1f),
        bassDb = bassDb.coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB),
        midDb = midDb.coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB),
        trebleDb = trebleDb.coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB),
    )

    companion object {
        const val MIN_PITCH = 0.6f
        const val MAX_PITCH = 1.5f
        const val MIN_VOLUME = 0.2f
        const val MAX_GAIN_DB = 12f
    }
}

/** Ecualizaciones listas para usar. */
enum class VoicePreset(val bassDb: Float, val midDb: Float, val trebleDb: Float) {
    NATURAL(0f, 0f, 0f),
    WARM(5f, 1f, -4f),
    CLEAR(-3f, 2f, 5f),
    SOFT(1f, -3f, -6f),
    DEEP(8f, -1f, -2f),
}

/**
 * Ecualizador de tres bandas (filtros biquad RBJ): graves en estante bajo, medios en campana
 * y agudos en estante alto. Al final baja el volumen si algo quedaría saturado.
 */
internal object VoiceEqualizer {
    const val BASS_HZ = 220.0
    const val MID_HZ = 1_500.0
    const val TREBLE_HZ = 4_500.0

    fun process(input: ShortArray, sampleRate: Int, bassDb: Float, midDb: Float, trebleDb: Float): ShortArray {
        if (input.isEmpty() || sampleRate <= 0) return input.copyOf()
        val x = DoubleArray(input.size) { input[it].toDouble() }
        val nyquist = sampleRate / 2.0
        if (abs(bassDb) >= 0.25f) Biquad.lowShelf(BASS_HZ, bassDb.toDouble(), sampleRate).run(x)
        if (abs(midDb) >= 0.25f && MID_HZ < nyquist) Biquad.peaking(MID_HZ, 0.9, midDb.toDouble(), sampleRate).run(x)
        if (abs(trebleDb) >= 0.25f && TREBLE_HZ < nyquist) Biquad.highShelf(TREBLE_HZ, trebleDb.toDouble(), sampleRate).run(x)
        var peak = 0.0
        for (v in x) peak = max(peak, abs(v))
        val limit = Short.MAX_VALUE * 0.97
        val scale = if (peak > limit) limit / peak else 1.0
        return ShortArray(x.size) { (x[it] * scale).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort() }
    }

    private class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
        fun run(x: DoubleArray) {
            var x1 = 0.0
            var x2 = 0.0
            var y1 = 0.0
            var y2 = 0.0
            for (i in x.indices) {
                val v = x[i]
                val y = b0 * v + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
                x2 = x1
                x1 = v
                y2 = y1
                y1 = y
                x[i] = y
            }
        }

        companion object {
            private fun make(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
                Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)

            fun peaking(f0: Double, q: Double, gainDb: Double, fs: Int): Biquad {
                val a = 10.0.pow(gainDb / 40.0)
                val w0 = 2 * PI * f0 / fs
                val alpha = sin(w0) / (2 * q)
                val c = cos(w0)
                return make(1 + alpha * a, -2 * c, 1 - alpha * a, 1 + alpha / a, -2 * c, 1 - alpha / a)
            }

            fun lowShelf(f0: Double, gainDb: Double, fs: Int): Biquad {
                val a = 10.0.pow(gainDb / 40.0)
                val w0 = 2 * PI * f0 / fs
                val c = cos(w0)
                val alpha = sin(w0) / 2 * sqrt(2.0)
                val k = 2 * sqrt(a) * alpha
                return make(
                    a * ((a + 1) - (a - 1) * c + k),
                    2 * a * ((a - 1) - (a + 1) * c),
                    a * ((a + 1) - (a - 1) * c - k),
                    (a + 1) + (a - 1) * c + k,
                    -2 * ((a - 1) + (a + 1) * c),
                    (a + 1) + (a - 1) * c - k,
                )
            }

            fun highShelf(f0: Double, gainDb: Double, fs: Int): Biquad {
                val a = 10.0.pow(gainDb / 40.0)
                val w0 = 2 * PI * f0 / fs
                val c = cos(w0)
                val alpha = sin(w0) / 2 * sqrt(2.0)
                val k = 2 * sqrt(a) * alpha
                return make(
                    a * ((a + 1) + (a - 1) * c + k),
                    -2 * a * ((a - 1) + (a + 1) * c),
                    a * ((a + 1) + (a - 1) * c - k),
                    (a + 1) - (a - 1) * c + k,
                    2 * ((a - 1) - (a + 1) * c),
                    (a + 1) - (a - 1) * c - k,
                )
            }
        }
    }
}
