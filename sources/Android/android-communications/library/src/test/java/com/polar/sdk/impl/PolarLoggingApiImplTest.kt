// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl

import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.api.ble.model.advertisement.BleAdvertisementContent
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.PftpResponseError
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility
import com.polar.sdk.api.model.HrSensorConfig
import com.polar.sdk.api.model.LogConfig
import data.SensorDataLog
import fi.polar.remote.representation.protobuf.Structures.PbBleDeviceName
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import protocol.PftpError.PbPFtpError
import protocol.PftpRequest
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

internal class PolarLoggingApiImplTest {

    private val deviceId = "A1B2C3"

    @Before
    fun setUp() {
        mockkObject(BlePolarDeviceCapabilitiesUtility.Companion)
        every {
            BlePolarDeviceCapabilitiesUtility.getFileSystemType(any<String>())
        } returns BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ── exportDeviceLogs ──────────────────────────────────────────────────────

    @Test
    fun `exportDeviceLogs returns all three static files when all are present on device`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarLoggingApiImpl(listener)

        setupClientRouting(client, mapOf(
            "/ERRORLOG.BPB" to "error log data".toByteArray(),
            "/ERRORLO2.BPB" to "error log 2 data".toByteArray(),
            "/SYSLOG.TXT"   to "system log data".toByteArray()
        ))

        val logs = api.exportDeviceLogs(deviceId)

        assertEquals(3, logs.size)
        assertEquals("/ERRORLOG.BPB", logs[0].path)
        assertArrayEquals("error log data".toByteArray(), logs[0].data)
        assertEquals("/ERRORLO2.BPB", logs[1].path)
        assertEquals("/SYSLOG.TXT",   logs[2].path)
    }

    @Test
    fun `exportDeviceLogs silently skips static files that do not exist on device`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarLoggingApiImpl(listener)

        setupClientRouting(client, mapOf("/SYSLOG.TXT" to "sys".toByteArray()))

        val logs = api.exportDeviceLogs(deviceId)

        assertEquals(1, logs.size)
        assertEquals("/SYSLOG.TXT", logs[0].path)
    }

    @Test
    fun `exportDeviceLogs fetches sequential TRC files starting at index 1 and stops at first missing`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarLoggingApiImpl(listener)

        setupClientRouting(client, mapOf(
            "/TRC1.BIN" to byteArrayOf(0x01),
            "/TRC2.BIN" to byteArrayOf(0x02)
        ))

        val logs = api.exportDeviceLogs(deviceId)

        val paths = logs.map { it.path }
        assertTrue(paths.contains("/TRC1.BIN"))
        assertTrue(paths.contains("/TRC2.BIN"))
        assertTrue(paths.none { it.startsWith("/TRC3") })
    }

    @Test
    fun `exportDeviceLogs fetches sequential DBGTRC files starting at index 1 and stops at first missing`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarLoggingApiImpl(listener)

        setupClientRouting(client, mapOf(
            "/DBGTRC1.BIN" to byteArrayOf(0xDE.toByte(), 0xAD.toByte()),
            "/DBGTRC2.BIN" to byteArrayOf(0xBE.toByte(), 0xEF.toByte())
        ))

        val logs = api.exportDeviceLogs(deviceId)

        val paths = logs.map { it.path }
        assertTrue(paths.contains("/DBGTRC1.BIN"))
        assertTrue(paths.contains("/DBGTRC2.BIN"))
        assertTrue(paths.none { it.startsWith("/DBGTRC3") })
    }

    @Test
    fun `exportDeviceLogs returns empty list when no log files found on device`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarLoggingApiImpl(listener)

        setupClientRouting(client, emptyMap())

        val logs = api.exportDeviceLogs(deviceId)

        assertTrue("Expected empty list when no log files exist", logs.isEmpty())
    }

    @Test
    fun `exportDeviceLogs preserves raw byte data unchanged`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarLoggingApiImpl(listener)
        val binaryPayload = ByteArray(256) { it.toByte() }

        setupClientRouting(client, mapOf("/ERRORLOG.BPB" to binaryPayload))

        val logs = api.exportDeviceLogs(deviceId)

        assertEquals(1, logs.size)
        assertArrayEquals(binaryPayload, logs[0].data)
    }

    // ── getLogConfig ──────────────────────────────────────────────────────────

    @Test
    fun `getLogConfig returns correctly parsed LogConfig from device proto bytes`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarLoggingApiImpl(listener)

        val hrSensorConfig = SensorDataLog.PbBleHrSensorConfig.newBuilder()
            .setDeviceName(PbBleDeviceName.newBuilder().setName("Polar H10 ABCD1234").build())
            .setWaitForConnect(true)
            .build()

        val protoBytes = SensorDataLog.PbSensorDataLog.newBuilder()
            .setOhrLogEnabled(true)
            .setPpiLogEnabled(false)
            .setAccelerationLogEnabled(true)
            .setGpsLogEnabled(false)
            .setBleHrSensorConfig(hrSensorConfig)
            .build()
            .toByteArray()

        setupClientRouting(client, mapOf(LogConfig.LOG_CONFIG_FILENAME to protoBytes))

        val config = api.getLogConfig(deviceId)

        assertEquals(true,  config.ohrLogEnabled)
        assertEquals(false, config.ppiLogEnabled)
        assertEquals(true,  config.accelerationLogEnabled)
        assertEquals(false, config.gpsLogEnabled)
        assertNotNull(config.hrSensorConfig)
        assertEquals("Polar H10 ABCD1234", config.hrSensorConfig?.deviceInformation)
        assertEquals(true, config.hrSensorConfig?.waitUntilConnected)
    }

    @Test
    fun `getLogConfig maps unset proto fields to null in LogConfig`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarLoggingApiImpl(listener)

        val emptyProtoBytes = SensorDataLog.PbSensorDataLog.newBuilder().build().toByteArray()
        setupClientRouting(client, mapOf(LogConfig.LOG_CONFIG_FILENAME to emptyProtoBytes))

        val config = api.getLogConfig(deviceId)

        assertNull(config.ohrLogEnabled)
        assertNull(config.ppiLogEnabled)
        assertNull(config.accelerationLogEnabled)
        assertNull(config.gpsLogEnabled)
        assertNull(config.sleepLogEnabled)
        assertNull(config.hrSensorConfig)
    }

    @Test
    fun `setLogConfig writes hrSensorConfig into the device proto payload`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarLoggingApiImpl(listener)
        val expectedConfig = LogConfig(
            hrSensorConfig = HrSensorConfig(
                deviceInformation = "Polar H10 ABCD1234",
                waitUntilConnected = true
            )
        )

        val captured = mutableListOf<Pair<ByteArray, InputStream>>()
        every { client.write(any(), any()) } answers {
            captured += (firstArg<ByteArray>() to secondArg<InputStream>())
            flowOf(1L)
        }

        api.setLogConfig(deviceId, expectedConfig)

        assertEquals(1, captured.size)
        val (requestBytes, payloadStream) = captured.first()
        val request = PftpRequest.PbPFtpOperation.parseFrom(requestBytes)
        assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, request.command)
        assertEquals(LogConfig.LOG_CONFIG_FILENAME, request.path)

        val writtenConfig = SensorDataLog.PbSensorDataLog.parseFrom(payloadStream.readBytes())
        assertNotNull(writtenConfig.bleHrSensorConfig)
        assertEquals("Polar H10 ABCD1234", writtenConfig.bleHrSensorConfig.deviceName.name)
        assertEquals(true, writtenConfig.bleHrSensorConfig.waitForConnect)
        verify(exactly = 1) { client.write(any(), any()) }
    }

    @Test
    fun `getLogConfig rethrows exception when device returns unparseable bytes`() {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarLoggingApiImpl(listener)

        setupClientRouting(client, mapOf(
            LogConfig.LOG_CONFIG_FILENAME to byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0xFD.toByte())
        ))

        var caught: Exception? = null
        kotlinx.coroutines.runBlocking {
            try { api.getLogConfig(deviceId) } catch (e: Exception) { caught = e }
        }
        assertNotNull("Expected an exception for unparseable bytes", caught)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun mockBleConnection(deviceId: String): Pair<BlePsFtpClient, BleDeviceListener> {
        val client     = mockk<BlePsFtpClient>()
        val listener   = mockk<BleDeviceListener>()
        val session    = mockk<BleDeviceSession>()
        val sessions   = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() }    returns session
        every { session.advertisementContent }  returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType }       returns "Polar360"
        every { session.sessionState }          returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) } returns client
        every { client.isServiceDiscovered }    returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0)

        return Pair(client, listener)
    }

    /**
     * Single stub on [client.request]: parses the path from proto bytes and routes:
     * - path in [presentFiles] → ByteArrayOutputStream with the data
     * - any other path → PftpResponseError NO_SUCH_FILE_OR_DIRECTORY (103)
     */
    private fun setupClientRouting(
        client: BlePsFtpClient,
        presentFiles: Map<String, ByteArray>
    ) {
        coEvery { client.request(any()) } coAnswers {
            val path = PftpRequest.PbPFtpOperation.parseFrom(firstArg<ByteArray>()).path
            val data = presentFiles[path]
            if (data != null) {
                ByteArrayOutputStream().apply { write(data) }
            } else {
                throw PftpResponseError("not found", PbPFtpError.NO_SUCH_FILE_OR_DIRECTORY.number)
            }
        }
    }
}







