package com.myhealth.ui.goals

import com.myhealth.domain.engine.goal.GoalProgress
import com.myhealth.domain.engine.running.CanonicalDistances
import com.myhealth.domain.model.Goal
import com.myhealth.domain.model.GoalStatus
import com.myhealth.domain.model.GoalType
import java.time.Clock
import java.time.LocalDate

/** Field identity for [validateGoal] errors (mirrors `EventDraft`'s pattern, P3.4). */
enum class GoalField { TITLE, DISTANCE, TIME, DATE, WEIGHT, VALUE }

/**
 * The goal editor's form state (PLAN §4.2 "Goal edit", P6.1). Which fields matter depends on
 * [type]: a race goal needs a distance + time (+ optional date and linked `RACE` event), a weight
 * goal a target weight, a consistency goal a sessions/week number.
 */
data class GoalDraft(
    val id: Long = 0L,
    val type: GoalType = GoalType.RACE_TIME,
    val title: String = "",
    val targetDay: LocalDate? = null,
    /**
     * Pre-set to the 5 km the distance picker displays for a new race goal (BUG-8): the editor
     * showed "5 km" while the draft carried `null`, so "Create goal" failed validation silently
     * until the picker was re-selected. [withType] keeps the two in step on a type change.
     */
    val targetDistanceMeters: Double? = CanonicalDistances.FIVE_KM,
    val targetMinutes: Int? = null,
    val targetSeconds: Int? = null,
    val targetWeightKg: Double? = null,
    val targetValue: Double? = null,
    val isPrimary: Boolean = false,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val linkedEventId: Long? = null,
    val notes: String = "",
    val createdAtMillis: Long = 0L,
) {
    /** The two time fields as one value; `null` when neither was entered. */
    val targetTimeSec: Int?
        get() = if (targetMinutes == null && targetSeconds == null) {
            null
        } else {
            (targetMinutes ?: 0) * 60 + (targetSeconds ?: 0)
        }
}

/**
 * Switches the draft's [GoalDraft.type], restoring the distance picker's default when the new type
 * is `RACE_TIME` and the draft carries no distance (BUG-8).
 */
fun GoalDraft.withType(newType: GoalType): GoalDraft = copy(
    type = newType,
    targetDistanceMeters = if (newType == GoalType.RACE_TIME) {
        targetDistanceMeters ?: CanonicalDistances.FIVE_KM
    } else {
        targetDistanceMeters
    },
)

/** Blocking errors only; an empty map means the draft can be saved. */
fun validateGoal(draft: GoalDraft): Map<GoalField, String> {
    val errors = mutableMapOf<GoalField, String>()
    if (draft.title.isBlank()) errors[GoalField.TITLE] = "Title is required."
    when (draft.type) {
        GoalType.RACE_TIME -> {
            if ((draft.targetDistanceMeters ?: 0.0) <= 0.0) errors[GoalField.DISTANCE] = "Pick a distance."
            val seconds = draft.targetTimeSec
            if (seconds == null || seconds <= 0) errors[GoalField.TIME] = "Enter a target time."
            if ((draft.targetSeconds ?: 0) !in 0..59) errors[GoalField.TIME] = "Seconds must be 0–59."
        }
        GoalType.BODY_WEIGHT ->
            if ((draft.targetWeightKg ?: 0.0) <= 0.0) errors[GoalField.WEIGHT] = "Enter a target weight."
        GoalType.CONSISTENCY ->
            if ((draft.targetValue ?: 0.0) <= 0.0) errors[GoalField.VALUE] = "Enter sessions per week."
        GoalType.STRENGTH_LIFT, GoalType.SOCCER_AVAILABILITY ->
            if ((draft.targetValue ?: 0.0) <= 0.0) errors[GoalField.VALUE] = "Enter a target value."
    }
    return errors
}

/** The domain goal the editor saves; only the fields its [GoalDraft.type] uses are carried over. */
fun GoalDraft.toGoal(clock: Clock): Goal {
    val now = clock.millis()
    val isRace = type == GoalType.RACE_TIME
    return Goal(
        id = id,
        type = type,
        title = title.trim(),
        targetDay = targetDay?.toEpochDay(),
        targetDistanceMeters = targetDistanceMeters.takeIf { isRace },
        targetTimeSec = targetTimeSec.takeIf { isRace },
        targetWeightKg = targetWeightKg.takeIf { type == GoalType.BODY_WEIGHT },
        targetValue = targetValue.takeIf { type != GoalType.RACE_TIME && type != GoalType.BODY_WEIGHT },
        priority = if (isPrimary) 1 else 2,
        status = status,
        linkedEventId = linkedEventId.takeIf { isRace },
        notes = notes.trim().takeIf { it.isNotEmpty() },
        createdAtMillis = if (id == 0L) now else createdAtMillis,
        updatedAtMillis = now,
    )
}

/** Loads an existing goal into a draft. */
fun goalDraftOf(goal: Goal): GoalDraft = GoalDraft(
    id = goal.id,
    type = goal.type,
    title = goal.title,
    targetDay = goal.targetDay?.let { LocalDate.ofEpochDay(it) },
    targetDistanceMeters = goal.targetDistanceMeters,
    targetMinutes = goal.targetTimeSec?.let { it / 60 },
    targetSeconds = goal.targetTimeSec?.let { it % 60 },
    targetWeightKg = goal.targetWeightKg,
    targetValue = goal.targetValue,
    isPrimary = goal.priority <= 1,
    status = goal.status,
    linkedEventId = goal.linkedEventId,
    notes = goal.notes.orEmpty(),
    createdAtMillis = goal.createdAtMillis,
)

/** "Race time", "Body weight", … — the §2.1 enum rendered for a dropdown. */
fun goalTypeLabel(type: GoalType): String = when (type) {
    GoalType.RACE_TIME -> "Race time"
    GoalType.BODY_WEIGHT -> "Body weight"
    GoalType.STRENGTH_LIFT -> "Strength lift"
    GoalType.CONSISTENCY -> "Consistency"
    GoalType.SOCCER_AVAILABILITY -> "Soccer availability"
}

fun goalStatusLabel(status: GoalStatus): String = when (status) {
    GoalStatus.ACTIVE -> "Active"
    GoalStatus.ACHIEVED -> "Achieved"
    GoalStatus.ABANDONED -> "Abandoned"
    GoalStatus.EXPIRED -> "Expired"
}

/**
 * The one-line summary the Goals list shows under the title, e.g. `5 km in 20:00 by 2026-11-15`
 * (§4.2 "Goals": "5k 20:00 by 15 Nov — current best 21:14, on track/behind").
 */
fun goalHeadline(goal: Goal): String {
    val by = goal.targetDay?.let { " by ${LocalDate.ofEpochDay(it)}" } ?: ""
    return when (goal.type) {
        GoalType.RACE_TIME -> {
            val distance = goal.targetDistanceMeters?.let { GoalProgress.distanceLabel(it) } ?: "race"
            val time = goal.targetTimeSec?.let { " in ${GoalProgress.formatTime(it)}" } ?: ""
            "$distance$time$by"
        }
        GoalType.BODY_WEIGHT -> "${goal.targetWeightKg ?: 0.0} kg$by"
        GoalType.CONSISTENCY -> "${goal.targetValue ?: 0.0} sessions/week$by"
        GoalType.STRENGTH_LIFT, GoalType.SOCCER_AVAILABILITY ->
            "${goalTypeLabel(goal.type)}: ${goal.targetValue ?: 0.0}$by"
    }
}
