package com.myhealth.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myhealth.domain.model.ActivitySummary
import com.myhealth.domain.model.DailyLoad
import com.myhealth.domain.model.EventOccurrence
import com.myhealth.domain.model.MacroTotals
import com.myhealth.domain.model.MealLogSummary
import com.myhealth.domain.model.NutritionTarget
import com.myhealth.domain.model.PlannedSession
import com.myhealth.domain.model.PlannedStatus
import com.myhealth.domain.model.SleepRecord
import com.myhealth.ui.activities.formatDistanceKm
import com.myhealth.ui.activities.formatDuration
import com.myhealth.ui.common.SectionCard
import com.myhealth.ui.common.SourceBadgeRow
import com.myhealth.ui.common.SportIcon
import com.myhealth.ui.common.displayName
import java.util.Locale

/** The per-item overflow menu shared by the event and planned-session rows (§4.2 Day detail). */
@Composable
internal fun OverflowMenu(actions: List<Pair<String, () -> Unit>>) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        action()
                    },
                )
            }
        }
    }
}

@Composable
internal fun EventsSection(
    events: List<EventOccurrence>,
    activities: List<ActivitySummary>,
    onEdit: (EventOccurrence) -> Unit,
    onDelete: (Long) -> Unit,
    onLink: (EventOccurrence) -> Unit,
) {
    SectionCard(title = "Events") {
        if (events.isEmpty()) {
            EmptyLine("No events on this day.")
            return@SectionCard
        }
        events.forEach { occurrence ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AssistChip(onClick = {}, label = { Text(occurrence.type.displayName()) })
                        Text(
                            text = formatMinuteOfDay(occurrence.effectiveStartMinuteOfDay) ?: "All day",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Text(
                        text = occurrence.effectiveTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = eventSubtitle(occurrence, activities),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val isLinked = occurrence.linkedActivityId != null
                OverflowMenu(
                    listOf(
                        "Edit" to { onEdit(occurrence) },
                        (if (isLinked) "Manage link" else "Link activity") to { onLink(occurrence) },
                        "Delete" to { onDelete(occurrence.eventId) },
                    ),
                )
            }
        }
    }
}

private fun eventSubtitle(
    occurrence: EventOccurrence,
    activities: List<ActivitySummary>,
): String = buildList {
    occurrence.effectiveDurationMin?.let { add("$it min") }
    occurrence.sportType?.let { add(it.displayName()) }
    occurrence.linkedActivityId?.let { add(linkedActivityLabel(it, activities)) }
    if (occurrence.isKeyEvent) add("Key event")
    if (occurrence.isOverride) add("Modified occurrence")
}.joinToString(" · ").ifEmpty { "No details" }

@Composable
internal fun PlannedSection(
    sessions: List<PlannedSession>,
    onSetStatus: (Long, PlannedStatus) -> Unit,
    onEdit: (Long) -> Unit,
) {
    SectionCard(title = "Planned sessions") {
        if (sessions.isEmpty()) {
            EmptyLine("Nothing planned for this day.")
            return@SectionCard
        }
        sessions.forEach { session ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                SportIcon(session.sportType.group, modifier = Modifier.padding(end = 12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = session.sessionType.displayName(), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = plannedSubtitle(session),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OverflowMenu(
                    listOf(
                        "Mark done" to { onSetStatus(session.id, PlannedStatus.COMPLETED) },
                        "Skip" to { onSetStatus(session.id, PlannedStatus.SKIPPED) },
                        "Edit" to { onEdit(session.id) },
                    ),
                )
            }
        }
    }
}

private fun plannedSubtitle(session: PlannedSession): String = buildList {
    add(session.sportType.displayName())
    add(session.status.displayName())
    session.targetDurationMin?.let { add("$it min") }
    formatDistanceKm(session.targetDistanceMeters)?.let { add(it) }
}.joinToString(" · ")

@Composable
internal fun ActivitiesSection(activities: List<ActivitySummary>, onOpenActivity: (Long) -> Unit) {
    SectionCard(title = "Activities") {
        if (activities.isEmpty()) {
            EmptyLine("No activities recorded.")
            return@SectionCard
        }
        activities.forEach { activity ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpenActivity(activity.id) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SportIcon(activity.sportGroup, modifier = Modifier.padding(end = 12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activity.title?.takeIf { it.isNotBlank() } ?: activity.sportType.displayName(),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(
                            formatDuration(activity.durationSec),
                            formatDistanceKm(activity.distanceMeters),
                            activity.avgHr?.let { "$it bpm" },
                            activity.trimp?.let { "TRIMP ${Math.round(it)}" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (activity.mergedSources.isNotEmpty()) {
                        SourceBadgeRow(activity.mergedSources, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun MealsSection(meals: List<MealLogSummary>, intake: MacroTotals, onOpenNutrition: () -> Unit) {
    SectionCard(title = "Meals") {
        if (meals.isEmpty()) {
            EmptyLine("Nothing logged yet.")
        } else {
            meals.forEach { meal ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenNutrition() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = meal.name?.takeIf { it.isNotBlank() } ?: meal.slot.displayName(),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "%.0f kcal · %.0f g P".format(Locale.US, meal.totals.kcal, meal.totals.proteinG),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text(
                text = "Day total: %.0f kcal · %.0f g protein".format(Locale.US, intake.kcal, intake.proteinG),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
internal fun SleepSection(sleep: SleepRecord?) {
    SectionCard(title = "Sleep") {
        if (sleep == null) {
            EmptyLine("No sleep recorded for this night.")
            return@SectionCard
        }
        Text(text = formatSleepDuration(sleep.totalSleepMin), style = MaterialTheme.typography.titleLarge)
        val stages = listOfNotNull(
            sleep.deepMin?.let { "Deep ${it}m" },
            sleep.remMin?.let { "REM ${it}m" },
            sleep.lightMin?.let { "Light ${it}m" },
            sleep.awakeMin?.let { "Awake ${it}m" },
        )
        if (stages.isNotEmpty()) {
            Text(
                text = stages.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        sleep.sleepScore?.let { Text(text = "Score $it", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
internal fun TargetsSection(target: NutritionTarget?, intake: MacroTotals, onOpenNutrition: () -> Unit) {
    SectionCard(title = "Targets vs intake") {
        val rows = targetProgressRows(target, intake)
        if (rows.isEmpty()) {
            EmptyLine("No target yet for this day.")
            return@SectionCard
        }
        Text(
            text = target?.dayType?.let { labelOf(it.name) }.orEmpty(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        rows.forEach { row ->
            Column(modifier = Modifier.fillMaxWidth().clickable { onOpenNutrition() }) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = row.label, style = MaterialTheme.typography.bodyMedium)
                    Text(text = row.valueLabel, style = MaterialTheme.typography.bodySmall)
                }
                LinearProgressIndicator(
                    progress = { row.fraction },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
internal fun LoadSection(load: DailyLoad?) {
    SectionCard(title = "Load") {
        if (load == null) {
            EmptyLine("No load computed for this day.")
            return@SectionCard
        }
        Text(
            text = "TRIMP %.0f · ACWR %s".format(
                Locale.US,
                load.trimp,
                load.acwr?.let { "%.2f".format(Locale.US, it) } ?: "—",
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
        val recovery = listOfNotNull(
            load.recoveryScore?.let { "Recovery $it" },
            load.recoveryBand?.let { labelOf(it.name) },
        )
        if (recovery.isNotEmpty()) {
            Text(
                text = recovery.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (load.flags.isNotEmpty()) {
            Text(text = load.flags.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
