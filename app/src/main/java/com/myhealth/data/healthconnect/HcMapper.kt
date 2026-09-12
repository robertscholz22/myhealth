package com.myhealth.data.healthconnect

import com.myhealth.domain.model.ActivitySession
import com.myhealth.domain.model.ActivitySource
import com.myhealth.domain.model.ActivitySourceRecord
import com.myhealth.domain.model.ActivityStreams
import com.myhealth.domain.model.BodyMeasurement
import com.myhealth.domain.model.DailyHealthSummary
import com.myhealth.domain.model.SleepRecord
import com.myhealth.domain.model.SleepStage
import com.myhealth.domain.model.SleepStageInterval
import com.myhealth.domain.model.SportType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.floor

/**
 * Health Connect DTOs → domain models (PLAN P2.3). Works purely on the plain data classes of
 * `HcDto.kt`, so `HcMapperTest` needs no Android and no device.
 *
 * Nothing here writes to the database: the ingestion pipeline (P2.5) takes the
 * [ActivitySourceRecord] as the idempotency key and the [ActivitySession] as the candidate for
 * the de-dup/merge engine (§2.4).
 */
interface HcMapper {

    /** The `activity_source_record` row — `externalId` is the Health Connect `metadata.id`. */
    fun toSourceRecord(exercise: HcExercise, receivedAtMillis: Long): ActivitySourceRecord

    /** The normalized, not-yet-merged candidate session for [exercise]. */
    fun toSession(exercise: HcExercise, zone: ZoneId, nowMillis: Long): ActivitySession

    fun toDailySummary(summary: HcDailySummary, updatedAtMillis: Long): DailyHealthSummary

    /** Sessions sharing a `night` are merged into one record (§2.2.3). */
    fun toSleepRecords(sleeps: List<HcSleep>, zone: ZoneId): List<SleepRecord>

    /** Weight and body-fat records taken at the same instant become one measurement. */
    fun toBodyMeasurements(bodies: List<HcBody>, zone: ZoneId): List<BodyMeasurement>
}

/** The normalized snapshot stored in `activity_source_record.payloadJson` (§2.2.2). */
@Serializable
internal data class HcExercisePayload(
    val externalId: String,
    val packageName: String?,
    val startMillis: Long,
    val endMillis: Long,
    val exerciseType: Int,
    val sportType: String,
    val title: String?,
    val notes: String?,
    val durationSec: Int,
    val distanceMeters: Double?,
    val totalEnergyKcal: Double?,
    val activeEnergyKcal: Double?,
    val elevationGainM: Double?,
    val avgHr: Int?,
    val maxHr: Int?,
    val avgSpeedMps: Double?,
    val maxSpeedMps: Double?,
    val avgCadenceSpm: Double?,
    val heartRateSampleCount: Int,
    val speedSampleCount: Int,
    val cadenceSampleCount: Int,
    val laps: List<HcLap>,
    val segments: List<HcSegment>,
)

/** Metrics derived once from an [HcExercise] and reused by the session and the payload. */
private data class Derived(
    val sportType: SportType,
    val durationSec: Int,
    val avgHr: Int?,
    val maxHr: Int?,
    val avgSpeedMps: Double?,
    val maxSpeedMps: Double?,
    val avgCadenceSpm: Double?,
)

class HealthConnectMapper(
    private val json: Json = Json { encodeDefaults = true },
) : HcMapper {

    override fun toSourceRecord(
        exercise: HcExercise,
        receivedAtMillis: Long,
    ): ActivitySourceRecord {
        val derived = exercise.derive()
        val payload = HcExercisePayload(
            externalId = exercise.externalId,
            packageName = exercise.packageName,
            startMillis = exercise.startMillis,
            endMillis = exercise.endMillis,
            exerciseType = exercise.exerciseType,
            sportType = derived.sportType.name,
            title = exercise.title,
            notes = exercise.notes,
            durationSec = derived.durationSec,
            distanceMeters = exercise.distanceMeters,
            totalEnergyKcal = exercise.totalEnergyKcal,
            activeEnergyKcal = exercise.activeEnergyKcal,
            elevationGainM = exercise.elevationGainM,
            avgHr = derived.avgHr,
            maxHr = derived.maxHr,
            avgSpeedMps = derived.avgSpeedMps,
            maxSpeedMps = derived.maxSpeedMps,
            avgCadenceSpm = derived.avgCadenceSpm,
            heartRateSampleCount = exercise.heartRateSamples.size,
            speedSampleCount = exercise.speedSamples.size,
            cadenceSampleCount = exercise.cadenceSamples.size,
            laps = exercise.laps,
            segments = exercise.segments,
        )
        return ActivitySourceRecord(
            id = 0,
            activityId = null,
            source = ActivitySource.HEALTH_CONNECT,
            externalId = exercise.externalId,
            payloadJson = json.encodeToString(payload),
            receivedAtMillis = receivedAtMillis,
        )
    }

    override fun toSession(
        exercise: HcExercise,
        zone: ZoneId,
        nowMillis: Long,
    ): ActivitySession {
        val derived = exercise.derive()
        val group = derived.sportType.group
        val streams = exercise.toStreams()
        return ActivitySession(
            id = 0,
            startAtMillis = exercise.startMillis,
            endAtMillis = exercise.endMillis,
            day = exercise.startMillis.toLocalDay(zone),
            sportType = derived.sportType,
            sportGroup = group,
            title = exercise.title,
            // Health Connect exposes no moving time, so timer time is elapsed time.
            durationSec = derived.durationSec,
            elapsedSec = derived.durationSec,
            distanceMeters = exercise.distanceMeters,
            activeEnergyKcal = exercise.activeEnergyKcal,
            totalEnergyKcal = exercise.totalEnergyKcal,
            avgHr = derived.avgHr,
            maxHr = derived.maxHr,
            avgSpeedMps = derived.avgSpeedMps,
            maxSpeedMps = derived.maxSpeedMps,
            avgCadenceSpm = derived.avgCadenceSpm,
            elevationGainM = exercise.elevationGainM,
            trimp = null,
            loadMethod = null,
            rpe = null,
            note = exercise.notes,
            primarySource = ActivitySource.HEALTH_CONNECT,
            mergedSources = listOf(ActivitySource.HEALTH_CONNECT),
            dedupeBucket = "$group|${exercise.startMillis / DEDUPE_BUCKET_MILLIS}",
            userEditedFields = emptyList(),
            hasStreams = streams != null,
            streams = streams,
            laps = emptyList(),
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
        )
    }

    override fun toDailySummary(
        summary: HcDailySummary,
        updatedAtMillis: Long,
    ): DailyHealthSummary = DailyHealthSummary(
        day = summary.day,
        steps = summary.steps,
        totalEnergyKcal = summary.totalEnergyKcal,
        activeEnergyKcal = summary.activeEnergyKcal,
        restingHr = summary.restingHr,
        distanceMeters = summary.distanceMeters,
        floors = summary.floors,
        avgSpo2Percent = summary.avgSpo2Percent,
        avgRespiratoryRate = summary.avgRespiratoryRate,
        hrvRmssdMs = summary.hrvRmssdMs,
        vo2Max = summary.vo2Max,
        // Only the Tier-3 Garmin client (P9) can fill these.
        bodyBattery = null,
        stressAvg = null,
        trainingReadiness = null,
        source = ActivitySource.HEALTH_CONNECT,
        updatedAtMillis = updatedAtMillis,
    )

    override fun toSleepRecords(sleeps: List<HcSleep>, zone: ZoneId): List<SleepRecord> = sleeps
        .groupBy { it.endMillis.toLocalDay(zone) }
        .map { (night, group) -> mergeNight(night, group) }
        .sortedBy { it.night }

    override fun toBodyMeasurements(
        bodies: List<HcBody>,
        zone: ZoneId,
    ): List<BodyMeasurement> = bodies
        .groupBy { it.timeMillis }
        .map { (atMillis, group) -> mergeBody(atMillis, group, zone) }
        .sortedBy { it.measuredAtMillis }

    /**
     * Union of the sessions attributed to one night: the widest interval, the concatenated
     * stages, and per-stage minutes. `externalId` is the earliest session's id so re-syncing the
     * same night produces the same row (the `(source, externalId)` unique index).
     */
    private fun mergeNight(night: Long, group: List<HcSleep>): SleepRecord {
        val ordered = group.sortedBy { it.startMillis }
        val start = ordered.minOf { it.startMillis }
        val end = ordered.maxOf { it.endMillis }
        val stages = ordered
            .flatMap { sleep -> sleep.stages }
            .sortedBy { it.startMillis }
            .map {
                SleepStageInterval(it.startMillis, it.endMillis, SleepStageMap.toSleepStage(it.stage))
            }
        val minutes = stages.groupBy { it.stage }
            .mapValues { (_, list) -> list.sumOf { it.endAtMillis - it.startAtMillis }.toMinutes() }
        val asleepMin = stages
            .filter { SleepStageMap.isAsleep(it.stage) }
            .sumOf { it.endAtMillis - it.startAtMillis }
            .toMinutes()
        return SleepRecord(
            id = 0,
            startAtMillis = start,
            endAtMillis = end,
            night = night,
            totalSleepMin = if (stages.isEmpty()) (end - start).toMinutes() else asleepMin,
            lightMin = if (stages.isEmpty()) null else minutes[SleepStage.LIGHT] ?: 0,
            deepMin = if (stages.isEmpty()) null else minutes[SleepStage.DEEP] ?: 0,
            remMin = if (stages.isEmpty()) null else minutes[SleepStage.REM] ?: 0,
            awakeMin = if (stages.isEmpty()) {
                null
            } else {
                (minutes[SleepStage.AWAKE] ?: 0) + (minutes[SleepStage.AWAKE_IN_BED] ?: 0)
            },
            stages = stages.ifEmpty { null },
            source = ActivitySource.HEALTH_CONNECT,
            externalId = ordered.first().externalId,
            sleepScore = null,
        )
    }

    private fun mergeBody(
        atMillis: Long,
        group: List<HcBody>,
        zone: ZoneId,
    ): BodyMeasurement {
        val weight = group.firstOrNull { it.weightKg != null }
        val fat = group.firstOrNull { it.bodyFatPercent != null }
        return BodyMeasurement(
            id = 0,
            measuredAtMillis = atMillis,
            day = atMillis.toLocalDay(zone),
            weightKg = weight?.weightKg,
            bodyFatPercent = fat?.bodyFatPercent,
            muscleMassKg = null,
            boneMassKg = null,
            bodyWaterPercent = null,
            source = ActivitySource.HEALTH_CONNECT,
            // Weight is the anchor when both exist, so the id is stable across re-syncs.
            externalId = (weight ?: fat ?: group.first()).externalId,
            note = null,
        )
    }

    private companion object {
        /** 5-minute de-dup buckets (§2.4). */
        const val DEDUPE_BUCKET_MILLIS = 300_000L
    }
}

// ---- derived metrics ----------------------------------------------------------------------

private fun HcExercise.derive(): Derived {
    val durationSec = ((endMillis - startMillis) / 1000L).toInt().coerceAtLeast(0)
    val hr = heartRateSamples.map { it.bpm }
    val speed = speedSamples.map { it.value }
    val cadence = cadenceSamples.map { it.value }
    val fallbackSpeed = distanceMeters?.takeIf { durationSec > 0 }?.div(durationSec)
    return Derived(
        sportType = ExerciseTypeMap.toSportType(exerciseType, title),
        durationSec = durationSec,
        avgHr = if (hr.isEmpty()) null else hr.average().roundHalfUp(),
        maxHr = hr.maxOrNull(),
        avgSpeedMps = if (speed.isEmpty()) fallbackSpeed else speed.average(),
        maxSpeedMps = speed.maxOrNull(),
        avgCadenceSpm = if (cadence.isEmpty()) null else cadence.average(),
    )
}

/**
 * Builds the shared time axis (§2.2.2) from the heart-rate samples — the only per-sample channel
 * Health Connect reliably provides and the one the TRIMP engine needs (§3.2). Speed and cadence,
 * which arrive on their own clocks, are resampled onto that axis with a last-value-carried-
 * forward fill. Falls back to the speed axis when there is no heart rate at all, and returns
 * `null` when the session has no samples whatsoever.
 *
 * Down-sampling: at most one sample per second, keeping the first of each second.
 */
private fun HcExercise.toStreams(): ActivityStreams? {
    val hrAxis = heartRateSamples.map { it.timeMillis }.toOffsets(startMillis)
    val axis = if (hrAxis.isNotEmpty()) {
        hrAxis
    } else {
        speedSamples.map { it.timeMillis }.toOffsets(startMillis)
    }
    if (axis.isEmpty()) return null

    val offsets = axis.map { it.second }
    val hr: List<Int?> = if (hrAxis.isNotEmpty()) {
        axis.map { (index, _) -> heartRateSamples[index].bpm }
    } else {
        List(axis.size) { null }
    }
    return ActivityStreams(
        sampleOffsetsSec = offsets.toIntArray(),
        hr = hr,
        distanceMeters = null,
        speedMps = speedSamples.resampleOnto(offsets, startMillis),
        cadenceSpm = cadenceSamples.resampleOnto(offsets, startMillis),
        altitudeM = null,
        latLngE7 = null,
        sampleCount = offsets.size,
        medianIntervalSec = offsets.medianInterval(),
    )
}

/**
 * Sample index → whole-second offset from [startMillis], at most one entry per second and never
 * negative. Input is assumed sorted by time (the reader sorts it).
 */
private fun List<Long>.toOffsets(startMillis: Long): List<Pair<Int, Int>> {
    val out = mutableListOf<Pair<Int, Int>>()
    var lastSecond = Int.MIN_VALUE
    forEachIndexed { index, timeMillis ->
        val second = ((timeMillis - startMillis) / 1000L).toInt()
        if (second >= 0 && second > lastSecond) {
            out += index to second
            lastSecond = second
        }
    }
    return out
}

/** Last-value-carried-forward resampling onto [offsets]; `null` when there is nothing to carry. */
private fun List<HcSample>.resampleOnto(offsets: List<Int>, startMillis: Long): DoubleArray? {
    if (isEmpty()) return null
    var cursor = 0
    var current = first().value
    return DoubleArray(offsets.size) { i ->
        val atMillis = startMillis + offsets[i] * 1000L
        while (cursor < size && this[cursor].timeMillis <= atMillis) {
            current = this[cursor].value
            cursor++
        }
        current
    }
}

/** Median gap between consecutive offsets; `0.0` for a single sample. */
private fun List<Int>.medianInterval(): Double {
    if (size < 2) return 0.0
    val gaps = (1 until size).map { (this[it] - this[it - 1]).toDouble() }.sorted()
    val middle = gaps.size / 2
    return if (gaps.size % 2 == 1) gaps[middle] else (gaps[middle - 1] + gaps[middle]) / 2.0
}

// ---- small helpers ------------------------------------------------------------------------

/** Half-up rounding (§3 preamble) — `kotlin.math.round` is half-to-even. */
private fun Double.roundHalfUp(): Int = floor(this + 0.5).toInt()

private fun Long.toMinutes(): Int = (this / 60_000L).toInt()

private fun Long.toLocalDay(zone: ZoneId): Long =
    LocalDate.ofInstant(Instant.ofEpochMilli(this), zone).toEpochDay()
