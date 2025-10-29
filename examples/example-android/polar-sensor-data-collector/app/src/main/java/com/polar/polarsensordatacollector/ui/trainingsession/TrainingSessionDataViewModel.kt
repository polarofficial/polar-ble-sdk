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
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionFetchResult
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionProgress
import dagger.hilt.android.lifecycle.HiltViewModel
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
    data class Fetching(val progress: PolarTrainingSessionProgress) : TrainingSessionDataUiState()
    data class FetchedData(val data: PolarTrainingSession) : TrainingSessionDataUiState()
    data class Failure(
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

        fetchTrainingSessionWithProgress(deviceId, path)
    }

    private fun fetchTrainingSessionWithProgress(deviceId: String, path: String) {
        Log.d(TAG, "fetchTrainingSessionWithProgress path: $path, deviceId: $deviceId")
        viewModelScope.launch {
            polarDeviceStreamingRepository.getTrainingSessionWithProgress(deviceId, path)
                .collect { resultOfRequest ->
                    trainingSessionDataUiState = when (resultOfRequest) {
                        is ResultOfRequest.Success -> {
                            when (val fetchResult = resultOfRequest.value) {
                                is PolarTrainingSessionFetchResult.Progress -> {
                                    Log.d(TAG, "Progress: ${fetchResult.progress.progressPercent}%")
                                    TrainingSessionDataUiState.Fetching(fetchResult.progress)
                                }
                                is PolarTrainingSessionFetchResult.Complete -> {
                                    Log.d(TAG, "Training session fetch complete")
                                    TrainingSessionDataUiState.FetchedData(fetchResult.session)
                                }
                                null -> {
                                    Log.e(TAG, "Received null result from fetch")
                                    TrainingSessionDataUiState.Failure(
                                        "Training session fetch returned empty result",
                                        null
                                    )
                                }
                            }
                        }
                        is ResultOfRequest.Failure -> {
                            Log.e(TAG, "Failed to fetch training session: ${resultOfRequest.message}")
                            TrainingSessionDataUiState.Failure(
                                resultOfRequest.message,
                                resultOfRequest.throwable
                            )
                        }
                    }
                }
        }
    }
}