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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EventRepeat
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhealth.R
import com.myhealth.di.rememberVm
import com.myhealth.domain.model.TrainingPhase
import com.myhealth.ui.common.EmptyState
import com.myhealth.ui.common.CARD_CORNER_RADIUS
import com.myhealth.ui.common.SCREEN_PADDING
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
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it.resolve(context))
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
                title = { Text(state.planName ?: stringResource(R.string.nav_training)) },
                actions = {
                    TextButton(onClick = vm::showCurrentWeek, enabled = !state.isCurrentWeek) {
                        Text(stringResource(R.string.training_this_week))
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
        contentPadding = PaddingValues(SCREEN_PADDING),
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
                    title = stringResource(R.string.training_empty_title),
                    message = stringResource(R.string.training_empty_message),
                    actionLabel = stringResource(R.string.training_generate_suggestions),
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
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.training_previous_week_desc),
            )
        }
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onNext) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.training_next_week_desc),
            )
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
        title = stringResource(R.string.training_this_block_title),
        action = {
            state.phase?.let { phase ->
                AssistChip(onClick = {}, enabled = false, label = { Text(phase.label()) })
            }
        },
    ) {
        WeeklyLoadBar(state.loads)
        if (state.suggestionsStale) {
            StaleSuggestionsHint(onRegenerate = onGenerate)
        }
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
                Text(
                    text = stringResource(R.string.training_generate_suggestions),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            OutlinedButton(onClick = { nav.onAddSession(state.selectedDay) }) {
                Text(stringResource(R.string.training_add_session))
            }
        }
    }
}

/**
 * POLISH-8: the calendar changed after the open batch was generated, so its assumptions (matches,
 * blocked days, spacing) may no longer hold. Shown on Training and, in a one-line form, on the
 * Today card.
 */
@Composable
internal fun StaleSuggestionsHint(onRegenerate: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CARD_CORNER_RADIUS),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.EventRepeat,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                text = stringResource(R.string.training_suggestions_stale),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRegenerate) { Text(stringResource(R.string.training_action_regenerate)) }
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
