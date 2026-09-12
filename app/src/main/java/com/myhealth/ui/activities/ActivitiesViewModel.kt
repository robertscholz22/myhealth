package com.myhealth.ui.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealth.domain.model.SportGroup
import com.myhealth.domain.repository.ActivityRepository
import com.myhealth.sync.SyncScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** How many of the most recent activities the unfiltered ("All") list loads. */
private const val RECENT_LIMIT = 500

/** Backs [ActivitiesScreen] (PLAN §4.2 Activities, P2.9): reverse-chronological, grouped by month,
 * filterable by [SportGroup]. */
class ActivitiesViewModel(
    private val activityRepo: ActivityRepository,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val filter = MutableStateFlow<SportGroup?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ActivitiesUiState> = filter
        .flatMapLatest { group ->
            val source = if (group == null) {
                activityRepo.observeRecent(RECENT_LIMIT)
            } else {
                activityRepo.observeBySportGroup(group, fromDay = 0L)
            }
            source.map { activities ->
                ActivitiesUiState(
                    isLoading = false,
                    filter = group,
                    groups = activities.sortedByDescending { it.startAtMillis }.groupByMonth(),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivitiesUiState())

    fun setFilter(group: SportGroup?) {
        filter.value = group
    }

    fun syncNow() {
        syncScheduler.syncNow()
    }
}
