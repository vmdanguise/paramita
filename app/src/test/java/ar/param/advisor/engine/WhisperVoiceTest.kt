package ar.param.advisor.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class WhisperVoiceTest {

    private fun wav(samples: ShortArray, rate: Int, channels: Int = 1, dataSize: Int? = null): ByteArray {
        val data = samples.size * 2
        val bb = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(channels.toShort())
            .putInt(rate).putInt(rate * 2 * channels).putShort((2 * channels).toShort()).putShort(16)
        bb.put("data".toByteArray()).putInt(dataSize ?: data)
        samples.forEach { bb.putShort(it) }
        return bb.array()
    }

    @Test
    fun parsesMonoWav() {
        val clip = WavPcm.parse(wav(shortArrayOf(1, -2, 3), 22_050))
        assertNotNull(clip)
        assertEquals(22_050, clip!!.sampleRate)
        assertArrayEquals(shortArrayOf(1, -2, 3), clip.samples)
    }

    @Test
    fun parsesWavWithZeroDataSize() {
        val clip = WavPcm.parse(wav(shortArrayOf(5, 6), 16_000, dataSize = 0))
        assertArrayEquals(shortArrayOf(5, 6), clip!!.samples)
    }

    @Test
    fun mixesStereoToMono() {
        val clip = WavPcm.parse(wav(shortArrayOf(10, 20, -4, 0), 16_000, channels = 2))
        assertArrayEquals(shortArrayOf(15, -2), clip!!.samples)
    }

    @Test
    fun rejectsGarbage() {
        assertNull(WavPcm.parse(ByteArray(8)))
        assertNull(WavPcm.parse("not a wav file at all".toByteArray()))
    }

    /** Correlación normalizada entre la señal y ella misma desplazada `lag` muestras. */
    private fun periodicity(x: ShortArray, lag: Int): Double {
        var num = 0.0
        var a = 0.0
        var b = 0.0
        for (i in 0 until x.size - lag) {
            num += x[i].toDouble() * x[i + lag]
            a += x[i].toDouble() * x[i]
            b += x[i + lag].toDouble() * x[i + lag]
        }
        return num / sqrt(a * b)
    }

    @Test
    fun removesVoicingButKeepsEnergyAndLength() {
        val rate = 22_050
        val pitch = 140.0
        val period = (rate / pitch).toInt()
        // Voz sintética: pulso glotal rico en armónicos (vocal sostenida).
        val voiced = ShortArray(rate) { i ->
            val t = i.toDouble() / rate
            var v = 0.0
            for (h in 1..20) v += sin(2 * PI * pitch * h * t) / h
            (v * 6_000).toInt().toShort()
        }
        val whisper = Whisperizer.process(voiced, rate)

        assertEquals(voiced.size, whisper.size)
        assertTrue("la entrada es periódica", periodicity(voiced, period) > 0.9)
        assertTrue("el susurro no debe tener tono", abs(periodicity(whisper, period)) < 0.3)
        val rms = sqrt(whisper.sumOf { it.toDouble() * it } / whisper.size)
        assertTrue("el susurro tiene que oírse", rms > 1_000)
    }

    @Test
    fun silenceStaysSilentAndIsDeterministic() {
        val silent = ShortArray(4_000)
        assertArrayEquals(silent, Whisperizer.process(silent, 16_000))
        val noise = ShortArray(4_000) { ((it * 7919) % 2000 - 1000).toShort() }
        assertArrayEquals(Whisperizer.process(noise, 16_000), Whisperizer.process(noise, 16_000))
    }

    @Test
    fun frameIsTwentyFiveMsAndEven() {
        assertEquals(550, Whisperizer.frameSizeFor(22_050))
        assertEquals(400, Whisperizer.frameSizeFor(16_000))
        assertEquals(1_200, Whisperizer.frameSizeFor(48_000))
        assertEquals(26, Whisperizer.lpcOrderFor(22_050))
    }
}
