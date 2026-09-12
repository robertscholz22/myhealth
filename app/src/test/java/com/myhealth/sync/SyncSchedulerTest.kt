package com.myhealth.sync

import com.google.common.truth.Truth.assertThat
import com.myhealth.testutil.Fixtures
import org.junit.Test

/**
 * The pure half of [SyncScheduler] (PLAN P4.12): the initial delay that puts the daily target
 * recompute on the next 03:00 local. WorkManager itself is not exercised here — it needs an
 * instrumented context.
 */
class SyncSchedulerTest {

    @Test
    fun the_daily_recompute_delay_points_at_the_next_three_am() {
        // 00:30 → 2.5 h to today's 03:00.
        val beforeThree = Fixtures.fixedClock("2026-09-12T00:30:00Z")
        assertThat(SyncScheduler.minutesUntilNext(beforeThree, 3)).isEqualTo(150L)

        // 08:00 → tomorrow's 03:00, 19 h out.
        val afterThree = Fixtures.fixedClock("2026-09-12T08:00:00Z")
        assertThat(SyncScheduler.minutesUntilNext(afterThree, 3)).isEqualTo(19 * 60L)
    }

    @Test
    fun the_delay_is_never_zero_even_exactly_on_the_hour() {
        val exactly = Fixtures.fixedClock("2026-09-12T03:00:00Z")

        // Exactly 03:00 must schedule tomorrow, not "immediately".
        assertThat(SyncScheduler.minutesUntilNext(exactly, 3)).isEqualTo(24 * 60L)
    }

    @Test
    fun the_unique_work_names_are_the_ones_the_plan_names() {
        assertThat(SyncScheduler.TARGETS_DAILY_NAME).isEqualTo("targets_daily")
        assertThat(SyncScheduler.TARGETS_NOW_NAME).isEqualTo("targets_now")
        assertThat(SyncScheduler.TARGET_RECOMPUTE_DEBOUNCE_SECONDS).isEqualTo(30L)
        assertThat(SyncScheduler.TARGET_RECOMPUTE_INTERVAL_HOURS).isEqualTo(24L)
        assertThat(SyncScheduler.TARGET_RECOMPUTE_HOUR).isEqualTo(3)
    }

    @Test
    fun the_recompute_window_is_yesterday_through_a_week_out() {
        assertThat(TargetRecomputeWorker.PAST_DAYS).isEqualTo(-1L)
        assertThat(TargetRecomputeWorker.FUTURE_DAYS).isEqualTo(7L)
        val days = (TargetRecomputeWorker.PAST_DAYS..TargetRecomputeWorker.FUTURE_DAYS).count()
        assertThat(days).isEqualTo(9)
    }
}
