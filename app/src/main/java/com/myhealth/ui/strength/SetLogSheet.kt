package com.myhealth.ui.strength

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myhealth.R
import com.myhealth.domain.engine.strength.ExerciseCatalog
import com.myhealth.domain.model.StrengthSetLog
import com.myhealth.domain.model.StrengthWorkout
import com.myhealth.ui.common.NumberField

/** One editable set of [SetLogSheet] — a row of `StrengthWorkoutExercise` expanded per set. */
data class SetLogRow(
    val exerciseId: String,
    val exerciseName: String,
    val setIndex: Int,
    val reps: Int?,
    val seconds: Int?,
    val loadKg: Double?,
    val skipped: Boolean = false,
)

/** One [SetLogRow] per set of every row in [workout], pre-filled from its prescription — what
 * [SetLogSheet] starts from (§4.2 "Planned session edit" / "Mark done", P14.7). */
fun setLogRowsFor(workout: StrengthWorkout): List<SetLogRow> = workout.exercises.flatMap { row ->
    val name = ExerciseCatalog.byId(row.exerciseId)?.name ?: row.exerciseId
    (0 until row.sets).map { setIndex ->
        SetLogRow(
            exerciseId = row.exerciseId,
            exerciseName = name,
            setIndex = setIndex,
            reps = row.reps,
            seconds = row.seconds,
            loadKg = row.loadKg,
        )
    }
}

/** [row] as the [StrengthSetLog] the repository stores, once the caller supplies the header the
 * flat table needs (§2.2.7): the day, the planned session it completes, and a write timestamp. */
fun SetLogRow.toStrengthSetLog(day: Long, plannedSessionId: Long, completedAtMillis: Long): StrengthSetLog =
    StrengthSetLog(
        id = 0L,
        day = day,
        plannedSessionId = plannedSessionId,
        exerciseId = exerciseId,
        setIndex = setIndex,
        reps = reps,
        seconds = seconds,
        loadKg = loadKg,
        completedAtMillis = completedAtMillis,
    )

/**
 * The optional per-set log a "Mark done" on a `STRENGTH_*` session with a workout opens (PLAN
 * §4.1/§4.2, P14.7): one row per set, pre-filled from the workout's prescription, each skippable.
 * [onSave] receives only the rows still checked in; [onSkip] marks the session done with no log at
 * all. Self-contained like `ExercisePickerSheet` — its edits are local, ephemeral form state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetLogSheet(
    workout: StrengthWorkout,
    onSave: (List<SetLogRow>) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()
    var rows by remember(workout.id) { mutableStateOf(setLogRowsFor(workout)) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        Text(
            text = stringResource(R.string.set_log_title_format, workout.name),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        // POLISH-19: the list must yield to the button row below it, or Save/Skip lay out off-screen.
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            items(rows.size) { index ->
                SetLogRowItem(
                    row = rows[index],
                    onChange = { updated -> rows = rows.toMutableList().also { it[index] = updated } },
                )
                HorizontalDivider()
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.set_log_skip))
            }
            TextButton(onClick = { onSave(rows.filterNot { it.skipped }) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun SetLogRowItem(row: SetLogRow, onChange: (SetLogRow) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(checked = !row.skipped, onCheckedChange = { checked -> onChange(row.copy(skipped = !checked)) })
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.set_log_row_format, row.exerciseName, row.setIndex + 1),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (row.seconds != null) {
                    NumberField(
                        label = stringResource(R.string.workout_edit_seconds_label),
                        value = row.seconds.toDouble(),
                        onValueChange = { v -> onChange(row.copy(seconds = v?.toInt())) },
                        decimals = 0,
                        enabled = !row.skipped,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    NumberField(
                        label = stringResource(R.string.workout_edit_reps_label),
                        value = row.reps?.toDouble(),
                        onValueChange = { v -> onChange(row.copy(reps = v?.toInt())) },
                        decimals = 0,
                        enabled = !row.skipped,
                        modifier = Modifier.weight(1f),
                    )
                }
                NumberField(
                    label = stringResource(R.string.workout_edit_load_label),
                    value = row.loadKg,
                    onValueChange = { v -> onChange(row.copy(loadKg = v)) },
                    suffix = "kg",
                    enabled = !row.skipped,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
