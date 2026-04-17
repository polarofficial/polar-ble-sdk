package com.polar.polarsensordatacollector.repository

import android.util.Log
import androidx.core.util.Pair
import com.polar.sdk.api.PolarH10OfflineExerciseApi
import com.polar.sdk.api.model.PolarExerciseData
import com.polar.sdk.api.model.PolarExerciseEntry
import com.polar.sdk.impl.BDBleApiImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.toList
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

    suspend fun requestRecordingStatus(deviceId: String): Pair<Boolean, String> {
        return api.requestRecordingStatus(deviceId)
    }

    suspend fun listExercises(deviceId: String): List<PolarExerciseEntry> {
        return try {
            api.listExercises(deviceId)
                .catch { e -> Log.e(TAG, "listExercises() failed", e); throw e }
                .toList()
        } catch (e: Exception) {
            Log.e(TAG, "listExercises() failed", e)
            throw e
        }
    }

    fun listExercisesAsFlow(deviceId: String): Flow<PolarExerciseEntry> {
        return api.listExercises(deviceId)
            .catch { e ->
                Log.e(TAG, "listExercises() failed", e)
                throw e
            }
    }

    suspend fun readExercise(
        deviceId: String,
        entry: PolarExerciseEntry
    ): PolarExerciseData {
        return try {
            api.fetchExercise(deviceId, entry)
        } catch (e: Exception) {
            Log.e(TAG, "readExercise() failed", e)
            throw e
        }
    }

    suspend fun removeExercise(
        deviceId: String,
        entry: PolarExerciseEntry
    ) {
        try {
            api.removeExercise(deviceId, entry)
        } catch (e: Exception) {
            Log.e(TAG, "removeExercise() failed", e)
            throw e
        }
    }

    suspend fun startRecording(
        deviceId: String,
        exerciseId: String
    ) {
        try {
            api.startRecording(
                deviceId,
                exerciseId,
                PolarH10OfflineExerciseApi.RecordingInterval.INTERVAL_1S,
                PolarH10OfflineExerciseApi.SampleType.HR
            )
            Log.d(TAG, "Recording started for $deviceId, exerciseId=$exerciseId")
        } catch (e: Exception) {
            Log.e(TAG, "startRecording() failed", e)
            throw e
        }
    }

    suspend fun stopRecording(deviceId: String) {
        try {
            api.stopRecording(deviceId)
            Log.d(TAG, "Recording stopped for $deviceId")
        } catch (e: Exception) {
            Log.e(TAG, "stopRecording() failed", e)
            throw e
        }
    }
}

data class H10RecordingFeature(
    val isSupported: Boolean = false,
    val isEnabled: Boolean = false
)
