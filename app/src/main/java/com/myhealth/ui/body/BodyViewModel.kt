package com.myhealth.ui.body

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealth.domain.model.ActivitySource
import com.myhealth.domain.model.BodyMeasurement
import com.myhealth.domain.repository.BodyRepository
import com.myhealth.domain.repository.ProfileRepository
import com.myhealth.sync.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

private const val HISTORY_WINDOW_DAYS = 90L

/** Backs [BodyScreen] — latest weight + goal delta, "Log weight", and the 90-day history list. */
class BodyViewModel(
    private val profileRepo: ProfileRepository,
    private val bodyRepo: BodyRepository,
    private val syncScheduler: SyncScheduler,
    private val clock: Clock,
) : ViewModel() {

    private val dialogOpen = MutableStateFlow(false)
    private fun today(): LocalDate = LocalDate.now(clock)

    val state: StateFlow<BodyUiState> = combine(
        profileRepo.observeProfile(),
        bodyRepo.observeRange(today().toEpochDay() - HISTORY_WINDOW_DAYS, today().toEpochDay()),
        dialogOpen,
    ) { profile, measurements, showDialog ->
        BodyUiState(
            isLoading = false,
            goalWeightKg = profile?.goalWeightKg,
            measurements = measurements.withinLastDays(HISTORY_WINDOW_DAYS, today()).sortedByRecencyDescending(),
            showLogDialog = showDialog,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyUiState())

    fun openLogDialog() {
        dialogOpen.value = true
    }

    fun dismissLogDialog() {
        dialogOpen.value = false
    }

    fun logWeight(weightKg: Double, bodyFatPercent: Double?) {
        viewModelScope.launch {
            bodyRepo.insert(
                BodyMeasurement(
                    measuredAtMillis = clock.millis(),
                    day = today().toEpochDay(),
                    weightKg = weightKg,
                    bodyFatPercent = bodyFatPercent,
                    muscleMassKg = null,
                    boneMassKg = null,
                    bodyWaterPercent = null,
                    source = ActivitySource.MANUAL,
                ),
            )
            dialogOpen.value = false
            // A new weight changes BMR, TDEE and every macro floor (P4.12).
            syncScheduler.requestTargetRecompute()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { bodyRepo.delete(id) }
    }
}
