package com.polar.polarsensordatacollector.ui.devicesettings

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class UserDeviceSettingsViewModel @Inject constructor(
    private val repository: PolarDeviceRepository,
    savedStateHandle: SavedStateHandle,
    private val application: Application
) : ViewModel() {

    companion object {
        const val KEY_DEVICE_ID = "DEVICE_ID"
    }

    private val deviceId: String = savedStateHandle[KEY_DEVICE_ID]
        ?: throw IllegalArgumentException("UserDeviceSettingsViewModel requires DEVICE_ID")

    private val _uiState = MutableStateFlow(UserDeviceSettingsUiState())
    val uiState: StateFlow<UserDeviceSettingsUiState> = _uiState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        loadSettings()
    }

    fun saveSettings(
        deviceLocation: Int,
        usbEnabled: Boolean,
        atdEnabled: Boolean,
        atdSensitivity: Int,
        atdMinDuration: Int
    ) {
        viewModelScope.launch {
            try {
                repository.setUserDeviceLocation(deviceId, deviceLocation)
                repository.setUsbConnectionMode(deviceId, usbEnabled)
                repository.setAutomaticTrainingDetectionSettings(
                    deviceId,
                    atdEnabled,
                    atdSensitivity,
                    atdMinDuration
                )
                _message.value = application.getString(R.string.user_device_settings_saved)
            } catch (e: Exception) {
                _message.value = application.getString(
                    R.string.failed_to_save_settings,
                    e.localizedMessage
                )
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            try {
                val settings = repository.getUserDeviceSettings(deviceId)
                _uiState.value = _uiState.value.copy(
                    deviceLocation = settings.deviceLocation,
                    usbEnabled = settings.usbConnectionMode ?: false,
                    atdEnabled = settings.automaticTrainingDetectionMode ?: false,
                    atdSensitivity = settings.automaticTrainingDetectionSensitivity ?: 0,
                    atdMinDuration = settings.minimumTrainingDurationSeconds ?: 0,
                    isLoading = false
                )
            } catch (e: Exception) {
                _message.value = application.getString(
                    R.string.failed_to_load_settings,
                    e.localizedMessage
                )
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    data class UserDeviceSettingsUiState(
        val deviceLocation: Int? = null,
        val usbEnabled: Boolean = false,
        val atdEnabled: Boolean = false,
        val atdSensitivity: Int = 0,
        val atdMinDuration: Int = 0,
        val isLoading: Boolean = true
    )
}
