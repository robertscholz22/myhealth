package com.myhealth.domain.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DateUtilsTest {

    @Test
    fun epoch_day_round_trips_through_long() {
        val date = LocalDate.of(2026, 9, 12)

        assertThat(date.toEpochDay().toLocalDate()).isEqualTo(date)
    }

    @Test
    fun iso_week_starts_on_monday() {
        // 2026-09-16 is a Wednesday in ISO week 2026-W38 (verified with `date -d ... +%G-W%V`).
        val wednesday = LocalDate.of(2026, 9, 16)
        val monday = wednesday.startOfIsoWeek()

        assertThat(monday.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
        assertThat(monday).isEqualTo(LocalDate.of(2026, 9, 14))
        assertThat(isoWeekOf(wednesday)).isEqualTo(isoWeekOf(monday))
    }

    @Test
    fun iso_week_of_matches_reference_week_number() {
        val week = isoWeekOf(LocalDate.of(2026, 9, 16))

        assertThat(week.weekBasedYear).isEqualTo(2026)
        assertThat(week.week).isEqualTo(38)
    }

    @Test
    fun instant_to_local_day_crosses_midnight_in_berlin() {
        val zone = ZoneId.of("Europe/Berlin")
        // Europe/Berlin is UTC+2 (CEST) in September: 21:30Z == 23:30 local (still 2026-09-12).
        val beforeMidnight = Instant.parse("2026-09-12T21:30:00Z")
        // 22:30Z == 00:30 local the next day (2026-09-13).
        val afterMidnight = Instant.parse("2026-09-12T22:30:00Z")

        assertThat(beforeMidnight.toLocalDay(zone)).isEqualTo(LocalDate.of(2026, 9, 12).toEpochDay())
        assertThat(afterMidnight.toLocalDay(zone)).isEqualTo(LocalDate.of(2026, 9, 13).toEpochDay())
    }
}
