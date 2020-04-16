package com.androidcommunications.polar.enpoints.ble.common.connection;

import android.content.Context;
import android.os.Handler;

import com.androidcommunications.polar.api.ble.BleLogger;
import com.androidcommunications.polar.api.ble.model.BleDeviceSession;
import com.androidcommunications.polar.common.ble.AtomicSet;
import com.androidcommunications.polar.common.ble.BleUtils;
import com.androidcommunications.polar.enpoints.ble.common.BleDeviceSession2;

import java.util.HashMap;

/**
 * Connection handler handles connection serialization, by using simple state pattern
 */
public class ConnectionHandler {

    /**
     * Connection handler state's
     */
    private enum ConnectionHandlerState{
        FREE,
        CONNECTING
    }

    /**
     * Connection handler current state action
     */
    private enum ConnectionHandlerAction{
        ENTRY,
        EXIT,
        CONNECT_DEVICE,
        ADVERTISEMENT_HEAD_RECEIVED,
        DISCONNECT_DEVICE,
        DEVICE_CONNECTED,
        DEVICE_DISCONNECTED
    }

    private final static String TAG = ConnectionHandler.class.getSimpleName();
    private ConnectionHandlerState state;
    private ScannerInterface scannerInterface;
    private ConnectionInterface connectionInterface;
    private BleDeviceSession2 current;
    private AtomicSet<ConnectionHandlerObserver> observers = new AtomicSet<>();
    private boolean automaticReconnection = true;
    private Handler stateHandler;

    public ConnectionHandler(Context context, ConnectionInterface connectionInterface,
                             ScannerInterface scannerInterface){
        this.stateHandler = new Handler(context.getMainLooper());
        this.scannerInterface = scannerInterface;
        this.connectionInterface = connectionInterface;
        this.state = ConnectionHandlerState.FREE;
    }

    public void setAutomaticReconnection(boolean automaticReconnection) {
        this.automaticReconnection = automaticReconnection;
    }

    public void advertisementHeadReceived(final BleDeviceSession2 bleDeviceSession){
        commandState(bleDeviceSession, ConnectionHandlerAction.ADVERTISEMENT_HEAD_RECEIVED);
    }

    public void connectDevice(BleDeviceSession2 bleDeviceSession, boolean bluetoothEnabled){
        if(bluetoothEnabled) {
            commandState(bleDeviceSession, ConnectionHandlerAction.CONNECT_DEVICE);
        }else{
            switch (bleDeviceSession.getSessionState()){
                case SESSION_CLOSED:
                case SESSION_CLOSING:{
                    updateSessionState(bleDeviceSession, BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
                    break;
                }
            }
        }
    }

    public void disconnectDevice(BleDeviceSession2 bleDeviceSession){
        commandState(bleDeviceSession, ConnectionHandlerAction.DISCONNECT_DEVICE);
    }

    public void deviceConnected(final BleDeviceSession2 bleDeviceSession){
        stateHandler.post(new Runnable() {
            @Override
            public void run() {
                commandState(bleDeviceSession, ConnectionHandlerAction.DEVICE_CONNECTED);
                observers.accessAll(new AtomicSet.ObjectAccess<ConnectionHandlerObserver>() {
                    @Override
                    public void access(ConnectionHandlerObserver object) {
                        object.deviceConnected(bleDeviceSession);
                    }
                });
            }
        });
    }

    public void deviceDisconnected(final BleDeviceSession2 bleDeviceSession){
        stateHandler.post(new Runnable() {
            @Override
            public void run() {
                commandState(bleDeviceSession, ConnectionHandlerAction.DEVICE_DISCONNECTED);
                observers.accessAll(new AtomicSet.ObjectAccess<ConnectionHandlerObserver>() {
                    @Override
                    public void access(ConnectionHandlerObserver object) {
                        object.deviceDisconnected(bleDeviceSession);
                    }
                });
            }
        });
    }

    public void addObserver(ConnectionHandlerObserver connectionHandlerObserver){
        observers.add(connectionHandlerObserver);
    }

    public void removeObserver(ConnectionHandlerObserver connectionHandlerObserver){
        observers.remove(connectionHandlerObserver);
    }

    /**
     * INTERNAL
     */
    private void commandState(BleDeviceSession2 bleDeviceSession, ConnectionHandlerAction action){
        switch (state){
            case FREE:
            {
                free(bleDeviceSession,action);
                break;
            }
            case CONNECTING:
            {
                BleLogger.d(TAG, "state: " + state.toString() + " action: " + action.toString());
                connecting(bleDeviceSession,action);
                break;
            }
        }
    }

    private void changeState(BleDeviceSession2 bleDeviceSession, ConnectionHandlerState newState){
        commandState(bleDeviceSession, ConnectionHandlerAction.EXIT);
        this.state = newState;
        commandState(bleDeviceSession, ConnectionHandlerAction.ENTRY);
    }

    private void updateSessionState(final BleDeviceSession2 bleDeviceSession, BleDeviceSession.DeviceSessionState newState){
        BleLogger.d(TAG, " Session update from: " + bleDeviceSession.getSessionState().toString() + " to: " + newState.toString());
        bleDeviceSession.setSessionState(newState);
        observers.accessAll(new AtomicSet.ObjectAccess<ConnectionHandlerObserver>() {
            @Override
            public void access(ConnectionHandlerObserver object) {
                object.deviceSessionStateChanged(bleDeviceSession);
            }
        });
    }

    private boolean containsRequiredUuids(final BleDeviceSession2 session){
        if( session.getConnectionUuids().size() != 0 ){
            HashMap<BleUtils.AD_TYPE, byte[]> content = session.getAdvertisementContent().getAdvertisementData();
            if (content.containsKey(BleUtils.AD_TYPE.GAP_ADTYPE_16BIT_MORE) ||
                content.containsKey(BleUtils.AD_TYPE.GAP_ADTYPE_16BIT_COMPLETE)) {

                byte[] uuids = content.containsKey(BleUtils.AD_TYPE.GAP_ADTYPE_16BIT_MORE) ?
                        content.get(BleUtils.AD_TYPE.GAP_ADTYPE_16BIT_MORE) :
                        content.get(BleUtils.AD_TYPE.GAP_ADTYPE_16BIT_COMPLETE);

                for(int i=0; i < uuids.length; i += 2){
                    String hexUUid = String.format("%02X%02X",uuids[i+1],uuids[i]);
                    if( session.getConnectionUuids().contains(hexUUid) ){
                        return true;
                    }
                }
            }
            return false;
        }
        return true;
    }

    private void free(final BleDeviceSession2 session, ConnectionHandlerAction action){
        switch (action){
            case ENTRY:
                break;
            case EXIT:
                break;
            case CONNECT_DEVICE:
            {
                // "direct" connection, only aquire direct connection in free state
                switch (session.getSessionState()){
                    case SESSION_OPEN_PARK:
                    case SESSION_CLOSED:{
                        if( session.isConnectableAdvertisement() && containsRequiredUuids(session) ) {
                            changeState(session, ConnectionHandlerState.CONNECTING);
                        }else{
                            updateSessionState(session, BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
                        }
                        break;
                    }
                    case SESSION_CLOSING:{
                        updateSessionState(session, BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
                        break;
                    }
                    case SESSION_OPEN:{
                        updateSessionState(session, BleDeviceSession.DeviceSessionState.SESSION_OPEN);
                        break;
                    }
                }
                break;
            }
            case ADVERTISEMENT_HEAD_RECEIVED:
            {
                // fallback
                if(session.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK){
                    if( session.isConnectableAdvertisement() && containsRequiredUuids(session) ) {
                        changeState(session, ConnectionHandlerState.CONNECTING);
                    }else{
                        BleLogger.d(TAG,"Skipped connection attempt due to reason device is not in connectable advertisement or missing service");
                    }
                }
                break;
            }
            case DISCONNECT_DEVICE:
            {
                handleDisconnectDevice(session);
                break;
            }
            case DEVICE_DISCONNECTED:
            {
                handleDeviceDisconnected(session);
                break;
            }
            case DEVICE_CONNECTED:
            {
                BleLogger.e(TAG, " Incorrect event received! ");
                break;
            }
        }
    }

    private void connecting(final BleDeviceSession2 session, ConnectionHandlerAction action){
        switch (action){
            case ENTRY:
            {
                scannerInterface.connectionHandlerRequestStopScanning();
                current = session;
                updateSessionState(session, BleDeviceSession.DeviceSessionState.SESSION_OPENING);
                connectionInterface.connectDevice(session);
                break;
            }
            case EXIT: {
                scannerInterface.connectionHandlerResumeScanning();
                break;
            }
            case CONNECT_DEVICE:
            {
                if( session.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_CLOSED) {
                    updateSessionState(session, BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
                }
                break;
            }
            case ADVERTISEMENT_HEAD_RECEIVED:
            {
                break;
            }
            case DISCONNECT_DEVICE:
            {
                if( !session.equals(current) ) {
                    handleDisconnectDevice(session);
                }else{
                    // cancel pending connection
                    connectionInterface.cancelDeviceConnection(session);
                    observers.accessAll(new AtomicSet.ObjectAccess<ConnectionHandlerObserver>() {
                        @Override
                        public void access(ConnectionHandlerObserver object) {
                            object.deviceConnectionCancelled(session);
                        }
                    });
                    updateSessionState(session, BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
                    changeState(session, ConnectionHandlerState.FREE);
                }
                break;
            }
            case DEVICE_CONNECTED:
            {
                BleUtils.validate(current == session, "incorrect session object");
                updateSessionState(session, BleDeviceSession.DeviceSessionState.SESSION_OPEN);
                changeState(session, ConnectionHandlerState.FREE);
                break;
            }
            case DEVICE_DISCONNECTED:
            {
                if( current == session ){
                    updateSessionState(session, BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
                    changeState(session, ConnectionHandlerState.FREE);
                }else{
                    handleDeviceDisconnected(session);
                }
                break;
            }
        }
    }

    private void handleDisconnectDevice(BleDeviceSession2 session) {
        switch (session.getSessionState()){
            case SESSION_OPEN_PARK:{
                updateSessionState(session, BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
                break;
            }
            case SESSION_OPEN:{
                updateSessionState(session, BleDeviceSession.DeviceSessionState.SESSION_CLOSING);
                connectionInterface.disconnectDevice(session);
                break;
            }
        }
    }

    private void handleDeviceDisconnected(BleDeviceSession2 session) {
        switch (session.getSessionState()){
            case SESSION_OPEN:{
                if(automaticReconnection) {
                    updateSessionState(session, BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
                }else{
                    updateSessionState(session, BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
                }
                break;
            }
            case SESSION_CLOSING:{
                updateSessionState(session, BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
                break;
            }
        }
    }
}
