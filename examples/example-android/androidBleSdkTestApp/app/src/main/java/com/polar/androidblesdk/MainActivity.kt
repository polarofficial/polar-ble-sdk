package com.polar.androidblesdk

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.util.Pair
import com.google.android.material.snackbar.Snackbar
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApi.DeviceStreamingFeature
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.*

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Function
import java.util.*

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val API_LOGGER_TAG = "API LOGGER"
        private const val PERMISSION_REQUEST_CODE = 1
    }

    // ATTENTION! Replace with the device ID from your device.
    private var deviceId = "8C4CAD2D"

    private val api: PolarBleApi by lazy {
        // Notice PolarBleApi.ALL_FEATURES are enabled
        PolarBleApiDefaultImpl.defaultImplementation(applicationContext, PolarBleApi.ALL_FEATURES)
    }
    private lateinit var broadcastDisposable: Disposable
    private var scanDisposable: Disposable? = null
    private var autoConnectDisposable: Disposable? = null
    private var ecgDisposable: Disposable? = null
    private var accDisposable: Disposable? = null
    private var gyrDisposable: Disposable? = null
    private var magDisposable: Disposable? = null
    private var ppgDisposable: Disposable? = null
    private var ppiDisposable: Disposable? = null
    private var sdkModeEnableDisposable: Disposable? = null
    private var recordingStartStopDisposable: Disposable? = null
    private var recordingStatusReadDisposable: Disposable? = null
    private var listExercisesDisposable: Disposable? = null
    private var fetchExerciseDisposable: Disposable? = null
    private var removeExerciseDisposable: Disposable? = null

    private var sdkModeEnabledStatus = false
    private var deviceConnected = false
    private var bluetoothEnabled = false
    private var exerciseEntries: MutableList<PolarExerciseEntry> = mutableListOf()

    private lateinit var broadcastButton: Button
    private lateinit var connectButton: Button
    private lateinit var autoConnectButton: Button
    private lateinit var scanButton: Button
    private lateinit var ecgButton: Button
    private lateinit var accButton: Button
    private lateinit var gyrButton: Button
    private lateinit var magButton: Button
    private lateinit var ppgButton: Button
    private lateinit var ppiButton: Button
    private lateinit var listExercisesButton: Button
    private lateinit var fetchExerciseButton: Button
    private lateinit var removeExerciseButton: Button
    private lateinit var startH10RecordingButton: Button
    private lateinit var stopH10RecordingButton: Button
    private lateinit var readH10RecordingStatusButton: Button
    private lateinit var setTimeButton: Button
    private lateinit var toggleSdkModeButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "version: " + PolarBleApiDefaultImpl.versionInfo())
        broadcastButton = findViewById(R.id.broadcast_button)
        connectButton = findViewById(R.id.connect_button)
        autoConnectButton = findViewById(R.id.auto_connect_button)
        scanButton = findViewById(R.id.scan_button)
        ecgButton = findViewById(R.id.ecg_button)
        accButton = findViewById(R.id.acc_button)
        gyrButton = findViewById(R.id.gyr_button)
        magButton = findViewById(R.id.mag_button)
        ppgButton = findViewById(R.id.ohr_ppg_button)
        ppiButton = findViewById(R.id.ohr_ppi_button)
        listExercisesButton = findViewById(R.id.list_exercises)
        fetchExerciseButton = findViewById(R.id.read_exercise)
        removeExerciseButton = findViewById(R.id.remove_exercise)
        startH10RecordingButton = findViewById(R.id.start_h10_recording)
        stopH10RecordingButton = findViewById(R.id.stop_h10_recording)
        readH10RecordingStatusButton = findViewById(R.id.h10_recording_status)
        setTimeButton = findViewById(R.id.set_time)
        toggleSdkModeButton = findViewById(R.id.toggle_SDK_mode)

        api.setPolarFilter(false)
        api.setApiLogger { s: String -> Log.d(API_LOGGER_TAG, s) }
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
                Log.d(TAG, "CONNECTED: " + polarDeviceInfo.deviceId)
                deviceId = polarDeviceInfo.deviceId
                deviceConnected = true
                val buttonText = getString(R.string.disconnect_from_device, deviceId)
                toggleButtonDown(connectButton, buttonText)
            }

            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "CONNECTING: " + polarDeviceInfo.deviceId)
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "DISCONNECTED: " + polarDeviceInfo.deviceId)
                deviceConnected = false
                val buttonText = getString(R.string.connect_to_device, deviceId)
                toggleButtonUp(connectButton, buttonText)
                toggleButtonUp(toggleSdkModeButton, R.string.enable_sdk_mode)
            }

            override fun streamingFeaturesReady(
                identifier: String, features: Set<DeviceStreamingFeature>
            ) {
                for (feature in features) {
                    Log.d(TAG, "Streaming feature $feature is ready")
                }
            }

            override fun hrFeatureReady(identifier: String) {
                Log.d(TAG, "HR READY: $identifier")
                // hr notifications are about to start
            }

            override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
                Log.d(TAG, "uuid: $uuid value: $value")
            }

            override fun batteryLevelReceived(identifier: String, level: Int) {
                Log.d(TAG, "BATTERY LEVEL: $level")
            }

            override fun hrNotificationReceived(identifier: String, data: PolarHrData) {
                Log.d(TAG, "HR value: ${data.hr} rrsMs: ${data.rrsMs} rr: ${data.rrs} contact: ${data.contactStatus} , ${data.contactStatusSupported}")
            }

            override fun polarFtpFeatureReady(s: String) {
                Log.d(TAG, "FTP ready")
            }
        })

        broadcastButton.setOnClickListener {
            if (!this::broadcastDisposable.isInitialized || broadcastDisposable.isDisposed) {
                toggleButtonDown(broadcastButton, R.string.listening_broadcast)
                broadcastDisposable = api.startListenForPolarHrBroadcasts(null)
                    .subscribe(
                        { polarBroadcastData: PolarHrBroadcastData ->
                            Log.d(TAG, "HR BROADCAST ${polarBroadcastData.polarDeviceInfo.deviceId} HR: ${polarBroadcastData.hr} batt: ${polarBroadcastData.batteryStatus}")
                        },
                        { error: Throwable ->
                            toggleButtonUp(broadcastButton, R.string.listen_broadcast)
                            Log.e(TAG, "Broadcast listener failed. Reason $error")
                        },
                        { Log.d(TAG, "complete") }
                    )
            } else {
                toggleButtonUp(broadcastButton, R.string.listen_broadcast)
                broadcastDisposable.dispose()
            }
        }

        connectButton.text = getString(R.string.connect_to_device, deviceId)
        connectButton.setOnClickListener {
            try {
                if (deviceConnected) {
                    api.disconnectFromDevice(deviceId)
                } else {
                    api.connectToDevice(deviceId)
                }
            } catch (polarInvalidArgument: PolarInvalidArgument) {
                val attempt = if (deviceConnected) {
                    "disconnect"
                } else {
                    "connect"
                }
                Log.e(TAG, "Failed to $attempt. Reason $polarInvalidArgument ")
            }
        }

        autoConnectButton.setOnClickListener {
            if (autoConnectDisposable != null) {
                autoConnectDisposable?.dispose()
            }
            autoConnectDisposable = api.autoConnectToDevice(-60, "180D", null)
                .subscribe(
                    { Log.d(TAG, "auto connect search complete") },
                    { throwable: Throwable -> Log.e(TAG, "" + throwable.toString()) }
                )
        }

        scanButton.setOnClickListener {
            val isDisposed = scanDisposable?.isDisposed ?: true
            if (isDisposed) {
                toggleButtonDown(scanButton, R.string.scanning_devices)
                scanDisposable = api.searchForDevice()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { polarDeviceInfo: PolarDeviceInfo ->
                            Log.d(TAG, "polar device found id: " + polarDeviceInfo.deviceId + " address: " + polarDeviceInfo.address + " rssi: " + polarDeviceInfo.rssi + " name: " + polarDeviceInfo.name + " isConnectable: " + polarDeviceInfo.isConnectable)
                        },
                        { error: Throwable ->
                            toggleButtonUp(scanButton, "Scan devices")
                            Log.e(TAG, "Device scan failed. Reason $error")
                        },
                        {
                            toggleButtonUp(scanButton, "Scan devices")
                            Log.d(TAG, "complete")
                        }
                    )
            } else {
                toggleButtonUp(scanButton, "Scan devices")
                scanDisposable?.dispose()
            }
        }

        ecgButton.setOnClickListener {
            val isDisposed = ecgDisposable?.isDisposed ?: true
            if (isDisposed) {
                toggleButtonDown(ecgButton, R.string.stop_ecg_stream)
                ecgDisposable = requestStreamSettings(deviceId, DeviceStreamingFeature.ECG)
                    .flatMap { settings: PolarSensorSetting ->
                        api.startEcgStreaming(deviceId, settings)
                    }
                    .subscribe(
                        { polarEcgData: PolarEcgData ->
                            for (microVolts in polarEcgData.samples) {
                                Log.d(TAG, "    yV: $microVolts")
                            }
                        },
                        { error: Throwable ->
                            toggleButtonUp(ecgButton, R.string.start_ecg_stream)
                            Log.e(TAG, "ECG stream failed. Reason $error")
                        },
                        { Log.d(TAG, "ECG stream complete") }
                    )
            } else {
                toggleButtonUp(ecgButton, R.string.start_ecg_stream)
                // NOTE stops streaming if it is "running"
                ecgDisposable?.dispose()
            }
        }

        accButton.setOnClickListener {
            val isDisposed = accDisposable?.isDisposed ?: true
            if (isDisposed) {
                toggleButtonDown(accButton, R.string.stop_acc_stream)
                accDisposable = requestStreamSettings(deviceId, DeviceStreamingFeature.ACC)
                    .flatMap { settings: PolarSensorSetting ->
                        api.startAccStreaming(deviceId, settings)
                    }
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { polarAccelerometerData: PolarAccelerometerData ->
                            for (data in polarAccelerometerData.samples) {
                                Log.d(TAG, "ACC    x: ${data.x} y:  ${data.y} z: ${data.z}")
                            }
                        },
                        { error: Throwable ->
                            toggleButtonUp(accButton, R.string.start_acc_stream)
                            Log.e(TAG, "ACC stream failed. Reason $error")
                        },
                        {
                            showToast("ACC stream complete")
                            Log.d(TAG, "ACC stream complete")
                        }
                    )
            } else {
                toggleButtonUp(accButton, R.string.start_acc_stream)
                // NOTE dispose will stop streaming if it is "running"
                accDisposable?.dispose()
            }
        }

        gyrButton.setOnClickListener {
            val isDisposed = gyrDisposable?.isDisposed ?: true
            if (isDisposed) {
                toggleButtonDown(gyrButton, R.string.stop_gyro_stream)
                gyrDisposable =
                    requestStreamSettings(deviceId, DeviceStreamingFeature.GYRO)
                        .flatMap { settings: PolarSensorSetting ->
                            api.startGyroStreaming(deviceId, settings)
                        }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                            { polarGyroData: PolarGyroData ->
                                for (data in polarGyroData.samples) {
                                    Log.d(TAG, "GYR    x: ${data.x} y:  ${data.y} z: ${data.z}")
                                }
                            },
                            { error: Throwable ->
                                toggleButtonUp(gyrButton, R.string.start_gyro_stream)
                                Log.e(TAG, "GYR stream failed. Reason $error")
                            },
                            { Log.d(TAG, "GYR stream complete") }
                        )
            } else {
                toggleButtonUp(gyrButton, R.string.start_gyro_stream)
                // NOTE dispose will stop streaming if it is "running"
                gyrDisposable?.dispose()
            }
        }

        magButton.setOnClickListener {
            val isDisposed = magDisposable?.isDisposed ?: true
            if (isDisposed) {
                toggleButtonDown(magButton, R.string.stop_mag_stream)
                magDisposable =
                    requestStreamSettings(deviceId, DeviceStreamingFeature.MAGNETOMETER)
                        .flatMap { settings: PolarSensorSetting ->
                            api.startMagnetometerStreaming(deviceId, settings)
                        }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                            { polarMagData: PolarMagnetometerData ->
                                for (data in polarMagData.samples) {
                                    Log.d(TAG, "MAG    x: ${data.x} y:  ${data.y} z: ${data.z}")
                                }
                            },
                            { error: Throwable ->
                                toggleButtonUp(magButton, R.string.start_mag_stream)
                                Log.e(TAG, "MAGNETOMETER stream failed. Reason $error")
                            },
                            { Log.d(TAG, "MAGNETOMETER stream complete") }
                        )
            } else {
                toggleButtonUp(magButton, R.string.start_mag_stream)
                // NOTE dispose will stop streaming if it is "running"
                magDisposable!!.dispose()
            }
        }

        ppgButton.setOnClickListener {
            val isDisposed = ppgDisposable?.isDisposed ?: true
            if (isDisposed) {
                toggleButtonDown(ppgButton, R.string.stop_ppg_stream)
                ppgDisposable =
                    requestStreamSettings(deviceId, DeviceStreamingFeature.PPG)
                        .flatMap { settings: PolarSensorSetting ->
                            api.startOhrStreaming(deviceId, settings)
                        }
                        .subscribe(
                            { polarOhrPPGData: PolarOhrData ->
                                if (polarOhrPPGData.type == PolarOhrData.OHR_DATA_TYPE.PPG3_AMBIENT1) {
                                    for (data in polarOhrPPGData.samples) {
                                        Log.d(TAG, "PPG    ppg0: ${data.channelSamples[0]} ppg1: ${data.channelSamples[1]} ppg2: ${data.channelSamples[2]} ambient: ${data.channelSamples[3]}")
                                    }
                                }
                            },
                            { error: Throwable ->
                                toggleButtonUp(ppgButton, R.string.start_ppg_stream)
                                Log.e(TAG, "PPG stream failed. Reason $error")
                            },
                            { Log.d(TAG, "PPG stream complete") }
                        )
            } else {
                toggleButtonUp(ppgButton, R.string.start_ppg_stream)
                // NOTE dispose will stop streaming if it is "running"
                ppgDisposable?.dispose()
            }
        }

        ppiButton.setOnClickListener {
            val isDisposed = ppiDisposable?.isDisposed ?: true
            if (isDisposed) {
                toggleButtonDown(ppiButton, R.string.stop_ppi_stream)
                ppiDisposable = api.startOhrPPIStreaming(deviceId)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { ppiData: PolarOhrPPIData ->
                            for (sample in ppiData.samples) {
                                Log.d(TAG, "PPI    ppi: ${sample.ppi} blocker: ${sample.blockerBit} errorEstimate: ${sample.errorEstimate}")
                            }
                        },
                        { error: Throwable ->
                            toggleButtonUp(ppiButton, R.string.start_ppi_stream)
                            Log.e(TAG, "PPI stream failed. Reason $error")
                        },
                        { Log.d(TAG, "PPI stream complete") }
                    )
            } else {
                toggleButtonUp(ppiButton, R.string.start_ppi_stream)
                // NOTE dispose will stop streaming if it is "running"
                ppiDisposable?.dispose()
            }
        }

        listExercisesButton.setOnClickListener {
            val isDisposed = listExercisesDisposable?.isDisposed ?: true
            if (isDisposed) {
                exerciseEntries.clear()
                listExercisesDisposable = api.listExercises(deviceId)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { polarExerciseEntry: PolarExerciseEntry ->
                            Log.d(TAG, "next: ${polarExerciseEntry.date} path: ${polarExerciseEntry.path} id: ${polarExerciseEntry.identifier}")
                            exerciseEntries.add(polarExerciseEntry)
                        },
                        { error: Throwable ->
                            val errorDescription = "Failed to list exercises. Reason: $error"
                            Log.w(TAG, errorDescription)
                            showSnackbar(errorDescription)
                        },
                        {
                            val completedOk = "Exercise listing completed. Listed ${exerciseEntries.count()} exercises on device $deviceId."
                            Log.d(TAG, completedOk)
                            showSnackbar(completedOk)
                        }
                    )
            } else {
                Log.d(TAG, "Listing of exercise entries is in progress at the moment.")
            }
        }

        fetchExerciseButton.setOnClickListener {
            val isDisposed = fetchExerciseDisposable?.isDisposed ?: true
            if (isDisposed) {
                if (exerciseEntries.isNotEmpty()) {
                    // just for the example purpose read the entry which is first on the exerciseEntries list
                    fetchExerciseDisposable = api.fetchExercise(deviceId, exerciseEntries.first())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                            { polarExerciseData: PolarExerciseData ->
                                Log.d(TAG, "Exercise data count: ${polarExerciseData.hrSamples.size} samples: ${polarExerciseData.hrSamples}")
                                var onComplete = "Exercise has ${polarExerciseData.hrSamples.size} hr samples.\n\n"
                                if (polarExerciseData.hrSamples.size >= 3)
                                    onComplete += "HR data {${polarExerciseData.hrSamples[0]}, ${polarExerciseData.hrSamples[1]}, ${polarExerciseData.hrSamples[2]} ...}"
                                showDialog("Exercise data read", onComplete)
                            },
                            { error: Throwable ->
                                val errorDescription = "Failed to read exercise. Reason: $error"
                                Log.e(TAG, errorDescription)
                                showSnackbar(errorDescription)
                            }
                        )
                } else {
                    val helpTitle = "Reading exercise is not possible"
                    val helpMessage = "Either device has no exercise entries or you haven't list them yet. Please, create an exercise or use the \"LIST EXERCISES\" " +
                            "button to list exercises on device."
                    showDialog(helpTitle, helpMessage)
                }
            } else {
                Log.d(TAG, "Reading of exercise is in progress at the moment.")
            }
        }

        removeExerciseButton.setOnClickListener {
            val isDisposed = removeExerciseDisposable?.isDisposed ?: true
            if (isDisposed) {
                if (exerciseEntries.isNotEmpty()) {
                    // just for the example purpose remove the entry which is first on the exerciseEntries list
                    val entry = exerciseEntries.first()
                    removeExerciseDisposable = api.removeExercise(deviceId, entry)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                            {
                                exerciseEntries.remove(entry)
                                val exerciseRemovedOk = "Exercise with id:${entry.identifier} successfully removed"
                                Log.d(TAG, exerciseRemovedOk)
                                showSnackbar(exerciseRemovedOk)
                            },
                            { error: Throwable ->
                                val exerciseRemoveFailed = "Exercise with id:${entry.identifier} remove failed: $error"
                                Log.w(TAG, exerciseRemoveFailed)
                                showSnackbar(exerciseRemoveFailed)
                            }
                        )
                } else {
                    val helpTitle = "Removing exercise is not possible"
                    val helpMessage = "Either device has no exercise entries or you haven't list them yet. Please, create an exercise or use the \"LIST EXERCISES\" button to list exercises on device"
                    showDialog(helpTitle, helpMessage)
                }
            } else {
                Log.d(TAG, "Removing of exercise is in progress at the moment.")
            }
        }

        startH10RecordingButton.setOnClickListener {
            val isDisposed = recordingStartStopDisposable?.isDisposed ?: true
            if (isDisposed) {
                val recordIdentifier = "TEST_APP_ID"
                recordingStartStopDisposable = api.startRecording(deviceId, recordIdentifier, PolarBleApi.RecordingInterval.INTERVAL_1S, PolarBleApi.SampleType.HR)
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

        stopH10RecordingButton.setOnClickListener {
            val isDisposed = recordingStartStopDisposable?.isDisposed ?: true
            if (isDisposed) {
                recordingStartStopDisposable = api.stopRecording(deviceId)
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

        readH10RecordingStatusButton.setOnClickListener {
            val isDisposed = recordingStatusReadDisposable?.isDisposed ?: true
            if (isDisposed) {
                recordingStatusReadDisposable = api.requestRecordingStatus(deviceId)
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

        setTimeButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            calendar.time = Date()
            api.setLocalTime(deviceId, calendar)
                .subscribe(
                    { Log.d(TAG, "time ${calendar.time} set to device") },
                    { error: Throwable -> Log.e(TAG, "set time failed: $error") }
                )
        }

        toggleSdkModeButton.setOnClickListener {
            toggleSdkModeButton.isEnabled = false
            if (!sdkModeEnabledStatus) {
                sdkModeEnableDisposable = api.enableSDKMode(deviceId)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        {
                            Log.d(TAG, "SDK mode enabled")
                            // at this point dispose all existing streams. SDK mode enable command
                            // stops all the streams but client is not informed. This is workaround
                            // for the bug.
                            disposeAllStreams()
                            toggleSdkModeButton.isEnabled = true
                            sdkModeEnabledStatus = true
                            toggleButtonDown(toggleSdkModeButton, R.string.disable_sdk_mode)
                        },
                        { error ->
                            toggleSdkModeButton.isEnabled = true
                            val errorString = "SDK mode enable failed: $error"
                            showToast(errorString)
                            Log.e(TAG, errorString)
                        }
                    )
            } else {
                sdkModeEnableDisposable = api.disableSDKMode(deviceId)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        {
                            Log.d(TAG, "SDK mode disabled")
                            toggleSdkModeButton.isEnabled = true
                            sdkModeEnabledStatus = false
                            toggleButtonUp(toggleSdkModeButton, R.string.enable_sdk_mode)
                        },
                        { error ->
                            toggleSdkModeButton.isEnabled = true
                            val errorString = "SDK mode disable failed: $error"
                            showToast(errorString)
                            Log.e(TAG, errorString)
                        }
                    )
            }
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
                    disableAllButtons()
                    Log.w(TAG, "No sufficient permissions")
                    showToast("No sufficient permissions")
                    return
                }
            }
            Log.d(TAG, "Needed permissions are granted")
            enableAllButtons()
        }
    }

    public override fun onPause() {
        super.onPause()
    }

    public override fun onResume() {
        super.onResume()
        api.foregroundEntered()
    }

    public override fun onDestroy() {
        super.onDestroy()
        api.shutDown()
    }

    private fun toggleButtonDown(button: Button, text: String? = null) {
        toggleButton(button, true, text)
    }

    private fun toggleButtonDown(button: Button, @StringRes resourceId: Int) {
        toggleButton(button, true, getString(resourceId))
    }

    private fun toggleButtonUp(button: Button, text: String? = null) {
        toggleButton(button, false, text)
    }

    private fun toggleButtonUp(button: Button, @StringRes resourceId: Int) {
        toggleButton(button, false, getString(resourceId))
    }

    private fun toggleButton(button: Button, isDown: Boolean, text: String? = null) {
        if (text != null) button.text = text

        var buttonDrawable = button.background
        buttonDrawable = DrawableCompat.wrap(buttonDrawable!!)
        if (isDown) {
            DrawableCompat.setTint(buttonDrawable, resources.getColor(R.color.primaryDarkColor))
        } else {
            DrawableCompat.setTint(buttonDrawable, resources.getColor(R.color.primaryColor))
        }
        button.background = buttonDrawable
    }

    private fun requestStreamSettings(identifier: String, feature: DeviceStreamingFeature): Flowable<PolarSensorSetting> {

        val availableSettings = api.requestStreamSettings(identifier, feature)
            .observeOn(AndroidSchedulers.mainThread())
            .onErrorReturn { error: Throwable ->
                val errorString = "Settings are not available for feature $feature. REASON: $error"
                Log.w(TAG, errorString)
                showToast(errorString)
                PolarSensorSetting(emptyMap())
            }
        val allSettings = api.requestFullStreamSettings(identifier, feature)
            .onErrorReturn { error: Throwable ->
                Log.w(
                    TAG,
                    "Full stream settings are not available for feature $feature. REASON: $error"
                )
                PolarSensorSetting(emptyMap())
            }
        return Single.zip(availableSettings, allSettings) { available: PolarSensorSetting, all: PolarSensorSetting ->
            if (available.settings.isEmpty()) {
                throw Throwable("Settings are not available")
            } else {
                Log.d(TAG, "Feature " + feature + " available settings " + available.settings)
                Log.d(TAG, "Feature " + feature + " all settings " + all.settings)
                return@zip android.util.Pair(available, all)
            }
        }
            .observeOn(AndroidSchedulers.mainThread())
            .toFlowable()
            .flatMap(
                Function { sensorSettings: android.util.Pair<PolarSensorSetting, PolarSensorSetting> ->
                    DialogUtility.showAllSettingsDialog(
                        this@MainActivity,
                        sensorSettings.first.settings,
                        sensorSettings.second.settings
                    ).toFlowable()
                } as Function<android.util.Pair<PolarSensorSetting, PolarSensorSetting>, Flowable<PolarSensorSetting>>
            )
    }

    private fun showToast(message: String) {
        val toast = Toast.makeText(applicationContext, message, Toast.LENGTH_LONG)
        toast.show()

    }

    private fun showSnackbar(message: String) {
        val contextView = findViewById<View>(R.id.buttons_container)
        Snackbar.make(contextView, message, Snackbar.LENGTH_LONG)
            .show()
    }

    private fun showDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { _, _ ->
                // Respond to positive button press
            }
            .show()
    }

    private fun disableAllButtons() {
        broadcastButton.isEnabled = false
        connectButton.isEnabled = false
        autoConnectButton.isEnabled = false
        scanButton.isEnabled = false
        ecgButton.isEnabled = false
        accButton.isEnabled = false
        gyrButton.isEnabled = false
        magButton.isEnabled = false
        ppgButton.isEnabled = false
        ppiButton.isEnabled = false
        listExercisesButton.isEnabled = false
        fetchExerciseButton.isEnabled = false
        removeExerciseButton.isEnabled = false
        startH10RecordingButton.isEnabled = false
        stopH10RecordingButton.isEnabled = false
        readH10RecordingStatusButton.isEnabled = false
        setTimeButton.isEnabled = false
        toggleSdkModeButton.isEnabled = false
    }

    private fun enableAllButtons() {
        broadcastButton.isEnabled = true
        connectButton.isEnabled = true
        autoConnectButton.isEnabled = true
        scanButton.isEnabled = true
        ecgButton.isEnabled = true
        accButton.isEnabled = true
        gyrButton.isEnabled = true
        magButton.isEnabled = true
        ppgButton.isEnabled = true
        ppiButton.isEnabled = true
        listExercisesButton.isEnabled = true
        fetchExerciseButton.isEnabled = true
        removeExerciseButton.isEnabled = true
        startH10RecordingButton.isEnabled = true
        stopH10RecordingButton.isEnabled = true
        readH10RecordingStatusButton.isEnabled = true
        setTimeButton.isEnabled = true
        toggleSdkModeButton.isEnabled = true
    }

    private fun disposeAllStreams() {
        ecgDisposable?.dispose()
        accDisposable?.dispose()
        gyrDisposable?.dispose()
        magDisposable?.dispose()
        ppgDisposable?.dispose()
        ppgDisposable?.dispose()
    }
}