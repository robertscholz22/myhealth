package com.myhealth.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhealth.di.rememberVm
import com.myhealth.domain.engine.calendar.LinkProposal
import com.myhealth.domain.model.ActivitySource
import com.myhealth.domain.model.ActivitySummary
import com.myhealth.domain.model.LoadMethod
import com.myhealth.domain.model.MacroTotals
import com.myhealth.domain.model.NutritionTarget
import com.myhealth.domain.model.SleepRecord
import com.myhealth.domain.model.SportGroup
import com.myhealth.domain.model.SportType
import com.myhealth.ui.activities.formatDuration
import com.myhealth.ui.calendar.confidenceLabel
import com.myhealth.ui.calendar.targetProgressRows
import com.myhealth.ui.nutrition.NO_TARGET_MESSAGE
import com.myhealth.ui.nutrition.energyFraction
import com.myhealth.ui.nutrition.remainingLabel
import com.myhealth.ui.nutrition.roundHalfUp
import com.myhealth.ui.common.EmptyState
import com.myhealth.ui.common.ErrorBanner
import com.myhealth.ui.common.SectionCard
import com.myhealth.ui.common.SportIcon
import com.myhealth.ui.common.StatTile
import com.myhealth.ui.common.displayName
import com.myhealth.ui.theme.MyHealthTheme
import java.time.ZoneId

@Composable
fun TodayScreen(
    onOpenActivity: (Long) -> Unit,
    onOpenDay: (Long) -> Unit,
    onOpenNutrition: () -> Unit,
    onOpenLoad: () -> Unit,
    onOpenTraining: () -> Unit,
    onReviewSuggestions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = rememberVm { graph ->
        TodayViewModel(
            graph.activityRepo,
            graph.bodyRepo,
            graph.profileRepo,
            graph.healthRepo,
            graph.mealRepo,
            graph.nutritionRepo,
            graph.syncStateRepo,
            graph.syncScheduler,
            graph.calendarRepo,
            graph.loadRepo,
            graph.planRepo,
            graph.suggestionRepo,
            graph.clock,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()

    TodayContent(
        state = state,
        onSyncNow = vm::syncNow,
        onOpenActivity = onOpenActivity,
        onOpenDay = onOpenDay,
        onOpenNutrition = onOpenNutrition,
        onOpenLoad = onOpenLoad,
        onAcceptSuggestion = vm::acceptSuggestion,
        onDismissSuggestion = vm::dismissSuggestion,
        onMarkPlannedDone = vm::markPlannedDone,
        onOpenTraining = onOpenTraining,
        onReviewSuggestions = onReviewSuggestions,
        modifier = modifier,
    )
}

@Composable
private fun TodayContent(
    state: TodayUiState,
    onSyncNow: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    onOpenDay: (Long) -> Unit,
    onOpenNutrition: () -> Unit,
    onOpenLoad: () -> Unit,
    onAcceptSuggestion: (LinkProposal) -> Unit,
    onDismissSuggestion: (LinkProposal) -> Unit,
    onMarkPlannedDone: (Long) -> Unit,
    onOpenTraining: () -> Unit,
    onReviewSuggestions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SyncStatusBanner(state, onSyncNow) }
        if (state.linkSuggestions.isNotEmpty()) {
            item { SuggestedLinksCard(state.linkSuggestions, onAcceptSuggestion, onDismissSuggestion) }
        }
        item { NutritionCard(state.target, state.intake, onOpenNutrition) }
        item {
            TodayPlanCard(
                planned = state.plannedToday,
                suggested = state.suggestedToday,
                onMarkDone = onMarkPlannedDone,
                onReviewSuggestions = onReviewSuggestions,
                onOpenTraining = onOpenTraining,
            )
        }
        item { RecoveryCard(state.latestLoad, state.topRecoveryFlag, onOpenLoad) }
        item { LoadCard(state.latestLoad, state.weeklyTrimp, onOpenLoad) }
        item { TodayActivitiesSection(state.activities, onOpenActivity) { onOpenDay(state.day) } }
        item { BodyChip(state.weightChipText) }
        item { SleepTile(state.sleep) }
    }
}

/** "Suggested links" card (§4.2 Today, P3.7): shown when ≥ 1 undismissed proposal has
 * `confidence >= EventActivityLinker.PROPOSE_THRESHOLD` (already true of everything
 * `TodayViewModel` puts in [TodayUiState.linkSuggestions]). */
@Composable
private fun SuggestedLinksCard(
    suggestions: List<LinkProposal>,
    onAccept: (LinkProposal) -> Unit,
    onDismiss: (LinkProposal) -> Unit,
) {
    SectionCard(title = "Suggested links") {
        suggestions.forEach { proposal ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(proposal.eventOccurrence.effectiveTitle, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "${proposal.activity.title ?: proposal.activity.sportType.displayName()} · " +
                            confidenceLabel(proposal.confidence),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onDismiss(proposal) }) { Text("Dismiss") }
                Button(onClick = { onAccept(proposal) }) { Text("Accept") }
            }
        }
    }
}

/**
 * Today's nutrition card (§4.2 Today, P4.12): the energy bar with the remaining kcal, one bar per
 * macro, and a tap through to the diary. Without a snapshot yet it says so instead of showing zeros
 * as if they were targets.
 */
@Composable
private fun NutritionCard(
    target: NutritionTarget?,
    intake: MacroTotals,
    onOpenNutrition: () -> Unit,
) {
    SectionCard(
        title = "Nutrition",
        modifier = Modifier.clickable(onClick = onOpenNutrition),
        action = { TextButton(onClick = onOpenNutrition) { Text("Diary") } },
    ) {
        if (target == null) {
            Text(NO_TARGET_MESSAGE, style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${roundHalfUp(intake.kcal)} / ${target.kcal} kcal",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = remainingLabel(target, intake).orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        LinearProgressIndicator(
            progress = { energyFraction(target, intake) },
            modifier = Modifier.fillMaxWidth(),
        )
        targetProgressRows(target, intake).drop(1).forEach { row ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(row.label, style = MaterialTheme.typography.bodyMedium)
                    Text(row.valueLabel, style = MaterialTheme.typography.bodySmall)
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
private fun SyncStatusBanner(state: TodayUiState, onSyncNow: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.lastSyncError != null) {
            ErrorBanner(message = "Sync failed: ${state.lastSyncError}", onRetry = onSyncNow)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = lastSyncedLabel(state.lastSyncSuccessAtMillis, ZoneId.systemDefault()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onSyncNow, enabled = !state.isSyncing) { Text("Sync now") }
            }
        }
    }
}

@Composable
private fun TodayActivitiesSection(
    activities: List<ActivitySummary>,
    onOpenActivity: (Long) -> Unit,
    onOpenDay: () -> Unit,
) {
    SectionCard(
        title = "Today's activities",
        action = { TextButton(onClick = onOpenDay) { Text("Day detail") } },
    ) {
        if (activities.isEmpty()) {
            EmptyState(title = "No activities yet today", message = "Anything you do today will show up here.")
        } else {
            activities.forEach { activity ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenActivity(activity.id) },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SportIcon(activity.sportGroup)
                    Column {
                        Text(
                            text = activity.title?.takeIf { it.isNotBlank() } ?: activity.sportType.displayName(),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(formatDuration(activity.durationSec), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun BodyChip(weightChipText: String?) {
    SectionCard(title = "Body") {
        Text(weightChipText ?: "No weight logged yet.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SleepTile(sleep: SleepRecord?) {
    SectionCard(title = "Sleep") {
        if (sleep == null) {
            Text("No sleep data for last night.", style = MaterialTheme.typography.bodyMedium)
        } else {
            val hours = sleep.totalSleepMin / 60.0
            StatTile(label = "Last night", value = "%.1f".format(hours), unit = "h")
        }
    }
}

@Preview(showBackground = true, name = "Populated")
@Composable
private fun TodayContentPreview() {
    MyHealthTheme(dynamicColor = false) {
        TodayContent(
            state = TodayUiState(
                isLoading = false,
                activities = listOf(
                    ActivitySummary(
                        id = 1,
                        startAtMillis = 1_757_000_000_000L,
                        endAtMillis = 1_757_003_600_000L,
                        day = 19980,
                        sportType = SportType.RUN_OUTDOOR,
                        sportGroup = SportGroup.RUN,
                        title = "Morning run",
                        durationSec = 2880,
                        elapsedSec = 3000,
                        distanceMeters = 8320.0,
                        activeEnergyKcal = 540.0,
                        totalEnergyKcal = 640.0,
                        avgHr = 142,
                        maxHr = 168,
                        avgSpeedMps = 2.89,
                        maxSpeedMps = 4.1,
                        avgCadenceSpm = 172.0,
                        elevationGainM = 45.0,
                        trimp = 108.1,
                        loadMethod = LoadMethod.HR_SAMPLES,
                        rpe = null,
                        note = null,
                        primarySource = ActivitySource.HEALTH_CONNECT,
                        mergedSources = listOf(ActivitySource.HEALTH_CONNECT),
                        hasStreams = true,
                    ),
                ),
                latestWeight = null,
                lastSyncSuccessAtMillis = 1_757_000_000_000L,
            ),
            onSyncNow = {},
            onOpenActivity = {},
            onOpenDay = {},
            onOpenNutrition = {},
            onOpenLoad = {},
            onAcceptSuggestion = {},
            onDismissSuggestion = {},
            onMarkPlannedDone = {},
            onOpenTraining = {},
            onReviewSuggestions = {},
        )
    }
}

@Preview(showBackground = true, name = "Empty")
@Composable
private fun TodayContentEmptyPreview() {
    MyHealthTheme(dynamicColor = false) {
        TodayContent(
            state = TodayUiState(isLoading = false, lastSyncError = "storage: disk full"),
            onSyncNow = {},
            onOpenActivity = {},
            onOpenDay = {},
            onOpenNutrition = {},
            onOpenLoad = {},
            onAcceptSuggestion = {},
            onDismissSuggestion = {},
            onMarkPlannedDone = {},
            onOpenTraining = {},
            onReviewSuggestions = {},
        )
    }
}
