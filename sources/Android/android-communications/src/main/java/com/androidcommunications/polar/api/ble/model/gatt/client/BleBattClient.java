package com.androidcommunications.polar.api.ble.model.gatt.client;

import androidx.annotation.NonNull;

import com.androidcommunications.polar.api.ble.exceptions.BleDisconnected;
import com.androidcommunications.polar.api.ble.model.gatt.BleGattBase;
import com.androidcommunications.polar.api.ble.model.gatt.BleGattTxInterface;
import com.androidcommunications.polar.common.ble.AtomicSet;
import com.androidcommunications.polar.common.ble.RxUtils;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableEmitter;
import io.reactivex.rxjava3.core.FlowableOnSubscribe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleEmitter;
import io.reactivex.rxjava3.core.SingleOnSubscribe;

public class BleBattClient extends BleGattBase {

    public static final UUID BATTERY_SERVICE = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    public static final UUID BATTERY_LEVEL_CHARACTERISTIC = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");

    private AtomicSet<FlowableEmitter<? super Integer>> notificationObservers = new AtomicSet<>();
    private AtomicSet<SingleEmitter<? super Integer>> observers = new AtomicSet<>();
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
        RxUtils.postDisconnectedAndClearList(notificationObservers);
    }

    @Override
    public void processServiceData(UUID characteristic, final byte[] data, int status, boolean notifying) {
        if (status == 0) {
            if (characteristic.equals(BATTERY_LEVEL_CHARACTERISTIC)) {
                batteryLevel.set(data[0]);
                RxUtils.emitNext(observers, object -> object.onSuccess((int) data[0]));
                RxUtils.emitNext(notificationObservers, object -> object.onNext((int) data[0]));
            }
        }
    }

    @Override
    public void processServiceDataWritten(UUID characteristic, int status) {
        // do nothing
    }

    @Override
    public @NonNull
    String toString() {
        return "Battery service";
    }

    // API

    /**
     * @param checkConnection, false = no is connected check before observer added, true = check's is connected <BR>
     * @return Observable<Integer> <BR>
     * Produces: onSuccess, when ever battery level has been read or returns cached one <BR>
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
        }).doFinally(() -> observers.remove(observer[0]));
    }

    /**
     * monitor battery notifications
     *
     * @param checkConnection false = no is connected check before observer added, true = check's is connected <BR>
     * @return Observable
     */
    public Flowable<Integer> monitorBatteryLevelUpdate(final boolean checkConnection) {
        final FlowableEmitter<? super Integer>[] observer = new FlowableEmitter[1];
        return Flowable.create((FlowableOnSubscribe<Integer>) emitter -> {
            if (!checkConnection || txInterface.isConnected()) {
                observer[0] = emitter;
                notificationObservers.add(emitter);
                if (batteryLevel.get() != -1) {
                    emitter.onNext(batteryLevel.get());
                }
            } else if (!emitter.isCancelled()) {
                emitter.tryOnError(new BleDisconnected());
            }
        }, BackpressureStrategy.LATEST).doFinally(() -> notificationObservers.remove(observer[0]));
    }

    /**
     * Request(Read) battery level from device
     *
     * @param checkConnection, false = no is connected check before observer added, true = check's is connected <BR>
     * @return Observable<Integer> <BR>
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
