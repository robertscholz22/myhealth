package com.myhealth.domain.repository

import com.myhealth.domain.model.StrengthSetLog
import com.myhealth.domain.model.StrengthWorkout
import com.myhealth.domain.util.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * The three strength tables (PLAN §2.2.7, P14) behind one interface: workouts with their ordered
 * exercises, and the optional per-set log.
 *
 * A workout is always read and written **whole** — [upsertWorkout] takes the header plus the
 * complete ordered exercise list and returns the workout's id — because a half-written workout is
 * never a thing the user asked for, and `uq_swe_order` would reject a partial reorder anyway.
 */
interface StrengthRepository {

    fun observeAll(): Flow<List<StrengthWorkout>>

    fun observe(id: Long): Flow<StrengthWorkout?>

    suspend fun getById(id: Long): StrengthWorkout?

    /** The materialised row of a built-in template, if it has been seeded yet (P14.4). */
    suspend fun getByTemplateId(templateId: String): StrengthWorkout?

    /**
     * Inserts or updates the header and rewrites its rows from [StrengthWorkout.exercises], in the
     * order given. Returns the workout's id — new when [StrengthWorkout.id] was `0`.
     */
    suspend fun upsertWorkout(workout: StrengthWorkout): Outcome<Long>

    /** Deletes the workout; its exercise rows go with it (`CASCADE`), its set logs do not. */
    suspend fun deleteWorkout(id: Long): Outcome<Unit>

    suspend fun insertSetLogs(logs: List<StrengthSetLog>): Outcome<Unit>

    fun observeSetLogsByDay(day: Long): Flow<List<StrengthSetLog>>

    suspend fun getSetLogsOfPlannedSession(plannedSessionId: Long): List<StrengthSetLog>

    suspend fun deleteSetLog(id: Long): Outcome<Unit>
}
