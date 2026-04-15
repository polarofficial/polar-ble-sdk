package com.polar.polarsensordatacollector.ui.devicesettings

import android.app.Application
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.repository.ResultOfRequest
import com.polar.polarsensordatacollector.repository.SdkMode
import com.polar.polarsensordatacollector.ui.landing.ONLINE_OFFLINE_KEY_DEVICE_ID
import com.polar.polarsensordatacollector.ui.physicalconfig.PhysicalConfigActivity
import com.polar.polarsensordatacollector.ui.utils.MessageUiState
import com.polar.sdk.api.model.FirmwareUpdateStatus
import com.polar.sdk.api.model.CheckFirmwareUpdateStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import android.content.Context
import android.content.Intent
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.ui.genericapi.GenericApiActivity
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarDiskSpaceData
import com.polar.sdk.api.model.PolarPhysicalConfiguration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

sealed class StatusReadTime {
    object Completed : StatusReadTime()
    object InProgress : StatusReadTime()
}

sealed class StatusWriteTime {
    object Completed : StatusWriteTime()
    object InProgress : StatusWriteTime()
}

data class SdkModeUiState(
    val isAvailable: Boolean = false,
    val sdkModeState: SdkMode.STATE = SdkMode.STATE.DISABLED,
    val sdkModeLedState: SdkMode.STATE = SdkMode.STATE.ENABLED,
    val ppiModeLedState: SdkMode.STATE = SdkMode.STATE.ENABLED
)

data class SecurityUiState(
    val isAvailable: Boolean = false,
    val isEnabled: Boolean = false,
)

data class BleMultiConnectionUiState(
    val isEnabled: Boolean = false
)

data class SleepRecordingState(
    val enabled: Boolean?
)

data class SettingsSupportUiState(
    val support: Boolean?
)

data class DeviceToHostNotificationsUiState(
    val isObserving: Boolean = false
)

@HiltViewModel
internal class DeviceSettingsViewModel @Inject constructor(
    private val polarDeviceStreamingRepository: PolarDeviceRepository,
    private val state: SavedStateHandle,
    private val application: Application
) : ViewModel() {

    companion object {
        private const val TAG = "DeviceSettingsViewModel"
    }

    private val deviceId = state.get<String>(ONLINE_OFFLINE_KEY_DEVICE_ID) ?: throw Exception("Device settings viewModel must know the deviceId")

    private val _uiShowError: MutableStateFlow<MessageUiState> = MutableStateFlow(MessageUiState("", ""))
    val uiShowError: StateFlow<MessageUiState> = _uiShowError.asStateFlow()

    private val _uiShowInfo: MutableStateFlow<MessageUiState> = MutableStateFlow(MessageUiState("", ""))
    val uiShowInfo: StateFlow<MessageUiState> = _uiShowInfo.asStateFlow()

    private val _uiSdkModeState = MutableStateFlow(SdkModeUiState())
    val uiSdkModeState: StateFlow<SdkModeUiState> = _uiSdkModeState.asStateFlow()

    private val _uiSecurityState: MutableStateFlow<SecurityUiState> = MutableStateFlow(SecurityUiState())
    val uiSecurityState: StateFlow<SecurityUiState> = _uiSecurityState.asStateFlow()

    private val _uiReadTimeStatus = MutableStateFlow<StatusReadTime>(StatusReadTime.Completed)
    val uiReadTimeStatus: StateFlow<StatusReadTime> = _uiReadTimeStatus.asStateFlow()

    private val _uiWriteTimeStatus = MutableStateFlow<StatusWriteTime>(StatusWriteTime.Completed)
    val uiWriteTimeStatus: StateFlow<StatusWriteTime> = _uiWriteTimeStatus.asStateFlow()

    private val _uiFirmwareUpdateStatus = MutableStateFlow<String>("")
    val uiFirmwareUpdateStatus: StateFlow<String> = _uiFirmwareUpdateStatus.asStateFlow()

    private val _uiCheckFirmwareUpdateStatus = MutableStateFlow<CheckFirmwareUpdateStatus>(
        CheckFirmwareUpdateStatus.CheckFwUpdateNotAvailable("Initial state")
    )
    val uiCheckFirmwareUpdateStatus: StateFlow<CheckFirmwareUpdateStatus> = _uiCheckFirmwareUpdateStatus.asStateFlow()

    private var _currentDeviceUserLocationIndex = MutableStateFlow<Int>(0)

    private val _connectionStatus = MutableStateFlow(false)
    val connectionStatus: StateFlow<Boolean> get() = _connectionStatus.asStateFlow()

    private var _uiMultiBleModeState = MutableStateFlow(BleMultiConnectionUiState())
    val uiMultiBleModeState: StateFlow<BleMultiConnectionUiState> = _uiMultiBleModeState.asStateFlow()

    private val _sleepRecordingState = MutableStateFlow(SleepRecordingState(enabled = null))
    val sleepRecordingState: StateFlow<SleepRecordingState> = _sleepRecordingState.asStateFlow()

    var physInfo: PolarPhysicalConfiguration? = null
        private set

    private val _uiSettingsSupportUiState = MutableStateFlow(false)
    val uiSettingsSupportUiState: StateFlow<Boolean> = _uiSettingsSupportUiState.asStateFlow()

    private val _uiDeviceToHostNotificationsState = MutableStateFlow(DeviceToHostNotificationsUiState())
    val uiDeviceToHostNotificationsState: StateFlow<DeviceToHostNotificationsUiState> = _uiDeviceToHostNotificationsState.asStateFlow()

    private var d2hNotificationsJob: Job? = null

    init {
        viewModelScope.launch {
            polarDeviceStreamingRepository.sdkModeState
                .collect { sdkMode ->
                    updateSdkModeUiState(
                        isAvailable = sdkMode.isAvailable,
                        isEnabled = sdkMode.sdkModeState,
                        sdkModeLedState = sdkMode.sdkModeLedAnimation,
                        ppiModeLedState = sdkMode.ppiModeLedAnimation
                    )
                }
        }

        viewModelScope.launch {
            polarDeviceStreamingRepository.availableFeatures
                .collect { deviceStreamsAvailable ->
                    if (deviceStreamsAvailable.availableOfflineFeatures.any { it.value == true }) {
                        updateUiOfflineRecordingSettings(offlineRecordingEnabled = true)
                    } else {
                        updateUiOfflineRecordingSettings(offlineRecordingEnabled = false)
                    }
                }
        }

        viewModelScope.launch {
            polarDeviceStreamingRepository.isOfflineRecordingSecurityEnabled
                .collect { isSecurityEnabled ->
                    updateSecurityUiState(isSecurityEnabled)
                }
        }

        viewModelScope.launch {
            polarDeviceStreamingRepository.isMultiBleModeEnabled
                .collect { isEnabled ->
                    _uiMultiBleModeState.update { BleMultiConnectionUiState(isEnabled) }
                }
        }

        viewModelScope.launch {
            polarDeviceStreamingRepository.deviceSupportsSettings
                .collect { support ->
                    _uiSettingsSupportUiState.update { support }
                }
        }

        getSdkModeStatus()
        getSecurityStatus()
        getBleMultiConnectionModeStatus()
        setDeviceUserLocationDefault()
        observeSleepRecordingState()
        checkFirmwareUpdate()
        getDeviceSettingsSupportUiState()
    }

    override fun onCleared() {
        super.onCleared()
        d2hNotificationsJob?.cancel()
    }

    private fun updateUiOfflineRecordingSettings(offlineRecordingEnabled: Boolean) {
        _uiSecurityState.update {
            it.copy(isAvailable = offlineRecordingEnabled)
        }
    }


    fun sdkModeToggle() {
        viewModelScope.launch(Dispatchers.IO) {
            polarDeviceStreamingRepository.sdkModeToggle(deviceId)
        }
    }

    fun sdkModeLedAnimation() {
        viewModelScope.launch {
            polarDeviceStreamingRepository.setSdkModeLedConfig(deviceId)
        }
    }

    fun ppiModeLedAnimation() {
        viewModelScope.launch {
            polarDeviceStreamingRepository.setPpiModeLedConfig(deviceId)
        }
    }

    fun toggleDeviceToHostNotifications() {
        viewModelScope.launch {
            if (_uiDeviceToHostNotificationsState.value.isObserving) {
                stopDeviceToHostNotifications()
            } else {
                startDeviceToHostNotifications()
            }
        }
    }

    private fun startDeviceToHostNotifications() {
        d2hNotificationsJob = viewModelScope.launch {
            _uiDeviceToHostNotificationsState.update { it.copy(isObserving = true) }
            polarDeviceStreamingRepository.observeDeviceToHostNotifications(deviceId)
                .catch { error ->
                    _uiDeviceToHostNotificationsState.update { it.copy(isObserving = false) }
                    showError("D2H Notification failed", error.toString())
                    Log.e(TAG, "Device to host notification observation failed: ${error.message}")
                }
                .collect { notification ->
                    val message = "${notification.notificationType} with ${notification.parameters.size} bytes"
                    showInfo("D2H Notification", message)
                    Log.d(TAG, "Device to host notification received: $message, param: ${notification.parsedParameters}")
                }
            _uiDeviceToHostNotificationsState.update { it.copy(isObserving = false) }
            Log.d(TAG, "Device to host notification observation completed")
        }
    }

    private fun stopDeviceToHostNotifications() {
        d2hNotificationsJob?.cancel()
        d2hNotificationsJob = null
        _uiDeviceToHostNotificationsState.update { it.copy(isObserving = false) }
        Log.d(TAG, "Device to host notification observation stopped")
    }

    fun openPhysicalConfigActivity(context: Context) {
        val intent = Intent(context, PhysicalConfigActivity::class.java)
        intent.putExtra(ONLINE_OFFLINE_KEY_DEVICE_ID, deviceId)
        context.startActivity(intent)
    }

    fun getFtuInfo() {
        viewModelScope.launch {
            try {
                val result = polarDeviceStreamingRepository.getFtuInfo(deviceId)
                showInfo("Has FTU been done", result.toString())
            } catch (error: Exception) {
                showError("Fetching FTU information failed", error.toString())
            }
        }
    }

    suspend fun getUserPhysicalInfo() {
        viewModelScope.run {
            when (val result = polarDeviceStreamingRepository.getUserPhysicalConfiguration(deviceId)) {
                is ResultOfRequest.Success -> {
                    physInfo = result.value
                }
                is ResultOfRequest.Failure -> {
                    Log.e(TAG, "Fetching Device Physical Info failed: ${result.throwable?.message ?: result.message}")
                    physInfo = null
                }
            }
        }
    }

    fun doRestart() {
        viewModelScope.launch {
            try {
                polarDeviceStreamingRepository.doRestart(deviceId)
                Log.d(TAG, "Device is restarting")
            } catch (error: Exception) {
                Log.e(TAG, "Device restart failed: $error")
            }
        }
    }

    fun doFactoryReset() {
        viewModelScope.launch {
            try {
                polarDeviceStreamingRepository.doFactoryReset(deviceId)
                Log.d(TAG, "Factory reset on device $deviceId is ongoing.")
            } catch (error: Exception) {
                Log.e(TAG, "Factory reset failed: $error")
            }
        }
    }

    fun doFirmwareUpdate(firmwareUrl: String = "") {
        viewModelScope.launch {
            polarDeviceStreamingRepository.doFirmwareUpdate(deviceId, firmwareUrl)
                .catch { throwable ->
                    _uiFirmwareUpdateStatus.value = FirmwareUpdateStatus.FwUpdateFailed("${throwable.message}").toString()
                    showError("Firmware update failed", errorDescription = throwable.message.toString())
                }
                .collect { status ->
                    _uiFirmwareUpdateStatus.value = status.details
                    if (status is FirmwareUpdateStatus.FwUpdateFailed) {
                        showError("Firmware update failed", errorDescription = status.details)
                    } else {
                        showInfo("Firmware update", description = status.details)
                    }
                }
        }
    }

    private fun checkFirmwareUpdate() {
        viewModelScope.launch {
            polarDeviceStreamingRepository.checkFirmwareUpdate(deviceId)
                .catch { throwable ->
                    _uiCheckFirmwareUpdateStatus.value = CheckFirmwareUpdateStatus.CheckFwUpdateFailed(
                        application.getString(R.string.firmware_update_check_failed)
                    )
                    showError(application.getString(R.string.firmware_update_check_failed), errorDescription = throwable.message ?: "")
                }
                .collect { status ->
                    _uiCheckFirmwareUpdateStatus.value = status
                    when (status) {
                        is CheckFirmwareUpdateStatus.CheckFwUpdateAvailable -> {
                            _uiFirmwareUpdateStatus.value = application.getString(
                                R.string.firmware_update_available,
                                status.version
                            )
                        }
                        is CheckFirmwareUpdateStatus.CheckFwUpdateNotAvailable -> {
                            _uiFirmwareUpdateStatus.value = status.details
                        }
                        is CheckFirmwareUpdateStatus.CheckFwUpdateFailed -> {
                            _uiFirmwareUpdateStatus.value = FirmwareUpdateStatus.FwUpdateFailed(status.details).toString()
                            showError(application.getString(R.string.firmware_update_check_failed), errorDescription = status.details)
                        }
                    }
                }
        }
    }

    fun openUserDeviceSettingsActivity(context: Context) {
        val intent = Intent(context, UserDeviceSettingsActivity::class.java)
        intent.putExtra("DEVICE_ID", deviceId)
        context.startActivity(intent)
    }

    private fun setDeviceUserLocationDefault() = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                if (polarDeviceStreamingRepository.deviceSupportsSettings.value) {
                    when (val result = polarDeviceStreamingRepository.getDeviceUserSettings(deviceId)) {
                        is ResultOfRequest.Success -> {
                            result.value?.let {
                                val deviceLocation = it.deviceLocation
                                if (deviceLocation != null) {
                                    _currentDeviceUserLocationIndex.value = deviceLocation
                                } else {
                                    _currentDeviceUserLocationIndex.value = 0
                                }
                            } ?: kotlin.run {
                                showError(
                                    "Setting device user location to spinner failed " +
                                            "due to unsuccessful settings get."
                                )
                            }
                        }

                        is ResultOfRequest.Failure -> {
                            showError(result.message, result.throwable?.toString() ?: "")
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error setting device user location default: ${e.message}", e)
            showError(e.message ?: "Failed to load device settings")
        }
    }

    fun deleteStoredDeviceFiles(storedDataType: PolarBleApi.PolarStoredDataType, untilDate: LocalDate) = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                when (val result = polarDeviceStreamingRepository.deleteDeviceData(deviceId, storedDataType, untilDate)) {
                    is ResultOfRequest.Success -> {
                        showInfo("Successfully deleted $storedDataType files until $untilDate")
                    }
                    is ResultOfRequest.Failure -> {
                        showError("Failure in deleting $storedDataType files from device $deviceId")
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error deleting device data: ${e.message}", e)
            showError(e.message ?: "Failed to delete device files")
        }
    }

    fun deleteDateFolders(fromDate: LocalDate?, toDate: LocalDate?) = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                when (polarDeviceStreamingRepository.deleteDeviceDateFolders(deviceId, fromDate, toDate)) {
                    is ResultOfRequest.Success -> {
                        showInfo("Successfully deleted date folders from: $fromDate to: $toDate")
                    }
                    is ResultOfRequest.Failure -> {
                        showError("Failure in deleting files from device: $deviceId")
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error deleting date folders: ${e.message}", e)
            showError(e.message ?: "Failed to delete date folders")
        }
    }

    fun deleteTelemetryData() = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                when (polarDeviceStreamingRepository.deleteTelemetryData(deviceId)) {
                    is ResultOfRequest.Success -> {
                        showInfo("Successfully deleted all telemetry data files")
                    }
                    is ResultOfRequest.Failure -> {
                        showError("Failure in deleting telemetry data files from device: $deviceId")
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error deleting telemetry data: ${e.message}", e)
            showError(e.message ?: "Failed to delete telemetry data")
        }
    }

    fun setTime() {
        _uiWriteTimeStatus.value = StatusWriteTime.InProgress

        viewModelScope.launch {
            val timeNow = LocalDateTime.now()

            when (val result = polarDeviceStreamingRepository.setTime(deviceId, timeNow)) {
                is ResultOfRequest.Success -> {
                    withContext(Dispatchers.Main) {
                        _uiWriteTimeStatus.value = StatusWriteTime.Completed
                        showInfo("Device time set", DateTimeFormatter.ISO_DATE_TIME.format(timeNow))
                    }
                }
                is ResultOfRequest.Failure -> {
                    withContext(Dispatchers.Main) {
                        _uiWriteTimeStatus.value = StatusWriteTime.Completed
                        showError(result.message, result.throwable?.toString() ?: "")
                    }
                }
            }
        }
    }

    fun readTime() {
        _uiReadTimeStatus.value = StatusReadTime.InProgress
        viewModelScope.launch {
            try {
                val dateTime = polarDeviceStreamingRepository.getTime(deviceId)
                _uiReadTimeStatus.value = StatusReadTime.Completed
                showInfo("Device time set", DateTimeFormatter.ISO_DATE_TIME.format(dateTime))
            } catch (error: Exception) {
                _uiReadTimeStatus.value = StatusReadTime.Completed
                showError("Get time failed", error.toString())
            }
        }
    }

    fun toggleSecurity(isChecked: Boolean) {
        Log.d(TAG, "toggleSecret to state $isChecked ")
        viewModelScope.launch(Dispatchers.IO) {
            polarDeviceStreamingRepository.toggleSecurity(deviceId, isChecked)
        }
    }

    fun setWarehouseSleep() {
        viewModelScope.launch {
            try {
                polarDeviceStreamingRepository.setWarehouseSleep(deviceId)
                Log.d(TAG, "Warehouse sleep mode set for device $deviceId")
            } catch (error: Exception) {
                Log.e(TAG, "Setting warehouse sleep failed: $error")
            }
        }
    }

    fun setTurnDeviceOff() {
        viewModelScope.launch {
            try {
                polarDeviceStreamingRepository.turnDeviceOff(deviceId)
                Log.d(TAG, "Device $deviceId turn off succeeded")
            } catch (error: Exception) {
                Log.e(TAG, "Device $deviceId turn off failed: $error")
            }
        }
    }

    fun waitForConnection() {
        viewModelScope.launch {
            try {
                polarDeviceStreamingRepository.waitForConnection(deviceId)
                _connectionStatus.value = true
            } catch (error: Exception) {
                _connectionStatus.value = false
                Log.e(TAG, "Error while waiting for connection: ${error.message}")
            }
        }
    }

    fun getDiskSpace(onSuccess: (PolarDiskSpaceData) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            when (val result = polarDeviceStreamingRepository.getDiskSpace(deviceId)) {
                is ResultOfRequest.Success -> result.value?.let { onSuccess(it) }
                is ResultOfRequest.Failure -> onError(result.message)
            }
        }
    }

    fun setBleMultiConnection(enabled: Boolean = false) {
        viewModelScope.launch {
            try {
                polarDeviceStreamingRepository.setBleMultiConnectionMode(deviceId, enabled)
                Log.d(TAG, "Set BLE dual connection mode to $enabled on device $deviceId.")
            } catch (error: Exception) {
                Log.e(TAG, "Setting BLE dual connection mode to $enabled failed: $error")
            }
        }
    }

    fun forceStopSleep() = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                when (val result = polarDeviceStreamingRepository.forceStopSleep(deviceId)) {
                    is ResultOfRequest.Success -> {
                        result.value?.let {
                            showInfo("Sleep recording successfully stopped.")
                        } ?: kotlin.run {
                            showError("Failed to stop sleep recording.")
                        }
                    }
                    is ResultOfRequest.Failure -> {
                        showError(result.message, result.throwable?.toString() ?: "")
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error stopping sleep: ${e.message}", e)
            showError(e.message ?: "Failed to stop sleep recording")
        }
    }

    fun getChargeState() = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                when (val result = polarDeviceStreamingRepository.getChargeInformation(deviceId)) {
                    is ResultOfRequest.Success -> {
                        result.value?.let {
                            showInfo("Charger information:\n" +
                                    "${result.value.chargerStatus}\n" +
                                    "Battery level is ${result.value.batteryLevel} %")
                        } ?: kotlin.run {
                            showError("Failed to fetch charge information.")
                        }
                    }
                    is ResultOfRequest.Failure -> {
                        showError(result.message, result.throwable?.toString() ?: "")
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error getting charge state: ${e.message}", e)
            showError(e.message ?: "Failed to get charge information")
        }
    }

    fun  checkIfDeviceDisconnectedDueRemovedPairing() = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                when (val result = polarDeviceStreamingRepository. checkIfDeviceDisconnectedDueRemovedPairing(deviceId)) {
                    is ResultOfRequest.Success -> {
                        result.value?.let {
                            showInfo("Pairing was disconnected due to pairing problem?\n" +
                                    "${result.value.first}.\n" +
                                    "With BLE GATT status code  ${result.value.second} " +
                                    if (result.value.second == -1) { "(Not set)" } else { "" }
                            )
                        } ?: kotlin.run {
                            showError("Failed to check if device is disconnected due to removed pairing")
                        }
                    }
                    is ResultOfRequest.Failure -> {
                        showError(result.message, result.throwable?.toString() ?: "")
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error checking pairing status: ${e.message}", e)
            showError(e.message ?: "Failed to check pairing status")
        }
    }

    fun getBLESignalStrength() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            when (val result = polarDeviceStreamingRepository.getBleSignalStrength(deviceId)) {
                is ResultOfRequest.Success -> {
                    result.value?.let {
                        showInfo("RSSI: ${result.value} dBm")
                    } ?: kotlin.run {
                        showError("Failed to BLE signal strength information.")
                    }
                }
                is ResultOfRequest.Failure -> {
                    showError(result.message, result.throwable?.toString() ?: "")
                }
            }
        }
    }

    private fun observeSleepRecordingState() {
        viewModelScope.launch {
            polarDeviceStreamingRepository.observeSleepRecordingState(deviceId)
                .catch { error ->
                    Log.w(TAG, "Observing sleep recording state failed: ${error.message ?: error.toString()}")
                }
                .collect { value ->
                    _sleepRecordingState.value = SleepRecordingState(enabled = value)
                }
        }
    }


    private fun getSdkModeStatus() {
        Log.d(TAG, "getSdkModeStatus()")
        viewModelScope.launch(Dispatchers.IO) {
            polarDeviceStreamingRepository.isSdkModeEnabled(deviceId)
        }
    }

    private fun getSecurityStatus() {
        Log.d(TAG, "getSdkModeStatus()")
        viewModelScope.launch(Dispatchers.IO) {
            polarDeviceStreamingRepository.isSecurityEnabled(deviceId)
        }
    }

    private fun getBleMultiConnectionModeStatus() {
        Log.d(TAG, "getBleMultiConnectionMode()")
        viewModelScope.launch(Dispatchers.IO) {
            polarDeviceStreamingRepository.getMultiBleModeEnabled(deviceId)
        }
    }

    private fun getDeviceSettingsSupportUiState() {
        Log.d(TAG, "getDeviceSettingsSupportUiState()")
        viewModelScope.launch(Dispatchers.IO) {
            polarDeviceStreamingRepository.deviceSupportsSettings
        }
    }

    private fun updateSdkModeUiState(isAvailable: Boolean = false, isEnabled: SdkMode.STATE, sdkModeLedState: SdkMode.STATE, ppiModeLedState: SdkMode.STATE) {
        _uiSdkModeState.update {
            it.copy(isAvailable = isAvailable, sdkModeState = isEnabled, sdkModeLedState = sdkModeLedState, ppiModeLedState = ppiModeLedState)
        }
    }

    private fun updateSecurityUiState(securityEnabled: Boolean) {
        _uiSecurityState.update {
            it.copy(isEnabled = securityEnabled)
        }
    }

    private fun showError(errorHeader: String, errorDescription: String = "") {
        Log.e(TAG, " Error: $errorHeader ${if (errorDescription.isNotEmpty()) "Description: $errorDescription" else ""}")
        _uiShowError.update {
            MessageUiState(errorHeader, errorDescription)
        }
    }

    private fun showInfo(header: String, description: String = "") {
        _uiShowInfo.update {
            MessageUiState(header, description)
        }
    }
}