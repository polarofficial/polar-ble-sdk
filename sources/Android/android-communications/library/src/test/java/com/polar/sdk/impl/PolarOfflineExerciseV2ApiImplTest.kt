// Copyright © 2023 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl

import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.api.ble.model.advertisement.BleAdvertisementContent
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.sdk.api.PolarOfflineExerciseV2Api
import com.polar.sdk.api.errors.PolarDeviceNotFound
import com.polar.sdk.api.errors.PolarServiceNotAvailable
import com.polar.sdk.api.model.PolarExerciseEntry
import com.polar.sdk.api.model.PolarExerciseSession
import fi.polar.remote.representation.protobuf.ExerciseSamples.PbExerciseSamples
import fi.polar.remote.representation.protobuf.Structures
import fi.polar.remote.representation.protobuf.Types
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Test
import protocol.PftpRequest
import protocol.PftpResponse
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for PolarOfflineExerciseV2ApiImpl.
 *
 * These tests validate the API implementation using mocked PFTP requests/responses.
 */
class PolarOfflineExerciseV2ApiImplTest {

    private val deviceId = "E123456F"

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `startOfflineExerciseV2() should return SUCCESS result`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)
        val sportProfile = PolarExerciseSession.SportProfile.RUNNING

        val mockResponse = PftpResponse.PbPftpStartDmExerciseResult.newBuilder()
            .setResult(PftpResponse.PbPftpStartDmExerciseResult.PbStartDmExerciseResult.RESULT_SUCCESS)
            .setDmDirectoryPath("/exercise")
            .build()

        val mockResponseContent = ByteArrayOutputStream().apply { mockResponse.writeTo(this) }
        coEvery { client.query(any(), any()) } returns mockResponseContent

        // Act
        val result = api.startOfflineExerciseV2(deviceId, sportProfile)

        // Assert
        coVerify { client.query(PftpRequest.PbPFtpQuery.START_DM_EXERCISE_VALUE, any()) }
        Assert.assertEquals(PolarOfflineExerciseV2Api.StartResult.SUCCESS, result.result)
        Assert.assertEquals("/exercise", result.directoryPath)
    }

    @Test
    fun `startOfflineExerciseV2() should return EXERCISE_ONGOING result`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)
        val sportProfile = PolarExerciseSession.SportProfile.CYCLING

        val mockResponse = PftpResponse.PbPftpStartDmExerciseResult.newBuilder()
            .setResult(PftpResponse.PbPftpStartDmExerciseResult.PbStartDmExerciseResult.RESULT_EXE_ONGOING)
            .build()

        val mockResponseContent = ByteArrayOutputStream().apply { mockResponse.writeTo(this) }
        coEvery { client.query(any(), any()) } returns mockResponseContent

        // Act
        val result = api.startOfflineExerciseV2(deviceId, sportProfile)

        // Assert
        coVerify { client.query(PftpRequest.PbPFtpQuery.START_DM_EXERCISE_VALUE, any()) }
        Assert.assertEquals(PolarOfflineExerciseV2Api.StartResult.EXERCISE_ONGOING, result.result)
    }

    @Test
    fun `startOfflineExerciseV2() should return LOW_BATTERY result`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)

        val mockResponse = PftpResponse.PbPftpStartDmExerciseResult.newBuilder()
            .setResult(PftpResponse.PbPftpStartDmExerciseResult.PbStartDmExerciseResult.RESULT_LOW_BATTERY)
            .build()

        val mockResponseContent = ByteArrayOutputStream().apply { mockResponse.writeTo(this) }
        coEvery { client.query(any(), any()) } returns mockResponseContent

        // Act
        val result = api.startOfflineExerciseV2(deviceId, PolarExerciseSession.SportProfile.RUNNING)

        // Assert
        Assert.assertEquals(PolarOfflineExerciseV2Api.StartResult.LOW_BATTERY, result.result)
    }

    @Test
    fun `startOfflineExerciseV2() should return SDK_MODE result`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)

        val mockResponse = PftpResponse.PbPftpStartDmExerciseResult.newBuilder()
            .setResult(PftpResponse.PbPftpStartDmExerciseResult.PbStartDmExerciseResult.RESULT_SDK_MODE)
            .build()

        val mockResponseContent = ByteArrayOutputStream().apply { mockResponse.writeTo(this) }
        coEvery { client.query(any(), any()) } returns mockResponseContent

        // Act
        val result = api.startOfflineExerciseV2(deviceId, PolarExerciseSession.SportProfile.RUNNING)

        // Assert
        Assert.assertEquals(PolarOfflineExerciseV2Api.StartResult.SDK_MODE, result.result)
    }

    @Test
    fun `startOfflineExerciseV2() should return UNKNOWN_SPORT result`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)

        val mockResponse = PftpResponse.PbPftpStartDmExerciseResult.newBuilder()
            .setResult(PftpResponse.PbPftpStartDmExerciseResult.PbStartDmExerciseResult.RESULT_UNKNOWN_SPORT)
            .build()

        val mockResponseContent = ByteArrayOutputStream().apply { mockResponse.writeTo(this) }
        coEvery { client.query(any(), any()) } returns mockResponseContent

        // Act
        val result = api.startOfflineExerciseV2(deviceId, PolarExerciseSession.SportProfile.OTHER_OUTDOOR)

        // Assert
        Assert.assertEquals(PolarOfflineExerciseV2Api.StartResult.UNKNOWN_SPORT, result.result)
    }

    @Test
    fun `startOfflineExerciseV2() should use default directory path when not provided`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)

        val mockResponse = PftpResponse.PbPftpStartDmExerciseResult.newBuilder()
            .setResult(PftpResponse.PbPftpStartDmExerciseResult.PbStartDmExerciseResult.RESULT_SUCCESS)
            .build()

        val mockResponseContent = ByteArrayOutputStream().apply { mockResponse.writeTo(this) }
        coEvery { client.query(any(), any()) } returns mockResponseContent

        // Act
        val result = api.startOfflineExerciseV2(deviceId, PolarExerciseSession.SportProfile.RUNNING)

        // Assert
        Assert.assertEquals("/", result.directoryPath)
    }

    @Test
    fun `startOfflineExerciseV2() should throw PolarDeviceNotFound when no session`() = runTest {
        // Arrange
        val listener = mockk<BleDeviceListener>()
        val sessions = mockk<Set<BleDeviceSession>>()
        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns false

        val api = PolarOfflineExerciseV2ApiImpl(listener)

        // Act & Assert
        try {
            api.startOfflineExerciseV2(deviceId, PolarExerciseSession.SportProfile.RUNNING)
            Assert.fail("Expected PolarDeviceNotFound")
        } catch (e: PolarDeviceNotFound) {
            // expected
        }
    }

    @Test
    fun `startOfflineExerciseV2() should throw PolarServiceNotAvailable when client not available`() = runTest {
        // Arrange
        val (_, listener, session) = mockBleConnectionWithoutClient(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)
        every { session.fetchClient(any()) } returns null

        // Act & Assert
        try {
            api.startOfflineExerciseV2(deviceId, PolarExerciseSession.SportProfile.RUNNING)
            Assert.fail("Expected PolarServiceNotAvailable")
        } catch (e: PolarServiceNotAvailable) {
            // expected
        }
    }

    @Test
    fun `stopOfflineExerciseV2() should complete successfully`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)

        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()

        // Act
        api.stopOfflineExerciseV2(deviceId)

        // Assert
        coVerify { client.query(PftpRequest.PbPFtpQuery.STOP_EXERCISE_VALUE, any()) }
    }

    @Test
    fun `stopOfflineExerciseV2() should throw PolarServiceNotAvailable when client not available`() = runTest {
        // Arrange
        val (_, listener, session) = mockBleConnectionWithoutClient(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)
        every { session.fetchClient(any()) } returns null

        // Act & Assert
        try {
            api.stopOfflineExerciseV2(deviceId)
            Assert.fail("Expected PolarServiceNotAvailable")
        } catch (e: PolarServiceNotAvailable) {
            // expected
        }
    }

    @Test
    fun `getOfflineExerciseStatusV2() should return true when exercise is running`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)

        val mockResponse = PftpResponse.PbPftpGetExerciseStatusResult.newBuilder()
            .setExerciseType(PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseType.EXERCISE_TYPE_DATA_MERGE)
            .setExerciseState(PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState.EXERCISE_STATE_RUNNING)
            .build()

        val mockResponseContent = ByteArrayOutputStream().apply { mockResponse.writeTo(this) }
        coEvery { client.query(any(), any()) } returns mockResponseContent

        // Act
        val result = api.getOfflineExerciseStatusV2(deviceId)

        // Assert
        Assert.assertEquals(true, result)
        coVerify { client.query(PftpRequest.PbPFtpQuery.GET_EXERCISE_STATUS_VALUE, any()) }
    }

    @Test
    fun `getOfflineExerciseStatusV2() should return false when exercise is paused`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)

        val mockResponse = PftpResponse.PbPftpGetExerciseStatusResult.newBuilder()
            .setExerciseType(PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseType.EXERCISE_TYPE_DATA_MERGE)
            .setExerciseState(PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState.EXERCISE_STATE_PAUSED)
            .build()

        val mockResponseContent = ByteArrayOutputStream().apply { mockResponse.writeTo(this) }
        coEvery { client.query(any(), any()) } returns mockResponseContent

        // Act
        val result = api.getOfflineExerciseStatusV2(deviceId)

        // Assert
        Assert.assertEquals(false, result)
    }

    @Test
    fun `getOfflineExerciseStatusV2() should return false when exercise type is not DATA_MERGE`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)

        val mockResponse = PftpResponse.PbPftpGetExerciseStatusResult.newBuilder()
            .setExerciseType(PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseType.EXERCISE_TYPE_NORMAL)
            .setExerciseState(PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState.EXERCISE_STATE_RUNNING)
            .build()

        val mockResponseContent = ByteArrayOutputStream().apply { mockResponse.writeTo(this) }
        coEvery { client.query(any(), any()) } returns mockResponseContent

        // Act
        val result = api.getOfflineExerciseStatusV2(deviceId)

        // Assert
        Assert.assertEquals(false, result)
    }

    @Test
    fun `fetchOfflineExerciseV2() should return heart rate samples`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)

        val entry = PolarExerciseEntry(
            path = "/exercise/session1/SAMPLES.BPB",
            date = LocalDateTime.now(),
            identifier = "SAMPLES.BPB"
        )

        val hrSamples = listOf(60, 62, 65, 70)
        val mockResponse = PbExerciseSamples.newBuilder()
            .setRecordingInterval(Types.PbDuration.newBuilder().setSeconds(1).build())
            .addAllHeartRateSamples(hrSamples)
            .build()

        val mockResponseContent = ByteArrayOutputStream().apply { mockResponse.writeTo(this) }
        coEvery { client.request(any()) } returns mockResponseContent

        // Act
        val result = api.fetchOfflineExerciseV2(deviceId, entry)

        // Assert
        coVerify {
            client.request(match { bytes ->
                val operation = PftpRequest.PbPFtpOperation.parseFrom(bytes)
                operation.command == PftpRequest.PbPFtpOperation.Command.GET &&
                operation.path == entry.path
            })
        }
        Assert.assertEquals(1, result.recordingInterval)
        Assert.assertEquals(hrSamples, result.hrSamples)
    }

    @Test
    fun `fetchOfflineExerciseV2() should throw PolarServiceNotAvailable when client not available`() = runTest {
        // Arrange
        val (_, listener, session) = mockBleConnectionWithoutClient(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)

        val entry = PolarExerciseEntry(
            path = "/exercise/session1/SAMPLES.BPB",
            date = LocalDateTime.now(),
            identifier = "SAMPLES.BPB"
        )

        every { session.fetchClient(any()) } returns null

        // Act & Assert
        try {
            api.fetchOfflineExerciseV2(deviceId, entry)
            Assert.fail("Expected PolarServiceNotAvailable")
        } catch (e: PolarServiceNotAvailable) {
            // expected
        }
    }

    @Test
    fun `removeOfflineExerciseV2() should complete successfully`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)

        val entry = PolarExerciseEntry(
            path = "/exercise/session1/SAMPLES.BPB",
            date = LocalDateTime.now(),
            identifier = "SAMPLES.BPB"
        )

        coEvery { client.request(any()) } returns ByteArrayOutputStream()

        // Act
        api.removeOfflineExerciseV2(deviceId, entry)

        // Assert
        coVerify {
            client.request(match { bytes ->
                val operation = PftpRequest.PbPFtpOperation.parseFrom(bytes)
                operation.command == PftpRequest.PbPFtpOperation.Command.REMOVE &&
                operation.path == entry.path
            })
        }
    }

    @Test
    fun `removeOfflineExerciseV2() should throw PolarServiceNotAvailable when client not available`() = runTest {
        // Arrange
        val (_, listener, session) = mockBleConnectionWithoutClient(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)

        val entry = PolarExerciseEntry(
            path = "/exercise/session1/SAMPLES.BPB",
            date = LocalDateTime.now(),
            identifier = "SAMPLES.BPB"
        )

        every { session.fetchClient(any()) } returns null

        // Act & Assert
        try {
            api.removeOfflineExerciseV2(deviceId, entry)
            Assert.fail("Expected PolarServiceNotAvailable")
        } catch (e: PolarServiceNotAvailable) {
            // expected
        }
    }

    @Test
    fun `test StartResult enum values`() {
        // Arrange
        // Act
        val results = PolarOfflineExerciseV2Api.StartResult.values()

        // Assert
        Assert.assertEquals(6, results.size)
        Assert.assertEquals(PolarOfflineExerciseV2Api.StartResult.SUCCESS, results[0])
        Assert.assertEquals(PolarOfflineExerciseV2Api.StartResult.EXERCISE_ONGOING, results[1])
        Assert.assertEquals(PolarOfflineExerciseV2Api.StartResult.LOW_BATTERY, results[2])
        Assert.assertEquals(PolarOfflineExerciseV2Api.StartResult.SDK_MODE, results[3])
        Assert.assertEquals(PolarOfflineExerciseV2Api.StartResult.UNKNOWN_SPORT, results[4])
        Assert.assertEquals(PolarOfflineExerciseV2Api.StartResult.OTHER, results[5])
    }

    private fun mockBleConnection(deviceId: String): Pair<BlePsFtpClient, BleDeviceListener> {
        val client = mockk<BlePsFtpClient>()
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) } returns client
        every { client.isServiceDiscovered } returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0)

        return Pair(client, listener)
    }

    private fun mockBleConnectionWithoutClient(deviceId: String): Triple<BlePsFtpClient, BleDeviceListener, BleDeviceSession> {
        val client = mockk<BlePsFtpClient>()
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { client.isServiceDiscovered } returns false

        return Triple(client, listener, session)
    }
}