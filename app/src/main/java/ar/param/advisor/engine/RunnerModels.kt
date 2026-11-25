package ar.param.advisor.engine

import ar.param.advisor.data.local.entity.PracticeWithDetails
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

enum class RunnerStatus { IDLE, PLAYING, PAUSED, COMPLETED }

enum class RunnerStage { SEGMENTS_BEFORE, MANTRA_LOOP, SEGMENTS_AFTER, DONE }

enum class RecitationMode(val labelEs: String) {
    SPOKEN("Voz clara"),
    WHISPER("Susurro sereno"),
    MENTAL("Recitación mental / pausa"),
}

data class PracticeSegmentModel(
    val id: String,
    val order: Int,
    val phase: String,
    val heading: String,
    val text: String,
    val transliteration: String? = null,
    val meaning: String? = null,
    val pauseAfterSec: Int = 3,
    val repeatCount: Int = 1,
    /** Grabación propia que reemplaza a la voz sintética en este segmento (REQ-036, B-02). */
    val audioPath: String? = null,
)

data class MantraRunConfig(
    val targetRepetitions: Int = 21,
    val bellEveryN: Int = 7,
    val pauseBetweenRepsMs: Long = 900L,
    val recitationMode: RecitationMode = RecitationMode.SPOKEN,
    val presetCounts: List<Int> = listOf(3, 7, 21, 108),
)

data class PracticeRunSpec(
    val practiceId: String,
    val title: String,
    val defaultPaceWpm: Int = 120,
    val meritDedication: String,
    val segments: List<PracticeSegmentModel>,
    val mantraConfig: MantraRunConfig? = null,
    val practiceType: String = "PRAYER",
    val imagePath: String? = null,
)

data class RunnerSnapshot(
    val status: RunnerStatus = RunnerStatus.IDLE,
    val stage: RunnerStage = RunnerStage.SEGMENTS_BEFORE,
    val practiceId: String? = null,
    val practiceTitle: String = "",
    val segmentIndex: Int = 0,
    val totalSegments: Int = 0,
    val currentSegment: PracticeSegmentModel? = null,
    val segmentRepetition: Int = 1,
    val segmentTotalRepetitions: Int = 1,
    val mantraRepetition: Int = 0,
    val mantraTargetRepetitions: Int = 0,
    val recitationMode: RecitationMode = RecitationMode.SPOKEN,
    val pauseRemainingSec: Int = 0,
    val paceWpm: Int = 120,
    val meritDedication: String = "",
    val sessionStartedAtEpochMs: Long = 0L,
) {
    val isActive: Boolean get() = status == RunnerStatus.PLAYING || status == RunnerStatus.PAUSED
}

/** Ajustes elegidos por el usuario que ganan sobre el catálogo (spec del servicio §4.1). */
data class PlaybackOverrides(
    val targetReps: Int? = null,
    val paceWpm: Int? = null,
    val pauseBetweenRepsMs: Long? = null,
    val bellEveryN: Int? = null,
    val mode: RecitationMode = RecitationMode.SPOKEN,
) {
    fun applyTo(spec: PracticeRunSpec): PracticeRunSpec {
        val cfg = spec.mantraConfig?.let { c ->
            c.copy(
                targetRepetitions = targetReps?.takeIf { it > 0 } ?: c.targetRepetitions,
                pauseBetweenRepsMs = pauseBetweenRepsMs?.takeIf { it > 0 } ?: c.pauseBetweenRepsMs,
                bellEveryN = bellEveryN?.takeIf { it >= 0 } ?: c.bellEveryN,
                recitationMode = mode,
            )
        }
        return spec.copy(
            defaultPaceWpm = paceWpm?.takeIf { it > 0 } ?: spec.defaultPaceWpm,
            mantraConfig = cfg,
        )
    }
}

private val DEFAULT_PRESETS = listOf(3, 7, 21, 108)
private val presetJson = Json { ignoreUnknownKeys = true }

/** Solo rutas de archivos propios; los `asset:///` del catálogo todavía no tienen audio (B-02). */
private fun localAudioPath(uri: String?): String? = uri?.takeIf { it.startsWith("/") }

fun parsePresetCounts(json: String): List<Int> = runCatching {
    presetJson.decodeFromString(ListSerializer(Int.serializer()), json).filter { it > 0 }
}.getOrNull()?.takeIf { it.isNotEmpty() } ?: DEFAULT_PRESETS

fun PracticeWithDetails.toPracticeRunSpec(): PracticeRunSpec {
    val ordered = orderedSegments
    val recording = localAudioPath(practice.audioAssetUri)
    // La grabación reemplaza al segmento principal (el último CORE, el mismo que repite el mantra).
    val recordedIndex = if (recording == null) {
        -1
    } else {
        ordered.indexOfLast { it.phase == "CORE" }.takeIf { it >= 0 } ?: ordered.lastIndex
    }
    return PracticeRunSpec(
        practiceId = practice.id,
        title = practice.title,
        defaultPaceWpm = practice.defaultPaceWpm,
        meritDedication = practice.meritDedication,
        segments = ordered.mapIndexed { index, it ->
            PracticeSegmentModel(
                id = it.id,
                order = it.segmentOrder,
                phase = it.phase,
                heading = it.heading,
                text = it.text,
                transliteration = it.transliteration,
                meaning = it.meaning,
                pauseAfterSec = it.pauseAfterSec,
                repeatCount = it.repeatCount,
                audioPath = if (index == recordedIndex) recording else null,
            )
        },
        mantraConfig = mantraConfig?.let {
            MantraRunConfig(
                targetRepetitions = it.targetRepetitions,
                bellEveryN = it.bellEveryN,
                pauseBetweenRepsMs = it.pauseBetweenRepsMs,
                presetCounts = parsePresetCounts(it.presetCountsJson),
            )
        },
        practiceType = practice.type,
        imagePath = practice.imageUri,
    )
}
