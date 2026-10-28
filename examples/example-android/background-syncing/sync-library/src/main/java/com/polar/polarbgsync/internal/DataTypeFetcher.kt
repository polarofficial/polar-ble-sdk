// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.polarbgsync.internal

import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.polar.polarbgsync.api.SyncResult
import com.polar.polarbgsync.api.SyncableDataType
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarOfflineRecordingData
import com.polar.sdk.api.model.PolarOfflineRecordingEntry
import com.polar.sdk.impl.utils.CaloriesType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.toList
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Fetches data from the device for each [SyncableDataType] using the appropriate SDK API,
 * then saves the results to local storage via [LocalDataStore].
 */
internal class DataTypeFetcher(
    private val api: PolarBleApi,
    private val localDataStore: LocalDataStore,
    private val gson: Gson,
    private val prefs: SharedPreferences,
    private val syncStateTracker: SyncStateTracker,
    private val cursors: DayCursors = DayCursors(),
    private val autoDeleteFromDevice: Boolean = false,
    private val offlineRecordingRetentionDays: Int = 0
) {

    companion object {
        private const val TAG = "DataTypeFetcher"
        private const val PREF_LAST_SYNC_PREFIX = "last_sync_"
        private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE
        /** Default lookback window for initial sync (30 days) */
        private const val DEFAULT_LOOKBACK_DAYS = 30L
    }

    /**
     * Fetch data for a given type and save to local storage.
     * Returns a list of sync results for each date/entry that was saved.
     *
     * [onResults] is invoked as soon as each day/entry is durably saved (and checkpointed), so
     * progress is reported even if a later day fails or the fetch is cancelled/timed out.
     * Failures are thrown, never reported as an empty success.
     */
    suspend fun fetchAndSave(
        deviceId: String,
        dataType: SyncableDataType,
        onResults: suspend (List<SyncResult>) -> Unit = {}
    ): List<SyncResult> {
        val today = LocalDate.now()

        // For date-range types, use the sync watermark to skip already-synced past days.
        // The watermark + 1 day is the earliest date we still need to fetch.
        // For reference-based types (training sessions, offline recordings),
        // path-level dedup happens inside their fetch methods.
        val fromDate = if (!dataType.isOfflineRecording && dataType != SyncableDataType.TRAINING_SESSIONS) {
            val watermark = syncStateTracker.getWatermark(deviceId, dataType)
            if (watermark != null) {
                val nextDate = watermark.plusDays(1)
                if (nextDate.isAfter(today)) {
                    Log.d(TAG, "Skipping $dataType for $deviceId — fully synced up to $watermark")
                    return emptyList()
                }
                nextDate
            } else {
                getLastSyncDate(deviceId, dataType)
                    ?: today.minusDays(DEFAULT_LOOKBACK_DAYS)
            }
        } else {
            getLastSyncDate(deviceId, dataType)
                ?: today.minusDays(DEFAULT_LOOKBACK_DAYS)
        }
        val toDate = today

        Log.d(TAG, "Fetching $dataType for $deviceId from $fromDate to $toDate")

        val isDateRange = !dataType.isOfflineRecording && dataType != SyncableDataType.TRAINING_SESSIONS
        if (isDateRange) {
            val persist = { date: LocalDate ->
                saveLastSyncDate(deviceId, dataType, date)
                syncStateTracker.advanceWatermark(deviceId, dataType, date)
            }
            if (isSdkRangeScoped(dataType)) {
                return syncWholeRange(
                    fromDate, toDate,
                    fetchRange = { f, t -> fetchRangeScoped(deviceId, dataType, f, t) },
                    onResults = onResults,
                    persistCompleted = persist
                )
            }
            return syncDateRange(
                cursors, deviceId, dataType, fromDate, toDate,
                fetchDay = { day ->
                    Log.d(TAG, "Day begin $dataType $day")
                    fetchDay(deviceId, dataType, day).also {
                        Log.d(TAG, "Day complete $dataType $day items=${it.size}")
                    }
                },
                onResults = onResults,
                persistCompleted = persist
            )
        }

        val results = when (dataType) {
            SyncableDataType.TRAINING_SESSIONS -> fetchTrainingSessions(deviceId, fromDate, toDate, onResults)
            else -> fetchOfflineRecordings(deviceId, dataType, onResults)
        }
        if (results.isNotEmpty()) saveLastSyncDate(deviceId, dataType, toDate)
        return results
    }

    private suspend fun fetchDay(
        deviceId: String, dataType: SyncableDataType, day: LocalDate
    ): List<SyncResult> = when (dataType) {
        SyncableDataType.STEPS -> fetchSteps(deviceId, day)
        SyncableDataType.DISTANCE -> fetchDistance(deviceId, day)
        SyncableDataType.ACTIVE_TIME -> fetchActiveTime(deviceId, day)
        SyncableDataType.CALORIES -> fetchCalories(deviceId, day)
        SyncableDataType.NIGHTLY_RECHARGE -> fetchNightlyRecharge(deviceId, day)
        SyncableDataType.ACTIVITY_SAMPLES -> fetchActivitySamples(deviceId, day)
        SyncableDataType.DAILY_SUMMARY -> fetchDailySummary(deviceId, day)
        SyncableDataType.SLEEP -> fetchSleep(deviceId, day)
        SyncableDataType.SKIN_TEMPERATURE -> fetchSkinTemperature(deviceId, day)
        else -> throw IllegalArgumentException("$dataType is not a date-range type")
    }

    private suspend fun fetchRangeScoped(
        deviceId: String, dataType: SyncableDataType, from: LocalDate, to: LocalDate
    ): List<SyncResult> {
        Log.d(TAG, "Range begin $dataType $from..$to (single SDK call)")
        val results = when (dataType) {
            SyncableDataType.HR_SAMPLES_247 -> fetchHrSamples(deviceId, from, to)
            SyncableDataType.PPI_SAMPLES_247 -> fetchPpiSamples(deviceId, from, to)
            else -> throw IllegalArgumentException("$dataType is not range-scoped")
        }
        Log.d(TAG, "Range complete $dataType items=${results.size}")
        return results
    }

    private suspend fun save(
        deviceId: String, dataType: SyncableDataType, date: LocalDate, json: String
    ): SyncResult {
        currentCoroutineContext().ensureActive()
        val path = localDataStore.saveData(deviceId, dataType, date, json)
        return SyncResult(deviceId, dataType, date, path, json.toByteArray().size.toLong())
    }

    // ── Per-type fetch methods ──────────────────────────────────────────

    private suspend fun fetchSteps(deviceId: String, day: LocalDate): List<SyncResult> {
        val dataList = api.getSteps(deviceId, day, day)
        currentCoroutineContext().ensureActive()
        return dataList.mapNotNull { data ->
            val date = data.date ?: return@mapNotNull null
            save(deviceId, SyncableDataType.STEPS, date, gson.toJson(data))
        }
    }

    private suspend fun fetchDistance(deviceId: String, day: LocalDate): List<SyncResult> {
        val dataList = api.getDistance(deviceId, day, day)
        currentCoroutineContext().ensureActive()
        return dataList.mapNotNull { data ->
            val date = data.date ?: return@mapNotNull null
            save(deviceId, SyncableDataType.DISTANCE, date, gson.toJson(data))
        }
    }

    private suspend fun fetchActiveTime(deviceId: String, day: LocalDate): List<SyncResult> {
        val dataList = api.getActiveTime(deviceId, day, day)
        currentCoroutineContext().ensureActive()
        return dataList.mapNotNull { data ->
            val date = data.date ?: return@mapNotNull null
            save(deviceId, SyncableDataType.ACTIVE_TIME, date, gson.toJson(data))
        }
    }

    private suspend fun fetchCalories(deviceId: String, day: LocalDate): List<SyncResult> {
        val dataList = api.getCalories(deviceId, day, day, CaloriesType.ACTIVITY)
        currentCoroutineContext().ensureActive()
        return dataList.mapNotNull { data ->
            val date = data.date ?: return@mapNotNull null
            save(deviceId, SyncableDataType.CALORIES, date, gson.toJson(data))
        }
    }

    private suspend fun fetchHrSamples(deviceId: String, from: LocalDate, to: LocalDate): List<SyncResult> {
        val dataList = api.get247HrSamples(deviceId, from, to)
        currentCoroutineContext().ensureActive()
        return dataList.map { data -> save(deviceId, SyncableDataType.HR_SAMPLES_247, data.date, gson.toJson(data)) }
    }

    private suspend fun fetchPpiSamples(deviceId: String, from: LocalDate, to: LocalDate): List<SyncResult> {
        val dataList = api.get247PPiSamples(deviceId, from, to)
        currentCoroutineContext().ensureActive()
        // The SDK returns one entry per PPI sample session — multiple entries can share the same date.
        // Group by date and save all sessions for a day as a JSON array to avoid overwriting.
        return dataList.groupBy { it.date }.map { (date, entries) ->
            save(deviceId, SyncableDataType.PPI_SAMPLES_247, date, gson.toJson(entries))
        }
    }

    private suspend fun fetchNightlyRecharge(deviceId: String, day: LocalDate): List<SyncResult> {
        val dataList = api.getNightlyRecharge(deviceId, day, day)
        currentCoroutineContext().ensureActive()
        return dataList.mapNotNull { data ->
            val date = data.sleepResultDate ?: data.createdTimestamp.toLocalDate()
            save(deviceId, SyncableDataType.NIGHTLY_RECHARGE, date, gson.toJson(data))
        }
    }

    private suspend fun fetchActivitySamples(deviceId: String, day: LocalDate): List<SyncResult> {
        val dataList = api.getActivitySampleData(deviceId, day, day)
        currentCoroutineContext().ensureActive()
        return dataList.map { data -> save(deviceId, SyncableDataType.ACTIVITY_SAMPLES, day, gson.toJson(data)) }
    }

    private suspend fun fetchDailySummary(deviceId: String, day: LocalDate): List<SyncResult> {
        val dataList = api.getDailySummaryData(deviceId, day, day)
        currentCoroutineContext().ensureActive()
        return dataList.mapNotNull { data ->
            val date = data.date ?: return@mapNotNull null
            save(deviceId, SyncableDataType.DAILY_SUMMARY, date, gson.toJson(data))
        }
    }

    private suspend fun fetchSleep(deviceId: String, day: LocalDate): List<SyncResult> {
        val dataList = api.getSleep(deviceId, day, day)
        currentCoroutineContext().ensureActive()
        return dataList.mapNotNull { data ->
            val date = data.date ?: return@mapNotNull null
            save(deviceId, SyncableDataType.SLEEP, date, gson.toJson(data))
        }
    }

    private suspend fun fetchSkinTemperature(deviceId: String, day: LocalDate): List<SyncResult> {
        val dataList = api.getSkinTemperature(deviceId, day, day)
        currentCoroutineContext().ensureActive()
        return dataList.mapNotNull { data ->
            val date = data.date ?: return@mapNotNull null
            save(deviceId, SyncableDataType.SKIN_TEMPERATURE, date, gson.toJson(data))
        }
    }

    private suspend fun fetchTrainingSessions(
        deviceId: String, from: LocalDate, to: LocalDate,
        onResults: suspend (List<SyncResult>) -> Unit
    ): List<SyncResult> {
        currentCoroutineContext().ensureActive()
        val references = api.getTrainingSessionReferences(deviceId, from, to).toList()
        currentCoroutineContext().ensureActive()
        if (references.isEmpty()) return emptyList()

        val results = mutableListOf<SyncResult>()
        var failures = 0
        var firstFailure: Exception? = null
        for (ref in references) {
            currentCoroutineContext().ensureActive()
            // Skip training sessions that have already been synced (identified by device path)
            if (syncStateTracker.isPathSynced(deviceId, SyncableDataType.TRAINING_SESSIONS, ref.path)) {
                Log.d(TAG, "Skipping already-synced training session: ${ref.path}")
                continue
            }
            try {
                val session = api.getTrainingSession(deviceId, ref)
                val result = save(deviceId, SyncableDataType.TRAINING_SESSIONS, ref.date, gson.toJson(session))
                results.add(result)
                syncStateTracker.markPathSynced(deviceId, SyncableDataType.TRAINING_SESSIONS, ref.path)
                onResults(listOf(result))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch training session ${ref.path}: ${e.message}")
                failures++
                if (firstFailure == null) firstFailure = e
            }
        }
        if (failures > 0) {
            throw PartialSyncException("Failed to fetch $failures of ${references.size} training session(s)", firstFailure)
        }
        return results
    }

    // ── Offline recording fetch ─────────────────────────────────────────

    private suspend fun fetchOfflineRecordings(
        deviceId: String, dataType: SyncableDataType,
        onResults: suspend (List<SyncResult>) -> Unit
    ): List<SyncResult> {
        currentCoroutineContext().ensureActive()
        val deviceDataType = dataType.toPolarDeviceDataType() ?: run {
            Log.w(TAG, "Cannot map $dataType to SDK PolarDeviceDataType")
            return emptyList()
        }
        Log.d(TAG, "Fetching offline recordings for $dataType (SDK type: $deviceDataType)")
        
        val allRecordings = api.listOfflineRecordings(deviceId).toList()
        currentCoroutineContext().ensureActive()
        Log.d(TAG, "Device has ${allRecordings.size} total offline recordings: ${allRecordings.map { it.type }.distinct()}")
        
        val allEntries = allRecordings.filter { it.type == deviceDataType }
        Log.d(TAG, "After filtering for $deviceDataType: ${allEntries.size} entries")
        if (allRecordings.isNotEmpty() && allEntries.isEmpty()) {
            Log.w(TAG, "No recordings match type $deviceDataType; available types: ${allRecordings.map { "${it.type}(${it.path})" }.take(5)}")
        }

        if (allEntries.isEmpty()) return emptyList()

        // Skip entries that have already been synced (identified by device path)
        val entries = allEntries.filter { entry ->
            val alreadySynced = syncStateTracker.isPathSynced(deviceId, dataType, entry.path)
            if (alreadySynced) Log.d(TAG, "Skipping already-synced offline recording: ${entry.path}")
            !alreadySynced
        }
        if (entries.isEmpty()) {
            Log.d(TAG, "All ${allEntries.size} offline ${dataType.name} recording(s) already synced")
            return emptyList()
        }
        Log.d(TAG, "Found ${entries.size} new offline ${dataType.name} recording(s) (${allEntries.size - entries.size} already synced)")

        val results = mutableListOf<SyncResult>()
        var failures = 0
        var firstFailure: Exception? = null
        for (entry in entries) {
            currentCoroutineContext().ensureActive()
            try {
                Log.d(TAG, "→ Calling getOfflineRecord() for ${entry.path}")
                val recordingData = api.getOfflineRecord(deviceId, entry)
                currentCoroutineContext().ensureActive()
                Log.d(TAG, "← getOfflineRecord() returned: $recordingData (class: ${recordingData?.javaClass?.simpleName})")

                if (!isMatchingOfflineRecordingData(dataType, recordingData)) {
                    Log.d(TAG, "  Skipping ${entry.path}: data payload does not match requested $dataType")
                    continue
                }
                
                val json = gson.toJson(recordingData)
                Log.d(TAG, "  Serialized to JSON (${json.length} chars)")
                
                val bytes = json.toByteArray()
                Log.d(TAG, "  Bytes: ${bytes.size} B")
                
                if (bytes.isEmpty()) {
                    Log.w(TAG, "⚠️  JSON is empty for ${entry.path}, skipping")
                    continue
                }
                
                val dateTime = entry.date
                Log.d(TAG, "  Entry date: $dateTime")
                
                currentCoroutineContext().ensureActive()
                val path = localDataStore.saveRawData(deviceId, dataType, dateTime, bytes)
                Log.d(TAG, "  ✓ Saved to: $path (${bytes.size} B)")
                
                val result = SyncResult(deviceId, dataType, dateTime.toLocalDate(), path, bytes.size.toLong())
                results.add(result)
                syncStateTracker.markPathSynced(deviceId, dataType, entry.path)
                onResults(listOf(result))
                Log.d(TAG, "  ✓ Marked as synced")
                
                // Remove recording from device if auto-delete is enabled and retention period has passed
                if (autoDeleteFromDevice) {
                    val cutoffDate = LocalDate.now().minusDays(offlineRecordingRetentionDays.toLong())
                    if (!dateTime.toLocalDate().isAfter(cutoffDate)) {
                        currentCoroutineContext().ensureActive()
                        api.removeOfflineRecord(deviceId, entry)
                        Log.d(TAG, "Fetched & removed offline recording: ${entry.path}")
                    } else {
                        Log.d(TAG, "Fetched offline recording (kept on device, retention ${offlineRecordingRetentionDays}d): ${entry.path}")
                    }
                } else {
                    Log.d(TAG, "Fetched offline recording (auto-delete disabled): ${entry.path}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to fetch offline recording ${entry.path}: ${e.message}", e)
                failures++
                if (firstFailure == null) firstFailure = e
            }
        }
        Log.d(TAG, "Fetch complete: ${results.size} new entries, $failures failed")
        if (failures > 0) {
            throw PartialSyncException("Failed to fetch $failures of ${entries.size} offline ${dataType.name} recording(s)", firstFailure)
        }
        return results
    }

    private fun isMatchingOfflineRecordingData(
        dataType: SyncableDataType,
        recordingData: PolarOfflineRecordingData
    ): Boolean {
        return when (dataType) {
            SyncableDataType.OFFLINE_ACC ->
                recordingData is PolarOfflineRecordingData.AccOfflineRecording
            SyncableDataType.OFFLINE_DERIVED_ACC ->
                recordingData is PolarOfflineRecordingData.DerivedAccOfflineRecording
            SyncableDataType.OFFLINE_ECG ->
                true
            SyncableDataType.OFFLINE_GYRO ->
                recordingData is PolarOfflineRecordingData.GyroOfflineRecording
            SyncableDataType.OFFLINE_MAGNETOMETER ->
                recordingData is PolarOfflineRecordingData.MagOfflineRecording
            SyncableDataType.OFFLINE_PPG ->
                recordingData is PolarOfflineRecordingData.PpgOfflineRecording
            SyncableDataType.OFFLINE_PPI ->
                recordingData is PolarOfflineRecordingData.PpiOfflineRecording
            SyncableDataType.OFFLINE_HR ->
                recordingData is PolarOfflineRecordingData.HrOfflineRecording
            SyncableDataType.OFFLINE_TEMPERATURE ->
                recordingData is PolarOfflineRecordingData.TemperatureOfflineRecording
            SyncableDataType.OFFLINE_SKIN_TEMPERATURE ->
                recordingData is PolarOfflineRecordingData.SkinTemperatureOfflineRecording
            else -> true
        }
    }

    private fun SyncableDataType.toPolarDeviceDataType(): PolarBleApi.PolarDeviceDataType? {
        return when (this) {
            SyncableDataType.OFFLINE_ACC -> PolarBleApi.PolarDeviceDataType.ACC
            SyncableDataType.OFFLINE_DERIVED_ACC -> PolarBleApi.PolarDeviceDataType.ACC
            SyncableDataType.OFFLINE_ECG -> PolarBleApi.PolarDeviceDataType.ECG
            SyncableDataType.OFFLINE_GYRO -> PolarBleApi.PolarDeviceDataType.GYRO
            SyncableDataType.OFFLINE_MAGNETOMETER -> PolarBleApi.PolarDeviceDataType.MAGNETOMETER
            SyncableDataType.OFFLINE_PPG -> PolarBleApi.PolarDeviceDataType.PPG
            SyncableDataType.OFFLINE_PPI -> PolarBleApi.PolarDeviceDataType.PPI
            SyncableDataType.OFFLINE_HR -> PolarBleApi.PolarDeviceDataType.HR
            SyncableDataType.OFFLINE_TEMPERATURE -> PolarBleApi.PolarDeviceDataType.TEMPERATURE
            SyncableDataType.OFFLINE_SKIN_TEMPERATURE -> PolarBleApi.PolarDeviceDataType.SKIN_TEMPERATURE
            else -> null
        }
    }

    // ── Last sync date tracking ─────────────────────────────────────────

    private fun getLastSyncDate(deviceId: String, dataType: SyncableDataType): LocalDate? {
        val key = "${PREF_LAST_SYNC_PREFIX}${deviceId}_${dataType.name}"
        val dateStr = prefs.getString(key, null) ?: return null
        return try {
            LocalDate.parse(dateStr, DATE_FORMAT)
        } catch (e: Exception) {
            null
        }
    }

    private fun saveLastSyncDate(deviceId: String, dataType: SyncableDataType, date: LocalDate) {
        val key = "${PREF_LAST_SYNC_PREFIX}${deviceId}_${dataType.name}"
        prefs.edit().putString(key, date.format(DATE_FORMAT)).apply()
    }
}
