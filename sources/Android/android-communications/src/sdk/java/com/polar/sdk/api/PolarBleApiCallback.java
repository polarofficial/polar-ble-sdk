// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api;

import androidx.annotation.NonNull;

import com.polar.sdk.api.model.PolarDeviceInfo;
import com.polar.sdk.api.model.PolarHrData;

import java.util.Set;
import java.util.UUID;

/**
 * Contains the callbacks of the API.
 */
public abstract class PolarBleApiCallback implements PolarBleApiCallbackProvider {

    /**
     * @param powered true = Bluetooth power on, false = Bluetooth power off
     */
    @Override
    public void blePowerStateChanged(final boolean powered) {
    }

    /**
     * Device is now connected
     *
     * @param polarDeviceInfo Polar device information
     */
    @Override
    public void deviceConnected(@NonNull final PolarDeviceInfo polarDeviceInfo) {
    }

    /**
     * Connecting to device
     *
     * @param polarDeviceInfo Polar device information
     */
    @Override
    public void deviceConnecting(@NonNull final PolarDeviceInfo polarDeviceInfo) {
    }

    /**
     * Device is now disconnected, no further action is needed from the application
     * if polar.com.sdk.api.PolarBleApi#disconnectFromPolarDevice is not called. Device will be automatically reconnected
     *
     * @param polarDeviceInfo Polar device information
     */
    @Override
    public void deviceDisconnected(@NonNull final PolarDeviceInfo polarDeviceInfo) {
    }

    /**
     * Polar device's streaming features ready. Application may start any stream now if desired.
     * Requires feature {@link PolarBleApi#FEATURE_POLAR_SENSOR_STREAMING} to be enabled for the
     * Polar SDK instance.
     *
     * @param identifier Polar device id
     * @param features   set of features available and ready
     */
    @Override
    public void streamingFeaturesReady(@NonNull final String identifier,
                                       @NonNull final Set<PolarBleApi.DeviceStreamingFeature> features) {
    }

    /**
     * Polar SDK Mode feature is available in the device. Application may now enter to SDK mode.
     * Requires feature PolarBleApi#FEATURE_POLAR_SENSOR_STREAMING
     *
     * @param identifier Polar device id
     */
    @Override
    public void sdkModeFeatureAvailable(@NonNull final String identifier) {
    }

    /**
     * Polar device HR client is now ready and HR transmission is starting in a moment.
     *
     * @param identifier Polar device id or bt address
     */
    @Override
    public void hrFeatureReady(@NonNull final String identifier) {
    }

    /**
     * DIS information received
     * requires feature PolarBleApi#FEATURE_DEVICE_INFO
     *
     * @param identifier Polar device id or bt address
     * @param uuid       uuid of dis value
     * @param value      dis value for uuid
     */
    @Override
    public void disInformationReceived(@NonNull final String identifier, @NonNull UUID uuid, @NonNull final String value) {
    }

    /**
     * Battery level received
     * requires feature PolarBleApi#FEATURE_BATTERY_INFO
     *
     * @param identifier Polar device id or bt address
     * @param level      battery level (value between 0-100%)
     */
    @Override
    public void batteryLevelReceived(@NonNull final String identifier, final int level) {
    }

    /**
     * HR notification data received from device. Notice when using OH1
     * and PPI measurement is started hr received from this callback is 0.
     *
     * @param identifier Polar device id or bt address
     * @param data       @see polar.com.sdk.api.model.PolarHrData.java
     */
    @Override
    public void hrNotificationReceived(@NonNull final String identifier, @NonNull final PolarHrData data) {
    }

    /**
     * File transfer ready
     * requires feature PolarBleApi#FEATURE_POLAR_FILE_TRANSFER
     *
     * @param identifier Polar device id
     */
    @Override
    public void polarFtpFeatureReady(@NonNull final String identifier) {
    }
}
