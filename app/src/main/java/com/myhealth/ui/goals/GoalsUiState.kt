package com.myhealth.ui.goals

import com.myhealth.domain.engine.goal.GoalProgress
import com.myhealth.domain.model.ActivitySummary
import com.myhealth.domain.model.BodyMeasurement
import com.myhealth.domain.model.Goal
import com.myhealth.domain.model.GoalStatus
import com.myhealth.domain.model.RunningBest
import java.time.LocalDate

/** One row of the Goals list: the goal plus its computed progress (PLAN §4.2 "Goals", P6.1). */
data class GoalRow(
    val goal: Goal,
    val progress: GoalProgress.Progress,
) {
    val isPrimary: Boolean get() = goal.priority <= 1 && goal.status == GoalStatus.ACTIVE
}

/** ViewModel state for [GoalsScreen]. */
data class GoalsUiState(
    val isLoading: Boolean = true,
    val active: List<GoalRow> = emptyList(),
    val archived: List<GoalRow> = emptyList(),
    val message: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && active.isEmpty() && archived.isEmpty()
}

/**
 * Pure projection of the repositories onto [GoalRow]s — kept out of the ViewModel so it can be
 * unit-tested without coroutines. Active goals come first, primary first, then by target day.
 */
fun goalRows(
    goals: List<Goal>,
    bests: List<RunningBest>,
    weights: List<BodyMeasurement>,
    activities: List<ActivitySummary>,
    today: LocalDate,
): List<GoalRow> = goals.map { goal ->
    GoalRow(
        goal = goal,
        progress = GoalProgress.compute(goal, bests, weights, today, activities),
    )
}

/** §4.2's list order: primary goal first, then the nearest deadline, then the newest goal. */
fun List<GoalRow>.sortedForDisplay(): List<GoalRow> = sortedWith(
    compareBy<GoalRow> { it.goal.priority }
        .thenBy { it.goal.targetDay ?: Long.MAX_VALUE }
        .thenByDescending { it.goal.createdAtMillis },
)

fun List<GoalRow>.activeOnly(): List<GoalRow> = filter { it.goal.status == GoalStatus.ACTIVE }

fun List<GoalRow>.archivedOnly(): List<GoalRow> = filter { it.goal.status != GoalStatus.ACTIVE }
