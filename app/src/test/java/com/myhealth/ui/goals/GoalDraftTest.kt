package com.myhealth.ui.goals

import com.google.common.truth.Truth.assertThat
import com.myhealth.domain.model.GoalStatus
import com.myhealth.domain.model.GoalType
import com.myhealth.testutil.Fixtures
import org.junit.Test
import java.time.LocalDate

/** The goal editor's pure form logic (PLAN §4.2 "Goal edit", P6.1). */
class GoalDraftTest {

    private val clock = Fixtures.fixedClock("2026-09-14T12:00:00Z")

    @Test
    fun a_race_goal_needs_a_distance_and_a_time() {
        val empty = GoalDraft(type = GoalType.RACE_TIME, targetDistanceMeters = null)
        assertThat(validateGoal(empty).keys)
            .containsExactly(GoalField.TITLE, GoalField.DISTANCE, GoalField.TIME)

        val complete = GoalDraft(
            type = GoalType.RACE_TIME,
            title = "Sub-20 5k",
            targetDistanceMeters = 5000.0,
            targetMinutes = 20,
            targetSeconds = 0,
        )
        assertThat(validateGoal(complete)).isEmpty()

        assertThat(validateGoal(complete.copy(targetSeconds = 75))).containsKey(GoalField.TIME)
    }

    /** BUG-8: the editor shows "5 km" for a new race goal, so the draft must already carry it. */
    @Test
    fun a_fresh_race_draft_with_a_time_validates_without_touching_the_distance_picker() {
        val fresh = GoalDraft().copy(title = "Sub-20 5k", targetMinutes = 19, targetSeconds = 59)

        assertThat(fresh.type).isEqualTo(GoalType.RACE_TIME)
        assertThat(fresh.targetDistanceMeters).isEqualTo(5000.0)
        assertThat(validateGoal(fresh)).isEmpty()
        assertThat(fresh.toGoal(clock).targetDistanceMeters).isEqualTo(5000.0)
    }

    @Test
    fun switching_back_to_a_race_goal_restores_the_default_distance() {
        val cleared = GoalDraft(targetDistanceMeters = null)

        assertThat(cleared.withType(GoalType.BODY_WEIGHT).targetDistanceMeters).isNull()
        assertThat(cleared.withType(GoalType.RACE_TIME).targetDistanceMeters).isEqualTo(5000.0)
        assertThat(
            GoalDraft(targetDistanceMeters = 10_000.0).withType(GoalType.RACE_TIME).targetDistanceMeters,
        ).isEqualTo(10_000.0)
    }

    @Test
    fun the_other_goal_types_validate_their_own_field() {
        val weight = GoalDraft(type = GoalType.BODY_WEIGHT, title = "80 kg")
        assertThat(validateGoal(weight)).containsKey(GoalField.WEIGHT)
        assertThat(validateGoal(weight.copy(targetWeightKg = 80.0))).isEmpty()

        val consistency = GoalDraft(type = GoalType.CONSISTENCY, title = "4 a week")
        assertThat(validateGoal(consistency)).containsKey(GoalField.VALUE)
        assertThat(validateGoal(consistency.copy(targetValue = 4.0))).isEmpty()

        val lift = GoalDraft(type = GoalType.STRENGTH_LIFT, title = "120 kg squat")
        assertThat(validateGoal(lift)).containsKey(GoalField.VALUE)
        assertThat(validateGoal(lift.copy(targetValue = 120.0))).isEmpty()
    }

    @Test
    fun the_time_fields_combine_into_seconds() {
        assertThat(GoalDraft(targetMinutes = 20, targetSeconds = 30).targetTimeSec).isEqualTo(1230)
        assertThat(GoalDraft(targetMinutes = 20).targetTimeSec).isEqualTo(1200)
        assertThat(GoalDraft(targetSeconds = 45).targetTimeSec).isEqualTo(45)
        assertThat(GoalDraft().targetTimeSec).isNull()
    }

    @Test
    fun saving_keeps_only_the_fields_the_type_uses() {
        val draft = GoalDraft(
            type = GoalType.BODY_WEIGHT,
            title = "  80 kg  ",
            targetDay = LocalDate.of(2026, 12, 24),
            targetDistanceMeters = 5000.0,
            targetMinutes = 20,
            targetWeightKg = 80.0,
            targetValue = 4.0,
            isPrimary = true,
        )
        val goal = draft.toGoal(clock)

        assertThat(goal.title).isEqualTo("80 kg")
        assertThat(goal.targetWeightKg).isEqualTo(80.0)
        assertThat(goal.targetDistanceMeters).isNull()
        assertThat(goal.targetTimeSec).isNull()
        assertThat(goal.targetValue).isNull()
        assertThat(goal.priority).isEqualTo(1)
        assertThat(goal.targetDay).isEqualTo(LocalDate.of(2026, 12, 24).toEpochDay())
        assertThat(goal.createdAtMillis).isEqualTo(clock.millis())

        val secondary = draft.copy(isPrimary = false).toGoal(clock)
        assertThat(secondary.priority).isEqualTo(2)
    }

    @Test
    fun a_saved_goal_round_trips_through_the_draft() {
        val original = GoalDraft(
            type = GoalType.RACE_TIME,
            title = "Sub-20 5k",
            targetDay = LocalDate.of(2026, 11, 15),
            targetDistanceMeters = 5000.0,
            targetMinutes = 20,
            targetSeconds = 0,
            isPrimary = true,
            status = GoalStatus.ACTIVE,
        ).toGoal(clock)

        val draft = goalDraftOf(original)
        assertThat(draft.type).isEqualTo(GoalType.RACE_TIME)
        assertThat(draft.targetMinutes).isEqualTo(20)
        assertThat(draft.targetSeconds).isEqualTo(0)
        assertThat(draft.isPrimary).isTrue()
        assertThat(draft.toGoal(clock).copy(updatedAtMillis = 0L))
            .isEqualTo(original.copy(updatedAtMillis = 0L))
    }

    @Test
    fun headlines_and_labels_describe_each_goal_type() {
        val race = GoalDraft(
            type = GoalType.RACE_TIME,
            title = "Sub-20 5k",
            targetDay = LocalDate.of(2026, 11, 15),
            targetDistanceMeters = 5000.0,
            targetMinutes = 20,
            targetSeconds = 0,
        ).toGoal(clock)
        assertThat(goalHeadline(race)).isEqualTo("5 km in 20:00 by 2026-11-15")

        val weight = GoalDraft(type = GoalType.BODY_WEIGHT, title = "80 kg", targetWeightKg = 80.0).toGoal(clock)
        assertThat(goalHeadline(weight)).isEqualTo("80.0 kg")

        assertThat(goalTypeLabel(GoalType.SOCCER_AVAILABILITY)).isEqualTo("Soccer availability")
        assertThat(goalStatusLabel(GoalStatus.ABANDONED)).isEqualTo("Abandoned")
    }
}
