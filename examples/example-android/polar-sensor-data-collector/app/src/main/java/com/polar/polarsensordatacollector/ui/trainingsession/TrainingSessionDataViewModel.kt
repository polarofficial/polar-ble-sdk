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
import com.polar.sdk.api.model.trainingsession.PolarTrainingSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class TrainingSessionDataDevConnectionState(
    val isConnected: Boolean
)

sealed class TrainingSessionDataUiState {
    data object IsFetching : TrainingSessionDataUiState()
    class FetchedData(val data: PolarTrainingSession) : TrainingSessionDataUiState()
    class Failure(
        val message: String,
        val throwable: Throwable?
    ) : TrainingSessionDataUiState()
}


private const val TAG: String = "TrainingSessionDataViewModel"

@HiltViewModel
class TrainingSessionDataViewModel @Inject constructor(
    private val polarDeviceStreamingRepository: PolarDeviceRepository,
    state: SavedStateHandle
) : ViewModel() {

    private val _devConnectionState = MutableStateFlow(TrainingSessionDataDevConnectionState(true))
    internal var devConnectionState: StateFlow<TrainingSessionDataDevConnectionState> = _devConnectionState.asStateFlow()

    private val deviceId = state.get<String>("deviceIdFragmentArgument") ?: throw Exception("TrainingSessionDataView model requires deviceId")
    private val path = state.get<String>("trainingSessionPathFragmentArgument") ?: throw Exception("TrainingSessionDataView model requires path")

    var trainingSessionDataUiState: TrainingSessionDataUiState by mutableStateOf(TrainingSessionDataUiState.IsFetching)
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

        fetchTrainingSession(deviceId, path)
    }

    private fun fetchTrainingSession(deviceId: String, path: String) {
        Log.d(TAG, "fetchTrainingSession path: $path, deviceId: $deviceId")
        viewModelScope.launch(Dispatchers.IO) {
            trainingSessionDataUiState = when (val trainingSession = polarDeviceStreamingRepository.getTrainingSession(deviceId, path)) {
                is ResultOfRequest.Success -> {
                    if (trainingSession.value != null) {
                        TrainingSessionDataUiState.FetchedData(trainingSession.value)
                    } else {
                        TrainingSessionDataUiState.Failure("fetch TrainingSession responded with empty data", null)
                    }
                }

                is ResultOfRequest.Failure -> {
                    TrainingSessionDataUiState.Failure(trainingSession.message, trainingSession.throwable)
                }
            }
        }
    }
}

