package com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection

import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.BDDeviceSessionImpl
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection.ConnectionHandler.Companion.GUARD_TIME_MS
import com.polar.androidcommunications.testrules.BleLoggerTestRule
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
}