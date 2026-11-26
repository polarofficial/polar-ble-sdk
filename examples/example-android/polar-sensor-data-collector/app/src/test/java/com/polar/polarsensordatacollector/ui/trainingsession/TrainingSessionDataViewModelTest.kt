package com.polar.polarsensordatacollector.ui.trainingsession

import androidx.lifecycle.SavedStateHandle
import com.polar.polarsensordatacollector.repository.DeviceConnectionState
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.repository.ResultOfRequest
import com.polar.sdk.api.model.trainingsession.PolarTrainingSession
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionFetchResult
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionProgress
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TrainingSessionDataViewModelTest {

    private val deviceId = "dev1"
    private val path = "/U/0/TSESS/E/1"
    private val connection = MutableStateFlow<DeviceConnectionState>(DeviceConnectionState.DeviceConnected(deviceId))
    private val repository = mockk<PolarDeviceRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repository.deviceConnectionStatus } returns connection
        every { repository.getTrainingSessionWithProgress(deviceId, path) } returns emptyFlow()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = TrainingSessionDataViewModel(
        repository,
        SavedStateHandle(mapOf("deviceIdFragmentArgument" to deviceId, "trainingSessionPathFragmentArgument" to path))
    )

    private fun fetchReturns(vararg results: ResultOfRequest<PolarTrainingSessionFetchResult>) {
        every { repository.getTrainingSessionWithProgress(deviceId, path) } returns flowOf(*results)
    }

    @Test
    fun `init throws when arguments are missing`() {
        // Act and Assert
        assertThrows(Exception::class.java) { TrainingSessionDataViewModel(repository, SavedStateHandle()) }
        assertThrows(Exception::class.java) {
            TrainingSessionDataViewModel(repository, SavedStateHandle(mapOf("deviceIdFragmentArgument" to deviceId)))
        }
    }

    @Test
    fun `ui state is fetching before any result`() {
        // Act
        val viewModel = newViewModel()

        // Assert
        assertTrue(viewModel.trainingSessionDataUiState is TrainingSessionDataUiState.IsFetching)
    }

    @Test
    fun `ui state shows progress when progress result arrives`() {
        // Arrange
        val progress = PolarTrainingSessionProgress(100L, 50L, 50)
        fetchReturns(ResultOfRequest.Success(PolarTrainingSessionFetchResult.Progress(progress)))

        // Act
        val state = newViewModel().trainingSessionDataUiState

        // Assert
        assertSame(progress, (state as TrainingSessionDataUiState.Fetching).progress)
    }

    @Test
    fun `ui state shows data when complete result arrives`() {
        // Arrange
        val session = mockk<PolarTrainingSession>()
        fetchReturns(ResultOfRequest.Success(PolarTrainingSessionFetchResult.Complete(session)))

        // Act
        val state = newViewModel().trainingSessionDataUiState

        // Assert
        assertSame(session, (state as TrainingSessionDataUiState.FetchedData).data)
    }

    @Test
    fun `ui state is failure when success has null value`() {
        // Arrange
        fetchReturns(ResultOfRequest.Success(null))

        // Act
        val state = newViewModel().trainingSessionDataUiState

        // Assert
        val failure = state as TrainingSessionDataUiState.Failure
        assertEquals("Training session fetch returned empty result", failure.message)
    }

    @Test
    fun `ui state is failure when request fails`() {
        // Arrange
        val error = RuntimeException("boom")
        fetchReturns(ResultOfRequest.Failure("fetch failed", error))

        // Act
        val state = newViewModel().trainingSessionDataUiState

        // Assert
        val failure = state as TrainingSessionDataUiState.Failure
        assertEquals("fetch failed", failure.message)
        assertSame(error, failure.throwable)
    }

    @Test
    fun `dev connection state follows device connection`() {
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
}
