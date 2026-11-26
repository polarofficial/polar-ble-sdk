package com.polar.polarsensordatacollector.ui.landing

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.polar.androidcommunications.api.ble.model.gatt.client.ChargeState
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.errors.BleOnlineStreamClosed
import com.polar.polarsensordatacollector.DataCollector
import com.polar.polarsensordatacollector.repository.AvailableFeatures
import com.polar.polarsensordatacollector.repository.DeviceConnectionState
import kotlinx.coroutines.CompletableDeferred
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.ui.utils.MessageUiState
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.model.EcgSample
import com.polar.sdk.api.model.FecgSample
import com.polar.sdk.api.model.GpsCoordinatesSample
import com.polar.sdk.api.model.GpsNMEASample
import com.polar.sdk.api.model.PolarAccelerometerData
import com.polar.sdk.api.model.PolarEcgData
import com.polar.sdk.api.model.PolarGyroData
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarLocationData
import com.polar.sdk.api.model.PolarMagnetometerData
import com.polar.sdk.api.model.PolarPpgData
import com.polar.sdk.api.model.PolarPpiData
import com.polar.sdk.api.model.PolarPressureData
import com.polar.sdk.api.model.PolarSensorSetting
import com.polar.sdk.api.model.PolarSensorSetting.SettingType
import com.polar.sdk.api.model.PolarTemperatureData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.EnumMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OnlineRecordingViewModelStreamsTest {

    private val deviceId = "dev1"
    private val settings = mapOf(SettingType.SAMPLE_RATE to 52)
    private val connectionStatus = MutableStateFlow<DeviceConnectionState>(DeviceConnectionState.DeviceNotConnected())
    private val availableFeatures = MutableStateFlow(AvailableFeatures())
    private val chargingStates = MutableStateFlow<Map<String, ChargeState>>(emptyMap())
    private val repository = mockk<PolarDeviceRepository>(relaxed = true)
    private val collector = mockk<DataCollector>(relaxed = true)
    private val created = mutableListOf<OnlineRecordingViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repository.availableFeatures } returns availableFeatures
        every { repository.deviceChargingStates } returns chargingStates
        every { repository.deviceConnectionStatus } returns connectionStatus
        every { repository.getDeviceName(deviceId) } returns "device"
        every { collector.finalizeAllStreams() } returns ArrayList()
    }

    @After
    fun tearDown() {
        created.forEach {
            OnlineRecordingViewModel::class.java.getDeclaredMethod("onCleared").apply { isAccessible = true }.invoke(it)
        }
        Dispatchers.resetMain()
    }

    private fun newViewModel(): OnlineRecordingViewModel =
        OnlineRecordingViewModel(
            repository,
            collector,
            SavedStateHandle(mapOf(ONLINE_OFFLINE_KEY_DEVICE_ID to deviceId))
        ).also { created += it }

    private fun <T> awaitState(flow: StateFlow<T>, predicate: (T) -> Boolean): T =
        runBlocking { withTimeout(5_000) { flow.first(predicate) } }

    private fun expectError(viewModel: OnlineRecordingViewModel, action: () -> Unit): MessageUiState = runBlocking {
        val error = async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiShowError.first() }
        action()
        withTimeout(5_000) { error.await() }
    }

    private fun startWithPresetSettings(viewModel: OnlineRecordingViewModel, feature: PolarDeviceDataType) {
        viewModel.updateSelectedStreamSettings(feature, settings)
        viewModel.startStream(feature)
    }

    private fun uri(path: String): Uri = Uri.parse("content://files/sensorDataLogs/$path")

    @Test
    fun `charging warning is shown once for the active HR device`() = runBlocking {
        every { repository.startHrStreaming(deviceId) } returns flow { awaitCancellation() }
        val viewModel = newViewModel()
        chargingStates.value = mapOf("other" to ChargeState.CHARGING)
        assertEquals(false, viewModel.uiHrChargingWarning.value)

        viewModel.startStream(PolarDeviceDataType.HR)
        awaitState(viewModel.uiStreamingState) { it.streamingRecordingState[PolarDeviceDataType.HR]?.state == StreamingFeatureState.STATES.RECORDING }
        assertEquals(false, viewModel.uiHrChargingWarning.value)
        chargingStates.value = mapOf("other" to ChargeState.CHARGING, deviceId to ChargeState.CHARGING)
        assertEquals(true, withTimeout(5_000) { viewModel.uiHrChargingWarning.first { it } })
        assertEquals(StreamingFeatureState.STATES.RECORDING, viewModel.uiStreamingState.value.streamingRecordingState[PolarDeviceDataType.HR]?.state)

        viewModel.acknowledgeHrChargingWarning()
        assertEquals(false, viewModel.uiHrChargingWarning.value)
        chargingStates.value = mapOf(deviceId to ChargeState.CHARGING)
        assertEquals(false, viewModel.uiHrChargingWarning.value)
        chargingStates.value = mapOf(deviceId to ChargeState.DISCHARGING_ACTIVE)
        chargingStates.value = mapOf(deviceId to ChargeState.CHARGING)
        withTimeout(5_000) { viewModel.uiHrChargingWarning.first { it } }
        viewModel.pauseStream(PolarDeviceDataType.HR)
        withTimeout(5_000) { viewModel.uiHrChargingWarning.first { !it } }
        Unit
    }

    @Test
    fun `starting HR while already charging shows warning`() = runBlocking {
        chargingStates.value = mapOf(deviceId to ChargeState.CHARGING)
        every { repository.startHrStreaming(deviceId) } returns flow { awaitCancellation() }
        val viewModel = newViewModel()
        assertEquals(false, viewModel.uiHrChargingWarning.value)
        viewModel.startStream(PolarDeviceDataType.HR)
        assertEquals(true, withTimeout(5_000) { viewModel.uiHrChargingWarning.first { it } })
    }

    @Test
    fun `charging warning does not stop HR data`() = runBlocking {
        val nextData = CompletableDeferred<PolarHrData>()
        val data = mockk<PolarHrData>()
        val sample = mockk<PolarHrData.PolarHrSample>(relaxed = true)
        every { data.samples } returns listOf(sample)
        every { repository.startHrStreaming(deviceId) } returns flow {
            emit(nextData.await())
            awaitCancellation()
        }
        val viewModel = newViewModel()
        viewModel.startStream(PolarDeviceDataType.HR)
        awaitState(viewModel.uiStreamingState) { it.streamingRecordingState[PolarDeviceDataType.HR]?.state == StreamingFeatureState.STATES.RECORDING }

        chargingStates.value = mapOf(deviceId to ChargeState.CHARGING)
        awaitState(viewModel.uiHrChargingWarning) { it }
        nextData.complete(data)
        assertSame(sample, awaitState(viewModel.uiHeartRateInfoState) { it.heartRate != null }.heartRate)
        assertEquals(StreamingFeatureState.STATES.RECORDING, viewModel.uiStreamingState.value.streamingRecordingState[PolarDeviceDataType.HR]?.state)
        coVerify(exactly = 0) { repository.stopHrStreaming(deviceId) }
    }

    @Test
    fun `charging warning clears on unplug disconnect pause and stop and rearms`() {
        every { repository.startHrStreaming(deviceId) } returns flow { awaitCancellation() }
        val viewModel = newViewModel()
        viewModel.startStream(PolarDeviceDataType.HR)
        awaitState(viewModel.uiStreamingState) { it.streamingRecordingState[PolarDeviceDataType.HR]?.state == StreamingFeatureState.STATES.RECORDING }

        chargingStates.value = mapOf(deviceId to ChargeState.CHARGING)
        awaitState(viewModel.uiHrChargingWarning) { it }
        chargingStates.value = mapOf(deviceId to ChargeState.DISCHARGING_ACTIVE)
        awaitState(viewModel.uiHrChargingWarning) { !it }
        chargingStates.value = mapOf(deviceId to ChargeState.CHARGING)
        awaitState(viewModel.uiHrChargingWarning) { it }
        chargingStates.value = emptyMap() // repository removes the device on disconnect
        awaitState(viewModel.uiHrChargingWarning) { !it }

        chargingStates.value = mapOf(deviceId to ChargeState.CHARGING)
        awaitState(viewModel.uiHrChargingWarning) { it }
        viewModel.pauseStream(PolarDeviceDataType.HR)
        assertEquals(false, viewModel.uiHrChargingWarning.value)
        viewModel.startStream(PolarDeviceDataType.HR)
        awaitState(viewModel.uiHrChargingWarning) { it }
        viewModel.stopStream(PolarDeviceDataType.HR)
        assertEquals(false, viewModel.uiHrChargingWarning.value)
        assertEquals(StreamingFeatureState.STATES.STOPPED, viewModel.uiStreamingState.value.streamingRecordingState[PolarDeviceDataType.HR]?.state)
    }

    @Test
    fun `charger connected while HR is paused warns only after resuming`() {
        every { repository.startHrStreaming(deviceId) } returns flow { awaitCancellation() }
        val viewModel = newViewModel()
        viewModel.startStream(PolarDeviceDataType.HR)
        awaitState(viewModel.uiStreamingState) { it.streamingRecordingState[PolarDeviceDataType.HR]?.state == StreamingFeatureState.STATES.RECORDING }
        viewModel.pauseStream(PolarDeviceDataType.HR)

        chargingStates.value = mapOf(deviceId to ChargeState.CHARGING)
        assertEquals(false, viewModel.uiHrChargingWarning.value)
        viewModel.startStream(PolarDeviceDataType.HR)
        awaitState(viewModel.uiHrChargingWarning) { it }
    }

    @Test
    fun `charger connected during ECG alone does not show HR warning`() {
        every { repository.startEcgStream(deviceId, any()) } returns flow { awaitCancellation() }
        val viewModel = newViewModel()
        startWithPresetSettings(viewModel, PolarDeviceDataType.ECG)
        awaitState(viewModel.uiStreamingState) { it.streamingRecordingState[PolarDeviceDataType.ECG]?.state == StreamingFeatureState.STATES.RECORDING }

        chargingStates.value = mapOf(deviceId to ChargeState.CHARGING)
        assertEquals(false, viewModel.uiHrChargingWarning.value)
        assertEquals(StreamingFeatureState.STATES.RECORDING, viewModel.uiStreamingState.value.streamingRecordingState[PolarDeviceDataType.ECG]?.state)
    }

    @Test
    fun `ACC device stop clears pending HR warning and emits confirmed stop`() = runBlocking {
        val stop = CompletableDeferred<Unit>()
        val finalized = CountDownLatch(1)
        every { collector.finalizeAllStreams() } answers { finalized.countDown(); ArrayList() }
        every { repository.startHrStreaming(deviceId) } returns flow { awaitCancellation() }
        every { repository.startAccStreaming(deviceId, any()) } returns flow {
            stop.await()
            throw BleOnlineStreamClosed("Stop command from device")
        }
        val viewModel = newViewModel()
        viewModel.startStream(PolarDeviceDataType.HR)
        startWithPresetSettings(viewModel, PolarDeviceDataType.ACC)
        awaitState(viewModel.uiStreamingState) {
            it.streamingRecordingState[PolarDeviceDataType.HR]?.state == StreamingFeatureState.STATES.RECORDING &&
                it.streamingRecordingState[PolarDeviceDataType.ACC]?.state == StreamingFeatureState.STATES.RECORDING
        }
        chargingStates.value = mapOf(deviceId to ChargeState.CHARGING)
        awaitState(viewModel.uiHrChargingWarning) { it }

        val stopped = async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiDeviceStoppedStream.first() }
        stop.complete(Unit)
        assertEquals(PolarDeviceDataType.ACC, withTimeout(5_000) { stopped.await() })
        assertEquals(false, viewModel.uiHrChargingWarning.value)
        awaitState(viewModel.uiStreamingState) {
            it.streamingRecordingState[PolarDeviceDataType.HR]?.state == StreamingFeatureState.STATES.STOPPED &&
                it.streamingRecordingState[PolarDeviceDataType.ACC]?.state == StreamingFeatureState.STATES.STOPPED
        }
        assertTrue(finalized.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `ACC device stop is shown instead of charger warning`() = runBlocking {
        val stop = CompletableDeferred<Unit>()
        every { repository.startAccStreaming(deviceId, any()) } returns flow {
            stop.await()
            throw BleOnlineStreamClosed("Stop command from device")
        }
        val viewModel = newViewModel()
        startWithPresetSettings(viewModel, PolarDeviceDataType.ACC)
        awaitState(viewModel.uiStreamingState) { it.streamingRecordingState[PolarDeviceDataType.ACC]?.state == StreamingFeatureState.STATES.RECORDING }
        chargingStates.value = mapOf(deviceId to ChargeState.CHARGING)
        assertEquals(false, viewModel.uiHrChargingWarning.value)
        val stopped = async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiDeviceStoppedStream.first() }
        stop.complete(Unit)
        assertEquals(PolarDeviceDataType.ACC, withTimeout(5_000) { stopped.await() })
        assertEquals(false, viewModel.uiHrChargingWarning.value)
    }

    @Test
    fun `start stream publishes ecg data when ecg streams`() {
        // Arrange
        val data = mockk<PolarEcgData>()
        every { data.samples } returns listOf(mockk<EcgSample>(relaxed = true), mockk<FecgSample>(relaxed = true))
        every { repository.startEcgStream(deviceId, any()) } returns flowOf(data)
        val viewModel = newViewModel()

        // Act
        startWithPresetSettings(viewModel, PolarDeviceDataType.ECG)

        // Assert
        assertSame(data, awaitState(viewModel.uiEcgStreamDataState) { it.sampleData != null }.sampleData)
        verify { collector.logEcg(any(), any()) }
        verify { collector.logFecg(any(), any(), any(), any()) }
    }

    @Test
    fun `start stream publishes acc data when acc streams`() {
        // Arrange
        val data = mockk<PolarAccelerometerData>()
        every { data.samples } returns listOf(mockk(relaxed = true), mockk(relaxed = true))
        every { repository.startAccStreaming(deviceId, any()) } returns flowOf(data)
        val viewModel = newViewModel()

        // Act
        startWithPresetSettings(viewModel, PolarDeviceDataType.ACC)

        // Assert
        assertSame(data, awaitState(viewModel.uiAccStreamDataState) { it.sampleData != null }.sampleData)
        verify(exactly = 2) { collector.logAcc(any(), any(), any(), any()) }
    }

    @Test
    fun `start stream publishes gyro data when gyro streams`() {
        // Arrange
        val data = mockk<PolarGyroData>()
        every { data.samples } returns listOf(mockk(relaxed = true), mockk(relaxed = true))
        every { repository.startGyroStreaming(deviceId, any()) } returns flowOf(data)
        val viewModel = newViewModel()

        // Act
        startWithPresetSettings(viewModel, PolarDeviceDataType.GYRO)

        // Assert
        assertSame(data, awaitState(viewModel.uiGyroStreamDataState) { it.sampleData != null }.sampleData)
    }

    @Test
    fun `start stream publishes magnetometer data when magnetometer streams`() {
        // Arrange
        val data = mockk<PolarMagnetometerData>()
        every { data.samples } returns listOf(mockk(relaxed = true), mockk(relaxed = true))
        every { repository.startMagnetometerStream(deviceId, any()) } returns flowOf(data)
        val viewModel = newViewModel()

        // Act
        startWithPresetSettings(viewModel, PolarDeviceDataType.MAGNETOMETER)

        // Assert
        assertSame(data, awaitState(viewModel.uiMagnStreamDataState) { it.sampleData != null }.sampleData)
    }

    @Test
    fun `start stream publishes pressure data when pressure streams`() {
        // Arrange
        val data = mockk<PolarPressureData>()
        every { data.samples } returns listOf(mockk(relaxed = true), mockk(relaxed = true))
        every { repository.startPressureStream(deviceId, any()) } returns flowOf(data)
        val viewModel = newViewModel()

        // Act
        startWithPresetSettings(viewModel, PolarDeviceDataType.PRESSURE)

        // Assert
        assertSame(data, awaitState(viewModel.uiPressureStreamDataState) { it.sampleData != null }.sampleData)
    }

    @Test
    fun `start stream publishes temperature data when temperature streams`() {
        // Arrange
        val data = mockk<PolarTemperatureData>()
        every { data.samples } returns listOf(mockk(relaxed = true), mockk(relaxed = true))
        every { repository.startTemperatureStreaming(deviceId, any()) } returns flowOf(data)
        val viewModel = newViewModel()

        // Act
        startWithPresetSettings(viewModel, PolarDeviceDataType.TEMPERATURE)

        // Assert
        assertSame(data, awaitState(viewModel.uiTemperatureStreamDataState) { it.sampleData != null }.sampleData)
    }

    @Test
    fun `start stream publishes skin temperature data when skin temperature streams`() {
        // Arrange
        val data = mockk<PolarTemperatureData>()
        every { data.samples } returns listOf(mockk(relaxed = true), mockk(relaxed = true))
        every { repository.startSkinTemperatureStreaming(deviceId, any()) } returns flowOf(data)
        val viewModel = newViewModel()

        // Act
        startWithPresetSettings(viewModel, PolarDeviceDataType.SKIN_TEMPERATURE)

        // Assert
        assertSame(data, awaitState(viewModel.uiSkinTemperatureStreamDataState) { it.sampleData != null }.sampleData)
    }

    @Test
    fun `start stream publishes ppg data when ppg frame type is displayed`() {
        // Arrange
        val data = mockk<PolarPpgData>()
        every { data.samples } returns listOf(mockk(relaxed = true), mockk(relaxed = true))
        every { data.type } returns PolarPpgData.PpgDataType.FRAME_TYPE_7
        every { repository.startPpgStream(deviceId, any()) } returns flowOf(data)
        val viewModel = newViewModel()

        // Act
        startWithPresetSettings(viewModel, PolarDeviceDataType.PPG)

        // Assert
        assertSame(data, awaitState(viewModel.uiPpgStreamDataState) { it.sampleData != null }.sampleData)
    }

    @Test
    fun `start stream only logs ppg data when ppg frame type is not displayed`() {
        // Arrange
        val data = mockk<PolarPpgData>()
        every { data.samples } returns listOf(mockk(relaxed = true))
        every { data.type } returns PolarPpgData.PpgDataType.SPORT_ID
        every { repository.startPpgStream(deviceId, any()) } returns flowOf(data)
        val viewModel = newViewModel()

        // Act
        startWithPresetSettings(viewModel, PolarDeviceDataType.PPG)

        // Assert
        verify(timeout = 5_000) { collector.logPpgData(data) }
        assertEquals(null, viewModel.uiPpgStreamDataState.value.sampleData)
    }

    @Test
    fun `start stream publishes location data when samples are coordinates`() {
        // Arrange
        val data = mockk<PolarLocationData>()
        every { data.samples } returns listOf(mockk<GpsCoordinatesSample>(relaxed = true), mockk<GpsCoordinatesSample>(relaxed = true))
        every { repository.startLocationStream(deviceId, any()) } returns flowOf(data)
        val viewModel = newViewModel()

        // Act
        startWithPresetSettings(viewModel, PolarDeviceDataType.LOCATION)

        // Assert
        assertSame(data, awaitState(viewModel.uiLocationStreamDataState) { it.sampleData != null }.sampleData)
    }

    @Test
    fun `start stream only logs location data when samples are not coordinates`() {
        // Arrange
        val data = mockk<PolarLocationData>()
        every { data.samples } returns listOf(mockk<GpsNMEASample>(relaxed = true))
        every { repository.startLocationStream(deviceId, any()) } returns flowOf(data)
        val viewModel = newViewModel()

        // Act
        startWithPresetSettings(viewModel, PolarDeviceDataType.LOCATION)

        // Assert
        verify(timeout = 5_000) { collector.logLocationNmeaSummary(any(), any()) }
        assertEquals(null, viewModel.uiLocationStreamDataState.value.sampleData)
    }

    @Test
    fun `start stream publishes ppi data when ppi streams`() {
        // Arrange
        val data = mockk<PolarPpiData>()
        val sample = mockk<PolarPpiData.PolarPpiSample>(relaxed = true)
        every { data.samples } returns listOf(sample)
        every { repository.startPpiStream(deviceId) } returns flowOf(data)
        val viewModel = newViewModel()

        // Act
        viewModel.startStream(PolarDeviceDataType.PPI)

        // Assert
        assertSame(sample, awaitState(viewModel.uiPpiStreamDataState) { it.sampleData != null }.sampleData)
    }

    @Test
    fun `start stream publishes heart rate when hr streams`() {
        // Arrange
        val data = mockk<PolarHrData>()
        val sample = mockk<PolarHrData.PolarHrSample>(relaxed = true)
        every { data.samples } returns listOf(sample)
        every { repository.startHrStreaming(deviceId) } returns flowOf(data)
        val viewModel = newViewModel()

        // Act
        viewModel.startStream(PolarDeviceDataType.HR)

        // Assert
        assertSame(sample, awaitState(viewModel.uiHeartRateInfoState) { it.heartRate != null }.heartRate)
    }

    @Test
    fun `start stream does nothing when feature is derived measurement`() {
        // Arrange
        val viewModel = newViewModel()

        // Act
        viewModel.startStream(PolarDeviceDataType.DERIVED_MEASUREMENT)

        // Assert
        verify(exactly = 0) { repository.startAccStreaming(any(), any()) }
    }

    @Test
    fun `start stream uses max available settings when no settings are selected`() {
        // Arrange
        val available = mockk<PolarSensorSetting>()
        every { available.settings } returns mutableMapOf(SettingType.SAMPLE_RATE to setOf(26, 52, 13))
        coEvery { repository.getAvailableStreamSettings(deviceId, PolarDeviceDataType.ACC) } returns available
        every { repository.startAccStreaming(deviceId, any()) } returns flow { awaitCancellation() }
        val viewModel = newViewModel()

        // Act
        viewModel.startStream(PolarDeviceDataType.ACC)

        // Assert
        val accState = awaitState(viewModel.uiStreamingState) {
            it.streamingRecordingState[PolarDeviceDataType.ACC]?.state == StreamingFeatureState.STATES.RECORDING
        }.streamingRecordingState[PolarDeviceDataType.ACC]
        assertEquals(mapOf(SettingType.SAMPLE_RATE to 52), accState?.settings)
    }

    @Test
    fun `start stream shows error when settings cannot be read`() {
        // Arrange
        coEvery { repository.getAvailableStreamSettings(deviceId, PolarDeviceDataType.ACC) } throws IllegalStateException("no settings")
        val viewModel = newViewModel()

        // Act
        val error = expectError(viewModel) { viewModel.startStream(PolarDeviceDataType.ACC) }

        // Assert
        assertEquals("Failed to get ACC settings", error.header)
    }

    @Test
    fun `start stream shows error when device name is unknown`() {
        // Arrange
        every { repository.getDeviceName(deviceId) } returns null
        every { repository.startHrStreaming(deviceId) } returns flow { awaitCancellation() }
        val viewModel = newViewModel()

        // Act
        val error = expectError(viewModel) { viewModel.startStream(PolarDeviceDataType.HR) }

        // Assert
        assertEquals("Failed to start HR stream. Device name is not known", error.header)
    }

    @Test
    fun `pause stream keeps settings when stream is running`() {
        // Arrange
        every { repository.startAccStreaming(deviceId, any()) } returns flow { awaitCancellation() }
        val viewModel = newViewModel()
        startWithPresetSettings(viewModel, PolarDeviceDataType.ACC)
        awaitState(viewModel.uiStreamingState) {
            it.streamingRecordingState[PolarDeviceDataType.ACC]?.state == StreamingFeatureState.STATES.RECORDING
        }

        // Act
        viewModel.pauseStream(PolarDeviceDataType.ACC)

        // Assert
        val accState = viewModel.uiStreamingState.value.streamingRecordingState[PolarDeviceDataType.ACC]
        assertEquals(StreamingFeatureState.STATES.PAUSED, accState?.state)
        assertEquals(settings, accState?.settings)
    }

    @Test
    fun `pause stream keeps state stopped when stream has no job`() {
        // Arrange
        val viewModel = newViewModel()

        // Act
        viewModel.pauseStream(PolarDeviceDataType.ACC)

        // Assert
        assertEquals(
            StreamingFeatureState.STATES.STOPPED,
            viewModel.uiStreamingState.value.streamingRecordingState[PolarDeviceDataType.ACC]?.state
        )
    }

    @Test
    fun `stop stream stops every stream type when called for all features`() {
        // Arrange
        val viewModel = newViewModel()
        PolarDeviceDataType.entries.forEach { viewModel.updateSelectedStreamSettings(it, settings) }

        // Act
        PolarDeviceDataType.entries.forEach { viewModel.stopStream(it) }

        // Assert
        verify(exactly = 9) { repository.stopStreaming(deviceId, any()) }
        coVerify(exactly = 2) { repository.stopHrStreaming(deviceId) }
        assertEquals(
            StreamingFeatureState.STATES.STOPPED,
            viewModel.uiStreamingState.value.streamingRecordingState[PolarDeviceDataType.ECG]?.state
        )
    }

    @Test
    fun `add marker to log writes marker when called`() {
        // Arrange
        val written = CountDownLatch(1)
        every { collector.marker(deviceId, true, any()) } answers { written.countDown() }
        val viewModel = newViewModel()

        // Act
        viewModel.addMarkerToLog(true)

        // Assert
        assertTrue(written.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `add marker to log does not throw when collector fails`() {
        // Arrange
        val attempted = CountDownLatch(1)
        every { collector.marker(deviceId, false, any()) } answers { attempted.countDown(); throw IllegalStateException("disk full") }
        val viewModel = newViewModel()

        // Act
        viewModel.addMarkerToLog(false)

        // Assert
        assertTrue(attempted.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `request stream settings publishes settings when settings are available`() {
        // Arrange
        val available = mockk<PolarSensorSetting>()
        val all = mockk<PolarSensorSetting>(relaxed = true)
        every { available.settings } returns mutableMapOf(SettingType.SAMPLE_RATE to setOf(26, 52))
        coEvery { repository.getAvailableStreamSettings(deviceId, PolarDeviceDataType.ACC) } returns available
        coEvery { repository.requestFullStreamSettings(deviceId, PolarDeviceDataType.ACC) } returns all
        val viewModel = newViewModel()

        // Act
        viewModel.requestStreamSettings(deviceId, PolarDeviceDataType.ACC)

        // Assert
        val requested = awaitState(viewModel.uiOnlineRequestedSettingsState) { it != null }
        assertEquals(PolarDeviceDataType.ACC, requested?.feature)
        assertSame(available, requested?.settings?.currentlyAvailable)
        viewModel.clearOnlineRequestedSettings()
        assertEquals(null, viewModel.uiOnlineRequestedSettingsState.value)
    }

    @Test
    fun `request stream settings uses empty all settings when full settings fail`() {
        // Arrange
        val available = mockk<PolarSensorSetting>()
        every { available.settings } returns mutableMapOf(SettingType.SAMPLE_RATE to setOf(26))
        coEvery { repository.getAvailableStreamSettings(deviceId, PolarDeviceDataType.ACC) } returns available
        coEvery { repository.requestFullStreamSettings(deviceId, PolarDeviceDataType.ACC) } throws IllegalStateException("no full settings")
        val viewModel = newViewModel()

        // Act
        viewModel.requestStreamSettings(deviceId, PolarDeviceDataType.ACC)

        // Assert
        val requested = awaitState(viewModel.uiOnlineRequestedSettingsState) { it != null }
        assertTrue(requested?.settings?.allPossibleSettings?.settings?.isEmpty() == true)
    }

    @Test
    fun `request stream settings shows error when no settings are available`() {
        // Arrange
        val available = mockk<PolarSensorSetting>()
        every { available.settings } returns mutableMapOf()
        coEvery { repository.getAvailableStreamSettings(deviceId, PolarDeviceDataType.ACC) } returns available
        val viewModel = newViewModel()

        // Act
        val error = expectError(viewModel) { viewModel.requestStreamSettings(deviceId, PolarDeviceDataType.ACC) }

        // Assert
        assertEquals("Settings are not available for feature ACC", error.header)
    }

    @Test
    fun `request stream settings shows error when settings fetch fails`() {
        // Arrange
        coEvery { repository.getAvailableStreamSettings(deviceId, PolarDeviceDataType.ACC) } throws IllegalStateException("boom")
        val viewModel = newViewModel()

        // Act
        val error = expectError(viewModel) { viewModel.requestStreamSettings(deviceId, PolarDeviceDataType.ACC) }

        // Assert
        assertEquals("Settings fetch error for feature ACC", error.header)
    }

    @Test
    fun `init publishes available features when device reports features`() {
        // Arrange
        val viewModel = newViewModel()
        val streaming = EnumMap(PolarDeviceDataType.entries.associateWith { it == PolarDeviceDataType.ACC })

        // Act
        availableFeatures.value = AvailableFeatures(identifier = deviceId, availableStreamingFeatures = streaming)

        // Assert
        val state = awaitState(viewModel.uiAvailableOnlineStreamDataTypesState) { it.identifier == deviceId }
        assertEquals(true, state.streamingFeaturesAvailable[PolarDeviceDataType.ACC])
    }

    @Test
    fun `init finalizes files when device is disconnecting`() {
        // Arrange
        val finalized = CountDownLatch(1)
        every { collector.finalizeAllStreams() } answers { finalized.countDown(); ArrayList() }
        newViewModel()

        // Act
        connectionStatus.value = DeviceConnectionState.DeviceDisconnecting(deviceId)

        // Assert
        assertTrue(finalized.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `on cleared finalizes files when reconnection does not happen`() {
        // Arrange
        val viewModel = newViewModel()

        // Act
        OnlineRecordingViewModel::class.java.getDeclaredMethod("onCleared").apply { isAccessible = true }.invoke(viewModel)

        // Assert
        verify(atLeast = 1, timeout = 5_000) { collector.finalizeAllStreams() }
    }

    @Test
    fun `init clears validated files when device is not connected`() {
        // Arrange
        val viewModel = newViewModel()
        viewModel.addValidatedFileUri(uri("HR/dev1/HR_2026-10-30T07:00:00_dev1.txt"))

        // Act
        connectionStatus.value = DeviceConnectionState.DeviceNotConnected(deviceId)

        // Assert
        assertTrue(viewModel.validatedFileUris.value.isEmpty())
    }

    @Test
    fun `stop stream links marker and shares files when session has marker`() {
        // Arrange
        val hr = uri("HR/dev1/HR_2026-10-30T07:00:00_dev1.txt")
        val marker = uri("MARKER/dev1/MARKER_2026-10-30T07:00:00_dev1.txt")
        every { collector.finalizeAllStreams() } returns arrayListOf(hr, marker)
        val viewModel = newViewModel()

        // Act
        viewModel.stopStream(PolarDeviceDataType.HR)

        // Assert
        assertEquals(arrayListOf(hr, marker), awaitState(viewModel.shareFiles) { it.isNotEmpty() })
        assertEquals(marker, viewModel.markerUriFor(hr))
        viewModel.fileShareCompleted()
        assertTrue(viewModel.shareFiles.value.isEmpty())
    }

    @Test
    fun `stop stream shows error when finalizing fails`() {
        // Arrange
        every { collector.finalizeAllStreams() } throws IllegalStateException("disk full")
        val viewModel = newViewModel()

        // Act
        val error = expectError(viewModel) { viewModel.stopStream(PolarDeviceDataType.HR) }

        // Assert
        assertEquals("Failed to finalize stream files", error.header)
    }

    @Test
    fun `set checked tracks checked types when checked and cleared`() {
        // Arrange
        val viewModel = newViewModel()

        // Act
        viewModel.setChecked(PolarDeviceDataType.ACC, true)
        viewModel.setChecked(PolarDeviceDataType.HR, true)
        viewModel.setChecked(PolarDeviceDataType.ACC, false)
        val afterUncheck = viewModel.checkedDataTypes.value
        viewModel.clearAllChecked()

        // Assert
        assertEquals(setOf(PolarDeviceDataType.HR), afterUncheck)
        assertTrue(viewModel.checkedDataTypes.value.isEmpty())
    }
}
