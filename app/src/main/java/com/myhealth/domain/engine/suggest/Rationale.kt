package com.myhealth.domain.engine.suggest

import com.myhealth.domain.engine.load.TrimpDefaults
import com.myhealth.domain.model.Intensity
import com.myhealth.domain.model.RationaleEntry
import com.myhealth.domain.model.RecoveryBand
import com.myhealth.domain.model.SessionType
import com.myhealth.domain.model.SportGroup
import com.myhealth.domain.model.TrainingPhase

/** What [Rationale.forSession] needs to explain one placement (PLAN §3.5.6 step 8). */
data class RationaleContext(
    val phase: TrainingPhase,
    val weeklyTarget: Double,
    /** Budget still unallocated **before** this session is subtracted. */
    val remainingBudget: Double,
    val recoveryScore: Int? = null,
    val recoveryBand: RecoveryBand? = null,
    /** Hours from the candidate's day to the next match/race, when one is close (C1/C2). */
    val hoursToKeyEvent: Long? = null,
    val keyEventLabel: String = "match",
    val primaryGoalTitle: String? = null,
    val sportCap: Int? = null,
    val sportUsed: Int = 0,
)

/**
 * The `{ruleId, text}` rationale of PLAN §3.5.6 step 8 — the "why" every suggestion card shows.
 *
 * Rule ids are stable identifiers (`PHASE_BUILD`, `BUDGET`, `C1_RESPECTED`, `RECOVERY_GOOD`, …) so
 * the UI can group or icon them later; the text is a full English sentence. The phase entry is
 * always emitted, which is what makes the §3.5.7 `sug15` guarantee ("every session has a non-empty
 * rationale") hold by construction.
 */
object Rationale {

    const val RULE_BUDGET: String = "BUDGET"
    const val RULE_C1_RESPECTED: String = "C1_RESPECTED"
    const val RULE_C4_RESPECTED: String = "C4_RESPECTED"
    const val RULE_SPORT_CAP: String = "SPORT_CAP"
    const val RULE_MOBILITY_REST_DAY: String = "MOBILITY_REST_DAY"
    const val RULE_DOWNGRADED: String = "DOWNGRADED_BEFORE_EVENT"

    fun phaseRuleId(phase: TrainingPhase): String = "PHASE_${phase.name}"

    fun recoveryRuleId(band: RecoveryBand?): String = "RECOVERY_${band?.name ?: "UNKNOWN"}"

    /** The ordered rationale of one placed session. */
    fun forSession(candidate: Candidate, ctx: RationaleContext): List<RationaleEntry> {
        val entries = mutableListOf(phaseEntry(candidate.sessionType, ctx))
        entries += budgetEntry(ctx)
        keyEventEntry(candidate, ctx)?.let { entries += it }
        recoveryEntry(ctx)?.let { entries += it }
        capEntry(candidate.sportGroup, ctx)?.let { entries += it }
        return entries
    }

    /** The rationale of a mobility session added by post-pass 7c. */
    fun forMobility(phase: TrainingPhase): List<RationaleEntry> = listOf(
        RationaleEntry(
            ruleId = RULE_MOBILITY_REST_DAY,
            text = "Rest day: 20 minutes of mobility keeps the day easy and still useful.",
        ),
        RationaleEntry(ruleId = phaseRuleId(phase), text = "${phaseLabel(phase)}: no training load on rest days."),
    )

    /** Appended when post-pass 7b downgrades a session on the eve of a match or race. */
    fun downgradeEntry(label: String): RationaleEntry = RationaleEntry(
        ruleId = RULE_DOWNGRADED,
        text = "Downgraded to an easy run — $label tomorrow.",
    )

    private fun phaseEntry(sessionType: SessionType, ctx: RationaleContext): RationaleEntry {
        val goal = ctx.primaryGoalTitle?.let { " for $it" } ?: ""
        return RationaleEntry(
            ruleId = phaseRuleId(ctx.phase),
            text = "${phaseLabel(ctx.phase)}: ${sessionPurpose(sessionType)}$goal.",
        )
    }

    private fun budgetEntry(ctx: RationaleContext): RationaleEntry = RationaleEntry(
        ruleId = RULE_BUDGET,
        text = "Weekly load target ${au(ctx.weeklyTarget)} AU; ${au(ctx.remainingBudget)} AU still unallocated.",
    )

    private fun keyEventEntry(candidate: Candidate, ctx: RationaleContext): RationaleEntry? {
        val hours = ctx.hoursToKeyEvent ?: return null
        val verb = if (candidate.intensity.ordinal <= Intensity.LOW.ordinal) "Kept easy" else "Scheduled"
        return RationaleEntry(
            ruleId = RULE_C1_RESPECTED,
            text = "$verb — ${ctx.keyEventLabel} in $hours h.",
        )
    }

    private fun recoveryEntry(ctx: RationaleContext): RationaleEntry? {
        val band = ctx.recoveryBand ?: return null
        val score = ctx.recoveryScore?.let { "Recovery $it/100" } ?: "Recovery ${band.name.lowercase()}"
        val advice = when (band) {
            RecoveryBand.FRESH, RecoveryBand.GOOD -> "you can absorb a quality session"
            RecoveryBand.MODERATE -> "moderate — a steady session is the safe choice"
            RecoveryBand.FATIGUED -> "keeping the intensity down until it comes back up"
            RecoveryBand.STRAINED -> "recovery only"
        }
        return RationaleEntry(ruleId = recoveryRuleId(band), text = "$score, $advice.")
    }

    private fun capEntry(group: SportGroup, ctx: RationaleContext): RationaleEntry? {
        val cap = ctx.sportCap ?: return null
        return RationaleEntry(
            ruleId = RULE_SPORT_CAP,
            text = "${groupLabel(group)}: ${ctx.sportUsed + 1} of $cap sessions this week.",
        )
    }

    private fun au(value: Double): Int = TrimpDefaults.roundHalfUp(value)

    fun phaseLabel(phase: TrainingPhase): String = when (phase) {
        TrainingPhase.BASE -> "Base phase"
        TrainingPhase.BUILD -> "Build phase"
        TrainingPhase.PEAK -> "Peak phase"
        TrainingPhase.TAPER -> "Taper"
        TrainingPhase.RACE_WEEK -> "Race week"
        TrainingPhase.IN_SEASON -> "In season"
        TrainingPhase.OFF_SEASON -> "Off season"
        TrainingPhase.RECOVERY_WEEK -> "Recovery week"
    }

    private fun sessionPurpose(sessionType: SessionType): String = when (sessionType) {
        SessionType.EASY_RUN -> "easy running builds aerobic volume"
        SessionType.LONG_RUN -> "the long run builds endurance"
        SessionType.TEMPO_RUN -> "tempo work develops threshold"
        SessionType.INTERVAL_RUN -> "intervals sharpen top-end speed"
        SessionType.RECOVERY_RUN -> "a recovery run keeps the legs moving without cost"
        SessionType.STRENGTH_FULL -> "full-body strength supports every sport"
        SessionType.STRENGTH_UPPER -> "upper-body strength adds no leg fatigue"
        SessionType.STRENGTH_LOWER -> "heavy legs build running-specific strength"
        SessionType.SOCCER_TRAINING -> "team training keeps match sharpness"
        SessionType.SOCCER_MATCH -> "the match is the session"
        SessionType.MOBILITY -> "mobility work protects the next hard day"
        SessionType.CROSS_TRAINING -> "cross-training adds aerobic load without impact"
        SessionType.REST -> "rest is the session"
    }

    private fun groupLabel(group: SportGroup): String = when (group) {
        SportGroup.RUN -> "Running"
        SportGroup.STRENGTH -> "Strength"
        SportGroup.SOCCER -> "Soccer"
        SportGroup.CYCLE -> "Cycling"
        SportGroup.WALK -> "Walking"
        SportGroup.SWIM -> "Swimming"
        SportGroup.OTHER -> "Other"
    }
}
