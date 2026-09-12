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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhealth.R
import com.myhealth.di.rememberVm
import com.myhealth.domain.repository.BackupMode
import com.myhealth.domain.repository.BackupSummary
import com.myhealth.ui.common.ErrorBanner
import com.myhealth.ui.common.SCREEN_PADDING
import com.myhealth.ui.common.SectionCard
import com.myhealth.ui.common.resolve
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
        contentPadding = PaddingValues(SCREEN_PADDING),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.error?.let { message ->
            item("error") { ErrorBanner(message = message.resolve(), onRetry = onDismissError) }
        }
        if (state.isRunning) {
            item("progress") { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        }
        item("export") {
            SectionCard(title = stringResource(R.string.backup_section_export_title)) {
                Text(
                    text = stringResource(R.string.backup_export_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = onExport,
                    enabled = state.canStart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.backup_action_export))
                }
            }
        }
        item("import") {
            SectionCard(title = stringResource(R.string.backup_section_import_title)) {
                Text(
                    text = when (state.importMode) {
                        BackupMode.MERGE -> stringResource(R.string.backup_import_mode_merge_description)
                        BackupMode.REPLACE -> stringResource(R.string.backup_import_mode_replace_description)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BackupMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.importMode == mode,
                            onClick = { onMode(mode) },
                            label = {
                                Text(
                                    if (mode == BackupMode.MERGE) {
                                        stringResource(R.string.backup_mode_label_merge)
                                    } else {
                                        stringResource(R.string.backup_mode_label_replace)
                                    },
                                )
                            },
                        )
                    }
                }
                OutlinedButton(
                    onClick = onImport,
                    enabled = state.canStart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.backup_action_import))
                }
            }
        }
        state.result?.let { result ->
            item("result") {
                SectionCard(
                    title = if (result.isImport) {
                        stringResource(R.string.backup_result_import_title)
                    } else {
                        stringResource(R.string.backup_result_export_title)
                    },
                ) {
                    Text(resultHeadline(result), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = backupOriginLine(result.summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    result.summary.rowsPerTable.forEach { (table, rows) ->
                        Text(
                            stringResource(R.string.backup_row_count_format, table, rows),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

/** "1 482 rows over 18 tables · 37 written" — the counts P8.4 asks the screen to show. */
@Composable
internal fun resultHeadline(result: BackupResult): String {
    val scope = stringResource(
        R.string.backup_result_scope_format,
        result.summary.totalRows,
        result.summary.tableCount,
    )
    return if (result.isImport) {
        val mode = if (result.mode == BackupMode.REPLACE) {
            stringResource(R.string.backup_result_mode_replaced)
        } else {
            stringResource(R.string.backup_result_mode_merged)
        }
        stringResource(R.string.backup_result_import_summary_format, scope, result.summary.rowsWritten, mode)
    } else {
        stringResource(R.string.backup_result_export_summary_format, scope)
    }
}

/** Where the file came from: schema version, app version and the moment it was exported. */
@Composable
internal fun backupOriginLine(summary: BackupSummary, zone: ZoneId = ZoneId.systemDefault()): String {
    val stamp = if (summary.exportedAtMillis > 0L) {
        BACKUP_TIMESTAMP.format(Instant.ofEpochMilli(summary.exportedAtMillis).atZone(zone))
    } else {
        stringResource(R.string.backup_unknown_date)
    }
    val app = summary.appVersion.ifBlank { stringResource(R.string.backup_unknown_build) }
    return stringResource(R.string.backup_origin_format, summary.schemaVersion, app, stamp)
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
