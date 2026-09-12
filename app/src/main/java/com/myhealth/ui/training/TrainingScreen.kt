package com.myhealth.ui.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhealth.di.rememberVm
import com.myhealth.domain.model.TrainingPhase
import com.myhealth.ui.common.EmptyState
import com.myhealth.ui.common.SectionCard
import com.myhealth.ui.theme.MyHealthTheme

/** Where the Training screen can navigate (§4.1: review, session editor, activity detail). */
data class TrainingNavActions(
    val onReviewSuggestions: () -> Unit = {},
    val onEditSession: (Long) -> Unit = {},
    val onAddSession: (Long) -> Unit = {},
    val onOpenActivity: (Long) -> Unit = {},
)

/**
 * The training plan week board (PLAN §4.2 "Training plan", P6.6): phase badge, weekly load bar,
 * seven day rows with their events / planned sessions / recorded activities, and the two entry
 * points into the suggester and the manual editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(nav: TrainingNavActions, modifier: Modifier = Modifier) {
    val vm = rememberVm { graph ->
        TrainingViewModel(
            planRepo = graph.planRepo,
            suggestionRepo = graph.suggestionRepo,
            calendarRepo = graph.calendarRepo,
            goalRepo = graph.goalRepo,
            settingsRepo = graph.settings,
            clock = graph.clock,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }
    LaunchedEffect(state.reviewReady) {
        if (state.reviewReady) {
            vm.consumeReviewReady()
            nav.onReviewSuggestions()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.planName ?: "Training") },
                actions = {
                    TextButton(onClick = vm::showCurrentWeek, enabled = !state.isCurrentWeek) {
                        Text("This week")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        TrainingContent(
            state = state,
            actions = PlannedSessionActions(
                onToggleLock = { session -> vm.setLocked(session.id, !session.locked) },
                onEdit = nav.onEditSession,
                onMarkDone = vm::markDone,
                onSkip = vm::skip,
                onReopen = vm::reopen,
                onDelete = vm::delete,
            ),
            nav = nav,
            onPreviousWeek = vm::showPreviousWeek,
            onNextWeek = vm::showNextWeek,
            onSelectDay = vm::selectDay,
            onGenerate = vm::generateSuggestions,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
    }
}

@Composable
internal fun TrainingContent(
    state: TrainingUiState,
    actions: PlannedSessionActions,
    nav: TrainingNavActions,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onSelectDay: (Long) -> Unit,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            WeekPager(
                label = state.weekLabel,
                onPrevious = onPreviousWeek,
                onNext = onNextWeek,
            )
        }
        item { PlanHeaderCard(state = state, onGenerate = onGenerate, nav = nav) }
        if (state.isEmptyWeek) {
            item {
                EmptyState(
                    title = "No sessions this week",
                    message = "Generate a week of suggestions from your goals, calendar and current " +
                        "load, review them, then accept the ones you want. You can always add a " +
                        "session by hand.",
                    actionLabel = "Generate suggestions",
                    onAction = onGenerate,
                )
            }
        }
        items(state.week.days, key = { it.day }) { row ->
            WeekDayRow(
                row = row,
                isSelected = row.day == state.selectedDay,
                actions = actions,
                onSelectDay = onSelectDay,
                onAddSession = nav.onAddSession,
                onOpenActivity = nav.onOpenActivity,
            )
        }
    }
}

@Composable
private fun WeekPager(label: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous week")
        }
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next week")
        }
    }
}

@Composable
private fun PlanHeaderCard(
    state: TrainingUiState,
    onGenerate: () -> Unit,
    nav: TrainingNavActions,
) {
    SectionCard(
        title = "This block",
        action = {
            state.phase?.let { phase ->
                AssistChip(onClick = {}, enabled = false, label = { Text(phase.label()) })
            }
        },
    ) {
        WeeklyLoadBar(state.loads)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onGenerate, enabled = !state.isGenerating) {
                if (state.isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(text = "Generate suggestions", modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = { nav.onAddSession(state.selectedDay) }) { Text("+ Session") }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TrainingContentPreview() {
    val monday = 20_709L
    MyHealthTheme(dynamicColor = false) {
        Column {
            TrainingContent(
                state = TrainingUiState(
                    isLoading = false,
                    today = monday + 1,
                    week = TrainingWeek(
                        startDay = monday,
                        offset = 0,
                        days = (0L until 7L).map { offset ->
                            TrainingDayRow(
                                day = monday + offset,
                                isToday = offset == 1L,
                                events = emptyList(),
                                planned = if (offset == 1L) listOf(previewSession()) else emptyList(),
                                activities = emptyList(),
                            )
                        },
                    ),
                    planName = "My plan",
                    phase = TrainingPhase.BUILD,
                    loads = WeeklyLoadSums(planned = 420.0, target = 620.0, actual = 300.0),
                    selectedDay = monday + 1,
                ),
                actions = PlannedSessionActions(),
                nav = TrainingNavActions(),
                onPreviousWeek = {},
                onNextWeek = {},
                onSelectDay = {},
                onGenerate = {},
            )
        }
    }
}
