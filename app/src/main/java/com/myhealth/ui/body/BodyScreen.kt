package com.myhealth.ui.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhealth.di.rememberVm
import com.myhealth.domain.model.ActivitySource
import com.myhealth.domain.model.BodyMeasurement
import com.myhealth.ui.common.NumberField
import com.myhealth.ui.common.SectionCard
import com.myhealth.ui.common.charts.BarChartCard
import com.myhealth.ui.common.charts.ChartSeries
import com.myhealth.ui.common.charts.LineChartCard
import com.myhealth.ui.common.charts.dropGaps
import com.myhealth.ui.theme.MyHealthTheme
import com.myhealth.domain.util.toLocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BodyScreen(modifier: Modifier = Modifier) {
    val vm = rememberVm { graph ->
        BodyViewModel(graph.profileRepo, graph.bodyRepo, graph.healthRepo, graph.syncScheduler, graph.clock)
    }
    val state by vm.state.collectAsStateWithLifecycle()

    BodyContent(
        state = state,
        onRangeSelect = vm::setRange,
        onLogWeightClick = vm::openLogDialog,
        onDismissDialog = vm::dismissLogDialog,
        onSaveWeight = vm::logWeight,
        onDelete = vm::delete,
        modifier = modifier,
    )
}

@Composable
private fun BodyContent(
    state: BodyUiState,
    onRangeSelect: (BodyRange) -> Unit,
    onLogWeightClick: () -> Unit,
    onDismissDialog: () -> Unit,
    onSaveWeight: (Double, Double?) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = onLogWeightClick) {
                Icon(Icons.Filled.Add, contentDescription = "Log weight")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { CurrentWeightCard(state.latest, state.goalWeightKg, state.deltaToGoalKg) }
            item { RangeSelector(state.range, onRangeSelect) }
            item { WeightChartCard(state) }
            item { BodyFatChartCard(state) }
            item { RestingHrChartCard(state) }
            item { SleepChartCard(state) }
            item {
                Text(
                    text = "Last ${state.range.days} days",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (state.measurements.isEmpty()) {
                item {
                    Text(
                        text = "No measurements in this window. Log your weight to start tracking it here.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                items(state.measurements, key = { it.id }) { measurement ->
                    MeasurementRow(measurement = measurement, onDelete = { onDelete(measurement.id) })
                }
            }
        }
    }

    if (state.showLogDialog) {
        LogWeightDialog(onDismiss = onDismissDialog, onSave = onSaveWeight)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeSelector(selected: BodyRange, onSelect: (BodyRange) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        BodyRange.entries.forEachIndexed { index, range ->
            SegmentedButton(
                selected = range == selected,
                onClick = { onSelect(range) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = BodyRange.entries.size),
            ) { Text(range.label) }
        }
    }
}

/** Weight, its dashed 7-day average and the goal-weight line (PLAN P8.3). */
@Composable
private fun WeightChartCard(state: BodyUiState) {
    val daily = weightPoints(state.measurements, state.fromDay, state.today)
    LineChartCard(
        title = "Weight",
        series = listOf(
            // The average is computed on the daily grid, then both lines drop the un-weighed days
            // so an every-third-day routine still reads as one trend rather than a dot cloud.
            ChartSeries(name = "Weight", points = daily.dropGaps()),
            ChartSeries(name = "7-day average", points = movingAveragePoints(daily).dropGaps(), dashed = true),
        ),
        xLabels = dayAxisLabels(state.fromDay, state.today),
        yFormatter = { "%.1f".format(Locale.US, it) },
        goalLine = state.goalWeightKg,
        emptyMessage = "No weight logged in this window yet.",
    )
}

@Composable
private fun BodyFatChartCard(state: BodyUiState) {
    LineChartCard(
        title = "Body fat",
        series = listOf(
            ChartSeries(
                name = "Body fat %",
                points = bodyFatPoints(state.measurements, state.fromDay, state.today).dropGaps(),
            ),
        ),
        xLabels = dayAxisLabels(state.fromDay, state.today),
        yFormatter = { "%.1f".format(Locale.US, it) },
        emptyMessage = "No body-fat readings in this window yet.",
    )
}

@Composable
private fun RestingHrChartCard(state: BodyUiState) {
    LineChartCard(
        title = "Resting heart rate",
        series = listOf(
            ChartSeries(name = "Resting HR", points = restingHrPoints(state.health, state.fromDay, state.today)),
        ),
        xLabels = dayAxisLabels(state.fromDay, state.today),
        yFormatter = { "%.0f".format(Locale.US, it) },
        emptyMessage = "No resting heart rate recorded in this window yet.",
    )
}

@Composable
private fun SleepChartCard(state: BodyUiState) {
    val hours = sleepHoursBars(state.sleep, state.sleepFromNight, state.today)
    BarChartCard(
        title = "Sleep (last $SLEEP_BAR_NIGHTS nights)",
        values = if (hours.all { it == 0.0 }) emptyList() else hours,
        xLabels = nightAxisLabels(state.sleepFromNight, state.today),
        yFormatter = { "%.1f".format(Locale.US, it) },
        highlightIndex = hours.lastIndex.takeIf { it >= 0 },
        emptyMessage = "No sleep sessions recorded in the last $SLEEP_BAR_NIGHTS nights.",
    )
}

@Composable
private fun CurrentWeightCard(latest: BodyMeasurement?, goalWeightKg: Double?, deltaToGoalKg: Double?) {
    SectionCard(title = "Current weight") {
        if (latest?.weightKg == null) {
            Text("No weight logged yet.")
        } else {
            Text(
                text = "%.1f kg".format(Locale.US, latest.weightKg),
                style = MaterialTheme.typography.headlineMedium,
            )
            if (goalWeightKg != null && deltaToGoalKg != null) {
                val delta = "%.1f".format(Locale.US, kotlin.math.abs(deltaToGoalKg))
                val message = when {
                    kotlin.math.abs(deltaToGoalKg) < 0.05 -> "At your goal weight."
                    deltaToGoalKg > 0 -> "$delta kg above your $goalWeightKg kg goal."
                    else -> "$delta kg below your $goalWeightKg kg goal."
                }
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun MeasurementRow(measurement: BodyMeasurement, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(measurement.day.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE))
            Text(
                text = measurementValueLabel(measurement.weightKg, measurement.bodyFatPercent),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(com.myhealth.R.string.action_delete))
        }
    }
}

@Composable
private fun LogWeightDialog(onDismiss: () -> Unit, onSave: (Double, Double?) -> Unit) {
    var weightKg by remember { mutableStateOf<Double?>(null) }
    var bodyFatPercent by remember { mutableStateOf<Double?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log weight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(label = "Weight", value = weightKg, onValueChange = { weightKg = it }, suffix = "kg", decimals = 1)
                NumberField(
                    label = "Body fat (optional)",
                    value = bodyFatPercent,
                    onValueChange = { bodyFatPercent = it },
                    suffix = "%",
                    decimals = 1,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { weightKg?.let { onSave(it, bodyFatPercent) } },
                enabled = weightKg != null && weightKg!! in 30.0..250.0,
            ) { Text(stringResource(com.myhealth.R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(com.myhealth.R.string.action_cancel)) } },
    )
}

@Preview(showBackground = true)
@Composable
private fun BodyContentPreview() {
    MyHealthTheme(dynamicColor = false) {
        BodyContent(
            state = BodyUiState(
                isLoading = false,
                goalWeightKg = 75.0,
                today = 19990,
                measurements = listOf(
                    BodyMeasurement(
                        id = 1,
                        measuredAtMillis = 0,
                        day = 19980,
                        weightKg = 78.4,
                        bodyFatPercent = 18.0,
                        muscleMassKg = null,
                        boneMassKg = null,
                        bodyWaterPercent = null,
                        source = ActivitySource.MANUAL,
                    ),
                ),
            ),
            onRangeSelect = {},
            onLogWeightClick = {},
            onDismissDialog = {},
            onSaveWeight = { _, _ -> },
            onDelete = {},
        )
    }
}
