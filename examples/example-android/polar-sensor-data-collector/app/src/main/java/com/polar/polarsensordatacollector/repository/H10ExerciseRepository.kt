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

    suspend fun requestRecordingStatus(identifier: String): Pair<Boolean, String> {
        return api.requestRecordingStatus(identifier)
    }

    suspend fun listExercises(identifier: String): List<PolarExerciseEntry> {
        return try {
            api.listExercises(identifier)
                .catch { e -> Log.e(TAG, "listExercises() failed", e); throw e }
                .toList()
        } catch (e: Exception) {
            Log.e(TAG, "listExercises() failed", e)
            throw e
        }
    }

    fun listExercisesAsFlow(identifier: String): Flow<PolarExerciseEntry> {
        return api.listExercises(identifier)
            .catch { e ->
                Log.e(TAG, "listExercises() failed", e)
                throw e
            }
    }

    suspend fun readExercise(
        identifier: String,
        entry: PolarExerciseEntry
    ): PolarExerciseData {
        return try {
            api.fetchExercise(identifier, entry)
        } catch (e: Exception) {
            Log.e(TAG, "readExercise() failed", e)
            throw e
        }
    }

    suspend fun removeExercise(
        identifier: String,
        entry: PolarExerciseEntry
    ) {
        try {
            api.removeExercise(identifier, entry)
        } catch (e: Exception) {
            Log.e(TAG, "removeExercise() failed", e)
            throw e
        }
    }

    suspend fun startRecording(
        identifier: String,
        exerciseId: String
    ) {
        try {
            api.startRecording(
                identifier,
                exerciseId,
                PolarH10OfflineExerciseApi.RecordingInterval.INTERVAL_1S,
                sampleType = PolarH10OfflineExerciseApi.SampleType.HR
            )
            Log.d(TAG, "Recording started for $identifier, exerciseId=$exerciseId")
        } catch (e: Exception) {
            Log.e(TAG, "startRecording() failed", e)
            throw e
        }
    }

    suspend fun stopRecording(identifier: String) {
        try {
            api.stopRecording(identifier)
            Log.d(TAG, "Recording stopped for $identifier")
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
