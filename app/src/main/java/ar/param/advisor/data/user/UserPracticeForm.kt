package ar.param.advisor.data.user

import ar.param.advisor.data.local.entity.MantraConfigEntity
import ar.param.advisor.data.local.entity.PracticeEntity
import ar.param.advisor.data.local.entity.PracticeSegmentEntity
import ar.param.advisor.data.local.entity.PracticeTypes
import ar.param.advisor.data.local.entity.PracticeWithDetails
import ar.param.advisor.data.local.entity.SegmentPhases
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Lo que el usuario completa al crear o editar una práctica propia (oración o mantra).
 * Se guarda en las mismas tablas que el catálogo, con id `user_…` (ADR-0008).
 */
data class UserPracticeForm(
    val id: String = newId(),
    val type: String = PracticeTypes.PRAYER,
    val title: String = "",
    val text: String = "",
    val repetitions: Int = 1,
    val pauseSec: Int = 3,
    val bellEveryN: Int = 0,
    val paceWpm: Int = DEFAULT_PACE_WPM,
    val dedication: String = "",
    val imagePath: String? = null,
    val recordingPath: String? = null,
) {
    val isMantra: Boolean get() = type == PracticeTypes.MANTRA

    /** Motivo por el que no se puede guardar todavía, o null si está lista. */
    fun problem(): Problem? = when {
        title.isBlank() -> Problem.NO_TITLE
        text.isBlank() && recordingPath == null -> Problem.NO_TEXT
        else -> null
    }

    enum class Problem { NO_TITLE, NO_TEXT }

    data class Bundle(
        val practice: PracticeEntity,
        val segments: List<PracticeSegmentEntity>,
        val mantraConfig: MantraConfigEntity?,
    )

    fun toEntities(defaultDedication: String, tradition: String, now: Long = System.currentTimeMillis()): Bundle {
        val cleanTitle = title.trim()
        // Si solo hay grabación, el título se muestra como texto (un segmento sin texto se saltea).
        val cleanText = text.trim().ifEmpty { cleanTitle }
        val reps = repetitions.coerceIn(1, MAX_REPETITIONS)
        val pause = pauseSec.coerceIn(0, MAX_PAUSE_SEC)
        val pace = paceWpm.coerceIn(MIN_PACE_WPM, MAX_PACE_WPM)
        val practice = PracticeEntity(
            id = id,
            type = if (isMantra) PracticeTypes.MANTRA else PracticeTypes.PRAYER,
            title = cleanTitle,
            subtitle = null,
            tradition = tradition,
            estimatedMinutes = estimateMinutes(cleanText, reps, pause, pace),
            audioAssetUri = recordingPath,
            ttsEnabled = true,
            defaultPaceWpm = pace,
            meritDedication = dedication.trim().ifEmpty { defaultDedication },
            paramitaSection = null,
            paramitaTopic = null,
            paramitaUrlPath = null,
            updatedAtEpochMs = now,
            imageUri = imagePath,
        )
        val segment = PracticeSegmentEntity(
            id = "${id}_seg_1",
            practiceId = id,
            segmentOrder = 1,
            phase = SegmentPhases.CORE,
            heading = cleanTitle,
            text = cleanText,
            transliteration = null,
            meaning = null,
            pauseAfterSec = if (isMantra) 0 else pause,
            repeatCount = if (isMantra) 1 else reps,
        )
        val mantra = if (isMantra) {
            MantraConfigEntity(
                practiceId = id,
                targetRepetitions = reps,
                presetCountsJson = presetsJson(reps),
                recitationModesJson = "[\"SPOKEN\",\"WHISPER\",\"MENTAL\"]",
                bellEveryN = bellEveryN.coerceIn(0, reps),
                pauseBetweenRepsMs = pause * 1_000L,
            )
        } else {
            null
        }
        return Bundle(practice, listOf(segment), mantra)
    }

    companion object {
        const val DEFAULT_PACE_WPM = 120
        const val MIN_PACE_WPM = 60
        const val MAX_PACE_WPM = 192
        const val MAX_REPETITIONS = 1_080
        const val MAX_PAUSE_SEC = 120

        fun newId(): String = "user_" + UUID.randomUUID().toString().replace("-", "")

        fun from(details: PracticeWithDetails, defaultDedication: String): UserPracticeForm {
            val p = details.practice
            val seg = details.orderedSegments.firstOrNull()
            val mantra = details.mantraConfig
            return UserPracticeForm(
                id = p.id,
                type = if (mantra != null) PracticeTypes.MANTRA else PracticeTypes.PRAYER,
                title = p.title,
                text = seg?.text?.takeIf { it != p.title || p.audioAssetUri == null }.orEmpty(),
                repetitions = mantra?.targetRepetitions ?: seg?.repeatCount ?: 1,
                pauseSec = mantra?.let { (it.pauseBetweenRepsMs / 1_000L).toInt() } ?: seg?.pauseAfterSec ?: 3,
                bellEveryN = mantra?.bellEveryN ?: 0,
                paceWpm = p.defaultPaceWpm,
                dedication = p.meritDedication.takeIf { it != defaultDedication }.orEmpty(),
                imagePath = p.imageUri,
                recordingPath = p.audioAssetUri,
            )
        }

        /** Atajos de vueltas: los clásicos más la cantidad elegida. */
        internal fun presetsJson(reps: Int): String =
            (listOf(3, 7, 21, 108) + reps).distinct().sorted().joinToString(",", "[", "]")

        internal fun estimateMinutes(text: String, reps: Int, pauseSec: Int, paceWpm: Int): Int {
            val words = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }.coerceAtLeast(1)
            val seconds = reps * (words * 60.0 / paceWpm + pauseSec)
            return (seconds / 60.0).roundToInt().coerceIn(1, 999)
        }
    }
}
