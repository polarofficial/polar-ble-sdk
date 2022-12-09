// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api

import com.polar.sdk.api.PolarBleApi.DeviceStreamingFeature
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHrData
import java.util.*

/**
 * Contains the callbacks of the API.
 */
abstract class PolarBleApiCallback : PolarBleApiCallbackProvider {
    /**
     * @param powered true = Bluetooth power on, false = Bluetooth power off
     */
    override fun blePowerStateChanged(powered: Boolean) {}

    /**
     * Device is now connected
     *
     * @param polarDeviceInfo Polar device information
     */
    override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {}

    /**
     * Connecting to device
     *
     * @param polarDeviceInfo Polar device information
     */
    override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {}

    /**
     * Device is now disconnected, no further action is needed from the application
     * if polar.com.sdk.api.PolarBleApi#disconnectFromPolarDevice is not called. Device will be automatically reconnected
     *
     * @param polarDeviceInfo Polar device information
     */
    override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {}

    /**
     * Polar device's streaming features ready. Application may start any stream now if desired.
     * Requires feature [PolarBleApi.FEATURE_POLAR_SENSOR_STREAMING] to be enabled for the
     * Polar SDK instance.
     *
     * @param identifier Polar device id
     * @param features   set of features available and ready
     */
    override fun streamingFeaturesReady(identifier: String, features: Set<DeviceStreamingFeature>) {}

    /**
     * Polar SDK Mode feature is available in the device. Application may now enter to SDK mode.
     * Requires feature PolarBleApi#FEATURE_POLAR_SENSOR_STREAMING
     *
     * @param identifier Polar device id
     */
    override fun sdkModeFeatureAvailable(identifier: String) {}

    /**
     * Polar device HR client is now ready and HR transmission is starting in a moment.
     *
     * @param identifier Polar device id or bt address
     */
    override fun hrFeatureReady(identifier: String) {}

    /**
     * DIS information received
     * requires feature PolarBleApi#FEATURE_DEVICE_INFO
     *
     * @param identifier Polar device id or bt address
     * @param uuid       uuid of dis value
     * @param value      dis value for uuid
     */
    override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {}

    /**
     * Battery level received
     * requires feature PolarBleApi#FEATURE_BATTERY_INFO
     *
     * @param identifier Polar device id or bt address
     * @param level      battery level (value between 0-100%)
     */
    override fun batteryLevelReceived(identifier: String, level: Int) {}

    /**
     * HR notification data received from device. Notice when using OH1
     * and PPI measurement is started hr received from this callback is 0.
     *
     * @param identifier Polar device id or bt address
     * @param data       @see polar.com.sdk.api.model.PolarHrData.java
     */
    override fun hrNotificationReceived(identifier: String, data: PolarHrData) {}

    /**
     * File transfer ready
     * requires feature PolarBleApi#FEATURE_POLAR_FILE_TRANSFER
     *
     * @param identifier Polar device id
     */
    override fun polarFtpFeatureReady(identifier: String) {}
}