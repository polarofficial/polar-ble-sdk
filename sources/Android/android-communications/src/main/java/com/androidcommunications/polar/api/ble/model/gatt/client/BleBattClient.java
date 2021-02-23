package com.androidcommunications.polar.api.ble.model.gatt.client;

import androidx.annotation.NonNull;

import com.androidcommunications.polar.api.ble.exceptions.BleDisconnected;
import com.androidcommunications.polar.api.ble.model.gatt.BleGattBase;
import com.androidcommunications.polar.api.ble.model.gatt.BleGattTxInterface;
import com.androidcommunications.polar.common.ble.AtomicSet;
import com.androidcommunications.polar.common.ble.RxUtils;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableEmitter;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleEmitter;
import io.reactivex.rxjava3.core.SingleOnSubscribe;

public class BleBattClient extends BleGattBase {

    public static final UUID BATTERY_SERVICE = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    public static final UUID BATTERY_LEVEL_CHARACTERISTIC = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");

    private final AtomicSet<FlowableEmitter<? super Integer>> batteryStatusObservers = new AtomicSet<>();
    private final AtomicSet<SingleEmitter<? super Integer>> observers = new AtomicSet<>();
    private AtomicInteger batteryLevel = new AtomicInteger(-1);

    public BleBattClient(BleGattTxInterface txInterface) {
        super(txInterface, BATTERY_SERVICE);
        addCharacteristicNotification(BATTERY_LEVEL_CHARACTERISTIC);
        addCharacteristicRead(BATTERY_LEVEL_CHARACTERISTIC);
    }

    @Override
    public void reset() {
        super.reset();
        batteryLevel.set(-1);
        RxUtils.postDisconnectedAndClearList(observers);
        RxUtils.postDisconnectedAndClearList(batteryStatusObservers);
    }

    @Override
    public void processServiceData(UUID characteristic, final byte[] data, int status, boolean notifying) {
        if (status == 0 && characteristic.equals(BATTERY_LEVEL_CHARACTERISTIC)) {
            batteryLevel.set(data[0]);
            RxUtils.emitNext(observers, object -> object.onSuccess((int) data[0]));
            RxUtils.emitNext(batteryStatusObservers, object -> object.onNext((int) data[0]));
        }
    }

    @Override
    public void processServiceDataWritten(UUID characteristic, int status) {
        // do nothing
    }

    @NonNull
    @Override
    public String toString() {
        return "Battery service";
    }

    /**
     * @param checkConnection false = connection is not check before observer added, true = connection is check
     * @return Single emitting battery level value or error
     * Produces: onSuccess, when ever battery level has been read or returns cached one
     * onError, if initial connection is false(depending on checkConnection flag) or ble disconnect's <BR>
     */
    public Single<Integer> waitBatteryLevelUpdate(final boolean checkConnection) {
        final SingleEmitter<? super Integer>[] observer = new SingleEmitter[1];
        return Single.create((SingleOnSubscribe<Integer>) subscriber -> {
                    if (!checkConnection || txInterface.isConnected()) {
                        observer[0] = subscriber;
                        observers.add(subscriber);
                        if (batteryLevel.get() != -1) {
                            subscriber.onSuccess(batteryLevel.get());
                        }
                    } else if (!subscriber.isDisposed()) {
                        subscriber.tryOnError(new BleDisconnected());
                    }
                }
        ).doFinally(() -> observers.remove(observer[0]));
    }

    /**
     * Monitor battery notifications
     *
     * @param checkConnection false = connection is not check before observer added, true = connection is check
     * @return Flowable stream
     * Produces: onNext, for every battery notification event
     * onError, if client is not initially connected or ble disconnect's
     * onCompleted, none except further configuration applied. If binded to fragment or activity life cycle this might be produced
     */
    public Flowable<Integer> monitorBatteryLevelUpdate(final boolean checkConnection) {
        return RxUtils.monitorNotifications(batteryStatusObservers, txInterface, checkConnection);
    }

    /**
     * Request(Read) battery level from device
     *
     * @param checkConnection false = connection is not check before observer added, true = connection is check
     * @return Observable<Integer>
     * Produces: onNext, when ever battery level has been read or updated(notified) <BR>
     * onError, if initial connection is false(depending on checkConnection flag) or ble disconnect's <BR>
     * onCompleted, non produced <BR>
     */
    public Single<Integer> requestBatteryLevelUpdate(final boolean checkConnection) {
        final SingleEmitter<? super Integer>[] observer = new SingleEmitter[1];
        return Single.create((SingleOnSubscribe<Integer>) subscriber -> {
            try {
                txInterface.readValue(BleBattClient.this, BATTERY_SERVICE, BATTERY_LEVEL_CHARACTERISTIC);
                observer[0] = subscriber;
                observers.add(subscriber);
            } catch (Throwable throwable) {
                subscriber.tryOnError(throwable);
            }
        }).doFinally(() -> observers.remove(observer[0]));
    }
}
