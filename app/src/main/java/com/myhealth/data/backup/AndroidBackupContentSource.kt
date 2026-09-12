package com.myhealth.data.backup

import android.content.Context
import android.net.Uri
import com.myhealth.domain.repository.BackupContentSource
import java.io.InputStream
import java.io.OutputStream

/**
 * Storage Access Framework side of [BackupService] (P8.4): the document the user picked with
 * `CreateDocument`/`OpenDocument`, opened as a plain stream so the service never sees an
 * Android type. Mirrors `AndroidImportContentSource` (P7.5).
 *
 * The write mode is `"wt"` — truncate — so re-exporting over an existing, longer file does not
 * leave the tail of the old JSON behind.
 */
class AndroidBackupContentSource(private val context: Context) : BackupContentSource {

    override suspend fun openInput(uri: String): InputStream =
        checkNotNull(context.contentResolver.openInputStream(Uri.parse(uri))) {
            "Could not open $uri"
        }

    override suspend fun openOutput(uri: String): OutputStream =
        checkNotNull(context.contentResolver.openOutputStream(Uri.parse(uri), "wt")) {
            "Could not write to $uri"
        }
}
