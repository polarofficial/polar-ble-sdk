package com.polar.sdk.api.model.utils

import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.api.ble.model.advertisement.BleAdvertisementContent
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility.Companion.getFileSystemType
import com.polar.sdk.api.errors.PolarDeviceNotFound
import com.polar.sdk.api.errors.PolarOperationNotSupported
import com.polar.sdk.api.errors.PolarServiceNotAvailable
import com.polar.sdk.impl.utils.PolarFileUtils
import fi.polar.remote.representation.protobuf.DailySummary
import fi.polar.remote.representation.protobuf.DailySummary.PbActivityGoalSummary
import fi.polar.remote.representation.protobuf.DailySummary.PbDailySummary
import fi.polar.remote.representation.protobuf.Types
import fi.polar.remote.representation.protobuf.Types.PbDate
import fi.polar.remote.representation.protobuf.Types.PbDuration
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import protocol.PftpRequest
import protocol.PftpResponse.PbPFtpDirectory
import protocol.PftpResponse.PbPFtpEntry
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger

class PolarFileUtilsTest {

    @Test
    fun testListFilesRecurseShallowSuccess() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val (client, listener, _) = mockBleConnection(deviceId)

        val dateDirectories = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(
                    mutableListOf(
                        PbPFtpEntry.newBuilder().setName("20250101/").setSize(8192L).build(),
                        PbPFtpEntry.newBuilder().setName("20250202/").setSize(8192L).build()
                    )
                ).build().writeTo(this)
        }

        mockkObject(BlePolarDeviceCapabilitiesUtility)
        every { getFileSystemType(any()) } returns BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2
        coEvery { client.request(any<ByteArray>()) } returns dateDirectories

        val expectedPaths = mutableListOf("/U/0/20250101/", "/U/0/20250202/")

        // Act
        val result = PolarFileUtils.getFileList(deviceId, "/U/0/", false, listener, "")

        // Assert
        Assert.assertEquals(expectedPaths, result)
        verify(exactly = 1) { client.isServiceDiscovered }
        coVerify(exactly = 1) { client.request(any()) }
    }

    @Test
    fun testListFilesRecurseDeepSuccess() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val (client, listener, _) = mockBleConnection(deviceId)

        val dateDirectories = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(
                    mutableListOf(
                        PbPFtpEntry.newBuilder().setName("20250101/").setSize(8192L).build(),
                        PbPFtpEntry.newBuilder().setName("20250202/").setSize(8192L).build()
                    )
                ).build().writeTo(this)
        }

        val actDirectory = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(
                    mutableListOf(
                        PbPFtpEntry.newBuilder().setName("ACT/").setSize(8192L).build(),
                    )
                ).build().writeTo(this)
        }

        val actFile = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(
                    mutableListOf(
                        PbPFtpEntry.newBuilder().setName("ASAMPL0.BPB").setSize(333L).build(),
                    )
                ).build().writeTo(this)
        }

        mockkObject(BlePolarDeviceCapabilitiesUtility)
        every { getFileSystemType(any()) } returns BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2
        coEvery { client.request(any<ByteArray>()) } answers { dateDirectories } andThen actDirectory andThen actFile andThen actDirectory andThen actFile

        val expectedPaths = mutableListOf("/U/0/20250101/ACT/ASAMPL0.BPB", "/U/0/20250202/ACT/ASAMPL0.BPB")

        // Act
        val result = PolarFileUtils.getFileList(deviceId, "/U/0/", true, listener, "")

        // Assert
        Assert.assertEquals(expectedPaths, result)
        verify(exactly = 1) { client.isServiceDiscovered }
        coVerify(exactly = 5) { client.request(any()) }
    }

    @Test
    fun testListFiles_Throws_When_NoSession() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val listener = mockk<BleDeviceListener>()
        val sessions = mockk<Set<BleDeviceSession>>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns false

        // Act & Assert
        try {
            PolarFileUtils.getFileList(deviceId, "/U/0/", true, listener, "")
            Assert.fail("Expected PolarDeviceNotFound")
        } catch (e: PolarDeviceNotFound) {
            // expected
        }
    }

    @Test
    fun testListFiles_Throws_When_FileSystemNotSupported() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val (client, listener, session) = mockBleConnection(deviceId)

        mockkObject(BlePolarDeviceCapabilitiesUtility)
        every { getFileSystemType(any()) } returns BlePolarDeviceCapabilitiesUtility.FileSystemType.H10_FILE_SYSTEM
        every { session.polarDeviceType } returns "h10"

        // Act & Assert
        try {
            PolarFileUtils.getFileList(deviceId, "/U/0/", true, listener, "")
            Assert.fail("Expected PolarOperationNotSupported")
        } catch (e: PolarOperationNotSupported) {
            // expected
        }
        verify(exactly = 1) { client.isServiceDiscovered }
        coVerify(exactly = 0) { client.request(any()) }
    }

    @Test
    fun testWriteFile() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val (client, listener) = mockBleConnection(deviceId)

        val proto = buildDailySummaryProto()
        val outputStream = ByteArrayOutputStream()
        proto.writeTo(outputStream)

        every { client.write(any(), any()) } returns flowOf(0L)

        // Act
        PolarFileUtils.writeFile(deviceId, "/U/0/20000101/DSUM/", listener, outputStream.toByteArray(), "")

        val verifyWriteBuilder = PftpRequest.PbPFtpOperation.newBuilder()
        verifyWriteBuilder.command = PftpRequest.PbPFtpOperation.Command.PUT
        verifyWriteBuilder.path = "/U/0/20000101/DSUM/"

        // Assert
        verify(exactly = 1) { client.isServiceDiscovered }
        verify(exactly = 1) { client.write(verifyWriteBuilder.build().toByteArray(), any()) }
    }

    @Test
    fun testReadFile() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val (client, listener) = mockBleConnection(deviceId)

        val proto = buildDailySummaryProto()
        val outputStream = ByteArrayOutputStream()
        proto.writeTo(outputStream)

        coEvery { client.request(any<ByteArray>()) } returns outputStream

        // Act
        val result = PolarFileUtils.readFile(deviceId, "/U/0/20000101/DSUM/DSUM.BPB", listener, "")

        // Assert
        Assert.assertNotNull(result)
        Assert.assertTrue(proto.toByteArray().contentEquals(result))
        verify(exactly = 1) { client.isServiceDiscovered }
        coVerify(exactly = 1) { client.request(any()) }
    }

    @Test
    fun testReadFile_Throws_When_NoSession() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val listener = mockk<BleDeviceListener>()
        val sessions = mockk<Set<BleDeviceSession>>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns false

        // Act & Assert
        try {
            PolarFileUtils.readFile(deviceId, "/U/0/", listener, "")
            Assert.fail("Expected PolarDeviceNotFound")
        } catch (e: PolarDeviceNotFound) {
            // expected
        }
    }

    @Test
    fun testReadFile_Throws_When_NoFtpClient() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()
        val client = mockk<BlePsFtpClient>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(any()) } returns client
        every { client.isServiceDiscovered } returns false

        // Act & Assert
        try {
            PolarFileUtils.readFile(deviceId, "/U/0/", listener, "")
            Assert.fail("Expected PolarServiceNotAvailable")
        } catch (e: PolarServiceNotAvailable) {
            // expected
        }
        coVerify(exactly = 0) { client.request(any()) }
    }

    @Test
    fun testReadFile_Throws_When_FtpRequestFails() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()
        val client = mockk<BlePsFtpClient>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(any()) } returns client
        every { client.isServiceDiscovered } returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0)
        coEvery { client.request(any()) } throws BlePsFtpUtils.PftpResponseError("All is lost!", 0)

        // Act & Assert
        try {
            PolarFileUtils.readFile(deviceId, "/U/0/", listener, "")
            Assert.fail("Expected exception")
        } catch (e: Exception) {
            // handleError always converts PftpResponseError into a plain Exception (never rethrows it directly)
            Assert.assertFalse(e is BlePsFtpUtils.PftpResponseError)
        }
        verify(exactly = 1) { client.isServiceDiscovered }
        coVerify(exactly = 1) { client.request(any()) }
    }

    @Test
    fun testRemoveSingleFile() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val (client, listener) = mockBleConnection(deviceId)

        val outputStream = ByteArrayOutputStream()
        coEvery { client.request(any<ByteArray>()) } returns outputStream

        // Act
        val result = PolarFileUtils.removeSingleFile(deviceId, "/U/0/20000101/DSUM/DSUM.BPB", listener, "")

        // Assert
        Assert.assertEquals(outputStream, result)
        verify(exactly = 1) { client.isServiceDiscovered }
        coVerify(exactly = 1) { client.request(any()) }
    }

    @Test
    fun testRemoveSingleFile_Throws_When_NoFtpClient() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()
        val client = mockk<BlePsFtpClient>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(any()) } returns client
        every { client.isServiceDiscovered } returns false

        // Act & Assert
        try {
            PolarFileUtils.removeSingleFile(deviceId, "/U/0/", listener, "")
            Assert.fail("Expected PolarServiceNotAvailable")
        } catch (e: PolarServiceNotAvailable) {
            // expected
        }
        coVerify(exactly = 0) { client.request(any()) }
    }

    @Test
    fun testRemoveSingleFile_Throws_When_FtpRequestFails() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()
        val client = mockk<BlePsFtpClient>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(any()) } returns client
        every { client.isServiceDiscovered } returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0)
        coEvery { client.request(any()) } throws BlePsFtpUtils.PftpResponseError("All is lost!", 0)

        // Act & Assert
        try {
            PolarFileUtils.removeSingleFile(deviceId, "/U/0/", listener, "")
            Assert.fail("Expected exception")
        } catch (e: Exception) {
            // handleError always converts PftpResponseError into a plain Exception (never rethrows it directly)
            Assert.assertFalse(e is BlePsFtpUtils.PftpResponseError)
        }
        verify(exactly = 1) { client.isServiceDiscovered }
        coVerify(exactly = 1) { client.request(any()) }
    }

    private fun buildDailySummaryProto(): PbDailySummary {
        return PbDailySummary.newBuilder()
            .setDate(PbDate.newBuilder().setDay(1).setMonth(1).setYear(2525))
            .setActivityDistance(1234.56f)
            .setActivityCalories(100)
            .setBmrCalories(2000)
            .setTrainingCalories(500)
            .setActivityClassTimes(
                DailySummary.PbActivityClassTimes.newBuilder()
                    .setTimeLightActivity(PbDuration.newBuilder().setHours(5).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeSleep(PbDuration.newBuilder().setHours(8).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeSedentary(PbDuration.newBuilder().setHours(7).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeContinuousModerate(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeContinuousVigorous(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeIntermittentModerate(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeIntermittentVigorous(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeNonWear(PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(0).setMillis(0))
            )
            .setActivityGoalSummary(
                PbActivityGoalSummary.newBuilder()
                    .setActivityGoal(100f)
                    .setAchievedActivity(50f)
            )
            .setDailyBalanceFeedback(Types.PbDailyBalanceFeedback.DB_YOU_COULD_DO_MORE_TRAINING)
            .setReadinessForSpeedAndStrengthTraining(Types.PbReadinessForSpeedAndStrengthTraining.RSST_A1_RECOVERED_READY_FOR_ALL_TRAINING)
            .setSteps(10000)
            .build()
    }

    @Test
    fun testFetchRecursively_Handles_Error103_NoSuchFileOrDirectory() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val (client, _, _) = mockBleConnection(deviceId)

        mockkObject(BlePolarDeviceCapabilitiesUtility)
        every { getFileSystemType(any()) } returns BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2

        coEvery { client.request(any<ByteArray>()) } throws
            BlePsFtpUtils.PftpResponseError("Directory not found", 103)

        // Act
        val results = mutableListOf<Pair<String, Long>>()
        PolarFileUtils.fetchRecursively(
            client = client,
            path = "/U/0/missing_directory/",
            condition = null,
            recurseDeep = true,
            tag = "TestTag"
        ).collect { results.add(it) }

        // Assert
        Assert.assertTrue("Expected empty results but got $results", results.isEmpty())
        coVerify(exactly = 1) { client.request(any<ByteArray>()) }
    }

    private fun mockBleConnection(deviceId: String): Triple<BlePsFtpClient, BleDeviceListener, BleDeviceSession> {
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
        every { session.fetchClient(any()) } returns client
        every { client.isServiceDiscovered } returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0)

        return Triple(client, listener, session)
    }
}