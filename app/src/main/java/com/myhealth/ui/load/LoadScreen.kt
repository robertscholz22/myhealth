package com.myhealth.ui.load

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.myhealth.ui.common.EmptyState
import com.myhealth.ui.common.SectionCard
import com.myhealth.ui.common.StatTile
import com.myhealth.ui.common.charts.BarChartCard
import com.myhealth.ui.common.charts.ChartBand
import com.myhealth.ui.common.charts.ChartSeries
import com.myhealth.ui.common.charts.LineChartCard
import com.myhealth.ui.theme.MyHealthTheme
import com.myhealth.ui.theme.PositiveGreen
import com.myhealth.ui.theme.WarningAmber
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
            item { AtlCtlChartCard(state.series) }
            item { AcwrChartCard(state.series) }
            item { DailyTrimpCard(state.series) }
            item { RecoveryTrendCard(state.series) }
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

/** Acute (7-day) and chronic (28-day) load, the two EWMAs of §3.2.2. */
@Composable
private fun AtlCtlChartCard(series: List<DailyLoad>) {
    LineChartCard(
        title = "Acute vs chronic load",
        series = listOf(
            ChartSeries(name = "ATL (7d)", points = atlPoints(series)),
            ChartSeries(name = "CTL (28d)", points = ctlPoints(series)),
        ),
        xLabels = loadAxisLabels(series),
        yFormatter = { "%.0f".format(Locale.US, it) },
        emptyMessage = "No cached load for this range yet.",
    )
}

/** ACWR with the §3.2.3 risk zones shaded behind the line. */
@Composable
private fun AcwrChartCard(series: List<DailyLoad>) {
    val bands = listOf(
        ChartBand(
            label = "Optimal",
            from = ACWR_OPTIMAL_MIN,
            to = ACWR_OPTIMAL_MAX,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
        ),
        ChartBand(
            label = "Caution",
            from = ACWR_OPTIMAL_MAX,
            to = ACWR_CAUTION_MAX,
            color = WarningAmber.copy(alpha = 0.20f),
        ),
        ChartBand(
            label = "High risk",
            from = ACWR_CAUTION_MAX,
            to = Double.MAX_VALUE,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
        ),
    )
    LineChartCard(
        title = "ACWR",
        series = listOf(ChartSeries(name = "ACWR", points = acwrPoints(series))),
        xLabels = loadAxisLabels(series),
        yFormatter = { "%.1f".format(Locale.US, it) },
        bands = bands,
        emptyMessage = "Not enough chronic load yet for an ACWR.",
    )
}

@Composable
private fun DailyTrimpCard(series: List<DailyLoad>) {
    val bars = trimpBars(series)
    BarChartCard(
        title = "Daily TRIMP",
        values = bars,
        xLabels = loadAxisLabels(series),
        yFormatter = { "%.0f".format(Locale.US, it) },
        highlightIndex = bars.lastIndex.takeIf { it >= 0 },
        emptyMessage = "No training logged in this range yet.",
    )
}

@Composable
private fun RecoveryTrendCard(series: List<DailyLoad>) {
    LineChartCard(
        title = "Recovery score",
        series = listOf(ChartSeries(name = "Recovery", points = recoveryPoints(series))),
        xLabels = loadAxisLabels(series),
        yFormatter = { "%.0f".format(Locale.US, it) },
        emptyMessage = "No recovery scores cached for this range yet.",
    )
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
    AcwrZone.DETRAINING -> MaterialTheme.colorScheme.secondary
    AcwrZone.OPTIMAL -> PositiveGreen
    AcwrZone.CAUTION -> WarningAmber
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
