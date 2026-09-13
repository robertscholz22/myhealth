package com.myhealth.data.repository

import com.myhealth.data.db.dao.StrengthDao
import com.myhealth.data.mapper.toDomain
import com.myhealth.data.mapper.toEntity
import com.myhealth.domain.model.StrengthSetLog
import com.myhealth.domain.model.StrengthWorkout
import com.myhealth.domain.repository.StrengthRepository
import com.myhealth.domain.util.AppError
import com.myhealth.domain.util.Outcome
import com.myhealth.domain.util.runCatchingApp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room-backed [StrengthRepository] over the three tables of §2.2.7 (P14.1).
 *
 * [upsertWorkout] writes the header, then **replaces** the child rows: the editor always hands
 * over the full ordered list, so deleting and re-inserting is what keeps `uq_swe_order` satisfied
 * at every instant (a diff would have to move rows through positions that are still taken). The
 * `orderIndex` of each row is taken from its position in the list, not from the row itself, so a
 * reorder in the UI cannot leave a hole — the compaction P14.4 asks for falls out of that, on a
 * delete as much as on a move.
 *
 * [upsertWorkout] is also where §2.2.7's one rule SQLite cannot express is enforced: a row
 * prescribes **exactly one** of `reps` / `seconds`, and at least one set. A violation is a
 * [AppError.Validation], not an exception — the editor shows it on the offending field.
 */
class RoomStrengthRepository(
    private val dao: StrengthDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StrengthRepository {

    override fun observeAll(): Flow<List<StrengthWorkout>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observe(id: Long): Flow<StrengthWorkout?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun getById(id: Long): StrengthWorkout? =
        withContext(ioDispatcher) { dao.getById(id)?.toDomain() }

    override suspend fun getByTemplateId(templateId: String): StrengthWorkout? =
        withContext(ioDispatcher) { dao.getByTemplateId(templateId)?.toDomain() }

    override suspend fun upsertWorkout(workout: StrengthWorkout): Outcome<Long> =
        withContext(ioDispatcher) {
            workout.validationError()?.let { return@withContext Outcome.Err(it) }
            runCatchingApp {
                val id = dao.upsertWorkout(workout.toEntity())
                    .let { if (it > 0L) it else workout.id }
                dao.deleteExercisesOf(id)
                if (workout.exercises.isNotEmpty()) {
                    dao.upsertExercises(
                        workout.exercises.mapIndexed { index, exercise ->
                            exercise.copy(id = 0L, orderIndex = index).toEntity(workoutId = id)
                        },
                    )
                }
                id
            }
        }

    override suspend fun deleteWorkout(id: Long): Outcome<Unit> = withContext(ioDispatcher) {
        runCatchingApp { dao.deleteWorkout(id) }
    }

    override suspend fun insertSetLogs(logs: List<StrengthSetLog>): Outcome<Unit> =
        withContext(ioDispatcher) {
            runCatchingApp {
                if (logs.isNotEmpty()) dao.insertSetLogs(logs.map { it.copy(id = 0L).toEntity() })
                Unit
            }
        }

    override fun observeSetLogsByDay(day: Long): Flow<List<StrengthSetLog>> =
        dao.observeSetLogsByDay(day).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getSetLogsOfPlannedSession(plannedSessionId: Long): List<StrengthSetLog> =
        withContext(ioDispatcher) {
            dao.getSetLogsOfPlannedSession(plannedSessionId).map { it.toDomain() }
        }

    override suspend fun deleteSetLog(id: Long): Outcome<Unit> = withContext(ioDispatcher) {
        runCatchingApp { dao.deleteSetLog(id) }
    }

    /** The first broken row's error, or `null` when the whole workout is well formed. */
    private fun StrengthWorkout.validationError(): AppError.Validation? {
        if (name.isBlank()) return AppError.Validation("name", "A workout needs a name.")
        exercises.forEachIndexed { index, row ->
            val position = index + 1
            if (row.sets < 1) {
                return AppError.Validation("sets", "Exercise $position needs at least one set.")
            }
            val counted = row.reps != null
            val held = row.seconds != null
            if (counted == held) {
                return AppError.Validation(
                    "reps",
                    "Exercise $position needs either a rep count or a hold in seconds, not both.",
                )
            }
        }
        return null
    }
}
