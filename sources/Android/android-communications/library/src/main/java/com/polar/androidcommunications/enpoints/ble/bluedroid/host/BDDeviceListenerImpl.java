package com.polar.androidcommunications.enpoints.ble.bluedroid.host;


import static android.bluetooth.BluetoothGatt.GATT_FAILURE;
import static com.polar.androidcommunications.api.ble.model.gatt.BleGattBase.DEFAULT_ATT_MTU_SIZE;
import static com.polar.androidcommunications.common.ble.AndroidBuildUtils.getBrand;
import static com.polar.androidcommunications.common.ble.AndroidBuildUtils.getBuildVersion;
import static com.polar.androidcommunications.common.ble.AndroidBuildUtils.getModel;
import static com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection.ConnectionHandler.MTU_SKIP_NEGOTIATION;
import static com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection.ConnectionHandler.POLAR_PREFERRED_MTU;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanFilter;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import com.polar.androidcommunications.api.ble.BleDeviceListener;
import com.polar.androidcommunications.api.ble.BleLogger;
import com.polar.androidcommunications.api.ble.exceptions.BleInvalidMtu;
import com.polar.androidcommunications.api.ble.exceptions.BleNotAvailableInDevice;
import com.polar.androidcommunications.api.ble.exceptions.BleStartScanError;
import com.polar.androidcommunications.api.ble.model.BleDeviceSession;
import com.polar.androidcommunications.api.ble.model.advertisement.BleAdvertisementContent;
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase;
import com.polar.androidcommunications.common.ble.AtomicSet;
import com.polar.androidcommunications.common.ble.BleUtils;
import com.polar.androidcommunications.common.ble.PhoneUtils;
import com.polar.androidcommunications.common.ble.RxUtils;
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection.ConnectionHandler;
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection.ConnectionHandlerObserver;
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection.ConnectionInterface;
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection.ScannerInterface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.BackpressureOverflowStrategy;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableEmitter;
import io.reactivex.rxjava3.core.FlowableOnSubscribe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

public class BDDeviceListenerImpl extends BleDeviceListener {

    private static final String TAG = BDDeviceListenerImpl.class.getSimpleName();
    private BluetoothAdapter bluetoothAdapter;
    private final BDDeviceList sessions = new BDDeviceList();
    private final GattCallback gattCallback;
    private final BDScanCallback scanCallback;
    private final BluetoothManager btManager;
    private final BDPowerListener powerManager;
    private final BDBondingListener bondingManager;
    private final AtomicSet<FlowableEmitter<? super BleDeviceSession>> observers = new AtomicSet<>();
    private final ConnectionHandler connectionHandler;
    private final Context context;
    private final BehaviorSubject<Pair<BleDeviceSession, BleDeviceSession.DeviceSessionState>> deviceSessionStateSubject = BehaviorSubject.create();
    private BleDeviceSessionStateChangedCallback changedCallback = null;
    private BlePowerStateChangedCallback powerStateChangedCallback = null;
    private int preferredMTU = POLAR_PREFERRED_MTU;

    public BDDeviceListenerImpl(@NonNull final Context context,
                                @NonNull Set<Class<? extends BleGattBase>> clients) throws BleNotAvailableInDevice {
        super(clients);
        this.context = context;
        btManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);

        if (btManager != null) {
            bluetoothAdapter = btManager.getAdapter();
        }
        // Guard
        if (btManager == null || bluetoothAdapter == null) {
            throw new BleNotAvailableInDevice("Device doesn't support BLE");
        }
        connectionHandler = new ConnectionHandler(connectionInterface, scannerInterface, connectionHandlerObserver);
        gattCallback = new GattCallback(connectionHandler, sessions);
        bondingManager = new BDBondingListener(context);
        scanCallback = new BDScanCallback(context, bluetoothAdapter, scanCallbackInterface);
        powerManager = new BDPowerListener(bluetoothAdapter, context, blePowerStateListener);
    }

    @Override
    public boolean bleActive() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    @Override
    public void scanRestart() {
        scanCallback.scanRestart();
    }

    @Override
    public void setScanFilters(@Nullable List<ScanFilter> filters) {
        scanCallback.setScanFilters(filters);
    }

    @Override
    public void setScanPreFilter(@Nullable BleSearchPreFilter filter) {
        this.preFilter = filter;
    }

    @Override
    public void setOpportunisticScan(boolean disable) {
        scanCallback.setOpportunistic(disable);
    }

    @SuppressLint("MissingPermission")
    @NonNull
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
                        },
                        BackpressureStrategy.BUFFER)
                .onBackpressureBuffer(200, () -> BleLogger.w(TAG, "search backpressure buffer full"), BackpressureOverflowStrategy.DROP_OLDEST)
                .doFinally(() -> {
                    observers.remove(subscriber1[0]);
                    scanCallback.clientRemoved();
                });
    }

    @Override
    public void setPreferredMtu(int mtu) throws BleInvalidMtu {
        if (mtu >= 0) {
            preferredMTU = mtu;
        } else {
            throw new BleInvalidMtu();
        }
    }

    @Override
    public int getPreferredMtu() {
        return preferredMTU;
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
    public boolean removeSession(@NonNull BleDeviceSession deviceSession) {
        if (deviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_CLOSED
                && !deviceSession.isAdvertising(30, TimeUnit.SECONDS)
                && sessions.getSessions().contains((BDDeviceSessionImpl) deviceSession)) {
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
    public int removeAllSessions(@NonNull Set<BleDeviceSession.DeviceSessionState> inStates) {
        int count = 0;
        Set<BleDeviceSession> list = sessions.copyDeviceList();
        for (BleDeviceSession session : list) {
            if (inStates.contains(session.getSessionState())
                    && sessions.getSessions().contains((BDDeviceSessionImpl) session)) {
                sessions.getSessions().remove((BDDeviceSessionImpl) session);
                count += 1;
            }
        }
        return count;
    }

    @Override
    public void setPowerMode(@PowerMode int mode) {
        switch (mode) {
            case POWER_MODE_NORMAL:
                scanCallback.stopScan();
                scanCallback.setLowPowerEnabled(false);
                scanCallback.startScan();
                break;
            case POWER_MODE_LOW:
                scanCallback.stopScan();
                scanCallback.setLowPowerEnabled(true);
                scanCallback.startScan();
                break;
        }
    }

    private final ConnectionInterface connectionInterface = new ConnectionInterface() {
        @SuppressLint({"NewApi", "MissingPermission"})
        @Override
        public void connectDevice(final BDDeviceSessionImpl session) {
            BluetoothGatt gatt;
            if (getBuildVersion() >= Build.VERSION_CODES.O) {
                int mask = BluetoothDevice.PHY_LE_1M_MASK;
                if (bluetoothAdapter.isLe2MPhySupported())
                    mask |= BluetoothDevice.PHY_LE_2M_MASK;
                BleLogger.d(TAG, "Attempt connect to device " + session.getName() + " with the mask " + mask);
                gatt = session.getBluetoothDevice().connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE, mask);
            } else {
                BleLogger.d(TAG, "Attempt connect to device " + session.getName());
                gatt = session.getBluetoothDevice().connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
            }
            session.setGatt(gatt);
        }

        @SuppressLint({"MissingPermission", "NewApi"})
        @Override
        public void setPhy(final BDDeviceSessionImpl session) {
            if (session.getGatt() != null) {
                if (getBuildVersion() >= Build.VERSION_CODES.O) {
                    synchronized (session.getGattMutex()) {
                        session.getGatt().setPreferredPhy(BluetoothDevice.PHY_LE_2M_MASK, BluetoothDevice.PHY_LE_2M_MASK, BluetoothDevice.PHY_OPTION_NO_PREFERRED);
                    }
                } else {
                    // PHY is not supported below Android 8.0, call the callback straight
                    gattCallback.onPhyUpdate(session.getGatt(), BluetoothDevice.PHY_LE_1M_MASK, BluetoothDevice.PHY_LE_1M_MASK, 0);
                }
            }
        }

        @SuppressLint({"MissingPermission", "NewApi"})
        @Override
        public void readPhy(BDDeviceSessionImpl session) {
            if (session.getGatt() != null) {
                if (getBuildVersion() >= Build.VERSION_CODES.O) {
                    session.getGatt().readPhy();
                } else {
                    // PHY is not supported below Android 8.0, call the callback straight
                    gattCallback.onPhyUpdate(session.getGatt(), BluetoothDevice.PHY_LE_1M_MASK, BluetoothDevice.PHY_LE_1M_MASK, 0);
                }
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void disconnectDevice(final BDDeviceSessionImpl session) {
            synchronized (session.getGattMutex()) {
                if (session.getGatt() != null) {
                    session.getGatt().disconnect();
                }
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void cancelDeviceConnection(BDDeviceSessionImpl session) {
            synchronized (session.getGattMutex()) {
                if (session.getGatt() != null) {
                    session.getGatt().disconnect();
                }
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public boolean setMtu(BDDeviceSessionImpl session) {
            boolean result = false;
            if (preferredMTU == MTU_SKIP_NEGOTIATION || PhoneUtils.isMtuNegotiationBroken(getBrand(), getModel())) {
                // Do not start MTU request, fall back to default MTU
                if (session.getGatt() != null) {
                    gattCallback.onMtuChanged(session.getGatt(), DEFAULT_ATT_MTU_SIZE, 0);
                }
                result = true;
            } else {
                synchronized (session.getGattMutex()) {
                    if (session.getGatt() != null) {
                        result = session.getGatt().requestMtu(preferredMTU);
                    }
                }
            }
            return result;
        }

        @SuppressLint("MissingPermission")
        @Override
        public boolean startServiceDiscovery(BDDeviceSessionImpl session) {
            boolean result = false;
            synchronized (session.getGattMutex()) {
                if (session.getGatt() != null) {
                    result = session.getGatt().discoverServices();
                }
            }

            // Protection mechanism if service discovery won't complete
            if (session.serviceDiscovery != null) {
                session.serviceDiscovery.dispose();
            }
            session.serviceDiscovery = Completable.timer(10, TimeUnit.SECONDS, Schedulers.newThread())
                    .observeOn(Schedulers.io())
                    .subscribe(
                            () -> {
                                BleLogger.e(BDDeviceListenerImpl.TAG, "service discovery timed out");
                                if (session.getGatt() != null) {
                                    gattCallback.onServicesDiscovered(session.getGatt(), GATT_FAILURE);
                                }
                            },
                            throwable -> {
                                BleLogger.e(BDDeviceListenerImpl.TAG, "service discovery timer failed: " + throwable.getLocalizedMessage());
                                if (session.getGatt() != null) {
                                    gattCallback.onServicesDiscovered(session.getGatt(), GATT_FAILURE);
                                }
                            }
                    );
            return result;
        }

        @Override
        public boolean isPowered() {
            return bleActive();
        }
    };

    private final BDScanCallback.BDScanCallbackInterface scanCallbackInterface = new BDScanCallback.BDScanCallbackInterface() {
        @Override
        public boolean isScanningNeeded() {
            return observers.size() != 0 || sessions.fetch(smartPolarDeviceSession1 -> smartPolarDeviceSession1.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK) != null;
        }

        @Override
        public void scanStartError(@NonNull String error) {
            BleLogger.e(TAG, "scanStartError " + error);
            RxUtils.postError(observers, new BleStartScanError(error));
        }

        @SuppressLint("MissingPermission")
        public void deviceDiscovered(BluetoothDevice device, int rssi, @NonNull byte[] scanRecord, @NonNull BleUtils.EVENT_TYPE type) {
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
                    if (content.getPolarHrAdvertisement().isPresent()
                            && content.getPolarDeviceIdInt() != 0
                            && (content.getPolarDeviceType().equals("H10") || content.getPolarDeviceType().equals("H9"))) {
                        // check if old can be found, NOTE this is a special case for H10 only (random static address)
                        BDDeviceSessionImpl oldSession = sessions.fetch(smartPolarDeviceSession1 -> smartPolarDeviceSession1.getAdvertisementContent().getPolarDeviceId().equals(content.getPolarDeviceId()));
                        if (oldSession != null
                                && (oldSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_CLOSED
                                || oldSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK)) {
                            BleLogger.d(TAG, "old polar device found name: " + oldSession.getAdvertisementContent().getName() + " dev name: " + device.getName() + " old name: " + oldSession.getBluetoothDevice().getName() + " old addr: " + oldSession.getAddress() + " device: " + device);
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
    };

    private final ScannerInterface scannerInterface = new ScannerInterface() {
        @Override
        public void connectionHandlerResumeScanning() {
            scanCallback.startScan();
        }

        @Override
        public void connectionHandlerRequestStopScanning() {
            scanCallback.stopScan();
        }
    };

    private final ConnectionHandlerObserver connectionHandlerObserver = new ConnectionHandlerObserver() {
        @Override
        public void deviceSessionStateChanged(@NonNull BDDeviceSessionImpl session) {
            if (sessions.fetch(smartPolarDeviceSession1 -> smartPolarDeviceSession1.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK) != null) {
                scanCallback.clientAdded();
            } else {
                scanCallback.clientRemoved();
            }
            if (changedCallback != null || deviceSessionStateSubject.hasObservers()) {
                if (session.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK &&
                        session.getPreviousState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN) {
                    //NOTE special case, we were connected so propagate closed event( informal )
                    if (changedCallback != null)
                        changedCallback.stateChanged(session, BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
                    deviceSessionStateSubject.onNext(new Pair<>(session, BleDeviceSession.DeviceSessionState.SESSION_CLOSED));
                    if (session.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK) {
                        if (changedCallback != null)
                            changedCallback.stateChanged(session, BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
                        deviceSessionStateSubject.onNext(new Pair<>(session, BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK));
                    }
                } else {
                    if (changedCallback != null)
                        changedCallback.stateChanged(session, session.getSessionState());
                    deviceSessionStateSubject.onNext(new Pair<>(session, session.getSessionState()));
                }
            }
        }

        @Override
        public void deviceConnected(@NonNull BDDeviceSessionImpl session) {
        }

        @Override
        public void deviceDisconnected(@NonNull BDDeviceSessionImpl session) {
            session.handleDisconnection();
            session.reset();
        }

        @Override
        public void deviceConnectionCancelled(@NonNull BDDeviceSessionImpl session) {
            session.reset();
        }
    };

    private final BDPowerListener.BlePowerState blePowerStateListener = new BDPowerListener.BlePowerState() {
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
                    case SESSION_CLOSING:
                        if (deviceSession.getGatt() != null) {
                            gattCallback.onConnectionStateChange(deviceSession.getGatt(), 0, BluetoothProfile.STATE_DISCONNECTED);
                        }
                        break;
                    default:
                        connectionHandler.deviceDisconnected(deviceSession);
                        break;
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
    };

    @Override
    public void setBlePowerStateCallback(@Nullable BlePowerStateChangedCallback cb) {
        this.powerStateChangedCallback = cb;
        if (cb != null) {
            cb.stateChanged(this.bleActive());
        }
    }

    @Override
    public void openSessionDirect(@NonNull BleDeviceSession session) {
        session.setConnectionUuids(new ArrayList<>());
        connectionHandler.connectDevice((BDDeviceSessionImpl) session, bleActive());
    }

    @Override
    public void openSessionDirect(@NonNull BleDeviceSession session, @NonNull List<String> uuids) {
        session.setConnectionUuids(uuids);
        connectionHandler.connectDevice((BDDeviceSessionImpl) session, bleActive());
    }

    @Override
    public void closeSessionDirect(@NonNull BleDeviceSession session) {
        connectionHandler.disconnectDevice((BDDeviceSessionImpl) session);
    }

    @Override
    public void setAutomaticReconnection(boolean automaticReconnection) {
        connectionHandler.setAutomaticReconnection(automaticReconnection);
    }

    @NonNull
    @Override
    public Observable<Pair<BleDeviceSession, BleDeviceSession.DeviceSessionState>> monitorDeviceSessionState() {
        return deviceSessionStateSubject;
    }
}
