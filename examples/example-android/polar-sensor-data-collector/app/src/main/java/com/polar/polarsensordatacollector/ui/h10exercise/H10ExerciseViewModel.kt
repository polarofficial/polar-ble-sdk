package com.polar.polarsensordatacollector.ui.h10exercise

import android.util.Log
import androidx.core.util.component1
import androidx.core.util.component2
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polar.polarsensordatacollector.repository.H10ExerciseRepository
import com.polar.polarsensordatacollector.ui.landing.ONLINE_OFFLINE_KEY_DEVICE_ID
import com.polar.sdk.api.model.PolarExerciseData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "H10ExerciseViewModel"

@HiltViewModel
class H10ExerciseViewModel @Inject constructor(
    private val repository: H10ExerciseRepository,
    state: SavedStateHandle
) : ViewModel() {

    private val deviceId =
        state.get<String>(ONLINE_OFFLINE_KEY_DEVICE_ID)
            ?: throw Exception("Device settings viewModel must know the deviceId")

    private val _statusText = MutableStateFlow("")

    val featureState = repository.featureState

    init {
        _statusText.value = ""
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (enabled, _) = repository.requestRecordingStatus(deviceId)
                repository.updateStatus(isSupported = true, isEnabled = enabled)
            } catch (e: Exception) {
                Log.e(TAG, "requestRecordingStatus() failed", e)
            }
        }
    }

    fun listExercises(onResult: (Int) -> Unit, onError: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entries = repository.listExercises(deviceId)
                launch(Dispatchers.Main) { onResult(entries.size) }
            } catch (e: Exception) {
                Log.e(TAG, "listExercises() failed", e)
                launch(Dispatchers.Main) { onError() }
            }
        }
    }

    fun readFirstExercise(onExercise: (PolarExerciseData) -> Unit, onError: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entries = repository.listExercises(deviceId)
                if (entries.isEmpty()) throw Exception("No exercises")
                val exercise = repository.readExercise(deviceId, entries.first())
                launch(Dispatchers.Main) { onExercise(exercise) }
            } catch (e: Exception) {
                Log.e(TAG, "readFirstExercise() failed", e)
                launch(Dispatchers.Main) { onError() }
            }
        }
    }

    fun removeFirstExercise(onComplete: () -> Unit, onError: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entries = repository.listExercises(deviceId)
                if (entries.isEmpty()) throw Exception("No exercises")
                repository.removeExercise(deviceId, entries.first())
                launch(Dispatchers.Main) { onComplete() }
            } catch (e: Exception) {
                Log.e(TAG, "removeFirstExercise() failed", e)
                launch(Dispatchers.Main) { onError() }
            }
        }
    }

    fun toggleRecording() {
        val feature = featureState.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (feature.isEnabled) {
                    repository.stopRecording(deviceId)
                } else {
                    val exerciseId = "H10_EX_${System.currentTimeMillis()}"
                    repository.startRecording(deviceId, exerciseId)
                }
                repository.updateRecordingEnabled(!feature.isEnabled)
            } catch (e: Exception) {
                Log.e(TAG, "toggleRecording() failed", e)
            }
        }
    }
}