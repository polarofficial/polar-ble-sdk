package com.androidcommunications.polar;

import android.os.Handler;

import com.androidcommunications.polar.api.ble.model.BleDeviceSession;
import com.androidcommunications.polar.api.ble.model.gatt.BleGattBase;
import com.androidcommunications.polar.api.ble.model.gatt.BleGattTxInterface;
import com.androidcommunications.polar.api.ble.model.gatt.client.BlePolarBPRCClient;
import com.androidcommunications.polar.api.ble.model.gatt.client.psftp.BlePsFtpClient;
import com.androidcommunications.polar.api.ble.model.gatt.client.psftp.BlePsFtpUtils;
import com.androidcommunications.polar.common.ble.BleUtils;
import com.androidcommunications.polar.enpoints.ble.common.BleDeviceSession2;
import com.androidcommunications.polar.enpoints.ble.common.attribute.AttributeOperation;
import com.androidcommunications.polar.enpoints.ble.common.connection.ConnectionHandler;
import com.androidcommunications.polar.enpoints.ble.common.connection.ConnectionInterface;
import com.androidcommunications.polar.enpoints.ble.common.connection.ScannerInterface;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class PolarRefTest extends TestCase {


    public void testConnectionHandler1(){
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

        }, new Handler());

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
            public void startAuthentication() {

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
            public Observable<HashMap<BleUtils.PAIRING_CAPABILITY, Byte>> authenticate(HashMap<BleUtils.PAIRING_CAPABILITY, Byte> parameters) {
                return null;
            }

            @Override
            public boolean isAuthenticated() {
                return false;
            }

            @Override
            public Observable<List<UUID>> monitorServicesDiscovered(boolean checkConnection) {
                return null;
            }

            @Override
            public boolean clearGattCache() {
                return false;
            }

            @Override
            public Observable<Integer> readRssiValue() {
                return null;
            }

        };

        connectionHandler.connectDevice(bleDeviceSession,true);
        connectionHandler.deviceConnected(bleDeviceSession);

        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN);
    }

    public void testConnectionHandler2(){
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
        },new Handler());

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
            public void startAuthentication() {

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
            public Observable<HashMap<BleUtils.PAIRING_CAPABILITY, Byte>> authenticate(HashMap<BleUtils.PAIRING_CAPABILITY, Byte> parameters) {
                return null;
            }

            @Override
            public boolean isAuthenticated() {
                return false;
            }

            @Override
            public Observable<List<UUID>> monitorServicesDiscovered(boolean checkConnection) {
                return null;
            }

            @Override
            public boolean clearGattCache() {
                return false;
            }

            @Override
            public Observable<Integer> readRssiValue() {
                return null;
            }
        };

        connectionHandler.connectDevice(bleDeviceSession,true);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPENING);
        connectionHandler.deviceDisconnected(bleDeviceSession);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
        connectionHandler.advertisementHeadReceived(bleDeviceSession);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPENING);
        connectionHandler.deviceConnected(bleDeviceSession);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN);
    }

    public void testConnectionHandler3(){

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
            public void startAuthentication() {

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
            public Observable<HashMap<BleUtils.PAIRING_CAPABILITY, Byte>> authenticate(HashMap<BleUtils.PAIRING_CAPABILITY, Byte> parameters) {
                return null;
            }

            @Override
            public boolean isAuthenticated() {
                return false;
            }

            @Override
            public Observable<List<UUID>> monitorServicesDiscovered(boolean checkConnection) {
                return null;
            }

            @Override
            public boolean clearGattCache() {
                return false;
            }

            @Override
            public Observable<Integer> readRssiValue() {
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
            public void startAuthentication() {

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
            public Observable<HashMap<BleUtils.PAIRING_CAPABILITY, Byte>> authenticate(HashMap<BleUtils.PAIRING_CAPABILITY, Byte> parameters) {
                return null;
            }

            @Override
            public boolean isAuthenticated() {
                return false;
            }

            @Override
            public Observable<List<UUID>> monitorServicesDiscovered(boolean checkConnection) {
                return null;
            }

            @Override
            public boolean clearGattCache() {
                return false;
            }

            @Override
            public Observable<Integer> readRssiValue() {
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
        }, new Handler());

        // multi connections
        connectionHandler.connectDevice(bleDeviceSession,true);
        connectionHandler.connectDevice(bleDeviceSession2,true);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPENING);
        assertTrue(bleDeviceSession2.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
        connectionHandler.deviceConnected(bleDeviceSession);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        connectionHandler.advertisementHeadReceived(bleDeviceSession2);
        assertTrue(bleDeviceSession2.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPENING);
        connectionHandler.deviceConnected(bleDeviceSession2);
        assertTrue(bleDeviceSession2.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN);

        // multi disconnection
        connectionHandler.disconnectDevice(bleDeviceSession);
        connectionHandler.disconnectDevice(bleDeviceSession2);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_CLOSING);
        assertTrue(bleDeviceSession2.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_CLOSING);
        connectionHandler.deviceDisconnected(bleDeviceSession);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
        assertTrue(bleDeviceSession2.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_CLOSING);
        connectionHandler.deviceDisconnected(bleDeviceSession2);
        assertTrue(bleDeviceSession2.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_CLOSED);

        // multi connect / disconnect
        bleDeviceSession2.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        connectionHandler.connectDevice(bleDeviceSession,true);
        connectionHandler.disconnectDevice(bleDeviceSession2);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPENING);
        assertTrue(bleDeviceSession2.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_CLOSING);
        connectionHandler.deviceConnected(bleDeviceSession);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        assertTrue(bleDeviceSession2.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_CLOSING);
        connectionHandler.deviceDisconnected(bleDeviceSession2);

        // maintaining multi connections
        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        bleDeviceSession2.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        connectionHandler.deviceDisconnected(bleDeviceSession);
        connectionHandler.deviceDisconnected(bleDeviceSession2);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
        assertTrue(bleDeviceSession2.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
        connectionHandler.advertisementHeadReceived(bleDeviceSession);
        connectionHandler.advertisementHeadReceived(bleDeviceSession2);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPENING);
        assertTrue(bleDeviceSession2.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
        connectionHandler.deviceConnected(bleDeviceSession);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        assertTrue(bleDeviceSession2.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
        connectionHandler.advertisementHeadReceived(bleDeviceSession2);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        assertTrue(bleDeviceSession2.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPENING);
        connectionHandler.deviceConnected(bleDeviceSession2);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        assertTrue(bleDeviceSession2.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN);

        // same state tests
        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        connectionHandler.connectDevice(bleDeviceSession,true);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
        connectionHandler.connectDevice(bleDeviceSession,true);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPENING);
        connectionHandler.connectDevice(bleDeviceSession,true);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPENING);
        connectionHandler.deviceConnected(bleDeviceSession);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        connectionHandler.connectDevice(bleDeviceSession,true);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN);

        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
        connectionHandler.disconnectDevice(bleDeviceSession);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        connectionHandler.disconnectDevice(bleDeviceSession);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_CLOSING);
        connectionHandler.disconnectDevice(bleDeviceSession);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_CLOSING);
        connectionHandler.deviceDisconnected(bleDeviceSession);

        //
        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
        nonConnectable[0] = true;
        connectionHandler.connectDevice(bleDeviceSession,true);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
        connectionHandler.advertisementHeadReceived(bleDeviceSession);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
        nonConnectable[0] = false;
        connectionHandler.advertisementHeadReceived(bleDeviceSession);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPENING);
        connectionHandler.deviceConnected(bleDeviceSession);

        //
        connectionHandler.disconnectDevice(bleDeviceSession);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_CLOSING);
        connectionHandler.connectDevice(bleDeviceSession, true);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
        connectionHandler.deviceDisconnected(bleDeviceSession);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);

        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
        connectionHandler.connectDevice(bleDeviceSession, false);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);

        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        connectionHandler.setAutomaticReconnection(false);
        connectionHandler.deviceDisconnected(bleDeviceSession);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
        bleDeviceSession.setSessionState(BleDeviceSession.DeviceSessionState.SESSION_OPEN);
        connectionHandler.setAutomaticReconnection(true);
        connectionHandler.deviceDisconnected(bleDeviceSession);
        assertTrue(bleDeviceSession.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);

    }

    public void testWaitNotification(){
        final BlePsFtpClient[] service = new BlePsFtpClient[1];

        BleGattTxInterface bleGattServiceTxInterface = new BleGattTxInterface() {
            @Override
            public void transmitMessages(BleGattBase gattServiceBase, UUID serviceUuid, UUID characteristicUuid, List<byte[]> packets, boolean withResponse) throws Throwable {
                /*for(byte[] pk : packets){
                    service[0].processServiceDataWritten(characteristicUuid, 0);
                }*/
            }

            @Override
            public void readValue(BleGattBase gattServiceBase, UUID serviceUuid, UUID characteristicUuid) throws Throwable {

            }

            @Override
            public void removeServiceMessages(BleGattBase gattServiceBase, UUID serviceUuid, UUID characteristicUuid) throws Throwable {

            }

            @Override
            public void setCharacteristicNotify(BleGattBase gattServiceBase, UUID serviceUuid, UUID characteristicUuid, boolean enable) throws Throwable {

            }

            @Override
            public boolean isConnected() {
                return true;
            }

            @Override
            public boolean isConnected(BleGattBase gattServiceBase) {
                return false;
            }


            @Override
            public void gattClientRequestStopScanning() {

            }

            @Override
            public void gattClientResumeScanning() {

            }

            @Override
            public int transportQueueSize() {
                return 0;
            }

        };
        service[0] = new BlePsFtpClient(bleGattServiceTxInterface);
        service[0].processCharacteristicDiscovered(BlePsFtpUtils.RFC77_PFTP_H2D_CHARACTERISTIC,0);
        service[0].setActive(BlePsFtpUtils.RFC77_PFTP_D2H_CHARACTERISTIC,true,0);

        ByteArrayInputStream inputStream2 = new ByteArrayInputStream(new byte[]{0x01,(byte)0xFF});
        BlePsFtpUtils.Rfc76SequenceNumber sequenceNumber = new BlePsFtpUtils.Rfc76SequenceNumber();
        final List<byte[]> responses = BlePsFtpUtils.buildRfc76MessageFrameAll(inputStream2, 20, sequenceNumber);
        for(byte[] packet : responses){
            service[0].processServiceData(BlePsFtpUtils.RFC77_PFTP_D2H_CHARACTERISTIC,packet,0,false);
        }

        final boolean[] result = {false};
        service[0].waitForNotification(Schedulers.trampoline()).observeOn(Schedulers.trampoline()).take(1).toObservable().subscribe(
                new Observer<BlePsFtpUtils.PftpNotificationMessage>() {
            @Override
            public void onComplete() {

            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onSubscribe(@NonNull Disposable d) {

            }

            @Override
            public void onNext(BlePsFtpUtils.PftpNotificationMessage pftpNotificationMessage) {
                result[0] = pftpNotificationMessage.id == 0x01;
                service[0].reset();
            }
        });
        assertTrue(result[0]);
    }

    public void testPpbmrcs1() {
        final boolean[] result = {false};

        BlePolarBPRCClient client = new BlePolarBPRCClient(new BleGattTxInterface() {
            @Override
            public void transmitMessages(BleGattBase gattServiceBase, UUID serviceUuid, UUID characteristicUuid, List<byte[]> packets, boolean withResponse) throws Throwable {

            }

            @Override
            public void readValue(BleGattBase gattServiceBase, UUID serviceUuid, UUID characteristicUuid) throws Throwable {

            }

            @Override
            public void removeServiceMessages(BleGattBase gattServiceBase, UUID serviceUuid, UUID characteristicUuid) throws Throwable {

            }

            @Override
            public void setCharacteristicNotify(BleGattBase gattServiceBase, UUID serviceUuid, UUID characteristicUuid, boolean enable) throws Throwable {

            }

            @Override
            public boolean isConnected() {
                return true;
            }

            @Override
            public boolean isConnected(BleGattBase gattServiceBase) {
                return false;
            }

            @Override
            public void gattClientRequestStopScanning() {

            }

            @Override
            public void gattClientResumeScanning() {

            }

            @Override
            public int transportQueueSize() {
                return 0;
            }

        });

        client.setActive(BlePolarBPRCClient.PBPRC_CP,true,0);
        client.processServiceData(BlePolarBPRCClient.PBPRC_CP,new byte[]{(byte)0xf0,0x01,0x01},0,false);
        client.sendBloodPressureMeasurementCommand(true,Schedulers.trampoline()).subscribe(new Observer<Void>() {
            @Override
            public void onComplete() {
                result[0]=true;
            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onSubscribe(@NonNull Disposable d) {

            }

            @Override
            public void onNext(Void aVoid) {

            }
        });
        assertTrue(result[0]);
    }

    public static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for(byte b: a) {
            sb.append(String.format("%02x ", b & 0xff).toUpperCase());
        }
        return sb.toString();
    }
}
