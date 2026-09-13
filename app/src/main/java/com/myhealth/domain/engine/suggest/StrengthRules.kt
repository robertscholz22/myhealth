package com.myhealth.domain.engine.suggest

import com.myhealth.domain.engine.strength.MuscleLoadEngine
import com.myhealth.domain.engine.strength.MuscleLoadState
import com.myhealth.domain.engine.strength.StrengthTemplates
import com.myhealth.domain.model.EventType
import com.myhealth.domain.model.MuscleLoadBand
import com.myhealth.domain.model.RationaleEntry
import com.myhealth.domain.model.SessionType
import com.myhealth.domain.model.SportGroup
import com.myhealth.domain.model.StrengthWorkout
import com.myhealth.domain.model.StrengthWorkoutKind

/**
 * The strength half of the suggester (PLAN §3.12.5, P14.5) — the rules that finally answer the
 * owner's "no leg day after an intense run, but upper body would be ok".
 *
 * | Rule | Effect |
 * |---|---|
 * | `C15` | a `STRENGTH_LOWER`/`STRENGTH_FULL` candidate is discarded when the day's projected lower-body band is `FATIGUED`, when a hard leg day sits within 36 h before it, or when a hard run / match / race sits within 48 h after it |
 * | bonus | `+0.10` for `STRENGTH_UPPER` on a loaded-legs / fresh-arms day, `+0.10` for leg work on fresh legs with nothing hard ahead |
 * | workout | a placed `STRENGTH_*` session proposes a built-in template, alternating per kind |
 *
 * **The whole layer is inert when [MuscleContext.state] is `null`** — which is what every
 * pre-P14.5 fixture relies on: `Constraints` returns no `C15`, `Scorer` adds a `0.0` bonus,
 * `Rationale` appends no line and `SuggestionEngine` proposes no template, so `sug01`…`sug36`,
 * `ar01`…`ar08` and the whole `sug28` baseline stay byte-identical (`sug39`, `sug40`).
 *
 * Ambiguity notes (§3.12.5 leaves these open):
 * - "the day's **projected** lower-body band": [MuscleLoadState] is computed for today, so a later
 *   horizon day is evaluated by decaying today's load with the engine's own 48-hour half-life. A
 *   Sunday leg day is therefore allowed after a Saturday long run once the legs have recovered by
 *   Wednesday, without the caller having to recompute anything.
 * - "within 36 h **before**" is read in whole local days, the way C1/C11/C13 read their windows:
 *   the candidate's own day and the day before it ([HARD_LEG_LOOKBACK_DAYS]). "Within 48 h after"
 *   reuses `Constraints.HARD_WINDOW_DAYS` (the candidate day and the two days after it), so (c)
 *   really is C2 widened from matches and races to hard runs.
 * - a *hard leg day* is a completed activity of ≥ [HARD_LEG_TRIMP] AU in [LEG_SPORT_GROUPS] **or**
 *   a `HIGH`/`MAX` grid item in one of those groups, fixed or already suggested.
 * - the alternation is by "the most recently accepted template of that kind", extended inside one
 *   batch by the [occurrence] index, so a week holding two upper days gets `UPPER_A` then
 *   `UPPER_B` rather than the same workout twice.
 */
object StrengthRules {

    /** §3.12.5 (b): the sports whose hard sessions leave the legs unable to lift. */
    val LEG_SPORT_GROUPS: Set<SportGroup> = setOf(SportGroup.RUN, SportGroup.SOCCER, SportGroup.CYCLE)

    /** §3.12.5 (b): a completed activity at or above this TRIMP is a hard leg day. */
    const val HARD_LEG_TRIMP: Double = 150.0

    /** §3.12.5 (b): "within 36 h before" in whole days — yesterday and today. */
    const val HARD_LEG_LOOKBACK_DAYS: Long = 1L

    /** §3.12.5 (c): the running sessions a leg day may not sit in front of. */
    val HARD_RUN_TYPES: Set<SessionType> = setOf(
        SessionType.TEMPO_RUN,
        SessionType.INTERVAL_RUN,
        SessionType.LONG_RUN,
    )

    /** §3.12.5: the two candidates `C15` can discard. */
    val LEG_STRENGTH_TYPES: Set<SessionType> =
        setOf(SessionType.STRENGTH_LOWER, SessionType.STRENGTH_FULL)

    /** §3.12.5: the unweighted score bonus, folded in the way `cycleBonus` is. */
    const val SCORE_BONUS: Double = 0.10

    fun isLegStrength(sessionType: SessionType): Boolean = sessionType in LEG_STRENGTH_TYPES

    /** The workout kind a `STRENGTH_*` session asks for, or `null` for everything else. */
    fun kindFor(sessionType: SessionType): StrengthWorkoutKind? = when (sessionType) {
        SessionType.STRENGTH_UPPER -> StrengthWorkoutKind.UPPER
        SessionType.STRENGTH_LOWER -> StrengthWorkoutKind.LOWER
        SessionType.STRENGTH_FULL -> StrengthWorkoutKind.FULL
        else -> null
    }

    /** Today's lower-body load decayed forward to [day] and re-banded (see the class KDoc). */
    fun projectedLowerBand(day: Long, ctx: MuscleContext): MuscleLoadBand? =
        projectedBand(ctx.state?.lowerBodyLoad, day, ctx)

    /** Today's upper-body load decayed forward to [day] and re-banded. */
    fun projectedUpperBand(day: Long, ctx: MuscleContext): MuscleLoadBand? =
        projectedBand(ctx.state?.upperBodyLoad, day, ctx)

    private fun projectedBand(load: Double?, day: Long, ctx: MuscleContext): MuscleLoadBand? {
        val state = ctx.state ?: return null
        val ageDays = (day - ctx.todayDay).coerceAtLeast(0L).toDouble()
        return MuscleLoadEngine.bandFor((load ?: 0.0) * MuscleLoadEngine.decay(ageDays), state.ref)
    }

    /**
     * `C15` — the predicate `Constraints` calls. Inert without a [MuscleContext.state], and never
     * applied to anything but lower-body and full-body strength.
     */
    fun violatesC15(candidate: Candidate, day: Long, grid: SuggestionGrid, ctx: MuscleContext): Boolean {
        if (!ctx.enabled) return false
        if (!isLegStrength(candidate.sessionType)) return false
        return legWorkBlocked(day, grid, ctx)
    }

    /**
     * The three halves of `C15` without the session-type test: "would leg work be wrong on [day]?".
     * The rationale reads it too, which is how `C15_RESPECTED` can say *why* the upper-body day was
     * chosen over the leg day.
     */
    fun legWorkBlocked(day: Long, grid: SuggestionGrid, ctx: MuscleContext): Boolean {
        if (!ctx.enabled) return false
        if (projectedLowerBand(day, ctx) == MuscleLoadBand.FATIGUED) return true
        if (hardLegDayBefore(day, grid, ctx)) return true
        return hardEffortAfter(day, grid, ctx)
    }

    /** `C15` (b) — a hard leg day within 36 h before [day]. */
    fun hardLegDayBefore(day: Long, grid: SuggestionGrid, ctx: MuscleContext): Boolean {
        if (ctx.hardLegDays.any { day - it in 0..HARD_LEG_LOOKBACK_DAYS }) return true
        return grid.entries().any { (otherDay, item) ->
            otherDay != day &&
                day - otherDay in 0..HARD_LEG_LOOKBACK_DAYS &&
                item.isHard &&
                item.sportGroup in LEG_SPORT_GROUPS
        }
    }

    /** `C15` (c) — a hard run, a match or a race within 48 h after [day]; C2 widened. */
    fun hardEffortAfter(day: Long, grid: SuggestionGrid, ctx: MuscleContext): Boolean {
        if (ctx.keyEventDays.any { it - day in 0..Constraints.HARD_WINDOW_DAYS }) return true
        return grid.entries().any { (otherDay, item) ->
            otherDay != day &&
                otherDay - day in 0..Constraints.HARD_WINDOW_DAYS &&
                (item.sessionType in HARD_RUN_TYPES || item.isKeyEvent)
        }
    }

    /** §3.12.5's `Scorer.muscleBonus`, unweighted; `0.0` whenever the layer is off. */
    fun scoreBonus(candidate: Candidate, day: Long, grid: SuggestionGrid, ctx: MuscleContext): Double {
        if (!ctx.enabled) return 0.0
        val lower = projectedLowerBand(day, ctx)
        val upper = projectedUpperBand(day, ctx)
        val upperDayFits = candidate.sessionType == SessionType.STRENGTH_UPPER &&
            lower != MuscleLoadBand.FRESH &&
            upper == MuscleLoadBand.FRESH
        val legDayFits = isLegStrength(candidate.sessionType) &&
            lower == MuscleLoadBand.FRESH &&
            !hardEffortAfter(day, grid, ctx)
        return if (upperDayFits || legDayFits) SCORE_BONUS else 0.0
    }

    /**
     * The built-in template a placed `STRENGTH_*` session proposes, or `null` when the layer is off
     * or the session type has no workout kind. [occurrence] is the 0-based index of this session
     * among the same-kind sessions of the same batch, so a second upper day alternates further.
     */
    fun templateIdFor(sessionType: SessionType, ctx: MuscleContext, occurrence: Int = 0): String? {
        if (!ctx.enabled) return null
        val kind = kindFor(sessionType) ?: return null
        val options = StrengthTemplates.ofKind(kind).mapNotNull { it.templateId }
        if (options.isEmpty()) return null
        // `indexOf` is −1 for "never accepted one", which makes the first option the next one.
        val start = options.indexOf(ctx.lastTemplateByKind[kind])
        return options[(start + 1 + occurrence.coerceAtLeast(0)) % options.size]
    }

    /**
     * P14.5 (§3.12.5): why *this* half of the body is being trained today. Emitted only for the
     * three `STRENGTH_*` types and only once the muscle layer is on — [RationaleContext] carries a
     * `null` band otherwise, so the list is empty and every earlier rationale is unchanged.
     */
    fun rationaleEntries(candidate: Candidate, ctx: RationaleContext): List<RationaleEntry> {
        val lower = ctx.muscleLowerBand ?: return emptyList()
        val entries = mutableListOf<RationaleEntry>()
        val isUpper = candidate.sessionType == SessionType.STRENGTH_UPPER
        if (isUpper && lower != MuscleLoadBand.FRESH) {
            entries += RationaleEntry(
                ruleId = Rationale.RULE_MUSCLE_LOWER_LOADED,
                text = "Legs are still ${bandLabel(lower)} from recent training — upper body works today.",
            )
        }
        if (isLegStrength(candidate.sessionType) && lower == MuscleLoadBand.FRESH) {
            entries += RationaleEntry(
                ruleId = Rationale.RULE_MUSCLE_LEGS_FRESH,
                text = "Legs are fresh and nothing hard is due in the next 48 h — a leg day fits here.",
            )
        }
        if (isUpper && ctx.muscleLegWorkBlocked) {
            entries += RationaleEntry(
                ruleId = Rationale.RULE_C15_RESPECTED,
                text = "Leg work would fall too close to hard running — kept off the legs.",
            )
        }
        return entries
    }

    /**
     * P14.5: the workout a placed `STRENGTH_*` session proposes ("Workout: Upper A — 6 exercises,
     * about 44 min"), attached where the interval lines are — once the session exists, not while
     * the candidate is being placed. `null` whenever no template was proposed.
     */
    fun workoutEntry(workout: StrengthWorkout?): RationaleEntry? {
        val row = workout ?: return null
        val count = row.exercises.size
        return RationaleEntry(
            ruleId = Rationale.RULE_STRENGTH_WORKOUT,
            text = "Workout: ${row.name} — $count ${if (count == 1) "exercise" else "exercises"}, " +
                "about ${row.estimatedMinutes} min.",
        )
    }

    private fun bandLabel(band: MuscleLoadBand): String = when (band) {
        MuscleLoadBand.FRESH -> "fresh"
        MuscleLoadBand.LOADED -> "loaded"
        MuscleLoadBand.FATIGUED -> "fatigued"
    }


    /** The template row itself — what the `STRENGTH_WORKOUT` rationale names. */
    fun templateFor(templateId: String?): StrengthWorkout? =
        templateId?.let { StrengthTemplates.byId(it) }
}

/**
 * Whether this athlete's week knows anything about muscle load at all, and the facts every §3.12.5
 * rule hangs off — the strength counterpart of [BikeContext].
 *
 * [MuscleContext.NONE] (a `null` [state]) is the state every pre-P14.5 input is in, where every
 * rule above does nothing.
 */
data class MuscleContext(
    val todayDay: Long = 0L,
    val state: MuscleLoadState? = null,
    /** Days carrying a completed ≥ 150 AU run, match or ride (§3.12.5 b). */
    val hardLegDays: Set<Long> = emptySet(),
    /** Days carrying a match or a race, spanning the horizon **+ 3 days** (§3.12.5 c). */
    val keyEventDays: Set<Long> = emptySet(),
    /** The most recently accepted built-in per workout kind, for the alternation. */
    val lastTemplateByKind: Map<StrengthWorkoutKind, String> = emptyMap(),
) {
    val enabled: Boolean get() = state != null

    companion object {
        val NONE: MuscleContext = MuscleContext()

        fun of(input: SuggestionInput): MuscleContext {
            val state = input.muscleLoad ?: return NONE
            return MuscleContext(
                todayDay = input.todayDay,
                state = state,
                hardLegDays = input.recentActivities
                    .filter {
                        it.sportGroup in StrengthRules.LEG_SPORT_GROUPS &&
                            (it.trimp ?: 0.0) >= StrengthRules.HARD_LEG_TRIMP
                    }
                    .map { it.day }
                    .toSet(),
                keyEventDays = input.events
                    .filter { it.type == EventType.SOCCER_MATCH || it.type == EventType.RACE }
                    .map { it.occurrenceDay }
                    .toSet(),
                lastTemplateByKind = input.lastAcceptedTemplateByKind,
            )
        }
    }
}
