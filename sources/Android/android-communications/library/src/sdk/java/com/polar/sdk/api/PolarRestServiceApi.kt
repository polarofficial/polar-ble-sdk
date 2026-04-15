package com.polar.sdk.api

import com.polar.sdk.api.model.restapi.PolarDeviceRestApiServiceDescription
import com.polar.sdk.api.model.restapi.PolarDeviceRestApiServices
import kotlinx.coroutines.flow.Flow

interface PolarRestServiceApi {
    /**
     * Discover available services from device.
     *
     * Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER].
     *
     * @param identifier Polar Device ID or BT address
     * @return [PolarDeviceRestApiServices] object listing service names and corresponding paths
     * @throws Throwable if the operation fails
     */
    suspend fun listRestApiServices(identifier: String): PolarDeviceRestApiServices

    /**
     * Get details related to particular REST API.
     *
     * Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER].
     *
     * @param identifier Polar Device ID or BT address
     * @param path the REST API path corresponding to a named service returned by listRestApiServices
     * @return [PolarDeviceRestApiServiceDescription] object with detailed description of the service
     * @throws Throwable if the operation fails
     */
    suspend fun getRestApiDescription(
        identifier: String,
        path: String
    ): PolarDeviceRestApiServiceDescription

    /**
     * Notify device via a REST API in the device.
     *
     * Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER].
     *
     * @param identifier Polar device ID or BT address
     * @param notification content of the notification in JSON format
     * @param path the API endpoint that will be notified; the path of the REST API file in device +
     * REST API parameters
     * @throws Throwable if the operation fails
     */
    suspend fun putNotification(identifier: String, notification: String, path: String)

    /**
     * Stream received device REST API event parameters decoded as given type [T].
     *
     * Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER].
     *
     * Normally requires an event action that subscribes to the events using [putNotification].
     *
     * @param identifier Polar device ID or BT address
     * @param mapper lambda that converts JSON string to type [T]
     * @return [Flow] of REST API event parameters decoded from JSON format using mapper lambda to
     * type [T]. SDK provides default decoding using `String.toObject<T>()` where [T] should be a
     * subclass of [RestApiEventPayload]. Example of such class is [PolarSleepRecordingStatusData].
     * Emits decoded `List<T>` values until collection is cancelled or the flow completes.
     * Exceptions may include [BlePsFtpException], [BleGattException].
     */
    fun <T : RestApiEventPayload> receiveRestApiEvents(
        identifier: String,
        mapper: (jsonString: String) -> T
    ): Flow<List<T>>
}

/**
 * Abstract base class for data classes parsed from JSON strings produced by device REST API.
 */
abstract class RestApiEventPayload {}
