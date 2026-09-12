package com.myhealth.domain.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields

/**
 * `java.time` helpers (PLAN §1.6). Local dates are stored as epoch day (`Long`); instants as
 * epoch millis (`Long`). `LocalDate.toEpochDay()` is stdlib — everything else lives here.
 */

/** Inverse of the stdlib `LocalDate.toEpochDay()`. */
fun Long.toLocalDate(): LocalDate = LocalDate.ofEpochDay(this)

/** The local calendar day (epoch day) this instant falls on in [zone]. */
fun Instant.toLocalDay(zone: ZoneId): Long = LocalDate.ofInstant(this, zone).toEpochDay()

/** ISO-8601 week-based year and week number (`WeekFields.ISO`), week starts Monday. */
data class IsoWeek(val weekBasedYear: Int, val week: Int)

fun isoWeekOf(date: LocalDate): IsoWeek {
    val fields = WeekFields.ISO
    return IsoWeek(
        weekBasedYear = date.get(fields.weekBasedYear()),
        week = date.get(fields.weekOfWeekBasedYear()),
    )
}

/** The Monday of the ISO week containing this date (`DayOfWeek.value`: Monday = 1 .. Sunday = 7). */
fun LocalDate.startOfIsoWeek(): LocalDate = this.minusDays((dayOfWeek.value - 1).toLong())
