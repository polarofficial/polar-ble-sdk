package com.polar.polarsensordatacollector.ui.landing

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.*
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.ui.utils.DialogUtility.showAllSettingsDialog
import com.polar.polarsensordatacollector.ui.utils.showSnackBar
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.model.*
import com.polar.sdk.api.model.PolarSensorSetting.SettingType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.DecimalFormat

@AndroidEntryPoint
class OnlineRecFragment : Fragment(R.layout.fragment_online_rec) {
    companion object {
        private const val TAG = "OnlineRecFragment"
        private const val INTENSITY_SELECTION_ENABLED_ALPHA = 1.0f
        private const val INTENSITY_SELECTION_DISABLED_ALPHA = 0.4f
    }

    private val viewModel: MainViewModel by activityViewModels()
    private val onlineViewModel: OnlineRecordingViewModel by viewModels()

    private lateinit var timer: TextView

    private lateinit var hrStatusAndSettings: View
    private lateinit var accStatusAndSettings: View
    private lateinit var ppgStatusAndSettings: View
    private lateinit var ecgStatusAndSettings: View
    private lateinit var ppiStatusAndSettings: View
    private lateinit var magStatusAndSettings: View
    private lateinit var gyrStatusAndSettings: View
    private lateinit var pressureStatusAndSettings: View
    private lateinit var locationStatusAndSettings: View
    private lateinit var temperatureStatusAndSettings: View
    private lateinit var skinTemperatureStatusAndSettings: View

    private lateinit var recordingStartGroup: ConstraintLayout
    private lateinit var recordingGroup: ConstraintLayout
    private lateinit var streamSettingHeader: TextView
    private lateinit var markerButton: Button
    private lateinit var startRecordingButton: Button

    private lateinit var magRecordingLive: View
    private lateinit var gyrRecordingLive: View
    private lateinit var accRecordingLive: View
    private lateinit var hrRecordingLive: View
    private lateinit var ppgRecordingLive: View
    private lateinit var ecgRecordingLive: View
    private lateinit var ppiRecordingLive: View
    private lateinit var pressureRecordingLive: View
    private lateinit var locRecordingLive: View
    private lateinit var temperatureRecordingLive: View
    private lateinit var skinTemperatureRecordingLive: View

    private var marker = false

    private lateinit var selectedDeviceId: String

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        selectedDeviceId = arguments?.getString(ONLINE_OFFLINE_KEY_DEVICE_ID) ?: throw Exception("OnlineRecFragment has no deviceId")

        setupViews(view)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                onlineViewModel.uiAvailableOnlineStreamDataTypesState.collect {
                    availableFeaturesUpdate(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                onlineViewModel.uiStreamingState.collect {
                    streamingFeatureUpdate(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                onlineViewModel.uiShowError.collect {
                    if (it.header.isNotEmpty()) {
                        showSnackBar(rootView = requireView(), it.header, it.description ?: "", showAsError = true)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                onlineViewModel.uiOnlineRecordingState.collect {
                    onlineRecStateUpdate(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                onlineViewModel.uiHeartRateInfoState.collect {
                    hrNotificationReceived(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                onlineViewModel.shareFiles.collect {
                    shareFilesToUser(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                onlineViewModel.uiOnlineRequestedSettingsState.collect {
                    userSelectsSettings(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                onlineViewModel.uiEcgStreamDataState.collect {
                    ecgDataReceived(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                onlineViewModel.uiAccStreamDataState.collect {
                    accDataReceived(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                onlineViewModel.uiGyroStreamDataState.collect {
                    gyroDataReceived(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                onlineViewModel.uiMagnStreamDataState.collect {
                    magnetometerDataReceived(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                onlineViewModel.uiPpgStreamDataState.collect {
                    ppgDataReceived(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                onlineViewModel.uiPpiStreamDataState.collect {
                    ppiDataReceived(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                onlineViewModel.uiPressureStreamDataState.collect {
                    pressureDataReceived(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                onlineViewModel.uiLocationStreamDataState.collect {
                    locationDataReceived(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                onlineViewModel.uiTemperatureStreamDataState.collect {
                    temperatureDataReceived(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                onlineViewModel.uiSkinTemperatureStreamDataState.collect {
                    skinTemperatureDataReceived(it)
                }
            }
        }
    }

    private fun shareFilesToUser(fileUris: ArrayList<Uri>) {
        if (fileUris.isNotEmpty()) {
            val intent = Intent().apply {
                action = Intent.ACTION_SEND_MULTIPLE
                putExtra(Intent.EXTRA_SUBJECT, "Stream data")
                type = "plain/text"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, fileUris)
            }
            this.startActivity(intent)
            onlineViewModel.fileShareCompleted()
        }
    }

    private fun userSelectsSettings(availableStreamSettingsUiState: OnlineAvailableStreamSettingsUiState?) {
        if (availableStreamSettingsUiState != null &&
            availableStreamSettingsUiState.settings.currentlyAvailable != null &&
            availableStreamSettingsUiState.settings.allPossibleSettings != null
        ) {
            showAllSettingsDialog(
                requireActivity(),
                availableStreamSettingsUiState.settings.currentlyAvailable.settings,
                availableStreamSettingsUiState.settings.allPossibleSettings.settings,
                availableStreamSettingsUiState.settings.selectedSettings
            )
                .toFlowable()
                .doFinally {
                    getOnlineRecSettingsButtonView(availableStreamSettingsUiState.feature)?.isEnabled = true
                }
                .subscribe(
                    { settings: Map<SettingType, Int>? ->
                        Log.d(TAG, "Dialog completed with settings $settings")
                        settings?.let {
                            onlineViewModel.updateSelectedStreamSettings(availableStreamSettingsUiState.feature, it)
                            recordingLiveSettingsView(getLiveSectionView(availableStreamSettingsUiState.feature), settings)
                        }
                    }, { error: Throwable ->
                        val settingsSelectionFailed = "Error while selecting settings for feature: ${availableStreamSettingsUiState.feature} error: $error"
                        Log.e(TAG, settingsSelectionFailed)
                        showToast(settingsSelectionFailed)
                    })
        }
    }

    private fun askStreamSettingsFromUser(identifier: String, feature: PolarDeviceDataType) {
        getOnlineRecSettingsButtonView(feature)?.isEnabled = false
        onlineViewModel.requestStreamSettings(deviceId = identifier, feature = feature)
    }

    private fun setupViews(view: View) {
        streamSettingHeader = view.findViewById(R.id.stream_settings_header)

        hrStatusAndSettings = view.findViewById(R.id.hr_online_recording_setup)
        // Hr do not have settings
        hrStatusAndSettings.findViewById<Button>(R.id.recording_controls_settings_button)?.visibility = GONE

        accStatusAndSettings = view.findViewById(R.id.acc_online_recording_setup)
        ppgStatusAndSettings = view.findViewById(R.id.ppg_online_recording_setup)
        ecgStatusAndSettings = view.findViewById(R.id.ecg_online_recording_setup)
        ppiStatusAndSettings = view.findViewById(R.id.ppi_online_recording_setup)
        // PPI do not have settings
        ppiStatusAndSettings.findViewById<Button>(R.id.recording_controls_settings_button)?.visibility = GONE

        magStatusAndSettings = view.findViewById(R.id.mag_online_recording_setup)
        gyrStatusAndSettings = view.findViewById(R.id.gyr_online_recording_setup)
        pressureStatusAndSettings = view.findViewById(R.id.pressure_online_recording_setup)
        locationStatusAndSettings = view.findViewById(R.id.location_online_recording_setup)
        temperatureStatusAndSettings = view.findViewById(R.id.temperature_online_recording_setup)
        skinTemperatureStatusAndSettings = view.findViewById(R.id.skin_temperature_online_recording_setup)

        recordingStartGroup = view.findViewById(R.id.stream_recording_start_group)
        startRecordingButton = view.findViewById(R.id.recording_button)

        recordingGroup = view.findViewById(R.id.stream_recording_group)
        markerButton = view.findViewById(R.id.marker_button)
        timer = view.findViewById(R.id.timer)
        gyrRecordingLive = view.findViewById(R.id.gyro_data_section)
        magRecordingLive = view.findViewById(R.id.magnetometer_data_section)
        accRecordingLive = view.findViewById(R.id.acc_data_section)
        hrRecordingLive = view.findViewById(R.id.hr_data_section)
        ppgRecordingLive = view.findViewById(R.id.ppg_data_section)
        ecgRecordingLive = view.findViewById(R.id.ecg_data_section)
        ppiRecordingLive = view.findViewById(R.id.ppi_data_section)
        pressureRecordingLive = view.findViewById(R.id.pressure_data_section)
        locRecordingLive = view.findViewById(R.id.loc_data_section)
        temperatureRecordingLive = view.findViewById(R.id.temperature_data_section)
        skinTemperatureRecordingLive = view.findViewById(R.id.skin_temperature_data_section)
    }

    private fun startStreams(): Boolean {
        val recordingsToStart = mutableListOf<PolarDeviceDataType>()
        for (feature in PolarDeviceDataType.values()) {
            val cb = getOnlineRecordingCheckBox(feature)
            if (cb.isChecked) {
                recordingsToStart.add(feature)
            }
        }

        return if (recordingsToStart.isNotEmpty()) {
            for (feature in recordingsToStart) {
                onlineViewModel.startStream(feature)
                getOnlineRecordingCheckBox(feature).setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                    if (isChecked) {
                        onlineViewModel.startStream(feature)
                    } else {
                        onlineViewModel.stopStream(feature)
                    }
                }
            }
            true
        } else {
            false
        }
    }

    private fun streamingFeatureUpdate(streamingFeatureUiState: LiveRecordingUiState) {
        val isStreamRecordingOn = streamingFeatureUiState.streamingRecordingState.any { it.value.state == StreamingFeatureState.STATES.RECORDING }
        if (isStreamRecordingOn) {
            startRecordingButton.setText(R.string.stop_recording)
            startRecordingButton.setOnClickListener {
                viewModel.selectedDevice?.let {
                    for (feature in PolarDeviceDataType.values()) {
                        val cb = getOnlineRecordingCheckBox(feature)
                        if (cb.isChecked) {
                            onlineViewModel.stopStream(feature)
                        }
                        streamingFeatureSettingsToggle(feature, true)
                        cb.isEnabled = true
                    }
                } ?: run {
                    showToast("No device selected")
                }
            }
        } else {
            startRecordingButton.setText(R.string.start_recording)
            startRecordingButton.setOnClickListener {
                viewModel.selectedDevice?.let {
                    val isRecordingStarted = startStreams()
                    if (!isRecordingStarted) {
                        showToast("No data stream selected")
                    }
                } ?: run {
                    showToast("No device selected")
                }
            }
        }

        if (isStreamRecordingOn) {
            markerButton.isEnabled = true
            markerButton.setOnClickListener {
                viewModel.selectedDevice?.let {
                    marker = !marker
                    onlineViewModel.addMarkerToLog(marker)
                    markerButton.text = if (marker) "MARKER STOP" else "MARKER START"
                }

            }
        } else {
            markerButton.isEnabled = false
        }

        if (isStreamRecordingOn) {
            streamingFeatureUiState.streamingRecordingState
                .filter { it.value.state == StreamingFeatureState.STATES.STOPPED }
                .map {
                    streamingFeatureCheckBoxDisable(it.key)
                    setupStreamLiveStopped(it.key)
                    streamingFeatureSettingsToggle(it.key, false)
                }

            streamingFeatureUiState.streamingRecordingState
                .filter { it.value.state == StreamingFeatureState.STATES.PAUSED }
                .map {
                    streamingFeatureCheckBoxEnable(it.key)
                    setupStreamLivePaused(it.key)
                    streamingFeatureSettingsToggle(it.key, false)
                }

            streamingFeatureUiState.streamingRecordingState
                .filter { it.value.state == StreamingFeatureState.STATES.RECORDING }
                .map {
                    streamingFeatureCheckBoxEnable(it.key)
                    setupStreamLiveRecording(it.key, it.value.settings)
                    streamingFeatureSettingsToggle(it.key, false)
                }
        }
    }

    private fun showToast(message: String) {
        val toast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
        toast.show()
    }

    private fun availableFeaturesUpdate(streamingFeatureUiState: AvailableOnlineStreamDataState) {
        if (streamingFeatureUiState.streamingFeaturesAvailable.any { it.value == true }) {
            recordingStartGroup.visibility = VISIBLE
            recordingGroup.visibility = VISIBLE
            streamSettingHeader.visibility = VISIBLE
            streamingFeatureUiState.streamingFeaturesAvailable
                .map {
                    if (it.value) {
                        setupOnlineRecStatusAndSettingsView(it.key, makeVisible = true)
                    } else {
                        setupOnlineRecStatusAndSettingsView(it.key, makeVisible = false)
                    }
                }
        } else {
            recordingStartGroup.visibility = GONE
            recordingGroup.visibility = GONE
            streamSettingHeader.visibility = GONE
        }
    }

    private fun onlineRecStateUpdate(onlineRecordingUiState: OnlineRecordingUiState) {
        if (onlineRecordingUiState.timer.isNotEmpty()) {
            timer.text = onlineRecordingUiState.timer
            timer.visibility = VISIBLE
        } else {
            timer.text = ""
            timer.visibility = GONE
        }
    }

    private fun streamingFeatureSettingsToggle(feature: PolarDeviceDataType, enabled: Boolean) {
        val settingsButton = getOnlineRecSettingsButtonView(feature)
        settingsButton?.isEnabled = enabled
    }

    private fun setupOnlineRecSettings(feature: PolarDeviceDataType) {
        val settingsButton = getOnlineRecSettingsButtonView(feature)
        settingsButton?.isEnabled = true
        settingsButton?.setOnClickListener {
            askStreamSettingsFromUser(selectedDeviceId, feature)
        }
    }

    private fun streamingFeatureCheckBoxEnable(feature: PolarDeviceDataType) {
        val cb = getOnlineRecordingCheckBox(feature)
        cb.visibility = VISIBLE
        cb.isEnabled = true
    }

    private fun streamingFeatureCheckBoxDisable(feature: PolarDeviceDataType) {
        val cb = getOnlineRecordingCheckBox(feature)
        cb.isEnabled = false
    }

    private fun hrNotificationReceived(heartRateInformationUiState: HeartRateInformationUiState) {
        Log.d(TAG, "HR notification received. ID: ${heartRateInformationUiState.deviceId} HR: ${heartRateInformationUiState.heartRate}")
        heartRateInformationUiState.heartRate?.let {
            printHrLiveData(hr = it.hr, rrAvailable = it.rrAvailable, rrsMs = it.rrsMs, contactSupported = it.contactStatusSupported, contactStatus = it.contactStatus)
        }
    }

    private fun ecgDataReceived(ecgSampleDataUiState: EcgSampleDataUiState) {
        if (ecgSampleDataUiState.deviceId.isNotEmpty()) {
            printSampleRate(ecgRecordingLive, ecgSampleDataUiState.calculatedFrequency)
            ecgSampleDataUiState.sampleData?.let {
                printEcgLiveData(it)
            }
        }
    }

    private fun accDataReceived(accSampleDataUiState: AccSampleDataUiState) {
        if (accSampleDataUiState.deviceId.isNotEmpty()) {
            printSampleRate(accRecordingLive, accSampleDataUiState.calculatedFrequency)
            accSampleDataUiState.sampleData?.let {
                printAccLiveData(it)
            }
        }
    }

    private fun gyroDataReceived(gyroSampleDataUiState: GyroSampleDataUiState) {
        if (gyroSampleDataUiState.deviceId.isNotEmpty()) {
            printSampleRate(gyrRecordingLive, gyroSampleDataUiState.calculatedFrequency)
            gyroSampleDataUiState.sampleData?.let {
                printGyroLiveData(it)
            }
        }
    }

    private fun magnetometerDataReceived(magSampleDataUiState: MagnSampleDataUiState) {
        if (magSampleDataUiState.deviceId.isNotEmpty()) {
            printSampleRate(magRecordingLive, magSampleDataUiState.calculatedFrequency)
            magSampleDataUiState.sampleData?.let {
                printMagLiveData(it)
            }
        }
    }

    private fun ppgDataReceived(ppgSampleDataUiState: PpgSampleDataUiState) {
        if (ppgSampleDataUiState.deviceId.isNotEmpty()) {
            printSampleRate(ppgRecordingLive, ppgSampleDataUiState.calculatedFrequency)
            ppgSampleDataUiState.sampleData?.let {
                printPpgLiveData(it)
            }
        }
    }

    private fun pressureDataReceived(pressureSampleDataUiState: PressureSampleDataUiState) {
        if (pressureSampleDataUiState.deviceId.isNotEmpty()) {
            printSampleRate(pressureRecordingLive, pressureSampleDataUiState.calculatedFrequency)
            pressureSampleDataUiState.sampleData?.let {
                printPressureLiveData(it)
            }
        }
    }

    private fun locationDataReceived(locationSampleDataUiState: LocationSampleDataUiState) {
        if (locationSampleDataUiState.deviceId.isNotEmpty()) {
            printSampleRate(locRecordingLive, locationSampleDataUiState.calculatedFrequency)
            locationSampleDataUiState.sampleData?.let {
                printLocationLiveData(it)
            }
        }
    }

    private fun temperatureDataReceived(temperatureSampleDataUiState: TemperatureSampleDataUiState) {
        if (temperatureSampleDataUiState.deviceId.isNotEmpty()) {
            printSampleRate(locRecordingLive, temperatureSampleDataUiState.calculatedFrequency)
            temperatureSampleDataUiState.sampleData?.let {
                printTemperatureLiveData(it)
            }

        }
    }

    private fun skinTemperatureDataReceived(skinTemperatureSampleDataUiState: SkinTemperatureSampleDataUiState) {
        if (skinTemperatureSampleDataUiState.deviceId.isNotEmpty()) {
            printSampleRate(locRecordingLive, skinTemperatureSampleDataUiState.calculatedFrequency)
            skinTemperatureSampleDataUiState.sampleData?.let {
                printSkinTemperatureLiveData(it)
            }

        }
    }

    private fun ppiDataReceived(ppiSampleDataUiState: PpiSampleDataUiState) {
        if (ppiSampleDataUiState.deviceId.isNotEmpty() &&
            ppiSampleDataUiState.sampleData != null
        ) {
            printPpiLiveData(ppiSampleDataUiState.sampleData)
        }
    }

    private fun printSampleRate(recordingLive: View, sampleRate: Double?) {
        if (sampleRate != null && sampleRate > 0.0) {
            val measSampleRate = recordingLive.findViewById<TextView>(R.id.meas_sampleRate)
            val formatter = DecimalFormat("#.00")
            val sRate = formatter.format(sampleRate)
            measSampleRate.text = sRate
            try {
                val requestSampleRateTv = recordingLive.findViewById<TextView>(R.id.recording_live_sampleRate)
                val expectedSampleRate = requestSampleRateTv.text.toString().toInt()
                // Mark RED when not in expected range
                if (sampleRate < expectedSampleRate - 6 || expectedSampleRate + 6 < sampleRate) {
                    val measSampleRateWarning: AppCompatImageView = recordingLive.findViewById(R.id.meas_SampleRateWarning)
                    val sampleRateWarningText = recordingLive.findViewById<TextView>(R.id.meas_SampleRateWarningText)
                    measSampleRateWarning.visibility = VISIBLE
                    sampleRateWarningText.visibility = VISIBLE
                    sampleRateWarningText.text = "Detected: $sRate"
                }
            } catch (e: Exception) {
                Log.d(TAG, "Sample rate check not possible $e")
            }
        }
    }

    private fun printHrLiveData(hr: Int, rrAvailable: Boolean, rrsMs: List<Int>, contactSupported: Boolean, contactStatus: Boolean) {
        val recordingLiveHeader0 = hrRecordingLive.findViewById<TextView>(R.id.data_0_header)
        val recordingLiveData0 = hrRecordingLive.findViewById<TextView>(R.id.data_0)
        recordingLiveHeader0.text = "bpm:"
        recordingLiveData0.text = hr.toString()
        val recordingLiveHeader1 = hrRecordingLive.findViewById<TextView>(R.id.data_1_header)
        val recordingLiveData1 = hrRecordingLive.findViewById<TextView>(R.id.data_1)
        recordingLiveHeader1.text = "rrsMs:"
        if (rrAvailable && rrsMs.isNotEmpty()) {
            recordingLiveData1.text = rrsMs[0].toString()
            recordingLiveData1.visibility = VISIBLE
            recordingLiveHeader1.visibility = VISIBLE
        } else {
            recordingLiveData1.text = "N/A"
        }
        val recordingLiveHeader2 = hrRecordingLive.findViewById<TextView>(R.id.data_2_header)
        val recordingLiveData2 = hrRecordingLive.findViewById<TextView>(R.id.data_2)

        recordingLiveHeader2.text = "contact:"
        if (contactSupported) {

            recordingLiveData2.text = contactStatus.toString()
        } else {
            recordingLiveData2.text = "N/A"
        }
    }

    private fun printMagLiveData(magData: PolarMagnetometerData) {
        val magRecordingLiveDataX = magRecordingLive.findViewById<TextView>(R.id.data_0)
        val magRecordingLiveDataY = magRecordingLive.findViewById<TextView>(R.id.data_1)
        val magRecordingLiveDataZ = magRecordingLive.findViewById<TextView>(R.id.data_2)
        val magRecordingLiveHeaderX = magRecordingLive.findViewById<TextView>(R.id.data_0_header)
        val magRecordingLiveHeaderY = magRecordingLive.findViewById<TextView>(R.id.data_1_header)
        val magRecordingLiveHeaderZ = magRecordingLive.findViewById<TextView>(R.id.data_2_header)
        magRecordingLiveHeaderX.text = "X:"
        val formatter = DecimalFormat("0.000")
        val x = formatter.format(magData.samples[magData.samples.size - 1].x.toDouble())
        val y = formatter.format(magData.samples[magData.samples.size - 1].y.toDouble())
        val z = formatter.format(magData.samples[magData.samples.size - 1].z.toDouble())
        magRecordingLiveDataX.text = x
        magRecordingLiveHeaderY.text = "Y:"
        magRecordingLiveDataY.text = y
        magRecordingLiveHeaderZ.text = "Z:"
        magRecordingLiveDataZ.text = z
    }

    private fun printGyroLiveData(gyroData: PolarGyroData) {
        val recordingLiveDataX = gyrRecordingLive.findViewById<TextView>(R.id.data_0)
        val recordingLiveDataY = gyrRecordingLive.findViewById<TextView>(R.id.data_1)
        val recordingLiveDataZ = gyrRecordingLive.findViewById<TextView>(R.id.data_2)
        val recordingLiveHeaderX = gyrRecordingLive.findViewById<TextView>(R.id.data_0_header)
        val recordingLiveHeaderY = gyrRecordingLive.findViewById<TextView>(R.id.data_1_header)
        val recordingLiveHeaderZ = gyrRecordingLive.findViewById<TextView>(R.id.data_2_header)
        val formatter = DecimalFormat("#.##")
        val x = formatter.format(gyroData.samples[gyroData.samples.size - 1].x.toDouble())
        val y = formatter.format(gyroData.samples[gyroData.samples.size - 1].y.toDouble())
        val z = formatter.format(gyroData.samples[gyroData.samples.size - 1].z.toDouble())
        recordingLiveHeaderX.text = "X:"
        recordingLiveDataX.text = x
        recordingLiveHeaderY.text = "Y:"
        recordingLiveDataY.text = y
        recordingLiveHeaderZ.text = "Z:"
        recordingLiveDataZ.text = z
    }

    private fun printPressureLiveData(pressureData: PolarPressureData) {
        val recordingLiveDataX = pressureRecordingLive.findViewById<TextView>(R.id.data_0)
        val recordingLiveHeaderX = pressureRecordingLive.findViewById<TextView>(R.id.data_0_header)
        val formatter = DecimalFormat("#.##")
        val pressure = formatter.format(pressureData.samples[pressureData.samples.size - 1].pressure.toDouble())
        recordingLiveHeaderX.text = "Pressure(bar):"
        recordingLiveDataX.text = pressure
    }

    private fun printLocationLiveData(locationData: PolarLocationData) {
        val recordingLiveData0 = locRecordingLive.findViewById<TextView>(R.id.data_0)
        val recordingLiveHeader0 = locRecordingLive.findViewById<TextView>(R.id.data_0_header)
        val recordingLiveData1 = locRecordingLive.findViewById<TextView>(R.id.data_1)
        val recordingLiveHeader1 = locRecordingLive.findViewById<TextView>(R.id.data_1_header)
        val recordingLiveData2 = locRecordingLive.findViewById<TextView>(R.id.data_2)
        val recordingLiveHeader2 = locRecordingLive.findViewById<TextView>(R.id.data_2_header)

        val formatter = DecimalFormat("#.#####")

        when (val sample = locationData.samples[0]) {
            is GpsCoordinatesSample -> {
                recordingLiveHeader0.text = "Fix:"
                recordingLiveData0.text = sample.fix.toString()
                recordingLiveHeader1.text = "Lat:"
                recordingLiveData1.text = formatter.format(sample.latitude)
                recordingLiveHeader2.text = "Lon:"
                recordingLiveData2.text = formatter.format(sample.longitude)
            }
            else -> {
                //NOP
            }
        }
    }

    private fun printTemperatureLiveData(temperatureData: PolarTemperatureData) {
        val recordingLiveDataX = temperatureRecordingLive.findViewById<TextView>(R.id.data_0)
        val recordingLiveHeaderX = temperatureRecordingLive.findViewById<TextView>(R.id.data_0_header)
        val formatter = DecimalFormat("#.##")
        val temperature = formatter.format(temperatureData.samples.last().temperature.toDouble())
        recordingLiveHeaderX.text = "Temperature:"
        recordingLiveDataX.text = temperature
    }

    private fun printSkinTemperatureLiveData(temperatureData: PolarTemperatureData) {
        val recordingLiveDataX = skinTemperatureRecordingLive.findViewById<TextView>(R.id.data_0)
        val recordingLiveHeaderX = skinTemperatureRecordingLive.findViewById<TextView>(R.id.data_0_header)
        val formatter = DecimalFormat("#.##")
        val skinTemperature = formatter.format(temperatureData.samples.last().temperature.toDouble())
        recordingLiveHeaderX.text = "Skin Temperature:"
        recordingLiveDataX.text = skinTemperature
    }

    private fun printAccLiveData(accData: PolarAccelerometerData) {
        val recordingLiveDataX = accRecordingLive.findViewById<TextView>(R.id.data_0)
        val recordingLiveDataY = accRecordingLive.findViewById<TextView>(R.id.data_1)
        val recordingLiveDataZ = accRecordingLive.findViewById<TextView>(R.id.data_2)
        val recordingLiveHeaderX = accRecordingLive.findViewById<TextView>(R.id.data_0_header)
        val recordingLiveHeaderY = accRecordingLive.findViewById<TextView>(R.id.data_1_header)
        val recordingLiveHeaderZ = accRecordingLive.findViewById<TextView>(R.id.data_2_header)
        recordingLiveHeaderX.text = "X:"
        recordingLiveDataX.text = accData.samples.last().x.toString()
        recordingLiveHeaderY.text = "Y:"
        recordingLiveDataY.text = accData.samples.last().y.toString()
        recordingLiveHeaderZ.text = "Z:"
        recordingLiveDataZ.text = accData.samples.last().z.toString()
    }

    private fun printEcgLiveData(polarEcgData: PolarEcgData) {
        val recordingLiveData0 = ecgRecordingLive.findViewById<TextView>(R.id.data_0)
        val recordingLiveHeader0 = ecgRecordingLive.findViewById<TextView>(R.id.data_0_header)
        when (val ecgSample = polarEcgData.samples.last()) {
            is EcgSample -> {
                recordingLiveHeader0.text = "Ecg uV:"
                recordingLiveData0.text = ecgSample.voltage.toString()
            }
            is FecgSample -> {
                val recordingLiveData1 = ecgRecordingLive.findViewById<TextView>(R.id.data_1)
                val recordingLiveHeader1 = ecgRecordingLive.findViewById<TextView>(R.id.data_1_header)

                val recordingLiveData2 = ecgRecordingLive.findViewById<TextView>(R.id.data_2)
                val recordingLiveHeader2 = ecgRecordingLive.findViewById<TextView>(R.id.data_2_header)

                recordingLiveHeader0.text = "Ecg:"
                recordingLiveData0.text = ecgSample.ecg.toString()

                recordingLiveHeader1.text = "Bioz:"
                recordingLiveData1.text = ecgSample.bioz.toString()

                recordingLiveHeader2.text = "Status:"
                recordingLiveData2.text = ecgSample.status.toString()
            }
        }
    }

    private fun printPpgLiveData(polarPpgData: PolarPpgData) {
        val recordingLiveHeader0 = ppgRecordingLive.findViewById<TextView>(R.id.data_0_header)

        if (polarPpgData.samples.isEmpty()) {
            recordingLiveHeader0.text = "Ppg recording is ON - No live data"
            recordingLiveHeader0.setTextColor(Color.GREEN)
            recordingLiveHeader0.visibility = VISIBLE
        } else {
            val recordingLiveData0 = ppgRecordingLive.findViewById<TextView>(R.id.data_0)
            val recordingLiveData1 = ppgRecordingLive.findViewById<TextView>(R.id.data_1)
            val recordingLiveData2 = ppgRecordingLive.findViewById<TextView>(R.id.data_2)
            val recordingLiveData3 = ppgRecordingLive.findViewById<TextView>(R.id.data_3)
            val recordingLiveHeader1 = ppgRecordingLive.findViewById<TextView>(R.id.data_1_header)
            val recordingLiveHeader2 = ppgRecordingLive.findViewById<TextView>(R.id.data_2_header)
            val recordingLiveHeader3 = ppgRecordingLive.findViewById<TextView>(R.id.data_3_header)
            when (polarPpgData.type) {
                PolarPpgData.PpgDataType.PPG3_AMBIENT1 -> {
                    recordingLiveHeader0.visibility = VISIBLE
                    recordingLiveHeader1.visibility = VISIBLE
                    recordingLiveHeader2.visibility = VISIBLE
                    recordingLiveHeader3.visibility = VISIBLE
                    recordingLiveData0.visibility = VISIBLE
                    recordingLiveData1.visibility = VISIBLE
                    recordingLiveData2.visibility = VISIBLE
                    recordingLiveData3.visibility = VISIBLE
                    recordingLiveHeader0.text = "ppg0:"
                    recordingLiveData0.text = polarPpgData.samples.last().channelSamples[0].toString()
                    recordingLiveHeader1.text = "ppg1:"
                    recordingLiveData1.text = polarPpgData.samples.last().channelSamples[1].toString()
                    recordingLiveHeader2.text = "ppg2:"
                    recordingLiveData2.text = polarPpgData.samples.last().channelSamples[2].toString()
                    recordingLiveHeader3.text = "ambient:"
                    recordingLiveData3.text = polarPpgData.samples.last().channelSamples[3].toString()
                }
                PolarPpgData.PpgDataType.FRAME_TYPE_7 -> {
                    recordingLiveHeader0.visibility = VISIBLE
                    recordingLiveHeader1.visibility = VISIBLE
                    recordingLiveHeader2.visibility = VISIBLE
                    recordingLiveHeader3.visibility = VISIBLE
                    recordingLiveData0.visibility = VISIBLE
                    recordingLiveData1.visibility = VISIBLE
                    recordingLiveData2.visibility = VISIBLE
                    recordingLiveData3.visibility = VISIBLE
                    recordingLiveHeader0.text = "ppg0:"
                    recordingLiveData0.text = polarPpgData.samples.last().channelSamples[0].toString()
                    recordingLiveHeader1.text = "ppg1:"
                    recordingLiveData1.text = polarPpgData.samples.last().channelSamples[1].toString()
                    recordingLiveHeader2.text = "ppg2:"
                    recordingLiveData2.text = polarPpgData.samples.last().channelSamples[2].toString()
                    recordingLiveHeader3.text = "ppg3:"
                    recordingLiveData3.text = polarPpgData.samples.last().channelSamples[3].toString()
                }
                PolarPpgData.PpgDataType.FRAME_TYPE_10 -> {
                    recordingLiveHeader0.visibility = VISIBLE
                    recordingLiveHeader1.visibility = VISIBLE
                    recordingLiveHeader2.visibility = VISIBLE
                    recordingLiveData0.visibility = VISIBLE
                    recordingLiveData1.visibility = VISIBLE
                    recordingLiveData2.visibility = VISIBLE
                    recordingLiveHeader0.text = "red:"
                    recordingLiveData0.text = polarPpgData.samples.last().channelSamples[7].toString()
                    recordingLiveHeader1.text = "green:"
                    recordingLiveData1.text = polarPpgData.samples.last().channelSamples[13].toString()
                    recordingLiveHeader2.text = "ir:"
                    recordingLiveData2.text = polarPpgData.samples.last().channelSamples[19].toString()
                }
                else -> {
                    //NOP
                }
            }
        }
    }

    private fun printPpiLiveData(ppiSampleData: PolarPpiData.PolarPpiSample) {
        val recordingLiveHeader0 = ppiRecordingLive.findViewById<TextView>(R.id.data_0_header)
        val recordingLiveData0 = ppiRecordingLive.findViewById<TextView>(R.id.data_0)
        val recordingLiveHeader1 = ppiRecordingLive.findViewById<TextView>(R.id.data_1_header)
        val recordingLiveData1 = ppiRecordingLive.findViewById<TextView>(R.id.data_1)
        recordingLiveHeader0.text = "Ppi:"
        recordingLiveData0.text = ppiSampleData.ppi.toString()
        recordingLiveHeader1.text = "ErrorE:"
        recordingLiveData1.text = ppiSampleData.errorEstimate.toString()
    }

    private fun setupStreamLiveStopped(feature: PolarDeviceDataType) {
        val liveSection = getLiveSectionView(feature)
        liveSection.visibility = GONE
    }

    private fun setupStreamLivePaused(feature: PolarDeviceDataType) {
        val liveSection = getLiveSectionView(feature)
        val recordingLiveHeader = liveSection.findViewById<TextView>(R.id.recording_live_data_header)
        val currentText = recordingLiveHeader.text
        val newText = "$currentText - PAUSED"
        recordingLiveHeader.text = newText
    }

    private fun setupStreamLiveRecording(feature: PolarDeviceDataType, settings: Map<SettingType, Int> = emptyMap()) {
        val liveSection = getLiveSectionView(feature)
        liveSection.visibility = VISIBLE
        recordingLiveSettingsView(liveSection, settings)
        val measSampleRateHeader = liveSection.findViewById<TextView>(R.id.meas_sampleRate_header)
        val measSampleRate = liveSection.findViewById<TextView>(R.id.meas_sampleRate)
        val measSampleRateWarning: AppCompatImageView = liveSection.findViewById(R.id.meas_SampleRateWarning)
        val sampleRateWarningText = liveSection.findViewById<TextView>(R.id.meas_SampleRateWarningText)
        measSampleRateWarning.visibility = GONE
        sampleRateWarningText.visibility = GONE
        val recordingLiveData0 = liveSection.findViewById<TextView>(R.id.data_0)
        recordingLiveData0.text = ""
        val recordingLiveData1 = liveSection.findViewById<TextView>(R.id.data_1)
        recordingLiveData1.text = ""
        val recordingLiveData2 = liveSection.findViewById<TextView>(R.id.data_2)
        recordingLiveData2.text = ""
        val recordingLiveData3 = liveSection.findViewById<TextView>(R.id.data_3)
        recordingLiveData3.text = ""
        val recordingLiveHeader0 = liveSection.findViewById<TextView>(R.id.data_0_header)
        val recordingLiveHeader1 = liveSection.findViewById<TextView>(R.id.data_1_header)
        val recordingLiveHeader2 = liveSection.findViewById<TextView>(R.id.data_2_header)
        measSampleRateHeader.visibility = VISIBLE
        measSampleRate.visibility = VISIBLE
        measSampleRate.setTextColor(Color.WHITE)
        measSampleRate.text = ""
        val recordingLiveError = liveSection.findViewById<TextView>(R.id.recording_live_data_stream_error)
        recordingLiveError.setTextColor(Color.RED)
        recordingLiveError.visibility = GONE
        val recordingLiveHeader = liveSection.findViewById<TextView>(R.id.recording_live_data_header)
        when (feature) {
            PolarDeviceDataType.ECG -> {
                recordingLiveHeader.text = "ECG"
                recordingLiveHeader0.visibility = VISIBLE
                recordingLiveData0.visibility = VISIBLE
                recordingLiveHeader1.visibility = VISIBLE
                recordingLiveData1.visibility = VISIBLE
                recordingLiveHeader2.visibility = VISIBLE
                recordingLiveData2.visibility = VISIBLE
            }
            PolarDeviceDataType.ACC -> {
                recordingLiveHeader.text = "ACC"
                recordingLiveHeader0.visibility = VISIBLE
                recordingLiveHeader1.visibility = VISIBLE
                recordingLiveHeader2.visibility = VISIBLE
                recordingLiveData0.visibility = VISIBLE
                recordingLiveData1.visibility = VISIBLE
                recordingLiveData2.visibility = VISIBLE
            }
            PolarDeviceDataType.PPG -> {
                recordingLiveHeader.text = "PPG"
            }
            PolarDeviceDataType.PPI -> {
                recordingLiveHeader.text = "PPI"
                recordingLiveHeader0.visibility = VISIBLE
                recordingLiveHeader1.visibility = VISIBLE
                recordingLiveData0.visibility = VISIBLE
                recordingLiveData1.visibility = VISIBLE
                measSampleRateHeader.visibility = GONE
                measSampleRate.visibility = GONE
            }
            PolarDeviceDataType.GYRO -> {
                recordingLiveHeader.text = "GYR"
                recordingLiveHeader0.visibility = VISIBLE
                recordingLiveHeader1.visibility = VISIBLE
                recordingLiveHeader2.visibility = VISIBLE
                recordingLiveData0.visibility = VISIBLE
                recordingLiveData1.visibility = VISIBLE
                recordingLiveData2.visibility = VISIBLE
            }
            PolarDeviceDataType.MAGNETOMETER -> {
                recordingLiveHeader.text = "MAGN"
                recordingLiveHeader0.visibility = VISIBLE
                recordingLiveHeader1.visibility = VISIBLE
                recordingLiveHeader2.visibility = VISIBLE
                recordingLiveData0.visibility = VISIBLE
                recordingLiveData1.visibility = VISIBLE
                recordingLiveData2.visibility = VISIBLE
            }
            PolarDeviceDataType.PRESSURE -> {
                recordingLiveHeader.text = "BARO"
                recordingLiveHeader0.visibility = VISIBLE
                recordingLiveData0.visibility = VISIBLE
            }
            PolarDeviceDataType.LOCATION -> {
                recordingLiveHeader.text = "LOCATION"
                recordingLiveHeader0.visibility = VISIBLE
                recordingLiveHeader1.visibility = VISIBLE
                recordingLiveHeader2.visibility = VISIBLE
                recordingLiveData0.visibility = VISIBLE
                recordingLiveData1.visibility = VISIBLE
                recordingLiveData2.visibility = VISIBLE
            }

            PolarDeviceDataType.TEMPERATURE -> {
                recordingLiveHeader.text = "TEMP"
                recordingLiveHeader0.visibility = VISIBLE
                recordingLiveData0.visibility = VISIBLE
            }

            PolarDeviceDataType.SKIN_TEMPERATURE -> {
                recordingLiveHeader.text = "SKIN_TEMP"
                recordingLiveHeader0.visibility = VISIBLE
                recordingLiveData0.visibility = VISIBLE
            }

            PolarDeviceDataType.HR -> {
                recordingLiveHeader.text = "HR"
                recordingLiveHeader0.visibility = VISIBLE
                recordingLiveData0.visibility = VISIBLE
            }
        }
    }

    private fun getLiveSectionView(feature: PolarDeviceDataType): View {
        return when (feature) {
            PolarDeviceDataType.ECG -> ecgRecordingLive
            PolarDeviceDataType.ACC -> accRecordingLive
            PolarDeviceDataType.PPG -> ppgRecordingLive
            PolarDeviceDataType.PPI -> ppiRecordingLive
            PolarDeviceDataType.GYRO -> gyrRecordingLive
            PolarDeviceDataType.MAGNETOMETER -> magRecordingLive
            PolarDeviceDataType.PRESSURE -> pressureRecordingLive
            PolarDeviceDataType.LOCATION -> locRecordingLive
            PolarDeviceDataType.TEMPERATURE -> temperatureRecordingLive
            PolarDeviceDataType.SKIN_TEMPERATURE -> skinTemperatureRecordingLive
            PolarDeviceDataType.HR -> hrRecordingLive
        }
    }

    private fun recordingLiveSettingsView(recordingLiveView: View, settings: Map<SettingType, Int> = emptyMap()) {
        val settingsSection = recordingLiveView.findViewById<LinearLayout>(R.id.recording_live_settings_section)
        settingsSection.visibility = if (settings.isNotEmpty()) {
            val sampleRateSection = recordingLiveView.findViewById<LinearLayout>(R.id.recording_live_settings_sampleRate)
            sampleRateSection.visibility = if (settings.containsKey(SettingType.SAMPLE_RATE)) {
                val sampleRate = recordingLiveView.findViewById<TextView>(R.id.recording_live_sampleRate)
                val sampleRateText = settings[SettingType.SAMPLE_RATE].toString()
                sampleRate.text = sampleRateText
                VISIBLE
            } else {
                GONE
            }

            val resolutionSection = recordingLiveView.findViewById<LinearLayout>(R.id.recording_live_settings_resolution)
            resolutionSection.visibility = if (settings.containsKey(SettingType.RESOLUTION)) {
                val resolution = recordingLiveView.findViewById<TextView>(R.id.recording_live_resolution)
                val resolutionText = settings[SettingType.RESOLUTION].toString()
                resolution.text = resolutionText
                VISIBLE
            } else {
                GONE
            }

            val rangeSection = recordingLiveView.findViewById<LinearLayout>(R.id.recording_live_settings_range)
            rangeSection.visibility = if (settings.containsKey(SettingType.RANGE)) {
                val range = recordingLiveView.findViewById<TextView>(R.id.recording_live_range)
                val rangeText = settings[SettingType.RANGE].toString()
                range.text = rangeText
                VISIBLE
            } else {
                GONE
            }

            val channelsSection = recordingLiveView.findViewById<LinearLayout>(R.id.recording_live_settings_channels)
            channelsSection.visibility = if (settings.containsKey(SettingType.CHANNELS)) {
                val sampleRate = recordingLiveView.findViewById<TextView>(R.id.recording_live_channels)
                val sampleRateText = settings[SettingType.CHANNELS].toString()
                sampleRate.text = sampleRateText
                VISIBLE
            } else {
                GONE
            }

            VISIBLE
        } else {
            GONE
        }
    }

    private fun getRecStatusAndSettingsView(feature: PolarDeviceDataType): View {
        return when (feature) {
            PolarDeviceDataType.ECG -> ecgStatusAndSettings
            PolarDeviceDataType.ACC -> accStatusAndSettings
            PolarDeviceDataType.PPG -> ppgStatusAndSettings
            PolarDeviceDataType.PPI -> ppiStatusAndSettings
            PolarDeviceDataType.GYRO -> gyrStatusAndSettings
            PolarDeviceDataType.MAGNETOMETER -> magStatusAndSettings
            PolarDeviceDataType.PRESSURE -> pressureStatusAndSettings
            PolarDeviceDataType.LOCATION -> locationStatusAndSettings
            PolarDeviceDataType.TEMPERATURE -> temperatureStatusAndSettings
            PolarDeviceDataType.SKIN_TEMPERATURE -> skinTemperatureStatusAndSettings
            PolarDeviceDataType.HR -> hrStatusAndSettings
        }
    }

    private fun getOnlineRecSettingsButtonView(feature: PolarDeviceDataType): Button? {
        val recordingSettingsView = getRecStatusAndSettingsView(feature)
        return when (feature) {
            PolarDeviceDataType.HR,
            PolarDeviceDataType.PPI -> null
            else -> recordingSettingsView.findViewById(R.id.recording_controls_settings_button)
        }
    }

    private fun getOnlineRecordingCheckBox(feature: PolarDeviceDataType): CheckBox {
        val recordingSettingsView = getRecStatusAndSettingsView(feature)
        return recordingSettingsView.findViewById(R.id.recording_controls_select_check_box)
    }

    private fun getOnlineHrRecordingCheckBox(): CheckBox {
        val hrRecording = hrStatusAndSettings
        return hrRecording.findViewById(R.id.recording_controls_select_check_box)
    }

    private fun getOnlineRecStartStopButton(feature: PolarDeviceDataType): Button {
        val recordingSettingsView = getRecStatusAndSettingsView(feature)
        return recordingSettingsView.findViewById(R.id.recording_controls_start_stop_button)
    }

    private fun getOnlineRecHeaderText(feature: PolarDeviceDataType): String {
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
            PolarDeviceDataType.SKIN_TEMPERATURE -> "SKIN_TEM"
            PolarDeviceDataType.HR -> "HR"
        }
    }

    private fun setupOnlineRecStatusAndSettingsView(feature: PolarDeviceDataType, makeVisible: Boolean) {
        val view = getRecStatusAndSettingsView(feature)
        if (makeVisible) {
            val header: TextView = view.findViewById(R.id.recording_controls_header)
            header.text = getOnlineRecHeaderText(feature)
            view.visibility = VISIBLE
            getOnlineRecStartStopButton(feature).visibility = GONE
            setupOnlineRecSettings(feature)
        } else {
            view.visibility = GONE
        }
    }
}