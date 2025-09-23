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
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.model.*
import com.polar.sdk.api.model.activity.PolarStepsData
import com.polar.sdk.api.model.sleep.PolarSleepData
import com.polar.sdk.impl.BDBleApiImpl
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.exceptions.UndeliverableException
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.withContext
import com.polar.sdk.api.model.PolarUserDeviceSettings
import com.polar.sdk.api.model.activity.PolarCaloriesData
import com.polar.sdk.impl.utils.CaloriesType
import com.polar.sdk.api.model.activity.Polar247HrSamplesData
import com.polar.sdk.api.model.sleep.PolarNightlyRechargeData
import com.polar.sdk.api.model.PolarSkinTemperatureData
import com.polar.sdk.api.model.activity.Polar247PPiSamplesData
import com.polar.sdk.api.model.activity.PolarActiveTimeData
import com.polar.sdk.api.model.trainingsession.PolarTrainingSession
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionReference
import com.polar.sdk.api.model.activity.PolarActivitySamplesDayData
import kotlinx.coroutines.rx3.awaitSingleOrNull
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
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

sealed class ResultOfRequest<out T> {
    data class Success<out R>(val value: R? = null) : ResultOfRequest<R>()
    data class Failure(
        val message: String,
        val throwable: Throwable?
    ) : ResultOfRequest<Nothing>()
}

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

    init {
        RxJavaPlugins.setErrorHandler { e ->
            if (e is UndeliverableException) {
                Log.e(TAG, "Undeliverable exception received:", e.cause)
            } else {
                Log.e(TAG, "Exception received.", e)
            }
        }
        api.setApiCallback(this)
        _polarBleSdkVersion.update {
            PolarBleApiDefaultImpl.versionInfo()
        }
    }

    fun listOfflineRecordings(deviceId: String): Flow<PolarOfflineRecordingEntry> {
        Log.d(TAG, "listOfflineRecordings from device $deviceId")
        return api.listOfflineRecordings(deviceId)
            .doOnSubscribe {
                offlineEntryCache[deviceId] = mutableListOf()
            }
            .map {
                offlineEntryCache[deviceId]?.add(it)
                it
            }
            .asFlow()
    }

    @OptIn(ExperimentalTime::class)
    suspend fun getOfflineRecording(deviceId: String, path: String): ResultOfRequest<OfflineRecordingData> {
        Log.d(TAG, "getOfflineRecording from device $deviceId in $path")
        val offlineRecEntry = offlineEntryCache[deviceId]?.find { it.path == path }
        offlineRecEntry?.let { offlineEntry ->
            return try {
                val result = measureTimedValue {
                    val secret = security.getSecretKey(deviceId)?.let { PolarRecordingSecret(it.encoded) }
                    return@measureTimedValue api.getOfflineRecord(deviceId, offlineEntry, secret)
                        .await()
                }

                val uri = saveData(deviceId, result.value)
                ResultOfRequest.Success(
                    OfflineRecordingData(
                        data = result.value,
                        uri = uri,
                        fileSize = offlineEntry.size,
                        downLoadSpeed = ((offlineEntry.size / 1000.0) / result.duration.inWholeMicroseconds) * 1000 * 1000
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Get offline recording fetch failed on entry $path error $e")
                ResultOfRequest.Failure("Get offline recording fetch failed on entry $path", e)
            }
        }
        return ResultOfRequest.Failure("Get offline recording fetch failed. Entry in path $path is not existing", null)
    }

    @OptIn(ExperimentalTime::class)
    suspend fun deleteRecording(deviceId: String, path: String): ResultOfRequest<Nothing> = withContext(Dispatchers.IO) {
        val offlineRecEntry = offlineEntryCache[deviceId]?.find { it.path == path }
        offlineRecEntry?.let { offlineEntry ->
            return@withContext try {
                val result = measureTimedValue {
                    api.removeOfflineRecord(deviceId, offlineEntry).await()
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

    suspend fun setTime(deviceId: String, calendar: Calendar): ResultOfRequest<Nothing> = withContext(Dispatchers.IO) {
        return@withContext try {
            api.setLocalTime(deviceId, calendar).await()
            ResultOfRequest.Success()
        } catch (e: Exception) {
            ResultOfRequest.Failure("Set time failed", e)
        }
    }

    suspend fun getTime(deviceId: String): Calendar {
        return withContext(Dispatchers.IO) {
            api.getLocalTime(deviceId).await()
        }
    }

    suspend fun getFtuInfo(deviceId: String): Boolean {
        return withContext(Dispatchers.IO) {
            api.isFtuDone(deviceId).await()
        }
    }

    suspend fun getUserPhysicalConfiguration(deviceId: String): ResultOfRequest<PolarPhysicalConfiguration?> =
        withContext(Dispatchers.IO) {
            try {
                val result: PolarPhysicalConfiguration? = api.getUserPhysicalConfiguration(deviceId)
                    .awaitSingleOrNull()
                ResultOfRequest.Success(result)
            } catch (e: Exception) {
                ResultOfRequest.Failure("Failed to get device physical info", e)
            }
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
                for (sample in offlineRecData.data.samples) {
                    var ppgs: List<Int>
                    when(sample.channelSamples.size) {
                        17 -> {
                            ppgs = sample.channelSamples.subList(0, 16)
                            val status = sample.channelSamples[16].toUInt().toLong()
                            collector.logPpgData16Channels(sample.timeStamp, ppgs, status)
                        }
                        3 -> {
                            ppgs = sample.channelSamples.subList(0, 2)
                            val status = sample.channelSamples[2].toUInt().toLong()
                            collector.logPpg2Channels(sample.timeStamp, ppgs, status.toInt())
                        }
                        else -> {
                            ppgs = sample.channelSamples.subList(0, 3)
                            val ambient = sample.channelSamples[3]
                            collector.logPpg(sample.timeStamp, ppgs, ambient)
                        }
                    }

                }
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

    override fun blePowerStateChanged(powered: Boolean) {
        Log.d(TAG, "Phone BLE is: ${if (powered) "ON" else "OFF"}")
        _isPhoneBlePowerOn.update {
            powered
        }
    }

    override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
        Log.d(TAG, "device ${polarDeviceInfo.deviceId} connected")
        Completable.fromAction {
            _deviceConnectionStatus.update {
                DeviceConnectionState.DeviceConnected(
                    deviceId = polarDeviceInfo.deviceId
                )
            }
        }.subscribeOn(Schedulers.io())
            .subscribe()
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

    override fun bleSdkFeatureReady(identifier: String, feature: PolarBleApi.PolarBleSdkFeature) {
        Log.d(TAG, "feature ready $feature")
        when (feature) {
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING,
            PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER,
            PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FEATURES_CONFIGURATION_SERVICE,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA -> {
                // do nothing
            }
            PolarBleApi.PolarBleSdkFeature.FEATURE_HR -> {
                api.getAvailableHRServiceDataTypes(identifier)
                    .subscribe(
                        { types ->
                            Log.d(TAG, "Available online streaming data: $types")
                            updateOnlineStreamDataTypes(identifier, types)
                        },
                        { exception: Throwable ->
                            Log.d(TAG, "Failed to check if HR service is available. Reason $exception")
                        },
                    )
            }
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING -> {
                api.getAvailableOnlineStreamDataTypes(identifier)
                    .subscribe(
                        { types ->
                            Log.d(TAG, "Available online streaming data: $types")
                            updateOnlineStreamDataTypes(identifier, types)
                        },
                        { exception: Throwable ->
                            Log.d(TAG, "Failed to get available online streaming types. Reason $exception")
                        },
                    )
            }
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING -> {
                api.getAvailableOfflineRecordingDataTypes(identifier)
                    .subscribe(
                        { types ->
                            Log.d(TAG, "Available offline recording data: $types")
                            updateOfflineStreamDataTypes(identifier, types)
                        },
                        { exception: Throwable ->
                            Log.d(TAG, "Failed to get available offline recording types. Reason $exception")
                        },
                    )
            }

            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE -> {
                _sdkModeState.update {
                    it.copy(deviceId = identifier, isAvailable = true)
                }
            }
            PolarBleApi.PolarBleSdkFeature.FEATURE_HTS -> {
                // do nothing
            }
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_LED_ANIMATION -> {
                // do nothing
            }
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE -> {
                // do nothing
            }

            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA -> {
                // do nothing
            }
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA -> {
                // do nothing
            }
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TEMPERATURE_DATA -> {
                // do nothing
            }
        }
    }

    fun getDeviceName(deviceId: String): String? {
        return api.fetchSession(deviceId)?.name
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
    }

    suspend fun isSdkModeEnabled(deviceId: String) = withContext(Dispatchers.IO) {
        if (_sdkModeState.value.isAvailable) {
            return@withContext try {
                val isEnabled = api.isSDKModeEnabled(deviceId)
                    .await()
                val state = if (isEnabled) {
                    SdkMode.STATE.ENABLED
                } else {
                    SdkMode.STATE.DISABLED
                }
                _sdkModeState.update {
                    it.copy(deviceId = deviceId, sdkModeState = state)
                }
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
                    _sdkModeState.update {
                        it.copy(deviceId = deviceId, sdkModeState = SdkMode.STATE.STATE_CHANGE_IN_PROGRESS)
                    }

                    api.disableSDKMode(deviceId)
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                            {
                                _sdkModeState.update {
                                    it.copy(deviceId = deviceId, sdkModeState = SdkMode.STATE.DISABLED)
                                }
                            },
                            { error: Throwable ->
                                _sdkModeState.update {
                                    it.copy(deviceId = deviceId, sdkModeState = SdkMode.STATE.ENABLED)
                                }
                                Log.e(TAG, "SDK mode disable failed: $error")
                            }
                        )
                }
                SdkMode.STATE.DISABLED -> {
                    _sdkModeState.update {
                        it.copy(deviceId = deviceId, sdkModeState = SdkMode.STATE.STATE_CHANGE_IN_PROGRESS)
                    }

                    api.enableSDKMode(deviceId)
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                            {
                                _sdkModeState.update {
                                    it.copy(deviceId = deviceId, sdkModeState = SdkMode.STATE.ENABLED)
                                }
                            },
                            { error: Throwable ->
                                _sdkModeState.update {
                                    it.copy(deviceId = deviceId, sdkModeState = SdkMode.STATE.DISABLED)
                                }
                                Log.e(TAG, "SDK mode disable failed: $error")
                            }
                        )
                }
                SdkMode.STATE.STATE_CHANGE_IN_PROGRESS -> {
                }
            }
        } else {
            Log.e(TAG, "SDK mode is not available")
        }
    }

    fun setSdkModeLedConfig(deviceId: String) {
        api.setLedConfig(deviceId, LedConfig(sdkModeState.value.sdkModeLedAnimation != SdkMode.STATE.ENABLED,
            sdkModeState.value.ppiModeLedAnimation == SdkMode.STATE.ENABLED))
            .subscribeOn(Schedulers.io())
            .subscribe(
            {
                _sdkModeState.update {
                    val sdkModeLedNewState = if (sdkModeState.value.sdkModeLedAnimation == SdkMode.STATE.ENABLED) SdkMode.STATE.DISABLED else SdkMode.STATE.ENABLED
                    it.copy(deviceId = deviceId, sdkModeLedAnimation = sdkModeLedNewState)
                }
            },
            { error: Throwable ->
                Log.e(TAG, "SDK Mode LED animation change failed: $error")
            }
        )
    }


    fun doRestart(deviceId: String): Completable {
         return api.doRestart(deviceId)
    }

    fun doFactoryReset(deviceId: String, savePairing: Boolean): Completable {
        return api.doFactoryReset(deviceId, savePairing)
    }

    fun setWarehouseSleep(deviceId: String): Completable {
        return api.setWareHouseSleep(deviceId)
    }

    fun turnDeviceOff(deviceId: String): Completable {
        return api.turnDeviceOff(deviceId)
    }

    fun setPpiModeLedConfig(deviceId: String) {
        api.setLedConfig(deviceId, LedConfig(sdkModeState.value.sdkModeLedAnimation == SdkMode.STATE.ENABLED,
            sdkModeState.value.ppiModeLedAnimation != SdkMode.STATE.ENABLED))
            .subscribeOn(Schedulers.io())
            .subscribe(
                {
                    _sdkModeState.update {
                        val ppiModeLedNewState = if (sdkModeState.value.ppiModeLedAnimation == SdkMode.STATE.ENABLED) SdkMode.STATE.DISABLED else SdkMode.STATE.ENABLED
                        it.copy(deviceId = deviceId, ppiModeLedAnimation = ppiModeLedNewState)
                    }
                },
                { error: Throwable ->
                    Log.e(TAG, "SDK Mode LED animation change failed: $error")
                }
            )
    }

    fun doFirmwareUpdate(deviceId: String, firmwareUrl: String = ""): Flowable<FirmwareUpdateStatus> {
        return api.updateFirmware(deviceId, firmwareUrl)
                .doOnSubscribe {
                    Log.d(TAG, "Firmware update started for device: $deviceId")
                }
                .doOnNext { status ->
                    Log.d(TAG, "Firmware update status: $status for device: $deviceId")
                }
                .doOnError { throwable ->
                    Log.e(TAG, "Error during firmware update for device: $deviceId", throwable)
                }
                .doOnComplete {
                    Log.d(TAG, "Firmware update completed for device: $deviceId")
                }
    }

    fun checkFirmwareUpdate(deviceId: String): Observable<CheckFirmwareUpdateStatus> {
        return api.checkFirmwareUpdate(deviceId)
            .doOnError { throwable ->
                Log.e(TAG, "Error checking firmware update for device: $deviceId", throwable)
            }
    }

    fun getAvailableStreamSettings(deviceId: String, feature: PolarBleApi.PolarDeviceDataType): Single<PolarSensorSetting> {
        return api.requestStreamSettings(deviceId, feature)
    }

    fun requestFullStreamSettings(deviceId: String, feature: PolarBleApi.PolarDeviceDataType): Single<PolarSensorSetting> {
        return api.requestFullStreamSettings(deviceId, feature)
    }

    fun getOfflineRecSettings(deviceId: String, feature: PolarBleApi.PolarDeviceDataType): Single<PolarSensorSetting> {
        return api.requestOfflineRecordingSettings(deviceId, feature)
    }

    fun getFullOfflineRecSettings(deviceId: String, feature: PolarBleApi.PolarDeviceDataType): Single<PolarSensorSetting> {
        return api.requestFullOfflineRecordingSettings(deviceId, feature)
    }

    fun startEcgStream(deviceId: String, polarSensorSetting: PolarSensorSetting): Flowable<PolarEcgData> {
        return api.startEcgStreaming(deviceId, polarSensorSetting)
    }

    fun startGyroStreaming(deviceId: String, polarSensorSetting: PolarSensorSetting): Flowable<PolarGyroData> {
        return api.startGyroStreaming(deviceId, polarSensorSetting)
    }

    fun disconnectFromDevice(deviceId: String) {
        _deviceConnectionStatus.update {
            DeviceConnectionState.DeviceDisconnecting(
                deviceId = deviceId
            )
        }
        return api.disconnectFromDevice(deviceId)
    }

    fun searchForDevice(withPrefix: String?): Flowable<PolarDeviceInfo> {
        return api.searchForDevice(withPrefix)
    }

    fun connectToDevice(deviceId: String) {
        api.connectToDevice(deviceId)
    }

    fun startMagnetometerStream(deviceId: String, polarSensorSetting: PolarSensorSetting): Flowable<PolarMagnetometerData> {
        return api.startMagnetometerStreaming(deviceId, polarSensorSetting)
    }

    fun startPpiStream(deviceId: String): Flowable<PolarPpiData> {
        return api.startPpiStreaming(deviceId)
    }

    fun startPpgStream(deviceId: String, polarSensorSetting: PolarSensorSetting): Flowable<PolarPpgData> {
        return api.startPpgStreaming(deviceId, polarSensorSetting)
    }

    fun startPressureStream(deviceId: String, polarSensorSetting: PolarSensorSetting): Flowable<PolarPressureData> {
        return api.startPressureStreaming(deviceId, polarSensorSetting)
    }

    fun startLocationStream(deviceId: String, polarSensorSetting: PolarSensorSetting): Flowable<PolarLocationData> {
        return api.startLocationStreaming(deviceId, polarSensorSetting)
    }

    fun startTemperatureStreaming(deviceId: String, polarSensorSetting: PolarSensorSetting): Flowable<PolarTemperatureData> {
        return api.startTemperatureStreaming(deviceId, polarSensorSetting)
    }

    fun startSkinTemperatureStreaming(deviceId: String, polarSensorSetting: PolarSensorSetting): Flowable<PolarTemperatureData> {
        return api.startSkinTemperatureStreaming(deviceId, polarSensorSetting)
    }

    fun startAccStreaming(deviceId: String, polarSensorSetting: PolarSensorSetting): Flowable<PolarAccelerometerData> {
        return api.startAccStreaming(deviceId, polarSensorSetting)
    }

    fun startHrStreaming(deviceId: String): Flowable<PolarHrData> {
        return api.startHrStreaming(deviceId)
    }

    fun stopStreaming(deviceId: String, type: PmdMeasurementType) {
        return api.stopStreaming(deviceId, type)
    }

    fun stopHrStreaming(deviceId: String): Completable {
        return api.stopHrStreaming(deviceId)
    }

    fun setLedConfig(deviceId: String, enableSdkModeLed: Boolean, enablePpiModeLed: Boolean): Completable {
        return api.setLedConfig(deviceId, LedConfig(enableSdkModeLed, enablePpiModeLed))
    }

    suspend fun startOfflineRecording(deviceId: String, feature: PolarBleApi.PolarDeviceDataType, polarSensorSetting: PolarSensorSetting? = null): ResultOfRequest<Nothing> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Start $feature offline recording with settings: ${polarSensorSetting?.settings} ")

            val secret = security.getSecretKey(deviceId)?.let { PolarRecordingSecret(it.encoded) }
            api.startOfflineRecording(deviceId, feature, polarSensorSetting, secret)
                .subscribeOn(Schedulers.io())
                .blockingAwait()
            ResultOfRequest.Success()
        } catch (e: Exception) {
            ResultOfRequest.Failure("Offline recording start failed", e)
        }
    }

    suspend fun stopOfflineRecording(deviceId: String, feature: PolarBleApi.PolarDeviceDataType): ResultOfRequest<Nothing> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Stop offline recording. Feature $feature, Device $deviceId")
            api.stopOfflineRecording(deviceId, feature)
                .subscribeOn(Schedulers.io())
                .blockingAwait()
            ResultOfRequest.Success()
        } catch (e: Exception) {
            ResultOfRequest.Failure("Offline recording stop failed", e)
        }
    }

    suspend fun requestOfflineRecordingStatus(deviceId: String): ResultOfRequest<List<PolarBleApi.PolarDeviceDataType>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = api.getOfflineRecordingStatus(deviceId)
                .await()
            ResultOfRequest.Success(result)
        } catch (e: Exception) {
            ResultOfRequest.Failure("Offline recording status fetch failed", e)
        }
    }

    suspend fun getOfflineRecordingTriggerStatus(deviceId: String): ResultOfRequest<PolarOfflineRecordingTrigger> = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = api.getOfflineRecordingTriggerSetup(deviceId)
                .await()
            _triggerState.update {
                OfflineRecTriggerStatus(deviceId = deviceId, result)
            }
            ResultOfRequest.Success(result)
        } catch (e: Exception) {
            ResultOfRequest.Failure("Offline recording trigger status get failed", e)
        }
    }

    suspend fun setOfflineRecordingTrigger(deviceId: String, trigger: PolarOfflineRecordingTrigger): ResultOfRequest<Int> = withContext(Dispatchers.IO) {
        return@withContext try {
            val secret = security.getSecretKey(deviceId)?.let { PolarRecordingSecret(it.encoded) }
            api.setOfflineRecordingTrigger(identifier = deviceId, trigger = trigger, secret = secret)
                .await()
            _triggerState.update {
                OfflineRecTriggerStatus(deviceId = deviceId, trigger)
            }
            ResultOfRequest.Success()
        } catch (e: Exception) {
            // because setup failed, ask the current status from the device as we don't know what did we set already
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
            val result = api.getLogConfig(deviceId).await()
            ResultOfRequest.Success(result)
        } catch (e: Exception) {
            ResultOfRequest.Failure("Failed to get LogConfig", e)
        }
    }

    suspend fun setLogConfig(deviceId: String, logConfig: LogConfig): ResultOfRequest<Int> = withContext(Dispatchers.IO) {
        return@withContext try {
            api.setLogConfig(deviceId, logConfig).await()
            ResultOfRequest.Success()
        } catch (e: Exception) {
            ResultOfRequest.Failure("Failed to set LogConfig", e)
        }
    }

    suspend fun fetchErrorLog(deviceId: String): ResultOfRequest<Errorlog> = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = api.getFile(deviceId, Errorlog.ERRORLOG_FILENAME).await()
            ResultOfRequest.Success(Errorlog(result))
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    fun observeSleepRecordingState(deviceId: String): Flowable<Boolean> {
        return api.observeSleepRecordingState(deviceId)
            .map { it.last() }
    }

    suspend fun forceStopSleep(deviceId: String): ResultOfRequest<Boolean?> = withContext(Dispatchers.IO) {
        return@withContext try {
            val sleepRecordingStateResult =
                api.stopSleepRecording(deviceId)
                    .andThen(api.getSleepRecordingState(deviceId)).await()

            if (sleepRecordingStateResult == true) {
                ResultOfRequest.Failure("Stopping sleep failed for $deviceId", throwable = null)
            } else {
                ResultOfRequest.Success(sleepRecordingStateResult)
            }
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun getSleepRecordingState(deviceId: String): ResultOfRequest<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = api.getSleepRecordingState(deviceId).await()
            ResultOfRequest.Success(result)
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun getSleepData(deviceId: String, from: LocalDate, to: LocalDate): ResultOfRequest<List<PolarSleepData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = api.getSleep(deviceId, from, to).await()
            ResultOfRequest.Success(result)
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun getStepsData(deviceId: String, from: LocalDate, to: LocalDate): ResultOfRequest<List<PolarStepsData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = api.getSteps(deviceId, from, to).await()
            ResultOfRequest.Success(result)
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun getCaloriesData(
        deviceId: String,
        from: LocalDate,
        to: LocalDate,
        caloriesType: CaloriesType
    ): ResultOfRequest<List<PolarCaloriesData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = api.getCalories(deviceId, from, to, caloriesType).await()
            ResultOfRequest.Success(result)
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun get247HrSamplesData(deviceId: String, from: Date, to: Date): ResultOfRequest<List<Polar247HrSamplesData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = api.get247HrSamples(deviceId, from, to).await()
            ResultOfRequest.Success(result)
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun getNightlyRechargeData(deviceId: String, from: LocalDate, to: LocalDate): ResultOfRequest<List<PolarNightlyRechargeData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = api.getNightlyRecharge(deviceId, from, to).await()
            ResultOfRequest.Success(result)
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun get247PPiSamples(deviceId: String, from: Date, to: Date): ResultOfRequest<List<Polar247PPiSamplesData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = api.get247PPiSamples(deviceId, from, to).await()
            ResultOfRequest.Success(result)
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    suspend fun getDeviceUserSettings(deviceId: String): ResultOfRequest<PolarUserDeviceSettings> = withContext(Dispatchers.IO) {
        return@withContext try {
            var result = api.getUserDeviceSettings(deviceId).await()
            ResultOfRequest.Success(result)
        } catch (e: Exception) {
            ResultOfRequest.Failure("Failed to get Device User Settings", e)
        }
    }

    suspend fun deleteDeviceData(
        deviceId: String,
        storedDeviceDataType: PolarBleApi.PolarStoredDataType,
        until: LocalDate
    ): ResultOfRequest<Int> = withContext(Dispatchers.IO) {
        return@withContext try {
            api.deleteStoredDeviceData(deviceId, storedDeviceDataType, until).await()
            ResultOfRequest.Success()
        } catch (e: Exception) {
            ResultOfRequest.Failure("Failed to delete $storedDeviceDataType files from device", e)
        }
    }

    suspend fun deleteDeviceDateFolders(
        deviceId: String,
        fromDate: LocalDate?,
        toDate: LocalDate?
    ): ResultOfRequest<Nothing> = withContext(Dispatchers.IO) {
        try {
            api.deleteDeviceDateFolders(deviceId, fromDate, toDate)
                .subscribeOn(Schedulers.io())
                .await()
            ResultOfRequest.Success()
        } catch (e: Exception) {
            ResultOfRequest.Failure("Failed to delete date folders from device", e)
        }
    }

    suspend fun getSkinTemperatureData(
        deviceId: String,
        from: LocalDate,
        to: LocalDate
    ): ResultOfRequest<List<PolarSkinTemperatureData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = api.getSkinTemperature(deviceId, from, to).await()
            ResultOfRequest.Success(result)
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    fun getTrainingSessionReferences(deviceId: String): Flow<PolarTrainingSessionReference> {
        Log.d(TAG, "getTrainingSessionReferences from device $deviceId")
        return api.getTrainingSessionReferences(deviceId)
            .doOnSubscribe {
                trainingSessionReferenceCache[deviceId] = mutableListOf()
            }
            .map {
                trainingSessionReferenceCache[deviceId]?.add(it)
                it
            }
            .asFlow()
    }

    suspend fun getTrainingSession(deviceId: String, path: String): ResultOfRequest<PolarTrainingSession> {
        Log.d(TAG, "getTrainingSession from device $deviceId in $path")

        val trainingSessionReference = trainingSessionReferenceCache[deviceId]?.find { it.path == path }

        trainingSessionReference?.let { offlineEntry ->
            return try {
                    return ResultOfRequest.Success(api.getTrainingSession(deviceId, offlineEntry).await())
            } catch (e: Exception) {
                Log.e(TAG, "getTrainingSession failed on path $path error $e")
                ResultOfRequest.Failure("getTrainingSession failed on path $path", e)
            }
        }

        return ResultOfRequest.Failure("getTrainingSession failed on path $path", null)
    }

    suspend fun getActiveTimeData(
        deviceId: String,
        from: LocalDate,
        to: LocalDate
    ): ResultOfRequest<List<PolarActiveTimeData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = api.getActiveTime(deviceId, from, to).await()
            ResultOfRequest.Success(result)
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    fun waitForConnection(deviceId: String): Completable {
        return api.waitForConnection(deviceId)
    }

    suspend fun getDiskSpace(
        deviceId: String
    ): ResultOfRequest<PolarDiskSpaceData> = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = api.getDiskSpace(deviceId).await()
            ResultOfRequest.Success(result)
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    fun setBleMultiConnectionMode(deviceId: String, enable: Boolean): Completable {
        return api.setMultiBLEConnectionMode(deviceId, enable)
    }

    suspend fun getActivitySamplesData(deviceId: String, from: Date, to: Date): ResultOfRequest<List<PolarActivitySamplesDayData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            var result = api.getActivitySampleData(deviceId,
                from.toInstant().atZone(ZoneId.systemDefault()).toLocalDate(),
                to.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
            ).await()
            ResultOfRequest.Success(result)
        } catch (e: Exception) {
            ResultOfRequest.Failure(e.message.toString(), e)
        }
    }

    private suspend fun getBleMultiConnectionMode(deviceId: String): Boolean {

        return try {
            val result = api.getMultiBLEConnectionMode(deviceId).await()

            (result as? Boolean)?.let { res ->
                _isMultiBleModeEnabled.update { res }
            }
            return result

        } catch (e: Exception) {
            Log.e(TAG, "getBleMultiConnectionMode failed. Error $e")
            return  false
        }
    }
}