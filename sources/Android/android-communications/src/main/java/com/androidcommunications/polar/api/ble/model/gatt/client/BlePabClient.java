package com.androidcommunications.polar.api.ble.model.gatt.client;

import android.util.Pair;

import com.androidcommunications.polar.api.ble.exceptions.BleCharacteristicNotificationNotEnabled;
import com.androidcommunications.polar.api.ble.exceptions.BleDisconnected;
import com.androidcommunications.polar.api.ble.model.gatt.BleGattBase;
import com.androidcommunications.polar.api.ble.model.gatt.BleGattTxInterface;
import com.androidcommunications.polar.common.ble.AtomicSet;
import com.androidcommunications.polar.common.ble.RxUtils;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Action;
import io.reactivex.schedulers.Schedulers;

public class BlePabClient extends BleGattBase {

    public static final byte SUCCESS = 0x01;
    public static final byte OP_CODE_NOT_SUPPORTED = 0x02;
    public static final byte INVALID_PARAMETER = 0x03;
    public static final byte OPERATION_FAILED = 0x04;
    public static final byte NOT_ALLOWED = 0x05;


    public final static UUID PAB_SERVICE = UUID.fromString("FB00AA20-02E7-F387-1CAD-8ACD2D8DF0C8");
    public final static UUID PAB_FEATURE = UUID.fromString("FB00AA21-02E7-F387-1CAD-8ACD2D8DF0C8");
    public final static UUID PAB_CP      = UUID.fromString("FB00AA22-02E7-F387-1CAD-8ACD2D8DF0C8");
    public final static UUID PAB_STATUS  = UUID.fromString("FB00AA23-02E7-F387-1CAD-8ACD2D8DF0C8");

    public static final byte RESPONSE_CODE = (byte)0xF0;

    private final Object pabMutex = new Object();
    private final Object mutexFeature = new Object();
    private PabFeature pabFeature = null;
    private AtomicInteger pabCpEnabled;
    private LinkedBlockingQueue<Pair<byte[],Integer> > CpInputQueue = new LinkedBlockingQueue<>();
    private Scheduler scheduler = Schedulers.newThread();
    private final AtomicSet<FlowableEmitter<? super PabStatus>> statusObservers = new AtomicSet<>();

    public enum Message{
        PAB_UNKNOWN(0),
        PAB_START_SCAN(1),
        PAB_STOP_SCAN(2),
        PAB_DISPLAY_SENSOR(3),
        PAB_REMOVE_SENSOR(4),
        PAB_PAIR_SENSOR(5),
        PAB_CLEAR_PAIRING(6);
        private int numVal;

        Message(int numVal) {
            this.numVal = numVal;
        }

        public int getNumVal() {
            return numVal;
        }
    };



    public static class PabStatus{
        public static final int PAB_STATUS_UNKNOWN = 0;
        public static final int PAB_STATUS_SCAN_RESULT = 1;
        public static final int PAB_STATUS_SENSOR_CONNECTED =2;
        public static final int PAB_STATUS_SENSOR_DISCONNECTED = 3;
        public static final int PAB_STATUS_SENSOR_PAIRED = 4;
        public static final int PAB_STATUS_HR_VALUE = 5;
        public static final int PAB_STATUS_TORQUE_DATA = 6;
        public static final int PAB_STATUS_POWER_DATA = 7;
        public static final int PAB_STATUS_INFORMATIVE_EVENT = 100;
        private int status_type;

        private byte[] payload;

        public PabStatus(byte[] data) {
            if( data.length >= 1 ) {
                status_type = (int)data[0];
                if(data.length > 1)
                    payload = new byte[data.length - 1];
                    System.arraycopy(data, 1, payload, 0, data.length - 1);
            } else {
                status_type = PAB_STATUS_UNKNOWN;
                payload = new byte[0];
            }
        }

        public int getStatus_type() {
            return status_type;
        }

        public byte[] getPayload() {
            return payload;
        }
    }

    public static class Response{
        private byte responseCode;
        private Message opCode;
        private byte status;

        public Response(byte[] data) {
            if( data.length > 2 ) {
                responseCode = data[0];
                opCode = Message.values()[data[1]];
                status = data[2];
            } else {
                opCode = Message.PAB_UNKNOWN;
                responseCode = 0;
                status = 0;
            }
        }

        public byte getResponseCode() {
            return responseCode;
        }

        public Message getOpCode() {
            return opCode;
        }

        public byte getStatus() {
            return status;
        }

        @Override
        public String toString(){
            return "Response code: " + String.format("%02x", responseCode) +
                   " op code: " + opCode +
                   " status: " + String.format("%02x", status);
        }
    }

    public static class PabFeature{
        // feature boolean's
        public boolean hrm_supported = false;



        public PabFeature(byte[] data){
            hrm_supported = (data[0] & 0x01) == 1;
            // = ((data[0] & 0x02) >> 1) == 1;
            //accSupported = ((data[0] & 0x04) >> 2) == 1;
            //ppSupported = ((data[0] & 0x08) != 0);
        }
    }

    public BlePabClient(BleGattTxInterface txInterface) {
        super(txInterface, PAB_SERVICE);
        addCharacteristicRead(PAB_FEATURE);
        addCharacteristicNotification(PAB_CP);
        addCharacteristicNotification(PAB_STATUS);
        pabCpEnabled = getNotificationAtomicInteger(PAB_CP);
    }

    @Override
    public void reset() {
        super.reset();
        CpInputQueue.clear();

        synchronized (mutexFeature) {
            pabFeature = null;
            mutexFeature.notifyAll();
        }
        RxUtils.postDisconnectedAndClearList(statusObservers);
    }

    @Override
    public void processServiceData(UUID characteristic, final byte[] data, int status, boolean notifying) {
        if(status==0){
            if(characteristic.equals(PAB_CP)){
                CpInputQueue.add(new Pair<byte[], Integer>(data,status));
            }else if(characteristic.equals(PAB_FEATURE)){
                synchronized (mutexFeature) {
                    pabFeature = new PabFeature(data);
                    mutexFeature.notifyAll();
                }
            }else if(characteristic.equals(PAB_STATUS)){
                RxUtils.emitNext(statusObservers, new RxUtils.Emitter<FlowableEmitter<? super PabStatus>>() {
                    @Override
                    public void item(FlowableEmitter<? super PabStatus> object) {
                        object.onNext(new PabStatus(data));
                    }
                });
            }
        }
    }

    @Override
    public void processServiceDataWritten(UUID characteristic, int status) {
        // add some implementation later if needed
    }

    @Override
    public String toString() {
        synchronized (pabFeature) {
            if (pabFeature != null) {
                return "PAB service with values hrm supported: " + String.valueOf(pabFeature.hrm_supported);
            } else {
                return "PAB service";
            }
        }
    }

    private void sendPabCommandAndProcessResponse(SingleEmitter<? super Response> subscriber, byte[] packet){
        try {
            txInterface.transmitMessages(BlePabClient.this,PAB_SERVICE,PAB_CP, Arrays.asList(packet),true);
            Pair<byte[], Integer> pair = null;
            pair = CpInputQueue.poll(3, TimeUnit.SECONDS);
            if(!subscriber.isDisposed()) {
                if (pair != null && pair.second == 0) {
                    Response response = new Response(pair.first);
                    subscriber.onSuccess(response);
                } else if(!subscriber.isDisposed()){
                    subscriber.tryOnError(new Throwable("Pab response failed in receive in timeline"));
                }
            }
        } catch (Throwable throwable) {
            if(!subscriber.isDisposed()) {
                subscriber.tryOnError(throwable);
            }
        }
    }

    @Override
    public Completable clientReady(boolean checkConnection) {
        return waitNotificationEnabled(PAB_CP,checkConnection);
    }

    /**
     * Produces:  onNext:  when response message from device has been received
     *            onError: if reponse read fails e.g. timeout etc...
     *            onCompleted: produced after onNext
     * @param command
     * @param params
     * @return Observable stream, @see Rx Observer
     */
    public Single<Response> sendControlPointCommand(final Message command, final byte[] params) {
        return Single.create(new SingleOnSubscribe<Response>() {
            @Override
            public void subscribe(SingleEmitter<Response> subscriber) throws Exception {
                // force pab operation to be 'atomic'
                synchronized (pabMutex) {
                    if (pabCpEnabled.get() == ATT_SUCCESS) {
                        CpInputQueue.clear();
                        ByteBuffer bb = ByteBuffer.allocate(1 + params.length);
                        bb.put(new byte[]{(byte)command.getNumVal()});
                        bb.put(params);
                        sendPabCommandAndProcessResponse(subscriber, bb.array());
                    }else if(!subscriber.isDisposed()){
                        subscriber.tryOnError(new BleCharacteristicNotificationNotEnabled("PAB control point not enabled"));
                    }
                }
            }
        }).subscribeOn(scheduler);
    }

    public Single<PabFeature> readFeature(){
        return Single.create(new SingleOnSubscribe<PabFeature>() {
            @Override
            public void subscribe(SingleEmitter<PabFeature> subscriber) throws Exception {
                try {
                    synchronized (mutexFeature) {
                        if( pabFeature == null ) {
                            mutexFeature.wait();
                        }
                    }
                } catch (InterruptedException e) {
                    if(!subscriber.isDisposed()) {
                        subscriber.tryOnError(e.getCause());
                    }
                    return;
                }
                if( pabFeature != null ) {
                    subscriber.onSuccess(pabFeature);
                } else if(!subscriber.isDisposed()) {
                    subscriber.tryOnError(new BleDisconnected());
                }
            }
        }).subscribeOn(Schedulers.newThread());
    }

    public Flowable<PabStatus> monitorStatusNotifications(){
        final FlowableEmitter<? super PabStatus>[] listener = new FlowableEmitter[1];
        return Flowable.create(new FlowableOnSubscribe<PabStatus>() {
            @Override
            public void subscribe(@NonNull FlowableEmitter<PabStatus> subscriber) throws Exception {
                listener[0] = subscriber;
                statusObservers.add(subscriber);
            }
        },BackpressureStrategy.BUFFER).doFinally(new Action() {
            @Override
            public void run() throws Exception {
                statusObservers.remove(listener[0]);
            }
        }).serialize();
    }
}
