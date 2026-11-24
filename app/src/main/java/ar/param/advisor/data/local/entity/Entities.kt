package ar.param.advisor.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "practices")
data class PracticeEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val subtitle: String?,
    val tradition: String,
    @ColumnInfo(name = "estimated_minutes", defaultValue = "5") val estimatedMinutes: Int,
    @ColumnInfo(name = "audio_asset_uri") val audioAssetUri: String?,
    @ColumnInfo(name = "tts_enabled", defaultValue = "1") val ttsEnabled: Boolean,
    @ColumnInfo(name = "default_pace_wpm", defaultValue = "120") val defaultPaceWpm: Int,
    @ColumnInfo(name = "merit_dedication") val meritDedication: String,
    @ColumnInfo(name = "paramita_section") val paramitaSection: String?,
    @ColumnInfo(name = "paramita_topic") val paramitaTopic: String?,
    @ColumnInfo(name = "paramita_url_path") val paramitaUrlPath: String?,
    @ColumnInfo(name = "is_favorite", defaultValue = "0") val isFavorite: Boolean = false,
    @ColumnInfo(name = "is_archived", defaultValue = "0") val isArchived: Boolean = false,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "practice_segments",
    foreignKeys = [
        ForeignKey(
            entity = PracticeEntity::class,
            parentColumns = ["id"],
            childColumns = ["practice_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["practice_id", "segment_order"], name = "idx_segments_practice_order")],
)
data class PracticeSegmentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "practice_id") val practiceId: String,
    @ColumnInfo(name = "segment_order") val segmentOrder: Int,
    val phase: String,
    val heading: String,
    val text: String,
    val transliteration: String?,
    val meaning: String?,
    @ColumnInfo(name = "pause_after_sec", defaultValue = "3") val pauseAfterSec: Int,
    @ColumnInfo(name = "repeat_count", defaultValue = "1") val repeatCount: Int,
)

@Entity(
    tableName = "mantra_configs",
    foreignKeys = [
        ForeignKey(
            entity = PracticeEntity::class,
            parentColumns = ["id"],
            childColumns = ["practice_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MantraConfigEntity(
    @PrimaryKey @ColumnInfo(name = "practice_id") val practiceId: String,
    @ColumnInfo(name = "target_repetitions", defaultValue = "21") val targetRepetitions: Int,
    @ColumnInfo(name = "preset_counts_json") val presetCountsJson: String,
    @ColumnInfo(name = "recitation_modes_json")
    val recitationModesJson: String,
    @ColumnInfo(name = "bell_every_n", defaultValue = "7") val bellEveryN: Int,
    @ColumnInfo(name = "pause_between_reps_ms", defaultValue = "900") val pauseBetweenRepsMs: Long,
)

@Entity(
    tableName = "practice_tags",
    primaryKeys = ["practice_id", "tag"],
    foreignKeys = [
        ForeignKey(
            entity = PracticeEntity::class,
            parentColumns = ["id"],
            childColumns = ["practice_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PracticeTagEntity(
    @ColumnInfo(name = "practice_id") val practiceId: String,
    val tag: String,
)

@Entity(
    tableName = "practice_jewel_focus",
    primaryKeys = ["practice_id", "jewel"],
    foreignKeys = [
        ForeignKey(
            entity = PracticeEntity::class,
            parentColumns = ["id"],
            childColumns = ["practice_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PracticeJewelFocusEntity(
    @ColumnInfo(name = "practice_id") val practiceId: String,
    val jewel: String,
)

/**
 * Sin ON DELETE CASCADE: las prácticas nunca se borran, se archivan (ADR-0003, opción A),
 * así el historial personal sobrevive a cualquier actualización del catálogo.
 */
@Entity(
    tableName = "practice_sessions",
    foreignKeys = [
        ForeignKey(
            entity = PracticeEntity::class,
            parentColumns = ["id"],
            childColumns = ["practice_id"],
        ),
    ],
    indices = [
        Index(value = ["started_at_epoch_ms"], name = "idx_sessions_started_at", orders = [Index.Order.DESC]),
        Index(value = ["practice_id"], name = "idx_sessions_practice"),
    ],
)
data class PracticeSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "practice_id") val practiceId: String,
    @ColumnInfo(name = "started_at_epoch_ms") val startedAtEpochMs: Long,
    @ColumnInfo(name = "ended_at_epoch_ms") val endedAtEpochMs: Long?,
    @ColumnInfo(name = "completed_repetitions", defaultValue = "0") val completedRepetitions: Int,
    @ColumnInfo(name = "recitation_mode") val recitationMode: String?,
    @ColumnInfo(name = "completed_full", defaultValue = "0") val completedFull: Boolean,
    @ColumnInfo(name = "merit_dedicated", defaultValue = "0") val meritDedicated: Boolean,
    val note: String?,
)

@Entity(
    tableName = "merit_and_calm_events",
    indices = [Index(value = ["created_at_epoch_ms"], name = "idx_merit_events_created_at", orders = [Index.Order.DESC])],
)
data class MeritAndCalmEventEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "event_type") val eventType: String,
    val title: String,
    @ColumnInfo(name = "trigger_context") val triggerContext: String?,
    @ColumnInfo(name = "avoided_amount_estimate") val avoidedAmountEstimate: Double?,
    @ColumnInfo(name = "currency_code") val currencyCode: String?,
    @ColumnInfo(name = "emotion_noticed") val emotionNoticed: String?,
    @ColumnInfo(name = "dedication_note") val dedicationNote: String?,
    val outcome: String? = null,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
)

@Entity(tableName = "mindful_app_targets")
data class MindfulAppTargetEntity(
    @PrimaryKey @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    val category: String,
    @ColumnInfo(name = "prompt_enabled", defaultValue = "1") val promptEnabled: Boolean,
    @ColumnInfo(name = "cooldown_minutes", defaultValue = "15") val cooldownMinutes: Int,
)

data class PracticeWithDetails(
    @Embedded val practice: PracticeEntity,
    @Relation(parentColumn = "id", entityColumn = "practice_id")
    val segments: List<PracticeSegmentEntity>,
    @Relation(parentColumn = "id", entityColumn = "practice_id")
    val mantraConfig: MantraConfigEntity?,
    @Relation(
        entity = PracticeTagEntity::class,
        parentColumn = "id",
        entityColumn = "practice_id",
        projection = ["tag"],
    )
    val tags: List<String>,
    @Relation(
        entity = PracticeJewelFocusEntity::class,
        parentColumn = "id",
        entityColumn = "practice_id",
        projection = ["jewel"],
    )
    val jewels: List<String>,
) {
    val orderedSegments: List<PracticeSegmentEntity>
        get() = segments.sortedBy { it.segmentOrder }
}

data class SessionWithPractice(
    @Embedded val session: PracticeSessionEntity,
    @Relation(parentColumn = "practice_id", entityColumn = "id")
    val practice: PracticeEntity?,
)

object PracticeTypes {
    const val PRAYER = "PRAYER"
    const val MANTRA = "MANTRA"
    const val GUIDED_MEDITATION = "GUIDED_MEDITATION"
    const val IMPULSE_PAUSE = "IMPULSE_PAUSE"
    val ALL = setOf(PRAYER, MANTRA, GUIDED_MEDITATION, IMPULSE_PAUSE)
}

object SegmentPhases {
    const val CORE = "CORE"
    val ALL = setOf("MOTIVATION", "POSTURE_BREATH", CORE, "CONTEMPLATION", "PAUSE", "DEDICATION")
}

object Jewels {
    val ALL = setOf("BUDDHA", "DHARMA", "SANGHA")
}
