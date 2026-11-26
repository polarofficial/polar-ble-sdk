package com.polar.polarsensordatacollector.ui.landing

import com.polar.androidcommunications.api.ble.model.gatt.client.BatteryPresentState
import com.polar.androidcommunications.api.ble.model.gatt.client.ChargeState
import com.polar.androidcommunications.api.ble.model.gatt.client.PowerSourceState
import com.polar.androidcommunications.api.ble.model.gatt.client.PowerSourcesState
import com.polar.polarsensordatacollector.model.Device
import com.polar.polarsensordatacollector.repository.AvailableFeatures
import com.polar.polarsensordatacollector.repository.DeviceConnectionState
import com.polar.polarsensordatacollector.repository.DeviceInformation
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository.SdkFeaturesReadyEvent
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHrBroadcastData
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.EnumMap

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MainViewModelTest {

    private val id = "dev1"
    private val device = Device("AA:BB", id, "Polar H10")
    private val repository = mockk<PolarDeviceRepository>(relaxed = true)
    private val blePower = MutableStateFlow(true)
    private val connection = MutableStateFlow<DeviceConnectionState>(DeviceConnectionState.DeviceNotConnected())
    private val information = MutableStateFlow(DeviceInformation())
    private val features = MutableStateFlow(AvailableFeatures())
    private val sdkReady = MutableStateFlow(SdkFeaturesReadyEvent())
    private val guidance = MutableSharedFlow<String>(extraBufferCapacity = 1)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repository.isPhoneBlePowerOn } returns blePower
        every { repository.deviceConnectionStatus } returns connection
        every { repository.deviceInformation } returns information
        every { repository.availableFeatures } returns features
        every { repository.sdkFeaturesReady } returns sdkReady
        every { repository.disconnectGuidance } returns guidance
        coEvery { repository.isOfflineExerciseV2Supported(any()) } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = MainViewModel(repository)

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Condition not reached" }
            Thread.sleep(10)
        }
    }

    private fun hrData(deviceId: String, hr: Int): PolarHrBroadcastData {
        val info = mockk<PolarDeviceInfo>()
        every { info.deviceId } returns deviceId
        val data = mockk<PolarHrBroadcastData>()
        every { data.polarDeviceInfo } returns info
        every { data.hr } returns hr
        return data
    }

    @Test
    fun `ui connection state follows phone ble power`() {
        // Arrange
        val viewModel = newViewModel()
        assertEquals(MainViewModel.DeviceConnectionStates.NOT_CONNECTED, viewModel.uiConnectionState.value.state)

        // Act
        blePower.value = false

        // Assert
        assertEquals(MainViewModel.DeviceConnectionStates.PHONE_BLE_OFF, viewModel.uiConnectionState.value.state)
        assertFalse(viewModel.isBluetoothEnabled())
    }

    @Test
    fun `ui connection state shows connecting when device connecting`() {
        // Arrange
        val viewModel = newViewModel()

        // Act
        connection.value = DeviceConnectionState.DeviceConnecting(id)

        // Assert
        assertEquals(DeviceConnectionUiState(id, MainViewModel.DeviceConnectionStates.CONNECTING_TO_SELECTED_DEVICE), viewModel.uiConnectionState.value)
        assertFalse(viewModel.isConnected())
    }

    @Test
    fun `ui connection state shows connected and checks v 2 support when device connected`() {
        // Arrange
        val viewModel = newViewModel()

        // Act
        connection.value = DeviceConnectionState.DeviceConnected(id)
        await { viewModel.uiOfflineRecordingV2State.value.isAvailable }

        // Assert
        assertTrue(viewModel.isConnected())
        assertEquals(id, viewModel.uiOfflineRecordingV2State.value.identifier)
    }

    @Test
    fun `ui offline recording v 2 state is unavailable when support check fails`() {
        // Arrange
        coEvery { repository.isOfflineExerciseV2Supported(any()) } throws IllegalStateException("fail")
        val viewModel = newViewModel()

        // Act
        connection.value = DeviceConnectionState.DeviceConnected(id)
        await { viewModel.uiOfflineRecordingV2State.value.identifier == id }

        // Assert
        assertFalse(viewModel.uiOfflineRecordingV2State.value.isAvailable)
    }

    @Test
    fun `connection state ignores disconnecting`() {
        // Arrange
        val viewModel = newViewModel()
        connection.value = DeviceConnectionState.DeviceConnecting(id)

        // Act
        connection.value = DeviceConnectionState.DeviceDisconnecting(id)

        // Assert
        assertEquals(MainViewModel.DeviceConnectionStates.CONNECTING_TO_SELECTED_DEVICE, viewModel.uiConnectionState.value.state)
    }

    @Test
    fun `not connected resets device information and offline state`() {
        // Arrange
        val viewModel = newViewModel()
        information.value = DeviceInformation(id, "1.2.3", 80)
        val offline = EnumMap<PolarBleApi.PolarDeviceDataType, Boolean>(PolarBleApi.PolarDeviceDataType::class.java)
        offline[PolarBleApi.PolarDeviceDataType.ACC] = true
        features.value = AvailableFeatures(id, availableOfflineFeatures = offline)
        assertTrue(viewModel.uiOfflineRecordingState.value.isAvailable)

        // Act
        connection.value = DeviceConnectionState.DeviceNotConnected(id)

        // Assert
        assertFalse(viewModel.uiOfflineRecordingState.value.isAvailable)
        assertNull(viewModel.uiDeviceInformationState.value.batteryLevel)
        assertEquals("", viewModel.uiDeviceInformationState.value.firmwareVersion)
        assertEquals(OfflineRecordingV2AvailabilityUiState(), viewModel.uiOfflineRecordingV2State.value)
    }

    @Test
    fun `device information is published`() {
        // Arrange
        val viewModel = newViewModel()
        val power = PowerSourcesState(BatteryPresentState.PRESENT, PowerSourceState.CONNECTED, PowerSourceState.NOT_CONNECTED)

        // Act
        information.value = DeviceInformation(id, "2.0", 55, ChargeState.CHARGING, power)

        // Assert
        assertEquals(DeviceInformationUiState(id, "2.0", 55, ChargeState.CHARGING, power), viewModel.uiDeviceInformationState.value)
    }

    @Test
    fun `available features keeps offline state unchanged when no offline feature`() {
        // Arrange
        val viewModel = newViewModel()

        // Act
        features.value = AvailableFeatures(id)

        // Assert
        assertFalse(viewModel.uiOfflineRecordingState.value.isAvailable)
        assertEquals("", viewModel.uiOfflineRecordingState.value.identifier)
    }

    @Test
    fun exposesRepositoryFlows() {
        // Act
        val viewModel = newViewModel()

        // Assert
        assertSame(sdkReady, viewModel.uiSdkFeaturesReadyState)
        assertSame(guidance, viewModel.disconnectGuidance)
    }

    @Test
    fun `connect to device connects and stores selected device`() {
        // Arrange
        val viewModel = newViewModel()

        // Act
        viewModel.connectToDevice(device)
        await { viewModel.selectedDevice == device }

        // Assert
        verify { repository.connectToDevice(id) }
    }

    @Test
    fun `disconnect all devices disconnects each device`() {
        // Arrange
        val other = device.copy(deviceId = "dev2")

        // Act
        newViewModel().disconnectAllDevices(listOf(device, other))

        // Assert
        verify(timeout = 5_000) { repository.disconnectFromDevice(id) }
        verify(timeout = 5_000) { repository.disconnectFromDevice("dev2") }
    }

    @Test
    fun `disconnect from device disconnects device`() {
        // Act
        newViewModel().disconnectFromDevice(device)

        // Assert
        verify(timeout = 5_000) { repository.disconnectFromDevice(id) }
    }

    @Test
    fun `delegating calls forward to repository`() {
        // Arrange
        val viewModel = newViewModel()
        every { repository.getDeviceName(id) } returns "H10"
        every { repository.isFeatureReady(id, PolarBleApi.PolarBleSdkFeature.FEATURE_HR) } returns true
        every { repository.searchForDevice("Polar") } returns flowOf()

        // Act
        viewModel.selectDevice(id)

        // Assert
        verify { repository.selectDevice(id) }
        assertEquals("H10", viewModel.getDeviceName(id))
        assertTrue(viewModel.isFeatureReady(id, PolarBleApi.PolarBleSdkFeature.FEATURE_HR))
        assertTrue(runBlocking { viewModel.searchForDevice("Polar").toList() }.isEmpty())
    }

    @Test
    fun `start listening hr publishes latest data and stop clears it`() {
        // Arrange
        val first = hrData("a", 60)
        val second = hrData("b", 70)
        every { repository.listenHrBroadcasts(setOf("x")) } returns flowOf(first, second)
        val viewModel = newViewModel()

        // Act
        viewModel.startListeningHr(setOf("x"))

        // Assert
        assertSame(second, viewModel.hrData.value)

        // Act
        viewModel.stopListeningHr()

        // Assert
        assertNull(viewModel.hrData.value)
    }

    @Test
    fun `request remove online offline fragments emits event`() {
        // Arrange
        val viewModel = newViewModel()
        val events = mutableListOf<Unit>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch { viewModel.removeOnlineOfflineFragments.collect { events.add(it) } }

        // Act
        viewModel.requestRemoveOnlineOfflineFragments()

        // Assert
        assertEquals(1, events.size)
        job.cancel()
    }

    @Test
    fun `on cleared cancels hr job`() {
        // Arrange
        val cancelled = java.util.concurrent.CountDownLatch(1)
        every { repository.listenHrBroadcasts(any()) } returns flow {
            try {
                awaitCancellation()
            } finally {
                cancelled.countDown()
            }
        }
        val viewModel = newViewModel()
        viewModel.startListeningHr(null)
        assertEquals(1L, cancelled.count)

        // Act
        viewModel.onCleared()

        // Assert
        assertTrue(cancelled.await(5, java.util.concurrent.TimeUnit.SECONDS))
    }
}
