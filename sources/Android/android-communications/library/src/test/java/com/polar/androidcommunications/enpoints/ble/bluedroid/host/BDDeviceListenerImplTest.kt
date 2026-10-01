package com.polar.androidcommunications.enpoints.ble.bluedroid.host

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanFilter
import android.content.Context
import android.content.IntentFilter
import android.os.Looper
import android.os.ParcelUuid
import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.exceptions.BleInvalidMtu
import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.common.ble.AtomicSet
import com.polar.androidcommunications.common.ble.BleUtils
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection.ConnectionHandler
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class BDDeviceListenerImplTest {

    @Before
    fun setUp() {
        mockkStatic(ParcelUuid::class)
        every { ParcelUuid.fromString(any()) } returns mockk(relaxed = true)

        mockkConstructor(IntentFilter::class)
        every { anyConstructed<IntentFilter>().addAction(any()) } just runs

        mockkConstructor(ScanFilter.Builder::class)
        every { anyConstructed<ScanFilter.Builder>().setServiceUuid(any()) } returns mockk(relaxed = true)
        every { anyConstructed<ScanFilter.Builder>().setManufacturerData(any(), any()) } returns mockk(relaxed = true)
        every { anyConstructed<ScanFilter.Builder>().build() } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun search_whenFetchKnownDevicesFalse_registersAndUnregistersClientWithoutEmissions() = runTest {
        // Arrange
        val scanCallback = mockk<BDScanCallback>(relaxed = true)
        val sut = createSut(emptyList(), emptySet(), scanCallback)

        // Act
        val values = mutableListOf<BleDeviceSession>()
        var caughtError: Throwable? = null
        val job = launch {
            try { sut.search(false).collect { values.add(it) } }
            catch (e: Throwable) { caughtError = e }
        }
        testScheduler.advanceUntilIdle()

        // Assert
        assertEquals(0, values.size)
        assertEquals(null, caughtError)
        verify(exactly = 1) { scanCallback.clientAdded() }

        // Act
        job.cancel()
        testScheduler.advanceUntilIdle()

        // Assert
        verify(exactly = 1) { scanCallback.clientRemoved() }
    }

    @Test
    fun search_whenFetchKnownDevicesTrue_emitsLeKnownDevicesAndRegistersClient() = runTest {
        // Arrange
        val connectedLe = mockBluetoothDevice("AA:BB:CC:00:00:01", BluetoothDevice.DEVICE_TYPE_LE)
        val connectedClassic = mockBluetoothDevice("AA:BB:CC:00:00:02", BluetoothDevice.DEVICE_TYPE_CLASSIC)
        val bondedLe = mockBluetoothDevice("AA:BB:CC:00:00:03", BluetoothDevice.DEVICE_TYPE_LE)
        val scanCallback = mockk<BDScanCallback>(relaxed = true)
        val sut = createSut(
            connectedDevices = listOf(connectedLe, connectedClassic),
            bondedDevices = setOf(bondedLe),
            scanCallback = scanCallback
        )

        // Act
        val values = mutableListOf<BleDeviceSession>()
        val job = launch { sut.search(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        // Assert
        assertEquals(2, values.size)
        assertTrue(values.all { it is BleDeviceSession })
        verify(exactly = 1) { scanCallback.clientAdded() }

        // Act
        job.cancel()
        testScheduler.advanceUntilIdle()

        // Assert
        verify(exactly = 1) { scanCallback.clientRemoved() }
    }

    @Test
    fun search_knownPolarDevices_restoresIdentityWithoutAdvertisementsAndDeduplicates() = runTest {
        val connected = mockBluetoothDevice("AA:BB:CC:00:00:04", BluetoothDevice.DEVICE_TYPE_LE)
        every { connected.name } returns "Polar 360 12345678"
        val bonded = mockBluetoothDevice("AA:BB:CC:00:00:05", BluetoothDevice.DEVICE_TYPE_DUAL)
        every { bonded.name } returns "Polar 360 87654321"
        val unnamed = mockBluetoothDevice("AA:BB:CC:00:00:06", BluetoothDevice.DEVICE_TYPE_LE)
        every { unnamed.name } returns null
        val sut = createSut(listOf(connected), setOf(connected, bonded, unnamed), mockk(relaxed = true))
        val values = mutableListOf<BleDeviceSession>()
        val job = launch { sut.search(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        assertEquals(3, values.size)
        val polarSession = values.first { it.address == connected.address }
        assertEquals("12345678", polarSession.polarDeviceId)
        assertEquals("360", polarSession.polarDeviceType)
        assertEquals("Polar 360 12345678", polarSession.name)
        assertEquals("87654321", values.first { it.address == bonded.address }.polarDeviceId)
        assertEquals("", values.first { it.address == unnamed.address }.polarDeviceId)
        assertTrue(values.all { it.advertisementContent.getAdvertisementData().isEmpty() })
        assertTrue(values.none { it.isAdvertising(30, TimeUnit.SECONDS) })
        assertTrue(values.all { it.rssi == -100 })
        job.cancel()
    }

    @Test
    fun openSessionDirect_whenAnotherAppIsConnected_usesDirectGattWithoutAdvertisement() = runTest {
        val device = mockBluetoothDevice("AA:BB:CC:00:00:07", BluetoothDevice.DEVICE_TYPE_LE)
        every { device.name } returns "Polar 360 12345678"
        val sut = createSut(listOf(device), emptySet(), mockk(relaxed = true))
        val handler = mockk<ConnectionHandler>(relaxed = true)
        setPrivateField(sut, "connectionHandler", handler)
        val job = launch { sut.search(true).collect {} }
        testScheduler.advanceUntilIdle()
        val session = sut.sessionByAddress(device.address) as BDDeviceSessionImpl
        assertEquals(false, session.isConnectableAdvertisement)

        sut.openSessionDirect(session)

        verify(exactly = 1) { handler.connectDeviceDirect(session, true) }
        verify(exactly = 0) { handler.connectDevice(any(), any()) }
        job.cancel()
    }

    @Test
    fun openSessionDirect_whenOnlyBonded_keepsAdvertisementBasedConnection() = runTest {
        val device = mockBluetoothDevice("AA:BB:CC:00:00:08", BluetoothDevice.DEVICE_TYPE_LE)
        every { device.bondState } returns BluetoothDevice.BOND_BONDED
        val sut = createSut(emptyList(), setOf(device), mockk(relaxed = true))
        val handler = mockk<ConnectionHandler>(relaxed = true)
        setPrivateField(sut, "connectionHandler", handler)
        val job = launch { sut.search(true).collect {} }
        testScheduler.advanceUntilIdle()
        val session = sut.sessionByAddress(device.address) as BDDeviceSessionImpl

        sut.openSessionDirect(session)

        verify(exactly = 1) { handler.connectDevice(session, true) }
        verify(exactly = 0) { handler.connectDeviceDirect(any(), any()) }
        job.cancel()
    }

    @Test
    fun openSessionDirect_whenUnbondedAndNotSystemConnected_keepsAdvertisementBasedConnection() {
        val sut = createSut(emptyList(), emptySet(), mockk(relaxed = true))
        val handler = mockk<ConnectionHandler>(relaxed = true)
        setPrivateField(sut, "connectionHandler", handler)
        val device = mockBluetoothDevice("AA:BB:CC:00:00:09", BluetoothDevice.DEVICE_TYPE_LE)
        val scanInterface = getPrivateField(sut, "scanCallbackInterface") as BDScanCallback.BDScanCallbackInterface
        scanInterface.deviceDiscovered(device, -50, byteArrayOf(), BleUtils.EVENT_TYPE.ADV_IND)
        val session = sut.sessionByAddress(device.address) as BDDeviceSessionImpl

        sut.openSessionDirect(session)

        verify(exactly = 1) { handler.connectDevice(session, true) }
        verify(exactly = 0) { handler.connectDeviceDirect(any(), any()) }
    }

    @Test
    fun search_whenCollectorTakesFirstKnownDevice_releasesObserverAndScanClient() = runTest {
        val device = mockBluetoothDevice("AA:BB:CC:00:00:10", BluetoothDevice.DEVICE_TYPE_LE)
        val scanCallback = mockk<BDScanCallback>(relaxed = true)
        val sut = createSut(listOf(device), emptySet(), scanCallback)

        assertEquals(device.address, sut.search(true).first().address)

        assertEquals(0, (getPrivateField(sut, "observers") as AtomicSet<*>).size())
        verify(exactly = 1) { scanCallback.clientAdded() }
        verify(exactly = 1) { scanCallback.clientRemoved() }
    }

    @Test
    fun search_whenBluetoothQueryFails_releasesObserverAndPropagatesError() = runTest {
        val scanCallback = mockk<BDScanCallback>(relaxed = true)
        val sut = createSut(emptyList(), emptySet(), scanCallback)
        val manager = getPrivateField(sut, "btManager") as BluetoothManager
        val failure = SecurityException("Bluetooth permission revoked")
        every { manager.getDevicesMatchingConnectionStates(any(), any()) } throws failure

        val result = runCatching { sut.search(true).first() }

        assertEquals(failure, result.exceptionOrNull())
        assertEquals(0, (getPrivateField(sut, "observers") as AtomicSet<*>).size())
        verify(exactly = 1) { scanCallback.clientRemoved() }
    }

    @Test
    fun search_restoresMissingNameOnExistingSession_withoutOverwritingAdvertisedIdentity() = runTest {
        val device = mockBluetoothDevice("AA:BB:CC:00:00:11", BluetoothDevice.DEVICE_TYPE_LE)
        every { device.name } returns null
        val sut = createSut(listOf(device), emptySet(), mockk(relaxed = true))
        val session = sut.search(true).first()
        assertEquals("", session.name)

        every { device.name } returns "Polar 360 12345678"
        assertEquals(session, sut.search(true).first())
        assertEquals("12345678", session.polarDeviceId)
        assertTrue(session.advertisementContent.getAdvertisementData().isEmpty())

        session.advertisementContent.processAdvertisementData(
            mapOf(BleUtils.AD_TYPE.GAP_ADTYPE_LOCAL_NAME_COMPLETE to "Polar 360 87654321".toByteArray()),
            BleUtils.EVENT_TYPE.ADV_IND,
            -45
        )
        sut.search(true).first()
        assertEquals("87654321", session.polarDeviceId)
        assertEquals(-45, session.rssi)
        assertEquals(1, sut.deviceSessions().size)
    }

    @Test
    fun openSessionDirect_whenBluetoothOff_doesNotQueryConnectionState() = runTest {
        val device = mockBluetoothDevice("AA:BB:CC:00:00:12", BluetoothDevice.DEVICE_TYPE_LE)
        val sut = createSut(listOf(device), emptySet(), mockk(relaxed = true))
        val session = sut.search(true).first() as BDDeviceSessionImpl
        val handler = mockk<ConnectionHandler>(relaxed = true)
        setPrivateField(sut, "connectionHandler", handler)
        val adapter = getPrivateField(sut, "bluetoothAdapter") as BluetoothAdapter
        val manager = getPrivateField(sut, "btManager") as BluetoothManager
        every { adapter.isEnabled } returns false

        sut.openSessionDirect(session)

        verify(exactly = 1) { handler.connectDevice(session, false) }
        verify(exactly = 0) { handler.connectDeviceDirect(any(), any()) }
        verify(exactly = 0) { manager.getConnectionState(any(), any()) }
    }

    @Test
    fun setPreferredMtu_defaultValue_isPolarPreferredMtu() {
        val sut = createSut(emptyList(), emptySet(), mockk(relaxed = true))
        assertEquals(ConnectionHandler.POLAR_PREFERRED_MTU, sut.getPreferredMtu())
    }

    @Test
    fun setPreferredMtu_withValidPositiveMtu_storesMtu() {
        val sut = createSut(emptyList(), emptySet(), mockk(relaxed = true))
        sut.setPreferredMtu(128)
        assertEquals(128, sut.getPreferredMtu())
    }

    @Test
    fun setPreferredMtu_withZeroMtu_storesMtu() {
        val sut = createSut(emptyList(), emptySet(), mockk(relaxed = true))
        sut.setPreferredMtu(0)
        assertEquals(0, sut.getPreferredMtu())
    }

    @Test
    fun setPreferredMtu_withNegativeMtu_throwsBleInvalidMtu() {
        val sut = createSut(emptyList(), emptySet(), mockk(relaxed = true))
        assertThrows(BleInvalidMtu::class.java) { sut.setPreferredMtu(-1) }
    }

    @Test
    fun setPreferredMtu_withNegativeMtu_doesNotChangePreviousValue() {
        val sut = createSut(emptyList(), emptySet(), mockk(relaxed = true))
        sut.setPreferredMtu(256)
        try { sut.setPreferredMtu(-1) } catch (e: BleInvalidMtu) { /* expected */ }
        assertEquals(256, sut.getPreferredMtu())
    }

    @Test
    fun getPreferredMtu_whenListenerCreated_returnsDefaultPolarPreferredMtu() {
        val sut = createSut(emptyList(), emptySet(), mockk(relaxed = true))
        assertEquals(ConnectionHandler.POLAR_PREFERRED_MTU, sut.getPreferredMtu())
    }

    @Test
    fun getPreferredMtu_afterSettingPositiveValue_returnsThatValue() {
        val sut = createSut(emptyList(), emptySet(), mockk(relaxed = true))
        sut.setPreferredMtu(128)
        assertEquals(128, sut.getPreferredMtu())
    }

    @Test
    fun removeSession_whenClosedNotAdvertisingAndPresent_removesAndReturnsTrue() = runTest {
        // Arrange
        val knownAddress = "AA:BB:CC:00:00:11"
        val connectedLe = mockBluetoothDevice(knownAddress, BluetoothDevice.DEVICE_TYPE_LE)
        val scanCallback = mockk<BDScanCallback>(relaxed = true)
        val sut = createSut(listOf(connectedLe), emptySet(), scanCallback)

        val searchValues = mutableListOf<BleDeviceSession>()
        val searchJob = launch { sut.search(true).collect { searchValues.add(it) } }
        testScheduler.advanceUntilIdle()

        val session = sut.sessionByAddress(knownAddress)
        session.sessionState = BleDeviceSession.DeviceSessionState.SESSION_CLOSED

        // Act
        val removed = sut.removeSession(session)

        // Assert
        assertTrue(removed)
        assertEquals(false, sut.deviceSessions()?.contains(session) ?: false)
        searchJob.cancel()
    }

    @Test
    fun removeAllSessions_whenCalledWithoutStates_removesOnlyClosedSessions() = runTest {
        // Arrange
        val addressClosed = "AA:BB:CC:00:10:01"
        val addressOpen  = "AA:BB:CC:00:10:02"
        val scanCallback = mockk<BDScanCallback>(relaxed = true)
        val sut = createSut(
            connectedDevices = listOf(
                mockBluetoothDevice(addressClosed, BluetoothDevice.DEVICE_TYPE_LE),
                mockBluetoothDevice(addressOpen, BluetoothDevice.DEVICE_TYPE_LE)
            ),
            bondedDevices = emptySet(),
            scanCallback = scanCallback
        )

        val searchJob = launch { sut.search(true).collect { } }
        testScheduler.advanceUntilIdle()

        val closedSession = sut.sessionByAddress(addressClosed)
        val openSession   = sut.sessionByAddress(addressOpen)
        closedSession.sessionState = BleDeviceSession.DeviceSessionState.SESSION_CLOSED
        openSession.sessionState   = BleDeviceSession.DeviceSessionState.SESSION_OPEN

        // Act
        val removedCount = sut.removeAllSessions()

        // Assert
        assertEquals(1, removedCount)
        assertEquals(false, sut.deviceSessions()?.contains(closedSession) ?: false)
        assertEquals(true,  sut.deviceSessions()?.contains(openSession) ?: false)
        searchJob.cancel()
    }

    @Test
    fun setPowerMode_whenNormalMode_stopsScanDisablesLowPowerAndStartsScan() {
        val scanCallback = mockk<BDScanCallback>(relaxed = true)
        val sut = createSut(emptyList(), emptySet(), scanCallback)
        sut.setPowerMode(BleDeviceListener.POWER_MODE_NORMAL)
        verify(exactly = 1) { scanCallback.stopScan() }
        verify { scanCallback.lowPowerEnabled = false }
        verify(exactly = 1) { scanCallback.startScan() }
    }

    @Test
    fun setPowerMode_whenLowMode_stopsScanEnablesLowPowerAndStartsScan() {
        val scanCallback = mockk<BDScanCallback>(relaxed = true)
        val sut = createSut(emptyList(), emptySet(), scanCallback)
        sut.setPowerMode(BleDeviceListener.POWER_MODE_LOW)
        verify(exactly = 1) { scanCallback.stopScan() }
        verify { scanCallback.lowPowerEnabled = true }
        verify(exactly = 1) { scanCallback.startScan() }
    }

    @Test
    fun openSessionDirect_withoutUuids_connectsDevice() = runTest {
        val address = "AA:BB:CC:00:50:01"
        val device = mockBluetoothDevice(address, BluetoothDevice.DEVICE_TYPE_LE)
        val sut = createSut(listOf(device), emptySet(), mockk(relaxed = true))

        val searchJob = launch { sut.search(true).collect { } }
        testScheduler.advanceUntilIdle()
        val session = sut.sessionByAddress(address)

        sut.openSessionDirect(session)
        assertEquals(emptyList<String>(), session.connectionUuids)
        searchJob.cancel()
    }

    @Test
    fun openSessionDirect_withUuids_connectsDeviceWithUuids() = runTest {
        val address = "AA:BB:CC:00:50:02"
        val device = mockBluetoothDevice(address, BluetoothDevice.DEVICE_TYPE_LE)
        val sut = createSut(listOf(device), emptySet(), mockk(relaxed = true))

        val searchJob = launch { sut.search(true).collect { } }
        testScheduler.advanceUntilIdle()
        val session = sut.sessionByAddress(address)
        val uuids = listOf("00001800-0000-1000-8000-00805f9b34fb", "00001801-0000-1000-8000-00805f9b34fb")

        sut.openSessionDirect(session, uuids)
        assertEquals(uuids, session.connectionUuids)
        searchJob.cancel()
    }

    @Test
    fun closeSessionDirect_disconnectsDevice() = runTest {
        val address = "AA:BB:CC:00:50:03"
        val device = mockBluetoothDevice(address, BluetoothDevice.DEVICE_TYPE_LE)
        val sut = createSut(listOf(device), emptySet(), mockk(relaxed = true))

        val searchJob = launch { sut.search(true).collect { } }
        testScheduler.advanceUntilIdle()
        val session = sut.sessionByAddress(address)

        sut.closeSessionDirect(session)
        searchJob.cancel()
    }

    @Test
    fun monitorDeviceSessionState_emitsSessionStateChanges() = runTest {
        val address = "AA:BB:CC:00:60:01"
        val device = mockBluetoothDevice(address, BluetoothDevice.DEVICE_TYPE_LE)
        val sut = createSut(listOf(device), emptySet(), mockk(relaxed = true))

        val searchJob = launch { sut.search(true).collect { } }
        testScheduler.advanceUntilIdle()

        var stateError: Throwable? = null
        val stateJob = launch {
            try { sut.monitorDeviceSessionState().collect { } }
            catch (e: Throwable) { stateError = e }
        }
        testScheduler.advanceUntilIdle()

        assertEquals(null, stateError)
        stateJob.cancel()
        searchJob.cancel()
    }

    @Test
    fun bleActive_whenAdapterEnabledAndNotNull_returnsTrue() {
        val sut = createSut(emptyList(), emptySet(), mockk(relaxed = true))
        assertTrue(sut.bleActive())
    }

    @Test
    fun shutDown_stopsScansAndClearssessions() = runTest {
        val address = "AA:BB:CC:00:70:01"
        val device = mockBluetoothDevice(address, BluetoothDevice.DEVICE_TYPE_LE)
        val scanCallback = mockk<BDScanCallback>(relaxed = true)
        val sut = createSut(listOf(device), emptySet(), scanCallback)

        val searchJob = launch { sut.search(true).collect { } }
        testScheduler.advanceUntilIdle()
        searchJob.cancel()
        testScheduler.advanceUntilIdle()

        sut.shutDown()

        verify { scanCallback.stopScan() }
        assertEquals(0, sut.deviceSessions()!!.size)
    }

    @Test
    fun getAutomaticReconnection_whenCreated_returnsTrueByDefault() {
        val sut = createSut(emptyList(), emptySet(), mockk(relaxed = true))
        assertEquals(true, sut.getAutomaticReconnection())
    }

    @Test
    fun getAutomaticReconnection_afterDisabling_returnsFalse() {
        val sut = createSut(emptyList(), emptySet(), mockk(relaxed = true))
        sut.setAutomaticReconnection(false)
        assertEquals(false, sut.getAutomaticReconnection())
    }

    @Test
    fun getAutomaticReconnection_afterEnabling_returnsTrue() {
        val sut = createSut(emptyList(), emptySet(), mockk(relaxed = true))
        sut.setAutomaticReconnection(false)
        sut.setAutomaticReconnection(true)
        assertEquals(true, sut.getAutomaticReconnection())
    }

    @Test
    fun deviceDiscovered_whenNewDeviceWithValidAdvertisement_createsSessionAndEmits() = runTest {
        val address = "AA:BB:CC:00:80:01"
        val device = mockBluetoothDevice(address, BluetoothDevice.DEVICE_TYPE_LE)
        val sut = createSut(emptyList(), emptySet(), mockk(relaxed = true))

        val searchJob = launch { sut.search(true).collect { } }
        testScheduler.advanceUntilIdle()

        val scanCallbackInterface = getPrivateField(sut, "scanCallbackInterface") as BDScanCallback.BDScanCallbackInterface
        scanCallbackInterface.deviceDiscovered(device, -50, byteArrayOf(), BleUtils.EVENT_TYPE.ADV_IND)
        testScheduler.advanceUntilIdle()

        assertTrue((sut.deviceSessions()?.size ?: 0) > 0)
        searchJob.cancel()
    }

    @Test
    fun deviceDiscovered_whenDeviceWithPreFilter_appliesFilterBeforeCreating() = runTest {
        val address = "AA:BB:CC:00:80:02"
        val device = mockBluetoothDevice(address, BluetoothDevice.DEVICE_TYPE_LE)
        val sut = createSut(emptyList(), emptySet(), mockk(relaxed = true))
        sut.setScanPreFilter { false }

        val searchJob = launch { sut.search(true).collect { } }
        testScheduler.advanceUntilIdle()

        val scanCallbackInterface = getPrivateField(sut, "scanCallbackInterface") as BDScanCallback.BDScanCallbackInterface
        scanCallbackInterface.deviceDiscovered(device, -50, byteArrayOf(), BleUtils.EVENT_TYPE.ADV_IND)
        testScheduler.advanceUntilIdle()

        assertEquals(0, sut.deviceSessions()?.size ?: 0)
        searchJob.cancel()
    }

    @Test
    fun deviceDiscovered_whenDeviceAdvertisesMultipleTimes_updatesExistingSession() = runTest {
        val address = "AA:BB:CC:00:80:03"
        val device = mockBluetoothDevice(address, BluetoothDevice.DEVICE_TYPE_LE)
        val sut = createSut(emptyList(), emptySet(), mockk(relaxed = true))

        val searchJob = launch { sut.search(true).collect { } }
        testScheduler.advanceUntilIdle()

        val scanCallbackInterface = getPrivateField(sut, "scanCallbackInterface") as BDScanCallback.BDScanCallbackInterface

        scanCallbackInterface.deviceDiscovered(device, -50, byteArrayOf(), BleUtils.EVENT_TYPE.ADV_IND)
        testScheduler.advanceUntilIdle()
        val session1 = sut.deviceSessions()?.firstOrNull()
        val rssi1After = session1?.advertisementContent?.rssi

        scanCallbackInterface.deviceDiscovered(device, -30, byteArrayOf(), BleUtils.EVENT_TYPE.ADV_IND)
        testScheduler.advanceUntilIdle()
        val session2 = sut.deviceSessions()?.firstOrNull()
        val rssi2After = session2?.advertisementContent?.rssi

        assertEquals(1, sut.deviceSessions()?.size)
        assertEquals(session1, session2)
        assertEquals(-50, rssi1After)
        assertEquals(-30, rssi2After)
        searchJob.cancel()
    }

    @Test
    fun getIndicatesPairingProblem_whenNoSessionAndNoGattProblem_returnsFalseWithMinusOne() {
        // Arrange
        val sut = createSut(emptyList(), emptySet(), mockk(relaxed = true))
        val unknownIdentifier = "AA:BB:CC:00:FF:01"

        // Act
        val result = sut.getIndicatesPairingProblem(unknownIdentifier)

        // Assert
        assertEquals(false, result.first)
        assertEquals(-1, result.second)
    }

    @Test
    fun getIndicatesPairingProblem_whenSessionExistsButReportsNoProblem_andGattReportsNoProblem_returnsFallback() = runTest {
        // Arrange
        val address = "AA:BB:CC:00:FF:02"
        val device = mockBluetoothDevice(address, BluetoothDevice.DEVICE_TYPE_LE)
        val sut = createSut(listOf(device), emptySet(), mockk(relaxed = true))

        val searchJob = launch { sut.search(true).collect { } }
        testScheduler.advanceUntilIdle()

        // session.getIndicatesPairingProblem() returns Pair(false, -1) by default
        // gattCallback also defaults to Pair(false, -1)

        // Act
        val result = sut.getIndicatesPairingProblem(address)

        // Assert – falls through to the indicatesPairingProblem field default
        assertEquals(false, result.first)
        assertEquals(-1, result.second)
        searchJob.cancel()
    }

    @Test
    fun getIndicatesPairingProblem_whenSessionIndicatesPairingProblem_returnsSessionPair() = runTest {
        // Arrange
        val address = "AA:BB:CC:00:FF:03"
        val device = mockBluetoothDevice(address, BluetoothDevice.DEVICE_TYPE_LE)
        val sut = createSut(listOf(device), emptySet(), mockk(relaxed = true))

        val searchJob = launch { sut.search(true).collect { } }
        testScheduler.advanceUntilIdle()

        val session = sut.sessionByAddress(address)
        session.markDisconnect(
            BleDeviceSession.DisconnectReason.PAIRING_INFORMATION_REMOVED,
            133
        )

        // Act
        val result = sut.getIndicatesPairingProblem(address)

        // Assert – confirmed removed pairing is returned
        assertEquals(true, result.first)
        assertEquals(133, result.second)
        searchJob.cancel()
    }

    @Test
    fun getIndicatesPairingProblem_whenSessionReportsNoProblemButGattDoes_returnsGattPair() = runTest {
        // Arrange
        val address = "AA:BB:CC:00:FF:04"
        val device = mockBluetoothDevice(address, BluetoothDevice.DEVICE_TYPE_LE)
        val sut = createSut(listOf(device), emptySet(), mockk(relaxed = true))

        val searchJob = launch { sut.search(true).collect { } }
        testScheduler.advanceUntilIdle()

        // Inject a generic GATT failure into gattCallback via reflection
        val gattCallbackField = sut.javaClass.getDeclaredField("gattCallback")
            .also { it.isAccessible = true }
        val gattCallback = gattCallbackField.get(sut) as GattCallback
        val gattProblemField = gattCallback.javaClass.getDeclaredField("indicatesPairingProblem")
            .also { it.isAccessible = true }
        gattProblemField.set(gattCallback, Pair(true, 19))

        // Act
        val result = sut.getIndicatesPairingProblem(address)

        // Generic GATT failures are not confirmed pairing loss.
        assertEquals(false, result.first)
        assertEquals(-1, result.second)
        searchJob.cancel()
    }

    @Test
    fun getIndicatesPairingProblem_whenSessionIsNull_andGattReportsNoProblem_returnsFallbackDefault() {
        // Arrange – use an address that has no session in the list
        val sut = createSut(emptyList(), emptySet(), mockk(relaxed = true))
        val nonExistingAddress = "AA:BB:CC:00:FF:05"

        // Act
        val result = sut.getIndicatesPairingProblem(nonExistingAddress)

        // Assert
        assertEquals(false, result.first)
        assertEquals(-1, result.second)
    }

    @Test
    fun getIndicatesPairingProblem_whenSessionProblemIsFalseAndGattProblemIsTrue_doesNotReturnSessionPair() = runTest {
        // Arrange
        val address = "AA:BB:CC:00:FF:06"
        val device = mockBluetoothDevice(address, BluetoothDevice.DEVICE_TYPE_LE)
        val sut = createSut(listOf(device), emptySet(), mockk(relaxed = true))

        val searchJob = launch { sut.search(true).collect { } }
        testScheduler.advanceUntilIdle()

        // Session has no pairing problem; inject a generic GATT problem
        val gattCallbackField = sut.javaClass.getDeclaredField("gattCallback")
            .also { it.isAccessible = true }
        val gattCallback = gattCallbackField.get(sut) as GattCallback
        val gattProblemField = gattCallback.javaClass.getDeclaredField("indicatesPairingProblem")
            .also { it.isAccessible = true }
        gattProblemField.set(gattCallback, Pair(true, 8))

        // Act
        val result = sut.getIndicatesPairingProblem(address)

        // Generic GATT failures are not confirmed pairing loss.
        assertEquals(false, result.first)
        assertEquals(-1, result.second)
        searchJob.cancel()
    }

    private fun getPrivateField(target: Any, fieldName: String): Any? {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(target)
    }

    private fun createSut(
        connectedDevices: List<BluetoothDevice>,
        bondedDevices: Set<BluetoothDevice>,
        scanCallback: BDScanCallback
    ): BDDeviceListenerImpl {
        val context = mockk<Context>()
        val bluetoothManager = mockk<BluetoothManager>()
        val bluetoothAdapter = mockk<BluetoothAdapter>()

        every { context.getSystemService(Context.BLUETOOTH_SERVICE) } returns bluetoothManager
        every { context.registerReceiver(any(), any()) } returns null
        every { context.registerReceiver(any(), any(), any(), any()) } returns null
        every { context.unregisterReceiver(any()) } just runs
        every { context.mainLooper } returns mockk<Looper>(relaxed = true)
        every { bluetoothManager.adapter } returns bluetoothAdapter
        every { bluetoothAdapter.isEnabled } returns true
        every { bluetoothAdapter.bondedDevices } returns bondedDevices
        every { bluetoothManager.getConnectionState(any(), BluetoothProfile.GATT) } answers {
            if (connectedDevices.contains(firstArg<BluetoothDevice>())) BluetoothProfile.STATE_CONNECTED
            else BluetoothProfile.STATE_DISCONNECTED
        }

        every {
            bluetoothManager.getDevicesMatchingConnectionStates(
                BluetoothProfile.GATT,
                intArrayOf(BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_CONNECTING)
            )
        } returns connectedDevices

        every { scanCallback.clientAdded() } just runs
        every { scanCallback.clientRemoved() } just runs

        val sut = BDDeviceListenerImpl(context, mutableSetOf<Class<out BleGattBase>>())
        setPrivateField(sut, "scanCallback", scanCallback)
        setPrivateField(sut, "bluetoothAdapter", bluetoothAdapter)

        return sut
    }

    private fun setPrivateField(target: Any, fieldName: String, value: Any?) {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }

    private fun mockBluetoothDevice(address: String, type: Int): BluetoothDevice {
        val device = mockk<BluetoothDevice>()
        every { device.address } returns address
        every { device.type } returns type
        every { device.name } returns "MockDevice-$address"
        every { device.bondState } returns BluetoothDevice.BOND_NONE
        every { device.connectGatt(any(), any(), any()) } returns mockk(relaxed = true)
        every { device.connectGatt(any(), any(), any(), any()) } returns mockk(relaxed = true)
        every { device.connectGatt(any(), any(), any(), any(), any()) } returns mockk(relaxed = true)
        return device
    }
}
