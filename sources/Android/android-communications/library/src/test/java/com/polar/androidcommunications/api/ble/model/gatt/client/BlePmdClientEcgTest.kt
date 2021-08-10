package com.polar.androidcommunications.api.ble.model.gatt.client

import org.junit.Assert
import org.junit.Test

class BlePmdClientEcgTest {
    @Test
    fun test_Ecg_DataSample_type0() {
        // Arrange
        // HEX: 02 08 FF 02 80 00
        // index    type                                            data:
        // 0
        val type: Byte = 0
        // 1..3     uVolts                                          02 80 FF (-32766)
        val ecgValue1 = -32766
        // 4..6     uVolts                                          02 80 00 (32770)
        val ecgValue2 = 32770
        val measurementFrame = byteArrayOf(
            0x02.toByte(),
            0x80.toByte(),
            0xFF.toByte(),
            0x02.toByte(),
            0x80.toByte(),
            0x00.toByte()
        )
        val timeStamp = Long.MAX_VALUE

        // Act
        val ecgData = BlePMDClient.EcgData(type, measurementFrame, timeStamp)

        // Assert
        Assert.assertEquals(timeStamp, ecgData.ecgSamples[0].timeStamp)
        Assert.assertEquals(type.toLong(), ecgData.ecgSamples[0].type.numVal.toLong())
        Assert.assertEquals(ecgValue1.toLong(), ecgData.ecgSamples[0].microVolts.toLong())
        Assert.assertEquals(ecgValue2.toLong(), ecgData.ecgSamples[1].microVolts.toLong())
        Assert.assertEquals(2, ecgData.ecgSamples.size.toLong())
    }

    @Test
    fun test_Ecg_DataSample_type1() {
        // Arrange
        // HEX:  80 02 FF 02 80 00
        // index    type                                            data:
        // 0
        val type: Byte = 1
        // 1..2     uVolts                                          80 02 (2)
        val ecgValue1 = 2
        // 3                                                        FF
        val sampleBit1 = 0xFF
        val skinContact1 = (sampleBit1 and 0x06 shr 1).toByte()
        val contactImpedance1 = (sampleBit1 and 0x18 shr 3).toByte()
        // 4..5     uVolts                                          02 80 (640)
        val ecgValue2 = 640
        // 6                                                        00
        val sampleBit2 = 0x00
        val skinContact2 = (sampleBit2 and 0x06 shr 1).toByte()
        val contactImpedance2 = (sampleBit2 and 0x18 shr 3).toByte()
        val measurementFrame = byteArrayOf(
            0x02.toByte(),
            0x80.toByte(),
            0xFF.toByte(),
            0x80.toByte(),
            0x02.toByte(),
            0x00.toByte()
        )
        val timeStamp = Long.MAX_VALUE

        // Act
        val ecgData = BlePMDClient.EcgData(type, measurementFrame, timeStamp)

        // Assert
        Assert.assertEquals(timeStamp, ecgData.ecgSamples[0].timeStamp)
        Assert.assertEquals(type.toLong(), ecgData.ecgSamples[0].type.numVal.toLong())
        Assert.assertEquals(ecgValue1.toLong(), ecgData.ecgSamples[0].microVolts.toLong())
        Assert.assertTrue(ecgData.ecgSamples[0].overSampling)
        Assert.assertEquals(skinContact1.toLong(), ecgData.ecgSamples[0].skinContactBit.toLong())
        Assert.assertEquals(
            contactImpedance1.toLong(),
            ecgData.ecgSamples[0].contactImpedance.toLong()
        )
        Assert.assertFalse(ecgData.ecgSamples[1].overSampling)
        Assert.assertEquals(skinContact2.toLong(), ecgData.ecgSamples[1].skinContactBit.toLong())
        Assert.assertEquals(
            contactImpedance2.toLong(),
            ecgData.ecgSamples[1].contactImpedance.toLong()
        )
        Assert.assertEquals(ecgValue2.toLong(), ecgData.ecgSamples[1].microVolts.toLong())
        Assert.assertEquals(2, ecgData.ecgSamples.size.toLong())
    }

    @Test
    fun test_Ecg_DataSample_type2() {
        // Arrange
        // HEX:  80 80 FC FF FF 03
        // index    type                                            data:
        // 0
        val type: Byte = 2
        // 1..3     uVolts                                          80 80 FC (32896)
        val ecgValue1 = 32896
        // 3                                                        FC
        val sampleBit1 = 0xFC
        val ecgDataTag1 = (sampleBit1 and 0x1C shr 2)
        val paceDataTag1 = (sampleBit1 and 0xE0 shr 5)
        // 4..6     uVolts                                          FF FF 03 (262143)
        val ecgValue2 = 262143
        // 6                                                        00
        val sampleBit2 = 0x00
        val ecgDataTag2 = (sampleBit2 and 0x1C shr 2)
        val paceDataTag2 = (sampleBit2 and 0xE0 shr 5)
        val measurementFrame = byteArrayOf(
            0x80.toByte(),
            0x80.toByte(),
            0xFC.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0x03.toByte()
        )
        val timeStamp = Long.MAX_VALUE

        // Act
        val ecgData = BlePMDClient.EcgData(type, measurementFrame, timeStamp)

        // Assert
        Assert.assertEquals(timeStamp, ecgData.ecgSamples[0].timeStamp)
        Assert.assertEquals(type.toLong(), ecgData.ecgSamples[0].type.numVal.toLong())
        Assert.assertEquals(ecgValue1.toLong(), ecgData.ecgSamples[0].microVolts.toLong())
        Assert.assertEquals(ecgDataTag1.toLong(), ecgData.ecgSamples[0].ecgDataTag.toLong())
        Assert.assertEquals(paceDataTag1.toLong(), ecgData.ecgSamples[0].paceDataTag.toLong())
        Assert.assertEquals(ecgValue2.toLong(), ecgData.ecgSamples[1].microVolts.toLong())
        Assert.assertEquals(ecgDataTag2.toLong(), ecgData.ecgSamples[1].ecgDataTag.toLong())
        Assert.assertEquals(paceDataTag2.toLong(), ecgData.ecgSamples[1].paceDataTag.toLong())
        Assert.assertEquals(2, ecgData.ecgSamples.size.toLong())
    }
}