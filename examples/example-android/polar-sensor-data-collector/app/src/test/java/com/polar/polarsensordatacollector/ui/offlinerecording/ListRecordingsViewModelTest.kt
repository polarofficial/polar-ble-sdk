package com.polar.polarsensordatacollector.ui.offlinerecording

import androidx.lifecycle.SavedStateHandle
import com.polar.polarsensordatacollector.repository.DeviceConnectionState
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.repository.ResultOfRequest
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarOfflineRecordingEntry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ListRecordingsViewModelTest {

    private val deviceId = "dev1"
    private val repository = mockk<PolarDeviceRepository>(relaxed = true)

    private fun entry(path: String) =
        PolarOfflineRecordingEntry(path, 10L, LocalDateTime.of(2024, 1, 1, 0, 0), PolarBleApi.PolarDeviceDataType.HR)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repository.deviceConnectionStatus } returns MutableStateFlow<DeviceConnectionState>(DeviceConnectionState.DeviceConnected(deviceId))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = ListRecordingsViewModel(repository, SavedStateHandle(mapOf("deviceIdFragmentArgument" to deviceId)))

    @Test
    fun `init throws when identifier is missing`() {
        // Act and Assert
        assertThrows(Exception::class.java) { ListRecordingsViewModel(repository, SavedStateHandle()) }
    }

    @Test
    fun `list offline recordings collects entries and completes`() {
        // Arrange
        val first = entry("/a")
        val second = entry("/b")
        every { repository.listOfflineRecordings(deviceId) } returns flowOf(first, second)
        val viewModel = newViewModel()

        // Act
        viewModel.listOfflineRecordings()

        // Assert
        val status = viewModel.offlineRecordingsUiState.fetchStatus as OfflineRecordingFetch.Success
        assertEquals(listOf(first, second), status.fetchedRecordings)
    }

    @Test
    fun `list offline recordings reports failure when flow throws`() {
        // Arrange
        every { repository.listOfflineRecordings(deviceId) } returns flow { throw IllegalStateException("boom") }
        val viewModel = newViewModel()

        // Act
        viewModel.listOfflineRecordings()

        // Assert
        val status = viewModel.offlineRecordingsUiState.fetchStatus as OfflineRecordingFetch.Failure
        assertEquals("Fetching of recordings failed: boom", status.message)
    }

    @Test
    fun `delete recording is ignored when listing is not complete`() {
        // Arrange
        val viewModel = newViewModel()

        // Act
        viewModel.deleteRecording(entry("/a"))

        // Assert
        assertNull(viewModel.offlineRecordingsUiState.deletingEntryPath)
        coVerify(exactly = 0) { repository.deleteRecording(any(), any()) }
    }

    @Test
    fun `delete recording removes entry when delete succeeds`() {
        // Arrange
        val first = entry("/a")
        val second = entry("/b")
        every { repository.listOfflineRecordings(deviceId) } returns flowOf(first, second)
        coEvery { repository.deleteRecording(deviceId, "/a") } returns ResultOfRequest.Success()
        val viewModel = newViewModel()
        viewModel.listOfflineRecordings()

        // Act
        viewModel.deleteRecording(first)

        // Assert
        val status = viewModel.offlineRecordingsUiState.fetchStatus as OfflineRecordingFetch.Success
        assertEquals(listOf(second), status.fetchedRecordings)
        assertNull(viewModel.offlineRecordingsUiState.deletingEntryPath)
    }

    @Test
    fun `delete recording reports failure when delete fails`() {
        // Arrange
        val first = entry("/a")
        every { repository.listOfflineRecordings(deviceId) } returns flowOf(first)
        coEvery { repository.deleteRecording(deviceId, "/a") } returns ResultOfRequest.Failure("nope", null)
        val viewModel = newViewModel()
        viewModel.listOfflineRecordings()

        // Act
        viewModel.deleteRecording(first)

        // Assert
        val status = viewModel.offlineRecordingsUiState.fetchStatus as OfflineRecordingFetch.Failure
        assertEquals("nope", status.message)
        assertNull(viewModel.offlineRecordingsUiState.deletingEntryPath)
        assertTrue(viewModel.offlineRecordingsUiState.fetchStatus !is OfflineRecordingFetch.Success)
    }
}
