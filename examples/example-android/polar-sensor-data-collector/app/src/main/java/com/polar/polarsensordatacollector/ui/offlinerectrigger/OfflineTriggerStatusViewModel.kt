package com.polar.polarsensordatacollector.ui.offlinerectrigger

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.repository.ResultOfRequest
import com.polar.polarsensordatacollector.ui.utils.MessageUiState
import com.polar.sdk.api.model.PolarOfflineRecordingTrigger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class OfflineRecTriggerStatusUiState {
    class CurrentStatus(val triggerStatus: PolarOfflineRecordingTrigger) : OfflineRecTriggerStatusUiState()
    object FetchingStatus : OfflineRecTriggerStatusUiState()
    class FetchingFailed(val message: String) : OfflineRecTriggerStatusUiState()
}

@HiltViewModel
class OfflineTriggerStatusViewModel @Inject constructor(
    private val polarDeviceStreamingRepository: PolarDeviceRepository,
    state: SavedStateHandle
) : ViewModel() {
    companion object {
        private const val TAG = "OfflineTriggerStatusViewModel"
    }

    private val identifier = state.get<String>(OFFLINE_REC_TRIG_KEY_DEVICE_ID) ?: throw Exception("Offline recording viewModel must know the identifier")

    private val _uiOfflineRecTriggerStatus = MutableStateFlow<OfflineRecTriggerStatusUiState>(OfflineRecTriggerStatusUiState.FetchingStatus)
    internal val uiOfflineRecTriggerStatus: StateFlow<OfflineRecTriggerStatusUiState> = _uiOfflineRecTriggerStatus.asStateFlow()

    private val _uiShowError = MutableSharedFlow<MessageUiState>(extraBufferCapacity = 1)
    internal val uiShowError: SharedFlow<MessageUiState> = _uiShowError.asSharedFlow()

    private val _uiShowInfo = MutableSharedFlow<MessageUiState>(extraBufferCapacity = 1)
    internal val uiShowInfo: SharedFlow<MessageUiState> = _uiShowInfo.asSharedFlow()

    init {
        getOfflineRecordingTriggerStatus()
        viewModelScope.launch {
            polarDeviceStreamingRepository.triggerState
                .collect { triggerState ->
                    if (triggerState.identifier == identifier) {
                        triggerState.triggerStatus?.let { triggersStatus ->
                            _uiOfflineRecTriggerStatus.update {
                                OfflineRecTriggerStatusUiState.CurrentStatus(triggersStatus)
                            }
                        }
                    }
                }
        }
    }

    private fun showError(errorDescription: String, errorThrowable: Throwable? = null) {
        Log.e(TAG, "Show error: $errorDescription. Error reason $errorThrowable")
        _uiShowError.tryEmit(MessageUiState(header = errorDescription, description = errorThrowable?.message))
    }

    private fun showInfo(header: String, description: String = "", timeout: Long? = null) {
        _uiShowInfo.tryEmit(MessageUiState(header, description, timeout))
    }

    internal fun getOfflineRecordingTriggerStatus() {
        Log.d(TAG, "getOfflineRecordingTriggerStatus()")
        viewModelScope.launch(Dispatchers.IO) {
            _uiOfflineRecTriggerStatus.update {
                OfflineRecTriggerStatusUiState.FetchingStatus
            }
            when (val result = polarDeviceStreamingRepository.getOfflineRecordingTriggerStatus(identifier)) {
                is ResultOfRequest.Success -> {
                    result.value?.let {
                        _uiOfflineRecTriggerStatus.update {
                            OfflineRecTriggerStatusUiState.CurrentStatus(result.value)
                        }
                    }
                }
                is ResultOfRequest.Failure -> {
                    _uiOfflineRecTriggerStatus.update {
                        val errorString = "${result.message}\nError reason ${result.throwable?.message}"
                        OfflineRecTriggerStatusUiState.FetchingFailed(errorString)
                    }
                }
            }
        }
    }
}


