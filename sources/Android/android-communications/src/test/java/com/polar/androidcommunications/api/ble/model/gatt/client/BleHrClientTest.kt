package com.polar.androidcommunications.api.ble.model.gatt.client

import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.api.ble.model.gatt.client.BleHrClient.HR_MEASUREMENT
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.reactivex.rxjava3.subscribers.TestSubscriber
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
        every { txInterface.isConnected } returns true
        every { txInterface.setCharacteristicNotify(any(), any(), any(), any()) } returns Unit
    }

    @Test
    fun test_HrFormatUint8() {
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
        val result = bleHrClient.observeHrNotifications(true)
        val testObserver = TestSubscriber<BleHrClient.HrNotificationData>()
        result.subscribe(testObserver)
        bleHrClient.processServiceData(characteristic, measurementFrame1, status, notifying)
        bleHrClient.processServiceData(characteristic, measurementFrame2, status, notifying)

        //Assert
        testObserver.assertNoErrors()
        testObserver.assertValueCount(2)
        val hrData1 = testObserver.values()[0]
        assertEquals(expectedHeartRate1, hrData1.hrValue)
        val hrData2 = testObserver.values()[1]
        assertEquals(expectedHeartRate2, hrData2.hrValue)
    }

    @Test
    fun test_HrFormatUint16() {
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
        val result = bleHrClient.observeHrNotifications(true)
        val testObserver = TestSubscriber<BleHrClient.HrNotificationData>()
        result.subscribe(testObserver)
        bleHrClient.processServiceData(characteristic, measurementFrame1, status, notifying)
        bleHrClient.processServiceData(characteristic, measurementFrame2, status, notifying)

        //Assert
        testObserver.assertNoErrors()
        testObserver.assertValueCount(2)
        val hrData1 = testObserver.values()[0]
        assertEquals(expectedHeartRate1, hrData1.hrValue)
        val hrData2 = testObserver.values()[1]
        assertEquals(expectedHeartRate2, hrData2.hrValue)
    }

    @Test
    fun test_SensorContactSupported() {
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
        val result = bleHrClient.observeHrNotifications(true)
        val testObserver = TestSubscriber<BleHrClient.HrNotificationData>()
        result.subscribe(testObserver)
        bleHrClient.processServiceData(characteristic, measurementFrame0, status, notifying)
        bleHrClient.processServiceData(characteristic, measurementFrame1, status, notifying)

        //Assert
        testObserver.assertNoErrors()
        testObserver.assertValueCount(2)
        val hrData0 = testObserver.values()[0]
        val hrData1 = testObserver.values()[1]
        assertTrue(hrData0.sensorContact)
        assertTrue(hrData0.sensorContactSupported)
        assertFalse(hrData1.sensorContact)
        assertTrue(hrData1.sensorContactSupported)
    }

    @Test
    fun test_SensorContactNotSupported() {
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
        val result = bleHrClient.observeHrNotifications(true)
        val testObserver = TestSubscriber<BleHrClient.HrNotificationData>()
        result.subscribe(testObserver)
        bleHrClient.processServiceData(characteristic, measurementFrame1, status, notifying)
        bleHrClient.processServiceData(characteristic, measurementFrame2, status, notifying)

        //Assert
        testObserver.assertNoErrors()
        testObserver.assertValueCount(2)
        val hrData1 = testObserver.values()[0]
        assertFalse(hrData1.sensorContactSupported)
        val hrData2 = testObserver.values()[1]
        assertFalse(hrData2.sensorContactSupported)
    }

    @Test
    fun test_EnergyExpended() {
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
        val result = bleHrClient.observeHrNotifications(true)
        val testObserver = TestSubscriber<BleHrClient.HrNotificationData>()
        result.subscribe(testObserver)
        bleHrClient.processServiceData(characteristic, measurementFrame1, status, notifying)
        bleHrClient.processServiceData(characteristic, measurementFrame2, status, notifying)

        //Assert
        testObserver.assertNoErrors()
        testObserver.assertValueCount(2)
        val hrData1 = testObserver.values()[0]
        assertEquals(expectedHeartRate1, hrData1.hrValue)
        assertEquals(energyExpended1, hrData1.energy)
        val hrData2 = testObserver.values()[1]
        assertEquals(expectedHeartRate2, hrData2.hrValue)
        assertEquals(energyExpended2, hrData2.energy)
    }

    @Test
    fun test_RRinterval() {
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
        val expectedRR1 = 65535
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
        val expectedRR2_0 = 65535
        val expectedRR2_1 = 32895
        val measurementFrame2 = byteArrayOf(
            0x11.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0x7F.toByte(),
            0x80.toByte()
        )

        //Act
        val result = bleHrClient.observeHrNotifications(true)
        val testObserver = TestSubscriber<BleHrClient.HrNotificationData>()
        result.subscribe(testObserver)
        bleHrClient.processServiceData(characteristic, measurementFrame1, status, notifying)
        bleHrClient.processServiceData(characteristic, measurementFrame2, status, notifying)

        //Assert
        testObserver.assertNoErrors()
        testObserver.assertValueCount(2)
        val hrData1 = testObserver.values()[0]
        assertEquals(expectedHeartRate1, hrData1.hrValue)
        assertTrue(hrData1.rrPresent)
        assertEquals(expectedRR1, hrData1.rrs[0])
        val hrData2 = testObserver.values()[1]

        assertEquals(expectedHeartRate2, hrData2.hrValue)
        assertTrue(hrData2.rrPresent)
        assertEquals(expectedRR2_0, hrData2.rrs[0])
        assertEquals(expectedRR2_1, hrData2.rrs[1])
    }
}