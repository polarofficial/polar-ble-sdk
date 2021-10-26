package com.polar.androidcommunications.api.ble.model.gatt;

import androidx.annotation.NonNull;

import com.polar.androidcommunications.api.ble.BleLogger;
import com.polar.androidcommunications.api.ble.model.gatt.client.BleBattClient;
import com.polar.androidcommunications.api.ble.model.gatt.client.BleDisClient;
import com.polar.androidcommunications.api.ble.model.gatt.client.BleH7SettingsClient;
import com.polar.androidcommunications.api.ble.model.gatt.client.BleHrClient;
import com.polar.androidcommunications.api.ble.model.gatt.client.BlePfcClient;
import com.polar.androidcommunications.api.ble.model.gatt.client.BlePsdClient;
import com.polar.androidcommunications.api.ble.model.gatt.client.BleRscClient;
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient;
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient;

import java.util.HashSet;
import java.util.Set;

public class BleGattFactory {

    private static final String TAG = BleGattFactory.class.getSimpleName();
    private final Set<Class<? extends BleGattBase>> classesRemote = new HashSet<>();

    public BleGattFactory(@NonNull Set<Class<? extends BleGattBase>> clients) {
        classesRemote.addAll(clients);
    }

    @NonNull
    public Set<BleGattBase> getRemoteServices(@NonNull BleGattTxInterface txInterface) {
        Set<BleGattBase> serviceBases = new HashSet<>();
        for (Class<?> classObject : classesRemote) {
            try {
                Class[] cArg = new Class[1];
                cArg[0] = BleGattTxInterface.class;
                BleGattBase serviceBase = (BleGattBase) classObject.getDeclaredConstructor(cArg).newInstance(txInterface);
                serviceBases.add(serviceBase);
            } catch (Exception e) {
                BleLogger.e(TAG, "remote services reflections usage failed: " + e.getLocalizedMessage());
                serviceBases.clear();
                if (classesRemote.contains(BlePsFtpClient.class)) {
                    serviceBases.add(new BlePsFtpClient(txInterface));
                }
                if (classesRemote.contains(BleHrClient.class)) {
                    serviceBases.add(new BleHrClient(txInterface));
                }
                if (classesRemote.contains(BlePsdClient.class)) {
                    serviceBases.add(new BlePsdClient(txInterface));
                }
                if (classesRemote.contains(BleH7SettingsClient.class)) {
                    serviceBases.add(new BleH7SettingsClient(txInterface));
                }
                if (classesRemote.contains(BlePfcClient.class)) {
                    serviceBases.add(new BlePfcClient(txInterface));
                }
                if (classesRemote.contains(BleDisClient.class)) {
                    serviceBases.add(new BleDisClient(txInterface));
                }
                if (classesRemote.contains(BleBattClient.class)) {
                    serviceBases.add(new BleBattClient(txInterface));
                }
                if (classesRemote.contains(BleRscClient.class)) {
                    serviceBases.add(new BleRscClient(txInterface));
                }
                if (classesRemote.contains(BlePMDClient.class)) {
                    serviceBases.add(new BlePMDClient(txInterface));
                }
                return serviceBases;
            }
        }
        return serviceBases;
    }
}
