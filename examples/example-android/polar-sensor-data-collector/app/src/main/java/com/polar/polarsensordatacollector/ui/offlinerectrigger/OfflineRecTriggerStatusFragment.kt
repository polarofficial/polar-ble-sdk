package com.polar.polarsensordatacollector.ui.offlinerectrigger

import android.os.Bundle
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.ui.utils.showSnackBar
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.model.PolarOfflineRecordingTriggerMode
import com.polar.sdk.api.model.PolarSensorSetting
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OfflineRecTriggerStatusFragment : Fragment(R.layout.fragment_offline_trigger_status) {
    companion object {
        private const val TAG = "OfflineRecTriggerSettingsFragment"
        private const val INTENSITY_SELECTION_ENABLED_ALPHA = 1.0f
        private const val INTENSITY_SELECTION_DISABLED_ALPHA = 0.4f
    }

    private val offlineTriggerStatusViewModel: OfflineTriggerStatusViewModel by viewModels()
    private lateinit var triggerModeHeader: TextView
    private lateinit var triggerStatusMode: TextView
    private lateinit var triggerSettingsHeader: TextView
    private lateinit var fetchProgressIndicator: LinearProgressIndicator

    private lateinit var accTriggerStatus: View
    private lateinit var ecgTriggerStatus: View
    private lateinit var ppgTriggerStatus: View
    private lateinit var ppiTriggerStatus: View
    private lateinit var magTriggerStatus: View
    private lateinit var gyrTriggerStatus: View
    private lateinit var pressureTriggerStatus: View
    private lateinit var locTriggerStatus: View
    private lateinit var temperatureTriggerStatus: View
    private lateinit var skinTemperatureTriggerStatus: View
    private lateinit var hrTriggerStatus: View

    private val triggerModes: Map<PolarOfflineRecordingTriggerMode, String> = mapOf(
        PolarOfflineRecordingTriggerMode.TRIGGER_DISABLED to "DISABLED",
        PolarOfflineRecordingTriggerMode.TRIGGER_SYSTEM_START to "SYSTEM START",
        PolarOfflineRecordingTriggerMode.TRIGGER_EXERCISE_START to "EXERCISE START"
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupViews(view)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                offlineTriggerStatusViewModel.uiOfflineRecTriggerStatus.collect {
                    offlineRecTriggerStatusUpdate(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                offlineTriggerStatusViewModel.uiShowError.collect {
                    if (it.header.isNotEmpty()) {
                        showSnackBar(rootView = requireView(), header = it.header, description = it.description ?: "", showAsError = true)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                offlineTriggerStatusViewModel.uiShowInfo.collect {
                    if (it.header.isNotEmpty()) {
                        showSnackBar(rootView = requireView(), it.header, it.description ?: "")
                    }
                }
            }
        }
    }

    private fun setupViews(view: View) {
        triggerModeHeader = view.findViewById(R.id.trigger_status_mode_header)
        triggerStatusMode = view.findViewById(R.id.trigger_status_mode)
        triggerSettingsHeader = view.findViewById(R.id.trigger_status_settings_header)
        fetchProgressIndicator = view.findViewById(R.id.trigger_status_fetching_progress)

        val items: MutableList<String> = mutableListOf()
        for (item in PolarOfflineRecordingTriggerMode.values()) {
            triggerModes[item]?.let {
                items.add(it)
            }
        }
        hrTriggerStatus = view.findViewById(R.id.hr_trigger_status)
        // HR do not have settings
        hrTriggerStatus.findViewById<Button>(R.id.feature_selection_settings_button)?.visibility = View.INVISIBLE

        accTriggerStatus = view.findViewById(R.id.acc_trigger_status)
        ecgTriggerStatus = view.findViewById(R.id.ecg_trigger_status)
        ppgTriggerStatus = view.findViewById(R.id.ppg_trigger_status)
        ppiTriggerStatus = view.findViewById(R.id.ppi_trigger_status)
        // PPI do not have settings
        ppiTriggerStatus.findViewById<Button>(R.id.feature_selection_settings_button)?.visibility = View.INVISIBLE
        magTriggerStatus = view.findViewById(R.id.mag_trigger_status)
        gyrTriggerStatus = view.findViewById(R.id.gyr_trigger_status)
        pressureTriggerStatus = view.findViewById(R.id.pressure_trigger_status)
        locTriggerStatus = view.findViewById(R.id.loc_trigger_status)
        temperatureTriggerStatus = view.findViewById(R.id.temperature_trigger_status)
        skinTemperatureTriggerStatus = view.findViewById(R.id.skin_temperature_trigger_status)

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

    private fun offlineRecTriggerStatusUpdate(offlineRecTriggerUiState: OfflineRecTriggerStatusUiState) {
        when (offlineRecTriggerUiState) {
            is OfflineRecTriggerStatusUiState.FetchingFailed -> {
                fetchProgressIndicator.visibility = GONE
                hideTriggerMode()
                hideSettings()
                showSnackBar(
                    rootView = requireView(),
                    header = offlineRecTriggerUiState.message,
                    showAsError = true,
                    action = Pair("RETRY", { offlineTriggerStatusViewModel.getOfflineRecordingTriggerStatus() })
                )
            }

            OfflineRecTriggerStatusUiState.FetchingStatus -> {
                triggerStatusMode.text = "Fetching status"
                fetchProgressIndicator.visibility = VISIBLE
                hideTriggerMode()
                hideSettings()
            }

            is OfflineRecTriggerStatusUiState.CurrentStatus -> {
                fetchProgressIndicator.visibility = GONE
                showTriggerMode()

                triggerStatusMode.text = triggerModes[offlineRecTriggerUiState.triggerStatus.triggerMode]

                if (offlineRecTriggerUiState.triggerStatus.triggerMode == PolarOfflineRecordingTriggerMode.TRIGGER_DISABLED) {
                    hideSettings()
                } else {
                    // first hide everything and then show the relevant
                    hideSettings()
                    for (triggerFeature in offlineRecTriggerUiState.triggerStatus.triggerFeatures) {
                        val triggerStatusView = getTriggerStatusView(triggerFeature.key)
                        triggerStatusView.visibility = VISIBLE
                        showSettings(feature = triggerFeature.key, settings = triggerFeature.value)
                    }
                }
            }
        }
    }

    private fun hideTriggerMode() {
        triggerModeHeader.visibility = GONE
        triggerStatusMode.visibility = GONE
    }

    private fun showTriggerMode() {
        triggerModeHeader.visibility = VISIBLE
        triggerStatusMode.visibility = VISIBLE
    }

    private fun hideSettings() {
        triggerSettingsHeader.visibility = GONE
        for (feature in PolarDeviceDataType.values()) {
            val triggerStatusView = getTriggerStatusView(feature)
            triggerStatusView.visibility = GONE
        }
    }

    private fun showSettings(feature: PolarDeviceDataType, settings: PolarSensorSetting?) {
        triggerSettingsHeader.visibility = VISIBLE

        val triggerFeatureView = getTriggerStatusView(feature)
        val header: TextView = triggerFeatureView.findViewById(R.id.feature_selection_feature_header)
        header.text = getRecTriggerFeatureHeaderText(feature)

        if (settings != null) {
            val settingsView: View = triggerFeatureView.findViewById(R.id.feature_selection_settings_section)
            settingsView.visibility = VISIBLE
            if (settings.settings.containsKey(PolarSensorSetting.SettingType.SAMPLE_RATE)) {
                val sampleRate: TextView = triggerFeatureView.findViewById(R.id.feature_selection_sample_rate)
                sampleRate.text = settings.settings[PolarSensorSetting.SettingType.SAMPLE_RATE]?.first().toString()
            }

            if (settings.settings.containsKey(PolarSensorSetting.SettingType.RESOLUTION)) {
                val resolution: TextView = triggerFeatureView.findViewById(R.id.feature_selection_resolution)
                resolution.text = settings.settings[PolarSensorSetting.SettingType.RESOLUTION]?.first().toString()
            }

            if (settings.settings.containsKey(PolarSensorSetting.SettingType.RANGE)) {
                val range: TextView = triggerFeatureView.findViewById(R.id.feature_selection_range)
                range.text = settings.settings[PolarSensorSetting.SettingType.RANGE]?.first().toString()
            }
            if (settings.settings.containsKey(PolarSensorSetting.SettingType.CHANNELS)) {
                val channels: TextView = triggerFeatureView.findViewById(R.id.feature_selection_channels)
                channels.text = settings.settings[PolarSensorSetting.SettingType.CHANNELS]?.first().toString()
            }
        }
    }

    private fun getTriggerStatusView(feature: PolarDeviceDataType): View {
        return when (feature) {
            PolarDeviceDataType.ECG -> ecgTriggerStatus
            PolarDeviceDataType.ACC -> accTriggerStatus
            PolarDeviceDataType.PPG -> ppgTriggerStatus
            PolarDeviceDataType.PPI -> ppiTriggerStatus
            PolarDeviceDataType.GYRO -> gyrTriggerStatus
            PolarDeviceDataType.MAGNETOMETER -> magTriggerStatus
            PolarDeviceDataType.SKIN_TEMPERATURE -> skinTemperatureTriggerStatus
            PolarDeviceDataType.PRESSURE -> pressureTriggerStatus
            PolarDeviceDataType.LOCATION -> locTriggerStatus
            PolarDeviceDataType.TEMPERATURE -> temperatureTriggerStatus
            PolarDeviceDataType.HR -> hrTriggerStatus
        }
    }

    private fun getRecTriggerFeatureHeaderText(feature: PolarDeviceDataType): String {
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
            PolarDeviceDataType.SKIN_TEMPERATURE -> "SKIN_TEM"
            PolarDeviceDataType.HR -> "HR"
        }
    }
}