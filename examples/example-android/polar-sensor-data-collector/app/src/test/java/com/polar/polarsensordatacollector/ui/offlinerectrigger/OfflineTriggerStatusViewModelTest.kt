package com.polar.polarsensordatacollector.ui.offlinerectrigger

import androidx.lifecycle.SavedStateHandle
import com.polar.polarsensordatacollector.repository.OfflineRecTriggerStatus
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.repository.ResultOfRequest
import com.polar.sdk.api.model.PolarOfflineRecordingTrigger
import com.polar.sdk.api.model.PolarOfflineRecordingTriggerMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OfflineTriggerStatusViewModelTest {

    private val deviceId = "dev1"
    private val triggerState = MutableStateFlow(OfflineRecTriggerStatus())
    private val repository = mockk<PolarDeviceRepository>(relaxed = true)
    private val trigger = PolarOfflineRecordingTrigger(PolarOfflineRecordingTriggerMode.TRIGGER_SYSTEM_START, emptyMap())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repository.triggerState } returns triggerState
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() =
        OfflineTriggerStatusViewModel(repository, SavedStateHandle(mapOf(OFFLINE_REC_TRIG_KEY_DEVICE_ID to deviceId)))

    private fun awaitState(viewModel: OfflineTriggerStatusViewModel, predicate: (OfflineRecTriggerStatusUiState) -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!predicate(viewModel.uiOfflineRecTriggerStatus.value)) {
            check(System.currentTimeMillis() < deadline) { "State not reached: ${viewModel.uiOfflineRecTriggerStatus.value}" }
            Thread.sleep(10)
        }
    }

    private fun awaitRequest() {
        coVerify(timeout = 5_000) { repository.getOfflineRecordingTriggerStatus(deviceId) }
    }

    @Test
    fun `init throws when device id is missing`() {
        // Act and Assert
        assertThrows(Exception::class.java) { OfflineTriggerStatusViewModel(repository, SavedStateHandle()) }
    }

    @Test
    fun `get offline recording trigger status shows current status when request succeeds`() {
        // Arrange
        coEvery { repository.getOfflineRecordingTriggerStatus(deviceId) } returns ResultOfRequest.Success(trigger)

        // Act
        val viewModel = newViewModel()
        awaitState(viewModel) { it is OfflineRecTriggerStatusUiState.CurrentStatus }

        // Assert
        val state = viewModel.uiOfflineRecTriggerStatus.value as OfflineRecTriggerStatusUiState.CurrentStatus
        assertSame(trigger, state.triggerStatus)
    }

    @Test
    fun `get offline recording trigger status shows failure when request fails`() {
        // Arrange
        coEvery { repository.getOfflineRecordingTriggerStatus(deviceId) } returns
            ResultOfRequest.Failure("Request failed", RuntimeException("boom"))

        // Act
        val viewModel = newViewModel()
        awaitState(viewModel) { it is OfflineRecTriggerStatusUiState.FetchingFailed }

        // Assert
        val state = viewModel.uiOfflineRecTriggerStatus.value as OfflineRecTriggerStatusUiState.FetchingFailed
        assertEquals("Request failed\nError reason boom", state.message)
    }

    @Test
    fun `get offline recording trigger status keeps fetching when success has no value`() {
        // Arrange
        coEvery { repository.getOfflineRecordingTriggerStatus(deviceId) } returns ResultOfRequest.Success(null)

        // Act
        val viewModel = newViewModel()
        awaitRequest()

        // Assert
        assertTrue(viewModel.uiOfflineRecTriggerStatus.value is OfflineRecTriggerStatusUiState.FetchingStatus)
    }

    @Test
    fun `trigger state updates status when identifier matches`() {
        // Arrange
        coEvery { repository.getOfflineRecordingTriggerStatus(deviceId) } returns ResultOfRequest.Success(null)
        val viewModel = newViewModel()
        awaitRequest()

        // Act
        triggerState.value = OfflineRecTriggerStatus(deviceId, trigger)

        // Assert
        val state = viewModel.uiOfflineRecTriggerStatus.value as OfflineRecTriggerStatusUiState.CurrentStatus
        assertSame(trigger, state.triggerStatus)
    }

    @Test
    fun `trigger state is ignored when identifier differs`() {
        // Arrange
        coEvery { repository.getOfflineRecordingTriggerStatus(deviceId) } returns ResultOfRequest.Success(null)
        val viewModel = newViewModel()
        awaitRequest()

        // Act
        triggerState.value = OfflineRecTriggerStatus("other", trigger)

        // Assert
        assertTrue(viewModel.uiOfflineRecTriggerStatus.value is OfflineRecTriggerStatusUiState.FetchingStatus)
    }
}
