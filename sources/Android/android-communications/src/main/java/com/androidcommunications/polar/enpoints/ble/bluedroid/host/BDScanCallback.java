package com.androidcommunications.polar.enpoints.ble.bluedroid.host;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;

import com.androidcommunications.polar.api.ble.BleLogger;
import com.androidcommunications.polar.common.ble.BleUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

class BDScanCallback extends ScanCallback {

    private enum ScanAction {
        ENTRY,
        EXIT,
        CLIENT_START_SCAN,
        CLIENT_REMOVED,
        ADMIN_START_SCAN,
        ADMIN_STOP_SCAN,
        BLE_POWER_OFF,
        BLE_POWER_ON
    }

    private enum ScannerState {
        IDLE,
        STOPPED,
        SCANNING
    }

    private final static String TAG = BDScanCallback.class.getSimpleName();
    private BluetoothAdapter bluetoothAdapter;
    private ScannerState state = ScannerState.IDLE;
    private boolean lowPowerEnabled = false;
    private int adminStops = 0;
    private Disposable timer = null;
    private List<ScanFilter> filters = null;
    private List<Long> scanPool = new ArrayList<>();
    private Scheduler delayScheduler;
    private Disposable delaySubscription;
    private Disposable opportunisticScanTimer;
    private boolean opportunistic = true;

    // scan window limit, for android's "is scanning too frequently"
    private final static int SCAN_WINDOW_LIMIT = 30000;

    public interface BDScanCallbackInterface {
        void deviceDiscovered(final BluetoothDevice device, int rssi, byte[] scanRecord, BleUtils.EVENT_TYPE type);

        void scanStartError(int error);

        boolean isScanningNeeded();
    }

    private BDScanCallbackInterface scanCallbackInterface;

    BDScanCallback(Context context,
                   BluetoothManager btManager,
                   BDScanCallbackInterface scanCallbackInterface) {
        this.scanCallbackInterface = scanCallbackInterface;
        this.delayScheduler = AndroidSchedulers.from(context.getMainLooper());
        this.bluetoothAdapter = btManager.getAdapter();
    }

    void setOpportunistic(boolean opportunistic) {
        this.opportunistic = opportunistic;
    }

    void setLowPowerEnabled(boolean lowPowerEnabled) {
        this.lowPowerEnabled = lowPowerEnabled;
    }

    void setScanFilters(final List<ScanFilter> filters) {
        stopScan();
        this.filters = filters;
        startScan();
    }

    void clientAdded() {
        commandState(ScanAction.CLIENT_START_SCAN);
    }

    void clientRemoved() {
        commandState(ScanAction.CLIENT_REMOVED);
    }

    void stopScan() {
        commandState(ScanAction.ADMIN_STOP_SCAN);
    }

    void startScan() {
        commandState(ScanAction.ADMIN_START_SCAN);
    }

    void powerOn() {
        commandState(ScanAction.BLE_POWER_ON);
    }

    void powerOff() {
        commandState(ScanAction.BLE_POWER_OFF);
    }

    private void commandState(ScanAction action) {
        BleLogger.d(TAG, "commandState state:" + state.toString() + " action: " + action.toString());
        switch (state) {
            case IDLE: {
                scannerIdleState(action);
                break;
            }
            case STOPPED: {
                scannerAdminState(action);
                break;
            }
            case SCANNING: {
                scannerScanningState(action);
                break;
            }
        }
    }

    private void changeState(ScannerState newState) {
        commandState(ScanAction.EXIT);
        this.state = newState;
        commandState(ScanAction.ENTRY);
    }

    private void scannerIdleState(ScanAction action) {
        switch (action) {
            case ENTRY: {
                if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                    if (scanCallbackInterface.isScanningNeeded()) {
                        changeState(ScannerState.SCANNING);
                    }
                }
                break;
            }
            case EXIT:
            case ADMIN_START_SCAN:
            case BLE_POWER_OFF: {
                break;
            }
            case CLIENT_START_SCAN: {
                if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                    if (scanCallbackInterface.isScanningNeeded()) {
                        changeState(ScannerState.SCANNING);
                    }
                } else {
                    BleLogger.d(TAG, "Skipped scan start, because of ble power off");
                }
                break;
            }
            case ADMIN_STOP_SCAN: {
                changeState(ScannerState.STOPPED);
                break;
            }
            case BLE_POWER_ON: {
                if (scanCallbackInterface.isScanningNeeded()) {
                    // if there is atleast one client waiting
                    changeState(ScannerState.SCANNING);
                }
                break;
            }
        }
    }

    private void scannerAdminState(ScanAction action) {
        // forced stopped state
        switch (action) {
            case ENTRY: {
                adminStops = 1;
                break;
            }
            case EXIT: {
                adminStops = 0;
                break;
            }
            case ADMIN_START_SCAN: {
                // go through idle state back to scanning, if needed
                --adminStops;
                if (adminStops <= 0) {
                    changeState(ScannerState.IDLE);
                } else {
                    BleLogger.d(TAG, "Waiting admins to call start c: " + adminStops);
                }
                break;
            }
            case ADMIN_STOP_SCAN: {
                ++adminStops;
                break;
            }
            case BLE_POWER_OFF: {
                changeState(ScannerState.IDLE);
                break;
            }
            case CLIENT_REMOVED:
            case CLIENT_START_SCAN:
            case BLE_POWER_ON: {
                // do nothing
                break;
            }
        }
    }

    private void scannerScanningState(ScanAction action) {
        switch (action) {
            case ENTRY: {
                // start scanning
                startScanning();
                break;
            }
            case EXIT: {
                // stop scanning
                stopScanning();
                if (opportunisticScanTimer != null) {
                    opportunisticScanTimer.dispose();
                    opportunisticScanTimer = null;
                }
                if (timer != null) {
                    timer.dispose();
                    timer = null;
                }
                break;
            }
            case CLIENT_START_SCAN: {
                // do nothing
                break;
            }
            case CLIENT_REMOVED: {
                if (!scanCallbackInterface.isScanningNeeded()) {
                    // scanning is not needed anymore
                    changeState(ScannerState.IDLE);
                }
                break;
            }
            case ADMIN_STOP_SCAN: {
                changeState(ScannerState.STOPPED);
                break;
            }
            case BLE_POWER_OFF: {
                changeState(ScannerState.IDLE);
                break;
            }
            case ADMIN_START_SCAN:
                // skip
                break;
            case BLE_POWER_ON: {
                // should not happen
                BleLogger.e(TAG, "INCORRECT event received in scanning state: " + action);
                break;
            }
        }
    }

    @Override
    public void onScanResult(int callbackType, ScanResult result) {
        scanCallbackInterface.deviceDiscovered(result.getDevice(), result.getRssi(), result.getScanRecord() != null ? result.getScanRecord().getBytes() : new byte[]{}, fetchAdvType(result));
    }

    @Override
    public void onBatchScanResults(List<ScanResult> results) {
        for (ScanResult result : results) {
            scanCallbackInterface.deviceDiscovered(result.getDevice(), result.getRssi(), result.getScanRecord() != null ? result.getScanRecord().getBytes() : new byte[]{}, fetchAdvType(result));
        }
    }

    @Override
    public void onScanFailed(int errorCode) {
        BleLogger.e(TAG, "START scan error: " + errorCode);
        scanCallbackInterface.scanStartError(errorCode);
    }

    @SuppressLint("CheckResult")
    private void startScanning() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (scanPool.size() != 0) {
                long elapsed = System.currentTimeMillis() - scanPool.get(0);
                if (scanPool.size() > 3 && elapsed < SCAN_WINDOW_LIMIT) {
                    long sift = (SCAN_WINDOW_LIMIT - elapsed) + 200;
                    BleLogger.d(TAG, "Prevent scanning too frequently delay: " + sift + "ms" + " elapsed: " + elapsed + "ms");
                    if (delaySubscription != null) {
                        delaySubscription.dispose();
                        delaySubscription = null;
                    }
                    delaySubscription = Observable.timer(sift, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.io()).observeOn(delayScheduler).subscribe(
                            aLong -> {
                                // do nothing
                            },
                            throwable -> BleLogger.e(TAG, "timer failed: " + throwable.getLocalizedMessage()),
                            () -> {
                                BleLogger.d(TAG, "delayed scan starting");
                                if (scanPool.size() != 0) scanPool.remove(0);
                                startLScan();
                            });
                    return;
                }
            }
            BleLogger.d(TAG, "timestamps left: " + scanPool.size());
            startLScan();
        } else {
            startLScan();
        }
    }

    private BleUtils.EVENT_TYPE fetchAdvType(ScanResult result) {
        BleUtils.EVENT_TYPE type = BleUtils.EVENT_TYPE.ADV_IND;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !result.isConnectable()) {
            type = BleUtils.EVENT_TYPE.ADV_NONCONN_IND;
        }
        return type;
    }

    @SuppressLint("NewApi")
    private void startLScan() {
        BleLogger.d(TAG, "Scan started -->");
        final ScanSettings scanSettings;
        if (!lowPowerEnabled) {
            scanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        } else {
            scanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build();
        }
        try {
            callStartScanL(scanSettings);
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && opportunistic) {
                opportunisticScanTimer = Observable.interval(30, TimeUnit.MINUTES).subscribeOn(Schedulers.io()).observeOn(delayScheduler).subscribe(
                        aLong -> {
                            BleLogger.d(TAG, "RESTARTING scan to avoid opportunistic");
                            stopScanning();
                            callStartScanL(scanSettings);
                        },
                        throwable -> BleLogger.e(TAG, "TIMER failed: " + throwable.getLocalizedMessage()),
                        () -> {
                            // non produced
                        }
                );
            }
            BleLogger.d(TAG, "Scan started <--");
        } catch (NullPointerException ex) {
            BleLogger.e(TAG, "startScan did throw null pointer exception");
            changeState(ScannerState.IDLE);
        }
    }

    @SuppressLint("NewApi")
    private void callStartScanL(ScanSettings scanSettings) {
        try {
            bluetoothAdapter.getBluetoothLeScanner().startScan(filters, scanSettings, this);
        } catch (Exception e) {
            BleLogger.e(TAG, "Failed to start scan e: " + e.getLocalizedMessage());
            changeState(ScannerState.IDLE);
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            scanPool.removeIf(aLong -> (System.currentTimeMillis() - aLong) >= SCAN_WINDOW_LIMIT);
            scanPool.add(System.currentTimeMillis());
        }
    }

    @SuppressLint("CheckResult")
    private void stopScanning() {
        BleLogger.d(TAG, "Stop scanning");
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (delaySubscription != null) {
                delaySubscription.dispose();
                delaySubscription = null;
            }
        }
        try {
            bluetoothAdapter.getBluetoothLeScanner().stopScan(this);
        } catch (Exception ex) {
            BleLogger.e(TAG, "stopScan did throw exception: " + ex.getLocalizedMessage());
        }
    }
}