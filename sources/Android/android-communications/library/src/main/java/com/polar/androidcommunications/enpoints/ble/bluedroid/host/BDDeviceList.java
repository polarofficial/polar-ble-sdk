package com.polar.androidcommunications.enpoints.ble.bluedroid.host;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;

import com.polar.androidcommunications.api.ble.BleLogger;
import com.polar.androidcommunications.api.ble.model.BleDeviceSession;
import com.polar.androidcommunications.common.ble.AtomicSet;

import java.util.HashSet;
import java.util.Set;

class BDDeviceList {

    private static final String TAG = BDDeviceList.class.getSimpleName();
    private final AtomicSet<BDDeviceSessionImpl> sessions = new AtomicSet<>();

    AtomicSet<BDDeviceSessionImpl> getSessions() {
        return sessions;
    }

    BDDeviceSessionImpl getSession(final BluetoothDevice device) {
        return sessions.fetch(object -> object.getBluetoothDevice().getAddress().equals(device.getAddress()));
    }

    void addSession(BDDeviceSessionImpl smartPolarDeviceSession) {
        BleLogger.d(TAG, "new session added: " + smartPolarDeviceSession.getAdvertisementContent().getName());
        sessions.add(smartPolarDeviceSession);
    }

    Set<BleDeviceSession> copyDeviceList() {
        return new HashSet<>(sessions.objects());
    }

    BDDeviceSessionImpl getSession(final BluetoothGatt gatt) {
        return sessions.fetch(object -> {
            synchronized (object.getGattMutex()) {
                return object.getGatt() != null && object.getGatt().equals(gatt);
            }
        });
    }

    BDDeviceSessionImpl getSession(final String address) {
        return sessions.fetch(object -> object.getAddress().equals(address));
    }

    interface CompareFunction {
        boolean compare(BDDeviceSessionImpl smartPolarDeviceSession1);
    }

    BDDeviceSessionImpl fetch(final CompareFunction function) {
        return sessions.fetch(function::compare);
    }
}
