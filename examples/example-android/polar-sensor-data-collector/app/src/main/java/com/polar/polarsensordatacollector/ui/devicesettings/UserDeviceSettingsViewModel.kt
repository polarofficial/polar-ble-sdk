package com.polar.polarsensordatacollector.ui.devicesettings

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.sdk.api.errors.PolarBleSdkInternalException
import com.polar.sdk.api.errors.PolarDeviceNotConnected
import com.polar.sdk.api.errors.PolarDeviceNotFound
import com.polar.sdk.api.errors.PolarServiceNotAvailable
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
        const val KEY_IDENTIFIER = "IDENTIFIER"
    }

    private val identifier: String = savedStateHandle[KEY_IDENTIFIER]
        ?: throw IllegalArgumentException("UserDeviceSettingsViewModel requires IDENTIFIER")

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
        atdMinDuration: Int,
        telemetryEnabled: Boolean,
        autosFilesEnabled: Boolean
    ) {
        viewModelScope.launch {
            try {
                repository.setUserDeviceLocation(identifier, deviceLocation)
                repository.setUsbConnectionMode(identifier, usbEnabled)
                repository.setAutomaticTrainingDetectionSettings(
                    identifier,
                    atdEnabled,
                    atdSensitivity,
                    atdMinDuration
                )
                repository.setTelemetryEnabled(identifier, telemetryEnabled)
                repository.setDaylightSavingTime(identifier)
                repository.setAutosFilesEnabled(identifier, autosFilesEnabled)
                _message.value = application.getString(R.string.user_device_settings_saved)
            } catch (e: Exception) {
                _message.value = application.getString(
                    R.string.failed_to_save_settings,
                    describeUserDeviceSettingsError(e)
                )
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            try {
                val settings = repository.getUserDeviceSettings(identifier)
                _uiState.value = _uiState.value.copy(
                    deviceLocation = settings.deviceLocation,
                    usbEnabled = settings.usbConnectionMode ?: false,
                    atdEnabled = settings.automaticTrainingDetectionMode ?: false,
                    atdSensitivity = settings.automaticTrainingDetectionSensitivity ?: 0,
                    atdMinDuration = settings.minimumTrainingDurationSeconds ?: 0,
                    telemetryEnabled = settings.telemetryEnabled ?: false,
                    autosFilesEnabled = settings.autosFilesEnabled ?: false,
                    isLoading = false
                )
            } catch (e: Exception) {
                _message.value = application.getString(
                    R.string.failed_to_load_settings,
                    describeUserDeviceSettingsError(e)
                )
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private fun describeUserDeviceSettingsError(error: Throwable): String {
        return when (error) {
            is PolarDeviceNotFound -> application.getString(R.string.user_device_settings_device_session_not_found)
            is PolarDeviceNotConnected -> application.getString(R.string.user_device_settings_device_not_connected)
            is PolarServiceNotAvailable -> application.getString(R.string.user_device_settings_service_not_found)
            is PolarBleSdkInternalException -> error.message
                ?: application.getString(R.string.user_device_settings_file_not_readable)
            else -> error.localizedMessage ?: application.getString(R.string.unknown_error)
        }
    }

    data class UserDeviceSettingsUiState(
        val deviceLocation: Int? = null,
        val usbEnabled: Boolean = false,
        val atdEnabled: Boolean = false,
        val atdSensitivity: Int = 0,
        val atdMinDuration: Int = 0,
        val telemetryEnabled: Boolean = false,
        val autosFilesEnabled: Boolean = false,
        val isLoading: Boolean = true
    )
}
