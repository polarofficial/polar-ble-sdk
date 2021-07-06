package com.polar.androidcommunications.enpoints.ble.bluedroid.host;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

class BDPowerListener {

    interface BlePowerState {
        void blePoweredOff();

        void blePoweredOn();
    }

    private final BluetoothAdapter bluetoothAdapter;
    private final BlePowerState powerState;
    private final Context context;

    BDPowerListener(BluetoothAdapter bluetoothAdapter, Context context, BlePowerState powerState) {
        this.bluetoothAdapter = bluetoothAdapter;
        this.context = context;
        context.registerReceiver(receiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        this.powerState = powerState;
    }

    void stopBroadcastReceiver() {
        if (receiver != null) {
            context.unregisterReceiver(receiver);
            receiver = null;
        }
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null && action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF) {
                    powerState.blePoweredOff();
                } else if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_ON) {
                    powerState.blePoweredOn();
                }
            }
        }
    };
}
