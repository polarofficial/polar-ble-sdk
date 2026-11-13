package com.polar.polarsensordatacollector.ui.landing

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.polar.polarsensordatacollector.DataCollector
import com.polar.polarsensordatacollector.repository.AvailableFeatures
import com.polar.polarsensordatacollector.repository.DeviceConnectionState
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.errors.PolarDeviceDisconnected
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OnlineRecordingViewModelTest {

    private val deviceId = "dev1"
    private val mainDispatcher = UnconfinedTestDispatcher()
    private val connectionStatus = MutableStateFlow<DeviceConnectionState>(DeviceConnectionState.DeviceNotConnected())
    private val repository = mockk<PolarDeviceRepository>(relaxed = true)
    private val collector = mockk<DataCollector>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        every { repository.availableFeatures } returns MutableStateFlow(AvailableFeatures())
        every { repository.deviceConnectionStatus } returns connectionStatus
        every { repository.getDeviceName(deviceId) } returns "device"
        every { repository.isFeatureReady(any(), any()) } returns true
        every { collector.finalizeAllStreams() } returns ArrayList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(state: SavedStateHandle = newState()) =
        OnlineRecordingViewModel(repository, collector, state)

    private fun newState() = SavedStateHandle(mapOf(ONLINE_OFFLINE_KEY_DEVICE_ID to deviceId))

    private fun uri(name: String): Uri = Uri.parse("content://files/sensorDataLogs/$name")

    @Test
    fun markerUriFor_returnsLinkedMarker_whenLinked() {
        // Arrange
        val viewModel = newViewModel()
        val marker = uri("MARKER/dev1/m.txt")
        val hr = uri("HR/dev1/hr.txt")
        val acc = uri("ACC/dev1/acc.txt")

        // Act
        viewModel.linkMarkerToDataFiles(marker, listOf(hr, acc))

        // Assert
        assertEquals(marker, viewModel.markerUriFor(hr))
        assertEquals(marker, viewModel.markerUriFor(acc))
        assertNull(viewModel.markerUriFor(uri("PPG/dev1/ppg.txt")))
    }

    @Test
    fun linkMarkerToDataFiles_keepsExistingLinks_whenDataUriListIsEmpty() {
        // Arrange
        val viewModel = newViewModel()
        val hr = uri("HR/dev1/hr.txt")
        viewModel.linkMarkerToDataFiles(uri("MARKER/dev1/m1.txt"), listOf(hr))

        // Act
        viewModel.linkMarkerToDataFiles(uri("MARKER/dev1/m2.txt"), emptyList())

        // Assert
        assertEquals(uri("MARKER/dev1/m1.txt"), viewModel.markerUriFor(hr))
    }

    @Test
    fun init_restoresMarkerLinks_whenSavedStateHasEntries() {
        // Arrange
        val state = newState()
        val marker = uri("MARKER/dev1/m|with|bars.txt")
        val hr = uri("HR/dev1/hr|1.txt")
        newViewModel(state).linkMarkerToDataFiles(marker, listOf(hr))

        // Act
        val restored = newViewModel(state)

        // Assert
        assertEquals(marker, restored.markerUriFor(hr))
    }

    @Test
    fun clearValidatedFileUris_removesMarkerLinks_forClearedDataUris() {
        // Arrange
        val viewModel = newViewModel()
        val marker = uri("MARKER/dev1/m.txt")
        val hr = uri("HR/dev1/hr.txt")
        val acc = uri("ACC/dev1/acc.txt")
        viewModel.addValidatedFileUri(hr)
        viewModel.addValidatedFileUri(acc)
        viewModel.linkMarkerToDataFiles(marker, listOf(hr, acc))

        // Act
        viewModel.clearValidatedFileUris(listOf(hr))

        // Assert
        assertNull(viewModel.markerUriFor(hr))
        assertEquals(marker, viewModel.markerUriFor(acc))
        assertEquals(listOf(acc), viewModel.validatedFileUris.value)
    }

    @Test
    fun handleStreamError_restartsAllActiveStreams_whenDeviceReconnects() {
        // Arrange
        val hrFails = CompletableDeferred<Unit>()
        val streams = StartCounters()
        fakeHrStream(streams) { hrFails.await(); throw PolarDeviceDisconnected() }
        fakePpiStream(streams) { awaitCancellation() }
        val viewModel = newViewModel()
        viewModel.startStream(PolarDeviceDataType.HR)
        viewModel.startStream(PolarDeviceDataType.PPI)
        awaitLatch(streams.hrStarted, 1)
        awaitLatch(streams.ppiStarted, 1)

        // Act
        hrFails.complete(Unit)
        awaitStopped(viewModel, PolarDeviceDataType.HR, PolarDeviceDataType.PPI)
        connectionStatus.value = DeviceConnectionState.DeviceConnected(deviceId)

        // Assert
        awaitLatch(streams.hrStarted, 2)
        awaitLatch(streams.ppiStarted, 2)
        assertEquals(2, streams.hrCalls.get())
        assertEquals(2, streams.ppiCalls.get())
    }

    @Test
    fun handleStreamError_keepsFilesOpenAndRestartsAll_whenTwoStreamsFailTogether() {
        // Arrange
        val gate = CompletableDeferred<Unit>()
        val streams = StartCounters()
        fakeHrStream(streams) { gate.await(); throw PolarDeviceDisconnected() }
        fakePpiStream(streams) { gate.await(); throw PolarDeviceDisconnected() }
        val viewModel = newViewModel()
        viewModel.startStream(PolarDeviceDataType.HR)
        viewModel.startStream(PolarDeviceDataType.PPI)
        awaitLatch(streams.hrStarted, 1)
        awaitLatch(streams.ppiStarted, 1)

        // Act
        gate.complete(Unit)
        awaitStopped(viewModel, PolarDeviceDataType.HR, PolarDeviceDataType.PPI)
        connectionStatus.value = DeviceConnectionState.DeviceConnected(deviceId)
        awaitLatch(streams.hrStarted, 2)
        awaitLatch(streams.ppiStarted, 2)

        // Assert
        verify(exactly = 0) { collector.finalizeAllStreams() }
        verify(exactly = 2) { repository.startHrStreaming(deviceId) }
        verify(exactly = 2) { repository.startPpiStream(deviceId) }
        // The restart reuses the session start time, so the streams append to the same files.
        verify(exactly = 2) { collector.startHrLog(any(), any()) }
    }

    @Test
    fun handleStreamError_finalizesFiles_whenErrorIsNotDisconnect() {
        // Arrange
        val fails = CompletableDeferred<Unit>()
        val streams = StartCounters()
        fakeHrStream(streams) { fails.await(); throw IllegalStateException("control point error") }
        val viewModel = newViewModel()
        viewModel.startStream(PolarDeviceDataType.HR)
        awaitLatch(streams.hrStarted, 1)
        val finalized = CountDownLatch(1)
        every { collector.finalizeAllStreams() } answers { finalized.countDown(); ArrayList() }

        // Act
        fails.complete(Unit)

        // Assert
        awaitFinalized(finalized)
    }

    @Test
    fun startStream_finalizesOldFilesBeforeNewStart_whenRestartWasPending() {
        // Arrange
        val hrFails = CompletableDeferred<Unit>()
        val streams = StartCounters()
        fakeHrStream(streams) { hrFails.await(); throw PolarDeviceDisconnected() }
        fakePpiStream(streams) { awaitCancellation() }
        val viewModel = newViewModel()
        viewModel.startStream(PolarDeviceDataType.HR)
        awaitLatch(streams.hrStarted, 1)
        hrFails.complete(Unit)
        awaitStopped(viewModel, PolarDeviceDataType.HR)

        // Act
        viewModel.startStream(PolarDeviceDataType.PPI)
        awaitLatch(streams.ppiStarted, 1)

        // Assert
        io.mockk.verifyOrder {
            collector.finalizeAllStreams()
            collector.startPpiLog(any(), any())
        }
    }

    @Test
    fun handleStreamError_closesOldFilesAndStartsReadyStreams_whenOnlyOneStreamIsReady() {
        // Arrange
        val gate = CompletableDeferred<Unit>()
        val streams = StartCounters()
        fakeHrStream(streams) { gate.await(); throw PolarDeviceDisconnected() }
        fakePpiStream(streams) { gate.await(); throw PolarDeviceDisconnected() }
        every { repository.isFeatureReady(any(), PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING) } returns false
        every { repository.lastDisconnectInfo } returns MutableStateFlow(null)
        val viewModel = newViewModel()
        viewModel.startStream(PolarDeviceDataType.HR)
        viewModel.startStream(PolarDeviceDataType.PPI)
        awaitLatch(streams.hrStarted, 1)
        awaitLatch(streams.ppiStarted, 1)
        gate.complete(Unit)
        awaitStopped(viewModel, PolarDeviceDataType.HR, PolarDeviceDataType.PPI)

        // Act
        connectionStatus.value = DeviceConnectionState.DeviceConnected(deviceId)
        mainDispatcher.scheduler.advanceUntilIdle()
        awaitLatch(streams.hrStarted, 2)

        // Assert
        io.mockk.verifyOrder {
            collector.finalizeAllStreams()
            collector.startHrLog(any(), any())
        }
        assertEquals(1, streams.ppiCalls.get())
    }

    @Test
    fun startStream_discardsPendingRestarts_whenUserStartsOtherStream() {
        // Arrange
        val streams = StartCounters()
        val hrFails = CompletableDeferred<Unit>()
        fakeHrStream(streams) { hrFails.await(); throw PolarDeviceDisconnected() }
        fakePpiStream(streams) { awaitCancellation() }
        val viewModel = newViewModel()
        viewModel.startStream(PolarDeviceDataType.HR)
        awaitLatch(streams.hrStarted, 1)
        hrFails.complete(Unit)
        awaitStopped(viewModel, PolarDeviceDataType.HR)

        // Act
        viewModel.startStream(PolarDeviceDataType.PPI)
        awaitLatch(streams.ppiStarted, 1)
        connectionStatus.value = DeviceConnectionState.DeviceConnected(deviceId)

        // Assert
        // The restart path checks feature readiness at once on the main dispatcher.
        verify(exactly = 0) { repository.isFeatureReady(any(), any()) }
        assertEquals(1, streams.hrCalls.get())
    }

    private class StartCounters {
        val hrCalls = AtomicInteger()
        val ppiCalls = AtomicInteger()
        val hrStarted = CountDownLatchHolder()
        val ppiStarted = CountDownLatchHolder()
    }

    private fun fakeHrStream(streams: StartCounters, body: suspend () -> Unit) {
        every { repository.startHrStreaming(deviceId) } answers {
            streams.hrStarted.signal(streams.hrCalls.incrementAndGet())
            hrOrPpiFlow(body)
        }
    }

    private fun fakePpiStream(streams: StartCounters, body: suspend () -> Unit) {
        every { repository.startPpiStream(deviceId) } answers {
            streams.ppiStarted.signal(streams.ppiCalls.incrementAndGet())
            hrOrPpiFlow(body)
        }
    }

    private fun <T> hrOrPpiFlow(body: suspend () -> Unit): Flow<T> = flow { body() }

    private fun awaitLatch(holder: CountDownLatchHolder, count: Int) {
        check(holder.await(count)) { "Timeout while waiting for call number $count" }
    }

    private fun awaitStopped(viewModel: OnlineRecordingViewModel, vararg features: PolarDeviceDataType) = runBlocking {
        withTimeout(5_000) {
            viewModel.uiStreamingState.first { ui ->
                features.all { ui.streamingRecordingState[it]?.state == StreamingFeatureState.STATES.STOPPED }
            }
        }
    }

    private fun awaitFinalized(latch: CountDownLatch) {
        check(latch.await(5, TimeUnit.SECONDS)) { "Timeout while waiting for finalizeAllStreams" }
    }
}

/** Counts calls and lets a test wait until a call number is reached. */
class CountDownLatchHolder {
    private val reached = java.util.concurrent.ConcurrentHashMap<Int, CountDownLatch>()

    private fun latchFor(count: Int) = reached.getOrPut(count) { CountDownLatch(1) }

    fun signal(count: Int) = latchFor(count).countDown()

    fun await(count: Int): Boolean = latchFor(count).await(5, TimeUnit.SECONDS)
}
