package com.polar.sdk.api.model.utils

import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarOfflineRecordingEntry
import com.polar.sdk.impl.utils.PolarOfflineRecordingUtils
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime

class PolarOfflineRecordingUtilsTest {

    private val mockClient = mockk<BlePsFtpClient>()

    @Test
    fun `listOfflineRecordingsV1 merges split REC files`() = runTest {
        val sampleEntries = listOf(
            Pair("/U/0/20250730/R/101010/ACC0.REC", 500120L),
            Pair("/U/0/20250730/R/101010/ACC1.REC", 500103L),
            Pair("/U/0/20250730/R/101010/ACC2.REC", 102325L),
            Pair("/U/0/20250730/R/101010/HR0.REC", 500000L),
            Pair("/U/0/20250730/R/101010/HR1.REC", 500050L),
            Pair("/U/0/20250730/R/101010/PPG0.REC", 300L)
        )

        val fetchRecursively: (BlePsFtpClient, String, (String) -> Boolean) -> Flow<Pair<String, Long>> =
            { _, _, _ -> sampleEntries.asFlow() }

        val emitted = mutableListOf<PolarOfflineRecordingEntry>()
        val job = launch {
            PolarOfflineRecordingUtils.listOfflineRecordingsV1(mockClient, fetchRecursively)
                .collect { emitted.add(it) }
        }
        job.join()

        val accEntries = emitted.filter { it.path.contains("ACC") }
        val hrEntries = emitted.filter { it.path.contains("HR") }
        val ppgEntries = emitted.filter { it.path.contains("PPG") }

        assert(accEntries.size == 1)
        assert(accEntries[0].size == 500120L + 500103L + 102325L)
        assert(accEntries[0].path.endsWith(".REC"))

        assert(hrEntries.size == 1)
        assert(hrEntries[0].size == 500000L + 500050L)
        assert(hrEntries[0].path.endsWith(".REC"))

        assert(ppgEntries.size == 1)
        assert(ppgEntries[0].size == 300L)
        assert(ppgEntries[0].path.endsWith(".REC"))
    }

    @Test
    fun `listOfflineRecordingsV1 does not return empty files`() = runTest {
        val sampleEntries = listOf(
            Pair("/U/0/20250730/R/101010/ACC0.REC", 500120L),
            Pair("/U/0/20250730/R/101010/ACC1.REC", 500103L),
            Pair("/U/0/20250730/R/101010/ACC2.REC", 0L),
            Pair("/U/0/20250730/R/101010/HR0.REC", 500000L),
            Pair("/U/0/20250730/R/101010/HR1.REC", 0L),
            Pair("/U/0/20250730/R/101010/PPG0.REC", 0L)
        )

        val fetchRecursively: (BlePsFtpClient, String, (String) -> Boolean) -> Flow<Pair<String, Long>> =
            { _, _, _ -> sampleEntries.asFlow() }

        val emitted = mutableListOf<PolarOfflineRecordingEntry>()
        val job = launch {
            PolarOfflineRecordingUtils.listOfflineRecordingsV1(mockClient, fetchRecursively)
                .collect { emitted.add(it) }
        }
        job.join()

        val accEntries = emitted.filter { it.path.contains("ACC") }
        val hrEntries = emitted.filter { it.path.contains("HR") }
        val ppgEntries = emitted.filter { it.path.contains("PPG") }

        assert(accEntries.size == 1)
        assert(accEntries[0].size == 500120L + 500103L)
        assert(accEntries[0].path.endsWith(".REC"))

        assert(hrEntries.size == 1)
        assert(hrEntries[0].size == 500000L)
        assert(hrEntries[0].path.endsWith(".REC"))

        assert(ppgEntries.isEmpty())
    }


    @Test
    fun `listOfflineRecordingsV2 merges split REC files`() = runTest {
        val pmdTxtContent = """
            500120 /U/0/20250730/R/101010/ACC0.REC
            500103 /U/0/20250730/R/101010/ACC1.REC
            102325 /U/0/20250730/R/101010/ACC2.REC
            500000 /U/0/20250730/R/101010/HR0.REC
            500050 /U/0/20250730/R/101010/HR1.REC
            300 /U/0/20250730/R/101010/PPG0.REC
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)

        val emitted = PolarOfflineRecordingUtils.listOfflineRecordingsV2(pmdTxtContent)

        val accEntries = emitted.filter { it.path.contains("ACC") }
        val hrEntries = emitted.filter { it.path.contains("HR") }
        val ppgEntries = emitted.filter { it.path.contains("PPG") }

        assert(accEntries.size == 1)
        assert(accEntries[0].size == 500120L + 500103L + 102325L)
        assert(accEntries[0].path.endsWith(".REC"))

        assert(hrEntries.size == 1)
        assert(hrEntries[0].size == 500000L + 500050L)
        assert(hrEntries[0].path.endsWith(".REC"))

        assert(ppgEntries.size == 1)
        assert(ppgEntries[0].size == 300L)
        assert(ppgEntries[0].path.endsWith(".REC"))
    }

    @Test
    fun `listOfflineRecordingsV2 does not return empty files`() = runTest {
        val pmdTxtContent = """
            500120 /U/0/20250730/R/101010/ACC0.REC
            500103 /U/0/20250730/R/101010/ACC1.REC
            0 /U/0/20250730/R/101010/ACC2.REC
            500050 /U/0/20250730/R/101010/HR0.REC
            0 /U/0/20250730/R/101010/HR1.REC
            0 /U/0/20250730/R/101010/PPG0.REC
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)

        val emitted = PolarOfflineRecordingUtils.listOfflineRecordingsV2(pmdTxtContent)

        val accEntries = emitted.filter { it.path.contains("ACC") }
        val hrEntries = emitted.filter { it.path.contains("HR") }
        val ppgEntries = emitted.filter { it.path.contains("PPG") }

        assert(accEntries.size == 1)
        assert(accEntries[0].size == 500120L + 500103L)
        assert(accEntries[0].path.endsWith(".REC"))

        assert(hrEntries.size == 1)
        assert(hrEntries[0].size == 500050L)
        assert(hrEntries[0].path.endsWith(".REC"))

        assert(ppgEntries.isEmpty())
    }

    @Test
    fun `listOfflineRecordingsV2 maps all supported measurement types correctly`() {
        val pmdTxtContent = """
            100 /U/0/20240101/R/120000/ACC0.REC
            100 /U/0/20240101/R/120001/GYRO0.REC
            100 /U/0/20240101/R/120002/MAG0.REC
            100 /U/0/20240101/R/120003/PPG0.REC
            100 /U/0/20240101/R/120004/PPI0.REC
            100 /U/0/20240101/R/120005/HR0.REC
            100 /U/0/20240101/R/120006/TEMP0.REC
            100 /U/0/20240101/R/120007/SKINTEMP0.REC
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)

        val result = PolarOfflineRecordingUtils.listOfflineRecordingsV2(pmdTxtContent)

        assertEquals(8, result.size)
        val types = result.map { it.type }.toSet()
        assertTrue(PolarBleApi.PolarDeviceDataType.ACC in types)
        assertTrue(PolarBleApi.PolarDeviceDataType.GYRO in types)
        assertTrue(PolarBleApi.PolarDeviceDataType.MAGNETOMETER in types)
        assertTrue(PolarBleApi.PolarDeviceDataType.PPG in types)
        assertTrue(PolarBleApi.PolarDeviceDataType.PPI in types)
        assertTrue(PolarBleApi.PolarDeviceDataType.HR in types)
        assertTrue(PolarBleApi.PolarDeviceDataType.TEMPERATURE in types)
        assertTrue(PolarBleApi.PolarDeviceDataType.SKIN_TEMPERATURE in types)
    }

    @Test
    fun `listOfflineRecordingsV1 maps all supported measurement types correctly`() = runTest {
        val sampleEntries = listOf(
            Pair("/U/0/20240101/R/120000/ACC0.REC", 100L),
            Pair("/U/0/20240101/R/120001/GYRO0.REC", 100L),
            Pair("/U/0/20240101/R/120002/MAG0.REC", 100L),
            Pair("/U/0/20240101/R/120003/PPG0.REC", 100L),
            Pair("/U/0/20240101/R/120004/PPI0.REC", 100L),
            Pair("/U/0/20240101/R/120005/HR0.REC", 100L),
            Pair("/U/0/20240101/R/120006/TEMP0.REC", 100L),
            Pair("/U/0/20240101/R/120007/SKINTEMP0.REC", 100L),
        )
        val fetchRecursively: (BlePsFtpClient, String, (String) -> Boolean) -> Flow<Pair<String, Long>> =
            { _, _, _ -> sampleEntries.asFlow() }

        val emitted = mutableListOf<PolarOfflineRecordingEntry>()
        launch { PolarOfflineRecordingUtils.listOfflineRecordingsV1(mockClient, fetchRecursively).collect { emitted.add(it) } }.join()

        assertEquals(8, emitted.size)
        val types = emitted.map { it.type }.toSet()
        assertTrue(PolarBleApi.PolarDeviceDataType.ACC in types)
        assertTrue(PolarBleApi.PolarDeviceDataType.GYRO in types)
        assertTrue(PolarBleApi.PolarDeviceDataType.MAGNETOMETER in types)
        assertTrue(PolarBleApi.PolarDeviceDataType.PPG in types)
        assertTrue(PolarBleApi.PolarDeviceDataType.PPI in types)
        assertTrue(PolarBleApi.PolarDeviceDataType.HR in types)
        assertTrue(PolarBleApi.PolarDeviceDataType.TEMPERATURE in types)
        assertTrue(PolarBleApi.PolarDeviceDataType.SKIN_TEMPERATURE in types)
    }

    @Test
    fun `listOfflineRecordingsV2 parses date and time correctly`() {
        val pmdTxtContent = "1024 /U/0/20231115/R/083045/HR0.REC\n"
            .toByteArray(StandardCharsets.UTF_8)

        val result = PolarOfflineRecordingUtils.listOfflineRecordingsV2(pmdTxtContent)

        assertEquals(1, result.size)
        assertEquals(LocalDateTime.of(2023, 11, 15, 8, 30, 45), result[0].date)
    }

    @Test
    fun `listOfflineRecordingsV1 parses date and time correctly`() = runTest {
        val sampleEntries = listOf(Pair("/U/0/20231115/R/083045/HR0.REC", 1024L))
        val fetchRecursively: (BlePsFtpClient, String, (String) -> Boolean) -> Flow<Pair<String, Long>> =
            { _, _, _ -> sampleEntries.asFlow() }

        val emitted = mutableListOf<PolarOfflineRecordingEntry>()
        launch { PolarOfflineRecordingUtils.listOfflineRecordingsV1(mockClient, fetchRecursively).collect { emitted.add(it) } }.join()

        assertEquals(1, emitted.size)
        assertEquals(LocalDateTime.of(2023, 11, 15, 8, 30, 45), emitted[0].date)
    }

    @Test
    fun `listOfflineRecordingsV2 emits separate entries for recordings on different dates`() {
        val pmdTxtContent = """
            500 /U/0/20240101/R/080000/ACC0.REC
            500 /U/0/20240102/R/080000/ACC0.REC
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)

        val result = PolarOfflineRecordingUtils.listOfflineRecordingsV2(pmdTxtContent)

        assertEquals(2, result.size)
        val dates = result.map { it.date }.toSet()
        assertTrue(LocalDateTime.of(2024, 1, 1, 8, 0, 0) in dates)
        assertTrue(LocalDateTime.of(2024, 1, 2, 8, 0, 0) in dates)
    }

    @Test
    fun `listOfflineRecordingsV1 emits separate entries for recordings on different dates`() = runTest {
        val sampleEntries = listOf(
            Pair("/U/0/20240101/R/080000/ACC0.REC", 500L),
            Pair("/U/0/20240102/R/080000/ACC0.REC", 500L),
        )
        val fetchRecursively: (BlePsFtpClient, String, (String) -> Boolean) -> Flow<Pair<String, Long>> =
            { _, _, _ -> sampleEntries.asFlow() }

        val emitted = mutableListOf<PolarOfflineRecordingEntry>()
        launch { PolarOfflineRecordingUtils.listOfflineRecordingsV1(mockClient, fetchRecursively).collect { emitted.add(it) } }.join()

        assertEquals(2, emitted.size)
        val dates = emitted.map { it.date }.toSet()
        assertTrue(LocalDateTime.of(2024, 1, 1, 8, 0, 0) in dates)
        assertTrue(LocalDateTime.of(2024, 1, 2, 8, 0, 0) in dates)
    }

    @Test
    fun `listOfflineRecordingsV2 returns empty list for empty byte array`() {
        val result = PolarOfflineRecordingUtils.listOfflineRecordingsV2(ByteArray(0))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `listOfflineRecordingsV1 emits nothing for empty source`() = runTest {
        val fetchRecursively: (BlePsFtpClient, String, (String) -> Boolean) -> Flow<Pair<String, Long>> =
            { _, _, _ -> emptyFlow() }

        val emitted = mutableListOf<PolarOfflineRecordingEntry>()
        launch { PolarOfflineRecordingUtils.listOfflineRecordingsV1(mockClient, fetchRecursively).collect { emitted.add(it) } }.join()

        assertTrue(emitted.isEmpty())
    }

    @Test
    fun `listOfflineRecordingsV2 skips lines that do not end with REC`() {
        val pmdTxtContent = """
            500 /U/0/20240101/R/080000/ACC0.TXT
            400 /U/0/20240101/R/080000/HR0.REC
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)

        val result = PolarOfflineRecordingUtils.listOfflineRecordingsV2(pmdTxtContent)

        assertEquals(1, result.size)
        assertTrue(result[0].path.contains("HR"))
    }

    @Test
    fun `listOfflineRecordingsV2 skips lines with fewer than two whitespace-separated parts`() {
        val pmdTxtContent = """
            500
            400 /U/0/20240101/R/080000/HR0.REC
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)

        val result = PolarOfflineRecordingUtils.listOfflineRecordingsV2(pmdTxtContent)

        assertEquals(1, result.size)
        assertTrue(result[0].path.contains("HR"))
    }

    @Test
    fun `listOfflineRecordingsV2 skips lines with non-numeric size`() {
        val pmdTxtContent = """
            NaN /U/0/20240101/R/080000/ACC0.REC
            400 /U/0/20240101/R/080000/HR0.REC
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)

        val result = PolarOfflineRecordingUtils.listOfflineRecordingsV2(pmdTxtContent)

        assertEquals(1, result.size)
        assertTrue(result[0].path.contains("HR"))
    }

    @Test
    fun `listOfflineRecordingsV2 preserves size for single-part recording`() {
        val pmdTxtContent = "12345 /U/0/20240101/R/080000/ACC0.REC\n"
            .toByteArray(StandardCharsets.UTF_8)

        val result = PolarOfflineRecordingUtils.listOfflineRecordingsV2(pmdTxtContent)

        assertEquals(1, result.size)
        assertEquals(12345L, result[0].size)
    }

    @Test
    fun `listOfflineRecordingsV1 preserves size for single-part recording`() = runTest {
        val sampleEntries = listOf(Pair("/U/0/20240101/R/080000/ACC0.REC", 12345L))
        val fetchRecursively: (BlePsFtpClient, String, (String) -> Boolean) -> Flow<Pair<String, Long>> =
            { _, _, _ -> sampleEntries.asFlow() }

        val emitted = mutableListOf<PolarOfflineRecordingEntry>()
        launch { PolarOfflineRecordingUtils.listOfflineRecordingsV1(mockClient, fetchRecursively).collect { emitted.add(it) } }.join()

        assertEquals(1, emitted.size)
        assertEquals(12345L, emitted[0].size)
    }
}