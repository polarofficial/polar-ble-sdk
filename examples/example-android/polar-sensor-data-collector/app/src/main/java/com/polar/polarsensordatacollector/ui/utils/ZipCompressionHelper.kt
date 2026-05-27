package com.polar.polarsensordatacollector.ui.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipCompressionHelper {
    private const val TAG = "ZipCompressionHelper"
    private const val GMAIL_SIZE_LIMIT_BYTES = 25 * 1024 * 1024 // 25MB in bytes
    private const val BUFFER_SIZE = 8192

    data class CompressionResult(
        val uri: Uri?,
        /** True when the file (compressed or not) still exceeds the email size limit */
        val isOverEmailSizeLimit: Boolean
    )

    /**
     * Calculates the total size of all files pointed to by the URIs.
     *
     * @param context Android context for content resolver
     * @param uris List of file URIs to calculate size for
     * @return Total size in bytes
     */
    fun calculateTotalSize(context: Context, uris: List<Uri>): Long {
        var totalSize = 0L
        Log.d(TAG, "Starting size calculation for ${uris.size} files")
        for ((index, uri) in uris.withIndex()) {
            val size = getUriSize(context, uri)
            val name = getUriFileName(context, uri) ?: uri.lastPathSegment ?: "unknown"
            Log.d(TAG, "File ${index + 1}: $name = $size bytes")
            totalSize += size
        }
        Log.d(TAG, "Total size: $totalSize bytes (limit: $GMAIL_SIZE_LIMIT_BYTES bytes)")
        return totalSize
    }

    /**
     * Compresses files to a ZIP archive if their total size exceeds the Gmail limit.
     * Returns a [CompressionResult] with the URI (zip or null if not compressed) and
     * a flag indicating whether the result still exceeds the email size limit.
     *
     * @param context Android context for file operations
     * @param uris List of file URIs to potentially compress
     * @param prefix Filename prefix for the resulting ZIP
     * @return [CompressionResult]
     */
    fun compressFilesIfNeeded(context: Context, uris: List<Uri>, prefix: String = "recordings"): CompressionResult {
        val totalSize = calculateTotalSize(context, uris)

        if (totalSize <= GMAIL_SIZE_LIMIT_BYTES) {
            Log.d(TAG, "Total size ($totalSize bytes) is within limit, no compression needed")
            return CompressionResult(uri = null, isOverEmailSizeLimit = false)
        }

        Log.d(TAG, "Total size ($totalSize bytes) exceeds limit, compressing...")
        return try {
            val zipUri = compressFiles(context, uris, prefix)
            val zipSize = getUriSize(context, zipUri)
            val stillOverLimit = zipSize > GMAIL_SIZE_LIMIT_BYTES
            if (stillOverLimit) {
                Log.w(TAG, "ZIP size ($zipSize bytes) still exceeds email limit after compression")
            }
            CompressionResult(uri = zipUri, isOverEmailSizeLimit = stillOverLimit)
        } catch (e: Exception) {
            Log.e(TAG, "Compression failed: ${e.message}", e)
            CompressionResult(uri = null, isOverEmailSizeLimit = true)
        }
    }

    /**
     * Compresses files to a ZIP archive.
     *
     * @param context Android context for file operations
     * @param uris List of file URIs to compress
     * @return URI of the created ZIP file
     */
    private fun compressFiles(context: Context, uris: List<Uri>, prefix: String = "recordings"): Uri {
        val zipFile = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.zip")
        val startTime = System.currentTimeMillis()
        var filesAdded = 0
        var filesSkipped = 0

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            for (uri in uris) {
                try {
                    val entryName = getUriFileName(context, uri) ?: uri.lastPathSegment ?: "unknown"
                    val fileSize = getUriSize(context, uri)

                    if (fileSize == 0L) {
                        Log.w(TAG, "Skipping empty file: $entryName")
                        filesSkipped++
                        continue
                    }

                    Log.d(TAG, "Adding to ZIP: $entryName ($fileSize bytes)")

                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val zipEntry = ZipEntry(entryName)
                        zos.putNextEntry(zipEntry)
                        val buffer = ByteArray(BUFFER_SIZE)
                        var length: Int
                        var bytesWritten = 0L
                        while (inputStream.read(buffer).also { length = it } > 0) {
                            zos.write(buffer, 0, length)
                            bytesWritten += length
                        }
                        zos.closeEntry()
                        Log.d(TAG, "Written $entryName: $bytesWritten bytes")
                        filesAdded++
                    } ?: run {
                        Log.w(TAG, "Could not open input stream for: $entryName")
                        filesSkipped++
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to add file to ZIP: ${e.message}", e)
                    filesSkipped++
                }
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "ZIP created: ${zipFile.name} (${zipFile.length()} bytes) in ${elapsed}ms - added: $filesAdded, skipped: $filesSkipped")

        return FileProvider.getUriForFile(
            context,
            "com.polar.polarsensordatacollector.fileprovider",
            zipFile
        )
    }

    private fun getUriSize(context: Context, uri: Uri): Long {
        return when (uri.scheme) {
            "file" -> File(uri.path.orEmpty()).length()
            "content" -> DocumentFile.fromSingleUri(context, uri)?.length() ?: 0L
            else -> 0L
        }
    }

    private fun getUriFileName(context: Context, uri: Uri): String? {
        return when (uri.scheme) {
            "file" -> File(uri.path.orEmpty()).name
            "content" -> DocumentFile.fromSingleUri(context, uri)?.name
            else -> null
        }
    }

    /**
     * Cleans up old ZIP files from the cache directory.
     * Deletes ZIP files older than the specified age in milliseconds.
     *
     * @param context Android context
     * @param maxAgeMs Maximum age of files to keep in milliseconds (default: 1 hour)
     */
    fun cleanupOldZipFiles(context: Context, maxAgeMs: Long = 60 * 60 * 1000) {
        try {
            val now = System.currentTimeMillis()
            context.cacheDir.listFiles()
                ?.filter { it.name.endsWith(".zip") }
                ?.forEach { file ->
                    if (now - file.lastModified() > maxAgeMs) {
                        val deleted = file.delete()
                        Log.d(TAG, "Cleanup: ${if (deleted) "deleted" else "failed to delete"} ${file.name}")
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Error during cleanup: ${e.message}", e)
        }
    }
}
