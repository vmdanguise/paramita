package ar.param.advisor.data.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CatalogBundleDto(
    @SerialName("schema_version") val schemaVersion: Int,
    val source: String? = null,
    @SerialName("generated_at") val generatedAt: String? = null,
    val practices: List<PracticeDto> = emptyList(),
)

@Serializable
data class PracticeDto(
    val id: String = "",
    val type: String = "",
    val title: String = "",
    val subtitle: String? = null,
    val tradition: String? = null,
    @SerialName("jewel_focus") val jewelFocus: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    @SerialName("estimated_minutes") val estimatedMinutes: Int = 5,
    @SerialName("audio_asset_uri") val audioAssetUri: String? = null,
    @SerialName("tts_enabled") val ttsEnabled: Boolean = true,
    @SerialName("default_pace_wpm") val defaultPaceWpm: Int = 120,
    val segments: List<SegmentDto> = emptyList(),
    @SerialName("mantra_config") val mantraConfig: MantraConfigDto? = null,
    @SerialName("merit_dedication") val meritDedication: String = "",
    @SerialName("paramita_ref") val paramitaRef: ParamitaRefDto? = null,
)

@Serializable
data class SegmentDto(
    val order: Int = 0,
    val phase: String = "",
    val heading: String = "",
    val text: String = "",
    val transliteration: String? = null,
    val meaning: String? = null,
    @SerialName("pause_after_sec") val pauseAfterSec: Int = 3,
    @SerialName("repeat_count") val repeatCount: Int = 1,
)

@Serializable
data class MantraConfigDto(
    @SerialName("target_repetitions") val targetRepetitions: Int = 21,
    @SerialName("preset_counts") val presetCounts: List<Int> = listOf(3, 7, 21, 108),
    @SerialName("recitation_modes") val recitationModes: List<String> = listOf("SPOKEN", "WHISPER", "MENTAL"),
    @SerialName("bell_every_n") val bellEveryN: Int = 7,
    @SerialName("pause_between_reps_ms") val pauseBetweenRepsMs: Long = 900L,
)

@Serializable
data class ParamitaRefDto(
    val section: String? = null,
    val topic: String? = null,
    @SerialName("url_path") val urlPath: String? = null,
)
