package com.myhealth.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.myhealth.data.fit.ArchiveEntryKind
import com.myhealth.data.fit.FitFileDecoder
import com.myhealth.data.fit.FitToDomainMapper
import com.myhealth.data.fit.GarminArchiveWalker
import com.myhealth.data.fit.GarminCsvParser
import com.myhealth.domain.model.ImportKind
import com.myhealth.domain.model.ImportRecord
import com.myhealth.domain.repository.ActivityImporter
import com.myhealth.domain.repository.ActivityIngestItem
import com.myhealth.domain.repository.ActivityRepository
import com.myhealth.domain.model.ImportCounts
import com.myhealth.domain.model.ImportItemError
import com.myhealth.domain.model.ImportProgress
import com.myhealth.domain.repository.ImportRepository
import com.myhealth.domain.util.AppError
import com.myhealth.domain.util.Outcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Clock

/** Where an import reads its bytes from — the seam that keeps [ImportService] testable. */
interface ImportContentSource {
    suspend fun displayName(uri: String): String
    suspend fun openStream(uri: String): InputStream
}

/** `content://` documents handed over by `OpenDocument` or the share sheet (P7.6). */
class AndroidImportContentSource(private val context: Context) : ImportContentSource {

    override suspend fun displayName(uri: String): String {
        val parsed = Uri.parse(uri)
        context.contentResolver
            .query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getString(0)
            }
        return parsed.lastPathSegment?.substringAfterLast('/') ?: uri
    }

    override suspend fun openStream(uri: String): InputStream =
        checkNotNull(context.contentResolver.openInputStream(Uri.parse(uri))) {
            "Could not open $uri"
        }
}

@Serializable
private data class ItemErrorDto(val item: String, val message: String)

/**
 * The FIT/CSV/ZIP import pipeline (PLAN P7.5).
 *
 * Per run: SHA-256 the file and short-circuit when `import_record` already holds that hash
 * (unless `force`); open the `import_record` row up front so every `activity_source_record` this
 * run writes can carry its `importRecordId` (that back-link is what "Undo import" selects on, and
 * the id has to exist before the first ingest); stream-decode the content; convert each activity with the same mappers the
 * unit tests use; ingest in chunks of [chunkSize] through [ActivityRepository.ingest] — the exact
 * seam Health Connect uses, so de-dup and the §2.4 merge come for free; write one `import_record`
 * with the counts and the per-item errors; finally request a load recompute from the earliest day
 * the import touched.
 *
 * Memory: never more than [chunkSize] decoded activities (and their streams) are held at once —
 * the archive walker hands over one entry at a time and the buffer is flushed as soon as it fills.
 */
class ImportService(
    private val content: ImportContentSource,
    private val activityRepo: ActivityRepository,
    private val importRepo: ImportRepository,
    private val csvParser: GarminCsvParser,
    private val clock: Clock,
    private val fitDecoder: FitFileDecoder = FitFileDecoder(),
    private val fitMapper: FitToDomainMapper = FitToDomainMapper(),
    private val walker: GarminArchiveWalker = GarminArchiveWalker(),
    private val onImported: suspend (Long) -> Unit = {},
    private val json: Json = Json { encodeDefaults = true },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val chunkSize: Int = CHUNK_SIZE,
) : ActivityImporter {

    override fun import(uri: String, kind: ImportKind, force: Boolean): Flow<ImportProgress> = flow {
        val fileName = runCatching { content.displayName(uri) }.getOrDefault(uri.substringAfterLast('/'))
        emit(ImportProgress.Started(fileName, kind))

        val hash = try {
            hashOf(uri)
        } catch (e: Exception) {
            emit(ImportProgress.Failed(AppError.Storage(e)))
            return@flow
        }
        if (!force) {
            importRepo.getByHash(hash)?.let {
                emit(ImportProgress.AlreadyImported(it))
                return@flow
            }
        }

        // A forced re-import reuses its own audit row; the file hash is uniquely indexed.
        val reusedId = importRepo.getByHash(hash)?.id ?: 0L
        val importId = openRecord(kind, fileName, hash, reusedId)

        val run = Run(this, kind, importId)
        try {
            content.openStream(uri).use { stream -> run.consume(stream, fileName) }
            run.flush()
        } catch (e: Exception) {
            // Nothing usable was written, so the checksum must not be remembered.
            if (reusedId == 0L && importId != 0L) importRepo.delete(importId)
            emit(ImportProgress.Failed(AppError.Unexpected(e)))
            return@flow
        }
        emit(run.finish(fileName, hash))
    }.flowOn(ioDispatcher)

    /**
     * Writes (or reuses) the `import_record` before anything is ingested and returns its id, so the
     * source records of this run can point back at it. `0` means the row could not be written; the
     * import still runs, its arrivals simply are not undoable.
     */
    private suspend fun openRecord(kind: ImportKind, fileName: String, hash: String, reusedId: Long): Long {
        val draft = ImportRecord(
            id = reusedId,
            kind = kind,
            fileName = fileName,
            fileHashSha256 = hash,
            importedAtMillis = clock.millis(),
            itemsParsed = 0,
            itemsInserted = 0,
            itemsDuplicate = 0,
            errorsJson = null,
        )
        return when (val outcome = importRepo.record(draft)) {
            is Outcome.Ok -> if (reusedId != 0L) reusedId else outcome.value
            is Outcome.Err -> reusedId
        }
    }

    private suspend fun hashOf(uri: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        content.openStream(uri).use { stream ->
            DigestInputStream(stream, digest).use { hashing ->
                val buffer = ByteArray(HASH_BUFFER_BYTES)
                while (hashing.read(buffer) > 0) {
                    // The digest is updated by the stream itself.
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** One import in flight: the ingest buffer, the running counts and the per-item errors. */
    private inner class Run(
        private val collector: FlowCollector<ImportProgress>,
        private val kind: ImportKind,
        private val importId: Long,
    ) {
        private val buffer = mutableListOf<ActivityIngestItem>()
        private val errors = mutableListOf<ImportItemError>()
        private var counts = ImportCounts()
        private var minDay: Long? = null

        suspend fun consume(stream: InputStream, fileName: String) = when (kind) {
            ImportKind.FIT_FILE -> addFit(stream, fileName)
            ImportKind.GARMIN_CSV -> addCsv(stream.readBytes(), fileName)
            ImportKind.GARMIN_ZIP -> walkArchive(stream)
            ImportKind.JSON_BACKUP -> errors += ImportItemError(
                fileName,
                "JSON backups are restored from the Backup screen, not the importer.",
            )
        }

        private suspend fun walkArchive(stream: InputStream) {
            val result = walker.walk(stream) { entry ->
                when (entry.kind) {
                    ArchiveEntryKind.FIT -> addFit(ByteArrayInputStream(entry.bytes), entry.path)
                    ArchiveEntryKind.CSV -> addCsv(entry.bytes, entry.path)
                }
            }
            result.rejectedPaths.forEach {
                errors += ImportItemError(it, "Rejected: the entry path escapes the archive root.")
            }
            result.depthSkippedPaths.forEach {
                errors += ImportItemError(it, "Skipped: nested deeper than ${GarminArchiveWalker.DEFAULT_MAX_DEPTH} archives.")
            }
            if (result.truncatedBySize) {
                errors += ImportItemError("<archive>", "Stopped: the archive exceeds the uncompressed size limit.")
            }
        }

        private suspend fun addFit(stream: InputStream, itemName: String) {
            when (val decoded = fitDecoder.decode(stream)) {
                is Outcome.Err -> fail(itemName, decoded.error)
                is Outcome.Ok -> {
                    val items = fitMapper.toIngestItems(decoded.value, clock.zone, clock.millis())
                    if (items.isEmpty()) {
                        fail(itemName, AppError.Parse("fit", "no session message in the file"))
                    } else {
                        offer(items, itemName)
                    }
                }
            }
        }

        private suspend fun addCsv(bytes: ByteArray, itemName: String) {
            val result = csvParser.parse(bytes.toString(Charsets.UTF_8))
            result.errors.forEach { errors += ImportItemError(itemName, it) }
            counts = counts.copy(failed = counts.failed + result.errors.size)
            offer(result.rows.map { csvParser.toIngestItem(it, clock.millis()) }, itemName)
        }

        /** Buffers candidates one at a time and ingests as soon as a full chunk is available. */
        private suspend fun offer(items: List<ActivityIngestItem>, itemName: String) {
            for (item in items) {
                buffer += item.stamped()
                counts = counts.copy(parsed = counts.parsed + 1)
                minDay = minOf(minDay ?: item.session.day, item.session.day)
                if (buffer.size >= chunkSize) ingestBuffer(itemName)
            }
            collector.emit(ImportProgress.Working(counts, itemName, errors.toList()))
        }

        /** Every arrival of this run carries the id of the `import_record` that produced it. */
        private fun ActivityIngestItem.stamped(): ActivityIngestItem =
            if (importId == 0L) this else copy(record = record.copy(importRecordId = importId))

        suspend fun flush() {
            if (buffer.isNotEmpty()) ingestBuffer(null)
        }

        private suspend fun ingestBuffer(itemName: String?) {
            val chunk = buffer.toList()
            buffer.clear()
            when (val outcome = activityRepo.ingest(chunk)) {
                is Outcome.Ok -> counts = counts.copy(
                    inserted = counts.inserted + outcome.value.inserted,
                    merged = counts.merged + outcome.value.merged,
                    duplicate = counts.duplicate + outcome.value.duplicate,
                )
                is Outcome.Err -> {
                    counts = counts.copy(failed = counts.failed + chunk.size)
                    errors += ImportItemError(itemName ?: "<chunk>", describe(outcome.error))
                }
            }
            collector.emit(ImportProgress.Working(counts, itemName, errors.toList()))
        }

        private fun fail(itemName: String, error: AppError) {
            counts = counts.copy(failed = counts.failed + 1)
            errors += ImportItemError(itemName, describe(error))
        }

        /** Writes the audit row, then asks for the load recompute the new activities invalidate. */
        suspend fun finish(fileName: String, hash: String): ImportProgress {
            val record = ImportRecord(
                id = importId,
                kind = kind,
                fileName = fileName,
                fileHashSha256 = hash,
                importedAtMillis = clock.millis(),
                itemsParsed = counts.parsed,
                itemsInserted = counts.inserted + counts.merged,
                itemsDuplicate = counts.duplicate,
                errorsJson = errors.takeIf { it.isNotEmpty() }
                    ?.let { list -> json.encodeToString(list.map { ItemErrorDto(it.item, it.message) }) },
            )
            val stored = when (val outcome = importRepo.record(record)) {
                is Outcome.Ok -> record.copy(id = if (importId != 0L) importId else outcome.value)
                is Outcome.Err -> record
            }
            minDay?.let { onImported(it) }
            return ImportProgress.Finished(stored, counts, errors.toList())
        }
    }

    private fun describe(error: AppError): String = when (error) {
        is AppError.Parse -> "${error.what}: ${error.detail}"
        is AppError.Storage -> error.cause.message ?: "storage error"
        is AppError.Unexpected -> error.cause.message ?: error.cause.javaClass.simpleName
        else -> error.toString()
    }

    companion object {
        /** PLAN P7.5: chunks of 50, which is also the in-flight memory guard. */
        const val CHUNK_SIZE: Int = 50
        private const val HASH_BUFFER_BYTES = 64 * 1024
    }
}
