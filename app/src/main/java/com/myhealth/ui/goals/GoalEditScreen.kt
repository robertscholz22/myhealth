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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhealth.R
import com.myhealth.di.rememberVm
import com.myhealth.domain.engine.goal.GoalProgress
import com.myhealth.domain.engine.running.CanonicalDistances
import com.myhealth.domain.model.GoalStatus
import com.myhealth.domain.model.GoalType
import com.myhealth.ui.common.DatePickerField
import com.myhealth.ui.common.DropdownField
import com.myhealth.ui.common.ErrorBanner
import com.myhealth.ui.common.NumberField
import com.myhealth.ui.common.SCREEN_PADDING
import com.myhealth.ui.common.SectionCard
import com.myhealth.ui.common.resolve
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
                title = { Text(stringResource(if (state.isNew) R.string.goal_edit_title_new else R.string.goal_edit_title_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = vm::requestDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.goal_edit_delete_content_description))
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
            title = { Text(stringResource(R.string.goal_edit_delete_dialog_title)) },
            text = { Text(stringResource(R.string.goal_edit_delete_dialog_message)) },
            confirmButton = { TextButton(onClick = vm::confirmDelete) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = vm::cancelDelete) { Text(stringResource(R.string.action_cancel)) } },
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
        contentPadding = PaddingValues(SCREEN_PADDING),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.saveError?.let { message ->
            item("saveError") { ErrorBanner(message = message.resolve()) }
        }
        state.loadError?.let { message ->
            item("loadError") { ErrorBanner(message = message.resolve()) }
        }
        item {
            SectionCard(title = stringResource(R.string.goal_edit_section_goal)) {
                DropdownField(
                    label = stringResource(R.string.goal_edit_type_label),
                    options = GoalType.entries,
                    selected = draft.type,
                    optionLabel = ::goalTypeLabel,
                    onSelect = { type -> onChange { it.withType(type) } },
                )
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { value -> onChange { it.copy(title = value) } },
                    label = { Text(stringResource(R.string.goal_edit_title_label)) },
                    isError = state.errors.containsKey(GoalField.TITLE),
                    supportingText = state.errors[GoalField.TITLE]?.let { { Text(it.resolve()) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TypeFields(state = state, onChange = onChange, onLinkRace = onLinkRace)
                DatePickerField(
                    label = stringResource(R.string.goal_edit_target_date_label),
                    value = draft.targetDay,
                    onValueChange = { date -> onChange { it.copy(targetDay = date) } },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.goal_edit_primary_label))
                    Switch(
                        checked = draft.isPrimary,
                        onCheckedChange = { value -> onChange { it.copy(isPrimary = value) } },
                    )
                }
                OutlinedTextField(
                    value = draft.notes,
                    onValueChange = { value -> onChange { it.copy(notes = value) } },
                    label = { Text(stringResource(R.string.goal_edit_notes_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            Button(onClick = onSave, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.isNew) R.string.goal_edit_create_action else R.string.goal_edit_save_action))
            }
        }
        if (!state.isNew) {
            item {
                SectionCard(title = stringResource(R.string.goal_edit_section_status)) {
                    Text(stringResource(R.string.goal_edit_current_status, goalStatusLabel(draft.status)))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onStatus(GoalStatus.ACHIEVED) }) {
                            Text(stringResource(R.string.goals_action_achieved))
                        }
                        TextButton(onClick = { onStatus(GoalStatus.ABANDONED) }) {
                            Text(stringResource(R.string.goal_edit_status_abandoned))
                        }
                        TextButton(onClick = { onStatus(GoalStatus.ACTIVE) }) {
                            Text(stringResource(R.string.goal_edit_status_active))
                        }
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
                label = stringResource(R.string.goal_edit_distance_label),
                options = CanonicalDistances.ALL,
                selected = draft.targetDistanceMeters ?: CanonicalDistances.FIVE_KM,
                optionLabel = { GoalProgress.distanceLabel(it) },
                onSelect = { meters -> onChange { it.copy(targetDistanceMeters = meters) } },
            )
            // BUG-8: the picker used to swallow its own error, so a failed save looked like a
            // dead button. The message now sits directly under the field, like every other one.
            state.errors[GoalField.DISTANCE]?.let { message ->
                Text(
                    text = message.resolve(),
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
                    label = stringResource(R.string.goal_edit_target_min_label),
                    value = draft.targetMinutes?.toDouble(),
                    onValueChange = { v -> onChange { it.copy(targetMinutes = v?.toInt()) } },
                    decimals = 0,
                    isError = state.errors.containsKey(GoalField.TIME),
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    label = stringResource(R.string.goal_edit_target_sec_label),
                    value = draft.targetSeconds?.toDouble(),
                    onValueChange = { v -> onChange { it.copy(targetSeconds = v?.toInt()) } },
                    decimals = 0,
                    isError = state.errors.containsKey(GoalField.TIME),
                    supportingText = state.errors[GoalField.TIME]?.resolve(),
                    modifier = Modifier.weight(1f),
                )
            }
            if (state.races.isNotEmpty()) {
                val notLinkedLabel = stringResource(R.string.goal_edit_race_not_linked)
                val raceOptionFormat = stringResource(R.string.goal_edit_race_option_with_date)
                val none = RaceOption(-1L, notLinkedLabel, 0L)
                DropdownField(
                    label = stringResource(R.string.goal_edit_linked_race_label),
                    options = listOf(none) + state.races,
                    selected = state.races.firstOrNull { it.eventId == draft.linkedEventId } ?: none,
                    optionLabel = { option ->
                        if (option.eventId == -1L) {
                            option.title
                        } else {
                            String.format(raceOptionFormat, option.title, LocalDate.ofEpochDay(option.day).toString())
                        }
                    },
                    onSelect = { option -> onLinkRace(option.takeIf { it.eventId != -1L }) },
                )
            }
        }
        GoalType.BODY_WEIGHT -> NumberField(
            label = stringResource(R.string.goal_edit_target_weight_label),
            value = draft.targetWeightKg,
            onValueChange = { v -> onChange { it.copy(targetWeightKg = v) } },
            suffix = stringResource(R.string.goal_edit_kg_suffix),
            decimals = 1,
            isError = state.errors.containsKey(GoalField.WEIGHT),
            supportingText = state.errors[GoalField.WEIGHT]?.resolve(),
        )
        GoalType.CONSISTENCY -> NumberField(
            label = stringResource(R.string.goal_edit_sessions_label),
            value = draft.targetValue,
            onValueChange = { v -> onChange { it.copy(targetValue = v) } },
            decimals = 1,
            isError = state.errors.containsKey(GoalField.VALUE),
            supportingText = state.errors[GoalField.VALUE]?.resolve(),
        )
        // Watts / hours-per-week: a single number is the whole form (P12.4).
        GoalType.STRENGTH_LIFT, GoalType.SOCCER_AVAILABILITY, GoalType.BIKE_FTP, GoalType.BIKE_VOLUME ->
            NumberField(
                label = stringResource(
                    when (draft.type) {
                        GoalType.STRENGTH_LIFT -> R.string.goal_edit_target_lift_label
                        GoalType.BIKE_FTP -> R.string.goal_edit_target_ftp_label
                        GoalType.BIKE_VOLUME -> R.string.goal_edit_target_ride_hours_label
                        else -> R.string.goal_edit_target_matches_label
                    },
                ),
                value = draft.targetValue,
                onValueChange = { v -> onChange { it.copy(targetValue = v) } },
                decimals = 1,
                isError = state.errors.containsKey(GoalField.VALUE),
                supportingText = state.errors[GoalField.VALUE]?.resolve(),
            )
        // Distance is required; the target time is optional (a date-only event is tracked
        // manually by GoalProgress.bikeEvent, P12.2) — the shared target-date field below covers
        // "optional date".
        GoalType.BIKE_EVENT -> {
            DropdownField(
                label = stringResource(R.string.goal_edit_distance_label),
                options = BIKE_EVENT_DISTANCES,
                selected = draft.targetDistanceMeters?.takeIf { it in BIKE_EVENT_DISTANCES }
                    ?: BIKE_EVENT_DEFAULT_DISTANCE_METERS,
                optionLabel = { GoalProgress.distanceLabel(it) },
                onSelect = { meters -> onChange { it.copy(targetDistanceMeters = meters) } },
            )
            state.errors[GoalField.DISTANCE]?.let { message ->
                Text(
                    text = message.resolve(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(stringResource(R.string.goal_edit_bike_event_time_hint), style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NumberField(
                    label = stringResource(R.string.goal_edit_target_min_label),
                    value = draft.targetMinutes?.toDouble(),
                    onValueChange = { v -> onChange { it.copy(targetMinutes = v?.toInt()) } },
                    decimals = 0,
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    label = stringResource(R.string.goal_edit_target_sec_label),
                    value = draft.targetSeconds?.toDouble(),
                    onValueChange = { v -> onChange { it.copy(targetSeconds = v?.toInt()) } },
                    decimals = 0,
                    modifier = Modifier.weight(1f),
                )
            }
        }
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
