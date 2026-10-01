// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.content.Context
import android.content.IntentFilter
import android.os.ParcelUuid
import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.api.ble.model.BleDeviceSession.DeviceSessionState
import com.polar.androidcommunications.api.ble.model.advertisement.BleAdvertisementContent
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.BDScanCallback
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarCompanionDeviceApi.PolarCompanionDeviceCallback
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
/**
 * Covers the Companion Device Manager wiring inside [BDBleApiImpl]: the opt-in feature flag
 * gating, automatic association/observation on [DeviceSessionState.SESSION_OPEN] and SDK init,
 * the public [PolarCompanionDeviceApi][com.polar.sdk.api.PolarCompanionDeviceApi] delegation, and
 * presence event forwarding to the app callback.
 */
class BDBleApiImplCompanionTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        BDBleApiImpl.clearInstance()
        Dispatchers.setMain(UnconfinedTestDispatcher())

        mockkStatic(ParcelUuid::class)
        every { ParcelUuid.fromString(any()) } returns mockk(relaxed = true)

        mockkConstructor(IntentFilter::class)
        every { anyConstructed<IntentFilter>().addAction(any()) } just runs

        mockkConstructor(ScanFilter.Builder::class)
        every { anyConstructed<ScanFilter.Builder>().setServiceUuid(any()) } answers { self as ScanFilter.Builder }
        every { anyConstructed<ScanFilter.Builder>().setServiceUuid(null) } answers { self as ScanFilter.Builder }
        every { anyConstructed<ScanFilter.Builder>().setManufacturerData(any(), any()) } answers { self as ScanFilter.Builder }
        every { anyConstructed<ScanFilter.Builder>().build() } returns mockk(relaxed = true)

        mockkConstructor(BDScanCallback::class)
        every { anyConstructed<BDScanCallback>().powerOn() } just runs
        every { anyConstructed<BDScanCallback>().powerOff() } just runs

        val bluetoothAdapter = mockk<BluetoothAdapter>(relaxed = true)
        val bluetoothManager = mockk<BluetoothManager>(relaxed = true)
        every { bluetoothManager.adapter } returns bluetoothAdapter

        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        every { context.getSystemService(Context.BLUETOOTH_SERVICE) } returns bluetoothManager
        every { context.registerReceiver(any(), any<IntentFilter>()) } returns null

        mockkConstructor(PolarCompanionDeviceApiImpl::class)
    }

    @After
    fun tearDown() {
        BDBleApiImpl.clearInstance()
        Dispatchers.resetMain()
        unmockkStatic(ParcelUuid::class)
        unmockkConstructor(IntentFilter::class)
        unmockkConstructor(ScanFilter.Builder::class)
        unmockkConstructor(BDScanCallback::class)
        unmockkConstructor(PolarCompanionDeviceApiImpl::class)
    }

    private fun mockConnectionListener(api: BDBleApiImpl): BleDeviceListener {
        val listener = mockk<BleDeviceListener>(relaxed = true)
        every { listener.monitorDeviceSessionState() } returns emptyFlow()
        api.javaClass.getDeclaredField("listener").also { it.isAccessible = true }.set(api, listener)
        return listener
    }

    private fun emptyFlow() = kotlinx.coroutines.flow.emptyFlow<androidx.core.util.Pair<BleDeviceSession, DeviceSessionState>>()

    private fun mockOpenSession(deviceId: String, address: String): BleDeviceSession {
        val session = mockk<BleDeviceSession>(relaxed = true)
        val content = BleAdvertisementContent().apply { processName("Polar 360 $deviceId") }
        every { session.advertisementContent } returns content
        every { session.polarDeviceId } returns deviceId
        every { session.address } returns address
        every { session.rssi } returns 0
        every { session.name } returns "Polar 360 $deviceId"
        every { session.polarDeviceType } returns "360"
        return session
    }

    @Test
    fun `init resumes presence observation when feature enabled`() {
        every { anyConstructed<PolarCompanionDeviceApiImpl>().resumeAllAssociatedDevicesPresenceObservation(any()) } just runs

        BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_COMPANION_DEVICE_MANAGEMENT))

        verify(exactly = 1) { anyConstructed<PolarCompanionDeviceApiImpl>().resumeAllAssociatedDevicesPresenceObservation(any()) }
    }

    @Test
    fun `init does not touch companion api when feature disabled`() {
        BDBleApiImpl.getInstance(context, emptySet())

        verify(exactly = 0) { anyConstructed<PolarCompanionDeviceApiImpl>().resumeAllAssociatedDevicesPresenceObservation(any()) }
    }

    @Test
    fun `openConnection requests association for advertising unassociated device when feature enabled`() = runTest {
        every { anyConstructed<PolarCompanionDeviceApiImpl>().resumeAllAssociatedDevicesPresenceObservation(any()) } just runs
        every { anyConstructed<PolarCompanionDeviceApiImpl>().isAssociated(any()) } returns false
        every { anyConstructed<PolarCompanionDeviceApiImpl>().ensureCompanionHandling(any(), any(), any()) } just runs
        every { anyConstructed<PolarCompanionDeviceApiImpl>().observePresenceIfAssociated(any(), any()) } just runs

        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_COMPANION_DEVICE_MANAGEMENT))
        api.javaClass.getDeclaredField("apiScope").also { it.isAccessible = true }.set(api, this)
        val session = mockOpenSession("12345678", "AA:BB:CC:00:00:01")
        every { session.isAdvertising(any(), any()) } returns true
        val listener = mockk<BleDeviceListener>(relaxed = true)
        every { listener.monitorDeviceSessionState() } returns flowOf(androidx.core.util.Pair(session, DeviceSessionState.SESSION_OPEN))
        api.javaClass.getDeclaredField("listener").also { it.isAccessible = true }.set(api, listener)

        api.javaClass.getDeclaredMethod("openConnection", BleDeviceSession::class.java)
            .also { it.isAccessible = true }.invoke(api, session)
        testScheduler.advanceUntilIdle()

        verify(exactly = 1) { anyConstructed<PolarCompanionDeviceApiImpl>().ensureCompanionHandling("12345678", any(), any()) }
        verify(exactly = 1) { anyConstructed<PolarCompanionDeviceApiImpl>().observePresenceIfAssociated("12345678", any()) }
    }

    @Test
    fun `openConnection defers GATT connect until companion association flow finishes`() = runTest {
        every { anyConstructed<PolarCompanionDeviceApiImpl>().resumeAllAssociatedDevicesPresenceObservation(any()) } just runs
        every { anyConstructed<PolarCompanionDeviceApiImpl>().isAssociated(any()) } returns false
        val onFinished = slot<() -> Unit>()
        every { anyConstructed<PolarCompanionDeviceApiImpl>().ensureCompanionHandling(any(), any(), capture(onFinished)) } just runs

        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_COMPANION_DEVICE_MANAGEMENT))
        api.javaClass.getDeclaredField("apiScope").also { it.isAccessible = true }.set(api, this)
        val session = mockOpenSession("12345678", "AA:BB:CC:00:00:01")
        every { session.isAdvertising(any(), any()) } returns true
        val listener = mockk<BleDeviceListener>(relaxed = true)
        every { listener.monitorDeviceSessionState() } returns emptyFlow()
        api.javaClass.getDeclaredField("listener").also { it.isAccessible = true }.set(api, listener)

        api.javaClass.getDeclaredMethod("openConnection", BleDeviceSession::class.java)
            .also { it.isAccessible = true }.invoke(api, session)
        testScheduler.runCurrent()

        verify(exactly = 0) { listener.openSessionDirect(session) }
        onFinished.captured.invoke()
        onFinished.captured.invoke()
        testScheduler.advanceUntilIdle()
        verify(exactly = 1) { listener.openSessionDirect(session) }
    }

    @Test
    fun `openConnection skips association when device is not advertising`() = runTest {
        every { anyConstructed<PolarCompanionDeviceApiImpl>().resumeAllAssociatedDevicesPresenceObservation(any()) } just runs
        every { anyConstructed<PolarCompanionDeviceApiImpl>().isAssociated(any()) } returns false
        every { anyConstructed<PolarCompanionDeviceApiImpl>().ensureCompanionHandling(any(), any(), any()) } just runs
        every { anyConstructed<PolarCompanionDeviceApiImpl>().observePresenceIfAssociated(any(), any()) } just runs

        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_COMPANION_DEVICE_MANAGEMENT))
        api.javaClass.getDeclaredField("apiScope").also { it.isAccessible = true }.set(api, this)
        val session = mockOpenSession("12345678", "AA:BB:CC:00:00:01")
        every { session.isAdvertising(any(), any()) } returns false
        val listener = mockk<BleDeviceListener>(relaxed = true)
        every { listener.monitorDeviceSessionState() } returns flowOf(androidx.core.util.Pair(session, DeviceSessionState.SESSION_OPEN))
        api.javaClass.getDeclaredField("listener").also { it.isAccessible = true }.set(api, listener)

        api.javaClass.getDeclaredMethod("openConnection", BleDeviceSession::class.java)
            .also { it.isAccessible = true }.invoke(api, session)
        testScheduler.advanceUntilIdle()

        verify(exactly = 0) { anyConstructed<PolarCompanionDeviceApiImpl>().ensureCompanionHandling(any(), any(), any()) }
    }

    @Test
    fun `SESSION_OPEN does not touch companion api when feature disabled`() = runTest {
        val api = BDBleApiImpl.getInstance(context, emptySet())
        api.javaClass.getDeclaredField("apiScope").also { it.isAccessible = true }.set(api, this)
        val session = mockOpenSession("12345678", "AA:BB:CC:00:00:01")
        val listener = mockk<BleDeviceListener>(relaxed = true)
        every { listener.monitorDeviceSessionState() } returns flowOf(androidx.core.util.Pair(session, DeviceSessionState.SESSION_OPEN))
        api.javaClass.getDeclaredField("listener").also { it.isAccessible = true }.set(api, listener)

        api.javaClass.getDeclaredMethod("openConnection", BleDeviceSession::class.java)
            .also { it.isAccessible = true }.invoke(api, session)
        testScheduler.advanceUntilIdle()

        verify(exactly = 0) { anyConstructed<PolarCompanionDeviceApiImpl>().ensureCompanionHandling(any(), any(), any()) }
    }

    @Test
    fun `companion API methods delegate to PolarCompanionDeviceApiImpl`() {
        every { anyConstructed<PolarCompanionDeviceApiImpl>().isCompanionDeviceManagerAvailable() } returns true
        every { anyConstructed<PolarCompanionDeviceApiImpl>().getAssociatedDeviceAddresses() } returns listOf("AA:BB:CC:DD:EE:FF")
        every { anyConstructed<PolarCompanionDeviceApiImpl>().isAssociated("12345678") } returns true
        every { anyConstructed<PolarCompanionDeviceApiImpl>().removeAssociation("12345678") } just runs
        every { anyConstructed<PolarCompanionDeviceApiImpl>().startObservingDevicePresence("12345678", any()) } returns true
        every { anyConstructed<PolarCompanionDeviceApiImpl>().stopObservingDevicePresence("12345678") } returns true

        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_COMPANION_DEVICE_MANAGEMENT))
        val cb = mockk<PolarCompanionDeviceCallback>(relaxed = true)

        assert(api.isCompanionDeviceManagerAvailable())
        assert(api.getAssociatedDeviceAddresses() == listOf("AA:BB:CC:DD:EE:FF"))
        assert(api.isAssociated("12345678"))
        api.removeAssociation("12345678")
        assert(api.startObservingDevicePresence("12345678", cb))
        assert(api.stopObservingDevicePresence("12345678"))

        verify(exactly = 1) { anyConstructed<PolarCompanionDeviceApiImpl>().removeAssociation("12345678") }
    }

    @Test
    fun `companion presence callbacks are forwarded to the app callback`() {
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_COMPANION_DEVICE_MANAGEMENT))
        val appeared = slot<String>()
        val disappeared = slot<String>()
        val appCallback = mockk<PolarBleApiCallback>(relaxed = true)
        api.setApiCallback(appCallback)

        val forwarderField = api.javaClass.getDeclaredField("companionDeviceEventForwarder").also { it.isAccessible = true }
        val forwarder = forwarderField.get(api) as PolarCompanionDeviceCallback
        forwarder.onDeviceAppeared("AA:BB:CC:DD:EE:FF")
        forwarder.onDeviceDisappeared("AA:BB:CC:DD:EE:FF")

        verify(exactly = 1) { appCallback.polarCompanionDeviceAppeared(capture(appeared)) }
        verify(exactly = 1) { appCallback.polarCompanionDeviceDisappeared(capture(disappeared)) }
        assert(appeared.captured == "AA:BB:CC:DD:EE:FF")
        assert(disappeared.captured == "AA:BB:CC:DD:EE:FF")
    }

    @Test
    fun `companion API methods are no-ops without the feature but instance is still usable`() {
        every { anyConstructed<PolarCompanionDeviceApiImpl>().isCompanionDeviceManagerAvailable() } returns false

        val api = BDBleApiImpl.getInstance(context, emptySet())

        assertFalse(api.isCompanionDeviceManagerAvailable())
    }
}










