package ar.param.advisor.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import ar.param.advisor.data.local.entity.MantraConfigEntity
import ar.param.advisor.data.local.entity.MeritAndCalmEventEntity
import ar.param.advisor.data.local.entity.MindfulAppTargetEntity
import ar.param.advisor.data.local.entity.PracticeEntity
import ar.param.advisor.data.local.entity.PracticeJewelFocusEntity
import ar.param.advisor.data.local.entity.PracticeSegmentEntity
import ar.param.advisor.data.local.entity.PracticeSessionEntity
import ar.param.advisor.data.local.entity.PracticeTagEntity
import ar.param.advisor.data.local.entity.PracticeWithDetails
import ar.param.advisor.data.local.entity.SessionWithPractice
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PracticeDao {
    @Transaction
    @Query("SELECT * FROM practices WHERE is_archived = 0 ORDER BY title ASC")
    abstract fun observeAllPracticesWithDetails(): Flow<List<PracticeWithDetails>>

    @Transaction
    @Query("SELECT * FROM practices WHERE is_archived = 0 AND type = :type ORDER BY title ASC")
    abstract fun observePracticesByType(type: String): Flow<List<PracticeWithDetails>>

    @Transaction
    @Query("SELECT * FROM practices WHERE id = :practiceId AND is_archived = 0")
    abstract fun observePracticeById(practiceId: String): Flow<PracticeWithDetails?>

    @Transaction
    @Query("SELECT * FROM practices WHERE id = :practiceId AND is_archived = 0")
    abstract suspend fun getPracticeByIdOnce(practiceId: String): PracticeWithDetails?

    @Transaction
    @Query(
        "SELECT p.* FROM practices p INNER JOIN practice_jewel_focus j ON j.practice_id = p.id " +
            "WHERE j.jewel = :jewel AND p.is_archived = 0 ORDER BY p.title ASC",
    )
    abstract fun observePracticesByJewel(jewel: String): Flow<List<PracticeWithDetails>>

    @Transaction
    @Query(
        "SELECT * FROM practices WHERE is_archived = 0 AND " +
            "(title LIKE '%' || :query || '%' OR subtitle LIKE '%' || :query || '%') ORDER BY title ASC",
    )
    abstract fun searchPractices(query: String): Flow<List<PracticeWithDetails>>

    @Query("SELECT id FROM practices WHERE is_favorite = 1")
    abstract suspend fun getFavoriteIds(): List<String>

    @Query("SELECT COUNT(*) FROM practices WHERE is_archived = 0")
    abstract suspend fun countPractices(): Int

    @Query("UPDATE practices SET is_favorite = :isFavorite WHERE id = :practiceId")
    abstract suspend fun setFavorite(practiceId: String, isFavorite: Boolean)

    /**
     * Solo upsert: nunca borra filas de `practices` (CON-001). Las que faltan en el
     * catálogo nuevo quedan archivadas, así sus sesiones siguen existiendo.
     */
    @Transaction
    open suspend fun replaceCatalogBundle(
        practices: List<PracticeEntity>,
        segments: List<PracticeSegmentEntity>,
        mantraConfigs: List<MantraConfigEntity>,
        tags: List<PracticeTagEntity>,
        jewels: List<PracticeJewelFocusEntity>,
    ) {
        val ids = practices.map { it.id }
        upsertPractices(practices)
        archiveAllExcept(ids)
        deleteSegmentsFor(ids)
        insertSegments(segments)
        deleteMantraConfigsFor(ids)
        insertMantraConfigs(mantraConfigs)
        deleteTagsFor(ids)
        insertTags(tags)
        deleteJewelsFor(ids)
        insertJewels(jewels)
    }

    @Upsert
    abstract suspend fun upsertPractices(practices: List<PracticeEntity>)

    @Query("UPDATE practices SET is_archived = 1 WHERE id NOT IN (:keepIds)")
    abstract suspend fun archiveAllExcept(keepIds: List<String>)

    @Query("DELETE FROM practice_segments WHERE practice_id IN (:ids)")
    abstract suspend fun deleteSegmentsFor(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSegments(segments: List<PracticeSegmentEntity>)

    @Query("DELETE FROM mantra_configs WHERE practice_id IN (:ids)")
    abstract suspend fun deleteMantraConfigsFor(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertMantraConfigs(configs: List<MantraConfigEntity>)

    @Query("DELETE FROM practice_tags WHERE practice_id IN (:ids)")
    abstract suspend fun deleteTagsFor(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTags(tags: List<PracticeTagEntity>)

    @Query("DELETE FROM practice_jewel_focus WHERE practice_id IN (:ids)")
    abstract suspend fun deleteJewelsFor(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertJewels(jewels: List<PracticeJewelFocusEntity>)
}

@Dao
interface PracticeSessionDao {
    @Transaction
    @Query("SELECT * FROM practice_sessions ORDER BY started_at_epoch_ms DESC LIMIT :limit")
    fun observeRecentSessions(limit: Int = 50): Flow<List<SessionWithPractice>>

    @Query("SELECT * FROM practice_sessions ORDER BY started_at_epoch_ms DESC LIMIT 1")
    suspend fun getLatestSession(): PracticeSessionEntity?

    @Query("SELECT * FROM practice_sessions WHERE practice_id = :practiceId ORDER BY started_at_epoch_ms DESC LIMIT 1")
    suspend fun getLatestSessionForPractice(practiceId: String): PracticeSessionEntity?

    @Query("SELECT COALESCE(SUM(completed_repetitions), 0) FROM practice_sessions WHERE started_at_epoch_ms >= :sinceEpochMs")
    fun observeRepetitionsSince(sinceEpochMs: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM practice_sessions WHERE completed_full = 1 AND started_at_epoch_ms >= :sinceEpochMs")
    fun observeCompletedSessionsSince(sinceEpochMs: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM practice_sessions WHERE started_at_epoch_ms >= :sinceEpochMs")
    fun observeSessionsCountSince(sinceEpochMs: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: PracticeSessionEntity)

    @Update
    suspend fun updateSession(session: PracticeSessionEntity)

    @Query("UPDATE practice_sessions SET merit_dedicated = 1 WHERE id = :sessionId")
    suspend fun markMeritDedicated(sessionId: String)
}

@Dao
interface MeritAndCalmDao {
    @Query("SELECT * FROM merit_and_calm_events ORDER BY created_at_epoch_ms DESC LIMIT :limit")
    fun observeEvents(limit: Int = 100): Flow<List<MeritAndCalmEventEntity>>

    @Query("SELECT * FROM merit_and_calm_events WHERE event_type = :eventType ORDER BY created_at_epoch_ms DESC")
    fun observeEventsByType(eventType: String): Flow<List<MeritAndCalmEventEntity>>

    @Query("SELECT COUNT(*) FROM merit_and_calm_events WHERE event_type = :eventType AND created_at_epoch_ms >= :sinceEpochMs")
    fun observeEventCountSince(eventType: String, sinceEpochMs: Long): Flow<Int>

    @Query("SELECT * FROM merit_and_calm_events WHERE created_at_epoch_ms >= :sinceEpochMs ORDER BY created_at_epoch_ms DESC")
    fun observeEventsSince(sinceEpochMs: Long): Flow<List<MeritAndCalmEventEntity>>

    @Query("SELECT COALESCE(SUM(avoided_amount_estimate), 0.0) FROM merit_and_calm_events WHERE created_at_epoch_ms >= :sinceEpochMs")
    fun observeAvoidedAmountSince(sinceEpochMs: Long): Flow<Double>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvent(event: MeritAndCalmEventEntity)
}

@Dao
interface MindfulAppTargetDao {
    @Query("SELECT * FROM mindful_app_targets ORDER BY display_name ASC")
    fun observeTargets(): Flow<List<MindfulAppTargetEntity>>

    @Query("SELECT * FROM mindful_app_targets WHERE prompt_enabled = 1")
    suspend fun getEnabledTargetsOnce(): List<MindfulAppTargetEntity>

    @Query("SELECT * FROM mindful_app_targets WHERE package_name = :packageName")
    suspend fun getTargetByPackage(packageName: String): MindfulAppTargetEntity?

    @Upsert
    suspend fun upsertTarget(target: MindfulAppTargetEntity)

    @Upsert
    suspend fun upsertTargets(targets: List<MindfulAppTargetEntity>)

    @Query("UPDATE mindful_app_targets SET prompt_enabled = :enabled, cooldown_minutes = :cooldownMinutes WHERE package_name = :packageName")
    suspend fun updateTargetRule(packageName: String, enabled: Boolean, cooldownMinutes: Int)

    @Query("DELETE FROM mindful_app_targets WHERE package_name = :packageName")
    suspend fun deleteTarget(packageName: String)
}
