// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api

import com.polar.androidcommunications.api.ble.model.DisInfo
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHealthThermometerData
import com.polar.sdk.api.model.PolarHrData
import java.util.*

/**
 * Contains the callbacks of the API.
 */
interface PolarBleApiCallbackProvider {
    /**
     * Bluetooth power state of the device where this SDK is running
     *
     * @param powered true = Bluetooth power on, false = Bluetooth power off
     */
    fun blePowerStateChanged(powered: Boolean)

    /**
     * Device is now connected
     *
     * @param polarDeviceInfo Polar device information
     */
    fun deviceConnected(polarDeviceInfo: PolarDeviceInfo)

    /**
     * Connecting to device
     *
     * @param polarDeviceInfo Polar device information
     */
    fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo)

    /**
     * Device is now disconnected
     *
     * @param polarDeviceInfo Polar device information
     */
    fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo)

    /**
     * The feature is available in this device and it is ready. Called only for the features which are specified in [PolarBleApi] construction.
     *
     * @param identifier Polar device id
     * @param feature   feature is ready
     */
    fun bleSdkFeatureReady(identifier: String, feature: PolarBleApi.PolarBleSdkFeature)

    /**
     * DIS information received. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO]
     *
     * @param identifier Polar device id or bt address
     * @param uuid       uuid of dis value
     * @param value      dis value for uuid
     */
    fun disInformationReceived(identifier: String, uuid: UUID, value: String)

    /**
     * DIS information received. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO]
     *
     * @param identifier Polar device id or bt address
     * @param disInfo    [DisInfo] key-value pair
     */
    fun disInformationReceived(identifier: String, disInfo: DisInfo)

    /**
     * Battery level received. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO]
     *
     * @param identifier Polar device id or bt address
     * @param level      battery level (value between 0-100%)
     */
    fun batteryLevelReceived(identifier: String, level: Int)

    /**
     * HR notification data received from device. Notice when using OH1
     * and PPI measurement is started hr received from this callback is 0.
     *
     * @param identifier Polar device id or bt address
     * @param data       @see polar.com.sdk.api.model.PolarHrData.java
     */
    fun hrNotificationReceived(identifier: String, data: PolarHrData.PolarHrSample)

    /**
     * Health Thermometer Service. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_HTS]
     *
     * @param identifier Polar device id or bt address
     * @param data       @see polar.com.sdk.api.model.PolarHealthThermometerData.kt
     */
    fun htsNotificationReceived(identifier: String, data: PolarHealthThermometerData)
}