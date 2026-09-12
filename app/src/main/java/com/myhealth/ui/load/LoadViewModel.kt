package com.myhealth.ui.load

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealth.domain.engine.load.RecoveryEngine
import com.myhealth.domain.engine.load.RecoveryInput
import com.myhealth.domain.model.DailyHealthSummary
import com.myhealth.domain.model.DailyLoad
import com.myhealth.domain.model.Profile
import com.myhealth.domain.model.RecoveryState
import com.myhealth.domain.model.SleepRecord
import com.myhealth.domain.repository.HealthRepository
import com.myhealth.domain.repository.LoadRepository
import com.myhealth.domain.repository.ProfileRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val RECOVERY_LOOKBACK_DAYS = 30L
private const val BEDTIME_HISTORY_DAYS = 14L
private const val SLEEP_DEBT_WINDOW_DAYS = 7L
private const val HRV_WINDOW_DAYS = 7L

/** The four inputs [LoadViewModel] combines into one [LoadUiState]. */
private data class LoadCore(
    val series: List<DailyLoad>,
    val latest: DailyLoad?,
    val recovery: RecoveryState?,
)

/**
 * Backs [LoadScreen] (PLAN §4.2 Load & recovery, P5.6): the range-selected daily-load series, the
 * latest cached row for the ATL/CTL/ACWR/TSB tiles, and a live [RecoveryState] recomputed from the
 * same inputs [com.myhealth.data.repository.LoadRecomputeService] uses — `daily_load` only caches
 * the score/band/confidence/flags totals, not the per-component breakdown (§2.2.6), so the
 * component list (name, points/max) is rebuilt here rather than read from the cache.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoadViewModel(
    private val loadRepo: LoadRepository,
    private val healthRepo: HealthRepository,
    private val profileRepo: ProfileRepository,
    private val clock: Clock,
) : ViewModel() {

    private val range = MutableStateFlow(LoadRange.D28)

    private fun today(): Long = LocalDate.now(clock).toEpochDay()

    private val series = range.flatMapLatest { r ->
        loadRepo.observeRange(today() - r.days + 1, today())
    }

    private val recovery = combine(
        loadRepo.observeLatest(),
        profileRepo.observeProfile(),
        healthRepo.observeRange(today() - RECOVERY_LOOKBACK_DAYS, today()),
        healthRepo.observeSleepRange(today() - BEDTIME_HISTORY_DAYS, today()),
    ) { latest, profile, health, sleep ->
        recoveryStateFor(latest, profile, health, sleep, today(), clock.zone)
    }

    private val core = combine(series, loadRepo.observeLatest(), recovery) { s, latest, r ->
        LoadCore(series = s, latest = latest, recovery = r)
    }

    val state: StateFlow<LoadUiState> = combine(range, core) { r, c ->
        LoadUiState(isLoading = false, range = r, series = c.series, latest = c.latest, recovery = c.recovery)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadUiState())

    fun setRange(newRange: LoadRange) {
        range.value = newRange
    }
}

/** Rebuilds a [RecoveryInput] for `today` from bulk-fetched history, mirroring
 * `LoadRecomputeService.recoveryInputFor` but scoped to a single day for live UI display. */
private fun recoveryStateFor(
    latest: DailyLoad?,
    profile: Profile?,
    health: List<DailyHealthSummary>,
    sleep: List<SleepRecord>,
    today: Long,
    zone: ZoneId,
): RecoveryState? {
    if (profile == null) return null
    val day = latest?.day ?: today
    val healthByDay = health.associateBy { it.day }
    val sleepByNight = sleep.associateBy { it.night }
    val lastNight = sleepByNight[day]

    val input = RecoveryInput(
        day = day,
        lastNight = lastNight,
        bedtimeMinuteOfDay = lastNight?.let { minuteOfDay(it.startAtMillis, zone) },
        bedtimeMinutesLast14 = ((day - BEDTIME_HISTORY_DAYS) until day).mapNotNull { night ->
            sleepByNight[night]?.let { minuteOfDay(it.startAtMillis, zone) }
        },
        sleepMinutesLast7 = (day - SLEEP_DEBT_WINDOW_DAYS + 1..day).mapNotNull { sleepByNight[it]?.totalSleepMin },
        sleepTargetHours = profile.sleepTargetHours,
        restingHrToday = healthByDay[day]?.restingHr ?: healthByDay[day - 1]?.restingHr,
        restingHrLast30 = (day - RECOVERY_LOOKBACK_DAYS until day).mapNotNull { healthByDay[it]?.restingHr },
        load = latest,
        hrvTodayMs = healthByDay[day]?.hrvRmssdMs,
        hrvLast7Ms = (day - HRV_WINDOW_DAYS until day).mapNotNull { healthByDay[it]?.hrvRmssdMs },
    )
    return RecoveryEngine.compute(input)
}

private fun minuteOfDay(atMillis: Long, zone: ZoneId): Int =
    Instant.ofEpochMilli(atMillis).atZone(zone).toLocalTime().toSecondOfDay() / 60
