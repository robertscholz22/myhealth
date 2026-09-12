package com.myhealth.ui.running

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealth.domain.engine.running.CanonicalDistances
import com.myhealth.domain.engine.running.RiegelPredictor
import com.myhealth.domain.engine.running.VdotCalculator
import com.myhealth.domain.model.RunningBest
import com.myhealth.domain.repository.RunningBestRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/** How many efforts per distance feed the progression chart. */
private const val EFFORTS_PER_DISTANCE = 100

/**
 * Backs [RunningPrsScreen] (PLAN §4.2 Running PRs, P5.7): the PR table (one row per canonical
 * distance, `RunningBestDao.observeBestPerDistance`'s `MIN(timeSec)`), Riegel predictions from the
 * best qualifying recent effort, the VDOT that effort corresponds to, and the "Add manual PR"
 * dialog.
 */
class RunningPrsViewModel(
    private val runningBestRepo: RunningBestRepository,
    private val clock: Clock,
) : ViewModel() {

    private val showAddDialog = MutableStateFlow(false)

    private fun today(): Long = LocalDate.now(clock).toEpochDay()

    /** Every kept effort, per canonical distance — the PR **table** only carries the single best. */
    private val efforts: Flow<List<RunningBest>> = combine(
        CanonicalDistances.ALL.map { runningBestRepo.observeByDistance(it, EFFORTS_PER_DISTANCE) },
    ) { perDistance -> perDistance.toList().flatten() }

    val state: StateFlow<RunningPrsUiState> = combine(
        runningBestRepo.observeBestPerDistance(),
        efforts,
        showAddDialog,
    ) { bests, allEfforts, showDialog ->
        val source = RiegelPredictor.pickSource(bests, today())
        RunningPrsUiState(
            isLoading = false,
            bests = bests,
            predictions = RiegelPredictor.predictAll(bests, today()),
            vdot = source?.let { VdotCalculator.vdot(it.distanceMeters, it.timeSec.toDouble()) },
            progression = prProgressionSeries(allEfforts),
            showAddDialog = showDialog,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RunningPrsUiState())

    fun openAddDialog() {
        showAddDialog.value = true
    }

    fun dismissAddDialog() {
        showAddDialog.value = false
    }

    /** "Add manual PR" (§4.2): a `MANUAL` `running_best` row with no linked activity. */
    fun addManualPr(distanceMeters: Double, timeSec: Int, day: Long) {
        if (distanceMeters <= 0.0 || timeSec <= 0) return
        viewModelScope.launch {
            runningBestRepo.upsertAll(
                listOf(
                    RunningBest(
                        id = 0L,
                        distanceMeters = distanceMeters,
                        timeSec = timeSec,
                        activityId = null,
                        day = day,
                        method = "MANUAL",
                        isEstimated = false,
                        paceSecPerKm = paceSecPerKmOf(distanceMeters, timeSec),
                        createdAtMillis = clock.millis(),
                    ),
                ),
            )
            showAddDialog.value = false
        }
    }
}
