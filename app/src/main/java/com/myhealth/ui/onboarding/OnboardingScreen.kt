package com.myhealth.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.myhealth.domain.model.NeatLevel
import com.myhealth.domain.model.Sex
import com.myhealth.domain.model.SportGroup
import com.myhealth.ui.common.DatePickerField
import com.myhealth.ui.common.DropdownField
import com.myhealth.ui.common.NumberField
import com.myhealth.ui.common.SectionCard
import com.myhealth.ui.theme.MyHealthTheme
import java.time.LocalDate

@Composable
fun OnboardingScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val vm = rememberVm { graph -> OnboardingViewModel(graph.profileRepo, graph.bodyRepo, graph.settings, graph.syncScheduler, graph.clock) }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            if (event is OnboardingEvent.Saved) onDone()
        }
    }

    OnboardingContent(
        state = state,
        onDraftChange = vm::updateDraft,
        onBack = vm::back,
        onNext = vm::next,
        modifier = modifier,
    )
}

@Composable
private fun OnboardingContent(
    state: OnboardingUiState,
    onDraftChange: ((OnboardingDraft) -> OnboardingDraft) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val steps = OnboardingStep.entries
    val stepIndex = steps.indexOf(state.step)

    Column(modifier = modifier.fillMaxSize()) {
        LinearProgressIndicator(
            progress = { (stepIndex + 1f) / steps.size },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Step ${stepIndex + 1} of ${steps.size}: ${state.step.title()}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                when (state.step) {
                    OnboardingStep.IDENTITY -> IdentityStep(state.draft, state.errors, onDraftChange)
                    OnboardingStep.BODY -> BodyStep(state.draft, state.errors, onDraftChange)
                    OnboardingStep.PREFERENCES -> PreferencesStep(state.draft, onDraftChange)
                }
            }
            if (state.saveError != null) {
                item { Text(text = state.saveError, color = MaterialTheme.colorScheme.error) }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (stepIndex > 0) {
                OutlinedButton(onClick = onBack, enabled = !state.isSaving) { Text(stringResource(com.myhealth.R.string.action_back)) }
            } else {
                Column {}
            }
            Button(onClick = onNext, enabled = state.canContinue && !state.isSaving) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Text(stringResource(if (state.isLastStep) com.myhealth.R.string.action_finish else com.myhealth.R.string.action_next))
            }
        }
    }
}

private fun OnboardingStep.title(): String = when (this) {
    OnboardingStep.IDENTITY -> "About you"
    OnboardingStep.BODY -> "Body & goals"
    OnboardingStep.PREFERENCES -> "Preferences"
}

@Composable
private fun IdentityStep(
    draft: OnboardingDraft,
    errors: Map<OnboardingField, String>,
    onDraftChange: ((OnboardingDraft) -> OnboardingDraft) -> Unit,
) {
    SectionCard(title = "About you") {
        OutlinedTextField(
            value = draft.displayName,
            onValueChange = { name -> onDraftChange { it.copy(displayName = name) } },
            label = { Text("Name") },
            singleLine = true,
            isError = errors.containsKey(OnboardingField.NAME),
            supportingText = errors[OnboardingField.NAME]?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        SexPicker(sex = draft.sex, onSexChange = { sex -> onDraftChange { it.copy(sex = sex) } })
        DatePickerField(
            label = "Birth date",
            value = draft.birthDay,
            onValueChange = { day -> onDraftChange { it.copy(birthDay = day) } },
            isError = errors.containsKey(OnboardingField.BIRTH_DATE),
            supportingText = errors[OnboardingField.BIRTH_DATE],
        )
    }
}

@Composable
private fun SexPicker(sex: Sex, onSexChange: (Sex) -> Unit) {
    DropdownField(
        label = "Sex",
        options = Sex.entries,
        selected = sex,
        optionLabel = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
        onSelect = onSexChange,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BodyStep(
    draft: OnboardingDraft,
    errors: Map<OnboardingField, String>,
    onDraftChange: ((OnboardingDraft) -> OnboardingDraft) -> Unit,
) {
    SectionCard(title = "Body") {
        NumberField(
            label = "Height",
            value = draft.heightCm,
            onValueChange = { v -> onDraftChange { it.copy(heightCm = v) } },
            suffix = "cm",
            decimals = 0,
            isError = errors.containsKey(OnboardingField.HEIGHT),
            supportingText = errors[OnboardingField.HEIGHT],
        )
        NumberField(
            label = "Current weight",
            value = draft.weightKg,
            onValueChange = { v -> onDraftChange { it.copy(weightKg = v) } },
            suffix = "kg",
            decimals = 1,
            isError = errors.containsKey(OnboardingField.WEIGHT),
            supportingText = errors[OnboardingField.WEIGHT],
        )
    }
    SectionCard(title = "Goals") {
        NumberField(
            label = "Goal weight (optional)",
            value = draft.goalWeightKg,
            onValueChange = { v -> onDraftChange { it.copy(goalWeightKg = v) } },
            suffix = "kg",
            decimals = 1,
            isError = errors.containsKey(OnboardingField.GOAL_WEIGHT),
            supportingText = errors[OnboardingField.GOAL_WEIGHT],
        )
        NumberField(
            label = "Goal pace",
            value = draft.goalPaceKgPerWeek,
            onValueChange = { v -> onDraftChange { it.copy(goalPaceKgPerWeek = v) } },
            suffix = "kg/week",
            decimals = 2,
            allowNegative = true,
            isError = errors.containsKey(OnboardingField.GOAL_PACE),
            supportingText = errors[OnboardingField.GOAL_PACE] ?: "Negative to lose weight, positive to gain.",
        )
        NeatLevelPicker(level = draft.neatLevel, onLevelChange = { level -> onDraftChange { it.copy(neatLevel = level) } })
    }
}

@Composable
private fun NeatLevelPicker(level: NeatLevel, onLevelChange: (NeatLevel) -> Unit) {
    DropdownField(
        label = "Daily activity level",
        options = NeatLevel.entries,
        selected = level,
        optionLabel = { it.label() },
        onSelect = onLevelChange,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun NeatLevel.label(): String = when (this) {
    NeatLevel.DESK -> "Desk job, mostly sitting"
    NeatLevel.LIGHT_ACTIVE -> "Light activity, on your feet sometimes"
    NeatLevel.ACTIVE -> "Active, on your feet most of the day"
    NeatLevel.PHYSICAL_JOB -> "Physical job, manual labor"
}

@Composable
private fun PreferencesStep(
    draft: OnboardingDraft,
    onDraftChange: ((OnboardingDraft) -> OnboardingDraft) -> Unit,
) {
    SectionCard(title = "Weekly sessions") {
        SportGroup.entries.filter { it in draft.sessionsPerWeek }.forEach { group ->
            NumberField(
                label = "${group.name.lowercase().replaceFirstChar { it.uppercase() }} sessions / week",
                value = draft.sessionsPerWeek[group]?.toDouble(),
                onValueChange = { v ->
                    val count = (v ?: 0.0).toInt().coerceIn(0, 14)
                    onDraftChange { it.copy(sessionsPerWeek = it.sessionsPerWeek + (group to count)) }
                },
                decimals = 0,
            )
        }
    }
    SectionCard(title = "Recovery") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Mobility on rest days")
            Switch(
                checked = draft.mobilityOnRestDays,
                onCheckedChange = { v -> onDraftChange { it.copy(mobilityOnRestDays = v) } },
            )
        }
        NumberField(
            label = "Sleep target",
            value = draft.sleepTargetHours,
            onValueChange = { v -> onDraftChange { it.copy(sleepTargetHours = v) } },
            suffix = "h",
            decimals = 1,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingContentIdentityPreview() {
    MyHealthTheme(dynamicColor = false) {
        OnboardingContent(state = OnboardingUiState(), onDraftChange = {}, onBack = {}, onNext = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingContentBodyPreview() {
    MyHealthTheme(dynamicColor = false) {
        OnboardingContent(
            state = OnboardingUiState(
                step = OnboardingStep.BODY,
                draft = OnboardingDraft(displayName = "Robert", birthDay = LocalDate.of(1990, 1, 1)),
            ),
            onDraftChange = {},
            onBack = {},
            onNext = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingContentPreferencesPreview() {
    MyHealthTheme(dynamicColor = false) {
        OnboardingContent(
            state = OnboardingUiState(
                step = OnboardingStep.PREFERENCES,
                draft = OnboardingDraft(
                    displayName = "Robert",
                    birthDay = LocalDate.of(1990, 1, 1),
                    heightCm = 180.0,
                    weightKg = 78.0,
                ),
            ),
            onDraftChange = {},
            onBack = {},
            onNext = {},
        )
    }
}
