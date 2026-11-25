package ar.param.advisor.engine

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeSpeechPort : SpeechPort {
    val spoken = mutableListOf<String>()
    val bells = mutableListOf<Boolean>()
    val audioPaths = mutableListOf<String?>()
    val paces = mutableListOf<Int>()
    var speakMs = 0L
    override fun setPaceFromWpm(wpm: Int) {
        paces += wpm
    }
    override suspend fun speakAndAwait(text: String, mode: RecitationMode, audioPath: String?) {
        spoken += text
        audioPaths += audioPath
        if (speakMs > 0) kotlinx.coroutines.delay(speakMs)
    }
    override fun ringBell(isCycleMilestone: Boolean) {
        bells += isCycleMilestone
    }
    override fun stopSpeaking() = Unit
    override fun release() = Unit
}

private fun seg(order: Int, phase: String, text: String, pause: Int = 0, reps: Int = 1, translit: String? = null) =
    PracticeSegmentModel("p_seg_$order", order, phase, "h$order", text, translit, null, pause, reps)

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeRunnerEngineTest {

    private class Harness(scope: TestScope) {
        val speech = FakeSpeechPort()
        val finished = mutableListOf<Boolean>()
        val engine = PracticeRunnerEngine(
            scope = scope,
            speech = speech,
            clock = { scope.currentTime },
            onSessionFinished = { _, _, full -> finished += full },
        )
    }

    private fun mantra(target: Int, bellEvery: Int, pauseMs: Long = 0L, segments: List<PracticeSegmentModel>? = null) =
        PracticeRunSpec(
            practiceId = "prac_mantra",
            title = "Mantra",
            meritDedication = "Que beneficie a todos los seres.",
            segments = segments ?: listOf(seg(1, "CORE", "OM", translit = "Om")),
            mantraConfig = MantraRunConfig(targetRepetitions = target, bellEveryN = bellEvery, pauseBetweenRepsMs = pauseMs),
            practiceType = "MANTRA",
        )

    @Test
    fun ac001_mantraOf7WithBellEvery3() = runTest {
        val h = Harness(this)
        h.engine.start(mantra(target = 7, bellEvery = 3))
        advanceUntilIdle()
        assertEquals(7, h.speech.spoken.size)
        assertTrue(h.speech.spoken.all { it == "Om" })
        assertEquals(listOf(true, true, false), h.speech.bells)
        assertEquals(RunnerStatus.COMPLETED, h.engine.snapshot.value.status)
        assertEquals(listOf(true), h.finished)
    }

    @Test
    fun recordedMantraPassesTheRecordingOnEveryRound() = runTest {
        val h = Harness(this)
        val recorded = seg(1, "CORE", "OM").copy(audioPath = "/rec.m4a")
        h.engine.start(mantra(target = 3, bellEvery = 0, segments = listOf(recorded)))
        advanceUntilIdle()
        assertEquals(listOf<String?>("/rec.m4a", "/rec.m4a", "/rec.m4a"), h.speech.audioPaths)
    }

    @Test
    fun paceCanChangeWhilePlaying() = runTest {
        val h = Harness(this)
        h.speech.speakMs = 1_000L
        h.engine.start(mantra(target = 5, bellEvery = 0).copy(defaultPaceWpm = 100))
        runCurrent()
        assertEquals(100, h.engine.snapshot.value.paceWpm)
        h.engine.setPace(168)
        assertEquals(168, h.engine.snapshot.value.paceWpm)
        assertEquals(listOf(100, 168), h.speech.paces)
        advanceUntilIdle()
        assertEquals(RunnerStatus.COMPLETED, h.engine.snapshot.value.status)
    }

    @Test
    fun ac002_maitriSpeaks5TimesWith65SecondsOfPauses() = runTest {
        val h = Harness(this)
        val spec = PracticeRunSpec(
            practiceId = "prac_med_amor_bondadoso",
            title = "Maitri",
            meritDedication = "d",
            segments = listOf(
                seg(1, "POSTURE_BREATH", "a", pause = 12),
                seg(2, "CORE", "b", pause = 15, reps = 3),
                seg(3, "DEDICATION", "c", pause = 8),
            ),
            practiceType = "GUIDED_MEDITATION",
        )
        h.engine.start(spec)
        advanceUntilIdle()
        assertEquals(listOf("a", "b", "b", "b", "c"), h.speech.spoken)
        assertEquals(65_000L, currentTime)
    }

    @Test
    fun ac003_resumeAfterPauseContinuesWithNextRound() = runTest {
        val h = Harness(this)
        h.speech.speakMs = 1_000L
        val spec = mantra(target = 21, bellEvery = 0, segments = listOf(seg(1, "MOTIVATION", "motivación"), seg(2, "CORE", "OM")))
        h.engine.start(spec)
        // motivación (1 s) + 4 vueltas (1 s cada una) = 5 s
        testScheduler.advanceTimeBy(5_000L)
        runCurrent()
        assertEquals(4, h.engine.snapshot.value.mantraRepetition)
        h.engine.pause()
        assertEquals(RunnerStatus.PAUSED, h.engine.snapshot.value.status)
        val spokenBefore = h.speech.spoken.size
        h.engine.resume()
        advanceUntilIdle()
        val after = h.speech.spoken.drop(spokenBefore)
        assertEquals(17, after.size)
        assertTrue("no se repite la motivación", after.none { it == "motivación" })
        assertEquals(21, h.engine.snapshot.value.mantraRepetition)
    }

    @Test
    fun ac004_mentalModeDoesNotSpeakAndUsesFixedTime() = runTest {
        val h = Harness(this)
        h.engine.start(mantra(target = 3, bellEvery = 0, pauseMs = 900L), RecitationMode.MENTAL)
        advanceUntilIdle()
        assertEquals(0, h.speech.spoken.size)
        assertEquals(3 * (2_600L + 900L), currentTime)
    }

    @Test
    fun ac005_sessionReportedOnlyOnce() = runTest {
        val h = Harness(this)
        h.engine.start(mantra(target = 2, bellEvery = 0))
        advanceUntilIdle()
        h.engine.stop(recordAsCompleted = true)
        advanceUntilIdle()
        assertEquals(listOf(true), h.finished)
    }

    @Test
    fun stopMidwayReportsPartialSessionWithReps() = runTest {
        val h = Harness(this)
        h.speech.speakMs = 1_000L
        h.engine.start(mantra(target = 21, bellEvery = 0))
        testScheduler.advanceTimeBy(5_000L)
        runCurrent()
        h.engine.stop(recordAsCompleted = true)
        advanceUntilIdle()
        assertEquals(listOf(false), h.finished)
        assertEquals(RunnerStatus.IDLE, h.engine.snapshot.value.status)
        assertEquals(5, h.engine.snapshot.value.mantraRepetition)
    }

    @Test
    fun startWhilePlayingRestartsWithoutReporting() = runTest {
        val h = Harness(this)
        h.speech.speakMs = 1_000L
        h.engine.start(mantra(target = 21, bellEvery = 0))
        testScheduler.advanceTimeBy(3_000L)
        h.engine.start(mantra(target = 2, bellEvery = 0))
        advanceUntilIdle()
        assertEquals(listOf(true), h.finished)
        assertEquals(2, h.engine.snapshot.value.mantraRepetition)
    }

    @Test
    fun overridesWinOverCatalog() {
        val spec = PlaybackOverrides(targetReps = 7, mode = RecitationMode.WHISPER).applyTo(mantra(target = 21, bellEvery = 7))
        assertEquals(7, spec.mantraConfig?.targetRepetitions)
        assertEquals(RecitationMode.WHISPER, spec.mantraConfig?.recitationMode)
    }
}
