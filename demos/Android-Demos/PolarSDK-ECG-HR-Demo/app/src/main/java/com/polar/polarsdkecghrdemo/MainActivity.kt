package com.polar.polarsdkecghrdemo

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.util.Pair
import com.google.android.material.snackbar.Snackbar
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.PolarH10OfflineExerciseApi
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarDeviceInfo
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import java.util.UUID

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "Polar_MainActivity"
        //private const val SHARED_PREFS_KEY = "polar_device_id"
        private const val DEVICE_ID = "89D62721"
        private const val PERMISSION_REQUEST_CODE = 1
    }

    private lateinit var api: PolarBleApi

    private var recordingStartStopDisposable: Disposable? = null
    private var recordingStatusReadDisposable: Disposable? = null
    //private var listExercisesDisposable: Disposable? = null
    //private var fetchExerciseDisposable: Disposable? = null
    //private var removeExerciseDisposable: Disposable? = null

    private var deviceConnected = false
    private var bluetoothEnabled = false

    private lateinit var startRecordingButton: Button
    private lateinit var stopRecordingButton: Button
    private lateinit var checkRecordingStatusButton: Button
    private lateinit var generateImagesButton: Button

    //private val entryCache: MutableMap<String, MutableList<PolarOfflineRecordingEntry>> = mutableMapOf()

    //private lateinit var sharedPreferences: SharedPreferences
    private val bluetoothOnActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode != Activity.RESULT_OK) {
            Log.w(TAG, "Bluetooth off")
            disableAllButtons()
        } else {
            enableAllButtons()
        }
    }
    //private var deviceId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //sharedPreferences = getPreferences(MODE_PRIVATE)
        //deviceId = sharedPreferences.getString(SHARED_PREFS_KEY, "")
        startRecordingButton = findViewById(R.id.buttonStartRecording)
        stopRecordingButton = findViewById(R.id.buttonStopRecording)
        checkRecordingStatusButton = findViewById(R.id.buttonCheckRecordingStatus)
        generateImagesButton = findViewById(R.id.buttonGenerateImages)

        api = PolarBleApiDefaultImpl.defaultImplementation(
            applicationContext,
            setOf(
                PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP,
                PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO
            )
        )

        checkBT()
        api.setApiCallback(object : PolarBleApiCallback() {
            override fun blePowerStateChanged(powered: Boolean) {
                Log.d(TAG, "BLE power: $powered")
                bluetoothEnabled = powered
                if (powered) {
                    enableAllButtons()
                    showToast("Phone Bluetooth on")
                } else {
                    disableAllButtons()
                    showToast("Phone Bluetooth off")
                }
            }

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "CONNECTED: $DEVICE_ID")
                deviceConnected = true
                enableAllButtons()
            }

            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "CONNECTING: $DEVICE_ID")
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "DISCONNECTED: $DEVICE_ID")
                deviceConnected = false
                disableAllButtons()
            }

            override fun bleSdkFeatureReady(identifier: String, feature: PolarBleApi.PolarBleSdkFeature) {
                Log.d(TAG, "feature ready $feature")
            }

            override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
                Log.d(TAG, "DIS INFO uuid: $uuid value: $value")
            }

            override fun batteryLevelReceived(identifier: String, level: Int) {
                Log.d(TAG, "BATTERY LEVEL: $level")
            }
        })
        try {
            api.connectToDevice(DEVICE_ID)
        } catch (a: PolarInvalidArgument) {
            a.printStackTrace()
        }
        //hrConnectButton.setOnClickListener { onClickConnectHr(it) }
        //TODO Add set time
        /*setTimeButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            calendar.time = Date()
            api.setLocalTime(deviceId, calendar)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        val timeSetString = "time ${calendar.time} set to device"
                        Log.d(TAG, timeSetString)
                        showToast(timeSetString)
                    },
                    { error: Throwable -> Log.e(TAG, "set time failed: $error") }
                )
        }*/


        startRecordingButton.setOnClickListener {
            val isDisposed = recordingStartStopDisposable?.isDisposed ?: true
            if (isDisposed) {
                val recordIdentifier = "TEST_APP_ID"
                recordingStartStopDisposable = api.startRecording(DEVICE_ID, recordIdentifier, PolarH10OfflineExerciseApi.RecordingInterval.INTERVAL_1S, PolarH10OfflineExerciseApi.SampleType.RR)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        {
                            val recordingStartOk = "Recording started with id $recordIdentifier"
                            Log.d(TAG, recordingStartOk)
                            showSnackbar(recordingStartOk)
                        },
                        { error: Throwable ->
                            val title = "Recording start failed with id $recordIdentifier"
                            val message = "Possible reasons are, the recording is already started on the device or there is exercise recorded on H10. " +
                                    "H10 can have one recording in the memory at the time.\n\n" +
                                    "Detailed Reason: $error"
                            Log.e(TAG, "Recording start failed with id $recordIdentifier. Reason: $error")
                            showDialog(title, message)
                        }
                    )
            } else {
                Log.d(TAG, "Recording start or stop request is already in progress at the moment.")
            }
        }

        stopRecordingButton.setOnClickListener {
            val isDisposed = recordingStartStopDisposable?.isDisposed ?: true
            if (isDisposed) {
                recordingStartStopDisposable = api.stopRecording(DEVICE_ID)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        {
                            val recordingStopOk = "Recording stopped"
                            Log.d(TAG, recordingStopOk)
                            showSnackbar(recordingStopOk)
                        },
                        { error: Throwable ->
                            val recordingStopError = "Recording stop failed. Reason: $error"
                            Log.e(TAG, recordingStopError)
                            showSnackbar(recordingStopError)
                        }
                    )
            } else {
                Log.d(TAG, "Recording start or stop request is already in progress at the moment.")
            }
        }

        checkRecordingStatusButton.setOnClickListener {
            val isDisposed = recordingStatusReadDisposable?.isDisposed ?: true
            if (isDisposed) {
                recordingStatusReadDisposable = api.requestRecordingStatus(DEVICE_ID)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { pair: Pair<Boolean, String> ->
                            val recordingOn = pair.first
                            val recordingId = pair.second

                            val recordingStatus = if (!recordingOn && recordingId.isEmpty()) {
                                "H10 Recording is OFF"
                            } else if (!recordingOn && recordingId.isNotEmpty()) {
                                "H10 Recording is OFF.\n\n" +
                                        "Exercise id $recordingId is currently found on H10 memory"
                            } else if (recordingOn && recordingId.isNotEmpty()) {
                                "H10 Recording is ON.\n\n" +
                                        "Exercise id $recordingId recording ongoing"
                            } else if (recordingOn && recordingId.isEmpty()) {
                                // This state is undefined. If recording is currently ongoing the H10 must return id of the recording
                                "H10 Recording state UNDEFINED"
                            } else {
                                // This state is unreachable and should never happen
                                "H10 recording state ERROR"
                            }
                            Log.d(TAG, recordingStatus)
                            showDialog("Recording status", recordingStatus)
                        },
                        { error: Throwable ->
                            val recordingStatusReadError = "Recording status read failed. Reason: $error"
                            Log.e(TAG, recordingStatusReadError)
                            showSnackbar(recordingStatusReadError)
                        }
                    )
            } else {
                Log.d(TAG, "Recording status request is already in progress at the moment.")
            }
        }

        generateImagesButton.setOnClickListener {
            val intent = Intent(this, ImageActivity::class.java)
            intent.putExtra("id", DEVICE_ID)
            startActivity(intent)
        }
    }

    /*private fun onClickConnectHr(view: View) {
        checkBT()
        if (deviceId == null || deviceId == "") {
            deviceId = sharedPreferences.getString(SHARED_PREFS_KEY, "")
            showDialog(view)
        } else {
            showToast(getString(R.string.connecting) + " " + deviceId)
            val intent = Intent(this, HRActivity::class.java)
            intent.putExtra("id", deviceId)
            startActivity(intent)
        }
    }

    private fun onClickChangeID(view: View) {
        showDialog(view)
    }*/

    private fun checkBT() {
        val btManager = applicationContext.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = btManager.adapter
        if (bluetoothAdapter == null) {
            showToast("Device doesn't support Bluetooth")
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            bluetoothOnActivityResultLauncher.launch(enableBtIntent)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), PERMISSION_REQUEST_CODE)
            } else {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_CODE)
            }
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (index in 0..grantResults.lastIndex) {
                if (grantResults[index] == PackageManager.PERMISSION_DENIED) {
                    Log.w(TAG, "Needed permissions are missing")
                    showToast("Needed permissions are missing")
                    return
                }
            }
            Log.d(TAG, "Needed permissions are granted")
        }
    }

    private fun showToast(message: String) {
        val toast = Toast.makeText(applicationContext, message, Toast.LENGTH_LONG)
        toast.show()
    }

    private fun showSnackbar(message: String) {
        //val contextView = findViewById<View>(R.id.buttons_container)
        val contextView = findViewById<View>(android.R.id.content)
        Snackbar.make(contextView, message, Snackbar.LENGTH_LONG)
            .show()
    }

    private fun disableAllButtons() {
        startRecordingButton.isEnabled = false
        stopRecordingButton.isEnabled = false
        checkRecordingStatusButton.isEnabled = false
        generateImagesButton.isEnabled = false
        toggleButton(startRecordingButton, true)
        toggleButton(stopRecordingButton, true)
        toggleButton(checkRecordingStatusButton, true)
        toggleButton(generateImagesButton, true)
    }

    private fun enableAllButtons() {
        startRecordingButton.isEnabled = true
        stopRecordingButton.isEnabled = true
        checkRecordingStatusButton.isEnabled = true
        generateImagesButton.isEnabled = true
        toggleButton(startRecordingButton, false)
        toggleButton(stopRecordingButton, false)
        toggleButton(checkRecordingStatusButton, false)
        toggleButton(generateImagesButton, false)
    }

    private fun showDialog(title: String, message: String) {
        AlertDialog.Builder(this, R.style.PolarTheme)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { _, _ ->
                // Respond to positive button press
            }
            .show()
    }

    private fun toggleButton(button: Button, isDown: Boolean) {
        var buttonDrawable = button.background
        buttonDrawable = DrawableCompat.wrap(buttonDrawable!!)
        if (isDown) {
            DrawableCompat.setTint(buttonDrawable, resources.getColor(R.color.colorPrimaryDark, null))
        } else {
            DrawableCompat.setTint(buttonDrawable, resources.getColor(R.color.colorPrimary, null))
        }
        button.background = buttonDrawable
    }

    public override fun onDestroy() {
        super.onDestroy()
        api.shutDown()
    }
}