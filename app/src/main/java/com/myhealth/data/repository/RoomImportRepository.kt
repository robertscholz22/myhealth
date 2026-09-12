package com.myhealth.data.repository

import com.myhealth.data.db.dao.ImportDao
import com.myhealth.data.mapper.toDomain
import com.myhealth.data.mapper.toEntity
import com.myhealth.domain.model.ImportRecord
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
 */
class RoomImportRepository(
    private val importDao: ImportDao,
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
}
