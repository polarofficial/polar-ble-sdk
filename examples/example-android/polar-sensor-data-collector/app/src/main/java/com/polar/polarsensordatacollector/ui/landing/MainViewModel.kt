package com.polar.polarsensordatacollector.ui.landing

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polar.androidcommunications.api.ble.model.gatt.client.ChargeState
import com.polar.androidcommunications.api.ble.model.gatt.client.PowerSourceState
import com.polar.androidcommunications.api.ble.model.gatt.client.BatteryPresentState
import com.polar.androidcommunications.api.ble.model.gatt.client.PowerSourcesState
import com.polar.polarsensordatacollector.model.Device
import com.polar.polarsensordatacollector.repository.DeviceConnectionState
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.sdk.api.model.PolarDeviceInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.core.Flowable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal data class DeviceConnectionUiState(
    val deviceId: String = "",
    val state: MainViewModel.DeviceConnectionStates = MainViewModel.DeviceConnectionStates.NOT_CONNECTED,
)

data class OfflineRecordingAvailabilityUiState(
    val deviceId: String = "",
    val isAvailable: Boolean = false
)

data class DeviceInformationUiState(
    val deviceId: String = "",
    val firmwareVersion: String = "",
    val batteryLevel: Int? = null,
    val batteryChargeState: ChargeState = ChargeState.UNKNOWN,
    val powerSourcesState: PowerSourcesState = PowerSourcesState(
        batteryPresent = BatteryPresentState.UNKNOWN,
        wiredExternalPowerConnected = PowerSourceState.UNKNOWN,
        wirelessExternalPowerConnected = PowerSourceState.UNKNOWN
    )
)

/**
 * UiState for available stream settings
 */

@HiltViewModel
class MainViewModel @Inject constructor(
    private val polarDeviceStreamingRepository: PolarDeviceRepository,
) : ViewModel() {
    companion object {
        private const val TAG = "MainViewModel"
    }

    var selectedDevice: Device? = null

    private val _uiConnectionState = MutableStateFlow(DeviceConnectionUiState(state = DeviceConnectionStates.NOT_CONNECTED))
    internal val uiConnectionState: StateFlow<DeviceConnectionUiState> = _uiConnectionState.asStateFlow()

    private val _uiDeviceInformationState = MutableStateFlow(DeviceInformationUiState())
    val uiDeviceInformationState: StateFlow<DeviceInformationUiState> = _uiDeviceInformationState.asStateFlow()

    private val _uiOfflineRecordingState = MutableStateFlow(OfflineRecordingAvailabilityUiState())
    val uiOfflineRecordingState: StateFlow<OfflineRecordingAvailabilityUiState> = _uiOfflineRecordingState.asStateFlow()

    enum class DeviceConnectionStates {
        PHONE_BLE_OFF, NOT_CONNECTED, CONNECTING_TO_SELECTED_DEVICE, CONNECTED, DISCONNECTING_FROM_SELECTED_DEVICE
    }

    init {
        viewModelScope.launch {
            polarDeviceStreamingRepository.isPhoneBlePowerOn
                .collect { isPhoneBlePowerOn ->
                    if (!isPhoneBlePowerOn) {
                        updateDeviceConnectionUiState("", DeviceConnectionStates.PHONE_BLE_OFF)
                    } else {
                        updateDeviceConnectionUiState("", DeviceConnectionStates.NOT_CONNECTED)
                    }
                }
        }

        viewModelScope.launch {
            polarDeviceStreamingRepository.deviceConnectionStatus
                .collect { deviceConnectionState ->
                    when (deviceConnectionState) {
                        is DeviceConnectionState.DeviceConnected -> {
                            Log.d(TAG, "Connected: " + deviceConnectionState.deviceId)
                            updateDeviceConnectionUiState(deviceConnectionState.deviceId, DeviceConnectionStates.CONNECTED)
                        }
                        is DeviceConnectionState.DeviceConnecting -> {
                            Log.d(TAG, "Connecting: " + deviceConnectionState.deviceId)
                            updateDeviceConnectionUiState(deviceConnectionState.deviceId, DeviceConnectionStates.CONNECTING_TO_SELECTED_DEVICE)

                        }
                        is DeviceConnectionState.DeviceDisconnecting -> {
                            //NOP
                        }
                        is DeviceConnectionState.DeviceNotConnected -> {
                            Log.d(TAG, "Not connected: " + deviceConnectionState.deviceId)
                            updateDeviceConnectionUiState(deviceConnectionState.deviceId, DeviceConnectionStates.NOT_CONNECTED)
                            deviceDisconnected(deviceConnectionState.deviceId)
                        }
                    }
                }
        }

        viewModelScope.launch {
            polarDeviceStreamingRepository.deviceInformation
                .collect { deviceInformation ->
                    updateDeviceBatteryUiState(deviceId = deviceInformation.deviceId, batteryLevel = deviceInformation.batteryLevel)
                    updateDeviceBatteryChargeStatusUiState(deviceId = deviceInformation.deviceId, chargeStatus = deviceInformation.batteryChargingStatus)
                    updateDeviceBatteryPowerSourceStateUiState(deviceId = deviceInformation.deviceId, powerSourcesState = deviceInformation.powerSourcesState)
                    updateDeviceFirmwareVersionUiState(deviceId = deviceInformation.deviceId, firmwareVersion = deviceInformation.firmwareVersion)
                }
        }

        /*viewModelScope.launch {
            polarDeviceStreamingRepository.offlineRecordingStates
                .collect { offlineRecInfo ->
                    offlineRecInfo?.let {
                        updateOfflineRecordingUiState(deviceId = it.deviceId, isAvailable = it.isAvailable)
                    }
                }
        }*/

        viewModelScope.launch {
            polarDeviceStreamingRepository.availableFeatures
                .collect { deviceStreamsAvailable ->
                    if (deviceStreamsAvailable.availableOfflineFeatures.any { it.value == true }) {
                        updateOfflineRecordingUiState(deviceStreamsAvailable.deviceId, isAvailable = true)
                    }
                }
        }
    }

    fun connectToDevice(device: Device) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            polarDeviceStreamingRepository.connectToDevice(device.deviceId)
            selectedDevice = device
        }
    }

    fun disconnectFromDevice(device: Device) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            polarDeviceStreamingRepository.disconnectFromDevice(device.deviceId)
        }
    }

    fun searchForDevice(withPrefix: String?): Flowable<PolarDeviceInfo> {
        return polarDeviceStreamingRepository.searchForDevice(withPrefix)
    }

    fun isBluetoothEnabled(): Boolean {
        return polarDeviceStreamingRepository.isPhoneBlePowerOn.value
    }

    private fun deviceDisconnected(deviceId: String) {
        updateOfflineRecordingUiState(deviceId = deviceId, isAvailable = false)
        updateDeviceBatteryUiState(deviceId = deviceId, null)
        updateDeviceFirmwareVersionUiState(deviceId = deviceId, "")
    }

    private fun updateDeviceConnectionUiState(deviceId: String, newState: DeviceConnectionStates) {
        Log.d(TAG, "updateOperationUiState() state change to $newState")
        _uiConnectionState.update {
            DeviceConnectionUiState(deviceId = deviceId, state = newState)
        }
    }

    private fun updateDeviceBatteryUiState(deviceId: String, batteryLevel: Int?) {
        Log.d(TAG, "updateDeviceInformationUiState() batteryLevel: $batteryLevel ")
        _uiDeviceInformationState.update {
            it.copy(deviceId = deviceId, batteryLevel = batteryLevel)
        }
    }

    private fun updateDeviceBatteryChargeStatusUiState(deviceId: String, chargeStatus: ChargeState) {
        Log.d(TAG, "updateDeviceBatteryChargeStatusUiState() chargeStatus $chargeStatus")
        _uiDeviceInformationState.update {
            it.copy(deviceId = deviceId, batteryChargeState = chargeStatus)
        }
    }

    private fun updateDeviceBatteryPowerSourceStateUiState(deviceId: String, powerSourcesState: PowerSourcesState) {
        Log.d(TAG, "updateDeviceBatteryPowerSourceStateUiState() powerSourcesState $powerSourcesState")
        _uiDeviceInformationState.update {
            it.copy(deviceId = deviceId, powerSourcesState = powerSourcesState)
        }
    }

    private fun updateDeviceFirmwareVersionUiState(deviceId: String, firmwareVersion: String = "") {
        Log.d(TAG, "updateDeviceInformationUiState() firmwareVersion $firmwareVersion")
        _uiDeviceInformationState.update {
            it.copy(deviceId = deviceId, firmwareVersion = firmwareVersion)
        }
    }

    private fun updateOfflineRecordingUiState(deviceId: String, isAvailable: Boolean = false) {
        _uiOfflineRecordingState.update {
            it.copy(deviceId = deviceId, isAvailable = isAvailable)
        }
    }

    public override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel onCleared()")
        polarDeviceStreamingRepository.sdkShutDown()
    }
}