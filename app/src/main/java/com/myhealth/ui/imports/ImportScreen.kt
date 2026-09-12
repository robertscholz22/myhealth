package com.myhealth.ui.imports

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhealth.di.rememberVm
import com.myhealth.domain.model.ImportKind
import com.myhealth.sync.ImportWorkState
import com.myhealth.ui.common.EmptyState
import com.myhealth.ui.common.ErrorBanner
import com.myhealth.ui.common.SectionCard
import com.myhealth.ui.theme.MyHealthTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** MIME filter for the document picker (§4.2): Garmin exports arrive under all of these. */
private val IMPORT_MIME_TYPES = arrayOf(
    "application/zip",
    "text/csv",
    "text/comma-separated-values",
    "application/octet-stream",
    "*/*",
)

private val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

@Composable
fun ImportScreen(modifier: Modifier = Modifier) {
    val vm = rememberVm { g ->
        ImportViewModel(g.importRepo, g.importService, g.syncScheduler, g.pendingImportUri)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val sharedUri by vm.sharedUri.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            vm.start(uri.toString(), displayNameOf(context, uri), context.contentResolver.getType(uri))
        }
    }

    // A `.fit`/`.csv`/`.zip` shared into the app starts importing as soon as this screen opens.
    LaunchedEffect(sharedUri) {
        val uri = sharedUri ?: return@LaunchedEffect
        val parsed = Uri.parse(uri)
        vm.start(uri, displayNameOf(context, parsed), context.contentResolver.getType(parsed))
        vm.clearSharedUri()
    }

    ImportContent(
        state = state,
        onPickFile = { picker.launch(IMPORT_MIME_TYPES) },
        onImportAnyway = vm::importAnyway,
        onDismissResult = vm::dismissResult,
        modifier = modifier,
    )
}

@Composable
private fun ImportContent(
    state: ImportUiState,
    onPickFile: () -> Unit,
    onImportAnyway: () -> Unit,
    onDismissResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { PickSection(state, onPickFile) }
        state.unsupportedFile?.let { name ->
            item { ErrorBanner(message = "$name is not a .fit, .csv or .zip file.") }
        }
        item { StatusSection(state, onImportAnyway, onDismissResult) }
        item { HistorySection(state.history) }
    }
}

@Composable
private fun PickSection(state: ImportUiState, onPickFile: () -> Unit) {
    SectionCard(title = "Import activities") {
        Text(
            "Pick a Garmin .fit file, an activities .csv, or the whole Export-Your-Data .zip. " +
                "Files you have already imported are detected by their checksum and skipped.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onPickFile, enabled = !state.isRunning, modifier = Modifier.padding(top = 12.dp)) {
            Text("Choose file")
        }
    }
}

@Composable
private fun StatusSection(
    state: ImportUiState,
    onImportAnyway: () -> Unit,
    onDismissResult: () -> Unit,
) {
    when (state.work.stage) {
        ImportWorkState.Stage.IDLE -> Unit
        ImportWorkState.Stage.RUNNING -> SectionCard(title = "Importing") {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            CountsRow(state.work)
            state.work.currentItem?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        ImportWorkState.Stage.DONE -> SectionCard(title = "Import finished") {
            CountsRow(state.work)
            OutlinedButton(onClick = onDismissResult, modifier = Modifier.padding(top = 12.dp)) {
                Text("Done")
            }
        }
        ImportWorkState.Stage.ALREADY_IMPORTED -> SectionCard(title = "Already imported") {
            Text(
                "This file was imported before" +
                    (state.work.message?.let { " (as $it)" } ?: "") + ".",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.canForce) {
                    Button(onClick = onImportAnyway) { Text("Import anyway") }
                }
                OutlinedButton(onClick = onDismissResult) { Text("Dismiss") }
            }
        }
        ImportWorkState.Stage.FAILED -> ErrorBanner(
            message = state.work.message ?: "The import failed.",
            onRetry = onDismissResult,
        )
    }
}

@Composable
private fun CountsRow(work: ImportWorkState) {
    Text(
        "${work.parsed} parsed · ${work.inserted} saved · ${work.duplicate} duplicates · " +
            "${work.failed} errors",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun HistorySection(history: List<ImportHistoryItem>) {
    SectionCard(title = "Import history") {
        if (history.isEmpty()) {
            EmptyState(
                title = "Nothing imported yet",
                message = "Imports you run appear here with their counts.",
            )
            return@SectionCard
        }
        history.forEach { item ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(item.fileName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${formatTimestamp(item.importedAtMillis)} · ${item.kind.label()} · " +
                        "${item.parsed} parsed, ${item.inserted} saved, ${item.duplicate} duplicates" +
                        if (item.errorCount > 0) ", ${item.errorCount} errors" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun ImportKind.label(): String = when (this) {
    ImportKind.FIT_FILE -> "FIT file"
    ImportKind.GARMIN_CSV -> "Garmin CSV"
    ImportKind.GARMIN_ZIP -> "Garmin export"
    ImportKind.JSON_BACKUP -> "JSON backup"
}

private fun formatTimestamp(millis: Long): String =
    TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** `OpenableColumns.DISPLAY_NAME` when the provider offers it, else the last path segment. */
private fun displayNameOf(context: Context, uri: Uri): String {
    runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getString(0) }
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()
}

@Preview(showBackground = true)
@Composable
private fun ImportContentPreview() {
    MyHealthTheme(dynamicColor = false) {
        ImportContent(
            state = ImportUiState(
                work = ImportWorkState(
                    stage = ImportWorkState.Stage.DONE,
                    parsed = 412,
                    inserted = 380,
                    duplicate = 30,
                    failed = 2,
                ),
                history = listOf(
                    ImportHistoryItem(
                        id = 1,
                        fileName = "garmin-export.zip",
                        kind = ImportKind.GARMIN_ZIP,
                        importedAtMillis = 1_778_396_400_000L,
                        parsed = 412,
                        inserted = 380,
                        duplicate = 30,
                        errorCount = 2,
                    ),
                ),
            ),
            onPickFile = {},
            onImportAnyway = {},
            onDismissResult = {},
        )
    }
}
