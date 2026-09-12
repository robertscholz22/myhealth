package com.myhealth.data.healthconnect

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.myhealth.domain.model.SportType

/**
 * `ExerciseSessionRecord.exerciseType` → [SportType] (PLAN P2.3).
 *
 * Only the `EXERCISE_TYPE_*` **names** are referenced, never their integer values (risk R3): they
 * are `const val`s, so a rename or removal in a future connect-client is a compile error rather
 * than a silent mis-mapping. Anything not in the table becomes [SportType.OTHER] — Health Connect
 * exercise types are coarse and a wrong guess would poison the load engine.
 *
 * Health Connect 1.1.0 has no soccer-vs-match distinction, so a soccer session is
 * [SportType.SOCCER_TRAINING] unless its title says otherwise; event linking (P3.3) can still
 * upgrade it later.
 */
object ExerciseTypeMap {

    /** Title words that turn a soccer session into a match — English and German. */
    private val MATCH_WORDS = listOf("match", "spiel")

    fun toSportType(exerciseType: Int, title: String? = null): SportType = when (exerciseType) {
        ExerciseSessionRecord.EXERCISE_TYPE_SOCCER ->
            if (isMatchTitle(title)) SportType.SOCCER_MATCH else SportType.SOCCER_TRAINING

        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> SportType.RUN_OUTDOOR
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> SportType.RUN_TREADMILL

        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
        -> SportType.STRENGTH

        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> SportType.CYCLING
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> SportType.CYCLING_INDOOR

        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> SportType.WALK
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> SportType.HIKE

        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
        -> SportType.SWIM

        ExerciseSessionRecord.EXERCISE_TYPE_ROWING,
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE,
        -> SportType.ROWING

        ExerciseSessionRecord.EXERCISE_TYPE_YOGA,
        ExerciseSessionRecord.EXERCISE_TYPE_PILATES,
        ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING,
        -> SportType.MOBILITY

        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> SportType.HIIT

        else -> SportType.OTHER
    }

    /** True when [title] contains "match" or "spiel", case- and position-insensitive. */
    fun isMatchTitle(title: String?): Boolean {
        val lower = title?.lowercase() ?: return false
        return MATCH_WORDS.any { lower.contains(it) }
    }
}
