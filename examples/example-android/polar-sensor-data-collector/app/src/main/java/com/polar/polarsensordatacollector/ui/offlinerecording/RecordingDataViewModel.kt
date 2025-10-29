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
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarSensorSetting
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

internal data class RecordingDataDevConnectionState(
    val isConnected: Boolean
)

sealed class RecordingDataUiState {
    data object Idle : RecordingDataUiState()
    data class Loading(
        val totalSize: Long,
        val recordingType: PolarBleApi.PolarDeviceDataType,
        val progress: OfflineRecordingProgress? = null
    ) : RecordingDataUiState()
    data class FetchedData(val data: RecordingData) : RecordingDataUiState()
    data object IsDeleting : RecordingDataUiState()
    data object RecordingDeleted : RecordingDataUiState()
    data class Failure(
        val message: String,
        val throwable: Throwable?
    ) : RecordingDataUiState()
}

data class OfflineRecordingProgress(
    val completedBytes: Long,
    val totalBytes: Long,
    val progressPercent: Int
)

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
    internal var devConnectionState: StateFlow<RecordingDataDevConnectionState> =
        _devConnectionState.asStateFlow()

    private val deviceId = state.get<String>("deviceIdFragmentArgument")
        ?: throw Exception("RecordingDataViewModel requires deviceId")
    private val path = state.get<String>("recordingPathFragmentArgument")
        ?: throw Exception("RecordingDataViewModel requires path")

    var recordingDataUiState: RecordingDataUiState by mutableStateOf(
        polarDeviceStreamingRepository.getOfflineEntryFromCache(deviceId, path)?.let { entry ->
            RecordingDataUiState.Loading(entry.size, entry.type)
        } ?: RecordingDataUiState.Loading(0L, PolarBleApi.PolarDeviceDataType.HR)
    )
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

    private fun fetchRecording(deviceId: String, path: String) {
        Log.d(TAG, "fetchRecording from device $deviceId, path: $path")
        viewModelScope.launch {
            polarDeviceStreamingRepository.getOfflineRecordingWithProgress(deviceId, path)
                .collect { resultOfRequest ->
                    recordingDataUiState = when (resultOfRequest) {
                        is ResultOfRequest.Success -> {
                            if (resultOfRequest.progress != null) {
                                val progressInfo = resultOfRequest.progress
                                Log.d(
                                    TAG,
                                    "Progress: ${progressInfo.progressPercent}% (${progressInfo.bytesDownloaded}/${progressInfo.totalBytes} bytes)"
                                )

                                val currentState = recordingDataUiState
                                if (currentState is RecordingDataUiState.Loading) {
                                    currentState.copy(
                                        progress = OfflineRecordingProgress(
                                            completedBytes = progressInfo.bytesDownloaded,
                                            totalBytes = progressInfo.totalBytes,
                                            progressPercent = progressInfo.progressPercent
                                        )
                                    )
                                } else {
                                    RecordingDataUiState.Loading(
                                        totalSize = progressInfo.totalBytes,
                                        recordingType = PolarBleApi.PolarDeviceDataType.ACC,
                                        progress = OfflineRecordingProgress(
                                            completedBytes = progressInfo.bytesDownloaded,
                                            totalBytes = progressInfo.totalBytes,
                                            progressPercent = progressInfo.progressPercent
                                        )
                                    )
                                }
                            } else if (resultOfRequest.value != null) {
                                val offlineRecording = resultOfRequest.value
                                Log.d(TAG, "Offline recording fetch complete")

                                val formattedStartTime = SimpleDateFormat(
                                    "yyyy-MM-dd HH:mm:ss",
                                    Locale.getDefault()
                                ).format(offlineRecording.data.startTime.time)

                                RecordingDataUiState.FetchedData(
                                    RecordingData(
                                        startTime = formattedStartTime,
                                        usedSettings = offlineRecording.data.settings,
                                        uri = offlineRecording.uri,
                                        size = offlineRecording.fileSize,
                                        downloadSpeed = offlineRecording.downLoadSpeed
                                    )
                                )
                            } else {
                                RecordingDataUiState.Failure("Unexpected state: no progress or value", null)
                            }
                        }
                        is ResultOfRequest.Failure -> {
                            Log.e(TAG, "Failed to fetch recording: ${resultOfRequest.message}")
                            RecordingDataUiState.Failure(
                                resultOfRequest.message,
                                resultOfRequest.throwable
                            )
                        }
                    }
                }
        }
    }

    fun deleteRecording() {
        Log.d(TAG, "deleteRecording from device $deviceId, path: $path")
        viewModelScope.launch {
            recordingDataUiState = RecordingDataUiState.IsDeleting
            recordingDataUiState =
                when (val result = polarDeviceStreamingRepository.deleteRecording(deviceId, path)) {
                    is ResultOfRequest.Success -> {
                        Log.d(TAG, "Recording deleted successfully")
                        RecordingDataUiState.RecordingDeleted
                    }

                    is ResultOfRequest.Failure -> {
                        Log.e(TAG, "Failed to delete recording: ${result.message}")
                        RecordingDataUiState.Failure(result.message, result.throwable)
                    }
                }
        }
    }
}