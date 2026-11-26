package com.polar.polarsensordatacollector.ui.activity

import androidx.lifecycle.SavedStateHandle
import com.polar.polarsensordatacollector.ui.landing.ONLINE_OFFLINE_KEY_DEVICE_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ActivityRecordingViewModelTest {

    @Test
    fun `identifier returns device id from saved state`() {
        // Arrange
        val state = SavedStateHandle(mapOf(ONLINE_OFFLINE_KEY_DEVICE_ID to "dev1"))

        // Act
        val viewModel = ActivityRecordingViewModel(state)

        // Assert
        assertEquals("dev1", viewModel.identifier)
    }

    @Test
    fun `init throws when device id is missing`() {
        // Act and Assert
        assertThrows(Exception::class.java) { ActivityRecordingViewModel(SavedStateHandle()) }
    }

    @Test
    fun `init view does not throw`() {
        // Arrange
        val viewModel = ActivityRecordingViewModel(SavedStateHandle(mapOf(ONLINE_OFFLINE_KEY_DEVICE_ID to "dev1")))

        // Act and Assert
        viewModel.initView()
    }
}
