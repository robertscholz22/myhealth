package com.myhealth.domain.model

/** Mirrors `training_plan` (PLAN §2.2.4). At most one `ACTIVE` plan (enforced in the repository). */
data class TrainingPlan(
    val id: Long,
    val name: String,
    val startDay: Long,
    val endDay: Long,
    val status: PlanStatus,
    val primaryGoalId: Long?,
    val notes: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

/** Mirrors `planned_session` (§2.2.4). */
data class PlannedSession(
    val id: Long,
    val planId: Long?,
    val day: Long,
    val startMinuteOfDay: Int?,
    val sportType: SportType,
    val sessionType: SessionType,
    val intensity: Intensity,
    val targetDurationMin: Int?,
    val targetDistanceMeters: Double?,
    val targetPaceSecPerKm: Int?,
    val estimatedTrimp: Double?,
    val description: String?,
    val rationale: String?,
    val status: PlannedStatus,
    /** User-pinned; the suggester must not move it (C6 in §3.5.3). */
    val locked: Boolean,
    val linkedActivityId: Long?,
    val sourceSuggestionId: Long?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

/** One `{ruleId, text}` entry of a suggested session's rationale (§3.5.6 step 8). */
data class RationaleEntry(val ruleId: String, val text: String)

/** Mirrors `suggested_session` — output of the suggestion engine (§2.2.4). */
data class SuggestedSession(
    val id: Long,
    val batchId: Long,
    val day: Long,
    val sportType: SportType,
    val sessionType: SessionType,
    val intensity: Intensity,
    val targetDurationMin: Int?,
    val targetDistanceMeters: Double?,
    val estimatedTrimp: Double,
    val score: Double,
    val rationale: List<RationaleEntry>,
    val status: SuggestionStatus,
)

/** Mirrors `suggestion_batch` (§2.2.4). */
data class SuggestionBatch(
    val id: Long,
    val generatedAtMillis: Long,
    val horizonStartDay: Long,
    val horizonEndDay: Long,
    val phase: TrainingPhase,
    val weeklyLoadTarget: Double,
    val inputsHash: String,
    val status: SuggestionStatus,
)

/** Mirrors `goal` (§2.2.4). */
data class Goal(
    val id: Long,
    val type: GoalType,
    val title: String,
    val targetDay: Long?,
    val targetDistanceMeters: Double?,
    val targetTimeSec: Int?,
    val targetWeightKg: Double?,
    val targetValue: Double?,
    /** 1 = primary. */
    val priority: Int,
    val status: GoalStatus,
    val linkedEventId: Long?,
    val notes: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
