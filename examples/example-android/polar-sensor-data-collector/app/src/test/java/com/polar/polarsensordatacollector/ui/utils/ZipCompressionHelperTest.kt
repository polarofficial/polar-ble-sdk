package com.polar.polarsensordatacollector.ui.utils

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
class ZipCompressionHelperTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val created = mutableListOf<File>()

    @Before
    fun setUp() {
        // FileProvider caches the roots of the first test. Each Robolectric test has a new cache directory.
        FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
            .let { (it.get(null) as MutableMap<*, *>).clear() }
        context.cacheDir.listFiles()?.forEach { it.delete() }
    }

    @After
    fun tearDown() {
        created.forEach { it.delete() }
        context.cacheDir.listFiles()?.forEach { it.delete() }
    }

    private fun file(name: String, size: Int): File =
        File(context.cacheDir, name).also {
            it.writeBytes(ByteArray(size) { index -> (index % 7).toByte() })
            created.add(it)
        }

    private fun uriOf(file: File): Uri = Uri.fromFile(file)

    @Test
    fun `calculate total size counts unknown scheme as zero`() {
        // Act
        val total = ZipCompressionHelper.calculateTotalSize(context, listOf(Uri.parse("http://example.com/a.csv")))

        // Assert
        assertEquals(0L, total)
    }

    @Test
    fun `compress files if needed creates zip when above limit`() {
        // Arrange
        val big = file("big.csv", 26 * 1024 * 1024)
        val empty = file("empty.csv", 0)
        val missing = Uri.fromFile(File(context.cacheDir, "missing.csv"))

        // Act
        val result = ZipCompressionHelper.compressFilesIfNeeded(context, listOf(uriOf(big), uriOf(empty), missing), "session")

        // Assert
        assertNotNull(result.uri)
        assertFalse(result.isOverEmailSizeLimit)
        val zip = context.cacheDir.listFiles()!!.single { it.name.startsWith("session_") && it.name.endsWith(".zip") }
        ZipFile(zip).use {
            assertEquals(listOf("big.csv"), it.entries().toList().map { entry -> entry.name })
        }
    }

    @Test
    fun `compress files if needed reports over limit when zip still too big`() {
        // Arrange
        // Random data does not compress, so the ZIP stays above the limit.
        val big = File(context.cacheDir, "random.bin").also {
            it.writeBytes(java.util.Random(1).let { random -> ByteArray(26 * 1024 * 1024).also(random::nextBytes) })
            created.add(it)
        }

        // Act
        val result = ZipCompressionHelper.compressFilesIfNeeded(context, listOf(uriOf(big)))

        // Assert
        assertNotNull(result.uri)
        assertTrue(result.isOverEmailSizeLimit)
    }

    @Test
    fun `cleanup old zip files deletes only old zip files`() {
        // Arrange
        val old = file("old.zip", 1).also { it.setLastModified(System.currentTimeMillis() - 2 * 60 * 60 * 1000) }
        val fresh = file("fresh.zip", 1)
        val other = file("old.csv", 1).also { it.setLastModified(System.currentTimeMillis() - 2 * 60 * 60 * 1000) }

        // Act
        ZipCompressionHelper.cleanupOldZipFiles(context)

        // Assert
        assertFalse(old.exists())
        assertTrue(fresh.exists())
        assertTrue(other.exists())
    }

    @Test
    fun `cleanup old zip files uses given max age`() {
        // Arrange
        val zip = file("recent.zip", 1).also { it.setLastModified(System.currentTimeMillis() - 5_000) }

        // Act
        ZipCompressionHelper.cleanupOldZipFiles(context, maxAgeMs = 1_000)

        // Assert
        assertFalse(zip.exists())
    }

    @Test
    fun `device log entry name strips leading slash and replaces separators`() {
        // Act and Assert
        assertEquals("ERRORLOG.BPB", ZipCompressionHelper.deviceLogEntryName("/ERRORLOG.BPB"))
        assertEquals("LOGS_A_B.TXT", ZipCompressionHelper.deviceLogEntryName("/LOGS/A/B.TXT"))
    }

}
