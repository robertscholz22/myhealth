package com.myhealth.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myhealth.ui.common.DatePickerField
import com.myhealth.ui.common.NumberField
import com.myhealth.ui.common.SectionCard
import java.time.DayOfWeek

/** Monday-first, matching the ISO week used everywhere else in the app (§1.6). */
private val WEEKDAY_ORDER = listOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY,
)

private fun DayOfWeek.shortLabel(): String = when (this) {
    DayOfWeek.MONDAY -> "Mo"
    DayOfWeek.TUESDAY -> "Tu"
    DayOfWeek.WEDNESDAY -> "We"
    DayOfWeek.THURSDAY -> "Th"
    DayOfWeek.FRIDAY -> "Fr"
    DayOfWeek.SATURDAY -> "Sa"
    DayOfWeek.SUNDAY -> "Su"
}

/**
 * Recurrence section of the event editor (§4.2 Event edit, P3.6): `None` / `Weekly on [Mo…Su
 * chips]` — "every n weeks" is the same weekly mode with the interval field above 1, per
 * [RecurrenceMode]'s doc — plus an optional "until" date.
 */
@Composable
fun RecurrencePicker(
    draft: EventDraft,
    errors: Map<EventField, String>,
    onDraftChange: ((EventDraft) -> EventDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(title = "Recurrence", modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = draft.recurrenceMode == RecurrenceMode.NONE,
                onClick = { onDraftChange { it.copy(recurrenceMode = RecurrenceMode.NONE) } },
                label = { Text("None") },
            )
            FilterChip(
                selected = draft.recurrenceMode == RecurrenceMode.WEEKLY,
                onClick = { onDraftChange { it.copy(recurrenceMode = RecurrenceMode.WEEKLY) } },
                label = { Text("Weekly") },
            )
        }
        if (draft.recurrenceMode == RecurrenceMode.WEEKLY) {
            Text("Repeat on", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WEEKDAY_ORDER.forEach { day ->
                    FilterChip(
                        selected = day in draft.recurrenceWeekdays,
                        onClick = {
                            onDraftChange {
                                val days = if (day in it.recurrenceWeekdays) {
                                    it.recurrenceWeekdays - day
                                } else {
                                    it.recurrenceWeekdays + day
                                }
                                it.copy(recurrenceWeekdays = days)
                            }
                        },
                        label = { Text(day.shortLabel()) },
                    )
                }
            }
            errors[EventField.RECURRENCE_WEEKDAYS]?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            NumberField(
                label = "Repeat every",
                value = draft.recurrenceIntervalWeeks.toDouble(),
                onValueChange = { v -> onDraftChange { it.copy(recurrenceIntervalWeeks = (v?.toInt() ?: 1).coerceAtLeast(1)) } },
                suffix = "week(s)",
                decimals = 0,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DatePickerField(
                    label = "Until (optional)",
                    value = draft.recurrenceUntil,
                    onValueChange = { d -> onDraftChange { it.copy(recurrenceUntil = d) } },
                    isError = errors.containsKey(EventField.RECURRENCE_UNTIL),
                    supportingText = errors[EventField.RECURRENCE_UNTIL],
                    modifier = Modifier.weight(1f),
                )
                if (draft.recurrenceUntil != null) {
                    TextButton(onClick = { onDraftChange { it.copy(recurrenceUntil = null) } }) { Text("Clear") }
                }
            }
        }
    }
}
