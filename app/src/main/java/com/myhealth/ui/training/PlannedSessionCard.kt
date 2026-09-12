package com.myhealth.ui.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myhealth.domain.model.Intensity
import com.myhealth.domain.model.PlannedSession
import com.myhealth.domain.model.PlannedStatus
import com.myhealth.domain.model.SessionType
import com.myhealth.domain.model.SportType
import com.myhealth.ui.activities.formatDistanceKm
import com.myhealth.ui.calendar.OverflowMenu
import com.myhealth.ui.calendar.displayName
import com.myhealth.ui.common.SportIcon
import com.myhealth.ui.theme.MyHealthTheme

/** The per-session actions of §4.2 "Training plan", grouped so the row keeps a short signature. */
data class PlannedSessionActions(
    val onToggleLock: (PlannedSession) -> Unit = {},
    val onEdit: (Long) -> Unit = {},
    val onMarkDone: (Long) -> Unit = {},
    val onSkip: (Long) -> Unit = {},
    val onReopen: (Long) -> Unit = {},
    val onDelete: (Long) -> Unit = {},
)

/**
 * One planned session on the week board: sport icon, session type, intensity chip, the duration /
 * distance target, its status, and the lock the suggester must respect (C6, §3.5.3).
 *
 * The lock is its own tap target rather than a menu entry — it is the one action the owner uses
 * while reading the board ("keep this one, regenerate the rest").
 */
@Composable
fun PlannedSessionCard(
    session: PlannedSession,
    actions: PlannedSessionActions,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SportIcon(session.sportType.group, modifier = Modifier.padding(end = 8.dp).size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = session.sessionType.displayName(),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(session.intensity.label()) },
                )
            }
            Text(
                text = plannedSessionSubtitle(session),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { actions.onToggleLock(session) }) {
            Icon(
                imageVector = if (session.locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = if (session.locked) "Unlock session" else "Lock session",
                tint = if (session.locked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        OverflowMenu(sessionMenu(session, actions))
    }
}

private fun sessionMenu(
    session: PlannedSession,
    actions: PlannedSessionActions,
): List<Pair<String, () -> Unit>> = buildList {
    if (session.status == PlannedStatus.PLANNED) {
        add("Mark done" to { actions.onMarkDone(session.id) })
        add("Skip" to { actions.onSkip(session.id) })
    } else {
        add("Reopen" to { actions.onReopen(session.id) })
    }
    add("Edit" to { actions.onEdit(session.id) })
    add("Delete" to { actions.onDelete(session.id) })
}

/** `"45 min · 8.00 km · 120 AU · Planned"` — whatever the session actually carries. */
fun plannedSessionSubtitle(session: PlannedSession): String = buildList {
    session.targetDurationMin?.let { add("$it min") }
    formatDistanceKm(session.targetDistanceMeters)?.let { add(it) }
    session.targetPaceSecPerKm?.let { add(formatPaceSecPerKm(it)) }
    session.estimatedTrimp?.let { add("${Math.round(it)} AU") }
    add(session.status.displayName())
    if (session.linkedActivityId != null) add("Linked")
}.joinToString(" · ")

/** `"5:30 /km"` from seconds per kilometre. */
fun formatPaceSecPerKm(secPerKm: Int): String = "%d:%02d /km".format(secPerKm / 60, secPerKm % 60)

@Preview(showBackground = true)
@Composable
private fun PlannedSessionCardPreview() {
    MyHealthTheme(dynamicColor = false) {
        PlannedSessionCard(
            session = previewSession(),
            actions = PlannedSessionActions(),
            modifier = Modifier.padding(16.dp),
        )
    }
}

internal fun previewSession(
    id: Long = 1L,
    day: Long = 20_710L,
    sessionType: SessionType = SessionType.TEMPO_RUN,
    status: PlannedStatus = PlannedStatus.PLANNED,
    locked: Boolean = false,
): PlannedSession = PlannedSession(
    id = id,
    planId = 1L,
    day = day,
    startMinuteOfDay = null,
    sportType = SportType.RUN_OUTDOOR,
    sessionType = sessionType,
    intensity = Intensity.HIGH,
    targetDurationMin = 50,
    targetDistanceMeters = 10_000.0,
    targetPaceSecPerKm = 285,
    estimatedTrimp = 105.0,
    description = null,
    rationale = null,
    status = status,
    locked = locked,
    linkedActivityId = null,
    sourceSuggestionId = null,
    createdAtMillis = 0L,
    updatedAtMillis = 0L,
)
