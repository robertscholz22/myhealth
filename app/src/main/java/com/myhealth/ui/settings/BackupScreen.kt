package com.myhealth.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhealth.di.rememberVm
import com.myhealth.domain.repository.BackupMode
import com.myhealth.domain.repository.BackupSummary
import com.myhealth.ui.common.ErrorBanner
import com.myhealth.ui.common.SectionCard
import com.myhealth.ui.theme.MyHealthTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val BACKUP_TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

/**
 * Backup & restore (PLAN §4.2 "Backup", P8.4): export the whole database into a JSON document the
 * user picks, and read one back in either MERGE (add what is missing) or REPLACE (wipe and
 * restore) mode. Settings and credentials are never part of a backup.
 */
@Composable
fun BackupScreen(modifier: Modifier = Modifier) {
    val vm = rememberVm { graph -> BackupViewModel(graph.backupRepo) }
    val state by vm.state.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(CreateDocument(BACKUP_MIME)) { uri ->
        vm.export(uri?.toString())
    }
    val importLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        vm.import(uri?.toString())
    }

    BackupContent(
        state = state,
        onExport = { exportLauncher.launch(defaultBackupFileName(LocalDate.now())) },
        onImport = { importLauncher.launch(arrayOf(BACKUP_MIME, "*/*")) },
        onMode = vm::setImportMode,
        onDismissError = vm::dismissError,
        modifier = modifier.fillMaxSize(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackupContent(
    state: BackupUiState,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onMode: (BackupMode) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.error?.let { message ->
            item("error") { ErrorBanner(message = message, onRetry = onDismissError) }
        }
        if (state.isRunning) {
            item("progress") { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        }
        item("export") {
            SectionCard(title = "Export") {
                Text(
                    text = "Writes every table to a JSON file you choose. Settings and any stored " +
                        "credentials are never included.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = onExport,
                    enabled = state.canStart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Export backup")
                }
            }
        }
        item("import") {
            SectionCard(title = "Import") {
                Text(
                    text = when (state.importMode) {
                        BackupMode.MERGE ->
                            "Merge adds only what this device does not have yet. Nothing is " +
                                "overwritten or deleted."
                        BackupMode.REPLACE ->
                            "Replace deletes everything on this device first, then restores the " +
                                "backup exactly as it was."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BackupMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.importMode == mode,
                            onClick = { onMode(mode) },
                            label = { Text(if (mode == BackupMode.MERGE) "Merge" else "Replace") },
                        )
                    }
                }
                OutlinedButton(
                    onClick = onImport,
                    enabled = state.canStart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Import backup")
                }
            }
        }
        state.result?.let { result ->
            item("result") {
                SectionCard(title = if (result.isImport) "Import finished" else "Export finished") {
                    Text(resultHeadline(result), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = backupOriginLine(result.summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    result.summary.rowsPerTable.forEach { (table, rows) ->
                        Text("$table: $rows", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/** "1 482 rows over 18 tables · 37 written" — the counts P8.4 asks the screen to show. */
internal fun resultHeadline(result: BackupResult): String {
    val scope = "${result.summary.totalRows} rows over ${result.summary.tableCount} tables"
    return if (result.isImport) {
        val mode = if (result.mode == BackupMode.REPLACE) "replaced" else "merged"
        "$scope in the file · ${result.summary.rowsWritten} $mode"
    } else {
        "$scope written"
    }
}

/** Where the file came from: schema version, app version and the moment it was exported. */
internal fun backupOriginLine(summary: BackupSummary, zone: ZoneId = ZoneId.systemDefault()): String {
    val stamp = if (summary.exportedAtMillis > 0L) {
        BACKUP_TIMESTAMP.format(Instant.ofEpochMilli(summary.exportedAtMillis).atZone(zone))
    } else {
        "unknown date"
    }
    val app = summary.appVersion.ifBlank { "unknown build" }
    return "Schema ${summary.schemaVersion} · app $app · $stamp"
}

/** `myhealth-backup-2026-09-12.json` — the name the document picker opens with. */
internal fun defaultBackupFileName(today: LocalDate): String = "myhealth-backup-$today.json"

private const val BACKUP_MIME = "application/json"

@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun BackupContentPreview() {
    MyHealthTheme(dynamicColor = false) {
        BackupContent(
            state = BackupUiState(
                result = BackupResult(
                    isImport = true,
                    mode = BackupMode.MERGE,
                    summary = BackupSummary(
                        schemaVersion = 2,
                        exportedAtMillis = 1_789_000_000_000L,
                        appVersion = "1.0",
                        rowsPerTable = mapOf("activity_session" to 38, "ingredient" to 12),
                        rowsWritten = 4,
                    ),
                ),
            ),
            onExport = {},
            onImport = {},
            onMode = {},
            onDismissError = {},
        )
    }
}
