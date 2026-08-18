package com.polar.sdk.impl

import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.BleMdsClient
import com.polar.sdk.api.DeviceTelemetryConfiguration
import com.polar.sdk.api.DeviceTelemetryEvent
import com.polar.sdk.api.DeviceTelemetrySupport
import com.polar.sdk.api.PolarBleTelemetryApi
import com.polar.sdk.api.PolarDeviceTelemetryType
import com.polar.sdk.api.errors.PolarServiceNotAvailable
import com.polar.sdk.impl.utils.PolarServiceClientUtils
import com.polar.sdk.impl.utils.PolarTelemetryMemfaultUtils
import kotlinx.coroutines.flow.Flow

class PolarTelemetryApiImpl(val listener: BleDeviceListener): PolarBleTelemetryApi {

    companion object {
        private const val TAG = "PolarTelemetryApiImpl"
    }

    override fun getDeviceTelemetryConfiguration(
        identifier: String,
        telemetryType: PolarDeviceTelemetryType
    ): DeviceTelemetryConfiguration {
        val session = PolarServiceClientUtils.sessionMdsClientReady(identifier, listener)
        val client = session.fetchClient(BleMdsClient.MDS_SERVICE) as? BleMdsClient
            ?: run {
                BleLogger.d(TAG, "getDeviceTelemetryConfiguration: BLE MDS client not available for $identifier, no-op")
                throw PolarServiceNotAvailable()
            }

        return when (telemetryType) {
            PolarDeviceTelemetryType.memfault_mds -> PolarTelemetryMemfaultUtils.getDeviceTelemetryConfiguration(client)
        }
    }

    override suspend fun startTelemetry(
        identifier: String,
        telemetryType: PolarDeviceTelemetryType
    ): Flow<DeviceTelemetryEvent> {

        val session = PolarServiceClientUtils.sessionMdsClientReady(identifier, listener)
        val client = session.fetchClient(BleMdsClient.MDS_SERVICE) as? BleMdsClient
            ?: run {
                BleLogger.d(TAG, "startTelemetry: BLE $telemetryType client not available for $identifier, no-op")
                throw PolarServiceNotAvailable()
            }

        return when (telemetryType) {
        PolarDeviceTelemetryType.memfault_mds -> PolarTelemetryMemfaultUtils.startTelemetryStreaming(client)
        }
    }

    override suspend fun stopTelemetry(
        identifier: String,
        telemetryType: PolarDeviceTelemetryType
    ): Boolean {
        val session = PolarServiceClientUtils.sessionMdsClientReady(identifier, listener)
        val client = session.fetchClient(BleMdsClient.MDS_SERVICE) as? BleMdsClient
            ?: run {
                BleLogger.d(TAG, "stopTelemetry: BLE MDS client not available for $identifier, no-op")
                throw PolarServiceNotAvailable()
            }

        return when (telemetryType) {
            PolarDeviceTelemetryType.memfault_mds -> PolarTelemetryMemfaultUtils.stopTelemetryStreaming (client)
        }
    }

    override suspend fun getAvailableTelemetryTypes(identifier: String): DeviceTelemetrySupport {
        val session = PolarServiceClientUtils.sessionMdsClientReady(identifier, listener)
        val client = session.fetchClient(BleMdsClient.MDS_SERVICE) as? BleMdsClient
            ?: throw PolarServiceNotAvailable()

        client.clientReady(true)
        val supportedTypes = PolarTelemetryMemfaultUtils.getAvailableTelemetryTypes(client)

        return DeviceTelemetrySupport(supportedTypes, identifier)
    }
}