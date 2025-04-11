package com.polar.polarsensordatacollector.ui.landing

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.View.*
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.polar.androidcommunications.api.ble.model.gatt.client.ChargeState
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.model.Device
import com.polar.polarsensordatacollector.ui.utils.DialogUtility.showSensorSelection
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarDeviceInfo
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch


@AndroidEntryPoint
class MainFragment : Fragment(R.layout.fragment_main) {
    companion object {
        private const val TAG = "MainFragment"
        private const val INTENSITY_SELECTION_ENABLED_ALPHA = 1.0f
        private const val INTENSITY_SELECTION_DISABLED_ALPHA = 0.4f
    }

    private val viewModel: MainViewModel by activityViewModels()
    private var connectionState: MainViewModel.DeviceConnectionStates = MainViewModel.DeviceConnectionStates.NOT_CONNECTED
    private val connectedDevices: MutableSet<Device> = mutableSetOf()

    private lateinit var sensorState: TextView
    private lateinit var phoneBleStatus: TextView
    private lateinit var firmwareVersion: TextView
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout

    private lateinit var deviceConnectionStatusGroup: ConstraintLayout

    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button

    private lateinit var batteryStatus: TextView
    private lateinit var batteryChargingStatus: TextView

    private var selectedDevice: Device? = null

    private lateinit var onlineOfflineAdapter: OnlineOfflineAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupViews(view)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiConnectionState.collect {
                    deviceConnectionStateChange(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiDeviceInformationState.collect {
                    disInformationReceived(it)
                    batteryLevelReceived(it)
                    batteryChargingStatusReceived(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiOfflineRecordingState.collect {
                    offlineRecordingStateChange(it)
                }
            }
        }

        connectButton.setOnClickListener { _ ->
            when (connectionState) {
                MainViewModel.DeviceConnectionStates.NOT_CONNECTED -> showSensorSelection(
                    requireActivity(),
                    { info: PolarDeviceInfo? ->
                        info?.let {
                            Log.d(TAG, "selected: $it")
                            val deviceId = it.deviceId.ifEmpty {
                                it.address
                            }
                            val deviceAddress = it.address
                            val name = it.name.replace(" ", "_")
                            selectedDevice = Device(deviceId = deviceId, address = deviceAddress, name = name)
                            try {
                                selectedDevice?.let { viewModel.connectToDevice(it) }
                                viewModel.selectedDevice = selectedDevice
                            } catch (polarInvalidArgument: PolarInvalidArgument) {
                                polarInvalidArgument.printStackTrace()
                            }
                        }
                    },
                    viewModel.searchForDevice()
                )
                MainViewModel.DeviceConnectionStates.CONNECTING_TO_SELECTED_DEVICE -> {
                    val connectingToDevice = "Connecting to device ${selectedDevice?.name}"
                    showToast(connectingToDevice)
                    try {
                        selectedDevice?.let { viewModel.disconnectFromDevice(it) }
                        selectedDevice = null
                        batteryStatus.text = ""
                        batteryChargingStatus.text = ""
                        firmwareVersion.text = ""
                        sensorState.text = ""
                        tabLayout.visibility = GONE
                        viewPager.visibility = INVISIBLE
                        phoneBleStatus.visibility = GONE
                        deviceConnectionStatusGroup.visibility = GONE
                        connectButton.setText(R.string.search_and_connect_search)
                        connectButton.setBackgroundColor(
                            resources.getColor(
                                R.color.colorButtonConnect,
                                null
                            )
                        )
                        connectButton.isEnabled = true
                        onlineOfflineAdapter.removeFragments()
                    } catch (polarInvalidArgument: PolarInvalidArgument) {
                        polarInvalidArgument.printStackTrace()
                    }
                }
                MainViewModel.DeviceConnectionStates.DISCONNECTING_FROM_SELECTED_DEVICE -> {
                    val disconnectingFromDevice =
                        "Disconnecting from the device ${selectedDevice?.name}"
                    showToast(disconnectingFromDevice)
                }

                MainViewModel.DeviceConnectionStates.CONNECTED -> {
                    try {
                        showSensorSelection(
                            requireActivity(),
                            { info: PolarDeviceInfo? ->
                                info?.let { it ->
                                    Log.d(TAG, "selected: $it")
                                    val deviceId = it.deviceId.ifEmpty {
                                        it.address
                                    }
                                    val deviceAddress = it.address
                                    val name = it.name.replace(" ", "_")
                                    selectedDevice = Device(
                                        deviceId = deviceId,
                                        address = deviceAddress,
                                        name = name
                                    )
                                    try {
                                        selectedDevice?.let { viewModel.connectToDevice(it) }
                                    } catch (polarInvalidArgument: PolarInvalidArgument) {
                                        polarInvalidArgument.printStackTrace()
                                    }
                                }
                            },
                            viewModel.searchForDevice()
                        )
                    } catch (polarInvalidArgument: PolarInvalidArgument) {
                        polarInvalidArgument.printStackTrace()
                    }
                }
                MainViewModel.DeviceConnectionStates.PHONE_BLE_OFF -> {
                    //NOP
                }
            }
        }

        disconnectButton.setOnClickListener {
            selectedDevice?.let {
                viewModel.disconnectFromDevice(it)
                connectedDevices.remove(selectedDevice)
                if (connectedDevices.isEmpty()) {
                    Log.d(TAG, "No devices connected")
                    onlineOfflineAdapter.removeFragments()
                    disconnectButton.visibility = GONE
                    connectButton.setText(R.string.search_and_connect_search)
                    batteryStatus.text = ""
                    firmwareVersion.text = ""
                    sensorState.text = ""
                    connectButton.isEnabled = true
                    tabLayout.visibility = GONE
                    viewPager.visibility = INVISIBLE
                    deviceConnectionStatusGroup.visibility = VISIBLE
                    phoneBleStatus.visibility = GONE
                    connectButton.setBackgroundColor(resources.getColor(R.color.colorButtonConnecting, null))
                } else {
                    val connectedDevice = connectedDevices.first()
                    Log.d(TAG, "Device changed to: $connectedDevice")
                    sensorState.text = getString(R.string.device_id, connectedDevice.deviceId)
                    viewModel.selectedDevice = connectedDevice
                    selectedDevice = connectedDevice
                }
            }
        }
    }

    private fun setupViews(view: View) {
        tabLayout = view.findViewById(R.id.tab_layout)
        onlineOfflineAdapter = OnlineOfflineAdapter(this)
        viewPager = view.findViewById(R.id.pager)
        viewPager.adapter = onlineOfflineAdapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = onlineOfflineAdapter.items[position].first
        }.attach()

        sensorState = view.findViewById(R.id.device_status)
        deviceConnectionStatusGroup = view.findViewById(R.id.device_connection_status_group)
        phoneBleStatus = view.findViewById(R.id.phone_bluetooth_status)
        connectButton = view.findViewById(R.id.search_connect_button)
        disconnectButton = view.findViewById(R.id.disconnect_button)
        firmwareVersion = view.findViewById(R.id.firmware_version)
        batteryStatus = view.findViewById(R.id.battery)
        batteryChargingStatus = view.findViewById(R.id.battery_charging_status)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("Current_frag", viewPager.currentItem)
    }

    private fun deviceConnectionStateChange(state: DeviceConnectionUiState) {
        Log.d(TAG, "device connection state change to $state")
        connectionState = state.state

        when (state.state) {
            MainViewModel.DeviceConnectionStates.NOT_CONNECTED -> {
                Log.i(TAG, "Device not connected")
                phoneBleStatus.visibility = GONE
            }
            MainViewModel.DeviceConnectionStates.CONNECTING_TO_SELECTED_DEVICE -> {
                connectButton.setText(R.string.search_and_connect_connecting)
                sensorState.text = "Device Id: ${selectedDevice?.deviceId}"
                connectButton.isEnabled = false
                tabLayout.visibility = GONE
                viewPager.visibility = INVISIBLE
                deviceConnectionStatusGroup.visibility = VISIBLE
                phoneBleStatus.visibility = GONE
                connectButton.setBackgroundColor(resources.getColor(R.color.colorButtonConnecting, null))
            }
            MainViewModel.DeviceConnectionStates.CONNECTED -> {
                connectButton.setText(R.string.search_and_connect_connections)
                val deviceId = selectedDevice?.deviceId ?: throw IllegalStateException("Selected device can't be null for connected device!")
                sensorState.text = getString(R.string.device_id, deviceId)
                connectButton.isEnabled = true
                onlineOfflineAdapter.removeFragments(isAlreadyConnected = connectedDevices.contains(selectedDevice))
                onlineOfflineAdapter.addOnlineRecordingFragment(deviceId)
                onlineOfflineAdapter.addDeviceSettingsFragment(deviceId)
                onlineOfflineAdapter.addLoggingFragment(deviceId)
                onlineOfflineAdapter.addActivityFragment(deviceId)
                tabLayout.visibility = VISIBLE
                viewPager.visibility = VISIBLE
                deviceConnectionStatusGroup.visibility = VISIBLE
                phoneBleStatus.visibility = GONE
                connectButton.setBackgroundColor(resources.getColor(R.color.colorButtonConnected, null))
                disconnectButton.visibility = VISIBLE
                selectedDevice?.let {
                    connectedDevices.add(it)
                }
            }
            MainViewModel.DeviceConnectionStates.DISCONNECTING_FROM_SELECTED_DEVICE -> {
                connectButton.setText(R.string.search_and_connect_disconnecting)
                sensorState.text = "Device Id: ${selectedDevice?.deviceId}"
                connectButton.isEnabled = false
                tabLayout.visibility = GONE
                viewPager.visibility = INVISIBLE
                deviceConnectionStatusGroup.visibility = VISIBLE
                phoneBleStatus.visibility = GONE
                connectButton.setBackgroundColor(resources.getColor(R.color.colorButtonConnecting, null))
                onlineOfflineAdapter.removeFragments()
                selectedDevice?.let {
                    connectedDevices.remove(it)
                }
                if (connectedDevices.isEmpty()) {
                    disconnectButton.visibility = GONE
                }
            }
            MainViewModel.DeviceConnectionStates.PHONE_BLE_OFF -> {
                connectButton.isEnabled = false
                selectedDevice = null
                batteryStatus.text = ""
                batteryChargingStatus.text = ""
                firmwareVersion.text = ""
                sensorState.text = ""
                tabLayout.visibility = GONE
                viewPager.visibility = INVISIBLE
                phoneBleStatus.visibility = VISIBLE
                deviceConnectionStatusGroup.visibility = GONE
                connectButton.setText(R.string.search_and_connect_search)
            }
        }
    }

    private fun showToast(message: String) {
        val toast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
        toast.show()
    }

    private fun offlineRecordingStateChange(offlineRecordingUiState: OfflineRecordingAvailabilityUiState) {
        if (offlineRecordingUiState.isAvailable) {
            onlineOfflineAdapter.addOfflineRecordingFragment(offlineRecordingUiState.deviceId)
        }
    }

    private fun disInformationReceived(deviceInformationUiState: DeviceInformationUiState) {
        if (deviceInformationUiState.firmwareVersion.isNotEmpty()) {
            val firmware = "Firmware: ${deviceInformationUiState.firmwareVersion}"
            firmwareVersion.text = firmware
        }
    }

    private fun batteryLevelReceived(deviceInformationUiState: DeviceInformationUiState) {
        if (deviceInformationUiState.batteryLevel != null) {
            val batteryLevel = "Battery status: ${deviceInformationUiState.batteryLevel}%"
            batteryStatus.text = batteryLevel
        }
    }

    private fun batteryChargingStatusReceived(deviceInformationUiState: DeviceInformationUiState) {
        val chargeState = when (deviceInformationUiState.batteryChargeState) {
            ChargeState.CHARGING -> getString(R.string.charging)
            ChargeState.DISCHARGING_INACTIVE -> getString(R.string.fully_charged)
            ChargeState.DISCHARGING_ACTIVE -> getString(R.string.discharging)
            ChargeState.UNKNOWN -> ""
        }
        this.batteryChargingStatus.text = chargeState
    }
}