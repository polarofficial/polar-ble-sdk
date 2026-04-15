package com.polar.polarsensordatacollector.ui.exercisev2

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polar.polarsensordatacollector.repository.ExerciseV2Repository
import com.polar.polarsensordatacollector.ui.landing.ONLINE_OFFLINE_KEY_DEVICE_ID
import com.polar.sdk.api.model.PolarExerciseData
import com.polar.sdk.api.model.PolarExerciseEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.toList
import javax.inject.Inject

private const val TAG = "ExerciseV2ViewModel"

@HiltViewModel
class ExerciseV2ViewModel @Inject constructor(
    private val repository: ExerciseV2Repository,
    state: SavedStateHandle
) : ViewModel() {

    private val deviceId: String = state.get<String>(ONLINE_OFFLINE_KEY_DEVICE_ID)
        ?: throw Exception("ExerciseV2ViewModel must know the deviceId")

    fun startOfflineExercise(onResult: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = repository.startOfflineExerciseV2(deviceId)
                Log.d(TAG, "startOfflineExercise: result=${result.result}, path=${result.directoryPath}")
                if (result.result.name == "SUCCESS") {
                    launch(Dispatchers.Main) { onResult() }
                } else {
                    val msg = "Start failed: ${result.result}"
                    Log.e(TAG, msg)
                    launch(Dispatchers.Main) { onError(msg) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "startOfflineExercise error: ${e.message}")
                launch(Dispatchers.Main) { onError(e.message ?: "Unknown error") }
            }
        }
    }

    fun stopOfflineExercise(onResult: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.stopOfflineExerciseV2(deviceId)
                Log.d(TAG, "stopOfflineExercise: success")
                launch(Dispatchers.Main) { onResult() }
            } catch (e: Exception) {
                Log.e(TAG, "stopOfflineExercise error: ${e.message}")
                launch(Dispatchers.Main) { onError(e.message ?: "Unknown error") }
            }
        }
    }

    fun listOfflineExercises(onNext: (PolarExerciseEntry) -> Unit, onError: (String) -> Unit, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.listOfflineExercisesV2(deviceId).collect { entry ->
                    Log.d(TAG, "listOfflineExercises: Found exercise ${entry.identifier}")
                    launch(Dispatchers.Main) { onNext(entry) }
                }
                Log.d(TAG, "listOfflineExercises: Complete")
                launch(Dispatchers.Main) { onComplete() }
            } catch (e: Exception) {
                Log.e(TAG, "listOfflineExercises error: ${e.message}")
                launch(Dispatchers.Main) { onError(e.message ?: "Unknown error") }
            }
        }
    }

    fun fetchOfflineExercise(onResult: (PolarExerciseData) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entry = repository.listOfflineExercisesV2(deviceId).toList().firstOrNull()
                    ?: throw Exception("No exercises found")
                Log.d(TAG, "fetchOfflineExercise: Fetching first exercise ${entry.identifier}")
                val data = repository.fetchOfflineExerciseV2(deviceId, entry)
                Log.d(TAG, "fetchOfflineExercise: Success")
                launch(Dispatchers.Main) { onResult(data) }
            } catch (e: Exception) {
                Log.e(TAG, "fetchOfflineExercise error: ${e.message}")
                launch(Dispatchers.Main) { onError(e.message ?: "Unknown error") }
            }
        }
    }

    fun removeOfflineExercise(onResult: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entry = repository.listOfflineExercisesV2(deviceId).toList().firstOrNull()
                    ?: throw Exception("No exercises found")
                Log.d(TAG, "removeOfflineExercise: Removing first exercise ${entry.identifier}")
                repository.removeOfflineExerciseV2(deviceId, entry)
                Log.d(TAG, "removeOfflineExercise: Success")
                launch(Dispatchers.Main) { onResult() }
            } catch (e: Exception) {
                Log.e(TAG, "removeOfflineExercise error: ${e.message}")
                launch(Dispatchers.Main) { onError(e.message ?: "Unknown error") }
            }
        }
    }

    fun getOfflineExerciseStatus(onResult: (Boolean) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val running = repository.getOfflineExerciseStatusV2(deviceId)
                Log.d(TAG, "getOfflineExerciseStatus: running=$running")
                launch(Dispatchers.Main) { onResult(running) }
            } catch (e: Exception) {
                Log.e(TAG, "getOfflineExerciseStatus error: ${e.message}")
                launch(Dispatchers.Main) { onError(e.message ?: "Unknown error") }
            }
        }
    }

    fun checkStatusAndListOfflineExercises(
        onResult: (isRunning: Boolean, hasExercise: Boolean, entry: PolarExerciseEntry?) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val running = repository.getOfflineExerciseStatusV2(deviceId)
                val entries = repository.listOfflineExercisesV2(deviceId).toList()
                Log.d(TAG, "checkStatusAndList: running=$running, exerciseCount=${entries.size}")
                launch(Dispatchers.Main) { onResult(running, entries.isNotEmpty(), entries.firstOrNull()) }
            } catch (e: Exception) {
                Log.e(TAG, "checkStatusAndList error: ${e.message}")
                launch(Dispatchers.Main) { onError(e.message ?: "Unknown error") }
            }
        }
    }
}
