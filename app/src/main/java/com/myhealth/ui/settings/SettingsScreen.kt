package com.myhealth.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhealth.di.rememberVm
import com.myhealth.domain.model.AppSettings
import com.myhealth.domain.model.NeatLevel
import com.myhealth.domain.model.Profile
import com.myhealth.domain.model.Sex
import com.myhealth.domain.model.SportGroup
import com.myhealth.ui.common.DatePickerField
import com.myhealth.ui.common.DropdownField
import com.myhealth.ui.common.NumberField
import com.myhealth.ui.common.SectionCard
import com.myhealth.ui.common.decodePreferredSports
import com.myhealth.ui.common.encodePreferredSports
import com.myhealth.ui.theme.MyHealthTheme
import java.time.LocalDate

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val vm = rememberVm { graph -> SettingsViewModel(graph.profileRepo, graph.settings, graph.syncScheduler, graph.clock) }
    val state by vm.state.collectAsStateWithLifecycle()

    SettingsContent(
        state = state,
        onProfileChange = vm::onProfileChange,
        onSettingsChange = vm::onSettingsChange,
        modifier = modifier,
    )
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    onProfileChange: (Profile) -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val profile = state.profile
        if (profile != null) {
            item { ProfileSection(profile = profile, onProfileChange = onProfileChange) }
        } else if (!state.isLoading) {
            item { Text("No profile yet.") }
        }
        item { AppPreferencesSection(settings = state.settings, onSettingsChange = onSettingsChange) }
        item { AdvancedSection(settings = state.settings, onSettingsChange = onSettingsChange) }
    }
}

@Composable
private fun ProfileSection(profile: Profile, onProfileChange: (Profile) -> Unit) {
    SectionCard(title = "Profile") {
        OutlinedTextField(
            value = profile.displayName,
            onValueChange = { onProfileChange(profile.copy(displayName = it)) },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownField(
            label = "Sex",
            options = Sex.entries,
            selected = profile.sex,
            optionLabel = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
            onSelect = { onProfileChange(profile.copy(sex = it)) },
        )
        DatePickerField(
            label = "Birth date",
            value = LocalDate.ofEpochDay(profile.birthDay),
            onValueChange = { onProfileChange(profile.copy(birthDay = it.toEpochDay())) },
        )
        NumberField(
            label = "Height",
            value = profile.heightCm,
            onValueChange = { it?.let { v -> onProfileChange(profile.copy(heightCm = v)) } },
            suffix = "cm",
            decimals = 0,
        )
        NumberField(
            label = "Goal weight (optional)",
            value = profile.goalWeightKg,
            onValueChange = { onProfileChange(profile.copy(goalWeightKg = it)) },
            suffix = "kg",
            decimals = 1,
        )
        NumberField(
            label = "Goal pace",
            value = profile.goalPaceKgPerWeek,
            onValueChange = { it?.let { v -> onProfileChange(profile.copy(goalPaceKgPerWeek = v)) } },
            suffix = "kg/week",
            decimals = 2,
            allowNegative = true,
        )
        DropdownField(
            label = "Daily activity level",
            options = NeatLevel.entries,
            selected = profile.neatLevel,
            optionLabel = { it.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase) },
            onSelect = { onProfileChange(profile.copy(neatLevel = it)) },
        )
        NumberField(
            label = "Resting HR override (optional)",
            value = profile.restingHrManual?.toDouble(),
            onValueChange = { onProfileChange(profile.copy(restingHrManual = it?.toInt())) },
            suffix = "bpm",
            decimals = 0,
        )
        NumberField(
            label = "Max HR override (optional)",
            value = profile.maxHrManual?.toDouble(),
            onValueChange = { onProfileChange(profile.copy(maxHrManual = it?.toInt())) },
            suffix = "bpm",
            decimals = 0,
        )
        NumberField(
            label = "Sleep target",
            value = profile.sleepTargetHours,
            onValueChange = { it?.let { v -> onProfileChange(profile.copy(sleepTargetHours = v)) } },
            suffix = "h",
            decimals = 1,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Mobility on rest days")
            Switch(
                checked = profile.mobilityOnRestDays,
                onCheckedChange = { onProfileChange(profile.copy(mobilityOnRestDays = it)) },
            )
        }
        PreferredSportsFields(profile = profile, onProfileChange = onProfileChange)
    }
}

@Composable
private fun PreferredSportsFields(profile: Profile, onProfileChange: (Profile) -> Unit) {
    val sessions = decodePreferredSports(profile.preferredSportsJson)
    listOf(SportGroup.RUN, SportGroup.STRENGTH, SportGroup.SOCCER).forEach { group ->
        NumberField(
            label = "${group.name.lowercase().replaceFirstChar(Char::uppercase)} sessions / week cap",
            value = (sessions[group] ?: 0).toDouble(),
            onValueChange = { v ->
                val updated = sessions + (group to (v ?: 0.0).toInt().coerceIn(0, 14))
                onProfileChange(profile.copy(preferredSportsJson = encodePreferredSports(updated)))
            },
            decimals = 0,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsContentPreview() {
    MyHealthTheme(dynamicColor = false) {
        SettingsContent(
            state = SettingsUiState(
                isLoading = false,
                profile = Profile(
                    displayName = "Robert",
                    sex = Sex.MALE,
                    birthDay = LocalDate.of(1990, 1, 1).toEpochDay(),
                    heightCm = 180.0,
                    createdAtMillis = 0,
                    updatedAtMillis = 0,
                ),
            ),
            onProfileChange = {},
            onSettingsChange = {},
        )
    }
}
