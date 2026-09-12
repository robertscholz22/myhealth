package com.myhealth.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.myhealth.R
import com.myhealth.domain.model.AppSettings
import com.myhealth.domain.model.ThemeMode
import com.myhealth.ui.common.DropdownField
import com.myhealth.ui.common.NumberField
import com.myhealth.ui.common.SectionCard

/** The non-profile [AppSettings] keys (§4.2 Settings / P1.8). */
@Composable
internal fun AppPreferencesSection(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    SectionCard(title = stringResource(R.string.settings_section_app)) {
        DropdownField(
            label = stringResource(R.string.settings_theme_label),
            options = ThemeMode.entries,
            selected = settings.themeMode,
            optionLabel = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
            onSelect = { onSettingsChange(settings.copy(themeMode = it)) },
        )
        SwitchRow(
            label = stringResource(R.string.settings_dynamic_color_label),
            checked = settings.useDynamicColor,
            onCheckedChange = { onSettingsChange(settings.copy(useDynamicColor = it)) },
        )
        NumberField(
            label = stringResource(R.string.settings_sync_interval_label),
            value = settings.syncIntervalHours.toDouble(),
            onValueChange = { it?.let { v -> onSettingsChange(settings.copy(syncIntervalHours = v.toInt().coerceAtLeast(1))) } },
            suffix = stringResource(R.string.settings_unit_hours),
            decimals = 0,
        )
        NumberField(
            label = stringResource(R.string.settings_suggestion_horizon_label),
            value = settings.suggestionHorizonDays.toDouble(),
            onValueChange = { it?.let { v -> onSettingsChange(settings.copy(suggestionHorizonDays = v.toInt().coerceAtLeast(1))) } },
            suffix = stringResource(R.string.settings_unit_days),
            decimals = 0,
        )
        SwitchRow(
            label = stringResource(R.string.settings_include_treadmill_prs_label),
            checked = settings.includeTreadmillInPrs,
            onCheckedChange = { onSettingsChange(settings.copy(includeTreadmillInPrs = it)) },
        )
        OutlinedTextField(
            value = settings.offUserAgentContact,
            onValueChange = { onSettingsChange(settings.copy(offUserAgentContact = it)) },
            label = { Text(stringResource(R.string.settings_off_contact_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Debug / experimental toggles (§4.2 Settings). */
@Composable
internal fun AdvancedSection(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    SectionCard(title = stringResource(R.string.settings_section_advanced)) {
        SwitchRow(
            label = stringResource(R.string.settings_garmin_direct_label),
            checked = settings.garminDirectEnabled,
            onCheckedChange = { onSettingsChange(settings.copy(garminDirectEnabled = it)) },
        )
        SwitchRow(
            label = stringResource(R.string.settings_allow_destructive_migration_label),
            checked = settings.allowDestructiveMigration,
            onCheckedChange = { onSettingsChange(settings.copy(allowDestructiveMigration = it)) },
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
