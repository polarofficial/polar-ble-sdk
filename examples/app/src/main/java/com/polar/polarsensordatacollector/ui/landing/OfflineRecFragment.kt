package com.polar.polarsensordatacollector.ui.landing

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.findNavController
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.ui.utils.DialogUtility.showAllSettingsDialog
import com.polar.polarsensordatacollector.ui.utils.showSnackBar
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.model.PolarSensorSetting.SettingType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OfflineRecFragment : Fragment(R.layout.fragment_offline_rec) {
    companion object {
        private const val TAG = "OfflineRecFragment"
        private const val INTENSITY_SELECTION_ENABLED_ALPHA = 1.0f
        private const val INTENSITY_SELECTION_DISABLED_ALPHA = 0.4f
    }

    private val offlineViewModel: OfflineRecordingViewModel by viewModels()

    private lateinit var hrStatusAndSettings: View
    private lateinit var accStatusAndSettings: View
    private lateinit var ecgStatusAndSettings: View
    private lateinit var ppgStatusAndSettings: View
    private lateinit var ppiStatusAndSettings: View
    private lateinit var magStatusAndSettings: View
    private lateinit var gyrStatusAndSettings: View
    private lateinit var pressureStatusAndSettings: View
    private lateinit var locStatusAndSettings: View
    private lateinit var temperatureStatusAndSettings: View
    private lateinit var skinTemperatureStatusAndSettings: View

    private lateinit var recordingStartGroup: ConstraintLayout

    private lateinit var offlineRecStartSelectedButton: Button

    private lateinit var getOfflineRecordingsButton: Button
    private lateinit var setOfflineTriggersButton: Button

    private lateinit var selectedDeviceId: String

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        selectedDeviceId = arguments?.getString(ONLINE_OFFLINE_KEY_DEVICE_ID) ?: throw Exception("OfflineRecFragment has no deviceId")

        setupViews(view)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                offlineViewModel.uiAvailableOfflineRecTypesState.collect {
                    availableFeaturesUpdate(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                offlineViewModel.uiOfflineRecordingState.collect {
                    offlineRecordingStateUpdate(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                offlineViewModel.uiOfflineRequestedSettingsState.collect {
                    userSelectsOfflineSettings(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                offlineViewModel.uiShowError.collect {
                    if (it.header.isNotEmpty()) {
                        showSnackBar(rootView = requireView(), it.header, it.description ?: "", showAsError = true)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                offlineViewModel.uiShowInfo.collect {
                    if (it.header.isNotEmpty()) {
                        showSnackBar(rootView = requireView(), it.header, it.description ?: "")
                    }
                }
            }
        }

        getOfflineRecordingsButton.setOnClickListener {
            val navigateActionToDevice = MainFragmentDirections.offlineRecNavigateToListAction(selectedDeviceId)
            it.findNavController().navigate(navigateActionToDevice)
        }

        setOfflineTriggersButton.setOnClickListener {
            val navigateToTriggers = MainFragmentDirections.offlineRecNavToTriggerAction(selectedDeviceId)
            it.findNavController().navigate(navigateToTriggers)
        }
    }

    private fun availableFeaturesUpdate(streamingFeatureUiState: AvailableOfflineRecordingsState) {
        if (streamingFeatureUiState.offlineRecordingsAvailableOfflineRecordingsState.any { it.value == true }) {
            streamingFeatureUiState.offlineRecordingsAvailableOfflineRecordingsState
                .map {
                    if (it.value) {
                        setOfflineRecStatusAndSettingsView(it.key, enable = true)
                    } else {
                        setOfflineRecStatusAndSettingsView(it.key, enable = false)
                    }
                }

        } else {
            offlineRecStartSelectedButton.isEnabled = false
        }
    }

    private fun userSelectsOfflineSettings(availableStreamSettingsUiState: OfflineAvailableStreamSettingsUiState?) {
        if (availableStreamSettingsUiState != null &&
            availableStreamSettingsUiState.settings.currentlyAvailable != null &&
            availableStreamSettingsUiState.settings.allPossibleSettings != null
        ) {
            showAllSettingsDialog(
                requireActivity(),
                availableStreamSettingsUiState.settings.currentlyAvailable.settings,
                availableStreamSettingsUiState.settings.allPossibleSettings.settings,
                availableStreamSettingsUiState.settings.selectedSettings
            ).toFlowable()
                .doFinally {
                    getOfflineRecSettingsButtonView(availableStreamSettingsUiState.feature)?.isEnabled = true
                }
                .subscribe({ settings: Map<SettingType, Int>? ->
                    Log.d(TAG, "Dialog completed with settings $settings")
                    settings?.let {
                        offlineViewModel.updateSelectedStreamSettings(availableStreamSettingsUiState.feature, it)
                    }
                }, { error: Throwable ->
                    val settingsSelectionFailed = "Error while selecting settings for feature: ${availableStreamSettingsUiState.feature} error: $error"
                    Log.e(TAG, settingsSelectionFailed)
                    showToast(settingsSelectionFailed)
                })
        }
    }

    private fun askStreamSettingsFromUser(identifier: String, feature: PolarDeviceDataType) {
        getOfflineRecSettingsButtonView(feature)?.isEnabled = false
        offlineViewModel.requestOfflineRecSettings(deviceId = identifier, feature = feature)
    }

    private fun setupViews(view: View) {
        hrStatusAndSettings = view.findViewById(R.id.hr_status_and_settings)
        // HR do not have settings
        hrStatusAndSettings.findViewById<Button>(R.id.recording_controls_settings_button)?.visibility = GONE

        accStatusAndSettings = view.findViewById(R.id.acc_status_and_settings)
        ecgStatusAndSettings = view.findViewById(R.id.ecg_status_and_settings)
        ppgStatusAndSettings = view.findViewById(R.id.ppg_status_and_settings)
        ppiStatusAndSettings = view.findViewById(R.id.ppi_status_and_settings)
        // PPI do not have settings
        ppiStatusAndSettings.findViewById<Button>(R.id.recording_controls_settings_button)?.visibility = GONE

        magStatusAndSettings = view.findViewById(R.id.mag_status_and_settings)
        gyrStatusAndSettings = view.findViewById(R.id.gyr_status_and_settings)
        pressureStatusAndSettings = view.findViewById(R.id.pressure_status_and_settings)
        locStatusAndSettings = view.findViewById(R.id.loc_status_and_settings)
        temperatureStatusAndSettings = view.findViewById(R.id.temperature_status_and_settings)
        skinTemperatureStatusAndSettings = view.findViewById(R.id.skin_temperature_status_and_settings)

        recordingStartGroup = view.findViewById(R.id.stream_recording_start_group)

        offlineRecStartSelectedButton = view.findViewById(R.id.offline_recording_button)

        getOfflineRecordingsButton = view.findViewById(R.id.get_offline_recordings_button)
        setOfflineTriggersButton = view.findViewById(R.id.set_offline_triggers_button)
    }

    private fun startAllSelected() {
        val recordingsToStart = mutableListOf<PolarDeviceDataType>()
        for (feature in PolarDeviceDataType.values()) {
            val cb = getOfflineRecCheckBox(feature)
            if (cb.isEnabled && cb.isChecked) {
                recordingsToStart.add(feature)
            }
        }
        if (recordingsToStart.isNotEmpty()) {
            offlineViewModel.startOfflineRecording(features = recordingsToStart)
        } else {
            showToast("None selected")
        }
    }

    private fun enableView(view: View) {
        view.animate().alpha(INTENSITY_SELECTION_ENABLED_ALPHA).withLayer()
    }

    private fun disableView(view: View) {
        view.animate().alpha(INTENSITY_SELECTION_DISABLED_ALPHA).withLayer()
    }

    private fun showToast(message: String) {
        val toast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
        toast.show()
    }

    private fun offlineRecordingStateUpdate(offlineRecordingUiState: OfflineRecordingUiState) {
        Log.d(TAG, "offlineRecordingStateUpdate $offlineRecordingUiState")
        when (offlineRecordingUiState) {
            is OfflineRecordingUiState.Enabled -> {

                offlineRecStartSelectedButton.isEnabled = true
                offlineRecStartSelectedButton.setOnClickListener {
                    startAllSelected()
                }
                offlineRecStartSelectedButton.text = "START SELECTED"

                for (feature in PolarDeviceDataType.values()) {
                    if (offlineRecordingUiState.recordingFeatures.contains(feature)) {
                        offlineRecCheckBox(feature = feature, isRecording = true)
                        offlineRecSettingsButton(feature = feature, isRecording = true)
                        offlineRecStartStopButton(feature = feature, isRecording = true)
                    } else {
                        offlineRecStartStopButton(feature = feature, isRecording = false)
                        offlineRecSettingsButton(feature = feature, isRecording = false)
                        offlineRecCheckBox(feature = feature, isRecording = false)
                    }
                }
            }
            is OfflineRecordingUiState.FetchingStatus -> {
                offlineRecStartSelectedButton.isEnabled = false
                offlineRecStartSelectedButton.text = "WAIT FOR THE STATUS"
            }
        }
    }

    private fun getRecStatusAndSettingsView(feature: PolarDeviceDataType): View {
        return when (feature) {
            PolarDeviceDataType.ECG -> ecgStatusAndSettings
            PolarDeviceDataType.ACC -> accStatusAndSettings
            PolarDeviceDataType.PPG -> ppgStatusAndSettings
            PolarDeviceDataType.PPI -> ppiStatusAndSettings
            PolarDeviceDataType.GYRO -> gyrStatusAndSettings
            PolarDeviceDataType.MAGNETOMETER -> magStatusAndSettings
            PolarDeviceDataType.PRESSURE -> pressureStatusAndSettings
            PolarDeviceDataType.LOCATION -> locStatusAndSettings
            PolarDeviceDataType.TEMPERATURE -> temperatureStatusAndSettings
            PolarDeviceDataType.SKIN_TEMPERATURE -> skinTemperatureStatusAndSettings
            PolarDeviceDataType.HR -> hrStatusAndSettings
        }
    }

    private fun getOfflineRecCheckBox(feature: PolarDeviceDataType): CheckBox {
        val recordingSettingsView = getRecStatusAndSettingsView(feature)
        return recordingSettingsView.findViewById(R.id.recording_controls_select_check_box)
    }

    private fun getOfflineRecStartStopButton(feature: PolarDeviceDataType): Button {
        val recordingSettingsView = getRecStatusAndSettingsView(feature)
        return recordingSettingsView.findViewById(R.id.recording_controls_start_stop_button)
    }

    private fun getOfflineRecSettingsButtonView(feature: PolarDeviceDataType): Button? {
        val recordingSettingsView = getRecStatusAndSettingsView(feature)
        return when (feature) {
            PolarDeviceDataType.HR,
            PolarDeviceDataType.PPI -> null
            else -> recordingSettingsView.findViewById(R.id.recording_controls_settings_button)
        }
    }

    private fun getRecStatusHeaderText(feature: PolarDeviceDataType): String {
        return when (feature) {
            PolarDeviceDataType.ECG -> "ECG"
            PolarDeviceDataType.ACC -> "ACC"
            PolarDeviceDataType.PPG -> "PPG"
            PolarDeviceDataType.PPI -> "PPI"
            PolarDeviceDataType.GYRO -> "GYR"
            PolarDeviceDataType.MAGNETOMETER -> "MAG"
            PolarDeviceDataType.PRESSURE -> "PRE"
            PolarDeviceDataType.LOCATION -> "LOC"
            PolarDeviceDataType.TEMPERATURE -> "TEM"
            PolarDeviceDataType.HR -> "HR"
            PolarDeviceDataType.SKIN_TEMPERATURE -> "SKIN_TEMP"
        }
    }

    private fun setOfflineRecStatusAndSettingsView(feature: PolarDeviceDataType, enable: Boolean) {
        val view = getRecStatusAndSettingsView(feature)
        if (enable) {
            val header: TextView = view.findViewById(R.id.recording_controls_header)
            header.text = getRecStatusHeaderText(feature)
            view.visibility = VISIBLE
        } else {
            view.visibility = GONE
        }
    }

    private fun offlineRecCheckBox(feature: PolarDeviceDataType, isRecording: Boolean) {
        val cb = getOfflineRecCheckBox(feature)
        cb.isEnabled = !isRecording
    }

    private fun offlineRecSettingsButton(feature: PolarDeviceDataType, isRecording: Boolean) {
        val settingsButton = getOfflineRecSettingsButtonView(feature)
        if (isRecording) {
            settingsButton?.isEnabled = false
        } else {
            settingsButton?.isEnabled = true
            settingsButton?.setOnClickListener {
                askStreamSettingsFromUser(selectedDeviceId, feature)
            }
        }
    }

    private fun offlineRecStartStopButton(feature: PolarDeviceDataType, isRecording: Boolean) {
        val button = getOfflineRecStartStopButton(feature)
        if (isRecording) {
            button.text = "STOP"
            button.setTextColor(resources.getColor(R.color.colorButtonRecording, null))
            button.setOnClickListener {
                offlineViewModel.stopOfflineRecording(features = listOf(feature))
            }
        } else {
            button.text = "START"
            button.setTextColor(resources.getColor(R.color.secondaryColor, null))
            button.setOnClickListener {
                offlineViewModel.startOfflineRecording(features = listOf(feature))
            }
        }
    }
}