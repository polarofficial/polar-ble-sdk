package com.androidcommunications.polar.api.ble.model.gatt.client;

import androidx.annotation.NonNull;

import com.androidcommunications.polar.api.ble.BleLogger;
import com.androidcommunications.polar.api.ble.model.gatt.BleGattBase;
import com.androidcommunications.polar.api.ble.model.gatt.BleGattTxInterface;
import com.androidcommunications.polar.common.ble.AtomicSet;
import com.androidcommunications.polar.common.ble.RxUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableEmitter;

public class BleHrClient extends BleGattBase {
    private static final String TAG = BleHrClient.class.getSimpleName();

    public static class HrNotificationData {
        public final int hrValue;
        public final boolean sensorContact;
        public final boolean sensorContactSupported;
        public final boolean rrPresent;
        public final int energy;
        public final long cumulativeRR;
        public final List<Integer> rrs = new ArrayList<>();

        public HrNotificationData(int hrValue,
                                  boolean sensorContact,
                                  int energy,
                                  List<Integer> rrs,
                                  boolean sensorContactSupported,
                                  long cumulativeRR,
                                  boolean rrPresent) {
            this.hrValue = hrValue;
            this.sensorContact = sensorContact;
            this.energy = energy;
            this.rrs.addAll(rrs);
            this.cumulativeRR = cumulativeRR;
            this.sensorContactSupported = sensorContactSupported;
            this.rrPresent = rrPresent;
        }
    }

    private final AtomicSet<FlowableEmitter<? super HrNotificationData>> hrObserverAtomicList = new AtomicSet<>();

    public static final UUID BODY_SENSOR_LOCATION = UUID.fromString("00002a38-0000-1000-8000-00805f9b34fb");
    public static final UUID HR_MEASUREMENT = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    public static final UUID HR_SERVICE = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb");

    public BleHrClient(BleGattTxInterface txInterface) {
        super(txInterface, HR_SERVICE);
        addCharacteristicNotification(HR_MEASUREMENT);
        addCharacteristicRead(BODY_SENSOR_LOCATION);
    }

    @Override
    public void reset() {
        super.reset();
        RxUtils.postDisconnectedAndClearList(hrObserverAtomicList);
    }

    @Override
    public void processServiceData(UUID characteristic, byte[] data, int status, boolean notifying) {
        BleLogger.d(TAG, "Processing service data. Status: " + status + ".  Data length: " + data.length);
        if (status == 0 && characteristic.equals(HR_MEASUREMENT)) {
            long cumulativeRR = 0;
            final int hrFormat = data[0] & 0x01;
            final boolean sensorContact = ((data[0] & 0x06) >> 1) == 0x03;
            final boolean contactSupported = (data[0] & 0x04) != 0;
            int energyExpended = (data[0] & 0x08) >> 3;
            final int rrPresent = (data[0] & 0x10) >> 4;
            final int hrValue = (hrFormat == 1 ? (data[1] & 0xFF) + (data[2] << 8) : data[1]) & (hrFormat == 1 ? 0x0000FFFF : 0x000000FF);
            int offset = hrFormat + 2;
            int energy = 0;
            if (energyExpended == 1) {
                energy = (data[offset] & 0xFF) + ((data[offset + 1] & 0xFF) << 8);
                offset += 2;
            }
            final ArrayList<Integer> rrs = new ArrayList<>();
            if (rrPresent == 1) {
                int len = data.length;
                while (offset < len) {
                    int rrValue = ((data[offset] & 0xFF) + ((data[offset + 1] & 0xFF) << 8));
                    offset += 2;
                    rrs.add(rrValue);
                }
            }

            final long finalCumulativeRR = cumulativeRR;
            final int finalEnergy = energy;
            RxUtils.emitNext(hrObserverAtomicList, object ->
                    object.onNext(new HrNotificationData(hrValue, sensorContact, finalEnergy, rrs, contactSupported, finalCumulativeRR, rrPresent == 1)));
        }
    }

    @Override
    public void processServiceDataWritten(UUID characteristic, int status) {
        BleLogger.d(TAG, "Service data written not processed in BleHrClient");
    }

    @NonNull
    @Override
    public String toString() {
        // and so on
        return "HR gatt client";
    }

    @Override
    public Completable clientReady(boolean checkConnection) {
        return waitNotificationEnabled(HR_MEASUREMENT, checkConnection);
    }

    // API

    /**
     * @return Flowable stream
     * Produces: onNext, for every hr notification event
     * onError, if client is not initially connected or ble disconnect's
     * onCompleted, none except further configuration applied. If binded to fragment or activity life cycle this might be produced
     */
    public Flowable<HrNotificationData> observeHrNotifications(final boolean checkConnection) {
        return RxUtils.monitorNotifications(hrObserverAtomicList, txInterface, checkConnection);
    }
}
