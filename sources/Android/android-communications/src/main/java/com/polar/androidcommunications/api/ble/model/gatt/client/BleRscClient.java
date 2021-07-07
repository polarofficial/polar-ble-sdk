package com.polar.androidcommunications.api.ble.model.gatt.client;

import androidx.annotation.NonNull;

import com.polar.androidcommunications.api.ble.BleLogger;
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase;
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface;
import com.polar.androidcommunications.common.ble.AtomicSet;
import com.polar.androidcommunications.common.ble.RxUtils;

import java.util.UUID;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableEmitter;

public class BleRscClient extends BleGattBase {

    private final static String TAG = BleRscClient.class.getSimpleName();

    public static class RscNotificationData {
        public boolean StrideLengthPresent;
        public boolean TotaDistancePresent;
        public boolean Running;

        public long Speed;
        public long Cadence;

        public long StrideLength;
        public long TotalDistance;


        public RscNotificationData(boolean StrideLengthPresent, boolean TotaDistancePresent, boolean Running, long Speed, long Cadence, long StrideLength, long TotaDistance) {
            this.StrideLengthPresent = StrideLengthPresent;
            this.TotaDistancePresent = TotaDistancePresent;
            this.Running = Running;
            this.Speed = Speed;
            this.Cadence = Cadence;
            this.StrideLength = StrideLength;
            this.TotalDistance = TotaDistance;
        }
    }

    private final AtomicSet<FlowableEmitter<? super RscNotificationData>> observers = new AtomicSet<>();

    public static final UUID RSC_FEATURE = UUID.fromString("00002a54-0000-1000-8000-00805f9b34fb");
    public static final UUID RSC_MEASUREMENT = UUID.fromString("00002a53-0000-1000-8000-00805f9b34fb");
    public static final UUID RSC_SERVICE = UUID.fromString("00001814-0000-1000-8000-00805f9b34fb");

    public BleRscClient(BleGattTxInterface txInterface) {
        super(txInterface, RSC_SERVICE);
        addCharacteristicNotification(RSC_MEASUREMENT);
        addCharacteristicRead(RSC_FEATURE);
    }

    @Override
    public void reset() {
        super.reset();
        RxUtils.postDisconnectedAndClearList(observers);
    }

    @Override
    public void processServiceData(UUID characteristic, byte[] data, int status, boolean notifying) {
        if (status == 0) {
            if (characteristic.equals(RSC_MEASUREMENT)) {
                // stupid java does not have bit fields
                int index = 0;
                long flags = data[index++];
                final boolean StrideLenPresent = (flags & 0x01) == 0x01;
                final boolean TotalDistancePresent = (flags & 0x02) == 0x02;
                final boolean Running = (flags & 0x04) == 0x04;

                final long Speed = (data[index++] | (data[index++] << 8));
                final long Cadence = data[index++];

                long StrideLength = 0;
                long TotalDistance = 0;

                if (StrideLenPresent)
                    StrideLength = (data[index++] | (data[index++] << 8));

                if (TotalDistancePresent)
                    TotalDistance = (data[index++] | (data[index++] << 8) | data[index++] << 16 | data[index] << 24);

                final long finalStrideLength = StrideLength;
                final long finalTotalDistance = TotalDistance;
                RxUtils.emitNext(observers, object -> object.onNext(new RscNotificationData(StrideLenPresent, TotalDistancePresent, Running, Speed, Cadence, finalStrideLength, finalTotalDistance)));
            } else if (characteristic.equals(RSC_FEATURE)) {
                long feature = data[0] | data[1] << 8;
                BleLogger.d(TAG, "RSC Feature Characteristic read: " + feature);
            }
        }
    }

    @Override
    public void processServiceDataWritten(UUID characteristic, int status) {
        // do  nothing
    }

    @Override
    public @NonNull
    String toString() {
        return "RSC service ";
    }

    @Override
    public Completable clientReady(boolean checkConnection) {
        // override in client if required
        return waitNotificationEnabled(RSC_MEASUREMENT, checkConnection);
    }

    // API

    /**
     * @return Flowable stream of RscNotificationData
     * Produces: onNext  for every Rsc notification event
     * onError for Interrupted mutex wait
     * onCompleted none except further configuration applied. If binded to fragment or activity life cycle this might be produced
     */
    public Flowable<RscNotificationData> monitorRscNotifications() {
        return RxUtils.monitorNotifications(observers, txInterface, true);
    }
}
