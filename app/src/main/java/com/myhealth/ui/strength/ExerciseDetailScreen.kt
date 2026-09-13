package com.myhealth.ui.strength

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhealth.R
import com.myhealth.di.rememberVm
import com.myhealth.domain.engine.strength.ExerciseCatalog
import com.myhealth.domain.model.Equipment
import com.myhealth.domain.model.Exercise
import com.myhealth.domain.model.MovementPattern
import com.myhealth.domain.model.MuscleGroup
import com.myhealth.domain.model.StrengthWorkout
import com.myhealth.ui.common.EmptyState
import com.myhealth.ui.common.SCREEN_PADDING
import com.myhealth.ui.common.SectionCard
import com.myhealth.ui.common.body.BodyFigure
import com.myhealth.ui.common.body.BodyFigureLegend
import com.myhealth.ui.common.body.BodyFigureLegendKind
import com.myhealth.ui.common.body.highlightFor
import com.myhealth.ui.theme.MyHealthTheme

/** One exercise, large figure + equipment/pattern/flags/cue + "Add to workout…" (PLAN §4.2
 * "Exercise detail", P14.7). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(exerciseId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm = rememberVm { graph ->
        ExercisesViewModel(graph.strengthRepo, graph.strengthWorkoutSeeder::seed, graph.clock)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val exercise = ExerciseCatalog.byId(exerciseId)
    var showPicker by remember { mutableStateOf(false) }
    var confirmedWorkout by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val addedToMessage = confirmedWorkout?.let { stringResource(R.string.exercise_detail_added_to_format, it) }

    LaunchedEffect(addedToMessage) {
        addedToMessage?.let {
            snackbar.showSnackbar(it)
            confirmedWorkout = null
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(exercise?.name ?: stringResource(R.string.exercise_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (exercise != null) {
                FloatingActionButton(onClick = { showPicker = true }) {
                    Text(stringResource(R.string.exercise_detail_add_to_workout))
                }
            }
        },
    ) { innerPadding ->
        if (exercise == null) {
            EmptyState(
                title = stringResource(R.string.exercise_detail_not_found_title),
                message = stringResource(R.string.exercise_detail_not_found_message),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        } else {
            ExerciseDetailContent(exercise = exercise, modifier = Modifier.fillMaxSize().padding(innerPadding))
        }
    }

    if (showPicker && exercise != null) {
        WorkoutPickerDialog(
            workouts = state.workouts,
            onPick = { workout ->
                vm.addToWorkout(exercise.id, workout.id)
                confirmedWorkout = workout.name
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
internal fun ExerciseDetailContent(exercise: Exercise, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(SCREEN_PADDING),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BodyFigure(
                    highlight = highlightFor(exercise),
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                )
                BodyFigureLegend(kind = BodyFigureLegendKind.PRIMARY_SECONDARY)
            }
        }
        item {
            SectionCard(title = stringResource(R.string.exercise_detail_section_details)) {
                DetailRow(stringResource(R.string.exercise_detail_equipment_label), exercise.equipment.label())
                DetailRow(stringResource(R.string.exercise_detail_pattern_label), exercise.pattern.label())
                if (exercise.unilateral) {
                    AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(R.string.exercise_detail_unilateral_flag)) })
                }
                if (exercise.isTimed) {
                    AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(R.string.exercise_detail_timed_flag)) })
                }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.exercise_detail_section_cue)) {
                Text(exercise.cue, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Text(text = "$label: $value", style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun WorkoutPickerDialog(
    workouts: List<StrengthWorkout>,
    onPick: (StrengthWorkout) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exercise_detail_pick_workout_title)) },
        text = {
            if (workouts.isEmpty()) {
                Text(stringResource(R.string.exercise_detail_pick_workout_empty))
            } else {
                LazyColumn {
                    items(workouts, key = { it.id }) { workout ->
                        Text(
                            text = workout.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(workout) }
                                .padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun ExerciseDetailContentPreview() {
    MyHealthTheme(dynamicColor = false) {
        ExerciseDetailContent(
            exercise = Exercise(
                id = "BARBELL_BENCH_PRESS",
                name = "Barbell bench press",
                primary = setOf(MuscleGroup.CHEST),
                secondary = setOf(MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS_FRONT),
                equipment = Equipment.BARBELL,
                pattern = MovementPattern.HORIZONTAL_PUSH,
                cue = "Shoulder blades pinned, bar to the lower chest, elbows about 45 degrees.",
            ),
        )
    }
}
