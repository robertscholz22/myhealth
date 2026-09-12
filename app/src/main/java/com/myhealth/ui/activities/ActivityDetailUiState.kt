package com.myhealth.ui.activities

import com.myhealth.domain.model.ActivitySession
import com.myhealth.domain.model.EventOccurrence

/** The five HR-zone band labels `timeInZones` (domain/engine/load/HrZones.kt) reports, in order. */
val HR_ZONE_LABELS: List<String> = listOf("<60% HRR", "60-70%", "70-80%", "80-90%", ">=90%")

/** ViewModel state for [ActivityDetailScreen] (PLAN §4.2 Activity detail, P2.9/P3.7). */
data class ActivityDetailUiState(
    val isLoading: Boolean = true,
    val activity: ActivitySession? = null,
    /** Minutes per [HR_ZONE_LABELS] band; empty when there is no HR stream. */
    val hrZoneMinutes: List<Double> = emptyList(),
    val showDeleteConfirm: Boolean = false,
    /** Set once the delete completes, so the screen can navigate back. */
    val deleted: Boolean = false,
    /** The calendar event linked to this activity, if any (P3.7). */
    val linkedEvent: EventOccurrence? = null,
    /** This activity's day's other events — the "Link to event…" picker's candidate list. */
    val dayEvents: List<EventOccurrence> = emptyList(),
    val showEventPicker: Boolean = false,
) {
    val minHr: Int? get() = activity?.streams?.hr?.filterNotNull()?.minOrNull()
    val avgHrFromStream: Int? get() = activity?.streams?.hr?.filterNotNull()
        ?.let { samples -> if (samples.isEmpty()) null else samples.sum() / samples.size }
    val maxHrFromStream: Int? get() = activity?.streams?.hr?.filterNotNull()?.maxOrNull()
    val hasHrZones: Boolean get() = hrZoneMinutes.isNotEmpty() && hrZoneMinutes.any { it > 0.0 }
}
