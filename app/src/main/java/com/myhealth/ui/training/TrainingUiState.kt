package com.myhealth.ui.training

import com.myhealth.domain.model.ActivitySummary
import com.myhealth.domain.model.CalendarDay
import com.myhealth.domain.model.EventOccurrence
import com.myhealth.domain.model.EventType
import com.myhealth.domain.model.ExercisePrescription
import com.myhealth.domain.model.Intensity
import com.myhealth.domain.model.PlannedSession
import com.myhealth.domain.model.StrengthWorkout
import com.myhealth.domain.model.TrainingPhase
import com.myhealth.ui.common.UiMessage
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One day row of the week board (PLAN §4.2 "Training plan"). */
data class TrainingDayRow(
    val day: Long,
    val isToday: Boolean,
    /** Only the fixed sport events: matches, trainings and races (§3.5.6 step 1). */
    val events: List<EventOccurrence>,
    val planned: List<PlannedSession>,
    val activities: List<ActivitySummary>,
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(day)

    val isEmpty: Boolean get() = events.isEmpty() && planned.isEmpty() && activities.isEmpty()
}

/** The week currently on screen; carries its own start day so paging can never tear. */
data class TrainingWeek(
    val startDay: Long,
    val offset: Int,
    val days: List<TrainingDayRow>,
) {
    companion object {
        val EMPTY: TrainingWeek = TrainingWeek(startDay = 0L, offset = 0, days = emptyList())
    }
}

/** ViewModel state for [TrainingScreen]. */
data class TrainingUiState(
    val isLoading: Boolean = true,
    val today: Long = 0L,
    val week: TrainingWeek = TrainingWeek.EMPTY,
    val planName: String? = null,
    val phase: TrainingPhase? = null,
    val loads: WeeklyLoadSums = WeeklyLoadSums.ZERO,
    /** The day "+ Session" and "Generate suggestions" act on — today, or the week's Monday. */
    val selectedDay: Long = 0L,
    val isGenerating: Boolean = false,
    /**
     * POLISH-8: the open `PROPOSED` batch was generated before the calendar changed, so the week
     * on screen no longer matches what the suggester saw.
     */
    val suggestionsStale: Boolean = false,
    val message: UiMessage? = null,
    /** One-shot: set after a successful generate so the screen can open the review. */
    val reviewReady: Boolean = false,
    /** `workoutId -> StrengthWorkout` (P14.7): the card's workout-name line and the "Mark done"
     * set-log sheet both read a session's workout off here. */
    val workoutsById: Map<Long, StrengthWorkout> = emptyMap(),
    /** `exerciseId -> ExercisePrescription` (P16.2), populated by `TrainingViewModel.prepareSetLog`
     * for the workout of whichever session's [SetLogSheet] is currently open. */
    val setLogPrescriptions: Map<String, ExercisePrescription> = emptyMap(),
) {
    val isCurrentWeek: Boolean get() = week.offset == 0

    /** Nothing planned, nothing recorded, no events: the screen shows how to get started. */
    val isEmptyWeek: Boolean get() = !isLoading && week.days.all { it.isEmpty }

    val weekLabel: String get() = weekRangeLabel(week.startDay)
}

/** The fixed events the plan cares about — an appointment or a note is not training. */
private val TRAINING_EVENT_TYPES = setOf(
    EventType.SOCCER_MATCH,
    EventType.SOCCER_TRAINING,
    EventType.RACE,
)

/** ISO week start: the Monday on or before [day] (§1.6 — week start is Monday, fixed). */
fun mondayOf(day: Long): Long {
    val date = LocalDate.ofEpochDay(day)
    return date.minusDays((date.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong()).toEpochDay()
}

/**
 * Projects the [CalendarDay] aggregate onto the seven rows of one week — pure, so the board can be
 * unit-tested and previewed without a repository. Days with nothing on them are still present.
 */
fun trainingDayRows(
    weekStartDay: Long,
    today: Long,
    days: Map<Long, CalendarDay>,
): List<TrainingDayRow> = (weekStartDay until weekStartDay + DAYS_PER_WEEK).map { day ->
    val aggregate = days[day]
    TrainingDayRow(
        day = day,
        isToday = day == today,
        events = aggregate?.events.orEmpty().filter { it.type in TRAINING_EVENT_TYPES },
        planned = aggregate?.planned.orEmpty().sortedWith(
            compareBy({ it.startMinuteOfDay ?: Int.MAX_VALUE }, { it.id }),
        ),
        activities = aggregate?.activities.orEmpty().sortedBy { it.startAtMillis },
    )
}

/** The `daily_load` rows of one week, in day order — the "actual" half of the load bar. */
fun weekLoads(weekStartDay: Long, days: Map<Long, CalendarDay>) =
    (weekStartDay until weekStartDay + DAYS_PER_WEEK).mapNotNull { days[it]?.load }

const val DAYS_PER_WEEK: Long = 7L

private val DAY_HEADER_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)

private val WEEK_RANGE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.US)

/** `"Mon 14 Sep"` — the day row's header. */
fun dayHeaderLabel(day: Long): String = LocalDate.ofEpochDay(day).format(DAY_HEADER_FORMAT)

/** `"14 Sep – 20 Sep"` — the week pager's title. */
fun weekRangeLabel(weekStartDay: Long): String {
    val start = LocalDate.ofEpochDay(weekStartDay)
    val end = start.plusDays(DAYS_PER_WEEK - 1)
    return "${start.format(WEEK_RANGE_FORMAT)} – ${end.format(WEEK_RANGE_FORMAT)}"
}

/** Title-cased enum label, e.g. `RACE_WEEK` -> "Race week". */
fun trainingLabelOf(name: String): String =
    name.split("_").joinToString(" ") { it.lowercase(Locale.US) }.replaceFirstChar(Char::uppercase)

fun TrainingPhase.label(): String = trainingLabelOf(name)

fun Intensity.label(): String = trainingLabelOf(name)
