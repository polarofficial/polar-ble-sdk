package com.androidcommunications.polar.enpoints.ble.bluedroid.host;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Context;
import android.os.Build;
import android.os.Handler;

import com.androidcommunications.polar.api.ble.BleLogger;
import com.androidcommunications.polar.common.ble.RxUtils;
import com.androidcommunications.polar.enpoints.ble.bluedroid.host.connection.ConnectionHandler;

import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;

class BDGattCallback extends BluetoothGattCallback {

    private final static String TAG = BDGattCallback.class.getSimpleName();
    private int POLAR_MAX_MTU = 512;

    private ConnectionHandler connectionHandler;
    private BDDeviceList sessions;
    private Scheduler scheduler;
    private Handler handler;

    BDGattCallback(Context context, ConnectionHandler connectionHandler, BDDeviceList sessions) {
        this.scheduler = AndroidSchedulers.from(context.getMainLooper());
        this.handler = new Handler(context.getMainLooper());
        this.connectionHandler = connectionHandler;
        this.sessions = sessions;
    }

    void setPolarMaxMtu(int mtu) {
        POLAR_MAX_MTU = mtu;
    }

    @Override
    public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
        final BDDeviceSessionImpl smartPolarDeviceSession = sessions.getSession(gatt);
        BleLogger.d(TAG, "GATT state changed device newState: " + newState + " status: " + status);
        if (smartPolarDeviceSession != null) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    handler.post(() -> connectionHandler.deviceConnected(smartPolarDeviceSession));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        gatt.setPreferredPhy(BluetoothDevice.PHY_LE_2M_MASK, BluetoothDevice.PHY_LE_2M_MASK, BluetoothDevice.PHY_OPTION_NO_PREFERRED);
                    }
                    if (smartPolarDeviceSession.isAuthenticated()) {
                        smartPolarDeviceSession.getSubscriptions().add(Observable.timer(600, TimeUnit.MILLISECONDS, Schedulers.newThread()).observeOn(scheduler).subscribe(
                                aLong -> {
                                },
                                throwable -> BleLogger.e(TAG, "Wait encryption start failed: " + throwable.getLocalizedMessage()),
                                () -> startDiscovery(smartPolarDeviceSession, gatt)));
                    } else {
                        handler.post(() -> startDiscovery(smartPolarDeviceSession, gatt));
                    }
                } else {
                    handler.post(() -> connectionHandler.deviceDisconnected(smartPolarDeviceSession));
                }
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                handler.post(() -> connectionHandler.deviceDisconnected(smartPolarDeviceSession));
            }
        } else {
            BleLogger.e(TAG, "Dead gatt object received");
            if (gatt != null) {
                gatt.close();
            }
        }
    }

    @Override
    public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);
        final BDDeviceSessionImpl bdDeviceSession = sessions.getSession(gatt);
        if (bdDeviceSession != null) {
            handler.post(() -> {
                if (bdDeviceSession.serviceDiscovery != null) {
                    bdDeviceSession.serviceDiscovery.dispose();
                    bdDeviceSession.serviceDiscovery = null;
                }
            });
            if (status == BluetoothGatt.GATT_SUCCESS) {
                bdDeviceSession.handleServicesDiscovered();
                gatt.requestMtu(POLAR_MAX_MTU);
            } else {
                BleLogger.e(TAG, "service discovery failed: " + status);
            }
        } else {
            BleLogger.e(TAG, "services discovered on non known gatt");
        }
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicRead(gatt, characteristic, status);
        final BDDeviceSessionImpl session = sessions.getSession(gatt);
        if (session != null) {
            session.handleCharacteristicRead(characteristic.getService(), characteristic, characteristic.getValue(), status);
        } else {
            BleLogger.e(TAG, "Dead gatt event?");
            if (gatt != null) {
                gatt.close();
            }
        }
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicWrite(gatt, characteristic, status);
        final BDDeviceSessionImpl session = sessions.getSession(gatt);
        if (session != null) {
            session.handleCharacteristicWrite(characteristic.getService(), characteristic, status);
        } else {
            BleLogger.e(TAG, "Dead gatt event?");
            if (gatt != null) {
                gatt.close();
            }
        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);
        final BDDeviceSessionImpl session = sessions.getSession(gatt);
        if (session != null) {
            session.handleCharacteristicValueUpdated(characteristic.getService(), characteristic, characteristic.getValue());
        } else {
            BleLogger.e(TAG, "Dead gatt event?");
            if (gatt != null) {
                gatt.close();
            }
        }
    }

    @Override
    public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorRead(gatt, descriptor, status);
        final BDDeviceSessionImpl session = sessions.getSession(gatt);
        if (session != null) {
            session.handleDescriptorRead(descriptor, descriptor.getValue(), status);
        } else {
            BleLogger.e(TAG, "Dead gatt event?");
            if (gatt != null) {
                gatt.close();
            }
        }
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorWrite(gatt, descriptor, status);
        final BDDeviceSessionImpl session = sessions.getSession(gatt);
        if (session != null) {
            session.handleDescriptorWrite(descriptor.getCharacteristic().getService(), descriptor.getCharacteristic(), descriptor, descriptor.getValue(), status);
        } else {
            BleLogger.e(TAG, "Dead gatt event?");
            if (gatt != null) {
                gatt.close();
            }
        }
    }

    @Override
    public void onReadRemoteRssi(final BluetoothGatt gatt, final int rssi, int status) {
        super.onReadRemoteRssi(gatt, rssi, status);
        BleLogger.d(TAG, "onReadRemoteRssi status: " + status);
        final BDDeviceSessionImpl session = sessions.getSession(gatt);
        if (session != null) {
            RxUtils.emitNext(session.getRssiObservers(),
                    object -> object.onSuccess(rssi));
        } else {
            BleLogger.e(TAG, "Dead gatt event?");
            if (gatt != null) {
                gatt.close();
            }
        }
    }

    @Override
    public void onMtuChanged(final BluetoothGatt gatt, int mtu, int status) {
        super.onMtuChanged(gatt, mtu, status);
        BleLogger.d(TAG, "onMtuChanged status: " + status);
        final BDDeviceSessionImpl smartDeviceSession = sessions.getSession(gatt);
        if (smartDeviceSession != null) {
            smartDeviceSession.handleMtuChanged(mtu, status);
            if (smartDeviceSession.isAuthenticationNeeded() && !smartDeviceSession.isAuthenticated()) {
                BleLogger.d(TAG, "Services discovered authentication is needed");
                // first mtu exchange
                smartDeviceSession.startAuthentication(smartDeviceSession::handleAuthenticationComplete);
            } else {
                BleLogger.d(TAG, "Services discovered authentication is not needed");
                smartDeviceSession.processNextAttributeOperation(false);
            }
        } else {
            BleLogger.e(TAG, "Dead gatt event?");
            if (gatt != null) {
                gatt.close();
            }
        }
    }

    @Override
    public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
        super.onPhyUpdate(gatt, txPhy, rxPhy, status);
        BleLogger.d(TAG, " phy updated tx: " + txPhy + " rx: " + rxPhy + " status: " + status);
    }

    @Override
    public void onPhyRead(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
        super.onPhyRead(gatt, txPhy, rxPhy, status);
        BleLogger.d(TAG, " phy read tx: " + txPhy + " rx: " + rxPhy + " status: " + status);
    }

    private void startDiscovery(BDDeviceSessionImpl smartDeviceSession, final BluetoothGatt gatt) {
        gatt.discoverServices();
        if (smartDeviceSession.serviceDiscovery != null) {
            smartDeviceSession.serviceDiscovery.dispose();
        }
        smartDeviceSession.serviceDiscovery = Observable.timer(10, TimeUnit.SECONDS, Schedulers.newThread()).observeOn(scheduler).subscribe(
                aLong -> {
                },
                throwable -> BleLogger.e(TAG, "service discovery timer failed: " + throwable.getLocalizedMessage()),
                () -> onServicesDiscovered(gatt, 0));
    }
}
