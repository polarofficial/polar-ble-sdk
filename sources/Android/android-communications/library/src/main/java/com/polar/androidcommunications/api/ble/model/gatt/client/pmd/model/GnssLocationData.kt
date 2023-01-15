package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient.PmdDataFieldEncoding
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrameUtils.parseFrameDataField

/**
 * Sealed class to represent Location data sample
 */
internal sealed class GnssLocationDataSample

internal class GnssLocationData {

    /**
     * GPS Coordinates, Speed, and Distance Data
     */
    data class GnssCoordinateSample internal constructor(
        val timeStamp: ULong,
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
        val timeStamp: ULong,
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
        val timeStamp: ULong,
        val seenGnssSatelliteSummaryBand1: GnssSatelliteSummary,
        val usedGnssSatelliteSummaryBand1: GnssSatelliteSummary,
        val seenGnssSatelliteSummaryBand2: GnssSatelliteSummary,
        val usedGnssSatelliteSummaryBand2: GnssSatelliteSummary,
        val maxSnr: UInt
    ) : GnssLocationDataSample()

    /**
     *  GPS NMEA Data
     */
    data class GnssGpsNMEASample internal constructor(
        val timeStamp: ULong,
        val measurementPeriod: UInt,
        val messageLength: UInt,
        val statusFlags: UByte,
        val nmeaMessage: String
    ) : GnssLocationDataSample()

    val gnssLocationDataSamples: MutableList<GnssLocationDataSample> = mutableListOf()

    companion object {

        private const val TYPE_0_SAMPLE_IN_BYTES = 51
        private const val TYPE_1_SAMPLE_IN_BYTES = 6
        private const val TYPE_2_SAMPLE_IN_BYTES = 41

        fun parseDataFromDataFrame(frame: PmdDataFrame): GnssLocationData {
            return if (frame.isCompressedFrame) {
                throw Exception("Compressed FrameType: ${frame.frameType} is not supported by Location data parser")
            } else {
                when (frame.frameType) {
                    PmdDataFrame.PmdDataFrameType.TYPE_0 -> dataFromRawType0(frame)
                    PmdDataFrame.PmdDataFrameType.TYPE_1 -> dataFromRawType1(frame)
                    PmdDataFrame.PmdDataFrameType.TYPE_2 -> dataFromRawType2(frame)
                    PmdDataFrame.PmdDataFrameType.TYPE_3 -> dataFromRawType3(frame)
                    else -> throw Exception("Raw FrameType: ${frame.frameType} is not supported by Location data parser")
                }
            }
        }

        private fun dataFromRawType0(frame: PmdDataFrame): GnssLocationData {
            val locationData = GnssLocationData()
            var offset = 0

            val samplesSize = frame.dataContent.size / TYPE_0_SAMPLE_IN_BYTES
            val timeStamps = PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp = frame.previousTimeStamp, frameTimeStamp = frame.timeStamp, samplesSize = samplesSize, frame.sampleRate)
            var timeStampIndex = 0
            while (offset < frame.dataContent.size) {
                val latitude = parseFrameDataField(frame.dataContent.sliceArray(offset..(offset + 7)), PmdDataFieldEncoding.DOUBLE_IEEE754) as Double
                offset += 8
                val longitude = parseFrameDataField(frame.dataContent.sliceArray(offset..(offset + 7)), PmdDataFieldEncoding.DOUBLE_IEEE754) as Double
                offset += 8
                val year = parseFrameDataField(frame.dataContent.sliceArray(offset..(offset + 1)), PmdDataFieldEncoding.UNSIGNED_INT) as UInt
                offset += 2
                val month = parseFrameDataField(frame.dataContent.sliceArray(offset..offset), PmdDataFieldEncoding.UNSIGNED_INT) as UInt
                offset += 1
                val day = parseFrameDataField(frame.dataContent.sliceArray(offset..offset), PmdDataFieldEncoding.UNSIGNED_INT) as UInt
                offset += 1
                val time = parseFrameDataField(frame.dataContent.sliceArray(offset..(offset + 3)), PmdDataFieldEncoding.UNSIGNED_INT) as UInt
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

                val cumulativeDistanceUInt = parseFrameDataField(frame.dataContent.sliceArray(offset..(offset + 3)), PmdDataFieldEncoding.UNSIGNED_INT) as UInt
                val cumulativeDistance = (cumulativeDistanceUInt.toDouble() / 10)
                offset += 4
                val speed: Float = parseFrameDataField(frame.dataContent.sliceArray(offset..(offset + 3)), PmdDataFieldEncoding.FLOAT_IEEE754) as Float
                offset += 4
                val usedAccelerationSpeed = parseFrameDataField(frame.dataContent.sliceArray(offset..(offset + 3)), PmdDataFieldEncoding.FLOAT_IEEE754) as Float
                offset += 4
                val coordinateSpeed = parseFrameDataField(frame.dataContent.sliceArray(offset..(offset + 3)), PmdDataFieldEncoding.FLOAT_IEEE754) as Float
                offset += 4
                val accelerationSpeedFactory = parseFrameDataField(frame.dataContent.sliceArray(offset..(offset + 3)), PmdDataFieldEncoding.FLOAT_IEEE754) as Float
                offset += 4
                val courseUInt = parseFrameDataField(frame.dataContent.sliceArray(offset..(offset + 1)), PmdDataFieldEncoding.UNSIGNED_INT) as UInt
                val course = (courseUInt.toFloat() / 100)
                offset += 2
                val knotsSpeedUInt = parseFrameDataField(frame.dataContent.sliceArray(offset..(offset + 1)), PmdDataFieldEncoding.UNSIGNED_INT) as UInt
                val gpsChipSpeed = (knotsSpeedUInt.toFloat() / 100)
                offset += 2
                val fix: Boolean = parseFrameDataField(frame.dataContent.sliceArray(offset..offset), PmdDataFieldEncoding.BOOLEAN) as Boolean
                offset += 1
                val speedFlag = parseFrameDataField(frame.dataContent.sliceArray(offset..offset), PmdDataFieldEncoding.SIGNED_INT) as Int
                offset += 1
                val fusionState = parseFrameDataField(frame.dataContent.sliceArray(offset..offset), PmdDataFieldEncoding.UNSIGNED_INT) as UInt
                offset += 1

                val sample = GnssCoordinateSample(
                    timeStamp = timeStamps[timeStampIndex],
                    latitude = latitude, longitude = longitude, date = date, cumulativeDistance = cumulativeDistance,
                    speed = speed, usedAccelerationSpeed = usedAccelerationSpeed, coordinateSpeed = coordinateSpeed, accelerationSpeedFactor = accelerationSpeedFactory,
                    course = course, gpsChipSpeed = gpsChipSpeed, fix = fix, speedFlag = speedFlag, fusionState = fusionState
                )
                locationData.gnssLocationDataSamples.add(sample)
                timeStampIndex++
            }
            return locationData
        }

        private fun dataFromRawType1(frame: PmdDataFrame): GnssLocationData {
            val locationData = GnssLocationData()
            var offset = 0

            val samplesSize = frame.dataContent.size / TYPE_1_SAMPLE_IN_BYTES
            val timeStamps = PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp = frame.previousTimeStamp, frameTimeStamp = frame.timeStamp, samplesSize = samplesSize, frame.sampleRate)
            var timeStampIndex = 0

            while (offset < frame.dataContent.size) {
                val dilutionInt = parseFrameDataField(frame.dataContent.sliceArray(offset..(offset + 1)), PmdDataFieldEncoding.UNSIGNED_INT) as UInt
                val dilution = (dilutionInt.toFloat() / 100)
                offset += 2
                val altitude = parseFrameDataField(frame.dataContent.sliceArray(offset..(offset + 1)), PmdDataFieldEncoding.SIGNED_INT) as Int
                offset += 2
                val numberOfSatellites = parseFrameDataField(frame.dataContent.sliceArray(offset..offset), PmdDataFieldEncoding.UNSIGNED_INT) as UInt
                offset += 1
                val fix: Boolean = parseFrameDataField(frame.dataContent.sliceArray(offset..offset), PmdDataFieldEncoding.BOOLEAN) as Boolean
                offset += 1

                val sample = GnssSatelliteDilutionSample(timeStamp = timeStamps[timeStampIndex], dilution = dilution, altitude = altitude, numberOfSatellites = numberOfSatellites, fix = fix)
                locationData.gnssLocationDataSamples.add(sample)
                timeStampIndex++
            }
            return locationData
        }

        private fun dataFromRawType2(frame: PmdDataFrame): GnssLocationData {
            val locationData = GnssLocationData()
            var offset = 0

            val samplesSize = frame.dataContent.size / TYPE_2_SAMPLE_IN_BYTES
            val timeStamps = PmdTimeStampUtils.getTimeStamps(previousFrameTimeStamp = frame.previousTimeStamp, frameTimeStamp = frame.timeStamp, samplesSize = samplesSize, frame.sampleRate)
            var timeStampIndex = 0

            while (offset < frame.dataContent.size) {
                val seenBand1 = GnssSatelliteSummary(
                    gpsNbrOfSat = frame.dataContent[0].toUByte(),
                    gpsMaxSnr = frame.dataContent[1].toUByte(),
                    glonassNbrOfSat = frame.dataContent[2].toUByte(),
                    glonassMaxSnr = frame.dataContent[3].toUByte(),
                    galileoNbrOfSat = frame.dataContent[4].toUByte(),
                    galileoMaxSnr = frame.dataContent[5].toUByte(),
                    beidouNbrOfSat = frame.dataContent[6].toUByte(),
                    beidouMaxSnr = frame.dataContent[7].toUByte(),
                    nbrOfSat = frame.dataContent[8].toUByte(),
                    snrTop5Avg = frame.dataContent[9].toUByte()
                )
                offset += 10
                val usedBand1 = GnssSatelliteSummary(
                    gpsNbrOfSat = frame.dataContent[10].toUByte(),
                    gpsMaxSnr = frame.dataContent[11].toUByte(),
                    glonassNbrOfSat = frame.dataContent[12].toUByte(),
                    glonassMaxSnr = frame.dataContent[13].toUByte(),
                    galileoNbrOfSat = frame.dataContent[14].toUByte(),
                    galileoMaxSnr = frame.dataContent[15].toUByte(),
                    beidouNbrOfSat = frame.dataContent[16].toUByte(),
                    beidouMaxSnr = frame.dataContent[17].toUByte(),
                    nbrOfSat = frame.dataContent[18].toUByte(),
                    snrTop5Avg = frame.dataContent[19].toUByte()
                )
                offset += 10
                val seenBand2 = GnssSatelliteSummary(
                    gpsNbrOfSat = frame.dataContent[20].toUByte(),
                    gpsMaxSnr = frame.dataContent[21].toUByte(),
                    glonassNbrOfSat = frame.dataContent[22].toUByte(),
                    glonassMaxSnr = frame.dataContent[23].toUByte(),
                    galileoNbrOfSat = frame.dataContent[24].toUByte(),
                    galileoMaxSnr = frame.dataContent[25].toUByte(),
                    beidouNbrOfSat = frame.dataContent[26].toUByte(),
                    beidouMaxSnr = frame.dataContent[27].toUByte(),
                    nbrOfSat = frame.dataContent[28].toUByte(),
                    snrTop5Avg = frame.dataContent[29].toUByte()
                )
                offset += 10
                val usedBand2 = GnssSatelliteSummary(
                    gpsNbrOfSat = frame.dataContent[30].toUByte(),
                    gpsMaxSnr = frame.dataContent[31].toUByte(),
                    glonassNbrOfSat = frame.dataContent[32].toUByte(),
                    glonassMaxSnr = frame.dataContent[33].toUByte(),
                    galileoNbrOfSat = frame.dataContent[34].toUByte(),
                    galileoMaxSnr = frame.dataContent[35].toUByte(),
                    beidouNbrOfSat = frame.dataContent[36].toUByte(),
                    beidouMaxSnr = frame.dataContent[37].toUByte(),
                    nbrOfSat = frame.dataContent[38].toUByte(),
                    snrTop5Avg = frame.dataContent[39].toUByte()
                )
                offset += 10
                val maxSnr = parseFrameDataField(frame.dataContent.sliceArray(offset..offset), PmdDataFieldEncoding.UNSIGNED_INT) as UInt
                offset += 1

                val sample = GnssSatelliteSummarySample(
                    timeStamp = timeStamps[timeStampIndex],
                    seenGnssSatelliteSummaryBand1 = seenBand1,
                    usedGnssSatelliteSummaryBand1 = usedBand1,
                    seenGnssSatelliteSummaryBand2 = seenBand2,
                    usedGnssSatelliteSummaryBand2 = usedBand2,
                    maxSnr = maxSnr
                )
                locationData.gnssLocationDataSamples.add(sample)
                timeStampIndex++
            }
            return locationData
        }

        private fun dataFromRawType3(frame: PmdDataFrame): GnssLocationData {
            val locationData = GnssLocationData()
            var offset = 0

            while (offset < frame.dataContent.size) {
                val measurementPeriod = parseFrameDataField(frame.dataContent.sliceArray(offset..(offset + 3)), PmdDataFieldEncoding.UNSIGNED_INT) as UInt
                offset += 4
                val messageLength = parseFrameDataField(frame.dataContent.sliceArray(offset..(offset + 1)), PmdDataFieldEncoding.UNSIGNED_INT) as UInt
                offset += 2
                val statusFlags = parseFrameDataField(frame.dataContent.sliceArray(offset..offset), PmdDataFieldEncoding.UNSIGNED_BYTE) as UByte
                offset += 1
                val nmeaMessage = String(frame.dataContent.sliceArray(offset until (offset + messageLength.toInt())))
                offset += messageLength.toInt()
                val sample = GnssGpsNMEASample(timeStamp = frame.timeStamp, measurementPeriod = measurementPeriod, messageLength = messageLength, statusFlags = statusFlags, nmeaMessage = nmeaMessage)
                locationData.gnssLocationDataSamples.add(sample)
            }
            return locationData
        }
    }
}