package com.myhealth.domain.model

/**
 * Domain enums (PLAN §2.1). Stored in Room as `TEXT` (`name()`); the Room `Converters` decode
 * unknown/forward-incompatible strings to the enum's last-resort member (usually `UNKNOWN` /
 * `OTHER`) and log a warning instead of crashing — see `data/db/converter/Converters.kt` (P1.2).
 */

enum class SportType {
    SOCCER_MATCH,
    SOCCER_TRAINING,
    RUN_OUTDOOR,
    RUN_TREADMILL,
    RUN_TRACK,
    RUN_TRAIL,
    WALK,
    HIKE,
    CYCLING,
    CYCLING_INDOOR,
    STRENGTH,
    HIIT,
    MOBILITY,
    SWIM,
    ROWING,
    OTHER,
    UNKNOWN;

    /** Used by the activity de-dup bucket (§2.4) and by the load/suggestion engines (§3.2, §3.5). */
    val group: SportGroup
        get() = when (this) {
            SOCCER_MATCH, SOCCER_TRAINING -> SportGroup.SOCCER
            RUN_OUTDOOR, RUN_TREADMILL, RUN_TRACK, RUN_TRAIL -> SportGroup.RUN
            STRENGTH, HIIT -> SportGroup.STRENGTH
            CYCLING, CYCLING_INDOOR -> SportGroup.CYCLE
            WALK, HIKE -> SportGroup.WALK
            SWIM -> SportGroup.SWIM
            ROWING, MOBILITY, OTHER, UNKNOWN -> SportGroup.OTHER
        }
}

enum class SportGroup { SOCCER, RUN, STRENGTH, CYCLE, WALK, SWIM, OTHER }

enum class ActivitySource { HEALTH_CONNECT, FIT_IMPORT, CSV_IMPORT, GARMIN_API, MANUAL }

enum class EventType { SOCCER_MATCH, SOCCER_TRAINING, RACE, APPOINTMENT, BLOCKED, NOTE, OTHER }

enum class MealSlot {
    BREAKFAST,
    MORNING_SNACK,
    LUNCH,
    AFTERNOON_SNACK,
    DINNER,
    EVENING_SNACK,
    PRE_WORKOUT,
    POST_WORKOUT,
}

/** How an ingredient's nutrition values are expressed. */
enum class MeasureBasis { PER_100G, PER_100ML, PER_PIECE }

enum class QuantityUnit { G, ML, PIECE, SERVING }

enum class Sex { MALE, FEMALE, OTHER }

/** Non-exercise activity thermogenesis multiplier used by the nutrition target engine (§3.1.3). */
enum class NeatLevel(val factor: Double) {
    DESK(1.25),
    LIGHT_ACTIVE(1.35),
    ACTIVE(1.45),
    PHYSICAL_JOB(1.60),
}

enum class Intensity { RECOVERY, LOW, MODERATE, HIGH, MAX }

enum class SessionType {
    EASY_RUN,
    LONG_RUN,
    TEMPO_RUN,
    INTERVAL_RUN,
    RECOVERY_RUN,
    STRENGTH_FULL,
    STRENGTH_UPPER,
    STRENGTH_LOWER,
    SOCCER_TRAINING,
    SOCCER_MATCH,
    MOBILITY,
    CROSS_TRAINING,
    REST,

    // P12 cycling (appended so the ordinals of every member above stay put).
    /** Steady aerobic ride, outdoors or on the trainer. */
    ENDURANCE_RIDE,
    /** Hard bike session: threshold / VO2 intervals. */
    BIKE_INTERVALS,
    /** A structured indoor trainer session. */
    TRAINER_SESSION,
    /** Very easy spin for active recovery. */
    RECOVERY_SPIN,
}

enum class GoalType {
    RACE_TIME,
    BODY_WEIGHT,
    STRENGTH_LIFT,
    CONSISTENCY,
    SOCCER_AVAILABILITY,

    // P12 cycling (appended; ordinals of the members above are unchanged).
    /** Functional threshold power target; `targetValue` is watts. */
    BIKE_FTP,
    /** Weekly riding volume; `targetValue` is hours per week. */
    BIKE_VOLUME,
    /** A cycling event: `targetDistanceMeters`, optional `targetTimeSec`, optional `targetDay`. */
    BIKE_EVENT,
}

enum class GoalStatus { ACTIVE, ACHIEVED, ABANDONED, EXPIRED }

enum class TrainingPhase {
    BASE,
    BUILD,
    PEAK,
    TAPER,
    RACE_WEEK,
    IN_SEASON,
    OFF_SEASON,
    RECOVERY_WEEK,
}

enum class RecoveryBand { FRESH, GOOD, MODERATE, FATIGUED, STRAINED }

enum class LinkMethod { MANUAL, AUTO_TIME_OVERLAP, AUTO_ACCEPTED }

enum class SleepStage { UNKNOWN, AWAKE, AWAKE_IN_BED, OUT_OF_BED, SLEEPING, LIGHT, DEEP, REM }

/** Computed per day, never stored except in the nutrition target snapshot (§2.2.5). */
enum class DayType {
    REST,
    TRAINING,
    HARD_TRAINING,
    MATCH_DAY,
    PRE_MATCH,
    RACE_DAY,
    PRE_RACE,
    RECOVERY,
}

enum class LoadMethod {
    HR_SAMPLES,
    HR_AVERAGE,
    RPE_ESTIMATE,
    DURATION_ONLY,

    /** P12: TSS from cycling power and an FTP estimate (§3.2, appended to keep the ordinals). */
    POWER_TSS,
}

/**
 * The kinds of ride best kept in `ride_best` (P12, §2.2.6).
 *
 * `POWER_*` values are watts and the PR is the **maximum**; `TIME_*` values are seconds over that
 * distance and the PR is the **minimum**. The two families are deliberately one enum because they
 * share a table, a repository and a screen; consumers branch on [isPower].
 */
enum class RideBestKind {
    POWER_5MIN,
    POWER_20MIN,
    POWER_60MIN,
    TIME_10K,
    TIME_20K,
    TIME_40K,
    TIME_100K,
    ;

    /** True for the `POWER_*` members, whose PR is `MAX(value)` rather than `MIN(value)`. */
    val isPower: Boolean get() = name.startsWith("POWER_")

    /** Seconds of the power window for a `POWER_*` kind, `null` for a `TIME_*` kind. */
    val windowSec: Int?
        get() = when (this) {
            POWER_5MIN -> 300
            POWER_20MIN -> 1_200
            POWER_60MIN -> 3_600
            else -> null
        }

    /** Metres of the distance for a `TIME_*` kind, `null` for a `POWER_*` kind. */
    val distanceMeters: Double?
        get() = when (this) {
            TIME_10K -> 10_000.0
            TIME_20K -> 20_000.0
            TIME_40K -> 40_000.0
            TIME_100K -> 100_000.0
            else -> null
        }
}

enum class ImportKind { FIT_FILE, GARMIN_CSV, GARMIN_ZIP, JSON_BACKUP }

enum class PlanStatus { DRAFT, ACTIVE, ARCHIVED }

enum class PlannedStatus { PLANNED, COMPLETED, SKIPPED, MOVED }

enum class SuggestionStatus { PROPOSED, ACCEPTED, REJECTED, SUPERSEDED }

enum class EngineWarningCode {
    MISSING_WEIGHT,
    MISSING_HR,
    ESTIMATED_LOAD,
    INSUFFICIENT_HISTORY,
    CLAMPED_TO_FLOOR,
    CLAMPED_TO_CEILING,
    ENERGY_MISMATCH,
    IMPLAUSIBLE_VALUE,
    COLUMN_AMBIGUOUS,
    NO_NUTRIENTS_FOUND,
    LOW_CONFIDENCE,
}
