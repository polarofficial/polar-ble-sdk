// Copyright © 2019 Polar Electro Oy. All rights reserved.
package polar.com.sdk.api;

import androidx.annotation.NonNull;

import java.util.UUID;

import polar.com.sdk.api.model.PolarDeviceInfo;
import polar.com.sdk.api.model.PolarHrData;

/**
 * Contains the callbacks of the API.
 */
public abstract class PolarBleApiCallback implements PolarBleApiCallbackProvider {

    /**
     * @param   powered true = Bluetooth power on, false = Bluetooth power off
     */
    public void blePowerStateChanged(final boolean powered) {}

    /**
     * Device is now connected
     * @param polarDeviceInfo Polar device information
     */
    public void deviceConnected(@NonNull final PolarDeviceInfo polarDeviceInfo) {}

    /**
     * Connecting to device
     * @param polarDeviceInfo Polar device information
     */
    public void deviceConnecting(@NonNull final PolarDeviceInfo polarDeviceInfo) {}

    /**
     * Device is now disconnected, no further action is needed from the application
     * if polar.com.sdk.api.PolarBleApi#disconnectFromPolarDevice is not called. Device will be automatically reconnected
     * @param polarDeviceInfo Polar device information
     */
    public void deviceDisconnected(@NonNull final PolarDeviceInfo polarDeviceInfo) {}

    /**
     * Polar device's ECG feature is ready. Application may start ECG stream now if desired.
     * requires feature PolarBleApi#FEATURE_POLAR_SENSOR_STREAMING
     * @param   identifier Polar device id
     */
    public void ecgFeatureReady(@NonNull final String identifier) {}

    /**
     * Polar device's ACC feature is ready. Application may start ACC stream now if desired.
     * requires feature PolarBleApi#FEATURE_POLAR_SENSOR_STREAMING
     * @param   identifier Polar device id
     */
    public void accelerometerFeatureReady(@NonNull final String identifier) {}

    /**
     * Polar device's PPG feature is ready. Application may start PPG stream now if desired.
     * requires feature PolarBleApi#FEATURE_POLAR_SENSOR_STREAMING
     * @param   identifier Polar device id
     */
    public void ppgFeatureReady(@NonNull final String identifier) {}

    /**
     * Polar device's PPI feature is ready. Application may start PPI stream now if desired.
     * requires feature PolarBleApi#FEATURE_POLAR_SENSOR_STREAMING
     * @param   identifier Polar device id
     */
    public void ppiFeatureReady(@NonNull final String identifier) {}

    /**
     * Polar device's bioz feature is ready.
     * @param   identifier Polar device id
     */
    public void biozFeatureReady(@NonNull final String identifier) {}

    /**
     * Polar device HR client is now ready and HR transmission is starting in a moment.
     * @param   identifier Polar device id or bt address
     */
    public void hrFeatureReady(@NonNull final String identifier) {}

    /**
     * DIS information received
     * requires feature PolarBleApi#FEATURE_DEVICE_INFO
     * @param   identifier Polar device id or bt address
     * @param   uuid uuid of dis value
     * @param   value dis value for uuid
     */
    public void disInformationReceived(@NonNull final String identifier, @NonNull UUID uuid, @NonNull final String value) {}

    /**
     * Battery level received
     * requires feature PolarBleApi#FEATURE_BATTERY_INFO
     * @param   identifier Polar device id or bt address
     * @param   level battery level (value between 0-100%)
     */
    public void batteryLevelReceived(@NonNull final String identifier, final int level) {}

    /**
     * HR notification data received from device. Notice when using OH1
     * and PPI measurement is started hr received from this callback is 0.
     * @param   identifier Polar device id or bt address
     * @param   data @see polar.com.sdk.api.model.PolarHrData.java
     */
    public void hrNotificationReceived(@NonNull final String identifier,@NonNull final PolarHrData data) {}

    /**
     * File transfer ready
     * requires feature PolarBleApi#FEATURE_POLAR_FILE_TRANSFER
     * @param   identifier Polar device id
     */
    public void polarFtpFeatureReady(@NonNull final String identifier) {}
}
