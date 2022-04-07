package com.polar.androidcommunications.api.ble.model.gatt.client;

import android.util.Pair;

import androidx.annotation.NonNull;

import com.polar.androidcommunications.api.ble.exceptions.BleAttributeError;
import com.polar.androidcommunications.api.ble.exceptions.BleCharacteristicNotificationNotEnabled;
import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected;
import com.polar.androidcommunications.api.ble.exceptions.BleNotSupported;
import com.polar.androidcommunications.api.ble.exceptions.BleTimeout;
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase;
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface;
import com.polar.androidcommunications.common.ble.AtomicSet;
import com.polar.androidcommunications.common.ble.RxUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableEmitter;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleOnSubscribe;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class BlePsdClient extends BleGattBase {

    public static final byte SUCCESS = 0x01;
    public static final byte OP_CODE_NOT_SUPPORTED = 0x02;
    public static final byte INVALID_PARAMETER = 0x03;
    public static final byte OPERATION_FAILED = 0x04;
    public static final byte NOT_ALLOWED = 0x05;

    public static final UUID PSD_SERVICE = UUID.fromString("FB005C20-02E7-F387-1CAD-8ACD2D8DF0C8");
    public static final UUID PSD_FEATURE = UUID.fromString("FB005C21-02E7-F387-1CAD-8ACD2D8DF0C8");
    public static final UUID PSD_CP = UUID.fromString("FB005C22-02E7-F387-1CAD-8ACD2D8DF0C8");
    public static final UUID PSD_PP = UUID.fromString("FB005C26-02E7-F387-1CAD-8ACD2D8DF0C8");

    public static final byte OP_CODE_START_ECG_STREAM = 0x01;
    public static final byte OP_CODE_STOP_ECG_STREAM = 0x02;
    public static final byte OP_CODE_START_OHR_STREAM = 0x03;
    public static final byte OP_CODE_STOP_OHR_STREAM = 0x04;
    public static final byte OP_CODE_START_ACC_STREAM = 0x05;
    public static final byte OP_CODE_STOP_ACC_STREAM = 0x06;
    public static final byte RESPONSE_CODE = (byte) 0xF0;

    private final Object psdMutex = new Object();
    private final AtomicInteger psdCpEnabled;
    private PsdFeature psdFeature = null;
    private final Object mutexFeature = new Object();
    private final LinkedBlockingQueue<Pair<byte[], Integer>> psdCpInputQueue = new LinkedBlockingQueue<>();
    private final Scheduler scheduler = Schedulers.newThread();

    private final AtomicSet<FlowableEmitter<? super PPData>> ppObservers = new AtomicSet<>();

    public enum PsdMessage {
        PSD_UNKNOWN(0),
        PSD_START_OHR_PP_STREAM(7),
        PSD_STOP_OHR_PP_STREAM(8);

        private final int numVal;

        PsdMessage(int numVal) {
            this.numVal = numVal;
        }

        public int getNumVal() {
            return numVal;
        }
    }

    public static class PsdData {
        private final byte[] data;

        public PsdData(byte[] data) {
            this.data = data;
        }

        public byte[] getPayload() {
            return data;
        }
    }

    public static class PPData {
        private final int rc;
        private final int hr;
        private final int ppInMs;
        private final int ppErrorEstimate;
        private final int blockerBit;
        private final int skinContactStatus;
        private final int skinContactSupported;

        public PPData(byte[] data) {
            rc = (int) ((long) data[0] & 0xFFL);
            hr = (int) ((long) data[1] & 0xFFL);
            ppInMs = (int) (((long) data[2] & 0xFFL) | ((long) data[3] & 0xFFL) << 8);
            ppErrorEstimate = (int) (((long) data[4] & 0xFFL) | (((long) data[5] & 0xFFL) << 8));
            blockerBit = data[6] & 0x01;
            skinContactStatus = (data[6] & 0x02) >> 1;
            skinContactSupported = (data[6] & 0x04) >> 2;
        }

        public int getRc() {
            return rc;
        }

        public int getHr() {
            return hr;
        }

        public int getPpErrorEstimate() {
            return ppErrorEstimate;
        }

        public int getBlockerBit() {
            return blockerBit;
        }

        public int getSkinContactStatus() {
            return skinContactStatus;
        }

        public int getSkinContactSupported() {
            return skinContactSupported;
        }

        public int getPpInMs() {
            return ppInMs;
        }
    }

    public static class PsdResponse {
        private byte responseCode;
        private PsdMessage opCode;
        private byte status;
        private byte payload;

        public PsdResponse() {
        }

        public PsdResponse(byte[] data) {
            if (data.length > 2) {
                responseCode = data[0];
                opCode = PsdMessage.values()[data[1]];
                status = data[2];
                if (data.length > 3) {
                    payload = data[3];
                }
            } else {
                opCode = PsdMessage.PSD_UNKNOWN;
                responseCode = 0;
                status = 0;
                payload = 0;
            }
        }

        public byte getResponseCode() {
            return responseCode;
        }

        public PsdMessage getOpCode() {
            return opCode;
        }

        public byte getStatus() {
            return status;
        }

        public byte getPayload() {
            return payload;
        }

        @Override
        public String toString() {
            return "Response code: " + String.format("%02x", responseCode) +
                    " op code: " + opCode +
                    " status: " + String.format("%02x", status) +
                    " payload: " + String.format("%02x", payload);
        }
    }

    public static class PsdFeature {
        // feature boolean's
        public boolean ecgSupported;
        public boolean accSupported;
        public boolean ohrSupported;
        public boolean ppSupported;

        public PsdFeature() {
        }

        public PsdFeature(byte[] data) {
            ecgSupported = (data[0] & 0x01) == 1;
            ohrSupported = ((data[0] & 0x02) >> 1) == 1;
            accSupported = ((data[0] & 0x04) >> 2) == 1;
            ppSupported = ((data[0] & 0x08) != 0);
        }

        PsdFeature(PsdFeature clone) {
            ecgSupported = clone.ecgSupported;
            ohrSupported = clone.ohrSupported;
            accSupported = clone.accSupported;
            ppSupported = clone.ppSupported;
        }
    }

    public BlePsdClient(BleGattTxInterface txInterface) {
        super(txInterface, PSD_SERVICE);
        addCharacteristicRead(PSD_FEATURE);
        addCharacteristicNotification(PSD_CP);
        addCharacteristicNotification(PSD_PP);
        psdCpEnabled = getNotificationAtomicInteger(PSD_CP);
    }

    @Override
    public void reset() {
        super.reset();
        psdCpInputQueue.clear();

        synchronized (mutexFeature) {
            psdFeature = null;
            mutexFeature.notifyAll();
        }

        RxUtils.postDisconnectedAndClearList(ppObservers);
    }

    @Override
    public void processServiceData(UUID characteristic, final byte[] data, int status, boolean notifying) {
        if (characteristic.equals(PSD_CP)) {
            psdCpInputQueue.add(new Pair<>(data, status));
        } else if (characteristic.equals(PSD_FEATURE)) {
            synchronized (mutexFeature) {
                if (status == 0) {
                    psdFeature = new PsdFeature(data);
                }
                mutexFeature.notifyAll();
            }
        } else if (status == 0) {
            if (characteristic.equals(PSD_PP)) {
                List<byte[]> list = splitPP(data);
                for (final byte[] packet : list) {
                    RxUtils.emitNext(ppObservers, object -> object.onNext(new PPData(packet)));
                }
            }
        }
    }

    private List<byte[]> splitPP(byte[] data) {
        int offset = 0;
        List<byte[]> components = new ArrayList<>();
        while (offset < data.length) {
            components.add(Arrays.copyOfRange(data, offset, offset + 7));
            offset += 7;
        }
        return components;
    }

    @Override
    public void processServiceDataWritten(UUID characteristic, int status) {
        // add some implementation later if needed
    }

    @Override
    public @NonNull
    String toString() {
        return "psd client";
    }

    private PsdResponse sendPsdCommandAndProcessResponse(byte[] packet) throws Exception {
        txInterface.transmitMessages(PSD_SERVICE, PSD_CP, Arrays.asList(packet), true);
        Pair<byte[], Integer> pair = psdCpInputQueue.poll(30, TimeUnit.SECONDS);
        if (pair != null) {
            if (pair.second == 0) {
                return new PsdResponse(pair.first);
            } else {
                throw new BleAttributeError("Psd attribute ", pair.second);
            }
        }
        throw new BleTimeout("Psd response failed in receive in timeline");
    }

    // API
    @Override
    public Completable clientReady(boolean checkConnection) {
        return waitNotificationEnabled(PSD_CP, checkConnection);
    }

    /**
     * Produces:  onNext:  when response message from device has been received
     * onError: if reponse read fails e.g. timeout etc...
     * onCompleted: produced after onNext
     *
     * @param command psd command
     * @param params  optional parameters if any
     * @return Observable stream, @see Rx Observer
     */
    public Single<PsdResponse> sendControlPointCommand(final PsdMessage command, final byte[] params) {
        return Single.create((SingleOnSubscribe<PsdResponse>) emitter -> {
            synchronized (psdMutex) {
                if (psdCpEnabled.get() == ATT_SUCCESS) {
                    try {
                        psdCpInputQueue.clear();
                        switch (command) {
                            case PSD_STOP_OHR_PP_STREAM:
                            case PSD_START_OHR_PP_STREAM: {
                                byte[] packet = new byte[]{(byte) command.getNumVal()};
                                emitter.onSuccess(sendPsdCommandAndProcessResponse(packet));
                                return;
                            }
                            default:
                                throw new BleNotSupported("Unknown psd command aquired");
                        }
                    } catch (Exception ex) {
                        if (!emitter.isDisposed()) {
                            emitter.tryOnError(ex);
                        }
                    }
                } else if (!emitter.isDisposed()) {
                    emitter.tryOnError(new BleCharacteristicNotificationNotEnabled("PSD control point not enabled"));
                }
            }
        }).subscribeOn(scheduler);
    }

    public Single<PsdFeature> readFeature() {
        return Single.create((SingleOnSubscribe<PsdFeature>) emitter -> {
            try {
                synchronized (mutexFeature) {
                    if (psdFeature == null) {
                        mutexFeature.wait();
                    }
                    if (psdFeature != null) {
                        emitter.onSuccess(new PsdFeature(psdFeature));
                        return;
                    } else if (!txInterface.isConnected()) {
                        throw new BleDisconnected();
                    }
                    throw new Exception("Undefined device error");
                }
            } catch (Exception ex) {
                if (!emitter.isDisposed()) {
                    emitter.tryOnError(ex);
                }
            }
        }).subscribeOn(Schedulers.io());
    }

    /**
     * start raw pp monitoring
     *
     * @return Flowable stream Produces:
     * - onNext for every air packet received <BR>
     * - onComplete non produced if stream is not further configured <BR>
     * - onError BleDisconnected produced on disconnection <BR>
     */
    public Flowable<PPData> monitorPPNotifications(final boolean checkConnection) {
        return RxUtils.monitorNotifications(ppObservers, txInterface, checkConnection);
    }
}
