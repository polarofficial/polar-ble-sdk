package com.polar.polarsensordatacollector.ui.landing

import android.net.Uri
import android.util.Log
import com.polar.polarsensordatacollector.DataCollector
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class StreamFileMetadata(
    val streamType: DataCollector.StreamType,
    val timestamp: LocalDateTime
)

object StreamFileUris {
    private const val TAG = "StreamFileUris"

    /** Parses stream type and session timestamp from a file URI produced by [DataCollector].
     *  Works with the per-device directory layout: sensorDataLogs/<streamType>/<deviceId>/file.
     *  Scans all path segments for a match with a known [DataCollector.StreamType] name so the
     *  parser is resilient to nesting depth changes. */
    fun parseMetadata(fileUri: Uri): StreamFileMetadata? {
        val decodedSegments = fileUri.pathSegments
            .map { Uri.decode(it) }
            .filter { it.isNotBlank() }
        if (decodedSegments.isEmpty()) {
            Log.w(TAG, "Unable to parse file uri metadata from empty path: $fileUri")
            return null
        }

        // The last segment may be the plain filename, or an encoded relative path like
        // "sensorDataLogs/HR/deviceId/filename.txt".  Split on "/" to get all path parts.
        val filePathParts = decodedSegments.last().split("/").filter { it.isNotBlank() }
        val fileName = filePathParts.lastOrNull()
        if (fileName == null) {
            Log.w(TAG, "Unable to determine file name from uri: $fileUri")
            return null
        }

        // Scan all directory segments (from innermost outward, skipping the filename itself)
        // looking for a segment whose name matches a known StreamType.
        val allSegments = (decodedSegments.dropLast(1) + filePathParts.dropLast(1)).distinct()
        val streamType = allSegments.reversed()
            .mapNotNull { seg -> DataCollector.StreamType.entries.firstOrNull { it.name == seg } }
            .firstOrNull()
        if (streamType == null) {
            Log.w(TAG, "Unable to determine stream type from uri: $fileUri")
            return null
        }

        val timestamp = fileName.split("_")
            .mapNotNull { token -> runCatching { LocalDateTime.parse(token, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }.getOrNull() }
            .firstOrNull()
        if (timestamp == null) {
            Log.w(TAG, "Unable to determine timestamp from file name: $fileName")
            return null
        }

        return StreamFileMetadata(streamType = streamType, timestamp = timestamp)
    }

    /** Returns the marker file of one session and the data files that belong to it. Returns null when the session has no marker file. */
    fun findMarkerLinks(sessionFileUris: Collection<Uri>): Pair<Uri, List<Uri>>? {
        val markerUri = sessionFileUris.firstOrNull { parseMetadata(it)?.streamType == DataCollector.StreamType.MARKER }
            ?: return null
        return markerUri to sessionFileUris.filter { it != markerUri }
    }
}
