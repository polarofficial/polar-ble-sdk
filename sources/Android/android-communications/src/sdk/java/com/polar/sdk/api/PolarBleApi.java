// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;
import androidx.core.util.Pair;

import com.polar.sdk.api.errors.PolarInvalidArgument;
import com.polar.sdk.api.model.PolarAccelerometerData;
import com.polar.sdk.api.model.PolarDeviceInfo;
import com.polar.sdk.api.model.PolarEcgData;
import com.polar.sdk.api.model.PolarExerciseData;
import com.polar.sdk.api.model.PolarExerciseEntry;
import com.polar.sdk.api.model.PolarGyroData;
import com.polar.sdk.api.model.PolarHrBroadcastData;
import com.polar.sdk.api.model.PolarMagnetometerData;
import com.polar.sdk.api.model.PolarOhrData;
import com.polar.sdk.api.model.PolarOhrPPIData;
import com.polar.sdk.api.model.PolarSensorSetting;

import java.util.Calendar;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

/**
 * Polar BLE API.
 */
public abstract class PolarBleApi {

    /**
     * Logger interface for logging events from SDK. Shall be used only for tracing and debugging purposes.
     */
    public interface PolarBleApiLogger {
        /**
         * message from sdk logging
         *
         * @param str formatted string message
         */
        void message(final @NonNull String str);
    }

    /**
     * Device stream features in Polar devices. The device streaming features requires the
     * {@link #FEATURE_POLAR_SENSOR_STREAMING}
     *
     * @see PolarBleApiCallback#streamingFeaturesReady(String, Set)
     */
    public enum DeviceStreamingFeature {
        ECG,
        ACC,
        PPG,
        PPI,
        GYRO,
        MAGNETOMETER
    }

    /**
     * Recoding intervals for H10 recording start
     */
    public enum RecordingInterval {
        INTERVAL_1S(1), /*!< 1 second interval */
        INTERVAL_5S(5); /*!< 5 second interval */

        private final int value;

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
        RR /*!< RR interval in milliseconds */
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
     * polar sensor streaming feature for ecg, acc, ppg, ppi, etc...
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
     *
     * @param features bitmask of feature(s) or {@link #ALL_FEATURES}
     */
    protected PolarBleApi(final int features) {
        this.features = features;
    }

    /**
     * set mtu to lower than default(232 is the default for polar devices, minimum for H10 is 70 and for OH1 is 140)
     * to minimize latency
     *
     * @param mtu value between 70-512 to be set
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
     * @param enable false disable polar filter which means in all apis identifier can be bt address too
     */
    public abstract void setPolarFilter(boolean enable);

    /**
     * Check if the feature is ready. Only the check for the {@link #FEATURE_POLAR_SENSOR_STREAMING}
     * and {@link #FEATURE_POLAR_FILE_TRANSFER} is supported by this api function
     *
     * @param deviceId polar device id or bt address
     * @param feature  feature to be requested
     * @return true if feature is ready for use,
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
     * Sets the API callback
     *
     * @param callback instance of {@link PolarBleApiCallbackProvider}
     */
    public abstract void setApiCallback(@NonNull PolarBleApiCallbackProvider callback);

    /**
     * Sets the API logger
     *
     * @param logger instance of {@link PolarBleApiLogger}
     */
    public abstract void setApiLogger(@NonNull PolarBleApiLogger logger);

    /**
     * Enable or disable automatic reconnection feature
     *
     * @param enable true = automatic reconnection is enabled, false = automatic reconnection is disabled
     */
    public abstract void setAutomaticReconnection(boolean enable);

    /**
     * Set time to device affects on sensor data stream(s) timestamps
     * requires feature {@link #FEATURE_POLAR_FILE_TRANSFER}
     *
     * @param identifier polar device id or bt address
     * @param calendar   time to set
     * @return Completable stream
     */
    @NonNull
    public abstract Completable setLocalTime(@NonNull final String identifier, @NonNull Calendar calendar);

    /**
     * Request the stream settings available in current operation mode. This request shall be used before the stream is started
     * to decide currently available. The available settings depend on the state of the device. For
     * example, if any stream(s) or optical heart rate measurement is already enabled, then
     * the device may limit the offer of possible settings for other stream feature. Requires feature
     * {@link #FEATURE_POLAR_SENSOR_STREAMING}
     *
     * @param identifier polar device id or bt address
     * @param feature    the stream feature of interest
     * @return Single stream
     */
    @NonNull
    public abstract Single<PolarSensorSetting> requestStreamSettings(@NonNull final String identifier,
                                                                     @NonNull DeviceStreamingFeature feature);

    /**
     * Request full steam settings capabilities. The request returns the all capabilities of the
     * requested streaming feature not limited by the current operation mode. Requires feature
     * {@link #FEATURE_POLAR_SENSOR_STREAMING}. This request is supported only by Polar Verity Sense (starting from firmware 1.1.5)
     *
     * @param identifier polar device id or bt address
     * @param feature    the stream feature of interest
     * @return Single stream
     */
    @NonNull
    public abstract Single<PolarSensorSetting> requestFullStreamSettings(@NonNull final String identifier,
                                                                         @NonNull DeviceStreamingFeature feature);

    /**
     * Start connecting to a nearby Polar device. {@link PolarBleApiCallback#deviceConnected(PolarDeviceInfo)} callback is
     * invoked when connection to a nearby device is established.
     *
     * @param rssiLimit       RSSI (Received Signal Strength Indication) value is typically from -40 to -60 (dBm), depends on the used Bluetooth chipset and/or antenna tuning
     * @param service         in hex string format like "180D" PolarInvalidArgument invoked if not in correct format
     * @param timeout         min time to search nearby device default = 2s
     * @param unit            time unit to be used
     * @param polarDeviceType like H10, OH1 etc... or null for any polar device
     * @return rx Completable, complete invoked when nearby device found, and connection attempt started.
     * deviceConnecting callback invoked to inform connection attempt
     */
    @NonNull
    public abstract Completable autoConnectToDevice(int rssiLimit, @Nullable String service, int timeout, @NonNull TimeUnit unit, @Nullable final String polarDeviceType);

    @NonNull
    public abstract Completable autoConnectToDevice(int rssiLimit, @Nullable String service, @Nullable final String polarDeviceType);

    /**
     * Request a connection to a Polar device. Invokes {@link PolarBleApiCallback#deviceConnected(PolarDeviceInfo)} callback.
     *
     * @param identifier Polar device id found printed on the sensor/device (in format "12345678")
     *                   or bt address (in format "00:11:22:33:44:55")
     * @throws PolarInvalidArgument if identifier is invalid formatted mac address or polar device id
     */
    public abstract void connectToDevice(@NonNull final String identifier) throws PolarInvalidArgument;

    /**
     * Request disconnecting from a Polar device. Invokes {@link PolarBleApiCallback#deviceDisconnected(PolarDeviceInfo)} callback.
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @throws PolarInvalidArgument if identifier is invalid formatted mac address or polar device id
     */
    public abstract void disconnectFromDevice(@NonNull final String identifier) throws PolarInvalidArgument;

    /**
     * Request start recording. Supported only by Polar H10. Requires feature
     * {@link #FEATURE_POLAR_FILE_TRANSFER}
     *
     * @param identifier polar device id or bt address
     * @param exerciseId unique id for exercise entry
     * @param interval   recording interval to be used, parameter has no effect if the `type` parameter is SampleType.RR
     * @param type       sample type to be used
     * @return Completable stream
     */
    @NonNull
    public abstract Completable startRecording(@NonNull final String identifier,
                                               @NonNull @Size(min = 1, max = 64) final String exerciseId,
                                               @Nullable RecordingInterval interval,
                                               @NonNull SampleType type);

    /**
     * Request stop recording. Supported only by Polar H10. Requires feature
     * {@link #FEATURE_POLAR_FILE_TRANSFER}
     *
     * @param identifier polar device id or bt address
     * @return Completable stream
     */
    @NonNull
    public abstract Completable stopRecording(@NonNull final String identifier);

    /**
     * Request current recording status. Supported only by Polar H10. Requires feature
     * {@link #FEATURE_POLAR_FILE_TRANSFER}
     *
     * @param identifier polar device id or bt address
     * @return Single stream Pair first recording status, second entryId if available
     */
    @NonNull
    public abstract Single<Pair<Boolean, String>> requestRecordingStatus(@NonNull final String identifier);

    /**
     * List exercises stored in the device Polar H10 device. Requires feature
     * {@link #FEATURE_POLAR_FILE_TRANSFER}. This API is working for Polar OH1 and
     * Polar Verity Sense devices too, however in those devices recording of exercise requires
     * that sensor is registered to Polar Flow account.
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @return Flowable stream of {@link PolarExerciseEntry} entries
     */
    @NonNull
    public abstract Flowable<PolarExerciseEntry> listExercises(@NonNull final String identifier);

    /**
     * Api for fetching a single exercise from Polar H10 device. Requires feature
     * {@link #FEATURE_POLAR_FILE_TRANSFER}. This API is working for Polar OH1 and
     * Polar Verity Sense devices too, however in those devices recording of exercise requires
     * that sensor is registered to Polar Flow account.
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @param entry      {@link PolarExerciseEntry} object
     * @return Single stream of {@link PolarExerciseData}
     */
    @NonNull
    public abstract Single<PolarExerciseData> fetchExercise(@NonNull final String identifier, @NonNull final PolarExerciseEntry entry);

    /**
     * Api for removing single exercise from Polar H10 device. Requires feature
     * {@link #FEATURE_POLAR_FILE_TRANSFER}. This API is working for Polar OH1 and
     * Polar Verity Sense devices too, however in those devices recording of exercise requires
     * that sensor is registered to Polar Flow account.
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @param entry      entry to be removed
     * @return Completable stream
     */
    @NonNull
    public abstract Completable removeExercise(@NonNull final String identifier, @NonNull final PolarExerciseEntry entry);

    /**
     * Start searching for device(s)
     *
     * @return Flowable stream of {@link PolarDeviceInfo}
     * Produces:
     * <BR> - onNext for any new Polar device detected
     * <BR> - onError if scan start fails
     * <BR> - onComplete non produced unless stream is further configured
     */
    @NonNull
    public abstract Flowable<PolarDeviceInfo> searchForDevice();

    /**
     * Start listening to heart rate broadcasts from one or more Polar devices
     *
     * @param deviceIds set of Polar device ids to filter or null for a any Polar device
     * @return Flowable stream of {@link PolarHrBroadcastData}
     * Produces:
     * <BR> - onNext when new advertisement is detected based on deviceId list as filter
     * <BR> - onError if scan start fails
     * <BR> - onComplete non produced unless stream is further configured
     */
    @NonNull
    public abstract Flowable<PolarHrBroadcastData> startListenForPolarHrBroadcasts(@Nullable final Set<String> deviceIds);

    /**
     * Start the ECG (Electrocardiography) stream. ECG stream is stopped if the connection is closed,
     * error occurs or stream is disposed. Requires feature {@link #FEATURE_POLAR_SENSOR_STREAMING}.
     * Before starting the stream it is recommended to query the available settings using {@link #requestStreamSettings}
     *
     * @param identifier    Polar device id found printed on the sensor/device or bt address
     * @param sensorSetting settings to be used to start streaming
     * @return Flowable stream of {@link PolarEcgData}
     * Produces:
     * <BR> - onNext {@link PolarEcgData}
     * <BR> - onError error for possible errors invoked
     * <BR> - onComplete non produced unless stream is further configured
     */
    @NonNull
    public abstract Flowable<PolarEcgData> startEcgStreaming(@NonNull final String identifier,
                                                             @NonNull PolarSensorSetting sensorSetting);

    /**
     * Start ACC (Accelerometer) stream. ACC stream is stopped if the connection is closed, error
     * occurs or stream is disposed. Requires feature {@link #FEATURE_POLAR_SENSOR_STREAMING}.
     * Before starting the stream it is recommended to query the available settings using {@link #requestStreamSettings}
     *
     * @param identifier    Polar device id found printed on the sensor/device or bt address
     * @param sensorSetting settings to be used to start streaming
     * @return Flowable stream of {@link PolarAccelerometerData}
     * Produces:
     * <BR> - onNext {@link PolarAccelerometerData}
     * <BR> - onError error for possible errors invoked
     * <BR> - onComplete non produced unless stream is further configured
     */
    @NonNull
    public abstract Flowable<PolarAccelerometerData> startAccStreaming(@NonNull final String identifier,
                                                                       @NonNull PolarSensorSetting sensorSetting);

    /**
     * Start OHR (Optical heart rate) PPG (Photoplethysmography) stream. PPG stream is stopped if
     * the connection is closed, error occurs or stream is disposed. Requires feature
     * {@link #FEATURE_POLAR_SENSOR_STREAMING}. Before starting the stream it is recommended to
     * query the available settings using {@link #requestStreamSettings}
     *
     * @param identifier    Polar device id found printed on the sensor/device or bt address
     * @param sensorSetting settings to be used to start streaming
     * @return Flowable stream of OHR PPG data.
     * Produces:
     * <BR> - onNext {@link PolarOhrData}
     * <BR> - onError error for possible errors invoked
     * <BR> - onComplete non produced unless the stream is further configured
     */
    @NonNull
    public abstract Flowable<PolarOhrData> startOhrStreaming(@NonNull final String identifier,
                                                             @NonNull PolarSensorSetting sensorSetting);

    /**
     * Start OHR (Optical heart rate) PPI (Pulse to Pulse interval) stream. PPI stream is stopped if
     * the connection is closed, error occurs or stream is disposed. Notice that there is a
     * delay before PPI data stream starts. Requires feature {@link #FEATURE_POLAR_SENSOR_STREAMING}.
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @return Flowable stream of OHR PPI data.
     * Produces:
     * <BR> - onNext {@link PolarOhrPPIData}
     * <BR> - onError error for possible errors invoked
     * <BR> - onComplete non produced unless the stream is further configured
     */
    @NonNull
    public abstract Flowable<PolarOhrPPIData> startOhrPPIStreaming(@NonNull final String identifier);

    /**
     * Start magnetometer stream. Magnetometer stream is stopped if the connection is closed, error
     * occurs or stream is disposed. Requires feature {@link #FEATURE_POLAR_SENSOR_STREAMING}.
     * Before starting the stream it is recommended to query the available settings using {@link #requestStreamSettings}
     *
     * @param identifier    Polar device id found printed on the sensor/device or bt address
     * @param sensorSetting settings to be used to start streaming
     * @return Flowable stream of magnetometer data.
     * Produces:
     * <BR> - onNext {@link PolarMagnetometerData}
     * <BR> - onError error for possible errors invoked
     * <BR> - onComplete non produced unless the stream is further configured
     */
    @NonNull
    public abstract Flowable<PolarMagnetometerData> startMagnetometerStreaming(@NonNull final String identifier,
                                                                               @NonNull PolarSensorSetting sensorSetting);

    /**
     * Start Gyro stream. Gyro stream is stopped if the connection is closed, error occurs during
     * start or stream is disposed. Requires feature {@link #FEATURE_POLAR_SENSOR_STREAMING}.
     * Before starting the stream it is recommended to query the available settings using {@link #requestStreamSettings}
     *
     * @param identifier    Polar device id found printed on the sensor/device or bt address
     * @param sensorSetting settings to be used to start streaming
     * @return Flowable stream of gyroscope data.
     * Produces:
     * <BR> - onNext {@link PolarGyroData}
     * <BR> - onError error for possible errors invoked
     * <BR> - onComplete non produced unless the stream is further configured
     */
    @NonNull
    public abstract Flowable<PolarGyroData> startGyroStreaming(@NonNull final String identifier,
                                                               @NonNull PolarSensorSetting sensorSetting);

    /**
     * Enables SDK mode. In SDK mode the wider range of capabilities is available for the stream
     * than in normal operation mode. SDK mode is only supported by Polar Verity Sense (starting from firmware 1.1.5).
     * Requires feature {@link #FEATURE_POLAR_SENSOR_STREAMING}.
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @return Completable stream produces:
     * success if SDK mode is enabled or device is already in SDK mode
     * error if SDK mode enable failed
     */
    @NonNull
    public abstract Completable enableSDKMode(@NonNull final String identifier);

    /**
     * Disables SDK mode. SDK mode is only supported by Polar Verity Sense (starting from firmware 1.1.5).
     * Requires feature {@link #FEATURE_POLAR_SENSOR_STREAMING}.
     *
     * @param identifier Polar device id found printed on the sensor/device or bt address
     * @return Completable stream produces:
     * success if SDK mode is disabled or SDK mode was already disabled
     * error if SDK mode disable failed
     */
    @NonNull
    public abstract Completable disableSDKMode(@NonNull final String identifier);
}
