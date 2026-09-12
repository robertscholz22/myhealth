package com.myhealth.domain.engine.suggest

import com.myhealth.domain.model.ActivitySource
import com.myhealth.domain.model.ActivitySummary
import com.myhealth.domain.model.DailyLoad
import com.myhealth.domain.model.EventOccurrence
import com.myhealth.domain.model.EventType
import com.myhealth.domain.model.Goal
import com.myhealth.domain.model.GoalStatus
import com.myhealth.domain.model.GoalType
import com.myhealth.domain.model.Intensity
import com.myhealth.domain.model.PlannedSession
import com.myhealth.domain.model.PlannedStatus
import com.myhealth.domain.model.Profile
import com.myhealth.domain.model.RecoveryBand
import com.myhealth.domain.model.RecoveryState
import com.myhealth.domain.model.SessionType
import com.myhealth.domain.model.Sex
import com.myhealth.domain.model.SportType
import com.myhealth.testutil.Fixtures
import java.time.LocalDate

/**
 * Builders for the suggestion-engine tests (PLAN §3.5). Every default is deliberately boring so a
 * test only states what it is actually about.
 *
 * "Today" is **Monday 2026-09-14** everywhere, so weekday-sensitive rules (C12's Sat/Sun long run)
 * are readable as day offsets: day +5 is Saturday, day +6 is Sunday.
 */
object SuggestFixtures {

    val TODAY: LocalDate = LocalDate.of(2026, 9, 14)
    val TODAY_DAY: Long = TODAY.toEpochDay()

    fun day(offset: Long): Long = TODAY_DAY + offset

    fun profile(
        preferredSportsJson: String = "{}",
        mobilityOnRestDays: Boolean = false,
    ): Profile = Profile(
        id = 1L,
        displayName = "Robert",
        sex = Sex.MALE,
        birthDay = Fixtures.epochDay("1990-05-20"),
        heightCm = 182.0,
        preferredSportsJson = preferredSportsJson,
        mobilityOnRestDays = mobilityOnRestDays,
        createdAtMillis = 0L,
        updatedAtMillis = 0L,
    )

    fun load(
        day: Long,
        trimp: Double = 0.0,
        atl: Double = 0.0,
        ctl: Double = 0.0,
        acwr: Double? = null,
    ): DailyLoad = DailyLoad(
        day = day,
        trimp = trimp,
        sessionCount = if (trimp > 0.0) 1 else 0,
        atl = atl,
        ctl = ctl,
        acwr = acwr,
        tsb = ctl - atl,
        monotony = null,
        strain = null,
        recoveryScore = null,
        recoveryBand = null,
        recoveryConfidence = 1.0,
        flags = emptyList(),
        computedAtMillis = 0L,
    )

    /**
     * 42 days of history ending yesterday: every day carries [dailyTrimp], so
     * `lastWeekActual = 7 * dailyTrimp`, and the newest row carries [ctl] / [acwr].
     */
    fun loadHistory(
        todayDay: Long = TODAY_DAY,
        ctl: Double = 40.0,
        dailyTrimp: Double = 40.0,
        acwr: Double? = 1.0,
    ): List<DailyLoad> = (1..42).map { back ->
        val d = todayDay - back
        load(day = d, trimp = dailyTrimp, atl = ctl, ctl = ctl, acwr = if (back == 1) acwr else null)
    }.sortedBy { it.day }

    fun recovery(band: RecoveryBand?, score: Int? = 70, day: Long = TODAY_DAY): RecoveryState =
        RecoveryState(
            day = day,
            score = score,
            band = band,
            confidence = 1.0,
            components = emptyList(),
            flags = emptyList(),
            warnings = emptyList(),
        )

    fun event(
        day: Long,
        type: EventType,
        eventId: Long = day,
        durationMin: Int? = null,
        sportType: SportType? = null,
    ): EventOccurrence = EventOccurrence(
        eventId = eventId,
        occurrenceDay = day,
        type = type,
        effectiveTitle = type.name,
        effectiveStartMinuteOfDay = 600,
        effectiveDurationMin = durationMin,
        isOverride = false,
        linkedActivityId = null,
        sportType = sportType,
        targetDistanceMeters = null,
        isKeyEvent = type == EventType.RACE,
    )

    fun raceGoal(
        targetDay: Long,
        targetTimeSec: Int = 1200,
        distanceMeters: Double = 5000.0,
        priority: Int = 1,
        id: Long = 1L,
    ): Goal = goal(
        id = id,
        type = GoalType.RACE_TIME,
        title = "5k in 20:00",
        targetDay = targetDay,
        targetDistanceMeters = distanceMeters,
        targetTimeSec = targetTimeSec,
        priority = priority,
    )

    @Suppress("LongParameterList")
    fun goal(
        id: Long = 1L,
        type: GoalType = GoalType.RACE_TIME,
        title: String = "Goal",
        targetDay: Long? = null,
        targetDistanceMeters: Double? = null,
        targetTimeSec: Int? = null,
        targetWeightKg: Double? = null,
        targetValue: Double? = null,
        priority: Int = 1,
        status: GoalStatus = GoalStatus.ACTIVE,
        createdAtMillis: Long = 0L,
    ): Goal = Goal(
        id = id,
        type = type,
        title = title,
        targetDay = targetDay,
        targetDistanceMeters = targetDistanceMeters,
        targetTimeSec = targetTimeSec,
        targetWeightKg = targetWeightKg,
        targetValue = targetValue,
        priority = priority,
        status = status,
        linkedEventId = null,
        notes = null,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = createdAtMillis,
    )

    fun locked(
        day: Long,
        sessionType: SessionType = SessionType.EASY_RUN,
        sportType: SportType = SportType.RUN_OUTDOOR,
        intensity: Intensity = Intensity.LOW,
        minutes: Int? = 45,
        estimatedTrimp: Double? = 54.0,
        id: Long = day,
    ): PlannedSession = PlannedSession(
        id = id,
        planId = null,
        day = day,
        startMinuteOfDay = 420,
        sportType = sportType,
        sessionType = sessionType,
        intensity = intensity,
        targetDurationMin = minutes,
        targetDistanceMeters = null,
        targetPaceSecPerKm = null,
        estimatedTrimp = estimatedTrimp,
        description = null,
        rationale = null,
        status = PlannedStatus.PLANNED,
        locked = true,
        linkedActivityId = null,
        sourceSuggestionId = null,
        createdAtMillis = 0L,
        updatedAtMillis = 0L,
    )

    fun activity(
        day: Long,
        trimp: Double,
        sportType: SportType = SportType.SOCCER_MATCH,
        id: Long = day,
        durationSec: Int = 5400,
    ): ActivitySummary = ActivitySummary(
        id = id,
        startAtMillis = day * 86_400_000L,
        endAtMillis = day * 86_400_000L + durationSec * 1000L,
        day = day,
        sportType = sportType,
        sportGroup = sportType.group,
        title = null,
        durationSec = durationSec,
        elapsedSec = durationSec,
        distanceMeters = null,
        activeEnergyKcal = null,
        totalEnergyKcal = null,
        avgHr = null,
        maxHr = null,
        avgSpeedMps = null,
        maxSpeedMps = null,
        avgCadenceSpm = null,
        elevationGainM = null,
        trimp = trimp,
        loadMethod = null,
        rpe = null,
        note = null,
        primarySource = ActivitySource.MANUAL,
        mergedSources = listOf(ActivitySource.MANUAL),
        hasStreams = false,
    )

    /** A [SuggestionInput] with a 7-day horizon, 40 AU/day of history and nothing else going on. */
    @Suppress("LongParameterList")
    fun input(
        horizonDays: Int = 7,
        goals: List<Goal> = emptyList(),
        events: List<EventOccurrence> = emptyList(),
        lockedPlanned: List<PlannedSession> = emptyList(),
        recentLoad: List<DailyLoad> = loadHistory(),
        recovery: RecoveryState? = null,
        recentActivities: List<ActivitySummary> = emptyList(),
        profile: Profile = profile(),
        planStartDay: Long? = null,
    ): SuggestionInput = SuggestionInput(
        today = TODAY,
        horizonDays = horizonDays,
        profile = profile,
        goals = goals,
        events = events,
        lockedPlanned = lockedPlanned,
        recentLoad = recentLoad,
        recovery = recovery,
        recentActivities = recentActivities,
        planStartDay = planStartDay,
    )

    /** A seeded grid for constraint tests — the engine's step 1 without the engine. */
    fun grid(input: SuggestionInput): SuggestionGrid = SuggestionGrid.seed(input)

    fun candidate(sessionType: SessionType, day: Long, minutes: Int? = null): Candidate {
        val entry = requireNotNull(SessionCatalog.entryFor(sessionType)) { "no catalog row for $sessionType" }
        return Candidate(entry = entry, day = day, minutes = minutes ?: entry.defaultMin)
    }
}
