package com.myhealth.ui.calendar

/** ViewModel state for [EventEditScreen] (PLAN §4.2 Event edit, P3.6). */
data class EventEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val draft: EventDraft = EventDraft(),
    val errors: Map<EventField, String> = emptyMap(),
    /** Whether the loaded event carries a recurrence rule — gates the delete-choice dialog. */
    val isRecurring: Boolean = false,
    /** The occurrence day "delete this occurrence only" writes its `event_override` against. */
    val occurrenceDay: Long = -1L,
    val pendingDelete: Boolean = false,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val loadError: String? = null,
    /** One-shot: the screen navigates back once either flips to `true`. */
    val saved: Boolean = false,
    val deleted: Boolean = false,
)
