package com.polar.sdk.api.model.utils

import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.DeviationFromBaseline
import com.polar.sdk.api.model.Spo2Class
import com.polar.sdk.api.model.Spo2TestStatus
import com.polar.sdk.impl.utils.PolarTestUtils
import com.polar.sdk.impl.utils.Spo2TestEntry
import com.polar.services.datamodels.protobuf.Spo2TestResult.PbDeviationFromBaseline
import com.polar.services.datamodels.protobuf.Spo2TestResult.PbSpo2Class
import com.polar.services.datamodels.protobuf.Spo2TestResult.PbSpo2TestResult
import com.polar.services.datamodels.protobuf.Spo2TestResult.PbSpo2TestStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import protocol.PftpRequest
import protocol.PftpResponse.PbPFtpDirectory
import protocol.PftpResponse.PbPFtpEntry
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PolarTestUtilsTest {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    @Test
    fun `readSpo2TestProtoFromDayDirectory() returns one entry per time subdirectory`() = runTest {
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.of(2026, 4, 8)
        val dateStr = date.format(dateFormatter)
        val spo2Dir = "/U/0/$dateStr/SPO2TEST/"

        val dirContent = buildDirectory("083906/", "112658/")
        val file1 = buildProto { setBloodOxygenPercent(97).setAverageHeartRateBpm(63).setTimeZoneOffset(180) }
        val file2 = buildProto { setBloodOxygenPercent(97).setAverageHeartRateBpm(73).setTimeZoneOffset(180) }

        coEvery { client.request(requestFor(spo2Dir)) } returns dirContent
        coEvery { client.request(requestFor("${spo2Dir}083906/SPO2TRES.BPB")) } returns file1
        coEvery { client.request(requestFor("${spo2Dir}112658/SPO2TRES.BPB")) } returns file2

        val result = PolarTestUtils.readSpo2TestProtoFromDayDirectory(client, date)

        assertEquals(2, result.size)
        assertEquals(date, result[0].date)
        assertEquals("083906", result[0].timeDirName)
        assertEquals(date, result[1].date)
        assertEquals("112658", result[1].timeDirName)

        val proto1 = PbSpo2TestResult.parseFrom(result[0].protoBytes)
        assertEquals(63, proto1.averageHeartRateBpm)
        val proto2 = PbSpo2TestResult.parseFrom(result[1].protoBytes)
        assertEquals(73, proto2.averageHeartRateBpm)
    }

    @Test
    fun `readSpo2TestProtoFromDayDirectory() returns empty list when directory listing fails`() = runTest {
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.of(2026, 4, 8)

        coEvery { client.request(any()) } throws Exception("network error")

        val result = PolarTestUtils.readSpo2TestProtoFromDayDirectory(client, date)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `readSpo2TestProtoFromDayDirectory() returns empty list when SPO2TEST dir has no subdirectories`() = runTest {
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.of(2026, 4, 8)
        val dateStr = date.format(dateFormatter)
        val spo2Dir = "/U/0/$dateStr/SPO2TEST/"

        // Directory listing contains only a file, no subdirectory entries
        val emptyDir = buildDirectory()
        coEvery { client.request(requestFor(spo2Dir)) } returns emptyDir

        val result = PolarTestUtils.readSpo2TestProtoFromDayDirectory(client, date)

        assertTrue(result.isEmpty())
        coVerify(exactly = 1) { client.request(requestFor(spo2Dir)) }
    }

    @Test
    fun `readSpo2TestProtoFromDayDirectory() skips subdirectory when proto file fetch fails`() = runTest {
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.of(2026, 4, 8)
        val dateStr = date.format(dateFormatter)
        val spo2Dir = "/U/0/$dateStr/SPO2TEST/"

        val dirContent = buildDirectory("083906/", "112658/")
        val file2 = buildProto { setBloodOxygenPercent(95).setAverageHeartRateBpm(66).setTimeZoneOffset(180) }

        coEvery { client.request(requestFor(spo2Dir)) } returns dirContent
        coEvery { client.request(requestFor("${spo2Dir}083906/SPO2TRES.BPB")) } throws Exception("file not found")
        coEvery { client.request(requestFor("${spo2Dir}112658/SPO2TRES.BPB")) } returns file2

        val result = PolarTestUtils.readSpo2TestProtoFromDayDirectory(client, date)

        // Only the second subdirectory succeeds
        assertEquals(1, result.size)
        assertEquals("112658", result[0].timeDirName)
    }

    @Test
    fun `readSpo2TestProtoFromDayDirectory() returns single entry when only one time subdirectory exists`() = runTest {
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.of(2026, 4, 14)
        val dateStr = date.format(dateFormatter)
        val spo2Dir = "/U/0/$dateStr/SPO2TEST/"

        val dirContent = buildDirectory("063635/")
        val fileContent = buildProto { setBloodOxygenPercent(95).setAverageHeartRateBpm(66).setTimeZoneOffset(180) }

        coEvery { client.request(requestFor(spo2Dir)) } returns dirContent
        coEvery { client.request(requestFor("${spo2Dir}063635/SPO2TRES.BPB")) } returns fileContent

        val result = PolarTestUtils.readSpo2TestProtoFromDayDirectory(client, date)

        assertEquals(1, result.size)
        assertEquals("063635", result[0].timeDirName)
        assertEquals(date, result[0].date)
    }

    @Test
    fun `dateTimeFromFolderNames() returns correctly formatted string for valid HHMMSS`() {
        val date = LocalDate.of(2026, 4, 8)
        val result = PolarTestUtils.dateTimeFromFolderNames(date, "083906")

        assertEquals("2026-04-08 08:39:06", result)
    }

    @Test
    fun `dateTimeFromFolderNames() returns null when timeDirName is not 6 characters`() {
        val date = LocalDate.of(2026, 4, 8)
        assertNull(PolarTestUtils.dateTimeFromFolderNames(date, "0839"))
        assertNull(PolarTestUtils.dateTimeFromFolderNames(date, "08390600"))
        assertNull(PolarTestUtils.dateTimeFromFolderNames(date, ""))
    }

    @Test
    fun `dateTimeFromFolderNames() returns null when timeDirName contains non-numeric characters`() {
        val date = LocalDate.of(2026, 4, 8)
        assertNull(PolarTestUtils.dateTimeFromFolderNames(date, "AB3906"))
        assertNull(PolarTestUtils.dateTimeFromFolderNames(date, "0839XY"))
    }

    @Test
    fun `dateTimeFromFolderNames() pads single-digit hours minutes and seconds correctly`() {
        val date = LocalDate.of(2026, 4, 8)
        val result = PolarTestUtils.dateTimeFromFolderNames(date, "010203")
        assertEquals("2026-04-08 01:02:03", result)
    }

    @Test
    fun `mapSpo2TestProto() returns null testTime when folder is invalid and proto testTime is zero`() {
        val date = LocalDate.of(2026, 4, 8)
        val proto = PbSpo2TestResult.newBuilder()
            .setRecordingDevice("0004BF3D")
            .setTimeZoneOffset(180)
            .setTestStatus(PbSpo2TestStatus.SPO2_TEST_PASSED)
            .buildPartial()

        val result = PolarTestUtils.mapSpo2TestProto(proto, date, "BADDIR")

        assertNull(result.testTime)
    }

    @Test
    fun `mapSpo2TestProto() maps all proto fields correctly`() {
        val date = LocalDate.of(2026, 4, 14)
        val proto = PbSpo2TestResult.newBuilder()
            .setRecordingDevice("0004BF3D")
            .setTimeZoneOffset(180)
            .setTestStatus(PbSpo2TestStatus.SPO2_TEST_PASSED)
            .setBloodOxygenPercent(95)
            .setSpo2Class(PbSpo2Class.SPO2_CLASS_NORMAL)
            .setSpo2ValueDeviationFromBaseline(PbDeviationFromBaseline.DEVIATION_NO_BASELINE)
            .setSpo2QualityAveragePercent(99.0f)
            .setAverageHeartRateBpm(66)
            .setHeartRateVariabilityMs(79.97114f)
            .setSpo2HrvDeviationFromBaseline(PbDeviationFromBaseline.DEVIATION_USUAL)
            .setAltitudeMeters(18.13582f)
            .buildPartial()

        val result = PolarTestUtils.mapSpo2TestProto(proto, date, "063635")

        assertEquals("0004BF3D", result.recordingDevice)
        assertEquals(180, result.timeZoneOffsetMinutes)
        assertEquals(Spo2TestStatus.PASSED, result.testStatus)
        assertEquals(95, result.bloodOxygenPercent)
        assertEquals(Spo2Class.NORMAL, result.spo2Class)
        assertEquals(DeviationFromBaseline.NO_BASELINE, result.spo2ValueDeviationFromBaseline)
        assertEquals(99.0f, result.spo2QualityAveragePercent)
        assertEquals(66u, result.averageHeartRateBpm)
        assertEquals(79.97114f, result.heartRateVariabilityMs)
        assertEquals(DeviationFromBaseline.USUAL, result.spo2HrvDeviationFromBaseline)
        assertEquals(18.13582f, result.altitudeMeters)
        assertEquals("2026-04-14 06:36:35", result.testTime)
    }

    @Test
    fun `mapSpo2TestProto() maps ABOVE_USUAL hrv deviation`() {
        val date = LocalDate.of(2026, 4, 14)
        val proto = PbSpo2TestResult.newBuilder()
            .setTimeZoneOffset(180)
            .setTestStatus(PbSpo2TestStatus.SPO2_TEST_PASSED)
            .setSpo2HrvDeviationFromBaseline(PbDeviationFromBaseline.DEVIATION_ABOVE_USUAL)
            .setBloodOxygenPercent(96)
            .buildPartial()

        val result = PolarTestUtils.mapSpo2TestProto(proto, date, "063751")

        assertEquals(DeviationFromBaseline.ABOVE_USUAL, result.spo2HrvDeviationFromBaseline)
        assertEquals(96, result.bloodOxygenPercent)
    }

    @Test
    fun `mapSpo2TestProto() returns null for optional fields when not set in proto`() {
        val date = LocalDate.of(2026, 4, 8)
        val proto = PbSpo2TestResult.newBuilder()
            .setTimeZoneOffset(180)
            .setTestStatus(PbSpo2TestStatus.SPO2_TEST_PASSED)
            .buildPartial()

        val result = PolarTestUtils.mapSpo2TestProto(proto, date, "083906")

        assertNull(result.bloodOxygenPercent)
        assertNull(result.spo2Class)
        assertNull(result.spo2ValueDeviationFromBaseline)
        assertNull(result.spo2QualityAveragePercent)
        assertNull(result.averageHeartRateBpm)
        assertNull(result.heartRateVariabilityMs)
        assertNull(result.spo2HrvDeviationFromBaseline)
        assertNull(result.altitudeMeters)
    }

    @Test
    fun `mapSpo2TestEntry() parses proto bytes and maps to PolarSpo2TestData`() {
        val date = LocalDate.of(2026, 4, 8)
        val protoBytes = ByteArrayOutputStream().apply {
            PbSpo2TestResult.newBuilder()
                .setRecordingDevice("0004BF3D")
                .setTimeZoneOffset(180)
                .setTestStatus(PbSpo2TestStatus.SPO2_TEST_PASSED)
                .setBloodOxygenPercent(97)
                .setAverageHeartRateBpm(63)
                .buildPartial()
                .writeTo(this)
        }.toByteArray()

        val entry = Spo2TestEntry(date = date, timeDirName = "083906", protoBytes = protoBytes)
        val result = PolarTestUtils.mapSpo2TestEntry(entry)

        assertEquals("0004BF3D", result.recordingDevice)
        assertEquals(97, result.bloodOxygenPercent)
        assertEquals(63u, result.averageHeartRateBpm)
        assertEquals("2026-04-08 08:39:06", result.testTime)
    }

    // -----------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------

    private fun requestFor(path: String): ByteArray =
        PftpRequest.PbPFtpOperation.newBuilder()
            .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
            .setPath(path)
            .build()
            .toByteArray()

    private fun buildDirectory(vararg entryNames: String): ByteArrayOutputStream =
        ByteArrayOutputStream().apply {
            val entries = entryNames.map {
                PbPFtpEntry.newBuilder().setName(it).setSize(0L).buildPartial()
            }
            write(PbPFtpDirectory.newBuilder().addAllEntries(entries).buildPartial().toByteArray())
        }

    private fun buildProto(block: PbSpo2TestResult.Builder.() -> PbSpo2TestResult.Builder): ByteArrayOutputStream =
        ByteArrayOutputStream().apply {
            PbSpo2TestResult.newBuilder().block().buildPartial().writeTo(this)
        }
}

