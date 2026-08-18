package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.model.gatt.client.BleMdsClient
import com.polar.sdk.api.DeviceTelemetryConfiguration
import com.polar.sdk.api.DeviceTelemetryEvent
import com.polar.sdk.api.PolarDeviceTelemetryType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal object PolarTelemetryMemfaultUtils {

    fun getDeviceTelemetryConfiguration(client: BleMdsClient): DeviceTelemetryConfiguration {
        val dataUri = client.dataUri ?: ""
        val auth = client.authorization ?: ""
        val supportedFeatures = client.supportedFeatures
        return DeviceTelemetryConfiguration(
            deviceIdentifier = client.deviceIdentifier,
            dataUri = dataUri,
            authorization = auth,
            supportedFeatures = supportedFeatures
        )
    }

    fun startTelemetryStreaming(client: BleMdsClient): Flow<DeviceTelemetryEvent> {
        return client.startMdsNotifications(true).map { config ->
            DeviceTelemetryEvent(type = PolarDeviceTelemetryType.memfault_mds, payload = config.exportData)
        }
    }

    fun stopTelemetryStreaming(client: BleMdsClient): Boolean {
        return client.sendControlPointCommand(0) == 0
    }

    fun getAvailableTelemetryTypes(client: BleMdsClient): List<PolarDeviceTelemetryType> {
        val availableTelemetryTypes = mutableListOf<PolarDeviceTelemetryType>()
        if (client.supportedFeatures != null) {
            availableTelemetryTypes.add(PolarDeviceTelemetryType.memfault_mds)
        }
        return availableTelemetryTypes
    }
}