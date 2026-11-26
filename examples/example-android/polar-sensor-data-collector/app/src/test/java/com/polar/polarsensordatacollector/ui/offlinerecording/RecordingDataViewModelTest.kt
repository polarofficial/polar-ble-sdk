package com.polar.polarsensordatacollector.ui.offlinerecording

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.polar.polarsensordatacollector.repository.DeviceConnectionState
import com.polar.polarsensordatacollector.repository.OfflineRecordingData
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.repository.ProgressInfo
import com.polar.polarsensordatacollector.repository.ResultOfRequest
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarOfflineRecordingData
import com.polar.sdk.api.model.PolarOfflineRecordingEntry
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RecordingDataViewModelTest {

    private val deviceId = "dev1"
    private val path = "/rec/1"
    private val repository = mockk<PolarDeviceRepository>(relaxed = true)
    private val connection = MutableStateFlow<DeviceConnectionState>(DeviceConnectionState.DeviceConnected(deviceId))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repository.deviceConnectionStatus } returns connection
        every { repository.getOfflineEntryFromCache(deviceId, path) } returns null
        every { repository.getOfflineRecordingWithProgress(deviceId, path) } returns flowOf()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(
        args: Map<String, Any?> = mapOf(
            "deviceIdFragmentArgument" to deviceId,
            "recordingPathFragmentArgument" to path
        )
    ) = RecordingDataViewModel(repository, SavedStateHandle(args))

    @Test
    fun `init throws when identifier is missing`() {
        // Act and Assert
        assertThrows(Exception::class.java) {
            newViewModel(mapOf("recordingPathFragmentArgument" to path))
        }
    }

    @Test
    fun `init throws when path is missing`() {
        // Act and Assert
        assertThrows(Exception::class.java) {
            newViewModel(mapOf("deviceIdFragmentArgument" to deviceId))
        }
    }

    @Test
    fun `init starts with empty loading when entry is not cached`() {
        // Act
        val state = newViewModel().recordingDataUiState

        // Assert
        assertEquals(RecordingDataUiState.Loading(0L, PolarBleApi.PolarDeviceDataType.HR), state)
    }

    @Test
    fun `init starts with entry info when entry is cached`() {
        // Arrange
        every { repository.getOfflineEntryFromCache(deviceId, path) } returns
            PolarOfflineRecordingEntry(path, 123L, LocalDateTime.of(2024, 1, 1, 0, 0), PolarBleApi.PolarDeviceDataType.ECG)

        // Act
        val state = newViewModel().recordingDataUiState

        // Assert
        assertEquals(RecordingDataUiState.Loading(123L, PolarBleApi.PolarDeviceDataType.ECG), state)
    }

    @Test
    fun `connection state follows repository status`() {
        // Arrange
        val viewModel = newViewModel()
        assertTrue(viewModel.devConnectionState.value.isConnected)

        // Act
        connection.value = DeviceConnectionState.DeviceDisconnecting(deviceId)

        // Assert
        assertFalse(viewModel.devConnectionState.value.isConnected)

        // Act
        connection.value = DeviceConnectionState.DeviceConnected(deviceId)

        // Assert
        assertTrue(viewModel.devConnectionState.value.isConnected)
    }

    @Test
    fun `fetch recording updates progress when loading and progress arrives`() {
        // Arrange
        every { repository.getOfflineRecordingWithProgress(deviceId, path) } returns
            flowOf(ResultOfRequest.Success(progress = ProgressInfo(50L, 100L, 50)))

        // Act
        val state = newViewModel().recordingDataUiState

        // Assert
        assertTrue(state is RecordingDataUiState.Loading)
        assertEquals(OfflineRecordingProgress(50L, 100L, 50), (state as RecordingDataUiState.Loading).progress)
    }

    @Test
    fun `fetch recording creates loading state when progress arrives after finish`() {
        // Arrange
        val recording = recordingData()
        every { repository.getOfflineRecordingWithProgress(deviceId, path) } returns flowOf(
            ResultOfRequest.Success(value = recording),
            ResultOfRequest.Success(progress = ProgressInfo(10L, 20L, 50))
        )

        // Act
        val state = newViewModel().recordingDataUiState

        // Assert
        assertEquals(
            RecordingDataUiState.Loading(
                20L,
                PolarBleApi.PolarDeviceDataType.ACC,
                OfflineRecordingProgress(10L, 20L, 50)
            ),
            state
        )
    }

    @Test
    fun `fetch recording shows data when value arrives`() {
        // Arrange
        val uri = mockk<Uri>()
        val recording = recordingData(uri)
        every { repository.getOfflineRecordingWithProgress(deviceId, path) } returns
            flowOf(ResultOfRequest.Success(value = recording))

        // Act
        val state = newViewModel().recordingDataUiState

        // Assert
        assertTrue(state is RecordingDataUiState.FetchedData)
        val data = (state as RecordingDataUiState.FetchedData).data
        assertEquals(recording.data.startTime.toString(), data.startTime)
        assertSame(uri, data.uri)
        assertEquals(2048L, data.size)
        assertEquals(1.5, data.downloadSpeed, 0.0)
    }

    @Test
    fun `fetch recording shows failure when success has no progress and no value`() {
        // Arrange
        every { repository.getOfflineRecordingWithProgress(deviceId, path) } returns
            flowOf(ResultOfRequest.Success<OfflineRecordingData>())

        // Act
        val state = newViewModel().recordingDataUiState

        // Assert
        assertTrue(state is RecordingDataUiState.Failure)
    }

    @Test
    fun `fetch recording shows failure when request fails`() {
        // Arrange
        val error = IllegalStateException("bad")
        every { repository.getOfflineRecordingWithProgress(deviceId, path) } returns
            flowOf(ResultOfRequest.Failure("fetch failed", error))

        // Act
        val state = newViewModel().recordingDataUiState

        // Assert
        assertEquals(RecordingDataUiState.Failure("fetch failed", error), state)
    }

    @Test
    fun `delete recording shows deleted when success`() {
        // Arrange
        coEvery { repository.deleteRecording(deviceId, path) } returns ResultOfRequest.Success()
        val viewModel = newViewModel()

        // Act
        viewModel.deleteRecording()

        // Assert
        assertEquals(RecordingDataUiState.RecordingDeleted, viewModel.recordingDataUiState)
    }

    @Test
    fun `delete recording shows failure when delete fails`() {
        // Arrange
        val error = RuntimeException("x")
        coEvery { repository.deleteRecording(deviceId, path) } returns ResultOfRequest.Failure("delete failed", error)
        val viewModel = newViewModel()

        // Act
        viewModel.deleteRecording()

        // Assert
        assertEquals(RecordingDataUiState.Failure("delete failed", error), viewModel.recordingDataUiState)
    }

    private fun recordingData(uri: Uri = mockk()): OfflineRecordingData {
        val data = mockk<PolarOfflineRecordingData>()
        every { data.startTime } returns LocalDateTime.of(2024, 1, 1, 12, 0)
        every { data.settings } returns null
        return OfflineRecordingData(data, uri, 2048L, 1.5)
    }
}
