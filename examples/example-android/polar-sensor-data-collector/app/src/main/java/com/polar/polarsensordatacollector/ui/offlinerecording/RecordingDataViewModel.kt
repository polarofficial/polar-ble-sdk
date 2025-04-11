package com.polar.polarsensordatacollector.ui.offlinerecording

import android.net.Uri
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
import com.polar.sdk.api.model.PolarOfflineRecordingData
import com.polar.sdk.api.model.PolarSensorSetting
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class RecordingDataDevConnectionState(
    val isConnected: Boolean
)

sealed class RecordingDataUiState {
    object IsFetching : RecordingDataUiState()
    object IsDeleting : RecordingDataUiState()
    object RecordingDeleted : RecordingDataUiState()
    class FetchedData(val data: RecordingData) : RecordingDataUiState()
    class Failure(
        val message: String,
        val throwable: Throwable?
    ) : RecordingDataUiState()
}

data class RecordingData(
    val startTime: String,
    val usedSettings: PolarSensorSetting?,
    val uri: Uri,
    val size: Long,
    val downloadSpeed: Double
)

private const val TAG: String = "RecordingDataViewModel"

@HiltViewModel
class RecordingDataViewModel @Inject constructor(
    private val polarDeviceStreamingRepository: PolarDeviceRepository,
    state: SavedStateHandle
) : ViewModel() {

    private val _devConnectionState = MutableStateFlow(RecordingDataDevConnectionState(true))
    internal var devConnectionState: StateFlow<RecordingDataDevConnectionState> = _devConnectionState.asStateFlow()

    private val deviceId = state.get<String>("deviceIdFragmentArgument") ?: throw Exception("RecordingDataView model requires deviceId")
    private val path = state.get<String>("recordingPathFragmentArgument") ?: throw Exception("RecordingDataView model requires path")

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

        fetchRecording(deviceId, path)
    }

    private fun fetchRecording(deviceId: String, entry: String) {
        Log.d(TAG, "fetchRecording $deviceId")
        viewModelScope.launch(Dispatchers.IO) {
            when (val offlineRecording = polarDeviceStreamingRepository.getOfflineRecording(deviceId, entry)) {
                is ResultOfRequest.Success -> {
                    if (offlineRecording.value != null) {
                        updateRecordingDataUiState(offlineRecording.value.data, offlineRecording.value.uri, offlineRecording.value.fileSize, offlineRecording.value.downLoadSpeed)
                    } else {
                        recordingDataUiState = RecordingDataUiState.Failure("fetch recording responded with empty data", null)
                    }
                }
                is ResultOfRequest.Failure -> {
                    recordingDataUiState = RecordingDataUiState.Failure(offlineRecording.message, offlineRecording.throwable)
                }
            }
        }
    }

    fun deleteRecording() {
        Log.d(TAG, "deleteRecording from device $deviceId")
        viewModelScope.launch(Dispatchers.IO) {
            recordingDataUiState = RecordingDataUiState.IsDeleting
            recordingDataUiState = when (val result = polarDeviceStreamingRepository.deleteRecording(deviceId, path)) {
                is ResultOfRequest.Success -> RecordingDataUiState.RecordingDeleted
                is ResultOfRequest.Failure -> RecordingDataUiState.Failure(result.message, result.throwable)
            }
        }
    }

    private fun updateRecordingDataUiState(offlineRecording: PolarOfflineRecordingData, uri: Uri, fileSize: Long, downloadSpeed: Double) {
        val recordingData = RecordingData(
            startTime = offlineRecording.startTime.time.toString(),
            usedSettings = offlineRecording.settings,
            uri = uri,
            size = fileSize,
            downloadSpeed = downloadSpeed
        )
        recordingDataUiState = RecordingDataUiState.FetchedData(data = recordingData)
    }
}

