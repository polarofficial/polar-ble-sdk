// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.polarbgsync.internal

import android.content.SharedPreferences
import android.util.Log
import com.polar.polarbgsync.api.SyncableDataType
import com.polar.sdk.api.PolarBleApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.toList
import java.time.LocalDate

/**
 * Handles deletion of synced data from the Polar device.
 *
 * Respects the retention policy: date-structured data is only deleted from the device
 * when it is at least [retentionDays] old, ensuring data remains on the device for
 * a configurable number of days after creation.
 */
internal class DeviceDataDeleter(
    private val api: PolarBleApi,
    private val localDataStore: LocalDataStore,
    private val prefs: SharedPreferences
) {
    companion object {
        private const val TAG = "DeviceDataDeleter"
    }

    /**
     * Delete data from the device that has been synced locally and is older
     * than the retention period.
     */
    suspend fun deleteOldSyncedData(
        deviceId: String,
        dataTypes: Set<SyncableDataType>,
        retentionDays: Int
    ) {
        val cutoffDate = LocalDate.now().minusDays(retentionDays.toLong())
        Log.i(TAG, "Cleaning device $deviceId — deleting data synced before $cutoffDate")

        for (dataType in dataTypes) {
            if (dataType == SyncableDataType.TRAINING_SESSIONS) {
                deleteTrainingSessions(deviceId, cutoffDate)
                continue
            }

            val storedType = mapToStoredDataType(dataType) ?: continue

            val syncedDates = localDataStore.getSyncedDates(deviceId, dataType)
            val hasOldSyncedData = syncedDates.any { it.isBefore(cutoffDate) }

            if (hasOldSyncedData) {
                try {
                    currentCoroutineContext().ensureActive()
                    api.deleteStoredDeviceData(deviceId, storedType, cutoffDate)
                    Log.d(TAG, "Deleted $dataType from device up to $cutoffDate")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete $dataType from device: $e")
                }
            }
        }
    }

    /**
     * Delete individual training sessions that are older than the cutoff date.
     */
    private suspend fun deleteTrainingSessions(
        deviceId: String,
        cutoffDate: LocalDate
    ) {
        try {
            val references = api.getTrainingSessionReferences(deviceId, null, cutoffDate).toList()
            for (ref in references) {
                if (localDataStore.hasData(deviceId, SyncableDataType.TRAINING_SESSIONS, ref.date)) {
                    try {
                        currentCoroutineContext().ensureActive()
                        api.deleteTrainingSession(deviceId, ref)
                        Log.d(TAG, "Deleted training session ${ref.date} from device")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to delete training session: $e")
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch training session references for deletion: $e")
        }
    }

    /**
     * Map [SyncableDataType] to the SDK's [PolarBleApi.PolarStoredDataType] for deletion.
     */
    private fun mapToStoredDataType(dataType: SyncableDataType): PolarBleApi.PolarStoredDataType? {
        return when (dataType) {
            SyncableDataType.STEPS -> PolarBleApi.PolarStoredDataType.ACTIVITY
            SyncableDataType.DISTANCE -> PolarBleApi.PolarStoredDataType.ACTIVITY
            SyncableDataType.ACTIVE_TIME -> PolarBleApi.PolarStoredDataType.ACTIVITY
            SyncableDataType.CALORIES -> PolarBleApi.PolarStoredDataType.ACTIVITY
            SyncableDataType.HR_SAMPLES_247 -> PolarBleApi.PolarStoredDataType.AUTO_SAMPLE
            SyncableDataType.PPI_SAMPLES_247 -> PolarBleApi.PolarStoredDataType.AUTO_SAMPLE
            SyncableDataType.NIGHTLY_RECHARGE -> PolarBleApi.PolarStoredDataType.NIGHTLY_RECOVERY
            SyncableDataType.ACTIVITY_SAMPLES -> PolarBleApi.PolarStoredDataType.ACTIVITY
            SyncableDataType.DAILY_SUMMARY -> PolarBleApi.PolarStoredDataType.DAILY_SUMMARY
            SyncableDataType.SLEEP -> PolarBleApi.PolarStoredDataType.SLEEP
            SyncableDataType.TRAINING_SESSIONS -> null // Handled separately
            SyncableDataType.SKIN_TEMPERATURE -> PolarBleApi.PolarStoredDataType.SKIN_TEMP
            SyncableDataType.OFFLINE_ACC,
            SyncableDataType.OFFLINE_DERIVED_ACC,
            SyncableDataType.OFFLINE_ECG,
            SyncableDataType.OFFLINE_GYRO,
            SyncableDataType.OFFLINE_MAGNETOMETER,
            SyncableDataType.OFFLINE_PPG,
            SyncableDataType.OFFLINE_PPI,
            SyncableDataType.OFFLINE_HR,
            SyncableDataType.OFFLINE_TEMPERATURE,
            SyncableDataType.OFFLINE_SKIN_TEMPERATURE -> null // Recordings are removed individually after fetch
        }
    }
}
