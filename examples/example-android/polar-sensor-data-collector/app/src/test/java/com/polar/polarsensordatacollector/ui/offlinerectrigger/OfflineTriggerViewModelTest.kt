package com.polar.polarsensordatacollector.ui.offlinerectrigger

import com.polar.polarsensordatacollector.repository.DeviceConnectionState
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineTriggerViewModelTest {

    private val connection = MutableStateFlow<DeviceConnectionState>(DeviceConnectionState.DeviceNotConnected())
    private val repository = mockk<PolarDeviceRepository>(relaxed = true) {
        every { deviceConnectionStatus } returns connection
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `ui connection state is disconnected when repository reports not connected`() {
        // Act
        val viewModel = OfflineTriggerViewModel(repository)

        // Assert
        assertFalse(viewModel.uiConnectionState.value.isConnected)
    }

    @Test
    fun `ui connection state is connected when device connects`() {
        // Arrange
        val viewModel = OfflineTriggerViewModel(repository)

        // Act
        connection.value = DeviceConnectionState.DeviceConnected("dev1")

        // Assert
        assertTrue(viewModel.uiConnectionState.value.isConnected)
    }

    @Test
    fun `ui connection state is disconnected when device is disconnecting`() {
        // Arrange
        val viewModel = OfflineTriggerViewModel(repository)
        connection.value = DeviceConnectionState.DeviceConnected("dev1")

        // Act
        connection.value = DeviceConnectionState.DeviceDisconnecting("dev1")

        // Assert
        assertFalse(viewModel.uiConnectionState.value.isConnected)
    }

    @Test
    fun `ui connection state is disconnected when device is connecting`() {
        // Arrange
        val viewModel = OfflineTriggerViewModel(repository)
        connection.value = DeviceConnectionState.DeviceConnected("dev1")

        // Act
        connection.value = DeviceConnectionState.DeviceConnecting("dev1")

        // Assert
        assertFalse(viewModel.uiConnectionState.value.isConnected)
    }
}
