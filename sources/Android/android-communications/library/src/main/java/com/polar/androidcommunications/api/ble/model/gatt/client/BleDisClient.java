package com.polar.androidcommunications.api.ble.model.gatt.client;

import android.util.Pair;

import androidx.annotation.NonNull;

import com.polar.androidcommunications.api.ble.exceptions.BleAttributeError;
import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected;
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase;
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface;
import com.polar.androidcommunications.common.ble.AtomicSet;
import com.polar.androidcommunications.common.ble.RxUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.UUID;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableEmitter;
import io.reactivex.rxjava3.core.FlowableOnSubscribe;

public class BleDisClient extends BleGattBase {

    private static final String TAG = BleDisClient.class.getSimpleName();

    public static final UUID DIS_SERVICE = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
    public static final UUID MODEL_NUMBER_STRING = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb");
    public static final UUID MANUFACTURER_NAME_STRING = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb");
    public static final UUID HARDWARE_REVISION_STRING = UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb");
    public static final UUID FIRMWARE_REVISION_STRING = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb");
    public static final UUID SOFTWARE_REVISION_STRING = UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb");
    public static final UUID SERIAL_NUMBER_STRING = UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb");
    public static final UUID SYSTEM_ID = UUID.fromString("00002a23-0000-1000-8000-00805f9b34fb");
    public static final UUID IEEE_11073_20601 = UUID.fromString("00002a2a-0000-1000-8000-00805f9b34fb");
    public static final UUID PNP_ID = UUID.fromString("00002a50-0000-1000-8000-00805f9b34fb");

    // store in map
    private final HashMap<UUID, String> disInformation = new HashMap<>();
    private final AtomicSet<FlowableEmitter<? super Pair<UUID, String>>> disObserverAtomicList = new AtomicSet<>();

    public BleDisClient(BleGattTxInterface txInterface) {
        super(txInterface, DIS_SERVICE);
        addCharacteristicRead(MODEL_NUMBER_STRING);
        addCharacteristicRead(MANUFACTURER_NAME_STRING);
        addCharacteristicRead(HARDWARE_REVISION_STRING);
        addCharacteristicRead(FIRMWARE_REVISION_STRING);
        addCharacteristicRead(SOFTWARE_REVISION_STRING);
        addCharacteristicRead(SERIAL_NUMBER_STRING);
        addCharacteristicRead(SYSTEM_ID);
        addCharacteristicRead(IEEE_11073_20601);
        addCharacteristicRead(PNP_ID);
    }

    @Override
    public void reset() {
        super.reset();
        synchronized (disInformation) {
            disInformation.clear();
        }
        RxUtils.postDisconnectedAndClearList(disObserverAtomicList);
    }

    @Override
    public void processServiceData(final UUID characteristic, final byte[] data, int status, boolean notifying) {
        if (status == 0) {
            synchronized (disInformation) {
                disInformation.put(characteristic, new String(data, StandardCharsets.UTF_8));
            }
            RxUtils.emitNext(disObserverAtomicList, object -> {
                object.onNext(new Pair<>(characteristic, new String(data, StandardCharsets.UTF_8)));
                synchronized (disInformation) {
                    if (hasAllAvailableReadableCharacteristics(disInformation.keySet())) {
                        object.onComplete();
                    }
                }
            });
        } else {
            RxUtils.postError(disObserverAtomicList, new BleAttributeError("dis ", status));
        }
    }

    @Override
    public void processServiceDataWritten(UUID characteristic, int status) {
        // do nothing
    }

    @Override
    public @NonNull
    String toString() {
        return "Device info service";
    }

    /**
     * Produces:  onNext, when a dis data has been read <BR>
     * onCompleted, after all available dis info has been read <BR>
     * onError, if client is not initially connected or ble disconnect's  <BR>
     *
     * @param checkConnection, optionally check connection on subscribe <BR>
     * @return Flowable stream <BR>
     */
    public Flowable<Pair<UUID, String>> observeDisInfo(final boolean checkConnection) {
        final FlowableEmitter<? super Pair<UUID, String>>[] observer = new FlowableEmitter[1];
        return Flowable.create((FlowableOnSubscribe<Pair<UUID, String>>) subscriber -> {
            if (!checkConnection || BleDisClient.this.txInterface.isConnected()) {
                observer[0] = subscriber;
                disObserverAtomicList.add(subscriber);
                synchronized (disInformation) {
                    for (UUID e : disInformation.keySet()) {
                        subscriber.onNext(new Pair<>(e, disInformation.get(e)));
                    }
                    if (hasAllAvailableReadableCharacteristics(disInformation.keySet())) {
                        subscriber.onComplete();
                    }
                }
            } else if (!subscriber.isCancelled()) {
                subscriber.tryOnError(new BleDisconnected());
            }
        }, BackpressureStrategy.BUFFER).doFinally(() -> disObserverAtomicList.remove(observer[0]));
    }
}

