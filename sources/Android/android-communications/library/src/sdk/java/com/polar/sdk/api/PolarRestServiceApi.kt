package com.polar.sdk.api

import com.polar.sdk.api.model.restapi.PolarDeviceRestApiServices
import com.polar.sdk.api.model.restapi.PolarDeviceRestApiServiceDescription
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single

interface PolarRestServiceApi {
    /**
     * Discover available services from device
     *
     * @param identifier Polar Device ID or BT address
     * @return [Single] [PolarDeviceRestApiServices] object listing service names and corresponding
     * paths or error
     */
    fun listRestApiServices(identifier: String): Single<PolarDeviceRestApiServices>

    /**
     * Get details related to particular REST API.
     *
     * @param identifier Polar Device ID or BT address
     * @param path the REST API path corresponding to a named service returned by listRestApiServices
     * @return [Single] [PolarDeviceRestApiServiceDescription] object with detailed description of the
     * service, or error
     */
    fun getRestApiDescription(identifier: String, path: String): Single<PolarDeviceRestApiServiceDescription>

    /**
     * Notify device via a REST API in the device.
     *
     * @param identifier Polar device ID or BT address
     * @param notification content of the notification in JSON format.
     * @param path the API endpoint that will be notified; the path of the REST API file in device +
     * REST API parameters.
     * @return  [Completable] with success or error
     */
    fun putNotification(identifier: String, notification: String, path: String): Completable

    /**
     * Streams for received device REST API events  parameters decoded as given type T endlessly.
     * Only dispose , take(1) etc ... stops stream.
     * Normally requires event action that subscribes to the events using putNotification()
     * @param identifier Polar device ID or BT address
     * @param mapper lambda that converts JSON string to type T
     * @return [Flowable] stream of REST API event parameters decoded from JSON format using
     * mapper lambda to type T. SDK provides default decoding using String.toObject<T>() where
     * T should be a subclass of RestApiEventPayload abstract class. Example of such class is
     * [PolarSleepRecordingStatusData].
     * Produces onNext after successfully received notification and decoded as List<T>.
     * onCompleted not produced unless stream is further configured.
     * onError, see [BlePsFtpException], [BleGattException]
     */
    fun <T : RestApiEventPayload>receiveRestApiEvents (identifier: String, mapper:((jsonString: String) -> T)): Flowable<List<T>>
}

/**
 * Abstract base class for data classes parsed from JSON strings produced by device REST API.
 */
abstract class RestApiEventPayload {}

