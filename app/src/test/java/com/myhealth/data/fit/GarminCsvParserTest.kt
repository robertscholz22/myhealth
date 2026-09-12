package com.myhealth.data.fit

import com.google.common.truth.Truth.assertThat
import com.myhealth.domain.model.ActivitySource
import com.myhealth.domain.model.SportType
import org.junit.Test
import java.security.MessageDigest
import java.time.ZoneId

/**
 * The named cases of PLAN P7.3 over the three fixtures in
 * `app/src/test/resources/fixtures/csv/`: a standard English export, a German-locale export and a
 * sparse export that is missing most known columns.
 */
class GarminCsvParserTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val parser = GarminCsvParser(zone)

    private fun parse(name: String) = parser.parse(loadCsvFixture(name))

    @Test
    fun csv01_standard_english_export() {
        val result = parse("garmin_en")

        assertThat(result.errors).isEmpty()
        assertThat(result.rows).hasSize(5)

        val run = result.rows.first()
        assertThat(run.sportType).isEqualTo(SportType.RUN_OUTDOOR)
        assertThat(run.startAtMillis).isEqualTo(RUN_START)
        assertThat(run.distanceMeters).isEqualTo(5_000.0)
        assertThat(run.calories).isEqualTo(355.0)
        assertThat(run.durationSec).isEqualTo(1_500)
        assertThat(run.avgHr).isEqualTo(152)
        assertThat(run.maxHr).isEqualTo(178)
        assertThat(run.elevationGainM).isEqualTo(42.0)
        assertThat(run.aerobicTrainingEffect).isEqualTo(3.4)
        // "Avg Pace" 5:00 min/km and "Best Pace" 4:04 min/km become metres per second.
        assertThat(checkNotNull(run.avgSpeedMps)).isWithin(1e-4).of(3.3333)
        assertThat(checkNotNull(run.maxSpeedMps)).isWithin(1e-4).of(1000.0 / 244.0)

        val ride = result.rows[1]
        assertThat(ride.sportType).isEqualTo(SportType.CYCLING)
        assertThat(ride.distanceMeters).isEqualTo(30_250.0)
        assertThat(ride.durationSec).isEqualTo(3_930)
        assertThat(result.rows[2].sportType).isEqualTo(SportType.SOCCER_TRAINING)
        assertThat(result.rows[3].sportType).isEqualTo(SportType.STRENGTH)
    }

    @Test
    fun csv02_german_locale_decimals() {
        val result = parse("garmin_de")

        assertThat(result.errors).isEmpty()
        assertThat(result.rows).hasSize(3)

        val run = result.rows.first()
        assertThat(run.sportType).isEqualTo(SportType.RUN_OUTDOOR)
        assertThat(run.startAtMillis).isEqualTo(RUN_START)
        // "10,52" km with a decimal comma, "1.234" kcal with a thousands dot.
        assertThat(run.distanceMeters).isEqualTo(10_520.0)
        assertThat(run.calories).isEqualTo(1_234.0)
        assertThat(run.durationSec).isEqualTo(3_150)
        assertThat(run.avgHr).isEqualTo(151)
        assertThat(run.maxHr).isEqualTo(177)
        assertThat(run.elevationGainM).isEqualTo(86.0)
        assertThat(result.rows[1].distanceMeters).isEqualTo(30_250.0)
        assertThat(result.rows[2].sportType).isEqualTo(SportType.STRENGTH)
    }

    @Test
    fun csv03_quoted_title_with_comma() {
        assertThat(parse("garmin_en").rows.first().title).isEqualTo("Morning run, easy")
        assertThat(parse("garmin_de").rows[1].title).isEqualTo("Feierabendrunde, flach")
        // A doubled quote inside a quoted field is one literal quote.
        assertThat(parse("garmin_sparse").rows[1].title).isEqualTo("Evening \"flow\" session")
    }

    @Test
    fun csv04_missing_columns_tolerated() {
        val result = parse("garmin_sparse")

        assertThat(result.errors).isEmpty()
        assertThat(result.rows).hasSize(2)

        val walk = result.rows.first()
        assertThat(walk.sportType).isEqualTo(SportType.WALK)
        assertThat(walk.title).isEqualTo("Lunch walk")
        assertThat(walk.calories).isEqualTo(150.0)
        assertThat(walk.distanceMeters).isNull()
        assertThat(walk.durationSec).isNull()
        assertThat(walk.avgHr).isNull()
        assertThat(walk.maxHr).isNull()
        assertThat(walk.elevationGainM).isNull()
        assertThat(result.rows[1].sportType).isEqualTo(SportType.MOBILITY)
    }

    @Test
    fun csv05_dash_means_null() {
        val rows = parse("garmin_en").rows

        val strength = rows[3]
        assertThat(strength.distanceMeters).isNull()
        assertThat(strength.avgSpeedMps).isNull()
        assertThat(strength.maxSpeedMps).isNull()
        assertThat(strength.elevationGainM).isNull()
        assertThat(strength.calories).isEqualTo(310.0)

        val pickleball = rows[4]
        assertThat(pickleball.avgHr).isNull()
        assertThat(pickleball.maxHr).isNull()
        assertThat(pickleball.aerobicTrainingEffect).isNull()
    }

    @Test
    fun csv06_duration_formats() {
        assertThat(parseDuration("00:25:00")).isEqualTo(1_500)
        assertThat(parseDuration("25:00")).isEqualTo(1_500)
        assertThat(parseDuration("1:05:30")).isEqualTo(3_930)
        assertThat(parseDuration("45:00")).isEqualTo(2_700)
        assertThat(parseDuration("00:00:07")).isEqualTo(7)
        assertThat(parseDuration("90")).isEqualTo(90)
        assertThat(parseDuration("1:02:03:04")).isNull()
        assertThat(parseDuration("not a time")).isNull()
        // The parsed rows agree with the standalone helper.
        assertThat(parse("garmin_en").rows.map { it.durationSec })
            .containsExactly(1_500, 3_930, 5_400, 2_700, 1_800).inOrder()
    }

    @Test
    fun csv07_unknown_activity_type_is_other() {
        val pickleball = parse("garmin_en").rows[4]

        assertThat(pickleball.activityTypeRaw).isEqualTo("Pickleball")
        assertThat(pickleball.sportType).isEqualTo(SportType.OTHER)
        assertThat(GarminActivityTypeMap.toSportType(null)).isEqualTo(SportType.OTHER)
        assertThat(GarminActivityTypeMap.toSportType("Backcountry Skiing")).isEqualTo(SportType.OTHER)
        // The long tail still resolves through the contains-fallbacks.
        assertThat(GarminActivityTypeMap.toSportType("Virtual Running")).isEqualTo(SportType.RUN_OUTDOOR)
        assertThat(GarminActivityTypeMap.toSportType("eBike Ride")).isEqualTo(SportType.CYCLING)
    }

    @Test
    fun csv08_row_hash_is_stable() {
        val first = parse("garmin_en").rows.map { it.externalId }
        val second = parse("garmin_en").rows.map { it.externalId }

        assertThat(first).isEqualTo(second)
        assertThat(first.toSet()).hasSize(5)
        first.forEach { assertThat(it).hasLength(64) }

        val run = parse("garmin_en").rows.first()
        assertThat(run.externalId).isEqualTo(sha256Hex(run.rawRow))

        // Any change to the row text is a different activity_source_record.
        val edited = parser.parse(
            loadCsvFixture("garmin_en").replace("Morning run, easy", "Morning run, hard"),
        )
        assertThat(edited.rows.first().externalId).isNotEqualTo(run.externalId)
    }

    @Test
    fun a_row_becomes_a_csv_import_ingest_item() {
        val row = parse("garmin_en").rows.first()

        val item = parser.toIngestItem(row, nowMillis = 1_800_000_000_000L)

        assertThat(item.record.source).isEqualTo(ActivitySource.CSV_IMPORT)
        assertThat(item.record.externalId).isEqualTo(row.externalId)
        assertThat(item.record.payloadJson).contains("\"avgHr\":152")
        assertThat(item.session.sportType).isEqualTo(SportType.RUN_OUTDOOR)
        assertThat(item.session.durationSec).isEqualTo(1_500)
        assertThat(item.session.endAtMillis).isEqualTo(RUN_START + 1_500_000L)
        assertThat(item.session.dedupeBucket).isEqualTo("RUN|${RUN_START / 300_000L}")
        assertThat(item.session.hasStreams).isFalse()
    }

    @Test
    fun a_file_without_a_date_column_reports_one_error_and_no_rows() {
        val result = parser.parse("Activity Type,Title\nRunning,Nope\n")

        assertThat(result.rows).isEmpty()
        assertThat(result.errors).hasSize(1)
        assertThat(result.errors.single()).contains("No date column")
    }

    @Test
    fun an_unparseable_date_fails_only_its_own_row() {
        val text = loadCsvFixture("garmin_en").replace("2026-05-11 17:30:00", "not-a-date")

        val result = parser.parse(text)

        assertThat(result.rows).hasSize(4)
        assertThat(result.errors).hasSize(1)
        assertThat(result.errors.single()).startsWith("Row 3:")
    }

    private companion object {
        /** 2026-05-10T09:00 Europe/Berlin = 2026-05-10T07:00Z. */
        const val RUN_START = 1_778_396_400_000L
    }
}

/** Loads `app/src/test/resources/fixtures/csv/<name>.csv` off the classpath (rule R12). */
internal fun loadCsvFixture(name: String): String {
    val path = "fixtures/csv/$name.csv"
    val url = checkNotNull(GarminCsvParser::class.java.classLoader).getResource(path)
        ?: error("Missing CSV fixture on the classpath: $path")
    return url.readText()
}

private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
