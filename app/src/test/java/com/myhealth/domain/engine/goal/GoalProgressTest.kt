package com.myhealth.domain.engine.goal

import com.google.common.truth.Truth.assertThat
import com.myhealth.domain.model.ActivitySource
import com.myhealth.domain.model.BodyMeasurement
import com.myhealth.domain.model.Goal
import com.myhealth.domain.model.GoalStatus
import com.myhealth.domain.model.GoalType
import com.myhealth.domain.model.RunningBest
import com.myhealth.domain.engine.suggest.SuggestFixtures
import com.myhealth.testutil.Fixtures
import org.junit.Test
import java.time.LocalDate

/**
 * [GoalProgress] against PLAN P6.1: `goal01`…`goal05` are the plan's named cases (`goal06` is the
 * repository's single-primary invariant, see `RoomGoalRepositoryTest`), `goal07`/`goal08` cover the
 * manual and no-data branches.
 */
class GoalProgressTest {

    private val today = LocalDate.of(2026, 9, 14)
    private val todayDay = today.toEpochDay()

    private fun best(distance: Double, timeSec: Int, dayOffset: Long = -5): RunningBest = RunningBest(
        id = distance.toLong() + timeSec,
        distanceMeters = distance,
        timeSec = timeSec,
        activityId = 1L,
        day = todayDay + dayOffset,
        method = "FULL_ACTIVITY",
        isEstimated = false,
        paceSecPerKm = (timeSec / (distance / 1000.0)).toInt(),
        createdAtMillis = 0L,
    )

    private fun weight(dayOffset: Long, kg: Double, atMillis: Long = 0L): BodyMeasurement = BodyMeasurement(
        id = dayOffset + 1000,
        measuredAtMillis = if (atMillis != 0L) atMillis else (todayDay + dayOffset) * 86_400_000L,
        day = todayDay + dayOffset,
        weightKg = kg,
        bodyFatPercent = null,
        muscleMassKg = null,
        boneMassKg = null,
        bodyWaterPercent = null,
        source = ActivitySource.MANUAL,
    )

    private fun raceGoal(targetSec: Int = 1200, distance: Double = 5000.0, targetDayOffset: Long = 60): Goal =
        SuggestFixtures.raceGoal(
            targetDay = todayDay + targetDayOffset,
            targetTimeSec = targetSec,
            distanceMeters = distance,
        )

    @Test
    fun goal01_race_time_percent() {
        val progress = GoalProgress.compute(
            goal = raceGoal(targetSec = 1200),
            bests = listOf(best(5000.0, 1274)), // 21:14
            weights = emptyList(),
            today = today,
        )
        assertThat(progress.percent).isWithin(1e-6).of(1200.0 / 1274.0)
        assertThat(progress.statusText).contains("21:14")
        assertThat(progress.isManual).isFalse()

        // A best that already beats the target is capped at 100 % and counts as on track.
        val met = GoalProgress.compute(raceGoal(1200), listOf(best(5000.0, 1150)), emptyList(), today)
        assertThat(met.percent).isWithin(1e-9).of(1.0)
        assertThat(met.onTrack).isTrue()
    }

    @Test
    fun goal02_race_time_on_track_via_riegel() {
        val goal = raceGoal(targetSec = 1200)
        // 10 km in 40:50 predicts 5 km in 2450 * 2^-1.06 = 1175 s, inside 1200 * 1.02.
        val quick = GoalProgress.compute(goal, listOf(best(5000.0, 1274), best(10000.0, 2450)), emptyList(), today)
        assertThat(quick.onTrack).isTrue()
        assertThat(quick.statusText).contains("on track")

        // 10 km in 45:00 predicts ~1295 s — behind.
        val slow = GoalProgress.compute(goal, listOf(best(5000.0, 1274), best(10000.0, 2700)), emptyList(), today)
        assertThat(slow.onTrack).isFalse()
        assertThat(slow.statusText).contains("behind")

        // The same fast effort 90 days ago is outside the 60-day window and proves nothing.
        val stale = GoalProgress.compute(
            goal,
            listOf(best(5000.0, 1274, dayOffset = -90), best(10000.0, 2450, dayOffset = -90)),
            emptyList(),
            today,
        )
        assertThat(stale.onTrack).isFalse()
    }

    @Test
    fun goal03_body_weight_progress() {
        val goal = weightGoal(targetKg = 80.0, createdOffset = -35, targetDayOffset = 35)
        val progress = GoalProgress.compute(
            goal = goal,
            bests = emptyList(),
            weights = listOf(weight(-35, 85.0), weight(-14, 83.5), weight(0, 82.5)),
            today = today,
        )
        // 85 -> 80 is 5 kg; 2.5 kg done.
        assertThat(progress.percent).isWithin(1e-6).of(0.5)
        assertThat(progress.statusText).contains("82.5 kg")
        assertThat(progress.onTrack).isTrue()
    }

    @Test
    fun goal04_body_weight_behind_schedule() {
        // 5 kg in 10 weeks = 0.5 kg/week required; 0.4 kg/week is the 80 % threshold.
        val goal = weightGoal(targetKg = 80.0, createdOffset = -35, targetDayOffset = 35)

        val behind = GoalProgress.compute(goal, emptyList(), listOf(weight(-35, 85.0), weight(0, 84.5)), today)
        assertThat(behind.onTrack).isFalse()
        assertThat(behind.statusText).contains("behind")

        val onTrack = GoalProgress.compute(goal, emptyList(), listOf(weight(-35, 85.0), weight(0, 82.75)), today)
        assertThat(onTrack.onTrack).isTrue()
    }

    @Test
    fun goal05_consistency_goal() {
        val goal = SuggestFixtures.goal(type = GoalType.CONSISTENCY, title = "4 sessions a week", targetValue = 4.0)
        val threePerWeek = (0L until 12L).map { SuggestFixtures.activity(todayDay - it * 2, trimp = 50.0, id = it) }
        val behind = GoalProgress.compute(goal, emptyList(), emptyList(), today, threePerWeek)
        assertThat(behind.percent).isWithin(1e-6).of(0.75)
        assertThat(behind.onTrack).isFalse()

        val sixteen = (0L until 16L).map { SuggestFixtures.activity(todayDay - it, trimp = 50.0, id = it) }
        val met = GoalProgress.compute(goal, emptyList(), emptyList(), today, sixteen)
        assertThat(met.percent).isWithin(1e-9).of(1.0)
        assertThat(met.onTrack).isTrue()

        // Activities older than four weeks do not count.
        val stale = (0L until 16L).map { SuggestFixtures.activity(todayDay - 40 - it, trimp = 50.0, id = it) }
        assertThat(GoalProgress.compute(goal, emptyList(), emptyList(), today, stale).percent)
            .isWithin(1e-9).of(0.0)
    }

    @Test
    fun goal07_manual_goals_are_reported_as_manual() {
        listOf(GoalType.STRENGTH_LIFT, GoalType.SOCCER_AVAILABILITY).forEach { type ->
            val progress = GoalProgress.compute(
                goal = SuggestFixtures.goal(type = type, targetValue = 120.0),
                bests = emptyList(),
                weights = emptyList(),
                today = today,
            )
            assertThat(progress.isManual).isTrue()
            assertThat(progress.percent).isWithin(1e-9).of(0.0)
            assertThat(progress.statusText).contains("manually")
        }
    }

    @Test
    fun goal08_goals_without_data_report_zero_and_say_so() {
        val race = GoalProgress.compute(raceGoal(), emptyList(), emptyList(), today)
        assertThat(race.percent).isWithin(1e-9).of(0.0)
        assertThat(race.onTrack).isFalse()
        assertThat(race.statusText).contains("5 km")

        val weightless = GoalProgress.compute(
            weightGoal(targetKg = 80.0, createdOffset = -10, targetDayOffset = 30),
            emptyList(),
            emptyList(),
            today,
        )
        assertThat(weightless.percent).isWithin(1e-9).of(0.0)
        assertThat(weightless.statusText).contains("Log a weight")

        // A race goal missing its target fields is manual, not a crash.
        val incomplete = GoalProgress.compute(
            SuggestFixtures.goal(type = GoalType.RACE_TIME),
            emptyList(),
            emptyList(),
            today,
        )
        assertThat(incomplete.isManual).isTrue()
    }

    @Test
    fun the_start_weight_is_the_first_measurement_after_the_goal_was_created() {
        val created = Fixtures.millis("2026-08-10T08:00:00Z")
        val series = listOf(
            weight(-60, 90.0, atMillis = Fixtures.millis("2026-07-16T08:00:00Z")),
            weight(-30, 85.0, atMillis = Fixtures.millis("2026-08-15T08:00:00Z")),
            weight(0, 82.5, atMillis = Fixtures.millis("2026-09-14T08:00:00Z")),
        )
        assertThat(GoalProgress.startMeasurement(series, created)?.weightKg).isEqualTo(85.0)
        // A goal older than every measurement falls back to the earliest one.
        assertThat(GoalProgress.startMeasurement(series, 0L)?.weightKg).isEqualTo(90.0)
    }

    @Test
    fun time_and_distance_labels_are_human_readable() {
        assertThat(GoalProgress.formatTime(1274)).isEqualTo("21:14")
        assertThat(GoalProgress.formatTime(3671)).isEqualTo("1:01:11")
        assertThat(GoalProgress.formatTime(-5)).isEqualTo("0:00")
        assertThat(GoalProgress.distanceLabel(5000.0)).isEqualTo("5 km")
        assertThat(GoalProgress.distanceLabel(21097.5)).isEqualTo("half marathon")
        assertThat(GoalProgress.distanceLabel(1609.34)).isEqualTo("mile")
        assertThat(GoalProgress.distanceLabel(800.0)).isEqualTo("800 m")
    }

    private fun weightGoal(targetKg: Double, createdOffset: Long, targetDayOffset: Long): Goal =
        SuggestFixtures.goal(
            type = GoalType.BODY_WEIGHT,
            title = "Down to $targetKg kg",
            targetDay = todayDay + targetDayOffset,
            targetWeightKg = targetKg,
            status = GoalStatus.ACTIVE,
            createdAtMillis = (todayDay + createdOffset) * 86_400_000L,
        )
}
