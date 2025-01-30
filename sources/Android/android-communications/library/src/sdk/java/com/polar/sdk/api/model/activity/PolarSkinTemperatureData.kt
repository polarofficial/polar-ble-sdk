package com.polar.sdk.api.model

import com.polar.services.datamodels.protobuf.TemperatureMeasurement.TemperatureMeasurementSample
import java.time.LocalDate

data class PolarSkinTemperatureData(val date: LocalDate? = null, val result: PolarSkinTemperatureResult? = null)

/**
 * TM_SKIN_TEMPERATURE, body temperature measured from skin surface
 * TM_CORE_TEMPERATURE, body temperature measured from inside a body
 */
enum class SkinTemperatureMeasurementType(val value: Int) {
    TM_UNKNOWN(0),
    TM_SKIN_TEMPERATURE(1),
    TM_CORE_TEMPERATURE(2);

    companion object {
        infix fun from(value: Int): SkinTemperatureMeasurementType? =
            SkinTemperatureMeasurementType.values().firstOrNull { it.value == value }
    }
}

/**
 * SL_DISTAL, sensor is located away from torso, for example on wrist
 * SL_PROXIMAL, sensor is located on torso, for example on chest
 */
enum class SkinTemperatureSensorLocation(val value: Int) {
    SL_UNKNOWN(0),
    SL_DISTAL(1),
    SL_PROXIMAL(2);

    companion object {
        infix fun from(value: Int): SkinTemperatureSensorLocation? =
            SkinTemperatureSensorLocation.values().firstOrNull { it.value == value }
    }
}

data class PolarSkinTemperatureResult(
    val deviceId: String?,
    val sensorLocation: SkinTemperatureSensorLocation?,
    val measurementType: SkinTemperatureMeasurementType?,
    val skinTemperatureList: List<PolarSkinTemperatureDataSample>?
)

data class PolarSkinTemperatureDataSample(
    val recordingTimeDeltaMs: Long,
    val temperature: Float
)

fun fromPbTemperatureMeasurementSamples(pbTemperatureMeasurementData: List<TemperatureMeasurementSample>):
        List<PolarSkinTemperatureDataSample> {

    var skinTemperatureSampleList = mutableListOf<PolarSkinTemperatureDataSample>()

    for (sample in pbTemperatureMeasurementData) {
        skinTemperatureSampleList.add(
            PolarSkinTemperatureDataSample(
                sample.recordingTimeDeltaMilliseconds, sample.temperatureCelsius
            )
        )
    }
    return skinTemperatureSampleList
}