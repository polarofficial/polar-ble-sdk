// Copyright (c) 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.content.Context
import android.content.IntentFilter
import android.os.ParcelUuid
import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.api.ble.model.advertisement.BleAdvertisementContent
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.BDScanCallback
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.errors.PolarDeviceNotFound
import com.polar.sdk.api.errors.PolarServiceNotAvailable
import com.polar.sdk.api.errors.PolarTimeoutException
import com.polar.sdk.api.model.sleep.PolarSleepAnalysisResult
import com.polar.sdk.impl.utils.PolarServiceClientUtils
import com.polar.sdk.impl.utils.PolarSleepUtils
import com.polar.sdk.impl.utils.receiveRestApiEvents
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

internal class PolarSleepApiImplTest {

    private val deviceId = "A1B2C3"
    private val from = LocalDate.of(2024, 2, 1)
    private val to   = LocalDate.of(2024, 2, 3)
    private lateinit var context: Context

    @Before
    fun setUp() {
        mockkObject(PolarSleepUtils)
        mockkObject(BlePolarDeviceCapabilitiesUtility.Companion)
        every { BlePolarDeviceCapabilitiesUtility.isActivityDataSupported(any()) } returns true

        mockkStatic(ParcelUuid::class)
        every { ParcelUuid.fromString(any()) } returns mockk(relaxed = true)

        mockkConstructor(ScanFilter.Builder::class)
        every { anyConstructed<ScanFilter.Builder>().setServiceUuid(any()) } answers { self as ScanFilter.Builder }
        every { anyConstructed<ScanFilter.Builder>().setServiceUuid(null) } answers { self as ScanFilter.Builder }
        every { anyConstructed<ScanFilter.Builder>().setManufacturerData(any(), any()) } answers { self as ScanFilter.Builder }
        every { anyConstructed<ScanFilter.Builder>().build() } returns mockk(relaxed = true)

        val bluetoothAdapter = mockk<BluetoothAdapter>(relaxed = true)
        val bluetoothManager = mockk<BluetoothManager>(relaxed = true)
        every { bluetoothManager.adapter } returns bluetoothAdapter

        mockkConstructor(IntentFilter::class)
        every { anyConstructed<IntentFilter>().addAction(any()) } just runs

        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        every { context.getSystemService(Context.BLUETOOTH_SERVICE) } returns bluetoothManager
        every { context.registerReceiver(any(), any<IntentFilter>()) } returns null
    }

    @After
    fun tearDown() {
        unmockkStatic(ParcelUuid::class)
        unmockkConstructor(IntentFilter::class)
        unmockkConstructor(ScanFilter.Builder::class)
        unmockkConstructor(BDScanCallback::class)
        unmockkAll()
        BDBleApiImpl.clearInstance()
    }

    private fun mockBleConnection(deviceId: String): Pair<BlePsFtpClient, BleDeviceListener> {
        val client     = mockk<BlePsFtpClient>()
        val listener   = mockk<BleDeviceListener>()
        val session    = mockk<BleDeviceSession>()
        val sessions   = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()
        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() }    returns session
        every { session.advertisementContent }  returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType }       returns "Polar360"
        every { session.sessionState }          returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) } returns client
        every { client.isServiceDiscovered }    returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0)
        return Pair(client, listener)
    }

    private fun mockNoSession(): BleDeviceListener {
        val listener = mockk<BleDeviceListener>()
        val sessions = mockk<Set<BleDeviceSession>>()
        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns false
        return listener
    }

    private fun mockNoClient(deviceId: String): BleDeviceListener {
        val listener   = mockk<BleDeviceListener>()
        val session    = mockk<BleDeviceSession>()
        val sessions   = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()
        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() }    returns session
        every { session.advertisementContent }  returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType }       returns "Polar360"
        every { session.sessionState }          returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) } returns null
        every { session.fetchClient(any()) }    returns null
        return listener
    }

    private fun resetCapabilityUtilityState() {
        val initializedField = BlePolarDeviceCapabilitiesUtility::class.java.getDeclaredField("initialized")
        initializedField.isAccessible = true
        initializedField.setBoolean(null, false)
    }

    /**
     * Directly injects a [BlePolarDeviceCapabilitiesUtility] config via reflection so that
     * [deviceType] reports `activityDataSupported = true` without requiring an Android context.
     */
    private fun initCapabilityUtilityWithActivityDataSupport(deviceType: String) {
        val config = BlePolarDeviceCapabilitiesUtility.DeviceCapabilitiesConfig(
            version = "test",
            devices = mapOf(
                deviceType.lowercase() to BlePolarDeviceCapabilitiesUtility.DeviceCapabilities(
                    activityDataSupported = true
                )
            ),
            defaults = BlePolarDeviceCapabilitiesUtility.DefaultsSection(activityDataSupported = false)
        )
        val outerClass = BlePolarDeviceCapabilitiesUtility::class.java

        val initializedField = outerClass.getDeclaredField("initialized")
        initializedField.isAccessible = true
        initializedField.setBoolean(null, true)

        val configField = outerClass.getDeclaredField("config")
        configField.isAccessible = true
        configField.set(null, config)
    }

    /**
     * Sets up a mock session and PsFtp client for sleep recording tests.
     * Also initialises [BlePolarDeviceCapabilitiesUtility] so that [deviceType]
     * is reported as supporting activity data.
     */
    private fun mockSleepConnection(deviceId: String, deviceType: String = "ignite3"): Pair<BlePsFtpClient, BleDeviceSession> {
        val client = mockk<BlePsFtpClient>()
        val session = mockk<BleDeviceSession>()

        every { session.polarDeviceType } returns deviceType
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) } returns client
        every { client.isServiceDiscovered } returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0)

        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.fetchSession(deviceId, any()) } returns session
        every { PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, any()) } returns session

        initCapabilityUtilityWithActivityDataSupport(deviceType)

        return Pair(client, session)
    }

    @Test
    fun `getSleep returns entries only for dates with non-null sleepStartTime`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarSleepApiImpl(listener)
        val resultWithStart = mockk<PolarSleepAnalysisResult>()
        val resultNoStart   = mockk<PolarSleepAnalysisResult>()
        every { resultWithStart.sleepStartTime } returns mockk()
        every { resultNoStart.sleepStartTime }   returns null
        coEvery { PolarSleepUtils.readSleepDataFromDayDirectory(client, LocalDate.of(2024, 2, 1)) } returns resultWithStart
        coEvery { PolarSleepUtils.readSleepDataFromDayDirectory(client, LocalDate.of(2024, 2, 2)) } returns resultNoStart
        coEvery { PolarSleepUtils.readSleepDataFromDayDirectory(client, LocalDate.of(2024, 2, 3)) } returns resultWithStart
        val result = api.getSleep(deviceId, from, to)
        assertEquals(2, result.size)
    }

    @Test
    fun `getSleep skips dates that throw and continues`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarSleepApiImpl(listener)
        val goodResult = mockk<PolarSleepAnalysisResult>()
        every { goodResult.sleepStartTime } returns mockk()
        coEvery { PolarSleepUtils.readSleepDataFromDayDirectory(client, LocalDate.of(2024, 2, 1)) } throws RuntimeException("fail")
        coEvery { PolarSleepUtils.readSleepDataFromDayDirectory(client, LocalDate.of(2024, 2, 2)) } returns goodResult
        coEvery { PolarSleepUtils.readSleepDataFromDayDirectory(client, LocalDate.of(2024, 2, 3)) } throws RuntimeException("fail")
        val result = api.getSleep(deviceId, from, to)
        assertEquals(1, result.size)
    }

    @Test
    fun `getSleep returns empty list when all dates have no start time`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarSleepApiImpl(listener)
        val resultNoStart = mockk<PolarSleepAnalysisResult>()
        every { resultNoStart.sleepStartTime } returns null
        coEvery { PolarSleepUtils.readSleepDataFromDayDirectory(client, any()) } returns resultNoStart
        val result = api.getSleep(deviceId, from, to)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getSleep throws PolarDeviceNotFound when no session`() = runTest {
        val api = PolarSleepApiImpl(mockNoSession())
        try {
            api.getSleep(deviceId, from, to)
            assert(false) { "Expected PolarDeviceNotFound" }
        } catch (e: PolarDeviceNotFound) { /* expected */ }
    }

    @Test
    fun `getSleep throws PolarServiceNotAvailable when no client`() = runTest {
        val api = PolarSleepApiImpl(mockNoClient(deviceId))
        try {
            api.getSleep(deviceId, from, to)
            assert(false) { "Expected PolarServiceNotAvailable" }
        } catch (e: PolarServiceNotAvailable) { /* expected */ }
    }

    // ── getSleepRecordingState ──────────────────────────────────────────────

    @Test
    fun `getSleepRecordingState returns true when device reports sleep recording is active`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA))
        val (client, _) = mockSleepConnection(deviceId)

        mockkStatic("com.polar.sdk.impl.utils.PolarDeviceRestApiUtilsKt")
        every { client.write(any(), any()) } returns flowOf(1L)
        every { client.receiveRestApiEvents(any()) } returns
                flowOf(listOf("""{"sleep_recording_state":{"enabled":1}}"""))

        try {
            // Act
            val result = api.getSleepRecordingState(deviceId)

            // Assert
            Assert.assertTrue("Expected true when device reports sleep recording enabled=1", result)
        } finally {
            unmockkObject(PolarServiceClientUtils)
            unmockkStatic("com.polar.sdk.impl.utils.PolarDeviceRestApiUtilsKt")
            resetCapabilityUtilityState()
        }
    }

    @Test
    fun `getSleepRecordingState returns false when device reports sleep recording is not active`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA))
        val (client, _) = mockSleepConnection(deviceId)

        mockkStatic("com.polar.sdk.impl.utils.PolarDeviceRestApiUtilsKt")
        every { client.write(any(), any()) } returns flowOf(1L)
        every { client.receiveRestApiEvents(any()) } returns
                flowOf(listOf("""{"sleep_recording_state":{"enabled":0}}"""))

        try {
            // Act
            val result = api.getSleepRecordingState(deviceId)

            // Assert
            Assert.assertFalse("Expected false when device reports sleep recording enabled=0", result)
        } finally {
            unmockkObject(PolarServiceClientUtils)
            unmockkStatic("com.polar.sdk.impl.utils.PolarDeviceRestApiUtilsKt")
            resetCapabilityUtilityState()
        }
    }

    @Test
    fun `getSleepRecordingState throws PolarTimeoutException when timeout expires before response is received`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA))
        val (client, _) = mockSleepConnection(deviceId)

        mockkStatic("com.polar.sdk.impl.utils.PolarDeviceRestApiUtilsKt")
        every { client.write(any(), any()) } returns flowOf(1L)
        // Flow that suspends forever – timeout should cancel it and throw PolarTimeoutException
        every { client.receiveRestApiEvents(any()) } returns flow { delay(Long.MAX_VALUE) }

        try {
            // Act – use a short timeout so the virtual-time scheduler advances quickly
            Assert.assertThrows(PolarTimeoutException::class.java) {
                runBlocking { api.getSleepRecordingState(deviceId, timeoutMs = 100L) }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
            unmockkStatic("com.polar.sdk.impl.utils.PolarDeviceRestApiUtilsKt")
            resetCapabilityUtilityState()
        }
    }

    @Test
    fun `getSleepRecordingState throws PolarServiceNotAvailable when device session is not found`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA))

        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.fetchSession(deviceId, any()) } returns null

        try {
            // Act & Assert
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.getSleepRecordingState(deviceId) }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `getSleepRecordingState throws PolarServiceNotAvailable when device does not support activity data`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val deviceType = "h10"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA))

        // Set up session with a device type that has activityDataSupported = false
        val session = mockk<BleDeviceSession>()
        every { session.polarDeviceType } returns deviceType

        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.fetchSession(deviceId, any()) } returns session

        // Override the default setUp() stub so h10 reports no activity-data support
        every { BlePolarDeviceCapabilitiesUtility.isActivityDataSupported(deviceType) } returns false

        try {
            // Act & Assert
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.getSleepRecordingState(deviceId) }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `getSleepRecordingState returns promptly when subscription flow stays open after first event`() = runTest {
        // Simulate device: emits initial state then keeps the flow open indefinitely.
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA))
        val (client, _) = mockSleepConnection(deviceId)

        mockkStatic("com.polar.sdk.impl.utils.PolarDeviceRestApiUtilsKt")
        every { client.write(any(), any()) } returns flowOf(1L)
        // Emit the initial state (as a real device would ~10 ms after subscribe) then
        // keep the flow open forever — exactly the scenario that triggered the 8.1.0 hang.
        every { client.receiveRestApiEvents(any()) } returns flow {
            emit(listOf("""{"sleep_recording_state":{"enabled":1}}"""))
            delay(Long.MAX_VALUE) // subscription stays open on the device side
        }

        try {
            val result = api.getSleepRecordingState(deviceId)

            // Assert
            Assert.assertTrue(
                "getSleepRecordingState must return true from the first event even when the flow never terminates",
                result
            )
        } finally {
            unmockkObject(PolarServiceClientUtils)
            unmockkStatic("com.polar.sdk.impl.utils.PolarDeviceRestApiUtilsKt")
            resetCapabilityUtilityState()
        }
    }

    @Test
    fun `getSleepRecordingState returns value from first event even when flow emits multiple events`() = runTest {
        // Device sends several events; only the first should be used.
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA))
        val (client, _) = mockSleepConnection(deviceId)

        mockkStatic("com.polar.sdk.impl.utils.PolarDeviceRestApiUtilsKt")
        every { client.write(any(), any()) } returns flowOf(1L)
        every { client.receiveRestApiEvents(any()) } returns flow {
            emit(listOf("""{"sleep_recording_state":{"enabled":1}}"""))
            emit(listOf("""{"sleep_recording_state":{"enabled":0}}""")) // must NOT override first
            delay(Long.MAX_VALUE)
        }

        try {
            val result = api.getSleepRecordingState(deviceId)
            Assert.assertTrue(
                "getSleepRecordingState must return the value from the first (true) event",
                result
            )
        } finally {
            unmockkObject(PolarServiceClientUtils)
            unmockkStatic("com.polar.sdk.impl.utils.PolarDeviceRestApiUtilsKt")
            resetCapabilityUtilityState()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `getSleepRecordingState and observeSleepRecordingState work correctly when used concurrently`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA))
        val (client, _) = mockSleepConnection(deviceId)

        mockkStatic("com.polar.sdk.impl.utils.PolarDeviceRestApiUtilsKt")
        every { client.write(any(), any()) } returns flowOf(1L)

        // Single hot flow shared by both callers — mimics the real SharedFlow inside BlePsFtpClient.
        val eventFlow = MutableSharedFlow<List<String>>(extraBufferCapacity = 8)
        every { client.receiveRestApiEvents(any()) } returns eventFlow

        val observedStates = mutableListOf<Boolean>()
        val observeJob = launch {
            api.observeSleepRecordingState(deviceId).collect { states ->
                observedStates.addAll(states.toList())
            }
        }
        // Let observer's inner eventJob subscribe to eventFlow (no timeout pending → safe).
        advanceUntilIdle()

        // Start getSleepRecordingState. Use runCurrent() — not advanceUntilIdle() — so that
        // virtual time does NOT advance past the withTimeoutOrNull deadline before we emit.
        val getDeferred = async { api.getSleepRecordingState(deviceId) }
        runCurrent() // collectorJob subscribes to eventFlow

        // Both observer and get are now subscribed. Emit first event.
        eventFlow.emit(listOf("""{"sleep_recording_state":{"enabled":1}}"""))
        runCurrent() // both process event 1; getSleepRecordingState completes; observer gets true

        val result = getDeferred.await()
        Assert.assertTrue("getSleepRecordingState must return true from the first event", result)

        // Emit two more events — collectorJob is cancelled, so only observer should see them.
        eventFlow.emit(listOf("""{"sleep_recording_state":{"enabled":0}}"""))
        eventFlow.emit(listOf("""{"sleep_recording_state":{"enabled":1}}"""))
        runCurrent()

        Assert.assertEquals("Observer must have received all three events", 3, observedStates.size)
        Assert.assertEquals(listOf(true, false, true), observedStates)

        observeJob.cancel()
        try {
            unmockkObject(PolarServiceClientUtils)
            unmockkStatic("com.polar.sdk.impl.utils.PolarDeviceRestApiUtilsKt")
            resetCapabilityUtilityState()
        } catch (_: Exception) {}
    }
}
