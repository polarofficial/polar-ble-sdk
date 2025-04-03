package com.polar.polarsensordatacollector.ui.offlinerectrigger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polar.polarsensordatacollector.repository.DeviceConnectionState
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class OffTrigDevConnectionUiState(
    val isConnected: Boolean
)

@HiltViewModel
class OfflineTriggerViewModel @Inject constructor(
    private val polarDeviceStreamingRepository: PolarDeviceRepository
) : ViewModel() {
    companion object {
        private const val TAG = "OfflineTriggerViewModel"
    }

    private val _uiConnectionState = MutableStateFlow(OffTrigDevConnectionUiState(true))
    internal val uiConnectionState: StateFlow<OffTrigDevConnectionUiState> = _uiConnectionState.asStateFlow()

    init {
        viewModelScope.launch {
            polarDeviceStreamingRepository.deviceConnectionStatus
                .collect { connectionStatus ->
                    when (connectionStatus) {
                        is DeviceConnectionState.DeviceConnected -> {
                            _uiConnectionState.update {
                                it.copy(isConnected = true)
                            }
                        }
                        is DeviceConnectionState.DeviceConnecting,
                        is DeviceConnectionState.DeviceDisconnecting,
                        is DeviceConnectionState.DeviceNotConnected -> {
                            _uiConnectionState.update {
                                it.copy(isConnected = false)
                            }
                        }
                    }
                }
        }
    }

}


