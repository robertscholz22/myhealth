package com.myhealth.data.fit

import com.google.common.truth.Truth.assertThat
import com.myhealth.domain.model.SportType
import com.myhealth.domain.util.AppError
import com.myhealth.domain.util.Outcome
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.ZoneId

/**
 * Exercises the real Garmin SDK decoder against the binary fixture built by [RunFixtureEncoder]
 * (PLAN P7.1/P7.2). The SDK is plain Java, so `Decode` runs on the JVM without an emulator.
 */
class FitFileDecoderTest {

    private val decoder = FitFileDecoder()

    private fun decodeFixture(): FitFileData {
        val bytes = RunFixtureEncoder.ensure().readBytes()
        return when (val outcome = decoder.decode(ByteArrayInputStream(bytes))) {
            is Outcome.Ok -> outcome.value
            is Outcome.Err -> error("Decoding run_5k.fit failed: ${outcome.error}")
        }
    }

    @Test
    fun decodes_the_5k_run_fixture_into_sessions_laps_and_records() {
        val data = decodeFixture()

        val session = data.sessions.single()
        assertThat(session.sport).isEqualTo("RUNNING")
        assertThat(session.subSport).isEqualTo("STREET")
        assertThat(checkNotNull(session.totalDistanceMeters)).isWithin(1.0).of(5_000.0)
        assertThat(checkNotNull(session.totalTimerSec)).isWithin(0.5).of(1_500.0)
        assertThat(session.startAtMillis).isEqualTo(RunFixtureEncoder.START_MILLIS)
        assertThat(session.avgHr).isEqualTo(152)

        assertThat(data.records.size).isAtLeast(1_490)
        assertThat(data.records.size).isAtMost(1_510)
        assertThat(data.laps).hasSize(2)
        assertThat(data.localTimestampOffsetSec).isEqualTo(7_200L)

        val fileId = checkNotNull(data.fileId)
        assertThat(fileId.type).isEqualTo("ACTIVITY")
        assertThat(fileId.serialNumber).isEqualTo(3_912_345_678L)
    }

    @Test
    fun record_channels_survive_the_round_trip() {
        val data = decodeFixture()

        val first = data.records.first()
        assertThat(first.timestampMillis).isEqualTo(RunFixtureEncoder.START_MILLIS)
        assertThat(first.hr).isEqualTo(130)
        assertThat(checkNotNull(first.speedMps)).isWithin(0.01).of(3.3333)
        assertThat(checkNotNull(first.positionLatSemicircles)).isNotEqualTo(0)

        val last = data.records.last()
        assertThat(checkNotNull(last.distanceMeters)).isWithin(1.0).of(5_000.0)
    }

    @Test
    fun the_decoded_fixture_maps_to_one_5k_running_activity() {
        val items = FitToDomainMapper().toIngestItems(
            decodeFixture(),
            ZoneId.of("Europe/Berlin"),
            nowMillis = 1_800_000_000_000L,
        )

        val session = items.single().session
        assertThat(session.sportType).isEqualTo(SportType.RUN_OUTDOOR)
        assertThat(checkNotNull(session.distanceMeters)).isWithin(1.0).of(5_000.0)
        assertThat(session.durationSec).isEqualTo(1_500)
        assertThat(session.laps).hasSize(2)
        val streams = checkNotNull(session.streams)
        assertThat(streams.sampleCount).isAtLeast(1_490)
        assertThat(streams.hr.none { it == null }).isTrue()
        assertThat(checkNotNull(streams.latLngE7)).hasSize(streams.sampleCount)
    }

    @Test
    fun a_stream_that_is_not_a_fit_file_becomes_a_parse_error() {
        val outcome = decoder.decode(ByteArrayInputStream(ByteArray(64) { 0x7 }))

        assertThat(outcome).isInstanceOf(Outcome.Err::class.java)
        assertThat((outcome as Outcome.Err).error).isInstanceOf(AppError.Parse::class.java)
    }
}
