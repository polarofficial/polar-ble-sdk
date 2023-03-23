// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api

import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHrData
import java.util.*

/**
 * Contains the callbacks of the API.
 */
abstract class PolarBleApiCallback : PolarBleApiCallbackProvider {
    /**
     * Bluetooth power state of the device where this SDK is running
     *
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
     * Device is now disconnected
     *
     * @param polarDeviceInfo Polar device information
     */
    override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {}

    /**
     * Called when the feature in connected device is available and it is ready. Called only for the features which are specified by [PolarBleApi] instantiation.
     *
     * @param identifier Polar device id
     * @param feature   feature is ready
     */
    override fun bleSdkFeatureReady(identifier: String, feature: PolarBleApi.PolarBleSdkFeature) {}

    /**
     * Polar device's streaming features ready. Application may start any stream now if desired.
     * Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING] to be enabled for the
     * Polar SDK instance.
     *
     * @param identifier Polar device id
     * @param features   set of features available and ready
     */
    @Deprecated(
        "The functionality has changed. Please use the bleSdkFeatureReady to know if onlineStreaming is available and" +
                " the getAvailableOnlineStreamDataTypes function know which data types are supported"
    )
    override fun streamingFeaturesReady(identifier: String, features: Set<PolarDeviceDataType>) {
    }

    /**
     * Polar SDK Mode feature is available in the device. Application may now enter to SDK mode.
     * Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE]
     *
     * @param identifier Polar device id
     */
    @Deprecated("Information whether SDK Mode is available is provided by bleSdkFeatureReady")
    override fun sdkModeFeatureAvailable(identifier: String) {
    }

    /**
     * Polar device HR client is now ready and HR transmission is starting in a moment.
     *
     * @param identifier Polar device id or bt address
     */
    @Deprecated("Information whether HR feature is available is provided by bleSdkFeatureReady")
    override fun hrFeatureReady(identifier: String) {
    }

    /**
     * DIS information received. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO]
     *
     * @param identifier Polar device id or bt address
     * @param uuid       uuid of dis value
     * @param value      dis value for uuid
     */
    override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {}

    /**
     * Battery level received. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO]
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
    @Deprecated("Please use the startHrStreaming API to get the heart rate data ")
    override fun hrNotificationReceived(identifier: String, data: PolarHrData.PolarHrSample) {
    }

    /**
     * File transfer ready
     * requires feature PolarBleApi#FEATURE_POLAR_FILE_TRANSFER
     *
     * @param identifier Polar device id
     */
    @Deprecated("Not supported anymore, won't be ever called. Use the bleSdkFeatureReady")
    override fun polarFtpFeatureReady(identifier: String) {
    }
}