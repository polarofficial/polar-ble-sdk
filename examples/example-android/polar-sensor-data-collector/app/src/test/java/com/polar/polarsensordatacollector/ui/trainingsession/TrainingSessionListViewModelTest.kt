package com.polar.polarsensordatacollector.ui.trainingsession

import androidx.lifecycle.SavedStateHandle
import com.polar.polarsensordatacollector.repository.DeviceConnectionState
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.repository.ResultOfRequest
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionReference
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ListTrainingSessionsViewModelTest {

    private val deviceId = "dev1"
    private val from = LocalDate.of(2024, 1, 1)
    private val to = LocalDate.of(2024, 1, 31)
    private val repository = mockk<PolarDeviceRepository>(relaxed = true)

    private fun ref(path: String) = PolarTrainingSessionReference(from, path, emptyList(), emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repository.deviceConnectionStatus } returns MutableStateFlow<DeviceConnectionState>(DeviceConnectionState.DeviceConnected(deviceId))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = ListTrainingSessionsViewModel(
        repository,
        SavedStateHandle(
            mapOf(
                "deviceIdFragmentArgument" to deviceId,
                "fromDateFragmentArgument" to "20240101",
                "toDateFragmentArgument" to "20240131"
            )
        )
    )

    private fun awaitFetch(viewModel: ListTrainingSessionsViewModel, predicate: (TrainingSessionFetch) -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!predicate(viewModel.trainingSessionsUiState.fetchStatus)) {
            check(System.currentTimeMillis() < deadline) { "State not reached" }
            Thread.sleep(10)
        }
    }

    @Test
    fun `init throws when argument is missing`() {
        // Act and Assert
        assertThrows(Exception::class.java) { ListTrainingSessionsViewModel(repository, SavedStateHandle()) }
    }

    @Test
    fun `ui state is in progress with no sessions initially`() {
        // Act
        val status = newViewModel().trainingSessionsUiState.fetchStatus

        // Assert
        assertTrue((status as TrainingSessionFetch.InProgress).fetchedTrainingSessions.isEmpty())
    }

    @Test
    fun `list training sessions collects references and completes`() {
        // Arrange
        val first = ref("/a")
        val second = ref("/b")
        every { repository.getTrainingSessionReferences(deviceId, from, to) } returns flowOf(first, second)
        val viewModel = newViewModel()

        // Act
        viewModel.listTrainingSessions()
        awaitFetch(viewModel) { it is TrainingSessionFetch.Success }

        // Assert
        val status = viewModel.trainingSessionsUiState.fetchStatus as TrainingSessionFetch.Success
        assertEquals(listOf(first, second), status.fetchedTrainingSessions)
    }

    @Test
    fun `list training sessions reports failure when flow throws`() {
        // Arrange
        every { repository.getTrainingSessionReferences(deviceId, from, to) } returns flow { throw IllegalStateException("boom") }
        val viewModel = newViewModel()

        // Act
        viewModel.listTrainingSessions()
        awaitFetch(viewModel) { it is TrainingSessionFetch.Failure }

        // Assert
        val status = viewModel.trainingSessionsUiState.fetchStatus as TrainingSessionFetch.Failure
        assertEquals("Fetching of trainingSessions failed", status.message)
        assertEquals("boom", status.throwable?.message)
    }

    @Test
    fun `delete training session removes entry when delete succeeds`() {
        // Arrange
        val first = ref("/a")
        val second = ref("/b")
        every { repository.getTrainingSessionReferences(deviceId, from, to) } returns flowOf(first, second)
        coEvery { repository.deleteTrainingSession(deviceId, "/a") } returns ResultOfRequest.Success()
        val viewModel = newViewModel()
        viewModel.listTrainingSessions()
        awaitFetch(viewModel) { it is TrainingSessionFetch.Success }

        // Act
        viewModel.deleteTrainingSession(first)

        // Assert
        val status = viewModel.trainingSessionsUiState.fetchStatus as TrainingSessionFetch.Success
        assertEquals(listOf(second), status.fetchedTrainingSessions)
        assertNull(viewModel.trainingSessionsUiState.deletingEntryPath)
    }

    @Test
    fun `delete training session keeps entry when delete fails`() {
        // Arrange
        val first = ref("/a")
        every { repository.getTrainingSessionReferences(deviceId, from, to) } returns flowOf(first)
        coEvery { repository.deleteTrainingSession(deviceId, "/a") } returns ResultOfRequest.Failure("nope", null)
        val viewModel = newViewModel()
        viewModel.listTrainingSessions()
        awaitFetch(viewModel) { it is TrainingSessionFetch.Success }

        // Act
        viewModel.deleteTrainingSession(first)

        // Assert
        val status = viewModel.trainingSessionsUiState.fetchStatus as TrainingSessionFetch.Success
        assertEquals(listOf(first), status.fetchedTrainingSessions)
        assertNull(viewModel.trainingSessionsUiState.deletingEntryPath)
        coVerify { repository.deleteTrainingSession(deviceId, "/a") }
    }
}
