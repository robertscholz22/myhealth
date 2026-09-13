package com.myhealth.data.repository

import com.google.common.truth.Truth.assertThat
import com.myhealth.domain.model.ActivitySource
import com.myhealth.domain.model.ImportKind
import com.myhealth.domain.model.ImportRecord
import com.myhealth.domain.util.Outcome
import com.myhealth.domain.repository.ActivityIngestItem
import com.myhealth.domain.model.ActivitySourceRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * `import_record` over fake DAOs (PLAN §2.2.6), with the **real** [ActivityIngestor] behind
 * [RoomImportRepository.undo] so the re-merge/delete decision asserted here is the app's.
 */
class RoomImportRepositoryTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val clock = Clock.fixed(Instant.ofEpochMilli(NOW), zone)
    private val activityDao = FakeActivityDao()
    private val importDao = FakeImportDao()
    private val ingestor = ActivityIngestor(activityDao, DirectTransactionRunner, clock)
    private val recomputeDays = mutableListOf<Long>()
    private val repo = RoomImportRepository(
        importDao = importDao,
        activityDao = activityDao,
        ingestor = ingestor,
        onUndone = { day -> recomputeDays += day },
        ioDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun a_record_round_trips_by_id_hash_and_recency() = runTest {
        val id = (repo.record(record("a.csv", "hash-a")) as Outcome.Ok).value
        repo.record(record("b.csv", "hash-b", importedAtMillis = NOW + 1_000))

        assertThat(repo.getById(id)?.fileName).isEqualTo("a.csv")
        assertThat(repo.getByHash("hash-b")?.fileName).isEqualTo("b.csv")
        assertThat(repo.getByHash("missing")).isNull()
        assertThat(repo.observeRecent(10).first().map { it.fileName })
            .containsExactly("b.csv", "a.csv").inOrder()

        repo.delete(id)
        assertThat(repo.getById(id)).isNull()
    }

    /**
     * The undo contract: a canonical activity another source also knows is re-merged and kept, one
     * that only the import produced is deleted, and the audit row goes so the file is importable
     * again. The recompute is requested from the earliest day the undo touched.
     */
    @Test
    fun undo_deletes_csv_only_activities_keeps_shared_ones_and_drops_the_audit_row() = runTest {
        val importId = (repo.record(record("activities.csv", "hash-a")) as Outcome.Ok).value
        ingest(ActivitySource.HEALTH_CONNECT, "hc-1", start = START, day = DAY, importRecordId = null)
        ingest(ActivitySource.CSV_IMPORT, "csv-1", start = START, day = DAY, importRecordId = importId)
        ingest(ActivitySource.CSV_IMPORT, "csv-2", start = START + DAY_MILLIS, day = DAY + 1, importRecordId = importId)
        assertThat(activityDao.rows()).hasSize(2)

        val summary = (repo.undo(importId) as Outcome.Ok).value

        assertThat(summary.importId).isEqualTo(importId)
        assertThat(summary.sourceRecordsRemoved).isEqualTo(2)
        assertThat(summary.activitiesDeleted).isEqualTo(1)
        assertThat(summary.activitiesKept).isEqualTo(1)
        assertThat(activityDao.rows().single().mergedSourcesCsv).isEqualTo("HEALTH_CONNECT")
        assertThat(activityDao.sourceRecords.map { it.externalId }).containsExactly("hc-1")
        assertThat(repo.getByHash("hash-a")).isNull()
        assertThat(recomputeDays).containsExactly(DAY)
    }

    @Test
    fun undoing_an_import_that_wrote_nothing_is_a_no_op_that_still_forgets_the_file() = runTest {
        val importId = (repo.record(record("empty.csv", "hash-empty")) as Outcome.Ok).value

        val summary = (repo.undo(importId) as Outcome.Ok).value

        assertThat(summary.sourceRecordsRemoved).isEqualTo(0)
        assertThat(summary.activitiesDeleted).isEqualTo(0)
        assertThat(summary.activitiesKept).isEqualTo(0)
        assertThat(repo.getByHash("hash-empty")).isNull()
        // Nothing was touched, so no recompute is asked for.
        assertThat(recomputeDays).isEmpty()
    }

    private suspend fun ingest(
        source: ActivitySource,
        externalId: String,
        start: Long,
        day: Long,
        importRecordId: Long?,
    ) {
        ingestor.ingest(
            listOf(
                ActivityIngestItem(
                    record = ActivitySourceRecord(
                        id = 0L,
                        activityId = null,
                        source = source,
                        externalId = externalId,
                        payloadJson = "{}",
                        receivedAtMillis = NOW,
                        importRecordId = importRecordId,
                    ),
                    session = hcSession(
                        startAtMillis = start,
                        durationSec = 1_500,
                        distanceMeters = 5_000.0,
                        day = day,
                    ).copy(primarySource = source, mergedSources = listOf(source)),
                ),
            ),
        )
    }

    private fun record(
        fileName: String,
        hash: String,
        importedAtMillis: Long = NOW,
    ) = ImportRecord(
        id = 0L,
        kind = ImportKind.GARMIN_CSV,
        fileName = fileName,
        fileHashSha256 = hash,
        importedAtMillis = importedAtMillis,
        itemsParsed = 2,
        itemsInserted = 2,
        itemsDuplicate = 0,
        errorsJson = null,
    )

    private companion object {
        const val NOW = 1_800_000_000_000L

        /** 2026-05-10T09:00 Europe/Berlin. */
        const val START = 1_778_396_400_000L
        const val DAY = 20_583L
        const val DAY_MILLIS = 86_400_000L
    }
}
