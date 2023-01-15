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

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleOnSubscribe;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class BlePfcClient extends BleGattBase {

    private static final String TAG = BlePfcClient.class.getSimpleName();

    public static final byte SUCCESS = 0x01;
    public static final byte ERROR_NOT_SUPPORTED = 0x02;
    public static final byte ERROR_INVALID_PARAMETER = 0x03;
    public static final byte ERROR_OPERATION_FAILED = 0x04;
    public static final byte ERROR_NOT_ALLOWED = 0x05;

    public static final byte RESPONSE_CODE = (byte) 0xF0;

    public static final UUID PFC_SERVICE = UUID.fromString("6217FF4B-FB31-1140-AD5A-A45545D7ECF3"); /* Poler Features Configuration Service (PFCS)*/
    public static final UUID PFC_FEATURE = UUID.fromString("6217FF4C-C8EC-B1FB-1380-3AD986708E2D");
    public static final UUID PFC_CP = UUID.fromString("6217FF4D-91BB-91D0-7E2A-7CD3BDA8A1F3");

    private PfcFeature pfcFeature = null;
    private final Object mutexFeature = new Object();
    private final LinkedBlockingQueue<Pair<byte[], Integer>> pfcCpInputQueue = new LinkedBlockingQueue<>();
    private final Scheduler scheduler = Schedulers.newThread();
    private final AtomicInteger pfcCpEnabled;
    private final Object pfcMutex = new Object();

    public enum PfcMessage {
        PFC_UNKNOWN(0),
        PFC_CONFIGURE_BROADCAST(1),
        PFC_REQUEST_BROADCAST_SETTING(2),
        PFC_CONFIGURE_5KHZ(3),
        PFC_REQUEST_5KHZ_SETTING(4),
        PFC_CONFIGURE_WHISPER_MODE(5),
        PFC_REQUEST_WHISPER_MODE(6),
        PFC_CONFIGURE_BLE_MODE(7),
        PFC_CONFIGURE_MULTI_CONNECTION_SETTING(8),
        PFC_REQUEST_MULTI_CONNECTION_SETTING(9),
        PFC_CONFIGURE_ANT_PLUS_SETTING(10),
        PFC_REQUEST_ANT_PLUS_SETTING(11);

        private final int numVal;

        PfcMessage(int numVal) {
            this.numVal = numVal;
        }

        public int getNumVal() {
            return numVal;
        }
    }

    public static class PfcResponse {
        private byte responseCode;
        private PfcMessage opCode;
        private byte status;
        private byte[] payload;

        public PfcResponse() {
        }

        public PfcResponse(byte[] data) {
            responseCode = data[0];
            opCode = PfcMessage.values()[data[1]];
            status = data[2];
            if (data.length > 3) {
                payload = new byte[data.length - 3];
                System.arraycopy(data, 3, payload, 0, data.length - 3);
            }
        }

        public byte getResponseCode() {
            return responseCode;
        }

        public PfcMessage getOpCode() {
            return opCode;
        }

        public byte getStatus() {
            return status;
        }

        public byte[] getPayload() {
            return payload;
        }

        @Override
        public String toString() {
            StringBuffer stringBuffer = new StringBuffer();
            if (payload != null) {
                for (byte b : payload) {
                    stringBuffer.append(String.format("%02x ", b));
                }
            }
            return "Response code: " + String.format("%02x", responseCode) +
                    " op code: " + opCode +
                    " status: " + String.format("%02x", status) + " payload: " + stringBuffer;
        }
    }

    public static class PfcFeature implements Cloneable {
        // feature boolean's
        public boolean broadcastSupported;
        public boolean khzSupported;
        public boolean whisperModeSupported;
        public boolean otaUpdateSupported;
        public boolean bleModeConfigureSupported;
        public boolean multiConnectionSupported;
        public boolean antSupported;

        public PfcFeature() {
        }

        PfcFeature(byte[] data) {
            broadcastSupported = (data[0] & 0x01) == 1;
            khzSupported = ((data[0] & 0x02) >> 1) == 1;
            otaUpdateSupported = ((data[0] & 0x04) >> 2) == 1;
            whisperModeSupported = ((data[0] & 0x10) >> 4) == 1;
            bleModeConfigureSupported = ((data[0] & 0x40) >> 6) == 1;
            multiConnectionSupported = ((data[0] & 0x80) >> 7) == 1;
            antSupported = (data[1] & 0x01) == 1;
        }

        PfcFeature(PfcFeature clone) {
            this.broadcastSupported = clone.broadcastSupported;
            this.khzSupported = clone.khzSupported;
            this.otaUpdateSupported = clone.otaUpdateSupported;
            this.whisperModeSupported = clone.whisperModeSupported;
            this.bleModeConfigureSupported = clone.bleModeConfigureSupported;
            this.multiConnectionSupported = clone.multiConnectionSupported;
            this.antSupported = clone.antSupported;
        }
    }

    public BlePfcClient(BleGattTxInterface txInterface) {
        super(txInterface, PFC_SERVICE);
        addCharacteristicRead(PFC_FEATURE);
        addCharacteristicNotification(PFC_CP);
        pfcCpEnabled = getNotificationAtomicInteger(PFC_CP);
    }

    @Override
    public void reset() {
        super.reset();
        pfcCpInputQueue.clear();
        synchronized (mutexFeature) {
            pfcFeature = null;
            mutexFeature.notifyAll();
        }
    }

    @Override
    public void processServiceData(UUID characteristic, byte[] data, int status, boolean notifying) {
        if (characteristic.equals(PFC_CP)) {
            pfcCpInputQueue.add(new Pair<>(data, status));
        } else if (characteristic.equals(PFC_FEATURE)) {
            synchronized (mutexFeature) {
                if (status == 0) {
                    pfcFeature = new PfcFeature(data);
                }
                mutexFeature.notifyAll();
            }
        }
    }

    @Override
    public void processServiceDataWritten(UUID characteristic, int status) {
        // add some implementation later if needed
    }

    @Override
    public @NonNull
    String toString() {
        return "PFC service with values broadcast supported: " + pfcFeature.broadcastSupported + " 5khz supported: " + pfcFeature.khzSupported;
    }

    private PfcResponse sendPfcCommandAndProcessResponse(byte[] packet) throws Exception {
        txInterface.transmitMessages(PFC_SERVICE, PFC_CP, Collections.singletonList(packet), true);
        Pair<byte[], Integer> pair = pfcCpInputQueue.poll(30, TimeUnit.SECONDS);
        if (pair != null) {
            if (pair.second == 0) {
                return new PfcResponse(pair.first);
            } else {
                throw new BleAttributeError("pfc attribute ", pair.second);
            }
        }
        throw new BleTimeout("Pfc response failed to receive in timeline");
    }

    // API
    public Single<PfcResponse> sendControlPointCommand(final PfcMessage command, int param) {
        return sendControlPointCommand(command, new byte[]{(byte) param});
    }

    @Override
    public Completable clientReady(boolean checkConnection) {
        return waitNotificationEnabled(PFC_CP, checkConnection);
    }

    /**
     * Produces:  onError: if reponse read fails e.g. timeout etc...
     * onSuccess: with control point response
     *
     * @param command @see PfcMessage
     * @param params  optional parameters depends on command
     * @return Observable stream, @see Rx Observable
     */
    public Single<PfcResponse> sendControlPointCommand(final PfcMessage command, final byte[] params) {
        return Single.create((SingleOnSubscribe<PfcResponse>) emitter -> {
            // force pfc operation to be 'atomic'
            synchronized (pfcMutex) {
                if (pfcCpEnabled.get() == ATT_SUCCESS) {
                    try {
                        pfcCpInputQueue.clear();
                        switch (command) {
                            case PFC_CONFIGURE_ANT_PLUS_SETTING:
                            case PFC_CONFIGURE_MULTI_CONNECTION_SETTING:
                            case PFC_CONFIGURE_BLE_MODE:
                            case PFC_CONFIGURE_WHISPER_MODE:
                            case PFC_CONFIGURE_BROADCAST:
                            case PFC_CONFIGURE_5KHZ: {
                                ByteBuffer bb = ByteBuffer.allocate(1 + params.length);
                                bb.put(new byte[]{(byte) command.getNumVal()});
                                bb.put(params);
                                emitter.onSuccess(sendPfcCommandAndProcessResponse(bb.array()));
                                return;
                            }
                            case PFC_REQUEST_MULTI_CONNECTION_SETTING:
                            case PFC_REQUEST_ANT_PLUS_SETTING:
                            case PFC_REQUEST_WHISPER_MODE:
                            case PFC_REQUEST_BROADCAST_SETTING:
                            case PFC_REQUEST_5KHZ_SETTING: {
                                byte[] packet = new byte[]{(byte) command.getNumVal()};
                                emitter.onSuccess(sendPfcCommandAndProcessResponse(packet));
                                return;
                            }
                            default:
                                throw new BleNotSupported("Unknown pfc command aquired");
                        }
                    } catch (Exception ex) {
                        if (!emitter.isDisposed()) {
                            emitter.tryOnError(ex);
                        }
                    }
                } else if (!emitter.isDisposed()) {
                    emitter.tryOnError(new BleCharacteristicNotificationNotEnabled("PFC control point not enabled"));
                }
            }
        }).subscribeOn(scheduler);
    }

    /**
     * read features from pfc service
     *
     * @return Observable stream
     * Produces:
     * - onSuccess device response data
     * - onError, @see errors package com.polar.androidcommunications.
     */
    public Single<PfcFeature> readFeature() {
        return Single.create((SingleOnSubscribe<PfcFeature>) emitter -> {
            try {
                synchronized (mutexFeature) {
                    if (pfcFeature == null) {
                        mutexFeature.wait();
                    }
                    if (pfcFeature != null) {
                        emitter.onSuccess(new PfcFeature(pfcFeature));
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
}
