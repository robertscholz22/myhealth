package com.myhealth.ui.zones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealth.domain.engine.load.HrBounds
import com.myhealth.domain.engine.load.HrZoneModel
import com.myhealth.domain.engine.load.PolarisationSplit
import com.myhealth.domain.engine.load.TrimpDefaults
import com.myhealth.domain.engine.running.PaceRun
import com.myhealth.domain.engine.running.PaceZoneEngine
import com.myhealth.domain.engine.running.PaceZoneInput
import com.myhealth.domain.engine.running.RiegelPredictor
import com.myhealth.domain.engine.running.VdotCalculator
import com.myhealth.domain.model.ActivitySession
import com.myhealth.domain.model.AppSettings
import com.myhealth.domain.model.Profile
import com.myhealth.domain.model.SportGroup
import com.myhealth.domain.repository.ActivityRepository
import com.myhealth.domain.repository.HealthRepository
import com.myhealth.domain.repository.ProfileRepository
import com.myhealth.domain.repository.RunningBestRepository
import com.myhealth.domain.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate

/**
 * Backs [ZonesScreen] (PLAN §4.2 "Zones & paces", P14.6): the athlete's [HrZoneModel], the
 * measured/modelled pace band per zone (§3.10.2), the current VDOT and the 28-day polarisation
 * split (§3.9).
 *
 * Resolved the same way `SuggestionPaceResolver` (`data/repository/SuggestionPaceInputs.kt`)
 * resolves them for the suggester (P14.3) — VDOT off the best qualifying `running_best` effort
 * (`RiegelPredictor.pickSource` + `VdotCalculator.vdot`, exactly what `RunningPrsViewModel` shows),
 * the zone model off `HrBounds.compute` fed with the last 7 days of resting HR and the highest
 * `maxHr` in the loaded window, and the pace bands off `PaceZoneEngine` — but here directly
 * against the domain repositories, since `ui/` may not import `data/` (`ArchitectureTest`).
 *
 * One `getRange` load per profile/settings change (not a live per-activity stream): this screen is
 * read-only and the 90-day window it needs is the expensive part, so it is refetched only when the
 * inputs that could change its answer actually change, exactly like `ActivityDetailViewModel`'s
 * one-shot `dayCalendar` re-derivation on a new activity.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ZonesViewModel(
    private val profileRepo: ProfileRepository,
    private val activityRepo: ActivityRepository,
    private val runningBestRepo: RunningBestRepository,
    private val healthRepo: HealthRepository,
    private val settingsRepo: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private fun today(): Long = LocalDate.now(clock).toEpochDay()

    val state: StateFlow<ZonesUiState> = combine(
        profileRepo.observeProfile(),
        settingsRepo.settings,
    ) { profile, settings -> profile to settings }
        .flatMapLatest { (profile, settings) -> flow { emit(compute(profile, settings)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ZonesUiState())

    private suspend fun compute(profile: Profile?, settings: AppSettings): ZonesUiState {
        if (profile == null) return ZonesUiState(isLoading = false)
        val todayDay = today()
        val sessions = activityRepo.getRange(todayDay - PaceZoneEngine.WINDOW_DAYS, todayDay)
        val model = resolveModel(profile, todayDay, sessions)
        val vdot = resolveVdot(todayDay)
        val bands = PaceZoneEngine.compute(
            PaceZoneInput(
                today = todayDay,
                runs = sessions.filter { it.sportGroup == SportGroup.RUN }.map { PaceRun.of(it) },
                zoneModel = model,
                vdot = vdot,
                includeTreadmillInPrs = settings.includeTreadmillInPrs,
            ),
        )
        return ZonesUiState(
            isLoading = false,
            model = model,
            bands = bands,
            vdot = vdot,
            polarisation = polarisationOf(model, sessions, todayDay),
        )
    }

    private suspend fun resolveVdot(todayDay: Long): Double? {
        val bests = runningBestRepo.observeBestPerDistance().first()
        val source = RiegelPredictor.pickSource(bests, todayDay) ?: return null
        return VdotCalculator.vdot(source.distanceMeters, source.timeSec.toDouble())
    }

    /** Ambiguity note (mirrors `SuggestionPaceResolver`): [observedMaxHrLast365d] is drawn from the
     * same 90-day [sessions] window as the pace bands rather than a separate 365-day read, so this
     * model can sit a beat lower than the load engine's when the athlete's hardest effort is older
     * than three months. */
    private suspend fun resolveModel(
        profile: Profile,
        todayDay: Long,
        sessions: List<ActivitySession>,
    ): HrZoneModel {
        val restingHr = healthRepo
            .observeRange(todayDay - TrimpDefaults.REST_HR_WINDOW_DAYS + 1, todayDay)
            .first()
            .mapNotNull { it.restingHr }
        val bounds = HrBounds.compute(
            profile = profile,
            on = LocalDate.ofEpochDay(todayDay),
            restingHrLast7Days = restingHr,
            observedMaxHrLast365d = sessions.mapNotNull { it.maxHr }.maxOrNull(),
        )
        return HrZoneModel.resolve(profile, bounds)
    }

    /** The 28-day zone-minutes sum (§3.9) over every activity in the window that has an HR stream. */
    private fun polarisationOf(
        model: HrZoneModel,
        sessions: List<ActivitySession>,
        todayDay: Long,
    ): PolarisationSplit {
        val since = todayDay - POLARISATION_WINDOW_DAYS
        val minutes = DoubleArray(5)
        sessions.filter { it.day >= since }.forEach { session ->
            session.streams?.let { streams ->
                model.minutesPerZone(streams).forEachIndexed { i, m -> minutes[i] += m }
            }
        }
        return PolarisationSplit.of(minutes.toList())
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val POLARISATION_WINDOW_DAYS = 28L
    }
}
