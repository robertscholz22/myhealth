package com.myhealth.data.repository

import com.myhealth.data.db.dao.ActivityDao
import com.myhealth.data.db.dao.ImportDao
import com.myhealth.data.mapper.toDomain
import com.myhealth.data.mapper.toEntity
import com.myhealth.domain.model.ImportRecord
import com.myhealth.domain.model.ImportUndoSummary
import com.myhealth.domain.repository.ImportRepository
import com.myhealth.domain.util.Outcome
import com.myhealth.domain.util.runCatchingApp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room-backed [ImportRepository] over `import_record` (PLAN §2.2.6, P7.5).
 *
 * `fileHashSha256` is uniquely indexed, so [getByHash] is the duplicate-import guard the pipeline
 * consults before it parses anything.
 *
 * [undo] is the other side of that guard: it hands every source record the import wrote back to
 * [ActivityIngestor.removeSourceRecord] — the same seam a Health Connect deletion uses, so an
 * activity two sources know survives with the other source's data — and then drops the audit row,
 * which is what makes the checksum forgotten and the file importable again. [onUndone] is the hook
 * the DI graph points at `SyncScheduler.requestLoadRecompute`; `data/` never imports `sync/`.
 */
class RoomImportRepository(
    private val importDao: ImportDao,
    private val activityDao: ActivityDao,
    private val ingestor: ActivityIngestor,
    private val onUndone: suspend (Long) -> Unit = {},
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ImportRepository {

    override fun observeRecent(limit: Int): Flow<List<ImportRecord>> =
        importDao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(id: Long): ImportRecord? =
        withContext(ioDispatcher) { importDao.getById(id)?.toDomain() }

    override suspend fun getByHash(fileHashSha256: String): ImportRecord? =
        withContext(ioDispatcher) { importDao.getByHash(fileHashSha256)?.toDomain() }

    override suspend fun record(record: ImportRecord): Outcome<Long> =
        withContext(ioDispatcher) { runCatchingApp { importDao.upsert(record.toEntity()) } }

    override suspend fun delete(id: Long): Outcome<Unit> =
        withContext(ioDispatcher) { runCatchingApp { importDao.deleteById(id) } }

    override suspend fun undo(importId: Long): Outcome<ImportUndoSummary> = withContext(ioDispatcher) {
        var minAffectedDay: Long? = null
        val outcome = runCatchingApp {
            val records = activityDao.getSourceRecordsOfImport(importId)
            val activityIds = records.mapNotNull { it.activityId }.distinct()
            // The days have to be read before the rows go, or the recompute has nothing to start at.
            minAffectedDay = activityIds.mapNotNull { activityDao.getById(it)?.day }.minOrNull()
            for (record in records) {
                ingestor.removeSourceRecord(record.source, record.externalId)
            }
            val deleted = activityIds.count { activityDao.getById(it) == null }
            importDao.deleteById(importId)
            ImportUndoSummary(
                importId = importId,
                sourceRecordsRemoved = records.size,
                activitiesDeleted = deleted,
                activitiesKept = activityIds.size - deleted,
            )
        }
        if (outcome is Outcome.Ok) minAffectedDay?.let { onUndone(it) }
        outcome
    }
}
