package com.myhealth.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.myhealth.domain.model.DailyLoad
import com.myhealth.ui.common.SectionCard
import com.myhealth.ui.common.StatTile
import com.myhealth.ui.load.acwrZoneOf
import com.myhealth.ui.load.color
import com.myhealth.ui.load.flagExplanation
import com.myhealth.ui.load.recoveryBandLabel
import java.util.Locale

/**
 * Today's recovery card (§4.2 Today, P5.8): score, band chip, confidence and the top active flag,
 * or a "not enough data yet" state before the first `daily_load` row exists. `daily_load` only
 * caches the score's totals, not its component breakdown — that detail lives on the Load screen
 * (P5.6), which this card taps through to.
 */
@Composable
internal fun RecoveryCard(load: DailyLoad?, topFlag: String?, onOpenLoad: () -> Unit) {
    SectionCard(
        title = "Recovery",
        modifier = Modifier.clickable(onClick = onOpenLoad),
    ) {
        if (load?.recoveryScore == null) {
            Text("Not enough data yet for a recovery score.", style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${load.recoveryScore} / 100", style = MaterialTheme.typography.headlineMedium)
            Column(horizontalAlignment = Alignment.End) {
                Text(recoveryBandLabel(load.recoveryBand), style = MaterialTheme.typography.titleMedium)
                Text(
                    "${(load.recoveryConfidence * 100).let { "%.0f".format(Locale.US, it) }}% confidence",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (topFlag != null) {
            Text(flagExplanation(topFlag), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Today's load card (§4.2 Today, P5.8): ACWR with its zone colour, ATL/CTL, and the last 7 days'
 * summed TRIMP. Taps through to the Load & Recovery screen for the full series. */
@Composable
internal fun LoadCard(load: DailyLoad?, weeklyTrimp: Double, onOpenLoad: () -> Unit) {
    SectionCard(
        title = "Training load",
        modifier = Modifier.clickable(onClick = onOpenLoad),
    ) {
        if (load == null) {
            Text("No training load yet.", style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        val zone = acwrZoneOf(load.acwr)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    "ACWR",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = load.acwr?.let { "%.2f".format(Locale.US, it) } ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    color = zone?.color() ?: MaterialTheme.colorScheme.onSurface,
                )
            }
            StatTile(label = "ATL", value = "%.0f".format(Locale.US, load.atl))
            StatTile(label = "CTL", value = "%.0f".format(Locale.US, load.ctl))
            StatTile(label = "7d TRIMP", value = "%.0f".format(Locale.US, weeklyTrimp))
        }
    }
}
