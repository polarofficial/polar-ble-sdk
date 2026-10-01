package com.polar.sdk.api

import com.polar.sdk.api.errors.PolarTimeoutException
import com.polar.sdk.api.model.sleep.PolarSleepData
import com.polar.sdk.api.model.sleep.PolarSleepRecordingStatus
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
     * @param timeoutMs maximum time in milliseconds to wait for the state. Default is 30000ms.
     * @return boolean value indicating if sleep recording is ongoing
     * @throws [PolarTimeoutException] if no response is received within [timeoutMs] milliseconds
     * @throws Throwable if the operation fails
     **/
    @Deprecated(
        message = "Use getSleepRecordingStatus, which reports unknown state instead of off",
        replaceWith = ReplaceWith("getSleepRecordingStatus(identifier, timeoutMs)")
    )
    suspend fun getSleepRecordingState(identifier: String, timeoutMs: Long = 30_000L): Boolean

    /**
     * Observe sleep recording state. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA]
     *
     * @param identifier The Polar device ID or BT address
     * @return [Flow] of boolean values indicating if sleep recording is ongoing
     */
    @Deprecated(
        message = "Use observeSleepRecordingStatus, which reports unknown state instead of off",
        replaceWith = ReplaceWith("observeSleepRecordingStatus(identifier)")
    )
    fun observeSleepRecordingState(identifier: String): Flow<Array<Boolean>>

    /**
     * Get sleep recording status. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA]
     *
     * @param identifier The Polar device ID or BT address
     * @param timeoutMs maximum time in milliseconds to wait for the status. Default is 30000ms.
     * @return [PolarSleepRecordingStatus]. [PolarSleepRecordingStatus.UNKNOWN] means the device did not report the state.
     * @throws [PolarTimeoutException] if no response is received within [timeoutMs] milliseconds
     * @throws Throwable if the operation fails
     **/
    suspend fun getSleepRecordingStatus(identifier: String, timeoutMs: Long = 30_000L): PolarSleepRecordingStatus

    /**
     * Observe sleep recording status. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA]
     *
     * @param identifier The Polar device ID or BT address
     * @return [Flow] of [PolarSleepRecordingStatus] batches. [PolarSleepRecordingStatus.UNKNOWN] means the device did not report the state.
     */
    fun observeSleepRecordingStatus(identifier: String): Flow<Array<PolarSleepRecordingStatus>>

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