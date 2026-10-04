package com.myhealth.data.backup

import java.io.InputStream
import java.io.OutputStream

/** The document-provider side of a backup, so the service itself stays free of Android types. */
interface BackupContentSource {
    suspend fun openInput(uri: String): InputStream

    suspend fun openOutput(uri: String): OutputStream
}
