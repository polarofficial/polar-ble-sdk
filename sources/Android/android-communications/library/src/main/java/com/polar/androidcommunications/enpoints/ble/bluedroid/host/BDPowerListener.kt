package com.polar.androidcommunications.enpoints.ble.bluedroid.host

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

internal class BDPowerListener(
    private val bluetoothAdapter: BluetoothAdapter,
    private val context: Context,
    powerState: BlePowerState
) {
    internal interface BlePowerState {
        fun blePoweredOff()

        fun blePoweredOn()
    }

    private val powerState: BlePowerState

    fun stopBroadcastReceiver() {
        if (receiver != null) {
            context.unregisterReceiver(receiver)
            receiver = null
        }
    }

    private var receiver: BroadcastReceiver? = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action != null && action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                if (bluetoothAdapter.state == BluetoothAdapter.STATE_OFF) {
                    powerState.blePoweredOff()
                } else if (bluetoothAdapter.state == BluetoothAdapter.STATE_ON) {
                    powerState.blePoweredOn()
                }
            }
        }
    }

    init {
        context.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        this.powerState = powerState
    }
}
