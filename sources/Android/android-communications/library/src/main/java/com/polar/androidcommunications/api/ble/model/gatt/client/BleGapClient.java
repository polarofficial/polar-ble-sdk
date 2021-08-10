package com.polar.androidcommunications.api.ble.model.gatt.client;

import androidx.annotation.NonNull;

import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected;
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase;
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface;
import com.polar.androidcommunications.common.ble.AtomicSet;
import com.polar.androidcommunications.common.ble.RxUtils;

import java.util.HashMap;
import java.util.UUID;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableEmitter;
import io.reactivex.rxjava3.core.FlowableOnSubscribe;

public class BleGapClient extends BleGattBase {

    public static UUID GAP_SERVICE = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb");
    public static UUID GAP_DEVICE_NAME_CHARACTERISTIC = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb");
    public static UUID GAP_APPEARANCE_CHARACTERISTIC = UUID.fromString("00002a01-0000-1000-8000-00805f9b34fb");

    private final HashMap<UUID, String> gapInformation = new HashMap<>();
    private final AtomicSet<FlowableEmitter<? super HashMap<UUID, String>>> gapObserverAtomicList = new AtomicSet<>();

    public BleGapClient(BleGattTxInterface txInterface) {
        super(txInterface, GAP_SERVICE);
        addCharacteristicRead(GAP_DEVICE_NAME_CHARACTERISTIC);
        addCharacteristicRead(GAP_APPEARANCE_CHARACTERISTIC);
    }

    @Override
    public void reset() {
        super.reset();
        synchronized (gapInformation) {
            gapInformation.clear();
        }
        RxUtils.postDisconnectedAndClearList(gapObserverAtomicList);
    }

    @Override
    public void processServiceData(UUID characteristic, byte[] data, int status, boolean notifying) {
        if (status == 0) {
            synchronized (gapInformation) {
                gapInformation.put(characteristic, new String(data));
            }

            RxUtils.emitNext(gapObserverAtomicList, object -> {
                HashMap<UUID, String> list;
                synchronized (gapInformation) {
                    list = new HashMap<>(gapInformation);
                }
                object.onNext(list);
                if (hasAllAvailableReadableCharacteristics(list.keySet())) {
                    object.onComplete();
                }
            });
        }
    }

    @Override
    public void processServiceDataWritten(UUID characteristic, int status) {

    }

    @Override
    public @NonNull
    String toString() {
        return "GAP service with values device name: ";
    }

    /**
     * Produces:  onNext, when a new gap info has been read <BR>
     * onCompleted, after all available gap info has been read ok <BR>
     * onError, if client is not initially connected or ble disconnect's <BR>
     *
     * @param checkConnection, optionally check connection on subscribe <BR>
     * @return Flowable stream
     */
    public Flowable<HashMap<UUID, String>> observeGapInfo(final boolean checkConnection) {
        final FlowableEmitter<? super HashMap<UUID, String>>[] observer = new FlowableEmitter[]{null};
        return Flowable.create((FlowableOnSubscribe<HashMap<UUID, String>>) subscriber -> {
            if (!checkConnection || BleGapClient.this.txInterface.isConnected()) {
                observer[0] = subscriber;
                gapObserverAtomicList.add(subscriber);
                HashMap<UUID, String> list;
                synchronized (gapInformation) {
                    list = new HashMap<>(gapInformation);
                }
                if (list.size() != 0) {
                    subscriber.onNext(list);
                }
                if (hasAllAvailableReadableCharacteristics(list.keySet())) {
                    subscriber.onComplete();
                }
            } else if (!subscriber.isCancelled()) {
                subscriber.tryOnError(new BleDisconnected());
            }
        }, BackpressureStrategy.BUFFER).doFinally(() -> gapObserverAtomicList.remove(observer[0]));
    }
}
