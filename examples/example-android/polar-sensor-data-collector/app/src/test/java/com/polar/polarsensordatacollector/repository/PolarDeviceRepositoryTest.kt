package com.polar.polarsensordatacollector.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.polar.androidcommunications.api.ble.model.gatt.client.BleDisClient
import com.polar.androidcommunications.api.ble.model.gatt.client.ChargeState
import com.polar.polarsensordatacollector.DataCollector
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.crypto.SecretKeyManager
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleDisconnectInfo
import com.polar.sdk.api.PolarBleDisconnectReason
import com.polar.sdk.api.PolarBleRecoveryAction
import com.polar.sdk.api.PolarDeviceTelemetryType
import com.polar.sdk.api.errors.PolarOperationNotSupported
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarDiskSpaceData
import com.polar.sdk.api.model.PolarHrBroadcastData
import com.polar.sdk.api.model.PolarOfflineRecordingData
import com.polar.sdk.api.model.PolarOfflineRecordingEntry
import com.polar.sdk.api.model.PolarOfflineRecordingResult
import com.polar.sdk.api.model.PolarSensorSetting
import com.polar.sdk.api.model.sleep.PolarSleepRecordingStatus
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionReference
import com.polar.sdk.impl.BDBleApiImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
class PolarDeviceRepositoryTest {

    private val id = "dev1"
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val api = mockk<BDBleApiImpl>(relaxed = true)
    private val collector = mockk<DataCollector>(relaxed = true)
    private val security = mockk<SecretKeyManager>(relaxed = true)
    private val from = LocalDate.of(2024, 3, 1)
    private val to = LocalDate.of(2024, 3, 2)
    private lateinit var repository: PolarDeviceRepository

    @Before
    fun setUp() {
        every { security.getSecretKey(any()) } returns null
        repository = PolarDeviceRepository(api, collector, security, DeviceApiCallSerializer(), context)
    }

    @After
    fun tearDown() {
        repository.sdkShutDown()
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Condition not reached" }
            Thread.sleep(10)
        }
    }

    private fun deviceInfo(deviceId: String = id, supportsSettings: Boolean = true): PolarDeviceInfo {
        val info = mockk<PolarDeviceInfo>(relaxed = true)
        every { info.deviceId } returns deviceId
        every { info.hasSAGRFCFileSystem } returns supportsSettings
        return info
    }

    private fun disconnectInfo(action: PolarBleRecoveryAction) =
        PolarBleDisconnectInfo(PolarBleDisconnectReason.CONNECTION_LOST, action)

    private fun connect(vararg ready: PolarBleApi.PolarBleSdkFeature) {
        val info = deviceInfo()
        repository.deviceConnected(info)
        repository.bleSdkFeaturesReadiness(id, ready.toList(), emptyList())
    }

    private fun resultOf(
        ok: () -> Unit,
        fail: () -> Unit,
        call: suspend () -> ResultOfRequest<*>
    ) = runBlocking {
        ok()
        assertTrue(call() is ResultOfRequest.Success)
        fail()
        assertTrue(call() is ResultOfRequest.Failure)
    }

    @Test
    fun `init registers callback with api`() {
        // Assert
        verify { api.setApiCallback(repository) }
    }

    @Test
    fun `ble power state changed updates power state`() {
        // Act
        repository.blePowerStateChanged(true)

        // Assert
        assertTrue(repository.isPhoneBlePowerOn.value)
    }

    @Test
    fun `ble power state changed reconnects connected devices after power returns`() {
        // Arrange
        connect(PolarBleApi.PolarBleSdkFeature.FEATURE_HR)
        every { api.connectToDevice(id) } returns Unit

        // Act
        repository.blePowerStateChanged(false)
        repository.blePowerStateChanged(true)

        // Assert
        verify { api.connectToDevice(id) }
    }

    @Test
    fun `ble power state changed continues when reconnect fails`() {
        // Arrange
        connect(PolarBleApi.PolarBleSdkFeature.FEATURE_HR)
        repository.deviceConnected(deviceInfo("other"))
        every { api.connectToDevice(id) } throws IllegalStateException("boom")

        // Act
        repository.blePowerStateChanged(false)
        repository.blePowerStateChanged(true)

        // Assert
        verify { api.connectToDevice(id) }
        verify { api.connectToDevice("other") }
    }

    @Test
    fun `device connecting sets connecting state`() {
        // Act
        repository.deviceConnecting(deviceInfo())

        // Assert
        val state = repository.deviceConnectionStatus.value
        assertTrue(state is DeviceConnectionState.DeviceConnecting)
        assertEquals(id, state.identifier)
    }

    @Test
    fun `device connected caches settings support and clears disconnect info`() {
        // Act
        repository.deviceConnected(deviceInfo(supportsSettings = true))

        // Assert
        assertTrue(repository.getDeviceSupportsSettings(id))
        assertTrue(repository.deviceSupportsSettings.value)
        assertNull(repository.lastDisconnectInfo.value)
        assertFalse(repository.getDeviceSupportsSettings("unknown"))
    }

    @Test
    fun `features readiness marks device connected and merges features`() {
        // Arrange
        repository.deviceConnected(deviceInfo())

        // Act
        repository.bleSdkFeaturesReadiness(id, listOf(PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO), emptyList())
        repository.bleSdkFeaturesReadiness(id, listOf(PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO), emptyList())
        repository.bleSdkFeatureReady(id, PolarBleApi.PolarBleSdkFeature.FEATURE_TELEMETRY)

        // Assert
        assertTrue(repository.deviceConnectionStatus.value is DeviceConnectionState.DeviceConnected)
        assertEquals(
            setOf(
                PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_TELEMETRY
            ),
            repository.sdkFeaturesReady.value.readyFeatures.toSet()
        )
    }

    @Test
    fun `features readiness disconnects device when nothing is ready but something is unavailable`() {
        // Arrange
        repository.deviceConnected(deviceInfo())

        // Act
        repository.bleSdkFeaturesReadiness(id, emptyList(), listOf(PolarBleApi.PolarBleSdkFeature.FEATURE_HR))

        // Assert
        verify { api.disconnectFromDevice(id) }
        assertTrue(repository.deviceConnectionStatus.value is DeviceConnectionState.DeviceNotConnected)
    }

    @Test
    fun `features readiness enables sdk mode when feature is ready`() {
        // Act
        connect(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE)

        // Assert
        assertTrue(repository.sdkModeState.value.isAvailable)
        assertEquals(id, repository.sdkModeState.value.identifier)
    }

    @Test
    fun `features readiness loads streaming and offline feature types`() {
        // Arrange
        coEvery { api.getAvailableHRServiceDataTypes(id) } returns setOf(PolarBleApi.PolarDeviceDataType.HR)
        coEvery { api.getAvailableOnlineStreamDataTypes(id) } returns setOf(PolarBleApi.PolarDeviceDataType.ACC)
        coEvery { api.getAvailableOfflineRecordingDataTypes(id) } returns setOf(PolarBleApi.PolarDeviceDataType.ECG)

        // Act
        connect(
            PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING
        )

        // Assert
        await {
            val f = repository.availableFeatures.value
            f.identifier == id &&
                f.availableStreamingFeatures[PolarBleApi.PolarDeviceDataType.ACC] == true &&
                f.availableStreamingFeatures[PolarBleApi.PolarDeviceDataType.HR] == true &&
                f.availableOfflineFeatures[PolarBleApi.PolarDeviceDataType.ECG] == true
        }
    }

    @Test
    fun `features readiness ignores type lookup failures`() {
        // Arrange
        coEvery { api.getAvailableHRServiceDataTypes(id) } throws IllegalStateException("x")
        coEvery { api.getAvailableOnlineStreamDataTypes(id) } throws IllegalStateException("x")
        coEvery { api.getAvailableOfflineRecordingDataTypes(id) } throws IllegalStateException("x")

        // Act
        connect(
            PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING
        )
        coVerify(timeout = 5_000) { api.getAvailableHRServiceDataTypes(id) }
        coVerify(timeout = 5_000) { api.getAvailableOnlineStreamDataTypes(id) }
        coVerify(timeout = 5_000) { api.getAvailableOfflineRecordingDataTypes(id) }
        repository.bleSdkFeatureReady(id, PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO)

        // Assert
        assertTrue(repository.deviceConnectionStatus.value is DeviceConnectionState.DeviceConnected)
        assertTrue(PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO in repository.sdkFeaturesReady.value.readyFeatures)
        assertTrue(repository.availableFeatures.value.availableStreamingFeatures.values.none { it })
    }

    @Test
    fun `select device restores cached features`() {
        // Arrange
        coEvery { api.getAvailableOnlineStreamDataTypes(id) } returns setOf(PolarBleApi.PolarDeviceDataType.ACC)
        connect(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)
        await { repository.availableFeatures.value.identifier == id }
        repository.deviceConnected(deviceInfo("other"))
        repository.bleSdkFeaturesReadiness("other", listOf(PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO), emptyList())
        assertEquals("other", repository.sdkFeaturesReady.value.identifier)

        // Act
        repository.selectDevice(id)

        // Assert
        assertEquals(id, repository.sdkFeaturesReady.value.identifier)
        assertEquals(id, repository.availableFeatures.value.identifier)
    }

    @Test
    fun `device disconnected resets device state`() {
        // Arrange
        connect(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE)

        // Act
        repository.deviceDisconnected(deviceInfo(), disconnectInfo(PolarBleRecoveryAction.NONE))

        // Assert
        assertTrue(repository.deviceConnectionStatus.value is DeviceConnectionState.DeviceNotConnected)
        assertEquals(id, repository.availableFeatures.value.identifier)
        assertFalse(repository.sdkModeState.value.isAvailable)
        assertTrue(repository.sdkFeaturesReady.value.readyFeatures.isEmpty())
        assertFalse(repository.getDeviceSupportsSettings(id))
    }

    @Test
    fun `device disconnected deprecated overload resets device state`() {
        // Arrange
        connect(PolarBleApi.PolarBleSdkFeature.FEATURE_HR)

        // Act
        @Suppress("DEPRECATION")
        repository.deviceDisconnected(deviceInfo())

        // Assert
        assertTrue(repository.deviceConnectionStatus.value is DeviceConnectionState.DeviceNotConnected)
    }

    @Test
    fun `device disconnected emits guidance for terminal action and reports only once`() {
        // Arrange
        repository.blePowerStateChanged(true)
        val guidance = CopyOnWriteArrayList<String>()
        CoroutineScope(Dispatchers.Unconfined).launch { repository.disconnectGuidance.collect { guidance.add(it) } }
        val info = disconnectInfo(PolarBleRecoveryAction.REMOVE_PAIRING_AND_PAIR_AGAIN)

        // Act
        repository.deviceDisconnected(deviceInfo(), info)
        repository.deviceDisconnected(deviceInfo(), info)

        // Assert
        assertEquals(listOf(context.getString(R.string.disconnect_guidance_remove_pairing_and_pair_again, id)), guidance.toList())
        assertEquals(info, repository.lastDisconnectInfo.value)
    }

    @Test
    fun `device disconnected emits delayed guidance for retry actions`() {
        // Arrange
        repository.blePowerStateChanged(true)
        val guidance = CopyOnWriteArrayList<String>()
        CoroutineScope(Dispatchers.Unconfined).launch { repository.disconnectGuidance.collect { guidance.add(it) } }

        // Act
        repository.deviceDisconnected(deviceInfo(), disconnectInfo(PolarBleRecoveryAction.RETRY_CONNECTION))
        await { guidance.isNotEmpty() }

        // Assert
        assertEquals(context.getString(R.string.disconnect_guidance_retry_connection, id), guidance.first())
    }

    @Test
    fun `device connected cancels pending guidance`() {
        // Arrange
        repository.blePowerStateChanged(true)
        val guidance = CopyOnWriteArrayList<String>()
        CoroutineScope(Dispatchers.Unconfined).launch { repository.disconnectGuidance.collect { guidance.add(it) } }

        // Act
        repository.deviceDisconnected(deviceInfo(), disconnectInfo(PolarBleRecoveryAction.RETRY_OPERATION))
        repository.deviceConnected(deviceInfo())
        Thread.sleep(1_000)

        // Assert
        assertTrue(guidance.isEmpty())
    }

    @Test
    fun `device disconnected stays silent when disconnect was requested by app`() {
        // Arrange
        repository.blePowerStateChanged(true)
        val guidance = CopyOnWriteArrayList<String>()
        CoroutineScope(Dispatchers.Unconfined).launch { repository.disconnectGuidance.collect { guidance.add(it) } }

        // Act
        repository.disconnectFromDevice(id)
        val state = repository.deviceConnectionStatus.value
        repository.deviceDisconnected(deviceInfo(), disconnectInfo(PolarBleRecoveryAction.RETRY_PAIRING))

        // Assert
        assertTrue(state is DeviceConnectionState.DeviceDisconnecting)
        assertTrue(guidance.isEmpty())
        verify { api.disconnectFromDevice(id) }
    }

    @Test
    fun `device disconnected stays silent when phone bluetooth is off`() {
        // Arrange
        val guidance = CopyOnWriteArrayList<String>()
        CoroutineScope(Dispatchers.Unconfined).launch { repository.disconnectGuidance.collect { guidance.add(it) } }

        // Act
        repository.deviceDisconnected(deviceInfo(), disconnectInfo(PolarBleRecoveryAction.RETRY_PAIRING))

        // Assert
        assertTrue(guidance.isEmpty())
    }

    @Test
    fun `connect to device delegates to api`() {
        // Act
        repository.connectToDevice(id)

        // Assert
        verify { api.connectToDevice(id) }
    }

    @Test
    fun `firmware update blocks connection state changes until completed`() = runBlocking {
        // Arrange
        every { api.updateFirmware(id, "url") } returns flow {
            repository.deviceConnected(deviceInfo())
            repository.deviceConnecting(deviceInfo())
            repository.deviceDisconnected(deviceInfo(), disconnectInfo(PolarBleRecoveryAction.NONE))
            @Suppress("DEPRECATION")
            repository.deviceDisconnected(deviceInfo())
            repository.bleSdkFeaturesReadiness(id, listOf(PolarBleApi.PolarBleSdkFeature.FEATURE_HR), emptyList())
            emit(com.polar.sdk.api.model.FirmwareUpdateStatus.FetchingFwUpdatePackage("x"))
        }

        // Act
        val statuses = repository.doFirmwareUpdate(id, "url").toList()

        // Assert
        assertEquals(1, statuses.size)
        assertTrue(repository.deviceConnectionStatus.value is DeviceConnectionState.DeviceNotConnected)
        assertTrue(repository.sdkFeaturesReady.value.readyFeatures.isEmpty())
    }

    @Test
    fun `device information callbacks update information`() {
        // Act
        repository.disInformationReceived(id, BleDisClient.SOFTWARE_REVISION_STRING, "1.2.3")
        repository.disInformationReceived(id, UUID.randomUUID(), "ignored")
        repository.batteryLevelReceived(id, 77)
        repository.batteryChargingStatusReceived(id, ChargeState.CHARGING)

        // Assert
        val info = repository.deviceInformation.value
        assertEquals("1.2.3", info.firmwareVersion)
        assertEquals(77, info.batteryLevel)
        assertEquals(ChargeState.CHARGING, info.batteryChargingStatus)
    }

    @Test
    fun `charging status tracks each device and updates device information`() {
        // Act
        repository.batteryChargingStatusReceived("one", ChargeState.CHARGING)
        repository.batteryChargingStatusReceived("two", ChargeState.DISCHARGING_ACTIVE)

        // Assert
        assertEquals(mapOf("one" to ChargeState.CHARGING, "two" to ChargeState.DISCHARGING_ACTIVE), repository.deviceChargingStates.value)
        assertEquals("two", repository.deviceInformation.value.identifier)
        assertEquals(ChargeState.DISCHARGING_ACTIVE, repository.deviceInformation.value.batteryChargingStatus)

        repository.batteryChargingStatusReceived("one", ChargeState.UNKNOWN)
        assertEquals(mapOf("one" to ChargeState.UNKNOWN, "two" to ChargeState.DISCHARGING_ACTIVE), repository.deviceChargingStates.value)
        assertEquals("one", repository.deviceInformation.value.identifier)
        assertEquals(ChargeState.UNKNOWN, repository.deviceInformation.value.batteryChargingStatus)
    }

    @Test
    fun `disconnect callbacks remove only the disconnected device charging state`() {
        // Arrange
        repository.batteryChargingStatusReceived("one", ChargeState.CHARGING)
        repository.batteryChargingStatusReceived("two", ChargeState.CHARGING)

        // Act and assert
        @Suppress("DEPRECATION")
        repository.deviceDisconnected(deviceInfo("one"))
        assertEquals(mapOf("two" to ChargeState.CHARGING), repository.deviceChargingStates.value)
        repository.deviceDisconnected(deviceInfo("two"), disconnectInfo(PolarBleRecoveryAction.NONE))
        assertTrue(repository.deviceChargingStates.value.isEmpty())
    }

    @Test
    fun `is sdk mode enabled fails when feature is not available`() = runBlocking {
        // Act and Assert
        assertTrue(repository.isSdkModeEnabled(id) is ResultOfRequest.Failure)
    }

    @Test
    fun `is sdk mode enabled updates state or fails`() = runBlocking {
        // Arrange
        connect(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE)
        coEvery { api.isSDKModeEnabled(id) } returns true andThenThrows IllegalStateException("boom")

        // Act
        val ok = repository.isSdkModeEnabled(id)
        val enabledState = repository.sdkModeState.value.sdkModeState
        val failed = repository.isSdkModeEnabled(id)

        // Assert
        assertEquals(true, (ok as ResultOfRequest.Success).value)
        assertEquals(SdkMode.STATE.ENABLED, enabledState)
        assertTrue(failed is ResultOfRequest.Failure)
    }

    @Test
    fun `sdk mode toggle enables and disables mode`() {
        // Arrange
        connect(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE)

        // Act
        repository.sdkModeToggle(id)
        await { repository.sdkModeState.value.sdkModeState == SdkMode.STATE.ENABLED }
        repository.sdkModeToggle(id)
        await { repository.sdkModeState.value.sdkModeState == SdkMode.STATE.DISABLED }

        // Assert
        coVerify { api.enableSDKMode(id) }
        coVerify { api.disableSDKMode(id) }
    }

    @Test
    fun `sdk mode toggle reverts state when api fails`() {
        // Arrange
        connect(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE)
        coEvery { api.enableSDKMode(id) } throws IllegalStateException("boom")

        // Act
        repository.sdkModeToggle(id)

        // Assert
        coVerify(timeout = 5_000) { api.enableSDKMode(id) }
        await { repository.sdkModeState.value.sdkModeState == SdkMode.STATE.DISABLED }
    }

    @Test
    fun `sdk mode toggle does nothing when sdk mode is not available`() {
        // Act
        repository.sdkModeToggle(id)

        // Assert
        coVerify(exactly = 0) { api.enableSDKMode(any()) }
    }

    @Test
    fun `led config toggles sdk and ppi animation`() {
        // Act
        repository.setSdkModeLedConfig(id)
        await { repository.sdkModeState.value.sdkModeLedAnimation == SdkMode.STATE.DISABLED }
        repository.setPpiModeLedConfig(id)
        await { repository.sdkModeState.value.ppiModeLedAnimation == SdkMode.STATE.DISABLED }

        // Assert
        coVerify(exactly = 2) { api.setLedConfig(id, any()) }
    }

    @Test
    fun `led config keeps state when api fails`() {
        // Arrange
        coEvery { api.setLedConfig(any(), any()) } throws IllegalStateException("boom")

        // Act
        repository.setSdkModeLedConfig(id)
        repository.setPpiModeLedConfig(id)
        Thread.sleep(200)

        // Assert
        assertEquals(SdkMode.STATE.ENABLED, repository.sdkModeState.value.sdkModeLedAnimation)
        assertEquals(SdkMode.STATE.ENABLED, repository.sdkModeState.value.ppiModeLedAnimation)
    }

    @Test
    fun `lifecycle commands report success through callback`() {
        // Arrange
        val results = CopyOnWriteArrayList<Result<Unit>>()
        val callback: (Result<Unit>) -> Unit = { results.add(it) }

        // Act
        repository.doRestart(id, callback)
        repository.doFactoryReset(id, true, callback)
        repository.setWarehouseSleep(id, callback)
        repository.setHibernateMode(id, callback)
        repository.turnDeviceOff(id, callback)
        await { results.size == 5 }

        // Assert
        assertTrue(results.all { it.isSuccess })
        coVerify { api.doFactoryReset(id, true) }
    }

    @Test
    fun `lifecycle commands report failure through callback`() {
        // Arrange
        coEvery { api.doRestart(id) } throws IllegalStateException("restart failed")
        val result = AtomicReference<Result<Unit>>()

        // Act
        repository.doRestart(id) { result.set(it) }
        await { result.get() != null }

        // Assert
        assertEquals("restart failed", result.get().exceptionOrNull()?.message)
    }

    @Test
    fun `firmware checks delegate to api`() = runBlocking {
        // Arrange
        every { api.checkFirmwareUpdate(id) } returns flowOf()

        // Act and Assert
        assertTrue(repository.checkFirmwareUpdate(id).toList().isEmpty())
    }

    @Test
    fun `stream settings delegate to api`() = runBlocking {
        // Arrange
        val setting = mockk<PolarSensorSetting>()
        coEvery { api.requestStreamSettings(id, PolarBleApi.PolarDeviceDataType.ACC) } returns setting
        coEvery { api.requestFullStreamSettings(id, PolarBleApi.PolarDeviceDataType.ACC) } returns setting
        coEvery { api.requestOfflineRecordingSettings(id, PolarBleApi.PolarDeviceDataType.ACC) } returns setting
        coEvery { api.requestFullOfflineRecordingSettings(id, PolarBleApi.PolarDeviceDataType.ACC) } returns setting

        // Act and Assert
        assertEquals(setting, repository.getAvailableStreamSettings(id, PolarBleApi.PolarDeviceDataType.ACC))
        assertEquals(setting, repository.requestFullStreamSettings(id, PolarBleApi.PolarDeviceDataType.ACC))
        assertEquals(setting, repository.getOfflineRecSettings(id, PolarBleApi.PolarDeviceDataType.ACC))
        assertEquals(setting, repository.getFullOfflineRecSettings(id, PolarBleApi.PolarDeviceDataType.ACC))
    }

    @Test
    fun `offline recording start and stop return results`() = runBlocking {
        // Arrange
        coEvery { api.startOfflineRecording(id, PolarBleApi.PolarDeviceDataType.ECG, any(), any()) } returns Unit andThenThrows IllegalStateException("x")
        coEvery { api.stopOfflineRecording(id, PolarBleApi.PolarDeviceDataType.ECG) } returns Unit andThenThrows IllegalStateException("x")

        // Act and Assert
        assertTrue(repository.startOfflineRecording(id, PolarBleApi.PolarDeviceDataType.ECG) is ResultOfRequest.Success)
        assertTrue(repository.startOfflineRecording(id, PolarBleApi.PolarDeviceDataType.ECG) is ResultOfRequest.Failure)
        assertTrue(repository.stopOfflineRecording(id, PolarBleApi.PolarDeviceDataType.ECG) is ResultOfRequest.Success)
        assertTrue(repository.stopOfflineRecording(id, PolarBleApi.PolarDeviceDataType.ECG) is ResultOfRequest.Failure)
    }

    @Test
    fun `derived measurement calls return results`() {
        val settings = mockk<com.polar.sdk.api.model.PolarDerivedMeasurementSettings>()
        resultOf(
            { coEvery { api.requestDerivedMeasurementGroupIds(id, PolarBleApi.PolarDeviceDataType.ACC) } returns setOf(1) },
            { coEvery { api.requestDerivedMeasurementGroupIds(id, PolarBleApi.PolarDeviceDataType.ACC) } throws IllegalStateException("x") }
        ) { repository.requestDerivedMeasurementGroupIds(id, PolarBleApi.PolarDeviceDataType.ACC) }
        resultOf(
            { coEvery { api.requestDerivedMeasurementSettingsGroup(id, 1) } returns mockk() },
            { coEvery { api.requestDerivedMeasurementSettingsGroup(id, 1) } throws IllegalStateException("x") }
        ) { repository.requestDerivedMeasurementSettingsGroup(id, 1) }
        resultOf(
            { coEvery { api.startDerivedOfflineRecording(id, settings, any()) } returns Unit },
            { coEvery { api.startDerivedOfflineRecording(id, settings, any()) } throws IllegalStateException("x") }
        ) { repository.startDerivedOfflineRecording(id, settings) }
        resultOf(
            { coEvery { api.stopDerivedOfflineRecording(id) } returns Unit },
            { coEvery { api.stopDerivedOfflineRecording(id) } throws IllegalStateException("x") }
        ) { repository.stopDerivedOfflineRecording(id) }
    }

    @Test
    fun `offline recording status returns results`() {
        resultOf(
            { coEvery { api.getOfflineRecordingStatus(id) } returns listOf(PolarBleApi.PolarDeviceDataType.ECG) },
            { coEvery { api.getOfflineRecordingStatus(id) } throws IllegalStateException("x") }
        ) { repository.requestOfflineRecordingStatus(id) }
    }

    @Test
    fun `offline recording trigger updates state and reports failure`() = runBlocking {
        // Arrange
        val trigger = mockk<com.polar.sdk.api.model.PolarOfflineRecordingTrigger>()
        coEvery { api.getOfflineRecordingTriggerSetup(id) } returns trigger
        coEvery { api.setOfflineRecordingTrigger(id, trigger, any()) } returns Unit andThenThrows IllegalStateException("x")

        // Act
        val get = repository.getOfflineRecordingTriggerStatus(id)
        val setOk = repository.setOfflineRecordingTrigger(id, trigger)
        val setFailed = repository.setOfflineRecordingTrigger(id, trigger)

        // Assert
        assertTrue(get is ResultOfRequest.Success)
        assertTrue(setOk is ResultOfRequest.Success)
        assertTrue(setFailed is ResultOfRequest.Failure)
        assertEquals(id, repository.triggerState.value.identifier)
    }

    @Test
    fun `offline recording trigger status fails when api throws`() = runBlocking {
        // Arrange
        coEvery { api.getOfflineRecordingTriggerSetup(id) } throws IllegalStateException("x")

        // Act and Assert
        assertTrue(repository.getOfflineRecordingTriggerStatus(id) is ResultOfRequest.Failure)
    }

    @Test
    fun `security is stored and toggled`() = runBlocking {
        // Arrange
        every { security.hasKey(id) } returns true

        // Act
        val enabled = repository.isSecurityEnabled(id)
        repository.toggleSecurity(id, false)
        val afterDisable = repository.isOfflineRecordingSecurityEnabled.value
        repository.toggleSecurity(id, true)

        // Assert
        assertTrue(enabled)
        assertFalse(afterDisable)
        assertTrue(repository.isOfflineRecordingSecurityEnabled.value)
        verify { security.removeKey(id) }
        verify { security.generateKey(id) }
    }

    @Test
    fun `multi ble and sensor initiated modes return value or null`() = runBlocking {
        // Arrange
        coEvery { api.getMultiBLEConnectionMode(id) } returns true andThenThrows IllegalStateException("x")
        coEvery { api.getSensorInitiatedSecurityMode(id) } returns true andThenThrows IllegalStateException("x")

        // Act and Assert
        assertEquals(true, repository.getMultiBleModeEnabled(id))
        assertTrue(repository.isMultiBleModeEnabled.value)
        assertNull(repository.getMultiBleModeEnabled(id))
        assertEquals(true, repository.getSensorInitiatedSecurityModeEnabled(id))
        assertTrue(repository.isSensorInitiatedSecurityModeEnabled.value)
        assertNull(repository.getSensorInitiatedSecurityModeEnabled(id))
    }

    @Test
    fun `log config returns results`() {
        resultOf(
            { coEvery { api.getLogConfig(id) } returns mockk() },
            { coEvery { api.getLogConfig(id) } throws IllegalStateException("x") }
        ) { repository.getLogConfig(id) }
        resultOf(
            { coEvery { api.setLogConfig(id, any()) } returns Unit },
            { coEvery { api.setLogConfig(id, any()) } throws IllegalStateException("x") }
        ) { repository.setLogConfig(id, mockk()) }
    }

    @Test
    fun `sleep calls return results`() = runBlocking {
        // Arrange
        every { api.observeSleepRecordingStatus(id) } returns flowOf(arrayOf(PolarSleepRecordingStatus.ENABLED, PolarSleepRecordingStatus.DISABLED))
        coEvery { api.getSleepRecordingStatus(id) } returns PolarSleepRecordingStatus.ENABLED andThen PolarSleepRecordingStatus.DISABLED
        coEvery { api.getSleepRecordingStatus(id, 5000) } returns PolarSleepRecordingStatus.ENABLED

        // Act
        val observed = repository.observeSleepRecordingStatus(id).first()
        val stillRecording = repository.forceStopSleep(id)
        val stopped = repository.forceStopSleep(id)
        val status = repository.getSleepRecordingStatus(id)

        // Assert
        assertEquals(PolarSleepRecordingStatus.DISABLED, observed)
        assertTrue(stillRecording is ResultOfRequest.Failure)
        assertEquals(false, (stopped as ResultOfRequest.Success).value)
        assertEquals(PolarSleepRecordingStatus.ENABLED, (status as ResultOfRequest.Success).value)
    }

    @Test
    fun `sleep calls fail when api throws`() = runBlocking {
        // Arrange
        coEvery { api.stopSleepRecording(id) } throws IllegalStateException("x")
        coEvery { api.getSleepRecordingStatus(id, 5000) } throws IllegalStateException("x")

        // Act and Assert
        assertTrue(repository.forceStopSleep(id) is ResultOfRequest.Failure)
        assertTrue(repository.getSleepRecordingStatus(id) is ResultOfRequest.Failure)
    }

    @Test
    fun `activity data calls return results`() {
        resultOf({ coEvery { api.getSleep(id, from, to) } returns emptyList() }, { coEvery { api.getSleep(id, from, to) } throws IllegalStateException("x") }) { repository.getSleepData(id, from, to) }
        resultOf({ coEvery { api.getSteps(id, from, to) } returns emptyList() }, { coEvery { api.getSteps(id, from, to) } throws IllegalStateException("x") }) { repository.getStepsData(id, from, to) }
        resultOf({ coEvery { api.getCalories(id, from, to, any()) } returns emptyList() }, { coEvery { api.getCalories(id, from, to, any()) } throws IllegalStateException("x") }) {
            repository.getCaloriesData(id, from, to, com.polar.sdk.impl.utils.CaloriesType.ACTIVITY)
        }
        resultOf({ coEvery { api.get247HrSamples(id, from, to) } returns emptyList() }, { coEvery { api.get247HrSamples(id, from, to) } throws IllegalStateException("x") }) { repository.get247HrSamplesData(id, from, to) }
        resultOf({ coEvery { api.getNightlyRecharge(id, from, to) } returns emptyList() }, { coEvery { api.getNightlyRecharge(id, from, to) } throws IllegalStateException("x") }) { repository.getNightlyRechargeData(id, from, to) }
        resultOf({ coEvery { api.get247PPiSamples(id, from, to) } returns emptyList() }, { coEvery { api.get247PPiSamples(id, from, to) } throws IllegalStateException("x") }) { repository.get247PPiSamples(id, from, to) }
        resultOf({ coEvery { api.getSkinTemperature(id, from, to) } returns emptyList() }, { coEvery { api.getSkinTemperature(id, from, to) } throws IllegalStateException("x") }) { repository.getSkinTemperatureData(id, from, to) }
        resultOf({ coEvery { api.getActiveTime(id, from, to) } returns emptyList() }, { coEvery { api.getActiveTime(id, from, to) } throws IllegalStateException("x") }) { repository.getActiveTimeData(id, from, to) }
        resultOf({ coEvery { api.getActivitySampleData(id, from, to) } returns emptyList() }, { coEvery { api.getActivitySampleData(id, from, to) } throws IllegalStateException("x") }) { repository.getActivitySamplesData(id, from, to) }
        resultOf({ coEvery { api.getDailySummaryData(id, from, to) } returns emptyList() }, { coEvery { api.getDailySummaryData(id, from, to) } throws IllegalStateException("x") }) { repository.getDailySummaryData(id, from, to) }
        resultOf({ coEvery { api.getSpo2TestData(id, from, to) } returns emptyList() }, { coEvery { api.getSpo2TestData(id, from, to) } throws IllegalStateException("x") }) { repository.getSpo2TestData(id, from, to) }
    }

    @Test
    fun `device storage calls return results`() {
        resultOf({ coEvery { api.getUserDeviceSettings(id) } returns mockk() }, { coEvery { api.getUserDeviceSettings(id) } throws IllegalStateException("x") }) { repository.getDeviceUserSettings(id) }
        resultOf({ coEvery { api.deleteStoredDeviceData(id, any(), from) } returns Unit }, { coEvery { api.deleteStoredDeviceData(id, any(), from) } throws IllegalStateException("x") }) {
            repository.deleteDeviceData(id, PolarBleApi.PolarStoredDataType.values().first(), from)
        }
        resultOf({ coEvery { api.deleteDeviceDateFolders(id, from, to) } returns Unit }, { coEvery { api.deleteDeviceDateFolders(id, from, to) } throws IllegalStateException("x") }) { repository.deleteDeviceDateFolders(id, from, to) }
        resultOf({ coEvery { api.deleteTelemetryData(id) } returns Unit }, { coEvery { api.deleteTelemetryData(id) } throws IllegalStateException("x") }) { repository.deleteTelemetryData(id) }
        resultOf({ coEvery { api.getDiskSpace(id) } returns PolarDiskSpaceData(2, 1) }, { coEvery { api.getDiskSpace(id) } throws IllegalStateException("x") }) { repository.getDiskSpace(id) }
        resultOf({ coEvery { api.getUserPhysicalConfiguration(id) } returns null }, { coEvery { api.getUserPhysicalConfiguration(id) } throws IllegalStateException("x") }) { repository.getUserPhysicalConfiguration(id) }
        resultOf({ coEvery { api.getWatchFaceConfig(id) } returns mockk() }, { coEvery { api.getWatchFaceConfig(id) } throws IllegalStateException("x") }) { repository.getWatchFaceConfig(id) }
        resultOf({ coEvery { api.setWatchFaceConfig(id, any()) } returns Unit }, { coEvery { api.setWatchFaceConfig(id, any()) } throws IllegalStateException("x") }) { repository.setWatchFaceConfig(id, mockk()) }
        resultOf({ coEvery { api.exportDeviceLogs(id) } returns emptyList() }, { coEvery { api.exportDeviceLogs(id) } throws IllegalStateException("x") }) { repository.exportDeviceLogs(id) }
        resultOf({ coEvery { api.getRSSIValue(id) } returns -50 }, { coEvery { api.getRSSIValue(id) } throws IllegalStateException("x") }) { repository.getBleSignalStrength(id) }
        resultOf({ coEvery { api.checkIfDeviceDisconnectedDueRemovedPairing(id) } returns Pair(false, -1) }, { coEvery { api.checkIfDeviceDisconnectedDueRemovedPairing(id) } throws IllegalStateException("x") }) {
            repository.checkIfDeviceDisconnectedDueRemovedPairing(id)
        }
    }

    @Test
    fun `file calls return results`() {
        resultOf({ coEvery { api.readFile(id, "/a") } returns ByteArray(1) }, { coEvery { api.readFile(id, "/a") } throws IllegalStateException("x") }) { repository.readFile(id, "/a") }
        resultOf({ coEvery { api.getFileList(id, "/a", true) } returns listOf("f") }, { coEvery { api.getFileList(id, "/a", true) } throws IllegalStateException("x") }) { repository.listFiles(id, "/a", true) }
        resultOf({ coEvery { api.writeFile(id, "/a", any()) } returns Unit }, { coEvery { api.writeFile(id, "/a", any()) } throws IllegalStateException("x") }) { repository.writeFile(id, "/a", ByteArray(1)) }
        resultOf({ coEvery { api.deleteFileOrDirectory(id, "/a") } returns Unit }, { coEvery { api.deleteFileOrDirectory(id, "/a") } throws IllegalStateException("x") }) { repository.deleteFile(id, "/a") }
        resultOf({ coEvery { api.createFolder(id, "/a") } returns Unit }, { coEvery { api.createFolder(id, "/a") } throws IllegalStateException("x") }) { repository.createFolder(id, "/a") }
    }

    @Test
    fun `get charge information stores information`() = runBlocking {
        // Arrange
        coEvery { api.getChargerState(id) } returns ChargeState.CHARGING
        coEvery { api.getBatteryLevel(id) } returns 55

        // Act
        val result = repository.getChargeInformation(id)

        // Assert
        assertEquals(ChargeInformation(55, ChargeState.CHARGING), (result as ResultOfRequest.Success).value)
        assertEquals(55, repository.chargeInfo.batteryLevel)
    }

    @Test
    fun `get charge information fails when api throws`() = runBlocking {
        // Arrange
        coEvery { api.getChargerState(id) } throws IllegalStateException("x")

        // Act and Assert
        assertTrue(repository.getChargeInformation(id) is ResultOfRequest.Failure)
    }

    @Test
    fun `is offline exercise v 2 supported caches result and handles errors`() = runBlocking {
        // Arrange
        coEvery { api.isOfflineExerciseV2Supported(id) } returns true andThenThrows PolarOperationNotSupported() andThenThrows IllegalStateException("x")

        // Act and Assert
        assertTrue(repository.isOfflineExerciseV2Supported(id))
        assertEquals(true, repository.offlineExerciseV2Supported.value[id])
        assertFalse(repository.isOfflineExerciseV2Supported(id))
        assertFalse(repository.isOfflineExerciseV2Supported(id))
    }

    @Test
    fun `telemetry calls toggle enabled flag`() = runBlocking {
        // Arrange
        val type = PolarDeviceTelemetryType.memfault_mds
        coEvery { api.startTelemetry(id, type) } returns flowOf()

        // Act
        repository.startTelemetry(type, id)
        repository.stopTelemetry(type, id)
        repository.getDeviceTelemetryConfiguration(type, id)

        // Assert
        assertTrue(repository.isTelemetryEnabled.value)
        coVerify { api.stopTelemetry(id, type) }
    }

    @Test
    fun `training sessions are cached and deleted`() = runBlocking {
        // Arrange
        val reference = mockk<PolarTrainingSessionReference>()
        every { reference.path } returns "/s1"
        every { api.getTrainingSessionReferences(id, from, to) } returns flowOf(reference)
        coEvery { api.deleteTrainingSession(id, reference) } returns Unit

        // Act
        val listed = repository.getTrainingSessionReferences(id, from, to).toList()
        val missing = repository.deleteTrainingSession(id, "/none")
        val deleted = repository.deleteTrainingSession(id, "/s1")
        val afterDelete = repository.deleteTrainingSession(id, "/s1")

        // Assert
        assertEquals(listOf(reference), listed)
        assertTrue(missing is ResultOfRequest.Failure)
        assertTrue(deleted is ResultOfRequest.Success)
        assertTrue(afterDelete is ResultOfRequest.Failure)
    }

    @Test
    fun `training session with progress fails for unknown path`() = runBlocking {
        // Act
        val results = repository.getTrainingSessionWithProgress(id, "/none").toList()

        // Assert
        assertTrue(results.single() is ResultOfRequest.Failure)
    }

    @Test
    fun `training session with progress wraps results and errors`() = runBlocking {
        // Arrange
        val reference = mockk<PolarTrainingSessionReference>()
        every { reference.path } returns "/s1"
        every { api.getTrainingSessionReferences(id, from, to) } returns flowOf(reference)
        repository.getTrainingSessionReferences(id, from, to).toList()
        every { api.getTrainingSessionWithProgress(id, reference) } returns flow { throw IllegalStateException("x") }

        // Act
        val results = repository.getTrainingSessionWithProgress(id, "/s1").toList()

        // Assert
        assertTrue(results.single() is ResultOfRequest.Failure)
    }

    @Test
    fun `offline recordings are cached and deleted`() = runBlocking {
        // Arrange
        val entry = PolarOfflineRecordingEntry("/rec1", 10L, LocalDateTime.of(2024, 1, 1, 0, 0), PolarBleApi.PolarDeviceDataType.ECG)
        every { api.listOfflineRecordings(id) } returns flowOf(entry)
        coEvery { api.removeOfflineRecord(id, entry) } returns Unit

        // Act
        val listed = repository.listOfflineRecordings(id).toList()
        val cached = repository.getOfflineEntryFromCache(id, "/rec1")
        val missing = repository.deleteRecording(id, "/none")
        val deleted = repository.deleteRecording(id, "/rec1")

        // Assert
        assertEquals(listOf(entry), listed)
        assertEquals(entry, cached)
        assertTrue(missing is ResultOfRequest.Failure)
        assertTrue(deleted is ResultOfRequest.Success)
        assertNull(repository.getOfflineEntryFromCache(id, "/rec1"))
    }

    @Test
    fun `delete recording fails when api throws`() = runBlocking {
        // Arrange
        val entry = PolarOfflineRecordingEntry("/rec1", 10L, LocalDateTime.of(2024, 1, 1, 0, 0), PolarBleApi.PolarDeviceDataType.ECG)
        every { api.listOfflineRecordings(id) } returns flowOf(entry)
        coEvery { api.removeOfflineRecord(id, entry) } throws IllegalStateException("x")
        repository.listOfflineRecordings(id).toList()

        // Act and Assert
        assertTrue(repository.deleteRecording(id, "/rec1") is ResultOfRequest.Failure)
        assertEquals(entry, repository.getOfflineEntryFromCache(id, "/rec1"))
    }

    @Test
    fun `offline recording with progress fails for unknown path`() = runBlocking {
        // Act
        val results = repository.getOfflineRecordingWithProgress(id, "/none").toList()

        // Assert
        assertTrue(results.single() is ResultOfRequest.Failure)
    }

    @Test
    fun `offline recording with progress emits progress and wraps errors`() = runBlocking {
        // Arrange
        val entry = PolarOfflineRecordingEntry("/rec1", 10L, LocalDateTime.of(2024, 1, 1, 0, 0), PolarBleApi.PolarDeviceDataType.ECG)
        every { api.listOfflineRecordings(id) } returns flowOf(entry)
        repository.listOfflineRecordings(id).toList()
        every { api.getOfflineRecordWithProgress(id, entry, any()) } returns flow {
            emit(PolarOfflineRecordingResult.Progress(5L, 10L, 50))
            throw IllegalStateException("x")
        }

        // Act
        val results = repository.getOfflineRecordingWithProgress(id, "/rec1").toList()

        // Assert
        val progress = (results[0] as ResultOfRequest.Success).progress
        assertEquals(50, progress?.progressPercent)
        assertTrue(results[1] is ResultOfRequest.Failure)
    }

    private fun downloadComplete(recording: PolarOfflineRecordingData): ResultOfRequest<OfflineRecordingData> = runBlocking {
        val entry = PolarOfflineRecordingEntry("/rec1", 2048L, LocalDateTime.of(2024, 1, 1, 0, 0), PolarBleApi.PolarDeviceDataType.ECG)
        every { api.listOfflineRecordings(id) } returns flowOf(entry)
        every { api.getDeviceName(id) } returns "Polar $id"
        repository.listOfflineRecordings(id).toList()
        every { api.getOfflineRecordWithProgress(id, entry, any()) } returns flowOf(PolarOfflineRecordingResult.Complete(recording))
        repository.getOfflineRecordingWithProgress(id, "/rec1").toList().last()
    }

    private val savedUri = mockk<android.net.Uri>(relaxed = true)

    private fun arrangeSavedUri() {
        every { collector.finalizeAllStreams() } returns arrayListOf(savedUri)
    }

    @Test
    fun `offline recording with progress saves acc data when download completes`() {
        // Arrange
        arrangeSavedUri()
        val recording = mockk<PolarOfflineRecordingData.AccOfflineRecording>(relaxed = true)
        every { recording.data.samples } returns listOf(mockk(relaxed = true), mockk(relaxed = true))

        // Act
        val result = downloadComplete(recording)

        // Assert
        val value = (result as ResultOfRequest.Success).value!!
        assertEquals(savedUri, value.uri)
        assertEquals(2048L, value.fileSize)
        verify { collector.startAccLog("Polar $id", startTime = any()) }
        verify(exactly = 2) { collector.logAcc(any(), any(), any(), any()) }
    }

    @Test
    fun `offline recording with progress saves gyro data when download completes`() {
        // Arrange
        arrangeSavedUri()
        val recording = mockk<PolarOfflineRecordingData.GyroOfflineRecording>(relaxed = true)
        every { recording.data.samples } returns listOf(mockk(relaxed = true))

        // Act
        val result = downloadComplete(recording)

        // Assert
        assertTrue(result is ResultOfRequest.Success)
        verify { collector.startGyroLog("Polar $id", startTime = any()) }
        verify(exactly = 1) { collector.logGyro(any(), any(), any(), any()) }
    }

    @Test
    fun `offline recording with progress saves magnetometer data when download completes`() {
        // Arrange
        arrangeSavedUri()
        val recording = mockk<PolarOfflineRecordingData.MagOfflineRecording>(relaxed = true)
        every { recording.data.samples } returns listOf(mockk(relaxed = true))

        // Act
        val result = downloadComplete(recording)

        // Assert
        assertTrue(result is ResultOfRequest.Success)
        verify { collector.startMagnetometerLog("Polar $id", startTime = any()) }
        verify(exactly = 1) { collector.logMagnetometer(any(), any(), any(), any()) }
    }

    @Test
    fun `offline recording with progress saves ppg data when download completes`() {
        // Arrange
        arrangeSavedUri()
        val recording = mockk<PolarOfflineRecordingData.PpgOfflineRecording>(relaxed = true)

        // Act
        val result = downloadComplete(recording)

        // Assert
        assertTrue(result is ResultOfRequest.Success)
        verify { collector.startPpgLog("Polar $id", startTime = any()) }
        verify { collector.logPpgData(any()) }
    }

    @Test
    fun `offline recording with progress saves ppi data when download completes`() {
        // Arrange
        arrangeSavedUri()
        val recording = mockk<PolarOfflineRecordingData.PpiOfflineRecording>(relaxed = true)
        every { recording.data.samples } returns listOf(mockk(relaxed = true))

        // Act
        val result = downloadComplete(recording)

        // Assert
        assertTrue(result is ResultOfRequest.Success)
        verify { collector.startPpiLog("Polar $id", startTime = any()) }
        verify(exactly = 1) { collector.logPpi(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `offline recording with progress saves hr data when download completes`() {
        // Arrange
        arrangeSavedUri()
        val recording = mockk<PolarOfflineRecordingData.HrOfflineRecording>(relaxed = true)
        every { recording.data.samples } returns listOf(mockk(relaxed = true))

        // Act
        val result = downloadComplete(recording)

        // Assert
        assertTrue(result is ResultOfRequest.Success)
        verify { collector.startHrLog("Polar $id", startTime = any()) }
        verify(exactly = 1) { collector.logHr(any(), any()) }
    }

    @Test
    fun `offline recording with progress saves temperature data when download completes`() {
        // Arrange
        arrangeSavedUri()
        val recording = mockk<PolarOfflineRecordingData.TemperatureOfflineRecording>(relaxed = true)
        every { recording.data.samples } returns listOf(mockk(relaxed = true))

        // Act
        val result = downloadComplete(recording)

        // Assert
        assertTrue(result is ResultOfRequest.Success)
        verify { collector.startTemperatureLog("Polar $id", startTime = any()) }
        verify(exactly = 1) { collector.logTemperature(any(), any()) }
    }

    @Test
    fun `offline recording with progress saves skin temperature data when download completes`() {
        // Arrange
        arrangeSavedUri()
        val recording = mockk<PolarOfflineRecordingData.SkinTemperatureOfflineRecording>(relaxed = true)
        every { recording.data.samples } returns listOf(mockk(relaxed = true))

        // Act
        val result = downloadComplete(recording)

        // Assert
        assertTrue(result is ResultOfRequest.Success)
        verify { collector.startSkinTemperatureLog("Polar $id", startTime = any()) }
        verify(exactly = 1) { collector.logSkinTemperature(any(), any()) }
    }

    @Test
    fun `offline recording with progress saves derived acc data when download completes`() {
        // Arrange
        arrangeSavedUri()
        val recording = mockk<PolarOfflineRecordingData.DerivedAccOfflineRecording>(relaxed = true)
        every { recording.data.samples } returns listOf(mockk(relaxed = true))

        // Act
        val result = downloadComplete(recording)

        // Assert
        assertTrue(result is ResultOfRequest.Success)
        verify { collector.startDerivedAccLog("Polar $id", startTime = any()) }
        verify(exactly = 1) { collector.logDerivedSample(any()) }
    }

    @Test
    fun `device commands delegate to api`() = runBlocking {
        // Arrange
        val time = java.time.ZonedDateTime.now()
        coEvery { api.getLocalTimeWithZone(id) } returns time
        coEvery { api.isFtuDone(id) } returns true
        coEvery { api.setLocalTime(id, any()) } returns Unit andThenThrows IllegalStateException("x")
        every { api.getDeviceName(id) } returns "H10 12345"
        every { api.isFeatureReady(id, PolarBleApi.PolarBleSdkFeature.FEATURE_HR) } returns true

        // Act and Assert
        assertEquals(time, repository.getTime(id))
        assertTrue(repository.getFtuInfo(id))
        assertTrue(repository.setTime(id, LocalDateTime.now()) is ResultOfRequest.Success)
        assertTrue(repository.setTime(id, LocalDateTime.now()) is ResultOfRequest.Failure)
        assertEquals("H10 12345", repository.getDeviceName(id))
        assertTrue(repository.isFeatureReady(id, PolarBleApi.PolarBleSdkFeature.FEATURE_HR))
    }

    @Test
    fun `suspend setters delegate to api`() = runBlocking {
        // Act
        repository.setBleMultiConnectionMode(id, true)
        repository.setSensorInitiatedSecurityMode(id, true)
        repository.setTelemetryEnabled(id, true)
        repository.setUserDeviceLocation(id, 2)
        repository.setUsbConnectionMode(id, true)
        repository.setDaylightSavingTime(id)
        repository.setAutomaticTrainingDetectionSettings(id, true, 1, 2)
        repository.setAutosFilesEnabled(id, true)
        repository.setLedConfig(id, true, false)
        repository.waitForConnection(id)
        repository.stopHrStreaming(id)
        repository.getUserDeviceSettings(id)

        // Assert
        coVerify { api.setMultiBLEConnectionMode(id, true) }
        coVerify { api.setSensorInitiatedSecurityMode(id, true) }
        coVerify { api.setTelemetryEnabled(id, true) }
        coVerify { api.setUserDeviceLocation(id, 2) }
        coVerify { api.setUsbConnectionMode(id, true) }
        coVerify { api.setDaylightSavingTime(id) }
        coVerify { api.setAutomaticTrainingDetectionSettings(id, true, 1, 2) }
        coVerify { api.setAutomaticOHRMeasurementEnabled(id, true) }
        coVerify { api.waitForConnection(id) }
        coVerify { api.stopHrStreaming(id) }
    }

    @Test
    fun `streaming calls delegate to api`() {
        // Arrange
        val setting = mockk<PolarSensorSetting>()
        every { api.startAccStreaming(id, setting) } returns flowOf()
        every { api.startHrStreaming(id) } returns flowOf()
        every { api.startPpiStreaming(id) } returns flowOf()

        // Act
        repository.startAccStreaming(id, setting)
        repository.startHrStreaming(id)
        repository.startPpiStream(id)

        // Assert
        verify { api.startAccStreaming(id, setting) }
        verify { api.startHrStreaming(id) }
        verify { api.startPpiStreaming(id) }
    }

    @Test
    fun `listen hr broadcasts filters excluded devices`() = runBlocking {
        // Arrange
        fun broadcast(deviceId: String): PolarHrBroadcastData {
            val data = mockk<PolarHrBroadcastData>(relaxed = true)
            every { data.polarDeviceInfo } returns deviceInfo(deviceId)
            return data
        }
        val kept = broadcast("b")
        every { api.startListenForPolarHrBroadcasts(null) } returns flowOf(broadcast("a"), kept)

        // Act
        val result = withTimeout(5_000) { repository.listenHrBroadcasts(setOf("a")).toList() }

        // Assert
        assertEquals(listOf(kept), result)
    }

    @Test
    fun `search for device delegates to api`() {
        // Arrange
        every { api.searchForDevice(withRequiredDeviceNamePrefix = "Polar") } returns flowOf()

        // Act
        repository.searchForDevice("Polar")

        // Assert
        verify { api.searchForDevice(withRequiredDeviceNamePrefix = "Polar") }
    }
}
