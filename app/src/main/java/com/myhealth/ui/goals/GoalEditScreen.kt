package com.myhealth.ui.goals

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
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
import com.myhealth.domain.engine.goal.GoalProgress
import com.myhealth.domain.engine.running.CanonicalDistances
import com.myhealth.domain.model.GoalStatus
import com.myhealth.domain.model.GoalType
import com.myhealth.ui.common.DatePickerField
import com.myhealth.ui.common.DropdownField
import com.myhealth.ui.common.ErrorBanner
import com.myhealth.ui.common.NumberField
import com.myhealth.ui.common.SectionCard
import com.myhealth.ui.theme.MyHealthTheme
import java.time.LocalDate

/** Type-dependent goal form (PLAN §4.2 "Goal edit", P6.1). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditScreen(id: Long, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm = rememberVm { graph -> GoalEditViewModel(id, graph.goalRepo, graph.calendarRepo, graph.clock) }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onBack()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New goal" else "Edit goal") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = vm::requestDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete goal")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        GoalEditContent(
            state = state,
            onChange = vm::update,
            onLinkRace = vm::linkRace,
            onSave = vm::save,
            onStatus = vm::setStatus,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
    }

    if (state.pendingDelete) {
        AlertDialog(
            onDismissRequest = vm::cancelDelete,
            title = { Text("Delete this goal?") },
            text = { Text("This cannot be undone.") },
            confirmButton = { TextButton(onClick = vm::confirmDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = vm::cancelDelete) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun GoalEditContent(
    state: GoalEditUiState,
    onChange: ((GoalDraft) -> GoalDraft) -> Unit,
    onLinkRace: (RaceOption?) -> Unit,
    onSave: () -> Unit,
    onStatus: (GoalStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.draft
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.saveError?.let { message ->
            item("saveError") { ErrorBanner(message = message) }
        }
        state.loadError?.let { message ->
            item("loadError") { ErrorBanner(message = message) }
        }
        item {
            SectionCard(title = "Goal") {
                DropdownField(
                    label = "Type",
                    options = GoalType.entries,
                    selected = draft.type,
                    optionLabel = ::goalTypeLabel,
                    onSelect = { type -> onChange { it.withType(type) } },
                )
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { value -> onChange { it.copy(title = value) } },
                    label = { Text("Title") },
                    isError = state.errors.containsKey(GoalField.TITLE),
                    supportingText = state.errors[GoalField.TITLE]?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TypeFields(state = state, onChange = onChange, onLinkRace = onLinkRace)
                DatePickerField(
                    label = "Target date (optional)",
                    value = draft.targetDay,
                    onValueChange = { date -> onChange { it.copy(targetDay = date) } },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Primary goal")
                    Switch(
                        checked = draft.isPrimary,
                        onCheckedChange = { value -> onChange { it.copy(isPrimary = value) } },
                    )
                }
                OutlinedTextField(
                    value = draft.notes,
                    onValueChange = { value -> onChange { it.copy(notes = value) } },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            Button(onClick = onSave, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.isNew) "Create goal" else "Save goal")
            }
        }
        if (!state.isNew) {
            item {
                SectionCard(title = "Status") {
                    Text("Current: ${goalStatusLabel(draft.status)}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onStatus(GoalStatus.ACHIEVED) }) { Text("Achieved") }
                        TextButton(onClick = { onStatus(GoalStatus.ABANDONED) }) { Text("Abandoned") }
                        TextButton(onClick = { onStatus(GoalStatus.ACTIVE) }) { Text("Active") }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeFields(
    state: GoalEditUiState,
    onChange: ((GoalDraft) -> GoalDraft) -> Unit,
    onLinkRace: (RaceOption?) -> Unit,
) {
    val draft = state.draft
    when (draft.type) {
        GoalType.RACE_TIME -> {
            DropdownField(
                label = "Distance",
                options = CanonicalDistances.ALL,
                selected = draft.targetDistanceMeters ?: CanonicalDistances.FIVE_KM,
                optionLabel = { GoalProgress.distanceLabel(it) },
                onSelect = { meters -> onChange { it.copy(targetDistanceMeters = meters) } },
            )
            // BUG-8: the picker used to swallow its own error, so a failed save looked like a
            // dead button. The message now sits directly under the field, like every other one.
            state.errors[GoalField.DISTANCE]?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NumberField(
                    label = "Target min",
                    value = draft.targetMinutes?.toDouble(),
                    onValueChange = { v -> onChange { it.copy(targetMinutes = v?.toInt()) } },
                    decimals = 0,
                    isError = state.errors.containsKey(GoalField.TIME),
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    label = "Target sec",
                    value = draft.targetSeconds?.toDouble(),
                    onValueChange = { v -> onChange { it.copy(targetSeconds = v?.toInt()) } },
                    decimals = 0,
                    isError = state.errors.containsKey(GoalField.TIME),
                    supportingText = state.errors[GoalField.TIME],
                    modifier = Modifier.weight(1f),
                )
            }
            if (state.races.isNotEmpty()) {
                val none = RaceOption(-1L, "Not linked", 0L)
                DropdownField(
                    label = "Linked race event",
                    options = listOf(none) + state.races,
                    selected = state.races.firstOrNull { it.eventId == draft.linkedEventId } ?: none,
                    optionLabel = { option ->
                        if (option.eventId == -1L) option.title else "${option.title} (${LocalDate.ofEpochDay(option.day)})"
                    },
                    onSelect = { option -> onLinkRace(option.takeIf { it.eventId != -1L }) },
                )
            }
        }
        GoalType.BODY_WEIGHT -> NumberField(
            label = "Target weight",
            value = draft.targetWeightKg,
            onValueChange = { v -> onChange { it.copy(targetWeightKg = v) } },
            suffix = "kg",
            decimals = 1,
            isError = state.errors.containsKey(GoalField.WEIGHT),
            supportingText = state.errors[GoalField.WEIGHT],
        )
        GoalType.CONSISTENCY -> NumberField(
            label = "Sessions per week",
            value = draft.targetValue,
            onValueChange = { v -> onChange { it.copy(targetValue = v) } },
            decimals = 1,
            isError = state.errors.containsKey(GoalField.VALUE),
            supportingText = state.errors[GoalField.VALUE],
        )
        GoalType.STRENGTH_LIFT, GoalType.SOCCER_AVAILABILITY -> NumberField(
            label = if (draft.type == GoalType.STRENGTH_LIFT) "Target lift (kg)" else "Target matches",
            value = draft.targetValue,
            onValueChange = { v -> onChange { it.copy(targetValue = v) } },
            decimals = 1,
            isError = state.errors.containsKey(GoalField.VALUE),
            supportingText = state.errors[GoalField.VALUE],
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GoalEditContentPreview() {
    MyHealthTheme {
        GoalEditContent(
            state = GoalEditUiState(
                isLoading = false,
                isNew = true,
                draft = GoalDraft(
                    type = GoalType.RACE_TIME,
                    title = "Sub-20 5k",
                    targetDistanceMeters = 5000.0,
                    targetMinutes = 20,
                    targetSeconds = 0,
                    isPrimary = true,
                ),
            ),
            onChange = {},
            onLinkRace = {},
            onSave = {},
            onStatus = {},
        )
    }
}
