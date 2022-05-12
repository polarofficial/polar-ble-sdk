package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient.PmdDataFieldEncoding
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClientUtils.parseFrameDataField
import java.util.*

/**
 * Sealed class to represent Location data sample
 */
sealed class GnssLocationDataSample

/**
 * Location data
 * @property timeStamp ns in epoch time. The [timeStamp] represent time of last sample in location data [gnssLocationDataSamples] list
 */
class GnssLocationData internal constructor(val timeStamp: Long) {

    /**
     * GPS Coordinates, Speed, and Distance Data
     */
    data class GnssCoordinateSample internal constructor(
        val latitude: Double,
        val longitude: Double,
        // time in format "yyyy-MM-dd'T'HH:mm:ss.SSS"
        val date: String,
        // cumulative distance in m
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
    ) : GnssLocationDataSample()

    /**
     * GPS Satellite Dilution, and Altitude Data
     */
    data class GnssSatelliteDilutionSample internal constructor(
        // dilution distance in 0.01 precision
        val dilution: Float,
        // altitude in meters
        val altitude: Int,
        val numberOfSatellites: UInt,
        val fix: Boolean,
    ) : GnssLocationDataSample()

    data class GnssSatelliteSummary internal constructor(
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

    /**
     * GPS Satellite Summary Data
     */
    data class GnssSatelliteSummarySample internal constructor(
        val seenGnssSatelliteSummaryBand1: GnssSatelliteSummary,
        val usedGnssSatelliteSummaryBand1: GnssSatelliteSummary,
        val seenGnssSatelliteSummaryBand2: GnssSatelliteSummary,
        val usedGnssSatelliteSummaryBand2: GnssSatelliteSummary,
        val max_snr: UInt
    ) : GnssLocationDataSample()

    /**
     *  GPS NMEA Data
     */
    data class GnssGpsNMEASample internal constructor(
        val measurementPeriod: UInt,
        val messageLength: UInt,
        val statusFlags: UByte,
        val nmeaMessage: String
    ) : GnssLocationDataSample()

    val gnssLocationDataSamples: MutableList<GnssLocationDataSample> = ArrayList()

    companion object {
        fun parseDataFromDataFrame(isCompressed: Boolean, frameType: BlePMDClient.PmdDataFrameType, frame: ByteArray, factor: Float, timeStamp: Long): GnssLocationData {
            return if (isCompressed) {
                throw Exception("Compressed FrameType: $frameType is not supported by Location data parser")
            } else {
                when (frameType) {
                    BlePMDClient.PmdDataFrameType.TYPE_0 -> dataFromType0(frame, timeStamp)
                    BlePMDClient.PmdDataFrameType.TYPE_1 -> dataFromType1(frame, timeStamp)
                    BlePMDClient.PmdDataFrameType.TYPE_2 -> dataFromType2(frame, timeStamp)
                    BlePMDClient.PmdDataFrameType.TYPE_3 -> dataFromType3(frame, timeStamp)
                    else -> throw Exception("Raw FrameType: $frameType is not supported by Location data parser")
                }
            }
        }

        private fun dataFromType0(frame: ByteArray, timeStamp: Long): GnssLocationData {
            val locationData = GnssLocationData(timeStamp)
            var offset = 0
            while (offset < frame.size) {
                val latitude = parseFrameDataField(frame.sliceArray(offset..(offset + 7)), PmdDataFieldEncoding.DOUBLE_IEEE754) as Double
                offset += 8
                val longitude = parseFrameDataField(frame.sliceArray(offset..(offset + 7)), PmdDataFieldEncoding.DOUBLE_IEEE754) as Double
                offset += 8
                val year = parseFrameDataField(frame.sliceArray(offset..(offset + 1)), PmdDataFieldEncoding.UNSIGNED_INT) as UInt
                offset += 2
                val month = parseFrameDataField(frame.sliceArray(offset..offset), PmdDataFieldEncoding.UNSIGNED_INT) as UInt
                offset += 1
                val day = parseFrameDataField(frame.sliceArray(offset..offset), PmdDataFieldEncoding.UNSIGNED_INT) as UInt
                offset += 1
                val time = parseFrameDataField(frame.sliceArray(offset..(offset + 3)), PmdDataFieldEncoding.UNSIGNED_INT) as UInt
                offset += 4

                val milliseconds = time and 0x3FFu
                val hours = (time and 0x7C00u) shr 10
                val minutes = (time and 0x1F8000u) shr 15
                val seconds = (time and 0x7E00000u) shr 21

                val date = "%04d".format(year.toInt()) + "-" +
                        "%02d".format(month.toInt()) + "-" +
                        "%02d".format(day.toInt()) +
                        "T" +
                        "%02d".format(hours.toInt()) + ":" +
                        "%02d".format(minutes.toInt()) + ":" +
                        "%02d".format(seconds.toInt()) + "." +
                        "%03d".format(milliseconds.toInt())

                val cumulativeDistanceUInt = parseFrameDataField(frame.sliceArray(offset..(offset + 3)), PmdDataFieldEncoding.UNSIGNED_INT) as UInt
                val cumulativeDistance = (cumulativeDistanceUInt.toDouble() / 10)
                offset += 4
                val speed: Float = parseFrameDataField(frame.sliceArray(offset..(offset + 3)), PmdDataFieldEncoding.FLOAT_IEEE754) as Float
                offset += 4
                val usedAccelerationSpeed = parseFrameDataField(frame.sliceArray(offset..(offset + 3)), PmdDataFieldEncoding.FLOAT_IEEE754) as Float
                offset += 4
                val coordinateSpeed = parseFrameDataField(frame.sliceArray(offset..(offset + 3)), PmdDataFieldEncoding.FLOAT_IEEE754) as Float
                offset += 4
                val accelerationSpeedFactory = parseFrameDataField(frame.sliceArray(offset..(offset + 3)), PmdDataFieldEncoding.FLOAT_IEEE754) as Float
                offset += 4
                val courseUInt = parseFrameDataField(frame.sliceArray(offset..(offset + 1)), PmdDataFieldEncoding.UNSIGNED_INT) as UInt
                val course = (courseUInt.toFloat() / 100)
                offset += 2
                val knotsSpeedUInt = parseFrameDataField(frame.sliceArray(offset..(offset + 1)), PmdDataFieldEncoding.UNSIGNED_INT) as UInt
                val gpsChipSpeed = (knotsSpeedUInt.toFloat() / 100)
                offset += 2
                val fix: Boolean = parseFrameDataField(frame.sliceArray(offset..offset), PmdDataFieldEncoding.BOOLEAN) as Boolean
                offset += 1
                val speedFlag = parseFrameDataField(frame.sliceArray(offset..offset), PmdDataFieldEncoding.SIGNED_INT) as Int
                offset += 1
                val fusionState = parseFrameDataField(frame.sliceArray(offset..offset), PmdDataFieldEncoding.UNSIGNED_INT) as UInt
                offset += 1

                val sample = GnssCoordinateSample(
                    latitude = latitude, longitude = longitude, date = date, cumulativeDistance = cumulativeDistance,
                    speed = speed, usedAccelerationSpeed = usedAccelerationSpeed, coordinateSpeed = coordinateSpeed, accelerationSpeedFactor = accelerationSpeedFactory,
                    course = course, gpsChipSpeed = gpsChipSpeed, fix = fix, speedFlag = speedFlag, fusionState = fusionState
                )
                locationData.gnssLocationDataSamples.add(sample)
            }
            return locationData
        }

        private fun dataFromType1(frame: ByteArray, timeStamp: Long): GnssLocationData {
            val locationData = GnssLocationData(timeStamp)
            var offset = 0
            while (offset < frame.size) {
                val dilutionInt = parseFrameDataField(frame.sliceArray(offset..(offset + 1)), PmdDataFieldEncoding.UNSIGNED_INT) as UInt
                val dilution = (dilutionInt.toFloat() / 100)
                offset += 2
                val altitude = parseFrameDataField(frame.sliceArray(offset..(offset + 1)), PmdDataFieldEncoding.SIGNED_INT) as Int
                offset += 2
                val numberOfSatellites = parseFrameDataField(frame.sliceArray(offset..offset), PmdDataFieldEncoding.UNSIGNED_INT) as UInt
                offset += 1
                val fix: Boolean = parseFrameDataField(frame.sliceArray(offset..offset), PmdDataFieldEncoding.BOOLEAN) as Boolean
                offset += 1

                val sample = GnssSatelliteDilutionSample(dilution = dilution, altitude = altitude, numberOfSatellites = numberOfSatellites, fix = fix)
                locationData.gnssLocationDataSamples.add(sample)
            }
            return locationData
        }

        private fun dataFromType2(frame: ByteArray, timeStamp: Long): GnssLocationData {
            val locationData = GnssLocationData(timeStamp)
            var offset = 0
            while (offset < frame.size) {
                val seenBand1 = GnssSatelliteSummary(
                    gpsNbrOfSat = frame[0].toUByte(),
                    gpsMaxSnr = frame[1].toUByte(),
                    glonassNbrOfSat = frame[2].toUByte(),
                    glonassMaxSnr = frame[3].toUByte(),
                    galileoNbrOfSat = frame[4].toUByte(),
                    galileoMaxSnr = frame[5].toUByte(),
                    beidouNbrOfSat = frame[6].toUByte(),
                    beidouMaxSnr = frame[7].toUByte(),
                    nbrOfSat = frame[8].toUByte(),
                    snrTop5Avg = frame[9].toUByte()
                )
                offset += 10
                val usedBand1 = GnssSatelliteSummary(
                    gpsNbrOfSat = frame[10].toUByte(),
                    gpsMaxSnr = frame[11].toUByte(),
                    glonassNbrOfSat = frame[12].toUByte(),
                    glonassMaxSnr = frame[13].toUByte(),
                    galileoNbrOfSat = frame[14].toUByte(),
                    galileoMaxSnr = frame[15].toUByte(),
                    beidouNbrOfSat = frame[16].toUByte(),
                    beidouMaxSnr = frame[17].toUByte(),
                    nbrOfSat = frame[18].toUByte(),
                    snrTop5Avg = frame[19].toUByte()
                )
                offset += 10
                val seenBand2 = GnssSatelliteSummary(
                    gpsNbrOfSat = frame[20].toUByte(),
                    gpsMaxSnr = frame[21].toUByte(),
                    glonassNbrOfSat = frame[22].toUByte(),
                    glonassMaxSnr = frame[23].toUByte(),
                    galileoNbrOfSat = frame[24].toUByte(),
                    galileoMaxSnr = frame[25].toUByte(),
                    beidouNbrOfSat = frame[26].toUByte(),
                    beidouMaxSnr = frame[27].toUByte(),
                    nbrOfSat = frame[28].toUByte(),
                    snrTop5Avg = frame[29].toUByte()
                )
                offset += 10
                val usedBand2 = GnssSatelliteSummary(
                    gpsNbrOfSat = frame[30].toUByte(),
                    gpsMaxSnr = frame[31].toUByte(),
                    glonassNbrOfSat = frame[32].toUByte(),
                    glonassMaxSnr = frame[33].toUByte(),
                    galileoNbrOfSat = frame[34].toUByte(),
                    galileoMaxSnr = frame[35].toUByte(),
                    beidouNbrOfSat = frame[36].toUByte(),
                    beidouMaxSnr = frame[37].toUByte(),
                    nbrOfSat = frame[38].toUByte(),
                    snrTop5Avg = frame[39].toUByte()
                )
                offset += 10
                val maxSnr = parseFrameDataField(frame.sliceArray(offset..offset), PmdDataFieldEncoding.UNSIGNED_INT) as UInt
                offset += 1

                val sample = GnssSatelliteSummarySample(
                    seenGnssSatelliteSummaryBand1 = seenBand1,
                    usedGnssSatelliteSummaryBand1 = usedBand1,
                    seenGnssSatelliteSummaryBand2 = seenBand2,
                    usedGnssSatelliteSummaryBand2 = usedBand2,
                    max_snr = maxSnr
                )
                locationData.gnssLocationDataSamples.add(sample)
            }
            return locationData
        }

        private fun dataFromType3(frame: ByteArray, timeStamp: Long): GnssLocationData {
            val locationData = GnssLocationData(timeStamp)
            var offset = 0
            while (offset < frame.size) {
                val measurementPeriod = parseFrameDataField(frame.sliceArray(offset..(offset + 3)), PmdDataFieldEncoding.UNSIGNED_INT) as UInt
                offset += 4
                val messageLength = parseFrameDataField(frame.sliceArray(offset..(offset + 1)), PmdDataFieldEncoding.UNSIGNED_INT) as UInt
                offset += 2
                val statusFlags = parseFrameDataField(frame.sliceArray(offset..offset), PmdDataFieldEncoding.UNSIGNED_BYTE) as UByte
                offset += 1
                val nmeaMessage = String(frame.sliceArray(offset until (offset + messageLength.toInt())))
                offset += messageLength.toInt()
                val sample = GnssGpsNMEASample(measurementPeriod = measurementPeriod, messageLength = messageLength, statusFlags = statusFlags, nmeaMessage = nmeaMessage)
                locationData.gnssLocationDataSamples.add(sample)
            }
            return locationData
        }
    }
}