package com.polar.androidcommunications.api.ble.model.gatt.client;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.polar.androidcommunications.api.ble.BleLogger;
import com.polar.androidcommunications.api.ble.exceptions.BleAttributeError;
import com.polar.androidcommunications.api.ble.exceptions.BleCharacteristicNotificationNotEnabled;
import com.polar.androidcommunications.api.ble.exceptions.BleControlPointCommandError;
import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected;
import com.polar.androidcommunications.api.ble.exceptions.BleOperationModeChange;
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase;
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface;
import com.polar.androidcommunications.common.ble.AtomicSet;
import com.polar.androidcommunications.common.ble.BleUtils;
import com.polar.androidcommunications.common.ble.RxUtils;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import io.reactivex.rxjava3.schedulers.Schedulers;

public class BlePMDClient extends BleGattBase {

    private static final String TAG = BlePMDClient.class.getSimpleName();

    public static final UUID PMD_DATA = UUID.fromString("FB005C82-02E7-F387-1CAD-8ACD2D8DF0C8");
    public static final UUID PMD_CP = UUID.fromString("FB005C81-02E7-F387-1CAD-8ACD2D8DF0C8");
    public static final UUID PMD_SERVICE = UUID.fromString("FB005C80-02E7-F387-1CAD-8ACD2D8DF0C8");

    private final Object controlPointMutex = new Object();
    private final LinkedBlockingQueue<Pair<byte[], Integer>> pmdCpInputQueue = new LinkedBlockingQueue<>();
    private final AtomicSet<FlowableEmitter<? super EcgData>> ecgObservers = new AtomicSet<>();
    private final AtomicSet<FlowableEmitter<? super AccData>> accObservers = new AtomicSet<>();
    private final AtomicSet<FlowableEmitter<? super GyrData>> gyroObservers = new AtomicSet<>();
    private final AtomicSet<FlowableEmitter<? super MagData>> magnetometerObservers = new AtomicSet<>();
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
    private final Map<PmdMeasurementType, PmdSetting> currentSettings = new HashMap<>();
    private final AtomicInteger pmdCpEnabled;
    private final AtomicInteger pmdDataEnabled;

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
        SDK_MODE(9),
        UNKNOWN_TYPE(0xff);

        private final int numVal;

        PmdMeasurementType(int numVal) {
            this.numVal = numVal;
        }

        public int getNumVal() {
            return numVal;
        }

        @NonNull
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
        MAX3000X(2);
        private final int numVal;

        PmdEcgDataType(int numVal) {
            this.numVal = numVal;
        }

        public int getNumVal() {
            return numVal;
        }
    }

    public static class PmdFeature {
        public final boolean ecgSupported;
        public final boolean ppgSupported;
        public final boolean accSupported;
        public final boolean ppiSupported;
        public final boolean bioZSupported;
        public final boolean gyroSupported;
        public final boolean magnetometerSupported;
        public final boolean barometerSupported;
        public final boolean ambientSupported;
        public final boolean sdkModeSupported;

        public PmdFeature(final @NonNull byte[] data) {
            ecgSupported = (data[1] & 0x01) != 0;
            ppgSupported = (data[1] & 0x02) != 0;
            accSupported = (data[1] & 0x04) != 0;
            ppiSupported = (data[1] & 0x08) != 0;
            bioZSupported = (data[1] & 0x10) != 0;
            gyroSupported = (data[1] & 0x20) != 0;
            magnetometerSupported = (data[1] & 0x40) != 0;
            barometerSupported = (data[1] & 0x80) != 0;
            ambientSupported = (data[2] & 0x01) != 0;
            sdkModeSupported = (data[2] & 0x02) != 0;
        }
    }

    enum PmdControlPointCommand {
        NULL_ITEM(0), // This fixes java enum bug
        GET_MEASUREMENT_SETTINGS(1),
        REQUEST_MEASUREMENT_START(2),
        STOP_MEASUREMENT(3),
        GET_SDK_MODE_MEASUREMENT_SETTINGS(4);

        private final int numVal;

        PmdControlPointCommand(int numVal) {
            this.numVal = numVal;
        }

        public int getNumVal() {
            return numVal;
        }
    }

    public static class PmdControlPointResponse {
        public final byte responseCode;
        public final PmdControlPointCommand opCode;
        public final byte measurementType;
        public final PmdControlPointResponseCode status;
        public final ByteArrayOutputStream parameters = new ByteArrayOutputStream();
        public final boolean more;

        public enum PmdControlPointResponseCode {
            SUCCESS(0),
            ERROR_INVALID_OP_CODE(1),
            ERROR_INVALID_MEASUREMENT_TYPE(2),
            ERROR_NOT_SUPPORTED(3),
            ERROR_INVALID_LENGTH(4),
            ERROR_INVALID_PARAMETER(5),
            ERROR_ALREADY_IN_STATE(6),
            ERROR_INVALID_RESOLUTION(7),
            ERROR_INVALID_SAMPLE_RATE(8),
            ERROR_INVALID_RANGE(9),
            ERROR_INVALID_MTU(10),
            ERROR_INVALID_NUMBER_OF_CHANNELS(11),
            ERROR_INVALID_STATE(12),
            ERROR_DEVICE_IN_CHARGER(13);
            private final int numVal;

            PmdControlPointResponseCode(int numVal) {
                this.numVal = numVal;
            }

            public int getNumVal() {
                return numVal;
            }
        }

        public PmdControlPointResponse(@NonNull byte[] data) {
            responseCode = data[0];
            opCode = PmdControlPointCommand.values()[data[1]];
            measurementType = data[2];
            status = PmdControlPointResponseCode.values()[data[3]];
            if (status == PmdControlPointResponseCode.SUCCESS) {
                more = data.length > 4 && data[4] != 0;
                if (data.length > 5) {
                    parameters.write(data, 5, data.length - 5);
                }
            } else {
                more = false;
            }
        }
    }

    public static class PmdSetting {
        public enum PmdSettingType {
            SAMPLE_RATE(0),
            RESOLUTION(1),
            RANGE(2),
            RANGE_MILLIUNIT(3),
            CHANNELS(4),
            FACTOR(5);

            private final int numVal;

            PmdSettingType(int numVal) {
                this.numVal = numVal;
            }

            public int getNumVal() {
                return numVal;
            }
        }

        private static final EnumMap<PmdSettingType, Integer> typeToFieldSize = new EnumMap<PmdSettingType, Integer>(PmdSettingType.class) {{
            put(PmdSettingType.SAMPLE_RATE, 2);
            put(PmdSettingType.RESOLUTION, 2);
            put(PmdSettingType.RANGE, 2);
            put(PmdSettingType.RANGE_MILLIUNIT, 4); // not has range from min to max
            put(PmdSettingType.CHANNELS, 1);
            put(PmdSettingType.FACTOR, 4);
        }};

        // available settings
        @Nullable
        public Map<PmdSettingType, Set<Integer>> settings = new TreeMap<>();

        // selected by client
        @Nullable
        public Map<PmdSettingType, Integer> selected;

        public PmdSetting() {
        }

        public PmdSetting(final @NonNull byte[] data) {
            EnumMap<PmdSettingType, Set<Integer>> parsedSettings = parsePmdSettingsData(data);
            validateSettings(parsedSettings);
            this.settings = parsedSettings;
        }

        public PmdSetting(@NonNull Map<PmdSettingType, Integer> selected) {
            PmdSetting.validateSelected(selected);
            this.selected = selected;
        }

        EnumMap<PmdSettingType, Set<Integer>> parsePmdSettingsData(final byte[] data) {
            EnumMap<PmdSettingType, Set<Integer>> parsedSettings = new EnumMap<>(PmdSettingType.class);
            if (data.length <= 1) {
                return parsedSettings;
            }

            int offset = 0;
            while (offset < data.length) {
                PmdSettingType type = PmdSetting.PmdSettingType.values()[data[offset++]];
                int count = data[offset++];
                Set<Integer> items = new HashSet<>();
                while (count-- > 0) {
                    int fieldSize = Objects.requireNonNull(typeToFieldSize.get(type));
                    int item = BleUtils.convertArrayToUnsignedInt(data, offset, fieldSize);
                    offset += fieldSize;
                    items.add(item);
                }
                parsedSettings.put(type, items);
            }
            return parsedSettings;
        }

        void updateSelectedFromStartResponse(final byte[] data) {
            EnumMap<PmdSettingType, Set<Integer>> settingsFromStartResponse = parsePmdSettingsData(data);
            if (settingsFromStartResponse.containsKey(PmdSettingType.FACTOR)) {
                selected.put(PmdSettingType.FACTOR, settingsFromStartResponse.get(PmdSettingType.FACTOR).iterator().next());
            }
        }

        @NonNull
        public byte[] serializeSelected() {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            if (selected != null) {
                for (Map.Entry<PmdSettingType, Integer> p : selected.entrySet()) {
                    if (p.getKey() == PmdSettingType.FACTOR) {
                        continue;
                    }
                    outputStream.write((byte) p.getKey().numVal);
                    outputStream.write((byte) 1);
                    int v = p.getValue();
                    int fieldSize = Objects.requireNonNull(typeToFieldSize.get(p.getKey()));
                    for (int i = 0; i < fieldSize; ++i) {
                        outputStream.write((byte) (v >> (i * 8)));
                    }
                }
            }
            return outputStream.toByteArray();
        }

        @NonNull
        public PmdSetting maxSettings() {
            Map<PmdSettingType, Integer> set = new TreeMap<>();
            if (settings != null) {
                for (Map.Entry<PmdSettingType, Set<Integer>> p : settings.entrySet()) {
                    set.put(p.getKey(), Collections.max(p.getValue()));
                }
            }
            return new PmdSetting(set);
        }

        private static void validateSettings(Map<PmdSettingType, Set<Integer>> settings) {
            for (Map.Entry<PmdSettingType, Set<Integer>> setting : settings.entrySet()) {
                PmdSettingType key = setting.getKey();
                for (Integer value : setting.getValue()) {
                    Map.Entry<PmdSettingType, Integer> entry = new AbstractMap.SimpleEntry<>(key, value);
                    validateSetting(entry);
                }
            }
        }

        private static void validateSelected(Map<PmdSettingType, Integer> settings) {
            for (Map.Entry<PmdSettingType, Integer> setting : settings.entrySet()) {
                validateSetting(setting);
            }
        }

        private static void validateSetting(Map.Entry<PmdSettingType, Integer> setting) {
            int fieldSize = typeToFieldSize.get(setting.getKey());
            int value = setting.getValue();
            if (fieldSize == 1 && (value < 0x0 || 0xFF < value)) {
                throw new RuntimeException("PmdSetting not in valid range. Field size: " + fieldSize + " value: " + value);
            }
            if (fieldSize == 2 && (value < 0x0 || 0xFFFF < value)) {
                throw new RuntimeException("PmdSetting not in valid range. Field size: " + fieldSize + " value: " + value);
            }
            if (fieldSize == 3 && (value < 0x0 || 0xFFFFFF < value)) {
                throw new RuntimeException("PmdSetting not in valid range. Field size: " + fieldSize + " value: " + value);
            }
        }
    }

    @NonNull
    public static List<List<Integer>> parseDeltaFrame(@NonNull byte[] bytes, int channels, int bitWidth, int totalBitLength) {
        int offset = 0;
        List<Boolean> bitSet = new ArrayList<>();
        for (byte b : bytes) {
            for (int i = 0; i < 8; ++i) {
                bitSet.add((b & (0x01 << i)) != 0);
            }
        }
        List<List<Integer>> samples = new ArrayList<>();
        int mask = Integer.MAX_VALUE << (bitWidth - 1);
        while (offset < totalBitLength) {
            List<Integer> channelSamples = new ArrayList<>();
            int channelCount = 0;
            while (channelCount++ < channels) {
                List<Boolean> bits = bitSet.subList(offset, offset + bitWidth);
                int val = 0;
                for (int i = 0; i < bits.size(); ++i) {
                    val |= ((bits.get(i) ? 0x01 : 0x00) << i);
                }
                if ((val & mask) != 0) {
                    val |= mask;
                }
                offset += bitWidth;
                channelSamples.add(val);
            }
            samples.add(channelSamples);
        }
        return samples;
    }

    @NonNull
    public static List<Integer> parseDeltaFrameRefSamples(@NonNull byte[] bytes, int channels, int resolution) {
        List<Integer> samples = new ArrayList<>();
        int offset = 0;
        int channelCount = 0;
        int mask = 0xFFFFFFFF << (resolution - 1);
        int resolutionInBytes = (int) Math.ceil(resolution / 8.0);
        while (channelCount++ < channels) {
            int sample = BleUtils.convertArrayToSignedInt(bytes, offset, resolutionInBytes);
            if ((sample & mask) != 0) {
                sample |= mask;
            }
            offset += resolutionInBytes;
            samples.add(sample);
        }
        return samples;
    }

    @NonNull
    public static List<List<Integer>> parseDeltaFramesAll(@NonNull byte[] value,
                                                          int channels,
                                                          int resolution) {
        int offset = 0;
        List<Integer> refSamples = parseDeltaFrameRefSamples(value, channels, resolution);
        offset += channels * Math.ceil(resolution / 8.0);
        List<List<Integer>> samples = new ArrayList<>(Collections.singleton(refSamples));
        BleUtils.validate(refSamples.size() == channels, "incorrect number of ref channels");
        while (offset < value.length) {
            int deltaSize = value[offset++] & 0xFF;
            int sampleCount = value[offset++] & 0xFF;
            int bitLength = (sampleCount * deltaSize * channels);
            int length = (int) Math.ceil(bitLength / 8.0);
            final byte[] deltaFrame = new byte[length];
            System.arraycopy(value, offset, deltaFrame, 0, deltaFrame.length);
            List<List<Integer>> deltaSamples = parseDeltaFrame(deltaFrame, channels, deltaSize, bitLength);
            for (List<Integer> delta : deltaSamples) {
                BleUtils.validate(delta.size() == channels, "incorrect number of delta channels");
                List<Integer> lastSample = samples.get(samples.size() - 1);
                List<Integer> nextSamples = new ArrayList<>();
                for (int i = 0; i < channels; ++i) {
                    int sample = lastSample.get(i) + delta.get(i);
                    nextSamples.add(sample);
                }
                samples.addAll(Collections.singleton(nextSamples));
            }
            offset += length;
        }

        return samples;
    }

    public static class EcgData {
        public static class EcgSample {
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

        public final long timeStamp;
        public final List<EcgSample> ecgSamples = new ArrayList<>();

        public EcgData(byte type, @NonNull byte[] value, long timeStamp) {
            int offset = 0;
            this.timeStamp = timeStamp;
            while (offset < value.length) {
                EcgSample sample = new EcgSample();
                sample.type = PmdEcgDataType.values()[type];
                sample.timeStamp = timeStamp;

                if (type == 1) { // BS01
                    sample.microVolts = (((value[offset] & 0xFF) | (value[offset + 1] & 0x3F) << 8) & 0x3FFF);
                    sample.overSampling = (value[offset + 2] & 0x01) != 0;
                    sample.skinContactBit = (byte) ((value[offset + 2] & 0x06) >> 1);
                    sample.contactImpedance = (byte) ((value[offset + 2] & 0x18) >> 3);
                } else if (type == 2) { // MAX3000
                    sample.microVolts = (((value[offset] & 0xFF) | ((value[offset + 1] & 0xFF) << 8) | ((value[offset + 2] & 0x03) << 16)) & 0x3FFFFF);
                    sample.ecgDataTag = (byte) ((value[offset + 2] & 0x1C) >> 2);
                    sample.paceDataTag = (byte) ((value[offset + 2] & 0xE0) >> 5);
                } else if (type == 0) { // production
                    sample.microVolts = BleUtils.convertArrayToSignedInt(value, offset, 3);
                }
                offset += 3;
                ecgSamples.add(sample);
            }
        }
    }

    public static class AccData {
        public static class AccSample {
            // Sample contains signed x,y,z axis values in milliG
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

        public AccData(byte type, @NonNull byte[] value, long timeStamp) {
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

        /**
         * ACC samples from delta frame
         *
         * @param value      bytes
         * @param factor     relative to absolute multiplier
         * @param resolution int bits
         * @param timeStamp  ns
         */
        public AccData(@NonNull byte[] value, float factor, int resolution, long timeStamp) {
            this.timeStamp = timeStamp;
            float accFactor = factor * 1000; // Modify the factor to get data in milliG
            ThreeAxisDeltaFramedData data = new ThreeAxisDeltaFramedData(value, accFactor, resolution, timeStamp);
            for (ThreeAxisDeltaFramedData.ThreeAxisSample sample : data.axisSamples) {
                this.accSamples.add(new AccSample((int) sample.x, (int) sample.y, (int) sample.z));
            }
        }
    }

    public static class MagData {
        public static class MagSample {
            // Sample contains signed x,y,z axis values in Gauss
            public final float x;
            public final float y;
            public final float z;

            MagSample(float x, float y, float z) {
                this.x = x;
                this.y = y;
                this.z = z;
            }
        }

        public final List<MagSample> magSamples = new ArrayList<>();
        public final long timeStamp;

        /**
         * Magnetometer samples from delta frame
         *
         * @param value      bytes
         * @param factor     relative to absolute multiplier
         * @param resolution int bits
         * @param timeStamp  ns
         */
        public MagData(@NonNull byte[] value, float factor, int resolution, long timeStamp) {
            this.timeStamp = timeStamp;
            ThreeAxisDeltaFramedData data = new ThreeAxisDeltaFramedData(value, factor, resolution, timeStamp);
            for (ThreeAxisDeltaFramedData.ThreeAxisSample sample : data.axisSamples) {
                this.magSamples.add(new MagSample(sample.x, sample.y, sample.z));
            }
        }
    }

    public static class GyrData {
        public static class GyrSample {
            // Sample contains signed x,y,z axis values in deg/sec
            public final float x;
            public final float y;
            public final float z;

            GyrSample(float x, float y, float z) {
                this.x = x;
                this.y = y;
                this.z = z;
            }
        }

        public final List<GyrSample> gyrSamples = new ArrayList<>();
        public final long timeStamp;

        /**
         * Magnetometer samples from delta frame
         *
         * @param value      bytes
         * @param factor     relative to absolute multiplier
         * @param resolution int bits
         * @param timeStamp  ns
         */
        public GyrData(@NonNull byte[] value, float factor, int resolution, long timeStamp) {
            this.timeStamp = timeStamp;
            ThreeAxisDeltaFramedData data = new ThreeAxisDeltaFramedData(value, factor, resolution, timeStamp);
            for (ThreeAxisDeltaFramedData.ThreeAxisSample sample : data.axisSamples) {
                this.gyrSamples.add(new GyrSample(sample.x, sample.y, sample.z));
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
            DELTA_FRAME(128),
            UNKNOWN_TYPE(0xff);
            private final int numVal;

            PpgFrameType(int numVal) {
                this.numVal = numVal;
            }

            public int getNumVal() {
                return numVal;
            }

            @NonNull
            public static PpgFrameType fromId(int id) {
                for (PpgFrameType type : values()) {
                    if (type.numVal == id) {
                        return type;
                    }
                }
                return UNKNOWN_TYPE;
            }
        }

        public static class PpgSample {
            public final List<Integer> ppgDataSamples;
            public final long status;

            public PpgSample(@NonNull List<Integer> ppgDataSamples) {
                this.ppgDataSamples = ppgDataSamples;
                this.status = 0;
            }

            public PpgSample(@NonNull List<Integer> ppgDataSamples, long status) {
                this.ppgDataSamples = ppgDataSamples;
                this.status = status;
            }
        }

        public final List<PpgSample> ppgSamples = new ArrayList<>();
        public final long timeStamp;
        public final int channels;

        public PpgData(@NonNull byte[] value, long timeStamp, int type) {
            this.timeStamp = timeStamp;
            this.channels = type == 0 ? 4 : 18;
            final int step = 3;
            for (int i = 0; i < value.length; ) {
                List<Integer> samples = new ArrayList<>();
                for (int ch = 0; ch < this.channels; ++ch) {
                    samples.add(BleUtils.convertArrayToSignedInt(value, i, step));
                    i += step;
                }
                long status = 0;
                if (channels == 18) {
                    status = BleUtils.convertArrayToUnsignedLong(value, i, 4);
                    i += 4;
                }
                ppgSamples.add(new PpgSample(samples, status));
            }
        }

        /**
         * PPG samples from delta frame
         *
         * @param value      bytes
         * @param factor     relative to absolute multiplier
         * @param resolution int bits
         * @param channels   number of channels in one sample
         * @param timeStamp  ns
         */
        public PpgData(@NonNull byte[] value, float factor, int resolution, int channels, long timeStamp) {
            List<List<Integer>> samples = parseDeltaFramesAll(value, channels, resolution);
            for (List<Integer> sample : samples) {
                for (int i = 0; i < sample.size(); i++) {
                    int absoluteChannelValue = (int) ((float) sample.get(i) * factor);
                    sample.set(i, absoluteChannelValue);
                }
                ppgSamples.add(new PpgSample(sample));
            }
            this.timeStamp = timeStamp;
            this.channels = channels;
        }
    }

    public static class PpiData {
        public static class PPSample {
            public final int hr;
            public final int ppInMs;
            public final int ppErrorEstimate;
            public final int blockerBit;
            public final int skinContactStatus;
            public final int skinContactSupported;

            public PPSample(@NonNull byte[] data) {
                hr = (int) ((long) data[0] & 0xFFL);
                ppInMs = (int) BleUtils.convertArrayToUnsignedLong(data, 1, 2);
                ppErrorEstimate = (int) BleUtils.convertArrayToUnsignedLong(data, 3, 2);
                blockerBit = data[5] & 0x01;
                skinContactStatus = (data[5] & 0x02) >> 1;
                skinContactSupported = (data[5] & 0x04) >> 2;
            }
        }

        public final List<PPSample> ppSamples = new ArrayList<>();
        public final long timeStamp;

        public PpiData(@NonNull byte[] data, long timeStamp) {
            int offset = 0;
            this.timeStamp = timeStamp;
            while (offset < data.length) {
                final int finalOffset = offset;
                ppSamples.add(new PPSample(Arrays.copyOfRange(data, finalOffset, finalOffset + 6)));
                offset += 6;
            }
        }
    }

    public static class AutoGainAFE4404 {
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

    public static class AutoGainAFE4410 {
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

    public static class AutoGainADPD4000 {
        public byte[] TIA_GAIN_CH1_TS;
        public byte[] TIA_GAIN_CH2_TS;
        public byte[] NUMINT_TS;
        public long timeStamp;

        public AutoGainADPD4000(final byte[] data, long timeStamp) {
            this.timeStamp = timeStamp;
            this.TIA_GAIN_CH1_TS = Arrays.copyOfRange(data, 0, 12);
            this.TIA_GAIN_CH2_TS = Arrays.copyOfRange(data, 12, 24);
            this.NUMINT_TS = Arrays.copyOfRange(data, 24, data.length);
        }
    }

    public static class BiozData {
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

    public static class ThreeAxisDeltaFramedData {
        public static class ThreeAxisSample {
            public final float x;
            public final float y;
            public final float z;

            public ThreeAxisSample(float x, float y, float z) {
                this.x = x;
                this.y = y;
                this.z = z;
            }
        }

        public final List<ThreeAxisSample> axisSamples = new ArrayList<>();
        public final long timeStamp;

        /**
         * Three axis samples from delta frame
         *
         * @param value      bytes
         * @param factor     relative to absolute multiplier
         * @param resolution int bits
         * @param timeStamp  ns
         */
        public ThreeAxisDeltaFramedData(@NonNull byte[] value, float factor, int resolution, long timeStamp) {
            this.timeStamp = timeStamp;
            List<List<Integer>> samples = parseDeltaFramesAll(value, 3, resolution);
            for (List<Integer> sample : samples) {
                BleUtils.validate(sample.size() == 3, "delta samples invalid length");
                float channel0 = (float) sample.get(0) * factor;
                float channel1 = (float) sample.get(1) * factor;
                float channel2 = (float) sample.get(2) * factor;
                axisSamples.add(new ThreeAxisSample(channel0, channel1, channel2));
            }
        }
    }

    public BlePMDClient(@NonNull BleGattTxInterface txInterface) {
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
        clearStreamObservers(new BleDisconnected());

        synchronized (mutexFeature) {
            pmdFeatureData = null;
            mutexFeature.notifyAll();
        }
    }

    private float fetchFactor(PmdMeasurementType type) {
        BleUtils.validate(currentSettings.containsKey(type), type + " setting not stored");
        if (currentSettings.get(type).selected.containsKey(PmdSetting.PmdSettingType.FACTOR)) {
            int ieee754 = currentSettings.get(type).selected.get(PmdSetting.PmdSettingType.FACTOR);
            return Float.intBitsToFloat(ieee754);
        } else {
            BleLogger.e(TAG, "No factor found for type: " + type);
            return 1.0f;
        }
    }

    private int fetchSetting(PmdMeasurementType type, PmdSetting.PmdSettingType setting) {
        BleUtils.validate(currentSettings.containsKey(type), type.toString() + " setting not stored");
        BleUtils.validate(Objects.requireNonNull(currentSettings.get(type)).selected.containsKey(setting), type.toString() + " setting not stored");
        return currentSettings.get(type).selected.get(setting);
    }

    @Override
    public void processServiceData(@NonNull UUID characteristic, final @NonNull byte[] data, int status, boolean notifying) {
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

                BleLogger.d_hex(TAG, "pmd data: ", data);

                PmdMeasurementType type = PmdMeasurementType.fromId(data[0]);
                final long timeStamp = BleUtils.convertArrayToUnsignedLong(data, 1, 8);
                final long frameType = BleUtils.convertArrayToUnsignedLong(data, 9, 1);
                final byte[] content = new byte[data.length - 10];
                System.arraycopy(data, 10, content, 0, content.length);
                switch (type) {
                    case ECG:
                        if (frameType <= 2) {
                            RxUtils.emitNext(ecgObservers, object -> object.onNext(new EcgData((byte) frameType, content, timeStamp)));
                        } else {
                            BleLogger.w(TAG, "Unknown ECG frame type received");
                        }
                        break;
                    case PPG:
                        switch (PpgData.PpgFrameType.fromId((int) frameType)) {
                            case PPG1_TYPE:
                            case PPG0_TYPE: {
                                RxUtils.emitNext(ppgObservers, object -> object.onNext(new PpgData(content, timeStamp, (int) frameType)));
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
                            case DELTA_FRAME: {
                                float factor = fetchFactor(PmdMeasurementType.PPG);
                                int resolution = fetchSetting(PmdMeasurementType.PPG, PmdSetting.PmdSettingType.RESOLUTION);
                                int channels = fetchSetting(PmdMeasurementType.PPG, PmdSetting.PmdSettingType.CHANNELS);
                                RxUtils.emitNext(ppgObservers, object -> object.onNext(
                                        new PpgData(content, factor, resolution, channels, timeStamp)));
                                break;
                            }
                            default:
                                BleLogger.w(TAG, "Unknown PPG frame type received");
                                break;
                        }
                        break;
                    case ACC:
                        if (frameType <= 2) {
                            RxUtils.emitNext(accObservers, object -> object.onNext(new AccData((byte) frameType, content, timeStamp)));
                        } else if (frameType == 128) {
                            float factor = fetchFactor(PmdMeasurementType.ACC);
                            int resolution = fetchSetting(PmdMeasurementType.ACC, PmdSetting.PmdSettingType.RESOLUTION);
                            RxUtils.emitNext(accObservers, object -> object.onNext(new AccData(content, factor, resolution, timeStamp)));

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
                        if (frameType == 0 || frameType == 1) {
                            RxUtils.emitNext(biozObservers, object -> object.onNext(
                                    new BiozData(content, timeStamp, (byte) frameType)));
                        } else {
                            BleLogger.w(TAG, "Unknown BIOZ frame type received");
                        }
                        break;
                    case GYRO:
                        if (frameType == 128) {
                            float factor = fetchFactor(PmdMeasurementType.GYRO);
                            int resolution = fetchSetting(PmdMeasurementType.GYRO, PmdSetting.PmdSettingType.RESOLUTION);
                            RxUtils.emitNext(gyroObservers, object -> object.onNext(
                                    new GyrData(content, factor, resolution, timeStamp)));
                        } else {
                            BleLogger.w(TAG, "Unknown GYRO frame type received");
                        }
                        break;
                    case MAGNETOMETER:
                        if (frameType == 128) {
                            float factor = fetchFactor(PmdMeasurementType.MAGNETOMETER);
                            int resolution = fetchSetting(PmdMeasurementType.MAGNETOMETER, PmdSetting.PmdSettingType.RESOLUTION);
                            RxUtils.emitNext(magnetometerObservers, object -> object.onNext(
                                    new MagData(content, factor, resolution, timeStamp)));
                        } else {
                            BleLogger.w(TAG, "Unknown MAGNETOMETER frame type received");
                        }
                        break;
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

    @NonNull
    @Override
    public String toString() {
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
        throw new Exception("Pmd response failed to receive in timeline");
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
                        throw new BleControlPointCommandError("pmd cp command error: ", response.status);
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
     * Query settings by type
     *
     * @return Single stream
     * - onSuccess settings query success, the queried settings emitted
     * - onError settings query failed
     */
    @NonNull
    public Single<PmdSetting> querySettings(@NonNull PmdMeasurementType type) {
        return sendControlPointCommand(PmdControlPointCommand.GET_MEASUREMENT_SETTINGS, (byte) type.getNumVal())
                .map(pmdControlPointResponse -> new PmdSetting(pmdControlPointResponse.parameters.toByteArray()));
    }

    /**
     * Query full settings by type
     *
     * @return Single stream
     * - onSuccess full settings query success, the queried settings emitted
     * - onError full settings query failed
     */
    @NonNull
    public Single<PmdSetting> queryFullSettings(@NonNull PmdMeasurementType type) {
        return sendControlPointCommand(PmdControlPointCommand.GET_SDK_MODE_MEASUREMENT_SETTINGS, (byte) type.getNumVal())
                .map(pmdControlPointResponse -> new PmdSetting(pmdControlPointResponse.parameters.toByteArray()));
    }

    /**
     * @return Single stream
     */
    @NonNull
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
     * query bioz settings available on device
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
    @NonNull
    public Completable startMeasurement(@NonNull final PmdMeasurementType type, @NonNull final PmdSetting setting) {
        byte[] set = setting.serializeSelected();
        ByteBuffer bb = ByteBuffer.allocate(1 + set.length);
        bb.put((byte) type.getNumVal());
        bb.put(set);
        currentSettings.put(type, setting);
        return sendControlPointCommand(PmdControlPointCommand.REQUEST_MEASUREMENT_START, bb.array())
                .doOnSuccess(pmdControlPointResponse ->
                        currentSettings.get(type).updateSelectedFromStartResponse(pmdControlPointResponse.parameters.toByteArray()))
                .toObservable()
                .ignoreElements();
    }

    /**
     * Request to start SDK mode
     *
     * @return Completable stream
     * - onComplete start SDK mode request completed successfully
     * - onError start SDK mode request failed
     */
    @NonNull
    public Completable startSDKMode() {
        return sendControlPointCommand(PmdControlPointCommand.REQUEST_MEASUREMENT_START, (byte) PmdMeasurementType.SDK_MODE.getNumVal())
                .toObservable()
                .doOnComplete(() -> clearStreamObservers(new BleOperationModeChange("SDK mode enabled")))
                .ignoreElements();
    }

    /**
     * Request to stop SDK mode
     *
     * @return Completable stream
     * - onComplete stop SDK mode request completed successfully
     * - onError stop SDK mode request failed
     */
    @NonNull
    public Completable stopSDKMode() {
        return sendControlPointCommand(PmdControlPointCommand.STOP_MEASUREMENT, (byte) PmdMeasurementType.SDK_MODE.getNumVal())
                .toObservable()
                .doOnComplete(() -> clearStreamObservers(new BleOperationModeChange("SDK mode disabled")))
                .ignoreElements();
    }

    /**
     * Request to stop measurement
     *
     * @param type measurement to stop
     * @return Completable stream
     */
    @NonNull
    public Completable stopMeasurement(final @NonNull PmdMeasurementType type) {
        return sendControlPointCommand(PmdControlPointCommand.STOP_MEASUREMENT, new byte[]{(byte) type.numVal})
                .toObservable()
                .ignoreElements();
    }

    /**
     * start raw ecg monitoring
     *
     * @return Flowable stream Produces:
     * - onNext for every air packet received <BR>
     * - onComplete non produced if stream is not further configured <BR>
     * - onError BleDisconnected produced on disconnection <BR>
     */
    @NonNull
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
    @NonNull
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
    @NonNull
    public Flowable<BlePMDClient.PpgData> monitorPpgNotifications(final boolean checkConnection) {
        return RxUtils.monitorNotifications(ppgObservers, txInterface, checkConnection);
    }

    /**
     * start raw bioz monitoring
     *
     * @param checkConnection check initial connection
     * @return Flowable stream
     */
    @NonNull
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
    @NonNull
    public Flowable<PpiData> monitorPpiNotifications(final boolean checkConnection) {
        return RxUtils.monitorNotifications(ppiObservers, txInterface, checkConnection);
    }

    @NonNull
    public Flowable<MagData> monitorMagnetometerNotifications(final boolean checkConnection) {
        return RxUtils.monitorNotifications(magnetometerObservers, txInterface, checkConnection);
    }

    @NonNull
    public Flowable<GyrData> monitorGyroNotifications(final boolean checkConnection) {
        return RxUtils.monitorNotifications(gyroObservers, txInterface, checkConnection);
    }

    @NonNull
    public Flowable<AutoGainAFE4404> monitorAutoGainAFE4404(final boolean checkConnection) {
        return RxUtils.monitorNotifications(autoGainAFE4404Observers, txInterface, checkConnection);
    }

    @NonNull
    public Flowable<AutoGainAFE4410> monitorAutoGainAFE4410(final boolean checkConnection) {
        return RxUtils.monitorNotifications(autoGainAFE4410Observers, txInterface, checkConnection);
    }

    @NonNull
    public Flowable<Pair<Long, Long>> monitorAfeOperationMode(final boolean checkConnection) {
        return RxUtils.monitorNotifications(afeOperationModeObservers, txInterface, checkConnection);
    }

    @NonNull
    public Flowable<AutoGainADPD4000> monitorAutoGainADPD4000(final boolean checkConnection) {
        return RxUtils.monitorNotifications(autoGainADPD4000Observers, txInterface, checkConnection);
    }

    @NonNull
    public Flowable<Pair<Long, Long>> monitorSportId(final boolean checkConnection) {
        return RxUtils.monitorNotifications(sportIdObservers, txInterface, checkConnection);
    }

    @NonNull
    public Flowable<byte[]> monitorRDData(final boolean checkConnection) {
        return RxUtils.monitorNotifications(rdObservers, txInterface, checkConnection);
    }

    @NonNull
    @Override
    public Completable clientReady(boolean checkConnection) {
        return Completable.concatArray(waitNotificationEnabled(PMD_CP, true),
                waitNotificationEnabled(PMD_DATA, true));
    }

    /**
     * @return current pmd feature or null if no features not available
     */
    @Nullable
    public PmdFeature getPmdFeatureData() {
        synchronized (mutexFeature) {
            if (pmdFeatureData != null) {
                return new PmdFeature(pmdFeatureData);
            } else {
                return null;
            }
        }
    }

    private void clearStreamObservers(@NonNull Throwable throwable) {
        RxUtils.postExceptionAndClearList(ecgObservers, throwable);
        RxUtils.postExceptionAndClearList(accObservers, throwable);
        RxUtils.postExceptionAndClearList(ppgObservers, throwable);
        RxUtils.postExceptionAndClearList(ppiObservers, throwable);
        RxUtils.postExceptionAndClearList(autoGainAFE4404Observers, throwable);
        RxUtils.postExceptionAndClearList(autoGainAFE4410Observers, throwable);
        RxUtils.postExceptionAndClearList(autoGainADPD4000Observers, throwable);
        RxUtils.postExceptionAndClearList(biozObservers, throwable);
        RxUtils.postExceptionAndClearList(afeOperationModeObservers, throwable);
        RxUtils.postExceptionAndClearList(sportIdObservers, throwable);
        RxUtils.postExceptionAndClearList(rdObservers, throwable);
        RxUtils.postExceptionAndClearList(gyroObservers, throwable);
        RxUtils.postExceptionAndClearList(magnetometerObservers, throwable);
    }
}