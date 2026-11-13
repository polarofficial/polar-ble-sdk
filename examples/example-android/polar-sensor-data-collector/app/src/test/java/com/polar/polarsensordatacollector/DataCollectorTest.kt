package com.polar.polarsensordatacollector

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.polar.sdk.api.model.GpsCoordinatesSample
import com.polar.sdk.api.model.GpsNMEASample
import com.polar.sdk.api.model.GpsSatelliteDilutionSample
import com.polar.sdk.api.model.GpsSatelliteSummarySample
import com.polar.sdk.api.model.PolarDerivedMeasurementMethod
import com.polar.sdk.api.model.PolarDerivedSample
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarPpgData
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import io.mockk.every
import io.mockk.verify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DataCollectorTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkStatic(FileProvider::class)
        every { FileProvider.getUriForFile(any(), any(), any()) } returns Uri.parse("content://test/file")
    }

    @After
    fun tearDown() {
        unmockkStatic(FileProvider::class)
    }

    private fun newWrapper(name: String) =
        DataCollector.FileOperationWrapper(context, name, "TEST", "dev")

    @Test
    fun `write stores data in the file`() {
        val wrapper = newWrapper("a.txt")
        wrapper.write("abc")
        wrapper.close()
        assertEquals(3L, wrapper.length())
    }

    @Test
    fun `write after close does not throw and adds no data`() {
        val wrapper = newWrapper("b.txt")
        wrapper.write("abc")
        wrapper.close()
        wrapper.write("more")
        assertEquals(3L, wrapper.length())
    }

    @Test
    fun `close twice does not throw`() {
        val wrapper = newWrapper("c.txt")
        wrapper.write("abc")
        wrapper.close()
        wrapper.close()
        assertFalse(wrapper.isStarted())
    }

    @Test
    fun `isStarted is true after first write`() {
        val wrapper = newWrapper("d.txt")
        assertFalse(wrapper.isStarted())
        wrapper.write("x")
        assertTrue(wrapper.isStarted())
        wrapper.close()
    }

    @Test
    fun `finalizeAllStreams returns one uri per stream and clears streams`() {
        val collector = DataCollector(context)
        collector.startPpgLog("dev")
        collector.startAccLog("dev")
        assertEquals(2, collector.finalizeAllStreams().size)
        assertEquals(0, collector.finalizeAllStreams().size)
    }

    private fun readLog(tag: String): List<String> {
        val dir = File(context.getExternalFilesDir(null), "sensorDataLogs/$tag")
        return dir.walkTopDown().filter { it.isFile }.flatMap { it.readLines() }.toList()
    }

    private fun assertHeaderAndLine(tag: String, headerPart: String, linePart: String, lines: Int = 2) {
        val content = readLog(tag)
        assertEquals(lines, content.size)
        assertContains(content[0], headerPart)
        assertContains(content[1], linePart)
    }

    @Test
    fun `acc log writes header and sample`() {
        val c = DataCollector(context)
        c.startAccLog("dev")
        c.startAccLog("dev")
        c.logAcc(10L, 1, 2, 3)
        c.finalizeAllStreams()
        assertHeaderAndLine("ACC", "X(mg)", "10 1 2 3")
    }

    @Test
    fun `ecg and fecg are logged`() {
        val c = DataCollector(context)
        c.startEcgLog("dev")
        c.logEcg(5L, 7)
        c.finalizeAllStreams()
        assertHeaderAndLine("ECG", "ECG(microV)", "5 7")

        val c2 = DataCollector(context)
        c2.startEcgLog("dev")
        c2.logFecg(6L, 8, 9, 1u)
        c2.finalizeAllStreams()
        assertHeaderAndLine("ECG", "BIOZ", "6 8 9 1")
    }

    @Test
    fun `hr log handles rr data and empty rr data`() {
        val c = DataCollector(context)
        c.startHrLog("dev")
        c.logHr(1L, PolarHrData.PolarHrSample(60, 61, 2, listOf(1000), listOf(976), true, true, true))
        c.logHr(null, PolarHrData.PolarHrSample(60, 61, 2, emptyList(), emptyList(), false, false, false))
        c.finalizeAllStreams()
        val lines = readLog("HR")
        assertEquals(3, lines.size)
        assertContains(lines[1], "976 | 1000")
        assertContains(lines[2], "NA | NA")
    }

    @Test
    fun `ppg log writes dynamic header and all samples`() {
        val c = DataCollector(context)
        c.startPpgLog("dev")
        val data = PolarPpgData(
            listOf(
                PolarPpgData.PolarPpgSample(1L, listOf(1, 2), listOf(1)),
                PolarPpgData.PolarPpgSample(2L, listOf(3, 4), listOf(0))
            ),
            PolarPpgData.PpgDataType.FRAME_TYPE_7
        )
        c.logPpgData(data)
        c.finalizeAllStreams()
        val lines = readLog("PPG")
        assertEquals(3, lines.size)
        assertContains(lines[0], "PPG0 PPG1 STATUS0")
        assertEquals("1 1 2 1", lines[1])
        assertEquals("2 3 4 0", lines[2])
    }

    @Test
    fun `ppi log writes sample`() {
        val c = DataCollector(context)
        c.startPpiLog("dev")
        c.logPpi(800, 5, true, false, true, 70, 9uL)
        c.finalizeAllStreams()
        assertHeaderAndLine("PPI", "PPI(ms)", "9 800 5 1 0 1 70")
    }

    @Test
    fun `magnetometer gyro and pressure are logged`() {
        val c = DataCollector(context)
        c.startMagnetometerLog("dev")
        c.startGyroLog("dev")
        c.startPressureLog("dev")
        c.logMagnetometer(1L, 1f, 2f, 3f)
        c.logGyro(2L, 4f, 5f, 6f)
        c.logPressure(3L, 1000f)
        c.finalizeAllStreams()
        assertHeaderAndLine("MAGNETOMETER", "Gauss", "1 1.0 2.0 3.0")
        assertHeaderAndLine("GYRO", "deg/sec", "2 4.0 5.0 6.0")
        assertHeaderAndLine("PRESSURE", "Pressure(mBar)", "3 1000.0")
    }

    @Test
    fun `temperature logs are written`() {
        val c = DataCollector(context)
        c.startTemperatureLog("dev")
        c.startSkinTemperatureLog("dev")
        c.logTemperature(1L, 36.5f)
        c.logSkinTemperature(2L, 33.5f)
        c.finalizeAllStreams()
        assertHeaderAndLine("TEMPERATURE", "TEMPERATURE(Celsius)", "1 36.5")
        assertHeaderAndLine("SKIN_TEMPERATURE", "SKIN_TEMPERATURE(Celsius)", "2 33.5")
    }

    @Test
    fun `location logs are written`() {
        val c = DataCollector(context)
        c.startLocationLog("dev")
        c.startLocationLog("dev")
        c.logLocationCoordinates(1L, mockk<GpsCoordinatesSample>(relaxed = true))
        c.logLocationDilution(2L, mockk<GpsSatelliteDilutionSample>(relaxed = true))
        c.logLocationSatelliteSummary(3L, mockk<GpsSatelliteSummarySample>(relaxed = true))
        c.logLocationNmeaSummary(4L, mockk<GpsNMEASample>(relaxed = true))
        c.finalizeAllStreams()
        assertHeaderAndLine("LOCATION_COORDINATES", "LATITUDE", "1 ")
        assertHeaderAndLine("LOCATION_DILUTION", "DILUTION", "2 ")
        assertHeaderAndLine("LOCATION_SUMMARY", "MAX_SNR", "3 ")
        assertHeaderAndLine("LOCATION_NMEA", "NMEA_MESSAGE", "4 ")
    }

    @Test
    fun `derived acc log writes component and scalar columns`() {
        val c = DataCollector(context)
        c.startDerivedAccLog("dev")
        val method = PolarDerivedMeasurementMethod.NORM
        val vec = PolarDerivedMeasurementMethod.DOWNSAMPLE
        val sample = PolarDerivedSample(
            7L,
            setOf(vec, method),
            mapOf(method to listOf(9), vec to listOf(1, 2, 3))
        )
        c.logDerivedSample(sample)
        c.finalizeAllStreams()
        val lines = readLog("ACC_DERIVED")
        assertEquals(2, lines.size)
        assertContains(lines[0], "DS_X DS_Y DS_Z NORM")
        assertEquals("7 1 2 3 9", lines[1])
    }

    @Test
    fun `marker uses given or latest timestamp`() {
        val c = DataCollector(context)
        c.startAccLog("dev")
        c.logAcc(42L, 0, 0, 0)
        c.marker("dev", true)
        c.marker("dev", false, 99L)
        c.finalizeAllStreams()
        assertEquals(listOf("MARKER_START 42", "MARKER_STOP 99"), readLog("MARKER"))
    }

    @Test
    fun `log calls without started stream do nothing`() {
        val c = DataCollector(context)
        c.logAcc(1L, 1, 2, 3)
        c.logTemperature(1L, 1f)
        c.logPpi(1, 1, true, true, true, 1, 1uL)
        assertEquals(0, c.finalizeAllStreams().size)
    }

    private fun newDocumentWrapper(out: ByteArrayOutputStream): Pair<DataCollector.DocumentFileOperationWrapper, DocumentFile> {
        val fileUri = Uri.parse("content://tree/doc")
        val docFile = mockk<DocumentFile>(relaxed = true)
        every { docFile.uri } returns fileUri
        every { docFile.name } returns "doc.txt"
        every { docFile.length() } returns 4L
        every { docFile.delete() } returns true
        val tree = mockk<DocumentFile>()
        every { tree.createFile("plain/text", "doc.txt") } returns docFile
        mockkStatic(DocumentFile::class)
        every { DocumentFile.fromTreeUri(any(), any()) } returns tree
        val ctx = mockk<Context>()
        every { ctx.contentResolver.openOutputStream(fileUri) } returns out
        return DataCollector.DocumentFileOperationWrapper(ctx, "doc.txt", Uri.parse("content://tree")) to docFile
    }

    @Test
    fun `document wrapper writes closes and reports file info`() {
        val out = ByteArrayOutputStream()
        val (wrapper, _) = newDocumentWrapper(out)
        try {
            assertEquals("doc.txt", wrapper.fileName)
            assertFalse(wrapper.isStarted())
            wrapper.write("abcd")
            assertTrue(wrapper.isStarted())
            assertEquals("abcd", out.toString())
            assertEquals(Uri.parse("content://tree/doc"), wrapper.getUri())
            assertEquals(4L, wrapper.length())
            assertTrue(wrapper.delete())
            wrapper.close()
            assertFalse(wrapper.isStarted())
        } finally {
            unmockkStatic(DocumentFile::class)
        }
    }

    @Test
    fun `document wrapper ignores write after close and double close`() {
        val out = ByteArrayOutputStream()
        val (wrapper, _) = newDocumentWrapper(out)
        try {
            wrapper.write("ab")
            wrapper.close()
            wrapper.close()
            wrapper.write("cd")
            assertEquals("ab", out.toString())
        } finally {
            unmockkStatic(DocumentFile::class)
        }
    }

    @Test
    fun `collector uses document wrapper when backup is enabled`() {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit()
            .putBoolean(context.getString(R.string.back_up_enabled), true)
            .putString(context.getString(R.string.back_up_location), "content://tree")
            .commit()
        val out = ByteArrayOutputStream()
        val fileUri = Uri.parse("content://tree/doc")
        val docFile = mockk<DocumentFile>(relaxed = true)
        every { docFile.uri } returns fileUri
        val tree = mockk<DocumentFile>()
        every { tree.createFile(any(), any()) } returns docFile
        mockkStatic(DocumentFile::class)
        try {
            every { DocumentFile.fromTreeUri(any(), any()) } returns tree
            val ctx = mockk<Context>(relaxed = true)
            every { ctx.getString(R.string.back_up_enabled) } returns context.getString(R.string.back_up_enabled)
            every { ctx.getString(R.string.back_up_location) } returns context.getString(R.string.back_up_location)
            every { ctx.getSharedPreferences(any(), any()) } returns prefs
            every { ctx.contentResolver.openOutputStream(fileUri) } returns out
            val c = DataCollector(ctx)
            c.startAccLog("dev")
            c.logAcc(1L, 1, 2, 3)
            assertEquals(1, c.finalizeAllStreams().size)
            assertContains(out.toString(), "1 1 2 3")
            verify { tree.createFile("plain/text", any()) }
        } finally {
            unmockkStatic(DocumentFile::class)
        }
    }
}
