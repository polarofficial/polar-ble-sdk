package com.polar.polarsensordatacollector.ui.utils

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.widget.TextView
import com.polar.polarsensordatacollector.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowToast
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DataViewerTest {

    private val authority = "com.polar.test.dataviewer"
    private lateinit var sourceFile: File
    private lateinit var targetDir: File

    class TestProvider : ContentProvider() {
        override fun onCreate() = true
        override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor {
            val cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME))
            cursor.addRow(arrayOf(uri.lastPathSegment))
            return cursor
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
        override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
            ParcelFileDescriptor.open(File(requireNotNull(sourcePath)), ParcelFileDescriptor.MODE_READ_ONLY)

        companion object {
            var sourcePath: String? = null
        }
    }

    @Before
    fun setUp() {
        targetDir = createTempDir("dataviewer-target")
        sourceFile = File(createTempDir("dataviewer-source"), "data.csv")
        TestProvider.sourcePath = sourceFile.absolutePath
        Robolectric.setupContentProvider(TestProvider::class.java, authority)
    }

    @After
    fun tearDown() {
        targetDir.deleteRecursively()
        sourceFile.parentFile?.deleteRecursively()
    }

    private fun launch(extras: Intent.() -> Unit): DataViewer {
        val intent = Intent(androidx.test.core.app.ApplicationProvider.getApplicationContext(), DataViewer::class.java).apply(extras)
        val activity = Robolectric.buildActivity(DataViewer::class.java, intent).create().get()
        shadowOf(Looper.getMainLooper()).idle()
        return activity
    }

    private fun text(activity: DataViewer) = activity.findViewById<TextView>(R.id.data_contents).text.toString()

    @Test
    fun `on create shows file name and content when uri is valid`() {
        // Arrange
        sourceFile.writeText("a,b\n1,2\n")

        // Act
        val activity = launch {
            putExtra("DATA_FILES_DIR_PATH", targetDir.absolutePath)
            putExtra("DATA_URI", "content://$authority/data.csv")
        }

        // Assert
        assertEquals("data.csv\na,b\n1,2\n", text(activity))
        assertEquals("a,b\n1,2\n", File(targetDir, "data.csv").readText())
    }

    @Test
    fun `on create shows toast when uri is missing`() {
        // Act
        launch {
            putExtra("DATA_FILES_DIR_PATH", targetDir.absolutePath)
            putExtra("TOAST_TEXT", "Cannot open")
        }

        // Assert
        assertEquals("Cannot open: ${targetDir.absolutePath}", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `on create shows nothing when directory extra is missing`() {
        // Act
        val activity = launch { }

        // Assert
        assertEquals(activity.getString(R.string.data_wait), text(activity))
        assertEquals(null, ShadowToast.getLatestToast())
    }

    @Test
    fun `get file returns destination file when copy fails`() {
        // Arrange
        val activity = launch { }

        // Act
        val file = activity.getFile(Uri.parse("content://$authority/missing.csv"), targetDir.absolutePath)

        // Assert
        assertEquals("missing.csv", file.name)
    }

    @Test
    fun `create file from stream copies all bytes`() {
        // Arrange
        val activity = launch { }
        val data = ByteArray(10_000) { (it % 251).toByte() }
        val destination = File(targetDir, "copy.bin")

        // Act
        activity.createFileFromStream(ByteArrayInputStream(data), destination)

        // Assert
        assertTrue(data.contentEquals(destination.readBytes()))
    }

    @Test
    fun `create file from stream does not throw when destination is invalid`() {
        // Arrange
        val activity = launch { }

        // Act
        activity.createFileFromStream(ByteArrayInputStream(ByteArray(1)), File(targetDir, "no/such/dir/file"))

        // Assert
        assertTrue(!File(targetDir, "no").exists())
    }

    @Test
    fun `on create limits text when file is very long`() {
        // Arrange
        sourceFile.writeText((1..100_050).joinToString("\n"))

        // Act
        val activity = launch {
            putExtra("DATA_FILES_DIR_PATH", targetDir.absolutePath)
            putExtra("DATA_URI", "content://$authority/data.csv")
        }

        // Assert
        assertEquals(100_000, text(activity).length)
        assertTrue(text(activity).startsWith("data.csv\nShowing first 100000 lines of 100050 total lines."))
    }
}
