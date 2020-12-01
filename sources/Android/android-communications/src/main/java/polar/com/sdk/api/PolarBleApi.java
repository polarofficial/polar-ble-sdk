// Copyright © 2019 Polar Electro Oy. All rights reserved.
package polar.com.sdk.api;

import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.annotation.Size;
import android.util.Pair;

import java.util.Calendar;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.annotations.NonNull;
import polar.com.sdk.api.errors.PolarInvalidArgument;
import polar.com.sdk.api.model.PolarAccelerometerData;
import polar.com.sdk.api.model.PolarBiozData;
import polar.com.sdk.api.model.PolarDeviceInfo;
import polar.com.sdk.api.model.PolarEcgData;
import polar.com.sdk.api.model.PolarExerciseData;
import polar.com.sdk.api.model.PolarExerciseEntry;
import polar.com.sdk.api.model.PolarHrBroadcastData;
import polar.com.sdk.api.model.PolarOhrPPGData;
import polar.com.sdk.api.model.PolarOhrPPIData;
import polar.com.sdk.api.model.PolarSensorSetting;

/**
 * Main class of the API.
 */
public abstract class PolarBleApi {

    /**
     * Logger interface declaration
     */
    public interface PolarBleApiLogger {
        /**
         * message from sdk logging
         * @param str formatted string message
         */
        void message(final String str);
    }

    /**
     * Recoding intervals for H10 recording start
     */
    public enum RecordingInterval {
        INTERVAL_1S(1), /*!< 1 second interval */
        INTERVAL_5S(5); /*!< 5 second interval */

        private int value;

        RecordingInterval(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * Sample types for H10 recording start
     */
    public enum SampleType {
        HR, /*!< HeartRate in BPM */
        RR; /*!< RR interval in milliseconds */
    }

    /**
     * hr feature to receive hr and rr data.
     */
    public static final int FEATURE_HR = 1;
    /**
     * dis feature to receive sw information.
     */
    public static final int FEATURE_DEVICE_INFO = 2;
    /**
     * bas feature to receive battery level info.
     */
    public static final int FEATURE_BATTERY_INFO = 4;
    /**
     * polar sensor streaming for ecg, acc, ppg, ppi, etc...
     */
    public static final int FEATURE_POLAR_SENSOR_STREAMING = 8;
    /**
     * polar file transfer feature to read exercises from device
     */
    public static final int FEATURE_POLAR_FILE_TRANSFER = 16;
    /**
     * all features mask
     */
    public static final int ALL_FEATURES = 0xff;

    protected int features;

    /**
     * Class constructor
     * @param features set of feature(s) or PolarBleApi#ALL_FEATURES
     */
    protected PolarBleApi(final int features) {
        this.features = features;
    }

    /**
     * set mtu to lower than default(232 is the default for polar devices, minimum for H10 is 70 and for OH1 is 140)
     * to minimize latency
     * @param mtu value between 64-512 to be set
     */
    public abstract void setMtu(@IntRange(from = 70, to = 512) int mtu);

    /**
     * Must be called when application is destroyed.
     */
    public abstract void shutDown();

    /**
     * removes all known devices which are not in use currently
     */
    public abstract void cleanup();

    /**
     *
     * @param enable false disable polar filter which means in all apis identifier can be bt address too
     */
    public abstract void setPolarFilter(boolean enable);

    /**
     * @param deviceId polar device id or bt address
     * @param feature feature to be requested
     * @return true if feature is ready for use, FEATURE_POLAR_SENSOR_STREAMING or FEATURE_POLAR_FILE_TRANSFER
     *         is supported by this api function
     */
    public abstract boolean isFeatureReady(@NonNull final String deviceId, final int feature);

    /**
     * enables scan filter while on background
     */
    public abstract void backgroundEntered();

    /**
     * disables scan filter while on foreground
     */
    public abstract void foregroundEntered();

    /**
     * @param callback @see polar.com.sdk.api.PolarBleApiCallbackProvider
     */
    public abstract void setApiCallback(@Nullable PolarBleApiCallbackProvider callback);

    /**
     * @param logger @see polar.com.sdk.api.PolarBleApi.PolarBleApiLogger
     */
    public abstract void setApiLogger(@Nullable PolarBleApiLogger logger);

    /**
     * enable or disable automatic reconnection feature
     * @param enable true = automatic reconnection is enabled, false = automatic reconnection is disabled
     */
    public abstract void setAutomaticReconnection(boolean enable);

    /**
     * set time to device affects on sensor data stream(s) timestamps
     * requires feature PolarBleApi#FEATURE_POLAR_FILE_TRANSFER
     * @param identifier polar device id or bt address
     * @param calendar time to set
     * @return Completable stream
     */
    public abstract Completable setLocalTime(@NonNull final String identifier, @NonNull Calendar calendar);

    /**
     * request available acc settings requires feature PolarBleApi#FEATURE_POLAR_SENSOR_STREAMING
     * @param identifier polar device id or bt address
     * @return Single stream
     */
    public abstract Single<PolarSensorSetting> requestAccSettings(@NonNull final String identifier);

    /**
     * request available ecg settings requires feature PolarBleApi#FEATURE_POLAR_SENSOR_STREAMING
     * @param identifier polar device id or bt address
     * @return Single stream
     */
    public abstract Single<PolarSensorSetting> requestEcgSettings(@NonNull final String identifier);

    /**
     * request available ppg settings requires feature PolarBleApi#FEATURE_POLAR_SENSOR_STREAMING
     * @param identifier polar device id or bt address
     * @return Single stream
     */
    public abstract Single<PolarSensorSetting> requestPpgSettings(@NonNull final String identifier);

    public abstract Single<PolarSensorSetting> requestBiozSettings(@NonNull final String identifier);

    /**
     * Start connecting to a nearby Polar device. PolarBleApiCallback#polarDeviceConnected callback is
     * invoked when connection to a nearby device is established.
     * @param rssiLimit RSSI (Received Signal Strength Indication) value is typically from -40 to -60 (dBm), depends on the used Bluetooth chipset and/or antenna tuning
     * @param service in hex string format like "180D" PolarInvalidArgument invoked if not in correct format
     * @param timeout min time to search nearby device default = 2s
     * @param unit time unit to be used
     * @param polarDeviceType like H10, OH1 etc... or null for any polar device
     * @return rx Completable, complete invoked when nearby device found, and connection attempt started.
     *         deviceConnecting callback invoked to inform connection attempt
     */
    public abstract Completable autoConnectToDevice(int rssiLimit, @Nullable String service, int timeout,@NonNull TimeUnit unit,@Nullable final String polarDeviceType);
    public abstract Completable autoConnectToDevice(int rssiLimit, @Nullable String service, final String polarDeviceType);

    /**
     * Request a connection to a Polar device. Invokes PolarBleApiCallback#polarDeviceConnected callback.
     * @param   identifier Polar device id found printed on the sensor/device (in format "12345678") or bt address (in format "00:11:22:33:44:55")
     * @throws PolarInvalidArgument if identifier is invalid formatted mac address or polar device id
     */
    public abstract void connectToDevice(@NonNull final String identifier) throws PolarInvalidArgument;

    /**
     * Request disconnecting from a Polar device. Invokes PolarBleApiCallback#polarDeviceDisconnected callback.
     * @param   identifier Polar device id found printed on the sensor/device or bt address
     * @throws PolarInvalidArgument if identifier is invalid formatted mac address or polar device id
     */
    public abstract void disconnectFromDevice(@NonNull final String identifier) throws PolarInvalidArgument;

    /**
     * request start recording only supported by H10, requires feature PolarBleApi#FEATURE_POLAR_FILE_TRANSFER
     * @param identifier polar device id or bt address
     * @param exerciseId unique id for exercise entry
     * @param interval recording interval to be used
     * @param type sample type to be used
     * @return Completable stream
     */
    public abstract Completable startRecording(@NonNull final String identifier,
                                               @NonNull @Size(min = 1, max = 64) final String exerciseId,
                                               @NonNull RecordingInterval interval,
                                               @NonNull SampleType type);

    /**
     * request stop recording only supported by H10, requires feature PolarBleApi#FEATURE_POLAR_FILE_TRANSFER
     * @param identifier polar device id or bt address
     * @return Completable stream
     */
    public abstract Completable stopRecording(@NonNull final String identifier);

    /**
     * request current recording status only supported by H10, requires feature PolarBleApi#FEATURE_POLAR_FILE_TRANSFER
     * @param identifier polar device id or bt address
     * @return Single stream Pair first recording status, second entryId if available
     */
    public abstract Single<Pair<Boolean,String>> requestRecordingStatus(@NonNull final String identifier);

    /**
     * List all exercises stored in the device OH1/H10 requires feature PolarBleApi#FEATURE_POLAR_FILE_TRANSFER
     * @param   identifier Polar device id found printed on the sensor/device or bt address
     * @return  Flowable stream of entrys @see polar.com.sdk.api.model.PolarExerciseEntry for details
     */
    public abstract Flowable<PolarExerciseEntry> listExercises(@NonNull final String identifier);

    /**
     * Fetch single exercise data requires feature PolarBleApi#FEATURE_POLAR_FILE_TRANSFER
     * @param   identifier Polar device id found printed on the sensor/device or bt address
     * @param   entry PolarExerciseEntry object
     * @return  Single stream @see polar.com.sdk.api.model.PolarExerciseData for details
     */
    public abstract Single<PolarExerciseData> fetchExercise(@NonNull final String identifier, @NonNull final PolarExerciseEntry entry);

    /**
     * Request to remove single exercise requires feature PolarBleApi#FEATURE_POLAR_FILE_TRANSFER
     * @param   identifier Polar device id found printed on the sensor/device or bt address
     * @param entry entry to be removed
     * @return Completable stream
     */
    public abstract Completable removeExercise(@NonNull final String identifier, @NonNull final PolarExerciseEntry entry);

    /**
     * Start searching for device(s)
     * @return  Flowable stream of polarDeviceInfo(s)
     *          Produces:
     *          <BR> - onNext for any new Polar device detected
     *          <BR> - onError if scan start fails
     *          <BR> - onComplete non produced unless stream is further configured
     */
    public abstract Flowable<PolarDeviceInfo> searchForDevice();

    /**
     * Starts listening to heart rate broadcast stream
     * @param   deviceIds set of Polar device ids. Null for a single Polar device
     * @return  Flowable stream of heart rate broadcasts.
     *          Produces:
     *          <BR> - onNext when new advertisement is detected based on deviceId list as filter
     *          <BR> - onError if scan start fails
     *          <BR> - onComplete non produced unless stream is further configured
     */
    public abstract Flowable<PolarHrBroadcastData> startListenForPolarHrBroadcasts(@Nullable final Set<String> deviceIds);

    /**
     * Start the ECG (Electrocardiography) stream. Stops the ECG stream if the connection is closed,
     * error occured during start or stream is disposed. Requires feature PolarBleApi#FEATURE_POLAR_SENSOR_STREAMING.
     * Query available settings before stream start PolarBleApi#requestEcgSettings
     * @param   identifier Polar device id found printed on the sensor/device or bt address
     * @param   sensorSetting settings to be used to start streaming
     * @return  Flowable stream of ECG data.
     *          Produces:
     *          <BR> - onNext @see PolarEcgData.java for details
     *          <BR> - onError @see errors package for possible errors invoked
     *          <BR> - onComplete non produced unless stream is further configured
     */
    public abstract Flowable<PolarEcgData> startEcgStreaming(@NonNull final String identifier,
                                                             @NonNull PolarSensorSetting sensorSetting);

    /**
     * Start ACC (Accelerometer) stream. Stops ACC stream if the connection is closed,
     * error occured during start or stream is disposed. Requires feature PolarBleApi#FEATURE_POLAR_SENSOR_STREAMING.
     * Query available settings before stream start PolarBleApi#requestAccSettings
     * @param   identifier Polar device id found printed on the sensor/device or bt address
     * @param   sensorSetting settings to be used to start streaming
     * @return  Flowable stream of Accelerometer data.
     *          Produces:
     *          <BR> - onNext @see PolarAccelerometerData for details
     *          <BR> - onError @see errors package for possible errors invoked
     *          <BR> - onComplete non produced unless stream is further configured
     */
    public abstract Flowable<PolarAccelerometerData> startAccStreaming(@NonNull final String identifier,
                                                                       @NonNull PolarSensorSetting sensorSetting);

    /**
     * Start OHR (Optical heart rate) PPG (Photoplethysmography) stream. Stops PPG stream if the connection is closed,
     * error occured during start or stream is disposed. Requires feature PolarBleApi#FEATURE_POLAR_SENSOR_STREAMING.
     * Query available settings before stream start PolarBleApi#requestPpgSettings
     * @param   identifier Polar device id found printed on the sensor/device or bt address
     * @param   sensorSetting settings to be used to start streaming
     * @return  Flowable stream of OHR PPG data.
     *          Produces:
     *          <BR> - onNext @see PolarOhrPPGData for details
     *          <BR> - onError @see errors package for possible errors invoked
     *          <BR> - onComplete non produced unless the stream is further configured
     */
    public abstract Flowable<PolarOhrPPGData> startOhrPPGStreaming(@NonNull final String identifier,
                                                                   @NonNull PolarSensorSetting sensorSetting);

    public abstract Flowable<PolarBiozData> startBiozStreaming(@NonNull final String identifier,
                                                               @NonNull PolarSensorSetting sensorSetting);

    /**
     * Start OHR (Optical heart rate) PPI (Pulse to Pulse interval) stream. Notice that there is a delay before PPI data stream starts
     * Requires feature PolarBleApi#FEATURE_POLAR_SENSOR_STREAMING. When using OH1 timestamp produced by PPI stream is 0.
     * @param   identifier Polar device id found printed on the sensor/device or bt address
     * @return  Flowable stream of OHR PPI data.
     *          Produces:
     *          <BR> - onNext @see PolarOhrPPIData for details
     *          <BR> - onError @see errors package for possible errors invoked
     *          <BR> - onComplete non produced unless the stream is further configured
     */
    public abstract Flowable<PolarOhrPPIData> startOhrPPIStreaming(@NonNull final String identifier);
}
