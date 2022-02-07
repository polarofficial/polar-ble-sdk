package com.polar.androidcommunications;

import com.polar.androidcommunications.api.ble.model.BleDeviceSession;
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.BDDeviceSessionImpl;
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection.ConnectionHandler;
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection.ConnectionHandlerObserver;
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection.ConnectionInterface;
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection.ScannerInterface;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TestConnectionHandler {

    private ConnectionHandler connectionHandler;

    private final ConnectionInterface connectionInterface = new ConnectionInterface() {
        @Override
        public void connectDevice(BDDeviceSessionImpl session) {

        }

        @Override
        public void disconnectDevice(BDDeviceSessionImpl session) {

        }

        @Override
        public void cancelDeviceConnection(BDDeviceSessionImpl session) {

        }

        @Override
        public boolean isPowered() {
            return true;
        }
    };

    @Mock
    private ScannerInterface scannerInterface;

    @Mock
    private ConnectionHandlerObserver connectionHandlerObserver;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        connectionHandler = new ConnectionHandler(connectionInterface, scannerInterface, connectionHandlerObserver);
    }

    @Test
    public void testConnectionHandler_1() {
        BDDeviceSessionImpl bleDeviceSession = new BDDeviceSessionImpl();
        connectionHandler.connectDevice(bleDeviceSession, true);
        connectionHandler.deviceConnected(bleDeviceSession);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN, bleDeviceSession.getSessionState());
    }


    @Test
    public void testConnectionHandler_2() {
        BDDeviceSessionImpl bleDeviceSession = new BDDeviceSessionImpl();
        connectionHandler.connectDevice(bleDeviceSession, true);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPENING, bleDeviceSession.getSessionState());
        connectionHandler.deviceDisconnected(bleDeviceSession);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK, bleDeviceSession.getSessionState());
        connectionHandler.advertisementHeadReceived(bleDeviceSession);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPENING, bleDeviceSession.getSessionState());
        connectionHandler.deviceConnected(bleDeviceSession);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN, bleDeviceSession.getSessionState());
    }

    @Test
    public void testConnectionHandler_3() {
        boolean[] connectTable = new boolean[1];
        connectTable[0] = true;
        BDDeviceSessionImpl bleDeviceSession = new BDDeviceSessionImpl() {
            @Override
            public boolean isConnectableAdvertisement() {
                return connectTable[0];
            }
        };
        BDDeviceSessionImpl bleDeviceSession2 = new BDDeviceSessionImpl();

        // multi connections
        connectionHandler.connectDevice(bleDeviceSession, true);
        connectionHandler.connectDevice(bleDeviceSession2, true);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPENING, bleDeviceSession.getSessionState());
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK, bleDeviceSession2.getSessionState());
        connectionHandler.deviceConnected(bleDeviceSession);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN, bleDeviceSession.getSessionState());
        connectionHandler.advertisementHeadReceived(bleDeviceSession2);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPENING, bleDeviceSession2.getSessionState());
        connectionHandler.deviceConnected(bleDeviceSession2);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN, bleDeviceSession2.getSessionState());

        // multi disconnection
        connectionHandler.disconnectDevice(bleDeviceSession);
        connectionHandler.disconnectDevice(bleDeviceSession2);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_CLOSING, bleDeviceSession.getSessionState());
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_CLOSING, bleDeviceSession2.getSessionState());
        connectionHandler.deviceDisconnected(bleDeviceSession);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_CLOSED, bleDeviceSession.getSessionState());
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_CLOSING, bleDeviceSession2.getSessionState());
        connectionHandler.deviceDisconnected(bleDeviceSession2);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_CLOSED, bleDeviceSession2.getSessionState());

        // multi connect / disconnect
        bleDeviceSession2.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        connectionHandler.connectDevice(bleDeviceSession, true);
        connectionHandler.disconnectDevice(bleDeviceSession2);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPENING, bleDeviceSession.getSessionState());
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_CLOSING, bleDeviceSession2.getSessionState());
        connectionHandler.deviceConnected(bleDeviceSession);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN, bleDeviceSession.getSessionState());
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_CLOSING, bleDeviceSession2.getSessionState());
        connectionHandler.deviceDisconnected(bleDeviceSession2);

        // maintaining multi connections
        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        bleDeviceSession2.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        connectionHandler.deviceDisconnected(bleDeviceSession);
        connectionHandler.deviceDisconnected(bleDeviceSession2);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK, bleDeviceSession.getSessionState());
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK, bleDeviceSession2.getSessionState());
        connectionHandler.advertisementHeadReceived(bleDeviceSession);
        connectionHandler.advertisementHeadReceived(bleDeviceSession2);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPENING, bleDeviceSession.getSessionState());
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK, bleDeviceSession2.getSessionState());
        connectionHandler.deviceConnected(bleDeviceSession);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN, bleDeviceSession.getSessionState());
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK, bleDeviceSession2.getSessionState());
        connectionHandler.advertisementHeadReceived(bleDeviceSession2);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN, bleDeviceSession.getSessionState());
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPENING, bleDeviceSession2.getSessionState());
        connectionHandler.deviceConnected(bleDeviceSession2);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN, bleDeviceSession.getSessionState());
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN, bleDeviceSession2.getSessionState());

        // same state tests
        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        connectionHandler.connectDevice(bleDeviceSession, true);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN, bleDeviceSession.getSessionState());
        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
        connectionHandler.connectDevice(bleDeviceSession, true);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPENING, bleDeviceSession.getSessionState());
        connectionHandler.connectDevice(bleDeviceSession, true);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPENING, bleDeviceSession.getSessionState());
        connectionHandler.deviceConnected(bleDeviceSession);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN, bleDeviceSession.getSessionState());
        connectionHandler.connectDevice(bleDeviceSession, true);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN, bleDeviceSession.getSessionState());

        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
        connectionHandler.disconnectDevice(bleDeviceSession);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_CLOSED, bleDeviceSession.getSessionState());
        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        connectionHandler.disconnectDevice(bleDeviceSession);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_CLOSING, bleDeviceSession.getSessionState());
        connectionHandler.disconnectDevice(bleDeviceSession);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_CLOSING, bleDeviceSession.getSessionState());
        connectionHandler.deviceDisconnected(bleDeviceSession);

        //
        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
        connectTable[0] = false;
        connectionHandler.connectDevice(bleDeviceSession, true);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK, bleDeviceSession.getSessionState());
        connectionHandler.advertisementHeadReceived(bleDeviceSession);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK, bleDeviceSession.getSessionState());
        connectTable[0] = true;
        connectionHandler.advertisementHeadReceived(bleDeviceSession);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPENING, bleDeviceSession.getSessionState());
        connectionHandler.deviceConnected(bleDeviceSession);

        //
        connectionHandler.disconnectDevice(bleDeviceSession);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_CLOSING, bleDeviceSession.getSessionState());
        connectionHandler.connectDevice(bleDeviceSession, true);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK, bleDeviceSession.getSessionState());
        connectionHandler.deviceDisconnected(bleDeviceSession);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK, bleDeviceSession.getSessionState());

        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
        connectionHandler.connectDevice(bleDeviceSession, false);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK, bleDeviceSession.getSessionState());

        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        connectionHandler.setAutomaticReconnection(false);
        connectionHandler.deviceDisconnected(bleDeviceSession);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_CLOSED, bleDeviceSession.getSessionState());
        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        connectionHandler.setAutomaticReconnection(true);
        connectionHandler.deviceDisconnected(bleDeviceSession);
        Assert.assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK, bleDeviceSession.getSessionState());
    }
}
