package com.polar.polarsensordatacollector.ui.landing

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.*
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.polar.androidcommunications.api.ble.model.gatt.client.BatteryPresentState
import com.polar.androidcommunications.api.ble.model.gatt.client.ChargeState
import com.polar.androidcommunications.api.ble.model.gatt.client.PowerSourceState
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.model.Device
import com.polar.polarsensordatacollector.ui.hrbroadcast.HrBroadcastDialogFragment
import com.polar.polarsensordatacollector.ui.utils.DialogUtility.showSensorSelection
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository.SdkFeaturesReadyEvent
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarDeviceInfo
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainFragment : Fragment() {
    companion object {
        private const val TAG = "MainFragment"
    }

    // ── Per-device pager component ────────────────────────────────────────────────────────────
    // Each connected device gets its own ViewPager2 + TabLayout + Adapter.  Switching devices is
    // a simple VISIBLE/GONE toggle — fragments and their ViewModels stay alive in the background,
    // so active streams continue running while another device is displayed.
    private inner class DevicePagerComponent(val deviceId: String) {
        // Adapter is created once and survives view recreation (navigating away and back).
        val adapter: OnlineOfflineAdapter = OnlineOfflineAdapter(this@MainFragment)

        // View references are recreated each time the fragment's view is created.
        private var containerView: View? = null
        private var _viewPager: ViewPager2? = null
        private var mediator: TabLayoutMediator? = null
        private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null

        fun setupViews(container: FrameLayout) {
            val view = LayoutInflater.from(container.context)
                .inflate(R.layout.layout_device_pager, container, false)
            container.addView(view)
            containerView = view

            val tabLayout: TabLayout = view.findViewById(R.id.tab_layout)
            _viewPager = view.findViewById<ViewPager2>(R.id.pager).also { pager ->
                pager.isSaveEnabled = false
                pager.adapter = adapter
            }
            mediator = TabLayoutMediator(tabLayout, _viewPager!!) { tab, position ->
                tab.text = adapter.items[position].first
            }.also { it.attach() }
            pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    viewPagerPagePerDevice[deviceId] = position
                }
            }.also { _viewPager!!.registerOnPageChangeCallback(it) }

            view.visibility = GONE  // caller calls show() after setting up
        }

        fun saveCurrentPage() {
            _viewPager?.currentItem?.let { viewPagerPagePerDevice[deviceId] = it }
        }

        fun teardownViews() {
            pageChangeCallback?.let { _viewPager?.unregisterOnPageChangeCallback(it) }
            mediator?.detach()
            _viewPager?.adapter = null
            containerView?.let { (it.parent as? ViewGroup)?.removeView(it) }
            pageChangeCallback = null
            mediator = null
            _viewPager = null
            containerView = null
        }

        fun show() {
            containerView?.visibility = VISIBLE
            _viewPager?.setCurrentItem(viewPagerPagePerDevice[deviceId] ?: 0, false)
        }
        fun hide() { containerView?.visibility = GONE }
        fun isShown(): Boolean = containerView?.visibility == VISIBLE
    }

    private val viewModel: MainViewModel by activityViewModels()
    private var connectionState: MainViewModel.DeviceConnectionStates = MainViewModel.DeviceConnectionStates.NOT_CONNECTED
    private val connectedDevices: MutableSet<Device> = mutableSetOf()
    // Remembered per-device tab position; persists across view recreation and device switches.
    private val viewPagerPagePerDevice: MutableMap<String, Int> = mutableMapOf()
    // Per-device pager components; keyed by device ID; survive view recreation.
    private val devicePagerComponents: MutableMap<String, DevicePagerComponent> = mutableMapOf()

    private lateinit var pagedContainer: FrameLayout
    private lateinit var sensorState: TextView
    private lateinit var phoneBleStatus: TextView
    private lateinit var firmwareVersion: TextView
    private lateinit var deviceConnectionStatusGroup: ConstraintLayout
    private lateinit var searchText: EditText
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var disconnectAllButton: Button
    private lateinit var listenHrBroadcastsButton: Button
    private lateinit var batteryStatus: TextView
    private lateinit var batteryChargingStatus: TextView
    private lateinit var batteryPresentStatus: TextView
    private lateinit var wiredPowerSourceConnectedStatus: TextView
    private lateinit var wirelessPowerSourceConnectedStatus: TextView

    private var selectedDevice: Device? = null
    private var selectedDeviceSupportsSettings: Boolean? = false
    private var activeDeviceId: String = ""
    private var recoveryGuidanceDialog: AlertDialog? = null
    private var selectedDeviceSupportsV2OfflineExercise: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_main, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupViews(view)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiConnectionState.collect { deviceConnectionStateChange(it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.disconnectGuidance.collect { showRecoveryGuidance(it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiDeviceInformationState.collect {
                    disInformationReceived(it)
                    batteryLevelReceived(it)
                    batteryChargingStatusReceived(it)
                    powerSourcesStateReceived(it)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiOfflineRecordingState.collect { offlineRecordingStateChange(it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiSdkFeaturesReadyState.collect { sdkFeaturesReadyChange(it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiOfflineRecordingV2State.collect { v2State ->
                    Log.d(TAG, "uiOfflineRecordingV2State: id=${v2State.identifier} available=${v2State.isAvailable}")
                    if (v2State.identifier.isNotEmpty() && v2State.isAvailable) {
                        selectedDeviceSupportsV2OfflineExercise = (v2State.identifier == selectedDevice?.deviceId)
                        val component = devicePagerComponents[v2State.identifier]
                        if (component != null && !component.adapter.hasExerciseV2Fragment()) {
                            component.adapter.addExerciseV2Fragment(v2State.identifier)
                        }
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.removeOnlineOfflineFragments.collect {
                    batteryStatus.text = ""
                    batteryChargingStatus.text = ""
                    hidePowerSourceState()
                    firmwareVersion.text = ""
                    sensorState.text = ""
                    connectButton.isEnabled = false
                    hideAllPagers()
                    deviceConnectionStatusGroup.visibility = VISIBLE
                    phoneBleStatus.visibility = GONE
                    listenHrBroadcastsButton.visibility = GONE
                    connectButton.setBackgroundColor(resources.getColor(R.color.colorButtonConnecting, null))
                    selectedDevice?.let { dev ->
                        destroyDeviceComponent(dev.deviceId)
                        connectedDevices.remove(dev)
                    }
                    if (connectedDevices.isEmpty()) {
                        disconnectButton.visibility = GONE
                        disconnectAllButton.visibility = GONE
                    }
                }
            }
        }

        connectButton.setOnClickListener {
            when (connectionState) {
                MainViewModel.DeviceConnectionStates.NOT_CONNECTED -> showSensorSelection(
                    requireActivity(),
                    { info: PolarDeviceInfo? ->
                        info?.let {
                            val deviceId = it.deviceId.ifEmpty { it.address }
                            selectedDevice = Device(deviceId = deviceId, address = it.address, name = it.name.replace(" ", "_"))
                            selectedDeviceSupportsV2OfflineExercise = false
                            try {
                                selectedDevice?.let { d -> viewModel.connectToDevice(d) }
                                viewModel.selectedDevice = selectedDevice
                            } catch (e: PolarInvalidArgument) { e.printStackTrace() }
                        }
                    },
                    viewModel.searchForDevice(searchText.text.toString()),
                    emptySet()
                )
                MainViewModel.DeviceConnectionStates.CONNECTING_TO_SELECTED_DEVICE -> {
                    showToast("Connecting to device ${selectedDevice?.name}")
                    try {
                        selectedDevice?.let { viewModel.disconnectFromDevice(it) }
                        selectedDevice?.let { dev -> destroyDeviceComponent(dev.deviceId) }
                        selectedDevice = null
                        batteryStatus.text = ""; batteryChargingStatus.text = ""
                        hidePowerSourceState(); firmwareVersion.text = ""; sensorState.text = ""
                        hideAllPagers()
                        phoneBleStatus.visibility = GONE
                        deviceConnectionStatusGroup.visibility = GONE
                        connectButton.setText(R.string.search_and_connect_search)
                        connectButton.setBackgroundColor(resources.getColor(R.color.colorButtonConnect, null))
                        connectButton.isEnabled = true
                    } catch (e: PolarInvalidArgument) { e.printStackTrace() }
                }
                MainViewModel.DeviceConnectionStates.DISCONNECTING_FROM_SELECTED_DEVICE ->
                    showToast("Disconnecting from the device ${selectedDevice?.name}")
                MainViewModel.DeviceConnectionStates.CONNECTED -> {
                    try {
                        showSensorSelection(
                            requireActivity(),
                            { info: PolarDeviceInfo? ->
                                info?.let {
                                    val deviceId = it.deviceId.ifEmpty { it.address }
                                    val alreadyConnected = connectedDevices.find { d -> d.deviceId == deviceId }
                                    if (alreadyConnected != null) {
                                        selectedDevice = alreadyConnected
                                        viewModel.selectedDevice = alreadyConnected
                                        selectedDeviceSupportsV2OfflineExercise = devicePagerComponents[deviceId]?.adapter?.hasExerciseV2Fragment() ?: false
                                        switchToDevice(deviceId)
                                        return@let
                                    }
                                    selectedDevice = Device(deviceId = deviceId, address = it.address, name = it.name.replace(" ", "_"))
                                    selectedDeviceSupportsV2OfflineExercise = false
                                    try {
                                        selectedDevice?.let { d -> viewModel.connectToDevice(d) }
                                        viewModel.selectedDevice = selectedDevice
                                    } catch (e: PolarInvalidArgument) { e.printStackTrace() }
                                }
                            },
                            viewModel.searchForDevice(searchText.text.toString()),
                            connectedDevices.map { it.deviceId }.toSet()
                        )
                    } catch (e: PolarInvalidArgument) { e.printStackTrace() }
                }
                MainViewModel.DeviceConnectionStates.PHONE_BLE_OFF -> { /*NOP*/ }
            }
        }

        disconnectButton.setOnClickListener {
            val deviceToDisconnect = selectedDevice
                ?: activeDeviceId.takeIf { it.isNotEmpty() }?.let { Device(deviceId = it, address = it, name = it) }
            deviceToDisconnect?.let {
                viewModel.disconnectFromDevice(it)
                selectedDevice?.let { dev ->
                    destroyDeviceComponent(dev.deviceId)
                    connectedDevices.remove(dev)
                }
                if (connectedDevices.isEmpty()) {
                    disconnectButton.visibility = GONE
                    disconnectAllButton.visibility = GONE
                    connectButton.setText(R.string.search_and_connect_search)
                    batteryStatus.text = ""; hidePowerSourceState(); firmwareVersion.text = ""; sensorState.text = ""
                    connectButton.isEnabled = true; hideAllPagers()
                    deviceConnectionStatusGroup.visibility = VISIBLE; phoneBleStatus.visibility = GONE
                    connectButton.setBackgroundColor(resources.getColor(R.color.colorButtonConnecting, null))
                } else {
                    disconnectAllButton.visibility = if (connectedDevices.size >= 2) VISIBLE else GONE
                    val next = connectedDevices.first()
                    selectedDevice = next; viewModel.selectedDevice = next
                    selectedDeviceSupportsV2OfflineExercise = devicePagerComponents[next.deviceId]?.adapter?.hasExerciseV2Fragment() ?: false
                    switchToDevice(next.deviceId)
                }
            }
        }

        listenHrBroadcastsButton.setOnClickListener { showHrBroadcastDialog() }

        disconnectAllButton.setOnClickListener {
            viewModel.disconnectAllDevices(connectedDevices.toList())
            connectedDevices.toList().forEach { dev -> destroyDeviceComponent(dev.deviceId) }
            connectedDevices.clear()
            selectedDevice = null
            disconnectButton.visibility = GONE
            disconnectAllButton.visibility = GONE
            connectButton.setText(R.string.search_and_connect_search)
            batteryStatus.text = ""; hidePowerSourceState(); firmwareVersion.text = ""; sensorState.text = ""
            connectButton.isEnabled = true; hideAllPagers()
            deviceConnectionStatusGroup.visibility = VISIBLE; phoneBleStatus.visibility = GONE
            connectButton.setBackgroundColor(resources.getColor(R.color.colorButtonConnecting, null))
        }

        batteryChargingStatus.setOnClickListener { togglePowersourceState() }
        batteryStatus.setOnClickListener { togglePowersourceState() }
        batteryPresentStatus.setOnClickListener { hidePowerSourceState() }
        wirelessPowerSourceConnectedStatus.setOnClickListener { hidePowerSourceState() }
        wiredPowerSourceConnectedStatus.setOnClickListener { hidePowerSourceState() }
    }

    private fun setupViews(view: View) {
        pagedContainer = view.findViewById(R.id.pager_container)
        // Recreate views for all existing components (handles navigation back to this fragment).
        devicePagerComponents.values.forEach { component ->
            component.setupViews(pagedContainer)
            if (component.deviceId == selectedDevice?.deviceId) component.show() else component.hide()
        }
        sensorState = view.findViewById(R.id.device_status)
        deviceConnectionStatusGroup = view.findViewById(R.id.device_connection_status_group)
        phoneBleStatus = view.findViewById(R.id.phone_bluetooth_status)
        searchText = view.findViewById(R.id.search_device_name_prefix)
        connectButton = view.findViewById(R.id.search_connect_button)
        disconnectButton = view.findViewById(R.id.disconnect_button)
        disconnectAllButton = view.findViewById(R.id.disconnect_all_button)
        listenHrBroadcastsButton = view.findViewById(R.id.listen_hr_broadcasts_button)
        firmwareVersion = view.findViewById(R.id.firmware_version)
        batteryStatus = view.findViewById(R.id.battery)
        batteryChargingStatus = view.findViewById(R.id.battery_charging_status)
        batteryPresentStatus = view.findViewById(R.id.battery_present_status)
        wiredPowerSourceConnectedStatus = view.findViewById(R.id.wired_power_source_connected_status)
        wirelessPowerSourceConnectedStatus = view.findViewById(R.id.wireless_power_source_connected_status)
    }

    override fun onStop() {
        devicePagerComponents.values.forEach { component ->
            component.saveCurrentPage()
        }
        super.onStop()
    }

    override fun onDestroyView() {
        devicePagerComponents.values.forEach { it.teardownViews() }
        recoveryGuidanceDialog?.dismiss()
        recoveryGuidanceDialog = null
        super.onDestroyView()
    }

    private fun showHrBroadcastDialog() {
        HrBroadcastDialogFragment.newInstance().show(childFragmentManager, HrBroadcastDialogFragment.TAG)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────────────────

    private fun hideAllPagers() { devicePagerComponents.values.forEach { it.hide() } }

    private fun showPager(deviceId: String) {
        devicePagerComponents.forEach { (id, comp) -> if (id == deviceId) comp.show() else comp.hide() }
    }

    private fun destroyDeviceComponent(deviceId: String) {
        devicePagerComponents.remove(deviceId)?.teardownViews()
    }

    private fun deviceConnectionStateChange(state: DeviceConnectionUiState) {
        Log.d(TAG, "device connection state change to $state")
        connectionState = state.state
        if (state.identifier.isNotEmpty()) activeDeviceId = state.identifier
        hidePowerSourceState()

        when (state.state) {
            MainViewModel.DeviceConnectionStates.NOT_CONNECTED -> {
                Log.i(TAG, "Device not connected: ${state.identifier}")
                val disconnectedDevice = connectedDevices.find { it.deviceId == state.identifier }
                disconnectedDevice?.let { connectedDevices.remove(it) }
                destroyDeviceComponent(state.identifier)
                // Clear saved tab position so reconnect always starts at tab 0 (OnlineRec).
                // The saved index is stale because dynamic tab insertions (OFFLINE, LOGGING) shift positions.
                viewPagerPagePerDevice.remove(state.identifier)

                if (state.identifier == selectedDevice?.deviceId || selectedDevice == null) {
                    if (connectedDevices.isNotEmpty()) {
                        disconnectAllButton.visibility = if (connectedDevices.size >= 2) VISIBLE else GONE
                        val fallback = connectedDevices.first()
                        selectedDevice = fallback; viewModel.selectedDevice = fallback
                        selectedDeviceSupportsV2OfflineExercise = devicePagerComponents[fallback.deviceId]?.adapter?.hasExerciseV2Fragment() ?: false
                        switchToDevice(fallback.deviceId)
                    } else {
                        selectedDevice = null
                        resetDisconnectedUi()
                        connectButton.visibility = VISIBLE; listenHrBroadcastsButton.visibility = VISIBLE
                        phoneBleStatus.visibility = if (viewModel.isBluetoothEnabled()) GONE else VISIBLE
                    }
                } else {
                    // background device disconnected — update Disconnect All visibility only
                    disconnectAllButton.visibility = if (connectedDevices.size >= 2) VISIBLE else GONE
                }
            }
            MainViewModel.DeviceConnectionStates.CONNECTING_TO_SELECTED_DEVICE -> {
                resetDisconnectedUi()
                connectButton.setText(R.string.search_and_connect_connecting)
                sensorState.text = getString(R.string.device_id, selectedDevice?.deviceId)
                connectButton.isEnabled = false; hideAllPagers()
                deviceConnectionStatusGroup.visibility = VISIBLE; phoneBleStatus.visibility = GONE
                disconnectButton.visibility = VISIBLE; listenHrBroadcastsButton.visibility = GONE
                connectButton.setBackgroundColor(resources.getColor(R.color.colorButtonConnecting, null))
            }
            MainViewModel.DeviceConnectionStates.CONNECTED -> {
                connectButton.setText(R.string.search_and_connect_connections)
                val deviceId = selectedDevice?.deviceId ?: state.identifier
                sensorState.text = getString(R.string.device_id, deviceId)
                connectButton.isEnabled = true

                // Guard: ensure selectedDevice always matches the device that just connected.
                // This covers two cases:
                // 1. SDK-initiated reconnect after Disconnect All (selectedDevice == null).
                // 2. Connecting a second device while another is selected (deviceId mismatch).
                // viewModel.getDeviceName() returns the real cached name when available,
                // preserving H10 tab detection. Falls back to deviceId; the real name arrives
                // shortly via the deviceInformation StateFlow.
                if (selectedDevice == null || selectedDevice?.deviceId != deviceId) {
                    val name = viewModel.getDeviceName(deviceId) ?: deviceId
                    val reconnected = Device(deviceId = deviceId, address = deviceId, name = name)
                    selectedDevice = reconnected
                    viewModel.selectedDevice = reconnected
                }

                // Fresh component for each connection (handles reconnect cleanly).
                destroyDeviceComponent(deviceId)
                val component = DevicePagerComponent(deviceId).also {
                    devicePagerComponents[deviceId] = it
                    it.setupViews(pagedContainer)
                }

                component.adapter.addOnlineRecordingFragment(deviceId)
                component.adapter.addDeviceSettingsFragment(deviceId)
                if (selectedDevice?.name?.contains("H10") == true) component.adapter.addH10ExerciseFragment(deviceId)

                if (viewModel.isFeatureReady(deviceId, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA)) {
                    component.adapter.addLoggingFragment(deviceId)
                    component.adapter.addActivityFragment(deviceId)
                }
                val cachedEvent = viewModel.uiSdkFeaturesReadyState.value
                if (cachedEvent.identifier == deviceId) sdkFeaturesReadyChange(cachedEvent)

                showPager(deviceId)
                deviceConnectionStatusGroup.visibility = VISIBLE; phoneBleStatus.visibility = GONE
                listenHrBroadcastsButton.visibility = GONE
                connectButton.setBackgroundColor(resources.getColor(R.color.colorButtonConnected, null))
                disconnectButton.visibility = VISIBLE
                selectedDevice?.let { connectedDevices.add(it) }
                disconnectAllButton.visibility = if (connectedDevices.size >= 2) VISIBLE else GONE
            }
            MainViewModel.DeviceConnectionStates.DISCONNECTING_FROM_SELECTED_DEVICE -> {
                connectButton.setText(R.string.search_and_connect_disconnecting)
                sensorState.text = getString(R.string.device_id, selectedDevice?.deviceId)
                connectButton.isEnabled = false; hideAllPagers()
                deviceConnectionStatusGroup.visibility = VISIBLE; phoneBleStatus.visibility = GONE
                listenHrBroadcastsButton.visibility = GONE
                connectButton.setBackgroundColor(resources.getColor(R.color.colorButtonConnecting, null))
            }
            MainViewModel.DeviceConnectionStates.PHONE_BLE_OFF -> {
                showToast(getString(R.string.phone_ble_off))
                searchText.setText(null); searchText.isEnabled = false; connectButton.isEnabled = false
                batteryStatus.text = ""; batteryChargingStatus.text = ""; firmwareVersion.text = ""; sensorState.text = ""
                hideAllPagers()
                connectButton.visibility = GONE; disconnectButton.visibility = GONE; disconnectAllButton.visibility = GONE; listenHrBroadcastsButton.visibility = GONE
                phoneBleStatus.visibility = VISIBLE; deviceConnectionStatusGroup.visibility = GONE
                connectButton.setText(R.string.search_and_connect_search)
            }
        }
    }

    private fun showToast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    private fun resetDisconnectedUi() {
        connectButton.visibility = VISIBLE
        connectButton.setText(R.string.search_and_connect_search)
        connectButton.isEnabled = true
        searchText.isEnabled = true
        connectButton.setBackgroundColor(resources.getColor(R.color.colorButtonConnect, null))
        hideAllPagers()
        disconnectButton.visibility = GONE
        disconnectAllButton.visibility = GONE
        batteryStatus.text = ""; batteryChargingStatus.text = ""
        hidePowerSourceState(); firmwareVersion.text = ""; sensorState.text = ""
        deviceConnectionStatusGroup.visibility = VISIBLE
    }

    private fun showRecoveryGuidance(message: String) {
        recoveryGuidanceDialog?.dismiss()
        val messageView = TextView(requireContext()).apply {
            text = message; setTextIsSelectable(true)
            movementMethod = ScrollingMovementMethod(); setPadding(48, 8, 48, 8)
        }
        recoveryGuidanceDialog = AlertDialog.Builder(requireContext())
            .setTitle("Bluetooth connection requires action")
            .setView(ScrollView(requireContext()).apply { addView(messageView) })
            .setPositiveButton(android.R.string.ok, null).create()
            .also { dialog -> dialog.setOnDismissListener { recoveryGuidanceDialog = null }; dialog.show() }
    }

    private fun offlineRecordingStateChange(offlineRecordingUiState: OfflineRecordingAvailabilityUiState) {
        if (offlineRecordingUiState.isAvailable && offlineRecordingUiState.identifier.isNotEmpty()) {
            devicePagerComponents[offlineRecordingUiState.identifier]?.adapter
                ?.addOfflineRecordingFragment(offlineRecordingUiState.identifier)
        }
    }

    private fun sdkFeaturesReadyChange(event: SdkFeaturesReadyEvent) {
        val deviceId = event.identifier; if (deviceId.isEmpty()) return
        val component = devicePagerComponents[deviceId] ?: return
        if (event.readyFeatures.contains(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA)) {
            component.adapter.addLoggingFragment(deviceId)
            component.adapter.addActivityFragment(deviceId)
        }
    }

    private fun switchToDevice(deviceId: String) {
        viewModel.selectDevice(deviceId)
        showPager(deviceId)
        sensorState.text = getString(R.string.device_id, deviceId)
        deviceConnectionStatusGroup.visibility = VISIBLE; phoneBleStatus.visibility = GONE
        listenHrBroadcastsButton.visibility = GONE
        connectButton.setText(R.string.search_and_connect_connections); connectButton.isEnabled = true
        connectButton.setBackgroundColor(resources.getColor(R.color.colorButtonConnected, null))
        disconnectButton.visibility = VISIBLE; hidePowerSourceState()
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

    private fun togglePowersourceState() {
        if (batteryPresentStatus.visibility != VISIBLE) {
            showPowerSourceState()
        } else {
            hidePowerSourceState()
        }
    }

    private fun hidePowerSourceState() {
        this.batteryPresentStatus.visibility = GONE
        this.wiredPowerSourceConnectedStatus.visibility = GONE
        this.wirelessPowerSourceConnectedStatus.visibility = GONE
    }

    private fun showPowerSourceState() {
        this.batteryPresentStatus.visibility = VISIBLE
        this.wiredPowerSourceConnectedStatus.visibility = VISIBLE
        this.wirelessPowerSourceConnectedStatus.visibility = VISIBLE
    }

    private fun powerSourcesStateReceived(deviceInformationUiState: DeviceInformationUiState) {
        if (connectionState == MainViewModel.DeviceConnectionStates.NOT_CONNECTED) {
            this.batteryPresentStatus.text = ""
            this.wiredPowerSourceConnectedStatus.text = ""
            this.wirelessPowerSourceConnectedStatus.text = ""
            return
        }

        val batteryPresentStatus = when (deviceInformationUiState.powerSourcesState.batteryPresent) {
            BatteryPresentState.PRESENT -> getString(R.string.battery_present_state_present)
            BatteryPresentState.NOT_PRESENT -> getString(R.string.battery_present_state_not_present)
            BatteryPresentState.UNKNOWN -> getString(R.string.battery_present_state_unknown)
        }
        this.batteryPresentStatus.text = batteryPresentStatus

        val wiredExternalPowerSourceConnectedStatus = when (deviceInformationUiState.powerSourcesState.wiredExternalPowerConnected) {
            PowerSourceState.CONNECTED -> getString(R.string.wired_external_power_connected)
            PowerSourceState.NOT_CONNECTED -> getString(R.string.wired_external_power_not_connected)
            PowerSourceState.UNKNOWN -> getString(R.string.wired_external_power_unknown)
            PowerSourceState.RESERVED_FOR_FUTURE_USE -> getString(R.string.wired_external_power_reserved_for_future_use)
        }
        this.wiredPowerSourceConnectedStatus.text = wiredExternalPowerSourceConnectedStatus

        val wirelessExternalPowerSourceConnectedStatus = when (deviceInformationUiState.powerSourcesState.wirelessExternalPowerConnected) {
            PowerSourceState.CONNECTED -> getString(R.string.wireless_external_power_connected)
            PowerSourceState.NOT_CONNECTED -> getString(R.string.wireless_external_power_not_connected)
            PowerSourceState.UNKNOWN -> getString(R.string.wireless_external_power_unknown)
            PowerSourceState.RESERVED_FOR_FUTURE_USE -> getString(R.string.wireless_external_power_reserved_for_future_use)
        }
        this.wirelessPowerSourceConnectedStatus.text = wirelessExternalPowerSourceConnectedStatus
    }
}