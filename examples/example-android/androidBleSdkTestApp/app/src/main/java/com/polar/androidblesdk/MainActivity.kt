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
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.PolarH10OfflineExerciseApi
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.*
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import java.util.*

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val API_LOGGER_TAG = "API LOGGER"
        private const val PERMISSION_REQUEST_CODE = 1
    }

    // ATTENTION! Replace with the device ID from your device.
    private var deviceId = "BC15022D"

    private val api: PolarBleApi by lazy {
        // Notice all features are enabled
        PolarBleApiDefaultImpl.defaultImplementation(
            applicationContext,
            setOf(
                PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE,
                PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP,
                PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_LED_ANIMATION
            )
        )
    }
    private lateinit var broadcastDisposable: Disposable
    private var scanDisposable: Disposable? = null
    private var autoConnectDisposable: Disposable? = null
    private var hrDisposable: Disposable? = null
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
    private lateinit var hrButton: Button
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
    private lateinit var getTimeButton: Button
    private lateinit var toggleSdkModeButton: Button
    private lateinit var getDiskSpaceButton: Button
    private lateinit var changeSdkModeLedAnimationStatusButton: Button
    private lateinit var changePpiModeLedAnimationStatusButton: Button
    private lateinit var doFactoryResetButton: Button

    //Verity Sense offline recording use
    private lateinit var listRecordingsButton: Button
    private lateinit var startRecordingButton: Button
    private lateinit var stopRecordingButton: Button
    private lateinit var downloadRecordingButton: Button
    private lateinit var deleteRecordingButton: Button
    private val entryCache: MutableMap<String, MutableList<PolarOfflineRecordingEntry>> = mutableMapOf()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "version: " + PolarBleApiDefaultImpl.versionInfo())
        broadcastButton = findViewById(R.id.broadcast_button)
        connectButton = findViewById(R.id.connect_button)
        autoConnectButton = findViewById(R.id.auto_connect_button)
        scanButton = findViewById(R.id.scan_button)
        hrButton = findViewById(R.id.hr_button)
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
        getTimeButton = findViewById(R.id.get_time)
        toggleSdkModeButton = findViewById(R.id.toggle_SDK_mode)
        getDiskSpaceButton = findViewById(R.id.get_disk_space)
        changeSdkModeLedAnimationStatusButton = findViewById(R.id.change_sdk_mode_led_animation_status)
        changePpiModeLedAnimationStatusButton = findViewById(R.id.change_ppi_mode_led_animation_status)
        doFactoryResetButton = findViewById(R.id.do_factory_reset)

        //Verity Sense recording buttons
        listRecordingsButton = findViewById(R.id.list_recordings)
        startRecordingButton = findViewById(R.id.start_recording)
        stopRecordingButton = findViewById(R.id.stop_recording)
        downloadRecordingButton = findViewById(R.id.download_recording)
        deleteRecordingButton = findViewById(R.id.delete_recording)

        api.setPolarFilter(false)

        // If there is need to log what is happening inside the SDK, it can be enabled like this:
        val enableSdkLogs = false
        if(enableSdkLogs) {
            api.setApiLogger { s: String -> Log.d(API_LOGGER_TAG, s) }
        }

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
                Log.d(TAG, "CONNECTED: ${polarDeviceInfo.deviceId}")
                deviceId = polarDeviceInfo.deviceId
                deviceConnected = true
                val buttonText = getString(R.string.disconnect_from_device, deviceId)
                toggleButtonDown(connectButton, buttonText)
            }

            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "CONNECTING: ${polarDeviceInfo.deviceId}")
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "DISCONNECTED: ${polarDeviceInfo.deviceId}")
                deviceConnected = false
                val buttonText = getString(R.string.connect_to_device, deviceId)
                toggleButtonUp(connectButton, buttonText)
                toggleButtonUp(toggleSdkModeButton, R.string.enable_sdk_mode)
            }

            override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
                Log.d(TAG, "DIS INFO uuid: $uuid value: $value")
            }

            override fun batteryLevelReceived(identifier: String, level: Int) {
                Log.d(TAG, "BATTERY LEVEL: $level")
            }

            override fun hrNotificationReceived(identifier: String, data: PolarHrData.PolarHrSample) {
                // deprecated
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

        hrButton.setOnClickListener {
            val isDisposed = hrDisposable?.isDisposed ?: true
            if (isDisposed) {
                toggleButtonDown(hrButton, R.string.stop_hr_stream)
                hrDisposable = api.startHrStreaming(deviceId)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { hrData: PolarHrData ->
                            for (sample in hrData.samples) {
                                Log.d(TAG, "HR     bpm: ${sample.hr} rrs: ${sample.rrsMs} rrAvailable: ${sample.rrAvailable} contactStatus: ${sample.contactStatus} contactStatusSupported: ${sample.contactStatusSupported}")
                            }
                        },
                        { error: Throwable ->
                            toggleButtonUp(hrButton, R.string.start_hr_stream)
                            Log.e(TAG, "HR stream failed. Reason $error")
                        },
                        { Log.d(TAG, "HR stream complete") }
                    )
            } else {
                toggleButtonUp(hrButton, R.string.start_hr_stream)
                // NOTE dispose will stop streaming if it is "running"
                hrDisposable?.dispose()
            }
        }

        ecgButton.setOnClickListener {
            val isDisposed = ecgDisposable?.isDisposed ?: true
            if (isDisposed) {
                toggleButtonDown(ecgButton, R.string.stop_ecg_stream)
                ecgDisposable = requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.ECG)
                    .flatMap { settings: PolarSensorSetting ->
                        api.startEcgStreaming(deviceId, settings)
                    }
                    .subscribe(
                        { polarEcgData: PolarEcgData ->
                            for (data in polarEcgData.samples) {
                                Log.d(TAG, "    yV: ${data.voltage} timeStamp: ${data.timeStamp}")
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
                accDisposable = requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.ACC)
                    .flatMap { settings: PolarSensorSetting ->
                        api.startAccStreaming(deviceId, settings)
                    }
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { polarAccelerometerData: PolarAccelerometerData ->
                            for (data in polarAccelerometerData.samples) {
                                Log.d(TAG, "ACC    x: ${data.x} y: ${data.y} z: ${data.z} timeStamp: ${data.timeStamp}")
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
                    requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.GYRO)
                        .flatMap { settings: PolarSensorSetting ->
                            api.startGyroStreaming(deviceId, settings)
                        }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                            { polarGyroData: PolarGyroData ->
                                for (data in polarGyroData.samples) {
                                    Log.d(TAG, "GYR    x: ${data.x} y: ${data.y} z: ${data.z} timeStamp: ${data.timeStamp}")
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
                    requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.MAGNETOMETER)
                        .flatMap { settings: PolarSensorSetting ->
                            api.startMagnetometerStreaming(deviceId, settings)
                        }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                            { polarMagData: PolarMagnetometerData ->
                                for (data in polarMagData.samples) {
                                    Log.d(TAG, "MAG    x: ${data.x} y: ${data.y} z: ${data.z} timeStamp: ${data.timeStamp}")
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
                    requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.PPG)
                        .flatMap { settings: PolarSensorSetting ->
                            api.startPpgStreaming(deviceId, settings)
                        }
                        .subscribe(
                            { polarPpgData: PolarPpgData ->
                                if (polarPpgData.type == PolarPpgData.PpgDataType.PPG3_AMBIENT1) {
                                    for (data in polarPpgData.samples) {
                                        Log.d(TAG, "PPG    ppg0: ${data.channelSamples[0]} ppg1: ${data.channelSamples[1]} ppg2: ${data.channelSamples[2]} ambient: ${data.channelSamples[3]} timeStamp: ${data.timeStamp}")
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
                ppiDisposable = api.startPpiStreaming(deviceId)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { ppiData: PolarPpiData ->
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
                    toggleButtonDown(fetchExerciseButton, R.string.reading_exercise)
                    // just for the example purpose read the entry which is first on the exerciseEntries list
                    fetchExerciseDisposable = api.fetchExercise(deviceId, exerciseEntries.first())
                        .observeOn(AndroidSchedulers.mainThread())
                        .doFinally {
                            toggleButtonUp(fetchExerciseButton, R.string.read_exercise)
                        }
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
                recordingStartStopDisposable = api.startRecording(deviceId, recordIdentifier, PolarH10OfflineExerciseApi.RecordingInterval.INTERVAL_1S, PolarH10OfflineExerciseApi.SampleType.HR)
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
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        val timeSetString = "time ${calendar.time} set to device"
                        Log.d(TAG, timeSetString)
                        showToast(timeSetString)
                    },
                    { error: Throwable -> Log.e(TAG, "set time failed: $error") }
                )
        }

        getTimeButton.setOnClickListener {
            api.getLocalTime(deviceId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { calendar ->
                        val timeGetString = "${calendar.time} read from the device"
                        Log.d(TAG, timeGetString)
                        showToast(timeGetString)

                    },
                    { error: Throwable -> Log.e(TAG, "get time failed: $error") }
                )
        }


        listRecordingsButton.setOnClickListener {
            api.listOfflineRecordings(deviceId)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe {
                    entryCache[deviceId] = mutableListOf()
                }
                .map {
                    entryCache[deviceId]?.add(it)
                    it
                }
                .subscribe(
                    { polarOfflineRecordingEntry: PolarOfflineRecordingEntry ->
                        Log.d(
                            TAG,
                            "next: ${polarOfflineRecordingEntry.date} path: ${polarOfflineRecordingEntry.path} size: ${polarOfflineRecordingEntry.size}"
                        )
                    },
                    { error: Throwable -> Log.e(TAG, "Failed to list recordings: $error") },
                    { Log.d(TAG, "list recordings complete") }
                )
        }

        startRecordingButton.setOnClickListener {
            //Example of starting ACC offline recording
            Log.d(TAG, "Starts ACC recording")
            val settings: MutableMap<PolarSensorSetting.SettingType, Int> = mutableMapOf()
            settings[PolarSensorSetting.SettingType.SAMPLE_RATE] = 52
            settings[PolarSensorSetting.SettingType.RESOLUTION] = 16
            settings[PolarSensorSetting.SettingType.RANGE] = 8
            settings[PolarSensorSetting.SettingType.CHANNELS] = 3
            //Using a secret key managed by your own.
            //  You can use a different key to each start recording calls.
            //  When using key at start recording, it is also needed for the recording download, otherwise could not be decrypted
            val yourSecret = PolarRecordingSecret(
                byteArrayOf(
                    0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09,
                    0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07
                )
            )
            api.startOfflineRecording(deviceId, PolarBleApi.PolarDeviceDataType.ACC, PolarSensorSetting(settings.toMap()), yourSecret)
                //Without a secret key
                //api.startOfflineRecording(deviceId, PolarBleApi.PolarDeviceDataType.ACC, PolarSensorSetting(settings.toMap()))
                .subscribe(
                    { Log.d(TAG, "start offline recording completed") },
                    { throwable: Throwable -> Log.e(TAG, "" + throwable.toString()) }
                )
        }

        stopRecordingButton.setOnClickListener {
            //Example of stopping ACC offline recording
            Log.d(TAG, "Stops ACC recording")
            api.stopOfflineRecording(deviceId, PolarBleApi.PolarDeviceDataType.ACC)
                .subscribe(
                    { Log.d(TAG, "stop offline recording completed") },
                    { throwable: Throwable -> Log.e(TAG, "" + throwable.toString()) }
                )
        }

        downloadRecordingButton.setOnClickListener {
            //Example of one offline recording download
            //NOTE: For this example you need to click on listRecordingsButton to have files entry (entryCache) up to date
            Log.d(TAG, "Searching to recording to download... ")
            //Get first entry for testing download
            val offlineRecEntry = entryCache[deviceId]?.firstOrNull()
            offlineRecEntry?.let { offlineEntry ->
                try {
                    //Using a secret key managed by your own.
                    //  You can use a different key to each start recording calls.
                    //  When using key at start recording, it is also needed for the recording download, otherwise could not be decrypted
                    val yourSecret = PolarRecordingSecret(
                        byteArrayOf(
                            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09,
                            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07
                        )
                    )
                    api.getOfflineRecord(deviceId, offlineEntry, yourSecret)
                        //Not using a secret key
                        //api.getOfflineRecord(deviceId, offlineEntry)
                        .subscribe(
                            {
                                Log.d(TAG, "Recording ${offlineEntry.path} downloaded. Size: ${offlineEntry.size}")
                                when (it) {
                                    is PolarOfflineRecordingData.AccOfflineRecording -> {
                                        Log.d(TAG, "ACC Recording started at ${it.startTime}")
                                        for (sample in it.data.samples) {
                                            Log.d(TAG, "ACC data: time: ${sample.timeStamp} X: ${sample.x} Y: ${sample.y} Z: ${sample.z}")
                                        }
                                    }
//                      is PolarOfflineRecordingData.GyroOfflineRecording -> { }
//                      is PolarOfflineRecordingData.MagOfflineRecording -> { }
//                      ...
                                    else -> {
                                        Log.d(TAG, "Recording type is not yet implemented")
                                    }
                                }
                            },
                            { throwable: Throwable -> Log.e(TAG, "" + throwable.toString()) }
                        )
                } catch (e: Exception) {
                    Log.e(TAG, "Get offline recording fetch failed on entry ...", e)
                }
            }
        }

        deleteRecordingButton.setOnClickListener {
            //Example of one offline recording deletion
            //NOTE: For this example you need to click on listRecordingsButton to have files entry (entryCache) up to date
            Log.d(TAG, "Searching to recording to delete... ")
            //Get first entry for testing deletion
            val offlineRecEntry = entryCache[deviceId]?.firstOrNull()
            offlineRecEntry?.let { offlineEntry ->
                try {
                    api.removeOfflineRecord(deviceId, offlineEntry)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                            {
                                Log.d(TAG, "Recording file deleted")
                            },
                            { error ->
                                val errorString = "Recording file deletion failed: $error"
                                showToast(errorString)
                                Log.e(TAG, errorString)
                            }
                        )

                } catch (e: Exception) {
                    Log.e(TAG, "Delete offline recording failed on entry ...", e)
                }
            }
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

        getDiskSpaceButton.setOnClickListener {
            api.getDiskSpace(deviceId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { diskSpace ->
                        Log.d(TAG, "disk space: $diskSpace")
                        showToast("Disk space left: ${diskSpace.freeSpace}/${diskSpace.totalSpace} Bytes")
                    },
                    { error: Throwable -> Log.e(TAG, "get disk space failed: $error") }
                )
        }

        var enableSdkModelLedAnimation = false
        var enablePpiModeLedAnimation = false
        changeSdkModeLedAnimationStatusButton.setOnClickListener {
            api.setLedConfig(deviceId, LedConfig(
                sdkModeLedEnabled = enableSdkModelLedAnimation,
                ppiModeLedEnabled = !enablePpiModeLedAnimation))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        Log.d(TAG, "SdkModeledAnimationEnabled set to $enableSdkModelLedAnimation")
                        showToast("SdkModeLedAnimationEnabled set to $enableSdkModelLedAnimation")
                        changeSdkModeLedAnimationStatusButton.text =
                            if (enableSdkModelLedAnimation) getString(R.string.disable_sdk_mode_led_animation) else getString(
                                R.string.enable_sdk_mode_led_animation
                            )
                        enableSdkModelLedAnimation = !enableSdkModelLedAnimation
                    },
                    { error: Throwable -> Log.e(TAG, "changeSdkModeLedAnimationStatus failed: $error") }
                )
        }

        changePpiModeLedAnimationStatusButton.setOnClickListener {
            api.setLedConfig(deviceId, LedConfig(
                sdkModeLedEnabled = !enableSdkModelLedAnimation,
                ppiModeLedEnabled = enablePpiModeLedAnimation))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        Log.d(TAG, "PpiModeLedAnimationEnabled set to $enablePpiModeLedAnimation")
                        showToast("PpiModeLedAnimationEnabled set to $enablePpiModeLedAnimation")
                        changePpiModeLedAnimationStatusButton.text =
                            if (enablePpiModeLedAnimation) getString(R.string.disable_ppi_mode_led_animation) else getString(
                                R.string.enable_ppi_mode_led_animation
                            )
                        enablePpiModeLedAnimation = !enablePpiModeLedAnimation
                    },
                    { error: Throwable -> Log.e(TAG, "changePpiModeLedAnimationStatus failed: $error") }
                )
        }

        doFactoryResetButton.setOnClickListener {
            api.doFactoryReset(deviceId, preservePairingInformation = true)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        Log.d(TAG, "send do factory reset to device")
                        showToast("send do factory reset to device")
                    },
                    { error: Throwable -> Log.e(TAG, "doFactoryReset() failed: $error") }
                )
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

    private fun requestStreamSettings(identifier: String, feature: PolarBleApi.PolarDeviceDataType): Flowable<PolarSensorSetting> {
        val availableSettings = api.requestStreamSettings(identifier, feature)
        val allSettings = api.requestFullStreamSettings(identifier, feature)
            .onErrorReturn { error: Throwable ->
                Log.w(TAG, "Full stream settings are not available for feature $feature. REASON: $error")
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
            .flatMap { sensorSettings: android.util.Pair<PolarSensorSetting, PolarSensorSetting> ->
                DialogUtility.showAllSettingsDialog(
                    this@MainActivity,
                    sensorSettings.first.settings,
                    sensorSettings.second.settings
                ).toFlowable()
            }
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
        getTimeButton.isEnabled = false
        toggleSdkModeButton.isEnabled = false
        getDiskSpaceButton.isEnabled = false
        //Verity Sense recording buttons
        listRecordingsButton.isEnabled = false
        startRecordingButton.isEnabled = false
        stopRecordingButton.isEnabled = false
        downloadRecordingButton.isEnabled = false
        deleteRecordingButton.isEnabled = false
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
        getTimeButton.isEnabled = true
        toggleSdkModeButton.isEnabled = true
        getDiskSpaceButton.isEnabled = true
        //Verity Sense recording buttons
        listRecordingsButton.isEnabled = true
        startRecordingButton.isEnabled = true
        stopRecordingButton.isEnabled = true
        downloadRecordingButton.isEnabled = true
        deleteRecordingButton.isEnabled = true
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