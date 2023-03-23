package com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection

import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.BDDeviceSessionImpl
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection.ConnectionHandler.Companion.GUARD_TIME_MS
import com.polar.androidcommunications.testrules.BleLoggerTestRule
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.reactivex.rxjava3.schedulers.TestScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

internal class ConnectionHandlerTest {
    private lateinit var connectionHandler: ConnectionHandler
    private lateinit var testScheduler: TestScheduler

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
        testScheduler = TestScheduler()
        connectionHandler = ConnectionHandler(mockConnectionInterface, mockScannerInterface, mockConnectionHandlerObserver, guardTimerScheduler = testScheduler)

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
    }

    @Test
    fun `connect to device`() {
        // Arrange
        val capturedSessionStates = mutableListOf<BleDeviceSession.DeviceSessionState>()
        every { mockDeviceSession.sessionState = capture(capturedSessionStates) } returns Unit

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
    fun `connect to device but PHY updated callback never called`() {
        // Arrange
        every { mockConnectionInterface.setPhy(any()) } answers {
            // Do not answer anything
        }

        val capturedSessionStates = mutableListOf<BleDeviceSession.DeviceSessionState>()
        every { mockDeviceSession.sessionState = capture(capturedSessionStates) } returns Unit

        // Act
        connectionHandler.connectDevice(mockDeviceSession, true)
        testScheduler.advanceTimeBy(GUARD_TIME_MS + 10, TimeUnit.MILLISECONDS)

        // Assert
        verify(exactly = 1) { mockScannerInterface.connectionHandlerRequestStopScanning() }
        verify(exactly = 1) { mockScannerInterface.connectionHandlerResumeScanning() }
        verify(exactly = 2) { mockConnectionHandlerObserver.deviceSessionStateChanged(any()) }

        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPENING, capturedSessionStates[0])
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN, capturedSessionStates[1])

        assertEquals(ConnectionHandler.ConnectionHandlerState.FREE, connectionHandler.state)
    }

    @Test
    fun `connect to device but MTU updated callback never called`() {
        // Arrange
        every { mockConnectionInterface.setMtu(any()) } returns true

        val capturedSessionStates = mutableListOf<BleDeviceSession.DeviceSessionState>()
        every { mockDeviceSession.sessionState = capture(capturedSessionStates) } returns Unit

        // Act
        connectionHandler.connectDevice(mockDeviceSession, true)
        testScheduler.advanceTimeBy(GUARD_TIME_MS + 10, TimeUnit.MILLISECONDS)

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
        every { mockDeviceSession.sessionState = capture(capturedSessionStates) } returns Unit
        every { mockDeviceSession.sessionState } answers {
            if (capturedSessionStates.isEmpty()) BleDeviceSession.DeviceSessionState.SESSION_CLOSED else capturedSessionStates.last()
        }
        every { mockDeviceSession.isConnectableAdvertisement } returns true
        every { mockDeviceSession.connectionUuids } returns ArrayList()

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
    fun `two parallel connections`() {
        //Arrange
        val mockDeviceSession1 = mockk<BDDeviceSessionImpl>()
        val mockDeviceSession2 = mockk<BDDeviceSessionImpl>()

        val capturedSessionStates1 = mutableListOf<BleDeviceSession.DeviceSessionState>()
        every { mockDeviceSession1.sessionState = capture(capturedSessionStates1) } returns Unit
        every { mockDeviceSession1.sessionState } answers {
            if (capturedSessionStates1.isEmpty()) BleDeviceSession.DeviceSessionState.SESSION_CLOSED else capturedSessionStates1.last()
        }
        every { mockDeviceSession1.isConnectableAdvertisement } returns true
        every { mockDeviceSession1.connectionUuids } returns ArrayList()

        val capturedSessionStates2 = mutableListOf<BleDeviceSession.DeviceSessionState>()
        every { mockDeviceSession2.sessionState = capture(capturedSessionStates2) } returns Unit
        every { mockDeviceSession2.sessionState } answers {
            if (capturedSessionStates2.isEmpty()) BleDeviceSession.DeviceSessionState.SESSION_CLOSED else capturedSessionStates2.last()
        }
        every { mockDeviceSession2.isConnectableAdvertisement } returns true
        every { mockDeviceSession2.connectionUuids } returns ArrayList()

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
}