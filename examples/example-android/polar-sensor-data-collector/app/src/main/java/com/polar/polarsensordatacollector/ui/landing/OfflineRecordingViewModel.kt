package com.polar.polarsensordatacollector.ui.landing

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.repository.ResultOfRequest
import com.polar.polarsensordatacollector.ui.utils.DerivedDialogResult
import com.polar.polarsensordatacollector.ui.utils.MessageUiState
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.model.PolarDerivedMeasurementMethod
import com.polar.sdk.api.model.PolarDerivedMeasurementSettings
import com.polar.sdk.api.model.PolarDerivedMeasurementSettingsGroup
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
    val selectedSettings: Map<PolarSensorSetting.SettingType, Int>?,
    val derivedSettingsGroup: PolarDerivedMeasurementSettingsGroup? = null,
    val selectedDerivedSettings: PolarDerivedMeasurementSettings? = null
)

internal data class AvailableOfflineRecordingsState(
    val identifier: String = "",
    val offlineRecordingsAvailableOfflineRecordingsState: EnumMap<PolarBleApi.PolarDeviceDataType, Boolean> = EnumMap(PolarBleApi.PolarDeviceDataType.values().associateWith { false }),
)

sealed class DerivedRecordingUiState {
    object NotRecording : DerivedRecordingUiState()
    object Recording : DerivedRecordingUiState()
}

data class DerivedSettingsUiState(
    val settingsGroup: PolarDerivedMeasurementSettingsGroup,
    val selectedMethods: Set<PolarDerivedMeasurementMethod>,
    val selectedSourceRate: Int,
    val selectedTimeWindowMs: Int
)

@HiltViewModel
class OfflineRecordingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val polarDeviceStreamingRepository: PolarDeviceRepository,
    private val state: SavedStateHandle
) : ViewModel() {
    companion object {
        private const val TAG = "OfflineRecordingViewModel"
    }

    private val identifier = state.get<String>(ONLINE_OFFLINE_KEY_DEVICE_ID) ?: throw Exception("Offline recording viewModel must know the identifier")

    private var settingsCache: EnumMap<PolarBleApi.PolarDeviceDataType, OfflineStreamSettings> =
        EnumMap(PolarBleApi.PolarDeviceDataType.values().associateWith { OfflineStreamSettings(null, null, null) })

    private var derivedSettingsGroupCache: PolarDerivedMeasurementSettingsGroup? = null
    private var derivedSelectedMethods: Set<PolarDerivedMeasurementMethod> = setOf(PolarDerivedMeasurementMethod.DOWNSAMPLE)
    private var derivedSelectedSourceRate: Int? = null
    private var derivedSelectedTimeWindowMs: Int? = null

    private val _uiOfflineRecordingState = MutableStateFlow<OfflineRecordingUiState>(OfflineRecordingUiState.FetchingStatus)
    val uiOfflineRecordingState: StateFlow<OfflineRecordingUiState> = _uiOfflineRecordingState.asStateFlow()

    private val _uiShowError = MutableSharedFlow<MessageUiState>(extraBufferCapacity = 1)
    val uiShowError: SharedFlow<MessageUiState> = _uiShowError.asSharedFlow()

    private val _uiShowInfo = MutableSharedFlow<MessageUiState>(extraBufferCapacity = 1)
    val uiShowInfo: SharedFlow<MessageUiState> = _uiShowInfo.asSharedFlow()

    private val _uiOfflineRequestedSettingsState: MutableStateFlow<OfflineAvailableStreamSettingsUiState?> = MutableStateFlow(null)
    val uiOfflineRequestedSettingsState: StateFlow<OfflineAvailableStreamSettingsUiState?> = _uiOfflineRequestedSettingsState.asStateFlow()

    private val _uiAvailableOfflineRecTypesState = MutableStateFlow(AvailableOfflineRecordingsState())
    internal val uiAvailableOfflineRecTypesState: StateFlow<AvailableOfflineRecordingsState> = _uiAvailableOfflineRecTypesState.asStateFlow()

    private val _uiDerivedRecordingState = MutableStateFlow<DerivedRecordingUiState>(DerivedRecordingUiState.NotRecording)
    val uiDerivedRecordingState: StateFlow<DerivedRecordingUiState> = _uiDerivedRecordingState.asStateFlow()

    private val _uiDerivedSettingsState: MutableStateFlow<DerivedSettingsUiState?> = MutableStateFlow(null)
    val uiDerivedSettingsState: StateFlow<DerivedSettingsUiState?> = _uiDerivedSettingsState.asStateFlow()

    init {
        getOfflineRecordingStatus()

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
        _uiShowInfo.tryEmit(MessageUiState(header, description, timeout))
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
    }

    fun requestOfflineRecSettings(identifier: String, feature: PolarBleApi.PolarDeviceDataType) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val available = polarDeviceStreamingRepository.getOfflineRecSettings(identifier, feature)
                val all = try {
                    polarDeviceStreamingRepository.getFullOfflineRecSettings(identifier, feature)
                } catch (e: Exception) {
                    PolarSensorSetting(emptyMap())
                }

                if (available.settings.isEmpty()) {
                    showError("Settings are not available for feature $feature")
                    return@launch
                }

                Log.d(TAG, "Feature $feature available settings ${available.settings}")
                Log.d(TAG, "Feature $feature all settings ${all.settings}")

                val derivedGroup: PolarDerivedMeasurementSettingsGroup? = try {
                    val idsResult = polarDeviceStreamingRepository.requestDerivedMeasurementGroupIds(identifier, feature)
                    val groupIds = (idsResult as? ResultOfRequest.Success)?.value ?: emptySet()
                    if (groupIds.isEmpty()) {
                        null
                    } else {
                        val allGroups = groupIds.mapNotNull { gid ->
                            val r = polarDeviceStreamingRepository.requestDerivedMeasurementSettingsGroup(identifier, gid)
                            val g = (r as? ResultOfRequest.Success)?.value
                            if (g == null) {
                                Log.w(TAG, "Derived group 0x${gid.toString(16).uppercase()}: failed to fetch settings – ${(r as? ResultOfRequest.Failure)?.message}")
                            }
                            g
                        }
                        val best = allGroups
                            .filter { g -> feature in g.sourceTypes }
                            .maxByOrNull { it.supportedMethods.size }
                            ?: allGroups.maxByOrNull { it.supportedMethods.size }
                        best
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Fetching derived settings failed for $feature: ${e.message}")
                    null
                }

                val newSettings = OfflineStreamSettings(
                    currentlyAvailable = available,
                    allPossibleSettings = all,
                    selectedSettings = settingsCache[feature]?.selectedSettings,
                    derivedSettingsGroup = derivedGroup ?: settingsCache[feature]?.derivedSettingsGroup,
                    selectedDerivedSettings = settingsCache[feature]?.selectedDerivedSettings
                )
                settingsCache[feature] = newSettings

                _uiOfflineRequestedSettingsState.update {
                    OfflineAvailableStreamSettingsUiState(
                        feature = feature,
                        settings = newSettings
                    )
                }
            } catch (e: Exception) {
                showError("Settings fetch error for feature $feature. REASON: $e")
            }
        }
    }

    private suspend fun getOfflineRecSettingsToStartRec(identifier: String, feature: PolarBleApi.PolarDeviceDataType): PolarSensorSetting {
        return settingsCache[feature]?.selectedSettings?.let {
            PolarSensorSetting(it)
        } ?: run {
            val sensorSetting = polarDeviceStreamingRepository.getOfflineRecSettings(identifier, feature)
            val selectedSettings = maxSettingsFromStreamSettings(sensorSetting)
            updateSelectedStreamSettings(feature, selectedSettings)
            PolarSensorSetting(selectedSettings)
        }
    }

    fun updateSelectedStreamSettings(
        feature: PolarBleApi.PolarDeviceDataType,
        settings: Map<PolarSensorSetting.SettingType, Int>,
        derivedResult: DerivedDialogResult? = null,
        hadDerivedSection: Boolean = false
    ) {
        val existing = settingsCache[feature] ?: OfflineStreamSettings(null, null, null)
        val derivedSettings: PolarDerivedMeasurementSettings? = derivedResult?.let { dr ->
            val group = existing.derivedSettingsGroup ?: return@let null
            val sourceType = group.sourceTypes.firstOrNull { it == feature }
                ?: group.sourceTypes.firstOrNull()
                ?: return@let null

            val sourceRate = dr.selectedSourceRate.takeIf { it in group.sourceSampleRates }
                ?: group.sourceSampleRates.maxOrNull() ?: 50
            val timeWindowMs = dr.selectedTimeWindowMs.takeIf { it in group.timeWindowOptions }
                ?: group.timeWindowOptions.minOrNull() ?: 1000


            PolarDerivedMeasurementSettings(
                groupId = group.groupId,
                sourceMeasurementType = sourceType,
                sourceSampleRate = sourceRate,
                timeWindowMs = timeWindowMs,
                selectedMethods = dr.selectedMethods
            )
        } ?: if (hadDerivedSection) {
            null
        } else {
            existing.selectedDerivedSettings
        }

        settingsCache[feature] = existing.copy(
            selectedSettings = settings,
            selectedDerivedSettings = derivedSettings
        )
    }

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

                val derivedSettings = settingsCache[feature]?.selectedDerivedSettings

                if (feature == PolarBleApi.PolarDeviceDataType.ACC && derivedSettings != null) {
                    polarDeviceStreamingRepository.stopDerivedOfflineRecording(identifier)
                    when (val dr = polarDeviceStreamingRepository.startDerivedOfflineRecording(identifier, derivedSettings)) {
                        is ResultOfRequest.Success -> Unit
                        is ResultOfRequest.Failure -> {
                            offlineRecDisabledUpdateUiState(disabledFeature = feature)
                            showError("ACC computed data recording failed", dr.throwable)
                        }
                    }
                    continue
                }

                val settings = if (feature == PolarBleApi.PolarDeviceDataType.PPI || feature == PolarBleApi.PolarDeviceDataType.HR) {
                    null
                } else {
                    try {
                        getOfflineRecSettingsToStartRec(identifier, feature)
                    } catch (settingsException: Exception) {
                        showError("Couldn't get settings for $feature", settingsException)
                        return@launch
                    }
                }
                when (val result = polarDeviceStreamingRepository.startOfflineRecording(identifier, feature, settings)) {
                    is ResultOfRequest.Success -> { /* nothing extra for non-derived */ }
                    is ResultOfRequest.Failure -> {
                        when (val stop = polarDeviceStreamingRepository.stopOfflineRecording(identifier, feature)) {
                            is ResultOfRequest.Success -> offlineRecDisabledUpdateUiState(disabledFeature = feature)
                            is ResultOfRequest.Failure -> showError(stop.message, stop.throwable)
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
                polarDeviceStreamingRepository.stopDerivedOfflineRecording(identifier)

                val isDerivedOnly = feature == PolarBleApi.PolarDeviceDataType.ACC &&
                        settingsCache[feature]?.selectedDerivedSettings != null
                if (isDerivedOnly) {
                    offlineRecDisabledUpdateUiState(disabledFeature = feature)
                    continue
                }

                when (val result = polarDeviceStreamingRepository.stopOfflineRecording(identifier, feature)) {
                    is ResultOfRequest.Success -> {
                        offlineRecDisabledUpdateUiState(disabledFeature = feature)
                    }
                    is ResultOfRequest.Failure -> {
                        offlineRecDisabledUpdateUiState(disabledFeature = feature)
                        showError(result.message, result.throwable)
                    }
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
            when (val result = polarDeviceStreamingRepository.requestOfflineRecordingStatus(identifier)) {
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

    private fun updateOfflineRecordingsAvailableUiState(identifier: String, featuresAvailable: EnumMap<PolarBleApi.PolarDeviceDataType, Boolean>) {
        _uiAvailableOfflineRecTypesState.update {
            it.copy(identifier = identifier, offlineRecordingsAvailableOfflineRecordingsState = featuresAvailable)
        }
    }

    fun requestDerivedMeasurementSettings(identifier: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val groupIds = when (val idsResult = polarDeviceStreamingRepository.requestDerivedMeasurementGroupIds(
                identifier,
                PolarBleApi.PolarDeviceDataType.ACC
            )) {
                is ResultOfRequest.Success -> idsResult.value ?: emptySet()
                is ResultOfRequest.Failure -> {
                    Log.e(TAG, "requestDerivedMeasurementSettings: failed to get group IDs – ${idsResult.message}", idsResult.throwable)
                    showError(context.getString(R.string.derived_error_group_ids_failed), idsResult.throwable)
                    return@launch
                }
            }

            if (groupIds.isEmpty()) {
                showError(context.getString(R.string.derived_error_no_groups_for_acc))
                return@launch
            }

            val allGroups = groupIds.mapNotNull { gid ->
                when (val r = polarDeviceStreamingRepository.requestDerivedMeasurementSettingsGroup(identifier, gid)) {
                    is ResultOfRequest.Success -> r.value
                    is ResultOfRequest.Failure -> {
                        Log.w(TAG, "requestDerivedMeasurementSettings: group 0x${gid.toString(16).uppercase()} fetch failed – ${r.message}")
                        null
                    }
                }
            }

            val best = allGroups
                .filter { g -> PolarBleApi.PolarDeviceDataType.ACC in g.sourceTypes }
                .maxByOrNull { it.supportedMethods.size }
                ?: allGroups.maxByOrNull { it.supportedMethods.size }

            if (best == null) {
                showError(context.getString(R.string.derived_error_no_usable_group))
                return@launch
            }


            derivedSettingsGroupCache = best
            val initRate = derivedSelectedSourceRate?.takeIf { it in best.sourceSampleRates }
                ?: best.sourceSampleRates.maxOrNull() ?: 50
            val initWindow = derivedSelectedTimeWindowMs?.takeIf { it in best.timeWindowOptions }
                ?: if (best.timeWindowOptions.contains(1000)) 1000 else best.timeWindowOptions.minOrNull() ?: 1000
            derivedSelectedSourceRate = initRate
            derivedSelectedTimeWindowMs = initWindow
            _uiDerivedSettingsState.update {
                DerivedSettingsUiState(
                    settingsGroup = best,
                    selectedMethods = derivedSelectedMethods,
                    selectedSourceRate = initRate,
                    selectedTimeWindowMs = initWindow
                )
            }
        }
    }

    fun updateSelectedDerivedMethods(methods: Set<PolarDerivedMeasurementMethod>) {
        derivedSelectedMethods = methods.ifEmpty { setOf(PolarDerivedMeasurementMethod.DOWNSAMPLE) }
        Log.d(TAG, "Derived methods updated: $derivedSelectedMethods")
        refreshDerivedSettingsUiState()
    }

    fun updateSelectedDerivedSourceRate(rateHz: Int) {
        val group = derivedSettingsGroupCache ?: return
        derivedSelectedSourceRate = rateHz.takeIf { it in group.sourceSampleRates } ?: derivedSelectedSourceRate
        Log.d(TAG, "Derived source rate updated: $derivedSelectedSourceRate (available=${group.sourceSampleRates})")
        refreshDerivedSettingsUiState()
    }

    fun updateSelectedDerivedTimeWindowMs(windowMs: Int) {
        val group = derivedSettingsGroupCache ?: return
        derivedSelectedTimeWindowMs = windowMs.takeIf { it in group.timeWindowOptions } ?: derivedSelectedTimeWindowMs
        Log.d(TAG, "Derived time window updated: $derivedSelectedTimeWindowMs ms (available=${group.timeWindowOptions})")
        refreshDerivedSettingsUiState()
    }

    private fun refreshDerivedSettingsUiState() {
        val group = derivedSettingsGroupCache ?: return
        val rate = derivedSelectedSourceRate ?: group.sourceSampleRates.minOrNull() ?: return
        val window = derivedSelectedTimeWindowMs ?: group.timeWindowOptions.minOrNull() ?: return
        _uiDerivedSettingsState.update {
            DerivedSettingsUiState(
                settingsGroup = group,
                selectedMethods = derivedSelectedMethods,
                selectedSourceRate = rate,
                selectedTimeWindowMs = window
            )
        }
    }

    fun startDerivedOfflineRecording() {
        viewModelScope.launch(Dispatchers.IO) {
            val group = derivedSettingsGroupCache ?: run {
                showError(context.getString(R.string.derived_error_no_settings))
                return@launch
            }
            val methods = derivedSelectedMethods
            // Use the values selected by the user (or seeded from the group on fetch).
            // Both must be from the group's advertised options per spec.
            // Fall back to MAX rate for better statistical quality.
            val sourceSampleRate = (derivedSelectedSourceRate ?: group.sourceSampleRates.maxOrNull())
                ?.takeIf { it in group.sourceSampleRates }
                ?: run {
                    showError(context.getString(R.string.derived_error_no_valid_source_rate))
                    return@launch
                }
            val timeWindowMs = (derivedSelectedTimeWindowMs ?: group.timeWindowOptions.minOrNull())
                ?.takeIf { it in group.timeWindowOptions }
                ?: run {
                    showError(context.getString(R.string.derived_error_no_valid_time_window))
                    return@launch
                }
            val sourceType = group.sourceTypes.firstOrNull { it == PolarBleApi.PolarDeviceDataType.ACC }
                ?: group.sourceTypes.firstOrNull()
                ?: run {
                    showError(context.getString(R.string.derived_error_no_source_type))
                    return@launch
                }

            Log.d(TAG, "startDerivedOfflineRecording: group=0x${group.groupId.toString(16).uppercase()} " +
                    "sourceSampleRate=$sourceSampleRate Hz (available=${group.sourceSampleRates}) " +
                    "timeWindowMs=$timeWindowMs ms (available=${group.timeWindowOptions}) " +
                    "sourceType=$sourceType methods=${methods.map { it.name }}")

            val settings = PolarDerivedMeasurementSettings(
                groupId = group.groupId,
                sourceMeasurementType = sourceType,
                sourceSampleRate = sourceSampleRate,
                timeWindowMs = timeWindowMs,
                selectedMethods = methods
            )

            Log.d(TAG, "Starting derived offline recording with settings: $settings")
            _uiDerivedRecordingState.update { DerivedRecordingUiState.Recording }
            when (val result = polarDeviceStreamingRepository.startDerivedOfflineRecording(identifier, settings)) {
                is ResultOfRequest.Success -> {
                    showInfo(context.getString(R.string.derived_info_recording_started))
                }
                is ResultOfRequest.Failure -> {
                    _uiDerivedRecordingState.update { DerivedRecordingUiState.NotRecording }
                    showError(result.message, result.throwable)
                }
            }
        }
    }

    fun stopDerivedOfflineRecording() {
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = polarDeviceStreamingRepository.stopDerivedOfflineRecording(identifier)) {
                is ResultOfRequest.Success -> {
                    _uiDerivedRecordingState.update { DerivedRecordingUiState.NotRecording }
                    showInfo(context.getString(R.string.derived_info_recording_stopped))
                }
                is ResultOfRequest.Failure -> {
                    _uiDerivedRecordingState.update { DerivedRecordingUiState.NotRecording }
                    showError(result.message, result.throwable)
                }
            }
        }
    }
}
