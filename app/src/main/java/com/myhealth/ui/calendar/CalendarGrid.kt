package com.myhealth.ui.calendar

import com.myhealth.domain.util.isoWeekOf
import com.myhealth.domain.util.startOfIsoWeek
import com.myhealth.domain.util.toLocalDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure grid geometry for the calendar screen (PLAN §4.2 Calendar, P3.4). No Compose, no Android —
 * unit-tested in `CalendarGridTest`.
 *
 * The month grid is always [MONTH_GRID_CELLS] cells (6 ISO weeks × 7 days, Monday first, §1.6) so
 * paging never changes the grid's height and the layout never reflows between months.
 */

/** 6 rows × 7 columns. Enough for every month: at most 6 leading blanks + 31 days = 37 ≤ 42. */
const val MONTH_GRID_CELLS: Int = 42

/** Monday-first weekday initials for the grid header. */
val WEEKDAY_INITIALS: List<String> = listOf("M", "T", "W", "T", "F", "S", "S")

/**
 * The 42 days rendered for the month containing [anchor]: starts on the Monday of the ISO week
 * holding the 1st, runs 6 weeks, and therefore always contains every day of that month. Days
 * outside the month are the neighbouring months' days (rendered dimmed).
 */
fun monthGridDays(anchor: LocalDate): List<LocalDate> {
    val gridStart = anchor.withDayOfMonth(1).startOfIsoWeek()
    return List(MONTH_GRID_CELLS) { index -> gridStart.plusDays(index.toLong()) }
}

/** The 7 days of the ISO week containing [anchor], Monday first. */
fun weekDays(anchor: LocalDate): List<LocalDate> {
    val weekStart = anchor.startOfIsoWeek()
    return List(7) { index -> weekStart.plusDays(index.toLong()) }
}

/** The epoch-day range the screen must observe for [anchorDay], one page of slack on each side. */
fun visibleRange(anchorDay: Long, mode: CalendarMode): LongRange {
    val anchor = anchorDay.toLocalDate()
    return when (mode) {
        CalendarMode.MONTH ->
            monthGridDays(anchor.minusMonths(1)).first().toEpochDay()..
                monthGridDays(anchor.plusMonths(1)).last().toEpochDay()
        CalendarMode.WEEK ->
            weekDays(anchor).first().minusWeeks(1).toEpochDay()..
                weekDays(anchor).last().plusWeeks(1).toEpochDay()
    }
}

private val MONTH_TITLE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.US)
private val FULL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", Locale.US)

/** "September 2026". */
fun monthTitle(anchor: LocalDate): String = anchor.format(MONTH_TITLE)

/** "W38 · 14 Sep – 20 Sep". */
fun weekTitle(anchor: LocalDate): String {
    val days = weekDays(anchor)
    return "W${isoWeekOf(anchor).week} · ${days.first().format(DAY_MONTH)} – ${days.last().format(DAY_MONTH)}"
}

/** "Monday, 14 Sep 2026" — the agenda / day-detail header. */
fun fullDateTitle(date: LocalDate): String = date.format(FULL_DATE)

/** "07:30" from a minute-of-day, or `null` for an all-day item. */
fun formatMinuteOfDay(minuteOfDay: Int?): String? {
    if (minuteOfDay == null) return null
    val clamped = minuteOfDay.coerceIn(0, 24 * 60 - 1)
    return "%02d:%02d".format(Locale.US, clamped / 60, clamped % 60)
}
