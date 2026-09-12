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
import com.myhealth.domain.model.AppSettings
import com.myhealth.domain.model.ThemeMode
import com.myhealth.ui.common.DropdownField
import com.myhealth.ui.common.NumberField
import com.myhealth.ui.common.SectionCard

/** The non-profile [AppSettings] keys (§4.2 Settings / P1.8). */
@Composable
internal fun AppPreferencesSection(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    SectionCard(title = "App preferences") {
        DropdownField(
            label = "Theme",
            options = ThemeMode.entries,
            selected = settings.themeMode,
            optionLabel = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
            onSelect = { onSettingsChange(settings.copy(themeMode = it)) },
        )
        SwitchRow(
            label = "Use wallpaper colours",
            checked = settings.useDynamicColor,
            onCheckedChange = { onSettingsChange(settings.copy(useDynamicColor = it)) },
        )
        NumberField(
            label = "Sync interval",
            value = settings.syncIntervalHours.toDouble(),
            onValueChange = { it?.let { v -> onSettingsChange(settings.copy(syncIntervalHours = v.toInt().coerceAtLeast(1))) } },
            suffix = "h",
            decimals = 0,
        )
        NumberField(
            label = "Suggestion horizon",
            value = settings.suggestionHorizonDays.toDouble(),
            onValueChange = { it?.let { v -> onSettingsChange(settings.copy(suggestionHorizonDays = v.toInt().coerceAtLeast(1))) } },
            suffix = "days",
            decimals = 0,
        )
        SwitchRow(
            label = "Include treadmill runs in PRs",
            checked = settings.includeTreadmillInPrs,
            onCheckedChange = { onSettingsChange(settings.copy(includeTreadmillInPrs = it)) },
        )
        OutlinedTextField(
            value = settings.offUserAgentContact,
            onValueChange = { onSettingsChange(settings.copy(offUserAgentContact = it)) },
            label = { Text("Open Food Facts contact (for the API user agent)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Debug / experimental toggles (§4.2 Settings). */
@Composable
internal fun AdvancedSection(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    SectionCard(title = "Advanced") {
        SwitchRow(
            label = "Garmin direct client (experimental)",
            checked = settings.garminDirectEnabled,
            onCheckedChange = { onSettingsChange(settings.copy(garminDirectEnabled = it)) },
        )
        SwitchRow(
            label = "Allow destructive DB migration (debug builds only)",
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
