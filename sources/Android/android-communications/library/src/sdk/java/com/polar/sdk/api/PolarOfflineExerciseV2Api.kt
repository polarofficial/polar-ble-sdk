// Copyright © 2023 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api

import com.polar.sdk.api.model.PolarExerciseSession
import com.polar.sdk.api.model.PolarExerciseEntry
import com.polar.sdk.api.model.PolarExerciseData
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single

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
     * @return Single stream of [OfflineExerciseStartResult]
     */
    fun startOfflineExerciseV2(
        identifier: String,
        sportProfile: PolarExerciseSession.SportProfile = PolarExerciseSession.SportProfile.OTHER_OUTDOOR
    ): Single<OfflineExerciseStartResult>

    /**
     * Stop an ongoing offline exercise.
     *
     * Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2]
     *
     * @param identifier Polar device id or BT address
     * @return Completable stream
     */
    fun stopOfflineExerciseV2(identifier: String): Completable

    /**
     * Get the current offline exercise status.
     * Returns true if an offline exercise is running.
     *
     * Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2]
     *
     * @param identifier Polar device id or BT address
     * @return Single stream of Boolean indicating if exercise is running
     */
    fun getOfflineExerciseStatusV2(identifier: String): Single<Boolean>

    /**
     * List all offline exercises stored in the device.
     *
     * Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2]
     *
     * @param identifier Polar device id or BT address
     * @param directoryPath Optional directory path to search
     * @return Flowable stream of [PolarExerciseEntry] entries
     */
    fun listOfflineExercisesV2(identifier: String, directoryPath: String = "/"): Flowable<PolarExerciseEntry>

    /**
     * Fetch an offline exercise from the device.
     *
     * Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2]
     *
     * @param identifier Polar device id or BT address
     * @param entry [PolarExerciseEntry] object to fetch
     * @return Single stream of [PolarExerciseData]
     */
    fun fetchOfflineExerciseV2(identifier: String, entry: PolarExerciseEntry): Single<PolarExerciseData>

    /**
     * Remove an offline exercise from the device.
     *
     * Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2]
     *
     * @param identifier Polar device id or BT address
     * @param entry [PolarExerciseEntry] object to remove
     * @return Completable stream
     */
    fun removeOfflineExerciseV2(identifier: String, entry: PolarExerciseEntry): Completable

    /**
     * Check if device supports offline exercise V2 (dm_exercise capability).
     * Reads DEVICE.BPB to verify if the device advertises dm_exercise capability.
     *
     * Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2]
     *
     * @param identifier Polar device id or BT address
     * @return Single that emits true if dm_exercise is supported, false otherwise
     */
    fun isOfflineExerciseV2Supported(identifier: String): Single<Boolean>

    companion object {
        const val EXERCISE_SAMPLES_FILE = "SAMPLES.BPB"
    }
}


