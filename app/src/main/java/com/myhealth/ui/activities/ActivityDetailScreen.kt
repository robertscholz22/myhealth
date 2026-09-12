package com.myhealth.ui.activities

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhealth.di.rememberVm
import com.myhealth.domain.model.ActivitySession
import com.myhealth.domain.model.ActivitySource
import com.myhealth.domain.model.ActivityStreams
import com.myhealth.domain.model.EventOccurrence
import com.myhealth.domain.model.Lap
import com.myhealth.domain.model.LoadMethod
import com.myhealth.domain.model.SportGroup
import com.myhealth.domain.model.SportType
import com.myhealth.ui.common.SectionCard
import com.myhealth.ui.common.SourceBadgeRow
import com.myhealth.ui.common.displayName
import com.myhealth.ui.theme.MyHealthTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailScreen(id: Long, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm = rememberVm { graph ->
        ActivityDetailViewModel(
            id,
            graph.activityRepo,
            graph.profileRepo,
            graph.calendarRepo,
            graph.syncScheduler,
            graph.clock,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.activity?.title ?: state.activity?.sportType?.displayName() ?: "Activity") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.activity != null) {
                        IconButton(onClick = vm::requestDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete activity")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        ActivityDetailBody(
            state = state,
            onSaveTitle = vm::saveTitle,
            onSaveNote = vm::saveNote,
            onOpenEventPicker = vm::openEventPicker,
            onUnlinkEvent = vm::unlinkEvent,
            onSaveRpe = vm::saveRpe,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }

    if (state.showDeleteConfirm) {
        DeleteConfirmDialog(onConfirm = vm::confirmDelete, onDismiss = vm::cancelDelete)
    }

    if (state.showEventPicker) {
        EventPickerSheet(events = state.dayEvents, onSelect = vm::linkEvent, onDismiss = vm::closeEventPicker)
    }
}

@Composable
private fun ActivityDetailBody(
    state: ActivityDetailUiState,
    onSaveTitle: (String) -> Unit,
    onSaveNote: (String) -> Unit,
    onOpenEventPicker: () -> Unit,
    onUnlinkEvent: () -> Unit,
    onSaveRpe: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = state.activity
    if (activity == null) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.Center) {
            if (!state.isLoading) Text("Activity not found.") else CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { HeaderStatsCard(activity) }
        item { SourceBadgeRow(activity.mergedSources) }
        item { TitleEditCard(activity.title, onSaveTitle) }
        item { NoteEditCard(activity.note, onSaveNote) }
        item { LinkedEventCard(state.linkedEvent, onOpenEventPicker, onUnlinkEvent) }
        item { HrChartCard(activity) }
        item { PaceOrSpeedChartCard(activity) }
        if (altitudePoints(activity.streams).any { it.y != null }) {
            item { AltitudeChartCard(activity) }
        }
        if (state.hasHrZones) {
            item { HrSummaryCard(state) }
        }
        if (activity.laps.isNotEmpty()) {
            item { LapsCard(activity.laps) }
        }
        item { RpeCard(activity.rpe, activity.loadMethod, onSaveRpe) }
    }
}

@Composable
private fun HeaderStatsCard(activity: ActivitySession) {
    SectionCard(title = "Overview") {
        Text(formatStartAtFull(activity.startAtMillis), style = MaterialTheme.typography.bodyMedium)
        StatLine("Duration", formatDuration(activity.durationSec))
        StatLine("Elapsed", formatDuration(activity.elapsedSec))
        formatDistanceKm(activity.distanceMeters)?.let { StatLine("Distance", it) }
        activity.avgHr?.let { StatLine("Avg HR", "$it bpm") }
        activity.maxHr?.let { StatLine("Max HR", "$it bpm") }
        if (activity.sportGroup == SportGroup.RUN) {
            formatPaceMinPerKm(activity.avgSpeedMps)?.let { StatLine("Avg pace", it) }
            formatPaceMinPerKm(activity.maxSpeedMps)?.let { StatLine("Best pace", it) }
        } else {
            activity.avgSpeedMps?.let { StatLine("Avg speed", "%.1f km/h".format(Locale.US, it * 3.6)) }
            activity.maxSpeedMps?.let { StatLine("Max speed", "%.1f km/h".format(Locale.US, it * 3.6)) }
        }
        activity.avgCadenceSpm?.let { StatLine("Cadence", "%.0f spm".format(Locale.US, it)) }
        activity.elevationGainM?.let { StatLine("Elevation gain", "%.0f m".format(Locale.US, it)) }
        activity.activeEnergyKcal?.let { StatLine("Active calories", "%.0f kcal".format(Locale.US, it)) }
        activity.totalEnergyKcal?.let { StatLine("Total calories", "%.0f kcal".format(Locale.US, it)) }
        activity.trimp?.let { trimp ->
            val method = activity.loadMethod?.let { " (${it.label()})" } ?: ""
            StatLine("TRIMP", "%.1f%s".format(Locale.US, trimp, method))
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun LoadMethod.label(): String = when (this) {
    LoadMethod.HR_SAMPLES -> "HR samples"
    LoadMethod.HR_AVERAGE -> "avg HR"
    LoadMethod.RPE_ESTIMATE -> "RPE estimate"
    LoadMethod.DURATION_ONLY -> "duration only"
}

@Composable
private fun TitleEditCard(title: String?, onSave: (String) -> Unit) {
    var text by remember(title) { mutableStateOf(title.orEmpty()) }
    SectionCard(title = "Title") {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(onClick = { onSave(text) }, modifier = Modifier.align(Alignment.End)) { Text("Save") }
    }
}

@Composable
private fun NoteEditCard(note: String?, onSave: (String) -> Unit) {
    var text by remember(note) { mutableStateOf(note.orEmpty()) }
    SectionCard(title = "Notes") {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        Button(onClick = { onSave(text) }, modifier = Modifier.align(Alignment.End)) { Text("Save") }
    }
}

@Composable
private fun HrSummaryCard(state: ActivityDetailUiState) {
    SectionCard(title = "Heart rate") {
        state.minHr?.let { StatLine("Min", "$it bpm") }
        state.avgHrFromStream?.let { StatLine("Avg", "$it bpm") }
        state.maxHrFromStream?.let { StatLine("Max", "$it bpm") }
        Text(
            "Time in zone",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        HR_ZONE_LABELS.forEachIndexed { index, label ->
            StatLine(label, "%.1f min".format(Locale.US, state.hrZoneMinutes.getOrElse(index) { 0.0 }))
        }
    }
}

@Composable
private fun LapsCard(laps: List<Lap>) {
    SectionCard(title = "Laps") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("#", style = MaterialTheme.typography.labelMedium)
            Text("Time", style = MaterialTheme.typography.labelMedium)
            Text("Dist.", style = MaterialTheme.typography.labelMedium)
            Text("Avg HR", style = MaterialTheme.typography.labelMedium)
        }
        laps.forEach { lap ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${lap.lapIndex + 1}", style = MaterialTheme.typography.bodyMedium)
                Text(formatDuration(lap.durationSec), style = MaterialTheme.typography.bodyMedium)
                Text(formatDistanceKm(lap.distanceMeters) ?: "—", style = MaterialTheme.typography.bodyMedium)
                Text(lap.avgHr?.let { "$it bpm" } ?: "—", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * RPE entry (§4.2 Activity detail, P5.9): a 1–10 segmented row that saves on tap, plus the TRIMP
 * method label so the athlete can see whether an RPE entry actually changed how the load was
 * computed (`RPE_ESTIMATE` only wins the method ladder when there is no HR data at all, §3.2.2).
 */
@Composable
private fun RpeCard(rpe: Int?, loadMethod: LoadMethod?, onSaveRpe: (Int) -> Unit) {
    SectionCard(title = "Perceived exertion (RPE)") {
        FlowRowRpeSelector(selected = rpe, onSelect = onSaveRpe)
        loadMethod?.let { StatLine("TRIMP method", it.label()) }
    }
}

@Composable
private fun FlowRowRpeSelector(selected: Int?, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        (1..10).forEach { value ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(value.toString()) },
            )
        }
    }
}

@Composable
private fun DeleteConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this activity?") },
        text = { Text("This cannot be undone.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatStartAtFull(startAtMillis: Long): String =
    Instant.ofEpochMilli(startAtMillis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEEE, MMM d yyyy · HH:mm", Locale.US))

@Preview(showBackground = true, name = "Populated")
@Composable
private fun ActivityDetailBodyPreview() {
    MyHealthTheme(dynamicColor = false) {
        ActivityDetailBody(
            state = ActivityDetailUiState(
                isLoading = false,
                activity = previewSession(),
                hrZoneMinutes = listOf(2.0, 5.0, 12.0, 8.0, 1.0),
            ),
            onSaveTitle = {},
            onSaveNote = {},
            onOpenEventPicker = {},
            onUnlinkEvent = {},
            onSaveRpe = {},
        )
    }
}

@Preview(showBackground = true, name = "Loading")
@Composable
private fun ActivityDetailBodyLoadingPreview() {
    MyHealthTheme(dynamicColor = false) {
        ActivityDetailBody(
            state = ActivityDetailUiState(isLoading = true),
            onSaveTitle = {},
            onSaveNote = {},
            onOpenEventPicker = {},
            onUnlinkEvent = {},
            onSaveRpe = {},
        )
    }
}

private fun previewStreams(): ActivityStreams {
    val offsets = (0..1800 step 10).toList()
    return ActivityStreams(
        sampleOffsetsSec = offsets.toIntArray(),
        hr = offsets.map { 120 + (it / 60) % 40 },
        distanceMeters = offsets.map { it * 2.9 }.toDoubleArray(),
        speedMps = offsets.map { 2.9 }.toDoubleArray(),
        altitudeM = offsets.map { 30.0 + (it / 120) % 25 }.toDoubleArray(),
        sampleCount = offsets.size,
        medianIntervalSec = 10.0,
    )
}

private fun previewSession(): ActivitySession = ActivitySession(
    id = 1,
    startAtMillis = 1_757_000_000_000L,
    endAtMillis = 1_757_003_600_000L,
    day = 19980,
    sportType = SportType.RUN_OUTDOOR,
    sportGroup = SportGroup.RUN,
    title = "Morning run",
    durationSec = 2880,
    elapsedSec = 3000,
    distanceMeters = 8320.0,
    activeEnergyKcal = 540.0,
    totalEnergyKcal = 640.0,
    avgHr = 142,
    maxHr = 168,
    avgSpeedMps = 2.89,
    maxSpeedMps = 4.1,
    avgCadenceSpm = 172.0,
    elevationGainM = 45.0,
    trimp = 108.1,
    loadMethod = LoadMethod.HR_SAMPLES,
    rpe = null,
    note = "Felt good, easy effort.",
    primarySource = ActivitySource.HEALTH_CONNECT,
    mergedSources = listOf(ActivitySource.HEALTH_CONNECT, ActivitySource.FIT_IMPORT),
    dedupeBucket = "RUN|1",
    userEditedFields = emptyList(),
    hasStreams = true,
    streams = previewStreams(),
    laps = listOf(
        Lap(1, 1, 0, 1_757_000_000_000L, 900, 2500.0, 138, 150, 2.8, 180.0),
        Lap(2, 1, 1, 1_757_000_900_000L, 900, 2600.0, 145, 160, 2.9, 190.0),
    ),
    createdAtMillis = 0,
    updatedAtMillis = 0,
)
