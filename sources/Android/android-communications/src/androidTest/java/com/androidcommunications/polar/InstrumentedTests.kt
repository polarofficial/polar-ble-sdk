package com.androidcommunications.polar

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.androidcommunications.polar.api.ble.model.BleDeviceSession
import com.androidcommunications.polar.enpoints.ble.bluedroid.host.BDDeviceSessionImpl
import com.androidcommunications.polar.enpoints.ble.bluedroid.host.connection.ConnectionHandler
import com.androidcommunications.polar.enpoints.ble.bluedroid.host.connection.ConnectionHandlerObserver
import com.androidcommunications.polar.enpoints.ble.bluedroid.host.connection.ConnectionInterface
import com.androidcommunications.polar.enpoints.ble.bluedroid.host.connection.ScannerInterface
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

import io.mockk.impl.annotations.MockK
import junit.framework.TestCase

@RunWith(AndroidJUnit4::class)
class InstrumentedTests {

    private lateinit var targetContext: Context
    private lateinit var connectionHandler: ConnectionHandler

    @MockK
    private lateinit var connectionInterface: ConnectionInterface

    @MockK
    private lateinit var scannerInterface: ScannerInterface

    @MockK
    private lateinit var connectionHandlerObserver: ConnectionHandlerObserver

    @Before
    fun setUp() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        connectionHandler = ConnectionHandler(connectionInterface, scannerInterface, connectionHandlerObserver)
    }

    @Test
    fun testConnectionHandler_1() {
        val bleDeviceSession = BDDeviceSessionImpl()
        connectionHandler.connectDevice(bleDeviceSession, true)
        connectionHandler.deviceConnected(bleDeviceSession)
        TestCase.assertSame(bleDeviceSession.sessionState, BleDeviceSession.DeviceSessionState.SESSION_OPEN)
    }

    @Test
    fun testConnectionHandler_2() {
        val bleDeviceSession = BDDeviceSessionImpl()
        connectionHandler.connectDevice(bleDeviceSession, true);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPENING);
        connectionHandler.deviceDisconnected(bleDeviceSession);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
        connectionHandler.advertisementHeadReceived(bleDeviceSession);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPENING);
        connectionHandler.deviceConnected(bleDeviceSession);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN);
    }

    @Test
    fun testConnectionHandler_3() {
        val bleDeviceSession = BDDeviceSessionImpl()
        val bleDeviceSession2 = BDDeviceSessionImpl()

        // multi connections
        connectionHandler.connectDevice(bleDeviceSession, true);
        connectionHandler.connectDevice(bleDeviceSession2, true);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPENING);
        TestCase.assertSame(bleDeviceSession2.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
        connectionHandler.deviceConnected(bleDeviceSession);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        connectionHandler.advertisementHeadReceived(bleDeviceSession2);
        TestCase.assertSame(bleDeviceSession2.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPENING);
        connectionHandler.deviceConnected(bleDeviceSession2);
        TestCase.assertSame(bleDeviceSession2.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN);

        // multi disconnection
        connectionHandler.disconnectDevice(bleDeviceSession);
        connectionHandler.disconnectDevice(bleDeviceSession2);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_CLOSING);
        TestCase.assertSame(bleDeviceSession2.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_CLOSING);
        connectionHandler.deviceDisconnected(bleDeviceSession);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
        TestCase.assertSame(bleDeviceSession2.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_CLOSING);
        connectionHandler.deviceDisconnected(bleDeviceSession2);
        TestCase.assertSame(bleDeviceSession2.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_CLOSED);

        // multi connect / disconnect
        bleDeviceSession2.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        connectionHandler.connectDevice(bleDeviceSession, true);
        connectionHandler.disconnectDevice(bleDeviceSession2);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPENING);
        TestCase.assertSame(bleDeviceSession2.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_CLOSING);
        connectionHandler.deviceConnected(bleDeviceSession);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        TestCase.assertSame(bleDeviceSession2.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_CLOSING);
        connectionHandler.deviceDisconnected(bleDeviceSession2);

        // maintaining multi connections
        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        bleDeviceSession2.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        connectionHandler.deviceDisconnected(bleDeviceSession);
        connectionHandler.deviceDisconnected(bleDeviceSession2);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
        TestCase.assertSame(bleDeviceSession2.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
        connectionHandler.advertisementHeadReceived(bleDeviceSession);
        connectionHandler.advertisementHeadReceived(bleDeviceSession2);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPENING);
        TestCase.assertSame(bleDeviceSession2.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
        connectionHandler.deviceConnected(bleDeviceSession);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        TestCase.assertSame(bleDeviceSession2.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
        connectionHandler.advertisementHeadReceived(bleDeviceSession2);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        TestCase.assertSame(bleDeviceSession2.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPENING);
        connectionHandler.deviceConnected(bleDeviceSession2);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        TestCase.assertSame(bleDeviceSession2.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN);

        // same state tests
        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        connectionHandler.connectDevice(bleDeviceSession, true);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
        connectionHandler.connectDevice(bleDeviceSession, true);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPENING);
        connectionHandler.connectDevice(bleDeviceSession, true);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPENING);
        connectionHandler.deviceConnected(bleDeviceSession);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        connectionHandler.connectDevice(bleDeviceSession, true);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN);

        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
        connectionHandler.disconnectDevice(bleDeviceSession);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        connectionHandler.disconnectDevice(bleDeviceSession);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_CLOSING);
        connectionHandler.disconnectDevice(bleDeviceSession);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_CLOSING);
        connectionHandler.deviceDisconnected(bleDeviceSession);

        //
        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
        // TODO nonConnectable[0] = true;
        connectionHandler.connectDevice(bleDeviceSession, true);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
        connectionHandler.advertisementHeadReceived(bleDeviceSession);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
        // TODO nonConnectable[0] = false;
        connectionHandler.advertisementHeadReceived(bleDeviceSession);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPENING);
        connectionHandler.deviceConnected(bleDeviceSession);

        //
        connectionHandler.disconnectDevice(bleDeviceSession);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_CLOSING);
        connectionHandler.connectDevice(bleDeviceSession, true);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
        connectionHandler.deviceDisconnected(bleDeviceSession);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);

        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
        connectionHandler.connectDevice(bleDeviceSession, false);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);

        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        connectionHandler.setAutomaticReconnection(false);
        connectionHandler.deviceDisconnected(bleDeviceSession);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        connectionHandler.setAutomaticReconnection(true);
        connectionHandler.deviceDisconnected(bleDeviceSession);
        TestCase.assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
    }
}