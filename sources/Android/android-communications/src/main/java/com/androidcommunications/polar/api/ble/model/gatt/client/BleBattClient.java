package com.androidcommunications.polar.api.ble.model.gatt.client;

import com.androidcommunications.polar.api.ble.exceptions.BleDisconnected;
import com.androidcommunications.polar.api.ble.model.gatt.BleGattBase;
import com.androidcommunications.polar.api.ble.model.gatt.BleGattTxInterface;
import com.androidcommunications.polar.common.ble.AtomicSet;
import com.androidcommunications.polar.common.ble.RxUtils;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.functions.Action;

public class BleBattClient extends BleGattBase {

    public static final UUID BATTERY_SERVICE              = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
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
        if(status == 0){
            if(characteristic.equals(BATTERY_LEVEL_CHARACTERISTIC)){
                batteryLevel.set(data[0]);
                RxUtils.emitNext(observers, new RxUtils.Emitter<SingleEmitter<? super Integer>>() {
                    @Override
                    public void item(SingleEmitter<? super Integer> object) {
                        object.onSuccess((int) data[0]);
                    }
                });
                RxUtils.emitNext(notificationObservers, new RxUtils.Emitter<FlowableEmitter<? super Integer>>() {
                    @Override
                    public void item(FlowableEmitter<? super Integer> object) {
                        object.onNext((int)data[0]);
                    }
                });
            }
        }
    }

    @Override
    public void processServiceDataWritten(UUID characteristic, int status) {
        // do nothing
    }

    @Override
    public String toString() {
        return "Battery service";
    }

    // API

    /**
     * @param checkConnection, false = no is connected check before observer added, true = check's is connected <BR>
     * @return Observable<Integer> <BR>
     *          Produces: onSuccess, when ever battery level has been read or returns cached one <BR>
     *                    onError, if initial connection is false(depending on checkConnection flag) or ble disconnect's <BR>
     */
    public Single<Integer> waitBatteryLevelUpdate(final boolean checkConnection){
        final SingleEmitter<? super Integer>[] observer = new SingleEmitter[1];
        return Single.create(new SingleOnSubscribe<Integer>() {
            @Override
            public void subscribe(SingleEmitter<Integer> subscriber) {
                if(!checkConnection || txInterface.isConnected()){
                    observer[0] = subscriber;
                    observers.add(subscriber);
                    if(batteryLevel.get()!=-1){
                        subscriber.onSuccess(batteryLevel.get());
                    }
                }else if(!subscriber.isDisposed()) {
                    subscriber.tryOnError(new BleDisconnected());
                }
            }
        }).doFinally(new Action() {
            @Override
            public void run() {
                observers.remove(observer[0]);
            }
        });
    }

    /**
     * monitor battery notifications
     * @param checkConnection
     * @return
     */
    public Flowable<Integer> monitorBatteryLevelUpdate(final boolean checkConnection) {
        final FlowableEmitter<? super Integer>[] observer = new FlowableEmitter[1];
        return Flowable.create(new FlowableOnSubscribe<Integer>() {
            @Override
            public void subscribe(FlowableEmitter<Integer> emitter) throws Exception {
                    if(!checkConnection || txInterface.isConnected()){
                        observer[0] = emitter;
                        notificationObservers.add(emitter);
                        if(batteryLevel.get()!=-1){
                            emitter.onNext(batteryLevel.get());
                        }
                    }else if(!emitter.isCancelled()) {
                        emitter.tryOnError(new BleDisconnected());
                    }
                }
        }, BackpressureStrategy.LATEST).doFinally(new Action() {
            @Override
            public void run() throws Exception {
                notificationObservers.remove(observer[0]);
            }
        });
    }

    /**
     * Request(Read) battery level from device
     * @param checkConnection, false = no is connected check before observer added, true = check's is connected <BR>
     * @return Observable<Integer> <BR>
     *          Produces: onNext, when ever battery level has been read or updated(notified) <BR>
     *                    onError, if initial connection is false(depending on checkConnection flag) or ble disconnect's <BR>
     *                    onCompleted, non produced <BR>
     */
    public Single<Integer> requestBatteryLevelUpdate(final boolean checkConnection){
        final SingleEmitter<? super Integer>[] observer = new SingleEmitter[1];
        return Single.create(new SingleOnSubscribe<Integer>() {
            @Override
            public void subscribe(SingleEmitter<Integer> subscriber) {
                try {
                    txInterface.readValue(BleBattClient.this,BATTERY_SERVICE, BATTERY_LEVEL_CHARACTERISTIC);
                    observer[0] = subscriber;
                    observers.add(subscriber);
                } catch (Throwable throwable) {
                    subscriber.tryOnError(throwable);
                }
            }
        }).doFinally(new Action() {
            @Override
            public void run() {
                observers.remove(observer[0]);
            }
        });
    }
}
