package com.polar.sdk.api

import kotlinx.coroutines.flow.Flow
/* Typical usage:
*
* 1. Get available telemetry types for the device using `getAvailableTelemetryTypes` API.
* 2. Read connection parameters from the device
* val telemetryConfig = polarDeviceStreamingRepository.getDeviceTelemetryConfiguration(telemetryType, deviceId)
*
* 2. Start streaming and listen for telemetry data using `startTelemetryStreaming` API.
* return api.startTelemetry(telemetryType, deviceId)
*    sendTelemetryData(telemetry, configuration: configuration) // Not SDK API
* }
* ```
*/

//Telemetry types available in the Polar SDK.
enum class PolarDeviceTelemetryType(val displayName: String) {
    memfault_mds("Memfault MDS");

    companion object {
        fun getByString(displayName: String): PolarDeviceTelemetryType? {
            return try {
                PolarDeviceTelemetryType.values().first { it.displayName == displayName }
            } catch (e: NoSuchElementException) {
                null
            }
        }
    }
}

// NOTE: this is experimental code intended for Polar internal use only. Polar will not support 3rd party users with this API.
data class DeviceTelemetrySupport(val availableTelemetryTypes: List<PolarDeviceTelemetryType>,
                                  val deviceId: String){}

// NOTE: this is experimental code intended for Polar internal use only. Polar will not support 3rd party users with this API.
data class DeviceTelemetryEvent(val type: PolarDeviceTelemetryType, val payload: ByteArray) {}

// NOTE: this is experimental code intended for Polar internal use only. Polar will not support 3rd party users with this API.
data class DeviceTelemetryConfiguration(
    val deviceIdentifier: String?,
    // Data URI for uploading telemetry data
    val dataUri: String?,
    // Authorization token for uploading telemetry data
    val authorization: String?,
    // Supported features bitmask (type-specific meaning)
    val supportedFeatures: Int?
){}

/**
 * Polar Telemetry API for streaming telemetry data from devices.
 * Requires feature FEATURE_TELEMETRY
 */
interface PolarBleTelemetryApi {

    /* Requires SDK feature(s): `PolarBleSdkFeature.feature_telemetry_data_streaming`
    * NOTE: this is an experimental API intended for Polar internal use only. Polar will not support 3rd party users with this API.
    * @param telemetryType The type of telemetry data to stream.
    * Reads the telemetry configuration from device or from BLE service.
    * @Param identifier: Polar device ID or BT address.
    * @Param telemetryType: Telemetry type to stream. If the desired telemetry type is not supported by the device, the API will throw an error.
    * @Returns `DeviceTelemetryConfiguration` containing items that describe the incoming data configuration or null if configuration is not available.
    * @Throws: Throwable if the operation fails
     *
    */
    @OptIn
    fun getDeviceTelemetryConfiguration(identifier: String, telemetryType: PolarDeviceTelemetryType): DeviceTelemetryConfiguration?

    /**
     * NOTE: this is an experimental API intended for Polar internal use only. Polar will not support 3rd party users with this API.
    * Start telemetry data stream from the device. Stream emits`DeviceTelemetryEvent` as soon as telemetry data arrives from device.
     *
    * @param identifier: Polar device ID or BT address.
    * @return Flow of that emits one `DeviceTelemetryEvent` per received telemetry data item from device.
     * @Throws: Throwable if the operation fails, for example if the device does not support the requested telemetry type or if there was an error while starting the telemetry stream.
    */
    @OptIn
    suspend fun startTelemetry(identifier: String, telemetryType: PolarDeviceTelemetryType):  Flow<DeviceTelemetryEvent>

    /**
     * NOTE: this is an experimental API intended for Polar internal use only. Polar will not support 3rd party users with this API.
    * Stop receiving telemetry data from the device.
    *
    * @param identifier: Polar device ID or BT address.
    * @return true if the device supports telemetry data streaming and the streaming was stopped successfully, false otherwise.
     * @Throws: Throwable if the operation fails, for example if the device does not support the requested telemetry type or if there was an error while stopping the telemetry stream.
    */
    @OptIn
    suspend fun stopTelemetry(identifier: String, telemetryType: PolarDeviceTelemetryType): Boolean

/**
 * NOTE: this is an experimental API intended for Polar internal use only. Polar will not support 3rd party users with this API.
 * Stop receiving telemetry data from the device.
 *
 * Returns all telemetry types supported by this SDK for the given device.
 *
 * @param identifier: Polar device ID or BT address.
 * @return DeviceTelemetrySupport containing a list of PolarDeviceTelemetryType that are available for the given device. DeviceTelemetrySupport is empty container if the device does not support any telemetry types or if there was an error during reading telemetry configuration.
 *
 **/
    @OptIn
    suspend fun getAvailableTelemetryTypes(identifier: String): DeviceTelemetrySupport
}