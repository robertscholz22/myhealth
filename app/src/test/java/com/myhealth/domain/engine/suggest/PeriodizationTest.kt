package com.myhealth.domain.engine.suggest

import com.google.common.truth.Truth.assertThat
import com.myhealth.domain.model.EventType
import com.myhealth.domain.model.RecoveryBand
import com.myhealth.domain.model.TrainingPhase
import com.myhealth.domain.engine.suggest.SuggestFixtures.day
import org.junit.Test

/**
 * [Periodization] against PLAN §3.5.2 — the phase table, the factors, the 25 % ramp cap and the
 * ACWR / recovery multipliers. Named cases `sug09`–`sug12`, `sug17` and `per01`–`per03` come from
 * §3.5.7 / P6.2; `per04`–`per06` cover the remaining branches of the same section.
 */
class PeriodizationTest {

    private val ctl = 40.0
    private val weeklyBase = ctl * 7 // 280 AU

    @Test
    fun sug09_taper_reduces_weekly_target_to_60_percent() {
        val target = Periodization.weeklyTarget(
            phase = TrainingPhase.TAPER,
            ctl = ctl,
            lastWeekActual = 400.0,
            acwr = 1.0,
            band = null,
        )
        assertThat(target).isWithin(0.01).of(weeklyBase * 0.60)
        assertThat(target).isWithin(0.01).of(168.0)
    }

    @Test
    fun sug10_race_week_phase_detected_at_seven_days() {
        assertThat(Periodization.phase(daysToRace = 7L, matchWithin21Days = false, weeksSincePlanStart = null))
            .isEqualTo(TrainingPhase.RACE_WEEK)
        assertThat(Periodization.phase(daysToRace = 8L, matchWithin21Days = false, weeksSincePlanStart = null))
            .isEqualTo(TrainingPhase.TAPER)
    }

    @Test
    fun sug11_recovery_week_every_fourth_week() {
        assertThat(Periodization.phase(daysToRace = null, matchWithin21Days = false, weeksSincePlanStart = 3))
            .isEqualTo(TrainingPhase.RECOVERY_WEEK)
        assertThat(Periodization.phase(daysToRace = null, matchWithin21Days = false, weeksSincePlanStart = 2))
            .isEqualTo(TrainingPhase.BASE)
        // …and it never overrides a taper or race week.
        assertThat(Periodization.phase(daysToRace = 9L, matchWithin21Days = false, weeksSincePlanStart = 3))
            .isEqualTo(TrainingPhase.TAPER)
        assertThat(Periodization.phase(daysToRace = 4L, matchWithin21Days = false, weeksSincePlanStart = 3))
            .isEqualTo(TrainingPhase.RACE_WEEK)
    }

    @Test
    fun sug12_weekly_target_never_ramps_more_than_25_percent() {
        val target = Periodization.weeklyTarget(
            phase = TrainingPhase.BUILD,
            ctl = 100.0, // 700 AU * 1.10 = 770 AU without the cap
            lastWeekActual = 400.0,
            acwr = null,
            band = null,
        )
        assertThat(target).isAtMost(500.0)
        assertThat(target).isWithin(0.01).of(500.0)
    }

    @Test
    fun sug17_in_season_phase_when_match_within_21_days() {
        val input = SuggestFixtures.input(
            events = listOf(SuggestFixtures.event(day(20), EventType.SOCCER_MATCH)),
        )
        assertThat(Periodization.compute(input).phase).isEqualTo(TrainingPhase.IN_SEASON)

        val tooFar = SuggestFixtures.input(
            events = listOf(SuggestFixtures.event(day(22), EventType.SOCCER_MATCH)),
        )
        assertThat(Periodization.compute(tooFar).phase).isEqualTo(TrainingPhase.BASE)
    }

    @Test
    fun per01_no_goal_no_match_is_base() {
        val result = Periodization.compute(SuggestFixtures.input())
        assertThat(result.phase).isEqualTo(TrainingPhase.BASE)
        assertThat(result.daysToRace).isNull()
        // ctl 40 -> 280 AU * 1.05 = 294, under the ramp cap of max(7*40*1.25, 150) = 350.
        assertThat(result.weeklyTarget).isWithin(0.01).of(294.0)
    }

    @Test
    fun per02_phase_boundaries_exact_days() {
        val expected = mapOf(
            0L to TrainingPhase.RACE_WEEK,
            7L to TrainingPhase.RACE_WEEK,
            8L to TrainingPhase.TAPER,
            10L to TrainingPhase.TAPER,
            11L to TrainingPhase.PEAK,
            35L to TrainingPhase.PEAK,
            36L to TrainingPhase.BUILD,
            77L to TrainingPhase.BUILD,
            78L to TrainingPhase.BASE,
        )
        expected.forEach { (days, phase) ->
            assertThat(Periodization.phase(days, matchWithin21Days = false, weeksSincePlanStart = null))
                .isEqualTo(phase)
        }
    }

    @Test
    fun per03_strained_recovery_scales_target_to_60_percent() {
        val fresh = Periodization.weeklyTarget(TrainingPhase.BASE, ctl, 400.0, 1.0, RecoveryBand.GOOD)
        val strained = Periodization.weeklyTarget(TrainingPhase.BASE, ctl, 400.0, 1.0, RecoveryBand.STRAINED)
        assertThat(strained).isWithin(0.01).of(fresh * 0.60)

        val fatigued = Periodization.weeklyTarget(TrainingPhase.BASE, ctl, 400.0, 1.0, RecoveryBand.FATIGUED)
        assertThat(fatigued).isWithin(0.01).of(fresh * 0.85)
    }

    @Test
    fun per04_high_acwr_scales_target_to_75_percent() {
        val calm = Periodization.weeklyTarget(TrainingPhase.BASE, ctl, 400.0, 1.4, null)
        val spiking = Periodization.weeklyTarget(TrainingPhase.BASE, ctl, 400.0, 1.6, null)
        assertThat(spiking).isWithin(0.01).of(calm * 0.75)
        // 1.5 exactly is not "> 1.5" and must not be penalised.
        assertThat(Periodization.weeklyTarget(TrainingPhase.BASE, ctl, 400.0, 1.5, null))
            .isWithin(0.01).of(calm)
    }

    @Test
    fun per05_ramp_cap_has_a_150_au_floor_for_a_returning_athlete() {
        // No load at all last week: the cap must not be 0, it is the 150 AU floor.
        val target = Periodization.weeklyTarget(TrainingPhase.BASE, ctl = 60.0, lastWeekActual = 0.0, acwr = null, band = null)
        assertThat(target).isWithin(0.01).of(150.0)
    }

    @Test
    fun per06_every_phase_factor_matches_the_plan_table() {
        assertThat(Periodization.factorFor(TrainingPhase.BASE)).isWithin(1e-9).of(1.05)
        assertThat(Periodization.factorFor(TrainingPhase.BUILD)).isWithin(1e-9).of(1.10)
        assertThat(Periodization.factorFor(TrainingPhase.PEAK)).isWithin(1e-9).of(1.05)
        assertThat(Periodization.factorFor(TrainingPhase.TAPER)).isWithin(1e-9).of(0.60)
        assertThat(Periodization.factorFor(TrainingPhase.RACE_WEEK)).isWithin(1e-9).of(0.45)
        assertThat(Periodization.factorFor(TrainingPhase.IN_SEASON)).isWithin(1e-9).of(1.00)
        assertThat(Periodization.factorFor(TrainingPhase.OFF_SEASON)).isWithin(1e-9).of(0.80)
        assertThat(Periodization.factorFor(TrainingPhase.RECOVERY_WEEK)).isWithin(1e-9).of(0.65)
    }

    @Test
    fun a_race_goal_drives_the_phase_and_a_past_race_is_ignored() {
        val upcoming = SuggestFixtures.input(goals = listOf(SuggestFixtures.raceGoal(day(20))))
        assertThat(Periodization.compute(upcoming).phase).isEqualTo(TrainingPhase.PEAK)
        assertThat(Periodization.compute(upcoming).daysToRace).isEqualTo(20L)

        val past = SuggestFixtures.input(goals = listOf(SuggestFixtures.raceGoal(day(-3))))
        assertThat(Periodization.compute(past).phase).isEqualTo(TrainingPhase.BASE)
        assertThat(Periodization.compute(past).daysToRace).isNull()
    }

    @Test
    fun weeks_since_plan_start_counts_whole_weeks() {
        assertThat(Periodization.weeksSincePlanStart(day(0), day(-21))).isEqualTo(3)
        assertThat(Periodization.weeksSincePlanStart(day(0), day(-20))).isEqualTo(2)
        assertThat(Periodization.weeksSincePlanStart(day(0), null)).isNull()
        assertThat(Periodization.weeksSincePlanStart(day(0), day(3))).isNull()
    }

    @Test
    fun last_week_actual_sums_the_seven_days_before_today() {
        val history = SuggestFixtures.loadHistory(dailyTrimp = 30.0)
        assertThat(Periodization.lastWeekActual(history, SuggestFixtures.TODAY_DAY))
            .isWithin(0.01).of(210.0)
    }
}
