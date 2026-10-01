package com.polar.polarsensordatacollector.ui.devicesettings

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.util.Patterns
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.util.Pair
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.CalendarConstraints.DateValidator
import com.google.android.material.datepicker.CompositeDateValidator
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.gson.GsonBuilder
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.repository.SdkMode
import com.polar.polarsensordatacollector.ui.activity.ActivityRecordingFragmentDirections
import com.polar.polarsensordatacollector.ui.landing.MainViewModel
import com.polar.polarsensordatacollector.ui.exercise.ExerciseActivity
import com.polar.polarsensordatacollector.ui.genericapi.GenericApiActivity
import com.polar.polarsensordatacollector.ui.utils.showSnackBar
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarDeviceTelemetryType
import com.polar.sdk.api.model.CheckFirmwareUpdateStatus
import com.polar.sdk.api.model.PolarDiskSpaceData
import com.polar.sdk.api.model.PolarPhysicalConfiguration
import com.polar.sdk.api.model.sleep.PolarSleepRecordingStatus
import com.polar.sdk.api.model.PolarUserDeviceSettings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date


@AndroidEntryPoint
class DeviceSettingsFragment : Fragment(R.layout.fragment_device_settings) {
    companion object {
        private const val TAG = "DeviceSettingsFragment"
    }

    private lateinit var viewModel: DeviceSettingsViewModel
    private val mainViewModel: MainViewModel by activityViewModels()

    private lateinit var sdkModeToggleButton: MaterialButton
    private lateinit var sdkModeProgress: CircularProgressIndicator

    private lateinit var sdkModeLedButton: Button

    private lateinit var ppiModeLedButton: Button

    private lateinit var deviceToHostNotificationsButton: Button

    private lateinit var readTimeButton: Button
    private lateinit var writeTimeButton: Button

    private lateinit var offlineRecSecuritySettingsEnabled: MaterialButton
    private lateinit var offlineRecSecuritySettingsProgress: CircularProgressIndicator

    private lateinit var doPhysicalConfigButton: Button

    private lateinit var getPhysicalConfigButton: Button

    private lateinit var doRestartButton: Button

    private lateinit var dofactoryResetGroup: ConstraintLayout
    private lateinit var dofactoryResetButton: Button
    private lateinit var doFactoryResetPreservePairingSwitch: SwitchMaterial

    private lateinit var setExerciseButton: Button

    private lateinit var setWareHouseSleepButton: Button
    private lateinit var setHibernateModeButton: Button

    private lateinit var setTurnDeviceOffButton: Button

    private lateinit var doFirmwareUpdateGroup: ConstraintLayout
    private lateinit var doFirmwareUpdateButton: Button
    private lateinit var doFirmwareUpdateCustomUrlButton: Button
    private lateinit var firmwareUpdateStatusText: TextView

    private lateinit var userDataSelectionSpinner: Spinner
    private lateinit var userDataDeletionSelectButton: Button
    private lateinit var userDataDeletionSelectGroup: ConstraintLayout
    private lateinit var deviceDataType: PolarBleApi.PolarStoredDataType

    private lateinit var doUserDeviceSettingsButton: Button
    private lateinit var getFtuStatusButton: Button

    private lateinit var deleteDateFoldersButton: Button

    private lateinit var deleteTelemetryDataButton: Button

    private lateinit var waitForConnectionButton: Button
    private lateinit var waitForConnectionStatusText: TextView
    private lateinit var waitForConnectionStatusGroup: ConstraintLayout

    private lateinit var getDiskSpaceButton: Button

    private lateinit var bleMultiConnectionEnableButton: MaterialButton
    private lateinit var bleMultiConnectionProgress: CircularProgressIndicator

    private lateinit var sensorInitiatedSecurityModeEnableButton: MaterialButton
    private lateinit var sensorInitiatedSecurityModeProgress: CircularProgressIndicator

    private lateinit var sleepRecordingStateHeader: TextView
    private lateinit var forceStopSleepButton: Button
    private lateinit var forceStopSleepGroup: ConstraintLayout
    private lateinit var getSleepRecordingStateButton: Button
    private lateinit var getSleepRecordingStatusProgress: CircularProgressIndicator

    private lateinit var getChargeStateButton: Button

    private lateinit var bleErrorTestButton: Button

    private lateinit var getBLESignalStrengthButton: Button

    private lateinit var telemetryType: PolarDeviceTelemetryType
    private lateinit var startTelemetryButton: Button
    private lateinit var telemetryGroup: ConstraintLayout
    private lateinit var startTelemetryText: TextView
    private lateinit var telemetryTypeSelectionSpinner: Spinner
    private var telemetryStreamOngoing = false

    private lateinit var genericButton: Button
    private var genericButtonCounter: Int = 0

    private lateinit var watchFaceComplicationsGroup: ConstraintLayout

    interface DateRangeSelectedListener {
        fun onDateRangeSelected(fromDate: LocalDate?, toDate: LocalDate?)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(this)[DeviceSettingsViewModel::class.java]
        setupViews(view)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiSdkModeState.collect {
                    sdkModeStateChange(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiSecurityState.collect {
                    securityStateChange(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiWriteTimeStatus.collect {
                    writeTimeUiState(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiReadTimeStatus.collect {
                    readTimeUiState(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiShowError.collect {
                        showSnackBar(
                            rootView = requireView(),
                            it.header,
                            it.description ?: "",
                            showAsError = true
                        )
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiShowInfo.collect {
                        showSnackBar(rootView = requireView(), it.header, it.description ?: "", timeout = it.timeout)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiFirmwareUpdateStatus.collect { status ->
                    firmwareUpdateStateChange(status)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiCheckFirmwareUpdateStatus.collect { status ->
                    doFirmwareUpdateButton.isEnabled = when (status) {
                        is CheckFirmwareUpdateStatus.CheckFwUpdateAvailable -> true
                        else -> false
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiMultiBleModeState.collect {
                    bleMultiConnectionStateChange(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sleepRecordingState.collect {
                    updateSleepRecordingStateUi(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiSettingsSupportUiState.collect {
                    settingsSupportUiState(SettingsSupportUiState(it))
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiDeviceToHostNotificationsState.collect { state ->
                    deviceToHostNotificationsButton.text =
                        if (state.isObserving) getString(R.string.device_to_host_notifications_stop) else getString(R.string.device_to_host_notifications_start)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiSensorInitiatedSecurityModeState.collect {
                    sensorInitiatedSecurityModeStateChange(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiTelemetryState.collect {
                    telemetryStateChange(it)
                }
            }
        }

        sdkModeToggleButton.setOnClickListener {
            viewModel.sdkModeToggle()
        }

        sdkModeLedButton.setOnClickListener {
            viewModel.sdkModeLedAnimation()
        }

        ppiModeLedButton.setOnClickListener {
            viewModel.ppiModeLedAnimation()
        }

        deviceToHostNotificationsButton.setOnClickListener {
            viewModel.toggleDeviceToHostNotifications()
        }

        setExerciseButton.setOnClickListener {
            ExerciseActivity.launch(requireContext())
        }

        offlineRecSecuritySettingsEnabled.setOnClickListener {
            val currentlyEnabled = viewModel.uiSecurityState.value.isEnabled
            viewModel.toggleSecurity(!currentlyEnabled)
        }

        doPhysicalConfigButton.setOnClickListener {
            viewModel.openPhysicalConfigActivity(requireContext())
        }

        doUserDeviceSettingsButton.setOnClickListener {
            viewModel.openUserDeviceSettingsActivity(requireContext())
        }

        getPhysicalConfigButton.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.getUserPhysicalInfo().run {
                    val ftu = viewModel.physInfo
                    if (ftu != null) {
                        showUserPhysicalInfoDialog(ftu)
                    }
                }
            }
        }

        getFtuStatusButton.setOnClickListener {
            viewModel.getFtuInfo()
        }

        doRestartButton.setOnClickListener {
            viewModel.doRestart()
            mainViewModel.requestRemoveOnlineOfflineFragments()
        }

        dofactoryResetButton.setOnClickListener {
            val preservePairing = doFactoryResetPreservePairingSwitch.isChecked
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.do_factory_reset_header))
                .setMessage(getString(R.string.confirm_factory_reset))
                .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                    viewModel.doFactoryReset(preservePairingInformation = preservePairing)
                    mainViewModel.requestRemoveOnlineOfflineFragments()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }

        setWareHouseSleepButton.setOnClickListener {
            viewModel.setWarehouseSleep()
            mainViewModel.requestRemoveOnlineOfflineFragments()
        }

        setHibernateModeButton.setOnClickListener {
            viewModel.setHibernateMode()
            mainViewModel.requestRemoveOnlineOfflineFragments()
        }

        setTurnDeviceOffButton.setOnClickListener {
            viewModel.setTurnDeviceOff()
            mainViewModel.requestRemoveOnlineOfflineFragments()
        }

        doFirmwareUpdateButton.setOnClickListener {
            try {
                viewModel.doFirmwareUpdate()
            } catch (e: Exception) {
                Log.e(TAG, "Error occurred when starting FWU: ", e)
                Toast.makeText(
                    this.context,
                    "Error occurred when starting FWU: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        doFirmwareUpdateCustomUrlButton.setOnClickListener {
            val input = EditText(context)
            input.inputType = InputType.TYPE_TEXT_VARIATION_URI
            input.setText("")
            input.hint = getText(R.string.enter_firmware_url)

            AlertDialog.Builder(requireContext())
                .setTitle(getText(R.string.firmware_update))
                .setMessage(getText(R.string.enter_the_url_for_firmware_update))
                .setView(input)
                .setPositiveButton(getText(R.string.update)) { _, _ ->
                    val firmwareUrl = input.text.toString().trim()
                    try {
                        if (firmwareUrl.isNotEmpty() && Patterns.WEB_URL.matcher(firmwareUrl)
                                .matches()
                        ) {
                            viewModel.doFirmwareUpdate(firmwareUrl)
                        } else {
                            Toast.makeText(
                                context,
                                getText(R.string.invalid_url),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error occurred when starting FWU:", e)
                        Toast.makeText(
                            this.context,
                            getText(R.string.firmware_update_error_occurred).toString() + { e.message },
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                .setNegativeButton(getText(R.string.cancel), null)
                .show()
        }

        userDataDeletionSelectButton.setOnClickListener {
            deviceDataType =
                PolarBleApi.PolarStoredDataType.values()[userDataSelectionSpinner.selectedItemPosition]

            showDataDeleteDatePicker()
        }

        deleteDateFoldersButton.setOnClickListener {
            showDateRangePicker(object : DateRangeSelectedListener {
                override fun onDateRangeSelected(fromDate: LocalDate?, toDate: LocalDate?) {
                    viewModel.deleteDateFolders(fromDate, toDate)
                }
            })
        }

        deleteTelemetryDataButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.do_telemetry_delete_header))
                .setMessage(getString(R.string.confirm_telemetry_data_delete))
                .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                    viewModel.deleteTelemetryData()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }

        waitForConnectionButton.setOnClickListener() {
            viewModel.waitForConnection()
        }

        getDiskSpaceButton.setOnClickListener {
            viewModel.getDiskSpace(
                onSuccess = { diskSpace ->
                    showDiskSpaceDialog(diskSpace)
                },
                onError = { error ->
                    showSnackBar(
                        rootView = requireView(),
                        header = getString(R.string.get_disk_space_error),
                        error,
                        showAsError = true
                    )
                }
            )
        }

        forceStopSleepButton.setOnClickListener {
            try {
                viewModel.forceStopSleep()
            } catch (e: Exception) {
                Log.e(TAG, "An error occurred while forcing sleep recording to stop: ", e)
                Toast.makeText(
                    this.context,
                    "An error occurred while forcing sleep recording to stop. Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        getSleepRecordingStateButton.setOnClickListener {
            try {
                viewModel.getSleepRecordingStatus()
            } catch (e: Exception) {
                Log.e(TAG, "An error occurred while getting sleep recording state: ", e)
                Toast.makeText(
                    this.context,
                    "An error occurred while getting sleep recording state. Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        getChargeStateButton.setOnClickListener {
            try {
                viewModel.getChargeState()
            } catch (e: Exception) {
                Log.e(TAG, "An error occurred while getting charge state: ", e)
                Toast.makeText(
                    this.context,
                    "An error occurred while getting charge status. Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        getBLESignalStrengthButton.setOnClickListener {
            try {
                viewModel.getBLESignalStrength()
            } catch (e: Exception) {
                Log.e(TAG, "An error occurred while getting BLE signal strength: ", e)
                Toast.makeText(
                    this.context,
                    "An error occurred while getting BLE signal strength. Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        genericButton.isEnabled = true

        genericButton.setOnClickListener {
            if (genericButtonCounter == 8) {
                genericButton.alpha = 1F
            }
            if (genericButtonCounter >= 9) {
                Log.d(TAG, "Launching GenericApiActivity")
                GenericApiActivity.launch(requireContext())
            } else {
                genericButtonCounter++
            }
        }

        bleErrorTestButton.setOnClickListener {
            try {
                viewModel.checkIfDeviceDisconnectedDueRemovedPairing()
            } catch (e: Exception) {
                Log.e(TAG, "An error occurred while getting possible BLE connection problems: ", e)
                Toast.makeText(
                    this.context,
                    "An error occurred while getting BLE connection problems. Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.connectionStatus.collect { isConnected ->
                    waitForConnectionStatusText.text = if (isConnected) {
                        getString(R.string.connected)
                    } else {
                        getString(R.string.waiting_connection)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.watchFaceConfigAvailable.collect { available ->
                    watchFaceComplicationsGroup.visibility = if (available) VISIBLE else GONE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.telemetryAvailable.collect { available ->
                    telemetryGroup.visibility = if (available) VISIBLE else GONE
                    startTelemetryButton.isEnabled = available
                }
            }
        }

        bleMultiConnectionEnableButton.setOnClickListener {
            val currentlyEnabled = viewModel.uiMultiBleModeState.value.isEnabled
            viewModel.setBleMultiConnection(enabled = !currentlyEnabled)
        }

        sensorInitiatedSecurityModeEnableButton.setOnClickListener {
            val currentlyEnabled = viewModel.uiSensorInitiatedSecurityModeState.value.isEnabled
            viewModel.setSensorInitiatedSecurityMode(enabled = !currentlyEnabled)
        }

        view.findViewById<Button>(R.id.watch_face_complications_button).setOnClickListener {
            com.polar.polarsensordatacollector.ui.watchface.WatchFaceActivity.launch(requireContext())
        }

        startTelemetryButton.setOnClickListener {
            telemetryType =
                PolarDeviceTelemetryType.values()[telemetryTypeSelectionSpinner.selectedItemPosition]
            if (telemetryStreamOngoing) {
                telemetryStreamOngoing = false
                startTelemetryButton.text = getString(R.string.start_button)
                viewModel.stopTelemetryStream()
            } else {
                telemetryStreamOngoing = true
                startTelemetryButton.text = getString(R.string.stop_button)
                try {
                    viewModel.startTelemetryStream(telemetryType)
                } catch (e: Exception) {
                    Log.e(TAG, "An error occurred while starting telemetry stream: ", e)
                    telemetryStreamOngoing = false
                    startTelemetryButton.text = getString(R.string.start_button)

                }
            }
        }
    }

    private fun setupViews(view: View) {
        sdkModeToggleButton = view.findViewById(R.id.sdk_mode_toggle_button)
        sdkModeProgress = view.findViewById(R.id.sdk_mode_progress)

        sdkModeLedButton = view.findViewById(R.id.sdk_mode_toggle_led_button)

        ppiModeLedButton = view.findViewById(R.id.ppi_mode_toggle_led_button)

        deviceToHostNotificationsButton = view.findViewById(R.id.device_to_host_notifications_button)

        readTimeButton = view.findViewById(R.id.time_settings_read_button)
        writeTimeButton = view.findViewById(R.id.time_settings_write_button)

        offlineRecSecuritySettingsEnabled =
            view.findViewById(R.id.offline_rec_security_settings_enabled)
        offlineRecSecuritySettingsProgress = view.findViewById(R.id.offline_rec_security_settings_progress)

        doPhysicalConfigButton = view.findViewById(R.id.do_physical_config_button)

        getPhysicalConfigButton = view.findViewById(R.id.get_physical_config_button)

        doRestartButton = view.findViewById(R.id.do_restart_button)

        dofactoryResetGroup = view.findViewById(R.id.do_factory_reset_group)
        dofactoryResetButton = view.findViewById(R.id.do_factory_reset_button)
        doFactoryResetPreservePairingSwitch = view.findViewById(R.id.do_factory_reset_preserve_pairing_switch)

        setExerciseButton = view.findViewById(R.id.set_exercise_button)

        setWareHouseSleepButton = view.findViewById(R.id.set_warehouse_sleep_button)
        setHibernateModeButton = view.findViewById(R.id.set_hibernate_mode_button)

        setTurnDeviceOffButton = view.findViewById(R.id.set_turn_device_off_button)

        doFirmwareUpdateGroup = view.findViewById(R.id.do_firmware_update_group)
        doFirmwareUpdateButton = view.findViewById(R.id.do_firmware_update_button)
        doFirmwareUpdateCustomUrlButton = view.findViewById(R.id.do_firmware_update_custom_url_button)
        firmwareUpdateStatusText = view.findViewById(R.id.firmware_update_status)


        sleepRecordingStateHeader = view.findViewById(R.id.force_stop_sleep_header)
        forceStopSleepButton = view.findViewById(R.id.force_stop_sleep_button)
        getSleepRecordingStateButton = view.findViewById(R.id.get_sleep_status_button)
        getSleepRecordingStatusProgress = view.findViewById(R.id.get_sleep_status_progress)

        genericButton = view.findViewById(R.id.generic_api_button)
        bleErrorTestButton = view.findViewById(R.id.ble_error_test_button)

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            PolarUserDeviceSettings.DeviceLocation.values()
        )

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        userDataSelectionSpinner = view.findViewById(R.id.set_file_type_selection_drop_down)
        val userDataSelectionSpinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            PolarBleApi.PolarStoredDataType.values()
        )

        userDataSelectionSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        userDataSelectionSpinner.adapter = userDataSelectionSpinnerAdapter
        userDataDeletionSelectButton = view.findViewById(R.id.do_file_delete_button)
        userDataDeletionSelectGroup = view.findViewById(R.id.do_file_delete_group)
        doUserDeviceSettingsButton = view.findViewById(R.id.do_device_user_settings_button)
        getFtuStatusButton = view.findViewById(R.id.get_user_physical_info_button)
        deleteDateFoldersButton = view.findViewById(R.id.do_date_folder_delete_button)

        deleteTelemetryDataButton = view.findViewById(R.id.do_telemetry_delete_button)

        waitForConnectionButton = view.findViewById(R.id.wait_connection_button)
        waitForConnectionStatusText = view.findViewById(R.id.wait_connection_status_text)
        waitForConnectionStatusGroup = view.findViewById(R.id.wait_for_connection_group)

        getDiskSpaceButton = view.findViewById(R.id.get_disk_space_button)
        bleMultiConnectionEnableButton = view.findViewById(R.id.multi_ble_settings_button)
        bleMultiConnectionProgress = view.findViewById(R.id.multi_ble_settings_progress)

        sensorInitiatedSecurityModeEnableButton = view.findViewById(R.id.sensor_initiated_security_mode_enable_button)
        sensorInitiatedSecurityModeProgress = view.findViewById(R.id.sensor_initiated_security_mode_progress)

        forceStopSleepButton = view.findViewById(R.id.force_stop_sleep_button)
        forceStopSleepGroup = view.findViewById(R.id.force_stop_sleep_group)

        getChargeStateButton = view.findViewById(R.id.get_charge_state_button)

        getBLESignalStrengthButton = view.findViewById(R.id.ble_signal_strength_button)

        watchFaceComplicationsGroup = view.findViewById(R.id.watch_face_complications_group)

        telemetryGroup = view.findViewById(R.id.telemetry_group)
        startTelemetryButton = view.findViewById(R.id.telemetry_button)
        startTelemetryText = view.findViewById(R.id.ble_signal_strength_button)
        telemetryTypeSelectionSpinner = view.findViewById(R.id.telemetry_type_selection_drop_down)
        val telemetryTypeSelectionSpinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            PolarDeviceTelemetryType.values().map { it.displayName }
        )

        telemetryTypeSelectionSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        telemetryTypeSelectionSpinner.adapter = telemetryTypeSelectionSpinnerAdapter
    }

    private fun settingsSupportUiState(settingsSupportUiState: SettingsSupportUiState) {
        Log.d(TAG, "Update UI settings based on support state: settingsSupportUiState")
        if (settingsSupportUiState.support == false) {
            setWareHouseSleepButton.visibility = GONE
            readTimeButton.visibility = GONE
            getPhysicalConfigButton.visibility = GONE
            doPhysicalConfigButton.visibility = GONE
            getFtuStatusButton.visibility = GONE
            deleteDateFoldersButton.visibility = GONE
            userDataDeletionSelectGroup.visibility = GONE
            setExerciseButton.visibility = GONE
            waitForConnectionStatusGroup.visibility = GONE
            getDiskSpaceButton.visibility = GONE
            getChargeStateButton.visibility = GONE
            forceStopSleepGroup.visibility = GONE
            setTurnDeviceOffButton.visibility = GONE
        }
    }

    private fun sdkModeStateChange(sdkModeUiState: SdkModeUiState) {
        Log.d(TAG, "Update UI SDK Mode: $sdkModeUiState")

        sdkModeProgress.visibility = if (sdkModeUiState.isAvailable && sdkModeUiState.isLoading) VISIBLE else GONE

        if (sdkModeUiState.isAvailable) {
            sdkModeToggleButton.visibility = VISIBLE
            sdkModeLedButton.visibility = VISIBLE
            ppiModeLedButton.visibility = VISIBLE

            if (!sdkModeUiState.isSwitchEnabled) {
                val grayColor = ContextCompat.getColor(requireContext(), R.color.color_gray)
                sdkModeToggleButton.setTextColor(grayColor)
                sdkModeToggleButton.strokeColor = ColorStateList.valueOf(grayColor)
                sdkModeToggleButton.isEnabled = false
            } else {
                when (sdkModeUiState.sdkModeState) {
                    SdkMode.STATE.ENABLED -> {
                        sdkModeToggleButton.text = getString(R.string.sdk_mode_disable)
                        sdkModeToggleButton.isEnabled = true
                    }
                    SdkMode.STATE.DISABLED -> {
                        sdkModeToggleButton.text = getString(R.string.sdk_mode_enable)
                        sdkModeToggleButton.isEnabled = true
                    }
                    SdkMode.STATE.STATE_CHANGE_IN_PROGRESS -> {
                        //TODO, add animation
                        sdkModeToggleButton.text = "Wait..."
                        sdkModeToggleButton.isEnabled = false
                    }
                }
            }
            if (sdkModeUiState.isLoading) {
                sdkModeToggleButton.isEnabled = false
            }
            when (sdkModeUiState.sdkModeLedState) {
                SdkMode.STATE.ENABLED -> {
                    sdkModeLedButton.text = getString(R.string.sdk_mode_led_disable)
                }
                else -> {
                    sdkModeLedButton.text = getString(R.string.sdk_mode_led_enable)
                }
            }
            when (sdkModeUiState.ppiModeLedState) {
                SdkMode.STATE.ENABLED -> {
                    ppiModeLedButton.text = getString(R.string.ppi_mode_led_disable)
                }
                else -> {
                    ppiModeLedButton.text = getString(R.string.ppi_mode_led_enable)
                }
            }
        } else {
            sdkModeToggleButton.visibility = GONE
            sdkModeLedButton.visibility = GONE
            ppiModeLedButton.visibility = GONE
        }
    }

    private fun securityStateChange(securityUiState: SecurityUiState) {
        val colorRes = if (securityUiState.isEnabled) R.color.color_toggle_button_red else R.color.color_toggle_button_blue
        val color = ContextCompat.getColor(requireContext(), colorRes)
        offlineRecSecuritySettingsEnabled.text =
            getString(if (securityUiState.isEnabled) R.string.offline_recording_disable_encryption else R.string.offline_recording_enable_encryption)
        offlineRecSecuritySettingsEnabled.setTextColor(color)
        offlineRecSecuritySettingsEnabled.strokeColor = ColorStateList.valueOf(color)
        offlineRecSecuritySettingsEnabled.isEnabled = securityUiState.isAvailable && !securityUiState.isLoading
        offlineRecSecuritySettingsEnabled.visibility = if (securityUiState.isAvailable) VISIBLE else GONE
        offlineRecSecuritySettingsProgress.visibility = if (securityUiState.isAvailable && securityUiState.isLoading) VISIBLE else GONE
    }

    private fun telemetryStateChange(telemetryUiState: TelemetryUiState) {
        if (telemetryUiState.isAvailable) {
            startTelemetryText.text = R.string.start_button.toString()
            telemetryGroup.visibility = VISIBLE
        } else {
            telemetryGroup.visibility = GONE
        }
    }

    private fun writeTimeUiState(it: StatusWriteTime) {
        when (it) {
            StatusWriteTime.Completed -> {
                writeTimeButton.isEnabled = true
                writeTimeButton.setOnClickListener {
                    viewModel.setTime()
                }
            }
            StatusWriteTime.InProgress -> {
                writeTimeButton.isEnabled = false
            }
        }
    }

    private fun readTimeUiState(it: StatusReadTime) {
        when (it) {
            StatusReadTime.Completed -> {
                readTimeButton.isEnabled = true
                readTimeButton.setOnClickListener {
                    viewModel.readTime()
                }
            }
            StatusReadTime.InProgress -> {
                readTimeButton.isEnabled = false
            }
        }
    }

    private fun firmwareUpdateStateChange(status: String) {
        Log.d(TAG, status)
        firmwareUpdateStatusText.text = status
    }

    private fun bleMultiConnectionStateChange(status: BleMultiConnectionUiState) {
        bleMultiConnectionProgress.visibility = if (status.isLoading) VISIBLE else GONE
        if (!status.isSwitchEnabled) {
            val grayColor = ContextCompat.getColor(requireContext(), R.color.color_gray)
            bleMultiConnectionEnableButton.setTextColor(grayColor)
            bleMultiConnectionEnableButton.strokeColor = ColorStateList.valueOf(grayColor)
            bleMultiConnectionEnableButton.isEnabled = false
            return
        }
        val colorRes = if (status.isEnabled) R.color.color_toggle_button_red else R.color.color_toggle_button_blue
        val color = ContextCompat.getColor(requireContext(), colorRes)
        bleMultiConnectionEnableButton.text =
            getString(if (status.isEnabled) R.string.ble_multi_connection_disable else R.string.ble_multi_connection_enable)
        bleMultiConnectionEnableButton.setTextColor(color)
        bleMultiConnectionEnableButton.strokeColor = ColorStateList.valueOf(color)
        bleMultiConnectionEnableButton.isEnabled = !status.isLoading
    }

    private fun sensorInitiatedSecurityModeStateChange(status: SensorInitiatedSecurityModeUiState) {
        sensorInitiatedSecurityModeProgress.visibility = if (status.isLoading) VISIBLE else GONE
        if (!status.isSwitchEnabled) {
            val grayColor = ContextCompat.getColor(requireContext(), R.color.color_gray)
            sensorInitiatedSecurityModeEnableButton.setTextColor(grayColor)
            sensorInitiatedSecurityModeEnableButton.strokeColor = ColorStateList.valueOf(grayColor)
            sensorInitiatedSecurityModeEnableButton.isEnabled = false
            return
        }
        val colorRes = if (status.isEnabled) R.color.color_toggle_button_red else R.color.color_toggle_button_blue
        val color = ContextCompat.getColor(requireContext(), colorRes)
        sensorInitiatedSecurityModeEnableButton.text =
            getString(if (status.isEnabled) R.string.sensor_initiated_security_mode_disable else R.string.sensor_initiated_security_mode_enable)
        sensorInitiatedSecurityModeEnableButton.setTextColor(color)
        sensorInitiatedSecurityModeEnableButton.strokeColor = ColorStateList.valueOf(color)
        sensorInitiatedSecurityModeEnableButton.isEnabled = !status.isLoading
    }

    private fun showDataDeleteDatePicker() {

        val dialog = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select until date")
            .setPositiveButtonText("Submit")
            .setTheme(R.style.MaterialCalendarTheme)
            .build()

        dialog.addOnPositiveButtonClickListener { timeInMillis ->
            val date = Instant.ofEpochMilli(timeInMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            viewModel.deleteStoredDeviceFiles(deviceDataType, date)
        }

        dialog.show(this.childFragmentManager, "Select until date")
    }

    private fun showDateRangePicker(listener: DateRangeSelectedListener) {
        val constraints = CalendarConstraints.Builder()
        val dateValidatorMax: DateValidator = DateValidatorPointBackward.before(Date().toInstant().toEpochMilli())
        val listValidators = ArrayList<DateValidator>()

        listValidators.apply {
            add(dateValidatorMax)
        }
        val validators = CompositeDateValidator.allOf(listValidators)
        constraints.setValidator(validators)

        val dateRange: MaterialDatePicker<Pair<Long, Long>> = MaterialDatePicker
            .Builder
            .dateRangePicker()
            .setTitleText("Select date range")
            .setTheme(R.style.MaterialCalendarTheme)
            .setCalendarConstraints(constraints.build())
            .build()

        dateRange.show(this.childFragmentManager, "DATE_RANGE_PICKER")

        dateRange.addOnPositiveButtonClickListener {
            val fromDate = Instant.ofEpochMilli(it.first.toLong()).atZone(ZoneId.systemDefault()).toLocalDate()
            val toDate = Instant.ofEpochMilli(it.second.toLong()).atZone(ZoneId.systemDefault()).toLocalDate()

            listener.onDateRangeSelected(fromDate, toDate)
        }

        dateRange.addOnCancelListener {
            findNavController().navigate(ActivityRecordingFragmentDirections.activityToHome())
        }
    }

    private fun showDiskSpaceDialog(diskSpace: PolarDiskSpaceData) {
        val freeFormatted = android.text.format.Formatter.formatFileSize(context, diskSpace.freeSpace)
        val totalFormatted = android.text.format.Formatter.formatFileSize(context, diskSpace.totalSpace)

        val message = getString(R.string.free_disk_space) + " $freeFormatted\n" +
                getString(R.string.total_disk_space) + " $totalFormatted"

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.get_disk_space)
            .setMessage(message)
            .setPositiveButton(R.string.ok_button, null)
            .show()

        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.secondaryColor)
        )
    }

    private fun updateSleepRecordingStateUi(state: SleepRecordingState) {
        val stringKey =
            when (state.status) {
                PolarSleepRecordingStatus.ENABLED -> R.string.sleep_recording_state_on
                PolarSleepRecordingStatus.DISABLED -> R.string.sleep_recording_state_off
                PolarSleepRecordingStatus.UNKNOWN -> R.string.sleep_recording_state_unknown
                null -> R.string.sleep_recording_state_unavailable
            }
        sleepRecordingStateHeader.text = getString(stringKey)
        forceStopSleepButton.isEnabled = state.status == PolarSleepRecordingStatus.ENABLED
        getSleepRecordingStatusProgress.visibility = if (state.isLoading) VISIBLE else GONE
        getSleepRecordingStateButton.isEnabled = !state.isLoading
    }

    private fun showUserPhysicalInfoDialog(physInfo: PolarPhysicalConfiguration) {
        val ctx = requireContext()

        val message = buildString {
            appendLine(getString(R.string.ftu_gender, physInfo.gender))
            appendLine(getString(R.string.ftu_birthdate, physInfo.birthDate.toString()))
            appendLine(getString(R.string.ftu_height, physInfo.height))
            appendLine(getString(R.string.ftu_weight, physInfo.weight))
            appendLine(getString(R.string.ftu_max_hr, physInfo.maxHeartRate))
            appendLine(getString(R.string.ftu_vo2max, physInfo.vo2Max))
            appendLine(getString(R.string.ftu_resting_hr, physInfo.restingHeartRate))
            appendLine(getString(R.string.ftu_training_background, physInfo.trainingBackground))
            appendLine(getString(R.string.ftu_typical_day, physInfo.typicalDay))
            // If device does not support sleep goal it returns zero value. -> Hide TextField if value is zero.
            if (physInfo.sleepGoalMinutes > 0) {
                appendLine(getString(R.string.ftu_sleep_goal, physInfo.sleepGoalMinutes))
            }
        }

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.ftu_dialog_title)
            .setMessage(message)
            .setPositiveButton(R.string.share) { _, _ ->

                val gson = GsonBuilder()
                    .setDateFormat("yyyy-MM-dd")
                    .setPrettyPrinting()
                    .create()
                val json = gson.toJson(physInfo)

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.ftu_dialog_title))
                    putExtra(Intent.EXTRA_TEXT, json)
                }
                ctx.startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.setTextColor(
            ContextCompat.getColor(ctx, R.color.secondaryColor)
        )
    }
}