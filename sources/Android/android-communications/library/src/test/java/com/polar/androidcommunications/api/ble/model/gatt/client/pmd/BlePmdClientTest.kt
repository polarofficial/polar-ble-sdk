package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

import com.polar.androidcommunications.api.ble.exceptions.BleNotImplemented
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.*
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.reactivex.rxjava3.subscribers.TestSubscriber
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class BlePmdClientTest {

    @MockK
    lateinit var mockGattTxInterface: BleGattTxInterface

    private lateinit var blePmdClient: BlePMDClient

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        blePmdClient = BlePMDClient(mockGattTxInterface)
        every { mockGattTxInterface.isConnected } returns true

        mockkObject(AccData)
        mockkObject(EcgData)
        mockkObject(GnssLocationData)
        mockkObject(GyrData)
        mockkObject(MagData)
        mockkObject(PpiData)
        mockkObject(PpgData)
        mockkObject(PressureData)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `process not supported frame type`() {
        // Arrange
        // HEX: 01 01 00 00 00 00 00 00 70 FF
        // index    type                                data
        // 0:      Measurement type                     01 (ppg data)
        // 1..8:   64-bit Timestamp                     00 00 00 00 00 00 00 70 (0x7000000000000000 = 8070450532247928832)
        // 9:      Frame type                           FF (compressed, frame type 0x7F)

        val locationDataFromService = byteArrayOf(
            0x01.toByte(),
            0x38.toByte(), 0x6C.toByte(), 0x31.toByte(), 0x72.toByte(), 0xA4.toByte(), 0xD3.toByte(), 0x23.toByte(), 0x0D.toByte(),
            0x7F.toByte(),
        )

        // Act && Assert
        assertThrows(BleNotImplemented::class.java) {
            blePmdClient.processServiceData(BlePMDClient.PMD_DATA, locationDataFromService, 0, false)
        }
    }

    @Test
    fun `process ecg data`() {
        // Arrange
        // HEX: 00 38 6C 31 72 A4 D3 23 0D 03 FF
        // index    type                                data
        // 0:      Measurement type                     00 (Ecg data)
        // 1..8:   64-bit Timestamp                     38 6C 31 72 A4 D3 23 0D (0x0D23D3A472316C38 = 946833049921875000)
        // 9:      Frame type                           03 (raw, frame type 3)
        // 10:     Data                                 FF
        val expectedTimeStamp = 946833049921875000L
        val expectedIsCompressed = false
        val expectedFrameType = BlePMDClient.PmdDataFrameType.TYPE_3
        val expectedFrameContent = byteArrayOf(0xFF.toByte())
        val expectedFactor = 1.0f

        val locationDataFromService = byteArrayOf(
            0x00.toByte(),
            0x38.toByte(), 0x6C.toByte(), 0x31.toByte(), 0x72.toByte(), 0xA4.toByte(), 0xD3.toByte(), 0x23.toByte(), 0x0D.toByte(),
            0x03.toByte(),
            0xFF.toByte()
        )

        val result = blePmdClient.monitorEcgNotifications(true)
        val testObserver = TestSubscriber<EcgData>()
        result.subscribe(testObserver)

        val capturedCompression = slot<Boolean>()
        val capturedFrameType = slot<BlePMDClient.PmdDataFrameType>()
        val capturedData = slot<ByteArray>()
        val capturedFactor = slot<Float>()
        val capturedTimeStamp = slot<Long>()

        every {
            EcgData.parseDataFromDataFrame(capture(capturedCompression), capture(capturedFrameType), capture(capturedData), capture(capturedFactor), capture(capturedTimeStamp))
        } answers {
            EcgData(timeStamp = capturedTimeStamp.captured)
        }

        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, locationDataFromService, 0, false)

        // Assert
        testObserver.assertNoErrors()
        testObserver.assertValueCount(1)
        val ecgData = testObserver.values()[0]

        Assert.assertEquals(expectedTimeStamp, ecgData.timeStamp)
        Assert.assertEquals(expectedIsCompressed, capturedCompression.captured)
        Assert.assertEquals(expectedFrameType, capturedFrameType.captured)
        Assert.assertEquals(expectedFactor, capturedFactor.captured)
        Assert.assertEquals(expectedFrameContent[0], capturedData.captured[0])
    }

    @Test
    fun `process ppg data`() {
        // Arrange
        // HEX: 01 01 00 00 00 00 00 00 70 80 FF
        // index    type                                data
        // 0:      Measurement type                     01 (ppg data)
        // 1..8:   64-bit Timestamp                     00 00 00 00 00 00 00 70 (0x7000000000000000 = 8070450532247928832)
        // 9:      Frame type                           80 (compressed, frame type 0)
        // 10:     Data                                 FF
        val expectedTimeStamp = 8070450532247928832L
        val expectedIsCompressed = true
        val expectedFrameType = BlePMDClient.PmdDataFrameType.TYPE_0
        val expectedFrameContent = byteArrayOf(0xFF.toByte())
        val expectedFactor = 1.0f

        val locationDataFromService = byteArrayOf(
            0x01.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x70.toByte(),
            0x80.toByte(),
            0xFF.toByte()
        )

        val result = blePmdClient.monitorPpgNotifications(true)
        val testObserver = TestSubscriber<PpgData>()
        result.subscribe(testObserver)

        val capturedCompression = slot<Boolean>()
        val capturedFrameType = slot<BlePMDClient.PmdDataFrameType>()
        val capturedData = slot<ByteArray>()
        val capturedFactor = slot<Float>()
        val capturedTimeStamp = slot<Long>()

        every {
            PpgData.parseDataFromDataFrame(capture(capturedCompression), capture(capturedFrameType), capture(capturedData), capture(capturedFactor), capture(capturedTimeStamp))
        } answers {
            PpgData(timeStamp = capturedTimeStamp.captured)
        }

        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, locationDataFromService, 0, false)

        // Assert
        testObserver.assertNoErrors()
        testObserver.assertValueCount(1)
        val ppgData = testObserver.values()[0]

        Assert.assertEquals(expectedTimeStamp, ppgData.timeStamp)
        Assert.assertEquals(expectedIsCompressed, capturedCompression.captured)
        Assert.assertEquals(expectedFrameType, capturedFrameType.captured)
        Assert.assertEquals(expectedFactor, capturedFactor.captured)
        Assert.assertEquals(expectedFrameContent[0], capturedData.captured[0])
    }

    @Test
    fun `process acc data`() {
        // Arrange
        // HEX: 02 00 00 00 00 00 00 00 00 83 00
        // index    type                                data
        // 0:      Measurement type                     02 (acc data)
        // 1..8:   64-bit Timestamp                     00 00 00 00 00 00 00 00 (0x0000000000000000 = 0)
        // 9:      Frame type                           83 (compressed, frame type 3)
        // 10:     Data                                 00
        val expectedTimeStamp = 0L
        val expectedIsCompressed = true
        val expectedFrameType = BlePMDClient.PmdDataFrameType.TYPE_3
        val expectedFrameContent = byteArrayOf(0x00.toByte())
        val expectedFactor = 1.0f

        val locationDataFromService = byteArrayOf(
            0x02.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x83.toByte(),
            0x00.toByte()
        )

        val result = blePmdClient.monitorAccNotifications(true)
        val testObserver = TestSubscriber<AccData>()
        result.subscribe(testObserver)

        val capturedCompression = slot<Boolean>()
        val capturedFrameType = slot<BlePMDClient.PmdDataFrameType>()
        val capturedData = slot<ByteArray>()
        val capturedFactor = slot<Float>()
        val capturedTimeStamp = slot<Long>()

        every {
            AccData.parseDataFromDataFrame(capture(capturedCompression), capture(capturedFrameType), capture(capturedData), capture(capturedFactor), capture(capturedTimeStamp))
        } answers {
            AccData(timeStamp = capturedTimeStamp.captured)
        }

        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, locationDataFromService, 0, false)

        // Assert
        testObserver.assertNoErrors()
        testObserver.assertValueCount(1)
        val accData = testObserver.values()[0]

        Assert.assertEquals(expectedTimeStamp, accData.timeStamp)
        Assert.assertEquals(expectedIsCompressed, capturedCompression.captured)
        Assert.assertEquals(expectedFrameType, capturedFrameType.captured)
        Assert.assertEquals(expectedFactor, capturedFactor.captured)
        Assert.assertEquals(expectedFrameContent[0], capturedData.captured[0])
    }

    @Test
    fun `process ppi data`() {
        // Arrange
        // HEX: 03 00 00 00 00 00 00 00 00 03 00
        // index    type                                data
        // 0:      Measurement type                     03 (ppi data)
        // 1..8:   64-bit Timestamp                     00 00 00 00 00 00 00 00 (0x0000000000000000 = 0)
        // 9:      Frame type                           03 (raw, frame type 3)
        // 10:     Data                                 00
        val expectedTimeStamp = 0L
        val expectedIsCompressed = false
        val expectedFrameType = BlePMDClient.PmdDataFrameType.TYPE_3
        val expectedFrameContent = byteArrayOf(0x00.toByte())
        val expectedFactor = 1.0f

        val locationDataFromService = byteArrayOf(
            0x03.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x03.toByte(),
            0x00.toByte()
        )

        val result = blePmdClient.monitorPpiNotifications(true)
        val testObserver = TestSubscriber<PpiData>()
        result.subscribe(testObserver)

        val capturedCompression = slot<Boolean>()
        val capturedFrameType = slot<BlePMDClient.PmdDataFrameType>()
        val capturedData = slot<ByteArray>()
        val capturedFactor = slot<Float>()
        val capturedTimeStamp = slot<Long>()

        every {
            PpiData.parseDataFromDataFrame(capture(capturedCompression), capture(capturedFrameType), capture(capturedData), capture(capturedFactor), capture(capturedTimeStamp))
        } answers {
            PpiData(timeStamp = capturedTimeStamp.captured)
        }

        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, locationDataFromService, 0, false)

        // Assert
        testObserver.assertNoErrors()
        testObserver.assertValueCount(1)
        val ppiData = testObserver.values()[0]

        Assert.assertEquals(expectedTimeStamp, ppiData.timeStamp)
        Assert.assertEquals(expectedIsCompressed, capturedCompression.captured)
        Assert.assertEquals(expectedFrameType, capturedFrameType.captured)
        Assert.assertEquals(expectedFactor, capturedFactor.captured)
        Assert.assertEquals(expectedFrameContent[0], capturedData.captured[0])
    }

    @Test
    fun `process gyro data`() {
        // Arrange
        // HEX: 05 01 00 00 00 00 00 00 00 80 FF
        // index    type                                data
        // 0:      Measurement type                     05 (gyro data)
        // 1..8:   64-bit Timestamp                     01 00 00 00 00 00 00 00 (0x0000000000000001 = 1)
        // 9:      Frame type                           80 (compressed, frame type 0)
        // 10:     Data                                 FF
        val expectedTimeStamp = 1L
        val expectedIsCompressed = true
        val expectedFrameType = BlePMDClient.PmdDataFrameType.TYPE_0
        val expectedFrameContent = byteArrayOf(0xFF.toByte())
        val expectedFactor = 1.0f

        val locationDataFromService = byteArrayOf(
            0x05.toByte(),
            0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x80.toByte(),
            0xFF.toByte()
        )

        val result = blePmdClient.monitorGyroNotifications(true)
        val testObserver = TestSubscriber<GyrData>()
        result.subscribe(testObserver)

        val capturedCompression = slot<Boolean>()
        val capturedFrameType = slot<BlePMDClient.PmdDataFrameType>()
        val capturedData = slot<ByteArray>()
        val capturedFactor = slot<Float>()
        val capturedTimeStamp = slot<Long>()

        every {
            GyrData.parseDataFromDataFrame(capture(capturedCompression), capture(capturedFrameType), capture(capturedData), capture(capturedFactor), capture(capturedTimeStamp))
        } answers {
            GyrData(timeStamp = capturedTimeStamp.captured)
        }

        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, locationDataFromService, 0, false)

        // Assert
        testObserver.assertNoErrors()
        testObserver.assertValueCount(1)
        val gyroData = testObserver.values()[0]

        Assert.assertEquals(expectedTimeStamp, gyroData.timeStamp)
        Assert.assertEquals(expectedIsCompressed, capturedCompression.captured)
        Assert.assertEquals(expectedFrameType, capturedFrameType.captured)
        Assert.assertEquals(expectedFactor, capturedFactor.captured)
        Assert.assertEquals(expectedFrameContent[0], capturedData.captured[0])
    }

    @Test
    fun `process magnetometer data`() {
        // Arrange
        // HEX: 06 01 00 00 00 00 00 00 70 80 FF
        // index    type                                data
        // 0:      Measurement type                     06 (magnetometer data)
        // 1..8:   64-bit Timestamp                     00 00 00 00 00 00 00 70 (0x7000000000000000 = 8070450532247928832)
        // 9:      Frame type                           80 (compressed, frame type 0)
        // 10:     Data                                 FF
        val expectedTimeStamp = 8070450532247928832L
        val expectedIsCompressed = true
        val expectedFrameType = BlePMDClient.PmdDataFrameType.TYPE_0
        val expectedFrameContent = byteArrayOf(0xFF.toByte())
        val expectedFactor = 1.0f

        val locationDataFromService = byteArrayOf(
            0x06.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x70.toByte(),
            0x80.toByte(),
            0xFF.toByte()
        )

        val result = blePmdClient.monitorMagnetometerNotifications(true)
        val testObserver = TestSubscriber<MagData>()
        result.subscribe(testObserver)

        val capturedCompression = slot<Boolean>()
        val capturedFrameType = slot<BlePMDClient.PmdDataFrameType>()
        val capturedData = slot<ByteArray>()
        val capturedFactor = slot<Float>()
        val capturedTimeStamp = slot<Long>()

        every {
            MagData.parseDataFromDataFrame(capture(capturedCompression), capture(capturedFrameType), capture(capturedData), capture(capturedFactor), capture(capturedTimeStamp))
        } answers {
            MagData(timeStamp = capturedTimeStamp.captured)
        }

        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, locationDataFromService, 0, false)

        // Assert
        testObserver.assertNoErrors()
        testObserver.assertValueCount(1)
        val magData = testObserver.values()[0]

        Assert.assertEquals(expectedTimeStamp, magData.timeStamp)
        Assert.assertEquals(expectedIsCompressed, capturedCompression.captured)
        Assert.assertEquals(expectedFrameType, capturedFrameType.captured)
        Assert.assertEquals(expectedFactor, capturedFactor.captured)
        Assert.assertEquals(expectedFrameContent[0], capturedData.captured[0])
    }

    @Test
    fun `process location data`() {
        // Arrange
        // HEX: 0A 38 6C 31 72 A4 D3 23 0D 00 12
        // index    type                                data
        // 0:      Measurement type                     0A (Location data)
        // 1..8:   64-bit Timestamp                     38 6C 31 72 A4 D3 23 0D (0x0D23D3A472316C38 = 946833049921875000)
        // 9:      Frame type                           00 (raw, frame type 0)
        // 10:     Data                                 12
        val expectedTimeStamp = 946833049921875000L
        val expectedIsCompressed = false
        val expectedFrameType = BlePMDClient.PmdDataFrameType.TYPE_0
        val expectedFrameContent = byteArrayOf(0x12)
        val expectedFactor = 1.0f

        val locationDataFromService = byteArrayOf(
            0x0A.toByte(), 0x38.toByte(), 0x6C.toByte(), 0x31.toByte(), 0x72.toByte(), 0xA4.toByte(), 0xD3.toByte(), 0x23.toByte(), 0x0D.toByte(),
            0x00.toByte(), 0x12.toByte()
        )

        val result = blePmdClient.monitorLocationNotifications(true)
        val testObserver = TestSubscriber<GnssLocationData>()
        result.subscribe(testObserver)

        val capturedCompression = slot<Boolean>()
        val capturedFrameType = slot<BlePMDClient.PmdDataFrameType>()
        val capturedData = slot<ByteArray>()
        val capturedFactor = slot<Float>()
        val capturedTimeStamp = slot<Long>()

        every {
            GnssLocationData.parseDataFromDataFrame(capture(capturedCompression), capture(capturedFrameType), capture(capturedData), capture(capturedFactor), capture(capturedTimeStamp))
        } answers {
            GnssLocationData(timeStamp = capturedTimeStamp.captured)
        }
        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, locationDataFromService, 0, false)

        // Assert
        testObserver.assertNoErrors()
        testObserver.assertValueCount(1)
        val locationData = testObserver.values()[0]

        Assert.assertEquals(expectedTimeStamp, locationData.timeStamp)
        Assert.assertEquals(expectedIsCompressed, capturedCompression.captured)
        Assert.assertEquals(expectedFrameType, capturedFrameType.captured)
        Assert.assertEquals(expectedFactor, capturedFactor.captured)
        Assert.assertEquals(expectedFrameContent[0], capturedData.captured[0])
    }

    @Test
    fun `process pressure data`() {
        // Arrange
        // HEX: 0B 00 00 00 00 00 00 00 00 03 00
        // index    type                                data
        // 0:      Measurement type                     0B (pressure data)
        // 1..8:   64-bit Timestamp                     00 00 00 00 00 00 00 00 (0x0000000000000000 = 0)
        // 9:      Frame type                           03 (raw, frame type 3)
        // 10:     Data                                 00
        val expectedTimeStamp = 0L
        val expectedIsCompressed = false
        val expectedFrameType = BlePMDClient.PmdDataFrameType.TYPE_3
        val expectedFrameContent = byteArrayOf(0x00.toByte())
        val expectedFactor = 1.0f

        val locationDataFromService = byteArrayOf(
            0x0B.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x03.toByte(),
            0x00.toByte()
        )

        val result = blePmdClient.monitorPressureNotifications(true)
        val testObserver = TestSubscriber<PressureData>()
        result.subscribe(testObserver)

        val capturedCompression = slot<Boolean>()
        val capturedFrameType = slot<BlePMDClient.PmdDataFrameType>()
        val capturedData = slot<ByteArray>()
        val capturedFactor = slot<Float>()
        val capturedTimeStamp = slot<Long>()

        every {
            PressureData.parseDataFromDataFrame(capture(capturedCompression), capture(capturedFrameType), capture(capturedData), capture(capturedFactor), capture(capturedTimeStamp))
        } answers {
            PressureData(timeStamp = capturedTimeStamp.captured)
        }

        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, locationDataFromService, 0, false)

        // Assert
        testObserver.assertNoErrors()
        testObserver.assertValueCount(1)
        val pressureData = testObserver.values()[0]

        Assert.assertEquals(expectedTimeStamp, pressureData.timeStamp)
        Assert.assertEquals(expectedIsCompressed, capturedCompression.captured)
        Assert.assertEquals(expectedFrameType, capturedFrameType.captured)
        Assert.assertEquals(expectedFactor, capturedFactor.captured)
        Assert.assertEquals(expectedFrameContent[0], capturedData.captured[0])
    }
}