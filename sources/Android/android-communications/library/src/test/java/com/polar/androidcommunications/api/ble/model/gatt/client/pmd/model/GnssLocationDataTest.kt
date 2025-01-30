package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.androidcommunications.testrules.BleLoggerTestRule
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import java.lang.Double.longBitsToDouble
import java.lang.Float.intBitsToFloat

internal class GnssLocationDataTest {

    @Rule
    @JvmField
    val bleLoggerTestRule = BleLoggerTestRule()

    @Test
    fun `process raw location data type 0`() {
        // Arrange
        // HEX: 0A 00 94 35 77 00 00 00 00 00
        // index                                                   data:
        // 0        type                                           0A (Location)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        val timeStamp = 2000000000uL
        // 10       frame type                                     00 (raw, type 0)
        val locationDataFrameHeader = byteArrayOf(
            0x0A.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(),
        )
        val previousTimeStamp = 100uL
        // HEX: 00 00 00 00 4C E2 81 42 00 00 00 00 FB 8F CB 41 E5 07 0A 01 00 14 8D 03 FF FF FF 0F E1 FA C9 42 CD CC CC 3D 48 61 8B 42 00 00 02 00 03 8D FF FF 01 FF FF
        // index    type                                data
        // 0..7    Latitude                             00 00 00 00 4C E2 81 42 (0x4281E24C)
        // 8..15   Longitude                            00 00 00 00 FB 8F CB 41 (0x41cb8ffb)
        // 16..19  Date                                 E5 07 0A 01 (year = 2021, month = 10, date = 1)
        // 20..23  Time                                 00 2C 47 0C (0x0C472C00 => hours = 11 , minutes = 14, seconds = 34, milliseconds = 000 trusted = true)
        // 24..27  Cumulative distance                  FF FF FF 0F (0x0FFFFFFF = 268435455)
        // 28..31  Speed                                E1 FA C9 42 (0x42C9FAE1 => 100.99km/h)
        // 32..35  Acceleration Speed                   CD CC CC 3D (0x3DCCCCCD => 0.1)
        // 36..39  Coordinate Speed                     48 61 8B 42 (0x428B6148 => 69.69)
        // 40..43  Acceleration Speed Factor            00 00 02 00 (0x00000200)
        // 44..45  Course                               03 8D (0x8CA0 = 360.99 degrees )
        // 46..47  Knots speed                          FF FF (0xFFFF = 655.35 )
        // 48      Fix                                  01 (true)
        // 49      Speed flag                           FF
        // 50      Fusion state                         FF
        val expectedSamplesSize = 1
        val latitude = longBitsToDouble(0x4281e24c00000000)
        val longitude = longBitsToDouble(0x41cb8ffb00000000)
        val date = "2021-10-01T11:14:34.000"
        val cumulativeDistance = 26843545.5
        val speed = intBitsToFloat(0x42C9FAE1)
        val accelerationSpeed = intBitsToFloat(0x3DCCCCCD)
        val coordinateSpeed = intBitsToFloat(0x428B6148)
        val accelerationSpeedFactor = intBitsToFloat(0x00000200)
        val course = 360.99f
        val gpsChipSpeed = 655.35f
        val speedFlag = -1
        val fusionState = 0xFFu

        val locationDataFrameContent = byteArrayOf(
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x4C.toByte(), 0xE2.toByte(), 0x81.toByte(), 0x42.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xFB.toByte(), 0x8F.toByte(), 0xCB.toByte(), 0x41.toByte(),
            0xE5.toByte(), 0x07.toByte(), 0x0A.toByte(), 0x01.toByte(),
            0x00.toByte(), 0x2C.toByte(), 0x47.toByte(), 0x0C.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x0F.toByte(), 0xE1.toByte(), 0xFA.toByte(), 0xC9.toByte(), 0x42.toByte(),
            0xCD.toByte(), 0xCC.toByte(), 0xCC.toByte(), 0x3D.toByte(), 0x48.toByte(), 0x61.toByte(), 0x8B.toByte(), 0x42.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x02.toByte(), 0x00.toByte(), 0x03.toByte(), 0x8D.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0x01.toByte(), 0xFF.toByte(), 0xFF.toByte()
        )

        val factor = 1.0f
        val dataFrame = PmdDataFrame(
            data = locationDataFrameHeader + locationDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }

        // Act
        val gnssData = GnssLocationData.parseDataFromDataFrame(dataFrame)
        Assert.assertEquals(expectedSamplesSize, gnssData.gnssLocationDataSamples.size)
        Assert.assertTrue(gnssData.gnssLocationDataSamples[0] is GnssLocationData.GnssCoordinateSample)
        Assert.assertEquals(latitude, (gnssData.gnssLocationDataSamples[0] as GnssLocationData.GnssCoordinateSample).latitude, 0.00001)
        Assert.assertEquals(longitude, (gnssData.gnssLocationDataSamples[0] as GnssLocationData.GnssCoordinateSample).longitude, 0.00001)
        Assert.assertEquals(date, (gnssData.gnssLocationDataSamples[0] as GnssLocationData.GnssCoordinateSample).date)
        Assert.assertEquals(cumulativeDistance, (gnssData.gnssLocationDataSamples[0] as GnssLocationData.GnssCoordinateSample).cumulativeDistance, 0.00001)
        Assert.assertEquals(speed, (gnssData.gnssLocationDataSamples[0] as GnssLocationData.GnssCoordinateSample).speed)
        Assert.assertEquals(accelerationSpeed, (gnssData.gnssLocationDataSamples[0] as GnssLocationData.GnssCoordinateSample).usedAccelerationSpeed)
        Assert.assertEquals(coordinateSpeed, (gnssData.gnssLocationDataSamples[0] as GnssLocationData.GnssCoordinateSample).coordinateSpeed)
        Assert.assertEquals(accelerationSpeedFactor, (gnssData.gnssLocationDataSamples[0] as GnssLocationData.GnssCoordinateSample).accelerationSpeedFactor, 0.00001f)
        Assert.assertEquals(course, (gnssData.gnssLocationDataSamples[0] as GnssLocationData.GnssCoordinateSample).course)
        Assert.assertEquals(gpsChipSpeed, (gnssData.gnssLocationDataSamples[0] as GnssLocationData.GnssCoordinateSample).gpsChipSpeed)
        Assert.assertTrue((gnssData.gnssLocationDataSamples[0] as GnssLocationData.GnssCoordinateSample).fix)
        Assert.assertEquals(speedFlag, (gnssData.gnssLocationDataSamples[0] as GnssLocationData.GnssCoordinateSample).speedFlag)
        Assert.assertEquals(fusionState, (gnssData.gnssLocationDataSamples[0] as GnssLocationData.GnssCoordinateSample).fusionState)

        Assert.assertEquals(timeStamp, (gnssData.gnssLocationDataSamples[0] as GnssLocationData.GnssCoordinateSample).timeStamp)
    }

    @Test
    fun `process raw location data type 1`() {
        // Arrange
        // HEX: 0A 00 94 35 77 00 00 00 00 01
        // index                                                   data:
        // 0        type                                           0A (Location)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        val timeStamp = 2000000000uL
        // 10       frame type                                     01 (raw, type 1)
        val locationDataFrameHeader = byteArrayOf(
            0x0A.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x01.toByte(),
        )
        val previousTimeStamp = 100uL
        // HEX: F5 07 00 80 FF 07
        // index    type                                data
        // 0..1:    Dilution                            F5 07 (0x7F5 = 20.37)
        // 2..3:    Altitude                            00 80 (0xFFFF = -32768)
        // 4:       Number of satellites                FF
        // 5:       Fix                                 07
        val expectedSamplesSize = 1
        val dilution = 20.37f
        val altitude = -32768
        val numberOfSatellites = 255u

        val locationDataFrameContent = byteArrayOf(0xF5.toByte(), 0x07.toByte(), 0x00.toByte(), 0x80.toByte(), 0xFF.toByte(), 0x07.toByte())

        val factor = 1.0f
        val dataFrame = PmdDataFrame(
            data = locationDataFrameHeader + locationDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }


        // Act
        val gnssData = GnssLocationData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(expectedSamplesSize, gnssData.gnssLocationDataSamples.size)
        Assert.assertEquals(dilution, (gnssData.gnssLocationDataSamples[0] as GnssLocationData.GnssSatelliteDilutionSample).dilution)
        Assert.assertEquals(altitude, (gnssData.gnssLocationDataSamples[0] as GnssLocationData.GnssSatelliteDilutionSample).altitude)
        Assert.assertEquals(numberOfSatellites, (gnssData.gnssLocationDataSamples[0] as GnssLocationData.GnssSatelliteDilutionSample).numberOfSatellites)
        Assert.assertTrue((gnssData.gnssLocationDataSamples[0] as GnssLocationData.GnssSatelliteDilutionSample).fix)
        Assert.assertEquals(timeStamp, (gnssData.gnssLocationDataSamples[0] as GnssLocationData.GnssSatelliteDilutionSample).timeStamp)
    }

    @Test
    fun `process raw location data type 2`() {
        // Arrange
        // HEX: 0A 00 94 35 77 00 00 00 00 02
        // index                                                   data:
        // 0        type                                           0A (Location)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        val timeStamp = 2000000000uL
        // 10       frame type                                     02 (raw, type 2)
        val locationDataFrameHeader = byteArrayOf(
            0x0A.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x02.toByte(),
        )
        val previousTimeStamp = 100uL
        // HEX: 00 01 02 03 04 05 06 07 08 09
        //      FF EF 80 00 01 02 03 04 00 00
        //      00 01 02 03 04 05 06 07 08 09
        //      FF EF 80 00 01 02 03 04 00 00
        //      FF
        // index    type                                data
        // 0..9:    Seen satellite summary              00 01 02 03 04 05 06 07 08 09
        // 10..19:   Used satellite summary             FF EF 80 00 01 02 03 04 00 00
        // 20..29:    Seen satellite summary            00 01 02 03 04 05 06 07 08 09
        // 30..39:   Used satellite summary             FF EF 80 00 01 02 03 04 00 00
        // 40:      MaxSnr                              FF
        val expectedSamplesSize = 1
        val seenBand1 = GnssLocationData.GnssSatelliteSummary(
            gpsNbrOfSat = 0u,
            gpsMaxSnr = 1u,
            glonassNbrOfSat = 2u,
            glonassMaxSnr = 3u,
            galileoNbrOfSat = 4u,
            galileoMaxSnr = 5u,
            beidouNbrOfSat = 6u,
            beidouMaxSnr = 7u,
            nbrOfSat = 8u,
            snrTop5Avg = 9u
        )
        val usedBand1 = GnssLocationData.GnssSatelliteSummary(
            gpsNbrOfSat = 0xFFu,
            gpsMaxSnr = 0xEFu,
            glonassNbrOfSat = 0x80u,
            glonassMaxSnr = 0x00u,
            galileoNbrOfSat = 1u,
            galileoMaxSnr = 2u,
            beidouNbrOfSat = 3u,
            beidouMaxSnr = 4u,
            nbrOfSat = 0u,
            snrTop5Avg = 0u
        )
        val seenBand2 = GnssLocationData.GnssSatelliteSummary(
            gpsNbrOfSat = 0u,
            gpsMaxSnr = 1u,
            glonassNbrOfSat = 2u,
            glonassMaxSnr = 3u,
            galileoNbrOfSat = 4u,
            galileoMaxSnr = 5u,
            beidouNbrOfSat = 6u,
            beidouMaxSnr = 7u,
            nbrOfSat = 8u,
            snrTop5Avg = 9u
        )

        val usedBand2 = GnssLocationData.GnssSatelliteSummary(
            gpsNbrOfSat = 0xFFu,
            gpsMaxSnr = 0xEFu,
            glonassNbrOfSat = 0x80u,
            glonassMaxSnr = 0x00u,
            galileoNbrOfSat = 1u,
            galileoMaxSnr = 2u,
            beidouNbrOfSat = 3u,
            beidouMaxSnr = 4u,
            nbrOfSat = 0u,
            snrTop5Avg = 0u
        )
        val maxSnr = 0xFFu

        val locationDataFrameContent = byteArrayOf(
            0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0x09.toByte(),
            0xFF.toByte(), 0xEF.toByte(), 0x80.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0x09.toByte(),
            0xFF.toByte(), 0xEF.toByte(), 0x80.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x00.toByte(), 0x00.toByte(),
            0xFF.toByte()
        )

        val factor = 1.0f
        val dataFrame = PmdDataFrame(
            data = locationDataFrameHeader + locationDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }

        // Act
        val gnssData = GnssLocationData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(expectedSamplesSize, gnssData.gnssLocationDataSamples.size)
        Assert.assertEquals(seenBand1, (gnssData.gnssLocationDataSamples[0] as GnssLocationData.GnssSatelliteSummarySample).seenGnssSatelliteSummaryBand1)
        Assert.assertEquals(usedBand1, (gnssData.gnssLocationDataSamples[0] as GnssLocationData.GnssSatelliteSummarySample).usedGnssSatelliteSummaryBand1)
        Assert.assertEquals(seenBand2, (gnssData.gnssLocationDataSamples[0] as GnssLocationData.GnssSatelliteSummarySample).seenGnssSatelliteSummaryBand2)
        Assert.assertEquals(usedBand2, (gnssData.gnssLocationDataSamples[0] as GnssLocationData.GnssSatelliteSummarySample).usedGnssSatelliteSummaryBand2)
        Assert.assertEquals(maxSnr, (gnssData.gnssLocationDataSamples[0] as GnssLocationData.GnssSatelliteSummarySample).maxSnr)
        Assert.assertEquals(timeStamp, (gnssData.gnssLocationDataSamples[0] as GnssLocationData.GnssSatelliteSummarySample).timeStamp)
    }

    @Test
    fun `process raw location data type 3`() {
        // Arrange
        // HEX: 0A 00 94 35 77 00 00 00 00 03
        // index                                                   data:
        // 0        type                                           0A (Location)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        val timeStamp = 2000000000uL
        // 10       frame type                                     03 (raw, type 3)
        val locationDataFrameHeader = byteArrayOf(
            0x0A.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x03.toByte(),
        )
        val previousTimeStamp = 100uL

        // HEX: E8 03 00 00 1C 00 00 1A 80 47 50 41 41 4D 2C 41 2C 41 2C 30 2E 31 30 2C 4E 2C 57 50 54 4E 4D 45 2A 33 32
        // index    type                                data
        // 0..3:    Measurement period                  00 00 1C 00 (0x001C0000 = 1835008)
        // 4..5:    NMEA Message length                 1A 00  (0x1A = 26)
        // 6:       Status flags                        80
        // 7..      NMEA message                        47 50 41 41 4D 2C 41 2C 41 2C 30 2E 31 30 2C 4E 2C 57 50 54 4E 4D 45 2A 33 32
        val expectedSamplesSize = 1
        val measurementPeriod = 1835008u
        val nmeaMessageLength = 26u
        val statusFlags = 0x80u.toUByte()
        val nmeaMessage = "GPAAM,A,A,0.10,N,WPTNME*32"
        val locationDataFrameContent = byteArrayOf(
            0x00.toByte(), 0x00.toByte(), 0x1C.toByte(), 0x00.toByte(), 0x1A.toByte(), 0x00.toByte(), 0x80.toByte(), 0x47.toByte(),
            0x50.toByte(), 0x41.toByte(), 0x41.toByte(), 0x4D.toByte(), 0x2C.toByte(), 0x41.toByte(), 0x2C.toByte(), 0x41.toByte(),
            0x2C.toByte(), 0x30.toByte(), 0x2E.toByte(), 0x31.toByte(), 0x30.toByte(), 0x2C.toByte(), 0x4E.toByte(), 0x2C.toByte(),
            0x57.toByte(), 0x50.toByte(), 0x54.toByte(), 0x4E.toByte(), 0x4D.toByte(), 0x45.toByte(), 0x2A.toByte(), 0x33.toByte(),
            0x32.toByte()
        )

        val factor = 1.0f
        val dataFrame = PmdDataFrame(
            data = locationDataFrameHeader + locationDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }

        // Act
        val gnssData = GnssLocationData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(expectedSamplesSize, gnssData.gnssLocationDataSamples.size)
        Assert.assertEquals(measurementPeriod, (gnssData.gnssLocationDataSamples[0] as GnssLocationData.GnssGpsNMEASample).measurementPeriod)
        Assert.assertEquals(nmeaMessageLength, (gnssData.gnssLocationDataSamples[0] as GnssLocationData.GnssGpsNMEASample).messageLength)
        Assert.assertEquals(statusFlags, (gnssData.gnssLocationDataSamples[0] as GnssLocationData.GnssGpsNMEASample).statusFlags)
        Assert.assertEquals(nmeaMessage, (gnssData.gnssLocationDataSamples[0] as GnssLocationData.GnssGpsNMEASample).nmeaMessage)
        Assert.assertEquals(timeStamp, (gnssData.gnssLocationDataSamples[0] as GnssLocationData.GnssGpsNMEASample).timeStamp)
    }
}