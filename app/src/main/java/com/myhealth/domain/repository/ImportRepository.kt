package com.myhealth.domain.repository

import com.myhealth.domain.model.ImportKind
import com.myhealth.domain.model.ImportProgress
import com.myhealth.domain.model.ImportRecord
import com.myhealth.domain.util.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * `import_record` (PLAN §2.2.6, P7.5). The SHA-256 file hash is the duplicate-import guard:
 * the pipeline short-circuits when [getByHash] already returns a record (unless forced).
 */
interface ImportRepository {

    fun observeRecent(limit: Int): Flow<List<ImportRecord>>

    suspend fun getById(id: Long): ImportRecord?

    suspend fun getByHash(fileHashSha256: String): ImportRecord?

    suspend fun record(record: ImportRecord): Outcome<Long>

    suspend fun delete(id: Long): Outcome<Unit>
}

/**
 * The import pipeline as the UI sees it (P7.5/P7.6). The `uri` is a `String` because `domain/`
 * may not reference `android.net.Uri` (rule R6); the implementation parses it back.
 *
 * Implemented by `data/repository/ImportService`, which hashes the file, short-circuits on a
 * known hash unless [force], decodes it, ingests in chunks through the same seam Health Connect
 * uses, writes the `import_record` and requests a load recompute.
 */
interface ActivityImporter {
    fun import(uri: String, kind: ImportKind, force: Boolean = false): Flow<ImportProgress>
}

/** Which [ImportKind] a picked document is, from its file name (P7.6); `null` when unsupported. */
object ImportKinds {

    fun forFileName(fileName: String): ImportKind? = when {
        fileName.endsWith(".fit", ignoreCase = true) -> ImportKind.FIT_FILE
        fileName.endsWith(".csv", ignoreCase = true) -> ImportKind.GARMIN_CSV
        fileName.endsWith(".zip", ignoreCase = true) -> ImportKind.GARMIN_ZIP
        else -> null
    }

    /** Fallback for a document whose name carries no usable extension. */
    fun forMimeType(mimeType: String?): ImportKind? = when (mimeType?.lowercase()) {
        "application/zip", "application/x-zip-compressed" -> ImportKind.GARMIN_ZIP
        "text/csv", "text/comma-separated-values", "application/csv" -> ImportKind.GARMIN_CSV
        else -> null
    }
}
