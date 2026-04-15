package com.polar.sdk.api

import com.polar.sdk.api.model.sleep.PolarSleepData
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Polar sleep API.
 * Requires feature FEATURE_POLAR_SLEEP_DATA
 */
interface PolarSleepApi {

    /**
     * Get sleep recording state. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA]
     *
     * @param identifier The Polar device ID or BT address
     * @return boolean value indicating if sleep recording is ongoing
     * @throws Throwable if the operation fails
     **/
    suspend fun getSleepRecordingState(identifier: String): Boolean

    /**
     * Observe sleep recording state. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA]
     *
     * @param identifier The Polar device ID or BT address
     * @return [Flow] of boolean values indicating if sleep recording is ongoing
     */
    fun observeSleepRecordingState(identifier: String): Flow<Array<Boolean>>

    /**
     * Stop sleep recording. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA]
     *
     * @param identifier The Polar device ID or BT address
     * @throws Throwable if sleep recording stop action cannot be sent to the device
     */
    suspend fun stopSleepRecording(identifier: String)

    /**
     * Get sleep stages and duration for a given period. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA]
     *
     * @param identifier The Polar device ID or BT address.
     * @param fromDate The starting date of the period to retrieve sleep data from.
     * @param toDate The ending date of the period to retrieve sleep data from.
     * @return list of [PolarSleepData] representing the sleep data for the specified period.
     * @throws Throwable if the operation fails
     */
    suspend fun getSleep(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<PolarSleepData>
}