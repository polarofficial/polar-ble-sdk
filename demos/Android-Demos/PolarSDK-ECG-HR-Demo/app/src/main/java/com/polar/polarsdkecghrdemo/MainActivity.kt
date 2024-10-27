package com.polar.polarsdkecghrdemo

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.util.Pair
import com.polar.androidcommunications.api.ble.model.DisInfo
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
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// The main purpose of the app is currently only data collection.
class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "Polar_MainActivity"
        private const val PARTICIPANT_NUMBER = 1
        private const val PERMISSION_REQUEST_CODE = 1
        private const val SHARED_LAST_ACTION = "last_action"
        private const val SHARED_RECORDING_ID = "recording_id"
        private const val SHARED_START_TIME = "start_time"
        private const val SHARED_END_TIME = "end_time"
        private const val SHARED_START_BUTTON_ENABLED = "start_button_enabled"
        private const val SHARED_STOP_BUTTON_ENABLED = "stop_button_enabled"
        private const val SHARED_SAVE_BUTTON_ENABLED = "save_button_enabled"
        private const val SHARED_REMOVE_BUTTON_ENABLED = "remove_button_enabled"
        private const val SHARED_WELCOME_SCREEN = "welcome_screen"
    }

    // Attention!! Replace this field with the device ID from your device!
    private val deviceId = "89D62721"

    // By lazy makes the api a singleton.
    private val api: PolarBleApi by lazy {
        PolarBleApiDefaultImpl.defaultImplementation(
            applicationContext,
            setOf(
                PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO
            )
        )
    }

    private var recordingStartStopDisposable: Disposable? = null
    private var recordingStatusReadDisposable: Disposable? = null
    private var saveExerciseDisposable: Disposable? = null
    private var removeRecordingDisposable: Disposable? = null

    // It is called entries, but on the H10 there is only one saved recording possible.
    private var recordingEntries: MutableList<PolarExerciseEntry> = mutableListOf()

    private lateinit var welcomeView: TextView
    private lateinit var startRecordingButton: Button
    private lateinit var stopRecordingButton: Button
    private lateinit var checkRecordingStatusButton: Button
    private lateinit var saveRecordingButton: Button
    private lateinit var removeRecordingButton: Button

    private lateinit var sharedPreferences: SharedPreferences

    private val bluetoothOnActivityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            result: ActivityResult ->
                if (result.resultCode == Activity.RESULT_OK) {
                    Log.w(TAG, "ActivityResult: Bluetooth was turned on")
                } else {
                    Log.w(TAG, "ActivityResult: Bluetooth still turned off")
                }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "MainActivity started, version: " + PolarBleApiDefaultImpl.versionInfo())

        // getPreferences returns one preferences file without name for only one activity.
        sharedPreferences = getPreferences(MODE_PRIVATE)

        // Initialize buttons and text view, then disable all buttons.
        welcomeView = findViewById(R.id.welcomeDisplay)
        startRecordingButton = findViewById(R.id.buttonStartRecording)
        stopRecordingButton = findViewById(R.id.buttonStopRecording)
        checkRecordingStatusButton = findViewById(R.id.buttonCheckRecordingStatus)
        removeRecordingButton = findViewById(R.id.buttonRemoveRecording)
        saveRecordingButton = findViewById(R.id.buttonSaveRecording)
        disableAllButtonsVisually()

        // Display the welcome message on every app start until user hits the "I understand" option.
        if (sharedPreferences.getBoolean(SHARED_WELCOME_SCREEN, true)) {
            val title = "Welcome!"
            val message = "Make sure Bluetooth is turned on and you gave the necessary permission to use Bluetooth.\n\n" +
                    "On older devices the location services are needed to search for nearby devices, but we will never actually use or save your location.\n" +
                    "If the app requested the permission to use location services, you need to turn bluetooth off and then on again.\n\n" +
                    "If any unexpected problems occur or if you have some questions, please contact us."
            AlertDialog.Builder(this, R.style.PolarTheme)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("I understand") { _ , _ ->
                    sharedPreferences.edit().putBoolean(SHARED_WELCOME_SCREEN, false).apply()
                }
                .setNegativeButton("Remind me") { _, _ ->
                    // No action needed here.
                }
                .show()
        }

        // Request Bluetooth permissions if needed, check status and ask to switch Bluetooth on.
        checkBT()

        // Configure Polar API and try to connect device.
        api.setApiCallback(object : PolarBleApiCallback() {
            override fun blePowerStateChanged(powered: Boolean) {
                Log.d(TAG, "BLE power: $powered")
                if (powered) {
                    val startText = "You are ready to go, put the sensor on and wait for connection.\n\n" +
                            "If not nothing happens, turn Bluetooth on/off and wait\n" +
                            "or close and open the app again with Bluetooth and GPS on."
                    welcomeView.text = startText
                } else {
                    disableAllButtonsVisually()
                    val startText = "Please ensure bluetooth is on."
                    welcomeView.text = startText
                }
            }

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "CONNECTED: ${polarDeviceInfo.deviceId}")
                val connectedText = "Device connected\n\n" +
                        "Last Action: " + sharedPreferences.getString(SHARED_LAST_ACTION, "")
                welcomeView.text = connectedText
                enableButtonsVisually()
                /* Problem: Buttons will be enabled before bleSdkFeatures are ready.
                This might result in a PolarNotificationNotEnabled, if user is impatient.
                However, the problem does not break anything and is thus temporarily ignored.
                Writing enableButtons() into bleSdkFeatureReady throws, but is caught somewhere:
                "android.view.ViewRootImpl$CalledFromWrongThreadException:
                Only the original thread that created a view hierarchy can touch its views."
                Solution: Thread.sleep(2000) in enableButtons, this is not satisfying but works. */
            }

            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "CONNECTING: ${polarDeviceInfo.deviceId}")
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "DISCONNECTED: ${polarDeviceInfo.deviceId}")
                disableAllButtonsVisually()
                val disconnectedText = "Device disconnected."
                welcomeView.text = disconnectedText
            }

            override fun bleSdkFeatureReady(identifier: String, feature: PolarBleApi.PolarBleSdkFeature) {
                Log.d(TAG, "Feature ready $feature")
            }

            override fun disInformationReceived(identifier: String, disInfo: DisInfo) {
                // DIS = Device Information Service
                if (disInfo.key == "00002a28-0000-1000-8000-00805f9b34fb") {
                    val value = disInfo.value.trim {it <=' '}
                    Log.d(TAG, "Firmware of device with id $identifier is $value")
                }
            }

            override fun batteryLevelReceived(identifier: String, level: Int) {
                // Until now it was always 100%, questionable if the returned number is correct.
                Log.d(TAG, "Battery level of device with id $identifier: $level%")
            }
        })

        try {
            api.connectToDevice(deviceId)
        } catch (a: PolarInvalidArgument) {
            a.printStackTrace()
        }

        startRecordingButton.setOnClickListener {
            val isDisposed = recordingStartStopDisposable?.isDisposed?: true
            // Check if the api is already busy, if yes queue requests or throw an error dialog.
            if (isDisposed) {

                // Deactivate the buttons to prevent multiple requests and change the button text.
                disableAllButtons()
                val workingText = "Processing..."
                startRecordingButton.text = workingText

                // Get the start time of the recording and construct the recording id.
                val calendar = Calendar.getInstance()
                val date = calendar.time
                val newFormat = SimpleDateFormat("dd-MM-yyyy'T'HH-mm-ss-SSS", Locale.GERMANY)
                val stringDate = newFormat.format(date)
                val recordIdentifier = "P$PARTICIPANT_NUMBER-$stringDate"

                // Sample Time will be ignored if SampleType is RR, which is the case.
                recordingStartStopDisposable =
                    api.startRecording(deviceId,
                        recordIdentifier,
                        PolarH10OfflineExerciseApi.RecordingInterval.INTERVAL_1S,
                        PolarH10OfflineExerciseApi.SampleType.RR)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doFinally {
                        // Activate the other buttons again and reset button text.
                        val normalText = resources.getString(R.string.start_recording_button)
                        startRecordingButton.text = normalText
                        enableButtonsVisually()
                    }
                    .subscribe(
                        {
                            // Save the starting time as unix timestamp into shared preferences.
                            val unixTimestamp = System.currentTimeMillis()
                            sharedPreferences.edit().putLong(SHARED_START_TIME, unixTimestamp).apply()
                            Log.d(TAG, "Recording started with id: \"$recordIdentifier\"")

                            // Save the current action as last action and update welcomeView.
                            val stringDateWithPoints = stringDate.replace("-", ":")
                            val recordingStartOk = "Recording was started at ${stringDateWithPoints.substring(11, 19)}."
                            welcomeView.text = recordingStartOk
                            sharedPreferences.edit().putString(SHARED_LAST_ACTION, recordingStartOk).apply()

                            // Save the recording id into shared preferences to get the recording later from device.
                            sharedPreferences.edit().putString(SHARED_RECORDING_ID, recordIdentifier).apply()

                            // Save the button states afterwards.
                            sharedPreferences.edit().putBoolean(SHARED_START_BUTTON_ENABLED, false).apply()
                            sharedPreferences.edit().putBoolean(SHARED_STOP_BUTTON_ENABLED, true).apply()
                            sharedPreferences.edit().putBoolean(SHARED_SAVE_BUTTON_ENABLED, true).apply()
                            toggleButton(startRecordingButton, true)
                        },
                        { error: Throwable ->
                            val title = "Recording start failed"
                            val message = "Possible reasons are, the recording has already started or there is already a saved recording. " +
                                    "The sensor can only have one saved recording at the time.\n\n" +
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
            val isDisposed = recordingStartStopDisposable?.isDisposed?: true
            if (isDisposed) {
                // Deactivate the buttons to prevent multiple requests and change the button text.
                disableAllButtons()
                val workingText = "Processing..."
                stopRecordingButton.text = workingText

                recordingStartStopDisposable = api.stopRecording(deviceId)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doFinally {
                        // Activate the other buttons again and reset button text.
                        val normalText = resources.getString(R.string.stop_recording_button)
                        stopRecordingButton.text = normalText
                        enableButtonsVisually()
                    }
                    .subscribe(
                        {
                            // Save the current action as last action, update welcomeView and save end time.
                            val calendar = Calendar.getInstance()
                            val date = calendar.time
                            val newFormat = SimpleDateFormat("HH:mm:ss:SSS", Locale.GERMANY)
                            val stringTime = newFormat.format(date)
                            val recordingStopOk = "Recording was stopped at " + stringTime.substring(0, 8) + "."
                            welcomeView.text = recordingStopOk
                            Log.d(TAG, "Recording stopped at $stringTime.")
                            sharedPreferences.edit().putString(SHARED_LAST_ACTION, recordingStopOk).apply()
                            sharedPreferences.edit().putString(SHARED_END_TIME, stringTime).apply()

                            // Save the button states afterwards.
                            sharedPreferences.edit().putBoolean(SHARED_STOP_BUTTON_ENABLED, false).apply()
                            sharedPreferences.edit().putBoolean(SHARED_SAVE_BUTTON_ENABLED, true).apply()
                            toggleButton(stopRecordingButton, true)

                        },
                        { error: Throwable ->
                            val title = "Recording stop failed"
                            val message = "Possible reason is that the recording was already stopped.\n\n" +
                                    "In most cases the recording has already stopped, since the sensor was taken off for longer than 30 seconds.\n\n" +
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
            val isDisposed = recordingStatusReadDisposable?.isDisposed?: true
            if (isDisposed) {
                // Deactivate the buttons to prevent multiple requests and change the button text.
                disableAllButtons()
                val workingText = "Processing..."
                checkRecordingStatusButton.text = workingText

                recordingStatusReadDisposable = api.requestRecordingStatus(deviceId)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doFinally {
                        // Activate the other buttons again and reset button text.
                        val normalText = resources.getString(R.string.check_recording_status_button)
                        checkRecordingStatusButton.text = normalText
                        toggleButton(checkRecordingStatusButton, false)
                        enableButtons()
                    }
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
                                // This state is undefined, there cannot be a recording without id.
                                "H10 Recording state UNDEFINED, please contact us!"
                            } else {
                                // This state is unreachable and should never happen.
                                "H10 recording state ERROR, please contact us!"
                            }
                            Log.d(TAG, recordingStatus)
                            showToast(recordingStatus)
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

        saveRecordingButton.setOnClickListener {
            val isDisposed = saveExerciseDisposable?.isDisposed?: true
            if (isDisposed) {
                // Ask user if the recording was already stopped and how.
                val titleDialog = "Just to be sure..."
                val messageDialog = "Have you stopped the recording using the \"Stop Recording\" button or maybe accidentally by taking off the sensor?\n\n" +
                        "Otherwise, the app can become stuck for a while until it throws an error, which then requires an app restart or switching on/off Bluetooth."
                AlertDialog.Builder(this, R.style.PolarTheme)
                    .setTitle(titleDialog)
                    .setMessage(messageDialog)
                    .setPositiveButton("Yes") { _, _ ->
                        // If there was no recording found or recording id was deleted by clearing the cache, abort.
                        if (getRecordingPath()) {
                            // Deactivate the buttons to prevent multiple requests and change the button text.
                            disableAllButtons()
                            val workingText = "Processing..."
                            saveRecordingButton.text = workingText

                            Log.d(TAG, "Fetching of recording with id: ${recordingEntries.first().identifier}.")
                            saveExerciseDisposable = api.fetchExercise(deviceId, recordingEntries.first())
                                .observeOn(AndroidSchedulers.mainThread())
                                .doFinally {
                                    // Activate the other buttons again and reset button text.
                                    val normalText = resources.getString(R.string.save_recording_button)
                                    saveRecordingButton.text = normalText
                                    enableButtonsVisually()
                                }
                                .subscribe(
                                    { polarExerciseData: PolarExerciseData ->
                                        // Save recording and calculate timestamps.
                                        val recording = mutableListOf<Int>()
                                        val timestamps = mutableListOf<Long>()
                                        var unixTimestamp = sharedPreferences.getLong(SHARED_START_TIME, 0)
                                        for (i in polarExerciseData.hrSamples.indices) {
                                            val currentSample = polarExerciseData.hrSamples[i]
                                            recording.add(currentSample)
                                            unixTimestamp += currentSample
                                            timestamps.add(unixTimestamp)
                                        }

                                        // Add the end time to the recording name before writing it to external storage.
                                        val id = if (sharedPreferences.contains(SHARED_END_TIME)) {
                                            val endTime = sharedPreferences.getString(SHARED_END_TIME, "")
                                            recordingEntries.first().identifier + "--" + endTime?.replace(":", "-")
                                        } else {
                                            // For the future the last 30 seconds could be deleted if the sensor was taken off.
                                            Log.d(TAG, "End time not found and last result appended to id.")
                                            val calculatedEndTime = Date(unixTimestamp)
                                            val newFormat = SimpleDateFormat("HH-mm-ss-SSS", Locale.GERMANY)
                                            val stringDate = newFormat.format(calculatedEndTime)
                                            recordingEntries.first().identifier + "--" + stringDate
                                        }
                                        Thread {
                                            saveRecordingToExternalStorage(id, recording, timestamps)
                                            Log.d(TAG, "Recording with id: ${recordingEntries.first().identifier} saved.")
                                        }.start()

                                        // Save the button status afterwards
                                        sharedPreferences.edit().putBoolean(SHARED_SAVE_BUTTON_ENABLED, false).apply()
                                        sharedPreferences.edit().putBoolean(SHARED_STOP_BUTTON_ENABLED, false).apply()
                                        sharedPreferences.edit().putBoolean(SHARED_REMOVE_BUTTON_ENABLED, true).apply()
                                        toggleButton(saveRecordingButton, true)

                                        // Remove start and end time afterwards.
                                        sharedPreferences.edit().remove(SHARED_START_TIME).apply()
                                        sharedPreferences.edit().remove(SHARED_END_TIME).apply()

                                        // Update welcomeView
                                        val recordingSavedOk = "Recording saved to device."
                                        welcomeView.text = recordingSavedOk
                                        sharedPreferences.edit().putString(SHARED_LAST_ACTION, recordingSavedOk).apply()
                                    },
                                    { error: Throwable ->
                                        val title = "Failed to read recordings"
                                        val message = "Possible reasons are, there was no recording on the device found or you cleared the app data.\n\n" +
                                                    "Note: Use this button only after hitting stop recording or if you have accidentally taken off the sensor!\n\n" +
                                                    "Detailed Reason: $error"
                                        Log.e(TAG, "Failed to read recording. Reason: $error")
                                        showDialog(title, message)
                                    }
                                )
                        }
                    }
                    .setNegativeButton("No") { _, _ ->
                        // No action needed here.
                    }
                    .show()
            } else {
                val title = "Please be patient"
                val message = "Fetching of a recoding is already in progress at the moment."
                Log.d(TAG, "Fetching of a recoding is in progress at the moment.")
                showDialog(title, message)
            }
        }

        removeRecordingButton.setOnClickListener {
            val isDisposed = removeRecordingDisposable?.isDisposed?: true
            if (isDisposed) {
                // Deactivate the buttons to prevent multiple requests and change the button text.
                disableAllButtons()
                val workingText = "Processing..."
                removeRecordingButton.text = workingText

                // Check if the name of the recording is saved, else retrieve the name.
                val recordingEntry = recordingEntries.firstOrNull()?: run {
                    if (getRecordingPath()) {
                        recordingEntries.first()
                    } else {
                        // Activate the other buttons again and reset button text before return.
                        val normalText = resources.getString(R.string.remove_recording_button)
                        removeRecordingButton.text = normalText
                        enableButtonsVisually()
                        return@setOnClickListener
                    }
                }

                removeRecordingDisposable = api.removeExercise(deviceId, recordingEntry)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doFinally {
                        // Activate the other buttons again and reset button text.
                        val normalText = resources.getString(R.string.remove_recording_button)
                        removeRecordingButton.text = normalText
                        enableButtonsVisually()
                    }
                    .subscribe(
                        {
                            // Save button status afterwards.
                            sharedPreferences.edit().putBoolean(SHARED_REMOVE_BUTTON_ENABLED, false).apply()
                            sharedPreferences.edit().putBoolean(SHARED_START_BUTTON_ENABLED, true).apply()
                            toggleButton(removeRecordingButton, true)

                            // Update welcomeView.
                            val recordingRemovedOk = "Recording successfully removed from device."
                            welcomeView.text = recordingRemovedOk
                            sharedPreferences.edit().putString(SHARED_LAST_ACTION, recordingRemovedOk).apply()

                            // Clear recording data.
                            recordingEntries.clear()
                            sharedPreferences.edit().remove(SHARED_RECORDING_ID).apply()
                            Log.d(TAG, "Recording with id: ${recordingEntry.identifier} successfully removed.")
                        },
                        { error: Throwable ->
                            val title = "Removal of recording failed"
                            val message = "Possible reasons are, the recording was already deleted or an internal bug appeared, please contact us.\n\n" +
                                    "Detailed Reason: $error"
                            Log.e(TAG, "Removal of recording: ${recordingEntry.identifier} failed. Reason: $error")
                            showDialog(title, message)
                        }
                    )
            } else {
                val title = "Please be patient"
                val message = "Removing of a recording is already in progress."
                Log.d(TAG, "Removing of a recording is already in progress.")
                showDialog(title, message)
            }
        }
    }

    private fun checkBT() {
        val btManager = applicationContext.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = btManager.adapter
        if (bluetoothAdapter == null) {
            showToast("Your Device doesn't support Bluetooth.")
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            bluetoothOnActivityResultLauncher.launch(enableBtIntent)
        }
        //Q is build version 29 (Android 10), R is 30 (Android 11), S is 31 (Android 12), S_V2 is 32
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), PERMISSION_REQUEST_CODE)
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

    private fun disableAllButtonsVisually() {
        startRecordingButton.isEnabled = false
        stopRecordingButton.isEnabled = false
        saveRecordingButton.isEnabled = false
        checkRecordingStatusButton.isEnabled = false
        removeRecordingButton.isEnabled = false
        toggleButton(startRecordingButton, true)
        toggleButton(stopRecordingButton, true)
        toggleButton(saveRecordingButton, true)
        toggleButton(checkRecordingStatusButton, true)
        toggleButton(removeRecordingButton, true)
    }

    private fun disableAllButtons() {
        startRecordingButton.isEnabled = false
        stopRecordingButton.isEnabled = false
        saveRecordingButton.isEnabled = false
        checkRecordingStatusButton.isEnabled = false
        removeRecordingButton.isEnabled = false
    }

    private fun enableButtonsVisually() {
        // Thread sleeps two seconds to give the sensor more time to start the features.
        Thread.sleep(2000)

        // Activate unlocked buttons and toggle them visually.
        if (sharedPreferences.getBoolean(SHARED_START_BUTTON_ENABLED, true)) {
            startRecordingButton.isEnabled = true
            toggleButton(startRecordingButton, false)
        }
        if (sharedPreferences.getBoolean(SHARED_STOP_BUTTON_ENABLED, false)) {
            stopRecordingButton.isEnabled = true
            toggleButton(stopRecordingButton, false)
        }
        if (sharedPreferences.getBoolean(SHARED_SAVE_BUTTON_ENABLED, false)) {
            saveRecordingButton.isEnabled = true
            toggleButton(saveRecordingButton, false)
        }
        if (sharedPreferences.getBoolean(SHARED_REMOVE_BUTTON_ENABLED, false)) {
            removeRecordingButton.isEnabled = true
            toggleButton(removeRecordingButton, false)
        }
        checkRecordingStatusButton.isEnabled = true
        toggleButton(checkRecordingStatusButton, false)
    }

    private fun enableButtons() {
        // Activate unlocked buttons internally.
        if (sharedPreferences.getBoolean(SHARED_START_BUTTON_ENABLED, true)) {
            startRecordingButton.isEnabled = true
        }
        if (sharedPreferences.getBoolean(SHARED_STOP_BUTTON_ENABLED, false)) {
            stopRecordingButton.isEnabled = true
        }
        if (sharedPreferences.getBoolean(SHARED_SAVE_BUTTON_ENABLED, false)) {
            saveRecordingButton.isEnabled = true
        }
        if (sharedPreferences.getBoolean(SHARED_REMOVE_BUTTON_ENABLED, false)) {
            removeRecordingButton.isEnabled = true
        }
        checkRecordingStatusButton.isEnabled = true
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

    private fun getRecordingPath(): Boolean {
        if (sharedPreferences.contains(SHARED_RECORDING_ID)) {
            // Shared preferences for strings can be null but only if set in the default value.
            val identifier = sharedPreferences.getString(SHARED_RECORDING_ID, "")?: ""
            // Clear old recording entries, which might be still there if app wasn't closed in between.
            recordingEntries.clear()
            // Save the device recording path.
            val date = Calendar.getInstance().time
            val recordingEntry = PolarExerciseEntry("/$identifier/SAMPLES.BPB", date, identifier)
            recordingEntries.add(recordingEntry)
            return true
        } else {
            // This case should not happen, if the app data was deleted, the user is stuck.
            val titleError = "An error occurred"
            val messageError = "Sorry, it seems like you cannot save or remove the recording, since you probably cleared the data of the app.\n\n" +
                    "Please inform us."
            Log.e(TAG, "Removing or saving of the recording failed. Reason: No recording found or data lost.")
            showDialog(titleError, messageError)
            return false
        }
    }


    private fun saveRecordingToExternalStorage(recordingName: String, recording: MutableList<Int>, timestamps: MutableList<Long>) {
        // Is the Android sdk version 29 or higher, the usage of the MediaStore is necessary
        val outputStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, recordingName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
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
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "$recordingName.csv"
            )
            FileOutputStream(file)
        }
        if (outputStream != null) {
            try {
                val recordingIterator = recording.listIterator()
                val timestampIterator = timestamps.listIterator()
                val p = PrintWriter(outputStream)
                p.println("RR-Intervals,Timestamps (End Time)")
                while(recordingIterator.hasNext()){
                    p.println(recordingIterator.next().toString() + "," +  timestampIterator.next().toString())
                }
                p.close()
                Log.d(TAG, "File written.")
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e(TAG, "Error while writing to file or permission denied.")
            }
        } else {
            // This case should not be reached.
            Log.e(TAG, "Output stream is null.")
        }
    }

    private fun showDialog(title: String, message: String) {
        AlertDialog.Builder(this, R.style.PolarTheme)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { _, _ ->
                // No action needed here.
            }
            .show()
    }

    // Toasts are incredibly faster than dialogs and thus probably better for performance.
    private fun showToast(message: String) {
        val toast = Toast.makeText(applicationContext, message, Toast.LENGTH_LONG)
        toast.show()
    }

    public override fun onDestroy() {
        super.onDestroy()
        api.shutDown()
    }
}