package com.polar.polarsensordatacollector.ui.landing

import com.polar.polarsensordatacollector.DataCollector
import com.polar.polarsensordatacollector.repository.AvailableFeatures
import com.polar.polarsensordatacollector.repository.DeviceConnectionState
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.verify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OnlineRecordingViewModelStartStreamTest {

    private val deviceId = "dev1"
    private val connectionStatus = MutableStateFlow<DeviceConnectionState>(DeviceConnectionState.DeviceNotConnected())
    private val repository = mockk<PolarDeviceRepository>(relaxed = true)
    private val collector = mockk<DataCollector>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repository.availableFeatures } returns MutableStateFlow(AvailableFeatures())
        every { repository.deviceChargingStates } returns MutableStateFlow(emptyMap())
        every { repository.deviceConnectionStatus } returns connectionStatus
        every { repository.getDeviceName(deviceId) } returns "device"
        every { repository.isFeatureReady(any(), any()) } returns true
        every { collector.finalizeAllStreams() } returns ArrayList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() =
        OnlineRecordingViewModel(repository, collector, androidx.lifecycle.SavedStateHandle(mapOf(ONLINE_OFFLINE_KEY_DEVICE_ID to deviceId)))

    private fun <T> failingFlow(): Flow<T> = flow { throw IllegalStateException("stream error") }

    private fun awaitState(viewModel: OnlineRecordingViewModel, feature: PolarDeviceDataType, expected: StreamingFeatureState.STATES) = runBlocking {
        withTimeout(5_000) {
            viewModel.uiStreamingState.first { it.streamingRecordingState[feature]?.state == expected }
        }
    }

    private fun assertStreamErrorStopsFeature(feature: PolarDeviceDataType) {
        // Arrange
        val finalized = CountDownLatch(1)
        every { collector.finalizeAllStreams() } answers { finalized.countDown(); ArrayList() }
        val viewModel = newViewModel()

        // Act
        viewModel.startStream(feature)

        // Assert
        awaitState(viewModel, feature, StreamingFeatureState.STATES.STOPPED)
        assertEquals(true, finalized.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `start stream stops ecg when ecg stream fails`() {
        every { repository.startEcgStream(any(), any()) } returns failingFlow()
        assertStreamErrorStopsFeature(PolarDeviceDataType.ECG)
    }

    @Test
    fun `start stream stops acc when acc stream fails`() {
        every { repository.startAccStreaming(any(), any()) } returns failingFlow()
        assertStreamErrorStopsFeature(PolarDeviceDataType.ACC)
    }

    @Test
    fun `start stream stops gyro when gyro stream fails`() {
        every { repository.startGyroStreaming(any(), any()) } returns failingFlow()
        assertStreamErrorStopsFeature(PolarDeviceDataType.GYRO)
    }

    @Test
    fun `start stream stops magnetometer when magnetometer stream fails`() {
        every { repository.startMagnetometerStream(any(), any()) } returns failingFlow()
        assertStreamErrorStopsFeature(PolarDeviceDataType.MAGNETOMETER)
    }

    @Test
    fun `start stream stops ppi when ppi stream fails`() {
        every { repository.startPpiStream(any()) } returns failingFlow()
        assertStreamErrorStopsFeature(PolarDeviceDataType.PPI)
    }

    @Test
    fun `start stream stops ppg when ppg stream fails`() {
        every { repository.startPpgStream(any(), any()) } returns failingFlow()
        assertStreamErrorStopsFeature(PolarDeviceDataType.PPG)
    }

    @Test
    fun `start stream stops pressure when pressure stream fails`() {
        every { repository.startPressureStream(any(), any()) } returns failingFlow()
        assertStreamErrorStopsFeature(PolarDeviceDataType.PRESSURE)
    }

    @Test
    fun `start stream stops location when location stream fails`() {
        every { repository.startLocationStream(any(), any()) } returns failingFlow()
        assertStreamErrorStopsFeature(PolarDeviceDataType.LOCATION)
    }

    @Test
    fun `start stream stops temperature when temperature stream fails`() {
        every { repository.startTemperatureStreaming(any(), any()) } returns failingFlow()
        assertStreamErrorStopsFeature(PolarDeviceDataType.TEMPERATURE)
    }

    @Test
    fun `start stream stops skin temperature when skin temperature stream fails`() {
        every { repository.startSkinTemperatureStreaming(any(), any()) } returns failingFlow()
        assertStreamErrorStopsFeature(PolarDeviceDataType.SKIN_TEMPERATURE)
    }

    @Test
    fun `start stream does not record when settings request fails`() {
        // Arrange
        coEvery { repository.getAvailableStreamSettings(any(), any()) } throws IllegalStateException("no settings")
        val viewModel = newViewModel()
        val features = listOf(
            PolarDeviceDataType.ECG, PolarDeviceDataType.ACC, PolarDeviceDataType.GYRO, PolarDeviceDataType.MAGNETOMETER,
            PolarDeviceDataType.PPG, PolarDeviceDataType.PRESSURE, PolarDeviceDataType.LOCATION,
            PolarDeviceDataType.TEMPERATURE, PolarDeviceDataType.SKIN_TEMPERATURE
        )

        // Act
        features.forEach { viewModel.startStream(it) }

        // Assert
        features.forEach {
            coVerify(timeout = 5_000) { repository.getAvailableStreamSettings(any(), it) }
        }
        verify(exactly = 0) { repository.startEcgStream(any(), any()) }
        verify(exactly = 0) { repository.startAccStreaming(any(), any()) }
        verify(exactly = 0) { repository.startGyroStreaming(any(), any()) }
        verify(exactly = 0) { repository.startMagnetometerStream(any(), any()) }
        verify(exactly = 0) { repository.startPpgStream(any(), any()) }
        verify(exactly = 0) { repository.startPressureStream(any(), any()) }
        verify(exactly = 0) { repository.startLocationStream(any(), any()) }
        verify(exactly = 0) { repository.startTemperatureStreaming(any(), any()) }
        verify(exactly = 0) { repository.startSkinTemperatureStreaming(any(), any()) }
        features.forEach {
            assertNull(viewModel.uiStreamingState.value.streamingRecordingState[it]?.takeIf { s -> s.state == StreamingFeatureState.STATES.RECORDING })
        }
    }

    @Test
    fun `start stream still records when device name is unknown`() {
        // Arrange
        every { repository.getDeviceName(deviceId) } returns null
        every { repository.startEcgStream(any(), any()) } returns flow { kotlinx.coroutines.awaitCancellation() }
        every { repository.startAccStreaming(any(), any()) } returns flow { kotlinx.coroutines.awaitCancellation() }
        val viewModel = newViewModel()

        // Act
        viewModel.startStream(PolarDeviceDataType.ECG)
        viewModel.startStream(PolarDeviceDataType.ACC)

        // Assert
        awaitState(viewModel, PolarDeviceDataType.ECG, StreamingFeatureState.STATES.RECORDING)
        awaitState(viewModel, PolarDeviceDataType.ACC, StreamingFeatureState.STATES.RECORDING)
    }
}
