package com.polar.polarsensordatacollector.ui.offlinerectrigger

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.repository.ResultOfRequest
import com.polar.polarsensordatacollector.ui.landing.AvailableOfflineRecordingsState
import com.polar.polarsensordatacollector.ui.utils.MessageUiState
import com.polar.polarsensordatacollector.R
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarOfflineRecordingTrigger
import com.polar.sdk.api.model.PolarOfflineRecordingTriggerMode
import com.polar.sdk.api.model.PolarSensorSetting
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    state: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {
    companion object {
        private const val TAG = "OfflineTriggerSettingsViewModel"
    }

    private val identifier = state.get<String>(OFFLINE_REC_TRIG_KEY_DEVICE_ID) ?: throw Exception("Offline recording viewModel must know the identifier")

    private var selectedSettingsCache: EnumMap<PolarBleApi.PolarDeviceDataType, OfflineRecTriggerSettings?> =
        EnumMap(PolarBleApi.PolarDeviceDataType.values().associateWith { null })

    private val _uiOfflineRecTriggerSetup = MutableStateFlow<OfflineRecSettingsTriggerUiState>(OfflineRecSettingsTriggerUiState.ReadyToSetUpTriggers)
    internal val uiOfflineRecTriggerSetup: StateFlow<OfflineRecSettingsTriggerUiState> = _uiOfflineRecTriggerSetup.asStateFlow()

    private val _uiShowError = MutableSharedFlow<MessageUiState>(extraBufferCapacity = 1)
    internal val uiShowError: SharedFlow<MessageUiState> = _uiShowError.asSharedFlow()

    private val _uiShowInfo = MutableSharedFlow<MessageUiState>(extraBufferCapacity = 1)
    internal val uiShowInfo: SharedFlow<MessageUiState> = _uiShowInfo.asSharedFlow()

    private val _uiOfflineRecTriggerSettingsState: MutableStateFlow<OfflineRecTriggerSettingsUiState?> = MutableStateFlow(null)
    internal val uiOfflineRecTriggerSettingsState: StateFlow<OfflineRecTriggerSettingsUiState?> = _uiOfflineRecTriggerSettingsState.asStateFlow()

    private val _uiAvailableOfflineRecTypesState = MutableStateFlow(AvailableOfflineRecordingsState())
    internal val uiAvailableOfflineRecTypesState: StateFlow<AvailableOfflineRecordingsState> = _uiAvailableOfflineRecTypesState.asStateFlow()

    init {
        viewModelScope.launch {
            polarDeviceStreamingRepository.availableFeatures
                .collect { deviceStreamsAvailable ->
                    updateOfflineRecordingsAvailableUiState(deviceStreamsAvailable.identifier, featuresAvailable = deviceStreamsAvailable.availableOfflineFeatures)
                }
        }
    }

    private fun showError(errorDescription: String, errorThrowable: Throwable? = null) {
        Log.e(TAG, "Show error: $errorDescription. Error reason $errorThrowable")
        _uiShowError.tryEmit(MessageUiState(header = errorDescription, description = errorThrowable?.message))
    }

    private fun showInfo(header: String, description: String = "", timeout: Long? = null) {
        _uiShowInfo.tryEmit(MessageUiState(header = header, description = description, timeout = timeout))
    }

    private suspend fun getSelectedSettings(feature: PolarBleApi.PolarDeviceDataType): Map<PolarSensorSetting.SettingType, Int> {
        return if (feature == PolarBleApi.PolarDeviceDataType.PPI || feature == PolarBleApi.PolarDeviceDataType.HR) {
            emptyMap()
        } else {
            selectedSettingsCache[feature]?.selectedSettings
                ?: run {
                    val sensorSetting = polarDeviceStreamingRepository.getOfflineRecSettings(identifier, feature)
                    val selectedSettings = maxSettingsFromStreamSettings(sensorSetting)
                    updateSelectedStreamSettings(feature, selectedSettings)
                    selectedSettings
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
            when (val result = polarDeviceStreamingRepository.setOfflineRecordingTrigger(identifier, trigger)) {
                is ResultOfRequest.Success -> {
                    showInfo(context.getString(R.string.offline_trigger_set_successfully))
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

    fun clearOfflineRecTriggerSettingsRequest() {
        _uiOfflineRecTriggerSettingsState.value = null
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
            try {
                val sensorSettings = polarDeviceStreamingRepository.getOfflineRecSettings(identifier, feature)
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
            } catch (e: Exception) {
                showError("Settings fetch error for feature $feature. REASON: $e")
            }
        }
    }

    private fun updateOfflineRecordingsAvailableUiState(identifier: String, featuresAvailable: EnumMap<PolarBleApi.PolarDeviceDataType, Boolean>) {
        _uiAvailableOfflineRecTypesState.update {
            it.copy(identifier = identifier, offlineRecordingsAvailableOfflineRecordingsState = featuresAvailable)
        }
    }
}