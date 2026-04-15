package com.polar.polarsensordatacollector.ui.landing

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.polarsensordatacollector.DataCollector
import com.polar.polarsensordatacollector.repository.DeviceConnectionState
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.ui.graph.AccDataHolder
import com.polar.polarsensordatacollector.ui.graph.HrDataHolder
import com.polar.polarsensordatacollector.ui.utils.MessageUiState
import com.polar.polarsensordatacollector.utils.StreamUtils
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.errors.PolarDeviceDisconnected
import com.polar.sdk.api.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.*
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

    private val deviceId = state.get<String>(ONLINE_OFFLINE_KEY_DEVICE_ID)
        ?: throw Exception("Online recording viewModel must know the deviceId")

    private var settingsCache: EnumMap<PolarBleApi.PolarDeviceDataType, OnlineStreamSettings> =
        EnumMap(PolarBleApi.PolarDeviceDataType.values().associateWith { OnlineStreamSettings(null, null, null) })

    private val streamJobs: MutableMap<PolarBleApi.PolarDeviceDataType, Job?> =
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

    private var recordingTimerJob: Job? = null

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

    fun addMarkerToLog(isStartMarker: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        try {
            collector.marker(deviceId, isStartMarker, System.nanoTime())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to add marker: $e")
        }
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

    private suspend fun getStreamSettingsToStartStream(feature: PolarBleApi.PolarDeviceDataType): Map<PolarSensorSetting.SettingType, Int> {
        return settingsCache[feature]?.selectedSettings
            ?: run {
                val sensorSetting = polarDeviceStreamingRepository.getAvailableStreamSettings(deviceId, feature)
                val selectedSettings = maxSettingsFromStreamSettings(sensorSetting)
                updateSelectedStreamSettings(feature, selectedSettings)
                selectedSettings
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
        streamJobs[PolarBleApi.PolarDeviceDataType.HR]?.cancel()
        streamJobs[PolarBleApi.PolarDeviceDataType.HR] = viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Start HR stream")
            polarDeviceStreamingRepository.getDeviceName(deviceId)?.let {
                collector.startHrLog(it)
            } ?: showError("Failed to start HR stream. Device name is not known")
            updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.HR, StreamingFeatureState.STATES.RECORDING)
            polarDeviceStreamingRepository.startHrStreaming(deviceId)
                .catch { error ->
                    if (error !is PolarDeviceDisconnected) showError("HR stream failed", error)
                }
                .collect { polarHrData ->
                    logHrData(polarHrData)
                    val hrSample = polarHrData.samples.first()
                    HrDataHolder.updateHr(hrSample.hr)
                    _uiHeartRateInfoState.update { it.copy(deviceId = deviceId, heartRate = hrSample) }
                }
        }
    }

    fun requestStreamSettings(deviceId: String, feature: PolarBleApi.PolarDeviceDataType) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val available = polarDeviceStreamingRepository.getAvailableStreamSettings(deviceId, feature)
                val all = try {
                    polarDeviceStreamingRepository.requestFullStreamSettings(deviceId, feature)
                } catch (e: Exception) {
                    PolarSensorSetting(emptyMap())
                }

                if (available.settings.isEmpty()) {
                    showError("Settings are not available for feature $feature")
                    return@launch
                }

                Log.d(TAG, "Feature $feature available settings ${available.settings}")
                Log.d(TAG, "Feature $feature all settings ${all.settings}")

                val newSettings = OnlineStreamSettings(
                    currentlyAvailable = available,
                    allPossibleSettings = all,
                    selectedSettings = settingsCache[feature]?.selectedSettings
                )
                settingsCache[feature] = newSettings
                _uiOnlineRequestedSettingsState.update {
                    OnlineAvailableStreamSettingsUiState(feature = feature, settings = newSettings)
                }
            } catch (e: Exception) {
                showError("Settings fetch error for feature $feature", e)
            }
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
        val job = streamJobs[feature]
        if (job != null) {
            job.cancel()
            streamJobs[feature] = null
            updateStreamingRecordingState(deviceId, feature, StreamingFeatureState.STATES.PAUSED)
        } else {
            Log.d(TAG, "Pausing stream which has no job $feature")
        }
    }

    fun stopStream(feature: PolarBleApi.PolarDeviceDataType) {
        streamJobs[feature]?.cancel()
        streamJobs[feature] = null
        when (feature) {
            PolarBleApi.PolarDeviceDataType.ECG -> {
                polarDeviceStreamingRepository.stopStreaming(deviceId, PmdMeasurementType.ECG)
                settingsCache[feature]?.selectedSettings?.let { updateStreamingRecordingState(deviceId, feature, StreamingFeatureState.STATES.STOPPED, it) }
            }
            PolarBleApi.PolarDeviceDataType.ACC -> {
                polarDeviceStreamingRepository.stopStreaming(deviceId, PmdMeasurementType.ACC)
                settingsCache[feature]?.selectedSettings?.let { updateStreamingRecordingState(deviceId, feature, StreamingFeatureState.STATES.STOPPED, it) }
                AccDataHolder.clear()
            }
            PolarBleApi.PolarDeviceDataType.PPG -> {
                polarDeviceStreamingRepository.stopStreaming(deviceId, PmdMeasurementType.PPG)
                settingsCache[feature]?.selectedSettings?.let { updateStreamingRecordingState(deviceId, feature, StreamingFeatureState.STATES.STOPPED, it) }
            }
            PolarBleApi.PolarDeviceDataType.PPI -> {
                viewModelScope.launch { polarDeviceStreamingRepository.stopHrStreaming(deviceId) }
                updateStreamingRecordingState(deviceId, feature, StreamingFeatureState.STATES.STOPPED, emptyMap())
            }
            PolarBleApi.PolarDeviceDataType.GYRO -> {
                polarDeviceStreamingRepository.stopStreaming(deviceId, PmdMeasurementType.GYRO)
                settingsCache[feature]?.selectedSettings?.let { updateStreamingRecordingState(deviceId, feature, StreamingFeatureState.STATES.STOPPED, it) }
            }
            PolarBleApi.PolarDeviceDataType.MAGNETOMETER -> {
                polarDeviceStreamingRepository.stopStreaming(deviceId, PmdMeasurementType.MAGNETOMETER)
                settingsCache[feature]?.selectedSettings?.let { updateStreamingRecordingState(deviceId, feature, StreamingFeatureState.STATES.STOPPED, it) }
            }
            PolarBleApi.PolarDeviceDataType.PRESSURE -> {
                polarDeviceStreamingRepository.stopStreaming(deviceId, PmdMeasurementType.PRESSURE)
                settingsCache[feature]?.selectedSettings?.let { updateStreamingRecordingState(deviceId, feature, StreamingFeatureState.STATES.STOPPED, it) }
            }
            PolarBleApi.PolarDeviceDataType.LOCATION -> {
                polarDeviceStreamingRepository.stopStreaming(deviceId, PmdMeasurementType.LOCATION)
                settingsCache[feature]?.selectedSettings?.let { updateStreamingRecordingState(deviceId, feature, StreamingFeatureState.STATES.STOPPED, it) }
            }
            PolarBleApi.PolarDeviceDataType.TEMPERATURE -> {
                polarDeviceStreamingRepository.stopStreaming(deviceId, PmdMeasurementType.TEMPERATURE)
                settingsCache[feature]?.selectedSettings?.let { updateStreamingRecordingState(deviceId, feature, StreamingFeatureState.STATES.STOPPED, it) }
            }
            PolarBleApi.PolarDeviceDataType.SKIN_TEMPERATURE -> {
                polarDeviceStreamingRepository.stopStreaming(deviceId, PmdMeasurementType.SKIN_TEMP)
                settingsCache[feature]?.selectedSettings?.let { updateStreamingRecordingState(deviceId, feature, StreamingFeatureState.STATES.STOPPED, it) }
            }
            PolarBleApi.PolarDeviceDataType.HR -> {
                viewModelScope.launch { polarDeviceStreamingRepository.stopHrStreaming(deviceId) }
                updateStreamingRecordingState(deviceId, feature, StreamingFeatureState.STATES.STOPPED, emptyMap())
                HrDataHolder.clear()
            }
        }
        finalizeCollector()
    }

    private fun startEcgStream() {
        streamJobs[PolarBleApi.PolarDeviceDataType.ECG]?.cancel()
        streamJobs[PolarBleApi.PolarDeviceDataType.ECG] = viewModelScope.launch(Dispatchers.IO) {
            val settings = try { getStreamSettingsToStartStream(PolarBleApi.PolarDeviceDataType.ECG) } catch (e: Exception) { showError("Failed to get ECG settings", e); return@launch }
            Log.d(TAG, "Start ECG stream with settings: $settings")
            polarDeviceStreamingRepository.getDeviceName(deviceId)?.let { collector.startEcgLog(it) } ?: showError("Failed start ECG stream. Device name is not known")
            updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.ECG, StreamingFeatureState.STATES.RECORDING, settings)
            polarDeviceStreamingRepository.startEcgStream(deviceId, PolarSensorSetting(settings))
                .catch { error ->
                    if (error !is PolarDeviceDisconnected) showError("ECG stream failed", error)
                    updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.ECG, StreamingFeatureState.STATES.STOPPED)
                }
                .collect { polarEcgData ->
                    logEcgData(polarEcgData)
                    val sampleRate = if (polarEcgData.samples.size > 1) StreamUtils.calculateSampleRate(polarEcgData.samples[0].timeStamp, polarEcgData.samples[1].timeStamp) else 0.0
                    _uiEcgStreamDataState.update { EcgSampleDataUiState(deviceId = deviceId, sampleRate, polarEcgData) }
                }
        }
    }

    private fun startAccStream() {
        streamJobs[PolarBleApi.PolarDeviceDataType.ACC]?.cancel()
        streamJobs[PolarBleApi.PolarDeviceDataType.ACC] = viewModelScope.launch(Dispatchers.IO) {
            val settings = try { getStreamSettingsToStartStream(PolarBleApi.PolarDeviceDataType.ACC) } catch (e: Exception) { showError("Failed to get ACC settings", e); return@launch }
            Log.d(TAG, "Start ACC stream with settings: $settings")
            polarDeviceStreamingRepository.getDeviceName(deviceId)?.let { collector.startAccLog(it) } ?: showError("Failed start ACC stream. Device name is not known")
            updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.ACC, StreamingFeatureState.STATES.RECORDING, settings)
            polarDeviceStreamingRepository.startAccStreaming(deviceId, PolarSensorSetting(settings))
                .catch { error ->
                    if (error !is PolarDeviceDisconnected) showError("ACC stream failed", error)
                    updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.ACC, StreamingFeatureState.STATES.STOPPED)
                }
                .collect { accData ->
                    logAccData(accData)
                    val sampleRate = if (accData.samples.size > 1) StreamUtils.calculateSampleRate(accData.samples[0].timeStamp, accData.samples[1].timeStamp) else 0.0
                    _uiAccStreamDataState.update { AccSampleDataUiState(deviceId = deviceId, calculatedFrequency = sampleRate, sampleData = accData) }
                    accData.samples.forEach { s -> AccDataHolder.updateAcc(s.x, s.y, s.z) }
                }
        }
    }

    private fun startGyroStream() {
        streamJobs[PolarBleApi.PolarDeviceDataType.GYRO]?.cancel()
        streamJobs[PolarBleApi.PolarDeviceDataType.GYRO] = viewModelScope.launch(Dispatchers.IO) {
            val settings = try { getStreamSettingsToStartStream(PolarBleApi.PolarDeviceDataType.GYRO) } catch (e: Exception) { showError("Failed to get Gyro settings", e); return@launch }
            Log.d(TAG, "Start Gyro stream with settings: $settings")
            polarDeviceStreamingRepository.getDeviceName(deviceId)?.let { collector.startGyroLog(it) } ?: showError("Failed start Gyro stream. Device name is not known")
            updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.GYRO, StreamingFeatureState.STATES.RECORDING, settings)
            polarDeviceStreamingRepository.startGyroStreaming(deviceId, PolarSensorSetting(settings))
                .catch { error -> if (error !is PolarDeviceDisconnected) showError("Gyro stream failed", error) }
                .collect { polarGyroData ->
                    logGyroData(polarGyroData)
                    val sampleRate = if (polarGyroData.samples.size > 1) StreamUtils.calculateSampleRate(polarGyroData.samples[0].timeStamp, polarGyroData.samples[1].timeStamp) else 0.0
                    _uiGyroStreamDataState.update { GyroSampleDataUiState(deviceId = deviceId, sampleRate, polarGyroData) }
                }
        }
    }

    private fun startMagnetometerStream() {
        streamJobs[PolarBleApi.PolarDeviceDataType.MAGNETOMETER]?.cancel()
        streamJobs[PolarBleApi.PolarDeviceDataType.MAGNETOMETER] = viewModelScope.launch(Dispatchers.IO) {
            val settings = try { getStreamSettingsToStartStream(PolarBleApi.PolarDeviceDataType.MAGNETOMETER) } catch (e: Exception) { showError("Failed to get Mag settings", e); return@launch }
            Log.d(TAG, "Start Magnetometer stream with settings: $settings")
            polarDeviceStreamingRepository.getDeviceName(deviceId)?.let { collector.startMagnetometerLog(it) } ?: showError("Failed start Mag stream. Device name is not known")
            updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.MAGNETOMETER, StreamingFeatureState.STATES.RECORDING, settings)
            polarDeviceStreamingRepository.startMagnetometerStream(deviceId, PolarSensorSetting(settings))
                .catch { error -> if (error !is PolarDeviceDisconnected) showError("MAG stream failed", error) }
                .collect { polarMagData ->
                    logMagnetometerData(polarMagData)
                    val sampleRate = if (polarMagData.samples.size > 1) StreamUtils.calculateSampleRate(polarMagData.samples[0].timeStamp, polarMagData.samples[1].timeStamp) else 0.0
                    _uiMagnStreamDataState.update { MagnSampleDataUiState(deviceId = deviceId, calculatedFrequency = sampleRate, sampleData = polarMagData) }
                }
        }
    }

    private fun startPpiStream() {
        streamJobs[PolarBleApi.PolarDeviceDataType.PPI]?.cancel()
        streamJobs[PolarBleApi.PolarDeviceDataType.PPI] = viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Start PPI stream")
            polarDeviceStreamingRepository.getDeviceName(deviceId)?.let { collector.startPpiLog(it) } ?: showError("Failed start PPI stream. Device name is not known")
            updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.PPI, StreamingFeatureState.STATES.RECORDING)
            polarDeviceStreamingRepository.startPpiStream(deviceId)
                .catch { error -> if (error !is PolarDeviceDisconnected) showError("PPI stream failed", error) }
                .collect { ppiSampleData ->
                    logPpiData(ppiSampleData)
                    _uiPpiStreamDataState.update { it.copy(deviceId = deviceId, sampleData = ppiSampleData.samples[0]) }
                }
        }
    }

    private fun startPpgStream() {
        streamJobs[PolarBleApi.PolarDeviceDataType.PPG]?.cancel()
        streamJobs[PolarBleApi.PolarDeviceDataType.PPG] = viewModelScope.launch(Dispatchers.IO) {
            val settings = try { getStreamSettingsToStartStream(PolarBleApi.PolarDeviceDataType.PPG) } catch (e: Exception) { showError("Failed to get PPG settings", e); return@launch }
            Log.d(TAG, "Start PPG stream with settings: $settings")
            polarDeviceStreamingRepository.getDeviceName(deviceId)?.let { collector.startPpgLog(it) } ?: showError("Failed start PPG stream. Device name is not known")
            updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.PPG, StreamingFeatureState.STATES.RECORDING, settings)
            polarDeviceStreamingRepository.startPpgStream(deviceId, PolarSensorSetting(settings))
                .catch { error -> if (error !is PolarDeviceDisconnected) showError("PPG stream failed", error) }
                .collect { polarPpgData ->
                    logPpgData(polarPpgData)
                    val sampleRate = if (polarPpgData.samples.size > 1) StreamUtils.calculateSampleRate(polarPpgData.samples[0].timeStamp, polarPpgData.samples[1].timeStamp) else 0.0
                    when (polarPpgData.type) {
                        PolarPpgData.PpgDataType.PPG3_AMBIENT1,
                        PolarPpgData.PpgDataType.FRAME_TYPE_7,
                        PolarPpgData.PpgDataType.FRAME_TYPE_8,
                        PolarPpgData.PpgDataType.FRAME_TYPE_10,
                        PolarPpgData.PpgDataType.FRAME_TYPE_13,
                        PolarPpgData.PpgDataType.FRAME_TYPE_14 ->
                            _uiPpgStreamDataState.update { PpgSampleDataUiState(deviceId = deviceId, calculatedFrequency = sampleRate, sampleData = polarPpgData) }
                        else -> { /* log only */ }
                    }
                }
        }
    }

    private fun startPressureStream() {
        streamJobs[PolarBleApi.PolarDeviceDataType.PRESSURE]?.cancel()
        streamJobs[PolarBleApi.PolarDeviceDataType.PRESSURE] = viewModelScope.launch(Dispatchers.IO) {
            val settings = try { getStreamSettingsToStartStream(PolarBleApi.PolarDeviceDataType.PRESSURE) } catch (e: Exception) { showError("Failed to get Pressure settings", e); return@launch }
            Log.d(TAG, "Start Pressure stream with settings: $settings")
            polarDeviceStreamingRepository.getDeviceName(deviceId)?.let { collector.startPressureLog(it) } ?: showError("Failed start Pressure stream. Device name is not known")
            updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.PRESSURE, StreamingFeatureState.STATES.RECORDING, settings)
            polarDeviceStreamingRepository.startPressureStream(deviceId, PolarSensorSetting(settings))
                .catch { error -> if (error !is PolarDeviceDisconnected) showError("PRESSURE stream failed", error) }
                .collect { polarPressureData ->
                    logPressureData(polarPressureData)
                    val sampleRate = if (polarPressureData.samples.size > 1) StreamUtils.calculateSampleRate(polarPressureData.samples[0].timeStamp, polarPressureData.samples[1].timeStamp) else 0.0
                    _uiPressureStreamDataState.update { PressureSampleDataUiState(deviceId = deviceId, calculatedFrequency = sampleRate, sampleData = polarPressureData) }
                }
        }
    }

    private fun startLocationStream() {
        streamJobs[PolarBleApi.PolarDeviceDataType.LOCATION]?.cancel()
        streamJobs[PolarBleApi.PolarDeviceDataType.LOCATION] = viewModelScope.launch(Dispatchers.IO) {
            val settings = try { getStreamSettingsToStartStream(PolarBleApi.PolarDeviceDataType.LOCATION) } catch (e: Exception) { showError("Failed to get Location settings", e); return@launch }
            Log.d(TAG, "Start Location stream with settings: $settings")
            polarDeviceStreamingRepository.getDeviceName(deviceId)?.let { collector.startLocationLog(it) } ?: showError("Failed start Location stream. Device name is not known")
            updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.LOCATION, StreamingFeatureState.STATES.RECORDING, settings)
            polarDeviceStreamingRepository.startLocationStream(deviceId, PolarSensorSetting(settings))
                .catch { error -> if (error !is PolarDeviceDisconnected) showError("LOCATION stream failed", error) }
                .collect { polarLocationData ->
                    logLocationData(polarLocationData)
                    when (val sample = polarLocationData.samples.firstOrNull()) {
                        is GpsCoordinatesSample -> {
                            val sampleRate = if (polarLocationData.samples.size > 1) StreamUtils.calculateSampleRate((polarLocationData.samples[0] as GpsCoordinatesSample).timeStamp, (polarLocationData.samples[1] as GpsCoordinatesSample).timeStamp) else 0.0
                            _uiLocationStreamDataState.update { LocationSampleDataUiState(deviceId = deviceId, calculatedFrequency = sampleRate, sampleData = polarLocationData) }
                        }
                        else -> { /* satellite/NMEA data logged only */ }
                    }
                }
        }
    }

    private fun startTemperatureStream() {
        streamJobs[PolarBleApi.PolarDeviceDataType.TEMPERATURE]?.cancel()
        streamJobs[PolarBleApi.PolarDeviceDataType.TEMPERATURE] = viewModelScope.launch(Dispatchers.IO) {
            val settings = try { getStreamSettingsToStartStream(PolarBleApi.PolarDeviceDataType.TEMPERATURE) } catch (e: Exception) { showError("Failed to get Temperature settings", e); return@launch }
            Log.d(TAG, "Start Temperature stream with settings: $settings")
            polarDeviceStreamingRepository.getDeviceName(deviceId)?.let { collector.startTemperatureLog(it) } ?: showError("Failed start Temperature stream. Device name is not known")
            updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.TEMPERATURE, StreamingFeatureState.STATES.RECORDING, settings)
            polarDeviceStreamingRepository.startTemperatureStreaming(deviceId, PolarSensorSetting(settings))
                .catch { error -> if (error !is PolarDeviceDisconnected) showError("TEMPERATURE stream failed", error) }
                .collect { polarTemperatureData ->
                    logTemperatureData(polarTemperatureData)
                    val sampleRate = if (polarTemperatureData.samples.size > 1) StreamUtils.calculateSampleRate(polarTemperatureData.samples[0].timeStamp, polarTemperatureData.samples[1].timeStamp) else 0.0
                    _uiTemperatureStreamDataState.update { TemperatureSampleDataUiState(deviceId = deviceId, calculatedFrequency = sampleRate, sampleData = polarTemperatureData) }
                }
        }
    }

    private fun startSkinTemperatureStream() {
        streamJobs[PolarBleApi.PolarDeviceDataType.SKIN_TEMPERATURE]?.cancel()
        streamJobs[PolarBleApi.PolarDeviceDataType.SKIN_TEMPERATURE] = viewModelScope.launch(Dispatchers.IO) {
            val settings = try { getStreamSettingsToStartStream(PolarBleApi.PolarDeviceDataType.SKIN_TEMPERATURE) } catch (e: Exception) { showError("Failed to get SkinTemp settings", e); return@launch }
            Log.d(TAG, "Start Skin Temperature stream with settings: $settings")
            polarDeviceStreamingRepository.getDeviceName(deviceId)?.let { collector.startSkinTemperatureLog(it) } ?: showError("Failed start Skin Temperature stream. Device name is not known")
            updateStreamingRecordingState(deviceId, PolarBleApi.PolarDeviceDataType.SKIN_TEMPERATURE, StreamingFeatureState.STATES.RECORDING, settings)
            polarDeviceStreamingRepository.startSkinTemperatureStreaming(deviceId, PolarSensorSetting(settings))
                .catch { error -> if (error !is PolarDeviceDisconnected) showError("SKIN TEMPERATURE stream failed", error) }
                .collect { polarTemperatureData ->
                    logSkinTemperatureData(polarTemperatureData)
                    val sampleRate = if (polarTemperatureData.samples.size > 1) StreamUtils.calculateSampleRate(polarTemperatureData.samples[0].timeStamp, polarTemperatureData.samples[1].timeStamp) else 0.0
                    _uiSkinTemperatureStreamDataState.update { SkinTemperatureSampleDataUiState(deviceId = deviceId, calculatedFrequency = sampleRate, sampleData = polarTemperatureData) }
                }
        }
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
            collector.logPpgData(polarPpgData)
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
        if (recordingTimerJob?.isActive == true) return
        recordingTimerJob = viewModelScope.launch(Dispatchers.IO) {
            var time = 0L
            while (true) {
                val hours = (time / 3600).toInt()
                val minutes = (time / 60 % 60).toInt()
                val seconds = (time % 60).toInt()
                updateOnlineRecordingUiState(deviceId = "", timer = String.format("%02d:%02d:%02d", hours, minutes, seconds))
                delay(1_000L)
                time++
            }
        }
    }

    private fun stopRecTimer() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
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
        for (job in streamJobs.values) {
            job?.cancel()
        }
        streamJobs.clear()
        stopRecTimer()
    }

    private fun finalizeCollector() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileUris = collector.finalizeAllStreams()
                if (fileUris.isNotEmpty()) {
                    _shareFiles.update { fileUris }
                }
            } catch (e: Exception) {
                showError("Failed to finalize stream files", e)
            }
        }
    }
}
