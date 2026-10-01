package com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection

import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.api.ble.model.advertisement.BleAdvertisementContent
import com.polar.androidcommunications.common.ble.BleUtils.AD_TYPE
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.BDDeviceSessionImpl
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection.ConnectionHandler.Companion.GUARD_TIME_MS
import com.polar.androidcommunications.testrules.BleLoggerTestRule
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class ConnectionHandlerTest {
    private lateinit var connectionHandler: ConnectionHandler
    private lateinit var testScope: TestScope

    @Rule
    @JvmField
    val bleLoggerTestRule = BleLoggerTestRule()

    @MockK
    private lateinit var mockConnectionInterface: ConnectionInterface

    @MockK
    private lateinit var mockScannerInterface: ScannerInterface

    @MockK
    private lateinit var mockConnectionHandlerObserver: ConnectionHandlerObserver

    @MockK
    private lateinit var mockDeviceSession: BDDeviceSessionImpl

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        testScope = TestScope()
        connectionHandler = ConnectionHandler(mockConnectionInterface, mockScannerInterface, mockConnectionHandlerObserver, scope = testScope)

        every { mockConnectionInterface.connectDevice(any()) } answers {
            connectionHandler.connectionInitialized(mockDeviceSession)
        }
        every { mockConnectionInterface.setPhy(any()) } answers {
            connectionHandler.phyUpdated(mockDeviceSession)
        }
        every { mockConnectionInterface.setMtu(any()) } answers {
            connectionHandler.mtuUpdated(mockDeviceSession)
            true
        }
        every { mockConnectionInterface.startServiceDiscovery(any()) } answers {
            connectionHandler.servicesDiscovered(mockDeviceSession)
            true
        }
        every { mockConnectionInterface.isPowered } returns true

        every { mockDeviceSession.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_CLOSED
        every { mockDeviceSession.isConnectableAdvertisement } returns true
        every { mockDeviceSession.connectionUuids } returns ArrayList()
        every { mockDeviceSession.address } returns "AA:BB:CC:DD:EE:FF"
        every { mockDeviceSession.consecutiveNoCallbackConnects } returns 0
        every { mockDeviceSession.nextConnectAllowedTimeMs } returns 0L
        every { mockDeviceSession.pendingWatchdogCancel } returns false
        every { mockDeviceSession.disconnectReason } returns BleDeviceSession.DisconnectReason.NONE
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `connect to device`() {
        // Arrange
        val capturedSessionStates = mutableListOf<BleDeviceSession.DeviceSessionState>()
        every { mockDeviceSession.setSessionStates(capture(capturedSessionStates)) } just runs
        every { mockDeviceSession.sessionState } answers {
            capturedSessionStates.lastOrNull() ?: BleDeviceSession.DeviceSessionState.SESSION_CLOSED
        }

        // Act
        connectionHandler.connectDevice(mockDeviceSession, true)

        // Assert
        verify(exactly = 1) { mockScannerInterface.connectionHandlerRequestStopScanning() }
        verify(exactly = 1) { mockScannerInterface.connectionHandlerResumeScanning() }
        verify(exactly = 2) { mockConnectionHandlerObserver.deviceSessionStateChanged(any()) }

        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPENING, capturedSessionStates[0])
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN, capturedSessionStates[1])

        assertEquals(ConnectionHandler.ConnectionHandlerState.FREE, connectionHandler.state)
    }

    @Test
    fun `connect to device but PHY updated callback never called`() = runTest {
        // Arrange
        every { mockConnectionInterface.setPhy(any()) } answers {
            // Do not answer anything
        }

        val capturedSessionStates = mutableListOf<BleDeviceSession.DeviceSessionState>()
        every { mockDeviceSession.setSessionStates(capture(capturedSessionStates)) } just runs
        every { mockDeviceSession.sessionState } answers {
            capturedSessionStates.lastOrNull() ?: BleDeviceSession.DeviceSessionState.SESSION_CLOSED
        }

        // Act
        connectionHandler.connectDevice(mockDeviceSession, true)
        testScope.advanceTimeBy(GUARD_TIME_MS + 10)

        // Assert
        verify(exactly = 1) { mockScannerInterface.connectionHandlerRequestStopScanning() }
        verify(exactly = 1) { mockScannerInterface.connectionHandlerResumeScanning() }
        verify(exactly = 2) { mockConnectionHandlerObserver.deviceSessionStateChanged(any()) }

        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPENING, capturedSessionStates[0])
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN, capturedSessionStates[1])

        assertEquals(ConnectionHandler.ConnectionHandlerState.FREE, connectionHandler.state)
    }

    @Test
    fun `connect to device but MTU updated callback never called`() = runTest {
        // Arrange
        every { mockConnectionInterface.setMtu(any()) } returns true

        val capturedSessionStates = mutableListOf<BleDeviceSession.DeviceSessionState>()
        every { mockDeviceSession.setSessionStates(capture(capturedSessionStates)) } just runs
        every { mockDeviceSession.sessionState } answers {
            capturedSessionStates.lastOrNull() ?: BleDeviceSession.DeviceSessionState.SESSION_CLOSED
        }

        // Act
        connectionHandler.connectDevice(mockDeviceSession, true)
        testScope.advanceTimeBy(GUARD_TIME_MS + 10)

        // Assert
        verify(exactly = 1) { mockScannerInterface.connectionHandlerRequestStopScanning() }
        verify(exactly = 1) { mockScannerInterface.connectionHandlerResumeScanning() }
        verify(exactly = 2) { mockConnectionHandlerObserver.deviceSessionStateChanged(any()) }

        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPENING, capturedSessionStates[0])
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN, capturedSessionStates[1])

        assertEquals(ConnectionHandler.ConnectionHandlerState.FREE, connectionHandler.state)
    }

    @Test
    fun `test connection steps opening, open, openpark and open`() {
        // Arrange
        val capturedSessionStates = mutableListOf<BleDeviceSession.DeviceSessionState>()
        every { mockDeviceSession.setSessionStates(capture(capturedSessionStates)) } just runs
        every { mockDeviceSession.sessionState } answers {
            capturedSessionStates.lastOrNull() ?: BleDeviceSession.DeviceSessionState.SESSION_CLOSED
        }
        every { mockDeviceSession.isConnectableAdvertisement } returns true
        every { mockDeviceSession.connectionUuids } returns ArrayList()
        every { mockDeviceSession.disconnectReason } returns BleDeviceSession.DisconnectReason.NONE

        //Act
        connectionHandler.connectDevice(mockDeviceSession, true)
        connectionHandler.deviceDisconnected(mockDeviceSession)
        connectionHandler.advertisementHeadReceived(mockDeviceSession)

        // Assert
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPENING, capturedSessionStates[0])
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN, capturedSessionStates[1])
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK, capturedSessionStates[2])
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPENING, capturedSessionStates[3])
        assertEquals(ConnectionHandler.ConnectionHandlerState.FREE, connectionHandler.state)
    }

    @Test
    fun `removed pairing from open session does not enter reconnect park`() {
        val capturedSessionStates = mutableListOf<BleDeviceSession.DeviceSessionState>()
        every { mockDeviceSession.setSessionStates(capture(capturedSessionStates)) } just runs
        every { mockDeviceSession.sessionState } answers {
            capturedSessionStates.lastOrNull() ?: BleDeviceSession.DeviceSessionState.SESSION_CLOSED
        }
        every { mockDeviceSession.disconnectReason } returns BleDeviceSession.DisconnectReason.PAIRING_INFORMATION_REMOVED

        connectionHandler.connectDevice(mockDeviceSession, true)
        connectionHandler.deviceDisconnected(mockDeviceSession)

        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_CLOSED, capturedSessionStates.last())
        assertEquals(ConnectionHandler.ConnectionHandlerState.FREE, connectionHandler.state)
    }

    @Test
    fun `two parallel connections`() {
        //Arrange
        val mockDeviceSession1 = mockk<BDDeviceSessionImpl>(relaxUnitFun = true)
        val mockDeviceSession2 = mockk<BDDeviceSessionImpl>(relaxUnitFun = true)

        val capturedSessionStates1 = mutableListOf<BleDeviceSession.DeviceSessionState>()
        every { mockDeviceSession1.setSessionStates(capture(capturedSessionStates1)) } just runs
        every { mockDeviceSession1.sessionState } answers {
            capturedSessionStates1.lastOrNull() ?: BleDeviceSession.DeviceSessionState.SESSION_CLOSED
        }
        every { mockDeviceSession1.isConnectableAdvertisement } returns true
        every { mockDeviceSession1.connectionUuids } returns ArrayList()
        every { mockDeviceSession1.address } returns "AA:BB:CC:DD:EE:01"
        every { mockDeviceSession1.consecutiveNoCallbackConnects } returns 0
        every { mockDeviceSession1.nextConnectAllowedTimeMs } returns 0L
        every { mockDeviceSession1.pendingWatchdogCancel } returns false

        val capturedSessionStates2 = mutableListOf<BleDeviceSession.DeviceSessionState>()
        every { mockDeviceSession2.setSessionStates(capture(capturedSessionStates2)) } just runs
        every { mockDeviceSession2.sessionState } answers {
            capturedSessionStates2.lastOrNull() ?: BleDeviceSession.DeviceSessionState.SESSION_CLOSED
        }
        every { mockDeviceSession2.isConnectableAdvertisement } returns true
        every { mockDeviceSession2.connectionUuids } returns ArrayList()
        every { mockDeviceSession2.address } returns "AA:BB:CC:DD:EE:02"
        every { mockDeviceSession2.consecutiveNoCallbackConnects } returns 0
        every { mockDeviceSession2.nextConnectAllowedTimeMs } returns 0L
        every { mockDeviceSession2.pendingWatchdogCancel } returns false

        val sessionInit = slot<BDDeviceSessionImpl>()
        every { mockConnectionInterface.connectDevice(capture(sessionInit)) } answers {
            connectionHandler.connectionInitialized(sessionInit.captured)
        }

        val sessionPhy = slot<BDDeviceSessionImpl>()
        every { mockConnectionInterface.setPhy(capture(sessionPhy)) } answers {
            connectionHandler.phyUpdated(sessionPhy.captured)
        }

        val sessionMtu = slot<BDDeviceSessionImpl>()
        every { mockConnectionInterface.setMtu(capture(sessionMtu)) } answers {
            connectionHandler.mtuUpdated(sessionMtu.captured)
            true
        }

        val sessionServiceDiscovery = slot<BDDeviceSessionImpl>()
        every { mockConnectionInterface.startServiceDiscovery(capture(sessionServiceDiscovery)) } answers {
            connectionHandler.servicesDiscovered(sessionServiceDiscovery.captured)
            true
        }

        val sessionDisconnect = slot<BDDeviceSessionImpl>()
        every { mockConnectionInterface.disconnectDevice(capture(sessionDisconnect)) } answers {
            connectionHandler.deviceDisconnected(sessionDisconnect.captured)
        }

        //Act
        connectionHandler.connectDevice(mockDeviceSession1, true)
        connectionHandler.connectDevice(mockDeviceSession2, true)
        connectionHandler.disconnectDevice(mockDeviceSession1)
        connectionHandler.disconnectDevice(mockDeviceSession2)
        connectionHandler.deviceDisconnected(mockDeviceSession1)
        connectionHandler.deviceDisconnected(mockDeviceSession2)

        //Assert
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPENING, capturedSessionStates1[0])
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN, capturedSessionStates1[1])
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPENING, capturedSessionStates2[0])
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN, capturedSessionStates2[1])

        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_CLOSING, capturedSessionStates1[2])
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_CLOSED, capturedSessionStates1[3])
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_CLOSING, capturedSessionStates2[2])
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_CLOSED, capturedSessionStates2[3])
        assertEquals(ConnectionHandler.ConnectionHandlerState.FREE, connectionHandler.state)
    }

    @Test
    fun `connectDeviceDirect - opens session bypassing advertisement gate`() {
        // Arrange: device has no connectable advertisement (isConnectableAdvertisement = false)
        every { mockDeviceSession.isConnectableAdvertisement } returns false
        val capturedSessionStates = mutableListOf<BleDeviceSession.DeviceSessionState>()
        every { mockDeviceSession.setSessionStates(capture(capturedSessionStates)) } just runs
        every { mockDeviceSession.sessionState } answers {
            if (capturedSessionStates.isEmpty()) BleDeviceSession.DeviceSessionState.SESSION_CLOSED else capturedSessionStates.last()
        }

        // Act - connectDevice would park; connectDeviceDirect must not
        connectionHandler.connectDeviceDirect(mockDeviceSession, true)

        // Assert: full open sequence without parking
        verify(exactly = 1) { mockScannerInterface.connectionHandlerRequestStopScanning() }
        verify(exactly = 1) { mockScannerInterface.connectionHandlerResumeScanning() }
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPENING, capturedSessionStates[0])
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN, capturedSessionStates[1])
        assertEquals(ConnectionHandler.ConnectionHandlerState.FREE, connectionHandler.state)
    }

    @Test
    fun `connectDeviceDirect - parks session when BLE is off`() {
        // Arrange
        every { mockDeviceSession.isConnectableAdvertisement } returns false
        val capturedSessionStates = mutableListOf<BleDeviceSession.DeviceSessionState>()
        every { mockDeviceSession.setSessionStates(capture(capturedSessionStates)) } just runs
        every { mockDeviceSession.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_CLOSED

        // Act - BLE disabled
        connectionHandler.connectDeviceDirect(mockDeviceSession, false)

        // Assert: session waits in OPEN_PARK for BLE power-on
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK, capturedSessionStates[0])
        assertEquals(ConnectionHandler.ConnectionHandlerState.FREE, connectionHandler.state)
    }

    @Test
    fun `connectDeviceDirect - reconnects after disconnect without advertisement`() {
        // Arrange
        every { mockDeviceSession.isConnectableAdvertisement } returns false
        val capturedSessionStates = mutableListOf<BleDeviceSession.DeviceSessionState>()
        every { mockDeviceSession.setSessionStates(capture(capturedSessionStates)) } just runs
        every { mockDeviceSession.sessionState } answers {
            if (capturedSessionStates.isEmpty()) BleDeviceSession.DeviceSessionState.SESSION_CLOSED else capturedSessionStates.last()
        }

        // Act: direct connect → remote disconnect → caller retries via connectDeviceDirect
        connectionHandler.connectDeviceDirect(mockDeviceSession, true)
        connectionHandler.deviceDisconnected(mockDeviceSession)
        // Session is now SESSION_OPEN_PARK; BDDeviceListenerImpl auto-retries — simulate that here
        connectionHandler.connectDeviceDirect(mockDeviceSession, true)

        // Assert: two full OPENING→OPEN cycles separated by OPEN_PARK
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPENING, capturedSessionStates[0])
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN, capturedSessionStates[1])
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK, capturedSessionStates[2])
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPENING, capturedSessionStates[3])
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN, capturedSessionStates[4])
        assertEquals(ConnectionHandler.ConnectionHandlerState.FREE, connectionHandler.state)
    }

    /**
     * Builds a second stateful session mock. No GATT callback is produced unless a test
     * explicitly supplies one, so the handler can be parked in CONNECTING on demand.
     */
    private fun newSession(address: String): BDDeviceSessionImpl {
        val session = mockk<BDDeviceSessionImpl>(relaxed = true)
        val states = mutableListOf<BleDeviceSession.DeviceSessionState>()
        every { session.setSessionStates(capture(states)) } just runs
        every { session.sessionState } answers {
            states.lastOrNull() ?: BleDeviceSession.DeviceSessionState.SESSION_CLOSED
        }
        every { session.address } returns address
        every { session.isConnectableAdvertisement } returns true
        every { session.connectionUuids } returns ArrayList()
        every { session.consecutiveNoCallbackConnects } returns 0
        every { session.nextConnectAllowedTimeMs } returns 0L
        every { session.pendingWatchdogCancel } returns false
        every { session.disconnectReason } returns BleDeviceSession.DisconnectReason.NONE
        return session
    }

    @Test
    fun `connect request arriving while busy is queued and served once handler is free`() = runTest {
        // Arrange: first session connects but never calls back, pinning the handler in CONNECTING.
        val busy = newSession("11:11:11:11:11:11")
        every { mockConnectionInterface.connectDevice(busy) } just runs

        val capturedSessionStates = mutableListOf<BleDeviceSession.DeviceSessionState>()
        every { mockDeviceSession.setSessionStates(capture(capturedSessionStates)) } just runs
        every { mockDeviceSession.sessionState } answers {
            capturedSessionStates.lastOrNull() ?: BleDeviceSession.DeviceSessionState.SESSION_CLOSED
        }

        connectionHandler.connectDevice(busy, true)
        assertEquals(ConnectionHandler.ConnectionHandlerState.CONNECTING, connectionHandler.state)

        // Act: a second connect request arrives while busy — previously dropped and never retried.
        connectionHandler.connectDevice(mockDeviceSession, true)
        testScope.advanceTimeBy(10)
        verify(exactly = 0) { mockConnectionInterface.connectDevice(mockDeviceSession) }

        // The blocking session finally disconnects, freeing the handler.
        connectionHandler.deviceDisconnected(busy)
        testScope.advanceTimeBy(10)

        // Assert: the queued request was resumed rather than lost.
        verify(exactly = 1) { mockConnectionInterface.connectDevice(mockDeviceSession) }
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN, mockDeviceSession.sessionState)
    }

    @Test
    fun `disabling automatic reconnection drops queued automatic retries but keeps explicit ones`() = runTest {
        // Arrange: park the handler in CONNECTING so requests queue up.
        val busy = newSession("11:11:11:11:11:11")
        every { mockConnectionInterface.connectDevice(busy) } just runs
        val capturedSessionStates = mutableListOf<BleDeviceSession.DeviceSessionState>()
        every { mockDeviceSession.setSessionStates(capture(capturedSessionStates)) } just runs
        every { mockDeviceSession.sessionState } answers {
            capturedSessionStates.lastOrNull() ?: BleDeviceSession.DeviceSessionState.SESSION_CLOSED
        }
        connectionHandler.connectDevice(busy, true)

        // Act: an explicit user request is queued, then automatic reconnection is turned off.
        connectionHandler.connectDevice(mockDeviceSession, true)
        connectionHandler.setAutomaticReconnection(false)
        connectionHandler.deviceDisconnected(busy)
        testScope.advanceTimeBy(10)

        // Assert: an explicit request survives - only library-scheduled retries are dropped.
        verify(exactly = 1) { mockConnectionInterface.connectDevice(mockDeviceSession) }
    }

    @Test
    fun `duplicate negotiation callback is ignored instead of skipping a stage`() = runTest {
        // Arrange: re-deliver SERVICES_DISCOVERED once while the attempt is still mid-negotiation
        // (at the PHY stage), which is where a stale guard timer would land.
        var duplicateEmitted = false
        every { mockConnectionInterface.setPhy(any()) } answers {
            if (!duplicateEmitted) {
                duplicateEmitted = true
                connectionHandler.servicesDiscovered(mockDeviceSession)
            }
            connectionHandler.phyUpdated(mockDeviceSession)
        }
        val capturedSessionStates = mutableListOf<BleDeviceSession.DeviceSessionState>()
        every { mockDeviceSession.setSessionStates(capture(capturedSessionStates)) } just runs
        every { mockDeviceSession.sessionState } answers {
            capturedSessionStates.lastOrNull() ?: BleDeviceSession.DeviceSessionState.SESSION_CLOSED
        }

        // Act
        connectionHandler.connectDevice(mockDeviceSession, true)

        // Assert: the out-of-order repeat is rejected, so the PHY stage is not restarted.
        verify(exactly = 1) { mockConnectionInterface.setPhy(mockDeviceSession) }
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN, mockDeviceSession.sessionState)
        assertEquals(ConnectionHandler.ConnectionHandlerState.FREE, connectionHandler.state)
    }

    @Test
    fun `odd length advertisement uuid payload is rejected without crashing`() = runTest {
        // Arrange: no UUID matches, and the payload ends in a truncated trailing byte. Walking
        // past the end previously threw IndexOutOfBoundsException from the advertisement path.
        val advertisement = BleAdvertisementContent()
        advertisement.advertisementData[AD_TYPE.GAP_ADTYPE_16BIT_COMPLETE] = byteArrayOf(0x0F, 0x18, 0x0A)
        every { mockDeviceSession.advertisementContent } returns advertisement
        every { mockDeviceSession.connectionUuids } returns arrayListOf("180D")
        every { mockDeviceSession.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK

        // Act
        connectionHandler.advertisementHeadReceived(mockDeviceSession)

        // Assert: the truncated entry is ignored and the device is simply not connected.
        verify(exactly = 0) { mockConnectionInterface.connectDevice(mockDeviceSession) }
        assertEquals(ConnectionHandler.ConnectionHandlerState.FREE, connectionHandler.state)
    }

    @Test
    fun `required uuid is matched from either 16 bit advertisement list`() = runTest {
        // Arrange: the required UUID is only in the MORE list, which the old single-list lookup
        // could skip entirely.
        val advertisement = BleAdvertisementContent()
        advertisement.advertisementData[AD_TYPE.GAP_ADTYPE_16BIT_MORE] = byteArrayOf(0x0F, 0x18)
        advertisement.advertisementData[AD_TYPE.GAP_ADTYPE_16BIT_COMPLETE] = byteArrayOf(0x0D, 0x18)
        every { mockDeviceSession.advertisementContent } returns advertisement
        every { mockDeviceSession.connectionUuids } returns arrayListOf("180D")
        val capturedSessionStates = mutableListOf<BleDeviceSession.DeviceSessionState>()
        every { mockDeviceSession.setSessionStates(capture(capturedSessionStates)) } just runs
        every { mockDeviceSession.sessionState } answers {
            capturedSessionStates.lastOrNull() ?: BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK
        }

        // Act
        connectionHandler.advertisementHeadReceived(mockDeviceSession)

        // Assert
        verify(exactly = 1) { mockConnectionInterface.connectDevice(mockDeviceSession) }
    }

    @Test
    fun `sessionRemoved frees a handler wedged on the removed session`() = runTest {
        // Arrange: connection never calls back, so the handler stays in CONNECTING.
        every { mockConnectionInterface.connectDevice(any()) } just runs
        val capturedSessionStates = mutableListOf<BleDeviceSession.DeviceSessionState>()
        every { mockDeviceSession.setSessionStates(capture(capturedSessionStates)) } just runs
        every { mockDeviceSession.sessionState } answers {
            capturedSessionStates.lastOrNull() ?: BleDeviceSession.DeviceSessionState.SESSION_CLOSED
        }
        connectionHandler.connectDevice(mockDeviceSession, true)
        assertEquals(ConnectionHandler.ConnectionHandlerState.CONNECTING, connectionHandler.state)

        // Act: the listener drops the session from its tracking list.
        connectionHandler.sessionRemoved(mockDeviceSession)

        // Assert: GATT released and the handler is usable again instead of wedged forever.
        verify(exactly = 1) { mockConnectionInterface.cancelDeviceConnection(mockDeviceSession) }
        verify(exactly = 1) { mockConnectionHandlerObserver.deviceConnectionCancelled(mockDeviceSession) }
        assertEquals(ConnectionHandler.ConnectionHandlerState.FREE, connectionHandler.state)
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_CLOSED, mockDeviceSession.sessionState)
    }

    @Test
    fun `dispatchGattCallback runs the action only for the session's current gatt`() {
        val currentGatt = mockk<android.bluetooth.BluetoothGatt>(relaxed = true)
        val staleGatt = mockk<android.bluetooth.BluetoothGatt>(relaxed = true)
        every { mockDeviceSession.gatt } returns currentGatt

        var currentRan = false
        var staleRan = false
        connectionHandler.dispatchGattCallback(mockDeviceSession, currentGatt) { currentRan = true }
        connectionHandler.dispatchGattCallback(mockDeviceSession, staleGatt) { staleRan = true }

        assertEquals(true, currentRan)
        // A callback from a GATT object a reconnect already replaced must never reach the
        // state machine of the new connection.
        assertEquals(false, staleRan)
    }

    @Test
    fun `dispatchGattCallback drops events queued before a power off`() {
        val gatt = mockk<android.bluetooth.BluetoothGatt>(relaxed = true)
        every { mockDeviceSession.gatt } returns gatt
        connectionHandler.blePoweredOff()

        var ran = false
        connectionHandler.dispatchGattCallback(mockDeviceSession, gatt) { ran = true }

        assertEquals(false, ran)
    }

    @Test
    fun `blePoweredOff frees a handler wedged on a session the listener no longer tracks`() = runTest {
        // Arrange: connection never calls back, so the handler stays pinned on this session.
        every { mockConnectionInterface.connectDevice(any()) } just runs
        val capturedSessionStates = mutableListOf<BleDeviceSession.DeviceSessionState>()
        every { mockDeviceSession.setSessionStates(capture(capturedSessionStates)) } just runs
        every { mockDeviceSession.sessionState } answers {
            capturedSessionStates.lastOrNull() ?: BleDeviceSession.DeviceSessionState.SESSION_CLOSED
        }
        connectionHandler.connectDevice(mockDeviceSession, true)
        assertEquals(ConnectionHandler.ConnectionHandlerState.CONNECTING, connectionHandler.state)

        // Act: power off, passing an EMPTY tracked list – the session is only known as 'current'.
        connectionHandler.blePoweredOff(emptyList())

        // Assert: a Bluetooth toggle clears the wedge instead of needing an app restart.
        verify(exactly = 1) { mockConnectionInterface.cancelDeviceConnection(mockDeviceSession) }
        assertEquals(ConnectionHandler.ConnectionHandlerState.FREE, connectionHandler.state)
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK, mockDeviceSession.sessionState)
    }

    @Test
    fun `connect requests are ignored while powered off and direct reconnect resumes on power on`() = runTest {
        val session = newSession("22:22:22:22:22:22")
        every { session.directConnect } returns true
        connectionHandler.blePoweredOff(emptyList())

        // Act: a connect attempt while the adapter is down must not reach the GATT layer.
        connectionHandler.connectDevice(session, true)
        verify(exactly = 0) { mockConnectionInterface.connectDevice(session) }

        // Park it so it is eligible for the automatic direct reconnect on power on.
        session.setSessionStates(BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK)
        connectionHandler.blePoweredOn(listOf(session))
        testScope.advanceTimeBy(10_000)
        testScope.runCurrent()

        verify(exactly = 1) { mockConnectionInterface.connectDevice(session) }
    }
}
