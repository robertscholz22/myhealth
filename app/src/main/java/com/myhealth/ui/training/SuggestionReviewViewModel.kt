package com.myhealth.ui.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealth.domain.model.SuggestedSession
import com.myhealth.domain.model.SuggestionBatch
import com.myhealth.domain.repository.SettingsRepository
import com.myhealth.domain.repository.SuggestionRepository
import com.myhealth.domain.util.Outcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Transient state the review screen owns: per-card toggles plus the in-flight accept/regenerate. */
private data class ReviewAction(
    val accepted: Map<Long, Boolean> = emptyMap(),
    val isWorking: Boolean = false,
    val message: String? = null,
    val done: Boolean = false,
)

/**
 * Backs [SuggestionReviewScreen] (PLAN §4.2 "Suggestion review", P6.7).
 *
 * Observes the latest [SuggestionBatch] and its sessions. Every card starts accepted — the batch
 * is a proposal the owner prunes, not a form to fill in — so the toggle map only ever records the
 * *rejections*. "Accept selected" therefore does both halves of the review in one go:
 * [SuggestionRepository.accept] for the ticked sessions and [SuggestionRepository.reject] for the
 * rest, which leaves no suggestion sitting in `PROPOSED` limbo.
 */
class SuggestionReviewViewModel(
    private val suggestionRepo: SuggestionRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    private val action = MutableStateFlow(ReviewAction())

    private val batch: Flow<SuggestionBatch?> = suggestionRepo.observeLatestBatch()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val sessions: Flow<List<SuggestedSession>> = batch.flatMapLatest { current ->
        if (current == null) flowOf(emptyList()) else suggestionRepo.observeSessions(current.id)
    }

    val state: StateFlow<SuggestionReviewUiState> =
        combine(batch, sessions, action) { current, rows, act ->
            SuggestionReviewUiState(
                isLoading = false,
                batch = current,
                rows = suggestionRows(current, rows),
                accepted = act.accepted,
                isWorking = act.isWorking,
                message = act.message,
                done = act.done,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            SuggestionReviewUiState(),
        )

    fun toggle(sessionId: Long) = action.update { current ->
        val next = !(current.accepted[sessionId] ?: true)
        current.copy(accepted = current.accepted + (sessionId to next))
    }

    fun acceptAll() = action.update { current ->
        current.copy(accepted = emptyMap())
    }

    /** Accepts what is still ticked and rejects the rest, then signals the screen to go back. */
    fun acceptSelected() {
        if (action.value.isWorking) return
        val current = state.value
        viewModelScope.launch {
            action.update { it.copy(isWorking = true, message = null) }
            val accepted = suggestionRepo.accept(current.selectedIds)
            val rejected = if (current.rejectedIds.isEmpty()) {
                Outcome.Ok(Unit)
            } else {
                suggestionRepo.reject(current.rejectedIds)
            }
            action.update {
                if (accepted is Outcome.Ok && rejected is Outcome.Ok) {
                    it.copy(isWorking = false, done = true)
                } else {
                    it.copy(isWorking = false, message = "Could not save the review. Please try again.")
                }
            }
        }
    }

    /** Runs the suggester again; the new batch supersedes this one and replaces the list. */
    fun regenerate() {
        if (action.value.isWorking) return
        viewModelScope.launch {
            action.update { it.copy(isWorking = true, message = null) }
            val horizon = settingsRepo.settings.first().suggestionHorizonDays
            action.update {
                when (suggestionRepo.generate(horizon)) {
                    is Outcome.Ok -> it.copy(isWorking = false, accepted = emptyMap())
                    is Outcome.Err -> it.copy(
                        isWorking = false,
                        message = "Could not generate suggestions. Please try again.",
                    )
                }
            }
        }
    }

    fun consumeMessage() = action.update { it.copy(message = null) }

    fun consumeDone() = action.update { it.copy(done = false) }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** The screen's read of the batch header: `"Build · target 620 AU · 540 AU suggested"`. */
internal fun SuggestionReviewUiState.headerLine(): String = buildList {
    phase?.let { add(it.label()) }
    if (weeklyTarget > 0.0) add("target ${Math.round(weeklyTarget)} AU")
    add("${Math.round(totalSuggestedLoad)} AU suggested")
    if (restDayCount > 0) add("$restDayCount rest ${if (restDayCount == 1) "day" else "days"}")
}.joinToString(" · ")
