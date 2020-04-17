package com.androidcommunications.polar.enpoints.ble.common;

import android.content.Context;
import android.support.annotation.IntRange;
import android.support.annotation.Nullable;
import android.util.Pair;

import com.androidcommunications.polar.api.ble.BleDeviceListener;
import com.androidcommunications.polar.api.ble.model.BleDeviceSession;
import com.androidcommunications.polar.api.ble.model.gatt.BleGattBase;
import com.androidcommunications.polar.enpoints.ble.common.connection.ConnectionHandler;
import com.androidcommunications.polar.enpoints.ble.common.connection.ConnectionHandlerObserver;
import com.androidcommunications.polar.enpoints.ble.common.connection.ConnectionInterface;
import com.androidcommunications.polar.enpoints.ble.common.connection.ScannerInterface;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Action;

public abstract class BleDeviceListener2 extends BleDeviceListener implements ScannerInterface, ConnectionInterface {

    protected ConnectionHandler connectionHandler;
    protected Context context;
    private BleDeviceSessionStateChangedCallback changedCallback = null;
    protected BlePowerStateChangedCallback powerStateChangedCallback = null;

    public BleDeviceListener2(final Context context, Set<Class<? extends BleGattBase> > clients) {
        super(clients);
        this.context = context;
        this.connectionHandler = new ConnectionHandler(context,this,this);
        this.connectionHandler.addObserver(new ConnectionHandlerObserver() {
            @Override
            public void deviceSessionStateChanged(final BleDeviceSession2 session) {
                if (changedCallback != null) {
                    if (session.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK &&
                            session.getPreviousState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN) {
                        //NOTE special case, we were connected so propagate closed event( informal )
                        changedCallback.stateChanged(session, BleDeviceSession.DeviceSessionState.SESSION_CLOSED);
                        if (session.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK) {
                            changedCallback.stateChanged(session, BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK);
                        }
                    } else {
                        changedCallback.stateChanged(session, session.getSessionState());
                    }
                }
            }

            @Override
            public void deviceConnected(BleDeviceSession2 session) {
                session.discoverServices();
            }

            @Override
            public void deviceDisconnected(BleDeviceSession2 session) {
                session.handleDisconnection();
                session.reset();
            }

            @Override
            public void deviceConnectionCancelled(BleDeviceSession2 session) {
                session.reset();
            }
        });
    }

    @Override
    public void shutDown(){

    }

    @Override
    public void setBlePowerStateCallback(@Nullable BlePowerStateChangedCallback cb) {
        this.powerStateChangedCallback = cb;
        if (cb != null) {
            cb.stateChanged(this.bleActive());
        }
    }

    @Override
    public void setDeviceSessionStateChangedCallback(@Nullable BleDeviceSessionStateChangedCallback changedCallback) {
        this.changedCallback = changedCallback;
    }

    @Override
    public void openSessionDirect(BleDeviceSession session){
        session.setConnectionUuids(new ArrayList<String>());
        connectionHandler.connectDevice((BleDeviceSession2)session,bleActive());
    }

    @Override
    public void openSessionDirect(BleDeviceSession session, List<String> uuids){
        session.setConnectionUuids(uuids);
        connectionHandler.connectDevice((BleDeviceSession2)session,bleActive());
    }

    @Override
    public Observable<Pair<BleDeviceSession, BleDeviceSession.DeviceSessionState> > monitorDeviceSessionState(final BleDeviceSession session) {
        final ConnectionHandlerObserver[] observer = new ConnectionHandlerObserver[1];
        return Observable.create(new ObservableOnSubscribe<Pair<BleDeviceSession, BleDeviceSession.DeviceSessionState>>() {
            @Override
            public void subscribe(final @NonNull ObservableEmitter<Pair<BleDeviceSession, BleDeviceSession.DeviceSessionState>> subscriber) {
                observer[0] = new ConnectionHandlerObserver() {
                    @Override
                    public void deviceSessionStateChanged(BleDeviceSession2 session1) {
                        if (session == null || session == session1) {
                            if( session1.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK &&
                                session1.getPreviousState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN ){
                                //NOTE special case, we were connected so propagate closed event( informal )
                                subscriber.onNext(new Pair<>((BleDeviceSession) session1, BleDeviceSession.DeviceSessionState.SESSION_CLOSED));
                                if(session1.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK) {
                                    subscriber.onNext(new Pair<>((BleDeviceSession)session1, BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK));
                                }
                            } else {
                                subscriber.onNext(new Pair<>((BleDeviceSession)session1, session1.getSessionState()));
                            }
                        }
                    }

                    @Override
                    public void deviceConnected(BleDeviceSession2 session) {
                        // do nothing
                    }

                    @Override
                    public void deviceDisconnected(BleDeviceSession2 session) {
                        // do nothing
                    }

                    @Override
                    public void deviceConnectionCancelled(BleDeviceSession2 session) {

                    }
                };
                connectionHandler.addObserver(observer[0]);
            }
        }).doFinally(new Action() {
            @Override
            public void run() {
                connectionHandler.removeObserver(observer[0]);
            }
        }).subscribeOn(AndroidSchedulers.from(context.getMainLooper()));
    }

    @Override
    public void closeSessionDirect(BleDeviceSession session){
        connectionHandler.disconnectDevice((BleDeviceSession2)session);
    }

    @Override
    public void setAutomaticReconnection(boolean automaticReconnection){
        connectionHandler.setAutomaticReconnection(automaticReconnection);
    }
}
