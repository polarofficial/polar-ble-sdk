package com.polar.polarsensordatacollector.ui.trainingsession

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polar.polarsensordatacollector.repository.DeviceConnectionState
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.repository.ResultOfRequest
import com.polar.polarsensordatacollector.ui.offlinerecording.OfflineRecordingFetch
import com.polar.sdk.api.model.PolarOfflineRecordingEntry
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionReference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import javax.inject.Inject

internal data class TrainingSessionListDevConnectionState(
    val isConnected: Boolean
)

data class TrainingSessionsUiState(
    val fetchStatus: TrainingSessionFetch,
    val deletingEntryPath: String? = null
)

sealed class TrainingSessionFetch {
    class Success(
        val fetchedTrainingSessions: List<PolarTrainingSessionReference>
    ) : TrainingSessionFetch()

    class InProgress(val fetchedTrainingSessions: List<PolarTrainingSessionReference>) : TrainingSessionFetch()
    class Failure(
        val message: String,
        val throwable: Throwable?
    ) : TrainingSessionFetch()
}

private const val TAG = "ListTrainingSessionsViewModel"

@HiltViewModel
class ListTrainingSessionsViewModel @Inject constructor(
    private val polarDeviceStreamingRepository: PolarDeviceRepository,
    state: SavedStateHandle
) : ViewModel() {
    val format = SimpleDateFormat("yyyyMMdd")
    private val deviceId = state.get<String>("deviceIdFragmentArgument") ?: throw Exception("ListTrainingSessionsViewModel model requires deviceId")
    private val fromDateString = state.get<String>("fromDateFragmentArgument") ?: throw Exception("ListTrainingSessionsViewModel model requires fromDate")
    private val toDateString = state.get<String>("toDateFragmentArgument") ?: throw Exception("ListTrainingSessionsViewModel model requires toDate")
    private val fromDate = format.parse(fromDateString)
    private val toDate = format.parse(toDateString)
    var trainingSessionsUiState by mutableStateOf(TrainingSessionsUiState(TrainingSessionFetch.InProgress(fetchedTrainingSessions = emptyList())))
        private set

    private val devConnectionState = MutableStateFlow(TrainingSessionListDevConnectionState(true))

    private var listOfTrainingSessions: MutableList<PolarTrainingSessionReference> = mutableListOf()

    init {
        viewModelScope.launch {
            polarDeviceStreamingRepository.deviceConnectionStatus
                .collect { connectionStatus ->
                    when (connectionStatus) {
                        is DeviceConnectionState.DeviceConnected -> {
                            devConnectionState.update {
                                it.copy(isConnected = true)
                            }
                        }
                        is DeviceConnectionState.DeviceConnecting,
                        is DeviceConnectionState.DeviceDisconnecting,
                        is DeviceConnectionState.DeviceNotConnected -> {
                            devConnectionState.update {
                                it.copy(isConnected = false)
                            }
                        }
                    }
                }
        }
    }

    fun listTrainingSessions() {
        Log.d(TAG, "listTrainingSessions $deviceId")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                polarDeviceStreamingRepository.getTrainingSessionReferences(deviceId, fromDate, toDate)
                    .onStart {
                        listOfTrainingSessions.clear()
                        withContext(Dispatchers.Main) {
                            trainingSessionsUiState = TrainingSessionsUiState(fetchStatus = TrainingSessionFetch.InProgress(listOfTrainingSessions.toList()))
                        }
                    }
                    .onCompletion {
                        withContext(Dispatchers.Main) {
                            trainingSessionsUiState = TrainingSessionsUiState(fetchStatus = TrainingSessionFetch.Success(listOfTrainingSessions.toList()))
                        }
                    }
                    .collect {
                        listOfTrainingSessions.add(it)
                        withContext(Dispatchers.Main) {
                            trainingSessionsUiState = TrainingSessionsUiState(fetchStatus = TrainingSessionFetch.InProgress(listOfTrainingSessions.toList()))
                        }
                    }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Fetch of trainingSessions error: $e")
                    trainingSessionsUiState = TrainingSessionsUiState(fetchStatus = TrainingSessionFetch.Failure(message = "Fetching of trainingSessions failed", throwable = e))
                }
            }
        }
    }

    fun deleteTrainingSession(entry: PolarTrainingSessionReference) {
        Log.d(TAG, "Delete training session from device $deviceId, path: ${entry.path}")
        viewModelScope.launch {
            trainingSessionsUiState = trainingSessionsUiState.copy(
                deletingEntryPath = entry.path
            )

            when (val result = polarDeviceStreamingRepository.deleteTrainingSession(deviceId, entry.path)) {
                is ResultOfRequest.Success -> {
                    Log.d(TAG, "Training session successfully deleted: ${entry.path}")

                    listOfTrainingSessions.remove(entry)
                    trainingSessionsUiState = trainingSessionsUiState.copy(
                        fetchStatus = TrainingSessionFetch.Success(listOfTrainingSessions.toList()),
                        deletingEntryPath = null
                    )
                }
                is ResultOfRequest.Failure -> {
                    Log.e(TAG, "Failed to delete training session: ${result.message}")

                    trainingSessionsUiState = trainingSessionsUiState.copy(
                        deletingEntryPath = null
                    )
                }
            }
        }
    }
}