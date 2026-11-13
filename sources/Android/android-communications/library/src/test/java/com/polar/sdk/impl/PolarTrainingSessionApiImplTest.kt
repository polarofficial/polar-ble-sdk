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
import com.polar.sdk.api.errors.PolarSportProfileNotFound
import com.polar.sdk.api.errors.PolarServiceNotAvailable
import com.polar.sdk.api.model.PolarExerciseSession
import com.polar.sdk.api.model.trainingsession.PolarTrainingSession
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionFetchResult
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionReference
import com.polar.sdk.impl.utils.PolarServiceClientUtils
import com.polar.sdk.impl.utils.PolarTrainingSessionUtils
import com.google.protobuf.CodedOutputStream
import fi.polar.remote.representation.protobuf.SportProfile
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
import kotlinx.coroutines.CancellationException
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
        mockSportProfiles(client, 1L, 16L, 1234L, Int.MAX_VALUE.toLong() + 1, Long.MAX_VALUE)
        return Pair(client, listener)
    }

    private fun directory(vararg names: String): ByteArray = PftpResponse.PbPFtpDirectory.newBuilder()
        .addAllEntries(names.map { PftpResponse.PbPFtpEntry.newBuilder().setName(it).setSize(0).build() })
        .build().toByteArray()

    private fun mockSportProfiles(client: BlePsFtpClient, vararg sportIds: Long): MutableMap<String, ByteArray> {
        val files = mutableMapOf("/U/0/SPROF/" to directory(*sportIds.indices.map { "$it/" }.toTypedArray()))
        sportIds.forEachIndexed { index, sportId ->
            files["/U/0/SPROF/$index/"] = directory("PROFILE.BPB")
            files["/U/0/SPROF/$index/PROFILE.BPB"] = SportProfile.PbSportProfile.newBuilder()
                .setSportIdentifier(Structures.PbSportIdentifier.newBuilder().setValue(sportId))
                .build().toByteArray()
        }
        coEvery { client.request(any()) } answers {
            val operation = PftpRequest.PbPFtpOperation.parseFrom(firstArg<ByteArray>())
            assertEquals(PftpRequest.PbPFtpOperation.Command.GET, operation.command)
            ByteArrayOutputStream().apply { write(files.getValue(operation.path)) }
        }
        return files
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

        mockSportProfiles(client, 1L, 16L, 1234L)
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
    fun `startExercise accepts sport IDs outside the legacy enum without truncation`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarTrainingSessionApiImpl(listener)
        val payloads = mutableListOf<ByteArray>()
        val sportIds = listOf(1234L, Int.MAX_VALUE.toLong() + 1, Long.MAX_VALUE)
        coEvery { client.query(PftpRequest.PbPFtpQuery.START_EXERCISE_VALUE, capture(payloads)) } returns ByteArrayOutputStream()

        // Act
        sportIds.forEach { api.startExercise(deviceId, sportId = it) }

        // Assert
        val encodedIds = payloads.map { PftpRequest.PbPFtpStartExerciseParams.parseFrom(it).sportIdentifier.value }
        assertEquals(sportIds, encodedIds)
    }

    @Test
    fun `startExercise without profile uses other outdoor`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarTrainingSessionApiImpl(listener)
        val payloads = mutableListOf<ByteArray>()
        coEvery { client.query(PftpRequest.PbPFtpQuery.START_EXERCISE_VALUE, capture(payloads)) } returns ByteArrayOutputStream()

        // Act
        api.startExercise(deviceId)

        // Assert
        val params = PftpRequest.PbPFtpStartExerciseParams.parseFrom(payloads.single())
        assertEquals(PolarExerciseSession.SportProfile.OTHER_OUTDOOR.id.toLong(), params.sportIdentifier.value)
    }

    @Test
    fun `startExercise rejects nonpositive sport IDs before sending any query`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarTrainingSessionApiImpl(listener)

        for (sportId in listOf(0L, -1L, Long.MIN_VALUE)) {
            // Act & Assert
            Assert.assertThrows(IllegalArgumentException::class.java) {
                runBlocking { api.startExercise(deviceId, sportId) }
            }
        }
        coVerify(exactly = 0) { client.query(any(), any()) }
        coVerify(exactly = 0) { client.request(any()) }
    }

    @Test
    fun `startExercise scans every folder and ignores unrelated profile fields`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarTrainingSessionApiImpl(listener)
        val files = mockSportProfiles(client, 55L, 16L)
        files["/U/0/SPROF/"] = directory("0/", "1/", "empty/", "README.TXT")
        files["/U/0/SPROF/empty/"] = directory("OTHER.BPB")
        val fullProfile = ByteArrayOutputStream().apply {
            write(files.getValue("/U/0/SPROF/0/PROFILE.BPB"))
            CodedOutputStream.newInstance(this).apply {
                writeUInt64(1, 999L) // Profile identifier is not the sport identifier.
                writeByteArray(3, byteArrayOf(0x08, 0x01)) // Unused settings.
                writeByteArray(226, byteArrayOf(0x08, 0x01)) // Unused profile info.
                flush()
            }
        }
        files["/U/0/SPROF/0/PROFILE.BPB"] = fullProfile.toByteArray()
        val paths = mutableListOf<String>()
        coEvery { client.request(any()) } answers {
            val operation = PftpRequest.PbPFtpOperation.parseFrom(firstArg<ByteArray>())
            assertEquals(PftpRequest.PbPFtpOperation.Command.GET, operation.command)
            paths.add(operation.path)
            ByteArrayOutputStream().apply { write(files.getValue(operation.path)) }
        }
        coEvery { client.query(PftpRequest.PbPFtpQuery.START_EXERCISE_VALUE, any()) } answers {
            assertEquals(listOf("/U/0/SPROF/", "/U/0/SPROF/0/", "/U/0/SPROF/0/PROFILE.BPB",
                "/U/0/SPROF/1/", "/U/0/SPROF/1/PROFILE.BPB", "/U/0/SPROF/empty/"), paths)
            ByteArrayOutputStream()
        }

        // Act
        api.startExercise(deviceId, 55L)

        // Assert
        coVerify(exactly = 1) { client.query(PftpRequest.PbPFtpQuery.START_EXERCISE_VALUE, any()) }
        coVerify(exactly = 6) { client.request(any()) }
    }

    @Test
    fun `startExercise blocks sport absent from profiles even when folder name matches`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarTrainingSessionApiImpl(listener)
        val files = mockSportProfiles(client, 16L)
        files["/U/0/SPROF/"] = directory("55/")
        files["/U/0/SPROF/55/"] = directory("PROFILE.BPB")
        files["/U/0/SPROF/55/PROFILE.BPB"] = files.getValue("/U/0/SPROF/0/PROFILE.BPB")

        // Act
        val error = Assert.assertThrows(PolarSportProfileNotFound::class.java) {
            runBlocking { api.startExercise(deviceId, 55L) }
        }

        // Assert
        assertEquals("No sport profile for sport ID 55 in /U/0/SPROF/. Available sport IDs: [16]", error.message)
        coVerify(exactly = 0) { client.query(any(), any()) }
    }

    @Test
    fun `startExercise blocks default sport when profile directory is empty`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarTrainingSessionApiImpl(listener)
        mockSportProfiles(client)

        // Act
        val error = Assert.assertThrows(PolarSportProfileNotFound::class.java) {
            runBlocking { api.startExercise(deviceId) }
        }

        // Assert
        assertTrue(error.message.orEmpty().contains("No sport profile for sport ID 16"))
        coVerify(exactly = 0) { client.query(any(), any()) }
    }

    @Test
    fun `startExercise blocks when folders contain no PROFILE BPB`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarTrainingSessionApiImpl(listener)
        val files = mockSportProfiles(client, 55L)
        files["/U/0/SPROF/0/"] = directory("OTHER.BPB")

        // Act
        Assert.assertThrows(PolarSportProfileNotFound::class.java) {
            runBlocking { api.startExercise(deviceId, 55L) }
        }

        // Assert
        coVerify(exactly = 2) { client.request(any()) }
        coVerify(exactly = 0) { client.query(any(), any()) }
    }

    @Test
    fun `startExercise blocks on unreadable directories or profiles`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarTrainingSessionApiImpl(listener)
        for (failingPath in listOf("/U/0/SPROF/", "/U/0/SPROF/0/", "/U/0/SPROF/0/PROFILE.BPB")) {
            val files = mockSportProfiles(client, 55L)
            coEvery { client.request(any()) } answers {
                val path = PftpRequest.PbPFtpOperation.parseFrom(firstArg<ByteArray>()).path
                if (path == failingPath) throw IllegalStateException("read failed: $path")
                ByteArrayOutputStream().apply { write(files.getValue(path)) }
            }

            // Act
            val error = Assert.assertThrows(Exception::class.java) {
                runBlocking { api.startExercise(deviceId, 55L) }
            }

            // Assert
            assertTrue(error.message.orEmpty().contains("read failed: $failingPath"))
        }
        coVerify(exactly = 0) { client.query(any(), any()) }
    }

    @Test
    fun `startExercise blocks malformed profiles or missing required sport identifier`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarTrainingSessionApiImpl(listener)
        val files = mockSportProfiles(client, 55L)
        for (data in listOf(byteArrayOf(0x12, 0x02, 0x08), byteArrayOf(), byteArrayOf(0x08, 0x37))) {
            files["/U/0/SPROF/0/PROFILE.BPB"] = data

            // Act & Assert
            Assert.assertThrows(Exception::class.java) {
                runBlocking { api.startExercise(deviceId, 55L) }
            }
        }
        coVerify(exactly = 0) { client.query(any(), any()) }
    }

    @Test
    fun `startExercise rereads profiles on every start`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarTrainingSessionApiImpl(listener)
        val files = mockSportProfiles(client, 55L)
        coEvery { client.query(PftpRequest.PbPFtpQuery.START_EXERCISE_VALUE, any()) } returns ByteArrayOutputStream()

        // Act
        api.startExercise(deviceId, 55L)
        files["/U/0/SPROF/"] = directory()
        val error = Assert.assertThrows(PolarSportProfileNotFound::class.java) {
            runBlocking { api.startExercise(deviceId, 55L) }
        }

        // Assert
        assertTrue(error.message.orEmpty().contains("No sport profile"))
        coVerify(exactly = 1) { client.query(any(), any()) }
    }

    @Test
    fun `startExercise preserves cancellation during profile discovery`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarTrainingSessionApiImpl(listener)
        coEvery { client.request(any()) } throws CancellationException("cancelled")

        // Act & Assert
        Assert.assertThrows(CancellationException::class.java) {
            runBlocking { api.startExercise(deviceId, 55L) }
        }
        coVerify(exactly = 0) { client.query(any(), any()) }
    }

    @Test
    fun `getExerciseStatus preserves numeric sport IDs without aliasing legacy enums`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarTrainingSessionApiImpl(listener)
        for (sportId in listOf(1234L, (1L shl 32) + 1, Long.MAX_VALUE)) {
            // Arrange
            val status = PftpResponse.PbPftpGetExerciseStatusResult.newBuilder()
                .setExerciseState(PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState.EXERCISE_STATE_RUNNING)
                .setSportIdentifier(Structures.PbSportIdentifier.newBuilder().setValue(sportId))
                .build()
            coEvery { client.query(PftpRequest.PbPFtpQuery.GET_EXERCISE_STATUS_VALUE, any()) } returns
                ByteArrayOutputStream().apply { write(status.toByteArray()) }

            // Act
            val result = api.getExerciseStatus(deviceId)

            // Assert
            assertEquals(sportId, result.sportId)
            assertEquals(PolarExerciseSession.SportProfile.UNKNOWN, result.sportProfile)
        }
    }

    @Test
    fun `observeExerciseStatus preserves numeric sport IDs`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarTrainingSessionApiImpl(listener)
        val notification = buildExerciseStatusNotification(
            PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState.EXERCISE_STATE_PAUSED,
            sportProfileId = 1234
        )
        every { client.waitForNotification() } returns flowOf(notification)

        // Act
        val result = api.observeExerciseStatus(deviceId).take(1).toList().single()

        // Assert
        assertEquals(1234L, result.sportId)
        assertEquals(PolarExerciseSession.SportProfile.UNKNOWN, result.sportProfile)
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
        val capturedParams = mutableListOf<ByteArray?>()
        coEvery {
            client.query(
                PftpRequest.PbPFtpQuery.STOP_EXERCISE_VALUE,
                captureNullable(capturedParams)
            )
        } returns ByteArrayOutputStream()
        api.stopExercise(deviceId)
        coVerify { client.query(PftpRequest.PbPFtpQuery.STOP_EXERCISE_VALUE, any()) }
        val params = PftpRequest.PbPFtpStopExerciseParams.parseFrom(capturedParams[0])
        Assert.assertTrue("save should default to true", params.save)
    }

    @Test
    fun `stopExercise with save=false sends save=false in params`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarTrainingSessionApiImpl(listener)
        val capturedParams = mutableListOf<ByteArray?>()
        coEvery {
            client.query(
                PftpRequest.PbPFtpQuery.STOP_EXERCISE_VALUE,
                captureNullable(capturedParams)
            )
        } returns ByteArrayOutputStream()
        api.stopExercise(deviceId, save = false)
        coVerify { client.query(PftpRequest.PbPFtpQuery.STOP_EXERCISE_VALUE, any()) }
        val params = PftpRequest.PbPFtpStopExerciseParams.parseFrom(capturedParams[0])
        Assert.assertFalse("save should be false when explicitly passed as false", params.save)
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
    fun `startExercise facade forwards numeric sport and default profile`() = runTest {
        // Arrange
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TRAINING_DATA))
        val (client, _) = mockPsFtpConnection(deviceId)
        val payloads = mutableListOf<ByteArray>()
        coEvery { client.query(PftpRequest.PbPFtpQuery.START_EXERCISE_VALUE, capture(payloads)) } returns ByteArrayOutputStream()

        try {
            // Act
            api.startExercise(deviceId, sportId = 1234L)
            api.startExercise(deviceId)

            // Assert
            assertEquals(listOf(1234L, 16L), payloads.map {
                PftpRequest.PbPFtpStartExerciseParams.parseFrom(it).sportIdentifier.value
            })
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

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

    @Test
    fun `stopExercise with save=false sends STOP_EXERCISE_VALUE query with save=false`() = runTest {
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
            api.stopExercise(deviceId, save = false)
        } finally {
            io.mockk.unmockkObject(PolarServiceClientUtils)
        }

        // Assert
        Assert.assertTrue(capturedIds.contains(PftpRequest.PbPFtpQuery.STOP_EXERCISE_VALUE))
        val params = PftpRequest.PbPFtpStopExerciseParams.parseFrom(capturedParams[0])
        Assert.assertFalse("save should be false when discarding", params.save)
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