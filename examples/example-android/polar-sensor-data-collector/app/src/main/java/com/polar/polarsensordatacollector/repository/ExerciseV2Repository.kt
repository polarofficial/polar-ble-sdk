package com.polar.polarsensordatacollector.repository

import android.util.Log
import com.polar.sdk.api.PolarOfflineExerciseV2Api
import com.polar.sdk.api.model.PolarExerciseData
import com.polar.sdk.api.model.PolarExerciseEntry
import com.polar.sdk.api.model.PolarExerciseSession
import com.polar.sdk.impl.BDBleApiImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseV2Repository @Inject constructor(
    private val api: BDBleApiImpl
) : PolarOfflineExerciseV2Api {

    companion object {
        private const val TAG = "ExerciseV2Repository"
    }

    /**
     * Start a new offline exercise on the device.
     *
     * @param identifier Polar device id or BT address
     * @param sportProfile The sport profile for the exercise (default: OTHER_OUTDOOR)
     * @return [PolarOfflineExerciseV2Api.OfflineExerciseStartResult]
     */
    override suspend fun startOfflineExerciseV2(
        identifier: String,
        sportProfile: PolarExerciseSession.SportProfile
    ): PolarOfflineExerciseV2Api.OfflineExerciseStartResult {
        return try {
            val result = (api as PolarOfflineExerciseV2Api).startOfflineExerciseV2(identifier, sportProfile)
            Log.d(TAG, "startOfflineExerciseV2 success: ${result.result}, path: ${result.directoryPath}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "startOfflineExerciseV2 failed: ${e.message}")
            throw e
        }
    }

    /**
     * Stop an ongoing offline exercise.
     *
     * @param identifier Polar device id or BT address
     */
    override suspend fun stopOfflineExerciseV2(identifier: String) {
        try {
            (api as PolarOfflineExerciseV2Api).stopOfflineExerciseV2(identifier)
            Log.d(TAG, "stopOfflineExerciseV2 success")
        } catch (e: Exception) {
            Log.e(TAG, "stopOfflineExerciseV2 failed: ${e.message}")
            throw e
        }
    }

    /**
     * Get the current offline exercise status.
     *
     * @param identifier Polar device id or BT address
     * @return true if exercise is running
     */
    override suspend fun getOfflineExerciseStatusV2(identifier: String): Boolean {
        return try {
            val isRunning = (api as PolarOfflineExerciseV2Api).getOfflineExerciseStatusV2(identifier)
            Log.d(TAG, "getOfflineExerciseStatusV2 success: isRunning=$isRunning")
            isRunning
        } catch (e: Exception) {
            Log.e(TAG, "getOfflineExerciseStatusV2 failed: ${e.message}")
            if (isSystemBusyError(e)) {
                Log.e(TAG, "Device BUSY (202) - Exercise running")
                throw Exception("Exercise running, system busy (202)")
            }
            throw e
        }
    }

    /**
     * List all offline exercises stored in the device.
     *
     * @param identifier Polar device id or BT address
     * @param directoryPath Optional directory path to search (default: "/")
     * @return [Flow] stream of [PolarExerciseEntry] entries
     */
    override fun listOfflineExercisesV2(identifier: String, directoryPath: String): Flow<PolarExerciseEntry> {
        return (api as PolarOfflineExerciseV2Api)
            .listOfflineExercisesV2(identifier, directoryPath)
            .onEach { entry ->
                Log.d(TAG, "listOfflineExercisesV2 found exercise: ${entry.identifier}")
            }
            .catch { throwable ->
                Log.e(TAG, "listOfflineExercisesV2 failed: ${throwable.message}")
                if (isSystemBusyError(throwable)) {
                    Log.e(TAG, "Device BUSY (202) - Stop exercise first")
                    Log.e(TAG, "Exercise running. Stop exercise first before listing.")
                    throw Exception("Exercise running, system busy (202)")
                }
                throw throwable
            }
    }

    /**
     * Fetch an offline exercise from the device.
     *
     * @param identifier Polar device id or BT address
     * @param entry [PolarExerciseEntry] object to fetch
     * @return [PolarExerciseData]
     */
    override suspend fun fetchOfflineExerciseV2(identifier: String, entry: PolarExerciseEntry): PolarExerciseData {
        return try {
            val data = (api as PolarOfflineExerciseV2Api).fetchOfflineExerciseV2(identifier, entry)
            Log.d(TAG, "fetchOfflineExerciseV2 success: ${data.hrSamples.size} samples")
            data
        } catch (e: Exception) {
            Log.e(TAG, "fetchOfflineExerciseV2 failed: ${e.message}")
            if (isSystemBusyError(e)) {
                Log.e(TAG, "Device BUSY (202) - Stop exercise first")
                Log.e(TAG, "Exercise running. Stop exercise first before fetching.")
                throw Exception("Exercise running, system busy (202)")
            }
            throw e
        }
    }

    /**
     * Remove an offline exercise from the device.
     *
     * @param identifier Polar device id or BT address
     * @param entry [PolarExerciseEntry] object to remove
     */
    override suspend fun removeOfflineExerciseV2(identifier: String, entry: PolarExerciseEntry) {
        try {
            (api as PolarOfflineExerciseV2Api).removeOfflineExerciseV2(identifier, entry)
            Log.d(TAG, "removeOfflineExerciseV2 success: removed ${entry.path}")
        } catch (e: Exception) {
            Log.e(TAG, "removeOfflineExerciseV2 failed: ${e.message}")
            if (isSystemBusyError(e)) {
                Log.e(TAG, "Exercise running. Stop exercise first before removing.")
                throw Exception("Exercise running, system busy (202)")
            }
            throw e
        }
    }

    /**
     * Check if device supports offline exercise V2 (dm_exercise capability).
     * Reads DEVICE.BPB to verify if the device advertises dm_exercise capability.
     *
     * @param identifier Polar device id or BT address
     * @return true if dm_exercise is supported, false otherwise
     */
    override suspend fun isOfflineExerciseV2Supported(identifier: String): Boolean {
        Log.d(TAG, "========== isOfflineExerciseV2Supported called for device: $identifier ==========")
        return try {
            val isSupported = (api as PolarOfflineExerciseV2Api).isOfflineExerciseV2Supported(identifier)
            Log.d(TAG, "========== CAPABILITY CHECK RESULT ==========")
            Log.d(TAG, "Device: $identifier")
            Log.d(TAG, "dm_exercise supported: $isSupported")
            if (isSupported) {
                Log.i(TAG, "✓ Device SUPPORTS offline exercise V2")
            } else {
                Log.w(TAG, "✗ Device DOES NOT support offline exercise V2")
            }
            Log.d(TAG, "==========================================")
            isSupported
        } catch (e: Exception) {
            Log.e(TAG, "========== CAPABILITY CHECK ERROR ==========")
            Log.e(TAG, "Device: $identifier")
            Log.e(TAG, "Error: ${e.message}")
            Log.e(TAG, "Error type: ${e.javaClass.simpleName}")
            e.printStackTrace()
            Log.e(TAG, "==========================================")
            throw e
        }
    }

    private fun isSystemBusyError(throwable: Throwable): Boolean {
        return throwable.message?.contains("202") == true ||
                throwable.message?.contains("SYSTEM_BUSY") == true ||
                throwable.message?.contains("Exercise running") == true ||
                throwable.message?.contains("BUSY") == true
    }
}
