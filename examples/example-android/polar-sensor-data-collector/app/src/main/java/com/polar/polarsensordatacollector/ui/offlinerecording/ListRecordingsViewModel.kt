package com.polar.polarsensordatacollector.ui.offlinerecording

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
import com.polar.polarsensordatacollector.ui.trainingsession.TrainingSessionListDevConnectionState
import com.polar.sdk.api.model.PolarOfflineRecordingEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class RecordingListDevConnectionState(
    val isConnected: Boolean
)

data class OfflineRecordingsUiState(
    val fetchStatus: OfflineRecordingFetch,
    val deletingEntryPath: String? = null
)

sealed class OfflineRecordingFetch {
    data class Success(
        val fetchedRecordings: List<PolarOfflineRecordingEntry>
    ) : OfflineRecordingFetch()

    data class InProgress(
        val fetchedRecordings: List<PolarOfflineRecordingEntry>
    ) : OfflineRecordingFetch()

    data class Failure(
        val message: String,
        val throwable: Throwable?
    ) : OfflineRecordingFetch()
}

private const val TAG = "ListRecordingsViewModel"

@HiltViewModel
class ListRecordingsViewModel @Inject constructor(
    private val polarDeviceStreamingRepository: PolarDeviceRepository,
    state: SavedStateHandle
) : ViewModel() {
    private val deviceId = state.get<String>("deviceIdFragmentArgument")
        ?: throw Exception("ListRecordingsViewModel requires deviceId")

    var offlineRecordingsUiState by mutableStateOf(
        OfflineRecordingsUiState(
            fetchStatus = OfflineRecordingFetch.InProgress(fetchedRecordings = emptyList())
        )
    )
        private set

    private val devConnectionState = MutableStateFlow(RecordingListDevConnectionState(true))
    private var listOfRecordings: MutableList<PolarOfflineRecordingEntry> = mutableListOf()

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

    fun listOfflineRecordings() {
        Log.d(TAG, "listOfflineRecordings for device $deviceId")
        viewModelScope.launch {
            try {
                listOfRecordings.clear()
                offlineRecordingsUiState = offlineRecordingsUiState.copy(
                    fetchStatus = OfflineRecordingFetch.InProgress(emptyList())
                )

                polarDeviceStreamingRepository.listOfflineRecordings(deviceId)
                    .onCompletion { error ->
                        if (error == null) {
                            Log.d(TAG, "Offline recordings listing completed. Total: ${listOfRecordings.size}")
                            offlineRecordingsUiState = offlineRecordingsUiState.copy(
                                fetchStatus = OfflineRecordingFetch.Success(listOfRecordings.toList())
                            )
                        } else {
                            Log.e(TAG, "Offline recordings listing failed: $error")
                        }
                    }
                    .catch { error ->
                        Log.e(TAG, "Fetch of recordings error: $error")
                        offlineRecordingsUiState = offlineRecordingsUiState.copy(
                            fetchStatus = OfflineRecordingFetch.Failure(
                                message = "Fetching of recordings failed: ${error.message}",
                                throwable = error
                            )
                        )
                    }
                    .collect { entry ->
                        Log.d(TAG, "Found recording: path=${entry.path}, size=${entry.size} bytes, type=${entry.type}, date=${entry.date}")
                        listOfRecordings.add(entry)
                        offlineRecordingsUiState = offlineRecordingsUiState.copy(
                            fetchStatus = OfflineRecordingFetch.InProgress(listOfRecordings.toList())
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error while fetching recordings: $e")
                offlineRecordingsUiState = offlineRecordingsUiState.copy(
                    fetchStatus = OfflineRecordingFetch.Failure(
                        message = "Fetching of recordings failed: ${e.message}",
                        throwable = e
                    )
                )
            }
        }
    }

    fun deleteRecording(entry: PolarOfflineRecordingEntry) {
        Log.d(TAG, "deleteRecording from device $deviceId, path: ${entry.path}")
        viewModelScope.launch {
            offlineRecordingsUiState = offlineRecordingsUiState.copy(
                deletingEntryPath = entry.path
            )

            when (val result = polarDeviceStreamingRepository.deleteRecording(deviceId, entry.path)) {
                is ResultOfRequest.Success -> {
                    Log.d(TAG, "Recording deleted successfully: ${entry.path}")

                    listOfRecordings.remove(entry)
                    offlineRecordingsUiState = offlineRecordingsUiState.copy(
                        fetchStatus = OfflineRecordingFetch.Success(listOfRecordings.toList()),
                        deletingEntryPath = null
                    )
                }
                is ResultOfRequest.Failure -> {
                    Log.e(TAG, "Failed to delete recording: ${result.message}")

                    offlineRecordingsUiState = offlineRecordingsUiState.copy(
                        deletingEntryPath = null
                    )
                }
            }
        }
    }
}
