package com.myhealth.domain.engine.suggest

import com.myhealth.domain.engine.load.TrimpDefaults
import com.myhealth.domain.engine.running.VdotCalculator
import com.myhealth.domain.model.Goal
import com.myhealth.domain.model.GoalStatus
import com.myhealth.domain.model.GoalType
import com.myhealth.domain.model.RationaleEntry
import com.myhealth.domain.model.TrainingPhase
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * P19's goal facts the suggester cannot derive from the rest of [SuggestionInput]. `null` on the
 * input switches the whole goal layer off (no benchmark, no long-run build-up, no goal pace), which
 * is what keeps every pre-P19 fixture byte-identical; the repository always fills it.
 */
data class GoalFormInputs(
    /** The day of the effort the VDOT comes from (180-day window), `null` when there is none. */
    val vdotSourceDay: Long? = null,
    /** The longest run of the last 28 days, in metres (`0.0` = none). */
    val longestRunMeters28d: Double = 0.0,
    /** Days carrying a non-skipped planned `TIME_TRIAL`, from `today − 7` to the horizon end. */
    val timeTrialDays: Set<Long> = emptySet(),
)

/** The race-time goal the run paces are measured against (§P19 item 6). */
data class PaceGoal(
    val title: String,
    val distanceMeters: Double,
    val targetSec: Int,
    /** What the current VDOT predicts for [distanceMeters]; `null` without a VDOT. */
    val predictedSec: Double?,
) {
    val goalPaceSecPerKm: Int get() = TrimpDefaults.roundHalfUp(targetSec / (distanceMeters / 1000.0))

    /** `predicted / target − 1`; positive = slower than the goal. */
    val gap: Double? get() = predictedSec?.let { it / targetSec - 1.0 }

    /** True once the athlete is close enough to the goal for its pace to be trained (3 %). */
    val withinReach: Boolean get() = (gap ?: Double.MAX_VALUE) <= GoalRules.GOAL_PACE_GAP
}

/** The long run of one week of a race build-up (§P19 item 7). */
data class LongRunPlan(
    val meters: Double,
    val baseMeters: Double,
    val peakMeters: Double,
    val goal: Goal,
    val daysToRace: Long,
    val phase: TrainingPhase,
)

/** Where the athlete is in the race calendar, for the Training screen (§P19 item 8). */
data class PhaseOutlook(
    /** The phase the race date implies (the every-4th-week down week is not part of it). */
    val phase: TrainingPhase,
    /** The next phase, or `null` when the next change is the race itself. */
    val nextPhase: TrainingPhase?,
    val nextPhaseDay: Long?,
    val raceGoal: Goal,
)

/**
 * The goal layer of the suggester (PLAN §P19): the benchmark time trial, goal pace, the long-run
 * build-up and the phase outlook. Pure functions of [SuggestionInput]; every rule is inert while
 * `SuggestionInput.goalForm` is `null`.
 */
object GoalRules {

    /** A VDOT source older than eight weeks no longer says much about current form. */
    const val BENCHMARK_STALE_DAYS: Long = 56L
    const val BENCHMARK_DISTANCE_M: Double = 5_000.0

    /** Goal pace is trained once the prediction is within 3 % of the target. */
    const val GOAL_PACE_GAP: Double = 0.03

    /** Race goals from this distance on get a long-run build-up. */
    const val LONG_RUN_GOAL_MIN_M: Double = 15_000.0
    const val LONG_RUN_PEAK_SHARE: Double = 0.9
    const val LONG_RUN_PEAK_CAP_M: Double = 32_000.0
    const val LONG_RUN_GROWTH: Double = 1.10
    const val LONG_RUN_FLOOR_SHARE: Double = 0.45
    const val LONG_RUN_DOWN_WEEK: Double = 0.75
    const val LONG_RUN_TAPER_SHARE: Double = 0.7
    const val LONG_RUN_RACE_WEEK_SHARE: Double = 0.4
    const val LONG_RUN_TAPER_DAYS: Long = 14L
    const val LONG_RUN_STEP_M: Double = 500.0

    /** The long run's pace when neither a measured band nor a VDOT exists (6:30 /km). */
    const val DEFAULT_LONG_RUN_SEC_PER_KM: Int = 390

    const val RULE_BENCHMARK: String = "BENCHMARK"
    const val RULE_GOAL_PACE: String = "GOAL_PACE"
    const val RULE_LONG_RUN_BUILD: String = "LONG_RUN_BUILD"

    /** The race-distance templates goal pace may replace (§P19 item 6). */
    private val SHORT_GOAL_TEMPLATES = setOf("RUN_1000_I", "RUN_800_I")
    private val LONG_GOAL_TEMPLATES = setOf("RUN_CRUISE_T", "RUN_TEMPO_CONT")
    private const val SHORT_GOAL_MAX_M = 10_000.0
    private val GOAL_PACE_PHASES = setOf(
        TrainingPhase.BUILD,
        TrainingPhase.PEAK,
        TrainingPhase.TAPER,
        TrainingPhase.RACE_WEEK,
    )

    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.US)

    // ---- benchmark --------------------------------------------------------------------------

    /**
     * Why a benchmark run is due this week, or `null` when it is not: an active `RACE_TIME` goal,
     * a VDOT that is missing or older than [BENCHMARK_STALE_DAYS], not a taper/race week or a
     * starter week, and no time trial already planned.
     */
    fun benchmarkReason(input: SuggestionInput, phase: TrainingPhase, isStarterWeek: Boolean): String? {
        val form = input.goalForm ?: return null
        val hasRaceGoal = input.goals.any { it.type == GoalType.RACE_TIME && it.status == GoalStatus.ACTIVE }
        if (!hasRaceGoal || isStarterWeek) return null
        if (phase == TrainingPhase.TAPER || phase == TrainingPhase.RACE_WEEK) return null
        if (form.timeTrialDays.isNotEmpty()) return null
        val source = form.vdotSourceDay
            ?: return "No hard effort of 3 km or more in the last 6 months"
        val age = input.todayDay - source
        if (age <= BENCHMARK_STALE_DAYS) return null
        return "Your last hard effort was on ${dayLabel(source)}, ${age / DAYS_PER_WEEK} weeks ago"
    }

    fun benchmarkEntry(reason: String): RationaleEntry = RationaleEntry(
        ruleId = RULE_BENCHMARK,
        text = "$reason. After a 15-minute warm-up run 5 km as fast as you can and record it as its " +
            "own activity (stop the watch at 5.0 km) — it sets your training paces and goal progress.",
    )

    /** The pace a time trial is expected at: the current VDOT's 5 km time, if there is one. */
    fun benchmarkPaceSecPerKm(vdot: Double?): Int? = vdot
        ?.let { VdotCalculator.raceTimeSec(it, BENCHMARK_DISTANCE_M) }
        ?.let { TrimpDefaults.roundHalfUp(it / (BENCHMARK_DISTANCE_M / 1000.0)) }

    // ---- goal pace --------------------------------------------------------------------------

    /**
     * The goal the paces are measured against: the race the week is periodized towards when it is
     * a running race with a time, else the highest-priority `RACE_TIME` goal with distance and time.
     * `null` while the goal layer is off.
     */
    fun paceGoal(input: SuggestionInput): PaceGoal? {
        if (input.goalForm == null) return null
        val race = Periodization.primaryRaceGoal(input.goals, input.todayDay)?.takeIf { it.isTimedRun() }
        val goal = race ?: input.goals.filter { it.isTimedRun() }.minByOrNull { it.priority } ?: return null
        val distance = goal.targetDistanceMeters ?: return null
        val target = goal.targetTimeSec ?: return null
        return PaceGoal(
            title = goal.title,
            distanceMeters = distance,
            targetSec = target,
            predictedSec = input.vdot?.let { VdotCalculator.raceTimeSec(it, distance) },
        )
    }

    /** Whether [templateId]'s work pace becomes the goal pace in [phase] (§P19 item 6). */
    fun usesGoalPace(goal: PaceGoal?, templateId: String, phase: TrainingPhase): Boolean {
        if (goal == null || !goal.withinReach || phase !in GOAL_PACE_PHASES) return false
        val templates = if (goal.distanceMeters <= SHORT_GOAL_MAX_M) SHORT_GOAL_TEMPLATES else LONG_GOAL_TEMPLATES
        return templateId in templates
    }

    /** The `GOAL_PACE` line every tempo and interval run carries while a pace goal exists. */
    fun goalPaceEntry(goal: PaceGoal, usedGoalPace: Boolean): RationaleEntry {
        val pace = mmss(goal.goalPaceSecPerKm)
        val target = "${distanceLabel(goal.distanceMeters)} in ${clock(goal.targetSec)}"
        val predicted = goal.predictedSec
        val text = when {
            predicted == null ->
                "Goal pace $pace /km ($target). Your current form is unknown until a recent hard effort is recorded."
            usedGoalPace ->
                "Goal pace $pace /km ($target). Current form predicts ${clock(predicted.roundToInt())} — " +
                    "these reps are run at goal pace."
            goal.withinReach ->
                "Goal pace $pace /km ($target). Current form predicts ${clock(predicted.roundToInt())} — " +
                    "within reach; goal-pace reps come in the build and peak weeks."
            else ->
                "Goal pace $pace /km ($target). Current form predicts ${clock(predicted.roundToInt())} " +
                    "(${percentLabel(goal.gap ?: 0.0)}) — reps stay at your current paces until you are within 3 %."
        }
        return RationaleEntry(ruleId = RULE_GOAL_PACE, text = text)
    }

    // ---- long run ---------------------------------------------------------------------------

    /** The nearest dated running race of at least [LONG_RUN_GOAL_MIN_M] still ahead. */
    fun longRunGoal(goals: List<Goal>, todayDay: Long): Goal? = goals
        .filter {
            it.type == GoalType.RACE_TIME && it.isRace &&
                (it.targetDay ?: Long.MIN_VALUE) >= todayDay &&
                (it.targetDistanceMeters ?: 0.0) >= LONG_RUN_GOAL_MIN_M
        }
        .minWithOrNull(compareBy<Goal> { it.targetDay }.thenBy { it.priority })

    /** This week's long run towards [longRunGoal], or `null` (no race, or the layer is off). */
    fun longRunPlan(input: SuggestionInput, phase: TrainingPhase): LongRunPlan? {
        val form = input.goalForm ?: return null
        val goal = longRunGoal(input.goals, input.todayDay) ?: return null
        val distance = goal.targetDistanceMeters ?: return null
        val daysOut = (goal.targetDay ?: return null) - input.todayDay
        val peak = min(distance * LONG_RUN_PEAK_SHARE, LONG_RUN_PEAK_CAP_M)
        val floor = min(distance * LONG_RUN_FLOOR_SHARE, peak)
        val base = max(form.longestRunMeters28d, 0.0)
        val raw = when {
            daysOut <= Periodization.RACE_WEEK_MAX_DAYS -> distance * LONG_RUN_RACE_WEEK_SHARE
            daysOut <= LONG_RUN_TAPER_DAYS -> peak * LONG_RUN_TAPER_SHARE
            phase == TrainingPhase.RECOVERY_WEEK -> max(base * LONG_RUN_DOWN_WEEK, floor)
            else -> max(base * LONG_RUN_GROWTH, floor)
        }
        val meters = roundToStep(min(raw, peak))
        return LongRunPlan(meters, base, peak, goal, daysOut, phase)
    }

    /** The long run's minutes at [paceSecPerKm] (or the 6:30 default), never under 10. */
    fun longRunMinutes(plan: LongRunPlan, paceSecPerKm: Int?): Int {
        val pace = paceSecPerKm ?: DEFAULT_LONG_RUN_SEC_PER_KM
        return max(SuggestionEngine.MIN_SESSION_MINUTES, TrimpDefaults.roundHalfUp(plan.meters / 1000.0 * pace / 60.0))
    }

    fun longRunEntry(plan: LongRunPlan): RationaleEntry {
        val km = kmLabel(plan.meters)
        val race = "${plan.goal.title} on ${plan.goal.targetDay?.let(::dayLabel).orEmpty()}"
        val text = when {
            plan.daysToRace <= LONG_RUN_TAPER_DAYS ->
                "Long run $km — shorter now so you arrive fresh for $race."
            plan.phase == TrainingPhase.RECOVERY_WEEK ->
                "Long run $km — a lighter week; the build towards ${kmLabel(plan.peakMeters)} for $race resumes next week."
            plan.meters >= plan.peakMeters ->
                "Long run $km — the peak distance of your build-up for $race."
            plan.baseMeters > 0.0 ->
                "Long run $km — building from ${kmLabel(plan.baseMeters)} (your longest in 4 weeks) " +
                    "towards ${kmLabel(plan.peakMeters)} for $race."
            else ->
                "Long run $km — the start of a build-up towards ${kmLabel(plan.peakMeters)} for $race."
        }
        return RationaleEntry(ruleId = RULE_LONG_RUN_BUILD, text = text)
    }

    // ---- outlook ----------------------------------------------------------------------------

    /** The race calendar as the Training screen shows it, or `null` without a dated race goal. */
    fun outlook(goals: List<Goal>, todayDay: Long): PhaseOutlook? {
        val race = Periodization.primaryRaceGoal(goals, todayDay) ?: return null
        val raceDay = race.targetDay ?: return null
        val daysOut = raceDay - todayDay
        val phase = Periodization.phase(daysOut, matchWithin21Days = false, weeksSincePlanStart = null)
        val (next, boundary) = when (phase) {
            TrainingPhase.BASE -> TrainingPhase.BUILD to Periodization.BUILD_MAX_DAYS
            TrainingPhase.BUILD -> TrainingPhase.PEAK to Periodization.PEAK_MAX_DAYS
            TrainingPhase.PEAK -> TrainingPhase.TAPER to Periodization.TAPER_MAX_DAYS
            TrainingPhase.TAPER -> TrainingPhase.RACE_WEEK to Periodization.RACE_WEEK_MAX_DAYS
            else -> null to null
        }
        return PhaseOutlook(
            phase = phase,
            nextPhase = next,
            nextPhaseDay = boundary?.let { raceDay - it },
            raceGoal = race,
        )
    }

    // ---- formatting -------------------------------------------------------------------------

    fun dayLabel(day: Long): String = LocalDate.ofEpochDay(day).format(DAY)

    private fun Goal.isTimedRun(): Boolean =
        type == GoalType.RACE_TIME && (targetTimeSec ?: 0) > 0 && (targetDistanceMeters ?: 0.0) > 0.0

    private fun roundToStep(meters: Double): Double =
        Math.round(meters / LONG_RUN_STEP_M) * LONG_RUN_STEP_M

    private fun kmLabel(meters: Double): String {
        val km = meters / 1000.0
        return if (km == Math.rint(km)) "${km.toInt()} km" else String.format(Locale.US, "%.1f km", km)
    }

    private fun distanceLabel(meters: Double): String = when {
        kotlin.math.abs(meters - 21_097.5) < 1.0 -> "half marathon"
        kotlin.math.abs(meters - 42_195.0) < 1.0 -> "marathon"
        else -> kmLabel(meters)
    }

    private fun percentLabel(gap: Double): String = String.format(Locale.US, "%+.1f %%", gap * 100.0)

    private fun mmss(seconds: Int): String = String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)

    private fun clock(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
    }

    private const val DAYS_PER_WEEK: Long = 7L
}
