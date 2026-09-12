package com.myhealth.ui.load

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myhealth.di.rememberVm
import com.myhealth.domain.engine.load.AcwrZone
import com.myhealth.domain.model.DailyLoad
import com.myhealth.domain.model.RecoveryState
import com.myhealth.domain.util.toLocalDate
import com.myhealth.ui.common.EmptyState
import com.myhealth.ui.common.SectionCard
import com.myhealth.ui.common.StatTile
import com.myhealth.ui.theme.MyHealthTheme
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun LoadScreen(modifier: Modifier = Modifier) {
    val vm = rememberVm { graph ->
        LoadViewModel(graph.loadRepo, graph.healthRepo, graph.profileRepo, graph.clock)
    }
    val state by vm.state.collectAsStateWithLifecycle()

    LoadContent(state = state, onRangeSelect = vm::setRange, modifier = modifier)
}

@Composable
private fun LoadContent(
    state: LoadUiState,
    onRangeSelect: (LoadRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { RangeSelector(state.range, onRangeSelect) }
        if (!state.hasData) {
            item {
                EmptyState(
                    title = "No training load yet",
                    message = "Log or sync some activities to see ATL/CTL/ACWR and recovery here.",
                )
            }
        } else {
            item { LoadTilesCard(state.latest) }
            item { DailyTrimpCard(state.series) }
        }
        item { RecoveryCard(state.recovery) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeSelector(selected: LoadRange, onSelect: (LoadRange) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        LoadRange.entries.forEachIndexed { index, range ->
            SegmentedButton(
                selected = range == selected,
                onClick = { onSelect(range) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = LoadRange.entries.size),
            ) { Text(range.label) }
        }
    }
}

@Composable
private fun LoadTilesCard(latest: DailyLoad?) {
    SectionCard(title = "Training load") {
        if (latest == null) {
            Text("No cached load yet.", style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatTile(label = "ATL", value = "%.0f".format(Locale.US, latest.atl))
            StatTile(label = "CTL", value = "%.0f".format(Locale.US, latest.ctl))
            AcwrTile(latest.acwr)
            StatTile(label = "TSB", value = "%.0f".format(Locale.US, latest.tsb))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatTile(label = "Monotony", value = latest.monotony?.let { "%.2f".format(Locale.US, it) } ?: "—")
            StatTile(label = "Strain", value = latest.strain?.let { "%.0f".format(Locale.US, it) } ?: "—")
        }
        if (latest.flags.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                latest.flags.forEach { flag ->
                    Text("• ${flagExplanation(flag)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun AcwrTile(acwr: Double?) {
    val zone = acwrZoneOf(acwr)
    Column {
        Text(
            text = "ACWR",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = acwr?.let { "%.2f".format(Locale.US, it) } ?: "—",
            style = MaterialTheme.typography.titleLarge,
            color = zone?.color() ?: MaterialTheme.colorScheme.onSurface,
        )
        if (zone != null) {
            Text(zone.label(), style = MaterialTheme.typography.bodySmall, color = zone.color())
        }
    }
}

@Composable
private fun DailyTrimpCard(series: List<DailyLoad>) {
    SectionCard(title = "Daily TRIMP") {
        val maxTrimp = series.maxOfOrNull { it.trimp }?.takeIf { it > 0.0 } ?: 1.0
        series.forEach { day ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(day.day.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE), style = MaterialTheme.typography.bodySmall)
                Text("%.0f AU".format(Locale.US, day.trimp), style = MaterialTheme.typography.bodySmall)
            }
            LinearProgressIndicator(
                progress = { (day.trimp / maxTrimp).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun RecoveryCard(recovery: RecoveryState?) {
    SectionCard(title = "Recovery") {
        if (recovery?.score == null) {
            Text("Not enough data yet for a recovery score.", style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${recovery.score} / 100", style = MaterialTheme.typography.headlineMedium)
            Column {
                Text(recoveryBandLabel(recovery.band), style = MaterialTheme.typography.titleMedium)
                Text(confidencePercentLabel(recovery.confidence), style = MaterialTheme.typography.bodySmall)
            }
        }
        recovery.components.forEach { component ->
            Text(componentLabel(component), style = MaterialTheme.typography.bodyMedium)
        }
        recovery.flags.forEach { flag ->
            Text("• ${flagExplanation(flag)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun AcwrZone.label(): String = when (this) {
    AcwrZone.DETRAINING -> "Detraining"
    AcwrZone.OPTIMAL -> "Optimal"
    AcwrZone.CAUTION -> "Caution"
    AcwrZone.HIGH_RISK -> "High risk"
}

@Composable
internal fun AcwrZone.color(): Color = when (this) {
    AcwrZone.DETRAINING -> Color(0xFF64B5F6)
    AcwrZone.OPTIMAL -> Color(0xFF2E7D32)
    AcwrZone.CAUTION -> Color(0xFFF9A825)
    AcwrZone.HIGH_RISK -> MaterialTheme.colorScheme.error
}

@Preview(showBackground = true)
@Composable
private fun LoadContentPreview() {
    MyHealthTheme(dynamicColor = false) {
        LoadContent(
            state = LoadUiState(isLoading = false),
            onRangeSelect = {},
        )
    }
}
