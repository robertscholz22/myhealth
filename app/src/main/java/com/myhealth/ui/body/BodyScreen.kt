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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.myhealth.ui.theme.MyHealthTheme
import com.myhealth.domain.util.toLocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BodyScreen(modifier: Modifier = Modifier) {
    val vm = rememberVm { graph -> BodyViewModel(graph.profileRepo, graph.bodyRepo, graph.syncScheduler, graph.clock) }
    val state by vm.state.collectAsStateWithLifecycle()

    BodyContent(
        state = state,
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
            item {
                Text(
                    text = "Last 90 days",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (state.measurements.isEmpty()) {
                item {
                    Text(
                        text = "No measurements in the last 90 days. Log your weight to start tracking it here.",
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
            onLogWeightClick = {},
            onDismissDialog = {},
            onSaveWeight = { _, _ -> },
            onDelete = {},
        )
    }
}
