package com.polar.polarsensordatacollector.ui.exercisev2

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polar.polarsensordatacollector.repository.ExerciseV2Repository
import com.polar.polarsensordatacollector.ui.exercise.ExerciseRepository
import com.polar.polarsensordatacollector.ui.landing.ONLINE_OFFLINE_KEY_DEVICE_ID
import com.polar.sdk.api.model.PolarExerciseData
import com.polar.sdk.api.model.PolarExerciseEntry
import com.polar.sdk.api.model.PolarExerciseSession
import com.polar.sdk.impl.BDBleApiImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.toList
import javax.inject.Inject

private const val TAG = "ExerciseV2ViewModel"
private const val EXERCISE_PREFS_NAME = "exercise_prefs"

@HiltViewModel
class ExerciseV2ViewModel @Inject constructor(
    private val repository: ExerciseV2Repository,
    private val onlineApi: BDBleApiImpl,
    @ApplicationContext private val appContext: Context,
    state: SavedStateHandle
) : ViewModel() {

    private val identifier: String = state.get<String>(ONLINE_OFFLINE_KEY_DEVICE_ID)
        ?: throw Exception("ExerciseV2ViewModel must know the identifier")

    // Backs the pause/resume controls. Uses the online training-session API,
    // not the offline-recording API used for start/stop.
    private val onlineExerciseRepository: ExerciseRepository by lazy {
        val prefs = appContext.getSharedPreferences(EXERCISE_PREFS_NAME, Context.MODE_PRIVATE)
        ExerciseRepository(onlineApi, onlineApi, identifier, prefs)
    }

    // Single source of truth for the online exercise status (and therefore
    // pause/resume state). The UI reads this instead of keeping its own
    // paused/running flag, so the two layers cannot drift out of sync.
    val onlineExerciseState: StateFlow<PolarExerciseSession.ExerciseInfo> =
        onlineExerciseRepository.state()

    fun startOfflineExercise(onResult: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = repository.startOfflineExerciseV2(identifier)
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
                repository.stopOfflineExerciseV2(identifier)
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
                repository.listOfflineExercisesV2(identifier).collect { entry ->
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
                val entry = repository.listOfflineExercisesV2(identifier).toList().firstOrNull()
                    ?: throw Exception("No exercises found")
                Log.d(TAG, "fetchOfflineExercise: Fetching first exercise ${entry.identifier}")
                val data = repository.fetchOfflineExerciseV2(identifier, entry)
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
                val entry = repository.listOfflineExercisesV2(identifier).toList().firstOrNull()
                    ?: throw Exception("No exercises found")
                Log.d(TAG, "removeOfflineExercise: Removing first exercise ${entry.identifier}")
                repository.removeOfflineExerciseV2(identifier, entry)
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
                val running = repository.getOfflineExerciseStatusV2(identifier)
                Log.d(TAG, "getOfflineExerciseStatus: running=$running")
                launch(Dispatchers.Main) { onResult(running) }
            } catch (e: Exception) {
                Log.e(TAG, "getOfflineExerciseStatus error: ${e.message}")
                launch(Dispatchers.Main) { onError(e.message ?: "Unknown error") }
            }
        }
    }

    fun listOfflineExercises(
        onResult: (hasExercise: Boolean, entry: PolarExerciseEntry?) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entries = repository.listOfflineExercisesV2(identifier).toList()
                Log.d(TAG, "listOfflineExercises: exerciseCount=${entries.size}")
                launch(Dispatchers.Main) { onResult(entries.isNotEmpty(), entries.firstOrNull()) }
            } catch (e: Exception) {
                Log.e(TAG, "listOfflineExercises error: ${e.message}")
                launch(Dispatchers.Main) { onError(e.message ?: "Unknown error") }
            }
        }
    }

    // Pauses the online training session. Not related to the offline
    // recording start/stop state above.
    fun pauseExercise(onResult: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                onlineExerciseRepository.pause()
                Log.d(TAG, "pauseExercise: success")
                launch(Dispatchers.Main) { onResult() }
            } catch (e: Exception) {
                Log.e(TAG, "pauseExercise error: ${e.message}")
                launch(Dispatchers.Main) { onError(e.message ?: "Unknown error") }
            }
        }
    }

    // Resumes the online training session after a pause.
    fun resumeExercise(onResult: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                onlineExerciseRepository.resume()
                Log.d(TAG, "resumeExercise: success")
                launch(Dispatchers.Main) { onResult() }
            } catch (e: Exception) {
                Log.e(TAG, "resumeExercise error: ${e.message}")
                launch(Dispatchers.Main) { onError(e.message ?: "Unknown error") }
            }
        }
    }

    // Refreshes onlineExerciseState from the device. Call this whenever the
    // screen becomes visible so a paused state set from elsewhere (for
    // example the Exercise screen) is reflected here too.
    fun refreshOnlineExerciseStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            onlineExerciseRepository.refresh()
        }
    }
}
