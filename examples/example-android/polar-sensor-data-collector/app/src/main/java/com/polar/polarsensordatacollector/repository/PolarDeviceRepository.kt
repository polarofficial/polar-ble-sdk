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
import com.polar.polarsensordatacollector.DataCollector
import com.polar.polarsensordatacollector.crypto.SecretKeyManager
import com.polar.sdk.api.DeviceTelemetryConfiguration
import com.polar.sdk.api.DeviceTelemetryEvent
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.PolarDeviceTelemetryType
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
import com.polar.sdk.api.model.PolarDerivedMeasurementSettings
import com.polar.sdk.api.model.PolarDerivedMeasurementSettingsGroup
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionReference
import com.polar.sdk.api.model.activity.PolarActivitySamplesDayData
import com.polar.sdk.api.model.activity.PolarDailySummaryData
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionFetchResult
import com.polar.sdk.api.model.PolarSpo2TestData
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

sealed class DeviceConnectionState(val identifier: String = "") {
    class DeviceConnected(identifier: String) : DeviceConnectionState(identifier)
    class DeviceConnecting(identifier: String) : DeviceConnectionState(identifier)
    class DeviceDisconnecting(identifier: String) : DeviceConnectionState(identifier)
    class DeviceNotConnected(identifier: String = "") : DeviceConnectionState(identifier)
}

data class DeviceInformation(
    val identifier: String = "",
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
    val identifier: String = "",
    val availableStreamingFeatures: EnumMap<PolarBleApi.PolarDeviceDataType, Boolean> = EnumMap(PolarBleApi.PolarDeviceDataType.values().associateWith { false }),
    val availableOfflineFeatures: EnumMap<PolarBleApi.PolarDeviceDataType, Boolean> = EnumMap(PolarBleApi.PolarDeviceDataType.values().associateWith { false }),
)

data class SdkMode(
    val identifier: String = "",
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
    val identifier: String = "",
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

    private val _deviceConnectionStatus: MutableStateFlow<DeviceConnectionState> =
        MutableStateFlow(DeviceConnectionState.DeviceNotConnected())
    val deviceConnectionStatus: StateFlow<DeviceConnectionState> =
        _deviceConnectionStatus.asStateFlow()

    private val _availableFeatures: MutableStateFlow<AvailableFeatures> =
        MutableStateFlow(AvailableFeatures())
    val availableFeatures: StateFlow<AvailableFeatures> = _availableFeatures.asStateFlow()

    private val _sdkModeState = MutableStateFlow(SdkMode())
    val sdkModeState: StateFlow<SdkMode> = _sdkModeState.asStateFlow()

    private val _triggerState = MutableStateFlow(OfflineRecTriggerStatus())
    val triggerState: StateFlow<OfflineRecTriggerStatus> = _triggerState.asStateFlow()

    private val _deviceInformation: MutableStateFlow<DeviceInformation> =
        MutableStateFlow(DeviceInformation())
    val deviceInformation: StateFlow<DeviceInformation> = _deviceInformation.asStateFlow()

    private val _isOfflineRecordingSecurityEnabled: MutableStateFlow<Boolean> =
        MutableStateFlow(false)
    val isOfflineRecordingSecurityEnabled: StateFlow<Boolean> =
        _isOfflineRecordingSecurityEnabled.asStateFlow()

    private val offlineEntryCache: MutableMap<String, MutableList<PolarOfflineRecordingEntry>> =
        mutableMapOf()
    private val trainingSessionReferenceCache: MutableMap<String, MutableList<PolarTrainingSessionReference>> =
        mutableMapOf()

    private val _isMultiBleModeEnabled: MutableStateFlow<Boolean> = MutableStateFlow(false)
    var isMultiBleModeEnabled: StateFlow<Boolean> = _isMultiBleModeEnabled.asStateFlow()

    private val _deviceSupportsSettings: MutableStateFlow<Boolean> = MutableStateFlow(false)
    var deviceSupportsSettings: StateFlow<Boolean> = _deviceSupportsSettings.asStateFlow()

    private val _isSensorInitiatedSecurityModeEnabled: MutableStateFlow<Boolean> =
        MutableStateFlow(false)
    var isSensorInitiatedSecurityModeEnabled: StateFlow<Boolean> =
        _isSensorInitiatedSecurityModeEnabled.asStateFlow()

    private val _isTelemetryAvailable: MutableStateFlow<Boolean> = MutableStateFlow(false)
    var isTelemetryAvailable: StateFlow<Boolean> = _isTelemetryAvailable.asStateFlow()

    private val _isTelemetryEnabled: MutableStateFlow<Boolean> = MutableStateFlow(false)
    var isTelemetryEnabled: StateFlow<Boolean> = _isTelemetryEnabled.asStateFlow()

    private val _offlineExerciseV2Supported: MutableStateFlow<Map<String, Boolean>> =
        MutableStateFlow(emptyMap())
    val offlineExerciseV2Supported: StateFlow<Map<String, Boolean>> =
        _offlineExerciseV2Supported.asStateFlow()

    data class SdkFeaturesReadyEvent(
        val identifier: String = "",
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

    fun listOfflineRecordings(identifier: String): Flow<PolarOfflineRecordingEntry> {
        Log.d(TAG, "listOfflineRecordings from device $identifier")
        return api.listOfflineRecordings(identifier)
            .onStart {
                offlineEntryCache[identifier] = mutableListOf()
            }
            .onEach { entry ->
                offlineEntryCache[identifier]?.add(entry)
            }
    }

    fun getOfflineEntryFromCache(identifier: String, path: String): PolarOfflineRecordingEntry? {
        return offlineEntryCache[identifier]?.find { it.path == path }
    }

    private fun saveData(identifier: String, offlineRecData: PolarOfflineRecordingData): Uri {
        val logIdentifier = getDeviceName(identifier) ?: identifier
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
                    collector.logPpi(
                        sample.ppi,
                        sample.errorEstimate,
                        sample.blockerBit,
                        sample.skinContactStatus,
                        sample.skinContactSupported,
                        sample.hr,
                        sample.timeStamp
                    )
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
                    collector.logTemperature(
                        timeStamp = sample.timeStamp,
                        temperature = sample.temperature
                    )
                }
                return collector.finalizeAllStreams().toList().first()
            }

            is PolarOfflineRecordingData.SkinTemperatureOfflineRecording -> {
                collector.startSkinTemperatureLog(
                    logIdentifier,
                    startTime = offlineRecData.startTime
                )
                for (sample in offlineRecData.data.samples) {
                    collector.logSkinTemperature(
                        timeStamp = sample.timeStamp,
                        temperature = sample.temperature
                    )
                }
                return collector.finalizeAllStreams().toList().first()
            }

            is PolarOfflineRecordingData.DerivedAccOfflineRecording -> {
                collector.startDerivedAccLog(logIdentifier, startTime = offlineRecData.startTime)
                for (sample in offlineRecData.data.samples) {
                    collector.logDerivedSample(sample)
                }
                return collector.finalizeAllStreams().toList().first()
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    suspend fun deleteRecording(identifier: String, path: String): ResultOfRequest<Nothing> =
        withContext(Dispatchers.IO) {
            val offlineRecEntry = offlineEntryCache[identifier]?.find { it.path == path }
            offlineRecEntry?.let { offlineEntry ->
                return@withContext try {
                    val result = measureTimedValue {
                        api.removeOfflineRecord(identifier, offlineEntry)
                    }
                    Log.d(
                        TAG,
                        "delete of recording $path took ${TimeUnit.MICROSECONDS.toSeconds(result.duration.inWholeMicroseconds)} seconds"
                    )
                    offlineEntryCache[identifier]?.remove(offlineRecEntry)
                    ResultOfRequest.Success()
                } catch (e: Exception) {
                    ResultOfRequest.Failure("Failed to remove ${offlineEntry.path}", e)
                }
            }
            ResultOfRequest.Failure(
                "Tried to remove \"$path\", but no matching entry in repository",
                null
            )
        }

    fun getOfflineRecordingWithProgress(
        identifier: String,
        path: String
    ): Flow<ResultOfRequest<OfflineRecordingData>> {
        Log.d(TAG, "getOfflineRecordingWithProgress from device $identifier in $path")

        val offlineRecEntry = offlineEntryCache[identifier]?.find { it.path == path }

        return if (offlineRecEntry != null) {
            flow<ResultOfRequest<OfflineRecordingData>> {
                val startTime = System.currentTimeMillis()

                api.getOfflineRecordWithProgress(
                    identifier,
                    offlineRecEntry,
                    security.getSecretKey(identifier)?.let { PolarRecordingSecret(it.encoded) }
                ).collect { result ->
                    when (result) {
                        is PolarOfflineRecordingResult.Progress -> {
                            Log.d(
                                TAG,
                                "Progress: ${result.progressPercent}% (${result.bytesDownloaded}/${result.totalBytes} bytes)"
                            )
                            emit(
                                ResultOfRequest.Success(
                                    value = null,
                                    progress = ProgressInfo(
                                        bytesDownloaded = result.bytesDownloaded,
                                        totalBytes = result.totalBytes,
                                        progressPercent = result.progressPercent
                                    )
                                )
                            )
                        }

                        is PolarOfflineRecordingResult.Complete -> {
                            val downloadDuration = (System.currentTimeMillis() - startTime) / 1024.0
                            val downloadSpeed =
                                if (downloadDuration > 0) (offlineRecEntry.size / 1024.0) / downloadDuration else 0.0
                            val uri = saveData(identifier, result.data)
                            emit(
                                ResultOfRequest.Success(
                                    value = OfflineRecordingData(
                                        data = result.data,
                                        uri = uri,
                                        fileSize = offlineRecEntry.size,
                                        downLoadSpeed = downloadSpeed
                                    ),
                                    progress = null
                                )
                            )
                        }
                    }
                }
            }.catch { e ->
                Log.e(TAG, "Get offline recording fetch failed on path $path error $e")
                emit(ResultOfRequest.Failure("Get offline recording fetch failed on path $path", e))
            }
        } else {
            flow {
                emit(
                    ResultOfRequest.Failure(
                        "Offline recording entry not found for path $path",
                        null
                    )
                )
            }
        }
    }

    suspend fun setTime(identifier: String, localDateTime: LocalDateTime): ResultOfRequest<Nothing> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                api.setLocalTime(identifier, localDateTime)
                ResultOfRequest.Success()
            } catch (e: Exception) {
                ResultOfRequest.Failure("Set time failed", e)
            }
        }

    suspend fun getTime(identifier: String): ZonedDateTime = withContext(Dispatchers.IO) {
        api.getLocalTimeWithZone(identifier)
    }

    suspend fun getFtuInfo(identifier: String): Boolean = withContext(Dispatchers.IO) {
        api.isFtuDone(identifier)
    }

    suspend fun startTelemetry(
        telemetryType: PolarDeviceTelemetryType,
        identifier: String
    ): Flow<DeviceTelemetryEvent> {
        _isTelemetryEnabled.value = true
        return api.startTelemetry(identifier, telemetryType)
    }

    suspend fun stopTelemetry(telemetryType: PolarDeviceTelemetryType, identifier: String) {
        api.stopTelemetry(identifier, telemetryType,)
        _isTelemetryEnabled.value = true
    }

    fun getDeviceTelemetryConfiguration(telemetryType: PolarDeviceTelemetryType, identifier: String): DeviceTelemetryConfiguration {
        return api.getDeviceTelemetryConfiguration(identifier, telemetryType)
    }

    suspend fun getUserPhysicalConfiguration(identifier: String): ResultOfRequest<PolarPhysicalConfiguration?> =
        withContext(Dispatchers.IO) {
            try {
                ResultOfRequest.Success(api.getUserPhysicalConfiguration(identifier))
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
        _deviceConnectionStatus.update { DeviceConnectionState.DeviceConnected(identifier = polarDeviceInfo.deviceId) }
    }

    override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
        _deviceConnectionStatus.update {
            DeviceConnectionState.DeviceConnecting(
                identifier = polarDeviceInfo.deviceId
            )
        }
    }

    override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
        _deviceConnectionStatus.update {
            DeviceConnectionState.DeviceNotConnected(
                identifier = polarDeviceInfo.deviceId
            )
        }

        _availableFeatures.update { AvailableFeatures(identifier = polarDeviceInfo.deviceId) }
        _sdkModeState.update { SdkMode(identifier = polarDeviceInfo.deviceId) }
        _deviceInformation.update { DeviceInformation() }
        _sdkFeaturesReady.update { SdkFeaturesReadyEvent() }
    }

    override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
        if (uuid == BleDisClient.SOFTWARE_REVISION_STRING) {
            Log.d(TAG, "disInformationReceived, software revision string $value")
            _deviceInformation.update {
                it.copy(identifier = identifier, firmwareVersion = value)
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
            it.copy(identifier = identifier, batteryLevel = level)
        }
    }

    override fun batteryChargingStatusReceived(identifier: String, chargingStatus: ChargeState) {
        _deviceInformation.update {
            it.copy(identifier = identifier, batteryChargingStatus = chargingStatus)
        }
    }

    override fun powerSourcesStateReceived(identifier: String, powerSourcesState: PowerSourcesState) {
        _deviceInformation.update {
            it.copy(identifier = identifier, powerSourcesState = powerSourcesState)
        }
    }

    override fun bleSdkFeaturesReadiness(identifier: String, ready: List<PolarBleApi.PolarBleSdkFeature>, unavailable: List<PolarBleApi.PolarBleSdkFeature>) {
        Log.d(TAG, "Features readiness. Ready: $ready, Unavailable: $unavailable")
        _sdkFeaturesReady.update { current ->
            val merged = (current.readyFeatures + ready).distinct()
            SdkFeaturesReadyEvent(identifier = identifier, readyFeatures = merged)
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
            _sdkModeState.update { it.copy(identifier = identifier, isAvailable = true) }
        }

        if (ready.contains(PolarBleApi.PolarBleSdkFeature.FEATURE_TELEMETRY)) {
            _isTelemetryAvailable.update { it }
        }
    }

    override fun bleSdkFeatureReady(identifier: String, feature: PolarBleApi.PolarBleSdkFeature) {
        Log.d(TAG, "feature ready $feature")
        _sdkFeaturesReady.update { current ->
            val merged = (current.readyFeatures + feature).distinct()
            SdkFeaturesReadyEvent(identifier = identifier, readyFeatures = merged)
        }
    }

    fun isFeatureReady(identifier: String, feature: PolarBleApi.PolarBleSdkFeature): Boolean {
        return api.isFeatureReady(identifier, feature)
    }

    fun getDeviceName(identifier: String): String? {
        return api.getDeviceName(identifier)
    }

    private fun updateOnlineStreamDataTypes(identifier: String, features: Set<PolarBleApi.PolarDeviceDataType>) {
        val allFeatures = _availableFeatures.value.availableStreamingFeatures.clone()
        for (feature in features) {
            allFeatures[feature] = true
        }

        _availableFeatures.update {
            it.copy(
                identifier = identifier,
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
                identifier = identifier,
                availableOfflineFeatures = allFeatures
            )
        }
    }

    fun sdkShutDown() {
        api.shutDown()
        repositoryScope.cancel()
    }

    suspend fun isSdkModeEnabled(identifier: String) = withContext(Dispatchers.IO) {
        if (_sdkModeState.value.isAvailable) {
            return@withContext try {
                val isEnabled = api.isSDKModeEnabled(identifier)
                val state = if (isEnabled) SdkMode.STATE.ENABLED else SdkMode.STATE.DISABLED
                _sdkModeState.update { it.copy(identifier = identifier, sdkModeState = state) }
                ResultOfRequest.Success(isEnabled)
            } catch (e: Exception) {
                ResultOfRequest.Failure("SDK mode status request failed", e)
            }
        } else {
            Log.d(TAG, "SDK mode is not available")
            return@withContext ResultOfRequest.Failure("SDK mode not available", null)
        }
    }

    fun sdkModeToggle(identifier: String) {
        if (_sdkModeState.value.isAvailable) {
            when (_sdkModeState.value.sdkModeState) {
                SdkMode.STATE.ENABLED -> {
                    _sdkModeState.update { it.copy(identifier = identifier, sdkModeState = SdkMode.STATE.STATE_CHANGE_IN_PROGRESS) }
                    repositoryScope.launch {
                        try {
                            api.disableSDKMode(identifier)
                            _sdkModeState.update { it.copy(identifier = identifier, sdkModeState = SdkMode.STATE.DISABLED) }
                        } catch (e: Exception) {
                            _sdkModeState.update { it.copy(identifier = identifier, sdkModeState = SdkMode.STATE.ENABLED) }
                            Log.e(TAG, "SDK mode disable failed: $e")
                        }
                    }
                }
                SdkMode.STATE.DISABLED -> {
                    _sdkModeState.update { it.copy(identifier = identifier, sdkModeState = SdkMode.STATE.STATE_CHANGE_IN_PROGRESS) }
                    repositoryScope.launch {
                        try {
                            api.enableSDKMode(identifier)
                            _sdkModeState.update { it.copy(identifier = identifier, sdkModeState = SdkMode.STATE.ENABLED) }
                        } catch (e: Exception) {
                            _sdkModeState.update { it.copy(identifier = identifier, sdkModeState = SdkMode.STATE.DISABLED) }
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

    fun setSdkModeLedConfig(identifier: String) {
        repositoryScope.launch {
            try {
                api.setLedConfig(identifier, LedConfig(
                    sdkModeState.value.sdkModeLedAnimation != SdkMode.STATE.ENABLED,
                    sdkModeState.value.ppiModeLedAnimation == SdkMode.STATE.ENABLED
                ))
                _sdkModeState.update {
                    val newState = if (sdkModeState.value.sdkModeLedAnimation == SdkMode.STATE.ENABLED) SdkMode.STATE.DISABLED else SdkMode.STATE.ENABLED
                    it.copy(identifier = identifier, sdkModeLedAnimation = newState)
                }
            } catch (e: Exception) {
                Log.e(TAG, "SDK Mode LED animation change failed: $e")
            }
        }
    }

    fun setPpiModeLedConfig(identifier: String) {
        repositoryScope.launch {
            try {
                api.setLedConfig(identifier, LedConfig(
                    sdkModeState.value.sdkModeLedAnimation == SdkMode.STATE.ENABLED,
                    sdkModeState.value.ppiModeLedAnimation != SdkMode.STATE.ENABLED
                ))
                _sdkModeState.update {
                    val newState = if (sdkModeState.value.ppiModeLedAnimation == SdkMode.STATE.ENABLED) SdkMode.STATE.DISABLED else SdkMode.STATE.ENABLED
                    it.copy(identifier = identifier, ppiModeLedAnimation = newState)
                }
            } catch (e: Exception) {
                Log.e(TAG, "PPI Mode LED animation change failed: $e")
            }
        }
    }

    suspend fun doRestart(identifier: String) = withContext(Dispatchers.IO) { api.doRestart(identifier) }

    suspend fun doFactoryReset(identifier: String, preservePairingInformation: Boolean = false) = withContext(Dispatchers.IO) {
        api.doFactoryReset(identifier, preservePairingInformation)
    }

    suspend fun setWarehouseSleep(identifier: String) = withContext(Dispatchers.IO) { api.setWareHouseSleep(identifier) }

    suspend fun setHibernateMode(identifier: String) = withContext(Dispatchers.IO) { api.setHibernateMode(identifier) }

    suspend fun turnDeviceOff(identifier: String) = withContext(Dispatchers.IO) { api.turnDeviceOff(identifier) }

    fun observeDeviceToHostNotifications(identifier: String): Flow<com.polar.sdk.api.PolarD2HNotificationData> {
        return api.observeDeviceToHostNotifications(identifier)
    }

    fun doFirmwareUpdate(identifier: String, firmwareUrl: String = ""): Flow<FirmwareUpdateStatus> {
        return api.updateFirmware(identifier, firmwareUrl)
            .onStart { Log.d(TAG, "Firmware update started for device: $identifier") }
            .onEach { status -> Log.d(TAG, "Firmware update status: $status for device: $identifier") }
            .catch { throwable ->
                Log.e(TAG, "Error during firmware update for device: $identifier", throwable)
                throw throwable
            }
    }

    fun checkFirmwareUpdate(identifier: String): Flow<CheckFirmwareUpdateStatus> {
        return api.checkFirmwareUpdate(identifier)
            .catch { throwable ->
                Log.e(TAG, "Error checking firmware update for device: $identifier", throwable)
                throw throwable
            }
    }

    suspend fun getAvailableStreamSettings(identifier: String, feature: PolarBleApi.PolarDeviceDataType): PolarSensorSetting =
        api.requestStreamSettings(identifier, feature)

    suspend fun requestFullStreamSettings(identifier: String, feature: PolarBleApi.PolarDeviceDataType): PolarSensorSetting =
        api.requestFullStreamSettings(identifier, feature)

    suspend fun getOfflineRecSettings(identifier: String, feature: PolarBleApi.PolarDeviceDataType): PolarSensorSetting =
        api.requestOfflineRecordingSettings(identifier, feature)

    suspend fun getFullOfflineRecSettings(identifier: String, feature: PolarBleApi.PolarDeviceDataType): PolarSensorSetting =
        api.requestFullOfflineRecordingSettings(identifier, feature)

    fun startEcgStream(identifier: String, polarSensorSetting: PolarSensorSetting): Flow<PolarEcgData> =
        api.startEcgStreaming(identifier, polarSensorSetting)

    fun startGyroStreaming(identifier: String, polarSensorSetting: PolarSensorSetting): Flow<PolarGyroData> =
        api.startGyroStreaming(identifier, polarSensorSetting)

    fun disconnectFromDevice(identifier: String) {
        _deviceConnectionStatus.update { DeviceConnectionState.DeviceDisconnecting(identifier = identifier) }
        api.disconnectFromDevice(identifier)
    }

    fun searchForDevice(withPrefix: String?): Flow<PolarDeviceInfo> {
        return api.searchForDevice(withPrefix)
    }

    fun connectToDevice(identifier: String) { api.connectToDevice(identifier) }

    fun startMagnetometerStream(identifier: String, polarSensorSetting: PolarSensorSetting): Flow<PolarMagnetometerData> =
        api.startMagnetometerStreaming(identifier, polarSensorSetting)

    fun startPpiStream(identifier: String): Flow<PolarPpiData> = api.startPpiStreaming(identifier)

    fun startPpgStream(identifier: String, polarSensorSetting: PolarSensorSetting): Flow<PolarPpgData> =
        api.startPpgStreaming(identifier, polarSensorSetting)

    fun startPressureStream(identifier: String, polarSensorSetting: PolarSensorSetting): Flow<PolarPressureData> =
        api.startPressureStreaming(identifier, polarSensorSetting)

    fun startLocationStream(identifier: String, polarSensorSetting: PolarSensorSetting): Flow<PolarLocationData> =
        api.startLocationStreaming(identifier, polarSensorSetting)

    fun startTemperatureStreaming(identifier: String, polarSensorSetting: PolarSensorSetting): Flow<PolarTemperatureData> =
        api.startTemperatureStreaming(identifier, polarSensorSetting)

    fun startSkinTemperatureStreaming(identifier: String, polarSensorSetting: PolarSensorSetting): Flow<PolarTemperatureData> =
        api.startSkinTemperatureStreaming(identifier, polarSensorSetting)

    fun startAccStreaming(identifier: String, polarSensorSetting: PolarSensorSetting): Flow<PolarAccelerometerData> =
        api.startAccStreaming(identifier, polarSensorSetting)

    fun startHrStreaming(identifier: String): Flow<PolarHrData> = api.startHrStreaming(identifier)

    fun stopStreaming(identifier: String, type: PmdMeasurementType) { api.stopStreaming(identifier, type) }

    suspend fun stopHrStreaming(identifier: String) = withContext(Dispatchers.IO) { api.stopHrStreaming(identifier) }

    suspend fun setLedConfig(identifier: String, enableSdkModeLed: Boolean, enablePpiModeLed: Boolean) =
        withContext(Dispatchers.IO) { api.setLedConfig(identifier, LedConfig(enableSdkModeLed, enablePpiModeLed)) }

    suspend fun startOfflineRecording(identifier: String, feature: PolarBleApi.PolarDeviceDataType, polarSensorSetting: PolarSensorSetting? = null): ResultOfRequest<Nothing> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Start $feature offline recording with settings: ${polarSensorSetting?.settings}")
            val secret = security.getSecretKey(identifier)?.let { PolarRecordingSecret(it.encoded) }
            api.startOfflineRecording(identifier, feature, polarSensorSetting, secret)
            ResultOfRequest.Success()
        } catch (e: Exception) {
            ResultOfRequest.Failure("Offline recording start failed", e)
        }
    }

    suspend fun stopOfflineRecording(identifier: String, feature: PolarBleApi.PolarDeviceDataType): ResultOfRequest<Nothing> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Stop offline recording. Feature $feature, Device $identifier")
            api.stopOfflineRecording(identifier, feature)
            ResultOfRequest.Success()
        } catch (e: Exception) {
            ResultOfRequest.Failure("Offline recording stop failed", e)
        }
    }

    suspend fun requestDerivedMeasurementGroupIds(identifier: String, sourceType: PolarBleApi.PolarDeviceDataType): ResultOfRequest<Set<Int>> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.requestDerivedMeasurementGroupIds(identifier, sourceType))
        } catch (e: Exception) {
            Log.e(TAG, "requestDerivedMeasurementGroupIds failed for $sourceType on $identifier: ${e.message}")
            ResultOfRequest.Failure("Failed to get derived measurement group IDs", e)
        }
    }

    suspend fun requestDerivedMeasurementSettingsGroup(identifier: String, groupId: Int): ResultOfRequest<PolarDerivedMeasurementSettingsGroup> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.requestDerivedMeasurementSettingsGroup(identifier, groupId))
        } catch (e: Exception) {
            Log.e(TAG, "requestDerivedMeasurementSettingsGroup failed for groupId=$groupId on $identifier: ${e.message}")
            ResultOfRequest.Failure("Failed to get derived measurement settings group", e)
        }
    }

    suspend fun startDerivedOfflineRecording(identifier: String, settings: PolarDerivedMeasurementSettings): ResultOfRequest<Nothing> = withContext(Dispatchers.IO) {
        return@withContext try {
            val secret = security.getSecretKey(identifier)?.let { PolarRecordingSecret(it.encoded) }
            api.startDerivedOfflineRecording(identifier, settings, secret)
            ResultOfRequest.Success()
        } catch (e: Exception) {
            ResultOfRequest.Failure("Derived offline recording start failed", e)
        }
    }

    suspend fun stopDerivedOfflineRecording(identifier: String): ResultOfRequest<Nothing> = withContext(Dispatchers.IO) {
        return@withContext try {
            api.stopDerivedOfflineRecording(identifier)
            ResultOfRequest.Success()
        } catch (e: Exception) {
            ResultOfRequest.Failure("Derived offline recording stop failed", e)
        }
    }

    suspend fun requestOfflineRecordingStatus(identifier: String): ResultOfRequest<List<PolarBleApi.PolarDeviceDataType>> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.getOfflineRecordingStatus(identifier))
        } catch (e: Exception) {
            ResultOfRequest.Failure("Offline recording status fetch failed", e)
        }
    }

    suspend fun getOfflineRecordingTriggerStatus(identifier: String): ResultOfRequest<PolarOfflineRecordingTrigger> = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = api.getOfflineRecordingTriggerSetup(identifier)
            _triggerState.update { OfflineRecTriggerStatus(identifier = identifier, result) }
            ResultOfRequest.Success(result)
        } catch (e: Exception) {
            ResultOfRequest.Failure("Offline recording trigger status get failed", e)
        }
    }

    suspend fun setOfflineRecordingTrigger(identifier: String, trigger: PolarOfflineRecordingTrigger): ResultOfRequest<Int> = withContext(Dispatchers.IO) {
        return@withContext try {
            val secret = security.getSecretKey(identifier)?.let { PolarRecordingSecret(it.encoded) }
            api.setOfflineRecordingTrigger(identifier = identifier, trigger = trigger, secret = secret)
            _triggerState.update { OfflineRecTriggerStatus(identifier = identifier, trigger) }
            ResultOfRequest.Success()
        } catch (e: Exception) {
            getOfflineRecordingTriggerStatus(identifier)
            ResultOfRequest.Failure("Offline recording trigger set failed", e)
        }
    }

    suspend fun isSecurityEnabled(identifier: String) = withContext(Dispatchers.IO) {
        _isOfflineRecordingSecurityEnabled.update { security.hasKey(identifier) }
    }

    suspend fun getMultiBleModeEnabled(identifier: String) = withContext(Dispatchers.IO) {
        _isMultiBleModeEnabled.update { getBleMultiConnectionMode(identifier) }
    }

    suspend fun getSensorInitiatedSecurityModeEnabled(identifier: String) = withContext(Dispatchers.IO) {
        _isSensorInitiatedSecurityModeEnabled.update { getSensorInitiatedSecurityMode(identifier) }
    }

    suspend fun toggleSecurity(identifier: String, enable: Boolean) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Toggled security to $enable")

        if (enable) {
            security.generateKey(identifier)
        } else {
            security.removeKey(identifier)
        }
    }

    suspend fun getLogConfig(identifier: String): ResultOfRequest<LogConfig> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.getLogConfig(identifier))
        } catch (e: Exception) {
            ResultOfRequest.Failure("Failed to get LogConfig", e)
        }
    }

    suspend fun setLogConfig(identifier: String, logConfig: LogConfig): ResultOfRequest<Int> = withContext(Dispatchers.IO) {
        return@withContext try {
            api.setLogConfig(identifier, logConfig)
            ResultOfRequest.Success()
        } catch (e: Exception) {
            ResultOfRequest.Failure("Failed to set LogConfig", e)
        }
    }

    suspend fun fetchErrorLog(identifier: String): ResultOfRequest<Errorlog> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(Errorlog(api.getFile(identifier, Errorlog.ERRORLOG_FILENAME)))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    fun observeSleepRecordingState(identifier: String): Flow<Boolean> {
        return api.observeSleepRecordingState(identifier).map { it.last() }
    }

    suspend fun forceStopSleep(identifier: String): ResultOfRequest<Boolean?> = withContext(Dispatchers.IO) {
        return@withContext try {
            api.stopSleepRecording(identifier)
            val state = api.getSleepRecordingState(identifier)
            if (state) {
                ResultOfRequest.Failure("Stopping sleep failed for $identifier", throwable = null)
            } else {
                ResultOfRequest.Success(state)
            }
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun getSleepRecordingState(identifier: String): ResultOfRequest<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.getSleepRecordingState(identifier, 5000))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun getSleepData(identifier: String, from: LocalDate, to: LocalDate): ResultOfRequest<List<PolarSleepData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.getSleep(identifier, from, to))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun getStepsData(identifier: String, from: LocalDate, to: LocalDate): ResultOfRequest<List<PolarStepsData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.getSteps(identifier, from, to))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun getCaloriesData(identifier: String, from: LocalDate, to: LocalDate, caloriesType: CaloriesType): ResultOfRequest<List<PolarCaloriesData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.getCalories(identifier, from, to, caloriesType))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun get247HrSamplesData(identifier: String, from: LocalDate, to: LocalDate): ResultOfRequest<List<Polar247HrSamplesData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.get247HrSamples(identifier, from, to))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun getNightlyRechargeData(identifier: String, from: LocalDate, to: LocalDate): ResultOfRequest<List<PolarNightlyRechargeData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.getNightlyRecharge(identifier, from, to))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun get247PPiSamples(identifier: String, from: LocalDate, to: LocalDate): ResultOfRequest<List<Polar247PPiSamplesData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.get247PPiSamples(identifier, from, to))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun getDeviceUserSettings(identifier: String): ResultOfRequest<PolarUserDeviceSettings> = try {
        val result = withContext(Dispatchers.IO) {
            api.getUserDeviceSettings(identifier)
        }
        ResultOfRequest.Success(result)
    } catch (e: Throwable) {
        Log.e(TAG, "Failed to get Device User Settings: ${e.message}", e)
        ResultOfRequest.Failure(e.message ?: "Failed to load device settings", e)
    }

    suspend fun deleteDeviceData(identifier: String, storedDeviceDataType: PolarBleApi.PolarStoredDataType, until: LocalDate): ResultOfRequest<Int> = withContext(Dispatchers.IO) {
        return@withContext try {
            api.deleteStoredDeviceData(identifier, storedDeviceDataType, until)
            ResultOfRequest.Success()
        } catch (e: Exception) {
            ResultOfRequest.Failure("Failed to delete $storedDeviceDataType files from device", e)
        }
    }

    suspend fun deleteDeviceDateFolders(identifier: String, fromDate: LocalDate?, toDate: LocalDate?): ResultOfRequest<Nothing> = withContext(Dispatchers.IO) {
        return@withContext try {
            api.deleteDeviceDateFolders(identifier, fromDate, toDate)
            ResultOfRequest.Success()
        } catch (e: Exception) {
            ResultOfRequest.Failure("Failed to delete date folders from device", e)
        }
    }

    suspend fun deleteTelemetryData(identifier: String): ResultOfRequest<Nothing> = withContext(Dispatchers.IO) {
        return@withContext try {
            api.deleteTelemetryData(identifier)
            ResultOfRequest.Success()
        } catch (e: Exception) {
            ResultOfRequest.Failure("Failed to delete telemetry data files from device", e)
        }
    }

    @OptIn(ExperimentalTime::class)
    suspend fun deleteTrainingSession(identifier: String, path: String): ResultOfRequest<Nothing> = withContext(Dispatchers.IO) {
        val trainingSessionReferenceEntry = trainingSessionReferenceCache[identifier]?.find { it.path == path }
        trainingSessionReferenceEntry?.let { trainingSessionEntry ->
            return@withContext try {
                val result = measureTimedValue { api.deleteTrainingSession(identifier, trainingSessionEntry) }
                Log.d(TAG, "delete of training session $path took ${TimeUnit.MICROSECONDS.toSeconds(result.duration.inWholeMicroseconds)} seconds")
                trainingSessionReferenceCache[identifier]?.remove(trainingSessionEntry)
                ResultOfRequest.Success()
            } catch (e: Exception) {
                ResultOfRequest.Failure("Failed to remove training session $path", e)
            }
        }
        ResultOfRequest.Failure("Tried to remove \"$path\", but no matching entry in repository", null)
    }

    suspend fun getSkinTemperatureData(identifier: String, from: LocalDate, to: LocalDate): ResultOfRequest<List<PolarSkinTemperatureData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.getSkinTemperature(identifier, from, to))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    fun getTrainingSessionReferences(identifier: String, fromDate: LocalDate, toDate: LocalDate): Flow<PolarTrainingSessionReference> {
        Log.d(TAG, "getTrainingSessionReferences from device $identifier")
        return api.getTrainingSessionReferences(identifier, fromDate, toDate)
            .onStart { trainingSessionReferenceCache[identifier] = mutableListOf() }
            .onEach { trainingSessionReferenceCache[identifier]?.add(it) }
    }

    fun getTrainingSessionWithProgress(identifier: String, path: String): Flow<ResultOfRequest<PolarTrainingSessionFetchResult>> {
        Log.d(TAG, "getTrainingSessionWithProgress from device $identifier in $path")
        val trainingSessionReference = trainingSessionReferenceCache[identifier]?.find { it.path == path }
        return if (trainingSessionReference != null) {
            api.getTrainingSessionWithProgress(identifier, trainingSessionReference)
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

    suspend fun getActiveTimeData(identifier: String, from: LocalDate, to: LocalDate): ResultOfRequest<List<PolarActiveTimeData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.getActiveTime(identifier, from, to))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun waitForConnection(identifier: String) = withContext(Dispatchers.IO) { api.waitForConnection(identifier) }

    suspend fun getDiskSpace(identifier: String): ResultOfRequest<PolarDiskSpaceData> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.getDiskSpace(identifier))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun setBleMultiConnectionMode(identifier: String, enable: Boolean) =
        withContext(Dispatchers.IO) { api.setMultiBLEConnectionMode(identifier, enable) }

    suspend fun getActivitySamplesData(identifier: String, from: LocalDate, to: LocalDate): ResultOfRequest<List<PolarActivitySamplesDayData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.getActivitySampleData(identifier, from, to))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun getDailySummaryData(identifier: String, from: LocalDate, to: LocalDate): ResultOfRequest<List<PolarDailySummaryData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.getDailySummaryData(identifier, from, to))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun getSpo2TestData(identifier: String, from: LocalDate, to: LocalDate): ResultOfRequest<List<PolarSpo2TestData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.getSpo2Test(identifier, from, to))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    private suspend fun getBleMultiConnectionMode(identifier: String): Boolean {
        return try {
            val result = api.getMultiBLEConnectionMode(identifier)
            _isMultiBleModeEnabled.update { result }
            result
        } catch (e: Exception) {
            Log.e(TAG, "getBleMultiConnectionMode failed. Error $e")
            false
        }
    }

    private suspend fun getSensorInitiatedSecurityMode(identifier: String): Boolean {
        return try {
            val result = api.getSensorInitiatedSecurityMode(identifier)
            _isSensorInitiatedSecurityModeEnabled.update { result }
            result
        } catch (e: Exception) {
            Log.e(TAG, "getSensorInitiatedSecurityMode failed. Error $e")
            false
        }
    }

    suspend fun setSensorInitiatedSecurityMode(identifier: String, enable: Boolean) =
        withContext(Dispatchers.IO) { api.setSensorInitiatedSecurityMode(identifier, enable) }

    suspend fun setTelemetryEnabled(identifier: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        api.setTelemetryEnabled(identifier, enabled)
    }

    suspend fun getUserDeviceSettings(identifier: String): PolarUserDeviceSettings =
        withContext(Dispatchers.IO) { api.getUserDeviceSettings(identifier = identifier) }

    suspend fun setUserDeviceLocation(identifier: String, location: Int) =
        withContext(Dispatchers.IO) { api.setUserDeviceLocation(identifier, location) }

    suspend fun setUsbConnectionMode(identifier: String, enabled: Boolean) =
        withContext(Dispatchers.IO) { api.setUsbConnectionMode(identifier, enabled) }

    suspend fun setDaylightSavingTime(identifier: String) =
        withContext(Dispatchers.IO) { api.setDaylightSavingTime(identifier) }

    suspend fun setAutomaticTrainingDetectionSettings(identifier: String, atdEnabled: Boolean, sensitivity: Int, minDuration: Int) = withContext(Dispatchers.IO) {
        api.setAutomaticTrainingDetectionSettings(
            identifier = identifier,
            automaticTrainingDetectionMode = atdEnabled,
            automaticTrainingDetectionSensitivity = sensitivity,
            minimumTrainingDurationSeconds = minDuration
        )
    }

    fun listenHrBroadcasts(excludeIdentifiers: Set<String>?): Flow<PolarHrBroadcastData> {
        return api.startListenForPolarHrBroadcasts(null)
            .filter { hrData -> excludeIdentifiers?.contains(hrData.polarDeviceInfo.deviceId) == false }
            .onEach { hrData -> Log.d(TAG, "HR Broadcast received: Device: ${hrData.polarDeviceInfo.deviceId}, HR: ${hrData.hr}") }
            .catch { error -> Log.e(TAG, "HR Broadcast error: ${error.message}", error); throw error }
    }

    suspend fun setAutosFilesEnabled(identifier: String, enabled: Boolean) =
        withContext(Dispatchers.IO) { api.setAutomaticOHRMeasurementEnabled(identifier, enabled) }

    suspend fun readFile(identifier: String, filePath: String): ResultOfRequest<ByteArray> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.readFile(identifier, filePath))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun listFiles(identifier: String, filePath: String, deleteDeep: Boolean): ResultOfRequest<List<String>> = withContext(Dispatchers.IO) {
        return@withContext try {
            ResultOfRequest.Success(api.getFileList(identifier, filePath, deleteDeep))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun writeFile(identifier: String, filePath: String, fileData: Any) = withContext(Dispatchers.IO) {
        return@withContext try {
            api.writeFile(identifier, filePath, fileData as ByteArray)
            ResultOfRequest.Success(Unit)
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun deleteFile(identifier: String, filePath: String) = withContext(Dispatchers.IO) {
        return@withContext try {
            api.deleteFileOrDirectory(identifier, filePath)
            ResultOfRequest.Success(Unit)
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun createFolder(identifier: String, folderPath: String) = withContext(Dispatchers.IO) {
        return@withContext try {
            api.createFolder(identifier, folderPath)
            ResultOfRequest.Success(Unit)
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun getChargeInformation(identifier: String) = withContext(Dispatchers.IO) {
        return@withContext try {
            val chargerStatusInfo = api.getChargerState(identifier)
            val batteryLevelInfo = api.getBatteryLevel(identifier)
            chargeInfo = ChargeInformation(batteryLevel = batteryLevelInfo, chargerStatus = chargerStatusInfo)
            ResultOfRequest.Success(chargeInfo)
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun isOfflineExerciseV2Supported(identifier: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val supported = (api as? com.polar.sdk.api.PolarOfflineExerciseV2Api)?.let { exerciseApi ->
                    try {
                        val result = exerciseApi.isOfflineExerciseV2Supported(identifier)
                        Log.d(TAG, "Offline Exercise V2 support check SUCCESS (immediate): $result")
                        result
                    } catch (e: Exception) {
                        Log.w(TAG, "Immediate check failed (PFTP not ready), retrying after 3s delay...")
                        delay(3000)
                        try {
                            val result = exerciseApi.isOfflineExerciseV2Supported(identifier)
                            Log.d(TAG, "Offline Exercise V2 support check SUCCESS (after retry): $result")
                            result
                        } catch (retryError: Exception) {
                            Log.e(TAG, "Offline Exercise V2 support check failed even after retry: ${retryError.message}")
                            false
                        }
                    }
                } ?: false
                _offlineExerciseV2Supported.update { current ->
                    current.toMutableMap().also { it[identifier] = supported }
                }

                supported
            } catch (e: Exception) {
                Log.e(TAG, "Offline Exercise V2 support check failed for $identifier", e)
                false
            }
        }

    suspend fun checkIfDeviceDisconnectedDueRemovedPairing(identifier: String) = withContext(Dispatchers.IO) {
        return@withContext try {
            val blePairingErrorStatus = api.checkIfDeviceDisconnectedDueRemovedPairing(identifier)
            ResultOfRequest.Success(blePairingErrorStatus)
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun getBleSignalStrength(identifier: String) = withContext(Dispatchers.IO) {
        return@withContext try {
            val bleSignalStrength = api.getRSSIValue(identifier)
            ResultOfRequest.Success(bleSignalStrength)
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun getWatchFaceConfig(identifier: String): ResultOfRequest<PolarWatchFaceConfig> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val config = api.getWatchFaceConfig(identifier)
                ResultOfRequest.Success(config)
            } catch (e: Exception) {
                ResultOfRequest.Failure(e.message.toString(), e)
            }
        }

    suspend fun setWatchFaceConfig(identifier: String, config: PolarWatchFaceConfig): ResultOfRequest<Unit> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                api.setWatchFaceConfig(identifier, config)
                ResultOfRequest.Success(Unit)
            } catch (e: Exception) {
                ResultOfRequest.Failure(e.message.toString(), e)
            }
        }

    suspend fun exportDeviceLogs(identifier: String): ResultOfRequest<List<com.polar.sdk.api.model.PolarDeviceLog>> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val logs = api.exportDeviceLogs(identifier)
                ResultOfRequest.Success(logs)
            } catch (e: Exception) {
                ResultOfRequest.Failure(e.message.toString(), e)
            }
        }
}