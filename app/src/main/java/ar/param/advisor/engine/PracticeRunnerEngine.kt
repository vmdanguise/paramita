package ar.param.advisor.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

interface SpeechPort {
    fun setPaceFromWpm(wpm: Int)
    /** Si [audioPath] apunta a una grabación propia que existe, suena esa grabación en lugar del TTS. */
    suspend fun speakAndAwait(text: String, mode: RecitationMode, audioPath: String? = null)
    fun ringBell(isCycleMilestone: Boolean)
    fun stopSpeaking()
    fun release()
}

/**
 * Recorre los segmentos, repite el mantra y toca la campana (spec-architecture-motor-practica-tts).
 * No depende de Android: la voz llega por [SpeechPort].
 */
class PracticeRunnerEngine(
    private val scope: CoroutineScope,
    private val speech: SpeechPort,
    private val clock: () -> Long = System::currentTimeMillis,
    private val mentalRepDurationMs: Long = 2600L,
    private val onSessionFinished: suspend (spec: PracticeRunSpec, snapshot: RunnerSnapshot, completedFull: Boolean) -> Unit,
) {
    private val _snapshot = MutableStateFlow(RunnerSnapshot())
    val snapshot: StateFlow<RunnerSnapshot> = _snapshot.asStateFlow()

    private var spec: PracticeRunSpec? = null
    private var job: Job? = null
    private var sessionReported = false

    private data class Plan(val before: List<Int>, val mantraIndex: Int?, val after: List<Int>)

    fun start(spec: PracticeRunSpec, initialMode: RecitationMode = RecitationMode.SPOKEN) {
        job?.cancel()
        speech.stopSpeaking()
        this.spec = spec
        sessionReported = false
        speech.setPaceFromWpm(spec.defaultPaceWpm)
        _snapshot.update {
            RunnerSnapshot(
                status = RunnerStatus.PLAYING,
                stage = RunnerStage.SEGMENTS_BEFORE,
                practiceId = spec.practiceId,
                practiceTitle = spec.title,
                segmentIndex = 0,
                totalSegments = spec.segments.size,
                currentSegment = spec.segments.firstOrNull(),
                mantraTargetRepetitions = spec.mantraConfig?.targetRepetitions?.coerceAtLeast(1) ?: 0,
                recitationMode = initialMode,
                paceWpm = spec.defaultPaceWpm,
                meritDedication = spec.meritDedication,
                sessionStartedAtEpochMs = clock(),
            )
        }
        job = scope.launch { run(spec, resuming = false) }
    }

    fun pause() {
        if (_snapshot.value.status != RunnerStatus.PLAYING) return
        job?.cancel()
        job = null
        speech.stopSpeaking()
        _snapshot.update { it.copy(status = RunnerStatus.PAUSED, pauseRemainingSec = 0) }
    }

    fun resume() {
        val current = spec ?: return
        if (_snapshot.value.status != RunnerStatus.PAUSED) return
        _snapshot.update { it.copy(status = RunnerStatus.PLAYING) }
        job = scope.launch { run(current, resuming = true) }
    }

    fun stop(recordAsCompleted: Boolean = false) {
        val wasActive = _snapshot.value.isActive
        job?.cancel()
        job = null
        speech.stopSpeaking()
        val last = _snapshot.value
        val current = spec
        if (recordAsCompleted && wasActive && !sessionReported && current != null) {
            sessionReported = true
            scope.launch { onSessionFinished(current, last, false) }
        }
        _snapshot.update { it.copy(status = RunnerStatus.IDLE, pauseRemainingSec = 0) }
    }

    /** Cambia la velocidad mientras suena: vale desde la próxima frase. */
    fun setPace(wpm: Int) {
        speech.setPaceFromWpm(wpm)
        _snapshot.update { it.copy(paceWpm = wpm) }
    }

    fun setRecitationMode(mode: RecitationMode) {
        _snapshot.update { it.copy(recitationMode = mode) }
        if (mode == RecitationMode.MENTAL) speech.stopSpeaking()
    }

    private fun plan(spec: PracticeRunSpec): Plan {
        val segs = spec.segments
        if (spec.mantraConfig == null || segs.isEmpty()) return Plan(segs.indices.toList(), null, emptyList())
        val lastCore = segs.indexOfLast { it.phase == "CORE" }
        val m = if (lastCore >= 0) lastCore else segs.lastIndex
        val before = (0 until m).filter { segs[it].phase != "CORE" }
        val after = ((m + 1) until segs.size).toList()
        return Plan(before, m, after)
    }

    private suspend fun run(spec: PracticeRunSpec, resuming: Boolean) {
        val plan = plan(spec)
        var resume = resuming

        if (_snapshot.value.stage == RunnerStage.SEGMENTS_BEFORE) {
            runSegments(spec, plan.before, resume)
            resume = false
            val next = if (plan.mantraIndex != null) RunnerStage.MANTRA_LOOP else RunnerStage.DONE
            _snapshot.update { it.copy(stage = next) }
        }
        if (_snapshot.value.stage == RunnerStage.MANTRA_LOOP && plan.mantraIndex != null) {
            runMantraLoop(spec, plan.mantraIndex)
            resume = false
            _snapshot.update { it.copy(stage = RunnerStage.SEGMENTS_AFTER) }
        }
        if (_snapshot.value.stage == RunnerStage.SEGMENTS_AFTER) {
            runSegments(spec, plan.after, resume)
            _snapshot.update { it.copy(stage = RunnerStage.DONE) }
        }
        finish(spec)
    }

    private suspend fun runSegments(spec: PracticeRunSpec, indices: List<Int>, resume: Boolean) {
        val snap = _snapshot.value
        val startPos = if (resume) indices.indexOf(snap.segmentIndex).coerceAtLeast(0) else 0
        for (pos in startPos until indices.size) {
            val index = indices[pos]
            val segment = spec.segments[index]
            if (segment.text.isBlank()) continue
            val total = segment.repeatCount.coerceAtLeast(1)
            val firstRep = if (resume && pos == startPos && index == snap.segmentIndex) {
                snap.segmentRepetition.coerceIn(1, total)
            } else {
                1
            }
            for (rep in firstRep..total) {
                currentCoroutineContext().ensureActive()
                _snapshot.update {
                    it.copy(
                        segmentIndex = index,
                        currentSegment = segment,
                        segmentRepetition = rep,
                        segmentTotalRepetitions = total,
                        pauseRemainingSec = 0,
                    )
                }
                utter(segment.text, segment.audioPath)
                countdown(segment.pauseAfterSec)
            }
        }
    }

    private suspend fun runMantraLoop(spec: PracticeRunSpec, mantraIndex: Int) {
        val config = spec.mantraConfig ?: return
        val segment = spec.segments[mantraIndex]
        val target = config.targetRepetitions.coerceAtLeast(1)
        _snapshot.update {
            it.copy(
                segmentIndex = mantraIndex,
                currentSegment = segment,
                segmentRepetition = 1,
                segmentTotalRepetitions = 1,
                mantraTargetRepetitions = target,
                pauseRemainingSec = 0,
            )
        }
        val text = segment.transliteration?.takeIf { it.isNotBlank() } ?: segment.text
        for (rep in (_snapshot.value.mantraRepetition + 1)..target) {
            currentCoroutineContext().ensureActive()
            utter(text, segment.audioPath)
            _snapshot.update { it.copy(mantraRepetition = rep) }
            if (config.bellEveryN > 0 && rep % config.bellEveryN == 0 && rep < target) {
                speech.ringBell(isCycleMilestone = true)
            }
            delay(config.pauseBetweenRepsMs)
        }
    }

    private suspend fun utter(text: String, audioPath: String? = null) {
        val mode = _snapshot.value.recitationMode
        if (mode == RecitationMode.MENTAL) {
            delay(mentalRepDurationMs)
        } else {
            speech.speakAndAwait(text, mode, audioPath)
        }
        currentCoroutineContext().ensureActive()
    }

    private suspend fun countdown(seconds: Int) {
        for (remaining in seconds downTo 1) {
            _snapshot.update { it.copy(pauseRemainingSec = remaining) }
            delay(1_000L)
        }
        _snapshot.update { it.copy(pauseRemainingSec = 0) }
    }

    private suspend fun finish(spec: PracticeRunSpec) {
        speech.ringBell(isCycleMilestone = false)
        _snapshot.update { it.copy(status = RunnerStatus.COMPLETED, stage = RunnerStage.DONE, pauseRemainingSec = 0) }
        if (!sessionReported) {
            sessionReported = true
            onSessionFinished(spec, _snapshot.value, true)
        }
    }
}
