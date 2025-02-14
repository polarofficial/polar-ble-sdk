package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.androidcommunications.testrules.BleLoggerTestRule
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

internal class EcgDataTest {
    @Rule
    @JvmField
    val bleLoggerTestRule = BleLoggerTestRule()

    @Test
    fun `process raw ecg data type 0`() {
        // Arrange
        // HEX: 00 00 94 35 77 00 00 00 00 00
        // index                                                   data:
        // 0        type                                           00 (Ecg)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        val timeStamp = 2000000000uL
        // 10       frame type                                     00 (raw, type 0)
        val ecgDataFrameHeader = byteArrayOf(
            0x00.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(),
        )
        val previousTimeStamp = 100uL
        // HEX: 02 08 FF 02 80 00
        // index    type                                            data:
        // 0..2     uVolts                                          02 80 FF (-32766)
        val ecgValue1 = -32766
        // 3..4     uVolts                                          02 80 00 (32770)
        val ecgValue2 = 32770
        val ecgDataFrameContent = byteArrayOf(0x02.toByte(), 0x80.toByte(), 0xFF.toByte(), 0x02.toByte(), 0x80.toByte(), 0x00.toByte())

        val factor = 1.0f
        val dataFrame = PmdDataFrame(
            data = ecgDataFrameHeader + ecgDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }

        // Act
        val ecgData = EcgData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(ecgValue1, (ecgData.ecgSamples[0] as EcgData.EcgSample).microVolts)
        Assert.assertEquals(ecgValue2, (ecgData.ecgSamples[1] as EcgData.EcgSample).microVolts)

        Assert.assertEquals(2, ecgData.ecgSamples.size.toLong())

        Assert.assertEquals(timeStamp, (ecgData.ecgSamples[1] as EcgData.EcgSample).timeStamp)
    }

    @Test
    fun `process raw ecg data type 1`() {
        // Arrange
        // HEX: 00 00 94 35 77 00 00 00 00 01
        // index                                                   data:
        // 0        type                                           00 (Ecg)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        // 10       frame type                                     01 (raw, type 1)
        val ecgDataFrameHeader = byteArrayOf(
            0x00.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x01.toByte(),
        )
        val previousTimeStamp = 100uL
        // HEX:  80 02 FF 02 80 00
        // index    type                                            data:
        // 0..1     uVolts                                          80 02 (2)
        val ecgValue1 = 2
        // 2                                                        FF
        val sampleBit1 = 0xFF
        val skinContact1 = (sampleBit1 and 0x06 shr 1).toByte()
        val contactImpedance1 = (sampleBit1 and 0x18 shr 3).toByte()
        // 3..4     uVolts                                          02 80 (640)
        val ecgValue2 = 640
        // 5                                                        00
        val sampleBit2 = 0x00
        val skinContact2 = (sampleBit2 and 0x06 shr 1).toByte()
        val contactImpedance2 = (sampleBit2 and 0x18 shr 3).toByte()
        val ecgDataFrameContent = byteArrayOf(0x02.toByte(), 0x80.toByte(), 0xFF.toByte(), 0x80.toByte(), 0x02.toByte(), 0x00.toByte())

        val factor = 1.0f
        val dataFrame = PmdDataFrame(
            data = ecgDataFrameHeader + ecgDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }

        // Act
        val ecgData = EcgData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(ecgValue1.toLong(), (ecgData.ecgSamples[0] as EcgData.EcgSample).microVolts.toLong())
        Assert.assertTrue((ecgData.ecgSamples[0] as EcgData.EcgSample).overSampling)
        Assert.assertEquals(skinContact1.toLong(), (ecgData.ecgSamples[0] as EcgData.EcgSample).skinContactBit.toLong())
        Assert.assertEquals(contactImpedance1.toLong(), (ecgData.ecgSamples[0] as EcgData.EcgSample).contactImpedance.toLong())
        Assert.assertFalse((ecgData.ecgSamples[1] as EcgData.EcgSample).overSampling)
        Assert.assertEquals(skinContact2.toLong(), (ecgData.ecgSamples[1] as EcgData.EcgSample).skinContactBit.toLong())
        Assert.assertEquals(contactImpedance2.toLong(), (ecgData.ecgSamples[1] as EcgData.EcgSample).contactImpedance.toLong())
        Assert.assertEquals(ecgValue2.toLong(), (ecgData.ecgSamples[1] as EcgData.EcgSample).microVolts.toLong())
        Assert.assertEquals(2, ecgData.ecgSamples.size.toLong())

        Assert.assertEquals(dataFrame.timeStamp, (ecgData.ecgSamples[1] as EcgData.EcgSample).timeStamp)
    }

    @Test
    fun `process raw ecg data type 2`() {
        // Arrange
        // HEX: 00 00 94 35 77 00 00 00 00 02
        // index                                                   data:
        // 0        type                                           00 (Ecg)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        val timeStamp = 2000000000uL
        // 10       frame type                                     02 (raw, type 2)
        val ecgDataFrameHeader = byteArrayOf(
            0x00.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x02.toByte(),
        )
        val previousTimeStamp = 100uL
        // HEX: 80 80 FC FF FF 03
        // index    type                                            data:
        // 0..1     uVolts                                          80 80 (32896)
        val ecgValue1 = 32896
        // 2                                                        FC
        val sampleBit1 = 0xFC
        val ecgDataTag1 = (sampleBit1 and 0x1C shr 2)
        val paceDataTag1 = (sampleBit1 and 0xE0 shr 5)
        // 3..4     uVolts                                          FF FF (262143)
        val ecgValue2 = 262143
        // 5                                                        03
        val sampleBit2 = 0x03
        val ecgDataTag2 = (sampleBit2 and 0x1C shr 2)
        val paceDataTag2 = (sampleBit2 and 0xE0 shr 5)
        val ecgDataFrameContent = byteArrayOf(0x80.toByte(), 0x80.toByte(), 0xFC.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x03.toByte())

        val factor = 1.0f
        val dataFrame = PmdDataFrame(
            data = ecgDataFrameHeader + ecgDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }

        // Act
        val ecgData = EcgData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(ecgValue1.toLong(), (ecgData.ecgSamples[0] as EcgData.EcgSample).microVolts.toLong())
        Assert.assertEquals(ecgDataTag1.toLong(), (ecgData.ecgSamples[0] as EcgData.EcgSample).ecgDataTag.toLong())
        Assert.assertEquals(paceDataTag1.toLong(), (ecgData.ecgSamples[0] as EcgData.EcgSample).paceDataTag.toLong())
        Assert.assertEquals(ecgValue2.toLong(), (ecgData.ecgSamples[1] as EcgData.EcgSample).microVolts.toLong())
        Assert.assertEquals(ecgDataTag2.toLong(), (ecgData.ecgSamples[1] as EcgData.EcgSample).ecgDataTag.toLong())
        Assert.assertEquals(paceDataTag2.toLong(), (ecgData.ecgSamples[1] as EcgData.EcgSample).paceDataTag.toLong())
        Assert.assertEquals(2, ecgData.ecgSamples.size.toLong())

        Assert.assertEquals(timeStamp, (ecgData.ecgSamples[1] as EcgData.EcgSample).timeStamp)
    }

    @Test
    fun `process raw ecg data type 3`() {
        // Arrange
        // HEX: 00 00 94 35 77 00 00 00 00 80
        // index                                                   data:
        // 0        type                                           00 (Ecg)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        // 10       frame type                                     03 (raw, type 3)
        val ecgDataFrameHeader = byteArrayOf(
            0x00.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x03.toByte(),
        )
        val previousTimeStamp = 100uL
        // HEX:  00 12 03 11 10 04 00 02 80 F0 11 10 04 FF
        // index    type                                            data:
        // 0..2     ECG                                             00 12 03 (201216)
        val ecgValue1 = 201216
        // 3..4                                                     11 10 04 (266257)
        val data1 = 266257
        // 5        Status                                          00
        val status1 = 0.toUByte()
        // 6..8     ECG                                             02 80 F0 (-1015806)
        val ecgValue2 = -1015806
        // 9..11                                                    11 10 04 (266257)
        val data2 = 266257
        // 12        Status                                         FF
        val status2 = 0xFF.toUByte()

        val ecgDataFrameContent = byteArrayOf(
            0x00.toByte(), 0x12.toByte(), 0x03.toByte(), 0x11.toByte(), 0x10.toByte(), 0x04.toByte(),
            0x00.toByte(), 0x02.toByte(), 0x80.toByte(), 0xF0.toByte(), 0x11.toByte(), 0x10.toByte(),
            0x04.toByte(), 0xFF.toByte()
        )
        val factor = 1.0f
        val dataFrame = PmdDataFrame(
            data = ecgDataFrameHeader + ecgDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }

        // Act
        val ecgData = EcgData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(ecgValue1, (ecgData.ecgSamples[0] as EcgData.EcgSampleFrameType3).data0)
        Assert.assertEquals(data1, (ecgData.ecgSamples[0] as EcgData.EcgSampleFrameType3).data1)
        Assert.assertEquals(status1, (ecgData.ecgSamples[0] as EcgData.EcgSampleFrameType3).status)
        Assert.assertEquals(ecgValue2, (ecgData.ecgSamples[1] as EcgData.EcgSampleFrameType3).data0)
        Assert.assertEquals(data2, (ecgData.ecgSamples[1] as EcgData.EcgSampleFrameType3).data1)
        Assert.assertEquals(status2, (ecgData.ecgSamples[1] as EcgData.EcgSampleFrameType3).status)
        Assert.assertEquals(2, ecgData.ecgSamples.size.toLong())

        Assert.assertEquals(dataFrame.timeStamp, (ecgData.ecgSamples[1] as EcgData.EcgSampleFrameType3).timeStamp)
    }
}