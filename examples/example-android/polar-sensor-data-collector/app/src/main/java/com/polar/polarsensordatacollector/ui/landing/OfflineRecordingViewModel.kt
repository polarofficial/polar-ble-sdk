package com.polar.polarsensordatacollector.ui.landing

import android.util.Log
import android.util.Pair
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.repository.ResultOfRequest
import com.polar.polarsensordatacollector.ui.utils.MessageUiState
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarSensorSetting
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

sealed class OfflineRecordingUiState {
    class Enabled(val recordingFeatures: List<PolarBleApi.PolarDeviceDataType>) : OfflineRecordingUiState()
    object FetchingStatus : OfflineRecordingUiState()
}

data class OfflineAvailableStreamSettingsUiState(
    val feature: PolarBleApi.PolarDeviceDataType,
    val settings: OfflineStreamSettings
)

data class OfflineStreamSettings(
    val currentlyAvailable: PolarSensorSetting?,
    val allPossibleSettings: PolarSensorSetting?,
    val selectedSettings: Map<PolarSensorSetting.SettingType, Int>?
)

internal data class AvailableOfflineRecordingsState(
    val deviceId: String = "",
    val offlineRecordingsAvailableOfflineRecordingsState: EnumMap<PolarBleApi.PolarDeviceDataType, Boolean> = EnumMap(PolarBleApi.PolarDeviceDataType.values().associateWith { false }),
)

@HiltViewModel
class OfflineRecordingViewModel @Inject constructor(
    private val polarDeviceStreamingRepository: PolarDeviceRepository,
    private val state: SavedStateHandle
) : ViewModel() {
    companion object {
        private const val TAG = "OfflineRecordingViewModel"
    }

    private val deviceId = state.get<String>(ONLINE_OFFLINE_KEY_DEVICE_ID) ?: throw Exception("Offline recording viewModel must know the deviceId")

    private var settingsCache: EnumMap<PolarBleApi.PolarDeviceDataType, OfflineStreamSettings> =
        EnumMap(PolarBleApi.PolarDeviceDataType.values().associateWith { OfflineStreamSettings(null, null, null) })

    private val _uiOfflineRecordingState = MutableStateFlow<OfflineRecordingUiState>(OfflineRecordingUiState.FetchingStatus)
    val uiOfflineRecordingState: StateFlow<OfflineRecordingUiState> = _uiOfflineRecordingState.asStateFlow()

    private val _uiShowError: MutableStateFlow<MessageUiState> = MutableStateFlow(MessageUiState(""))
    val uiShowError: StateFlow<MessageUiState> = _uiShowError.asStateFlow()

    private val _uiShowInfo: MutableStateFlow<MessageUiState> = MutableStateFlow(MessageUiState("", ""))
    val uiShowInfo: StateFlow<MessageUiState> = _uiShowInfo.asStateFlow()

    private val _uiOfflineRequestedSettingsState: MutableStateFlow<OfflineAvailableStreamSettingsUiState?> = MutableStateFlow(null)
    val uiOfflineRequestedSettingsState: StateFlow<OfflineAvailableStreamSettingsUiState?> = _uiOfflineRequestedSettingsState.asStateFlow()

    private val _uiAvailableOfflineRecTypesState = MutableStateFlow(AvailableOfflineRecordingsState())
    internal val uiAvailableOfflineRecTypesState: StateFlow<AvailableOfflineRecordingsState> = _uiAvailableOfflineRecTypesState.asStateFlow()

    init {
        getOfflineRecordingStatus()

        viewModelScope.launch {
            polarDeviceStreamingRepository.availableFeatures
                .collect { deviceStreamsAvailable ->
                    updateOfflineRecordingsAvailableUiState(deviceStreamsAvailable.deviceId, featuresAvailable = deviceStreamsAvailable.availableOfflineFeatures)
                }
        }
    }

    private fun showError(errorDescription: String, errorThrowable: Throwable? = null) {
        Log.e(TAG, "Show error: $errorDescription. Error reason $errorThrowable")
        _uiShowError.update {
            MessageUiState(header = errorDescription, description = errorThrowable?.message)
        }
    }

    private fun showInfo(header: String, description: String = "") {
        _uiShowInfo.update {
            MessageUiState(header, description)
        }
    }


    private fun offlineRecEnabledUpdateUiState(enabledFeature: PolarBleApi.PolarDeviceDataType) {
        _uiOfflineRecordingState.update {
            if (it is OfflineRecordingUiState.Enabled) {
                val existingRecordings = it.recordingFeatures.toMutableList()
                existingRecordings.add(enabledFeature)
                OfflineRecordingUiState.Enabled(recordingFeatures = existingRecordings)
            } else {
                it
            }
        }
    }

    private fun offlineRecDisabledUpdateUiState(disabledFeature: PolarBleApi.PolarDeviceDataType) {
        _uiOfflineRecordingState.update {
            if (it is OfflineRecordingUiState.Enabled) {
                val existingRecordings = it.recordingFeatures.toMutableList()
                existingRecordings.remove(disabledFeature)
                OfflineRecordingUiState.Enabled(recordingFeatures = existingRecordings)
            } else {
                it
            }
        }
    }

    public override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel onCleared()")
        for (disposable in disposables.values) {
            disposable?.dispose()
        }
        disposables.clear()
    }

    fun requestOfflineRecSettings(deviceId: String, feature: PolarBleApi.PolarDeviceDataType) {
        viewModelScope.launch(Dispatchers.IO) {
            val availableSettings = polarDeviceStreamingRepository.getOfflineRecSettings(deviceId, feature)
            val allSettings = polarDeviceStreamingRepository.getFullOfflineRecSettings(deviceId, feature)
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

                        val newSettings = OfflineStreamSettings(
                            currentlyAvailable = sensorSettings.first,
                            allPossibleSettings = sensorSettings.second,
                            selectedSettings = settingsCache[feature]?.selectedSettings
                        )
                        settingsCache[feature] = newSettings

                        _uiOfflineRequestedSettingsState.update {
                            OfflineAvailableStreamSettingsUiState(
                                feature = feature,
                                settings = OfflineStreamSettings(
                                    currentlyAvailable = sensorSettings.first,
                                    allPossibleSettings = sensorSettings.second,
                                    selectedSettings = settingsCache[feature]?.selectedSettings
                                )
                            )
                        }
                    },
                    { error: Throwable ->
                        showError("Settings fetch error for feature $feature. REASON: $error")
                    }
                )
        }
    }

    private fun getOfflineRecSettingsToStartRec(deviceId: String, feature: PolarBleApi.PolarDeviceDataType): Single<PolarSensorSetting> {
        return settingsCache[feature]?.selectedSettings?.let {
            Single.just(PolarSensorSetting(it))
        } ?: run {
            polarDeviceStreamingRepository.getOfflineRecSettings(deviceId, feature)
                .map { sensorSetting: PolarSensorSetting ->
                    val selectedSettings = maxSettingsFromStreamSettings(sensorSetting)
                    updateSelectedStreamSettings(feature, selectedSettings)
                    PolarSensorSetting(selectedSettings)
                }
        }
    }

    fun updateSelectedStreamSettings(feature: PolarBleApi.PolarDeviceDataType, settings: Map<PolarSensorSetting.SettingType, Int>) {
        val newSettings = OfflineStreamSettings(
            currentlyAvailable = settingsCache[feature]?.currentlyAvailable,
            allPossibleSettings = settingsCache[feature]?.allPossibleSettings,
            selectedSettings = settings
        )
        settingsCache[feature] = newSettings
    }

    private val disposables: MutableMap<PolarBleApi.PolarDeviceDataType, Disposable?> =
        EnumMap(PolarBleApi.PolarDeviceDataType::class.java)

    // TODO, move to utils
    private fun maxSettingsFromStreamSettings(sensorSetting: PolarSensorSetting): Map<PolarSensorSetting.SettingType, Int> {
        val settings: MutableMap<PolarSensorSetting.SettingType, Int> = mutableMapOf()
        for ((key, value) in sensorSetting.settings) {
            settings[key] = Collections.max(value)
        }
        return settings
    }

    fun startOfflineRecording(features: List<PolarBleApi.PolarDeviceDataType>) {
        viewModelScope.launch(Dispatchers.IO) {
            for (feature in features) {
                offlineRecEnabledUpdateUiState(enabledFeature = feature)
                val settings = if (feature == PolarBleApi.PolarDeviceDataType.PPI || feature == PolarBleApi.PolarDeviceDataType.HR) {
                    null
                } else {
                    try {
                        getOfflineRecSettingsToStartRec(deviceId, feature)
                            .blockingGet()
                    } catch (settingsException: Exception) {
                        showError("Couldn't get settings for $feature", settingsException)
                        return@launch
                    }
                }
                when (val result = polarDeviceStreamingRepository.startOfflineRecording(deviceId, feature, settings)) {
                    is ResultOfRequest.Success -> {}
                    is ResultOfRequest.Failure -> {
                        when (val result = polarDeviceStreamingRepository.stopOfflineRecording(deviceId, feature)) {
                            is ResultOfRequest.Success -> offlineRecDisabledUpdateUiState(disabledFeature = feature)
                            is ResultOfRequest.Failure -> showError(result.message, result.throwable)
                        }
                        showError(result.message, result.throwable)
                    }
                }
            }
        }
    }

    fun stopOfflineRecording(features: List<PolarBleApi.PolarDeviceDataType>) {
        viewModelScope.launch(Dispatchers.IO) {
            for (feature in features) {
                when (val result = polarDeviceStreamingRepository.stopOfflineRecording(deviceId, feature)) {
                    is ResultOfRequest.Success -> offlineRecDisabledUpdateUiState(disabledFeature = feature)
                    is ResultOfRequest.Failure -> showError(result.message, result.throwable)
                }
            }
        }
    }

    private fun getOfflineRecordingStatus() {
        Log.d(TAG, "getOfflineRecordingStatus()")
        viewModelScope.launch(Dispatchers.IO) {
            _uiOfflineRecordingState.update {
                OfflineRecordingUiState.FetchingStatus
            }
            when (val result = polarDeviceStreamingRepository.requestOfflineRecordingStatus(deviceId)) {
                is ResultOfRequest.Success -> {
                    _uiOfflineRecordingState.update {
                        OfflineRecordingUiState.Enabled(recordingFeatures = result.value ?: emptyList())
                    }
                }
                is ResultOfRequest.Failure -> {
                    showError(result.message, result.throwable)
                }
            }
        }
    }

    private fun updateOfflineRecordingsAvailableUiState(deviceId: String, featuresAvailable: EnumMap<PolarBleApi.PolarDeviceDataType, Boolean>) {
        _uiAvailableOfflineRecTypesState.update {
            it.copy(deviceId = deviceId, offlineRecordingsAvailableOfflineRecordingsState = featuresAvailable)
        }
    }
}


