package com.polar.polarsensordatacollector.ui.utils

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ZipCompressionHelperTest {

    private lateinit var context: Context
    private lateinit var testDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testDir = File(context.cacheDir, "test_files")
        testDir.mkdirs()
    }

    /**
     * Test: calculateTotalSize with single file
     * Expected: Returns correct file size
     */
    @Test
    fun testCalculateTotalSize_SingleFile() {
        // Arrange
        val testFile = File(testDir, "test_file.txt")
        testFile.writeText("A".repeat(1024)) // 1KB

        val uri = Uri.fromFile(testFile)

        // Act
        val size = ZipCompressionHelper.calculateTotalSize(context, listOf(uri))

        // Assert
        assertEquals(1024, size)

        // Cleanup
        testFile.delete()
    }

    /**
     * Test: calculateTotalSize with multiple files
     * Expected: Returns sum of all file sizes
     */
    @Test
    fun testCalculateTotalSize_MultipleFiles() {
        // Arrange
        val file1 = File(testDir, "file1.txt")
        val file2 = File(testDir, "file2.txt")
        val file3 = File(testDir, "file3.txt")

        file1.writeText("A".repeat(1024))      // 1KB
        file2.writeText("B".repeat(2048))      // 2KB
        file3.writeText("C".repeat(512))       // 512B

        val uris = listOf(Uri.fromFile(file1), Uri.fromFile(file2), Uri.fromFile(file3))

        // Act
        val totalSize = ZipCompressionHelper.calculateTotalSize(context, uris)

        // Assert
        assertEquals(3584, totalSize) // 1024 + 2048 + 512

        // Cleanup
        file1.delete()
        file2.delete()
        file3.delete()
    }

    /**
     * Test: compressFilesIfNeeded returns null for small files
     * Expected: No compression when total size < 25MB
     */
    @Test
    fun testCompressFilesIfNeeded_BelowThreshold() {
        // Arrange
        val smallFile = File(testDir, "small.txt")
        smallFile.writeText("A".repeat(1024 * 100)) // 100KB (well below 25MB)

        val uri = Uri.fromFile(smallFile)

        // Act
        val result = ZipCompressionHelper.compressFilesIfNeeded(context, listOf(uri))

        // Assert
        assertNull(result, "Compression should not occur for files below 25MB")

        // Cleanup
        smallFile.delete()
    }

    /**
     * Test: Error handling for non-existent files
     * Expected: Returns null without crashing
     */
    @Test
    fun testCompressFilesIfNeeded_InvalidFile() {
        // Arrange
        val invalidUri = Uri.fromFile(File(testDir, "nonexistent.txt"))

        // Act
        val result = ZipCompressionHelper.compressFilesIfNeeded(context, listOf(invalidUri))

        // Assert
        assertNull(result, "Should handle invalid files gracefully")
    }

    /**
     * Test: calculateTotalSize with empty list
     * Expected: Returns 0
     */
    @Test
    fun testCalculateTotalSize_EmptyList() {
        // Act
        val size = ZipCompressionHelper.calculateTotalSize(context, emptyList())

        // Assert
        assertEquals(0, size)
    }

    /**
     * Test: ZIP files are created in cache directory
     * Expected: ZIP file exists after compression attempt
     */
    @Test
    fun testZipFileCreationLocation() {
        // Verify cache directory cleanup removes ZIP files
        // Create dummy ZIP file in cache
        val oldZipFile = File(context.cacheDir, "recordings_${System.currentTimeMillis() - 7200000}.zip")
        oldZipFile.createNewFile()

        assertTrue(oldZipFile.exists(), "Test ZIP file should be created")

        // Run cleanup (should remove old files)
        ZipCompressionHelper.cleanupOldZipFiles(context, maxAgeMs = 60 * 60 * 1000)

        // Assert
        assertTrue(!oldZipFile.exists(), "Old ZIP file should be cleaned up")
    }

    /**
     * Test: Cleanup only removes old files
     * Expected: Recent ZIP files are not deleted
     */
    @Test
    fun testCleanupOldZipFiles_PreservesRecent() {
        // Create a recent ZIP file
        val recentZipFile = File(context.cacheDir, "recordings_${System.currentTimeMillis()}.zip")
        recentZipFile.createNewFile()

        // Run cleanup with 1-hour threshold
        ZipCompressionHelper.cleanupOldZipFiles(context, maxAgeMs = 60 * 60 * 1000)

        // Assert
        assertTrue(recentZipFile.exists(), "Recent ZIP file should be preserved")

        // Cleanup
        recentZipFile.delete()
    }
}