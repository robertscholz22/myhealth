package com.myhealth.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myhealth.domain.model.CalendarDay
import com.myhealth.ui.common.CARD_CORNER_RADIUS
import com.myhealth.ui.theme.MyHealthTheme
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * The selectable 7-day strip of week mode (§4.3 `WeekStrip`, P3.4): Monday first, the selected
 * day filled, today outlined, marker dots under each day.
 */
@Composable
fun WeekStrip(
    weekAnchor: LocalDate,
    days: Map<Long, CalendarDay>,
    today: Long,
    selectedDay: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        weekDays(weekAnchor).forEach { date ->
            val epochDay = date.toEpochDay()
            WeekStripDay(
                date = date,
                day = days[epochDay],
                isToday = epochDay == today,
                isSelected = epochDay == selectedDay,
                onClick = { onSelect(epochDay) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WeekStripDay(
    date: LocalDate,
    day: CalendarDay?,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background =
        if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    Column(
        modifier = modifier
            .background(background, RoundedCornerShape(CARD_CORNER_RADIUS))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US).take(2),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .then(
                    if (isToday) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape) else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isToday) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.height(6.dp),
        ) {
            dayMarkers(day).forEach { marker ->
                Box(modifier = Modifier.size(5.dp).background(marker.color(), CircleShape))
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun WeekStripPreview() {
    val date = LocalDate.of(2026, 9, 16)
    val epochDay = date.toEpochDay()
    MyHealthTheme(dynamicColor = false) {
        WeekStrip(
            weekAnchor = date,
            days = mapOf(epochDay to previewCalendarDay(epochDay)),
            today = epochDay,
            selectedDay = epochDay + 1,
            onSelect = {},
        )
    }
}
