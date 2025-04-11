package com.polar.polarsensordatacollector.ui.offlinerectrigger

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.repository.ResultOfRequest
import com.polar.polarsensordatacollector.ui.landing.AvailableOfflineRecordingsState
import com.polar.polarsensordatacollector.ui.utils.MessageUiState
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarOfflineRecordingTrigger
import com.polar.sdk.api.model.PolarOfflineRecordingTriggerMode
import com.polar.sdk.api.model.PolarSensorSetting
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

sealed class OfflineRecSettingsTriggerUiState {
    object ReadyToSetUpTriggers : OfflineRecSettingsTriggerUiState()
    object SettingUpTriggers : OfflineRecSettingsTriggerUiState()
}

internal data class OfflineRecTriggerSettingsUiState(
    val feature: PolarBleApi.PolarDeviceDataType,
    val settings: OfflineRecTriggerSettings
)

internal data class OfflineRecTriggerSettings(
    val currentlyAvailable: PolarSensorSetting?,
    val selectedSettings: Map<PolarSensorSetting.SettingType, Int>?
)

@HiltViewModel
class OfflineTriggerSettingsViewModel @Inject constructor(
    private val polarDeviceStreamingRepository: PolarDeviceRepository,
    state: SavedStateHandle
) : ViewModel() {
    companion object {
        private const val TAG = "OfflineTriggerSettingsViewModel"
    }

    private val deviceId = state.get<String>(OFFLINE_REC_TRIG_KEY_DEVICE_ID) ?: throw Exception("Offline recording viewModel must know the deviceId")

    private var selectedSettingsCache: EnumMap<PolarBleApi.PolarDeviceDataType, OfflineRecTriggerSettings?> =
        EnumMap(PolarBleApi.PolarDeviceDataType.values().associateWith { null })

    private val _uiOfflineRecTriggerSetup = MutableStateFlow<OfflineRecSettingsTriggerUiState>(OfflineRecSettingsTriggerUiState.ReadyToSetUpTriggers)
    internal val uiOfflineRecTriggerSetup: StateFlow<OfflineRecSettingsTriggerUiState> = _uiOfflineRecTriggerSetup.asStateFlow()

    private val _uiShowError: MutableStateFlow<MessageUiState> = MutableStateFlow(MessageUiState("", ""))
    internal val uiShowError: StateFlow<MessageUiState> = _uiShowError.asStateFlow()

    private val _uiShowInfo: MutableStateFlow<MessageUiState> = MutableStateFlow(MessageUiState("", ""))
    internal val uiShowInfo: StateFlow<MessageUiState> = _uiShowInfo.asStateFlow()

    private val _uiOfflineRecTriggerSettingsState: MutableStateFlow<OfflineRecTriggerSettingsUiState?> = MutableStateFlow(null)
    internal val uiOfflineRecTriggerSettingsState: StateFlow<OfflineRecTriggerSettingsUiState?> = _uiOfflineRecTriggerSettingsState.asStateFlow()

    private val _uiAvailableOfflineRecTypesState = MutableStateFlow(AvailableOfflineRecordingsState())
    internal val uiAvailableOfflineRecTypesState: StateFlow<AvailableOfflineRecordingsState> = _uiAvailableOfflineRecTypesState.asStateFlow()

    init {
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
            MessageUiState(header = header, description = description)
        }
    }

    private suspend fun getSelectedSettings(feature: PolarBleApi.PolarDeviceDataType): Map<PolarSensorSetting.SettingType, Int> {
        return if (feature == PolarBleApi.PolarDeviceDataType.PPI || feature == PolarBleApi.PolarDeviceDataType.HR) {
            Single.just(emptyMap<PolarSensorSetting.SettingType, Int>()).blockingGet()
        } else {
            selectedSettingsCache[feature]?.selectedSettings?.let {
                Single.just(it).blockingGet()
            } ?: run {
                polarDeviceStreamingRepository.getOfflineRecSettings(deviceId, feature)
                    .map { sensorSetting: PolarSensorSetting ->
                        val selectedSettings = maxSettingsFromStreamSettings(sensorSetting)
                        updateSelectedStreamSettings(feature, selectedSettings)
                        selectedSettings
                    }.blockingGet()
            }
        }
    }

    // TODO, move to utils
    private fun maxSettingsFromStreamSettings(sensorSetting: PolarSensorSetting): Map<PolarSensorSetting.SettingType, Int> {
        val settings: MutableMap<PolarSensorSetting.SettingType, Int> = mutableMapOf()
        for ((key, value) in sensorSetting.settings) {
            settings[key] = Collections.max(value)
        }
        return settings
    }

    fun setOfflineRecordingTrigger(triggerMethod: PolarOfflineRecordingTriggerMode, features: List<PolarBleApi.PolarDeviceDataType>) {
        Log.d(TAG, "setOfflineRecordingTrigger()")
        viewModelScope.launch(Dispatchers.IO) {
            _uiOfflineRecTriggerSetup.update {
                OfflineRecSettingsTriggerUiState.SettingUpTriggers
            }

            val triggerFeatures: MutableMap<PolarBleApi.PolarDeviceDataType, PolarSensorSetting> = mutableMapOf()
            for (feature in features) {
                val settings = getSelectedSettings(feature)
                triggerFeatures[feature] = PolarSensorSetting(settings)
            }
            val trigger = PolarOfflineRecordingTrigger(triggerMode = triggerMethod, triggerFeatures = triggerFeatures)
            when (val result = polarDeviceStreamingRepository.setOfflineRecordingTrigger(deviceId, trigger)) {
                is ResultOfRequest.Success -> {
                    showInfo("Successfully set the offline trigger")
                }
                is ResultOfRequest.Failure -> {
                    showError(result.message, result.throwable)
                }
            }
            _uiOfflineRecTriggerSetup.update {
                OfflineRecSettingsTriggerUiState.ReadyToSetUpTriggers
            }
        }
    }

    fun updateSelectedStreamSettings(feature: PolarBleApi.PolarDeviceDataType, settings: Map<PolarSensorSetting.SettingType, Int>) {
        val newSettings = OfflineRecTriggerSettings(
            currentlyAvailable = selectedSettingsCache[feature]?.currentlyAvailable,
            selectedSettings = settings
        )
        selectedSettingsCache[feature] = newSettings
    }

    fun requestStreamSettings(feature: PolarBleApi.PolarDeviceDataType) {
        viewModelScope.launch(Dispatchers.IO) {
            polarDeviceStreamingRepository.getOfflineRecSettings(deviceId, feature)
                .subscribe(
                    { sensorSettings: PolarSensorSetting ->
                        Log.d(TAG, "Sensor settings fetch completed")
                        val newSettings = OfflineRecTriggerSettings(
                            currentlyAvailable = sensorSettings,
                            selectedSettings = selectedSettingsCache[feature]?.selectedSettings
                        )
                        selectedSettingsCache[feature] = newSettings

                        _uiOfflineRecTriggerSettingsState.update {
                            OfflineRecTriggerSettingsUiState(
                                feature = feature,
                                settings = OfflineRecTriggerSettings(
                                    currentlyAvailable = sensorSettings,
                                    selectedSettings = selectedSettingsCache[feature]?.selectedSettings
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

    private fun updateOfflineRecordingsAvailableUiState(deviceId: String, featuresAvailable: EnumMap<PolarBleApi.PolarDeviceDataType, Boolean>) {
        _uiAvailableOfflineRecTypesState.update {
            it.copy(deviceId = deviceId, offlineRecordingsAvailableOfflineRecordingsState = featuresAvailable)
        }
    }
}