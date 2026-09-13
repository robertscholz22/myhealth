package com.myhealth.data.repository

import com.google.common.truth.Truth.assertThat
import com.myhealth.data.healthconnect.FakeHealthRepository
import com.myhealth.domain.model.ActivitySession
import com.myhealth.domain.model.ActivitySource
import com.myhealth.domain.model.DailyLoad
import com.myhealth.domain.model.LoadMethod
import com.myhealth.domain.model.Profile
import com.myhealth.domain.model.Sex
import com.myhealth.domain.model.SportGroup
import com.myhealth.domain.model.SportType
import com.myhealth.domain.util.Outcome
import com.myhealth.testutil.Fixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * [LoadRecomputeService] against in-memory fakes (PLAN P5.5): TRIMP gets filled in for an activity
 * with an average HR, a rerun over the same window is idempotent, and a 400-day history recomputes
 * quickly. [FakeActivityDao] plays double duty as both the `ActivityDao` the service reads
 * `sumTrimpPerDay` from and the backing store of the [RoomActivityRepository] it calls
 * `setTrimp` through, so both collaborators see the same writes.
 */
class LoadRecomputeTest {

    private val clock = Fixtures.fixedClock("2026-09-12T20:00:00Z")
    private val today = Fixtures.epochDay("2026-09-12")

    private val activityDao = FakeActivityDao()
    private val activityRepo = RoomActivityRepository(
        activityDao,
        ActivityIngestor(activityDao, DirectTransactionRunner, clock),
        clock,
        ioDispatcher = Dispatchers.Unconfined,
    )
    private val loadRepo = FakeLoadRepository()
    private val runningBestRepo = FakeRunningBestRepository()
    private val profileRepo = FakeTargetProfileRepository(testProfile())
    private val healthRepo = FakeHealthRepository()
    private val settingsRepo = FakeLoadSettingsRepository()

    private val service = LoadRecomputeService(
        activityDao = activityDao,
        activityRepo = activityRepo,
        loadRepo = loadRepo,
        runningBestRepo = runningBestRepo,
        profileRepo = profileRepo,
        healthRepo = healthRepo,
        settingsRepo = settingsRepo,
        clock = clock,
        ioDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun trimp_is_filled_for_an_activity_with_average_hr() = runTest {
        val id = seedActivity(day = today, avgHr = 150, durationSec = 3600)

        service.recompute(today)

        val stored = activityDao.getById(id)
        assertThat(stored?.trimp).isNotNull()
        assertThat(stored!!.trimp!!).isGreaterThan(0.0)
        assertThat(stored.loadMethod).isEqualTo(LoadMethod.HR_AVERAGE)
    }

    @Test
    fun recompute_is_idempotent() = runTest {
        seedActivity(day = today, avgHr = 150, durationSec = 3600)
        seedActivity(day = today - 1, avgHr = 140, durationSec = 1800)
        seedActivity(day = today - 5, avgHr = 160, durationSec = 2700, sportType = SportType.SOCCER_MATCH)

        service.recompute(today)
        val firstRun = loadRepo.rows.toMap()
        assertThat(firstRun).isNotEmpty()

        service.recompute(today)
        val secondRun = loadRepo.rows.toMap()

        assertThat(secondRun).isEqualTo(firstRun)
    }

    @Test
    fun backfill_starts_at_the_first_activity_day_not_fromDay_minus_28() = runTest {
        val firstActivityDay = today - 10
        seedActivity(day = firstActivityDay, avgHr = 150, durationSec = 3600)

        service.recompute(today - 1_000)

        assertThat(loadRepo.rows.keys).isNotEmpty()
        assertThat(loadRepo.rows.keys.min()).isEqualTo(firstActivityDay)
        assertThat(loadRepo.rows.keys.all { it >= firstActivityDay }).isTrue()
    }

    @Test
    fun rows_before_the_first_activity_are_deleted_on_recompute() = runTest {
        val firstActivityDay = today - 10
        loadRepo.rows[today - 500] = staleRow(today - 500)
        loadRepo.rows[firstActivityDay - 1] = staleRow(firstActivityDay - 1)
        seedActivity(day = firstActivityDay, avgHr = 150, durationSec = 3600)

        service.recompute(today - 1_000)

        assertThat(loadRepo.rows).doesNotContainKey(today - 500)
        assertThat(loadRepo.rows).doesNotContainKey(firstActivityDay - 1)
    }

    @Test
    fun no_activities_means_no_rows() = runTest {
        loadRepo.rows[today - 5] = staleRow(today - 5)

        service.recompute(today)

        assertThat(loadRepo.rows).isEmpty()
    }

    @Test
    fun four_hundred_days_of_history_recomputes_quickly() = runTest {
        for (offset in 0 until 400) {
            seedActivity(day = today - offset.toLong(), avgHr = 130 + offset % 20, durationSec = 2_400)
        }

        val start = System.nanoTime()
        service.recompute(today - 400)
        val elapsedMillis = (System.nanoTime() - start) / 1_000_000

        println("LoadRecomputeService.recompute over 400 days took $elapsedMillis ms")
        // Generous bound to avoid flakiness on a loaded CI box (PLAN §5 P5.5); the value above is
        // what actually gets reported.
        assertThat(elapsedMillis).isLessThan(2_000L)
    }

    private suspend fun seedActivity(
        day: Long,
        avgHr: Int,
        durationSec: Int,
        sportType: SportType = SportType.RUN_OUTDOOR,
    ): Long {
        val startAtMillis = day * 86_400_000L + 6 * 3_600_000L
        val session = ActivitySession(
            id = 0L,
            startAtMillis = startAtMillis,
            endAtMillis = startAtMillis + durationSec * 1_000L,
            day = day,
            sportType = sportType,
            sportGroup = sportType.group,
            title = null,
            durationSec = durationSec,
            elapsedSec = durationSec,
            distanceMeters = if (sportType.group == SportGroup.RUN) 8_000.0 else null,
            activeEnergyKcal = null,
            totalEnergyKcal = null,
            avgHr = avgHr,
            maxHr = avgHr + 20,
            avgSpeedMps = null,
            maxSpeedMps = null,
            avgCadenceSpm = null,
            elevationGainM = null,
            trimp = null,
            loadMethod = null,
            rpe = null,
            note = null,
            primarySource = ActivitySource.MANUAL,
            mergedSources = listOf(ActivitySource.MANUAL),
            dedupeBucket = "${sportType.group}|${startAtMillis / 300_000}",
            userEditedFields = emptyList(),
            hasStreams = false,
            streams = null,
            laps = emptyList(),
            createdAtMillis = startAtMillis,
            updatedAtMillis = startAtMillis,
        )
        return (activityRepo.upsert(session) as Outcome.Ok).value
    }

    /** A minimal cached row for pre-populating [FakeLoadRepository] in the POLISH-13 tests below;
     * the values themselves are never asserted on, only whether the row survives a recompute. */
    private fun staleRow(day: Long): DailyLoad = DailyLoad(
        day = day,
        trimp = 0.0,
        sessionCount = 0,
        atl = 0.0,
        ctl = 0.0,
        acwr = null,
        tsb = 0.0,
        monotony = null,
        strain = null,
        recoveryScore = null,
        recoveryBand = null,
        recoveryConfidence = 0.0,
        flags = emptyList(),
        computedAtMillis = 0L,
    )

    private fun testProfile(): Profile = Profile(
        displayName = "Test",
        sex = Sex.MALE,
        birthDay = Fixtures.epochDay("1990-01-01"),
        heightCm = 180.0,
        createdAtMillis = 0L,
        updatedAtMillis = 0L,
    )
}
