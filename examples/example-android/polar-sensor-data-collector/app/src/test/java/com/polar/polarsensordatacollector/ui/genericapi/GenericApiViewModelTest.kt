package com.polar.polarsensordatacollector.ui.genericapi

import android.app.Activity
import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.repository.ResultOfRequest
import com.polar.polarsensordatacollector.ui.utils.DataViewer
import com.polar.polarsensordatacollector.ui.utils.FileUtils
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class GenericApiViewModelTest {

    private val deviceId = "dev1"
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val activity = Robolectric.buildActivity(Activity::class.java).create().get()
    private val repository = mockk<PolarDeviceRepository>(relaxed = true)
    private val fileUtils = mockk<FileUtils>(relaxed = true)
    private val viewModel = GenericApiViewModel(activity, repository, fileUtils, deviceId)
    private val uri = Uri.parse("content://files/GENERIC_API/x")

    @Test
    fun `write file shows success toast when write succeeds`() {
        // Arrange
        coEvery { repository.writeFile(deviceId, "/a.txt", any()) } returns ResultOfRequest.Success(Unit)

        // Act
        viewModel.writeFile("/a.txt", byteArrayOf(1))

        // Assert
        assertEquals("Successfully written file at path '/a.txt' on device dev1", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `write file shows failure toast when write fails`() {
        // Arrange
        coEvery { repository.writeFile(deviceId, "/a.txt", any()) } returns ResultOfRequest.Failure("err", null)

        // Act
        viewModel.writeFile("/a.txt", byteArrayOf(1))

        // Assert
        assertEquals("Failed to write file at path '/a.txt' on device dev1", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `read file saves data and opens viewer when read succeeds`() {
        // Arrange
        val data = byteArrayOf(1, 2)
        val name = slot<String>()
        coEvery { repository.readFile(deviceId, "/dir/file.bin") } returns ResultOfRequest.Success(data)
        every { fileUtils.saveToFile(data, capture(name)) } returns uri

        // Act
        viewModel.readFile("/dir/file.bin")

        // Assert
        assertEquals("GENERIC_API/file.bin", name.captured)
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(DataViewer::class.java.name, intent.component?.className)
        assertEquals(uri.toString(), intent.getStringExtra("DATA_URI"))
    }

    @Test
    fun `read file shows failure toast when read fails`() {
        // Arrange
        coEvery { repository.readFile(deviceId, "/f") } returns ResultOfRequest.Failure("err", null)

        // Act
        viewModel.readFile("/f")

        // Assert
        assertEquals("Failed to read file '/f' on device dev1", ShadowToast.getTextOfLatestToast())
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `list files saves one item per line and opens viewer`() {
        // Arrange
        val saved = slot<ByteArray>()
        coEvery { repository.listFiles(deviceId, "/", true) } returns ResultOfRequest.Success(listOf("/a", "/b"))
        every { fileUtils.saveToFile(capture(saved), "GENERIC_API/FILES_LIST.txt") } returns uri

        // Act
        viewModel.listFiles("/", true)

        // Assert
        assertEquals("/a\n/b\n", String(saved.captured))
        assertEquals(uri.toString(), shadowOf(activity).nextStartedActivity.getStringExtra("DATA_URI"))
    }

    @Test
    fun `list files shows failure toast when list fails`() {
        // Arrange
        coEvery { repository.listFiles(deviceId, "/", false) } returns ResultOfRequest.Failure("err", null)

        // Act
        viewModel.listFiles("/", false)

        // Assert
        assertEquals("Failed to list files at path '/' on device dev1", ShadowToast.getTextOfLatestToast())
        verify(exactly = 0) { fileUtils.saveToFile(any(), any()) }
    }

    @Test
    fun `delete file shows toast for success and failure`() {
        // Arrange
        coEvery { repository.deleteFile(deviceId, "/ok") } returns ResultOfRequest.Success(Unit)
        coEvery { repository.deleteFile(deviceId, "/bad") } returns ResultOfRequest.Failure("err", null)

        // Act
        viewModel.deleteFile("/ok")
        val success = ShadowToast.getTextOfLatestToast()
        viewModel.deleteFile("/bad")

        // Assert
        assertEquals("Successfully deleted file '/ok' on device dev1", success)
        assertEquals("Failed to delete file at path '/bad' on device dev1", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `create folder shows toast for success and failure`() {
        // Arrange
        coEvery { repository.createFolder(deviceId, "/ok") } returns ResultOfRequest.Success(Unit)
        coEvery { repository.createFolder(deviceId, "/bad") } returns ResultOfRequest.Failure("err", null)

        // Act
        viewModel.createFolder("/ok")
        val success = ShadowToast.getTextOfLatestToast()
        viewModel.createFolder("/bad")

        // Assert
        assertEquals("Successfully created folder '/ok' on device dev1", success)
        assertEquals("Failed to create folder '/bad' on device dev1", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `factory creates view model`() {
        // Act
        val created = GenericApiViewModelFactory(repository, fileUtils, app, deviceId).create(GenericApiViewModel::class.java)

        // Assert
        assertNull(created.message.value)
    }
}
