package com.polar.polarsensordatacollector.ui.devicesettings

import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.di.PolarBleSdkModule
import com.polar.sdk.api.model.PolarUserDeviceSettings
import io.reactivex.rxjava3.core.Single

private const val TAG = "UserDeviceSettingsActivity"

class UserDeviceSettingsActivity : AppCompatActivity() {

    private lateinit var setDeviceUserLocationSpinner: Spinner

    private lateinit var usbConnectionSwitch: SwitchMaterial

    private lateinit var atdSwitch: SwitchMaterial

    private lateinit var atdMinimumTrainingDurationSeconds: EditText
    private lateinit var atdSensitivity: NumberPicker

    private lateinit var buttonOk: Button
    private lateinit var buttonCancel: Button

    private val api = PolarBleSdkModule.providePolarBleSdkApi(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_user_settings)

        initItems()
    }

    private fun initItems() {
        initDeviceUserLocationComponents()
        initUSBSettingsComponents()
        initATDSettingsComponents()
        initButtons()

        initWithValues()
    }

    private fun initDeviceUserLocationComponents() {
        setDeviceUserLocationSpinner = findViewById(R.id.set_device_user_location_drop_down)
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            PolarUserDeviceSettings.DeviceLocation.values()
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
            showToast("User device settings not saved")
            super.onBackPressed()
        }
    }

    private fun initWithValues() {

        val settings = getSettings().blockingGet()
        setDeviceUserLocationSpinner.setSelection(settings.deviceLocation!!)

        usbConnectionSwitch.isChecked = settings.usbConnectionMode == true
        atdSwitch.isChecked = settings.automaticTrainingDetectionMode == true

        if (settings.automaticTrainingDetectionSensitivity != null) {
            atdSensitivity.value = settings.automaticTrainingDetectionSensitivity!!
        }
        if (settings.minimumTrainingDurationSeconds != null) {
            atdMinimumTrainingDurationSeconds.setText(settings.minimumTrainingDurationSeconds!!.toString())
        }
    }

    private fun getSettings(): Single<PolarUserDeviceSettings> {
        val deviceId = intent.getStringExtra("DEVICE_ID") ?: run {
            showToast("Device ID not found")
        }
        return api.getUserDeviceSettings(identifier = deviceId.toString())
    }
    private fun saveDeviceSettings() {
        val deviceUserLocation = setDeviceUserLocationSpinner.selectedItemPosition
        val usbEnabled = usbConnectionSwitch.isChecked
        val atdEnabled = atdSwitch.isChecked

        val atdSensitivityValue = if (atdEnabled) atdSensitivity.value else 0
        val atdMinimumDuration = if (atdEnabled) {
            atdMinimumTrainingDurationSeconds.text.toString().toIntOrNull() ?: 0
        } else {
            0
        }

        val deviceId = intent.getStringExtra("DEVICE_ID") ?: run {
            showToast("Device ID not found")
            return
        }

        api.setUserDeviceLocation(deviceId, deviceUserLocation)
            .doOnError { error ->
                Log.e(TAG, "Failed to set user device location", error)
                runOnUiThread {
                    showToast("Failed to set device location")
                }
            }
            .onErrorComplete()
            .andThen(
                api.setUsbConnectionMode(deviceId, usbEnabled)
                    .doOnError { error ->
                        Log.e(TAG, "Failed to set USB connection mode", error)
                        runOnUiThread {
                            showToast("Failed to set USB mode")
                        }
                    }
                    .onErrorComplete()
            )
            .andThen(
                api.setAutomaticTrainingDetectionSettings(
                    identifier = deviceId,
                    automaticTrainingDetectionMode = atdEnabled,
                    automaticTrainingDetectionSensitivity = atdSensitivityValue,
                    minimumTrainingDurationSeconds = atdMinimumDuration
                )
                    .doOnError { error ->
                        Log.e(TAG, "Failed to set ATD settings", error)
                        runOnUiThread {
                            showToast("Failed to set ATD settings")
                        }
                    }
                    .onErrorComplete()
            )
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())
            .subscribe({
                showToast("User device settings saved")
            }, { error ->
                Log.e("UserDeviceSettingsActivity", "Failed to send user device settings to device ${deviceId}, error message: ${error.localizedMessage}", error)
                showToast("Failed to save user device settings")
            })
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        super.onBackPressed();
    }
}
