package com.polar.polarsensordatacollector.ui.devicesettings

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
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
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.CalendarConstraints.DateValidator
import com.google.android.material.datepicker.CompositeDateValidator
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.gson.GsonBuilder
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.repository.SdkMode
import com.polar.polarsensordatacollector.ui.activity.ActivityRecordingFragmentDirections
import com.polar.polarsensordatacollector.ui.utils.showSnackBar
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.CheckFirmwareUpdateStatus
import com.polar.sdk.api.model.PolarDiskSpaceData
import com.polar.sdk.api.model.PolarUserDeviceSettings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import com.polar.polarsensordatacollector.ui.exercise.ExerciseActivity
import com.polar.sdk.api.model.PolarPhysicalConfiguration
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Locale

@AndroidEntryPoint
class DeviceSettingsFragment : Fragment(R.layout.fragment_device_settings) {
    companion object {
        private const val TAG = "DeviceSettingsFragment"
        private const val INTENSITY_SELECTION_ENABLED_ALPHA = 1.0f
        private const val INTENSITY_SELECTION_DISABLED_ALPHA = 0.4f
    }

    private val viewModel: DeviceSettingsViewModel by viewModels()

    private lateinit var sdkModeGroup: ConstraintLayout
    private lateinit var sdkModeToggleButton: Button
    private lateinit var sdkModeToggleHeader: TextView

    private lateinit var sdkModeLedGroup: ConstraintLayout
    private lateinit var sdkModeLedButton: Button
    private lateinit var sdkModeLedHeader: TextView

    private lateinit var ppiModeLedGroup: ConstraintLayout
    private lateinit var ppiModeLedButton: Button
    private lateinit var ppiModeLedHeader: TextView

    private lateinit var timeSettingsGroup: ConstraintLayout
    private lateinit var readTimeButton: Button
    private lateinit var writeTimeButton: Button

    private lateinit var offlineRecSettingsGroup: ConstraintLayout
    private lateinit var offlineRecSecuritySettingsHeader: TextView
    private lateinit var offlineRecSecuritySettingsEnabled: SwitchMaterial

    private lateinit var doPhysicalConfigGroup: ConstraintLayout
    private lateinit var doPhysicalConfigButton: Button
    private lateinit var doPhysicalConfigHeader: TextView

    private lateinit var getFtuConfigGroup: ConstraintLayout
    private lateinit var getFtuButton: Button
    private lateinit var getFtuConfigHeader: TextView

    private lateinit var doRestartGroup: ConstraintLayout
    private lateinit var doRestartButton: Button
    private lateinit var doRestartHeader: TextView

    private lateinit var dofactoryResetGroup: ConstraintLayout
    private lateinit var dofactoryResetButton: Button
    private lateinit var dofactoryResetHeader: TextView
    private lateinit var dofactoryResetEnabledSwitch: SwitchMaterial
    private lateinit var dofactoryResetSwitchHeader: TextView

    private lateinit var setExerciseGroup: ConstraintLayout
    private lateinit var setExerciseButton: Button
    private lateinit var setExerciseHeader: TextView

    private lateinit var setWareHouseSleepGroup: ConstraintLayout
    private lateinit var setWareHouseSleepButton: Button
    private lateinit var setWareHouseSleepHeader: TextView

    private lateinit var setTurnDeviceOffGroup: ConstraintLayout
    private lateinit var setTurnDeviceOffButton: Button
    private lateinit var setTurnDeviceOffHeader: TextView

    private lateinit var doFirmwareUpdateGroup: ConstraintLayout
    private lateinit var doFirmwareUpdateButton: Button
    private lateinit var doFirmwareUpdateHeader: TextView
    private lateinit var doFirmwareUpdateCustomUrlButton: Button
    private lateinit var firmwareUpdateStatusText: TextView

    private lateinit var userDataSelectionSpinner: Spinner
    private lateinit var userDataDeletionSelectButton: Button
    private lateinit var deviceDataType: PolarBleApi.PolarStoredDataType

    private lateinit var doUserDeviceSettingsButton: Button
    private lateinit var getUserPhysicalInfoButton: Button

    private lateinit var deleteDateFoldersButton: Button

    private lateinit var waitForConnectionButton: Button
    private lateinit var waitForConnectionStatusText: TextView

    private lateinit var getDiskSpaceButton: Button
    private lateinit var bleMultiConnectionEnableGroup: ConstraintLayout
    private lateinit var bleMultiConnectionEnableHeader: TextView
    private lateinit var bleMultiConnectionEnableButton: Button
    private lateinit var bleMultiConnectionEnabledSwitch: SwitchMaterial

    private lateinit var sleepRecordingStateHeader: TextView
    private lateinit var forceStopSleepButton: Button

    private var latestPhysicalConfiguration: PolarPhysicalConfiguration? = null

    interface DateRangeSelectedListener {
        fun onDateRangeSelected(fromDate: LocalDate?, toDate: LocalDate?)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
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
                    if (it.header.isNotEmpty()) {
                        showSnackBar(
                            rootView = requireView(),
                            it.header,
                            it.description ?: "",
                            showAsError = true
                        )
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiShowInfo.collect {
                    if (it.header.isNotEmpty()) {
                        showSnackBar(rootView = requireView(), it.header, it.description ?: "")
                    }
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
                viewModel.uiMultiBleModeState.collect { bleMultiConnectionStateChange(it)
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
                viewModel.physInfo.collect { physInfo ->
                    latestPhysicalConfiguration = physInfo
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

        setExerciseButton.setOnClickListener {
            ExerciseActivity.launch(requireContext())
        }

        offlineRecSecuritySettingsEnabled.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleSecurity(isChecked)
        }

        doPhysicalConfigButton.setOnClickListener {
            viewModel.openPhysicalConfigActivity(requireContext())
        }

        doUserDeviceSettingsButton.setOnClickListener {
            viewModel.openUserDeviceSettingsActivity(requireContext())
        }

        getUserPhysicalInfoButton.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.getUserPhysicalInfo()
                val ftu = viewModel.physInfo.drop(1).filterNotNull().first()
                showUserPhysicalInfoDialog(ftu)
            }
        }

        getFtuButton.setOnClickListener {
            viewModel.getFtuInfo()
        }

        doRestartButton.setOnClickListener {
            viewModel.doRestart()
        }

        dofactoryResetButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.do_factory_reset_header))
                .setMessage(getString(R.string.confirm_factory_reset))
                .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                    val savePairing = dofactoryResetEnabledSwitch.isChecked
                    viewModel.doFactoryReset(savePairing = savePairing)
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }

        setWareHouseSleepButton.setOnClickListener {
            viewModel.setWarehouseSleep()
        }

        setTurnDeviceOffButton.setOnClickListener {
            viewModel.setTurnDeviceOff()
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
                Toast.makeText(this.context, "An error occurred while forcing sleep recording to stop. Error: ${e.message}", Toast.LENGTH_LONG).show()
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

        bleMultiConnectionEnableButton.setOnClickListener {
            val enableDisableBleMultiConnection = bleMultiConnectionEnabledSwitch.isChecked
            viewModel.setBleMultiConnection(enabled = enableDisableBleMultiConnection)
        }
    }

    private fun setupViews(view: View) {
        sdkModeGroup = view.findViewById(R.id.sdk_mode_group)
        sdkModeToggleHeader = view.findViewById(R.id.sdk_mode_header)
        sdkModeToggleButton = view.findViewById(R.id.sdk_mode_toggle_button)

        sdkModeLedGroup = view.findViewById(R.id.sdk_mode_led_group)
        sdkModeLedHeader = view.findViewById(R.id.sdk_mode_led_header)
        sdkModeLedButton = view.findViewById(R.id.sdk_mode_toggle_led_button)

        ppiModeLedGroup = view.findViewById(R.id.ppi_mode_led_group)
        ppiModeLedHeader = view.findViewById(R.id.ppi_mode_led_header)
        ppiModeLedButton = view.findViewById(R.id.ppi_mode_toggle_led_button)

        timeSettingsGroup = view.findViewById(R.id.time_settings_group)
        readTimeButton = view.findViewById(R.id.time_settings_read_button)
        writeTimeButton = view.findViewById(R.id.time_settings_write_button)

        offlineRecSettingsGroup = view.findViewById(R.id.offline_rec_settings_group)
        offlineRecSecuritySettingsHeader =
            view.findViewById(R.id.offline_rec_security_settings_header)
        offlineRecSecuritySettingsEnabled =
            view.findViewById(R.id.offline_rec_security_settings_enabled)

        doPhysicalConfigGroup = view.findViewById(R.id.do_physical_config_group)
        doPhysicalConfigHeader = view.findViewById(R.id.do_physical_config_header)
        doPhysicalConfigButton = view.findViewById(R.id.do_physical_config_button)

        getFtuConfigGroup = view.findViewById(R.id.get_ftu_group)
        getFtuConfigHeader = view.findViewById(R.id.get_ftu_header)
        getFtuButton = view.findViewById(R.id.get_ftu_button)

        doRestartGroup = view.findViewById(R.id.do_restart_group)
        doRestartHeader = view.findViewById(R.id.do_restart_header)
        doRestartButton = view.findViewById(R.id.do_restart_button)

        dofactoryResetGroup = view.findViewById(R.id.do_factory_reset_group)
        dofactoryResetButton = view.findViewById(R.id.do_factory_reset_button)
        dofactoryResetHeader = view.findViewById(R.id.do_factory_reset_header)
        dofactoryResetEnabledSwitch = view.findViewById(R.id.do_factory_reset_switch)
        dofactoryResetSwitchHeader = view.findViewById(R.id.do_factory_reset_switch_header)

        setExerciseGroup = view.findViewById(R.id.set_exercise_group)
        setExerciseButton = view.findViewById(R.id.set_exercise_button)
        setExerciseHeader = view.findViewById(R.id.set_exercise_header)

        setWareHouseSleepGroup = view.findViewById(R.id.set_warehouse_sleep_group)
        setWareHouseSleepButton = view.findViewById(R.id.set_warehouse_sleep_button)
        setWareHouseSleepHeader = view.findViewById(R.id.set_warehouse_sleep_button_header)

        setTurnDeviceOffGroup = view.findViewById(R.id.set_warehouse_sleep_group)
        setTurnDeviceOffButton = view.findViewById(R.id.set_turn_device_off_button)
        setTurnDeviceOffHeader = view.findViewById(R.id.set_turn_device_off_header)

        doFirmwareUpdateGroup = view.findViewById(R.id.do_firmware_update_group)
        doFirmwareUpdateHeader = view.findViewById(R.id.do_firmware_update_header)
        doFirmwareUpdateButton = view.findViewById(R.id.do_firmware_update_button)
        doFirmwareUpdateCustomUrlButton = view.findViewById(R.id.do_firmware_update_custom_url_button)
        firmwareUpdateStatusText = view.findViewById(R.id.firmware_update_status)

        sleepRecordingStateHeader = view.findViewById(R.id.force_stop_sleep_header)
        forceStopSleepButton = view.findViewById(R.id.force_stop_sleep_button)

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
        doUserDeviceSettingsButton = view.findViewById(R.id.do_device_user_settings_button)
        getUserPhysicalInfoButton = view.findViewById(R.id.get_user_physical_info_button)
        deleteDateFoldersButton = view.findViewById(R.id.do_date_folder_delete_button)

        waitForConnectionButton = view.findViewById(R.id.wait_connection_button)
        waitForConnectionStatusText = view.findViewById(R.id.wait_connection_status_text)

        getDiskSpaceButton = view.findViewById(R.id.get_disk_space_button)
        bleMultiConnectionEnableGroup = view.findViewById(R.id.multi_ble_settings_group)
        bleMultiConnectionEnableButton = view.findViewById(R.id.multi_ble_settings_button)
        bleMultiConnectionEnableHeader =
            view.findViewById(R.id.multi_ble_settings_header)
        bleMultiConnectionEnabledSwitch =
            view.findViewById(R.id.multi_ble_settings_enabled)

        forceStopSleepButton = view.findViewById(R.id.force_stop_sleep_button)
    }

    private fun sdkModeStateChange(sdkModeUiState: SdkModeUiState) {
        Log.d(TAG, "Update UI SDK Mode: $sdkModeUiState")
        if (sdkModeUiState.isAvailable) {
            sdkModeToggleHeader.visibility = VISIBLE
            sdkModeToggleButton.visibility = VISIBLE
            sdkModeLedHeader.visibility = VISIBLE
            sdkModeLedButton.visibility = VISIBLE
            ppiModeLedHeader.visibility = VISIBLE
            ppiModeLedButton.visibility = VISIBLE
            when (sdkModeUiState.sdkModeState) {
                SdkMode.STATE.ENABLED -> {
                    sdkModeToggleButton.text = "Disable"
                    sdkModeToggleButton.isEnabled = true
                }
                SdkMode.STATE.DISABLED -> {
                    sdkModeToggleButton.text = "Enable"
                    sdkModeToggleButton.isEnabled = true
                }
                SdkMode.STATE.STATE_CHANGE_IN_PROGRESS -> {
                    //TODO, add animation
                    sdkModeToggleButton.text = "Wait..."
                    sdkModeToggleButton.isEnabled = false
                }
            }
            when (sdkModeUiState.sdkModeLedState) {
                SdkMode.STATE.ENABLED -> {
                    sdkModeLedButton.text = "Disable"
                }
                else -> {
                    sdkModeLedButton.text = "Enable"
                }
            }
            when (sdkModeUiState.ppiModeLedState) {
                SdkMode.STATE.ENABLED -> {
                    ppiModeLedButton.text = "Disable"
                }
                else -> {
                    ppiModeLedButton.text = "Enable"
                }
            }
        } else {
            sdkModeToggleHeader.visibility = GONE
            sdkModeToggleButton.visibility = GONE
            sdkModeLedHeader.visibility = GONE
            sdkModeLedButton.visibility = GONE
            ppiModeLedHeader.visibility = GONE
            ppiModeLedButton.visibility = GONE
        }
    }

    private fun securityStateChange(securityUiState: SecurityUiState) {
        if (securityUiState.isAvailable) {
            offlineRecSecuritySettingsEnabled.isChecked = securityUiState.isEnabled
            offlineRecSettingsGroup.visibility = VISIBLE
        } else {
            offlineRecSettingsGroup.visibility = GONE
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
        bleMultiConnectionEnabledSwitch.isChecked = status.isEnabled
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
            when (state.enabled) {
                true -> R.string.sleep_recording_state_on
                false -> R.string.sleep_recording_state_off
                null -> R.string.sleep_recording_state_unavailable
            }
        sleepRecordingStateHeader.text = getString(stringKey)
        forceStopSleepButton.isEnabled = state.enabled != null && state.enabled == true
    }

    private fun showUserPhysicalInfoDialog(physInfo: PolarPhysicalConfiguration) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val ctx = requireContext()

        val message = buildString {
            appendLine(getString(R.string.ftu_gender, physInfo.gender))
            appendLine(getString(R.string.ftu_birthdate, dateFormat.format(physInfo.birthDate)))
            appendLine(getString(R.string.ftu_height, physInfo.height))
            appendLine(getString(R.string.ftu_weight, physInfo.weight))
            appendLine(getString(R.string.ftu_max_hr, physInfo.maxHeartRate))
            appendLine(getString(R.string.ftu_vo2max, physInfo.vo2Max))
            appendLine(getString(R.string.ftu_resting_hr, physInfo.restingHeartRate))
            appendLine(getString(R.string.ftu_training_background, physInfo.trainingBackground))
            appendLine(getString(R.string.ftu_typical_day, physInfo.typicalDay))
            appendLine(getString(R.string.ftu_sleep_goal, physInfo.sleepGoalMinutes))
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