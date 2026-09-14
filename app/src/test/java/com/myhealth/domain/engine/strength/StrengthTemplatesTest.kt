package com.myhealth.domain.engine.strength

import com.google.common.truth.Truth.assertThat
import com.myhealth.data.repository.StrengthWorkoutSeeder
import com.myhealth.domain.model.Exercise
import com.myhealth.domain.model.ExercisePrescription
import com.myhealth.domain.model.ExerciseProgress
import com.myhealth.domain.model.MuscleGroup
import com.myhealth.domain.model.StrengthSetLog
import com.myhealth.domain.model.StrengthWorkout
import com.myhealth.domain.model.StrengthWorkoutKind
import com.myhealth.domain.repository.StrengthRepository
import com.myhealth.domain.util.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The six built-in workouts and the seeder that materialises them (PLAN §3.12.3, `sw01`…`sw05`). */
class StrengthTemplatesTest {

    private val lowerBodyGroups = MuscleGroup.entries.filter { it.isLowerBody }.toSet()

    @Test
    fun sw01_six_builtin_templates() {
        assertThat(StrengthTemplates.ALL.map { it.templateId })
            .containsExactly("UPPER_A", "UPPER_B", "LOWER_A", "LOWER_B", "FULL_A", "CORE_A")
            .inOrder()
        assertThat(StrengthTemplates.ALL.all { it.isBuiltIn }).isTrue()
        assertThat(StrengthTemplates.ALL.all { it.id == 0L }).isTrue()
        assertThat(StrengthTemplates.ALL.map { it.kind }).containsExactly(
            StrengthWorkoutKind.UPPER,
            StrengthWorkoutKind.UPPER,
            StrengthWorkoutKind.LOWER,
            StrengthWorkoutKind.LOWER,
            StrengthWorkoutKind.FULL,
            StrengthWorkoutKind.CORE,
        ).inOrder()
        StrengthTemplates.ALL.forEach { workout ->
            assertThat(workout.exercises.size).isAtLeast(5)
            assertThat(workout.exercises.size).isAtMost(7)
            assertThat(workout.exercises.map { it.orderIndex })
                .isEqualTo(workout.exercises.indices.toList())
            workout.exercises.forEach { row ->
                assertThat(ExerciseCatalog.byId(row.exerciseId)).isNotNull()
            }
        }
        assertThat(StrengthTemplates.byId("LOWER_B")).isEqualTo(StrengthTemplates.LOWER_B)
        assertThat(StrengthTemplates.byId("NOPE")).isNull()
        assertThat(StrengthTemplates.ofKind(StrengthWorkoutKind.UPPER))
            .containsExactly(StrengthTemplates.UPPER_A, StrengthTemplates.UPPER_B)
    }

    @Test
    fun sw02_upper_template_has_no_lower_primary() {
        listOf(StrengthTemplates.UPPER_A, StrengthTemplates.UPPER_B).forEach { workout ->
            val offenders = workout.exercises
                .map { requireNotNull(ExerciseCatalog.byId(it.exerciseId)) }
                .filter { (it.primary intersect lowerBodyGroups).isNotEmpty() }
                .map { it.id }
            assertThat(offenders).isEmpty()
        }
    }

    @Test
    fun sw03_lower_template_has_no_upper_primary() {
        listOf(StrengthTemplates.LOWER_A, StrengthTemplates.LOWER_B).forEach { workout ->
            val offenders = workout.exercises
                .map { requireNotNull(ExerciseCatalog.byId(it.exerciseId)) }
                .filterNot { lowerBodyGroups.containsAll(it.primary) }
                .map { it.id }
            assertThat(offenders).isEmpty()
        }
    }

    @Test
    fun sw04_estimated_minutes_upper_a_is_44() {
        val upperA = StrengthTemplates.UPPER_A
        assertThat(upperA.exercises).hasSize(6)
        assertThat(upperA.exercises.map { it.sets }.toSet()).containsExactly(3)
        assertThat(upperA.exercises.map { it.reps }.toSet()).containsExactly(10)
        assertThat(upperA.exercises.map { it.restSec }.toSet()).containsExactly(90)
        // 6 × 3 × (10 × 3 s + 90 s) + 480 s = 2640 s → 44 min.
        assertThat(upperA.estimatedMinutes).isEqualTo(44)
        // The 480 s overhead alone rounds up to a 8-minute workout with no exercises at all.
        assertThat(upperA.copy(exercises = emptyList()).estimatedMinutes).isEqualTo(8)
        // A hold counts its own seconds, not reps × 3.
        assertThat(StrengthTemplates.CORE_A.estimatedMinutes).isEqualTo(
            EXPECTED_CORE_A_MINUTES,
        )
    }

    @Test
    fun sw05_seeder_is_idempotent() = runTest {
        val repo = InMemoryStrengthRepository()
        val seeder = StrengthWorkoutSeeder(repo, FIXED_CLOCK)

        assertThat(seeder.seed()).isEqualTo(6)
        assertThat(repo.stored.value).hasSize(6)

        assertThat(seeder.seed()).isEqualTo(0)
        assertThat(repo.stored.value).hasSize(6)
        assertThat(repo.stored.value.values.map { it.templateId })
            .containsExactly("UPPER_A", "UPPER_B", "LOWER_A", "LOWER_B", "FULL_A", "CORE_A")

        val upperA = requireNotNull(repo.getByTemplateId("UPPER_A"))
        assertThat(upperA.id).isGreaterThan(0L)
        assertThat(upperA.isBuiltIn).isTrue()
        assertThat(upperA.createdAtMillis).isEqualTo(FIXED_MILLIS)
        assertThat(upperA.exercises).hasSize(6)

        // A user edit to a seeded built-in survives the next seed.
        repo.upsertWorkout(upperA.copy(name = "Upper A (mine)"))
        assertThat(seeder.seed()).isEqualTo(0)
        assertThat(requireNotNull(repo.getByTemplateId("UPPER_A")).name).isEqualTo("Upper A (mine)")
        assertThat(repo.stored.value).hasSize(6)
    }

    /** In-memory [StrengthRepository] — only what the seeder touches is modelled. */
    private class InMemoryStrengthRepository : StrengthRepository {
        val stored = MutableStateFlow<Map<Long, StrengthWorkout>>(emptyMap())
        private var nextId = 1L

        val progress: MutableMap<String, ExerciseProgress> = mutableMapOf()

        override fun observeAll(): Flow<List<StrengthWorkout>> =
            stored.map { all -> all.values.sortedBy { it.name } }

        override fun observe(id: Long): Flow<StrengthWorkout?> = stored.map { it[id] }

        override suspend fun getById(id: Long): StrengthWorkout? = stored.value[id]

        override suspend fun getByTemplateId(templateId: String): StrengthWorkout? =
            stored.value.values.firstOrNull { it.templateId == templateId }

        override suspend fun upsertWorkout(workout: StrengthWorkout): Outcome<Long> {
            val id = if (workout.id == 0L) nextId++ else workout.id
            stored.value = stored.value + (id to workout.copy(id = id))
            return Outcome.Ok(id)
        }

        override suspend fun deleteWorkout(id: Long): Outcome<Unit> {
            stored.value = stored.value - id
            return Outcome.Ok(Unit)
        }

        override suspend fun insertSetLogs(logs: List<StrengthSetLog>): Outcome<Unit> =
            Outcome.Ok(Unit)

        override fun observeSetLogsByDay(day: Long): Flow<List<StrengthSetLog>> =
            MutableStateFlow(emptyList())

        override suspend fun getSetLogsOfPlannedSession(plannedSessionId: Long): List<StrengthSetLog> =
            emptyList()

        override suspend fun deleteSetLog(id: Long): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun getRecentSetLogs(exerciseId: String, limit: Int): List<StrengthSetLog> =
            emptyList()

        override suspend fun saveSetLogs(
            logs: List<StrengthSetLog>,
        ): Outcome<Map<String, ExerciseProgress>> = Outcome.Ok(emptyMap())

        override fun observeProgress(exerciseId: String): Flow<ExerciseProgress?> =
            MutableStateFlow(progress[exerciseId])

        override suspend fun getProgress(exerciseId: String): ExerciseProgress? = progress[exerciseId]

        override suspend fun getAllProgress(): List<ExerciseProgress> = progress.values.toList()

        override suspend fun upsertProgress(progress: ExerciseProgress): Outcome<Unit> {
            this.progress[progress.exerciseId] = progress
            return Outcome.Ok(Unit)
        }

        override suspend fun prescriptionFor(
            exercise: Exercise,
            bodyWeightKg: Double,
        ): ExercisePrescription =
            ProgressionEngine.prescription(exercise, progress[exercise.id], bodyWeightKg)
    }

    private companion object {
        const val FIXED_MILLIS = 1_757_000_000_000L
        val FIXED_CLOCK: Clock = Clock.fixed(Instant.ofEpochMilli(FIXED_MILLIS), ZoneOffset.UTC)

        /**
         * `CORE_A`, all rests 60 s: 3 × (60 + 45 + 30 + 30 + 30 + 30 + 36) s of work +
         * 21 × 60 s of rest = 2043 s, + 480 s overhead = 2523 s → ceil(42.05) = 43 min.
         */
        const val EXPECTED_CORE_A_MINUTES = 43
    }
}
