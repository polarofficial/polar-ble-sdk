// Copyright © 2023 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api

import com.polar.sdk.api.model.PolarExerciseSession
import com.polar.sdk.api.model.PolarExerciseEntry
import com.polar.sdk.api.model.PolarExerciseData
import kotlinx.coroutines.flow.Flow

/**
 * Offline Exercise V2 API.
 *
 * Allows managing offline exercise sessions on supported Polar devices.
 * This API supports devices that use the Data Merge protocol for offline exercise recording,
 * enabling recording of exercise data when the device is not connected.
 *
 * All methods in this interface require the SDK feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2].
 * The device must have dm_exercise capability.
 *
 * @see PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2
 */
interface PolarOfflineExerciseV2Api {

    /**
     * Result of starting an offline exercise.
     */
    data class OfflineExerciseStartResult(
        val result: StartResult,
        val directoryPath: String
    )

    /**
     * Possible results of starting an offline exercise.
     */
    enum class StartResult {
        SUCCESS,
        EXERCISE_ONGOING,
        LOW_BATTERY,
        SDK_MODE,
        UNKNOWN_SPORT,
        OTHER
    }

    /**
     * Start a new offline exercise on the device.
     *
     * Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2]
     *
     * @param identifier Polar device id or BT address
     * @param sportProfile The sport profile for the exercise
     * @return [OfflineExerciseStartResult]
     */
    suspend fun startOfflineExerciseV2(
        identifier: String,
        sportProfile: PolarExerciseSession.SportProfile = PolarExerciseSession.SportProfile.OTHER_OUTDOOR
    ): OfflineExerciseStartResult

    /**
     * Stop an ongoing offline exercise.
     *
     * Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2]
     *
     * @param identifier Polar device id or BT address
     * @return Success or error
     */
    suspend fun stopOfflineExerciseV2(identifier: String)

    /**
     * Get the current offline exercise status.
     * Returns true if an offline exercise is running.
     *
     * Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2]
     *
     * @param identifier Polar device id or BT address
     * @return true if exercise is running
     */
    suspend fun getOfflineExerciseStatusV2(identifier: String): Boolean

    /**
     * List all offline exercises stored in the device.
     *
     * Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2]
     *
     * @param identifier Polar device id or BT address
     * @param directoryPath Optional directory path to search
     * @return [Flow] stream of [PolarExerciseEntry] entries
     */
    fun listOfflineExercisesV2(identifier: String, directoryPath: String = "/"): Flow<PolarExerciseEntry>

    /**
     * Fetch an offline exercise from the device.
     *
     * Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2]
     *
     * @param identifier Polar device id or BT address
     * @param entry [PolarExerciseEntry] object to fetch
     * @return [PolarExerciseData]
     */
    suspend fun fetchOfflineExerciseV2(identifier: String, entry: PolarExerciseEntry): PolarExerciseData

    /**
     * Remove an offline exercise from the device.
     *
     * Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2]
     *
     * @param identifier Polar device id or BT address
     * @param entry [PolarExerciseEntry] object to remove
     * @return Success or error
     */
    suspend fun removeOfflineExerciseV2(identifier: String, entry: PolarExerciseEntry)

    /**
     * Check if device supports offline exercise V2 (dm_exercise capability).
     * Reads DEVICE.BPB to verify if the device advertises dm_exercise capability.
     *
     * Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2]
     *
     * @param identifier Polar device id or BT address
     * @return true if dm_exercise is supported, false otherwise
     */
    suspend fun isOfflineExerciseV2Supported(identifier: String): Boolean

    companion object {
        const val EXERCISE_SAMPLES_FILE = "SAMPLES.BPB"
    }
}
