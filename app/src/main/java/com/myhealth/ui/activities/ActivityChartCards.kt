package com.myhealth.ui.activities

import androidx.compose.runtime.Composable
import com.myhealth.domain.model.ActivitySession
import com.myhealth.domain.model.SportGroup
import com.myhealth.ui.common.charts.ChartSeries
import com.myhealth.ui.common.charts.LineChartCard
import com.myhealth.ui.common.charts.invertY
import java.util.Locale

/** Heart rate against elapsed minutes (PLAN P8.3). */
@Composable
internal fun HrChartCard(activity: ActivitySession) {
    LineChartCard(
        title = "Heart rate over time",
        series = listOf(ChartSeries(name = "HR", points = hrPoints(activity.streams))),
        xLabels = minuteAxisLabels(activity.streams),
        yFormatter = { "%.0f".format(Locale.US, it) },
        emptyMessage = "This activity has no heart-rate stream.",
    )
}

/**
 * Runs get pace (min/km) on an **inverted** axis so a faster kilometre sits higher; everything
 * else gets plain speed in km/h.
 */
@Composable
internal fun PaceOrSpeedChartCard(activity: ActivitySession) {
    val streams = activity.streams
    val distance = streams?.distanceMeters
    val speed = streams?.speedMps
    val pace = when {
        streams == null || activity.sportGroup != SportGroup.RUN -> null
        distance != null -> paceSeriesFromStream(streams.sampleOffsetsSec, distance)
        speed != null -> paceSeriesFromSpeed(streams.sampleOffsetsSec, speed)
        else -> null
    }
    if (pace != null) {
        LineChartCard(
            title = "Pace over time",
            series = listOf(ChartSeries(name = "Pace", points = pace.invertY())),
            xLabels = minuteAxisLabels(streams),
            yFormatter = { formatPaceAxis(-it) },
            emptyMessage = "This run has no distance or speed stream to derive pace from.",
        )
    } else {
        LineChartCard(
            title = "Speed over time",
            series = listOf(ChartSeries(name = "Speed", points = speedPoints(streams))),
            xLabels = minuteAxisLabels(streams),
            yFormatter = { "%.1f".format(Locale.US, it) },
            emptyMessage = "This activity has no speed stream.",
        )
    }
}

@Composable
internal fun AltitudeChartCard(activity: ActivitySession) {
    LineChartCard(
        title = "Altitude",
        series = listOf(ChartSeries(name = "Altitude", points = altitudePoints(activity.streams))),
        xLabels = minuteAxisLabels(activity.streams),
        yFormatter = { "%.0f".format(Locale.US, it) },
        emptyMessage = "This activity has no altitude stream.",
    )
}
