package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

import com.polar.androidcommunications.api.ble.exceptions.BleCharacteristicNotificationNotEnabled
import com.polar.androidcommunications.api.ble.exceptions.BleControlPointCommandError
import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected
import com.polar.androidcommunications.api.ble.exceptions.BleNotImplemented
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.errors.BleOnlineStreamClosed
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.AccData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.EcgData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.GnssLocationData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.GyrData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.MagData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.PpgData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.PpiData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.PressureData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.TemperatureData
import com.polar.androidcommunications.common.ble.AtomicSet
import com.polar.androidcommunications.common.ble.ChannelUtils
import com.polar.androidcommunications.testrules.BleLoggerTestRule
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class BlePmdClientTest {

    @Rule
    @JvmField
    val bleLoggerTestRule = BleLoggerTestRule()

    @MockK
    lateinit var mockGattTxInterface: BleGattTxInterface

    private lateinit var blePmdClient: BlePMDClient

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        blePmdClient = BlePMDClient(mockGattTxInterface)
        every { mockGattTxInterface.isConnected() } returns true

        mockkObject(ChannelUtils.Companion)
        every { ChannelUtils.postDisconnectedAndClearList(any<AtomicSet<Channel<Any>>>()) } answers {
            @Suppress("UNCHECKED_CAST")
            val list = firstArg<AtomicSet<Channel<Any>>>()
            val error = BleDisconnected()
            list.objects().forEach { it.close(error) }
            list.clear()
        }
        every { ChannelUtils.postError(any<AtomicSet<Channel<Any>>>(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            val list = firstArg<AtomicSet<Channel<Any>>>()
            val throwable = secondArg<Throwable>()
            list.objects().forEach { it.close(throwable) }
            list.clear()
        }

        mockkObject(AccData)
        mockkObject(EcgData)
        mockkObject(GnssLocationData)
        mockkObject(GyrData)
        mockkObject(MagData)
        mockkObject(PpiData)
        mockkObject(PpgData)
        mockkObject(PressureData)
        mockkObject(TemperatureData)
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
    fun `process control point response when status is success`() {
        // Arrange
        // HEX: F0 01 00 00 00 00 00 00 70 FF
        // index    type                                data
        // 0:      Response code                        F0
        // 1...:   Data                                 01 00 00 00 00 00 00 70 FF

        val controlPointResponse = byteArrayOf(
            0xF0.toByte(),
            0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x70.toByte(), 0xFF.toByte()
        )
        val successStatusCode = 0x00
        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_CP, controlPointResponse, successStatusCode, true)

        // Assert
        val (data, status) = blePmdClient.pmdCpResponseQueue.take()
        Assert.assertEquals(successStatusCode, status)
        Assert.assertArrayEquals(controlPointResponse, data)
    }

    @Test
    fun `process control point response when status is fail`() {
        // Arrange
        // HEX: F0 01 00 00 00 00 00 00 70 FF
        // index    type                                data
        // 0:      Response code                        F0
        // 1...:   Data                                 01 00 00 00 00 00 00 70 FF
        val controlPointResponse = byteArrayOf(
            0xF0.toByte(),
            0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x70.toByte(), 0xFF.toByte()
        )
        val someRandomFailureStatusCode = 0x11

        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_CP, controlPointResponse, someRandomFailureStatusCode, true)

        // Assert
        val (data, status) = blePmdClient.pmdCpResponseQueue.take()
        Assert.assertEquals(someRandomFailureStatusCode, status)
        Assert.assertArrayEquals(controlPointResponse, data)
    }

    @Test
    fun `process measurement stop control point command from service no content`() {
        // Arrange
        // HEX: 01
        // index    type                                data
        // 0:      Online Measurement Stopped           01
        // 1...:   Measurement types                    <Empty>
        val controlPointResponse = byteArrayOf(0x01.toByte())
        val successStatusCode = 0x00

        // Act & Assert
        //should not throw an exception
        blePmdClient.processServiceData(BlePMDClient.PMD_CP, controlPointResponse, successStatusCode, true)
    }

    @Test
    fun `process measurement stop control point command`() = runTest {
        // Arrange
        // HEX: 01
        // index    type                                data
        // 0:      Online Measurement Stopped           01
        // 1...:   Measurement types                    01, 02
        val controlPointResponse = byteArrayOf(0x01.toByte(), 0x01.toByte(), 0x02.toByte())
        val successStatusCode = 0x00

        val ppgValues = mutableListOf<PpgData>()
        val accValues = mutableListOf<AccData>()
        val ppiValues = mutableListOf<PpiData>()
        var ppgError: Throwable? = null
        var accError: Throwable? = null
        var ppiError: Throwable? = null

        val jobPpg = launch {
            try { blePmdClient.monitorPpgNotifications(false).collect { ppgValues.add(it) } }
            catch (e: Throwable) { ppgError = e }
        }
        val jobAcc = launch {
            try { blePmdClient.monitorAccNotifications(false).collect { accValues.add(it) } }
            catch (e: Throwable) { accError = e }
        }
        val jobPpi = launch {
            try { blePmdClient.monitorPpiNotifications(false).collect { ppiValues.add(it) } }
            catch (e: Throwable) { ppiError = e }
        }
        testScheduler.advanceUntilIdle()

        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_CP, controlPointResponse, successStatusCode, true)
        testScheduler.advanceUntilIdle()
        jobPpg.join()
        jobAcc.join()
        jobPpi.cancel()

        // Assert
        Assert.assertTrue(ppgError is BleOnlineStreamClosed)
        Assert.assertTrue(ppgValues.isEmpty())
        Assert.assertTrue(accError is BleOnlineStreamClosed)
        Assert.assertTrue(accValues.isEmpty())
        Assert.assertNull(ppiError)
        Assert.assertTrue(ppiValues.isEmpty())
    }

    @Test
    fun `process ecg data`() = runTest {
        // Arrange
        // HEX: 00 38 6C 31 72 A4 D3 23 0D 03 00 12 03 11 10 04 00
        // index    type                                data
        // 0:      Measurement type                     00 (Ecg data)
        // 1..8:   64-bit Timestamp                     38 6C 31 72 A4 D3 23 0D (0x0D23D3A472316C38 = 946833049921875000)
        // 9:      Frame type                           03 (raw, frame type 3)
        // 10:     Data                                 00 12 03 11 10 04 00
        val expectedTimeStamp = 946833049921875000uL
        val expectedPreviousTimeStamp = 0uL
        val expectedIsCompressed = false
        val expectedFrameType = PmdDataFrame.PmdDataFrameType.TYPE_3
        val expectedFactor = 1.0f
        val expectedSampleRate = 0

        val ecgDataHeaderFromService = byteArrayOf(
            0x00.toByte(),
            0x38.toByte(), 0x6C.toByte(), 0x31.toByte(), 0x72.toByte(), 0xA4.toByte(), 0xD3.toByte(), 0x23.toByte(), 0x0D.toByte(),
            0x03.toByte(),
        )
        val ecgDataFromService = byteArrayOf(
            0x00.toByte(), 0x12.toByte(), 0x03.toByte(),
            0x11.toByte(), 0x10.toByte(), 0x04.toByte(),
            0x00.toByte(),
        )

        val dataFromService = ecgDataHeaderFromService + ecgDataFromService

        val values = mutableListOf<EcgData>()
        val job = launch { blePmdClient.monitorEcgNotifications(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        val frame = slot<PmdDataFrame>()
        every { EcgData.parseDataFromDataFrame(frame = capture(frame)) } answers {
            val ecgData = EcgData()
            ecgData.ecgSamples.add(EcgData.EcgSample(timeStamp = expectedTimeStamp, microVolts = 1000))
            ecgData
        }

        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, dataFromService, 0, false)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        Assert.assertEquals(1, values.size)
        Assert.assertEquals(expectedTimeStamp, (values[0].ecgSamples.first() as EcgData.EcgSample).timeStamp)
        Assert.assertEquals(expectedPreviousTimeStamp, frame.captured.previousTimeStamp)
        Assert.assertEquals(expectedSampleRate, frame.captured.sampleRate)
        Assert.assertEquals(expectedIsCompressed, frame.captured.isCompressedFrame)
        Assert.assertEquals(expectedFrameType, frame.captured.frameType)
        Assert.assertEquals(expectedFactor, frame.captured.factor)
        Assert.assertArrayEquals(ecgDataFromService, frame.captured.dataContent)
    }

    @Test
    fun `process ppg data`() = runTest {
        // Arrange
        // HEX: 01 01 00 00 00 00 00 00 70 80 FF
        // index    type                                data
        // 0:      Measurement type                     01 (ppg data)
        // 1..8:   64-bit Timestamp                     00 00 00 00 00 00 00 70 (0x7000000000000000 = 8070450532247928832)
        // 9:      Frame type                           80 (compressed, frame type 0)
        // 10:     Data                                 FF
        val expectedTimeStamp = 8070450532247928832uL
        val expectedPreviousTimeStamp = 0uL
        val expectedIsCompressed = true
        val expectedFrameType = PmdDataFrame.PmdDataFrameType.TYPE_0
        val expectedFactor = 1.0f
        val expectedSampleRate = 0

        val ppgDataHeaderFromService = byteArrayOf(
            0x01.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x70.toByte(),
            0x80.toByte(),
        )
        val ppgDataPartFromService = byteArrayOf(0xFF.toByte())
        val ppgDataFromService = ppgDataHeaderFromService + ppgDataPartFromService

        val values = mutableListOf<PpgData>()
        val job = launch { blePmdClient.monitorPpgNotifications(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        val frame = slot<PmdDataFrame>()
        every { PpgData.parseDataFromDataFrame(frame = capture(frame)) } answers {
            val ppgData = PpgData()
            ppgData.ppgSamples.add(PpgData.PpgDataFrameType0(timeStamp = frame.captured.timeStamp, ppgDataSamples = emptyList(), ambientSample = 0))
            ppgData
        }

        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, ppgDataFromService, 0, false)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        Assert.assertEquals(1, values.size)
        Assert.assertEquals(expectedTimeStamp, (values[0].ppgSamples[0] as PpgData.PpgDataFrameType0).timeStamp)
        Assert.assertEquals(expectedPreviousTimeStamp, frame.captured.previousTimeStamp)
        Assert.assertEquals(expectedSampleRate, frame.captured.sampleRate)
        Assert.assertEquals(expectedIsCompressed, frame.captured.isCompressedFrame)
        Assert.assertEquals(expectedFrameType, frame.captured.frameType)
        Assert.assertEquals(expectedFactor, frame.captured.factor)
        Assert.assertArrayEquals(ppgDataPartFromService, frame.captured.dataContent)
    }

    @Test
    fun `process acc data`() = runTest {
        // Arrange
        // HEX: 02 01 00 00 00 00 00 00 00 83 00
        // index    type                                data
        // 0:      Measurement type                     02 (acc data)
        // 1..8:   64-bit Timestamp                     00 00 00 00 00 00 00 01 (0x0000000000000000 = 1)
        // 9:      Frame type                           83 (compressed, frame type 3)
        // 10:     Data                                 00
        val expectedTimeStamp = 1uL
        val expectedPreviousTimeStamp = 0uL
        val expectedIsCompressed = true
        val expectedFrameType = PmdDataFrame.PmdDataFrameType.TYPE_3
        val expectedFactor = 1.0f
        val expectedSampleRate = 0

        val accDataHeaderFromService = byteArrayOf(
            0x02.toByte(),
            0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x83.toByte(),
        )
        val accDataPartFromService = byteArrayOf(0x00.toByte())
        val accDataFromService = accDataHeaderFromService + accDataPartFromService

        val values = mutableListOf<AccData>()
        val job = launch { blePmdClient.monitorAccNotifications(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        val frame = slot<PmdDataFrame>()
        every { AccData.parseDataFromDataFrame(frame = capture(frame)) } answers {
            val accData = AccData()
            accData.accSamples.add(AccData.AccSample(timeStamp = frame.captured.timeStamp, x = 1, y = 2, z = 3))
            accData
        }

        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, accDataFromService, 0, false)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        Assert.assertEquals(1, values.size)
        Assert.assertEquals(expectedTimeStamp, values[0].accSamples[0].timeStamp)
        Assert.assertEquals(expectedPreviousTimeStamp, frame.captured.previousTimeStamp)
        Assert.assertEquals(expectedSampleRate, frame.captured.sampleRate)
        Assert.assertEquals(expectedIsCompressed, frame.captured.isCompressedFrame)
        Assert.assertEquals(expectedFrameType, frame.captured.frameType)
        Assert.assertEquals(expectedFactor, frame.captured.factor)
        Assert.assertArrayEquals(accDataPartFromService, frame.captured.dataContent)
    }

    @Test
    fun `process ppi data`() = runTest {
        // Arrange
        // HEX: 03 00 00 00 00 00 00 00 00 00 00
        // index    type                                data
        // 0:      Measurement type                     03 (ppi data)
        // 1..8:   64-bit Timestamp                     00 00 00 00 00 00 00 00 (0x0000000000000000 = 0)
        // 9:      Frame type                           00 (raw, frame type 0)
        // 10:     Data                                 00
        val expectedTimeStamp = 0uL
        val expectedIsCompressed = false
        val expectedFrameType = PmdDataFrame.PmdDataFrameType.TYPE_0
        val expectedFactor = 1.0f

        val ppiDataHeaderFromService = byteArrayOf(
            0x03.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(),
        )
        val ppiDataPartFromService = byteArrayOf(0x00.toByte())
        val ppiDataFromService = ppiDataHeaderFromService + ppiDataPartFromService

        val values = mutableListOf<PpiData>()
        val job = launch { blePmdClient.monitorPpiNotifications(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        val frame = slot<PmdDataFrame>()
        every { PpiData.parseDataFromDataFrame(frame = capture(frame)) } answers { PpiData() }

        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, ppiDataFromService, 0, false)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        Assert.assertEquals(1, values.size)
        Assert.assertEquals(expectedIsCompressed, frame.captured.isCompressedFrame)
        Assert.assertEquals(expectedFrameType, frame.captured.frameType)
        Assert.assertEquals(expectedFactor, frame.captured.factor)
        Assert.assertArrayEquals(ppiDataPartFromService, frame.captured.dataContent)
    }

    @Test
    fun `process gyro data`() = runTest {
        // Arrange
        // HEX: 05 01 00 00 00 00 00 00 00 80 EA FF 08 00 0D 00 03 01 DF 00
        // index    type                                data
        // 0:      Measurement type                     05 (gyro data)
        // 1..8:   64-bit Timestamp                     01 00 00 00 00 00 00 00 (0x0000000000000001 = 1)
        // 9:      Frame type                           80 (compressed, frame type 0)
        // 10.. :  Data                                 EA FF 08 00 0D 00 03 01 DF 00
        val expectedTimeStamp = 1uL
        val expectedPreviousTimeStamp = 0uL
        val expectedIsCompressed = true
        val expectedFrameType = PmdDataFrame.PmdDataFrameType.TYPE_0
        val expectedFactor = 1.0f
        val expectedSampleRate = 0

        val gyroDataHeaderFromService = byteArrayOf(
            0x05.toByte(),
            0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x80.toByte(),
        )
        val gyroDataPartFromService = byteArrayOf(
            0xEA.toByte(), 0xFF.toByte(),
            0x08.toByte(), 0x00.toByte(), 0x0D.toByte(), 0x00.toByte(),
            0x03.toByte(), 0x01.toByte(), 0xDF.toByte(), 0x00.toByte()
        )
        val gyroDataFromService = gyroDataHeaderFromService + gyroDataPartFromService

        val values = mutableListOf<GyrData>()
        val job = launch { blePmdClient.monitorGyroNotifications(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        val frame = slot<PmdDataFrame>()
        every { GyrData.parseDataFromDataFrame(frame = capture(frame)) } answers {
            val gyrData = GyrData()
            gyrData.gyrSamples.add(GyrData.GyrSample(timeStamp = expectedTimeStamp, x = 1.0f, y = 2.0f, z = 3.0f))
            gyrData
        }

        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, gyroDataFromService, 0, false)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        Assert.assertEquals(1, values.size)
        Assert.assertEquals(expectedTimeStamp, values[0].gyrSamples.first().timeStamp)
        Assert.assertEquals(expectedPreviousTimeStamp, frame.captured.previousTimeStamp)
        Assert.assertEquals(expectedSampleRate, frame.captured.sampleRate)
        Assert.assertEquals(expectedIsCompressed, frame.captured.isCompressedFrame)
        Assert.assertEquals(expectedFrameType, frame.captured.frameType)
        Assert.assertEquals(expectedFactor, frame.captured.factor)
        Assert.assertArrayEquals(gyroDataPartFromService, frame.captured.dataContent)
    }

    @Test
    fun `process magnetometer data`() = runTest {
        // Arrange
        // HEX: 06 01 00 00 00 00 00 00 70 80 FF
        // index    type                                data
        // 0:      Measurement type                     06 (magnetometer data)
        // 1..8:   64-bit Timestamp                     00 00 00 00 00 00 00 70 (0x7000000000000000 = 8070450532247928832)
        // 9:      Frame type                           80 (compressed, frame type 0)
        // 10:     Data                                 FF
        val expectedTimeStamp = 8070450532247928832uL
        val expectedPreviousTimeStamp = 0uL
        val expectedIsCompressed = true
        val expectedFrameType = PmdDataFrame.PmdDataFrameType.TYPE_0
        val expectedFactor = 1.0f
        val expectedSampleRate = 0

        val magDataHeaderFromService = byteArrayOf(
            0x06.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x70.toByte(),
            0x80.toByte(),
        )
        val magDataPartFromService = byteArrayOf(0xFF.toByte())
        val magDataFromService = magDataHeaderFromService + magDataPartFromService

        val values = mutableListOf<MagData>()
        val job = launch { blePmdClient.monitorMagnetometerNotifications(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        val frame = slot<PmdDataFrame>()
        every { MagData.parseDataFromDataFrame(frame = capture(frame)) } answers {
            val magData = MagData()
            magData.magSamples.add(MagData.MagSample(timeStamp = expectedTimeStamp, x = 1.0f, y = 2.0f, z = 3.0f))
            magData
        }

        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, magDataFromService, 0, false)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        Assert.assertEquals(1, values.size)
        Assert.assertEquals(expectedTimeStamp, values[0].magSamples.first().timeStamp)
        Assert.assertEquals(expectedPreviousTimeStamp, frame.captured.previousTimeStamp)
        Assert.assertEquals(expectedSampleRate, frame.captured.sampleRate)
        Assert.assertEquals(expectedIsCompressed, frame.captured.isCompressedFrame)
        Assert.assertEquals(expectedFrameType, frame.captured.frameType)
        Assert.assertEquals(expectedFactor, frame.captured.factor)
        Assert.assertArrayEquals(magDataPartFromService, frame.captured.dataContent)
    }

    @Test
    fun `process location data`() = runTest {
        // Arrange
        // HEX: 0A 38 6C 31 72 A4 D3 23 0D 00 12
        // index    type                                data
        // 0:      Measurement type                     0A (Location data)
        // 1..8:   64-bit Timestamp                     38 6C 31 72 A4 D3 23 0D (0x0D23D3A472316C38 = 946833049921875000)
        // 9:      Frame type                           00 (raw, frame type 0)
        // 10:     Data                                 12
        val expectedPreviousTimeStamp = 0uL
        val expectedIsCompressed = false
        val expectedFrameType = PmdDataFrame.PmdDataFrameType.TYPE_0
        val expectedFactor = 1.0f
        val expectedSampleRate = 0

        val locationDataHeaderFromService = byteArrayOf(
            0x0A.toByte(),
            0x38.toByte(), 0x6C.toByte(), 0x31.toByte(), 0x72.toByte(), 0xA4.toByte(), 0xD3.toByte(), 0x23.toByte(), 0x0D.toByte(),
            0x00.toByte(),
        )
        val locationDataPartFromService = byteArrayOf(0x12.toByte())
        val locationDataFromService = locationDataHeaderFromService + locationDataPartFromService

        val values = mutableListOf<GnssLocationData>()
        val job = launch { blePmdClient.monitorLocationNotifications(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        val frame = slot<PmdDataFrame>()
        every { GnssLocationData.parseDataFromDataFrame(frame = capture(frame)) } answers { GnssLocationData() }

        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, locationDataFromService, 0, false)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        Assert.assertEquals(1, values.size)
        Assert.assertEquals(expectedPreviousTimeStamp, frame.captured.previousTimeStamp)
        Assert.assertEquals(expectedSampleRate, frame.captured.sampleRate)
        Assert.assertEquals(expectedIsCompressed, frame.captured.isCompressedFrame)
        Assert.assertEquals(expectedFrameType, frame.captured.frameType)
        Assert.assertEquals(expectedFactor, frame.captured.factor)
        Assert.assertArrayEquals(locationDataPartFromService, frame.captured.dataContent)
    }

    @Test
    fun `process pressure data`() = runTest {
        // Arrange
        // HEX: 0B 00 00 00 00 00 00 00 00 03 00
        // index    type                                data
        // 0:      Measurement type                     0B (pressure data)
        // 1..8:   64-bit Timestamp                     00 00 00 00 00 00 00 00 (0x0000000000000000 = 0)
        // 9:      Frame type                           03 (raw, frame type 3)
        // 10:     Data                                 00
        val expectedPreviousTimeStamp = 0uL
        val expectedIsCompressed = true
        val expectedFrameType = PmdDataFrame.PmdDataFrameType.TYPE_3
        val expectedFactor = 1.0f
        val expectedSampleRate = 0

        val pressureDataHeaderFromService = byteArrayOf(
            0x0B.toByte(),
            0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x83.toByte(),
        )
        val pressureDataPartFromService = byteArrayOf(0x00.toByte())
        val pressureDataFromService = pressureDataHeaderFromService + pressureDataPartFromService

        val values = mutableListOf<PressureData>()
        val job = launch { blePmdClient.monitorPressureNotifications(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        val frame = slot<PmdDataFrame>()
        every { PressureData.parseDataFromDataFrame(frame = capture(frame)) } answers { PressureData() }

        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, pressureDataFromService, 0, false)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        Assert.assertEquals(1, values.size)
        Assert.assertEquals(expectedPreviousTimeStamp, frame.captured.previousTimeStamp)
        Assert.assertEquals(expectedSampleRate, frame.captured.sampleRate)
        Assert.assertEquals(expectedIsCompressed, frame.captured.isCompressedFrame)
        Assert.assertEquals(expectedFrameType, frame.captured.frameType)
        Assert.assertEquals(expectedFactor, frame.captured.factor)
        Assert.assertArrayEquals(pressureDataPartFromService, frame.captured.dataContent)
    }

    @Test
    fun `process temperature data`() = runTest {
        // Arrange
        // HEX: 0C 01 00 00 00 00 00 00 00 03 00
        // index    type                                data
        // 0:      Measurement type                     0C (temperature data)
        // 1..8:   64-bit Timestamp                     01 00 00 00 00 00 00 00 (0x0000000000000000 = 0)
        // 9:      Frame type                           80 (compressed, frame type 0)
        // 10:     Data                                 FF
        val expectedPreviousTimeStamp = 0uL
        val expectedIsCompressed = true
        val expectedFrameType = PmdDataFrame.PmdDataFrameType.TYPE_0
        val expectedFactor = 1.0f
        val expectedSampleRate = 0

        val temperatureDataHeaderFromService = byteArrayOf(
            0x0C.toByte(),
            0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x80.toByte(),
        )
        val temperatureDataPartFromService = byteArrayOf(0xFF.toByte())
        val temperatureDataFromService = temperatureDataHeaderFromService + temperatureDataPartFromService

        val values = mutableListOf<TemperatureData>()
        val job = launch { blePmdClient.monitorTemperatureNotifications(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        val frame = slot<PmdDataFrame>()
        every { TemperatureData.parseDataFromDataFrame(frame = capture(frame)) } answers { TemperatureData() }

        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, temperatureDataFromService, 0, false)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        Assert.assertEquals(1, values.size)
        Assert.assertEquals(expectedPreviousTimeStamp, frame.captured.previousTimeStamp)
        Assert.assertEquals(expectedSampleRate, frame.captured.sampleRate)
        Assert.assertEquals(expectedIsCompressed, frame.captured.isCompressedFrame)
        Assert.assertEquals(expectedFrameType, frame.captured.frameType)
        Assert.assertEquals(expectedFactor, frame.captured.factor)
        Assert.assertArrayEquals(temperatureDataPartFromService, frame.captured.dataContent)
    }

    @Test
    fun `test previous timestamp`() = runTest {
        // Arrange
        // firstDataFromService: 02 01 00 00 00 00 00 00 00 83 00
        val accDataFromServiceFirst = byteArrayOf(
            0x02.toByte(),
            0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x83.toByte(), 0x00.toByte()
        )
        // secondDataFromService: 02 FF FF FF FF FF FF FF F0 83 00
        val accDataFromServiceSecond = byteArrayOf(
            0x02.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x0F.toByte(),
            0x83.toByte(), 0x00.toByte()
        )

        // randomDataFromService: 03 01 FF FF FF FF FF FF F0 83 00
        val randomDataFromService = byteArrayOf(
            0x03.toByte(),
            0x01.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x0F.toByte(),
            0x83.toByte(), 0x00.toByte()
        )

        // thirdDataFromService: 02 03 00 00 00 00 00 00 00 83 00
        val accDataFromServiceThird = byteArrayOf(
            0x02.toByte(),
            0x03.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x83.toByte(), 0x00.toByte()
        )

        val expectedTimeStampAfterFirstProcess = 1uL
        val expectedPreviousTimeStampAfterFirstProcess = 0uL

        val expectedTimeStampAfterSecondProcess = 0x0FFFFFFFFFFFFFFFuL
        val expectedPreviousTimeStampAfterSecondProcess = 1uL

        val expectedTimeStampAfterThirdProcess = 3uL
        val expectedPreviousTimeStampAfterThirdProcess = 0x0FFFFFFFFFFFFFFFuL

        val values = mutableListOf<AccData>()
        val job = launch { blePmdClient.monitorAccNotifications(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        val capturedFrames = mutableListOf<PmdDataFrame>()
        val frame = slot<PmdDataFrame>()
        every { AccData.parseDataFromDataFrame(frame = capture(frame)) } answers {
            val accData = AccData()
            accData.accSamples.add(AccData.AccSample(timeStamp = frame.captured.timeStamp, x = 1, y = 2, z = 3))
            capturedFrames.add(frame.captured)
            accData
        }

        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, accDataFromServiceFirst, 0, false)
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, randomDataFromService, 0, false)
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, accDataFromServiceSecond, 0, false)
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, randomDataFromService, 0, false)
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, accDataFromServiceThird, 0, false)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        Assert.assertEquals(expectedTimeStampAfterFirstProcess, capturedFrames[0].timeStamp)
        Assert.assertEquals(expectedPreviousTimeStampAfterFirstProcess, capturedFrames[0].previousTimeStamp)

        Assert.assertEquals(expectedTimeStampAfterSecondProcess, capturedFrames[1].timeStamp)
        Assert.assertEquals(expectedPreviousTimeStampAfterSecondProcess, capturedFrames[1].previousTimeStamp)

        Assert.assertEquals(expectedTimeStampAfterThirdProcess, capturedFrames[2].timeStamp)
        Assert.assertEquals(expectedPreviousTimeStampAfterThirdProcess, capturedFrames[2].previousTimeStamp)
    }

    @Test
    fun `test previous timestamp reset`() = runTest {
        // Arrange
        val accDataFromService = byteArrayOf(
            0x02.toByte(),
            0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x83.toByte(), 0x00.toByte()
        )

        val expectedTimeStampAfterFirstProcess = 1uL

        val values = mutableListOf<AccData>()
        val job = launch { blePmdClient.monitorAccNotifications(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        val capturedFrames = mutableListOf<PmdDataFrame>()
        val frame = slot<PmdDataFrame>()
        every { AccData.parseDataFromDataFrame(frame = capture(frame)) } answers {
            val accData = AccData()
            accData.accSamples.add(AccData.AccSample(timeStamp = frame.captured.timeStamp, x = 1, y = 2, z = 3))
            capturedFrames.add(frame.captured)
            accData
        }

        // Act & Assert
        val previousTimeStampAtTheBeginning = blePmdClient.getPreviousFrameTimeStamp(PmdMeasurementType.ACC, PmdDataFrame.PmdDataFrameType.TYPE_3)
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, accDataFromService, 0, false)
        testScheduler.advanceUntilIdle()
        val previousTimeStampAfterProcess = blePmdClient.getPreviousFrameTimeStamp(PmdMeasurementType.ACC, PmdDataFrame.PmdDataFrameType.TYPE_3)
        job.cancel()
        blePmdClient.reset()
        val previousTimeStampAfterReset = blePmdClient.getPreviousFrameTimeStamp(PmdMeasurementType.ACC, PmdDataFrame.PmdDataFrameType.TYPE_3)

        Assert.assertEquals(0uL, previousTimeStampAtTheBeginning)
        Assert.assertEquals(expectedTimeStampAfterFirstProcess, previousTimeStampAfterProcess)
        Assert.assertEquals(0uL, previousTimeStampAfterReset)
    }

    /**
     * Helper that simulates both PMD_CP and PMD_DATA notifications being enabled,
     * which is required by [BlePMDClient.sendControlPointCommand].
     */
    private fun enableNotifications() {
        blePmdClient.descriptorWritten(BlePMDClient.PMD_CP, true, BleGattBase.ATT_SUCCESS)
        blePmdClient.descriptorWritten(BlePMDClient.PMD_DATA, true, BleGattBase.ATT_SUCCESS)
    }

    /**
     * Builds a minimal success control-point response byte array for REQUEST_MEASUREMENT_START.
     *
     * Layout (see [PmdControlPointResponse]):
     *  [0] = 0xF0 (response code)
     *  [1] = command op-code (REQUEST_MEASUREMENT_START = 2)
     *  [2] = measurement type byte
     *  [3] = status (0 = SUCCESS)
     *  [4] = more flag (0 = no more)
     *  [5..] = optional parameters
     */
    private fun buildStartMeasurementSuccessResponse(
        measurementTypeByte: Byte = PmdMeasurementType.ECG.numVal.toByte(),
        parameters: ByteArray = byteArrayOf()
    ): ByteArray {
        return byteArrayOf(
            0xF0.toByte(),
            PmdControlPointCommandClientToService.REQUEST_MEASUREMENT_START.code.toByte(),
            measurementTypeByte,
            0x00.toByte(), // SUCCESS
            0x00.toByte()  // more = false
        ) + parameters
    }

    /**
     * Builds a failure control-point response for REQUEST_MEASUREMENT_START.
     */
    private fun buildStartMeasurementFailureResponse(
        measurementTypeByte: Byte = PmdMeasurementType.ECG.numVal.toByte(),
        errorCode: Byte = PmdControlPointResponse.PmdControlPointResponseCode.ERROR_INVALID_STATE.numVal.toByte()
    ): ByteArray {
        return byteArrayOf(
            0xF0.toByte(),
            PmdControlPointCommandClientToService.REQUEST_MEASUREMENT_START.code.toByte(),
            measurementTypeByte,
            errorCode,
            0x00.toByte()
        )
    }

    @Test
    fun `startMeasurement - succeeds with online ECG and stores setting`() = runTest {
        // Arrange
        enableNotifications()
        every { mockGattTxInterface.transmitMessage(any(), any(), any(), any()) } just runs

        val setting = PmdSetting(
            mapOf(
                PmdSetting.PmdSettingType.SAMPLE_RATE to 130,
                PmdSetting.PmdSettingType.RESOLUTION to 14,
                PmdSetting.PmdSettingType.CHANNELS to 1
            )
        )

        val responseBytes = buildStartMeasurementSuccessResponse(
            measurementTypeByte = PmdMeasurementType.ECG.numVal.toByte()
        )

        var caughtError: Throwable? = null
        val job = launch {
            try {
                blePmdClient.startMeasurement(PmdMeasurementType.ECG, setting, PmdRecordingType.ONLINE)
            } catch (e: Throwable) {
                caughtError = e
            }
        }
        testScheduler.advanceUntilIdle()

        // Feed the CP response so that receiveControlPointPacket unblocks
        blePmdClient.processServiceData(BlePMDClient.PMD_CP, responseBytes, BleGattBase.ATT_SUCCESS, true)
        testScheduler.advanceUntilIdle()
        job.join()

        // Assert
        assertTrue("Expected no error but got: $caughtError", caughtError == null)
        assertNotNull("currentSettings should contain ECG entry", blePmdClient.currentSettings[PmdMeasurementType.ECG])
        verify { mockGattTxInterface.transmitMessage(any(), BlePMDClient.PMD_CP, any(), true) }
    }

    @Test
    fun `startMeasurement - succeeds with offline ACC and stores setting`() = runTest {
        // Arrange
        enableNotifications()
        every { mockGattTxInterface.transmitMessage(any(), any(), any(), any()) } just runs

        val setting = PmdSetting(
            mapOf(
                PmdSetting.PmdSettingType.SAMPLE_RATE to 50,
                PmdSetting.PmdSettingType.RESOLUTION to 16,
                PmdSetting.PmdSettingType.CHANNELS to 3
            )
        )

        val responseBytes = buildStartMeasurementSuccessResponse(
            measurementTypeByte = PmdMeasurementType.ACC.numVal.toByte()
        )

        var caughtError: Throwable? = null
        val job = launch {
            try {
                blePmdClient.startMeasurement(PmdMeasurementType.ACC, setting, PmdRecordingType.OFFLINE)
            } catch (e: Throwable) {
                caughtError = e
            }
        }
        testScheduler.advanceUntilIdle()
        blePmdClient.processServiceData(BlePMDClient.PMD_CP, responseBytes, BleGattBase.ATT_SUCCESS, true)
        testScheduler.advanceUntilIdle()
        job.join()

        // Assert
        assertTrue("Expected no error but got: $caughtError", caughtError == null)
        assertNotNull(blePmdClient.currentSettings[PmdMeasurementType.ACC])
        assertEquals(50, blePmdClient.currentSettings[PmdMeasurementType.ACC]?.selected?.get(PmdSetting.PmdSettingType.SAMPLE_RATE))
    }

    @Test
    fun `startMeasurement - success response updates factor in currentSettings`() = runTest {
        // Arrange
        enableNotifications()
        every { mockGattTxInterface.transmitMessage(any(), any(), any(), any()) } just runs

        val setting = PmdSetting(
            mapOf(
                PmdSetting.PmdSettingType.SAMPLE_RATE to 200,
                PmdSetting.PmdSettingType.RESOLUTION to 16
            )
        )

        // Build a FACTOR parameter in the response: PmdSettingType.FACTOR(5), count=1, value=1082130432 (1.0f as IEEE754)
        // IEEE 754 for 1.0f = 0x3F800000 = 1065353216
        val factorInt = java.lang.Float.floatToIntBits(2.5f) // 0x40200000 = 1075838976
        val factorBytes = byteArrayOf(
            PmdSetting.PmdSettingType.FACTOR.numVal.toByte(),
            0x01.toByte(), // count
            (factorInt and 0xFF).toByte(),
            ((factorInt shr 8) and 0xFF).toByte(),
            ((factorInt shr 16) and 0xFF).toByte(),
            ((factorInt shr 24) and 0xFF).toByte()
        )

        val responseBytes = buildStartMeasurementSuccessResponse(
            measurementTypeByte = PmdMeasurementType.PPG.numVal.toByte(),
            parameters = factorBytes
        )

        var caughtError: Throwable? = null
        val job = launch {
            try {
                blePmdClient.startMeasurement(PmdMeasurementType.PPG, setting, PmdRecordingType.ONLINE)
            } catch (e: Throwable) {
                caughtError = e
            }
        }
        testScheduler.advanceUntilIdle()
        blePmdClient.processServiceData(BlePMDClient.PMD_CP, responseBytes, BleGattBase.ATT_SUCCESS, true)
        testScheduler.advanceUntilIdle()
        job.join()

        // Assert
        assertTrue("Expected no error but got: $caughtError", caughtError == null)
        val storedFactor = blePmdClient.currentSettings[PmdMeasurementType.PPG]
            ?.selected?.get(PmdSetting.PmdSettingType.FACTOR)
        assertNotNull("Factor should be stored in currentSettings", storedFactor)
        assertEquals(factorInt, storedFactor)
    }

    @Test
    fun `startMeasurement - device returns error status throws BleControlPointCommandError`() = runTest {
        // Arrange
        enableNotifications()
        every { mockGattTxInterface.transmitMessage(any(), any(), any(), any()) } just runs

        val setting = PmdSetting(mapOf(PmdSetting.PmdSettingType.SAMPLE_RATE to 130))

        val responseBytes = buildStartMeasurementFailureResponse(
            measurementTypeByte = PmdMeasurementType.ECG.numVal.toByte(),
            errorCode = PmdControlPointResponse.PmdControlPointResponseCode.ERROR_INVALID_STATE.numVal.toByte()
        )

        var caughtError: Throwable? = null
        val job = launch {
            try {
                blePmdClient.startMeasurement(PmdMeasurementType.ECG, setting)
            } catch (e: Throwable) {
                caughtError = e
            }
        }
        testScheduler.advanceUntilIdle()
        blePmdClient.processServiceData(BlePMDClient.PMD_CP, responseBytes, BleGattBase.ATT_SUCCESS, true)
        testScheduler.advanceUntilIdle()
        job.join()

        // Assert
        assertTrue("Expected BleControlPointCommandError", caughtError is BleControlPointCommandError)
        val error = caughtError as BleControlPointCommandError
        assertEquals(PmdControlPointResponse.PmdControlPointResponseCode.ERROR_INVALID_STATE, error.error)
    }

    @Test
    fun `startMeasurement - device returns ALREADY_IN_STATE throws BleControlPointCommandError`() = runTest {
        // Arrange
        enableNotifications()
        every { mockGattTxInterface.transmitMessage(any(), any(), any(), any()) } just runs

        val setting = PmdSetting(mapOf(PmdSetting.PmdSettingType.SAMPLE_RATE to 52))

        val responseBytes = buildStartMeasurementFailureResponse(
            measurementTypeByte = PmdMeasurementType.ACC.numVal.toByte(),
            errorCode = PmdControlPointResponse.PmdControlPointResponseCode.ERROR_ALREADY_IN_STATE.numVal.toByte()
        )

        var caughtError: Throwable? = null
        val job = launch {
            try {
                blePmdClient.startMeasurement(PmdMeasurementType.ACC, setting)
            } catch (e: Throwable) {
                caughtError = e
            }
        }
        testScheduler.advanceUntilIdle()
        blePmdClient.processServiceData(BlePMDClient.PMD_CP, responseBytes, BleGattBase.ATT_SUCCESS, true)
        testScheduler.advanceUntilIdle()
        job.join()

        assertTrue("Expected BleControlPointCommandError", caughtError is BleControlPointCommandError)
        val error = caughtError as BleControlPointCommandError
        assertEquals(PmdControlPointResponse.PmdControlPointResponseCode.ERROR_ALREADY_IN_STATE, error.error)
    }

    @Test
    fun `startMeasurement - notifications not enabled throws BleCharacteristicNotificationNotEnabled`() = runTest {
        // Arrange: intentionally do NOT call enableNotifications()
        every { mockGattTxInterface.transmitMessage(any(), any(), any(), any()) } just runs

        val setting = PmdSetting(mapOf(PmdSetting.PmdSettingType.SAMPLE_RATE to 130))

        var caughtError: Throwable? = null
        val job = launch {
            try {
                blePmdClient.startMeasurement(PmdMeasurementType.ECG, setting)
            } catch (e: Throwable) {
                caughtError = e
            }
        }
        testScheduler.advanceUntilIdle()
        job.join()

        // Assert
        assertTrue(
            "Expected BleCharacteristicNotificationNotEnabled but got: $caughtError",
            caughtError is BleCharacteristicNotificationNotEnabled
        )
    }

    @Test
    fun `startMeasurement - with secret serializes secret into command bytes`() = runTest {
        // Arrange
        enableNotifications()

        val capturedPacket = slot<ByteArray>()
        every { mockGattTxInterface.transmitMessage(any(), any(), capture(capturedPacket), any()) } just runs

        val setting = PmdSetting(
            mapOf(
                PmdSetting.PmdSettingType.SAMPLE_RATE to 130,
                PmdSetting.PmdSettingType.RESOLUTION to 14
            )
        )
        val secretKey = ByteArray(16) { it.toByte() }
        val secret = PmdSecret(PmdSecret.SecurityStrategy.AES128, secretKey)

        val responseBytes = buildStartMeasurementSuccessResponse(
            measurementTypeByte = PmdMeasurementType.ECG.numVal.toByte()
        )

        val job = launch {
            try {
                blePmdClient.startMeasurement(PmdMeasurementType.ECG, setting, PmdRecordingType.ONLINE, secret)
            } catch (_: Throwable) {}
        }
        testScheduler.advanceUntilIdle()
        blePmdClient.processServiceData(BlePMDClient.PMD_CP, responseBytes, BleGattBase.ATT_SUCCESS, true)
        testScheduler.advanceUntilIdle()
        job.join()

        // The captured packet should contain the security setting bytes after the measurement header
        val packet = capturedPacket.captured
        // First byte of the PMD command is the command opcode (REQUEST_MEASUREMENT_START = 2)
        assertEquals(PmdControlPointCommandClientToService.REQUEST_MEASUREMENT_START.code.toByte(), packet[0])
        // The packet should be longer than just the 2-byte header (opcode + measurement byte)
        // because settings + secret are appended
        assertTrue("Packet should contain setting + secret bytes", packet.size > 2)
        // Verify security strategy byte (AES128 = 0x02) is present somewhere in the packet
        assertTrue("Packet should contain AES128 strategy byte", packet.contains(PmdSecret.SecurityStrategy.AES128.numVal.toByte()))
    }

    @Test
    fun `startMeasurement - offline recording type sets high bit in first byte`() = runTest {
        // Arrange
        enableNotifications()

        val capturedPacket = slot<ByteArray>()
        every { mockGattTxInterface.transmitMessage(any(), any(), capture(capturedPacket), any()) } just runs

        val setting = PmdSetting(mapOf(PmdSetting.PmdSettingType.SAMPLE_RATE to 50))

        val responseBytes = buildStartMeasurementSuccessResponse(
            measurementTypeByte = PmdMeasurementType.ACC.numVal.toByte()
        )

        val job = launch {
            try {
                blePmdClient.startMeasurement(PmdMeasurementType.ACC, setting, PmdRecordingType.OFFLINE)
            } catch (_: Throwable) {}
        }
        testScheduler.advanceUntilIdle()
        blePmdClient.processServiceData(BlePMDClient.PMD_CP, responseBytes, BleGattBase.ATT_SUCCESS, true)
        testScheduler.advanceUntilIdle()
        job.join()

        // The command packet sent to PMD_CP has:
        //   packet[0] = command op-code = REQUEST_MEASUREMENT_START.code
        //   packet[1] = firstByte = OFFLINE.asBitField() OR ACC.numVal = 0x80 OR 0x02 = 0x82
        val expectedFirstByte = (PmdRecordingType.OFFLINE.asBitField() or PmdMeasurementType.ACC.numVal).toByte()
        assertEquals("Second byte should encode OFFLINE recording type + ACC measurement", expectedFirstByte, capturedPacket.captured[1])
    }

    @Test
    fun `startMeasurement - online recording type has high bit clear in first byte`() = runTest {
        // Arrange
        enableNotifications()

        val capturedPacket = slot<ByteArray>()
        every { mockGattTxInterface.transmitMessage(any(), any(), capture(capturedPacket), any()) } just runs

        val setting = PmdSetting(mapOf(PmdSetting.PmdSettingType.SAMPLE_RATE to 130))

        val responseBytes = buildStartMeasurementSuccessResponse(
            measurementTypeByte = PmdMeasurementType.ECG.numVal.toByte()
        )

        val job = launch {
            try {
                blePmdClient.startMeasurement(PmdMeasurementType.ECG, setting, PmdRecordingType.ONLINE)
            } catch (_: Throwable) {}
        }
        testScheduler.advanceUntilIdle()
        blePmdClient.processServiceData(BlePMDClient.PMD_CP, responseBytes, BleGattBase.ATT_SUCCESS, true)
        testScheduler.advanceUntilIdle()
        job.join()

        // packet[1] = ONLINE.asBitField() OR ECG.numVal = 0x00 OR 0x00 = 0x00
        val expectedFirstByte = (PmdRecordingType.ONLINE.asBitField() or PmdMeasurementType.ECG.numVal).toByte()
        assertEquals("Second byte should encode ONLINE recording type + ECG measurement", expectedFirstByte, capturedPacket.captured[1])
    }
}