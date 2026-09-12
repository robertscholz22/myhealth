package com.myhealth.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhealth.di.HcStatus
import com.myhealth.di.appGraph
import com.myhealth.di.rememberVm
import com.myhealth.ui.common.DatePickerField
import com.myhealth.ui.common.SectionCard
import com.myhealth.ui.theme.MyHealthTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

/**
 * Falls back to the raw string when the SDK's own (deprecated)
 * `HealthConnectClient.getHealthConnectSettingsAction()` accessor is unavailable to callers.
 */
private const val ACTION_HEALTH_CONNECT_SETTINGS = "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"

@Composable
fun IntegrationsScreen(modifier: Modifier = Modifier) {
    val graph = appGraph()
    val vm = rememberVm { g -> IntegrationsViewModel(g.hcIntegration, g.syncStateRepo, g.syncScheduler, g.clock) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val contract = remember(graph) { graph.hcIntegration.permissionContract() }
    val launcher = rememberLauncherForActivityResult(contract) { result ->
        vm.onPermissionsResult(result)
        vm.refreshGranted()
    }

    IntegrationsContent(
        state = state,
        onGrantPermissions = { launcher.launch(graph.hcIntegration.allPermissions) },
        onOpenPlayStore = { openPlayStore(context) },
        onOpenHcSettings = { openHealthConnectSettings(context) },
        onSyncNow = vm::syncNow,
        onBackfillStartDayChange = vm::setBackfillStartDay,
        onStartBackfill = vm::startBackfill,
        modifier = modifier,
    )
}

@Composable
private fun IntegrationsContent(
    state: IntegrationsUiState,
    onGrantPermissions: () -> Unit,
    onOpenPlayStore: () -> Unit,
    onOpenHcSettings: () -> Unit,
    onSyncNow: () -> Unit,
    onBackfillStartDayChange: (Long) -> Unit,
    onStartBackfill: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { StatusSection(state.status, onOpenPlayStore) }
        if (state.status == HcStatus.AVAILABLE) {
            item {
                PermissionsSection(
                    permissions = state.permissions,
                    onGrantPermissions = onGrantPermissions,
                    onOpenHcSettings = onOpenHcSettings,
                )
            }
            item { SyncSection(state.isSyncing, state.syncChannels, onSyncNow) }
            item {
                BackfillSection(
                    startDay = state.backfillStartDay,
                    completeDay = state.backfillCompleteDay,
                    isRunning = state.isBackfillRunning,
                    onStartDayChange = onBackfillStartDayChange,
                    onStartBackfill = onStartBackfill,
                )
            }
        }
    }
}

@Composable
private fun StatusSection(status: HcStatus, onOpenPlayStore: () -> Unit) {
    SectionCard(title = "Health Connect") {
        when (status) {
            HcStatus.AVAILABLE -> Text("Available.", style = MaterialTheme.typography.bodyMedium)
            HcStatus.UPDATE_REQUIRED -> {
                Text("An update is required.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onOpenPlayStore) { Text("Update in Play Store") }
            }
            HcStatus.UNAVAILABLE -> {
                Text("Not installed on this device.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onOpenPlayStore) { Text("Install from Play Store") }
            }
        }
    }
}

@Composable
private fun PermissionsSection(
    permissions: List<PermissionRow>,
    onGrantPermissions: () -> Unit,
    onOpenHcSettings: () -> Unit,
) {
    SectionCard(title = "Permissions") {
        permissions.forEach { row -> PermissionLine(row) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onGrantPermissions) { Text("Grant permissions") }
            OutlinedButton(onClick = onOpenHcSettings) { Text("Open Health Connect settings") }
        }
    }
}

@Composable
private fun PermissionLine(row: PermissionRow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (row.granted) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Granted",
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(20.dp),
            )
        } else {
            Icon(
                Icons.Filled.Cancel,
                contentDescription = "Not granted",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(row.label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SyncSection(isSyncing: Boolean, channels: List<SyncChannelRow>, onSyncNow: () -> Unit) {
    SectionCard(title = "Sync") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onSyncNow, enabled = !isSyncing) { Text("Sync now") }
            if (isSyncing) CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
        channels.forEach { channel -> SyncChannelLine(channel) }
    }
}

@Composable
private fun SyncChannelLine(channel: SyncChannelRow) {
    val lastSync = channel.lastSuccessAtMillis?.let { formatInstant(it) } ?: "Never synced"
    Text("${channel.label}: $lastSync", style = MaterialTheme.typography.bodyMedium)
    channel.lastError?.let { error ->
        Text(
            "Last error: $error",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun BackfillSection(
    startDay: Long,
    completeDay: Long?,
    isRunning: Boolean,
    onStartDayChange: (Long) -> Unit,
    onStartBackfill: () -> Unit,
) {
    SectionCard(title = "Backfill history") {
        Text(
            "Requires the \"Full history\" permission above, or Health Connect only returns the last 30 days.",
            style = MaterialTheme.typography.bodySmall,
        )
        DatePickerField(
            label = "Backfill from",
            value = LocalDate.ofEpochDay(startDay),
            onValueChange = { onStartDayChange(it.toEpochDay()) },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onStartBackfill, enabled = !isRunning) { Text("Start backfill") }
            if (isRunning) CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
        val progressText = completeDay?.let { "Backfilled back to ${LocalDate.ofEpochDay(it)}" }
            ?: "No backfill has run yet."
        Text(progressText, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatInstant(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

private fun openPlayStore(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE"))
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // No Play Store on this device — nothing more we can do from here.
    }
}

private fun openHealthConnectSettings(context: Context) {
    val intent = Intent(ACTION_HEALTH_CONNECT_SETTINGS)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Health Connect is not installed — the status card already says so.
    }
}

@Preview(showBackground = true, name = "Available")
@Composable
private fun IntegrationsContentAvailablePreview() {
    MyHealthTheme(dynamicColor = false) {
        IntegrationsContent(
            state = IntegrationsUiState(
                status = HcStatus.AVAILABLE,
                permissions = listOf(
                    PermissionRow("android.permission.health.READ_EXERCISE", "Workouts", granted = true),
                    PermissionRow("android.permission.health.READ_SLEEP", "Sleep", granted = false),
                ),
                syncChannels = listOf(
                    SyncChannelRow("hc.exercise", "Workouts", lastSuccessAtMillis = 0L, lastError = null),
                    SyncChannelRow("hc.body", "Body measurements", lastSuccessAtMillis = null, lastError = "storage: disk full"),
                ),
                backfillStartDay = LocalDate.of(2025, 9, 12).toEpochDay(),
                backfillCompleteDay = LocalDate.of(2026, 1, 1).toEpochDay(),
            ),
            onGrantPermissions = {},
            onOpenPlayStore = {},
            onOpenHcSettings = {},
            onSyncNow = {},
            onBackfillStartDayChange = {},
            onStartBackfill = {},
        )
    }
}

@Preview(showBackground = true, name = "Update required")
@Composable
private fun IntegrationsContentUpdateRequiredPreview() {
    MyHealthTheme(dynamicColor = false) {
        IntegrationsContent(
            state = IntegrationsUiState(status = HcStatus.UPDATE_REQUIRED),
            onGrantPermissions = {},
            onOpenPlayStore = {},
            onOpenHcSettings = {},
            onSyncNow = {},
            onBackfillStartDayChange = {},
            onStartBackfill = {},
        )
    }
}

@Preview(showBackground = true, name = "Not installed")
@Composable
private fun IntegrationsContentUnavailablePreview() {
    MyHealthTheme(dynamicColor = false) {
        IntegrationsContent(
            state = IntegrationsUiState(status = HcStatus.UNAVAILABLE),
            onGrantPermissions = {},
            onOpenPlayStore = {},
            onOpenHcSettings = {},
            onSyncNow = {},
            onBackfillStartDayChange = {},
            onStartBackfill = {},
        )
    }
}
