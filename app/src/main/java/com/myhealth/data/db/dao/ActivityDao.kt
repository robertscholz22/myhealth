package com.myhealth.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.myhealth.data.db.entity.ActivityLapEntity
import com.myhealth.data.db.entity.ActivitySessionEntity
import com.myhealth.data.db.entity.ActivitySourceRecordEntity
import com.myhealth.data.db.entity.ActivityStreamEntity
import com.myhealth.data.db.relation.ActivityWithStream
import com.myhealth.domain.model.ActivitySource
import com.myhealth.domain.model.SportGroup
import kotlinx.coroutines.flow.Flow

/** One day's summed TRIMP — projection for `ActivityDao.sumTrimpPerDay` (PLAN §3.2.3). */
data class DayTrimp(val day: Long, val trimp: Double)

/**
 * DAO for `activity_session` and its source records, streams and laps (PLAN §2.2.2).
 * `getByBuckets` is the candidate lookup of the de-dup algorithm (§2.4): callers pass buckets
 * `n-1, n, n+1` and then apply `ActivityMatcher.matches`.
 */
@Dao
interface ActivityDao {

    @Upsert
    suspend fun upsert(entity: ActivitySessionEntity): Long

    @Upsert
    suspend fun upsertAll(entities: List<ActivitySessionEntity>)

    @Query("SELECT * FROM activity_session WHERE id = :id")
    suspend fun getById(id: Long): ActivitySessionEntity?

    @Query("DELETE FROM activity_session WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        "SELECT * FROM activity_session WHERE day BETWEEN :fromDay AND :toDay " +
            "ORDER BY startAtMillis ASC",
    )
    fun observeRange(fromDay: Long, toDay: Long): Flow<List<ActivitySessionEntity>>

    @Query("SELECT * FROM activity_session WHERE dedupeBucket IN (:buckets)")
    suspend fun getByBuckets(buckets: List<String>): List<ActivitySessionEntity>

    @Query("SELECT * FROM activity_session WHERE day = :day ORDER BY startAtMillis ASC")
    suspend fun getByDay(day: Long): List<ActivitySessionEntity>

    @Query("SELECT * FROM activity_session ORDER BY startAtMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ActivitySessionEntity>>

    @Query(
        "SELECT * FROM activity_session WHERE sportGroup = :group AND day >= :fromDay " +
            "ORDER BY startAtMillis DESC",
    )
    fun observeBySportGroup(group: SportGroup, fromDay: Long): Flow<List<ActivitySessionEntity>>

    /** Earliest `activity_session.day` across all activities, or `null` when there are none
     * (POLISH-13): the load series must never start before this day. */
    @Query("SELECT MIN(day) FROM activity_session")
    suspend fun getFirstActivityDay(): Long?

    /** Daily TRIMP totals feeding the ATL/CTL series (§3.2.3). Days without activities are absent. */
    @Query(
        "SELECT day AS day, SUM(trimp) AS trimp FROM activity_session " +
            "WHERE day BETWEEN :fromDay AND :toDay AND trimp IS NOT NULL " +
            "GROUP BY day ORDER BY day ASC",
    )
    fun sumTrimpPerDay(fromDay: Long, toDay: Long): Flow<List<DayTrimp>>

    @Transaction
    @Query("SELECT * FROM activity_session WHERE id = :id")
    suspend fun getFullById(id: Long): ActivityWithStream?

    @Transaction
    @Query("SELECT * FROM activity_session WHERE id = :id")
    fun observeFullById(id: Long): Flow<ActivityWithStream?>

    /** Range read including streams and laps — the load recompute worker needs the HR series. */
    @Transaction
    @Query(
        "SELECT * FROM activity_session WHERE day BETWEEN :fromDay AND :toDay " +
            "ORDER BY startAtMillis ASC",
    )
    suspend fun getRangeFull(fromDay: Long, toDay: Long): List<ActivityWithStream>

    // ---- source records ----------------------------------------------------------------------

    @Upsert
    suspend fun upsertSourceRecord(entity: ActivitySourceRecordEntity): Long

    /**
     * The idempotent arrival path (§2.4): `(source, externalId)` is unique, so a record that is
     * already known is ignored and `-1` is returned instead of raising a constraint violation.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSourceRecordIgnoring(entity: ActivitySourceRecordEntity): Long

    /** Re-arrival of a known record: the payload may have changed, so it is refreshed in place. */
    @Query(
        "UPDATE activity_source_record SET activityId = :activityId, payloadJson = :payloadJson, " +
            "receivedAtMillis = :receivedAtMillis, importRecordId = :importRecordId " +
            "WHERE source = :source AND externalId = :externalId",
    )
    suspend fun updateSourceRecord(
        source: ActivitySource,
        externalId: String,
        payloadJson: String,
        activityId: Long?,
        receivedAtMillis: Long,
        importRecordId: Long?,
    )

    @Query("DELETE FROM activity_source_record WHERE source = :source AND externalId = :externalId")
    suspend fun deleteSourceRecord(source: ActivitySource, externalId: String)

    @Query("SELECT * FROM activity_source_record WHERE source = :source AND externalId = :externalId")
    suspend fun getSourceRecord(source: ActivitySource, externalId: String): ActivitySourceRecordEntity?

    @Query("SELECT * FROM activity_source_record WHERE activityId = :activityId")
    suspend fun getSourceRecordsFor(activityId: Long): List<ActivitySourceRecordEntity>

    /** Everything one import wrote — the selection "Undo import" removes (§2.2.6). */
    @Query("SELECT * FROM activity_source_record WHERE importRecordId = :importRecordId")
    suspend fun getSourceRecordsOfImport(importRecordId: Long): List<ActivitySourceRecordEntity>

    // ---- streams and laps --------------------------------------------------------------------

    @Upsert
    suspend fun upsertStream(entity: ActivityStreamEntity)

    @Query("SELECT * FROM activity_stream WHERE activityId = :activityId")
    suspend fun getStream(activityId: Long): ActivityStreamEntity?

    @Upsert
    suspend fun upsertLaps(laps: List<ActivityLapEntity>)

    @Query("SELECT * FROM activity_lap WHERE activityId = :activityId ORDER BY lapIndex ASC")
    suspend fun getLaps(activityId: Long): List<ActivityLapEntity>

    @Query("DELETE FROM activity_lap WHERE activityId = :activityId")
    suspend fun deleteLaps(activityId: Long)
}
