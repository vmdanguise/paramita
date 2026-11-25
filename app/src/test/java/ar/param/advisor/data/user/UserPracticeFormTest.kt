package ar.param.advisor.data.user

import ar.param.advisor.data.local.entity.PracticeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPracticeFormTest {

    @Test
    fun needsTitleAndTextOrRecording() {
        assertEquals(UserPracticeForm.Problem.NO_TITLE, UserPracticeForm(text = "x").problem())
        assertEquals(UserPracticeForm.Problem.NO_TEXT, UserPracticeForm(title = "T").problem())
        assertNull(UserPracticeForm(title = "T", recordingPath = "/a.m4a").problem())
        assertNull(UserPracticeForm(title = "T", text = "x").problem())
    }

    @Test
    fun idsAreUserPrefixedAndUnique() {
        val a = UserPracticeForm.newId()
        assertTrue(a.startsWith("user_"))
        assertTrue(a != UserPracticeForm.newId())
    }

    @Test
    fun emptyDedicationUsesDefault_andValuesAreClamped() {
        val b = UserPracticeForm(title = " T ", text = "x", repetitions = 99_999, pauseSec = -3, paceWpm = 10)
            .toEntities("Dedicación por defecto", "Propia", now = 5L)
        assertEquals("T", b.practice.title)
        assertEquals("Dedicación por defecto", b.practice.meritDedication)
        assertEquals(UserPracticeForm.MAX_REPETITIONS, b.segments.single().repeatCount)
        assertEquals(0, b.segments.single().pauseAfterSec)
        assertEquals(UserPracticeForm.MIN_PACE_WPM, b.practice.defaultPaceWpm)
        assertNull(b.mantraConfig)
    }

    @Test
    fun recordingOnlyMantraShowsTitleAsText() {
        val b = UserPracticeForm(type = PracticeTypes.MANTRA, title = "Mi mantra", recordingPath = "/r.m4a", repetitions = 7, bellEveryN = 50)
            .toEntities("D", "Propia")
        assertEquals("Mi mantra", b.segments.single().text)
        val m = assertNotNull(b.mantraConfig).let { b.mantraConfig!! }
        assertEquals(7, m.targetRepetitions)
        assertEquals(7, m.bellEveryN)
        assertEquals("[3,7,21,108]", m.presetCountsJson)
        assertEquals("/r.m4a", b.practice.audioAssetUri)
    }

    @Test
    fun presetsIncludeChosenCount() {
        assertEquals("[3,7,21,50,108]", UserPracticeForm.presetsJson(50))
    }

    @Test
    fun estimateIsAtLeastOneMinute() {
        assertEquals(1, UserPracticeForm.estimateMinutes("om", 1, 0, 120))
        // 108 vueltas de 4 palabras a 120 ppm + 1 s de pausa = 108 * 3 s = 5,4 min.
        assertEquals(5, UserPracticeForm.estimateMinutes("om mani padme hum", 108, 1, 120))
    }
}
