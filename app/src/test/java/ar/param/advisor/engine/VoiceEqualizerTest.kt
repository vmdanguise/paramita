package ar.param.advisor.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class VoiceEqualizerTest {
    private val rate = 22_050

    private fun tone(hz: Double, amp: Double = 8_000.0, n: Int = rate) =
        ShortArray(n) { (amp * sin(2 * PI * hz * it / rate)).toInt().toShort() }

    /** Energía de la segunda mitad (ya sin el transitorio inicial del filtro). */
    private fun rms(x: ShortArray): Double {
        val tail = x.copyOfRange(x.size / 2, x.size)
        return sqrt(tail.sumOf { it.toDouble() * it } / tail.size)
    }

    @Test
    fun flatSettingsLeaveSignalUnchanged() {
        val input = tone(440.0)
        assertArrayEquals(input, VoiceEqualizer.process(input, rate, 0f, 0f, 0f))
    }

    @Test
    fun bassBoostRaisesLowsMoreThanHighs() {
        val low = rms(VoiceEqualizer.process(tone(80.0), rate, 9f, 0f, 0f)) / rms(tone(80.0))
        val high = rms(VoiceEqualizer.process(tone(6_000.0), rate, 9f, 0f, 0f)) / rms(tone(6_000.0))
        assertTrue("graves x$low", low > 2.0)
        assertTrue("agudos x$high", high in 0.85..1.15)
    }

    @Test
    fun trebleCutLowersHighs() {
        val high = rms(VoiceEqualizer.process(tone(8_000.0), rate, 0f, 0f, -9f)) / rms(tone(8_000.0))
        assertTrue("agudos x$high", high < 0.5)
    }

    @Test
    fun loudBoostNeverClips() {
        val out = VoiceEqualizer.process(tone(100.0, amp = 30_000.0), rate, 12f, 0f, 0f)
        assertTrue(out.all { it > Short.MIN_VALUE && it < Short.MAX_VALUE })
    }

    @Test
    fun settingsAreClampedAndFlatDetected() {
        val v = VoiceSettings(pitch = 9f, volume = 0f, bassDb = 40f).normalized()
        assertEquals(VoiceSettings.MAX_PITCH, v.pitch)
        assertEquals(VoiceSettings.MIN_VOLUME, v.volume)
        assertEquals(VoiceSettings.MAX_GAIN_DB, v.bassDb)
        assertTrue(VoiceSettings().isFlatEq)
    }
}
