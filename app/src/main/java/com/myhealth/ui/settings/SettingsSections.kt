package com.myhealth.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
            // Testing hook (PLAN P10.2): every SwitchRow's Switch is otherwise indistinguishable
            // from the others by text, since the label sits next to it rather than on it.
            switchTestTag = "settings_dynamic_color_switch",
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
        // P11.3: on by default for FEMALE profiles (P11.1's onboarding/settings hooks set this),
        // but anyone can opt in or out here regardless of `sex`.
        SwitchRow(
            label = stringResource(R.string.cycle_track_switch_label),
            checked = settings.cycleTrackingEnabled,
            onCheckedChange = { onSettingsChange(settings.copy(cycleTrackingEnabled = it)) },
            switchTestTag = "settings_cycle_tracking_switch",
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
internal fun AdvancedSection(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onRemoveOrphanedImportData: () -> Unit,
    onRecomputeTrainingLoad: () -> Unit,
) {
    var confirmingCleanup by remember { mutableStateOf(false) }

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
        OutlinedButton(
            onClick = { confirmingCleanup = true },
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.settings_orphan_cleanup_button))
        }
        OutlinedButton(
            onClick = onRecomputeTrainingLoad,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.settings_recompute_load_button))
        }
        Text(
            text = stringResource(R.string.settings_recompute_load_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (confirmingCleanup) {
        OrphanCleanupDialog(
            onConfirm = {
                confirmingCleanup = false
                onRemoveOrphanedImportData()
            },
            onDismiss = { confirmingCleanup = false },
        )
    }
}

/**
 * Confirms the BUG-12b orphan cleanup (§4.2 Settings / Advanced): activities imported from files
 * before version 1.0.2 can no longer be undone individually, so this is a one-way removal.
 */
@Composable
private fun OrphanCleanupDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_orphan_cleanup_dialog_title)) },
        text = { Text(stringResource(R.string.settings_orphan_cleanup_dialog_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.settings_orphan_cleanup_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchTestTag: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = switchTestTag?.let { Modifier.testTag(it) } ?: Modifier,
        )
    }
}
