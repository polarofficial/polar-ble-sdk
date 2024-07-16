package com.polar.polarsdkecghrdemo

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ContentValues
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.util.Pair
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.PolarH10OfflineExerciseApi
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarExerciseData
import com.polar.sdk.api.model.PolarExerciseEntry
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.math.pow
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "Polar_MainActivity"
        private const val DEVICE_ID = "89D62721"
        private const val PARTICIPANT_NUMBER = 1
        private const val PERMISSION_REQUEST_CODE = 1
        private const val SHARED_LAST_ACTION = "last_action"
        private const val SHARED_START_BUTTON_ENABLED = "start_button_enabled"
        private const val SHARED_STOP_BUTTON_ENABLED = "stop_button_enabled"
        private const val SHARED_FETCH_BUTTON_ENABLED = "fetch_button_enabled"
        private const val SHARED_DELETE_BUTTON_ENABLED = "delete_button_enabled"
        private const val SHARED_GENERATE_IMAGES_BUTTON_ENABLED = "generate_images_button_enabled"
        private const val SHARED_WELCOME_SCREEN = "welcome_screen"
    }

    private lateinit var api: PolarBleApi

    private var recordingStartStopDisposable: Disposable? = null
    private var recordingStatusReadDisposable: Disposable? = null
    private var listRecordingsDisposable: Disposable? = null
    private var removeRecordingDisposable: Disposable? = null

    //It is called entries, but on the H10 there is only entry/recording possible
    private var recordingEntries: MutableList<PolarExerciseEntry> = mutableListOf()

    private lateinit var welcomeView: TextView
    private lateinit var loadRecordingButton: Button
    private lateinit var startRecordingButton: Button
    private lateinit var stopRecordingButton: Button
    private lateinit var deleteButton: Button
    private lateinit var checkRecordingStatusButton: Button
    private lateinit var listRecordingsButton: Button
    private lateinit var generateImagesButton: Button

    private lateinit var sharedPreferences: SharedPreferences

    private val bluetoothOnActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode != Activity.RESULT_OK) {
            Log.w(TAG, "ActivityResult: Bluetooth still turned off")
        } else {
            Log.w(TAG, "ActivityResult: Bluetooth was turned on")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //Use getPreferences if you need only one preferences file for your Activity. For this reason you don't supply a name. Otherwise, use getSharedPreferences() if you want to share preferences across activities.
        sharedPreferences = getPreferences(MODE_PRIVATE)
        welcomeView = findViewById(R.id.welcomeDisplay)
        loadRecordingButton = findViewById(R.id.buttonLoadRecording)
        startRecordingButton = findViewById(R.id.buttonStartRecording)
        stopRecordingButton = findViewById(R.id.buttonStopRecording)
        checkRecordingStatusButton = findViewById(R.id.buttonCheckRecordingStatus)
        generateImagesButton = findViewById(R.id.buttonGenerateImages)
        deleteButton = findViewById(R.id.buttonDelete)
        listRecordingsButton = findViewById(R.id.buttonFetchandList)
        disableAllButtons()
        Log.d(TAG, "MainActivity started, version: " + PolarBleApiDefaultImpl.versionInfo())

        api = PolarBleApiDefaultImpl.defaultImplementation(
            applicationContext,
            setOf(
                PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO
            )
        )
        if (sharedPreferences.getBoolean(SHARED_WELCOME_SCREEN, true)) {
            val title = "Welcome!"
            val message = "Make sure Bluetooth is turned on and you gave the necessary permission to use Bluetooth.\n\n" +
                    "On older devices the location services are needed to search for nearby devices, but we will never actually use or save your location.\n" +
                    "If the app requested the permission to use location services, a restart by closing the app completely is necessary.\n\n" +
                    "If any unexpected problems occur or if you have some questions, please contact us."
            AlertDialog.Builder(this, R.style.PolarTheme)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("I understand") { _ , _ ->
                    sharedPreferences.edit().putBoolean(SHARED_WELCOME_SCREEN, false).apply()
                }
                .show()
        }
        api.setPolarFilter(true)
        checkBT()

        api.setApiCallback(object : PolarBleApiCallback() {
            override fun blePowerStateChanged(powered: Boolean) {
                Log.d(TAG, "BLE power: $powered")
                if (powered) {
                    val startText = "You are ready to go, put the sensor on and wait for connection."
                    welcomeView.text = startText
                    showToast("Phone Bluetooth on")
                } else {
                    disableAllButtons()
                    val startText = "Please ensure bluetooth is on."
                    welcomeView.text = startText
                    showToast("Phone Bluetooth off")
                }
            }

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "CONNECTED: ${polarDeviceInfo.deviceId}")
                var connectedText = "Device connected"
                welcomeView.text = connectedText
                connectedText = "\nLast Action: " + sharedPreferences.getString(SHARED_LAST_ACTION, "")
                welcomeView.append(connectedText)
                //Problem: Buttons will be enabled before bleSdkFeatures are ready which might result in a PolarNotificationNotEnabled, if user is inpatient
                //However, problem does not break anything and is thus temporary ignored.
                //Solution: Not found and writing enableButtons() into bleSdkFeatureReady works not for any reason
                enableButtons()
            }

            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "CONNECTING: ${polarDeviceInfo.deviceId}")
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                disableAllButtons()
                Log.d(TAG, "DISCONNECTED: ${polarDeviceInfo.deviceId}")
                val disconnectedText = "Device disconnected"
                welcomeView.text = disconnectedText
            }

            override fun bleSdkFeatureReady(identifier: String, feature: PolarBleApi.PolarBleSdkFeature) {
                Log.d(TAG, "Feature ready $feature")
            }

            override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
                Log.d(TAG, "DIS INFO uuid: $uuid value: $value")
                if (uuid == UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb")) {
                    val msg = "Firmware: " + value.trim { it <= ' ' }
                    Log.d(TAG, "Firmware: " + identifier + " " + value.trim { it <= ' ' })
                    welcomeView.append("\n" + msg.trimIndent())
                }
            }

            override fun batteryLevelReceived(identifier: String, level: Int) {
                Log.d(TAG, "BATTERY LEVEL of $identifier: $level%")
                val batteryLevelText = "\n\nBattery level: $level% (might be incorrect)"
                welcomeView.append(batteryLevelText)
            }
        })
        try {
            api.connectToDevice(DEVICE_ID)
        } catch (a: PolarInvalidArgument) {
            a.printStackTrace()
        }

        startRecordingButton.setOnClickListener {
            val isDisposed = recordingStartStopDisposable?.isDisposed ?: true
            if (isDisposed) {
                val backup = TimeZone.getDefault()
                val timeZoneTarget = TimeZone.getTimeZone("GMT+2")
                //Changes TimeZone for the current process only. However, no guarantees to last for whole app lifecycle.
                TimeZone.setDefault(timeZoneTarget)
                val calendar = Calendar.getInstance()
                val date = calendar.time
                val newFormat = SimpleDateFormat("dd-MM-yyyy'T'HH-mm-ss", Locale.GERMANY)
                val stringDate = newFormat.format(date)
                //To avoid side effects, undo the TimeZone change
                TimeZone.setDefault(backup)
                val recordIdentifier = "P$PARTICIPANT_NUMBER-$stringDate"
                //Sample Time will be ignored if SampleType is RR, which is the case
                recordingStartStopDisposable = api.startRecording(DEVICE_ID, recordIdentifier, PolarH10OfflineExerciseApi.RecordingInterval.INTERVAL_1S, PolarH10OfflineExerciseApi.SampleType.RR)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        {
                            startRecordingButton.isEnabled = false
                            toggleButton(startRecordingButton, true)
                            sharedPreferences.edit().putBoolean(SHARED_START_BUTTON_ENABLED, false).apply()
                            Log.d(TAG, "Recording started with id \"$recordIdentifier\"")
                            val stringDateWithPoints = stringDate.replace("-", ":")
                            val recordingStartOk = "Recording started at ${stringDateWithPoints.substring(11)}"
                            welcomeView.text = recordingStartOk
                            sharedPreferences.edit().putString(SHARED_LAST_ACTION, recordingStartOk).apply()
                            sharedPreferences.edit().putBoolean(SHARED_STOP_BUTTON_ENABLED, true).apply()
                            toggleButton(stopRecordingButton, false)
                            stopRecordingButton.isEnabled = true
                        },
                        { error: Throwable ->
                            val title = "Recording start failed"
                            val message = "Possible reasons are, the recording has already started or there is a saved recording. " +
                                    "The sensor can only have one recording in the memory at the time.\n\n" +
                                    "Detailed Reason: $error"
                            Log.e(TAG, "Recording start failed with id \"$recordIdentifier\". Reason: $error")
                            showDialog(title, message)
                        }
                    )
            } else {
                val title = "Please be patient"
                val message = "Recording start or stop request is already in progress at the moment."
                Log.d(TAG, "Recording start or stop request is already in progress at the moment.")
                showDialog(title, message)
            }
        }

        stopRecordingButton.setOnClickListener {
            val isDisposed = recordingStartStopDisposable?.isDisposed ?: true
            if (isDisposed) {
                recordingStartStopDisposable = api.stopRecording(DEVICE_ID)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        {
                            stopRecordingButton.isEnabled = false
                            toggleButton(stopRecordingButton, true)
                            sharedPreferences.edit().putBoolean(SHARED_STOP_BUTTON_ENABLED, false).apply()
                            val calendar = Calendar.getInstance()
                            val date = calendar.time
                            val newFormat = SimpleDateFormat("HH:mm:ss", Locale.GERMANY)
                            val stringTime = newFormat.format(date)
                            Log.d(TAG, "Recording stopped")
                            val recordingStopOk = "Recording was stopped at $stringTime"
                            welcomeView.text = recordingStopOk
                            sharedPreferences.edit().putString(SHARED_LAST_ACTION, recordingStopOk).apply()
                            sharedPreferences.edit().putBoolean(SHARED_FETCH_BUTTON_ENABLED, true).apply()
                            toggleButton(listRecordingsButton, false)
                            listRecordingsButton.isEnabled = true
                        },
                        { error: Throwable ->
                            val title = "Recording stop failed"
                            val message = "Possible reasons are, the connection to the device is lost or there is no recording currently on the device. \n\n" +
                                    "Detailed Reason: $error"
                            Log.e(TAG, "Recording stop failed. Reason: $error")
                            showDialog(title, message)
                        }
                    )
            } else {
                val title = "Please be patient"
                val message = "Recording start or stop request is already in progress at the moment."
                Log.d(TAG, "Recording start or stop request is already in progress at the moment.")
                showDialog(title, message)
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
                                "No recording is in progress and no saved recording was found."
                            } else if (!recordingOn && recordingId.isNotEmpty()) {
                                "No recording is in progress but one saved recording was found."
                            } else if (recordingOn && recordingId.isNotEmpty()) {
                                "Recording is in progress."
                            } else if (recordingOn && recordingId.isEmpty()) {
                                // This state is undefined. If recording is currently ongoing the H10 must return id of the recording
                                "H10 Recording state UNDEFINED, please contact us!"
                            } else {
                                // This state is unreachable and should never happen
                                "H10 recording state ERROR, please contact us!"
                            }
                            Log.d(TAG, recordingStatus)
                            showDialog("Recording status", recordingStatus)
                        },
                        { error: Throwable ->
                            val title = "Recording status read failed"
                            val message = "Possible reasons are, there is no connection to the device or there is already a request in progress.\n\n" +
                                    "Detailed Reason: $error"
                            Log.e(TAG, "Recording status read failed. Reason: $error")
                            showDialog(title, message)
                        }
                    )
            } else {
                val title = "Please be patient"
                val message = "Recording status request is already in progress at the moment."
                Log.d(TAG, "Recording status request is already in progress at the moment.")
                showDialog(title, message)
            }
        }

        deleteButton.setOnClickListener {
            val isDisposed = removeRecordingDisposable?.isDisposed ?: true
            if (isDisposed) {
                if (recordingEntries.isNotEmpty()) {
                    val entry = recordingEntries.first()
                    removeRecordingDisposable = api.removeExercise(DEVICE_ID, entry)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                            {
                                recordingEntries.remove(entry)
                                val recordingRemovedOk = "Recording successfully removed from device"
                                welcomeView.text = recordingRemovedOk
                                sharedPreferences.edit().putString(SHARED_LAST_ACTION, recordingRemovedOk).apply()
                                Log.d(TAG, "Recording with id:${entry.identifier} successfully removed")
                                //Toggle the buttons afterwards
                                deleteButton.isEnabled = false
                                generateImagesButton.isEnabled = false
                                listRecordingsButton.isEnabled = false
                                toggleButton(deleteButton, true)
                                toggleButton(generateImagesButton, true)
                                toggleButton(listRecordingsButton, true)
                                sharedPreferences.edit().putBoolean(SHARED_DELETE_BUTTON_ENABLED, false).apply()
                                sharedPreferences.edit().putBoolean(SHARED_FETCH_BUTTON_ENABLED, false).apply()
                                sharedPreferences.edit().putBoolean(SHARED_GENERATE_IMAGES_BUTTON_ENABLED, false).apply()
                                sharedPreferences.edit().putBoolean(SHARED_START_BUTTON_ENABLED, true).apply()
                                toggleButton(startRecordingButton, false)
                                startRecordingButton.isEnabled = true
                            },
                            { error: Throwable ->
                                val title = "Removal of recording failed"
                                val message = "Possible reasons are, the recording was already deleted or internal bug appeared, please contact us.\n\n" +
                                        "Detailed Reason: $error"
                                Log.e(TAG, "Removal of recording:${entry.identifier} failed. Reason: $error")
                                showDialog(title, message)
                            }
                        )
                } else {
                    val title = "Removing the recording is not possible"
                    val message = "Either the device has no recording entries or you haven't list them yet."
                    Log.e(TAG, "Removing the recording is not possible. Reason: No recording found or listed")
                    showDialog(title, message)
                }
            } else {
                val title = "Please be patient"
                val message = "Removing of a recoding is already in progress at the moment."
                Log.d(TAG, "Removing of a recoding is in progress at the moment.")
                showDialog(title, message)
            }
        }

        listRecordingsButton.setOnClickListener {
            val isDisposed = listRecordingsDisposable?.isDisposed ?: true
            if (isDisposed) {
                recordingEntries.clear()
                listRecordingsDisposable = api.listExercises(DEVICE_ID)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { polarExerciseEntry: PolarExerciseEntry ->
                            Log.d(TAG, "path: ${polarExerciseEntry.path} id: ${polarExerciseEntry.identifier}")
                            recordingEntries.add(polarExerciseEntry)
                        },
                        { error: Throwable ->
                            val title = "Failed to list recordings"
                            val message = "Possible reasons are, there were no recordings found or there is one in progress.\n\n" +
                                    "Detailed Reason: $error"
                            Log.e(TAG, "Failed to read recordings. Reason: $error")
                            showDialog(title, message)
                        },
                        {
                            if (recordingEntries.isNotEmpty()) {
                                api.fetchExercise(DEVICE_ID, recordingEntries.first())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe(
                                        { polarExerciseData: PolarExerciseData ->
                                            //It's called hrSamples but it can be HR or RR samples. In my case RR-intervals.
                                            Log.d(TAG, "Recording sample count: ${polarExerciseData.hrSamples.size} samples: ${polarExerciseData.hrSamples}")
                                            val recording = mutableListOf<Int>()
                                            val title = "Recording data read"
                                            var message = "The Recording has ${polarExerciseData.hrSamples.size} RR samples.\n\n"
                                            var sumRR = 0.0
                                            for (i in polarExerciseData.hrSamples.indices) {
                                                sumRR += polarExerciseData.hrSamples[i]
                                                recording.add(i, polarExerciseData.hrSamples[i])
                                            }
                                            val meanRR = sumRR / polarExerciseData.hrSamples.size
                                            var deviations = 0.0
                                            for (i in polarExerciseData.hrSamples.indices) {
                                                deviations += (polarExerciseData.hrSamples[i] - meanRR).pow(2)
                                            }
                                            val sdrr = sqrt(deviations / polarExerciseData.hrSamples.lastIndex)
                                            message += "\nSDRR: $sdrr and would conclude ${if (sdrr < 50.0) {"more stressful"} else {"less stressful"}}"
                                            showDialog(title, message)
                                            val id = recordingEntries.first().identifier
                                            saveRecordingToExternalStorage(id, recording)
                                            toggleButton(deleteButton, false)
                                            toggleButton(generateImagesButton, false)
                                            sharedPreferences.edit().putBoolean(SHARED_DELETE_BUTTON_ENABLED, true).apply()
                                            sharedPreferences.edit().putBoolean(SHARED_GENERATE_IMAGES_BUTTON_ENABLED, true).apply()
                                            deleteButton.isEnabled = true
                                            generateImagesButton.isEnabled = true
                                        },
                                        { error: Throwable ->
                                            val title = "Failed to read recordings"
                                            val message = "Possible reasons are, the recordings were not listed correctly or there was no recording on the device found. \n\n" +
                                                    "Detailed Reason: $error"
                                            Log.e(TAG, "Failed to read recording. Reason: $error")
                                            showDialog(title, message)
                                        }
                                    )
                            } else {
                                val title = "Failed to read recordings"
                                val message = "Possible reasons are, the recordings were not listed correctly or there was no recording on the device found."
                                Log.e(TAG, "Failed to read recordings. Reason: RecordingEntries were empty")
                                showDialog(title, message)
                            }
                        }
                    )
            } else {
                val title = "Please be patient"
                val message = "Listing and Fetching of a recoding is already in progress at the moment."
                Log.d(TAG, "Listing and Fetching of a recording entries is in progress at the moment.")
                showDialog(title, message)
            }
        }

        generateImagesButton.setOnClickListener {
            val intent = Intent(this, ImageActivity::class.java)
            intent.putExtra("id", DEVICE_ID)
            intent.putExtra("participant", PARTICIPANT_NUMBER)
            startActivity(intent)
        }

        loadRecordingButton.setOnClickListener {
            showRecordingDialog()
        }
    }

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
        //Q is 29, S is 31, S_V2 is 32
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

    private fun disableAllButtons() {
        loadRecordingButton.isEnabled = false
        startRecordingButton.isEnabled = false
        stopRecordingButton.isEnabled = false
        checkRecordingStatusButton.isEnabled = false
        generateImagesButton.isEnabled = false
        deleteButton.isEnabled = false
        listRecordingsButton.isEnabled = false
        toggleButton(loadRecordingButton, true)
        toggleButton(listRecordingsButton, true)
        toggleButton(deleteButton, true)
        toggleButton(startRecordingButton, true)
        toggleButton(stopRecordingButton, true)
        toggleButton(checkRecordingStatusButton, true)
        toggleButton(generateImagesButton, true)
    }

    private fun enableButtons() {
        if (sharedPreferences.getBoolean(SHARED_START_BUTTON_ENABLED, true)) {
            startRecordingButton.isEnabled = true
            toggleButton(startRecordingButton, false)
        }
        if (sharedPreferences.getBoolean(SHARED_STOP_BUTTON_ENABLED, false)) {
            stopRecordingButton.isEnabled = true
            toggleButton(stopRecordingButton, false)
        }
        if (sharedPreferences.getBoolean(SHARED_FETCH_BUTTON_ENABLED, false)) {
            listRecordingsButton.isEnabled = true
            toggleButton(listRecordingsButton, false)
            if (sharedPreferences.getBoolean(SHARED_DELETE_BUTTON_ENABLED, false)) {
                deleteButton.isEnabled = true
                toggleButton(deleteButton, false)
            }
        }
        if (sharedPreferences.getBoolean(SHARED_GENERATE_IMAGES_BUTTON_ENABLED, false)) {
            generateImagesButton.isEnabled = true
            toggleButton(generateImagesButton, false)
        }
        loadRecordingButton.isEnabled = true
        toggleButton(loadRecordingButton, false)
        checkRecordingStatusButton.isEnabled = true
        toggleButton(checkRecordingStatusButton, false)
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

    private fun showRecordingDialog() {
        val recordings = listFiles()
        if (recordings != null) {
            val alertDialogBuilder = AlertDialog.Builder(this, R.style.PolarTheme)
            alertDialogBuilder.setTitle("Choose the recording you want to load")

            val mAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, recordings)
            alertDialogBuilder.setAdapter(mAdapter) { _, which ->
                val pickedItem: String = mAdapter.getItem(which).toString()
                val loadResult = loadRecordingFromExternalStorage(pickedItem)
                val title = "Recording loaded"
                val message = "The retrieved samples are:\n"
                AlertDialog.Builder(this, R.style.PolarTheme)
                    .setTitle(title)
                    .setMessage(message + loadResult)
                    .setPositiveButton("OK") { _, _ ->
                        val nextDialog = AlertDialog.Builder(this, R.style.PolarTheme)
                        nextDialog.setTitle("Do you wish to delete the recording?")
                        nextDialog.setPositiveButton("Yes") { _: DialogInterface?, _: Int ->
                            val removeResult = removeRecordingFromExternalStorage(pickedItem)
                            if (removeResult) {
                                Log.d(TAG, "File deleted")
                            } else {
                                showDialog("Error occurred", "File was not deleted")
                            }
                        }
                        nextDialog.setNegativeButton("No") { dialogInterface: DialogInterface, _: Int -> dialogInterface.dismiss() }
                        nextDialog.show()
                    }
                    .show()
            }
            alertDialogBuilder.setNegativeButton("Cancel") { dialogInterface: DialogInterface, _: Int -> dialogInterface.dismiss() }
            alertDialogBuilder.show()
            } else {
                val title = "No recording found"
                val message = "The supplied recording id was wrong or there is no recording with this id."
                showDialog(title, message)
        }
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

    private fun loadRecordingFromExternalStorage(recordingName: String): String? {
        try {
            if (!checkPermission()) {
                requestPermission()
            }
            val myInputStream = FileInputStream(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString() + File.separatorChar + recordingName)
            val size: Int = myInputStream.available()
            val buffer = ByteArray(size)
            myInputStream.read(buffer)
            val myOutput = String(buffer)
            myInputStream.close()
            Log.d(TAG, "File loaded")
            return myOutput
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e(TAG, "File not found or permission denied")
        }
        return null
    }

    private fun saveRecordingToExternalStorage(recordingName: String, recording: MutableList<Int>): Boolean {
        if (!checkPermission()) {
            requestPermission()
        }
        //Is the Android sdk version 29 or higher, we need MediaStore
        val outputStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, recordingName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = this.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                this.contentResolver.openOutputStream(uri)
            } else {
                null
            }
        } else {
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), recordingName)
            FileOutputStream(file)
        }
        if (outputStream != null) {
            try {
                val itr = recording.listIterator()
                val p = PrintWriter(outputStream)
                while(itr.hasNext()){
                    p.println(itr.next())
                }
                p.close()
                Log.d(TAG, "File written")
                return true
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e(TAG, "Error while writing to file or permission denied")
            }
        } else {
            Log.e(TAG, "Output stream is null")
        }
        return false
    }

    private fun removeRecordingFromExternalStorage(recordingName: String): Boolean {
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString() + File.separatorChar + recordingName)
        try {
            if (file.isFile) {
                return file.delete()
            } else {
                Log.e(TAG, "File does not exist")
                return false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Error while deleting file or permission denied")
        }
        return false
    }

    private fun listFiles(): List<Any>? {
        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString()
        val directory = File(path)
        val files = directory.listFiles()?.filter {
            it.isFile
            it.extension.contains("txt")
            it.name.contains("P1")
        }
        if (files != null) {
            Log.d(TAG, "Found " + files.size + " files")
            val labels: MutableList<String> = mutableListOf()
            for (i in files.indices) {
                labels.add(files[i].name)
                Log.d(TAG, "FileName:" + files[i].name)
            }
            return labels
        } else {
            Log.d(TAG,"No files in target Directory")
            return null
        }
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.setData(
                    Uri.parse(
                        String.format(
                            "package:%s",
                            applicationContext.packageName
                        )
                    )
                )
                startActivityForResult(intent, 2296)
            } catch (e: java.lang.Exception) {
                val intent = Intent()
                intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivityForResult(intent, 2296)
            }
        } else {
            //below android 11
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ), PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun checkPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        } else {
            val result =
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            val result1 =
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return result == PackageManager.PERMISSION_GRANTED && result1 == PackageManager.PERMISSION_GRANTED
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        api.shutDown()
    }
}