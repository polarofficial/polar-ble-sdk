package com.androidcommunications.polar.api.ble.model.gatt.client;

import androidx.annotation.NonNull;

import com.androidcommunications.polar.api.ble.model.gatt.BleGattBase;
import com.androidcommunications.polar.api.ble.model.gatt.BleGattTxInterface;
import com.androidcommunications.polar.common.ble.AtomicSet;
import com.androidcommunications.polar.common.ble.RxUtils;

import java.util.ArrayList;
import java.util.UUID;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableEmitter;

public class BleHrClient extends BleGattBase {

    public static class HrNotificationData {
        public int hrValue;
        public boolean sensorContact;
        public boolean sensorContactSupported;
        public boolean rrPresent;
        public int energy;
        public int cumulative_rr;
        public ArrayList<Integer> rrs = new ArrayList<>();

        public HrNotificationData(int hrValue,
                                  boolean sensorContact,
                                  int energy,
                                  ArrayList<Integer> rrs,
                                  boolean sensorContactSupported,
                                  int cumulative_rr,
                                  boolean rrPresent) {
            this.hrValue = hrValue;
            this.sensorContact = sensorContact;
            this.energy = energy;
            this.rrs.addAll(rrs);
            this.cumulative_rr = cumulative_rr;
            this.sensorContactSupported = sensorContactSupported;
            this.rrPresent = rrPresent;
        }
    }

    private AtomicSet<FlowableEmitter<? super HrNotificationData>> hrObserverAtomicList = new AtomicSet<>();

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
        if (status == 0) if (characteristic.equals(HR_MEASUREMENT)) {
            // stupid java does not have bit fields
            int cumulative_rr = 0;
            int hrFormat = data[0] & 0x01;
            final boolean sensorContact = ((data[0] & 0x06) >> 1) == 3;
            final boolean contactSupported = !((data[0] & 0x06) == 0);
            int energyExpended = (data[0] & 0x08) >> 3;
            final int rrPresent = (data[0] & 0x10) >> 4;
            int polar_32bit_rr = (data[0] & 0x20) >> 5; // Polar RR format
            final int hrValue = (hrFormat == 1 ? data[1] + (data[2] << 8) : data[1]) & (hrFormat == 1 ? 0x0000FFFF : 0x000000FF);
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
            } else if (polar_32bit_rr == 1 && (offset + 3) < data.length) {
                cumulative_rr = ((data[offset] & 0xFF) + ((data[offset + 1] & 0xFF) << 8) + ((data[offset + 2] & 0xFF) << 16) + ((data[offset + 3] & 0xFF) << 24));
            }

            final int finalCumulative_rr = cumulative_rr;
            final int finalEnergy = energy;
            RxUtils.emitNext(hrObserverAtomicList, object -> object.onNext(new HrNotificationData(hrValue, sensorContact, finalEnergy, rrs, contactSupported, finalCumulative_rr, rrPresent == 1)));
        }
    }

    @Override
    public void processServiceDataWritten(UUID characteristic, int status) {
    }

    @Override
    public @NonNull
    String toString() {
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
