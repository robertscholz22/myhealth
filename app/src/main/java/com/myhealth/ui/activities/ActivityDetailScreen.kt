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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhealth.R
import com.myhealth.di.rememberVm
import com.myhealth.domain.model.ActivitySession
import com.myhealth.domain.model.ActivitySource
import com.myhealth.domain.model.ActivityStreams
import com.myhealth.domain.model.EventOccurrence
import com.myhealth.domain.model.Lap
import com.myhealth.domain.model.LoadMethod
import com.myhealth.domain.model.SportGroup
import com.myhealth.domain.model.SportType
import com.myhealth.ui.common.SCREEN_PADDING
import com.myhealth.ui.common.SectionCard
import com.myhealth.ui.common.SourceBadgeRow
import com.myhealth.ui.common.displayName
import com.myhealth.ui.common.fmtDecimal
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
                title = {
                    val fallback = stringResource(R.string.activity_detail_title_fallback)
                    Text(state.activity?.title ?: state.activity?.sportType?.displayName() ?: fallback)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.activity != null) {
                        IconButton(onClick = vm::requestDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.activity_detail_delete_cd))
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
internal fun ActivityDetailBody(
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
            if (!state.isLoading) Text(stringResource(R.string.activity_detail_not_found)) else CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(SCREEN_PADDING),
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
    SectionCard(title = stringResource(R.string.activity_detail_overview_title)) {
        Text(formatStartAtFull(activity.startAtMillis), style = MaterialTheme.typography.bodyMedium)
        StatLine(stringResource(R.string.activity_detail_duration_label), formatDuration(activity.durationSec))
        StatLine(stringResource(R.string.activity_detail_elapsed_label), formatDuration(activity.elapsedSec))
        formatDistanceKm(activity.distanceMeters)?.let { StatLine(stringResource(R.string.activity_detail_distance_label), it) }
        activity.avgHr?.let { StatLine(stringResource(R.string.activity_detail_avg_hr_label), "$it bpm") }
        activity.maxHr?.let { StatLine(stringResource(R.string.activity_detail_max_hr_label), "$it bpm") }
        if (activity.sportGroup == SportGroup.RUN) {
            formatPaceMinPerKm(activity.avgSpeedMps)?.let { StatLine(stringResource(R.string.activity_detail_avg_pace_label), it) }
            formatPaceMinPerKm(activity.maxSpeedMps)?.let { StatLine(stringResource(R.string.activity_detail_best_pace_label), it) }
        } else {
            val avgLabel = stringResource(R.string.activity_detail_avg_speed_label)
            val maxLabel = stringResource(R.string.activity_detail_max_speed_label)
            activity.avgSpeedMps?.let { StatLine(avgLabel, "${fmtDecimal(it * 3.6, 1)} km/h") }
            activity.maxSpeedMps?.let { StatLine(maxLabel, "${fmtDecimal(it * 3.6, 1)} km/h") }
        }
        activity.avgCadenceSpm?.let { StatLine(stringResource(R.string.activity_detail_cadence_label), "${fmtDecimal(it, 0)} spm") }
        val elevationLabel = stringResource(R.string.activity_detail_elevation_gain_label)
        activity.elevationGainM?.let { StatLine(elevationLabel, "${fmtDecimal(it, 0)} m") }
        val activeCaloriesLabel = stringResource(R.string.activity_detail_active_calories_label)
        activity.activeEnergyKcal?.let { StatLine(activeCaloriesLabel, "${fmtDecimal(it, 0)} kcal") }
        val totalCaloriesLabel = stringResource(R.string.activity_detail_total_calories_label)
        activity.totalEnergyKcal?.let { StatLine(totalCaloriesLabel, "${fmtDecimal(it, 0)} kcal") }
        activity.trimp?.let { trimp ->
            val method = activity.loadMethod?.let { " (${it.label()})" } ?: ""
            StatLine(stringResource(R.string.activity_detail_trimp_label), "${fmtDecimal(trimp, 1)}$method")
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

@Composable
private fun LoadMethod.label(): String = when (this) {
    LoadMethod.HR_SAMPLES -> stringResource(R.string.activity_detail_load_method_hr_samples)
    LoadMethod.HR_AVERAGE -> stringResource(R.string.activity_detail_load_method_avg_hr)
    LoadMethod.RPE_ESTIMATE -> stringResource(R.string.activity_detail_load_method_rpe_estimate)
    LoadMethod.DURATION_ONLY -> stringResource(R.string.activity_detail_load_method_duration_only)
}

@Composable
private fun TitleEditCard(title: String?, onSave: (String) -> Unit) {
    var text by remember(title) { mutableStateOf(title.orEmpty()) }
    SectionCard(title = stringResource(R.string.activity_detail_title_card_title)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(onClick = { onSave(text) }, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.action_save)) }
    }
}

@Composable
private fun NoteEditCard(note: String?, onSave: (String) -> Unit) {
    var text by remember(note) { mutableStateOf(note.orEmpty()) }
    SectionCard(title = stringResource(R.string.activity_detail_notes_title)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        Button(onClick = { onSave(text) }, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.action_save)) }
    }
}

@Composable
private fun HrSummaryCard(state: ActivityDetailUiState) {
    SectionCard(title = stringResource(R.string.activity_detail_hr_summary_title)) {
        state.minHr?.let { StatLine(stringResource(R.string.activity_detail_hr_min_label), "$it bpm") }
        state.avgHrFromStream?.let { StatLine(stringResource(R.string.activity_detail_hr_avg_label), "$it bpm") }
        state.maxHrFromStream?.let { StatLine(stringResource(R.string.activity_detail_hr_max_label), "$it bpm") }
        Text(stringResource(R.string.activity_detail_hr_time_in_zone_label), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
        HR_ZONE_LABELS.forEachIndexed { index, label ->
            StatLine(label, "${fmtDecimal(state.hrZoneMinutes.getOrElse(index) { 0.0 }, 1)} min")
        }
    }
}

@Composable
private fun LapsCard(laps: List<Lap>) {
    SectionCard(title = stringResource(R.string.activity_detail_laps_title)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.activity_detail_laps_header_index), style = MaterialTheme.typography.labelMedium)
            Text(stringResource(R.string.activity_detail_laps_header_time), style = MaterialTheme.typography.labelMedium)
            Text(stringResource(R.string.activity_detail_laps_header_distance), style = MaterialTheme.typography.labelMedium)
            Text(stringResource(R.string.activity_detail_laps_header_avg_hr), style = MaterialTheme.typography.labelMedium)
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
    SectionCard(title = stringResource(R.string.activity_detail_rpe_title)) {
        FlowRowRpeSelector(selected = rpe, onSelect = onSaveRpe)
        loadMethod?.let { StatLine(stringResource(R.string.activity_detail_trimp_method_label), it.label()) }
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
        title = { Text(stringResource(R.string.activity_detail_delete_dialog_title)) },
        text = { Text(stringResource(R.string.activity_detail_delete_dialog_text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun formatStartAtFull(startAtMillis: Long): String =
    Instant.ofEpochMilli(startAtMillis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEEE, MMM d yyyy · HH:mm", Locale.US))
