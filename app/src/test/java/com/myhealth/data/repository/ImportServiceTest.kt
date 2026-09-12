package com.myhealth.data.repository

import com.google.common.truth.Truth.assertThat
import com.myhealth.data.fit.GarminCsvParser
import com.myhealth.data.fit.RunFixtureEncoder
import com.myhealth.data.fit.zipOf
import com.myhealth.domain.model.ActivitySource
import com.myhealth.domain.model.ImportKind
import com.myhealth.domain.repository.ActivityRepository
import com.myhealth.domain.repository.ImportKinds
import com.myhealth.domain.model.ImportProgress
import com.myhealth.domain.util.Outcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * PLAN P7.5 with fakes for the file source and `import_record`, but the **real** ingestion stack
 * (`ActivityIngestor` over an in-memory `ActivityDao`), so the de-dup/merge behaviour asserted
 * here is the behaviour the app has.
 */
class ImportServiceTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val clock = Clock.fixed(Instant.ofEpochMilli(NOW), zone)
    private val dao = FakeActivityDao()
    private val ingestor = ActivityIngestor(dao, DirectTransactionRunner, clock)
    private val activityRepo: ActivityRepository =
        RoomActivityRepository(dao, ingestor, clock, Dispatchers.Unconfined)
    private val importRepo = FakeImportRepository()
    private val recomputeDays = mutableListOf<Long>()

    private fun service(
        content: FakeImportContentSource,
        repo: ActivityRepository = activityRepo,
        chunkSize: Int = ImportService.CHUNK_SIZE,
    ) = ImportService(
        content = content,
        activityRepo = repo,
        importRepo = importRepo,
        csvParser = GarminCsvParser(zone),
        clock = clock,
        onImported = { day -> recomputeDays += day },
        ioDispatcher = Dispatchers.Unconfined,
        chunkSize = chunkSize,
    )

    private fun fitContent() = FakeImportContentSource("run_5k.fit", RunFixtureEncoder.ensure().readBytes())

    @Test
    fun a_file_whose_hash_is_already_recorded_short_circuits() = runTest {
        val content = fitContent()
        val first = service(content).import("content://doc", ImportKind.FIT_FILE).toList()
        val finished = first.filterIsInstance<ImportProgress.Finished>().single()
        assertThat(finished.counts.inserted).isEqualTo(1)
        assertThat(dao.rows()).hasSize(1)

        val second = service(content).import("content://doc", ImportKind.FIT_FILE).toList()

        val duplicate = second.filterIsInstance<ImportProgress.AlreadyImported>().single()
        assertThat(duplicate.previous.fileHashSha256).isEqualTo(finished.record.fileHashSha256)
        assertThat(second.filterIsInstance<ImportProgress.Working>()).isEmpty()
        assertThat(dao.rows()).hasSize(1)
        assertThat(importRepo.all()).hasSize(1)

        // `force` bypasses the guard and re-ingests idempotently (same (source, externalId)).
        val forced = service(content).import("content://doc", ImportKind.FIT_FILE, force = true).toList()
        val redone = forced.filterIsInstance<ImportProgress.Finished>().single()
        assertThat(redone.counts.duplicate).isEqualTo(1)
        assertThat(redone.counts.inserted).isEqualTo(0)
        assertThat(dao.rows()).hasSize(1)
    }

    @Test
    fun a_corrupt_entry_fails_alone_and_is_recorded_in_the_import_record() = runTest {
        val archive = zipOf(
            "DI_CONNECT/uploads/good.fit" to RunFixtureEncoder.ensure().readBytes(),
            "DI_CONNECT/uploads/broken.fit" to ByteArray(200) { 0x5 },
            "DI_CONNECT/activities.csv" to loadEnglishCsv().toByteArray(),
            "../escape.fit" to ByteArray(10),
        )
        val content = FakeImportContentSource("export.zip", archive)

        val progress = service(content).import("content://zip", ImportKind.GARMIN_ZIP).toList()

        val finished = progress.filterIsInstance<ImportProgress.Finished>().single()
        // One FIT activity plus the five CSV rows parsed; the broken FIT and the escaping entry
        // are errors, not a failed import.
        assertThat(finished.counts.parsed).isEqualTo(6)
        assertThat(finished.counts.failed).isEqualTo(1)
        assertThat(finished.errors.map { it.item })
            .containsAtLeast("DI_CONNECT/uploads/broken.fit", "../escape.fit")
        assertThat(finished.record.errorsJson).isNotNull()
        assertThat(finished.record.itemsParsed).isEqualTo(6)
        assertThat(finished.record.kind).isEqualTo(ImportKind.GARMIN_ZIP)
        // Five canonical rows, not six: the CSV's first row is the same run as the FIT file and
        // the two are merged by §2.4 on the way in.
        assertThat(dao.rows()).hasSize(5)
        assertThat(dao.rows().first().mergedSourcesCsv).isEqualTo("FIT_IMPORT,CSV_IMPORT")
    }

    @Test
    fun activities_are_ingested_in_chunks_of_fifty() = runTest {
        val content = FakeImportContentSource("activities.csv", syntheticCsv(rows = 120).toByteArray())
        val recording = RecordingActivityRepository(activityRepo)

        val progress = service(content, recording).import("content://csv", ImportKind.GARMIN_CSV).toList()

        assertThat(recording.chunkSizes).containsExactly(50, 50, 20).inOrder()
        val finished = progress.filterIsInstance<ImportProgress.Finished>().single()
        assertThat(finished.counts.parsed).isEqualTo(120)
        assertThat(finished.counts.inserted).isEqualTo(120)
        assertThat(dao.rows()).hasSize(120)
        // Progress is reported while the import runs, not only at the end.
        assertThat(progress.filterIsInstance<ImportProgress.Working>().size).isAtLeast(3)
        // The load recompute is requested once, from the earliest day the import touched.
        assertThat(recomputeDays).containsExactly(LocalDate.of(2026, 1, 1).toEpochDay())
    }

    @Test
    fun a_failing_chunk_is_accounted_for_without_aborting_the_import() = runTest {
        val content = FakeImportContentSource("activities.csv", syntheticCsv(rows = 60).toByteArray())
        val recording = RecordingActivityRepository(activityRepo, failOn = { it.session.day % 2 == 0L })

        val finished = service(content, recording)
            .import("content://csv", ImportKind.GARMIN_CSV)
            .toList()
            .filterIsInstance<ImportProgress.Finished>()
            .single()

        assertThat(finished.counts.parsed).isEqualTo(60)
        assertThat(finished.counts.failed).isGreaterThan(0)
        assertThat(finished.counts.inserted + finished.counts.failed).isEqualTo(60)
        assertThat(finished.record.errorsJson).contains("db")
    }

    @Test
    fun a_fit_import_merges_into_an_existing_health_connect_activity() = runTest {
        val day = LocalDate.of(2026, 5, 10).toEpochDay()
        val existing = hcSession(
            startAtMillis = RunFixtureEncoder.START_MILLIS,
            durationSec = RunFixtureEncoder.TOTAL_SECONDS,
            distanceMeters = 4_980.0,
            day = day,
        )
        val seeded = activityRepo.upsert(existing)
        assertThat(seeded).isInstanceOf(Outcome.Ok::class.java)
        assertThat(dao.rows()).hasSize(1)

        service(fitContent()).import("content://doc", ImportKind.FIT_FILE).toList()

        val row = dao.rows().single()
        assertThat(row.mergedSourcesCsv).isEqualTo("HEALTH_CONNECT,FIT_IMPORT")
        assertThat(row.primarySource).isEqualTo(ActivitySource.FIT_IMPORT)
        // Streams and laps come from the FIT file (§2.4: FIT_IMPORT > HEALTH_CONNECT).
        assertThat(row.hasStreams).isTrue()
        assertThat(dao.streams[row.id]).isNotNull()
        assertThat(dao.laps.filter { it.activityId == row.id }).hasSize(2)
        // Distance is FIT's, calories stay Health Connect's (it carries the device-calibrated kcal).
        assertThat(checkNotNull(row.distanceMeters)).isWithin(1.0).of(5_000.0)
        assertThat(row.totalEnergyKcal).isEqualTo(400.0)
        assertThat(row.activeEnergyKcal).isEqualTo(355.0)
        assertThat(recomputeDays).containsExactly(day)
    }

    @Test
    fun an_unreadable_document_fails_the_whole_import() = runTest {
        val content = object : ImportContentSource {
            override suspend fun displayName(uri: String) = "gone.fit"
            override suspend fun openStream(uri: String) = throw java.io.IOException("no such file")
        }
        val service = ImportService(
            content = content,
            activityRepo = activityRepo,
            importRepo = importRepo,
            csvParser = GarminCsvParser(zone),
            clock = clock,
            ioDispatcher = Dispatchers.Unconfined,
        )

        val progress = service.import("content://gone", ImportKind.FIT_FILE).toList()

        assertThat(progress.filterIsInstance<ImportProgress.Failed>()).hasSize(1)
        assertThat(importRepo.all()).isEmpty()
    }

    @Test
    fun the_file_name_decides_the_import_kind() {
        assertThat(ImportKinds.forFileName("2026-05-10.fit")).isEqualTo(ImportKind.FIT_FILE)
        assertThat(ImportKinds.forFileName("Activities.CSV")).isEqualTo(ImportKind.GARMIN_CSV)
        assertThat(ImportKinds.forFileName("export.zip")).isEqualTo(ImportKind.GARMIN_ZIP)
        assertThat(ImportKinds.forFileName("notes.txt")).isNull()
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}

/** 120 synthetic rows, one per day from 2026-01-01, to exercise the chunk boundary. */
internal fun syntheticCsv(rows: Int): String = buildString {
    appendLine("Activity Type,Date,Title,Distance,Calories,Time,Avg HR")
    repeat(rows) { index ->
        val date = LocalDate.of(2026, 1, 1).plusDays(index.toLong())
        appendLine("Running,$date 09:00:00,Run $index,5.00,350,00:25:00,150")
    }
}

/** The English CSV fixture, reused as a zip entry. */
internal fun loadEnglishCsv(): String =
    checkNotNull(ImportServiceTest::class.java.classLoader)
        .getResource("fixtures/csv/garmin_en.csv")!!
        .readText()
