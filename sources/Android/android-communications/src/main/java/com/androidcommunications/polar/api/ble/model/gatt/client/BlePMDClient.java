package com.androidcommunications.polar.api.ble.model.gatt.client;

import android.util.Pair;

import androidx.annotation.NonNull;

import com.androidcommunications.polar.api.ble.BleLogger;
import com.androidcommunications.polar.api.ble.exceptions.BleAttributeError;
import com.androidcommunications.polar.api.ble.exceptions.BleCharacteristicNotificationNotEnabled;
import com.androidcommunications.polar.api.ble.exceptions.BleDisconnected;
import com.androidcommunications.polar.api.ble.model.gatt.BleGattBase;
import com.androidcommunications.polar.api.ble.model.gatt.BleGattTxInterface;
import com.androidcommunications.polar.common.ble.AtomicSet;
import com.androidcommunications.polar.common.ble.BleUtils;
import com.androidcommunications.polar.common.ble.RxUtils;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableEmitter;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleOnSubscribe;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class BlePMDClient extends BleGattBase {

    private final static String TAG = BlePMDClient.class.getSimpleName();

    public static final UUID PMD_DATA = UUID.fromString("FB005C82-02E7-F387-1CAD-8ACD2D8DF0C8");
    public static final UUID PMD_CP = UUID.fromString("FB005C81-02E7-F387-1CAD-8ACD2D8DF0C8");
    public static final UUID PMD_SERVICE = UUID.fromString("FB005C80-02E7-F387-1CAD-8ACD2D8DF0C8");

    private final Object controlPointMutex = new Object();
    private final LinkedBlockingQueue<Pair<byte[], Integer>> pmdCpInputQueue = new LinkedBlockingQueue<>();
    private final AtomicSet<FlowableEmitter<? super EcgData>> ecgObservers = new AtomicSet<>();
    private final AtomicSet<FlowableEmitter<? super AccData>> accObservers = new AtomicSet<>();
    private final AtomicSet<FlowableEmitter<? super PpgData>> ppgObservers = new AtomicSet<>();
    private final AtomicSet<FlowableEmitter<? super PpiData>> ppiObservers = new AtomicSet<>();
    private final AtomicSet<FlowableEmitter<? super AutoGainAFE4404>> autoGainAFE4404Observers = new AtomicSet<>();
    private final AtomicSet<FlowableEmitter<? super AutoGainAFE4410>> autoGainAFE4410Observers = new AtomicSet<>();
    private final AtomicSet<FlowableEmitter<? super AutoGainADPD4000>> autoGainADPD4000Observers = new AtomicSet<>();
    private final AtomicSet<FlowableEmitter<? super Pair<Long, Long>>> afeOperationModeObservers = new AtomicSet<>();
    private final AtomicSet<FlowableEmitter<? super Pair<Long, Long>>> sportIdObservers = new AtomicSet<>();
    private final AtomicSet<FlowableEmitter<? super BiozData>> biozObservers = new AtomicSet<>();
    private final AtomicSet<FlowableEmitter<? super byte[]>> rdObservers = new AtomicSet<>();
    private byte[] pmdFeatureData = null;
    private final Object mutexFeature = new Object();

    private AtomicInteger pmdCpEnabled;
    private AtomicInteger pmdDataEnabled;

    public enum PmdMeasurementType {
        ECG(0),
        PPG(1),
        ACC(2),
        PPI(3),
        BIOZ(4),
        GYRO(5),
        MAGNETOMETER(6),
        BAROMETER(7),
        AMBIENT(8),
        UNKNOWN_TYPE(0xff);

        private int numVal;

        PmdMeasurementType(int numVal) {
            this.numVal = numVal;
        }

        public int getNumVal() {
            return numVal;
        }

        public static PmdMeasurementType fromId(byte id) {
            for (PmdMeasurementType type : values()) {
                if (type.numVal == id) {
                    return type;
                }
            }
            return UNKNOWN_TYPE;
        }
    }

    public enum PmdEcgDataType {
        ECG(0),
        BS01(1),
        MAX3000x(2);
        private int numVal;

        PmdEcgDataType(int numVal) {
            this.numVal = numVal;
        }

        public int getNumVal() {
            return numVal;
        }
    }


    public class PmdFeature {
        public boolean ecgSupported;
        public boolean ppgSupported;
        public boolean accSupported;
        public boolean ppiSupported;
        public boolean bioZSupported;
        public boolean gyroSupported;
        public boolean magnetometerSupported;
        public boolean barometerSupported;
        public boolean ambientSupported;

        public PmdFeature(final byte[] data) {
            ecgSupported = (data[1] & 0x01) != 0;
            ppgSupported = (data[1] & 0x02) != 0;
            accSupported = (data[1] & 0x04) != 0;
            ppiSupported = (data[1] & 0x08) != 0;
            bioZSupported = (data[1] & 0x10) != 0;
            gyroSupported = (data[1] & 0x20) != 0;
            magnetometerSupported = (data[1] & 0x40) != 0;
            barometerSupported = (data[1] & 0x80) != 0;
            ambientSupported = (data[2] & 0x01) != 0;
        }
    }

    enum PmdControlPointCommand {
        NULL_ITEM(0), // This fixes java enum bug
        GET_MEASUREMENT_SETTINGS(1),
        REQUEST_MEASUREMENT_START(2),
        STOP_MEASUREMENT(3);

        private int numVal;

        PmdControlPointCommand(int numVal) {
            this.numVal = numVal;
        }

        public int getNumVal() {
            return numVal;
        }
    }

    public static class PmdControlPointResponse {
        public byte responseCode;
        public PmdControlPointCommand opCode;
        public byte measurementType;
        public PmdControlPointResponseCode status;
        public ByteArrayOutputStream parameters = new ByteArrayOutputStream();
        public boolean more;

        public enum PmdControlPointResponseCode {
            SUCCESS(0),
            ERROR_INVALID_OP_CODE(1),
            ERROR_INVALID_MEASUREMENT_TYPE(2),
            ERROR_NOT_SUPPORTED(3),
            ERROR_INVALID_LENGTH(4),
            ERROR_INVALID_PARAMETER(5),
            ERROR_INVALID_STATE(6),
            ERROR_INVALID_RESOLUTION(7),
            ERROR_INVALID_SAMPLE_RATE(8),
            ERROR_INVALID_RANGE(9),
            ERROR_INVALID_MTU(10);

            private int numVal;

            PmdControlPointResponseCode(int numVal) {
                this.numVal = numVal;
            }

            public int getNumVal() {
                return numVal;
            }
        }

        public PmdControlPointResponse(byte[] data) {
            responseCode = data[0];
            opCode = PmdControlPointCommand.values()[data[1]];
            measurementType = data[2];
            status = PmdControlPointResponseCode.values()[data[3]];
            if (status == PmdControlPointResponseCode.SUCCESS) {
                more = data.length > 4 && data[4] != 0;
                if (data.length > 5) {
                    parameters.write(data, 5, data.length - 5);
                }
            }
        }
    }

    public static class PmdSetting {
        public enum PmdSettingType {
            SAMPLE_RATE(0),
            RESOLUTION(1),
            RANGE(2);

            private int numVal;

            PmdSettingType(int numVal) {
                this.numVal = numVal;
            }

            public int getNumVal() {
                return numVal;
            }
        }

        // available settings
        public Map<PmdSettingType, Set<Integer>> settings = new TreeMap<>();
        // selected by client
        public Map<PmdSettingType, Integer> selected;

        public PmdSetting() {
        }

        public PmdSetting(final byte[] data) {
            int offset = 0;
            while (offset < data.length) {
                PmdSettingType type = PmdSetting.PmdSettingType.values()[data[offset++]];
                int count = data[offset++];
                Set<Integer> items = new HashSet<>();
                while (count-- > 0) {
                    int item = (int) BleUtils.convertArrayToUnsignedLong(data, offset, 2);
                    items.add(item);
                    offset += 2;
                }
                settings.put(type, items);
            }
        }

        public PmdSetting(Map<PmdSettingType, Integer> selected) {
            this.selected = selected;
        }

        public byte[] serializeSelected() {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            for (Map.Entry<PmdSettingType, Integer> p : selected.entrySet()) {
                outputStream.write((byte) p.getKey().numVal);
                outputStream.write((byte) 1);
                int v = p.getValue();
                outputStream.write((byte) v);
                outputStream.write((byte) (v >> 8));
            }
            return outputStream.toByteArray();
        }

        public int selectedResolution() {
            return selected.get(PmdSettingType.RESOLUTION);
        }

        public PmdSetting maxSettings() {
            Map<PmdSettingType, Integer> set = new TreeMap<>();
            for (Map.Entry<PmdSettingType, Set<Integer>> p : settings.entrySet()) {
                set.put(p.getKey(), Collections.max(p.getValue()));
            }
            return new PmdSetting(set);
        }
    }

    public class EcgData {
        public class EcgSample {
            // samples in signed microvolts
            public PmdEcgDataType type;
            public long timeStamp;
            public int microVolts;
            public boolean overSampling;
            public byte skinContactBit;
            public byte contactImpedance;

            public byte ecgDataTag;
            public byte paceDataTag;
        }

        public long timeStamp;
        public List<EcgSample> ecgSamples = new ArrayList<>();

        public EcgData(byte type, byte[] value, long timeStamp) {
            int offset = 0;
            this.timeStamp = timeStamp;
            while (offset < value.length) {
                EcgSample sample = new EcgSample();
                sample.type = PmdEcgDataType.values()[type];
                sample.timeStamp = timeStamp;

                if (type == 1) { // BS01
                    sample.microVolts = ((value[offset] | (value[offset + 1] & 0x3F) << 8) & 0x3FFF);
                    sample.overSampling = (value[offset + 2] & 0x01) != 0;
                    sample.skinContactBit = (byte) ((value[offset + 2] & 0x06) >> 1);
                    sample.contactImpedance = (byte) ((value[offset + 2] & 0x18) >> 3);
                } else if (type == 2) { // MAX3000
                    sample.microVolts = ((value[offset] | (value[offset + 1] << 8) | ((value[offset + 2] & 0x03) << 16)) & 0x3FFF);
                    sample.ecgDataTag = (byte) (value[offset + 2] & 0x07);
                    sample.paceDataTag = (byte) (value[offset + 2] & 0x38);
                } else if (type == 0) { // production
                    sample.microVolts = BleUtils.convertArrayToSignedInt(value, offset, 3);
                }
                offset += 3;
                ecgSamples.add(sample);
            }
        }
    }

    public class AccData {
        public class AccSample {
            public final int x;
            public final int y;
            public final int z;

            AccSample(int x, int y, int z) {
                this.x = x;
                this.y = y;
                this.z = z;
            }
        }

        public final List<AccSample> accSamples = new ArrayList<>();
        public final long timeStamp;

        public AccData(byte type, byte[] value, long timeStamp) {
            int offset = 0;
            this.timeStamp = timeStamp;
            int resolution = (type + 1) * 8;
            int z, y, x, step = (int) Math.ceil((double) resolution / 8.0);
            while (offset < value.length) {
                x = BleUtils.convertArrayToSignedInt(value, offset, step);
                offset += step;
                y = BleUtils.convertArrayToSignedInt(value, offset, step);
                offset += step;
                z = BleUtils.convertArrayToSignedInt(value, offset, step);
                offset += step;
                accSamples.add(new AccSample(x, y, z));
            }
        }
    }

    public static class PpgData {
        public enum PpgFrameType {
            PPG0_TYPE(0),
            AFE4410(1),
            AFE4404(2),
            PPG1_TYPE(3),
            ADPD4000(4),
            AFE_OPERATION_MODE(5),
            SPORT_ID(6),
            UNKNOWN_TYPE(0xff);
            private int numVal;

            PpgFrameType(int numVal) {
                this.numVal = numVal;
            }

            public int getNumVal() {
                return numVal;
            }

            public static PpgFrameType fromId(byte id) {
                for (PpgFrameType type : values()) {
                    if (type.numVal == id) {
                        return type;
                    }
                }
                return UNKNOWN_TYPE;
            }
        }

        public class PpgSample {
            public List<Integer> ppgDataSamples;
            public int ppg0;
            public int ppg1;
            public int ppg2;
            public int ambient;
            public int ambient1;
            public long status;

            public PpgSample(List<Integer> ppgDataSamples, int ambient, int ambient1, long status) {
                this.ppgDataSamples = ppgDataSamples;
                this.ambient = ambient;
                this.ambient1 = ambient1;
                this.ppg0 = ppgDataSamples.get(0);
                this.ppg1 = ppgDataSamples.get(1);
                this.ppg2 = ppgDataSamples.get(2);
                this.status = status;
            }
        }

        public List<PpgSample> ppgSamples = new ArrayList<>();
        public long timeStamp;
        public byte type;

        public PpgData(byte[] value, long timeStamp, byte type) {
            this.timeStamp = timeStamp;
            this.type = type;
            final int step = 3;
            for (int i = 0; i < value.length; ) {
                List<Integer> samples = new ArrayList<>();
                int ambient, ambient1 = 0;
                int count = type == 0 ? 3 : 16;
                while (count-- > 0) {
                    samples.add(BleUtils.convertArrayToSignedInt(value, i, step));
                    i += step;
                }
                ambient = BleUtils.convertArrayToSignedInt(value, i, step);
                i += step;
                long status = 0;
                if (type != 0) {
                    ambient1 = BleUtils.convertArrayToSignedInt(value, i, step);
                    i += step;
                    status = BleUtils.convertArrayToUnsignedLong(value, i, 4);
                    i += 4;
                }
                ppgSamples.add(new PpgSample(samples, ambient, ambient1, status));
            }
        }
    }

    public class PpiData {
        public class PPSample {
            public int hr;
            public int ppInMs;
            public int ppErrorEstimate;
            public int blockerBit;
            public int skinContactStatus;
            public int skinContactSupported;

            public PPSample(byte[] data) {
                hr = (int) ((long) data[0] & 0xFFL);
                ppInMs = (int) BleUtils.convertArrayToUnsignedLong(data, 1, 2);
                ppErrorEstimate = (int) BleUtils.convertArrayToUnsignedLong(data, 3, 2);
                blockerBit = data[5] & 0x01;
                skinContactStatus = (data[5] & 0x02) >> 1;
                skinContactSupported = (data[5] & 0x04) >> 2;
            }
        }

        public List<PPSample> ppSamples = new ArrayList<>();
        public long timestamp;

        public PpiData(byte[] data, long timestamp) {
            int offset = 0;
            this.timestamp = timestamp;
            while (offset < data.length) {
                final int finalOffset = offset;
                ppSamples.add(new PPSample(Arrays.copyOfRange(data, finalOffset, finalOffset + 6)));
                offset += 6;
            }
        }
    }

    public class AutoGainAFE4404 {
        public byte I_OFFDAC;
        public byte TIA_GAIN;
        public byte ILED;
        public byte TIA_CF;
        public long timeStamp;

        public AutoGainAFE4404(final byte[] data, long timeStamp) {
            this.timeStamp = timeStamp;
            this.I_OFFDAC = (byte) (data[0] & 0x1f);
            this.TIA_GAIN = (byte) ((byte) (data[0] & 0xE0) >> 5);
            this.ILED = data[1];
            this.TIA_CF = data[2];
        }
    }

    public class AutoGainAFE4410 {
        public byte I_OFFDAC_1_MID;
        public byte I_OFFDAC_2_MID;
        public byte I_OFFDAC_3_MID;
        public byte I_OFFDAC_AMB_MID;
        public byte TIA_RF;
        public byte TIA_CF;
        public int ILED_1;
        public int ILED_2;
        public int ILED_3;
        public long timeStamp;

        public AutoGainAFE4410(final byte[] data, long timeStamp) {
            this.timeStamp = timeStamp;
            this.I_OFFDAC_1_MID = data[0];
            this.I_OFFDAC_2_MID = data[1];
            this.I_OFFDAC_3_MID = data[2];
            this.I_OFFDAC_AMB_MID = data[3];
            this.TIA_RF = data[4];
            this.TIA_CF = data[5];
            this.ILED_1 = data[6] & 0xFF;
            this.ILED_2 = data[7] & 0xFF;
            this.ILED_3 = data[8] & 0xFF;
        }
    }

    public class AutoGainADPD4000 {
        public byte TIA_GAIN_CH1_TS[];
        public byte TIA_GAIN_CH2_TS[];
        public byte NUMINT_TS[];
        public long timeStamp;

        public AutoGainADPD4000(final byte[] data, long timeStamp) {
            this.timeStamp = timeStamp;
            this.TIA_GAIN_CH1_TS = Arrays.copyOfRange(data, 0, 12);
            this.TIA_GAIN_CH2_TS = Arrays.copyOfRange(data, 12, 24);
            this.NUMINT_TS = Arrays.copyOfRange(data, 24, data.length);
        }
    }

    public class BiozData {
        public long timeStamp;
        public List<Integer> samples = new ArrayList<>();
        public byte status;
        public byte type;

        public BiozData(final byte[] data, long timeStamp, byte type) {
            this.timeStamp = timeStamp;
            this.type = type;
            int offset = 0;
            this.status = 0;
            while (offset < data.length) {
                if (type == 0) {
                    samples.add(BleUtils.convertArrayToSignedInt(data, offset, 3));
                    offset += 3;
                } else {
                    samples.add(BleUtils.convertArrayToSignedInt(data, offset, 3));
                    offset += 3;
                    samples.add(BleUtils.convertArrayToSignedInt(data, offset, 3));
                    offset += 3;
                    status = data[offset];
                    offset += 1;
                }
            }
        }
    }

    public BlePMDClient(BleGattTxInterface txInterface) {
        super(txInterface, PMD_SERVICE);
        addCharacteristicNotification(PMD_CP);
        addCharacteristicRead(PMD_CP);
        addCharacteristicNotification(PMD_DATA);
        pmdCpEnabled = getNotificationAtomicInteger(PMD_CP);
        pmdDataEnabled = getNotificationAtomicInteger(PMD_DATA);
    }

    @Override
    public void reset() {
        super.reset();
        RxUtils.postDisconnectedAndClearList(ecgObservers);
        RxUtils.postDisconnectedAndClearList(accObservers);
        RxUtils.postDisconnectedAndClearList(ppgObservers);
        RxUtils.postDisconnectedAndClearList(ppiObservers);
        RxUtils.postDisconnectedAndClearList(autoGainAFE4404Observers);
        RxUtils.postDisconnectedAndClearList(autoGainAFE4410Observers);
        RxUtils.postDisconnectedAndClearList(autoGainADPD4000Observers);
        RxUtils.postDisconnectedAndClearList(biozObservers);
        RxUtils.postDisconnectedAndClearList(afeOperationModeObservers);
        RxUtils.postDisconnectedAndClearList(sportIdObservers);
        RxUtils.postDisconnectedAndClearList(rdObservers);

        synchronized (mutexFeature) {
            pmdFeatureData = null;
            mutexFeature.notifyAll();
        }
    }

    @Override
    public void processServiceData(UUID characteristic, final byte[] data, int status, boolean notifying) {
        if (characteristic.equals(PMD_CP)) {
            if (notifying) {
                pmdCpInputQueue.add(new Pair<>(data, status));
            } else {
                // feature read
                synchronized (mutexFeature) {
                    pmdFeatureData = data;
                    mutexFeature.notifyAll();
                }
            }
        } else if (characteristic.equals(PMD_DATA)) {
            if (status == 0) {
                PmdMeasurementType type = PmdMeasurementType.fromId(data[0]);
                final long timeStamp = BleUtils.convertArrayToUnsignedLong(data, 1, 8);
                final byte frameType = data[9];
                final byte[] content = new byte[data.length - 10];
                System.arraycopy(data, 10, content, 0, content.length);
                switch (type) {
                    case ECG:
                        if (frameType <= 2) {
                            RxUtils.emitNext(ecgObservers, object -> object.onNext(new EcgData(frameType, content, timeStamp)));
                        } else {
                            BleLogger.w(TAG, "Unknown ECG frame type received");
                        }
                        break;
                    case PPG:
                        switch (PpgData.PpgFrameType.fromId(frameType)) {
                            case PPG1_TYPE:
                            case PPG0_TYPE: {
                                RxUtils.emitNext(ppgObservers, object -> object.onNext(new PpgData(content, timeStamp, frameType)));
                                break;
                            }
                            case AFE4410: {
                                RxUtils.emitNext(autoGainAFE4410Observers, object -> object.onNext(new AutoGainAFE4410(content, timeStamp)));
                                break;
                            }
                            case AFE4404: {
                                RxUtils.emitNext(autoGainAFE4404Observers, object -> object.onNext(new AutoGainAFE4404(content, timeStamp)));
                                break;
                            }
                            case ADPD4000: {
                                RxUtils.emitNext(autoGainADPD4000Observers, object -> object.onNext(new AutoGainADPD4000(content, timeStamp)));
                                break;
                            }
                            case AFE_OPERATION_MODE: {
                                RxUtils.emitNext(afeOperationModeObservers, object -> {
                                    Long value = BleUtils.convertArrayToUnsignedLong(content, 0, content.length);
                                    object.onNext(new Pair<>(timeStamp, value));
                                });
                                break;
                            }
                            case SPORT_ID: {
                                RxUtils.emitNext(sportIdObservers, object -> {
                                    final long sportId = BleUtils.convertArrayToUnsignedLong(content, 0, 8);
                                    object.onNext(new Pair<>(timeStamp, sportId));
                                });
                                break;
                            }
                            default:
                                BleLogger.w(TAG, "Unknown PPG frame type received");
                                break;
                        }
                        break;
                    case ACC:
                        if (frameType <= 2) {
                            RxUtils.emitNext(accObservers, object -> object.onNext(new AccData(frameType, content, timeStamp)));
                        } else {
                            BleLogger.w(TAG, "Unknown ACC frame type received");
                        }
                        break;
                    case PPI:
                        if (frameType == 0) {
                            RxUtils.emitNext(ppiObservers, object -> object.onNext(new PpiData(content, timeStamp)));
                        } else {
                            BleLogger.w(TAG, "Unknown PPI frame type received");
                        }
                        break;
                    case BIOZ:
                        if (frameType == 0 ||
                                frameType == 1) {
                            RxUtils.emitNext(biozObservers, object -> object.onNext(new BiozData(content, timeStamp, frameType)));
                        } else {
                            BleLogger.w(TAG, "Unknown BIOZ frame type received");
                        }
                        break;
                    case GYRO:
                    default:
                        final byte[] rdData = new byte[data.length - 1];
                        System.arraycopy(data, 1, content, 0, content.length);
                        RxUtils.emitNext(rdObservers, object -> object.onNext(rdData));
                        break;
                }
            } else {
                BleLogger.e(TAG, "pmd data attribute error");
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
        return "PMD Client";
    }

    private byte[] receiveControlPointPacket() throws Exception {
        Pair<byte[], Integer> pair = pmdCpInputQueue.poll(30, TimeUnit.SECONDS);
        if (pair != null) {
            if (pair.second == 0) {
                return pair.first;
            }
            throw new BleAttributeError("pmd cp attribute error: ", pair.second);
        }
        throw new Exception("Pmd response failed in receive in timeline");
    }

    private BlePMDClient.PmdControlPointResponse sendPmdCommand(byte[] packet) throws Exception {
        txInterface.transmitMessage(BlePMDClient.this, BlePMDClient.PMD_SERVICE, BlePMDClient.PMD_CP, packet, true);
        byte[] first = receiveControlPointPacket();
        BlePMDClient.PmdControlPointResponse response = new BlePMDClient.PmdControlPointResponse(first);
        boolean more = response.more;
        while (more) {
            byte[] moreParameters = receiveControlPointPacket();
            more = moreParameters[0] != 0;
            response.parameters.write(moreParameters, 1, moreParameters.length - 1);
        }
        return response;
    }

    private Single<PmdControlPointResponse> sendControlPointCommand(final PmdControlPointCommand command, final byte value) {
        return sendControlPointCommand(command, new byte[]{value});
    }

    private Single<PmdControlPointResponse> sendControlPointCommand(final PmdControlPointCommand command, final byte[] params) {
        return Single.create((SingleOnSubscribe<PmdControlPointResponse>) subscriber -> {
            synchronized (controlPointMutex) {
                try {
                    if (pmdCpEnabled.get() == ATT_SUCCESS && pmdDataEnabled.get() == ATT_SUCCESS) {
                        ByteBuffer bb = ByteBuffer.allocate(1 + params.length);
                        bb.put(new byte[]{(byte) command.getNumVal()});
                        bb.put(params);
                        PmdControlPointResponse response = sendPmdCommand(bb.array());
                        if (response.status == PmdControlPointResponse.PmdControlPointResponseCode.SUCCESS) {
                            subscriber.onSuccess(response);
                            return;
                        }
                        throw new Exception("Pmd cp failed: " + response.status.numVal);
                    }
                    throw new BleCharacteristicNotificationNotEnabled();
                } catch (Throwable throwable) {
                    if (!subscriber.isDisposed()) {
                        subscriber.tryOnError(throwable);
                    }
                }
            }
        }).subscribeOn(Schedulers.io());
    }

    /**
     * helper to query settings by type
     *
     * @return Single stream
     */
    public Single<PmdSetting> querySettings(PmdMeasurementType type) {
        return sendControlPointCommand(PmdControlPointCommand.GET_MEASUREMENT_SETTINGS, (byte) type.getNumVal()).map(new Function<PmdControlPointResponse, PmdSetting>() {
            @Override
            public PmdSetting apply(PmdControlPointResponse pmdControlPointResponse) throws Exception {
                return new PmdSetting(pmdControlPointResponse.parameters.toByteArray());
            }
        });
    }

    /**
     * @return Single stream
     */
    public Single<PmdFeature> readFeature(final boolean checkConnection) {
        return Single.create((SingleOnSubscribe<PmdFeature>) emitter -> {
            try {
                if (!checkConnection || txInterface.isConnected()) {
                    synchronized (mutexFeature) {
                        if (pmdFeatureData == null) {
                            mutexFeature.wait();
                        }
                        if (pmdFeatureData != null) {
                            emitter.onSuccess(new PmdFeature(pmdFeatureData));
                            return;
                        } else if (!txInterface.isConnected()) {
                            throw new BleDisconnected();
                        }
                        throw new Exception("Undefined device error");
                    }
                }
                throw new BleDisconnected();
            } catch (Exception ex) {
                if (!emitter.isDisposed()) {
                    emitter.tryOnError(ex);
                }
            }
        }).subscribeOn(Schedulers.io());
    }

    /**
     * query ecg settings available on device
     *
     * @return Single stream
     */
    public Single<PmdSetting> queryEcgSettings() {
        return querySettings(PmdMeasurementType.ECG);
    }

    /**
     * query ppg settings available on device
     *
     * @return Single stream
     */
    public Single<PmdSetting> queryPpgSettings() {
        return querySettings(PmdMeasurementType.PPG);
    }

    /**
     * query acc settings available on device
     *
     * @return Single stream
     */
    public Single<PmdSetting> queryAccSettings() {
        return querySettings(PmdMeasurementType.ACC);
    }

    /**
     * query acc settings available on device
     *
     * @return Single stream
     */
    public Single<PmdSetting> queryBiozSettings() {
        return querySettings(PmdMeasurementType.BIOZ);
    }

    /**
     * request to start a specific measurement
     *
     * @param type    measurement to start
     * @param setting desired settings
     * @return Completable stream
     */
    public Completable startMeasurement(final PmdMeasurementType type, final PmdSetting setting) {
        byte[] set = setting.serializeSelected();
        ByteBuffer bb = ByteBuffer.allocate(1 + set.length);
        bb.put((byte) type.getNumVal());
        bb.put(set);
        return sendControlPointCommand(PmdControlPointCommand.REQUEST_MEASUREMENT_START, bb.array()).toObservable().ignoreElements();
    }

    /**
     * request to stop measurement
     *
     * @param type measurement to stop
     * @return Completable stream
     */
    public Completable stopMeasurement(final PmdMeasurementType type) {
        return sendControlPointCommand(PmdControlPointCommand.STOP_MEASUREMENT, new byte[]{(byte) type.numVal}).toObservable().ignoreElements();
    }

    /**
     * start raw ecg monitoring
     *
     * @return Flowable stream Produces:
     * - onNext for every air packet received <BR>
     * - onComplete non produced if stream is not further configured <BR>
     * - onError BleDisconnected produced on disconnection <BR>
     */
    public Flowable<BlePMDClient.EcgData> monitorEcgNotifications(final boolean checkConnection) {
        return RxUtils.monitorNotifications(ecgObservers, txInterface, checkConnection);
    }

    /**
     * start raw acc monitoring
     *
     * @return Flowable stream Produces:
     * - onNext for every air packet received <BR>
     * - onComplete non produced if stream is not further configured <BR>
     * - onError BleDisconnected produced on disconnection <BR>
     */
    public Flowable<BlePMDClient.AccData> monitorAccNotifications(final boolean checkConnection) {
        return RxUtils.monitorNotifications(accObservers, txInterface, checkConnection);
    }

    /**
     * start raw ppg monitoring
     *
     * @return Flowable stream Produces:
     * - onNext for every air packet received <BR>
     * - onComplete non produced if stream is not further configured <BR>
     * - onError BleDisconnected produced on disconnection <BR>
     */
    public Flowable<BlePMDClient.PpgData> monitorPpgNotifications(final boolean checkConnection) {
        return RxUtils.monitorNotifications(ppgObservers, txInterface, checkConnection);
    }

    /**
     * start raw bioz monitoring
     *
     * @param checkConnection check initial connection
     * @return Flowable stream
     */
    public Flowable<BiozData> monitorBiozNotifications(final boolean checkConnection) {
        return RxUtils.monitorNotifications(biozObservers, txInterface, checkConnection);
    }

    /**
     * start raw ppi monitoring
     *
     * @return Flowable stream Produces:
     * - onNext for every air packet received <BR>
     * - onComplete non produced if stream is not further configured <BR>
     * - onError BleDisconnected produced on disconnection <BR>
     */
    public Flowable<BlePMDClient.PpiData> monitorPpiNotifications(final boolean checkConnection) {
        return RxUtils.monitorNotifications(ppiObservers, txInterface, checkConnection);
    }

    public Flowable<AutoGainAFE4404> monitorAutoGainAFE4404(final boolean checkConnection) {
        return RxUtils.monitorNotifications(autoGainAFE4404Observers, txInterface, checkConnection);
    }

    public Flowable<AutoGainAFE4410> monitorAutoGainAFE4410(final boolean checkConnection) {
        return RxUtils.monitorNotifications(autoGainAFE4410Observers, txInterface, checkConnection);
    }

    public Flowable<Pair<Long, Long>> monitorAfeOperationMode(final boolean checkConnection) {
        return RxUtils.monitorNotifications(afeOperationModeObservers, txInterface, checkConnection);
    }

    public Flowable<AutoGainADPD4000> monitorAutoGainADPD4000(final boolean checkConnection) {
        return RxUtils.monitorNotifications(autoGainADPD4000Observers, txInterface, checkConnection);
    }

    public Flowable<Pair<Long, Long>> monitorSportId(final boolean checkConnection) {
        return RxUtils.monitorNotifications(sportIdObservers, txInterface, checkConnection);
    }

    public Flowable<byte[]> monitorRDData(final boolean checkConnection) {
        return RxUtils.monitorNotifications(rdObservers, txInterface, checkConnection);
    }

    @Override
    public Completable clientReady(boolean checkConnection) {
        return Completable.concatArray(waitNotificationEnabled(PMD_CP, true),
                waitNotificationEnabled(PMD_DATA, true));
    }

    /**
     * @return current pmd feature or null if no features not available
     */
    public PmdFeature getPmdFeatureData() {
        synchronized (mutexFeature) {
            if (pmdFeatureData != null) {
                return new PmdFeature(pmdFeatureData);
            } else {
                return null;
            }
        }
    }
}