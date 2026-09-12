package com.myhealth.ui.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhealth.di.rememberVm
import com.myhealth.domain.model.Intensity
import com.myhealth.domain.model.SessionType
import com.myhealth.domain.model.SportType
import com.myhealth.ui.common.DatePickerField
import com.myhealth.ui.common.DropdownField
import com.myhealth.ui.common.DurationField
import com.myhealth.ui.common.NumberField
import com.myhealth.ui.common.SectionCard
import com.myhealth.ui.common.TimePickerField
import com.myhealth.ui.theme.MyHealthTheme

/** Manual planned-session form (PLAN §4.2 "Planned session edit", P6.8). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannedSessionEditScreen(
    id: Long,
    epochDay: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = rememberVm { graph ->
        PlannedSessionEditViewModel(id, epochDay, graph.planRepo, graph.clock)
    }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onBack()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New session" else "Edit session") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = vm::requestDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete session")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        PlannedSessionEditContent(
            state = state,
            onChange = vm::update,
            onSport = vm::setSport,
            onSessionType = vm::setSessionType,
            onSave = vm::save,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
    }

    if (state.pendingDelete) {
        AlertDialog(
            onDismissRequest = vm::cancelDelete,
            title = { Text("Delete this session?") },
            text = { Text("This cannot be undone.") },
            confirmButton = { TextButton(onClick = vm::confirmDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = vm::cancelDelete) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun PlannedSessionEditContent(
    state: PlannedSessionEditUiState,
    onChange: ((PlannedSessionDraft) -> PlannedSessionDraft) -> Unit,
    onSport: (SportType) -> Unit,
    onSessionType: (SessionType) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.draft
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard(title = "Session") {
                DropdownField(
                    label = "Sport",
                    options = PLANNABLE_SPORT_TYPES,
                    selected = draft.sportType,
                    optionLabel = { it.planLabel() },
                    onSelect = onSport,
                )
                DropdownField(
                    label = "Type",
                    options = state.sessionTypes,
                    selected = draft.sessionType,
                    optionLabel = { it.label() },
                    onSelect = onSessionType,
                )
                DropdownField(
                    label = "Intensity",
                    options = Intensity.entries,
                    selected = draft.intensity,
                    optionLabel = { it.label() },
                    onSelect = { intensity -> onChange { it.copy(intensity = intensity) } },
                )
            }
        }
        item {
            SectionCard(title = "When") {
                DatePickerField(
                    label = "Day",
                    value = draft.day,
                    onValueChange = { day -> onChange { it.copy(day = day) } },
                )
                TimePickerField(
                    label = "Start time (optional)",
                    value = draft.startMinuteOfDay,
                    onValueChange = { minute -> onChange { it.copy(startMinuteOfDay = minute) } },
                )
            }
        }
        item { TargetsCard(state = state, onChange = onChange) }
        item {
            SectionCard(title = "Notes") {
                OutlinedTextField(
                    value = draft.description,
                    onValueChange = { text -> onChange { it.copy(description = text) } },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Lock (the suggester will not move it)")
                    Switch(
                        checked = draft.locked,
                        onCheckedChange = { locked -> onChange { it.copy(locked = locked) } },
                    )
                }
            }
        }
        item {
            state.saveError?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
            state.loadError?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = onSave,
                enabled = !state.isSaving && state.loadError == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun TargetsCard(
    state: PlannedSessionEditUiState,
    onChange: ((PlannedSessionDraft) -> PlannedSessionDraft) -> Unit,
) {
    val draft = state.draft
    SectionCard(title = "Targets") {
        DurationField(
            value = draft.durationMin,
            onValueChange = { minutes -> onChange { it.copy(durationMin = minutes) } },
            isError = state.errors.containsKey(PlannedSessionField.DURATION),
            supportingText = state.errors[PlannedSessionField.DURATION],
        )
        NumberField(
            label = "Distance",
            value = draft.distanceKm,
            onValueChange = { km -> onChange { it.copy(distanceKm = km) } },
            suffix = "km",
            decimals = 2,
            isError = state.errors.containsKey(PlannedSessionField.DISTANCE),
            supportingText = state.errors[PlannedSessionField.DISTANCE],
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NumberField(
                label = "Pace min",
                value = draft.paceMinutes?.toDouble(),
                onValueChange = { value -> onChange { it.copy(paceMinutes = value?.toInt()) } },
                modifier = Modifier.weight(1f),
                decimals = 0,
                isError = state.errors.containsKey(PlannedSessionField.PACE),
            )
            NumberField(
                label = "Pace sec",
                value = draft.paceSeconds?.toDouble(),
                onValueChange = { value -> onChange { it.copy(paceSeconds = value?.toInt()) } },
                modifier = Modifier.weight(1f),
                decimals = 0,
                isError = state.errors.containsKey(PlannedSessionField.PACE),
            )
        }
        state.errors[PlannedSessionField.PACE]?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        draft.estimatedTrimp?.let { trimp ->
            Text(
                text = "Estimated load ${Math.round(trimp)} AU",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PlannedSessionEditContentPreview() {
    MyHealthTheme(dynamicColor = false) {
        PlannedSessionEditContent(
            state = PlannedSessionEditUiState(
                isLoading = false,
                isNew = true,
                draft = PlannedSessionDraft(
                    day = java.time.LocalDate.of(2026, 9, 15),
                    sessionType = SessionType.TEMPO_RUN,
                    intensity = Intensity.HIGH,
                    durationMin = 50,
                    distanceKm = 10.0,
                    paceMinutes = 4,
                    paceSeconds = 45,
                ),
            ),
            onChange = {},
            onSport = {},
            onSessionType = {},
            onSave = {},
        )
    }
}
