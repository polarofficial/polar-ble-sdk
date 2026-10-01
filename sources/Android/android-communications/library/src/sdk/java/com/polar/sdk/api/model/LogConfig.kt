package com.polar.sdk.api.model

import data.SensorDataLog
import data.SensorDataLog.PbSensorDataLog
import fi.polar.remote.representation.protobuf.Structures.PbBleDeviceName

data class LogConfig(
    val ohrLogEnabled: Boolean? = null,
    val ppiLogEnabled: Boolean? = null,
    val accelerationLogEnabled: Boolean? = null,
    val caloriesLogEnabled: Boolean? = null,
    val gpsLogEnabled: Boolean? = null,
    val gpsNmeaLogEnabled: Boolean? = null,
    val magnetometerLogEnabled: Boolean? = null,
    val tapLogEnabled: Boolean? = null,
    val barometerLogEnabled: Boolean? = null,
    val gyroscopeLogEnabled: Boolean? = null,
    val sleepLogEnabled: Boolean? = null,
    val slopeLogEnabled: Boolean? = null,
    val ambientLightLogEnabled: Boolean? = null,
    val tlrLogEnabled: Boolean? = null,
    val ondemandLogEnabled: Boolean? = null,
    val capsenseLogEnabled: Boolean? = null,
    val fusionLogEnabled: Boolean? = null,
    val metLogEnabled: Boolean? = null,
    val verticalAccLogEnabled: Boolean? = null,
    val amdLogEnabled: Boolean? = null,
    val skinTemperatureLogEnabled: Boolean? = null,
    val compassLogEnabled: Boolean? = null,
    val speed3DLogEnabled: Boolean? = null,
    val retainSettingsOverBoot: Boolean? = null,
    val logTriggerSettings: PbSensorDataLog.PbLogTrigger? = null,
    val magnetometerFrequency: PbSensorDataLog.PbMagnetometerLogFrequency? = null,
    val hrSensorConfig: HrSensorConfig? = null
) {
    companion object {
        const val LOG_CONFIG_FILENAME = "/SDLOGS.BPB"

        fun fromBytes(bytes: ByteArray): LogConfig {
            val proto = PbSensorDataLog.parseFrom(bytes)
            val hrSensorConfig: HrSensorConfig? = null
            return LogConfig(
                ohrLogEnabled = if (proto.hasOhrLogEnabled()) proto.ohrLogEnabled else null,
                ppiLogEnabled = if (proto.hasPpiLogEnabled()) proto.ppiLogEnabled else null,
                accelerationLogEnabled = if (proto.hasAccelerationLogEnabled()) proto.accelerationLogEnabled else null,
                caloriesLogEnabled = if (proto.hasCaloriesLogEnabled()) proto.caloriesLogEnabled else null,
                gpsLogEnabled = if (proto.hasGpsLogEnabled()) proto.gpsLogEnabled else null,
                gpsNmeaLogEnabled = if (proto.hasGpsNmeaLogEnabled()) proto.gpsNmeaLogEnabled else null,
                magnetometerLogEnabled = if (proto.hasMagnetometerLogEnabled()) proto.magnetometerLogEnabled else null,
                tapLogEnabled = if (proto.hasTapLogEnabled()) proto.tapLogEnabled else null,
                barometerLogEnabled = if (proto.hasBarometerLogEnabled()) proto.barometerLogEnabled else null,
                gyroscopeLogEnabled = if (proto.hasGyroscopeLogEnabled()) proto.gyroscopeLogEnabled else null,
                sleepLogEnabled = if (proto.hasSleepLogEnabled()) proto.sleepLogEnabled else null,
                slopeLogEnabled = if (proto.hasSlopeLogEnabled()) proto.slopeLogEnabled else null,
                ambientLightLogEnabled = if (proto.hasAmbientLightLogEnabled()) proto.ambientLightLogEnabled else null,
                tlrLogEnabled = if (proto.hasTlrLogEnabled()) proto.tlrLogEnabled else null,
                ondemandLogEnabled = if (proto.hasOndemandLogEnabled()) proto.ondemandLogEnabled else null,
                capsenseLogEnabled = if (proto.hasCapsenseLogEnabled()) proto.capsenseLogEnabled else null,
                fusionLogEnabled = if ( proto.hasFusionLogEnabled()) proto.fusionLogEnabled else null,
                metLogEnabled = if (proto.hasMetLogEnabled()) proto.metLogEnabled else null,
                verticalAccLogEnabled = if (proto.hasVerticalAccLogEnabled()) proto.verticalAccLogEnabled else null,
                amdLogEnabled = if (proto.hasAmdLogEnabled()) proto.amdLogEnabled else null,
                skinTemperatureLogEnabled = if (proto.hasSkinTemperatureLogEnabled()) proto.skinTemperatureLogEnabled else null,
                compassLogEnabled = if (proto.hasCompassLogEnabled()) proto.compassLogEnabled else null,
                speed3DLogEnabled = if (proto.hasSpeed3DLogEnabled()) proto.speed3DLogEnabled else null,
                retainSettingsOverBoot = if (proto.hasRetainSettingsOverBoot()) proto.retainSettingsOverBoot else null,
                logTriggerSettings = if (proto.hasLogTrigger()) proto.logTrigger else null,
                magnetometerFrequency = if (proto.hasMagnetometerLogFrequency()) proto.magnetometerLogFrequency else null,
                hrSensorConfig = if (proto.hasBleHrSensorConfig()) HrSensorConfig.fromBytes(proto.bleHrSensorConfig.toByteArray()) else null
            )
        }
    }

    fun toProto(): PbSensorDataLog {
        val builder = PbSensorDataLog.newBuilder()
        if (ohrLogEnabled != null) builder.ohrLogEnabled = ohrLogEnabled
        if (ppiLogEnabled != null) builder.ppiLogEnabled = ppiLogEnabled
        if (accelerationLogEnabled != null) builder.accelerationLogEnabled = accelerationLogEnabled
        if (caloriesLogEnabled != null) builder.caloriesLogEnabled = caloriesLogEnabled
        if (gpsLogEnabled != null) builder.gpsLogEnabled = gpsLogEnabled
        if (gpsNmeaLogEnabled != null) builder.gpsNmeaLogEnabled = gpsNmeaLogEnabled
        if (magnetometerLogEnabled != null) builder.magnetometerLogEnabled = magnetometerLogEnabled
        if (tapLogEnabled != null) builder.tapLogEnabled = tapLogEnabled
        if (barometerLogEnabled != null) builder.barometerLogEnabled = barometerLogEnabled
        if (gyroscopeLogEnabled != null) builder.gyroscopeLogEnabled = gyroscopeLogEnabled
        if (sleepLogEnabled != null) builder.sleepLogEnabled = sleepLogEnabled
        if (slopeLogEnabled != null) builder.slopeLogEnabled = slopeLogEnabled
        if (ambientLightLogEnabled != null) builder.ambientLightLogEnabled = ambientLightLogEnabled
        if (tlrLogEnabled != null) builder.tlrLogEnabled = tlrLogEnabled
        if (ondemandLogEnabled != null) builder.ondemandLogEnabled = ondemandLogEnabled
        if (capsenseLogEnabled != null) builder.capsenseLogEnabled = capsenseLogEnabled
        if (fusionLogEnabled != null) builder.fusionLogEnabled = fusionLogEnabled
        if (metLogEnabled != null) builder.metLogEnabled = metLogEnabled
        if (verticalAccLogEnabled != null) builder.verticalAccLogEnabled = verticalAccLogEnabled
        if (amdLogEnabled != null) builder.amdLogEnabled = amdLogEnabled
        if (skinTemperatureLogEnabled != null) builder.skinTemperatureLogEnabled = skinTemperatureLogEnabled
        if (compassLogEnabled != null) builder.compassLogEnabled = compassLogEnabled
        if (speed3DLogEnabled != null) builder.speed3DLogEnabled = speed3DLogEnabled
        if (retainSettingsOverBoot != null) builder.retainSettingsOverBoot = retainSettingsOverBoot
        if (logTriggerSettings != null) builder.logTrigger = logTriggerSettings
        if (magnetometerFrequency != null) builder.magnetometerLogFrequency = magnetometerFrequency
        if(hrSensorConfig != null) builder.bleHrSensorConfig = hrSensorConfig.toProto()

        return builder.build()
    }
}

data class HrSensorConfig(
    val deviceInformation :String? = null, //  Device identified by its BLE Complete Local Name (e.g. "Polar H10 ABCD1234")
    val waitUntilConnected: Boolean? = null  // When true, OHR logging will not start until this sensor is connected and delivering HR data.
) {

    companion object {
        fun fromBytes(bytes: ByteArray): HrSensorConfig {
            val proto = SensorDataLog.PbBleHrSensorConfig.parseFrom(bytes)
            return HrSensorConfig(
                deviceInformation = if (proto.hasDeviceName()) proto.deviceName.name else null,
                waitUntilConnected = if (proto.hasWaitForConnect()) proto.waitForConnect else null
            )
        }
    }


    fun toProto(): SensorDataLog.PbBleHrSensorConfig {
        val builder = SensorDataLog.PbBleHrSensorConfig.newBuilder()
        val deviceNameBuilder = PbBleDeviceName.newBuilder()
        deviceNameBuilder.name = deviceInformation
        if (deviceInformation != null) builder.deviceName = deviceNameBuilder.build()
        if (waitUntilConnected != null) builder.waitForConnect = waitUntilConnected

        return builder.build()
    }
}