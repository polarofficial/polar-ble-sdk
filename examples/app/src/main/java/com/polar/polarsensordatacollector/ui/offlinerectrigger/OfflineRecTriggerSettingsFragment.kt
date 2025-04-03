package com.polar.polarsensordatacollector.ui.offlinerectrigger

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.View.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputLayout
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.ui.landing.AvailableOfflineRecordingsState
import com.polar.polarsensordatacollector.ui.utils.DialogUtility
import com.polar.polarsensordatacollector.ui.utils.showSnackBar
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.model.PolarOfflineRecordingTriggerMode
import com.polar.sdk.api.model.PolarSensorSetting
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OfflineRecTriggerSettingsFragment : Fragment(R.layout.fragment_offline_trigger_settings) {
    companion object {
        private const val TAG = "OfflineRecTriggerSettingsFragment"
        private const val INTENSITY_SELECTION_ENABLED_ALPHA = 1.0f
        private const val INTENSITY_SELECTION_DISABLED_ALPHA = 0.4f
    }

    private val offlineTriggerViewModel: OfflineTriggerSettingsViewModel by viewModels()

    private lateinit var triggerModeTextView: TextInputLayout

    private lateinit var hrTriggerSettings: View
    private lateinit var accTriggerSettings: View
    private lateinit var ecgTriggerSettings: View
    private lateinit var ppgTriggerSettings: View
    private lateinit var ppiTriggerSettings: View
    private lateinit var magTriggerSettings: View
    private lateinit var gyrTriggerSettings: View
    private lateinit var pressureTriggerSettings: View
    private lateinit var locTriggerSettings: View
    private lateinit var temperatureTriggerSettings: View
    private lateinit var skinTemperatureTriggerSettings: View
    private lateinit var triggerSetupButton: Button

    private lateinit var offlineTriggerSettingsSetupGroup: View
    private lateinit var setUpProgressIndicator: LinearProgressIndicator

    private val triggerMode: Map<PolarOfflineRecordingTriggerMode, String> = mapOf(
        PolarOfflineRecordingTriggerMode.TRIGGER_DISABLED to "DISABLED",
        PolarOfflineRecordingTriggerMode.TRIGGER_SYSTEM_START to "SYSTEM START",
        PolarOfflineRecordingTriggerMode.TRIGGER_EXERCISE_START to "EXERCISE START"
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        setupViews(view)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                offlineTriggerViewModel.uiOfflineRecTriggerSetup.collect {
                    offlineRecTriggerStateUpdate(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                offlineTriggerViewModel.uiAvailableOfflineRecTypesState.collect {
                    availableFeaturesUpdate(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                offlineTriggerViewModel.uiOfflineRecTriggerSettingsState.collect {
                    userSelectsOfflineSettings(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                offlineTriggerViewModel.uiShowError.collect {
                    if (it.header.isNotEmpty()) {
                        showSnackBar(
                            rootView = requireView(),
                            header = it.header,
                            description = it.description ?: "",
                            showAsError = true
                        )
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                offlineTriggerViewModel.uiShowInfo.collect {
                    if (it.header.isNotEmpty()) {
                        showSnackBar(
                            rootView = requireView(),
                            header = it.header,
                            description = it.description ?: ""
                        )
                    }
                }
            }
        }
    }

    private fun availableFeaturesUpdate(streamingFeatureUiState: AvailableOfflineRecordingsState) {
        if (streamingFeatureUiState.offlineRecordingsAvailableOfflineRecordingsState.any { it.value == true }) {
            streamingFeatureUiState.offlineRecordingsAvailableOfflineRecordingsState
                .map {
                    if (it.value) {
                        setTriggerSettingsView(it.key, enable = true)
                        setTriggerSettingsButton(it.key)
                    } else {
                        setTriggerSettingsView(it.key, enable = false)
                    }
                }

        } else {
            triggerSetupButton.isEnabled = false
        }
    }

    private fun askStreamSettingsFromUser(feature: PolarDeviceDataType) {
        getRecTriggerSettingsButtonView(feature)?.isEnabled = false
        offlineTriggerViewModel.requestStreamSettings(feature = feature)
    }

    private fun setupViews(view: View) {
        val items: MutableList<String> = mutableListOf()
        for (item in PolarOfflineRecordingTriggerMode.values()) {
            triggerMode[item]?.let {
                items.add(it)
            }
        }

        offlineTriggerSettingsSetupGroup = view.findViewById(R.id.offline_trigger_settings_setup_group)
        setUpProgressIndicator = view.findViewById(R.id.offline_trigger_settings_in_progress)

        triggerModeTextView = view.findViewById(R.id.offline_trigger_settings_setup_type)

        val adapter = ArrayAdapter(requireContext(), R.layout.offline_trigger_type_item, items)
        (triggerModeTextView.editText as? AutoCompleteTextView)?.setAdapter(adapter)
        (triggerModeTextView.editText as? AutoCompleteTextView)?.setText(items.first(), false)

        hrTriggerSettings = view.findViewById(R.id.hr_trigger_settings)
        // Hr do not have settings
        hrTriggerSettings.findViewById<Button>(R.id.feature_selection_settings_button)?.visibility = INVISIBLE

        accTriggerSettings = view.findViewById(R.id.acc_trigger_settings)
        ecgTriggerSettings = view.findViewById(R.id.ecg_trigger_settings)
        ppgTriggerSettings = view.findViewById(R.id.ppg_trigger_settings)
        ppiTriggerSettings = view.findViewById(R.id.ppi_trigger_settings)
        // PPI do not have settings
        ppiTriggerSettings.findViewById<Button>(R.id.feature_selection_settings_button)?.visibility = INVISIBLE
        magTriggerSettings = view.findViewById(R.id.mag_trigger_settings)
        gyrTriggerSettings = view.findViewById(R.id.gyr_trigger_settings)
        pressureTriggerSettings = view.findViewById(R.id.pressure_trigger_settings)
        locTriggerSettings = view.findViewById(R.id.loc_trigger_settings)
        temperatureTriggerSettings = view.findViewById(R.id.temperature_trigger_settings)
        skinTemperatureTriggerSettings = view.findViewById(R.id.skin_temperature_trigger_settings)

        triggerSetupButton = view.findViewById(R.id.trigger_setup_button)
    }

    private fun userSelectsOfflineSettings(settingsUiState: OfflineRecTriggerSettingsUiState?) {
        if (settingsUiState != null && settingsUiState.settings.currentlyAvailable != null) {
            DialogUtility.showAllSettingsDialog(
                requireActivity(),
                settingsUiState.settings.currentlyAvailable.settings,
                settingsUiState.settings.currentlyAvailable.settings,
                settingsUiState.settings.selectedSettings
            ).toFlowable()
                .doFinally {
                    getRecTriggerSettingsButtonView(settingsUiState.feature)?.isEnabled = true
                }
                .subscribe({ settings: Map<PolarSensorSetting.SettingType, Int>? ->
                    Log.d(TAG, "Dialog completed with settings $settings")
                    settings?.let {
                        offlineTriggerViewModel.updateSelectedStreamSettings(settingsUiState.feature, it)
                    }
                }, { error: Throwable ->
                    val settingsSelectionFailed = "Error while selecting settings for feature: ${settingsUiState.feature} error: $error"
                    Log.e(TAG, settingsSelectionFailed)
                    showToast(settingsSelectionFailed)
                })
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

    private fun offlineRecTriggerStateUpdate(offlineRecTriggerUiState: OfflineRecSettingsTriggerUiState) {
        when (offlineRecTriggerUiState) {
            OfflineRecSettingsTriggerUiState.ReadyToSetUpTriggers -> {
                setUpProgressIndicator.visibility = GONE

                // clean all the current selections
                for (feature in PolarDeviceDataType.values()) {
                    val cb = getRecTriggerSettingsCheckBox(feature)
                    if (cb.isEnabled && cb.isChecked) {
                        cb.isChecked = false
                    }
                }
                // use the DISABLE as default
                (triggerModeTextView.editText as? AutoCompleteTextView)?.setText(triggerMode[PolarOfflineRecordingTriggerMode.TRIGGER_DISABLED], false)
                clearTriggerSelection()
                disableTriggerSettingsSection()
                triggerModeTextView.editText?.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                        //nop
                    }

                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        //nop
                    }

                    override fun afterTextChanged(s: Editable) {
                        if (s.toString() == triggerMode[PolarOfflineRecordingTriggerMode.TRIGGER_DISABLED]) {
                            disableTriggerSettingsSection()
                        } else {
                            enableTriggerSettingsSection()
                        }
                    }
                })

                enableView(offlineTriggerSettingsSetupGroup)
                triggerSetupButton.isEnabled = true
                triggerSetupButton.setOnClickListener {
                    setupTrigger()
                }
            }
            OfflineRecSettingsTriggerUiState.SettingUpTriggers -> {
                setUpProgressIndicator.visibility = VISIBLE
                disableTriggerSettingsSection()
                triggerSetupButton.isEnabled = false
                disableView(offlineTriggerSettingsSetupGroup)
            }
        }
    }

    private fun setTriggerSettingsButton(feature: PolarDeviceDataType) {
        val settingsButton = getRecTriggerSettingsButtonView(feature)
        settingsButton?.setOnClickListener {
            askStreamSettingsFromUser(feature)
        }
    }

    private fun setupTrigger() {
        val triggerModeString = triggerModeTextView.editText?.text.toString()
        val triggerMode = triggerMode.filter { triggerModeString == it.value }.keys.first()

        val triggersToSetup = mutableListOf<PolarDeviceDataType>()
        for (feature in PolarDeviceDataType.values()) {
            val cb = getRecTriggerSettingsCheckBox(feature)
            if (cb.isEnabled && cb.isChecked) {
                triggersToSetup.add(feature)
            }
        }

        if (triggerMode == PolarOfflineRecordingTriggerMode.TRIGGER_DISABLED) {
            offlineTriggerViewModel.setOfflineRecordingTrigger(triggerMethod = triggerMode, emptyList())
        } else {
            if (triggersToSetup.isNotEmpty()) {
                offlineTriggerViewModel.setOfflineRecordingTrigger(
                    triggerMethod = triggerMode,
                    features = triggersToSetup
                )
            } else {
                showToast("None selected")
            }
        }
    }

    private fun disableTriggerSettingsSection() {
        for (feature in PolarDeviceDataType.values()) {
            val view = getTriggerSettingsView(feature)
            disableView(view)
            getRecTriggerSettingsButtonView(feature)?.isEnabled = false
            getRecTriggerSettingsCheckBox(feature).isEnabled = false
        }
    }

    private fun clearTriggerSelection() {
        for (feature in PolarDeviceDataType.values()) {
            getRecTriggerSettingsCheckBox(feature).isChecked = false
        }
    }

    private fun enableTriggerSettingsSection() {
        for (feature in PolarDeviceDataType.values()) {
            val view = getTriggerSettingsView(feature)
            enableView(view)
            getRecTriggerSettingsButtonView(feature)?.isEnabled = true
            getRecTriggerSettingsCheckBox(feature).isEnabled = true
        }
    }

    private fun getTriggerSettingsView(feature: PolarDeviceDataType): View {
        return when (feature) {
            PolarDeviceDataType.ECG -> ecgTriggerSettings
            PolarDeviceDataType.ACC -> accTriggerSettings
            PolarDeviceDataType.PPG -> ppgTriggerSettings
            PolarDeviceDataType.PPI -> ppiTriggerSettings
            PolarDeviceDataType.GYRO -> gyrTriggerSettings
            PolarDeviceDataType.MAGNETOMETER -> magTriggerSettings
            PolarDeviceDataType.PRESSURE -> pressureTriggerSettings
            PolarDeviceDataType.LOCATION -> locTriggerSettings
            PolarDeviceDataType.TEMPERATURE -> temperatureTriggerSettings
            PolarDeviceDataType.SKIN_TEMPERATURE -> skinTemperatureTriggerSettings
            PolarDeviceDataType.HR -> hrTriggerSettings
        }
    }

    private fun getRecTriggerSettingsCheckBox(feature: PolarDeviceDataType): CheckBox {
        val recordingSettingsView = getTriggerSettingsView(feature)
        return recordingSettingsView.findViewById(R.id.feature_selection_select_check_box)
    }

    private fun getRecTriggerSettingsButtonView(feature: PolarDeviceDataType): Button? {
        val recordingSettingsView = getTriggerSettingsView(feature)
        return when (feature) {
            PolarDeviceDataType.HR,
            PolarDeviceDataType.PPI -> null
            else -> recordingSettingsView.findViewById(R.id.feature_selection_settings_button)
        }
    }

    private fun getRecTriggerSettingsHeaderText(feature: PolarDeviceDataType): String {
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
            PolarDeviceDataType.SKIN_TEMPERATURE -> "SKIM_TEM"
            PolarDeviceDataType.HR -> "HR"
        }
    }

    private fun setTriggerSettingsView(feature: PolarDeviceDataType, enable: Boolean) {
        val view = getTriggerSettingsView(feature)
        if (enable) {
            val header: TextView = view.findViewById(R.id.feature_selection_feature_header)
            header.text = getRecTriggerSettingsHeaderText(feature)
            val settingsButton = getRecTriggerSettingsButtonView(feature)
            val checkBox = getRecTriggerSettingsCheckBox(feature)
            settingsButton?.visibility = VISIBLE
            checkBox.visibility = VISIBLE
            view.visibility = VISIBLE
        } else {
            view.visibility = GONE
        }
    }
}