package com.polar.polarsensordatacollector.ui.landing

import android.net.Uri
import android.util.Log
import android.util.Pair
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.polarsensordatacollector.DataCollector
import com.polar.polarsensordatacollector.repository.DeviceConnectionState
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.ui.utils.MessageUiState
import com.polar.polarsensordatacollector.utils.StreamUtils
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.errors.PolarDeviceDisconnected
import com.polar.sdk.api.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class LiveRecordingUiState(
    val deviceId: String = "",
    val streamingRecordingState: EnumMap<PolarBleApi.PolarDeviceDataType, StreamingFeatureState> =
        EnumMap(PolarBleApi.PolarDeviceDataType.values().associateWith { StreamingFeatureState() }),
)

class StreamingFeatureState(
    val state: STATES = STATES.STOPPED,
    val settings: Map<PolarSensorSetting.SettingType, Int> = mutableMapOf(),
) {
    enum class STATES {
        RECORDING,
        PAUSED,
        STOPPED,
    }
}

data class AvailableOnlineStreamDataState(
    val deviceId: String = "",
    val streamingFeaturesAvailable: EnumMap<PolarBleApi.PolarDeviceDataType, Boolean> = EnumMap(PolarBleApi.PolarDeviceDataType.values().associateWith { false }),
)

data class OnlineRecordingUiState(
    val deviceId: String = "",
    val timer: String = ""
)

data class OnlineAvailableStreamSettingsUiState(
    val feature: PolarBleApi.PolarDeviceDataType,
    val settings: OnlineStreamSettings
)

data class OnlineStreamSettings(
    val currentlyAvailable: PolarSensorSetting?,
    val allPossibleSettings: PolarSensorSetting?,
    val selectedSettings: Map<PolarSensorSetting.SettingType, Int>?
)

data class EcgSampleDataUiState(
    val deviceId: String = "",
    val calculatedFrequency: Double?,
    val sampleData: PolarEcgData?,
)

data class AccSampleDataUiState(
    val deviceId: String = "",
    val calculatedFrequency: Double?,
    val sampleData: PolarAccelerometerData?,
)

data class GyroSampleDataUiState(
    val deviceId: String = "",
    val calculatedFrequency: Double?,
    val sampleData: PolarGyroData?,
)

data class MagnSampleDataUiState(
    val deviceId: String,
    val calculatedFrequency: Double?,
    val sampleData: PolarMagnetometerData?
)

data class PpgSampleDataUiState(
    val deviceId: String,
    val calculatedFrequency: Double?,
    val sampleData: PolarPpgData?
)

data class PressureSampleDataUiState(
    val deviceId: String,
    val calculatedFrequency: Double?,
    val sampleData: PolarPressureData?
)

data class LocationSampleDataUiState(
    val deviceId: String,
    val calculatedFrequency: Double?,
    val sampleData: PolarLocationData?
)

data class TemperatureSampleDataUiState(
    val deviceId: String,
    val calculatedFrequency: Double?,
    val sampleData: PolarTemperatureData?
)

data class PpiSampleDataUiState(
    val deviceId: String,
    val sampleData: PolarPpiData.PolarPpiSample? = null
)

data class HeartRateInformationUiState(
    val deviceId: String = "",
    val heartRate: PolarHrData.PolarHrSample? = null,
)

data class SkinTemperatureSampleDataUiState(
    val deviceId: String,
    val calculatedFrequency: Double?,
    val sampleData: PolarTemperatureData?
)

@HiltViewModel
class OnlineRecordingViewModel @Inject constructor(
    val polarDeviceStreamingRepository: PolarDeviceRepository,
    private val collector: DataCollector,
    private val state: SavedStateHandle
) : ViewModel() {
    companion object {
        private const val TAG = "OnlineRecordingViewModel"
    }

    private val deviceId = state.get<String>(ONLINE_OFFLINE_KEY_DEVICE_ID) ?: throw Exception("Online recording viewModel must know the deviceId")

    private var settingsCache: EnumMap<PolarBleApi.PolarDeviceDataType, OnlineStreamSettings> = EnumMap(PolarBleApi.PolarDeviceDataType.values().associateWith { OnlineStreamSettings(null, null, null) })

    private val streamDisposables: MutableMap<PolarBleApi.PolarDeviceDataType, Disposable?> =
        EnumMap(PolarBleApi.PolarDeviceDataType::class.java)

    private val _uiAvailableOnlineStreamDataTypesState = MutableStateFlow(AvailableOnlineStreamDataState())
    val uiAvailableOnlineStreamDataTypesState: StateFlow<AvailableOnlineStreamDataState> = _uiAvailableOnlineStreamDataTypesState.asStateFlow()

    private val _uiOnlineRecordingState = MutableStateFlow(OnlineRecordingUiState())
    val uiOnlineRecordingState: StateFlow<OnlineRecordingUiState> = _uiOnlineRecordingState.asStateFlow()

    private val _uiHeartRateInfoState = MutableStateFlow(HeartRateInformationUiState())
    val uiHeartRateInfoState: StateFlow<HeartRateInformationUiState> = _uiHeartRateInfoState.asStateFlow()

    private val _uiStreamingState = MutableStateFlow(LiveRecordingUiState())
    val uiStreamingState: StateFlow<LiveRecordingUiState> = _uiStreamingState.asStateFlow()

    private val _uiAccStreamDataState = MutableStateFlow(AccSampleDataUiState("", null, null))
    val uiAccStreamDataState: StateFlow<AccSampleDataUiState> = _uiAccStreamDataState.asStateFlow()

    private val _uiGyroStreamDataState = MutableStateFlow(GyroSampleDataUiState("", null, null))
    val uiGyroStreamDataState: StateFlow<GyroSampleDataUiState> = _uiGyroStreamDataState.asStateFlow()

    private val _uiMagnStreamDataState = MutableStateFlow(MagnSampleDataUiState("", null, null))
    val uiMagnStreamDataState: StateFlow<MagnSampleDataUiState> = _uiMagnStreamDataState.asStateFlow()

    private val _uiPpiStreamDataState = MutableStateFlow(PpiSampleDataUiState("", null))
    val uiPpiStreamDataState: StateFlow<PpiSampleDataUiState> = _uiPpiStreamDataState.asStateFlow()

    private val _uiEcgStreamDataState = MutableStateFlow(EcgSampleDataUiState("", null, null))
    val uiEcgStreamDataState: StateFlow<EcgSampleDataUiState> = _uiEcgStreamDataState.asStateFlow()

    private val _uiPpgStreamDataState = MutableStateFlow(PpgSampleDataUiState("", null, null))
    val uiPpgStreamDataState: StateFlow<PpgSampleDataUiState> = _uiPpgStreamDataState.asStateFlow()

    private val _uiPressureStreamDataState = MutableStateFlow(PressureSampleDataUiState("", null, null))
    val uiPressureStreamDataState: StateFlow<PressureSampleDataUiState> = _uiPressureStreamDataState.asStateFlow()

    private val _uiLocationStreamDataState = MutableStateFlow(LocationSampleDataUiState("", null, null))
    val uiLocationStreamDataState: StateFlow<LocationSampleDataUiState> = _uiLocationStreamDataState.asStateFlow()

    private val _uiTemperatureStreamDataState = MutableStateFlow(TemperatureSampleDataUiState("", null, null))
    val uiTemperatureStreamDataState: StateFlow<TemperatureSampleDataUiState> = _uiTemperatureStreamDataState.asStateFlow()

    private val _uiSkinTemperatureStreamDataState = MutableStateFlow(SkinTemperatureSampleDataUiState("", null, null))
    val uiSkinTemperatureStreamDataState: StateFlow<SkinTemperatureSampleDataUiState> = _uiSkinTemperatureStreamDataState.asStateFlow()

    private val _uiOnlineRequestedSettingsState: MutableStateFlow<OnlineAvailableStreamSettingsUiState?> = MutableStateFlow(null)
    val uiOnlineRequestedSettingsState: StateFlow<OnlineAvailableStreamSettingsUiState?> = _uiOnlineRequestedSettingsState.asStateFlow()

    private val _uiShowError: MutableStateFlow<MessageUiState> = MutableStateFlow(MessageUiState(""))
    val uiShowError: StateFlow<MessageUiState> = _uiShowError.asStateFlow()

    private val _shareFiles: MutableStateFlow<ArrayList<Uri>> = MutableStateFlow(ArrayList<Uri>())
    val shareFiles: StateFlow<ArrayList<Uri>> = _shareFiles.asStateFlow()

    private var recordingTimerDisposable: Disposable? = null

    init {

        viewModelScope.launch {
            polarDeviceStreamingRepository.availableFeatures
                .collect { deviceStreamsAvailable ->
                    updateStreamingFeatureAvailableUiState(deviceStreamsAvailable.deviceId, featuresAvailable = deviceStreamsAvailable.availableStreamingFeatures)
                }
        }

        viewModelScope.launch {
            polarDeviceStreamingRepository.deviceConnectionStatus
                .collect { deviceConnectionState ->
                    when (deviceConnectionState) {
                        is DeviceConnectionState.DeviceDisconnecting -> {
                            viewModelScope.launch {
                                finalizeCollector()
                            }
                        }
                        else -> {
                            //NOP
                        }
                    }
                }
        }
    }

    fun addMarkerToLog(isStartMarker: Boolean) = viewModelScope.launch {
        Single.fromCallable { collector.marker(isStartMarker, deviceId, isStartMarker) }
            .subscribeOn(Schedulers.io())
            .subscribe(
                {},
                { error ->
                    Log.w(TAG, "Failed to add marker: $error")
                }
            )
    }

    fun fileShareCompleted() {
        _shareFiles.update {
            ArrayList<Uri>()
        }
    }

    private fun updateStreamingFeatureAvailableUiState(deviceId: String, featuresAvailable: EnumMap<PolarBleApi.PolarDeviceDataType, Boolean>) {
        _uiAvailableOnlineStreamDataTypesState.update {
            it.copy(deviceId = deviceId, streamingFeaturesAvailable = featuresAvailable)
        }
    }

    private fun getStreamSettingsToStartStream(feature: PolarBleApi.PolarDeviceDataType): Single<Map<PolarSensorSetting.SettingType, Int>> {
        return settingsCache[feature]?.selectedSettings?.let {
            Single.just(it)
        } ?: run {
            polarDeviceStreamingRepository.getAvailableStreamSettings(deviceId, feature)
                .map { sensorSetting: PolarSensorSetting ->
                    val selectedSettings = maxSettingsFromStreamSettings(sensorSetting)
                    updateSelectedStreamSettings(feature, selectedSettings)
                    selectedSettings
                }
        }
    }

    fun updateSelectedStreamSettings(feature: PolarBleApi.PolarDeviceDataType, settings: Map<PolarSensorSetting.SettingType, Int>) {
        val newSettings = OnlineStreamSettings(
            currentlyAvailable = settingsCache[feature]?.currentlyAvailable,
            allPossibleSettings = settingsCache[feature]?.allPossibleSettings,
            selectedSettings = settings
        )
        settingsCache[feature] = newSettings
    }

    private fun maxSettingsFromStreamSettings(sensorSetting: PolarSensorSetting): Map<PolarSensorSetting.SettingType, Int> {
        val settings: MutableMap<PolarSensorSetting.SettingType, Int> = mutableMapOf()
        for ((key, value) in sensorSetting.settings) {
            settings[key] = Collections.max(value)
        }
        return settings
    }

    private fun startHrStream() {
        val disposable = Single.fromCallable {}
            .toFlowable()
            .flatMap {
                Log.d(TAG, "Start HR stream")
                polarDeviceStreamingRepository.getDeviceName(deviceId)?.let {
                    collector.startHrLog(it)
                } ?: kotlin.run {
                    showError("Failed to start HR stream. Device name is not known")
                }

                updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.HR, StreamingFeatureState.STATES.RECORDING)
                polarDeviceStreamingRepository.startHrStreaming(deviceId)
            }
            .subscribeOn(Schedulers.io())
            .subscribe(
                { polarHrData: PolarHrData ->
                    logHrData(polarHrData)
                    _uiHeartRateInfoState.update {
                        it.copy(deviceId = deviceId, heartRate = polarHrData.samples.first())
                    }
                },
                { error: Throwable ->
                    if (error !is PolarDeviceDisconnected) {
                        showError("HR stream failed", error)
                    }
                }
            )
        streamDisposables[PolarBleApi.PolarDeviceDataType.HR] = disposable
    }

    fun requestStreamSettings(deviceId: String, feature: PolarBleApi.PolarDeviceDataType) {
        viewModelScope.launch(Dispatchers.IO) {
            val availableSettings = polarDeviceStreamingRepository.getAvailableStreamSettings(deviceId, feature)
            val allSettings = polarDeviceStreamingRepository.requestFullStreamSettings(deviceId, feature)
                .onErrorReturn { PolarSensorSetting(emptyMap()) }

            Single.zip(availableSettings, allSettings) { available: PolarSensorSetting, all: PolarSensorSetting ->
                if (available.settings.isEmpty()) {
                    throw Throwable("Settings are not available")
                } else {
                    Log.d(TAG, "Feature " + feature + " available settings " + available.settings)
                    Log.d(TAG, "Feature " + feature + " all settings " + all.settings)
                    return@zip Pair(available, all)
                }
            }
                .toFlowable()
                .subscribe(
                    { sensorSettings: Pair<PolarSensorSetting, PolarSensorSetting> ->
                        Log.d(TAG, "Sensor settings fetch completed")

                        val newSettings = OnlineStreamSettings(
                            currentlyAvailable = sensorSettings.first,
                            allPossibleSettings = sensorSettings.second,
                            selectedSettings = settingsCache[feature]?.selectedSettings
                        )
                        settingsCache[feature] = newSettings

                        _uiOnlineRequestedSettingsState.update {
                            OnlineAvailableStreamSettingsUiState(
                                feature = feature,
                                OnlineStreamSettings(
                                    currentlyAvailable = sensorSettings.first,
                                    allPossibleSettings = sensorSettings.second,
                                    selectedSettings = settingsCache[feature]?.selectedSettings
                                )
                            )
                        }
                    },
                    { error: Throwable ->
                        showError("Settings fetch error for feature $feature", error)
                    }
                )
        }
    }

    fun startStream(feature: PolarBleApi.PolarDeviceDataType) {
        when (feature) {
            PolarBleApi.PolarDeviceDataType.ECG -> startEcgStream()
            PolarBleApi.PolarDeviceDataType.ACC -> startAccStream()
            PolarBleApi.PolarDeviceDataType.PPG -> startPpgStream()
            PolarBleApi.PolarDeviceDataType.PPI -> startPpiStream()
            PolarBleApi.PolarDeviceDataType.GYRO -> startGyroStream()
            PolarBleApi.PolarDeviceDataType.MAGNETOMETER -> startMagnetometerStream()
            PolarBleApi.PolarDeviceDataType.PRESSURE -> startPressureStream()
            PolarBleApi.PolarDeviceDataType.LOCATION -> startLocationStream()
            PolarBleApi.PolarDeviceDataType.TEMPERATURE -> startTemperatureStream()
            PolarBleApi.PolarDeviceDataType.SKIN_TEMPERATURE -> startSkinTemperatureStream()
            PolarBleApi.PolarDeviceDataType.HR -> startHrStream()
        }
        startRecTimer()
    }

    fun pauseStream(feature: PolarBleApi.PolarDeviceDataType) {
        val disposable = streamDisposables[feature]
        if (disposable != null) {
            disposable.dispose()
            streamDisposables[feature] = null
            updateStreamingRecordingState(deviceId, feature, StreamingFeatureState.STATES.PAUSED)
        } else {
            Log.d(TAG, "Disposing stream which has no disposable $feature")
        }
    }

    fun stopStream(feature: PolarBleApi.PolarDeviceDataType) {
        when (feature) {
            PolarBleApi.PolarDeviceDataType.ECG -> {
                polarDeviceStreamingRepository.stopStreaming(deviceId, PmdMeasurementType.ECG)
                settingsCache[PolarBleApi.PolarDeviceDataType.ECG]?.selectedSettings?.let {
                    updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.ECG, StreamingFeatureState.STATES.STOPPED,
                        it
                    )
                }
            }
            PolarBleApi.PolarDeviceDataType.ACC -> {
                polarDeviceStreamingRepository.stopStreaming(deviceId, PmdMeasurementType.ACC)
                settingsCache[PolarBleApi.PolarDeviceDataType.ACC]?.selectedSettings?.let {
                    updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.ACC, StreamingFeatureState.STATES.STOPPED,
                        it
                    )
                }
            }
            PolarBleApi.PolarDeviceDataType.PPG -> {
                polarDeviceStreamingRepository.stopStreaming(deviceId, PmdMeasurementType.PPG)
                settingsCache[PolarBleApi.PolarDeviceDataType.PPG]?.selectedSettings?.let {
                    updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.PPG, StreamingFeatureState.STATES.STOPPED,
                        it
                    )
                }
            }
            PolarBleApi.PolarDeviceDataType.PPI -> {
                polarDeviceStreamingRepository.stopStreaming(deviceId, PmdMeasurementType.PPI)
                settingsCache[PolarBleApi.PolarDeviceDataType.PPI]?.selectedSettings?.let {
                    updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.PPI, StreamingFeatureState.STATES.STOPPED,
                        it
                    )
                }
            }
            PolarBleApi.PolarDeviceDataType.GYRO -> {
                polarDeviceStreamingRepository.stopStreaming(deviceId, PmdMeasurementType.GYRO)
                settingsCache[PolarBleApi.PolarDeviceDataType.GYRO]?.selectedSettings?.let {
                    updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.GYRO, StreamingFeatureState.STATES.STOPPED,
                        it
                    )
                }
            }
            PolarBleApi.PolarDeviceDataType.MAGNETOMETER -> {
                polarDeviceStreamingRepository.stopStreaming(deviceId, PmdMeasurementType.MAGNETOMETER)
                settingsCache[PolarBleApi.PolarDeviceDataType.MAGNETOMETER]?.selectedSettings?.let {
                    updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.MAGNETOMETER, StreamingFeatureState.STATES.STOPPED,
                        it
                    )
                }
            }
            PolarBleApi.PolarDeviceDataType.PRESSURE -> {
                polarDeviceStreamingRepository.stopStreaming(deviceId, PmdMeasurementType.PRESSURE)
                settingsCache[PolarBleApi.PolarDeviceDataType.PRESSURE]?.selectedSettings?.let {
                    updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.PRESSURE, StreamingFeatureState.STATES.STOPPED,
                        it
                    )
                }
            }
            PolarBleApi.PolarDeviceDataType.LOCATION -> {
                polarDeviceStreamingRepository.stopStreaming(deviceId, PmdMeasurementType.LOCATION)
                settingsCache[PolarBleApi.PolarDeviceDataType.LOCATION]?.selectedSettings?.let {
                    updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.LOCATION, StreamingFeatureState.STATES.STOPPED,
                        it
                    )
                }
            }
            PolarBleApi.PolarDeviceDataType.TEMPERATURE -> {
                polarDeviceStreamingRepository.stopStreaming(deviceId, PmdMeasurementType.TEMPERATURE)
                settingsCache[PolarBleApi.PolarDeviceDataType.TEMPERATURE]?.selectedSettings?.let {
                    updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.TEMPERATURE, StreamingFeatureState.STATES.STOPPED,
                        it
                    )
                }
            }

            PolarBleApi.PolarDeviceDataType.SKIN_TEMPERATURE -> {
                polarDeviceStreamingRepository.stopStreaming(deviceId, PmdMeasurementType.SKIN_TEMP)
                settingsCache[PolarBleApi.PolarDeviceDataType.SKIN_TEMPERATURE]?.selectedSettings?.let {
                    updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.SKIN_TEMPERATURE, StreamingFeatureState.STATES.STOPPED,
                        it
                    )
                }
            }

            PolarBleApi.PolarDeviceDataType.HR -> {
                polarDeviceStreamingRepository.stopHrStreaming(deviceId)
                settingsCache[PolarBleApi.PolarDeviceDataType.HR]?.selectedSettings?.let {
                    updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.HR, StreamingFeatureState.STATES.STOPPED,
                        it
                    )
                }
            }
        }
        finalizeCollector()
    }

    private fun startEcgStream() {
        val disposable = getStreamSettingsToStartStream(PolarBleApi.PolarDeviceDataType.ECG)
            .observeOn(Schedulers.io())
            .toFlowable()
            .flatMap { settings: Map<PolarSensorSetting.SettingType, Int> ->
                Log.d(TAG, "Start ECG stream with settings: $settings")
                polarDeviceStreamingRepository.getDeviceName(deviceId)?.let {
                    collector.startEcgLog(it)
                } ?: kotlin.run {
                    showError("Failed start ECG stream. Device name is not known")
                }
                updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.ECG, StreamingFeatureState.STATES.RECORDING, settings)
                polarDeviceStreamingRepository.startEcgStream(deviceId, PolarSensorSetting(settings))
            }
            .subscribeOn(Schedulers.io())
            .subscribe(
                { polarEcgData: PolarEcgData ->
                    logEcgData(polarEcgData)
                    val sampleRate = if (polarEcgData.samples.size > 1) {
                        StreamUtils.calculateSampleRate(timeStampEarlier = polarEcgData.samples[0].timeStamp, timeStampLater = polarEcgData.samples[1].timeStamp)
                    } else {
                        0.0
                    }
                    _uiEcgStreamDataState.update {
                        EcgSampleDataUiState(deviceId = deviceId, sampleRate, polarEcgData)
                    }
                },
                { error: Throwable ->
                    if (error !is PolarDeviceDisconnected) {
                        showError("ECG stream failed", error)
                    }
                    updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.ECG, StreamingFeatureState.STATES.STOPPED)
                }
            )
        streamDisposables[PolarBleApi.PolarDeviceDataType.ECG] = disposable
    }

    private fun startAccStream() {
        val disposable = getStreamSettingsToStartStream(PolarBleApi.PolarDeviceDataType.ACC)
            .observeOn(Schedulers.io())
            .toFlowable()
            .flatMap { settings: Map<PolarSensorSetting.SettingType, Int> ->
                Log.d(TAG, "Start ACC stream with settings: $settings")
                polarDeviceStreamingRepository.getDeviceName(deviceId)?.let {
                    collector.startAccLog(it)
                } ?: kotlin.run {
                    showError("Failed start ACC stream. Device name is not known")
                }
                updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.ACC, StreamingFeatureState.STATES.RECORDING, settings)
                polarDeviceStreamingRepository.startAccStreaming(deviceId, PolarSensorSetting(settings))
            }
            .subscribeOn(Schedulers.io())
            .subscribe(
                { accData: PolarAccelerometerData ->
                    logAccData(accData)
                    val sampleRate = if (accData.samples.size > 1) {
                        StreamUtils.calculateSampleRate(timeStampEarlier = accData.samples[0].timeStamp, timeStampLater = accData.samples[1].timeStamp)
                    } else {
                        0.0
                    }
                    _uiAccStreamDataState.update {
                        AccSampleDataUiState(deviceId = deviceId, calculatedFrequency = sampleRate, sampleData = accData)
                    }
                },
                { error: Throwable ->
                    if (error !is PolarDeviceDisconnected) {
                        showError("ACC stream failed", error)
                    }
                    updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.ACC, StreamingFeatureState.STATES.STOPPED)
                }
            )
        streamDisposables[PolarBleApi.PolarDeviceDataType.ACC] = disposable
    }

    private fun startGyroStream() {
        val disposable = getStreamSettingsToStartStream(PolarBleApi.PolarDeviceDataType.GYRO)
            .toFlowable()
            .flatMap { settings: Map<PolarSensorSetting.SettingType, Int> ->
                Log.d(TAG, "Start Gyro stream with settings: $settings")
                polarDeviceStreamingRepository.getDeviceName(deviceId)?.let {
                    collector.startGyroLog(it)
                } ?: kotlin.run {
                    showError("Failed start Gyro stream. Device name is not known")
                }
                updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.GYRO, StreamingFeatureState.STATES.RECORDING, settings)
                polarDeviceStreamingRepository.startGyroStreaming(deviceId, PolarSensorSetting(settings))
            }
            .subscribeOn(Schedulers.io())
            .subscribe(
                { polarGyroData: PolarGyroData ->
                    logGyroData(polarGyroData)
                    val sampleRate = if (polarGyroData.samples.size > 1) {
                        StreamUtils.calculateSampleRate(timeStampEarlier = polarGyroData.samples[0].timeStamp, timeStampLater = polarGyroData.samples[1].timeStamp)
                    } else {
                        0.0
                    }

                    _uiGyroStreamDataState.update {
                        GyroSampleDataUiState(deviceId = deviceId, sampleRate, polarGyroData)
                    }
                },
                { error: Throwable ->
                    if (error !is PolarDeviceDisconnected) {
                        showError("Gyro stream failed", error)
                    }
                }
            )
        streamDisposables[PolarBleApi.PolarDeviceDataType.GYRO] = disposable
    }

    private fun startMagnetometerStream() {
        val disposable = getStreamSettingsToStartStream(PolarBleApi.PolarDeviceDataType.MAGNETOMETER)
            .observeOn(Schedulers.io())
            .toFlowable()
            .flatMap { settings: Map<PolarSensorSetting.SettingType, Int> ->
                Log.d(TAG, "Start Magnetometer stream with settings: $settings")
                polarDeviceStreamingRepository.getDeviceName(deviceId)?.let {
                    collector.startMagnetometerLog(it)
                } ?: kotlin.run {
                    showError("Failed start Mag stream. Device name is not known")
                }

                updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.MAGNETOMETER, StreamingFeatureState.STATES.RECORDING, settings)
                polarDeviceStreamingRepository.startMagnetometerStream(deviceId, PolarSensorSetting(settings))
            }
            .subscribeOn(Schedulers.io())
            .subscribe(
                { polarMagData: PolarMagnetometerData ->
                    logMagnetometerData(polarMagData)
                    val sampleRate = if (polarMagData.samples.size > 1) {
                        StreamUtils.calculateSampleRate(timeStampEarlier = polarMagData.samples[0].timeStamp, timeStampLater = polarMagData.samples[1].timeStamp)
                    } else {
                        0.0
                    }

                    _uiMagnStreamDataState.update {
                        MagnSampleDataUiState(deviceId = deviceId, calculatedFrequency = sampleRate, sampleData = polarMagData)
                    }
                },
                { error: Throwable ->
                    if (error !is PolarDeviceDisconnected) {
                        showError("MAG stream failed", error)
                    }
                }
            )
        streamDisposables[PolarBleApi.PolarDeviceDataType.MAGNETOMETER] = disposable
    }

    private fun startPpiStream() {
        val disposable =
            Single.fromCallable {}
                .toFlowable()
                .flatMap {
                    Log.d(TAG, "Start PPI stream")
                    polarDeviceStreamingRepository.getDeviceName(deviceId)?.let {
                        collector.startPpiLog(it)
                    } ?: kotlin.run {
                        showError("Failed start PPI stream. Device name is not known")
                    }

                    updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.PPI, StreamingFeatureState.STATES.RECORDING)
                    polarDeviceStreamingRepository.startPpiStream(deviceId)
                }
                .subscribeOn(Schedulers.io())
                .subscribe(
                    { ppiSampleData: PolarPpiData ->
                        logPpiData(ppiSampleData)
                        _uiPpiStreamDataState.update {
                            it.copy(deviceId = deviceId, sampleData = ppiSampleData.samples[0])
                        }
                    },
                    { error: Throwable ->
                        if (error !is PolarDeviceDisconnected) {
                            showError("PPI stream failed", error)
                        }
                    }
                )
        streamDisposables[PolarBleApi.PolarDeviceDataType.PPI] = disposable
    }

    private fun startPpgStream() {
        val ppgStreamsConnectable = getStreamSettingsToStartStream(PolarBleApi.PolarDeviceDataType.PPG)
            .observeOn(Schedulers.io())
            .toFlowable()
            .flatMap { settings: Map<PolarSensorSetting.SettingType, Int> ->
                Log.d(TAG, "Start PPG stream with settings: $settings")
                polarDeviceStreamingRepository.getDeviceName(deviceId)?.let {
                    collector.startPpgLog(it)
                } ?: kotlin.run {
                    showError("Failed start PPG stream. Device name is not known")
                }
                updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.PPG, StreamingFeatureState.STATES.RECORDING, settings)
                polarDeviceStreamingRepository.startPpgStream(deviceId, PolarSensorSetting(settings))
            }.publish()

        ppgStreamsConnectable.filter { value: PolarPpgData -> value.type == PolarPpgData.PpgDataType.PPG3_AMBIENT1 }
            .subscribeOn(Schedulers.io())
            .subscribe(
                { polarPpgData: PolarPpgData ->
                    logPpgData(polarPpgData)
                    val sampleRate = if (polarPpgData.samples.size > 1) {
                        StreamUtils.calculateSampleRate(timeStampEarlier = polarPpgData.samples[0].timeStamp, timeStampLater = polarPpgData.samples[1].timeStamp)
                    } else {
                        0.0
                    }

                    _uiPpgStreamDataState.update {
                        PpgSampleDataUiState(deviceId = deviceId, calculatedFrequency = sampleRate, sampleData = polarPpgData)
                    }

                },
                { error: Throwable ->
                    if (error !is PolarDeviceDisconnected) {
                        showError("PPG3_AMBIENT1 stream failed", error)
                    }
                }
            )

        ppgStreamsConnectable.filter { value: PolarPpgData -> value.type == PolarPpgData.PpgDataType.FRAME_TYPE_7 }
            .subscribeOn(Schedulers.io())
            .subscribe(
                { polarPpgData: PolarPpgData ->
                    logPpgFrameType7(polarPpgData)
                    val sampleRate = if (polarPpgData.samples.size > 1) {
                        StreamUtils.calculateSampleRate(timeStampEarlier = polarPpgData.samples[0].timeStamp, timeStampLater = polarPpgData.samples[1].timeStamp)
                    } else {
                        0.0
                    }
                    _uiPpgStreamDataState.update {
                        PpgSampleDataUiState(deviceId = deviceId, calculatedFrequency = sampleRate, sampleData = polarPpgData)
                    }
                },
                { error: Throwable ->
                    if (error !is PolarDeviceDisconnected) {
                        showError("PPG16 stream failed", error)
                    }
                }
            )

        ppgStreamsConnectable.filter { value: PolarPpgData -> value.type == PolarPpgData.PpgDataType.FRAME_TYPE_8 }
            .subscribeOn(Schedulers.io())
            .subscribe(
                { polarPpgData: PolarPpgData ->
                    logPpgFrameType8(polarPpgData)
                    val sampleRate = if (polarPpgData.samples.size > 1) {
                        StreamUtils.calculateSampleRate(timeStampEarlier = polarPpgData.samples[0].timeStamp, timeStampLater = polarPpgData.samples[1].timeStamp)
                    } else {
                        0.0
                    }
                    _uiPpgStreamDataState.update {
                        PpgSampleDataUiState(deviceId = deviceId, calculatedFrequency = sampleRate, sampleData = polarPpgData)
                    }
                },
                { error: Throwable ->
                    if (error !is PolarDeviceDisconnected) {
                        showError("PPG24 stream failed", error)
                    }
                }
            )

        ppgStreamsConnectable.filter { value: PolarPpgData -> value.type == PolarPpgData.PpgDataType.SPORT_ID }
            .subscribeOn(Schedulers.io())
            .subscribe(
                { ohrData: PolarPpgData ->
                    logPpgSportIdData(ohrData)
                },
                { error: Throwable ->
                    if (error !is PolarDeviceDisconnected) {
                        showError("PPG SPORT_ID stream failed", error)
                    }
                }
            )

        ppgStreamsConnectable.filter { value: PolarPpgData -> value.type == PolarPpgData.PpgDataType.FRAME_TYPE_4 }
            .subscribeOn(Schedulers.io())
            .subscribe(
                { ohrData: PolarPpgData ->
                    logPpgFrameType4(ohrData)
                },
                { error: Throwable ->
                    if (error !is PolarDeviceDisconnected) {
                        showError("PPG FRAME_TYPE_4 stream failed", error)
                    }
                }
            )

        ppgStreamsConnectable.filter { value: PolarPpgData -> value.type == PolarPpgData.PpgDataType.FRAME_TYPE_5 }
            .subscribeOn(Schedulers.io())
            .subscribe(
                { ohrData: PolarPpgData ->
                    logPpgFrameType5(ohrData)
                },
                { error: Throwable ->
                    if (error !is PolarDeviceDisconnected) {
                        showError("PPG FRAME_TYPE_5 stream failed", error)
                    }
                }
            )

        ppgStreamsConnectable.filter { value: PolarPpgData -> value.type == PolarPpgData.PpgDataType.FRAME_TYPE_9 }
            .subscribeOn(Schedulers.io())
            .subscribe(
                { ohrData: PolarPpgData ->
                    logPpgFrameType9(ohrData)
                },
                { error: Throwable ->
                    if (error !is PolarDeviceDisconnected) {
                        showError("PPG FRAME_TYPE_9 stream failed", error)
                    }
                }
            )

        ppgStreamsConnectable.filter { value: PolarPpgData -> value.type == PolarPpgData.PpgDataType.FRAME_TYPE_10 }
            .subscribeOn(Schedulers.io())
            .subscribe(
                { ppgData: PolarPpgData ->
                    logPpgFrameType10(ppgData)
                    val sampleRate = if (ppgData.samples.size > 1) {
                        StreamUtils.calculateSampleRate(timeStampEarlier = ppgData.samples[0].timeStamp, timeStampLater = ppgData.samples[1].timeStamp)
                    } else {
                        0.0
                    }
                    _uiPpgStreamDataState.update {
                        PpgSampleDataUiState(deviceId = deviceId, calculatedFrequency = sampleRate, sampleData = ppgData)
                    }
                },
                { error: Throwable ->
                    if (error !is PolarDeviceDisconnected) {
                        showError("PPG FRAME_TYPE_10 stream failed", error)
                    }
                }
            )



        val disposable = ppgStreamsConnectable.connect()
        streamDisposables[PolarBleApi.PolarDeviceDataType.PPG] = disposable
    }

    private fun startPressureStream() {
        val disposable = getStreamSettingsToStartStream(PolarBleApi.PolarDeviceDataType.PRESSURE)
            .toFlowable()
            .flatMap { settings: Map<PolarSensorSetting.SettingType, Int> ->
                Log.d(TAG, "Start Pressure stream with settings: $settings")
                polarDeviceStreamingRepository.getDeviceName(deviceId)?.let {
                    collector.startPressureLog(it)
                } ?: kotlin.run {
                    showError("Failed start PPG stream. Device name is not known")
                }
                updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.PRESSURE, StreamingFeatureState.STATES.RECORDING, settings)
                polarDeviceStreamingRepository.startPressureStream(deviceId, PolarSensorSetting(settings))
            }
            .subscribeOn(Schedulers.io())
            .subscribe(
                { polarPressureData: PolarPressureData ->
                    logPressureData(polarPressureData)
                    val sampleRate = if (polarPressureData.samples.size > 1) {
                        StreamUtils.calculateSampleRate(timeStampEarlier = polarPressureData.samples[0].timeStamp, timeStampLater = polarPressureData.samples[1].timeStamp)
                    } else {
                        0.0
                    }

                    _uiPressureStreamDataState.update {
                        PressureSampleDataUiState(deviceId = deviceId, calculatedFrequency = sampleRate, sampleData = polarPressureData)
                    }
                },
                { error: Throwable ->
                    if (error !is PolarDeviceDisconnected) {
                        showError("PRESSURE stream failed", error)
                    }
                }
            )
        streamDisposables[PolarBleApi.PolarDeviceDataType.PRESSURE] = disposable
    }

    private fun startLocationStream() {
        val locationStreamsConnectable = getStreamSettingsToStartStream(PolarBleApi.PolarDeviceDataType.LOCATION)
            .observeOn(Schedulers.io())
            .toFlowable()
            .flatMap { settings: Map<PolarSensorSetting.SettingType, Int> ->
                Log.d(TAG, "Start Location stream with settings: $settings")
                polarDeviceStreamingRepository.getDeviceName(deviceId)?.let {
                    collector.startLocationLog(it)
                } ?: kotlin.run {
                    showError("Failed start Location stream. Device name is not known")
                }

                updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.LOCATION, StreamingFeatureState.STATES.RECORDING, settings)
                polarDeviceStreamingRepository.startLocationStream(deviceId, PolarSensorSetting(settings))
            }.publish()

        locationStreamsConnectable.filter { value: PolarLocationData -> value.samples.first() is GpsCoordinatesSample }
            .subscribe(
                { polarLocationData: PolarLocationData ->
                    val sampleRate = if (polarLocationData.samples.size > 1) {
                        StreamUtils.calculateSampleRate(timeStampEarlier = (polarLocationData.samples[0] as GpsCoordinatesSample).timeStamp, timeStampLater = (polarLocationData.samples[1] as GpsCoordinatesSample).timeStamp)
                    } else {
                        0.0
                    }

                    logLocationData(polarLocationData)
                    _uiLocationStreamDataState.update {
                        LocationSampleDataUiState(deviceId = deviceId, calculatedFrequency = sampleRate, polarLocationData)
                    }
                },
                { error: Throwable ->
                    if (error !is PolarDeviceDisconnected) {
                        showError("LOCATION GpsCoordinatesSample stream failed", error)
                    }
                }
            )

        locationStreamsConnectable.filter { value: PolarLocationData -> value.samples.first() is GpsSatelliteDilutionSample }
            .subscribe(
                { polarLocationData: PolarLocationData ->
                    logLocationData(polarLocationData)
                },
                { error: Throwable ->
                    if (error !is PolarDeviceDisconnected) {
                        showError("LOCATION GpsSatelliteDilutionSample stream failed", error)
                    }
                }
            )

        locationStreamsConnectable.filter { value: PolarLocationData -> value.samples.first() is GpsSatelliteSummarySample }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { polarLocationData: PolarLocationData ->
                    logLocationData(polarLocationData)
                },
                { error: Throwable ->
                    if (error !is PolarDeviceDisconnected) {
                        showError("LOCATION GpsSatelliteSummarySample stream failed", error)
                    }
                }
            )

        locationStreamsConnectable.filter { value: PolarLocationData -> value.samples.first() is GpsNMEASample }
            .subscribe(
                { polarLocationData: PolarLocationData ->
                    logLocationData(polarLocationData)
                },
                { error: Throwable ->
                    if (error !is PolarDeviceDisconnected) {
                        showError("LOCATION timeStampPreviousPacketLocationNMEA stream failed", error)
                    }
                }
            )

        val disposable = locationStreamsConnectable.connect()
        streamDisposables[PolarBleApi.PolarDeviceDataType.LOCATION] = disposable
    }

    private fun startTemperatureStream() {
        val disposable = getStreamSettingsToStartStream(PolarBleApi.PolarDeviceDataType.TEMPERATURE)
            .toFlowable()
            .flatMap { settings: Map<PolarSensorSetting.SettingType, Int> ->
                Log.d(TAG, "Start Temperature stream with settings: $settings")
                polarDeviceStreamingRepository.getDeviceName(deviceId)?.let {
                    collector.startTemperatureLog(it)
                } ?: kotlin.run {
                    showError("Failed start Temperature stream. Device name is not known")
                }
                updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.TEMPERATURE, StreamingFeatureState.STATES.RECORDING, settings)
                polarDeviceStreamingRepository.startTemperatureStreaming(deviceId, PolarSensorSetting(settings))
            }
            .subscribeOn(Schedulers.io())
            .subscribe(
                { polarTemperatureData: PolarTemperatureData ->
                    logTemperatureData(polarTemperatureData)
                    val sampleRate = if (polarTemperatureData.samples.size > 1) {
                        StreamUtils.calculateSampleRate(timeStampEarlier = polarTemperatureData.samples[0].timeStamp, timeStampLater = polarTemperatureData.samples[1].timeStamp)
                    } else {
                        0.0
                    }

                    _uiTemperatureStreamDataState.update {
                        TemperatureSampleDataUiState(deviceId = deviceId, calculatedFrequency = sampleRate, sampleData = polarTemperatureData)
                    }
                },
                { error: Throwable ->
                    if (error !is PolarDeviceDisconnected) {
                        showError("TEMPERATURE stream failed", error)
                    }
                }
            )
        streamDisposables[PolarBleApi.PolarDeviceDataType.TEMPERATURE] = disposable
    }

    private fun startSkinTemperatureStream() {
        val disposable = getStreamSettingsToStartStream(PolarBleApi.PolarDeviceDataType.SKIN_TEMPERATURE)
            .toFlowable()
            .flatMap { settings: Map<PolarSensorSetting.SettingType, Int> ->
                Log.d(TAG, "Start Skin Temperature stream with settings: $settings")
                polarDeviceStreamingRepository.getDeviceName(deviceId)?.let {
                    collector.startSkinTemperatureLog(it)
                } ?: kotlin.run {
                    showError("Failed start Skin Temperature stream. Device name is not known")
                }
                updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.SKIN_TEMPERATURE, StreamingFeatureState.STATES.RECORDING, settings)
                polarDeviceStreamingRepository.startSkinTemperatureStreaming(deviceId, PolarSensorSetting(settings))
            }
            .subscribeOn(Schedulers.io())
            .subscribe(
                { polarTemperatureData: PolarTemperatureData ->
                    logSkinTemperatureData(polarTemperatureData)
                    val sampleRate = if (polarTemperatureData.samples.size > 1) {
                        StreamUtils.calculateSampleRate(timeStampEarlier = polarTemperatureData.samples[0].timeStamp, timeStampLater = polarTemperatureData.samples[1].timeStamp)
                    } else {
                        0.0
                    }

                    _uiSkinTemperatureStreamDataState.update {
                        SkinTemperatureSampleDataUiState(deviceId = deviceId, calculatedFrequency = sampleRate, sampleData = polarTemperatureData)
                    }
                },
                { error: Throwable ->
                    if (error !is PolarDeviceDisconnected) {
                        showError("SKIN TEMPERATURE stream failed", error)
                    }
                }
            )
        streamDisposables[PolarBleApi.PolarDeviceDataType.SKIN_TEMPERATURE] = disposable
    }

    @Throws(IOException::class)
    private fun logEcgData(ecgData: PolarEcgData) {
        for (sample in ecgData.samples) {
            when (sample) {
                is EcgSample -> collector.logEcg(sample.timeStamp, sample.voltage)
                is FecgSample -> collector.logFecg(
                    sample.timeStamp,
                    ecg = sample.ecg,
                    bioz = sample.bioz,
                    status = sample.status
                )
            }
        }
    }

    @Throws(IOException::class)
    private fun logAccData(accData: PolarAccelerometerData) {
        for (sample in accData.samples) {
            collector.logAcc(sample.timeStamp, sample.x, sample.y, sample.z)
        }
    }

    @Throws(IOException::class)
    private fun logGyroData(gyroData: PolarGyroData) {
        for (sample in gyroData.samples) {
            collector.logGyro(sample.timeStamp, sample.x, sample.y, sample.z)
        }
    }

    @Throws(IOException::class)
    private fun logMagnetometerData(magnetometerData: PolarMagnetometerData) {
        for (sample in magnetometerData.samples) {
            collector.logMagnetometer(sample.timeStamp, sample.x, sample.y, sample.z)
        }
    }

    @Throws(IOException::class)
    private fun logPressureData(pressureData: PolarPressureData) {
        for (sample in pressureData.samples) {
            collector.logPressure(sample.timeStamp, sample.pressure)
        }
    }

    @Throws(IOException::class)
    private fun logLocationData(locationData: PolarLocationData) {
        for (sample in locationData.samples) {
            when (sample) {
                is GpsCoordinatesSample -> collector.logLocationCoordinates(sample.timeStamp, sample)
                is GpsSatelliteDilutionSample -> collector.logLocationDilution(sample.timeStamp, sample)
                is GpsSatelliteSummarySample -> collector.logLocationSatelliteSummary(sample.timeStamp, sample)
                is GpsNMEASample -> collector.logLocationNmeaSummary(sample.timeStamp, sample)
            }
        }
    }

    @Throws(IOException::class)
    private fun logTemperatureData(temperatureData: PolarTemperatureData) {
        for (sample in temperatureData.samples) {
            collector.logTemperature(sample.timeStamp, sample.temperature)
        }
    }

    @Throws(IOException::class)
    private fun logPpgData(polarPpgData: PolarPpgData) {
        for (sample in polarPpgData.samples) {
            check(sample.channelSamples.size == 4) { "Received UNKNOWN PPG Data" }
            val ppgs: List<Int> =
                sample.channelSamples.subList(0, 3)
            val ambient = sample.channelSamples[3]
            collector.logPpg(sample.timeStamp, ppgs, ambient)
        }
    }

    @Throws(IOException::class)
    private fun logPpgFrameType7(polarPpgData: PolarPpgData) {
        for (sample in polarPpgData.samples) {
            check(sample.channelSamples.size == 17) { "Received UNKNOWN PPG Frame Type 7 Data" }
            val ppgs: List<Int> = sample.channelSamples.subList(0, 16)
            val status = sample.channelSamples[16].toUInt().toLong()
            collector.logPpgData16Channels(sample.timeStamp, ppgs, status)
        }
    }

    @Throws(IOException::class)
    private fun logPpgFrameType8(polarPpgData: PolarPpgData) {
        for (sample in polarPpgData.samples) {
            check(sample.channelSamples.size == 25) { "Received UNKNOWN PPG Frame Type 8 Data" }
            val ppgGreen: List<Int> = sample.channelSamples.subList(0, 8)
            val ppgRed: List<Int> = sample.channelSamples.subList(8, 20)
            val ppgIr: List<Int> = sample.channelSamples.subList(20, 24)
            collector.logPpgGreen(sample.timeStamp, ppgGreen)
            collector.logPpgRed(sample.timeStamp, ppgRed)
            collector.logPpgIr(sample.timeStamp, ppgIr)
        }
    }

    @Throws(IOException::class)
    private fun logPpgSportIdData(polarPpgData: PolarPpgData) {
        check(polarPpgData.samples[0].channelSamples.size == 1) { "Received UNKNOWN PPG Sport Id Data" }
        val sportId = polarPpgData.samples[0].channelSamples[0].toUInt().toLong()
        collector.logPpgSportIdData(polarPpgData.samples[0].timeStamp, sportId = sportId)
    }

    @Throws(IOException::class)
    private fun logPpgFrameType4(polarPpgData: PolarPpgData) {
        check(polarPpgData.samples[0].channelSamples.size == 36) { "Received UNKNOWN PPG Frame Type 4 Data" }
        val channel1GainTs: List<Int> = polarPpgData.samples[0].channelSamples.subList(0, 12)
        val channel2GainTs: List<Int> = polarPpgData.samples[0].channelSamples.subList(12, 24)
        val numIntTs: List<Int> = polarPpgData.samples[0].channelSamples.subList(24, 36)
        collector.logPpgAdpd4000Data(polarPpgData.samples[0].timeStamp, channel1GainTs = channel1GainTs, channel2GainTs = channel2GainTs, numIntTs = numIntTs)
    }

    @Throws(IOException::class)
    private fun logPpgFrameType5(polarPpgData: PolarPpgData) {
        check(polarPpgData.samples[0].channelSamples.size == 1) { "Received UNKNOWN PPG Frame Type 5 Data" }
        val operationMode = polarPpgData.samples[0].channelSamples[0].toUInt()
        collector.logPpgOperationMode(polarPpgData.samples[0].timeStamp, operationMode = operationMode)
    }

    @Throws(IOException::class)
    private fun logPpgFrameType9(polarPpgData: PolarPpgData) {
        check(polarPpgData.samples[0].channelSamples.size == 36) { "Received UNKNOWN PPG Frame Type 9 Data" }
        val channel1GainTs: List<Int> = polarPpgData.samples[0].channelSamples.subList(0, 12)
        val channel2GainTs: List<Int> = polarPpgData.samples[0].channelSamples.subList(12, 24)
        val numIntTs: List<Int> = polarPpgData.samples[0].channelSamples.subList(24, 36)
        collector.logPpgAdpd4100Data(polarPpgData.samples[0].timeStamp, channel1GainTs = channel1GainTs, channel2GainTs = channel2GainTs, numIntTs = numIntTs)
    }

    @Throws(IOException::class)
    private fun logPpgFrameType10(polarPpgData: PolarPpgData) {
        for (sample in polarPpgData.samples) {
            val ppgGreen: List<Int> = sample.channelSamples.subList(0, 8)
            val ppgRed: List<Int> = sample.channelSamples.subList(8, 14)
            val ppgIr: List<Int> = sample.channelSamples.subList(14, 20)
            collector.logPpgGreen(sample.timeStamp, ppgGreen)
            collector.logPpgRed(sample.timeStamp, ppgRed)
            collector.logPpgIr(sample.timeStamp, ppgIr)
        }
    }

    @Throws(IOException::class)
    private fun logPpiData(ppiSampleData: PolarPpiData) {
        for (sample in ppiSampleData.samples) {
            collector.logPpi(sample.ppi, sample.errorEstimate, sample.blockerBit, sample.skinContactStatus, sample.skinContactSupported, sample.hr, sample.timeStamp)
        }
    }

    @Throws(IOException::class)
    private fun logHrData(hrSampleData: PolarHrData) {
        for (sample in hrSampleData.samples) {
            collector.logHr(System.nanoTime(), data = sample)
        }
    }

    @Throws(IOException::class)
    private fun logSkinTemperatureData(temperatureData: PolarTemperatureData) {
        for (sample in temperatureData.samples) {
            collector.logSkinTemperature(sample.timeStamp, sample.temperature)
        }
    }

    private fun startRecTimer() {
        val disposable = recordingTimerDisposable
        if (disposable == null || disposable.isDisposed) {
            recordingTimerDisposable = Observable.interval(1, TimeUnit.SECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { time: Long ->
                    val hours = (time / 3600).toInt()
                    val minutes = (time / 60 % 60).toInt()
                    val seconds = (time % 60).toInt()
                    updateOnlineRecordingUiState(deviceId = "", timer = String.format("%02d:%02d:%02d", hours, minutes, seconds))
                }
        }
    }

    private fun stopRecTimer() {
        recordingTimerDisposable?.dispose()
        recordingTimerDisposable = null
    }

    private fun updateOnlineRecordingUiState(deviceId: String, timer: String = "") {
        _uiOnlineRecordingState.update {
            it.copy(deviceId = deviceId, timer = "")
        }
    }

    private fun showError(errorDescription: String, errorThrowable: Throwable? = null) {
        Log.e(TAG, "Show error: $errorDescription. Error reason $errorThrowable")
        _uiShowError.update {
            MessageUiState(header = errorDescription, description = errorThrowable?.message)
        }
    }

    private fun updateStreamingRecordingState(deviceId: String, feature: PolarBleApi.PolarDeviceDataType, state: StreamingFeatureState.STATES, settings: Map<PolarSensorSetting.SettingType, Int> = emptyMap()) {
        _uiStreamingState.update {
            val newSettings: Map<PolarSensorSetting.SettingType, Int> = settings.ifEmpty {
                if (state == StreamingFeatureState.STATES.PAUSED) {
                    // keep the settings
                    _uiStreamingState.value.streamingRecordingState[feature]?.settings ?: emptyMap()
                } else {
                    emptyMap()
                }
            }

            val updatedRecordingStates = it.streamingRecordingState.clone()
            updatedRecordingStates[feature] = StreamingFeatureState(state = state, settings = newSettings)
            it.copy(deviceId = deviceId, streamingRecordingState = updatedRecordingStates)
        }
    }

    override fun onCleared() {
        super.onCleared()
        for (disposable in streamDisposables.values) {
            disposable?.dispose()
        }
        streamDisposables.clear()
        stopRecTimer()


    }

    private fun finalizeCollector() {
        try {
            Single.fromCallable { collector.finalizeAllStreams() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { fileUris ->
                        if (fileUris.isNotEmpty()) {
                            _shareFiles.update {
                                fileUris
                            }
                        }
                    },
                    { error ->
                        showError("Failed to finalize stream files", error)
                    }
                )
        } catch (e: Exception) {
            showError("Exception when finalizing streams", e)
        }
    }


}


