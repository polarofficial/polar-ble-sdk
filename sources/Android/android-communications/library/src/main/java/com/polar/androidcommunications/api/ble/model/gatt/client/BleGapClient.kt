package com.polar.androidcommunications.api.ble.model.gatt.client

import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.common.ble.AtomicSet
import com.polar.androidcommunications.common.ble.ChannelUtils
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import java.util.UUID

class BleGapClient(txInterface: BleGattTxInterface) : BleGattBase(txInterface, GAP_SERVICE) {
    private val gapInformation = HashMap<UUID, String>()
    private val gapObserverAtomicList = AtomicSet<Channel<HashMap<UUID, String>>>()

    init {
        addCharacteristicRead(GAP_DEVICE_NAME_CHARACTERISTIC)
        addCharacteristicRead(GAP_APPEARANCE_CHARACTERISTIC)
    }

    override fun reset() {
        super.reset()
        synchronized(gapInformation) {
            gapInformation.clear()
        }
        ChannelUtils.postDisconnectedAndClearList(gapObserverAtomicList)
    }

    override fun processServiceData(
        characteristic: UUID,
        data: ByteArray,
        status: Int,
        notifying: Boolean
    ) {
        if (status == ATT_SUCCESS) {
            synchronized(gapInformation) {
                if (data.isNotEmpty()) {
                    gapInformation[characteristic] = data.toString()
                }
            }

            ChannelUtils.emitNext(gapObserverAtomicList) { observer ->
                val list: HashMap<UUID, String>
                synchronized(gapInformation) {
                    list = HashMap(gapInformation)
                }
                observer.trySend(list)
                if (hasAllAvailableReadableCharacteristics(list.keys)) {
                    observer.close()
                }
            }
        }
    }

    override fun processServiceDataWritten(characteristic: UUID, status: Int) {
        // do nothing
    }

    override fun toString(): String {
        return "GAP service with values device name: "
    }

    /**
     * Produces:  onNext, when a new gap info has been read <BR></BR>
     * onCompleted, after all available gap info has been read ok <BR></BR>
     * onError, if client is not initially connected or ble disconnect's <BR></BR>
     *
     * @param checkConnection, optionally check connection on subscribe <BR></BR>
     * @return Flow stream
     */
    fun observeGapInfo(checkConnection: Boolean): Flow<HashMap<UUID, String>> {
        if (!checkConnection || txInterface.isConnected()) {
            val list: HashMap<UUID, String>
            synchronized(gapInformation) {
                list = HashMap(gapInformation)
            }
            if (list.isNotEmpty() && hasAllAvailableReadableCharacteristics(list.keys)) {
                return flow { emit(list) }
            }
        }

        val initial = flow {
            if (!checkConnection || txInterface.isConnected()) {
                val list: HashMap<UUID, String>
                synchronized(gapInformation) {
                    list = HashMap(gapInformation)
                }
                if (list.isNotEmpty()) {
                    emit(list)
                }
            } else {
                throw BleDisconnected()
            }
        }
        return merge(initial, ChannelUtils.monitorNotifications(gapObserverAtomicList, txInterface, checkConnection))
    }

    companion object {
        var GAP_SERVICE: UUID = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")
        var GAP_DEVICE_NAME_CHARACTERISTIC: UUID =
            UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb")
        var GAP_APPEARANCE_CHARACTERISTIC: UUID =
            UUID.fromString("00002a01-0000-1000-8000-00805f9b34fb")
    }
}