package com.polar.androidcommunications.api.ble.model.gatt.client

import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.api.ble.model.gatt.client.BleHrClient.Companion.HR_MEASUREMENT
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.*

class BleHrClientTest {

    @MockK
    private lateinit var txInterface: BleGattTxInterface

    private lateinit var bleHrClient: BleHrClient

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        bleHrClient = BleHrClient(txInterface)
        every { txInterface.isConnected() } returns true
        every { txInterface.setCharacteristicNotify(any(), any(), any()) } returns Unit
    }

    @Test
    fun `hr format uint8`() = runTest {
        //Arrange
        val characteristic: UUID = HR_MEASUREMENT
        val status = 0
        val notifying = true

        // HEX: 00 FF
        // index    type                                                data:
        // 0:       Flags field                             size 1:     0x00
        // Heart rate value format bit:     0 (uint8)
        // 1:       Heart Rate Measurement Value field      size 1:     0xFF
        val expectedHeartRate1 = 255
        val measurementFrame1 = byteArrayOf(0x00.toByte(), 0xFF.toByte())
        // HEX: 00 7F
        // index    type                                                data:
        // 0:       Flags field                             size 1:     0x00
        // Heart rate value format bit:     0 (uint8)
        // 1:       Heart Rate Measurement Value field      size 1:     0x7F
        val expectedHeartRate2 = 127
        val measurementFrame2 = byteArrayOf(0x00.toByte(), 0x7F.toByte())

        //Act
        val values = mutableListOf<BleHrClient.HrNotificationData>()
        val job = launch { bleHrClient.observeHrNotifications(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()
        bleHrClient.processServiceData(characteristic, measurementFrame1, status, notifying)
        bleHrClient.processServiceData(characteristic, measurementFrame2, status, notifying)
        testScheduler.advanceUntilIdle()
        job.cancel()

        //Assert
        assertEquals(2, values.size)
        assertEquals(expectedHeartRate1, values[0].hrValue)
        assertEquals(expectedHeartRate2, values[1].hrValue)
    }

    @Test
    fun `hr format uint16`() = runTest {
        //Arrange
        val characteristic: UUID = HR_MEASUREMENT
        val status = 0
        val notifying = true
        // HEX: 01 80 80
        // index    type                                                data:
        // 0:       Flags field                             size 1:     0x01
        // Heart rate value format bit      1 (uint16)
        // 1..2:    Heart Rate Measurement Value field      size 2:     0x80 0x80 (32896)
        val expectedHeartRate1 = 32896
        val measurementFrame1 = byteArrayOf(0x01.toByte(), 0x80.toByte(), 0x80.toByte())
        // HEX: 01 7F 7F
        // index    type                                                data:
        // 0:       Flags field                             size 1:     0x01
        // Heart rate value format bit      1 (uint16)
        // 1..2:    Heart Rate Measurement Value field      size 2:     0x7F 0x7F
        val expectedHeartRate2 = 32639
        val measurementFrame2 = byteArrayOf(0x01.toByte(), 0x7F.toByte(), 0x7F.toByte())

        //Act
        val values = mutableListOf<BleHrClient.HrNotificationData>()
        val job = launch { bleHrClient.observeHrNotifications(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()
        bleHrClient.processServiceData(characteristic, measurementFrame1, status, notifying)
        bleHrClient.processServiceData(characteristic, measurementFrame2, status, notifying)
        testScheduler.advanceUntilIdle()
        job.cancel()

        //Assert
        assertEquals(2, values.size)
        assertEquals(expectedHeartRate1, values[0].hrValue)
        assertEquals(expectedHeartRate2, values[1].hrValue)
    }

    @Test
    fun `sensor contact supported`() = runTest {
        //Arrange
        val characteristic: UUID = HR_MEASUREMENT
        val status = 0
        val notifying = true
        // HEX: 06 00
        // index    type                                                data:
        // 0:       Flags field                             size 1:     0x06
        // Sensor Contact Status bit        1
        // Sensor Contact Support bit       1 (Supported)
        val measurementFrame0 = byteArrayOf(0x06.toByte(), 0x00.toByte())
        // HEX: 04 00
        // index    type                                                data:
        // 0:       Flags field                             size 1:     0x04
        // Sensor Contact Status bit        0
        // Sensor Contact Support bit       1 (Supported)
        val measurementFrame1 = byteArrayOf(0x04.toByte(), 0x00.toByte())

        //Act
        val values = mutableListOf<BleHrClient.HrNotificationData>()
        val job = launch { bleHrClient.observeHrNotifications(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()
        bleHrClient.processServiceData(characteristic, measurementFrame0, status, notifying)
        bleHrClient.processServiceData(characteristic, measurementFrame1, status, notifying)
        testScheduler.advanceUntilIdle()
        job.cancel()

        //Assert
        assertEquals(2, values.size)
        assertTrue(values[0].sensorContact)
        assertTrue(values[0].sensorContactSupported)
        assertFalse(values[1].sensorContact)
        assertTrue(values[1].sensorContactSupported)
    }

    @Test
    fun `sensor contact not supported`() = runTest {
        //Arrange
        val characteristic: UUID = HR_MEASUREMENT
        val status = 0
        val notifying = true

        // HEX: 02 00
        // index    type                                                data:
        // 0:       Flags field                             size 1:     0x02
        // Sensor Contact Status bit        1
        // Sensor Contact Support bit       0 (Not supported)
        val measurementFrame1 = byteArrayOf(0x02.toByte(), 0x00.toByte())

        // HEX: 00 00
        // index    type                                                data:
        // 0:       Flags field                             size 1:     0x00
        // Sensor Contact Status bit        0
        // Sensor Contact Support bit       0 (Not supported)
        val measurementFrame2 = byteArrayOf(0x00.toByte(), 0x00.toByte())

        //Act
        val values = mutableListOf<BleHrClient.HrNotificationData>()
        val job = launch { bleHrClient.observeHrNotifications(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()
        bleHrClient.processServiceData(characteristic, measurementFrame1, status, notifying)
        bleHrClient.processServiceData(characteristic, measurementFrame2, status, notifying)
        testScheduler.advanceUntilIdle()
        job.cancel()

        //Assert
        assertEquals(2, values.size)
        assertFalse(values[0].sensorContactSupported)
        assertFalse(values[1].sensorContactSupported)
    }

    @Test
    fun `energy expended`() = runTest {
        //Arrange
        val characteristic: UUID = HR_MEASUREMENT
        val status = 0
        val notifying = true

        // HEX: 09 00 00 FF FF
        // index    type                                                data:
        // 0:       Flags field                             size 1:     0x09
        // Heart rate value format bit      1 (uint16)
        // Energy Expended Status bit       1 (Energy Expended field is present)
        // 1..2:    Heart Rate Measurement Value field      size 2:     0x00 0x00
        // 3..4:    Energy Expended field                   size 2:     0xFF 0xFF
        val expectedHeartRate1 = 0
        val energyExpended1 = 65535
        val measurementFrame1 =
            byteArrayOf(0x09.toByte(), 0x00.toByte(), 0x00.toByte(), 0xFF.toByte(), 0xFF.toByte())
        // HEX: 08 00 7F 80
        // index    type                                                data:
        // 0:       Flags field                             size 1:     0x08
        // Heart rate value format bit      0 (uint8)
        // Energy Expended Status bit       1 (Energy Expended field is present)
        // 1:       Heart Rate Measurement Value field      size 1:     0x00
        // 2..3:    Energy Expended field                   size 2:     0x7F 0x80
        val expectedHeartRate2 = 0
        val energyExpended2 = 32895
        val measurementFrame2 =
            byteArrayOf(0x08.toByte(), 0x00.toByte(), 0x7F.toByte(), 0x80.toByte())

        //Act
        val values = mutableListOf<BleHrClient.HrNotificationData>()
        val job = launch { bleHrClient.observeHrNotifications(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()
        bleHrClient.processServiceData(characteristic, measurementFrame1, status, notifying)
        bleHrClient.processServiceData(characteristic, measurementFrame2, status, notifying)
        testScheduler.advanceUntilIdle()
        job.cancel()

        //Assert
        assertEquals(2, values.size)
        assertEquals(expectedHeartRate1, values[0].hrValue)
        assertEquals(energyExpended1, values[0].energy)
        assertEquals(expectedHeartRate2, values[1].hrValue)
        assertEquals(energyExpended2, values[1].energy)
    }

    @Test
    fun `rr interval`() = runTest {
        //Arrange
        val characteristic: UUID = HR_MEASUREMENT
        val status = 0
        val notifying = true
        // HEX: 10 00 FF FF
        // index    type                                                data:
        // 0:       Flags field                             size 1:     0x10
        // Heart rate value format bit      0 (uint8)
        // RR-interval bit                  1 (RR-Interval values are present)
        // 1:       Heart Rate Measurement Value field      size 1:     0x00
        // 2..3:    RR-Interval Field                       size 2:     0xFF 0xFF
        val expectedHeartRate1 = 0
        val sample1ExpectedRRin1024Unit = 65535
        val sample1ExpectedRRinMsUnit = 63999
        val measurementFrame1 =
            byteArrayOf(0x10.toByte(), 0x00.toByte(), 0xFF.toByte(), 0xFF.toByte())
        // HEX: 11 00 00 FF FF 7F 80
        // index    type                                                data:
        // 0:       Flags field                             size 1:     0x11
        // Heart rate value format bit      1 (uint16)
        // RR-interval bit                  1 (RR-Interval values are present)
        // 1..2:    Heart Rate Measurement Value field      size 1:     0x00 0x00
        // 3..4:    RR-Interval Field                       size 2:     0xFF 0xFF
        // 5..6:    RR-Interval Field                       size 2:     0x7F 0x80
        val expectedHeartRate2 = 0
        val sample2ExpectedRRin1024Unit = 65535
        val sample2ExpectedRRinMsUnit = 63999
        val sample3ExpectedRRin1024Unit = 32895
        val sample3ExpectedRRinMsUnit = 32124
        val measurementFrame2 = byteArrayOf(
            0x11.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0x7F.toByte(),
            0x80.toByte()
        )

        // HEX: 10 00 00 00
        // index    type                                                data:
        // 0:       Flags field                             size 1:     0x10
        // Heart rate value format bit      0 (uint8)
        // RR-interval bit                  1 (RR-Interval values are present)
        // 1:       Heart Rate Measurement Value field      size 1:     0x00
        // 2..3:    RR-Interval Field                       size 2:     0x00 0x00
        val expectedHeartRate3 = 0
        val sample4ExpectedRRin1024Unit = 0
        val sample4ExpectedRRinMsUnit = 0
        val measurementFrame3 = byteArrayOf(
            0x11.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte()
        )

        //Act
        val values = mutableListOf<BleHrClient.HrNotificationData>()
        val job = launch { bleHrClient.observeHrNotifications(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()
        bleHrClient.processServiceData(characteristic, measurementFrame1, status, notifying)
        bleHrClient.processServiceData(characteristic, measurementFrame2, status, notifying)
        bleHrClient.processServiceData(characteristic, measurementFrame3, status, notifying)
        testScheduler.advanceUntilIdle()
        job.cancel()

        //Assert
        assertEquals(3, values.size)

        val hrData1 = values[0]
        assertEquals(expectedHeartRate1, hrData1.hrValue)
        assertEquals(1, hrData1.rrs.size)
        assertEquals(1, hrData1.rrsMs.size)
        assertTrue(hrData1.rrPresent)
        assertEquals(sample1ExpectedRRin1024Unit, hrData1.rrs[0])
        assertEquals(sample1ExpectedRRinMsUnit, hrData1.rrsMs[0])

        val hrData2 = values[1]
        assertEquals(expectedHeartRate2, hrData2.hrValue)
        assertEquals(2, hrData2.rrs.size)
        assertEquals(2, hrData2.rrsMs.size)
        assertTrue(hrData2.rrPresent)
        assertEquals(sample2ExpectedRRin1024Unit, hrData2.rrs[0])
        assertEquals(sample2ExpectedRRinMsUnit, hrData2.rrsMs[0])

        assertEquals(sample3ExpectedRRin1024Unit, hrData2.rrs[1])
        assertEquals(sample3ExpectedRRinMsUnit, hrData2.rrsMs[1])

        val hrData3 = values[2]
        assertEquals(expectedHeartRate3, hrData3.hrValue)
        assertEquals(1, hrData3.rrs.size)
        assertEquals(1, hrData3.rrsMs.size)
        assertTrue(hrData3.rrPresent)
        assertEquals(sample4ExpectedRRin1024Unit, hrData3.rrs[0])
        assertEquals(sample4ExpectedRRinMsUnit, hrData3.rrsMs[0])
    }
}