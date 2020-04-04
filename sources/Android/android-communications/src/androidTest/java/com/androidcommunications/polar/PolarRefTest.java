package com.androidcommunications.polar;

import android.bluetooth.BluetoothDevice;

import com.androidcommunications.polar.api.ble.model.BleDeviceSession;
import com.androidcommunications.polar.enpoints.ble.common.BleDeviceSession2;
import com.androidcommunications.polar.enpoints.ble.common.attribute.AttributeOperation;
import com.androidcommunications.polar.enpoints.ble.common.connection.ConnectionHandler;
import com.androidcommunications.polar.enpoints.ble.common.connection.ConnectionInterface;
import com.androidcommunications.polar.enpoints.ble.common.connection.ScannerInterface;

import junit.framework.TestCase;

import java.util.List;
import java.util.UUID;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.functions.Action;

public class PolarRefTest extends TestCase {


    public void testConnectionHandler1() {
        ConnectionHandler connectionHandler = new ConnectionHandler(
                new ConnectionInterface() {
                    @Override
                    public void connectDevice(BleDeviceSession2 session) {

                    }

                    @Override
                    public void disconnectDevice(BleDeviceSession2 session) {

                    }

                    @Override
                    public void cancelDeviceConnection(BleDeviceSession2 session) {

                    }
                },
                new ScannerInterface() {
                    @Override
                    public void connectionHandlerResumeScanning() {

                    }

                    @Override
                    public void connectionHandlerRequestStopScanning() {

                    }

                });

        BleDeviceSession2 bleDeviceSession = new BleDeviceSession2() {

            @Override
            public boolean sendNextAttributeOperation(AttributeOperation attributeOperation) {
                return false;
            }

            @Override
            public void discoverServices() {

            }

            @Override
            public void handleDisconnection() {

            }

            @Override
            public void startAuthentication(Action complete) {

            }

            @Override
            public void reset() {

            }

            @Override
            public boolean isNonConnectableAdvertisement() {
                return false;
            }

            @Override
            public String getAddress() {
                return "";
            }

            @Override
            public Completable authenticate() {
                return null;
            }

            @Override
            public boolean isAuthenticated() {
                return false;
            }

            @Override
            public Single<List<UUID>> monitorServicesDiscovered(boolean checkConnection) {
                return null;
            }

            @Override
            public boolean clearGattCache() {
                return false;
            }

            @Override
            public Single<Integer> readRssiValue() {
                return null;
            }

            @Override
            public BluetoothDevice getBluetoothDevice() {
                return null;
            }

        };

        connectionHandler.connectDevice(bleDeviceSession, true);
        connectionHandler.deviceConnected(bleDeviceSession);

        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN);
    }

    public void testConnectionHandler2() {
        ConnectionHandler connectionHandler = new ConnectionHandler(
                new ConnectionInterface() {
                    @Override
                    public void connectDevice(BleDeviceSession2 session) {

                    }

                    @Override
                    public void disconnectDevice(BleDeviceSession2 session) {

                    }

                    @Override
                    public void cancelDeviceConnection(BleDeviceSession2 session) {

                    }
                },
                new ScannerInterface() {
                    @Override
                    public void connectionHandlerResumeScanning() {

                    }

                    @Override
                    public void connectionHandlerRequestStopScanning() {

                    }
                });

        BleDeviceSession2 bleDeviceSession = new BleDeviceSession2() {

            @Override
            public boolean sendNextAttributeOperation(AttributeOperation attributeOperation) {
                return false;
            }

            @Override
            public void discoverServices() {

            }

            @Override
            public void handleDisconnection() {

            }

            @Override
            public void startAuthentication(Action complete) {

            }

            @Override
            public void reset() {

            }

            @Override
            public boolean isNonConnectableAdvertisement() {
                return false;
            }

            @Override
            public String getAddress() {
                return "";
            }

            @Override
            public Completable authenticate() {
                return null;
            }

            @Override
            public boolean isAuthenticated() {
                return false;
            }

            @Override
            public Single<List<UUID>> monitorServicesDiscovered(boolean checkConnection) {
                return null;
            }

            @Override
            public boolean clearGattCache() {
                return false;
            }

            @Override
            public Single<Integer> readRssiValue() {
                return null;
            }

            @Override
            public BluetoothDevice getBluetoothDevice() {
                return null;
            }
        };

        connectionHandler.connectDevice(bleDeviceSession, true);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPENING);
        connectionHandler.deviceDisconnected(bleDeviceSession);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
        connectionHandler.advertisementHeadReceived(bleDeviceSession);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPENING);
        connectionHandler.deviceConnected(bleDeviceSession);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN);
    }

    public void testConnectionHandler3() {

        final boolean[] nonConnectable = new boolean[1];
        nonConnectable[0] = false;

        final BleDeviceSession2 bleDeviceSession = new BleDeviceSession2() {

            @Override
            public boolean sendNextAttributeOperation(AttributeOperation attributeOperation) {
                return false;
            }

            @Override
            public void discoverServices() {

            }

            @Override
            public void handleDisconnection() {

            }

            @Override
            public void startAuthentication(Action complete) {

            }

            @Override
            public void reset() {

            }

            @Override
            public boolean isNonConnectableAdvertisement() {
                return nonConnectable[0];
            }

            @Override
            public String getAddress() {
                return "";
            }

            @Override
            public Completable authenticate() {
                return null;
            }

            @Override
            public boolean isAuthenticated() {
                return false;
            }

            @Override
            public Single<List<UUID>> monitorServicesDiscovered(boolean checkConnection) {
                return null;
            }

            @Override
            public boolean clearGattCache() {
                return false;
            }

            @Override
            public Single<Integer> readRssiValue() {
                return null;
            }

            @Override
            public BluetoothDevice getBluetoothDevice() {
                return null;
            }
        };

        final BleDeviceSession2 bleDeviceSession2 = new BleDeviceSession2() {

            @Override
            public boolean sendNextAttributeOperation(AttributeOperation attributeOperation) {
                return false;
            }

            @Override
            public void discoverServices() {

            }

            @Override
            public void handleDisconnection() {

            }

            @Override
            public void startAuthentication(Action complete) {

            }

            @Override
            public void reset() {

            }

            @Override
            public boolean isNonConnectableAdvertisement() {
                return false;
            }

            @Override
            public String getAddress() {
                return "";
            }

            @Override
            public Completable authenticate() {
                return null;
            }

            @Override
            public boolean isAuthenticated() {
                return false;
            }

            @Override
            public Single<List<UUID>> monitorServicesDiscovered(boolean checkConnection) {
                return null;
            }

            @Override
            public boolean clearGattCache() {
                return false;
            }

            @Override
            public Single<Integer> readRssiValue() {
                return null;
            }

            @Override
            public BluetoothDevice getBluetoothDevice() {
                return null;
            }
        };

        ConnectionHandler connectionHandler = new ConnectionHandler(
                new ConnectionInterface() {
                    @Override
                    public void connectDevice(BleDeviceSession2 session) {

                    }

                    @Override
                    public void disconnectDevice(BleDeviceSession2 session) {

                    }

                    @Override
                    public void cancelDeviceConnection(BleDeviceSession2 session) {

                    }
                },
                new ScannerInterface() {
                    @Override
                    public void connectionHandlerResumeScanning() {

                    }

                    @Override
                    public void connectionHandlerRequestStopScanning() {

                    }
                });

        // multi connections
        connectionHandler.connectDevice(bleDeviceSession, true);
        connectionHandler.connectDevice(bleDeviceSession2, true);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPENING);
        assertSame(bleDeviceSession2.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
        connectionHandler.deviceConnected(bleDeviceSession);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        connectionHandler.advertisementHeadReceived(bleDeviceSession2);
        assertSame(bleDeviceSession2.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPENING);
        connectionHandler.deviceConnected(bleDeviceSession2);
        assertSame(bleDeviceSession2.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN);

        // multi disconnection
        connectionHandler.disconnectDevice(bleDeviceSession);
        connectionHandler.disconnectDevice(bleDeviceSession2);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_CLOSING);
        assertSame(bleDeviceSession2.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_CLOSING);
        connectionHandler.deviceDisconnected(bleDeviceSession);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
        assertSame(bleDeviceSession2.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_CLOSING);
        connectionHandler.deviceDisconnected(bleDeviceSession2);
        assertSame(bleDeviceSession2.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_CLOSED);

        // multi connect / disconnect
        bleDeviceSession2.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        connectionHandler.connectDevice(bleDeviceSession, true);
        connectionHandler.disconnectDevice(bleDeviceSession2);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPENING);
        assertSame(bleDeviceSession2.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_CLOSING);
        connectionHandler.deviceConnected(bleDeviceSession);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        assertSame(bleDeviceSession2.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_CLOSING);
        connectionHandler.deviceDisconnected(bleDeviceSession2);

        // maintaining multi connections
        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        bleDeviceSession2.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        connectionHandler.deviceDisconnected(bleDeviceSession);
        connectionHandler.deviceDisconnected(bleDeviceSession2);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
        assertSame(bleDeviceSession2.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
        connectionHandler.advertisementHeadReceived(bleDeviceSession);
        connectionHandler.advertisementHeadReceived(bleDeviceSession2);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPENING);
        assertSame(bleDeviceSession2.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
        connectionHandler.deviceConnected(bleDeviceSession);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        assertSame(bleDeviceSession2.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
        connectionHandler.advertisementHeadReceived(bleDeviceSession2);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        assertSame(bleDeviceSession2.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPENING);
        connectionHandler.deviceConnected(bleDeviceSession2);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        assertSame(bleDeviceSession2.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN);

        // same state tests
        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        connectionHandler.connectDevice(bleDeviceSession, true);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
        connectionHandler.connectDevice(bleDeviceSession, true);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPENING);
        connectionHandler.connectDevice(bleDeviceSession, true);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPENING);
        connectionHandler.deviceConnected(bleDeviceSession);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        connectionHandler.connectDevice(bleDeviceSession, true);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN);

        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
        connectionHandler.disconnectDevice(bleDeviceSession);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        connectionHandler.disconnectDevice(bleDeviceSession);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_CLOSING);
        connectionHandler.disconnectDevice(bleDeviceSession);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_CLOSING);
        connectionHandler.deviceDisconnected(bleDeviceSession);

        //
        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
        nonConnectable[0] = true;
        connectionHandler.connectDevice(bleDeviceSession, true);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
        connectionHandler.advertisementHeadReceived(bleDeviceSession);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
        nonConnectable[0] = false;
        connectionHandler.advertisementHeadReceived(bleDeviceSession);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPENING);
        connectionHandler.deviceConnected(bleDeviceSession);

        //
        connectionHandler.disconnectDevice(bleDeviceSession);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_CLOSING);
        connectionHandler.connectDevice(bleDeviceSession, true);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
        connectionHandler.deviceDisconnected(bleDeviceSession);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);

        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
        connectionHandler.connectDevice(bleDeviceSession, false);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);

        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        connectionHandler.setAutomaticReconnection(false);
        connectionHandler.deviceDisconnected(bleDeviceSession);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        connectionHandler.setAutomaticReconnection(true);
        connectionHandler.deviceDisconnected(bleDeviceSession);
        assertSame(bleDeviceSession.getSessionState(), BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);

    }
}
