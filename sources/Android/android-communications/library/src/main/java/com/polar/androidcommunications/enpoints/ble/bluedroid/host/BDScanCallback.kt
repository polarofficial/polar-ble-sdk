package com.polar.androidcommunications.enpoints.ble.bluedroid.host

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.ParcelUuid
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.BleHrClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.androidcommunications.common.ble.AndroidBuildUtils.Companion.getBuildVersion
import com.polar.androidcommunications.common.ble.BleUtils.EVENT_TYPE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Predicate
import kotlin.streams.toList

internal class BDScanCallback(
    context: Context,
    private val bluetoothAdapter: BluetoothAdapter,
    private val scanCallbackInterface: BDScanCallbackInterface
) {
    companion object {
        private const val TAG = "BDScanCallback"
        private const val POLAR_MANUFACTURER_ID = 0x006b
        private const val SCAN_WINDOW_LIMIT = 30000
        private const val OPPORTUNISTIC_RESTART_INTERVAL_MS = 30L * 60L * 1000L // 30 minutes
    }

    private var scanFilter: List<ScanFilter?>? = null

    init {
        val defaultFilter: MutableList<ScanFilter> = ArrayList()
        defaultFilter.add(ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(BleHrClient.HR_SERVICE.toString())).build())
        defaultFilter.add(ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(BlePsFtpUtils.RFC77_PFTP_SERVICE.toString())).build())
        defaultFilter.add(ScanFilter.Builder().setManufacturerData(POLAR_MANUFACTURER_ID, byteArrayOf()).build())
        this.scanFilter = defaultFilter
    }

    private enum class ScanAction {
        ENTRY, EXIT, CLIENT_START_SCAN, CLIENT_REMOVED,
        ADMIN_START_SCAN, ADMIN_STOP_SCAN, BLE_POWER_OFF, BLE_POWER_ON
    }

    private enum class ScannerState {
        IDLE, STOPPED, SCANNING
    }

    private val scanPool: MutableList<Long> = CopyOnWriteArrayList()
    private val mainHandler = Handler(context.mainLooper)
    private val scope = CoroutineScope(Dispatchers.IO)
    private var state = ScannerState.IDLE
    var lowPowerEnabled = false
    var opportunistic = true
    private var adminStops = 0

    private var delayJob: Job? = null
    private var opportunisticScanJob: Job? = null

    internal interface BDScanCallbackInterface {
        fun deviceDiscovered(device: BluetoothDevice, rssi: Int, scanRecord: ByteArray, type: EVENT_TYPE)
        fun scanStartError(error: String)
        fun isScanningNeeded(): Boolean
    }

    fun setScanFilters(filters: List<ScanFilter?>?) {
        stopScan()
        this.scanFilter = filters
        startScan()
    }

    fun scanRestart() {
        stopScan()
        startScan()
    }

    fun clientAdded() {
        commandState(ScanAction.CLIENT_START_SCAN)
    }

    fun clientRemoved() {
        commandState(ScanAction.CLIENT_REMOVED)
    }

    fun stopScan() {
        commandState(ScanAction.ADMIN_STOP_SCAN)
    }

    fun startScan() {
        commandState(ScanAction.ADMIN_START_SCAN)
    }

    fun powerOn() {
        commandState(ScanAction.BLE_POWER_ON)
    }

    fun powerOff() {
        commandState(ScanAction.BLE_POWER_OFF)
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val bytes = result.scanRecord?.bytes ?: byteArrayOf()
            scanCallbackInterface.deviceDiscovered(result.device, result.rssi, bytes, fetchAdvType(result))
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            for (result in results) {
                val bytes = result.scanRecord?.bytes ?: byteArrayOf()
                scanCallbackInterface.deviceDiscovered(result.device, result.rssi, bytes, fetchAdvType(result))
            }
        }

        override fun onScanFailed(errorCode: Int) {
            val scanFailureString = "Scan start failed to ScanCallback errorCode: $errorCode"
            BleLogger.e(TAG, scanFailureString)
            scanCallbackInterface.scanStartError(scanFailureString)
        }
    }

    private fun commandState(action: ScanAction) {
        BleLogger.d(TAG, "commandState state:$state action: $action")
        when (state) {
            ScannerState.IDLE -> scannerIdleState(action)
            ScannerState.STOPPED -> scannerAdminState(action)
            ScannerState.SCANNING -> scannerScanningState(action)
        }
    }

    private fun changeState(newState: ScannerState) {
        commandState(ScanAction.EXIT)
        state = newState
        commandState(ScanAction.ENTRY)
    }

    private fun scannerIdleState(action: ScanAction) {
        when (action) {
            ScanAction.ENTRY -> {
                if (bluetoothAdapter.isEnabled && scanCallbackInterface.isScanningNeeded()) {
                    changeState(ScannerState.SCANNING)
                }
            }
            ScanAction.EXIT,
            ScanAction.ADMIN_START_SCAN,
            ScanAction.CLIENT_REMOVED,
            ScanAction.BLE_POWER_OFF -> { /* no-op */ }
            ScanAction.CLIENT_START_SCAN -> {
                if (bluetoothAdapter.isEnabled) {
                    if (scanCallbackInterface.isScanningNeeded()) {
                        changeState(ScannerState.SCANNING)
                    }
                } else {
                    BleLogger.d(TAG, "Skipped scan start, because of ble power off")
                }
            }
            ScanAction.ADMIN_STOP_SCAN -> {
                changeState(ScannerState.STOPPED)
            }
            ScanAction.BLE_POWER_ON -> {
                if (scanCallbackInterface.isScanningNeeded()) {
                    changeState(ScannerState.SCANNING)
                }
            }
        }
    }

    private fun scannerAdminState(action: ScanAction) {
        when (action) {
            ScanAction.ENTRY -> { adminStops = 1 }
            ScanAction.EXIT -> { adminStops = 0 }
            ScanAction.ADMIN_START_SCAN -> {
                --adminStops
                if (adminStops <= 0) {
                    changeState(ScannerState.IDLE)
                } else {
                    BleLogger.d(TAG, "Waiting admins to call start c: $adminStops")
                }
            }
            ScanAction.ADMIN_STOP_SCAN -> { ++adminStops }
            ScanAction.BLE_POWER_OFF -> { changeState(ScannerState.IDLE) }
            ScanAction.CLIENT_REMOVED,
            ScanAction.CLIENT_START_SCAN,
            ScanAction.BLE_POWER_ON -> { /* no-op */ }
        }
    }

    private fun scannerScanningState(action: ScanAction) {
        when (action) {
            ScanAction.ENTRY -> { startScanning() }
            ScanAction.EXIT -> {
                stopScanning()
                opportunisticScanJob?.cancel()
                opportunisticScanJob = null
            }
            ScanAction.CLIENT_REMOVED -> {
                if (!scanCallbackInterface.isScanningNeeded()) {
                    changeState(ScannerState.IDLE)
                }
            }
            ScanAction.ADMIN_STOP_SCAN -> { changeState(ScannerState.STOPPED) }
            ScanAction.BLE_POWER_OFF -> { changeState(ScannerState.IDLE) }
            ScanAction.BLE_POWER_ON -> {
                BleLogger.e(TAG, "INCORRECT event received in scanning state: $action")
            }
            ScanAction.CLIENT_START_SCAN,
            ScanAction.ADMIN_START_SCAN -> { /* no-op */ }
        }
    }

    private fun startScanning() {
        if (scanPool.isNotEmpty()) {
            val elapsed = System.currentTimeMillis() - scanPool[0]
            if (scanPool.size > 3 && elapsed < SCAN_WINDOW_LIMIT) {
                val sift = SCAN_WINDOW_LIMIT - elapsed + 200
                BleLogger.d(TAG, "Prevent scanning too frequently delay: ${sift}ms elapsed: ${elapsed}ms")
                delayJob?.cancel()
                delayJob = scope.launch {
                    delay(sift)
                    mainHandler.post {
                        BleLogger.d(TAG, "delayed scan starting")
                        if (scanPool.isNotEmpty()) scanPool.removeAt(0)
                        startLScan()
                    }
                }
                return
            }
        }
        BleLogger.d(TAG, "timestamps left: " + scanPool.size)
        startLScan()
    }

    @SuppressLint("NewApi")
    private fun fetchAdvType(result: ScanResult): EVENT_TYPE {
        var type = EVENT_TYPE.ADV_IND
        if (getBuildVersion() >= Build.VERSION_CODES.O && !result.isConnectable) {
            type = EVENT_TYPE.ADV_NONCONN_IND
        }
        return type
    }

    private fun startLScan() {
        BleLogger.d(TAG, "Scan started -->")
        val scanSettings: ScanSettings = if (!lowPowerEnabled) {
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        } else {
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()
        }
        try {
            callStartScanL(scanSettings)
            if (opportunistic) {
                opportunisticScanJob?.cancel()
                opportunisticScanJob = scope.launch {
                    while (true) {
                        delay(OPPORTUNISTIC_RESTART_INTERVAL_MS)
                        mainHandler.post {
                            BleLogger.d(TAG, "RESTARTING scan to avoid opportunistic")
                            stopScanning()
                            callStartScanL(scanSettings)
                        }
                    }
                }
            }
            BleLogger.d(TAG, "Scan started <--")
        } catch (ex: NullPointerException) {
            BleLogger.e(TAG, "startScan did throw null pointer exception")
            changeState(ScannerState.IDLE)
        }
    }

    @SuppressLint("MissingPermission", "NewApi")
    private fun callStartScanL(scanSettings: ScanSettings) {
        try {
            bluetoothAdapter.bluetoothLeScanner.startScan(scanFilter, scanSettings, leScanCallback)
        } catch (e: Exception) {
            val errorString = "Failed to start scan. Reason: ${e.message}"
            BleLogger.e(TAG, errorString)
            scanCallbackInterface.scanStartError(errorString)
            changeState(ScannerState.IDLE)
            return
        }
        val isWithinScanWindow = Predicate<Long> { aLong -> System.currentTimeMillis() - aLong < SCAN_WINDOW_LIMIT }
        val scanWindowList = scanPool.stream().filter(isWithinScanWindow).toList()
        scanPool.clear()
        scanPool.addAll(scanWindowList)
        scanPool.add(System.currentTimeMillis())
    }

    @SuppressLint("MissingPermission")
    private fun stopScanning() {
        BleLogger.d(TAG, "Stop scanning")
        delayJob?.cancel()
        delayJob = null
        try {
            bluetoothAdapter.bluetoothLeScanner.stopScan(leScanCallback)
        } catch (ex: Exception) {
            BleLogger.e(TAG, "stopScan did throw exception: " + ex.localizedMessage)
        }
    }
}