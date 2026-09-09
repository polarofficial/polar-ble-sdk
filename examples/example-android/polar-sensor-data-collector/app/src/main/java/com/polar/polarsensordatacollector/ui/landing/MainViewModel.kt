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
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository.SdkFeaturesReadyEvent
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHrBroadcastData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal data class DeviceConnectionUiState(
    val identifier: String = "",
    val state: MainViewModel.DeviceConnectionStates = MainViewModel.DeviceConnectionStates.NOT_CONNECTED,
)

data class OfflineRecordingAvailabilityUiState(
    val identifier: String = "",
    val isAvailable: Boolean = false
)

data class OfflineRecordingV2AvailabilityUiState(
    val identifier: String = "",
    val isAvailable: Boolean = false
)

data class DeviceInformationUiState(
    val identifier: String = "",
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

    private val _uiOfflineRecordingV2State =
        MutableStateFlow(OfflineRecordingV2AvailabilityUiState())

    val uiOfflineRecordingV2State: StateFlow<OfflineRecordingV2AvailabilityUiState> =
        _uiOfflineRecordingV2State.asStateFlow()

    val uiSdkFeaturesReadyState: StateFlow<SdkFeaturesReadyEvent> =
        polarDeviceStreamingRepository.sdkFeaturesReady

    val disconnectGuidance: SharedFlow<String> = polarDeviceStreamingRepository.disconnectGuidance

    private val _hrData = MutableStateFlow<PolarHrBroadcastData?>(null)
    val hrData: StateFlow<PolarHrBroadcastData?> = _hrData.asStateFlow()

    private var hrJob: kotlinx.coroutines.Job? = null

    private val _removeOnlineOfflineFragments = MutableSharedFlow<Unit>()
    val removeOnlineOfflineFragments: SharedFlow<Unit> = _removeOnlineOfflineFragments.asSharedFlow()

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
                            Log.d(TAG, "Connected: " + deviceConnectionState.identifier)
                            updateDeviceConnectionUiState(deviceConnectionState.identifier, DeviceConnectionStates.CONNECTED)
                            checkOfflineExerciseV2Support(deviceConnectionState.identifier)
                        }
                        is DeviceConnectionState.DeviceConnecting -> {
                            Log.d(TAG, "Connecting: " + deviceConnectionState.identifier)
                            updateDeviceConnectionUiState(deviceConnectionState.identifier, DeviceConnectionStates.CONNECTING_TO_SELECTED_DEVICE)
                        }
                        is DeviceConnectionState.DeviceDisconnecting -> {
                        }
                        is DeviceConnectionState.DeviceNotConnected -> {
                            Log.d(TAG, "Not connected: " + deviceConnectionState.identifier)
                            updateDeviceConnectionUiState(deviceConnectionState.identifier, DeviceConnectionStates.NOT_CONNECTED)
                            deviceDisconnected(deviceConnectionState.identifier)
                            _uiOfflineRecordingV2State.value = OfflineRecordingV2AvailabilityUiState()
                        }
                    }
                }
        }
        viewModelScope.launch {
            polarDeviceStreamingRepository.deviceInformation
                .collect { deviceInformation ->
                    updateDeviceBatteryUiState(identifier = deviceInformation.identifier, batteryLevel = deviceInformation.batteryLevel)
                    updateDeviceBatteryChargeStatusUiState(identifier = deviceInformation.identifier, chargeStatus = deviceInformation.batteryChargingStatus)
                    updateDeviceBatteryPowerSourceStateUiState(identifier = deviceInformation.identifier, powerSourcesState = deviceInformation.powerSourcesState)
                    updateDeviceFirmwareVersionUiState(identifier = deviceInformation.identifier, firmwareVersion = deviceInformation.firmwareVersion)
                }
        }
        viewModelScope.launch {
            polarDeviceStreamingRepository.availableFeatures
                .collect { deviceStreamsAvailable ->
                    if (deviceStreamsAvailable.availableOfflineFeatures.any { it.value == true }) {
                        updateOfflineRecordingUiState(deviceStreamsAvailable.identifier, isAvailable = true)
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

    fun disconnectAllDevices(devices: List<Device>) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            devices.forEach { polarDeviceStreamingRepository.disconnectFromDevice(it.deviceId) }
        }
    }

    fun disconnectFromDevice(device: Device) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            polarDeviceStreamingRepository.disconnectFromDevice(device.deviceId)
        }
    }

    /** Re-emits this device's stored feature data to shared StateFlows before a device switch. */
    fun selectDevice(identifier: String) {
        polarDeviceStreamingRepository.selectDevice(identifier)
    }

    /** Returns the cached display name for [identifier], or null if not yet known. */
    fun getDeviceName(identifier: String): String? =
        polarDeviceStreamingRepository.getDeviceName(identifier)

    fun searchForDevice(withPrefix: String?): Flow<PolarDeviceInfo> {
        return polarDeviceStreamingRepository.searchForDevice(withPrefix)
    }

    fun startListeningHr(excludeIdentifiers: Set<String>?) {
        hrJob?.cancel()
        hrJob = viewModelScope.launch {
            polarDeviceStreamingRepository.listenHrBroadcasts(excludeIdentifiers)
                .collect { hrData ->
                    Log.d(TAG, "HR data received! Device=${hrData.polarDeviceInfo.deviceId}, HR=${hrData.hr} bpm")
                    _hrData.value = hrData
                }
        }
    }

    fun stopListeningHr() {
        hrJob?.cancel()
        hrJob = null
        _hrData.value = null
    }

    fun isBluetoothEnabled(): Boolean {
        return polarDeviceStreamingRepository.isPhoneBlePowerOn.value
    }

    fun isFeatureReady(identifier: String, feature: PolarBleApi.PolarBleSdkFeature): Boolean {
        return polarDeviceStreamingRepository.isFeatureReady(identifier, feature)
    }

    fun isConnected(): Boolean {
        return _uiConnectionState.value.state == DeviceConnectionStates.CONNECTED
    }

    fun requestRemoveOnlineOfflineFragments() {
        viewModelScope.launch {
            _removeOnlineOfflineFragments.emit(Unit)
        }
    }

    private fun deviceDisconnected(identifier: String) {
        updateOfflineRecordingUiState(identifier = identifier, isAvailable = false)
        updateDeviceBatteryUiState(identifier = identifier, null)
        updateDeviceFirmwareVersionUiState(identifier = identifier, "")
    }

    private fun updateDeviceConnectionUiState(identifier: String, newState: DeviceConnectionStates) {
        Log.d(TAG, "updateOperationUiState() state change to $newState")
        _uiConnectionState.update {
            DeviceConnectionUiState(identifier = identifier, state = newState)
        }
    }

    private fun updateDeviceBatteryUiState(identifier: String, batteryLevel: Int?) {
        Log.d(TAG, "updateDeviceInformationUiState() batteryLevel: $batteryLevel ")
        _uiDeviceInformationState.update {
            it.copy(identifier = identifier, batteryLevel = batteryLevel)
        }
    }

    private fun updateDeviceBatteryChargeStatusUiState(identifier: String, chargeStatus: ChargeState) {
        Log.d(TAG, "updateDeviceBatteryChargeStatusUiState() chargeStatus $chargeStatus")
        _uiDeviceInformationState.update {
            it.copy(identifier = identifier, batteryChargeState = chargeStatus)
        }
    }

    private fun updateDeviceBatteryPowerSourceStateUiState(identifier: String, powerSourcesState: PowerSourcesState) {
        Log.d(TAG, "updateDeviceBatteryPowerSourceStateUiState() powerSourcesState $powerSourcesState")
        _uiDeviceInformationState.update {
            it.copy(identifier = identifier, powerSourcesState = powerSourcesState)
        }
    }

    private fun updateDeviceFirmwareVersionUiState(identifier: String, firmwareVersion: String = "") {
        Log.d(TAG, "updateDeviceInformationUiState() firmwareVersion $firmwareVersion")
        _uiDeviceInformationState.update {
            it.copy(identifier = identifier, firmwareVersion = firmwareVersion)
        }
    }

    private fun updateOfflineRecordingUiState(identifier: String, isAvailable: Boolean = false) {
        _uiOfflineRecordingState.update {
            it.copy(identifier = identifier, isAvailable = isAvailable)
        }
    }


    private fun checkOfflineExerciseV2Support(identifier: String) {
        viewModelScope.launch {
            try {
                val isSupported = withContext(Dispatchers.IO) {
                    polarDeviceStreamingRepository.isOfflineExerciseV2Supported(identifier)
                }
                _uiOfflineRecordingV2State.value =
                    OfflineRecordingV2AvailabilityUiState(
                        identifier = identifier,
                        isAvailable = isSupported
                    )
            } catch (e: Exception) {
                Log.e(TAG, "Offline Exercise V2 support check failed: ${e.message}", e)
                _uiOfflineRecordingV2State.value =
                    OfflineRecordingV2AvailabilityUiState(
                        identifier = identifier,
                        isAvailable = false
                    )
            }
        }
    }

    public override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel onCleared()")
        hrJob?.cancel()
    }
}