package com.androidcommunications.polar.enpoints.ble.common;

import com.androidcommunications.polar.api.ble.BleLogger;
import com.androidcommunications.polar.api.ble.model.BleDeviceSession;
import com.androidcommunications.polar.enpoints.ble.common.attribute.AttributeOperation;

import io.reactivex.functions.Action;

public abstract class BleDeviceSession2 extends BleDeviceSession {
    public static final String TAG = BleDeviceSession2.class.getSimpleName();

    public BleDeviceSession2() {
        super();
    }

    protected void logIfError(final String message, int status){
        if(status!=0) {
            BleLogger.e(TAG, message + " Failed with error: " + status);
        }
    }

    /**
     * Internal use only
     * @param sessionState @see BleDeviceSession.DeviceSessionState
     */
    public void setSessionState(BleDeviceSession.DeviceSessionState sessionState) {
        this.previousState = this.state;
        this.state = sessionState;
    }

    public abstract boolean sendNextAttributeOperation(AttributeOperation attributeOperation) throws Throwable;
    public abstract void discoverServices();
    public abstract void handleDisconnection();
    public abstract void startAuthentication(Action complete);
    public abstract void reset();
}
