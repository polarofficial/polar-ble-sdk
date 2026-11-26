package com.polar.polarsensordatacollector.ui.watchface

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.repository.ResultOfRequest
import com.polar.sdk.api.model.PolarWatchFaceComplication
import com.polar.sdk.api.model.PolarWatchFaceConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WatchFaceViewModelTest {

    private val deviceId = "dev1"
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val repository = mockk<PolarDeviceRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { repository.getWatchFaceConfig(deviceId) } returns ResultOfRequest.Success(PolarWatchFaceConfig(emptyList()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = WatchFaceViewModel(repository, deviceId, app)

    @Test
    fun `init loads slots from device when read succeeds`() {
        // Arrange
        val config = PolarWatchFaceConfig(listOf(PolarWatchFaceComplication.ALARM, PolarWatchFaceComplication.BATTERY))
        coEvery { repository.getWatchFaceConfig(deviceId) } returns ResultOfRequest.Success(config)

        // Act
        val state = newViewModel().uiState.value

        // Assert
        assertFalse(state.isLoading)
        assertEquals(
            listOf(PolarWatchFaceComplication.ALARM, PolarWatchFaceComplication.BATTERY, null, null),
            state.slots
        )
        assertEquals(app.getString(R.string.watch_face_status_loaded, 2), state.readStatus)
    }

    @Test
    fun `load current config keeps slots and reports null when value is null`() {
        // Arrange
        coEvery { repository.getWatchFaceConfig(deviceId) } returns ResultOfRequest.Success(null)

        // Act
        val state = newViewModel().uiState.value

        // Assert
        assertFalse(state.isLoading)
        assertEquals(app.getString(R.string.watch_face_status_read_null), state.readStatus)
        assertEquals(List(SLOT_COUNT) { null }, state.slots)
    }

    @Test
    fun `load current config reports failure when read fails`() {
        // Arrange
        coEvery { repository.getWatchFaceConfig(deviceId) } returns ResultOfRequest.Failure("no link", null)

        // Act
        val state = newViewModel().uiState.value

        // Assert
        assertFalse(state.isLoading)
        assertEquals(app.getString(R.string.watch_face_status_read_failed, "no link"), state.readStatus)
    }

    @Test
    fun `set slot replaces slot and clears result message`() {
        // Arrange
        val viewModel = newViewModel()

        // Act
        viewModel.setSlot(2, PolarWatchFaceComplication.COMPASS)

        // Assert
        val state = viewModel.uiState.value
        assertEquals(PolarWatchFaceComplication.COMPASS, state.slots[2])
        assertNull(state.slots[0])
        assertNull(state.resultMessage)
    }

    @Test
    fun `set slot throws when index is out of range`() {
        // Arrange
        val viewModel = newViewModel()

        // Act and Assert
        assertThrows(IllegalArgumentException::class.java) { viewModel.setSlot(SLOT_COUNT, PolarWatchFaceComplication.ALARM) }
        assertThrows(IllegalArgumentException::class.java) { viewModel.setSlot(-1, PolarWatchFaceComplication.ALARM) }
    }

    @Test
    fun `apply complications writes empty for unset slots and reports success`() {
        // Arrange
        val viewModel = newViewModel()
        viewModel.setSlot(1, PolarWatchFaceComplication.BATTERY)
        val written = slot<PolarWatchFaceConfig>()
        coEvery { repository.setWatchFaceConfig(deviceId, capture(written)) } returns ResultOfRequest.Success(Unit)

        // Act
        viewModel.applyComplications()

        // Assert
        assertEquals(
            listOf(
                PolarWatchFaceComplication.EMPTY,
                PolarWatchFaceComplication.BATTERY,
                PolarWatchFaceComplication.EMPTY,
                PolarWatchFaceComplication.EMPTY
            ),
            written.captured.enabledComplications
        )
        assertEquals(app.getString(R.string.watch_face_applied_success), viewModel.uiState.value.resultMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `apply complications reports failure when write fails`() {
        // Arrange
        val viewModel = newViewModel()
        coEvery { repository.setWatchFaceConfig(deviceId, any()) } returns ResultOfRequest.Failure("write error", null)

        // Act
        viewModel.applyComplications()

        // Assert
        assertEquals(app.getString(R.string.watch_face_failed_prefix) + "write error", viewModel.uiState.value.resultMessage)
        coVerify { repository.setWatchFaceConfig(deviceId, any()) }
    }

    @Test
    fun `factory creates view model`() {
        // Act
        val viewModel = WatchFaceViewModelFactory(repository, deviceId, app).create(WatchFaceViewModel::class.java)

        // Assert
        assertEquals(List(SLOT_COUNT) { null }, viewModel.uiState.value.slots)
    }
}
