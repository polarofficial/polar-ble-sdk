// Copyright © 2023 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api

import androidx.annotation.Size
import androidx.core.util.Pair
import com.polar.sdk.api.model.PolarExerciseData
import com.polar.sdk.api.model.PolarExerciseEntry
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single

/**
 * H10 Exercise recording API.
 *
 * H10 Exercise recording makes it possible to record Hr or Rr data to H10 device memory.
 * With H10 Exercise recording the H10 and phone don't need to be connected all the time, as H10 exercise recording
 * continues in Polar device even the BLE disconnects.
 *
 * Requires features [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING]
 *
 * Note, API is working only with Polar H10 device
 */
interface PolarH10OfflineExerciseApi {

    /**
     * Recoding intervals for H10 recording start
     */
    enum class RecordingInterval(val value: Int) {
        INTERVAL_1S(1), /*!< 1 second interval */
        INTERVAL_5S(5); /*!< 5 second interval */
    }

    /**
     * Sample types for H10 recording start
     */
    enum class SampleType {
        HR, /*!< HeartRate in BPM */
        RR, /*!< RR interval in milliseconds */
    }

    /**
     * Request start recording.
     *
     * @param identifier polar device id or bt address
     * @param exerciseId unique id for exercise entry
     * @param interval   recording interval to be used, parameter has no effect if the `type` parameter is SampleType.RR
     * @param type       sample type to be used
     * @return Completable stream
     */
    fun startRecording(
        identifier: String,
        @Size(min = 1, max = 64) exerciseId: String,
        interval: RecordingInterval?,
        type: SampleType
    ): Completable


    /**
     * Request to stop recording.
     *
     * @param identifier polar device id or bt address
     * @return Completable stream
     */
    fun stopRecording(identifier: String): Completable

    /**
     * Request current recording status.
     *
     * @param identifier polar device id or bt address
     * @return Single stream Pair first recording status, second entryId if available
     */
    fun requestRecordingStatus(identifier: String): Single<Pair<Boolean, String>>

    /**
     * List exercises stored in the device Polar H10 device.
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @return Flowable stream of [PolarExerciseEntry] entries
     */
    fun listExercises(identifier: String): Flowable<PolarExerciseEntry>

    /**
     * Api for fetching a single exercise from Polar H10 device.
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @param entry      [PolarExerciseEntry] object
     * @return Single stream of [PolarExerciseData]
     */
    fun fetchExercise(identifier: String, entry: PolarExerciseEntry): Single<PolarExerciseData>

    /**
     * Api for removing single exercise from Polar H10 device.
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @param entry      entry to be removed
     * @return Completable stream
     */
    fun removeExercise(identifier: String, entry: PolarExerciseEntry): Completable
}