package com.polar.polarsensordatacollector.ui.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.polar.polarsensordatacollector.testutil.FileProviderTestSupport
import com.polar.sdk.api.model.Errorlog
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class FileUtilsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        FileProviderTestSupport.reset()
    }

    @Test
    fun `save to file writes bytes and returns file provider uri`() {
        // Arrange
        val data = byteArrayOf(1, 2, 3)

        // Act
        val uri = FileUtils(context).saveToFile(data, "fileutils_test.bin")

        // Assert
        assertArrayEquals(data, File(context.filesDir, "fileutils_test.bin").readBytes())
        assertEquals("${context.packageName}.fileprovider", uri.authority)
        assertTrue(uri.toString().endsWith("fileutils_test.bin"))
    }

    @Test
    fun `save error log to file writes bytes to error log file`() {
        // Arrange
        val data = "error log".toByteArray()

        // Act
        ErrorLogUtils.saveErrorLogToFile(context, data)

        // Assert
        assertArrayEquals(data, File(context.filesDir, Errorlog.ERRORLOG_FILENAME).readBytes())
    }
}
