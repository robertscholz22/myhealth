package com.myhealth.ui.strength

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhealth.R
import com.myhealth.di.rememberVm
import com.myhealth.domain.model.StrengthWorkout
import com.myhealth.domain.model.StrengthWorkoutExercise
import com.myhealth.domain.model.StrengthWorkoutKind
import com.myhealth.ui.common.EmptyState
import com.myhealth.ui.common.SCREEN_PADDING
import com.myhealth.ui.common.SectionCard
import com.myhealth.ui.common.body.BodyFigure
import com.myhealth.ui.common.resolve
import com.myhealth.ui.common.body.highlightFor
import com.myhealth.ui.theme.MyHealthTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Built-in and user workouts, kind chip + exercise count + estimated minutes + a figure thumbnail
 * (PLAN §4.2 "Strength workouts", P14.7, More entry). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutsScreen(
    onBack: () -> Unit,
    onNewWorkout: () -> Unit,
    onEditWorkout: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = rememberVm { graph ->
        WorkoutsViewModel(graph.strengthRepo, graph.planRepo, graph.strengthWorkoutSeeder::seed, graph.clock)
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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.workouts_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewWorkout) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.workouts_new_content_description))
            }
        },
    ) { innerPadding ->
        WorkoutsContent(
            state = state,
            onEdit = onEditWorkout,
            onDuplicate = vm::duplicate,
            onRequestDelete = vm::requestDelete,
            onRequestPlanForDay = vm::requestPlanForDay,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
    }

    state.pendingDeleteId?.let {
        AlertDialog(
            onDismissRequest = vm::cancelDelete,
            title = { Text(stringResource(R.string.workouts_delete_confirm_title)) },
            text = { Text(stringResource(R.string.workouts_delete_confirm_message)) },
            confirmButton = { TextButton(onClick = vm::confirmDelete) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = vm::cancelDelete) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    state.planForDayId?.let { workoutId ->
        PlanForDayDialog(
            onConfirm = { day -> vm.planForDay(workoutId, day) },
            onDismiss = vm::cancelPlanForDay,
        )
    }
}

@Composable
internal fun WorkoutsContent(
    state: WorkoutsUiState,
    onEdit: (Long) -> Unit,
    onDuplicate: (Long) -> Unit,
    onRequestDelete: (Long) -> Unit,
    onRequestPlanForDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isLoading && state.workouts.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.workouts_empty_title),
            message = stringResource(R.string.workouts_empty_message),
            modifier = modifier,
        )
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(SCREEN_PADDING),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.workouts, key = { it.id }) { workout ->
            WorkoutRow(
                workout = workout,
                onClick = { onEdit(workout.id) },
                onDuplicate = { onDuplicate(workout.id) },
                onDelete = { onRequestDelete(workout.id) },
                onPlanForDay = { onRequestPlanForDay(workout.id) },
            )
        }
    }
}

@Composable
private fun WorkoutRow(
    workout: StrengthWorkout,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onPlanForDay: () -> Unit,
) {
    SectionCard(
        title = workout.name,
        modifier = Modifier.clickable(onClick = onClick),
        action = { WorkoutOverflowMenu(onDuplicate, onDelete, onPlanForDay) },
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BodyFigure(highlight = highlightFor(workout), modifier = Modifier.size(width = 60.dp, height = 66.dp))
            Column {
                AssistChip(onClick = {}, enabled = false, label = { Text(workout.kind.label()) })
                Text(
                    text = stringResource(
                        R.string.workouts_row_subtitle_format,
                        workout.exercises.size,
                        workout.estimatedMinutes,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WorkoutOverflowMenu(onDuplicate: () -> Unit, onDelete: () -> Unit, onPlanForDay: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.workouts_overflow_desc))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.workouts_action_plan_for_day)) },
                onClick = { expanded = false; onPlanForDay() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.workouts_action_duplicate)) },
                onClick = { expanded = false; onDuplicate() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                onClick = { expanded = false; onDelete() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanForDayDialog(onConfirm: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                }
            }) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun WorkoutsContentPreview() {
    MyHealthTheme(dynamicColor = false) {
        WorkoutsContent(
            state = WorkoutsUiState(
                isLoading = false,
                workouts = listOf(
                    StrengthWorkout(
                        id = 1L,
                        name = "Upper A",
                        kind = StrengthWorkoutKind.UPPER,
                        templateId = "UPPER_A",
                        isBuiltIn = true,
                        exercises = listOf(
                            StrengthWorkoutExercise(
                                id = 1L, workoutId = 1L, orderIndex = 0,
                                exerciseId = "BARBELL_BENCH_PRESS", sets = 3, reps = 10,
                            ),
                        ),
                        createdAtMillis = 0L,
                        updatedAtMillis = 0L,
                    ),
                ),
            ),
            onEdit = {},
            onDuplicate = {},
            onRequestDelete = {},
            onRequestPlanForDay = {},
        )
    }
}
