package com.polar.sdk.api

import com.polar.sdk.api.model.sleep.PolarSleepData
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Completable
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
     * @return A [Single] boolean value indicating if sleep recording is ongoing
     **/
    fun getSleepRecordingState(identifier: String): Single<Boolean>

    /**
     * Observe sleep recording state. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA]
     *
     * @param identifier The Polar device ID or BT address
     * @return [Flowable] of boolean values indicating if sleep recording is ongoing
     */
    fun observeSleepRecordingState(identifier: String):  Flowable<Array<Boolean>>

    /**
     * Stop sleep recording. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA]
     *
     * @param identifier The Polar device ID or BT address
     * @return [Completable] success when sleep recording stop action has been succesfully
     * sent to device
     */
     fun stopSleepRecording(identifier: String): Completable

    /**
     * Get sleep stages and duration for a given period. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA]
     *
     * @param identifier The Polar device ID or BT address.
     * @param fromDate The starting date of the period to retrieve sleep data from.
     * @param toDate The ending date of the period to retrieve sleep data from.
     * @return A [Single] emitting a list of [PolarSleepData] representing the sleep data for the specified period.
     */
    fun getSleep(identifier: String, fromDate: LocalDate, toDate: LocalDate): Single<List<PolarSleepData>>
}