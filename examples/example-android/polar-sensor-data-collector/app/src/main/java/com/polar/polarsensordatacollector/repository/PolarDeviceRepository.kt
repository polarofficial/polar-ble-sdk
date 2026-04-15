package com.polar.polarsensordatacollector.repository

import android.net.Uri
import android.util.Log
import com.polar.androidcommunications.api.ble.model.DisInfo
import com.polar.androidcommunications.api.ble.model.gatt.client.BleDisClient
import com.polar.androidcommunications.api.ble.model.gatt.client.ChargeState
import com.polar.androidcommunications.api.ble.model.gatt.client.PowerSourcesState
import com.polar.androidcommunications.api.ble.model.gatt.client.BatteryPresentState
import com.polar.androidcommunications.api.ble.model.gatt.client.PowerSourceState
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility
import com.polar.polarsensordatacollector.DataCollector
import com.polar.polarsensordatacollector.crypto.SecretKeyManager
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.model.*
import com.polar.sdk.api.model.activity.PolarStepsData
import com.polar.sdk.api.model.sleep.PolarSleepData
import com.polar.sdk.impl.BDBleApiImpl
import com.polar.sdk.api.model.PolarUserDeviceSettings
import com.polar.sdk.api.model.activity.PolarCaloriesData
import com.polar.sdk.impl.utils.CaloriesType
import com.polar.sdk.api.model.activity.Polar247HrSamplesData
import com.polar.sdk.api.model.sleep.PolarNightlyRechargeData
import com.polar.sdk.api.model.PolarSkinTemperatureData
import com.polar.sdk.api.model.activity.Polar247PPiSamplesData
import com.polar.sdk.api.model.activity.PolarActiveTimeData
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionReference
import com.polar.sdk.api.model.activity.PolarActivitySamplesDayData
import com.polar.sdk.api.model.activity.PolarDailySummaryData
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionFetchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.EnumMap
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

sealed class DeviceConnectionState(val deviceId: String = "") {
    class DeviceConnected(deviceId: String) : DeviceConnectionState(deviceId)
    class DeviceConnecting(deviceId: String) : DeviceConnectionState(deviceId)
    class DeviceDisconnecting(deviceId: String) : DeviceConnectionState(deviceId)
    class DeviceNotConnected(deviceId: String = "") : DeviceConnectionState(deviceId)
}

data class DeviceInformation(
    val deviceId: String = "",
    val firmwareVersion: String = "",
    val batteryLevel: Int? = null,
    val batteryChargingStatus: ChargeState = ChargeState.UNKNOWN,
    val powerSourcesState: PowerSourcesState =
        PowerSourcesState(BatteryPresentState.UNKNOWN,
            PowerSourceState.UNKNOWN,
            PowerSourceState.UNKNOWN)
)

data class OfflineRecordingData(
    val data: PolarOfflineRecordingData,
    val uri: Uri,
    val fileSize: Long,
    val downLoadSpeed: Double,
)

data class AvailableFeatures(
    val deviceId: String = "",
    val availableStreamingFeatures: EnumMap<PolarBleApi.PolarDeviceDataType, Boolean> = EnumMap(PolarBleApi.PolarDeviceDataType.values().associateWith { false }),
    val availableOfflineFeatures: EnumMap<PolarBleApi.PolarDeviceDataType, Boolean> = EnumMap(PolarBleApi.PolarDeviceDataType.values().associateWith { false }),
)

data class SdkMode(
    val deviceId: String = "",
    val isAvailable: Boolean = false,
    val sdkModeState: STATE = STATE.DISABLED,
    val sdkModeLedAnimation: STATE = STATE.ENABLED,
    val ppiModeLedAnimation: STATE = STATE.ENABLED
) {
    enum class STATE {
        ENABLED,
        DISABLED,
        STATE_CHANGE_IN_PROGRESS,
    }
}

data class OfflineRecTriggerStatus(
    val deviceId: String = "",
    val triggerStatus: PolarOfflineRecordingTrigger? = null
)

data class ChargeInformation(
    val batteryLevel: Int = -1,
    val chargerStatus: ChargeState = ChargeState.UNKNOWN
)

sealed class ResultOfRequest<out T> {
    data class Success<out T>(
        val value: T? = null,
        val progress: ProgressInfo? = null
    ) : ResultOfRequest<T>()

    data class Failure(
        val message: String,
        val throwable: Throwable?
    ) : ResultOfRequest<Nothing>()
}

data class ProgressInfo(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val progressPercent: Int
)

@Singleton
class PolarDeviceRepository @Inject constructor(
    private val api: BDBleApiImpl,
    private val collector: DataCollector,
    private val security: SecretKeyManager
) : PolarBleApiCallback() {
    companion object {
        private const val TAG = "PhoneStatusRepository"
    }

    private val _polarBleSdkVersion: MutableStateFlow<String> = MutableStateFlow("")
    val polarBleSdkVersion: StateFlow<String> = _polarBleSdkVersion.asStateFlow()

    private val _isPhoneBlePowerOn: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isPhoneBlePowerOn: StateFlow<Boolean> = _isPhoneBlePowerOn.asStateFlow()

    private val _deviceConnectionStatus: MutableStateFlow<DeviceConnectionState> = MutableStateFlow(DeviceConnectionState.DeviceNotConnected())
    val deviceConnectionStatus: StateFlow<DeviceConnectionState> = _deviceConnectionStatus.asStateFlow()

    private val _availableFeatures: MutableStateFlow<AvailableFeatures> = MutableStateFlow(AvailableFeatures())
    val availableFeatures: StateFlow<AvailableFeatures> = _availableFeatures.asStateFlow()

    private val _sdkModeState = MutableStateFlow(SdkMode())
    val sdkModeState: StateFlow<SdkMode> = _sdkModeState.asStateFlow()

    private val _triggerState = MutableStateFlow(OfflineRecTriggerStatus())
    val triggerState: StateFlow<OfflineRecTriggerStatus> = _triggerState.asStateFlow()

    private val _deviceInformation: MutableStateFlow<DeviceInformation> = MutableStateFlow(DeviceInformation())
    val deviceInformation: StateFlow<DeviceInformation> = _deviceInformation.asStateFlow()

    private val _isOfflineRecordingSecurityEnabled: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isOfflineRecordingSecurityEnabled: StateFlow<Boolean> = _isOfflineRecordingSecurityEnabled.asStateFlow()

    private val offlineEntryCache: MutableMap<String, MutableList<PolarOfflineRecordingEntry>> = mutableMapOf()
    private val trainingSessionReferenceCache: MutableMap<String, MutableList<PolarTrainingSessionReference>> = mutableMapOf()

    private val _isMultiBleModeEnabled: MutableStateFlow<Boolean> = MutableStateFlow(false)
    var isMultiBleModeEnabled: StateFlow<Boolean> = _isMultiBleModeEnabled.asStateFlow()

    private val _deviceSupportsSettings: MutableStateFlow<Boolean> = MutableStateFlow(false)
    var deviceSupportsSettings: StateFlow<Boolean> = _deviceSupportsSettings.asStateFlow()

    private val _offlineExerciseV2Supported: MutableStateFlow<Map<String, Boolean>> =
        MutableStateFlow(emptyMap())
    val offlineExerciseV2Supported: StateFlow<Map<String, Boolean>> =
        _offlineExerciseV2Supported.asStateFlow()

    data class SdkFeaturesReadyEvent(
        val deviceId: String = "",
        val readyFeatures: List<PolarBleApi.PolarBleSdkFeature> = emptyList()
    )

    private val _sdkFeaturesReady: MutableStateFlow<SdkFeaturesReadyEvent> =
        MutableStateFlow(SdkFeaturesReadyEvent())
    val sdkFeaturesReady: StateFlow<SdkFeaturesReadyEvent> = _sdkFeaturesReady.asStateFlow()

    var chargeInfo = ChargeInformation()

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        api.setApiCallback(this)
        _polarBleSdkVersion.update {
            PolarBleApiDefaultImpl.versionInfo()
        }
    }

    fun listOfflineRecordings(deviceId: String): Flow<PolarOfflineRecordingEntry> {
        Log.d(TAG, "listOfflineRecordings from device $deviceId")
        return api.listOfflineRecordings(deviceId)
            .onStart {
                offlineEntryCache[deviceId] = mutableListOf()
            }
            .onEach { entry ->
                offlineEntryCache[deviceId]?.add(entry)
            }
    }

    fun getOfflineEntryFromCache(deviceId: String, path: String): PolarOfflineRecordingEntry? {
        return offlineEntryCache[deviceId]?.find { it.path == path }
    }

    private fun saveData(deviceId: String, offlineRecData: PolarOfflineRecordingData): Uri {
        val logIdentifier = getDeviceName(deviceId) ?: deviceId
        when (offlineRecData) {
            is PolarOfflineRecordingData.AccOfflineRecording -> {
                collector.startAccLog(logIdentifier, startTime = offlineRecData.startTime)
                for (sample in offlineRecData.data.samples) {
                    collector.logAcc(sample.timeStamp, sample.x, sample.y, sample.z)
                }
                return collector.finalizeAllStreams().toList().first()
            }
            is PolarOfflineRecordingData.GyroOfflineRecording -> {
                collector.startGyroLog(logIdentifier, startTime = offlineRecData.startTime)
                for (sample in offlineRecData.data.samples) {
                    collector.logGyro(sample.timeStamp, sample.x, sample.y, sample.z)
                }
                return collector.finalizeAllStreams().toList().first()
            }
            is PolarOfflineRecordingData.MagOfflineRecording -> {
                collector.startMagnetometerLog(logIdentifier, startTime = offlineRecData.startTime)
                for (sample in offlineRecData.data.samples) {
                    collector.logMagnetometer(sample.timeStamp, sample.x, sample.y, sample.z)
                }
                return collector.finalizeAllStreams().toList().first()
            }
            is PolarOfflineRecordingData.PpgOfflineRecording -> {
                collector.startPpgLog(logIdentifier, startTime = offlineRecData.startTime)
                collector.logPpgData(offlineRecData.data)
                return collector.finalizeAllStreams().toList().first()
            }
            is PolarOfflineRecordingData.PpiOfflineRecording -> {
                collector.startPpiLog(logIdentifier, startTime = offlineRecData.startTime)
                for (sample in offlineRecData.data.samples) {
                    collector.logPpi(sample.ppi, sample.errorEstimate, sample.blockerBit, sample.skinContactStatus, sample.skinContactSupported, sample.hr, sample.timeStamp)
                }
                return collector.finalizeAllStreams().toList().first()
            }
            is PolarOfflineRecordingData.HrOfflineRecording -> {
                collector.startHrLog(logIdentifier, startTime = offlineRecData.startTime)
                for (sample in offlineRecData.data.samples) {
                    collector.logHr(data = sample)
                }
                return collector.finalizeAllStreams().toList().first()
            }
            is PolarOfflineRecordingData.TemperatureOfflineRecording -> {
                collector.startTemperatureLog(logIdentifier, startTime = offlineRecData.startTime)
                for (sample in offlineRecData.data.samples) {
                    collector.logTemperature(timeStamp = sample.timeStamp, temperature = sample.temperature)
                }
                return collector.finalizeAllStreams().toList().first()
            }
            is PolarOfflineRecordingData.SkinTemperatureOfflineRecording -> {
                collector.startSkinTemperatureLog(logIdentifier, startTime = offlineRecData.startTime)
                for (sample in offlineRecData.data.samples) {
                    collector.logSkinTemperature(timeStamp = sample.timeStamp, temperature = sample.temperature)
                }
                return collector.finalizeAllStreams().toList().first()
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    suspend fun deleteRecording(deviceId: String, path: String): ResultOfRequest<Nothing> = withContext(Dispatchers.IO) {
        val offlineRecEntry = offlineEntryCache[deviceId]?.find { it.path == path }
        offlineRecEntry?.let { offlineEntry ->
            return@withContext try {
                val result = measureTimedValue {
                    api.removeOfflineRecord(deviceId, offlineEntry)
                }
                Log.d(TAG, "delete of recording $path took ${TimeUnit.MICROSECONDS.toSeconds(result.duration.inWholeMicroseconds)} seconds")
                offlineEntryCache[deviceId]?.remove(offlineRecEntry)
                ResultOfRequest.Success()
            } catch (e: Exception) {
                ResultOfRequest.Failure("Failed to remove ${offlineEntry.path}", e)
            }
        }
        ResultOfRequest.Failure("Tried to remove \"$path\", but no matching entry in repository", null)
    }

    fun getOfflineRecordingWithProgress(
        deviceId: String,
        path: String
    ): Flow<ResultOfRequest<OfflineRecordingData>> {
        Log.d(TAG, "getOfflineRecordingWithProgress from device $deviceId in $path")

        val offlineRecEntry = offlineEntryCache[deviceId]?.find { it.path == path }

        return if (offlineRecEntry != null) {
            flow<ResultOfRequest<OfflineRecordingData>> {
                val startTime = System.currentTimeMillis()

                api.getOfflineRecordWithProgress(
                    deviceId,
                    offlineRecEntry,
                    security.getSecretKey(deviceId)?.let { PolarRecordingSecret(it.encoded) }
                ).collect { result ->
                    when (result) {
                        is PolarOfflineRecordingResult.Progress -> {
                            Log.d(TAG, "Progress: ${result.progressPercent}% (${result.bytesDownloaded}/${result.totalBytes} bytes)")
                            emit(ResultOfRequest.Success(
                                value = null,
                                progress = ProgressInfo(
                                    bytesDownloaded = result.bytesDownloaded,
                                    totalBytes = result.totalBytes,
                                    progressPercent = result.progressPercent
                                )
                            ))
                        }
                        is PolarOfflineRecordingResult.Complete -> {
                            val downloadDuration = (System.currentTimeMillis() - startTime) / 1024.0
                            val downloadSpeed = if (downloadDuration > 0) (offlineRecEntry.size / 1024.0) / downloadDuration else 0.0
                            val uri = saveData(deviceId, result.data)
                            emit(ResultOfRequest.Success(
                                value = OfflineRecordingData(
                                    data = result.data,
                                    uri = uri,
                                    fileSize = offlineRecEntry.size,
                                    downLoadSpeed = downloadSpeed
                                ),
                                progress = null
                            ))
                        }
                    }
                }
            }.catch { e ->
                Log.e(TAG, "Get offline recording fetch failed on path $path error $e")
                emit(ResultOfRequest.Failure("Get offline recording fetch failed on path $path", e))
            }
        } else {
            flow { emit(ResultOfRequest.Failure("Offline recording entry not found for path $path", null)) }
        }
    }

    suspend fun setTime(deviceId: String, localDateTime: LocalDateTime): ResultOfRequest<Nothing> = withContext(Dispatchers.IO) {
        return@withContext try {
            api.setLocalTime(deviceId, localDateTime)
            ResultOfRequest.Success()
        } catch (e: Exception) {
            ResultOfRequest.Failure("Set time failed", e)
        }
    }

    suspend fun getTime(deviceId: String): ZonedDateTime = withContext(Dispatchers.IO) {
        api.getLocalTimeWithZone(deviceId)
    }

    suspend fun getFtuInfo(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        api.isFtuDone(deviceId)
    }

    suspend fun getUserPhysicalConfiguration(deviceId: String): ResultOfRequest<PolarPhysicalConfiguration?> =
        withContext(Dispatchers.IO) {
            try {
                ResultOfRequest.Success(api.getUserPhysicalConfiguration(deviceId))
            } catch (e: Exception) {
                ResultOfRequest.Failure("Failed to get device physical info", e)
            }
        }

    override fun blePowerStateChanged(powered: Boolean) {
        Log.d(TAG, "Phone BLE is: ${if (powered) "ON" else "OFF"}")
        _isPhoneBlePowerOn.update {
            powered
        }
    }

    override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
        Log.d(TAG, "device ${polarDeviceInfo.deviceId} connected")
        _deviceSupportsSettings.update { polarDeviceInfo.hasSAGRFCFileSystem }
        _deviceConnectionStatus.update { DeviceConnectionState.DeviceConnected(deviceId = polarDeviceInfo.deviceId) }
    }

    override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
        _deviceConnectionStatus.update {
            DeviceConnectionState.DeviceConnecting(
                deviceId = polarDeviceInfo.deviceId
            )
        }
    }

    override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
        _deviceConnectionStatus.update {
            DeviceConnectionState.DeviceNotConnected(
                deviceId = polarDeviceInfo.deviceId
            )
        }

        _availableFeatures.update { AvailableFeatures(deviceId = polarDeviceInfo.deviceId) }
        _sdkModeState.update { SdkMode(deviceId = polarDeviceInfo.deviceId) }
        _deviceInformation.update { DeviceInformation() }
        _sdkFeaturesReady.update { SdkFeaturesReadyEvent() }
    }

    override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
        if (uuid == BleDisClient.SOFTWARE_REVISION_STRING) {
            Log.d(TAG, "disInformationReceived, software revision string $value")
            _deviceInformation.update {
                it.copy(deviceId = identifier, firmwareVersion = value)
            }
        }
    }

    override fun disInformationReceived(identifier: String, disInfo: DisInfo) {
        // Not implemented
    }

    override fun htsNotificationReceived(identifier: String, data: PolarHealthThermometerData) {
        // Not implemented
    }

    override fun batteryLevelReceived(identifier: String, level: Int) {
        _deviceInformation.update {
            it.copy(deviceId = identifier, batteryLevel = level)
        }
    }

    override fun batteryChargingStatusReceived(identifier: String, chargingStatus: ChargeState) {
        _deviceInformation.update {
            it.copy(deviceId = identifier, batteryChargingStatus = chargingStatus)
        }
    }

    override fun powerSourcesStateReceived(identifier: String, powerSourcesState: PowerSourcesState) {
        _deviceInformation.update {
            it.copy(deviceId = identifier, powerSourcesState = powerSourcesState)
        }
    }

    override fun bleSdkFeaturesReadiness(identifier: String, ready: List<PolarBleApi.PolarBleSdkFeature>, unavailable: List<PolarBleApi.PolarBleSdkFeature>) {
        Log.d(TAG, "Features readiness. Ready: $ready, Unavailable: $unavailable")
        _sdkFeaturesReady.update { current ->
            val merged = (current.readyFeatures + ready).distinct()
            SdkFeaturesReadyEvent(deviceId = identifier, readyFeatures = merged)
        }

        if (ready.contains(PolarBleApi.PolarBleSdkFeature.FEATURE_HR)) {
            repositoryScope.launch {
                try {
                    val types = api.getAvailableHRServiceDataTypes(identifier)
                    Log.d(TAG, "Available online streaming data: $types")
                    updateOnlineStreamDataTypes(identifier, types)
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to check if HR service is available. Reason $e")
                }
            }
        }

        if (ready.contains(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)) {
            repositoryScope.launch {
                try {
                    val types = api.getAvailableOnlineStreamDataTypes(identifier)
                    Log.d(TAG, "Available online streaming data: $types")
                    updateOnlineStreamDataTypes(identifier, types)
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to get available online streaming types. Reason $e")
                }
            }
        }

        if (ready.contains(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING)) {
            repositoryScope.launch {
                try {
                    val types = api.getAvailableOfflineRecordingDataTypes(identifier)
                    Log.d(TAG, "Available offline recording data: $types")
                    updateOfflineStreamDataTypes(identifier, types)
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to get available offline recording types. Reason $e")
                }
            }
        }

        if (ready.contains(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE)) {
            _sdkModeState.update { it.copy(deviceId = identifier, isAvailable = true) }
        }
    }

    override fun bleSdkFeatureReady(identifier: String, feature: PolarBleApi.PolarBleSdkFeature) {
        Log.d(TAG, "feature ready $feature")
        _sdkFeaturesReady.update { current ->
            val merged = (current.readyFeatures + feature).distinct()
            SdkFeaturesReadyEvent(deviceId = identifier, readyFeatures = merged)
        }
    }

    fun isFeatureReady(deviceId: String, feature: PolarBleApi.PolarBleSdkFeature): Boolean {
        return api.isFeatureReady(deviceId, feature)
    }

    fun getDeviceName(deviceId: String): String? {
        return api.getDeviceName(deviceId)
    }

    private fun updateOnlineStreamDataTypes(identifier: String, features: Set<PolarBleApi.PolarDeviceDataType>) {
        val allFeatures = _availableFeatures.value.availableStreamingFeatures.clone()
        for (feature in features) {
            allFeatures[feature] = true
        }

        _availableFeatures.update {
            it.copy(
                deviceId = identifier,
                availableStreamingFeatures = allFeatures
            )
        }
    }

    private fun updateOfflineStreamDataTypes(identifier: String, features: Set<PolarBleApi.PolarDeviceDataType>) {
        val allFeatures = _availableFeatures.value.availableOfflineFeatures.clone()
        for (feature in features) {
            allFeatures[feature] = true
        }

        _availableFeatures.update {
            it.copy(
                deviceId = identifier,
                availableOfflineFeatures = allFeatures
            )
        }
    }

    fun sdkShutDown() {
        api.shutDown()
        repositoryScope.cancel()
    }

    suspend fun isSdkModeEnabled(deviceId: String) = withContext(Dispatchers.IO) {
        if (_sdkModeState.value.isAvailable) {
            return@withContext try {
                val isEnabled = api.isSDKModeEnabled(deviceId)
                val state = if (isEnabled) SdkMode.STATE.ENABLED else SdkMode.STATE.DISABLED
                _sdkModeState.update { it.copy(deviceId = deviceId, sdkModeState = state) }
                ResultOfRequest.Success(isEnabled)
            } catch (e: Exception) {
                ResultOfRequest.Failure("SDK mode status request failed", e)
            }
        } else {
            Log.d(TAG, "SDK mode is not available")
            return@withContext ResultOfRequest.Failure("SDK mode not available", null)
        }
    }

    fun sdkModeToggle(deviceId: String) {
        if (_sdkModeState.value.isAvailable) {
            when (_sdkModeState.value.sdkModeState) {
                SdkMode.STATE.ENABLED -> {
                    _sdkModeState.update { it.copy(deviceId = deviceId, sdkModeState = SdkMode.STATE.STATE_CHANGE_IN_PROGRESS) }
                    repositoryScope.launch {
                        try {
                            api.disableSDKMode(deviceId)
                            _sdkModeState.update { it.copy(deviceId = deviceId, sdkModeState = SdkMode.STATE.DISABLED) }
                        } catch (e: Exception) {
                            _sdkModeState.update { it.copy(deviceId = deviceId, sdkModeState = SdkMode.STATE.ENABLED) }
                            Log.e(TAG, "SDK mode disable failed: $e")
                        }
                    }
                }
                SdkMode.STATE.DISABLED -> {
                    _sdkModeState.update { it.copy(deviceId = deviceId, sdkModeState = SdkMode.STATE.STATE_CHANGE_IN_PROGRESS) }
                    repositoryScope.launch {
                        try {
                            api.enableSDKMode(deviceId)
                            _sdkModeState.update { it.copy(deviceId = deviceId, sdkModeState = SdkMode.STATE.ENABLED) }
                        } catch (e: Exception) {
                            _sdkModeState.update { it.copy(deviceId = deviceId, sdkModeState = SdkMode.STATE.DISABLED) }
                            Log.e(TAG, "SDK mode enable failed: $e")
                        }
                    }
                }
                SdkMode.STATE.STATE_CHANGE_IN_PROGRESS -> {}
            }
        } else {
            Log.e(TAG, "SDK mode is not available")
        }
    }

    fun setSdkModeLedConfig(deviceId: String) {
        repositoryScope.launch {
            try {
                api.setLedConfig(deviceId, LedConfig(
                    sdkModeState.value.sdkModeLedAnimation != SdkMode.STATE.ENABLED,
                    sdkModeState.value.ppiModeLedAnimation == SdkMode.STATE.ENABLED
                ))
                _sdkModeState.update {
                    val newState = if (sdkModeState.value.sdkModeLedAnimation == SdkMode.STATE.ENABLED) SdkMode.STATE.DISABLED else SdkMode.STATE.ENABLED
                    it.copy(deviceId = deviceId, sdkModeLedAnimation = newState)
                }
            } catch (e: Exception) {
                Log.e(TAG, "SDK Mode LED animation change failed: $e")
            }
        }
    }

    fun setPpiModeLedConfig(deviceId: String) {
        repositoryScope.launch {
            try {
                api.setLedConfig(deviceId, LedConfig(
                    sdkModeState.value.sdkModeLedAnimation == SdkMode.STATE.ENABLED,
                    sdkModeState.value.ppiModeLedAnimation != SdkMode.STATE.ENABLED
                ))
                _sdkModeState.update {
                    val newState = if (sdkModeState.value.ppiModeLedAnimation == SdkMode.STATE.ENABLED) SdkMode.STATE.DISABLED else SdkMode.STATE.ENABLED
                    it.copy(deviceId = deviceId, ppiModeLedAnimation = newState)
                }
            } catch (e: Exception) {
                Log.e(TAG, "PPI Mode LED animation change failed: $e")
            }
        }
    }

    suspend fun doRestart(deviceId: String) = withContext(Dispatchers.IO) { api.doRestart(deviceId) }

    suspend fun doFactoryReset(deviceId: String) = withContext(Dispatchers.IO) { api.doFactoryReset(deviceId) }

    suspend fun setWarehouseSleep(deviceId: String) = withContext(Dispatchers.IO) { api.setWareHouseSleep(deviceId) }

    suspend fun turnDeviceOff(deviceId: String) = withContext(Dispatchers.IO) { api.turnDeviceOff(deviceId) }

    fun observeDeviceToHostNotifications(deviceId: String): Flow<com.polar.sdk.api.PolarD2HNotificationData> {
        return api.observeDeviceToHostNotifications(deviceId)
    }

    fun doFirmwareUpdate(deviceId: String, firmwareUrl: String = ""): Flow<FirmwareUpdateStatus> {
        return api.updateFirmware(deviceId, firmwareUrl)
            .onStart { Log.d(TAG, "Firmware update started for device: $deviceId") }
            .onEach { status -> Log.d(TAG, "Firmware update status: $status for device: $deviceId") }
            .catch { throwable ->
                Log.e(TAG, "Error during firmware update for device: $deviceId", throwable)
                throw throwable
            }
    }

    fun checkFirmwareUpdate(deviceId: String): Flow<CheckFirmwareUpdateStatus> {
        return api.checkFirmwareUpdate(deviceId)
            .catch { throwable ->
                Log.e(TAG, "Error checking firmware update for device: $deviceId", throwable)
                throw throwable
            }
    }

    suspend fun getAvailableStreamSettings(deviceId: String, feature: PolarBleApi.PolarDeviceDataType): PolarSensorSetting =
        api.requestStreamSettings(deviceId, feature)

    suspend fun requestFullStreamSettings(deviceId: String, feature: PolarBleApi.PolarDeviceDataType): PolarSensorSetting =
        api.requestFullStreamSettings(deviceId, feature)

    suspend fun getOfflineRecSettings(deviceId: String, feature: PolarBleApi.PolarDeviceDataType): PolarSensorSetting =
        api.requestOfflineRecordingSettings(deviceId, feature)

    suspend fun getFullOfflineRecSettings(deviceId: String, feature: PolarBleApi.PolarDeviceDataType): PolarSensorSetting =
        api.requestFullOfflineRecordingSettings(deviceId, feature)

    fun startEcgStream(deviceId: String, polarSensorSetting: PolarSensorSetting): Flow<PolarEcgData> =
        api.startEcgStreaming(deviceId, polarSensorSetting)

    fun startGyroStreaming(deviceId: String, polarSensorSetting: PolarSensorSetting): Flow<PolarGyroData> =
        api.startGyroStreaming(deviceId, polarSensorSetting)

    fun disconnectFromDevice(deviceId: String) {
        _deviceConnectionStatus.update { DeviceConnectionState.DeviceDisconnecting(deviceId = deviceId) }
        api.disconnectFromDevice(deviceId)
    }

    fun searchForDevice(withPrefix: String?): Flow<PolarDeviceInfo> {
        return api.searchForDevice(withPrefix)
    }

    fun connectToDevice(deviceId: String) { api.connectToDevice(deviceId) }

    fun startMagnetometerStream(deviceId: String, polarSensorSetting: PolarSensorSetting): Flow<PolarMagnetometerData> =
        api.startMagnetometerStreaming(deviceId, polarSensorSetting)

    fun startPpiStream(deviceId: String): Flow<PolarPpiData> = api.startPpiStreaming(deviceId)

    fun startPpgStream(deviceId: String, polarSensorSetting: PolarSensorSetting): Flow<PolarPpgData> =
        api.startPpgStreaming(deviceId, polarSensorSetting)

    fun startPressureStream(deviceId: String, polarSensorSetting: PolarSensorSetting): Flow<PolarPressureData> =
        api.startPressureStreaming(deviceId, polarSensorSetting)

    fun startLocationStream(deviceId: String, polarSensorSetting: PolarSensorSetting): Flow<PolarLocationData> =
        api.startLocationStreaming(deviceId, polarSensorSetting)

    fun startTemperatureStreaming(deviceId: String, polarSensorSetting: PolarSensorSetting): Flow<PolarTemperatureData> =
        api.startTemperatureStreaming(deviceId, polarSensorSetting)

    fun startSkinTemperatureStreaming(deviceId: String, polarSensorSetting: PolarSensorSetting): Flow<PolarTemperatureData> =
        api.startSkinTemperatureStreaming(deviceId, polarSensorSetting)

    fun startAccStreaming(deviceId: String, polarSensorSetting: PolarSensorSetting): Flow<PolarAccelerometerData> =
        api.startAccStreaming(deviceId, polarSensorSetting)

    fun startHrStreaming(deviceId: String): Flow<PolarHrData> = api.startHrStreaming(deviceId)

    fun stopStreaming(deviceId: String, type: PmdMeasurementType) { api.stopStreaming(deviceId, type) }

    suspend fun stopHrStreaming(deviceId: String) = withContext(Dispatchers.IO) { api.stopHrStreaming(deviceId) }

    suspend fun setLedConfig(deviceId: String, enableSdkModeLed: Boolean, enablePpiModeLed: Boolean) =
        withContext(Dispatchers.IO) { api.setLedConfig(deviceId, LedConfig(enableSdkModeLed, enablePpiModeLed)) }

    suspend fun startOfflineRecording(deviceId: String, feature: PolarBleApi.PolarDeviceDataType, polarSensorSetting: PolarSensorSetting? = null): ResultOfRequest<Nothing> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Start $feature offline recording with settings: ${polarSensorSetting?.settings}")
            val secret = security.getSecretKey(deviceId)?.let { PolarRecordingSecret(it.encoded) }
            api.startOfflineRecording(deviceId, feature, polarSensorSetting, secret)
            ResultOfRequest.Success()
        } catch (e: Exception) {
            ResultOfRequest.Failure("Offline recording start failed", e)
        }
    }

    suspend fun stopOfflineRecording(deviceId: String, feature: PolarBleApi.PolarDeviceDataType): ResultOfRequest<Nothing> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Stop offline recording. Feature $feature, Device $deviceId")
            api.stopOfflineRecording(deviceId, feature)
            ResultOfRequest.Success()
        } catch (e: Exception) {
            ResultOfRequest.Failure("Offline recording stop failed", e)
        }
    }

    suspend fun requestOfflineRecordingStatus(deviceId: String): ResultOfRequest<List<PolarBleApi.PolarDeviceDataType>> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.getOfflineRecordingStatus(deviceId))
        } catch (e: Exception) {
            ResultOfRequest.Failure("Offline recording status fetch failed", e)
        }
    }

    suspend fun getOfflineRecordingTriggerStatus(deviceId: String): ResultOfRequest<PolarOfflineRecordingTrigger> = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = api.getOfflineRecordingTriggerSetup(deviceId)
            _triggerState.update { OfflineRecTriggerStatus(deviceId = deviceId, result) }
            ResultOfRequest.Success(result)
        } catch (e: Exception) {
            ResultOfRequest.Failure("Offline recording trigger status get failed", e)
        }
    }

    suspend fun setOfflineRecordingTrigger(deviceId: String, trigger: PolarOfflineRecordingTrigger): ResultOfRequest<Int> = withContext(Dispatchers.IO) {
        return@withContext try {
            val secret = security.getSecretKey(deviceId)?.let { PolarRecordingSecret(it.encoded) }
            api.setOfflineRecordingTrigger(identifier = deviceId, trigger = trigger, secret = secret)
            _triggerState.update { OfflineRecTriggerStatus(deviceId = deviceId, trigger) }
            ResultOfRequest.Success()
        } catch (e: Exception) {
            getOfflineRecordingTriggerStatus(deviceId)
            ResultOfRequest.Failure("Offline recording trigger set failed", e)
        }
    }

    suspend fun isSecurityEnabled(deviceId: String) = withContext(Dispatchers.IO) {
        _isOfflineRecordingSecurityEnabled.update { security.hasKey(deviceId) }
    }

    suspend fun getMultiBleModeEnabled(deviceId: String) = withContext(Dispatchers.IO) {
        _isMultiBleModeEnabled.update { getBleMultiConnectionMode(deviceId) }
    }

    suspend fun toggleSecurity(deviceId: String, enable: Boolean) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Toggled security to $enable")

        if (enable) {
            security.generateKey(deviceId)
        } else {
            security.removeKey(deviceId)
        }
    }

    suspend fun getLogConfig(deviceId: String): ResultOfRequest<LogConfig> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.getLogConfig(deviceId))
        } catch (e: Exception) {
            ResultOfRequest.Failure("Failed to get LogConfig", e)
        }
    }

    suspend fun setLogConfig(deviceId: String, logConfig: LogConfig): ResultOfRequest<Int> = withContext(Dispatchers.IO) {
        return@withContext try {
            api.setLogConfig(deviceId, logConfig)
            ResultOfRequest.Success()
        } catch (e: Exception) {
            ResultOfRequest.Failure("Failed to set LogConfig", e)
        }
    }

    suspend fun fetchErrorLog(deviceId: String): ResultOfRequest<Errorlog> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(Errorlog(api.getFile(deviceId, Errorlog.ERRORLOG_FILENAME)))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    fun observeSleepRecordingState(deviceId: String): Flow<Boolean> {
        return api.observeSleepRecordingState(deviceId).map { it.last() }
    }

    suspend fun forceStopSleep(deviceId: String): ResultOfRequest<Boolean?> = withContext(Dispatchers.IO) {
        return@withContext try {
            api.stopSleepRecording(deviceId)
            val state = api.getSleepRecordingState(deviceId)
            if (state) {
                ResultOfRequest.Failure("Stopping sleep failed for $deviceId", throwable = null)
            } else {
                ResultOfRequest.Success(state)
            }
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun getSleepRecordingState(deviceId: String): ResultOfRequest<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.getSleepRecordingState(deviceId))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun getSleepData(deviceId: String, from: LocalDate, to: LocalDate): ResultOfRequest<List<PolarSleepData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.getSleep(deviceId, from, to))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun getStepsData(deviceId: String, from: LocalDate, to: LocalDate): ResultOfRequest<List<PolarStepsData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.getSteps(deviceId, from, to))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun getCaloriesData(deviceId: String, from: LocalDate, to: LocalDate, caloriesType: CaloriesType): ResultOfRequest<List<PolarCaloriesData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.getCalories(deviceId, from, to, caloriesType))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun get247HrSamplesData(deviceId: String, from: LocalDate, to: LocalDate): ResultOfRequest<List<Polar247HrSamplesData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.get247HrSamples(deviceId, from, to))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun getNightlyRechargeData(deviceId: String, from: LocalDate, to: LocalDate): ResultOfRequest<List<PolarNightlyRechargeData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.getNightlyRecharge(deviceId, from, to))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun get247PPiSamples(deviceId: String, from: LocalDate, to: LocalDate): ResultOfRequest<List<Polar247PPiSamplesData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.get247PPiSamples(deviceId, from, to))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun getDeviceUserSettings(deviceId: String): ResultOfRequest<PolarUserDeviceSettings> = try {
        val result = withContext(Dispatchers.IO) {
            api.getUserDeviceSettings(deviceId)
        }
        ResultOfRequest.Success(result)
    } catch (e: Throwable) {
        Log.e(TAG, "Failed to get Device User Settings: ${e.message}", e)
        ResultOfRequest.Failure(e.message ?: "Failed to load device settings", e)
    }

    suspend fun deleteDeviceData(deviceId: String, storedDeviceDataType: PolarBleApi.PolarStoredDataType, until: LocalDate): ResultOfRequest<Int> = withContext(Dispatchers.IO) {
        return@withContext try {
            api.deleteStoredDeviceData(deviceId, storedDeviceDataType, until)
            ResultOfRequest.Success()
        } catch (e: Exception) {
            ResultOfRequest.Failure("Failed to delete $storedDeviceDataType files from device", e)
        }
    }

    suspend fun deleteDeviceDateFolders(deviceId: String, fromDate: LocalDate?, toDate: LocalDate?): ResultOfRequest<Nothing> = withContext(Dispatchers.IO) {
        return@withContext try {
            api.deleteDeviceDateFolders(deviceId, fromDate, toDate)
            ResultOfRequest.Success()
        } catch (e: Exception) {
            ResultOfRequest.Failure("Failed to delete date folders from device", e)
        }
    }

    suspend fun deleteTelemetryData(deviceId: String): ResultOfRequest<Nothing> = withContext(Dispatchers.IO) {
        return@withContext try {
            api.deleteTelemetryData(deviceId)
            ResultOfRequest.Success()
        } catch (e: Exception) {
            ResultOfRequest.Failure("Failed to delete telemetry data files from device", e)
        }
    }

    @OptIn(ExperimentalTime::class)
    suspend fun deleteTrainingSession(deviceId: String, path: String): ResultOfRequest<Nothing> = withContext(Dispatchers.IO) {
        val trainingSessionReferenceEntry = trainingSessionReferenceCache[deviceId]?.find { it.path == path }
        trainingSessionReferenceEntry?.let { trainingSessionEntry ->
            return@withContext try {
                val result = measureTimedValue { api.deleteTrainingSession(deviceId, trainingSessionEntry) }
                Log.d(TAG, "delete of training session $path took ${TimeUnit.MICROSECONDS.toSeconds(result.duration.inWholeMicroseconds)} seconds")
                trainingSessionReferenceCache[deviceId]?.remove(trainingSessionEntry)
                ResultOfRequest.Success()
            } catch (e: Exception) {
                ResultOfRequest.Failure("Failed to remove training session $path", e)
            }
        }
        ResultOfRequest.Failure("Tried to remove \"$path\", but no matching entry in repository", null)
    }

    suspend fun getSkinTemperatureData(deviceId: String, from: LocalDate, to: LocalDate): ResultOfRequest<List<PolarSkinTemperatureData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.getSkinTemperature(deviceId, from, to))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    fun getTrainingSessionReferences(deviceId: String, fromDate: LocalDate, toDate: LocalDate): Flow<PolarTrainingSessionReference> {
        Log.d(TAG, "getTrainingSessionReferences from device $deviceId")
        return api.getTrainingSessionReferences(deviceId, fromDate, toDate)
            .onStart { trainingSessionReferenceCache[deviceId] = mutableListOf() }
            .onEach { trainingSessionReferenceCache[deviceId]?.add(it) }
    }

    fun getTrainingSessionWithProgress(deviceId: String, path: String): Flow<ResultOfRequest<PolarTrainingSessionFetchResult>> {
        Log.d(TAG, "getTrainingSessionWithProgress from device $deviceId in $path")
        val trainingSessionReference = trainingSessionReferenceCache[deviceId]?.find { it.path == path }
        return if (trainingSessionReference != null) {
            api.getTrainingSessionWithProgress(deviceId, trainingSessionReference)
                .map<PolarTrainingSessionFetchResult, ResultOfRequest<PolarTrainingSessionFetchResult>> { result -> ResultOfRequest.Success(result) }
                .catch { e ->
                    Log.e(TAG, "getTrainingSessionWithProgress failed on path $path error $e")
                    emit(ResultOfRequest.Failure("getTrainingSessionWithProgress failed on path $path", e))
                }
        } else {
            flow {
                Log.e(TAG, "Training session reference not found for path $path")
                emit(ResultOfRequest.Failure("Training session reference not found for path $path", null))
            }
        }
    }

    suspend fun getActiveTimeData(deviceId: String, from: LocalDate, to: LocalDate): ResultOfRequest<List<PolarActiveTimeData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.getActiveTime(deviceId, from, to))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun waitForConnection(deviceId: String) = withContext(Dispatchers.IO) { api.waitForConnection(deviceId) }

    suspend fun getDiskSpace(deviceId: String): ResultOfRequest<PolarDiskSpaceData> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.getDiskSpace(deviceId))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun setBleMultiConnectionMode(deviceId: String, enable: Boolean) =
        withContext(Dispatchers.IO) { api.setMultiBLEConnectionMode(deviceId, enable) }

    suspend fun getActivitySamplesData(deviceId: String, from: LocalDate, to: LocalDate): ResultOfRequest<List<PolarActivitySamplesDayData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.getActivitySampleData(deviceId, from, to))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun getDailySummaryData(deviceId: String, from: LocalDate, to: LocalDate): ResultOfRequest<List<PolarDailySummaryData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.getDailySummaryData(deviceId, from, to))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    private suspend fun getBleMultiConnectionMode(deviceId: String): Boolean {
        return try {
            val result = api.getMultiBLEConnectionMode(deviceId)
            _isMultiBleModeEnabled.update { result }
            result
        } catch (e: Exception) {
            Log.e(TAG, "getBleMultiConnectionMode failed. Error $e")
            false
        }
    }

    suspend fun setTelemetryEnabled(deviceId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        api.setTelemetryEnabled(deviceId, enabled)
    }

    suspend fun getUserDeviceSettings(deviceId: String): PolarUserDeviceSettings =
        withContext(Dispatchers.IO) { api.getUserDeviceSettings(identifier = deviceId) }

    suspend fun setUserDeviceLocation(deviceId: String, location: Int) =
        withContext(Dispatchers.IO) { api.setUserDeviceLocation(deviceId, location) }

    suspend fun setUsbConnectionMode(deviceId: String, enabled: Boolean) =
        withContext(Dispatchers.IO) { api.setUsbConnectionMode(deviceId, enabled) }

    suspend fun setDaylightSavingTime(deviceId: String) =
        withContext(Dispatchers.IO) { api.setDaylightSavingTime(deviceId) }

    suspend fun setAutomaticTrainingDetectionSettings(deviceId: String, atdEnabled: Boolean, sensitivity: Int, minDuration: Int) = withContext(Dispatchers.IO) {
        api.setAutomaticTrainingDetectionSettings(
            identifier = deviceId,
            automaticTrainingDetectionMode = atdEnabled,
            automaticTrainingDetectionSensitivity = sensitivity,
            minimumTrainingDurationSeconds = minDuration
        )
    }

    fun listenHrBroadcasts(excludeDeviceIds: Set<String>?): Flow<PolarHrBroadcastData> {
        return api.startListenForPolarHrBroadcasts(null)
            .filter { hrData -> excludeDeviceIds?.contains(hrData.polarDeviceInfo.deviceId) == false }
            .onEach { hrData -> Log.d(TAG, "HR Broadcast received: Device: ${hrData.polarDeviceInfo.deviceId}, HR: ${hrData.hr}") }
            .catch { error -> Log.e(TAG, "HR Broadcast error: ${error.message}", error); throw error }
    }

    suspend fun setAutosFilesEnabled(deviceId: String, enabled: Boolean) =
        withContext(Dispatchers.IO) { api.setAutomaticOHRMeasurementEnabled(deviceId, enabled) }

    suspend fun readFile(deviceId: String, filePath: String): ResultOfRequest<ByteArray> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.readFile(deviceId, filePath))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun listFiles(deviceId: String, filePath: String, deleteDeep: Boolean): ResultOfRequest<List<String>> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.getFileList(deviceId, filePath, deleteDeep))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun writeFile(deviceId: String, filePath: String, fileData: Any) = withContext(Dispatchers.IO) {
        return@withContext try {
            api.writeFile(deviceId, filePath, fileData as ByteArray)
            ResultOfRequest.Success(Unit)
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun deleteFile(deviceId: String, filePath: String) = withContext(Dispatchers.IO) {
        return@withContext try {
            api.deleteFileOrDirectory(deviceId, filePath)
            ResultOfRequest.Success(Unit)
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun getChargeInformation(deviceId: String) = withContext(Dispatchers.IO) {
        return@withContext try {
            val chargerStatusInfo = api.getChargerState(deviceId)
            val batteryLevelInfo = api.getBatteryLevel(deviceId)
            chargeInfo = ChargeInformation(batteryLevel = batteryLevelInfo, chargerStatus = chargerStatusInfo)
            ResultOfRequest.Success(chargeInfo)
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun isOfflineExerciseV2Supported(deviceId: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val supported = (api as? com.polar.sdk.api.PolarOfflineExerciseV2Api)?.let { exerciseApi ->
                    try {
                        val result = exerciseApi.isOfflineExerciseV2Supported(deviceId)
                        Log.d(TAG, "Offline Exercise V2 support check SUCCESS (immediate): $result")
                        result
                    } catch (e: Exception) {
                        Log.w(TAG, "Immediate check failed (PFTP not ready), retrying after 3s delay...")
                        delay(3000)
                        try {
                            val result = exerciseApi.isOfflineExerciseV2Supported(deviceId)
                            Log.d(TAG, "Offline Exercise V2 support check SUCCESS (after retry): $result")
                            result
                        } catch (retryError: Exception) {
                            Log.e(TAG, "Offline Exercise V2 support check failed even after retry: ${retryError.message}")
                            false
                        }
                    }
                } ?: false
                _offlineExerciseV2Supported.update { current ->
                    current.toMutableMap().also { it[deviceId] = supported }
                }

                supported
            } catch (e: Exception) {
                Log.e(TAG, "Offline Exercise V2 support check failed for $deviceId", e)
                false
            }
        }

    suspend fun checkIfDeviceDisconnectedDueRemovedPairing(deviceId: String) = withContext(Dispatchers.IO) {
        return@withContext try {
            val blePairingErrorStatus = api.checkIfDeviceDisconnectedDueRemovedPairing(deviceId)
            ResultOfRequest.Success(blePairingErrorStatus)
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun getBleSignalStrength(deviceId: String) = withContext(Dispatchers.IO) {
        return@withContext try {
            val bleSignalStrength = api.getRSSIValue(deviceId)
            ResultOfRequest.Success(bleSignalStrength)
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }
}