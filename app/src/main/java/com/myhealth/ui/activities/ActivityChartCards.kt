package com.myhealth.ui.activities

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.myhealth.R
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
        title = stringResource(R.string.activity_chart_hr_title),
        series = listOf(ChartSeries(name = stringResource(R.string.activity_chart_hr_series), points = hrPoints(activity.streams))),
        xLabels = minuteAxisLabels(activity.streams),
        yFormatter = { "%.0f".format(Locale.US, it) },
        emptyMessage = stringResource(R.string.activity_chart_hr_empty),
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
            title = stringResource(R.string.activity_chart_pace_title),
            series = listOf(ChartSeries(name = stringResource(R.string.activity_chart_pace_series), points = pace.invertY())),
            xLabels = minuteAxisLabels(streams),
            yFormatter = { formatPaceAxis(-it) },
            emptyMessage = stringResource(R.string.activity_chart_pace_empty),
        )
    } else {
        LineChartCard(
            title = stringResource(R.string.activity_chart_speed_title),
            series = listOf(ChartSeries(name = stringResource(R.string.activity_chart_speed_series), points = speedPoints(streams))),
            xLabels = minuteAxisLabels(streams),
            yFormatter = { "%.1f".format(Locale.US, it) },
            emptyMessage = stringResource(R.string.activity_chart_speed_empty),
        )
    }
}

@Composable
internal fun AltitudeChartCard(activity: ActivitySession) {
    LineChartCard(
        title = stringResource(R.string.activity_chart_altitude_title),
        series = listOf(ChartSeries(name = stringResource(R.string.activity_chart_altitude_series), points = altitudePoints(activity.streams))),
        xLabels = minuteAxisLabels(activity.streams),
        yFormatter = { "%.0f".format(Locale.US, it) },
        emptyMessage = stringResource(R.string.activity_chart_altitude_empty),
    )
}
