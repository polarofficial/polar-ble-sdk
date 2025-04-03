package com.polar.polarsensordatacollector.ui.logging

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.repository.ResultOfRequest
import com.polar.polarsensordatacollector.ui.landing.ONLINE_OFFLINE_KEY_DEVICE_ID
import com.polar.sdk.api.model.Errorlog
import com.polar.sdk.api.model.LogConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class LoggingViewModel @Inject constructor(
    private val polarDeviceStreamingRepository: PolarDeviceRepository,
    state: SavedStateHandle
) : ViewModel() {
    companion object {
        private const val TAG = "LoggingViewModel"
    }

    private val deviceId = state.get<String>(ONLINE_OFFLINE_KEY_DEVICE_ID) ?: throw Exception("Logging viewModel must know the deviceId")

    private val _uiLogConfigState = MutableStateFlow(LogConfig())
    val uiLogConfigState: StateFlow<LogConfig> = _uiLogConfigState.asStateFlow()

    private val _errorlogLiveData = MutableLiveData<Errorlog?>()
    val errorlogLiveData: LiveData<Errorlog?> = _errorlogLiveData

    init {
        viewModelScope.launch {
            when (val result = polarDeviceStreamingRepository.getLogConfig(deviceId)) {
                is ResultOfRequest.Success -> {
                    _uiLogConfigState.value = result.value ?: LogConfig()
                    updateLogConfigState(_uiLogConfigState.value)
                }
                is ResultOfRequest.Failure -> {
                    Log.w(TAG, "Failed to read log config")
                }
            }
        }
    }

    fun ohrLogging() {
        viewModelScope.launch(Dispatchers.IO) {
            polarDeviceStreamingRepository.setLogConfig(
                deviceId,
                _uiLogConfigState.value.copy(ohrLogEnabled = !_uiLogConfigState.value.ohrLogEnabled!!)
            )
            updateLogConfigState(_uiLogConfigState.value.copy(ohrLogEnabled = !_uiLogConfigState.value.ohrLogEnabled!!))
        }
    }

    fun ppiLogging() {
        viewModelScope.launch(Dispatchers.IO) {
            polarDeviceStreamingRepository.setLogConfig(
                deviceId,
                _uiLogConfigState.value.copy(ppiLogEnabled = !_uiLogConfigState.value.ppiLogEnabled!!)
            )
            updateLogConfigState(_uiLogConfigState.value.copy(ppiLogEnabled = !_uiLogConfigState.value.ppiLogEnabled!!))
        }
    }

    fun accLogging() {
        viewModelScope.launch(Dispatchers.IO) {
            polarDeviceStreamingRepository.setLogConfig(
                deviceId,
                _uiLogConfigState.value.copy(accelerationLogEnabled = !_uiLogConfigState.value.accelerationLogEnabled!!)
            )
            updateLogConfigState(_uiLogConfigState.value.copy(accelerationLogEnabled = !_uiLogConfigState.value.accelerationLogEnabled!!))
        }
    }

    fun skinTempLogging() {
        viewModelScope.launch(Dispatchers.IO) {
            polarDeviceStreamingRepository.setLogConfig(
                deviceId,
                _uiLogConfigState.value.copy(skinTemperatureLogEnabled = !_uiLogConfigState.value.skinTemperatureLogEnabled!!)
            )
            updateLogConfigState(_uiLogConfigState.value.copy(skinTemperatureLogEnabled = !_uiLogConfigState.value.skinTemperatureLogEnabled!!))
        }
    }

    fun metLogging() {
        viewModelScope.launch(Dispatchers.IO) {
            polarDeviceStreamingRepository.setLogConfig(
                deviceId,
                _uiLogConfigState.value.copy(metLogEnabled = !_uiLogConfigState.value.metLogEnabled!!)
            )
            updateLogConfigState(_uiLogConfigState.value.copy(metLogEnabled = !_uiLogConfigState.value.metLogEnabled!!))
        }
    }

    fun caloriesLogging() {
        viewModelScope.launch(Dispatchers.IO) {
            polarDeviceStreamingRepository.setLogConfig(
                deviceId,
                _uiLogConfigState.value.copy(caloriesLogEnabled = !_uiLogConfigState.value.caloriesLogEnabled!!)
            )
            updateLogConfigState(_uiLogConfigState.value.copy(caloriesLogEnabled = !_uiLogConfigState.value.caloriesLogEnabled!!))
        }
    }

    fun sleepLogging() {
        viewModelScope.launch(Dispatchers.IO) {
            polarDeviceStreamingRepository.setLogConfig(
                deviceId,
                _uiLogConfigState.value.copy(sleepLogEnabled = !_uiLogConfigState.value.sleepLogEnabled!!)
            )
            updateLogConfigState(_uiLogConfigState.value.copy(sleepLogEnabled = !_uiLogConfigState.value.sleepLogEnabled!!))
        }
    }

    fun fetchErrorLog() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = polarDeviceStreamingRepository.fetchErrorLog(deviceId)
                if (result is ResultOfRequest.Success) {
                    _errorlogLiveData.postValue(result.value)
                } else if (result is ResultOfRequest.Failure) {
                    Log.w(TAG, "Failed to fetch errorlog: ${result.message}", result.throwable)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching errorlog", e)
            }
        }
    }

    private fun updateLogConfigState(logConfig: LogConfig) {
        _uiLogConfigState.update {
            it.copy(
                ohrLogEnabled = logConfig.ohrLogEnabled,
                ppiLogEnabled = logConfig.ppiLogEnabled,
                accelerationLogEnabled = logConfig.accelerationLogEnabled,
                skinTemperatureLogEnabled = logConfig.skinTemperatureLogEnabled,
                metLogEnabled = logConfig.metLogEnabled,
                caloriesLogEnabled = logConfig.caloriesLogEnabled,
                sleepLogEnabled = logConfig.sleepLogEnabled
            )
        }
    }
}