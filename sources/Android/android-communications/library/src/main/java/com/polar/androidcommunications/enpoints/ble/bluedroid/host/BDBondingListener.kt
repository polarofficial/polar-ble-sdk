package com.polar.androidcommunications.enpoints.ble.bluedroid.host

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.polar.androidcommunications.api.ble.BleLogger.Companion.d
import com.polar.androidcommunications.common.ble.AtomicSet


class BDBondingListener(private val context: Context) {
    internal interface AuthenticationObserverInterface {
        // bonding completion
        fun bonding()

        fun bonded()

        fun bondNone()
    }

    private val authenticationObservers: AtomicSet<BondingObserver> = AtomicSet()

    fun stopBroadcastReceiver() {
        if (mReceiver != null) {
            context.unregisterReceiver(mReceiver)
            mReceiver = null
        }
    }

    abstract class BondingObserver(val device: BluetoothDevice) :
        AuthenticationObserverInterface

    fun addObserver(observer: BondingObserver) {
        authenticationObservers.add(observer)
    }

    fun removeObserver(observer: BondingObserver) {
        authenticationObservers.remove(observer)
    }

    private var mReceiver: BroadcastReceiver? = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            if (device != null && action != null) {
                if (action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                    val state =
                        intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    d(TAG, "Bond manager state:$state action: $intent")
                    when (state) {
                        BluetoothDevice.BOND_BONDING -> authenticationObservers.accessAll<BondingObserver> { item: Any ->
                            if ((item  as BondingObserver).device == device) {
                                item.bonding()
                            }
                        }

                        BluetoothDevice.BOND_BONDED -> authenticationObservers.accessAll<BondingObserver> { item: Any ->
                            if ((item  as BondingObserver).device == device) {
                                item.bonded()
                            }
                        }

                        BluetoothDevice.BOND_NONE -> authenticationObservers.accessAll<BondingObserver> { item: Any ->
                            if ((item  as BondingObserver).device == device) {
                                item.bondNone()
                            }
                        }
                    }
                }
            }
        }
    }

    init {
        val intent = IntentFilter()
        intent.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        context.registerReceiver(mReceiver, intent)
    }

    companion object {
        private val TAG: String = BDBondingListener::class.java.simpleName
    }
}
