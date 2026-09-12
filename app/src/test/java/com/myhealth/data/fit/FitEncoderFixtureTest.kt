package com.myhealth.data.fit

import com.garmin.fit.Activity
import com.garmin.fit.ActivityMesg
import com.garmin.fit.DateTime
import com.garmin.fit.Event
import com.garmin.fit.EventType
import com.garmin.fit.FileEncoder
import com.garmin.fit.FileIdMesg
import com.garmin.fit.Fit
import com.garmin.fit.LapMesg
import com.garmin.fit.RecordMesg
import com.garmin.fit.SessionMesg
import com.garmin.fit.Sport
import com.garmin.fit.SubSport
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * Produces the one **binary** FIT fixture the project needs (PLAN P7.2): JSON fixtures cover the
 * mapper, but nothing else would ever exercise the real SDK decoder, the R8 keep rule or the
 * end-to-end import on a device.
 *
 * The file is written with the SDK's own `FileEncoder` — a 5 km run with a file id, one session,
 * two laps and one record per second carrying HR, cumulative distance, speed and position — and
 * is also published to `docs/testassets/run_5k.fit` for the lead's emulator walkthrough.
 * Generation is idempotent: an existing fixture is left untouched so the bytes stay stable.
 */
class FitEncoderFixtureTest {

    @Test
    fun encoder_writes_the_run_5k_fixture_when_it_is_missing() {
        val file = RunFixtureEncoder.ensure()

        assertThat(file.isFile).isTrue()
        assertThat(file.length()).isGreaterThan(1_000L)
        // ".FIT" is at bytes 8..11 of every FIT header.
        assertThat(String(file.readBytes().copyOfRange(8, 12), Charsets.US_ASCII)).isEqualTo(".FIT")
        assertThat(RunFixtureEncoder.publishedCopy().isFile).isTrue()
    }
}

/** Builds and caches `run_5k.fit`; shared by [FitEncoderFixtureTest] and [FitFileDecoderTest]. */
internal object RunFixtureEncoder {

    /** 2026-05-10T07:00:00Z, the same start instant the JSON fixtures use. */
    const val START_MILLIS: Long = 1_778_396_400_000L
    const val RECORD_COUNT: Int = 1_501
    const val TOTAL_DISTANCE_M: Double = 5_000.0
    const val TOTAL_SECONDS: Int = 1_500

    private const val SERIAL = 3_912_345_678L
    private const val LAT_START = 52.5200
    private const val LNG_START = 13.4050
    private val SEMICIRCLES_PER_DEGREE = 2147483648.0 / 180.0

    fun fixtureFile(): File = File(FitFixtures.resourceDir(), "run_5k.fit")

    fun publishedCopy(): File = File(FitFixtures.docsAssetDir(), "run_5k.fit")

    @Synchronized
    fun ensure(): File {
        val file = fixtureFile()
        if (!file.isFile || file.length() == 0L) encode(file)
        val published = publishedCopy()
        if (!published.isFile || published.length() != file.length()) {
            file.copyTo(published, overwrite = true)
        }
        return file
    }

    private fun encode(file: File) {
        val encoder = FileEncoder(file, Fit.ProtocolVersion.V2_0)
        encoder.write(fileIdMesg())
        repeat(RECORD_COUNT) { second -> encoder.write(recordMesg(second)) }
        encoder.write(lapMesg(index = 0, startSecond = 0))
        encoder.write(lapMesg(index = 1, startSecond = TOTAL_SECONDS / 2))
        encoder.write(sessionMesg())
        encoder.write(activityMesg())
        encoder.close()
    }

    private fun at(second: Int): DateTime =
        DateTime(FitEpoch.toFitSeconds(START_MILLIS + second * 1000L))

    private fun fileIdMesg() = FileIdMesg().apply {
        type = com.garmin.fit.File.ACTIVITY
        manufacturer = 1 // Garmin
        product = 3121
        serialNumber = SERIAL
        timeCreated = at(-60)
    }

    private fun recordMesg(second: Int) = RecordMesg().apply {
        timestamp = at(second)
        val fraction = second.toDouble() / TOTAL_SECONDS
        heartRate = (130 + (second % 40)).toShort()
        distance = (TOTAL_DISTANCE_M * fraction).toFloat()
        speed = 3.3333f
        cadence = 85.toShort()
        altitude = (34.0 + 6.0 * fraction).toFloat()
        positionLat = ((LAT_START + 0.01 * fraction) * SEMICIRCLES_PER_DEGREE).toInt()
        positionLong = ((LNG_START + 0.02 * fraction) * SEMICIRCLES_PER_DEGREE).toInt()
    }

    private fun lapMesg(index: Int, startSecond: Int) = LapMesg().apply {
        messageIndex = index
        startTime = at(startSecond)
        timestamp = at(startSecond + TOTAL_SECONDS / 2)
        event = Event.LAP
        eventType = EventType.STOP
        totalElapsedTime = (TOTAL_SECONDS / 2).toFloat()
        totalTimerTime = (TOTAL_SECONDS / 2).toFloat()
        totalDistance = (TOTAL_DISTANCE_M / 2).toFloat()
        totalCalories = 175 + index * 5
        avgHeartRate = (148 + index * 10).toShort()
        maxHeartRate = (165 + index * 13).toShort()
        avgSpeed = 3.3333f
    }

    private fun sessionMesg() = SessionMesg().apply {
        messageIndex = 0
        startTime = at(0)
        timestamp = at(TOTAL_SECONDS)
        event = Event.SESSION
        eventType = EventType.STOP
        sport = Sport.RUNNING
        subSport = SubSport.STREET
        sportProfileName = "Morning Run"
        totalElapsedTime = TOTAL_SECONDS.toFloat()
        totalTimerTime = TOTAL_SECONDS.toFloat()
        totalDistance = TOTAL_DISTANCE_M.toFloat()
        totalCalories = 355
        avgHeartRate = 152.toShort()
        maxHeartRate = 178.toShort()
        avgSpeed = 3.3333f
        maxSpeed = 4.1f
        avgCadence = 85.toShort()
        totalAscent = 42
        firstLapIndex = 0
        numLaps = 2
    }

    private fun activityMesg() = ActivityMesg().apply {
        timestamp = at(TOTAL_SECONDS)
        totalTimerTime = TOTAL_SECONDS.toFloat()
        numSessions = 1
        type = Activity.MANUAL
        event = Event.ACTIVITY
        eventType = EventType.STOP
        // Device set to UTC+2 (Europe/Berlin in May).
        localTimestamp = FitEpoch.toFitSeconds(START_MILLIS + TOTAL_SECONDS * 1000L) + 7_200L
    }
}
