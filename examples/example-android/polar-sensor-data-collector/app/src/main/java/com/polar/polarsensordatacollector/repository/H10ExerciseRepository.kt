package com.polar.polarsensordatacollector.repository

import android.util.Log
import com.polar.sdk.api.PolarH10OfflineExerciseApi
import com.polar.sdk.api.model.PolarExerciseData
import com.polar.sdk.api.model.PolarExerciseEntry
import com.polar.sdk.impl.BDBleApiImpl
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class H10ExerciseRepository @Inject constructor(
    val api: BDBleApiImpl
) {

    companion object {
        private const val TAG = "H10ExerciseRepository"
    }

    private val _featureState = MutableStateFlow(H10RecordingFeature())
    val featureState: StateFlow<H10RecordingFeature> = _featureState

    fun updateStatus(isSupported: Boolean, isEnabled: Boolean) {
        _featureState.value = _featureState.value.copy(
            isSupported = isSupported,
            isEnabled = isEnabled
        )
    }

    fun updateRecordingEnabled(isEnabled: Boolean) {
        _featureState.value = _featureState.value.copy(isEnabled = isEnabled)
    }

    fun requestRecordingStatus(deviceId: String): Single<Pair<Boolean, String>> {
        return api.requestRecordingStatus(deviceId)
            .map { Pair(it.first, it.second) }
    }

    fun listExercises(deviceId: String): Single<List<PolarExerciseEntry>> {
        return api.listExercises(deviceId)
            .toList()
            .doOnError { e ->
                Log.e(TAG, "listExercises() failed", e)
            }
    }

    fun readExercise(
        deviceId: String,
        entry: PolarExerciseEntry
    ): Single<PolarExerciseData> {
        return api.fetchExercise(deviceId, entry)
            .doOnError { e ->
                Log.e(TAG, "readExercise() failed", e)
            }
    }

    fun removeExercise(
        deviceId: String,
        entry: PolarExerciseEntry
    ): Completable {
        return api.removeExercise(deviceId, entry)
            .doOnError { e ->
                Log.e(TAG, "removeExercise() failed", e)
            }
    }

    fun startRecording(
        deviceId: String,
        exerciseId: String
    ): Completable {
        return api.startRecording(
            deviceId,
            exerciseId,
            PolarH10OfflineExerciseApi.RecordingInterval.INTERVAL_1S,
            PolarH10OfflineExerciseApi.SampleType.HR
        )
            .doOnComplete {
                Log.d(TAG, "Recording started for $deviceId, exerciseId=$exerciseId")
            }
            .doOnError { e ->
                Log.e(TAG, "startRecording() failed", e)
            }
    }

    fun stopRecording(deviceId: String): Completable {
        return api.stopRecording(deviceId)
            .doOnComplete {
                Log.d(TAG, "Recording stopped for $deviceId")
            }
            .doOnError { e ->
                Log.e(TAG, "stopRecording() failed", e)
            }
    }
}

data class H10RecordingFeature(
    val isSupported: Boolean = false,
    val isEnabled: Boolean = false
)
