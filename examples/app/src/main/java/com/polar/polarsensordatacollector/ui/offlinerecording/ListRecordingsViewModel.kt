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
import com.polar.sdk.api.model.PolarOfflineRecordingEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal data class RecordingListDevConnectionState(
    val isConnected: Boolean
)

data class OfflineRecordingsUiState(
    val fetchStatus: OfflineRecordingFetch
)

sealed class OfflineRecordingFetch {
    class Success(
        val fetchedRecordings: List<PolarOfflineRecordingEntry>
    ) : OfflineRecordingFetch()

    class InProgress(val fetchedRecordings: List<PolarOfflineRecordingEntry>) : OfflineRecordingFetch()
    class Failure(
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
    private val deviceId = state.get<String>("deviceIdFragmentArgument") ?: throw Exception("ListRecordingsViewModel model requires deviceId")

    var offlineRecordingsUiState by mutableStateOf(OfflineRecordingsUiState(OfflineRecordingFetch.InProgress(fetchedRecordings = emptyList())))
        private set

    private val _devConnectionState = MutableStateFlow(RecordingListDevConnectionState(true))
    internal var devConnectionState: StateFlow<RecordingListDevConnectionState> = _devConnectionState.asStateFlow()

    private var listOfRecordings: MutableList<PolarOfflineRecordingEntry> = mutableListOf()

    var recordingDataUiState: RecordingDataUiState by mutableStateOf(RecordingDataUiState.IsFetching)
        private set

    init {
        viewModelScope.launch {
            polarDeviceStreamingRepository.deviceConnectionStatus
                .collect { connectionStatus ->
                    when (connectionStatus) {
                        is DeviceConnectionState.DeviceConnected -> {
                            _devConnectionState.update {
                                it.copy(isConnected = true)
                            }
                        }
                        is DeviceConnectionState.DeviceConnecting,
                        is DeviceConnectionState.DeviceDisconnecting,
                        is DeviceConnectionState.DeviceNotConnected -> {
                            _devConnectionState.update {
                                it.copy(isConnected = false)
                            }
                        }
                    }
                }
        }
    }

    fun listOfflineRecordings() {
        Log.d(TAG, "listFiles $deviceId")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                polarDeviceStreamingRepository.listOfflineRecordings(deviceId)
                    .onStart {
                        listOfRecordings.clear()
                        withContext(Dispatchers.Main) {
                            offlineRecordingsUiState = OfflineRecordingsUiState(fetchStatus = OfflineRecordingFetch.InProgress(listOfRecordings.toList()))
                        }
                    }
                    .onCompletion {
                        withContext(Dispatchers.Main) {
                            offlineRecordingsUiState = OfflineRecordingsUiState(fetchStatus = OfflineRecordingFetch.Success(listOfRecordings.toList()))
                        }
                    }.collect {
                        listOfRecordings.add(it)
                        withContext(Dispatchers.Main) {
                            offlineRecordingsUiState = OfflineRecordingsUiState(fetchStatus = OfflineRecordingFetch.InProgress(listOfRecordings.toList()))
                        }
                    }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Fetch of recordings error: $e")
                    offlineRecordingsUiState = OfflineRecordingsUiState(fetchStatus = OfflineRecordingFetch.Failure(message = "Fetching of recordings failed", throwable = e))
                }
            }
        }
    }

    fun deleteRecording(entry: PolarOfflineRecordingEntry) {
        Log.d(TAG, "deleteRecording from device $deviceId")
        viewModelScope.launch(Dispatchers.IO) {
            recordingDataUiState = RecordingDataUiState.IsDeleting
            recordingDataUiState = when (val result = polarDeviceStreamingRepository.deleteRecording(deviceId, entry.path)) {
                is ResultOfRequest.Success -> RecordingDataUiState.RecordingDeleted
                is ResultOfRequest.Failure -> RecordingDataUiState.Failure(result.message, result.throwable)
            }
        }
        listOfRecordings.remove(entry)
        offlineRecordingsUiState = OfflineRecordingsUiState(fetchStatus = OfflineRecordingFetch.Success(listOfRecordings.toList()))
    }
}