package com.polar.polarsensordatacollector.ui.devicesettings

import android.app.Activity
import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.polar.androidcommunications.api.ble.model.gatt.client.ChargeState
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.repository.AvailableFeatures
import com.polar.polarsensordatacollector.repository.ChargeInformation
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository.SdkFeaturesReadyEvent
import com.polar.polarsensordatacollector.repository.ResultOfRequest
import com.polar.polarsensordatacollector.repository.SdkMode
import com.polar.polarsensordatacollector.ui.landing.ONLINE_OFFLINE_KEY_DEVICE_ID
import com.polar.polarsensordatacollector.ui.utils.MessageUiState
import com.polar.sdk.api.DeviceTelemetryConfiguration
import com.polar.sdk.api.DeviceTelemetryEvent
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarD2HNotificationData
import com.polar.sdk.api.PolarDeviceTelemetryType
import com.polar.sdk.api.PolarDeviceToHostNotification
import com.polar.sdk.api.errors.PolarDeviceNotConnected
import com.polar.sdk.api.errors.PolarDeviceNotFound
import com.polar.sdk.api.model.CheckFirmwareUpdateStatus
import com.polar.sdk.api.model.FirmwareUpdateStatus
import com.polar.sdk.api.model.PolarDiskSpaceData
import com.polar.sdk.api.model.PolarPhysicalConfiguration
import com.polar.sdk.api.model.PolarUserDeviceSettings
import com.polar.sdk.api.model.sleep.PolarSleepRecordingStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DeviceSettingsViewModelTest {

    private val id = "dev1"
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val repository = mockk<PolarDeviceRepository>(relaxed = true)

    private val sdkMode = MutableStateFlow(SdkMode())
    private val features = MutableStateFlow(AvailableFeatures())
    private val offlineSecurity = MutableStateFlow(false)
    private val featuresReady = MutableStateFlow(SdkFeaturesReadyEvent())
    private val supportsSettings = MutableStateFlow(false)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repository.sdkModeState } returns sdkMode
        every { repository.availableFeatures } returns features
        every { repository.isOfflineRecordingSecurityEnabled } returns offlineSecurity
        every { repository.sdkFeaturesReady } returns featuresReady
        every { repository.isTelemetryEnabled } returns MutableStateFlow(false)
        every { repository.isTelemetryAvailable } returns MutableStateFlow(false)
        every { repository.deviceSupportsSettings } returns supportsSettings
        every { repository.getDeviceSupportsSettings(any()) } returns false
        every { repository.observeSleepRecordingStatus(any()) } returns emptyFlow()
        every { repository.checkFirmwareUpdate(any()) } returns emptyFlow()
        coEvery { repository.isSdkModeEnabled(id) } returns ResultOfRequest.Success(true)
        coEvery { repository.isSecurityEnabled(id) } returns true
        coEvery { repository.getMultiBleModeEnabled(id) } returns true
        coEvery { repository.getSensorInitiatedSecurityModeEnabled(id) } returns true
    }

    @After
    fun tearDown() {
        // Stop the jobs of each view model. Old jobs must not run in later tests.
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        // A background job can still use Dispatchers.Main. Retry the reset until the job ends.
        val deadline = System.currentTimeMillis() + 5_000
        while (true) {
            try {
                Dispatchers.resetMain()
                return
            } catch (e: IllegalStateException) {
                if (System.currentTimeMillis() > deadline) throw e
                Thread.sleep(20)
            }
        }
    }

    private val createdViewModels = mutableListOf<DeviceSettingsViewModel>()

    private fun newViewModel() =
        DeviceSettingsViewModel(repository, SavedStateHandle(mapOf(ONLINE_OFFLINE_KEY_DEVICE_ID to id)), application)
            .also { createdViewModels += it }

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Condition not reached" }
            Thread.sleep(10)
        }
    }

    // Wait for one message after each call. This keeps the message order fixed.
    private fun repeatAwaiting(times: Int, total: () -> Int, call: () -> Unit) {
        val base = total()
        repeat(times) { index ->
            call()
            await { total() >= base + index + 1 }
        }
    }

    private fun collect(flow: SharedFlow<MessageUiState>): List<MessageUiState> {
        val messages = CopyOnWriteArrayList<MessageUiState>()
        CoroutineScope(Dispatchers.Unconfined).launch { flow.collect { messages.add(it) } }
        return messages
    }

    @Test
    fun `init throws when identifier is missing`() {
        // Act and Assert
        assertThrows(Exception::class.java) { DeviceSettingsViewModel(repository, SavedStateHandle(), application) }
    }

    @Test
    fun `init exposes identifier`() {
        // Act
        val viewModel = newViewModel()

        // Assert
        assertEquals(id, viewModel.uiIdentifier.value)
    }

    @Test
    fun `sdk mode state updates ui only for own device`() {
        // Arrange
        val viewModel = newViewModel()

        // Act
        sdkMode.value = SdkMode("other", true, SdkMode.STATE.ENABLED, SdkMode.STATE.DISABLED, SdkMode.STATE.DISABLED)
        val afterOther = viewModel.uiSdkModeState.value.isAvailable
        sdkMode.value = SdkMode(id, true, SdkMode.STATE.ENABLED, SdkMode.STATE.DISABLED, SdkMode.STATE.STATE_CHANGE_IN_PROGRESS)

        // Assert
        assertFalse(afterOther)
        val state = viewModel.uiSdkModeState.value
        assertTrue(state.isAvailable)
        assertEquals(SdkMode.STATE.ENABLED, state.sdkModeState)
        assertEquals(SdkMode.STATE.DISABLED, state.sdkModeLedState)
        assertEquals(SdkMode.STATE.STATE_CHANGE_IN_PROGRESS, state.ppiModeLedState)
    }

    @Test
    fun `sdk mode status disables switch when request fails`() {
        // Arrange
        coEvery { repository.isSdkModeEnabled(id) } returns ResultOfRequest.Failure("n/a", null)

        // Act
        val viewModel = newViewModel()
        await { !viewModel.uiSdkModeState.value.isLoading && !viewModel.uiSdkModeState.value.isSwitchEnabled }

        // Assert
        assertFalse(viewModel.uiSdkModeState.value.isSwitchEnabled)
    }

    @Test
    fun `security state follows features and offline security flow`() {
        // Arrange
        val viewModel = newViewModel()
        val map = java.util.EnumMap<PolarBleApi.PolarDeviceDataType, Boolean>(PolarBleApi.PolarDeviceDataType::class.java)
        map[PolarBleApi.PolarDeviceDataType.ECG] = true

        // Act
        features.value = AvailableFeatures(identifier = id, availableOfflineFeatures = map)
        offlineSecurity.value = true

        // Assert
        assertTrue(viewModel.uiSecurityState.value.isAvailable)
        assertTrue(viewModel.uiSecurityState.value.isEnabled)

        // Act
        features.value = AvailableFeatures(identifier = id)

        // Assert
        assertFalse(viewModel.uiSecurityState.value.isAvailable)
    }

    @Test
    fun `security status ends loading`() {
        // Act
        val viewModel = newViewModel()
        await { viewModel.uiSecurityState.value.let { !it.isLoading } && viewModel.uiMultiBleModeState.value.isEnabled }

        // Assert
        assertFalse(viewModel.uiSecurityState.value.isLoading)
        coVerify { repository.isSecurityEnabled(id) }
    }

    @Test
    fun `sdk features ready sets watch face and telemetry availability`() {
        // Arrange
        val viewModel = newViewModel()

        // Act
        featuresReady.value = SdkFeaturesReadyEvent(
            id,
            listOf(PolarBleApi.PolarBleSdkFeature.FEATURE_WATCH_FACES_CONFIGURATION, PolarBleApi.PolarBleSdkFeature.FEATURE_TELEMETRY)
        )

        // Assert
        assertTrue(viewModel.watchFaceConfigAvailable.value)
        assertTrue(viewModel.telemetryAvailable.value)

        // Act
        featuresReady.value = SdkFeaturesReadyEvent(id, emptyList())

        // Assert
        assertFalse(viewModel.watchFaceConfigAvailable.value)
        assertFalse(viewModel.telemetryAvailable.value)
    }

    @Test
    fun `ble multi and sensor initiated status are loaded`() {
        // Act
        val viewModel = newViewModel()
        await {
            viewModel.uiMultiBleModeState.value.isEnabled && viewModel.uiSensorInitiatedSecurityModeState.value.isEnabled
        }

        // Assert
        assertTrue(viewModel.uiMultiBleModeState.value.isSwitchEnabled)
        assertTrue(viewModel.uiSensorInitiatedSecurityModeState.value.isSwitchEnabled)
    }

    @Test
    fun `ble multi and sensor initiated status disable switches when unknown`() {
        // Arrange
        coEvery { repository.getMultiBleModeEnabled(id) } returns null
        coEvery { repository.getSensorInitiatedSecurityModeEnabled(id) } returns null

        // Act
        val viewModel = newViewModel()
        await {
            !viewModel.uiMultiBleModeState.value.isSwitchEnabled && !viewModel.uiSensorInitiatedSecurityModeState.value.isSwitchEnabled
        }

        // Assert
        assertFalse(viewModel.uiMultiBleModeState.value.isEnabled)
    }

    @Test
    fun `settings support is read from repository`() {
        // Arrange
        every { repository.getDeviceSupportsSettings(id) } returns true

        // Act
        val viewModel = newViewModel()

        // Assert
        assertTrue(viewModel.uiSettingsSupportUiState.value)
    }

    @Test
    fun `firmware check reports mandatory update`() {
        // Arrange
        every { repository.checkFirmwareUpdate(id) } returns flowOf(CheckFirmwareUpdateStatus.CheckFwUpdateAvailable("2.0.0", true))

        // Act
        val viewModel = newViewModel()

        // Assert
        val expected = application.getString(R.string.firmware_update_available, "2.0.0") + " " + application.getString(R.string.firmware_update_mandatory)
        assertEquals(expected, viewModel.uiFirmwareUpdateStatus.value)
    }

    @Test
    fun `firmware check reports optional update`() {
        // Arrange
        every { repository.checkFirmwareUpdate(id) } returns flowOf(CheckFirmwareUpdateStatus.CheckFwUpdateAvailable("2.0.0", false))

        // Act
        val viewModel = newViewModel()

        // Assert
        assertTrue(viewModel.uiFirmwareUpdateStatus.value.endsWith(application.getString(R.string.firmware_update_optional)))
    }

    @Test
    fun `firmware check reports no update`() {
        // Arrange
        every { repository.checkFirmwareUpdate(id) } returns flowOf(CheckFirmwareUpdateStatus.CheckFwUpdateNotAvailable("up to date"))

        // Act
        val viewModel = newViewModel()

        // Assert
        assertEquals("up to date", viewModel.uiFirmwareUpdateStatus.value)
    }

    @Test
    fun `firmware check shows error when status is failed`() {
        // Arrange
        every { repository.checkFirmwareUpdate(id) } returns flowOf(CheckFirmwareUpdateStatus.CheckFwUpdateFailed("bad"))

        // Act
        val viewModel = newViewModel()

        // Assert
        assertTrue(viewModel.uiCheckFirmwareUpdateStatus.value is CheckFirmwareUpdateStatus.CheckFwUpdateFailed)
        assertTrue(viewModel.uiFirmwareUpdateStatus.value.contains("bad"))
    }

    @Test
    fun `firmware check sets failed state when flow throws`() {
        // Arrange
        every { repository.checkFirmwareUpdate(id) } returns flow { throw IllegalStateException("boom") }

        // Act
        val viewModel = newViewModel()

        // Assert
        val status = viewModel.uiCheckFirmwareUpdateStatus.value as CheckFirmwareUpdateStatus.CheckFwUpdateFailed
        assertEquals(application.getString(R.string.firmware_update_check_failed), status.details)
    }

    @Test
    fun `do firmware update publishes progress and failure`() {
        // Arrange
        every { repository.doFirmwareUpdate(id, "url") } returns flowOf(
            FirmwareUpdateStatus.WritingFwUpdatePackage("writing"),
            FirmwareUpdateStatus.FwUpdateFailed("failed")
        )
        val viewModel = newViewModel()
        val infos = collect(viewModel.uiShowInfo)
        val errors = collect(viewModel.uiShowError)

        // Act
        viewModel.doFirmwareUpdate("url")

        // Assert
        assertEquals("writing", infos.first().description)
        assertEquals("Firmware update failed", errors.first().header)
        assertEquals("failed", viewModel.uiFirmwareUpdateStatus.value)
    }

    @Test
    fun `do firmware update shows error when flow throws`() {
        // Arrange
        every { repository.doFirmwareUpdate(id, "") } returns flow { throw IllegalStateException("boom") }
        val viewModel = newViewModel()
        val errors = collect(viewModel.uiShowError)

        // Act
        viewModel.doFirmwareUpdate()

        // Assert
        assertEquals("boom", errors.first().description)
    }

    @Test
    fun `simple commands delegate to repository`() {
        // Arrange
        val viewModel = newViewModel()

        // Act
        viewModel.sdkModeToggle()
        viewModel.sdkModeLedAnimation()
        viewModel.ppiModeLedAnimation()
        viewModel.toggleSecurity(true)

        // Assert
        verify(timeout = 5_000) { repository.sdkModeToggle(id) }
        verify(timeout = 5_000) { repository.setSdkModeLedConfig(id) }
        verify(timeout = 5_000) { repository.setPpiModeLedConfig(id) }
        coVerify(timeout = 5_000) { repository.toggleSecurity(id, true) }
    }

    @Test
    fun `device to host notifications show info and stop when toggled again`() {
        // Arrange
        val source = kotlinx.coroutines.flow.MutableSharedFlow<PolarD2HNotificationData>(extraBufferCapacity = 1)
        every { repository.observeDeviceToHostNotifications(id) } returns source
        val viewModel = newViewModel()
        val infos = collect(viewModel.uiShowInfo)

        // Act
        viewModel.toggleDeviceToHostNotifications()
        val observing = viewModel.uiDeviceToHostNotificationsState.value.isObserving
        source.tryEmit(PolarD2HNotificationData(PolarDeviceToHostNotification.values().first(), ByteArray(3), null))

        // Assert
        assertTrue(observing)
        assertTrue(infos.first().description!!.endsWith("with 3 bytes"))

        // Act
        viewModel.toggleDeviceToHostNotifications()

        // Assert
        assertFalse(viewModel.uiDeviceToHostNotificationsState.value.isObserving)
    }

    @Test
    fun `device to host notifications show error when observation fails`() {
        // Arrange
        every { repository.observeDeviceToHostNotifications(id) } returns flow { throw IllegalStateException("boom") }
        val viewModel = newViewModel()
        val errors = collect(viewModel.uiShowError)

        // Act
        viewModel.toggleDeviceToHostNotifications()

        // Assert
        assertEquals("D2H Notification failed", errors.first().header)
        assertFalse(viewModel.uiDeviceToHostNotificationsState.value.isObserving)
    }

    @Test
    fun `get ftu info shows info or error`() {
        // Arrange
        val viewModel = newViewModel()
        val infos = collect(viewModel.uiShowInfo)
        val errors = collect(viewModel.uiShowError)
        coEvery { repository.getFtuInfo(id) } returns true andThenThrows IllegalStateException("boom")

        // Act
        viewModel.getFtuInfo()
        viewModel.getFtuInfo()

        // Assert
        await { infos.isNotEmpty() && errors.isNotEmpty() }
        assertEquals("true", infos.first().description)
        assertEquals("Fetching FTU information failed", errors.first().header)
    }

    @Test
    fun `get user physical info stores value and clears on failure`() = runBlocking {
        // Arrange
        val config = mockk<PolarPhysicalConfiguration>()
        coEvery { repository.getUserPhysicalConfiguration(id) } returns ResultOfRequest.Success(config) andThen
            ResultOfRequest.Failure("x", null)
        val viewModel = newViewModel()

        // Act
        viewModel.getUserPhysicalInfo()
        val first = viewModel.physInfo
        viewModel.getUserPhysicalInfo()

        // Assert
        assertEquals(config, first)
        assertNull(viewModel.physInfo)
    }

    @Test
    fun `do restart and power commands run through repository`() {
        // Arrange
        every { repository.doRestart(id, any()) } answers { secondArg<(Result<Unit>) -> Unit>().invoke(Result.success(Unit)) }
        every { repository.setWarehouseSleep(id, any()) } answers { secondArg<(Result<Unit>) -> Unit>().invoke(Result.failure(IllegalStateException("x"))) }
        every { repository.setHibernateMode(id, any()) } answers { secondArg<(Result<Unit>) -> Unit>().invoke(Result.success(Unit)) }
        every { repository.turnDeviceOff(id, any()) } answers { secondArg<(Result<Unit>) -> Unit>().invoke(Result.failure(IllegalStateException("x"))) }
        val viewModel = newViewModel()

        // Act
        viewModel.doRestart()
        viewModel.setWarehouseSleep()
        viewModel.setHibernateMode()
        viewModel.setTurnDeviceOff()

        // Assert
        verify { repository.doRestart(id, any()) }
        verify { repository.setWarehouseSleep(id, any()) }
        verify { repository.setHibernateMode(id, any()) }
        verify { repository.turnDeviceOff(id, any()) }
    }

    @Test
    fun `do factory reset shows info with pairing note on success`() {
        // Arrange
        every { repository.doFactoryReset(id, true, any()) } answers { thirdArg<(Result<Unit>) -> Unit>().invoke(Result.success(Unit)) }
        val viewModel = newViewModel()
        val infos = collect(viewModel.uiShowInfo)

        // Act
        viewModel.doFactoryReset(true)

        // Assert
        assertTrue(infos.first().description!!.contains("Pairing information will be preserved"))
    }

    @Test
    fun `do factory reset shows error on failure`() {
        // Arrange
        every { repository.doFactoryReset(id, false, any()) } answers { thirdArg<(Result<Unit>) -> Unit>().invoke(Result.failure(IllegalStateException("nope"))) }
        val viewModel = newViewModel()
        val errors = collect(viewModel.uiShowError)

        // Act
        viewModel.doFactoryReset()

        // Assert
        assertEquals("nope", errors.first().description)
    }

    @Test
    fun `start telemetry stream shows info when type is not supported`() {
        // Arrange
        val type = PolarDeviceTelemetryType.memfault_mds
        every { repository.getDeviceTelemetryConfiguration(type, id) } returns DeviceTelemetryConfiguration(id, "uri", "auth", 1)
        val viewModel = newViewModel()
        val infos = collect(viewModel.uiShowInfo)

        // Act
        viewModel.startTelemetryStream(type)

        // Assert
        assertEquals("Telemetry not supported:", infos.first().header)
        coVerify(exactly = 0) { repository.startTelemetry(any(), any()) }
    }

    @Test
    fun `start telemetry stream reports chunks when supported`() {
        // Arrange
        val type = PolarDeviceTelemetryType.memfault_mds
        every { repository.getDeviceTelemetryConfiguration(type, id) } returns DeviceTelemetryConfiguration(id, "uri", "auth", 0)
        coEvery { repository.startTelemetry(type, id) } returns flowOf(DeviceTelemetryEvent(type, ByteArray(4)))
        val viewModel = newViewModel()
        val infos = collect(viewModel.uiShowInfo)

        // Act
        viewModel.startTelemetryStream(type)
        await { infos.any { it.header.startsWith("Telemetry chunk") } }

        // Assert
        assertEquals(type, viewModel.selectedTelemetryType)
    }

    @Test
    fun `start telemetry stream shows error when stream fails`() {
        // Arrange
        val type = PolarDeviceTelemetryType.memfault_mds
        every { repository.getDeviceTelemetryConfiguration(type, id) } returns DeviceTelemetryConfiguration(id, "uri", "auth", 0)
        coEvery { repository.startTelemetry(type, id) } returns flow { throw IllegalStateException("boom") }
        val viewModel = newViewModel()
        val errors = collect(viewModel.uiShowError)

        // Act
        viewModel.startTelemetryStream(type)
        await { errors.isNotEmpty() }

        // Assert
        assertEquals("Telemetry stream failed", errors.first().header)
        // The ViewModel throws the error again. The test library keeps it and reports it in the next
        // runTest call of another test. Call runTest here to clear it.
        runCatching { kotlinx.coroutines.test.runTest { } }
    }

    @Test
    fun `stop telemetry stream shows info or error`() {
        // Arrange
        val type = PolarDeviceTelemetryType.memfault_mds
        every { repository.getDeviceTelemetryConfiguration(type, id) } returns DeviceTelemetryConfiguration(id, "uri", "auth", 0)
        coEvery { repository.startTelemetry(type, id) } returns emptyFlow()
        coEvery { repository.stopTelemetry(type, id) } returns Unit andThenThrows IllegalStateException("boom")
        val viewModel = newViewModel()
        val infos = collect(viewModel.uiShowInfo)
        val errors = collect(viewModel.uiShowError)
        viewModel.startTelemetryStream(type)

        // Act
        viewModel.stopTelemetryStream()
        viewModel.stopTelemetryStream()

        // Assert
        assertTrue(infos.any { it.header == "Telemetry stream stopped" })
        assertEquals("Telemetry stream stop failed", errors.first().header)
    }

    @Test
    fun `open user device settings activity starts activity when settings exist`() {
        // Arrange
        coEvery { repository.getDeviceUserSettings(id) } returns ResultOfRequest.Success(PolarUserDeviceSettings())
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()

        // Act
        newViewModel().openUserDeviceSettingsActivity(activity)

        // Assert
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(id, intent.getStringExtra("IDENTIFIER"))
    }

    @Test
    fun `open user device settings activity shows error when settings are empty`() {
        // Arrange
        coEvery { repository.getDeviceUserSettings(id) } returns ResultOfRequest.Success()
        val viewModel = newViewModel()
        val errors = collect(viewModel.uiShowError)

        // Act
        viewModel.openUserDeviceSettingsActivity(Robolectric.buildActivity(Activity::class.java).create().get())

        // Assert
        assertEquals(application.getString(R.string.user_device_settings_file_not_readable), errors.first().description)
    }

    @Test
    fun `open user device settings activity describes failure cause`() {
        // Arrange
        val causes = listOf(
            PolarDeviceNotFound() to R.string.user_device_settings_device_session_not_found,
            PolarDeviceNotConnected() to R.string.user_device_settings_device_not_connected
        )
        causes.forEach { (cause, resource) ->
            coEvery { repository.getDeviceUserSettings(id) } returns ResultOfRequest.Failure("msg", cause)
            val viewModel = newViewModel()
            val errors = collect(viewModel.uiShowError)

            // Act
            viewModel.openUserDeviceSettingsActivity(Robolectric.buildActivity(Activity::class.java).create().get())

            // Assert
            assertEquals(application.getString(resource), errors.first().description)
        }
    }

    @Test
    fun `open user device settings activity uses message when no cause`() {
        // Arrange
        coEvery { repository.getDeviceUserSettings(id) } returns ResultOfRequest.Failure("fallback", null)
        val viewModel = newViewModel()
        val errors = collect(viewModel.uiShowError)

        // Act
        viewModel.openUserDeviceSettingsActivity(Robolectric.buildActivity(Activity::class.java).create().get())

        // Assert
        assertEquals("fallback", errors.first().description)
    }

    @Test
    fun `open physical config activity passes identifier`() {
        // Arrange
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()

        // Act
        newViewModel().openPhysicalConfigActivity(activity)

        // Assert
        assertEquals(id, shadowOf(activity).nextStartedActivity.getStringExtra(ONLINE_OFFLINE_KEY_DEVICE_ID))
    }

    @Test
    fun `device user location default shows error when settings fetch fails`() {
        // Arrange
        supportsSettings.value = true
        // The call waits for the collector. The error flow drops values without a collector.
        val collectorReady = java.util.concurrent.CountDownLatch(1)
        coEvery { repository.getDeviceUserSettings(id) } coAnswers {
            collectorReady.await(5, java.util.concurrent.TimeUnit.SECONDS)
            ResultOfRequest.Failure("fetch failed", null)
        }
        val viewModel = newViewModel()
        val errors = collect(viewModel.uiShowError)

        // Act
        collectorReady.countDown()
        await { errors.isNotEmpty() }

        // Assert
        assertEquals("fetch failed", errors.first().header)
        assertNotNull(viewModel)
    }

    @Test
    fun `delete stored device files shows info error and exception`() {
        // Arrange
        val type = PolarBleApi.PolarStoredDataType.values().first()
        val date = LocalDate.of(2024, 1, 1)
        coEvery { repository.deleteDeviceData(id, type, date) } returnsMany listOf(ResultOfRequest.Success(1), ResultOfRequest.Failure("x", null)) andThenThrows IllegalStateException("boom")
        val viewModel = newViewModel()
        val infos = collect(viewModel.uiShowInfo)
        val errors = collect(viewModel.uiShowError)

        // Act
        repeatAwaiting(3, { infos.size + errors.size }) { viewModel.deleteStoredDeviceFiles(type, date) }

        // Assert
        assertTrue(infos.first().header.startsWith("Successfully deleted"))
        assertEquals("boom", errors.last().header)
    }

    @Test
    fun `delete date folders and telemetry report results`() {
        // Arrange
        coEvery { repository.deleteDeviceDateFolders(id, null, null) } returns ResultOfRequest.Success() andThen ResultOfRequest.Failure("x", null) andThenThrows IllegalStateException("boom")
        coEvery { repository.deleteTelemetryData(id) } returns ResultOfRequest.Success() andThen ResultOfRequest.Failure("x", null) andThenThrows IllegalStateException("boom2")
        val viewModel = newViewModel()
        val infos = collect(viewModel.uiShowInfo)
        val errors = collect(viewModel.uiShowError)

        // Act
        repeatAwaiting(3, { infos.size + errors.size }) { viewModel.deleteDateFolders(null, null) }
        repeatAwaiting(3, { infos.size + errors.size }) { viewModel.deleteTelemetryData() }
        await { errors.size >= 4 }

        // Assert
        assertEquals(2, infos.size)
        assertTrue(errors.any { it.header == "boom" })
        assertTrue(errors.any { it.header == "boom2" })
    }

    @Test
    fun `set time completes and shows info or error`() {
        // Arrange
        coEvery { repository.setTime(id, any()) } returns ResultOfRequest.Success() andThen ResultOfRequest.Failure("set failed", null)
        val viewModel = newViewModel()
        val infos = collect(viewModel.uiShowInfo)
        val errors = collect(viewModel.uiShowError)

        // Act
        viewModel.setTime()
        await { infos.isNotEmpty() }
        viewModel.setTime()
        await { errors.isNotEmpty() }

        // Assert
        assertEquals("Device time set", infos.first().header)
        assertEquals("set failed", errors.first().header)
        await { viewModel.uiWriteTimeStatus.value == StatusWriteTime.Completed }
    }

    @Test
    fun `read time shows info or error`() {
        // Arrange
        coEvery { repository.getTime(id) } returns ZonedDateTime.now() andThenThrows IllegalStateException("boom")
        val viewModel = newViewModel()
        val infos = collect(viewModel.uiShowInfo)
        val errors = collect(viewModel.uiShowError)

        // Act
        viewModel.readTime()
        viewModel.readTime()

        // Assert
        assertEquals("Device time get", infos.first().header)
        assertEquals("Get time failed", errors.first().header)
        assertEquals(StatusReadTime.Completed, viewModel.uiReadTimeStatus.value)
    }

    @Test
    fun `wait for connection sets connection status`() {
        // Arrange
        coEvery { repository.waitForConnection(id) } returns Unit andThenThrows IllegalStateException("boom")
        val viewModel = newViewModel()

        // Act
        viewModel.waitForConnection()
        val connected = viewModel.connectionStatus.value
        viewModel.waitForConnection()

        // Assert
        assertTrue(connected)
        assertFalse(viewModel.connectionStatus.value)
    }

    @Test
    fun `get disk space calls success or error callback`() {
        // Arrange
        val space = PolarDiskSpaceData(100L, 40L)
        coEvery { repository.getDiskSpace(id) } returns ResultOfRequest.Success(space) andThen ResultOfRequest.Failure("disk failed", null)
        val viewModel = newViewModel()
        var success: PolarDiskSpaceData? = null
        var error: String? = null

        // Act
        viewModel.getDiskSpace({ success = it }, { error = it })
        viewModel.getDiskSpace({ success = it }, { error = it })

        // Assert
        await { success != null && error != null }
        assertEquals(space, success)
        assertEquals("disk failed", error)
    }

    @Test
    fun `set ble multi connection updates state or disables switch`() {
        // Arrange
        coEvery { repository.setBleMultiConnectionMode(id, true) } returns Unit
        coEvery { repository.setBleMultiConnectionMode(id, false) } throws IllegalStateException("boom")
        val viewModel = newViewModel()
        val errors = collect(viewModel.uiShowError)

        // Act
        viewModel.setBleMultiConnection(true)
        val enabled = viewModel.uiMultiBleModeState.value.isEnabled
        viewModel.setBleMultiConnection(false)

        // Assert
        assertTrue(enabled)
        assertFalse(viewModel.uiMultiBleModeState.value.isSwitchEnabled)
        assertTrue(errors.first().header.contains("BLE multi connection"))
    }

    @Test
    fun `set sensor initiated security mode updates state or disables switch`() {
        // Arrange
        coEvery { repository.setSensorInitiatedSecurityMode(id, true) } returns Unit
        coEvery { repository.setSensorInitiatedSecurityMode(id, false) } throws IllegalStateException("boom")
        val viewModel = newViewModel()
        val errors = collect(viewModel.uiShowError)

        // Act
        viewModel.setSensorInitiatedSecurityMode(true)
        val enabled = viewModel.uiSensorInitiatedSecurityModeState.value.isEnabled
        viewModel.setSensorInitiatedSecurityMode(false)

        // Assert
        assertTrue(enabled)
        assertFalse(viewModel.uiSensorInitiatedSecurityModeState.value.isSwitchEnabled)
        assertTrue(errors.first().header.contains("sensor initiated security mode"))
    }

    @Test
    fun `force stop sleep reports each outcome`() {
        // Arrange
        coEvery { repository.forceStopSleep(id) } returnsMany listOf(
            ResultOfRequest.Success(true), ResultOfRequest.Success(), ResultOfRequest.Failure("stop failed", null)
        ) andThenThrows IllegalStateException("boom")
        val viewModel = newViewModel()
        val infos = collect(viewModel.uiShowInfo)
        val errors = collect(viewModel.uiShowError)

        // Act
        repeatAwaiting(4, { infos.size + errors.size }) { viewModel.forceStopSleep() }
        await { errors.size >= 3 }

        // Assert
        assertEquals(1, infos.size)
        assertEquals(listOf("Failed to stop sleep recording.", "stop failed", "boom"), errors.map { it.header })
    }

    @Test
    fun `get sleep recording status updates state and shows info`() {
        // Arrange
        val status = mockk<PolarSleepRecordingStatus>(relaxed = true)
        coEvery { repository.getSleepRecordingStatus(id) } returns ResultOfRequest.Success(status)
        val viewModel = newViewModel()
        val infos = collect(viewModel.uiShowInfo)

        // Act
        viewModel.getSleepRecordingStatus()
        await { infos.isNotEmpty() }

        // Assert
        assertEquals(status, viewModel.sleepRecordingState.value.status)
        assertFalse(viewModel.sleepRecordingState.value.isLoading)
    }

    @Test
    fun `get sleep recording status stops loading on empty failure and exception`() {
        // Arrange
        coEvery { repository.getSleepRecordingStatus(id) } returnsMany listOf(
            ResultOfRequest.Success(), ResultOfRequest.Failure("status failed", null)
        ) andThenThrows IllegalStateException("boom")
        val viewModel = newViewModel()
        val errors = collect(viewModel.uiShowError)

        // Act
        repeatAwaiting(3, { errors.size }) { viewModel.getSleepRecordingStatus() }
        await { errors.size >= 3 }

        // Assert
        assertEquals(application.getString(R.string.sleep_recording_status_fetch_failed), errors.first().header)
        assertEquals("boom", errors.last().header)
        assertFalse(viewModel.sleepRecordingState.value.isLoading)
    }

    @Test
    fun `observed sleep recording status updates state`() {
        // Arrange
        val status = mockk<PolarSleepRecordingStatus>(relaxed = true)
        every { repository.observeSleepRecordingStatus(id) } returns flowOf(status)

        // Act
        val viewModel = newViewModel()

        // Assert
        assertEquals(status, viewModel.sleepRecordingState.value.status)
    }

    @Test
    fun `observed sleep recording status ignores stream error`() {
        // Arrange
        every { repository.observeSleepRecordingStatus(id) } returns flow { throw IllegalStateException("boom") }

        // Act
        val viewModel = newViewModel()

        // Assert
        assertNull(viewModel.sleepRecordingState.value.status)
    }

    @Test
    fun `get charge state shows info or errors`() {
        // Arrange
        coEvery { repository.getChargeInformation(id) } returnsMany listOf(
            ResultOfRequest.Success(ChargeInformation(80, ChargeState.CHARGING)),
            ResultOfRequest.Success(),
            ResultOfRequest.Failure("charge failed", null)
        ) andThenThrows IllegalStateException("boom")
        val viewModel = newViewModel()
        val infos = collect(viewModel.uiShowInfo)
        val errors = collect(viewModel.uiShowError)

        // Act
        repeatAwaiting(4, { infos.size + errors.size }) { viewModel.getChargeState() }
        await { errors.size >= 3 }

        // Assert
        assertTrue(infos.first().header.contains("80 %"))
        assertEquals(listOf("Failed to fetch charge information.", "charge failed", "boom"), errors.map { it.header })
    }

    @Test
    fun `check if device disconnected due removed pairing reports each outcome`() {
        // Arrange
        coEvery { repository.checkIfDeviceDisconnectedDueRemovedPairing(id) } returnsMany listOf(
            ResultOfRequest.Success(Pair(true, -1)),
            ResultOfRequest.Success(),
            ResultOfRequest.Failure("check failed", null)
        ) andThenThrows IllegalStateException("boom")
        val viewModel = newViewModel()
        val infos = collect(viewModel.uiShowInfo)
        val errors = collect(viewModel.uiShowError)

        // Act
        repeatAwaiting(4, { infos.size + errors.size }) { viewModel.checkIfDeviceDisconnectedDueRemovedPairing() }
        await { errors.size >= 3 }

        // Assert
        assertTrue(infos.first().header.contains("(Not set)"))
        assertEquals("boom", errors.last().header)
    }

    @Test
    fun `get BLE signal strength reports each outcome`() {
        // Arrange
        coEvery { repository.getBleSignalStrength(id) } returnsMany listOf(
            ResultOfRequest.Success(-60), ResultOfRequest.Success(), ResultOfRequest.Failure("rssi failed", null)
        )
        val viewModel = newViewModel()
        val infos = collect(viewModel.uiShowInfo)
        val errors = collect(viewModel.uiShowError)

        // Act
        repeatAwaiting(3, { infos.size + errors.size }) { viewModel.getBLESignalStrength() }
        await { errors.size >= 2 }

        // Assert
        assertEquals("RSSI: -60 dBm", infos.first().header)
        assertEquals("rssi failed", errors.last().header)
    }

    @Test
    fun `export device logs delegates to repository`() = runBlocking {
        // Arrange
        val result = ResultOfRequest.Success(emptyList<com.polar.sdk.api.model.PolarDeviceLog>())
        coEvery { repository.exportDeviceLogs(id) } returns result

        // Act and Assert
        assertEquals(result, newViewModel().exportDeviceLogs())
    }
}
