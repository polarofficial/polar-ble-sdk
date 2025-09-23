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
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject
import android.content.Context
import android.content.Intent
import com.polar.polarsensordatacollector.R
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarDiskSpaceData
import com.polar.sdk.api.model.PolarPhysicalConfiguration
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.rx3.rxSingle
import java.time.LocalDate

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

    private val _physInfo = MutableStateFlow<PolarPhysicalConfiguration?>(null)
    val physInfo: StateFlow<PolarPhysicalConfiguration?> = _physInfo.asStateFlow()

    private val compositeDisposable = CompositeDisposable()

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

        getSdkModeStatus()
        getSecurityStatus()
        getBleMultiConnectionModeStatus()
        setDeviceUserLocationDefault()
        observeSleepRecordingState()
        checkFirmwareUpdate()
    }

    override fun onCleared() {
        super.onCleared()
        compositeDisposable.clear()
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

    fun openPhysicalConfigActivity(context: Context) {
        val intent = Intent(context, PhysicalConfigActivity::class.java)
        intent.putExtra(ONLINE_OFFLINE_KEY_DEVICE_ID, deviceId)
        context.startActivity(intent)
    }

    fun getFtuInfo() {
        val disposable = rxSingle { polarDeviceStreamingRepository.getFtuInfo(deviceId) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { boolean ->
                    showInfo("Has FTU been done", boolean.toString())
                },
                { error ->
                    showError("Fetching FTU information failed", error.toString())
                }
            )
        compositeDisposable.add(disposable)
    }

    fun getUserPhysicalInfo() {
        viewModelScope.launch {
            when (val result = polarDeviceStreamingRepository.getUserPhysicalConfiguration(deviceId)) {
                is ResultOfRequest.Success -> {
                    _physInfo.value = result.value
                }
                is ResultOfRequest.Failure -> {
                    Log.e(TAG, "Fetching Device Physical Info failed: ${result.throwable?.message ?: result.message}")
                    _physInfo.value = null
                }
            }
        }
    }

    fun doRestart() {
        val disposable = polarDeviceStreamingRepository.doRestart(deviceId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { Log.d(TAG, "Device is restarting") },
                { error -> Log.e(TAG, "Device restart failed: $error") }
            )
        compositeDisposable.add(disposable)
    }

    fun doFactoryReset(savePairing: Boolean = false) {
        val disposable = polarDeviceStreamingRepository.doFactoryReset(deviceId, savePairing)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { Log.d(TAG, "Factory reset on device $deviceId is ongoing.") },
                { error -> Log.e(TAG, "Factory reset failed: $error") }
            )
        compositeDisposable.add(disposable)
    }

    fun doFirmwareUpdate(firmwareUrl: String = "") {
        viewModelScope.launch(Dispatchers.Main) {
            polarDeviceStreamingRepository.doFirmwareUpdate(deviceId, firmwareUrl)
                    .subscribe(
                            { status ->
                                _uiFirmwareUpdateStatus.value = status.details
                               if (status is FirmwareUpdateStatus.FwUpdateFailed) {
                                   showError("Firmware update failed", errorDescription = status.details)
                               } else {
                                   showInfo("Firmware update", description = status.details)
                               }
                            },
                            { throwable ->
                                _uiFirmwareUpdateStatus.value = FirmwareUpdateStatus.FwUpdateFailed("${throwable.message}").toString()
                                showError("Firmware update failed", errorDescription = throwable.message.toString())
                            }
                    )
        }
    }

    private fun checkFirmwareUpdate() {
        viewModelScope.launch(Dispatchers.Main) {
            polarDeviceStreamingRepository.checkFirmwareUpdate(deviceId)
                .subscribe(
                    { status ->
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
                                _uiFirmwareUpdateStatus.value =
                                    FirmwareUpdateStatus.FwUpdateFailed(status.details).toString()
                                showError(application.getString(R.string.firmware_update_check_failed), errorDescription = status.details)
                            }
                        }
                    },
                    { throwable ->
                        _uiCheckFirmwareUpdateStatus.value = CheckFirmwareUpdateStatus.CheckFwUpdateFailed(application.getString(R.string.firmware_update_check_failed))
                        showError(application.getString(R.string.firmware_update_check_failed), errorDescription = throwable.message ?: "")
                    }
                )
        }
    }

    fun openUserDeviceSettingsActivity(context: Context) {
        val intent = Intent(context, UserDeviceSettingsActivity::class.java)
        intent.putExtra("DEVICE_ID", deviceId)
        context.startActivity(intent)
    }

    private fun setDeviceUserLocationDefault() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
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
                        showError("Setting device user location to spinner failed " +
                                "due to unsuccessful settings get.")
                    }
                }
                is ResultOfRequest.Failure -> {
                    showError(result.message, result.throwable?.toString() ?: "")
                }
            }
        }
    }

    fun deleteStoredDeviceFiles(storedDataType: PolarBleApi.PolarStoredDataType, untilDate: LocalDate) = viewModelScope.launch {
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
    }

    fun deleteDateFolders(fromDate: LocalDate?, toDate: LocalDate?) = viewModelScope.launch {
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
    }

    fun setTime() {
        _uiWriteTimeStatus.value = StatusWriteTime.InProgress

        coroutineScope.launch {
            val timeNow = Calendar.getInstance()

            when (val result = polarDeviceStreamingRepository.setTime(deviceId, timeNow)) {
                is ResultOfRequest.Success -> {
                    withContext(Dispatchers.Main) {
                        _uiWriteTimeStatus.value = StatusWriteTime.Completed
                        showInfo("Device time set", timeNow.time.toString())
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

        val disposable = rxSingle { polarDeviceStreamingRepository.getTime(deviceId) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { calendar ->
                    _uiReadTimeStatus.value = StatusReadTime.Completed
                    showInfo("Device time read", calendar.time.toString())
                },
                { error ->
                    _uiReadTimeStatus.value = StatusReadTime.Completed
                    showError("Get time failed", error.toString())
                }
            )
        compositeDisposable.add(disposable)
    }

    fun toggleSecurity(isChecked: Boolean) {
        Log.d(TAG, "toggleSecret to state $isChecked ")
        viewModelScope.launch(Dispatchers.IO) {
            polarDeviceStreamingRepository.toggleSecurity(deviceId, isChecked)
        }
    }

    fun setWarehouseSleep() {
        val disposable = polarDeviceStreamingRepository.setWarehouseSleep(deviceId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { Log.d(TAG, "Warehouse sleep mode set for device $deviceId") },
                { error -> Log.e(TAG, "Setting warehouse sleep failed: $error") }
            )
        compositeDisposable.add(disposable)
    }

    fun setTurnDeviceOff() {
        val disposable = polarDeviceStreamingRepository.turnDeviceOff(deviceId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    Log.d(TAG, "Device $deviceId turn off succeeded")
                },
                { error -> Log.e(TAG, "Device $deviceId turn off failed: $error") }
            )
        compositeDisposable.add(disposable)
    }

    fun waitForConnection() {
        polarDeviceStreamingRepository.waitForConnection(deviceId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                _connectionStatus.value = true
            }, { error ->
                _connectionStatus.value = false
                Log.e(TAG, "Error while waiting for connection: ${error.message}")
            })
    }

    fun getDiskSpace(onSuccess: (PolarDiskSpaceData) -> Unit, onError: (String) -> Unit) {
        val disposable = rxSingle { polarDeviceStreamingRepository.getDiskSpace(deviceId) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { resultOfRequest ->
                    when (resultOfRequest) {
                        is ResultOfRequest.Success -> {
                            resultOfRequest.value?.let { onSuccess(it) }
                        }
                        is ResultOfRequest.Failure -> {
                            onError(resultOfRequest.message)
                        }
                    }
                },
                { error ->
                    onError(error.toString())
                }
            )
        compositeDisposable.add(disposable)
    }

    fun setBleMultiConnection(enabled: Boolean = false) {
        val disposable = polarDeviceStreamingRepository.setBleMultiConnectionMode(deviceId, enabled)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { Log.d(TAG, "Set BLE dual connection mode to $enabled on device $deviceId.") },
                { error -> Log.e(TAG, "Setting BLE dual connection mode to $enabled failed: $error") }
            )
        compositeDisposable.add(disposable)
    }

    fun forceStopSleep() = viewModelScope.launch {
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
    }

    private fun observeSleepRecordingState() {
        val disposable = polarDeviceStreamingRepository.observeSleepRecordingState(deviceId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { value ->
                    _sleepRecordingState.value  = SleepRecordingState(enabled = value)
                },
                { error ->
                    Log.w(TAG, "Observing sleep recording state failed: ${error.message ?: error.toString()}")
                }
            )
        compositeDisposable.add(disposable)
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