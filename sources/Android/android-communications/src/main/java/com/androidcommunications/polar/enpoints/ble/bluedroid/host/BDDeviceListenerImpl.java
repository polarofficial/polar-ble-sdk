package com.androidcommunications.polar.enpoints.ble.bluedroid.host;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanFilter;
import android.content.Context;
import android.os.Build;

import androidx.annotation.Nullable;

import com.androidcommunications.polar.api.ble.BleDeviceListener;
import com.androidcommunications.polar.api.ble.BleLogger;
import com.androidcommunications.polar.api.ble.exceptions.BleStartScanError;
import com.androidcommunications.polar.api.ble.model.BleDeviceSession;
import com.androidcommunications.polar.api.ble.model.advertisement.BleAdvertisementContent;
import com.androidcommunications.polar.api.ble.model.gatt.BleGattBase;
import com.androidcommunications.polar.common.ble.AtomicSet;
import com.androidcommunications.polar.common.ble.BleUtils;
import com.androidcommunications.polar.common.ble.RxUtils;
import com.androidcommunications.polar.enpoints.ble.bluedroid.host.connection.ConnectionHandler;
import com.androidcommunications.polar.enpoints.ble.bluedroid.host.connection.ConnectionHandlerObserver;
import com.androidcommunications.polar.enpoints.ble.bluedroid.host.connection.ConnectionInterface;
import com.androidcommunications.polar.enpoints.ble.bluedroid.host.connection.ScannerInterface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.BackpressureOverflowStrategy;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableEmitter;
import io.reactivex.rxjava3.core.FlowableOnSubscribe;

public class BDDeviceListenerImpl extends BleDeviceListener implements
        BDScanCallback.BDScanCallbackInterface,
        ScannerInterface,
        ConnectionInterface,
        BDPowerListener.BlePowerState,
        ConnectionHandlerObserver {

    private final static String TAG = BDDeviceListenerImpl.class.getSimpleName();
    private BluetoothAdapter bluetoothAdapter;
    private BDDeviceList sessions = new BDDeviceList();
    private BDGattCallback gattCallback;
    private BDScanCallback scanCallback;
    private BluetoothManager btManager;
    private BDPowerListener powerManager;
    private BDBondingListener bondingManager;
    private AtomicSet<FlowableEmitter<? super BleDeviceSession>> observers = new AtomicSet<>();
    private ConnectionHandler connectionHandler;
    private Context context;
    private BleDeviceSessionStateChangedCallback changedCallback = null;
    private BlePowerStateChangedCallback powerStateChangedCallback = null;

    public BDDeviceListenerImpl(
            @NonNull final Context context,
            @NonNull Set<Class<? extends BleGattBase>> clients) {
        super(clients);
        this.context = context;
        btManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (btManager != null) {
            bluetoothAdapter = btManager.getAdapter();
        }
        connectionHandler = new ConnectionHandler(this, this, this);
        gattCallback = new BDGattCallback(context, connectionHandler, sessions);
        bondingManager = new BDBondingListener(context);
        scanCallback = new BDScanCallback(context, btManager, this);
        powerManager = new BDPowerListener(bluetoothAdapter, context, this);
    }

    @Override
    public boolean bleActive() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    @Override
    public void setScanFilters(final List<ScanFilter> filters) {
        scanCallback.setScanFilters(filters);
    }

    @Override
    public void setScanPreFilter(BleSearchPreFilter filter) {
        this.preFilter = filter;
    }

    @Override
    public void setOpportunisticScan(boolean disable) {
        scanCallback.setOpportunistic(disable);
    }

    @Override
    public Flowable<BleDeviceSession> search(final boolean fetchKnownDevices) {
        final FlowableEmitter<BleDeviceSession>[] subscriber1 = new FlowableEmitter[1];
        return Flowable.create((FlowableOnSubscribe<BleDeviceSession>) subscriber -> {
            if (fetchKnownDevices) {
                List<BluetoothDevice> devices =
                        btManager.getDevicesMatchingConnectionStates(BluetoothProfile.GATT,
                                new int[]{BluetoothProfile.STATE_CONNECTED | BluetoothProfile.STATE_CONNECTING});
                for (BluetoothDevice device : devices) {
                    if (device.getType() == BluetoothDevice.DEVICE_TYPE_LE && sessions.getSession(device) == null) {
                        BDDeviceSessionImpl newDevice = new BDDeviceSessionImpl(context, device, scanCallback, bondingManager, factory);
                        sessions.addSession(newDevice);
                    }
                }
                Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
                for (BluetoothDevice device : bondedDevices) {
                    if (device.getType() == BluetoothDevice.DEVICE_TYPE_LE && sessions.getSession(device) == null) {
                        BDDeviceSessionImpl newDevice = new BDDeviceSessionImpl(context, device, scanCallback, bondingManager, factory);
                        sessions.addSession(newDevice);
                    }
                }
                Set<BleDeviceSession> sessionsList = sessions.copyDeviceList();
                for (BleDeviceSession deviceSession : sessionsList) {
                    subscriber.onNext(deviceSession);
                }
            }

            subscriber1[0] = subscriber;
            observers.add(subscriber);
            scanCallback.clientAdded();
        }, BackpressureStrategy.BUFFER).onBackpressureBuffer(200, () -> {
            // do nothing
            BleLogger.w(TAG, "search backpressure buffer full");
        }, BackpressureOverflowStrategy.DROP_OLDEST).doFinally(() -> {
            observers.remove(subscriber1[0]);
            scanCallback.clientRemoved();
        });
    }

    @Override
    public void setMtu(int mtu) {
        gattCallback.setPolarMaxMtu(mtu);
    }

    @Override
    public void shutDown() {
        bondingManager.stopBroadcastReceiver();
        powerManager.stopBroadcastReceiver();
        scanCallback.stopScan();
        sessions.getSessions().accessAll(BDDeviceSessionImpl::resetGatt);
        sessions.getSessions().clear();
    }

    @Override
    public Set<BleDeviceSession> deviceSessions() {
        return sessions.copyDeviceList();
    }

    @Override
    public BleDeviceSession sessionByAddress(String address) {
        BDDeviceSessionImpl session = sessions.getSession(address);
        if (session == null) {
            session = new BDDeviceSessionImpl(context, bluetoothAdapter.getRemoteDevice(address), scanCallback, bondingManager, factory);
            sessions.addSession(session);
        }
        return session;
    }

    @Override
    public boolean removeSession(BleDeviceSession deviceSession) {
        if (deviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_CLOSED &&
                !deviceSession.isAdvertising(30, TimeUnit.SECONDS) &&
                sessions.getSessions().contains((BDDeviceSessionImpl) deviceSession)) {
            sessions.getSessions().remove((BDDeviceSessionImpl) deviceSession);
            return true;
        }
        return false;
    }

    @Override
    public int removeAllSessions() {
        return removeAllSessions(new HashSet<>(Collections.singletonList(BleDeviceSession.DeviceSessionState.SESSION_CLOSED)));
    }

    @Override
    public int removeAllSessions(Set<BleDeviceSession.DeviceSessionState> inStates) {
        int count = 0;
        Set<BleDeviceSession> list = sessions.copyDeviceList();
        for (BleDeviceSession session : list) {
            if (inStates.contains(session.getSessionState()) &&
                    sessions.getSessions().contains((BDDeviceSessionImpl) session)) {
                sessions.getSessions().remove((BDDeviceSessionImpl) session);
                count += 1;
            }
        }
        return count;
    }

    @Override
    public void connectDevice(final BDDeviceSessionImpl session) {
        BluetoothGatt gatt;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                int mask = BluetoothDevice.PHY_LE_1M_MASK;
                if (bluetoothAdapter.isLe2MPhySupported()) mask |= BluetoothDevice.PHY_LE_2M_MASK;
                gatt = session.getBluetoothDevice().connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE, mask);
            } else {
                gatt = session.getBluetoothDevice().connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
            }
        } else {
            gatt = session.getBluetoothDevice().connectGatt(context, false, gattCallback);
        }
        synchronized (session.getGattMutex()) {
            session.setGatt(gatt);
        }
    }

    @Override
    public void disconnectDevice(final BDDeviceSessionImpl session) {
        synchronized (session.getGattMutex()) {
            if (session.getGatt() != null) {
                session.getGatt().disconnect();
            }
        }
    }

    @Override
    public void cancelDeviceConnection(BDDeviceSessionImpl session) {
        synchronized (session.getGattMutex()) {
            if (session.getGatt() != null) {
                session.getGatt().disconnect();
            }
        }
    }

    @Override
    public boolean isPowered() {
        return bleActive();
    }

    @Override
    public void connectionHandlerResumeScanning() {
        scanCallback.startScan();
    }

    @Override
    public void connectionHandlerRequestStopScanning() {
        scanCallback.stopScan();
    }

    @Override
    public void setPowerMode(@PowerMode int mode) {
        switch (mode) {
            case POWER_MODE_NORMAL: {
                scanCallback.stopScan();
                scanCallback.setLowPowerEnabled(false);
                scanCallback.startScan();
                break;
            }
            case POWER_MODE_LOW: {
                scanCallback.stopScan();
                scanCallback.setLowPowerEnabled(true);
                scanCallback.startScan();
                break;
            }
        }
    }

    @Override
    public void deviceDiscovered(BluetoothDevice device, int rssi, byte[] scanRecord, BleUtils.EVENT_TYPE type) {
        BDDeviceSessionImpl deviceSession = sessions.getSession(device);
        HashMap<BleUtils.AD_TYPE, byte[]> advData = BleUtils.advertisementBytes2Map(scanRecord);
        final String manufacturer = Build.MANUFACTURER;
        final String name = device.getName();
        if (name != null && manufacturer.equalsIgnoreCase("samsung")) {
            // BIG NOTE, this is workaround for uber stupid samsung bug where they mess up whoms advertisement data belongs to who
            advData.remove(BleUtils.AD_TYPE.GAP_ADTYPE_LOCAL_NAME_SHORT);
            advData.put(BleUtils.AD_TYPE.GAP_ADTYPE_LOCAL_NAME_COMPLETE, name.getBytes());
        }

        if (deviceSession == null) {
            final BleAdvertisementContent content = new BleAdvertisementContent();
            content.processAdvertisementData(advData, type, rssi);
            if (preFilter == null || preFilter.process(content)) {
                if (content.getPolarHrAdvertisement().isPresent() &&
                        content.getPolarDeviceIdInt() != 0 &&
                        (content.getPolarDeviceType().equals("H10") ||
                                content.getPolarDeviceType().equals("H9"))) {
                    // check if old can be found, NOTE this is a special case for H10 only (random static address)
                    BDDeviceSessionImpl oldSession = sessions.fetch(smartPolarDeviceSession1 -> smartPolarDeviceSession1.getAdvertisementContent().getPolarDeviceId().equals(content.getPolarDeviceId()));
                    if (oldSession != null && (oldSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_CLOSED ||
                            oldSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK)) {
                        BleLogger.d(TAG, "old polar device found name: " + oldSession.getAdvertisementContent().getName() + " dev name: " + device.getName() + " old name: " + oldSession.getBluetoothDevice().getName() + " old addr: " + oldSession.getAddress() + " device: " + device.toString());
                        oldSession.setBluetoothDevice(device);
                        deviceSession = oldSession;
                        deviceSession.getAdvertisementContent().processAdvertisementData(advData, type, rssi);
                    }
                }
                if (deviceSession == null) {
                    deviceSession = new BDDeviceSessionImpl(context, device, scanCallback, bondingManager, factory);
                    deviceSession.getAdvertisementContent().processAdvertisementData(advData, type, rssi);
                    BleLogger.d(TAG, "new device allocated name: " + deviceSession.getAdvertisementContent().getName());
                    sessions.addSession(deviceSession);
                }
            } else {
                // device is not desired
                return;
            }
        } else {
            deviceSession.getAdvertisementContent().processAdvertisementData(advData, type, rssi);
        }

        connectionHandler.advertisementHeadReceived(deviceSession);
        final BDDeviceSessionImpl finalDeviceSession = deviceSession;
        RxUtils.emitNext(observers, object -> object.onNext(finalDeviceSession));
    }

    @Override
    public void scanStartError(int error) {
        RxUtils.postError(observers, new BleStartScanError("scan start failed ", error));
    }

    @Override
    public boolean isScanningNeeded() {
        return observers.size() != 0 || sessions.fetch(smartPolarDeviceSession1 -> smartPolarDeviceSession1.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK) != null;
    }

    @Override
    public void setBlePowerStateCallback(@Nullable BlePowerStateChangedCallback cb) {
        this.powerStateChangedCallback = cb;
        if (cb != null) {
            cb.stateChanged(this.bleActive());
        }
    }

    @Override
    public void setDeviceSessionStateChangedCallback(@Nullable BleDeviceSessionStateChangedCallback changedCallback) {
        this.changedCallback = changedCallback;
    }

    @Override
    public void openSessionDirect(BleDeviceSession session) {
        session.setConnectionUuids(new ArrayList<>());
        connectionHandler.connectDevice((BDDeviceSessionImpl) session, bleActive());
    }

    @Override
    public void openSessionDirect(BleDeviceSession session, List<String> uuids) {
        session.setConnectionUuids(uuids);
        connectionHandler.connectDevice((BDDeviceSessionImpl) session, bleActive());
    }

    @Override
    public void closeSessionDirect(BleDeviceSession session) {
        connectionHandler.disconnectDevice((BDDeviceSessionImpl) session);
    }

    @Override
    public void setAutomaticReconnection(boolean automaticReconnection) {
        connectionHandler.setAutomaticReconnection(automaticReconnection);
    }

    @Override
    public void blePoweredOff() {
        BleLogger.e(TAG, "BLE powered off");
        scanCallback.powerOff();
        if (powerStateChangedCallback != null) {
            powerStateChangedCallback.stateChanged(false);
        }
        for (BDDeviceSessionImpl deviceSession : sessions.getSessions().objects()) {
            switch (deviceSession.getSessionState()) {
                case SESSION_OPEN:
                case SESSION_OPENING:
                case SESSION_CLOSING: {
                    gattCallback.onConnectionStateChange(deviceSession.getGatt(), 0, BluetoothGatt.STATE_DISCONNECTED);
                    break;
                }
                default: {
                    connectionHandler.deviceDisconnected(deviceSession);
                    break;
                }
            }
        }
    }

    @Override
    public void blePoweredOn() {
        BleLogger.d(TAG, "BLE powered on");
        scanCallback.powerOn();
        if (powerStateChangedCallback != null) {
            powerStateChangedCallback.stateChanged(true);
        }
    }

    @Override
    public void deviceSessionStateChanged(BDDeviceSessionImpl session) {
        if (sessions.fetch(smartPolarDeviceSession1 -> smartPolarDeviceSession1.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK) != null) {
            scanCallback.clientAdded();
        } else {
            scanCallback.clientRemoved();
        }
        if (changedCallback != null) {
            if (session.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK &&
                    session.getPreviousState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN) {
                //NOTE special case, we were connected so propagate closed event( informal )
                changedCallback.stateChanged(session, BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
                if (session.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK) {
                    changedCallback.stateChanged(session, BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
                }
            } else {
                changedCallback.stateChanged(session, session.getSessionState());
            }
        }
    }

    @Override
    public void deviceConnected(BDDeviceSessionImpl session) {

    }

    @Override
    public void deviceDisconnected(BDDeviceSessionImpl session) {
        session.handleDisconnection();
        session.reset();
    }

    @Override
    public void deviceConnectionCancelled(BDDeviceSessionImpl session) {
        session.reset();
    }
}
