package com.polar.sdk.api.model

/**
 * Polar location data
 *  @property samples location data samples
 */
class PolarLocationData(val samples: List<PolarLocationDataSample>)

/**
 * Polar location data sample
 */
sealed class PolarLocationDataSample
data class GpsCoordinatesSample(
    val timeStamp: Long,
    val latitude: Double,
    val longitude: Double,
    // time in format "yyyy-MM-dd'T'HH:mm:ss.SSS"
    val time: String,
    // cumulative distance in dm
    val cumulativeDistance: Double,
    // speed in km/h
    val speed: Float,
    val usedAccelerationSpeed: Float,
    val coordinateSpeed: Float,
    val accelerationSpeedFactor: Float,
    // course in degrees
    val course: Float,
    // speed in knots
    val gpsChipSpeed: Float,
    val fix: Boolean,
    val speedFlag: Int,
    val fusionState: UInt
) : PolarLocationDataSample()

data class GpsSatelliteDilutionSample(
    val timeStamp: Long,
    // dilution in 0.01 precision
    val dilution: Float,
    // altitude in meters
    val altitude: Int,
    val numberOfSatellites: UInt,
    val fix: Boolean,
) : PolarLocationDataSample()

data class SatelliteSummary(
    val gpsNbrOfSat: UByte,
    val gpsMaxSnr: UByte,
    val glonassNbrOfSat: UByte,
    val glonassMaxSnr: UByte,
    val galileoNbrOfSat: UByte,
    val galileoMaxSnr: UByte,
    val beidouNbrOfSat: UByte,
    val beidouMaxSnr: UByte,
    val nbrOfSat: UByte,
    val snrTop5Avg: UByte,
)

data class GpsSatelliteSummarySample(
    val timeStamp: Long,
    val seenSatelliteSummaryBand1: SatelliteSummary,
    val usedSatelliteSummaryBand1: SatelliteSummary,
    val seenSatelliteSummaryBand2: SatelliteSummary,
    val usedSatelliteSummaryBand2: SatelliteSummary,
    val maxSnr: UInt
) : PolarLocationDataSample()

data class GpsNMEASample(
    val timeStamp: Long,
    val measurementPeriod: UInt,
    val statusFlags: UByte,
    val nmeaMessage: String
) : PolarLocationDataSample()