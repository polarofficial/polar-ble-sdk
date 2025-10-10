package com.polar.polarsensordatacollector.ui.devicesettings

import android.os.Bundle
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import com.polar.polarsensordatacollector.R
import com.polar.sdk.api.model.PolarUserDeviceSettings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class UserDeviceSettingsActivity : AppCompatActivity() {

    private lateinit var setDeviceUserLocationSpinner: Spinner

    private lateinit var usbConnectionSwitch: SwitchMaterial

    private lateinit var atdSwitch: SwitchMaterial

    private lateinit var atdMinimumTrainingDurationSeconds: EditText
    private lateinit var atdSensitivity: NumberPicker

    private lateinit var buttonOk: Button
    private lateinit var buttonCancel: Button

    private val viewModel: UserDeviceSettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_user_settings)

        initItems()
        observeViewModel()
    }

    private fun initItems() {
        initDeviceUserLocationComponents()
        initUSBSettingsComponents()
        initATDSettingsComponents()
        initButtons()
    }

    private fun initDeviceUserLocationComponents() {
        setDeviceUserLocationSpinner = findViewById(R.id.set_device_user_location_drop_down)
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            PolarUserDeviceSettings.DeviceLocation.entries.toTypedArray()
        )

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        setDeviceUserLocationSpinner.adapter = adapter

    }

    private fun initUSBSettingsComponents() {
        usbConnectionSwitch = findViewById(R.id.usb_enable_switch)
    }

    private fun initATDSettingsComponents() {
        atdSwitch = findViewById(R.id.atd_enable_switch)
        atdMinimumTrainingDurationSeconds = findViewById(R.id.atdDurationPicker)
        atdSensitivity = findViewById(R.id.atdSensitivityPicker)

        atdSensitivity.minValue = 0
        atdSensitivity.maxValue = 100
    }

    private fun initButtons() {
        buttonOk = findViewById(R.id.submit_device_settings_button)

        buttonOk.setOnClickListener {
            saveDeviceSettings()
        }

        buttonCancel = findViewById(R.id.cancel_device_settings_button)
        buttonCancel.setOnClickListener {
            showToastAndFinish(getString(R.string.user_device_settings_not_saved))
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                state.deviceLocation?.let { setDeviceUserLocationSpinner.setSelection(it) }
                usbConnectionSwitch.isChecked = state.usbEnabled
                atdSwitch.isChecked = state.atdEnabled
                atdSensitivity.value = state.atdSensitivity
                atdMinimumTrainingDurationSeconds.setText(state.atdMinDuration.toString())
            }
        }

        lifecycleScope.launch {
            viewModel.message.collect { msg ->
                msg?.let { showToastAndFinish(it) }
            }
        }
    }

    private fun saveDeviceSettings() {
        viewModel.saveSettings(
            deviceLocation = setDeviceUserLocationSpinner.selectedItemPosition,
            usbEnabled = usbConnectionSwitch.isChecked,
            atdEnabled = atdSwitch.isChecked,
            atdSensitivity = if (atdSwitch.isChecked) atdSensitivity.value else 0,
            atdMinDuration = if (atdSwitch.isChecked)
                atdMinimumTrainingDurationSeconds.text.toString().toIntOrNull() ?: 0
            else 0
        )
    }

    private fun showToastAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        super.onBackPressed()
    }
}
