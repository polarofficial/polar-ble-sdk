// Copyright (c) 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.content.Context
import android.content.IntentFilter
import android.os.ParcelUuid
import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.api.ble.model.advertisement.BleAdvertisementContent
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.errors.PolarDeviceNotFound
import com.polar.sdk.api.errors.PolarServiceNotAvailable
import com.polar.sdk.api.model.PolarExerciseSession
import com.polar.sdk.api.model.trainingsession.PolarTrainingSession
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionFetchResult
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionReference
import com.polar.sdk.impl.utils.PolarServiceClientUtils
import com.polar.sdk.impl.utils.PolarTrainingSessionUtils
import fi.polar.remote.representation.protobuf.Structures
import io.mockk.MockK
import io.mockk.MockKDsl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import protocol.PftpNotification
import protocol.PftpRequest
import protocol.PftpResponse
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

internal class PolarTrainingSessionApiImplTest {

    private val deviceId = "B1C2D3"
    private lateinit var context: Context

    @Before
    fun setUp() {
        mockkObject(PolarTrainingSessionUtils)

        val bluetoothAdapter = mockk<BluetoothAdapter>(relaxed = true)
        val bluetoothManager = mockk<BluetoothManager>(relaxed = true)
        every { bluetoothManager.adapter } returns bluetoothAdapter

        mockkStatic(ParcelUuid::class)
        every { ParcelUuid.fromString(any()) } returns mockk(relaxed = true)

        mockkConstructor(IntentFilter::class)
        every { anyConstructed<IntentFilter>().addAction(any()) } just runs

        mockkConstructor(ScanFilter.Builder::class)
        every { anyConstructed<ScanFilter.Builder>().setServiceUuid(any()) } answers { self as ScanFilter.Builder }
        every { anyConstructed<ScanFilter.Builder>().setServiceUuid(null) } answers { self as ScanFilter.Builder }
        every { anyConstructed<ScanFilter.Builder>().setManufacturerData(any(), any()) } answers { self as ScanFilter.Builder }
        every { anyConstructed<ScanFilter.Builder>().build() } returns mockk(relaxed = true)

        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        every { context.getSystemService(Context.BLUETOOTH_SERVICE) } returns bluetoothManager
        every { context.registerReceiver(any(), any<IntentFilter>()) } returns null
    }

    @After
    fun tearDown() {
        unmockkAll()
        BDBleApiImpl.clearInstance()
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

    private fun mockNoSession(): BleDeviceListener {
        val listener = mockk<BleDeviceListener>()
        val sessions = mockk<Set<BleDeviceSession>>()
        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns false
        return listener
    }

    private fun mockNoClient(deviceId: String): BleDeviceListener {
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
        every { session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) } returns null
        every { session.fetchClient(any()) } returns null
        return listener
    }

    private fun makeReference() = PolarTrainingSessionReference(
        date = LocalDate.of(2024, 5, 1),
        path = "/U/0/20240501T120000/",
        trainingDataTypes = emptyList(),
        exercises = emptyList(),
        fileSize = 1024L
    )

    private fun mockPsFtpConnection(deviceId: String): Pair<BlePsFtpClient, BleDeviceSession> {
        val client = mockk<BlePsFtpClient>()
        val session = mockk<BleDeviceSession>()
        val advContent = mockk<BleAdvertisementContent>()

        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) } returns client
        every { client.isServiceDiscovered } returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0)

        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, any()) } returns session

        return Pair(client, session)
    }

    inline fun unmockkObject(vararg objects: Any): Unit =
        MockK.useImpl {
            MockKDsl.internalUnmockkObject(objects)
        }

    @Test
    fun `getTrainingSession returns session from utility`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarTrainingSessionApiImpl(listener)
        val reference = makeReference()
        val expectedSession = mockk<PolarTrainingSession>()
        coEvery {
            PolarTrainingSessionUtils.readTrainingSession(
                client,
                reference
            )
        } returns expectedSession
        val result = api.getTrainingSession(deviceId, reference)
        assertEquals(expectedSession, result)
    }

    @Test
    fun `getTrainingSession throws PolarDeviceNotFound when no session`() = runTest {
        val api = PolarTrainingSessionApiImpl(mockNoSession())
        try {
            api.getTrainingSession(deviceId, makeReference())
            assert(false) { "Expected PolarDeviceNotFound" }
        } catch (e: PolarDeviceNotFound) { /* expected */
        }
    }

    @Test
    fun `getTrainingSession throws PolarServiceNotAvailable when no client`() = runTest {
        val api = PolarTrainingSessionApiImpl(mockNoClient(deviceId))
        try {
            api.getTrainingSession(deviceId, makeReference())
            assert(false) { "Expected PolarServiceNotAvailable" }
        } catch (e: PolarServiceNotAvailable) { /* expected */
        }
    }

    @Test
    fun `getTrainingSessionReferences throws PolarServiceNotAvailable when session not found`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(
            context,
            setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TRAINING_DATA)
        )
        mockkObject(PolarServiceClientUtils)
        every {
            PolarServiceClientUtils.sessionPsFtpClientReady(
                deviceId,
                any()
            )
        } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.getTrainingSessionReferences(deviceId, null, null).toList() }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `getTrainingSessionWithProgress emits initial Progress then Complete`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarTrainingSessionApiImpl(listener)
        val reference = makeReference()
        val trainingSession = mockk<PolarTrainingSession>()
        every { client.setProgressCallback(any()) } returns Unit
        coEvery {
            PolarTrainingSessionUtils.readTrainingSessionWithProgress(
                client,
                reference
            )
        } returns trainingSession
        val items = api.getTrainingSessionWithProgress(deviceId, reference).toList()
        assertTrue(items.isNotEmpty())
        assertTrue(items.first() is PolarTrainingSessionFetchResult.Progress)
        assertTrue(items.last() is PolarTrainingSessionFetchResult.Complete)
    }

    @Test
    fun `deleteTrainingSession calls utility delete`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarTrainingSessionApiImpl(listener)
        val reference = makeReference()
        coEvery { PolarTrainingSessionUtils.deleteTrainingSession(client, reference) } returns Unit
        api.deleteTrainingSession(deviceId, reference)
        coVerify { PolarTrainingSessionUtils.deleteTrainingSession(client, reference) }
    }

    @Test
    fun `deleteTrainingSession throws PolarServiceNotAvailable when no client`() = runTest {
        val api = PolarTrainingSessionApiImpl(mockNoClient(deviceId))
        try {
            api.deleteTrainingSession(deviceId, makeReference())
            assert(false) { "Expected PolarServiceNotAvailable" }
        } catch (e: PolarServiceNotAvailable) { /* expected */
        }
    }


    @Test
    fun `startExercise sends query with correct sport profile`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarTrainingSessionApiImpl(listener)
        val capturedPayloads = mutableListOf<ByteArray>()
        coEvery {
            client.query(
                PftpRequest.PbPFtpQuery.START_EXERCISE_VALUE,
                capture(capturedPayloads)
            )
        } returns ByteArrayOutputStream()

        api.startExercise(deviceId, PolarExerciseSession.SportProfile.RUNNING)

        coVerify { client.query(PftpRequest.PbPFtpQuery.START_EXERCISE_VALUE, any()) }
        val params = PftpRequest.PbPFtpStartExerciseParams.parseFrom(capturedPayloads.last())
        assertEquals(PolarExerciseSession.SportProfile.RUNNING.id.toLong(), params.sportIdentifier.value)
    }

    @Test
    fun `startExercise throws PolarServiceNotAvailable when no client`() = runTest {
        val api = PolarTrainingSessionApiImpl(mockNoClient(deviceId))
        try {
            api.startExercise(deviceId, PolarExerciseSession.SportProfile.RUNNING)
            assert(false) { "Expected PolarServiceNotAvailable" }
        } catch (e: PolarServiceNotAvailable) { /* expected */
        }
    }


    @Test
    fun `pauseExercise sends correct query`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarTrainingSessionApiImpl(listener)
        coEvery {
            client.query(
                PftpRequest.PbPFtpQuery.PAUSE_EXERCISE_VALUE,
                any()
            )
        } returns ByteArrayOutputStream()
        api.pauseExercise(deviceId)
        coVerify { client.query(PftpRequest.PbPFtpQuery.PAUSE_EXERCISE_VALUE, any()) }
    }


    @Test
    fun `resumeExercise sends correct query`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarTrainingSessionApiImpl(listener)
        coEvery {
            client.query(
                PftpRequest.PbPFtpQuery.RESUME_EXERCISE_VALUE,
                any()
            )
        } returns ByteArrayOutputStream()
        api.resumeExercise(deviceId)
        coVerify { client.query(PftpRequest.PbPFtpQuery.RESUME_EXERCISE_VALUE, any()) }
    }


    @Test
    fun `stopExercise sends stop query with save flag`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarTrainingSessionApiImpl(listener)
        coEvery {
            client.query(
                PftpRequest.PbPFtpQuery.STOP_EXERCISE_VALUE,
                any()
            )
        } returns ByteArrayOutputStream()
        api.stopExercise(deviceId)
        coVerify { client.query(PftpRequest.PbPFtpQuery.STOP_EXERCISE_VALUE, any()) }
    }


    @Test
    fun `getExerciseStatus parses RUNNING state correctly`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarTrainingSessionApiImpl(listener)
        val proto = PftpResponse.PbPftpGetExerciseStatusResult.newBuilder()
            .setExerciseState(PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState.EXERCISE_STATE_RUNNING)
            .build()
        val responseBytes = ByteArrayOutputStream().apply { proto.writeTo(this) }
        coEvery {
            client.query(
                PftpRequest.PbPFtpQuery.GET_EXERCISE_STATUS_VALUE,
                any()
            )
        } returns responseBytes
        val info = api.getExerciseStatus(deviceId)
        assertEquals(PolarExerciseSession.ExerciseStatus.IN_PROGRESS, info.status)
    }

    @Test
    fun `getExerciseStatus parses PAUSED state correctly`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarTrainingSessionApiImpl(listener)
        val proto = PftpResponse.PbPftpGetExerciseStatusResult.newBuilder()
            .setExerciseState(PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState.EXERCISE_STATE_PAUSED)
            .build()
        val responseBytes = ByteArrayOutputStream().apply { proto.writeTo(this) }
        coEvery {
            client.query(
                PftpRequest.PbPFtpQuery.GET_EXERCISE_STATUS_VALUE,
                any()
            )
        } returns responseBytes
        val info = api.getExerciseStatus(deviceId)
        assertEquals(PolarExerciseSession.ExerciseStatus.PAUSED, info.status)
    }

    @Test
    fun `getExerciseStatus parses STOPPED state correctly`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarTrainingSessionApiImpl(listener)
        val proto = PftpResponse.PbPftpGetExerciseStatusResult.newBuilder()
            .setExerciseState(PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState.EXERCISE_STATE_OFF)
            .build()
        val responseBytes = ByteArrayOutputStream().apply { proto.writeTo(this) }
        coEvery {
            client.query(
                PftpRequest.PbPFtpQuery.GET_EXERCISE_STATUS_VALUE,
                any()
            )
        } returns responseBytes
        val info = api.getExerciseStatus(deviceId)
        assertEquals(PolarExerciseSession.ExerciseStatus.STOPPED, info.status)
    }

    @Test
    fun `getExerciseStatus throws PolarServiceNotAvailable when no client`() = runTest {
        val api = PolarTrainingSessionApiImpl(mockNoClient(deviceId))
        try {
            api.getExerciseStatus(deviceId)
            assert(false) { "Expected PolarServiceNotAvailable" }
        } catch (e: PolarServiceNotAvailable) { /* expected */
        }
    }

    // ── observeExerciseStatus ──────────────────────────────────────────────

    /** Builds an EXERCISE_STATUS notification message carrying the given proto payload. */
    private fun buildExerciseStatusNotification(
        state: PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState,
        sportProfileId: Int? = null
    ): BlePsFtpUtils.PftpNotificationMessage {
        val builder = PftpResponse.PbPftpGetExerciseStatusResult.newBuilder()
            .setExerciseState(state)
        if (sportProfileId != null) {
            builder.setSportIdentifier(
                Structures.PbSportIdentifier.newBuilder().setValue(sportProfileId.toLong()).build()
            )
        }
        return BlePsFtpUtils.PftpNotificationMessage().apply {
            id = PftpNotification.PbPFtpDevToHostNotification.EXERCISE_STATUS_VALUE
            byteArrayOutputStream.write(builder.build().toByteArray())
        }
    }

    @Test
    fun `observeExerciseStatus emits IN_PROGRESS when device sends RUNNING state`() = runTest {
        // Arrange
        val deviceId = "A1B2C3D4"
        val api = BDBleApiImpl.getInstance(
            context,
            setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING)
        )
        val (client, _) = mockPsFtpConnection(deviceId)
        val notification = buildExerciseStatusNotification(
            PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState.EXERCISE_STATE_RUNNING
        )
        every { client.waitForNotification() } returns flowOf(notification)

        try {
            // Act
            val result = api.observeExerciseStatus(deviceId).take(1).toList()

            // Assert
            Assert.assertEquals(1, result.size)
            Assert.assertEquals(PolarExerciseSession.ExerciseStatus.IN_PROGRESS, result[0].status)
        } finally {
            io.mockk.unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `observeExerciseStatus emits PAUSED when device sends PAUSED state`() = runTest {
        // Arrange
        val deviceId = "A1B2C3D4"
        val api = BDBleApiImpl.getInstance(
            context,
            setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING)
        )
        val (client, _) = mockPsFtpConnection(deviceId)
        val notification = buildExerciseStatusNotification(
            PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState.EXERCISE_STATE_PAUSED
        )
        every { client.waitForNotification() } returns flowOf(notification)

        try {
            // Act
            val result = api.observeExerciseStatus(deviceId).take(1).toList()

            // Assert
            Assert.assertEquals(PolarExerciseSession.ExerciseStatus.PAUSED, result[0].status)
        } finally {
            io.mockk.unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `observeExerciseStatus emits STOPPED when device sends OFF state`() = runTest {
        // Arrange
        val deviceId = "A1B2C3D4"
        val api = BDBleApiImpl.getInstance(
            context,
            setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING)
        )
        val (client, _) = mockPsFtpConnection(deviceId)
        val notification = buildExerciseStatusNotification(
            PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState.EXERCISE_STATE_OFF
        )
        every { client.waitForNotification() } returns flowOf(notification)

        try {
            // Act
            val result = api.observeExerciseStatus(deviceId).take(1).toList()

            // Assert
            Assert.assertEquals(PolarExerciseSession.ExerciseStatus.STOPPED, result[0].status)
        } finally {
            io.mockk.unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `observeExerciseStatus emits NOT_STARTED when device sends OTHER state`() = runTest {
        // Arrange
        val deviceId = "A1B2C3D4"
        val api = BDBleApiImpl.getInstance(
            context,
            setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING)
        )
        val (client, _) = mockPsFtpConnection(deviceId)
        val notification = buildExerciseStatusNotification(
            PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState.EXERCISE_STATE_OTHER
        )
        every { client.waitForNotification() } returns flowOf(notification)

        try {
            // Act
            val result = api.observeExerciseStatus(deviceId).take(1).toList()

            // Assert
            Assert.assertEquals(PolarExerciseSession.ExerciseStatus.NOT_STARTED, result[0].status)
        } finally {
            io.mockk.unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `observeExerciseStatus resolves sport profile from notification`() = runTest {
        // Arrange
        val deviceId = "A1B2C3D4"
        val api = BDBleApiImpl.getInstance(
            context,
            setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING)
        )
        val (client, _) = mockPsFtpConnection(deviceId)
        val notification = buildExerciseStatusNotification(
            state = PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState.EXERCISE_STATE_RUNNING,
            sportProfileId = PolarExerciseSession.SportProfile.RUNNING.id
        )
        every { client.waitForNotification() } returns flowOf(notification)

        try {
            // Act
            val result = api.observeExerciseStatus(deviceId).take(1).toList()

            // Assert
            Assert.assertEquals(PolarExerciseSession.SportProfile.RUNNING, result[0].sportProfile)
        } finally {
            io.mockk.unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `observeExerciseStatus emits UNKNOWN sport profile when notification has no sport identifier`() =
        runTest {
            // Arrange
            val deviceId = "A1B2C3D4"
            val api = BDBleApiImpl.getInstance(
                context,
                setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING)
            )
            val (client, _) = mockPsFtpConnection(deviceId)
            val notification = buildExerciseStatusNotification(
                state = PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState.EXERCISE_STATE_RUNNING,
                sportProfileId = null
            )
            every { client.waitForNotification() } returns flowOf(notification)

            try {
                // Act
                val result = api.observeExerciseStatus(deviceId).take(1).toList()

                // Assert
                Assert.assertEquals(
                    PolarExerciseSession.SportProfile.UNKNOWN,
                    result[0].sportProfile
                )
            } finally {
                io.mockk.unmockkObject(PolarServiceClientUtils)
            }
        }

    @Test
    fun `observeExerciseStatus ignores notifications with non-exercise IDs`() = runTest {
        // Arrange
        val deviceId = "A1B2C3D4"
        val api = BDBleApiImpl.getInstance(
            context,
            setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING)
        )
        val (client, _) = mockPsFtpConnection(deviceId)

        // A notification with a different ID (INITIALIZE = 1) followed by an exercise notification
        val otherNotification = BlePsFtpUtils.PftpNotificationMessage().apply {
            id = PftpNotification.PbPFtpDevToHostNotification.BATTERY_STATUS_VALUE
        }
        val exerciseNotification = buildExerciseStatusNotification(
            PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState.EXERCISE_STATE_RUNNING
        )
        every { client.waitForNotification() } returns flowOf(
            otherNotification,
            exerciseNotification
        )

        try {
            // Act
            val result = api.observeExerciseStatus(deviceId).take(1).toList()

            // Assert — only the exercise notification passes the filter
            Assert.assertEquals(1, result.size)
            Assert.assertEquals(PolarExerciseSession.ExerciseStatus.IN_PROGRESS, result[0].status)
        } finally {
            io.mockk.unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `observeExerciseStatus emits multiple items from consecutive notifications`() = runTest {
        // Arrange
        val deviceId = "A1B2C3D4"
        val api = BDBleApiImpl.getInstance(
            context,
            setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING)
        )
        val (client, _) = mockPsFtpConnection(deviceId)
        val running =
            buildExerciseStatusNotification(PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState.EXERCISE_STATE_RUNNING)
        val paused =
            buildExerciseStatusNotification(PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState.EXERCISE_STATE_PAUSED)
        val stopped =
            buildExerciseStatusNotification(PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState.EXERCISE_STATE_OFF)
        every { client.waitForNotification() } returns flowOf(running, paused, stopped)

        try {
            // Act
            val results = api.observeExerciseStatus(deviceId).take(3).toList()

            // Assert
            Assert.assertEquals(3, results.size)
            Assert.assertEquals(PolarExerciseSession.ExerciseStatus.IN_PROGRESS, results[0].status)
            Assert.assertEquals(PolarExerciseSession.ExerciseStatus.PAUSED, results[1].status)
            Assert.assertEquals(PolarExerciseSession.ExerciseStatus.STOPPED, results[2].status)
        } finally {
            io.mockk.unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `observeExerciseStatus throws PolarServiceNotAvailable when device session is not found`() =
        runTest {
            // Arrange
            val deviceId = "A1B2C3D4"
            val api = BDBleApiImpl.getInstance(
                context,
                setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING)
            )

            mockkObject(PolarServiceClientUtils)
            every {
                PolarServiceClientUtils.sessionPsFtpClientReady(
                    deviceId,
                    any()
                )
            } throws PolarServiceNotAvailable()

            try {
                // Act & Assert
                Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                    runBlocking { api.observeExerciseStatus(deviceId).take(1).toList() }
                }
            } finally {
                io.mockk.unmockkObject(PolarServiceClientUtils)
            }
        }

    @Test
    fun `observeExerciseStatus throws when notification data cannot be parsed`() = runTest {
        // Arrange
        val deviceId = "A1B2C3D4"
        val api = BDBleApiImpl.getInstance(
            context,
            setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING)
        )
        val (client, _) = mockPsFtpConnection(deviceId)

        // Notification with garbage bytes — proto parsing will throw InvalidProtocolBufferException
        val malformed = BlePsFtpUtils.PftpNotificationMessage().apply {
            id = PftpNotification.PbPFtpDevToHostNotification.EXERCISE_STATUS_VALUE
            byteArrayOutputStream.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0xFD.toByte()))
        }
        every { client.waitForNotification() } returns flowOf(malformed)

        try {
            // Act & Assert — parse error propagates out of the flow
            Assert.assertThrows(Exception::class.java) {
                runBlocking { api.observeExerciseStatus(deviceId).take(1).toList() }
            }
        } finally {
            io.mockk.unmockkObject(PolarServiceClientUtils)
        }
    }

    // ── startExercise ─────────────────────────────────────────────────────────

    @Test
    fun `startExercise sends START_EXERCISE_VALUE query with sport profile`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(
            context,
            setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING)
        )
        val (client, _) = mockPsFtpConnection(deviceId)
        val capturedIds = mutableListOf<Int>()
        val capturedParams = mutableListOf<ByteArray?>()
        coEvery {
            client.query(
                capture(capturedIds),
                captureNullable(capturedParams)
            )
        } returns ByteArrayOutputStream()

        try {
            // Act
            api.startExercise(deviceId, PolarExerciseSession.SportProfile.RUNNING)
        } finally {
            io.mockk.unmockkObject(PolarServiceClientUtils)
        }

        // Assert
        Assert.assertTrue(capturedIds.contains(PftpRequest.PbPFtpQuery.START_EXERCISE_VALUE))
        val params = PftpRequest.PbPFtpStartExerciseParams.parseFrom(capturedParams[0])
        Assert.assertEquals(
            PolarExerciseSession.SportProfile.RUNNING.id.toLong(),
            params.sportIdentifier.value
        )
    }

    // ── pauseExercise ─────────────────────────────────────────────────────────

    @Test
    fun `pauseExercise sends PAUSE_EXERCISE_VALUE query`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(
            context,
            setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING)
        )
        val (client, _) = mockPsFtpConnection(deviceId)
        val capturedIds = mutableListOf<Int>()
        coEvery { client.query(capture(capturedIds), any()) } returns ByteArrayOutputStream()

        try {
            // Act
            api.pauseExercise(deviceId)
        } finally {
            io.mockk.unmockkObject(PolarServiceClientUtils)
        }

        // Assert
        Assert.assertTrue(capturedIds.contains(PftpRequest.PbPFtpQuery.PAUSE_EXERCISE_VALUE))
    }

    // ── resumeExercise ────────────────────────────────────────────────────────

    @Test
    fun `resumeExercise sends RESUME_EXERCISE_VALUE query`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(
            context,
            setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING)
        )
        val (client, _) = mockPsFtpConnection(deviceId)
        val capturedIds = mutableListOf<Int>()
        coEvery { client.query(capture(capturedIds), any()) } returns ByteArrayOutputStream()

        try {
            // Act
            api.resumeExercise(deviceId)
        } finally {
            io.mockk.unmockkObject(PolarServiceClientUtils)
        }

        // Assert
        Assert.assertTrue(capturedIds.contains(PftpRequest.PbPFtpQuery.RESUME_EXERCISE_VALUE))
    }

    // ── stopExercise ──────────────────────────────────────────────────────────

    @Test
    fun `stopExercise sends STOP_EXERCISE_VALUE query with save=true`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(
            context,
            setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING)
        )
        val (client, _) = mockPsFtpConnection(deviceId)
        val capturedIds = mutableListOf<Int>()
        val capturedParams = mutableListOf<ByteArray?>()
        coEvery {
            client.query(
                capture(capturedIds),
                captureNullable(capturedParams)
            )
        } returns ByteArrayOutputStream()

        try {
            // Act
            api.stopExercise(deviceId)
        } finally {
            io.mockk.unmockkObject(PolarServiceClientUtils)
        }

        // Assert
        Assert.assertTrue(capturedIds.contains(PftpRequest.PbPFtpQuery.STOP_EXERCISE_VALUE))
        val params = PftpRequest.PbPFtpStopExerciseParams.parseFrom(capturedParams[0])
        Assert.assertTrue("save should be true", params.save)
    }

    // ── getExerciseStatus ─────────────────────────────────────────────────────

    @Test
    fun `getExerciseStatus returns ExerciseInfo parsed from GET_EXERCISE_STATUS_VALUE response`() =
        runTest {
            // Arrange
            val deviceId = "E123456F"
            val api = BDBleApiImpl.getInstance(
                context,
                setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING)
            )
            val (client, _) = mockPsFtpConnection(deviceId)
            val proto = PftpResponse.PbPftpGetExerciseStatusResult.newBuilder()
                .setExerciseState(PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState.EXERCISE_STATE_RUNNING)
                .setSportIdentifier(
                    Structures.PbSportIdentifier.newBuilder()
                        .setValue(PolarExerciseSession.SportProfile.CYCLING.id.toLong()).build()
                )
                .build()
            val bos = ByteArrayOutputStream().apply { write(proto.toByteArray()) }
            coEvery {
                client.query(
                    PftpRequest.PbPFtpQuery.GET_EXERCISE_STATUS_VALUE,
                    any()
                )
            } returns bos

            try {
                // Act
                val result = api.getExerciseStatus(deviceId)

                // Assert
                Assert.assertEquals(PolarExerciseSession.ExerciseStatus.IN_PROGRESS, result.status)
                Assert.assertEquals(PolarExerciseSession.SportProfile.CYCLING, result.sportProfile)
            } finally {
                io.mockk.unmockkObject(PolarServiceClientUtils)
            }
        }
}