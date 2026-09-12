package com.myhealth.domain.engine.suggest

import com.myhealth.domain.engine.load.TrimpDefaults
import com.myhealth.domain.model.EventType
import com.myhealth.domain.model.Intensity
import com.myhealth.domain.model.SessionType
import com.myhealth.domain.model.SportGroup
import com.myhealth.domain.model.SportType
import kotlin.math.max

/**
 * The session catalog of PLAN §3.5.4: the twelve session shapes the suggester may propose, each
 * with its sport, intensity, default duration and RPE.
 *
 * `estTrimp` is **computed**, never tabulated: `0.30 * rpe * minutes`, with the `0.30` taken from
 * [TrimpDefaults.RPE_TO_TRIMP] so the suggester's budget arithmetic and the load engine's sRPE
 * rung can never drift apart (test `cat01_est_trimp_matches_trimp_formula`).
 */
data class CatalogEntry(
    val sessionType: SessionType,
    val sportType: SportType,
    val intensity: Intensity,
    val defaultMin: Int,
    val rpe: Double,
) {
    val sportGroup: SportGroup get() = sportType.group

    /** `0.30 * rpe * minutes` — the §3.2.2 sRPE formula, so budgets stay consistent (§3.5.4). */
    fun estTrimpFor(minutes: Int): Double = TrimpDefaults.RPE_TO_TRIMP * rpe * minutes

    /** Estimated load at the default duration. */
    val estTrimp: Double get() = estTrimpFor(defaultMin)

    val isHard: Boolean get() = intensity == Intensity.HIGH || intensity == Intensity.MAX
}

object SessionCatalog {

    /** The §3.5.4 table, in the plan's row order. */
    val ALL: List<CatalogEntry> = listOf(
        CatalogEntry(SessionType.RECOVERY_RUN, SportType.RUN_OUTDOOR, Intensity.RECOVERY, 30, 3.0),
        CatalogEntry(SessionType.EASY_RUN, SportType.RUN_OUTDOOR, Intensity.LOW, 45, 4.0),
        CatalogEntry(SessionType.LONG_RUN, SportType.RUN_OUTDOOR, Intensity.MODERATE, 80, 5.0),
        CatalogEntry(SessionType.TEMPO_RUN, SportType.RUN_OUTDOOR, Intensity.HIGH, 50, 7.0),
        CatalogEntry(SessionType.INTERVAL_RUN, SportType.RUN_OUTDOOR, Intensity.HIGH, 55, 8.0),
        CatalogEntry(SessionType.STRENGTH_FULL, SportType.STRENGTH, Intensity.MODERATE, 55, 6.0),
        CatalogEntry(SessionType.STRENGTH_UPPER, SportType.STRENGTH, Intensity.MODERATE, 45, 6.0),
        CatalogEntry(SessionType.STRENGTH_LOWER, SportType.STRENGTH, Intensity.HIGH, 55, 7.0),
        CatalogEntry(SessionType.MOBILITY, SportType.MOBILITY, Intensity.RECOVERY, 20, 2.0),
        CatalogEntry(SessionType.CROSS_TRAINING, SportType.CYCLING, Intensity.LOW, 60, 4.0),
        CatalogEntry(SessionType.SOCCER_TRAINING, SportType.SOCCER_TRAINING, Intensity.MODERATE, 90, 6.5),
        CatalogEntry(SessionType.SOCCER_MATCH, SportType.SOCCER_MATCH, Intensity.HIGH, 90, 8.5),
    )

    private val byType: Map<SessionType, CatalogEntry> = ALL.associateBy { it.sessionType }

    /** Everything except `SOCCER_MATCH` — a match is a fixed calendar event, never a suggestion. */
    val SUGGESTABLE: List<CatalogEntry> = ALL.filter { it.sessionType != SessionType.SOCCER_MATCH }

    fun entryFor(sessionType: SessionType): CatalogEntry? = byType[sessionType]

    /**
     * The catalog row a fixed calendar event is seeded as (§3.5.6 step 1).
     *
     * §3.5.4 has no `RACE` row — a race is seeded as a `MAX`-intensity outdoor run at RPE 9.0, the
     * hardest thing the athlete will do that week, which is what C1/C4/C5 need it to be.
     */
    fun forEvent(eventType: EventType): CatalogEntry? = when (eventType) {
        EventType.SOCCER_MATCH -> byType[SessionType.SOCCER_MATCH]
        EventType.SOCCER_TRAINING -> byType[SessionType.SOCCER_TRAINING]
        EventType.RACE -> RACE_ENTRY
        else -> null
    }

    /** See [forEvent]: the synthetic race row (not part of [ALL], never suggested). */
    val RACE_ENTRY: CatalogEntry = CatalogEntry(
        sessionType = SessionType.TEMPO_RUN,
        sportType = SportType.RUN_OUTDOOR,
        intensity = Intensity.MAX,
        defaultMin = 60,
        rpe = 9.0,
    )

    const val MIN_DURATION_SCALE: Double = 0.7
    const val MAX_DURATION_SCALE: Double = 1.3

    /**
     * §3.5.4's `durationScale = clamp(remainingBudget / Σ(remaining planned defaults), 0.7, 1.3)`,
     * expressed in minutes on both sides of the ratio so the two are comparable: the budget is
     * converted to "minutes of this session type I can still afford".
     */
    fun durationScale(affordableMinutes: Double, remainingDefaultMinutes: Double): Double {
        if (remainingDefaultMinutes <= 0.0) return 1.0
        val raw = affordableMinutes / remainingDefaultMinutes
        return max(MIN_DURATION_SCALE, kotlin.math.min(raw, MAX_DURATION_SCALE))
    }

    /** `actualMin = round(defaultMin * durationScale)`, half-up, never below 10 minutes. */
    fun scaledMinutes(defaultMin: Int, scale: Double): Int =
        max(10, TrimpDefaults.roundHalfUp(defaultMin * scale))
}
