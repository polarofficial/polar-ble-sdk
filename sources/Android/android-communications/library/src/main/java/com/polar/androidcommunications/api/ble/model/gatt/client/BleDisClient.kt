package com.polar.androidcommunications.api.ble.model.gatt.client

import android.util.Pair
import com.polar.androidcommunications.api.ble.exceptions.BleAttributeError
import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected
import com.polar.androidcommunications.api.ble.model.DisInfo
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.common.ble.AtomicSet
import com.polar.androidcommunications.common.ble.ChannelUtils
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.stream.Collectors

class BleDisClient(txInterface: BleGattTxInterface) : BleGattBase(txInterface, DIS_SERVICE) {
    // store in map
    private val disInformation = HashMap<UUID, String>()
    private val disObserverAtomicList = AtomicSet<Channel<Pair<UUID, String>>>()

    private val disInformationDataSet: MutableSet<DisInfo> = HashSet()
    private val disInfoObservers = AtomicSet<Channel<DisInfo>>()

    init {
        addCharacteristicRead(MODEL_NUMBER_STRING)
        addCharacteristicRead(MANUFACTURER_NAME_STRING)
        addCharacteristicRead(HARDWARE_REVISION_STRING)
        addCharacteristicRead(FIRMWARE_REVISION_STRING)
        addCharacteristicRead(SOFTWARE_REVISION_STRING)
        addCharacteristicRead(SERIAL_NUMBER_STRING)
        addCharacteristicRead(SYSTEM_ID)
        addCharacteristicRead(IEEE_11073_20601)
        addCharacteristicRead(PNP_ID)
    }

    override fun reset() {
        super.reset()
        synchronized(disInformation) {
            disInformation.clear()
            disInformationDataSet.clear()
        }
        ChannelUtils.postDisconnectedAndClearList(disObserverAtomicList)
        ChannelUtils.postDisconnectedAndClearList(disInfoObservers)
    }

    override fun processServiceData(
        characteristic: UUID,
        data: ByteArray,
        status: Int,
        notifying: Boolean
    ) {
        if (data.isNotEmpty() && containsCharacteristicRead(characteristic)) {
            if (status == ATT_SUCCESS) {
                val asciiRepresentation = data.toString(StandardCharsets.UTF_8)
                synchronized(disInformation) {
                    disInformation.put(characteristic, asciiRepresentation)
                }
                synchronized(disInformationDataSet) {
                    if (characteristic == SYSTEM_ID) {
                        val hexRepresentation = systemIdBytesToHex(data)
                        disInformationDataSet.add(DisInfo(SYSTEM_ID_HEX, hexRepresentation))
                    } else {
                        disInformationDataSet.add(
                            DisInfo(
                                characteristic.toString(),
                                asciiRepresentation
                            )
                        )
                    }
                }
                ChannelUtils.emitNext(disObserverAtomicList) { observer ->
                    observer.trySend(
                        Pair(
                            characteristic,
                            String(data, StandardCharsets.UTF_8)
                        )
                    )
                    synchronized(disInformation) {
                        if (hasAllAvailableReadableCharacteristics(disInformation.keys)) {
                            observer.close()
                        }
                    }
                }

                ChannelUtils.emitNext(disInfoObservers) { observer ->
                    disInformationDataSet.stream()
                        .filter { info: DisInfo ->
                            (characteristic == SYSTEM_ID && info.key == SYSTEM_ID_HEX) ||
                                (characteristic.toString() == info.key)
                        }
                        .findFirst().ifPresent { info -> observer.trySend(info) }
                    synchronized(disInformationDataSet) {
                        val validUuids =
                            disInformationDataSet.stream()
                                .map(DisInfo::key)
                                .filter { s: String -> this.isValidUUIDString(s) }
                                .map { name: String? -> UUID.fromString(name) }
                                .collect(Collectors.toSet())
                        if (hasAllAvailableReadableCharacteristics(validUuids)) {
                            observer.close()
                        }
                    }
                }
            } else {
                ChannelUtils.postError(disObserverAtomicList, BleAttributeError("dis ", status))
            }
        }
    }

    override fun processServiceDataWritten(characteristic: UUID, status: Int) {
        // do nothing
    }

    override fun toString(): String {
        return "Device info service"
    }

    /**
     * Produces:  onNext, when a dis data has been read <BR></BR>
     * onCompleted, after all available dis info has been read <BR></BR>
     * onError, if client is not initially connected or ble disconnect's  <BR></BR>
     *
     * @param checkConnection, optionally check connection on subscribe <BR></BR>
     * @return Flow stream <BR></BR>
     */
    fun observeDisInfo(checkConnection: Boolean): Flow<Pair<UUID, String>> {
        val initial = flow {
            if (!checkConnection || txInterface.isConnected()) {
                val snapshot: List<Pair<UUID, String>>
                synchronized(disInformation) {
                    snapshot = disInformation.entries.map { Pair(it.key, it.value) }
                }
                for (item in snapshot) {
                    emit(item)
                }
            } else {
                throw BleDisconnected()
            }
        }
        return merge(initial, ChannelUtils.monitorNotifications(disObserverAtomicList, txInterface, checkConnection))
    }

    /**
     * Produces: onNext, when a [DisInfo] has been read <BR></BR>
     * onCompleted, after all available [DisInfo] has been read <BR></BR>
     * onError, if client is not initially connected or ble disconnect's  <BR></BR>
     *
     * @param checkConnection, optionally check connection on subscribe <BR></BR>
     * @return Flow stream emitting [DisInfo] <BR></BR>
     */
    fun observeDisInfoWithKeysAsStrings(checkConnection: Boolean): Flow<DisInfo> {
        val initial = flow {
            if (!checkConnection || txInterface.isConnected()) {
                val snapshot: List<DisInfo>
                synchronized(disInformationDataSet) {
                    snapshot = disInformationDataSet.toList()
                }
                for (disInfo in snapshot) {
                    emit(disInfo)
                }
            } else {
                throw BleDisconnected()
            }
        }
        return merge(initial, ChannelUtils.monitorNotifications(disInfoObservers, txInterface, checkConnection))
    }

    private fun systemIdBytesToHex(bytes: ByteArray): String {
        val hex = StringBuilder(2 * bytes.size)
        for (i in bytes.indices.reversed()) {
            hex.append(String.format("%02X", bytes[i]))
        }
        return hex.toString()
    }

    private fun isValidUUIDString(s: String): Boolean {
        try {
            UUID.fromString(s)
            return true
        } catch (e: IllegalArgumentException) {
            return false
        }
    }

    companion object {
        private val TAG: String = BleDisClient::class.java.simpleName

        val DIS_SERVICE: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
        val MODEL_NUMBER_STRING: UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
        val MANUFACTURER_NAME_STRING: UUID = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")
        val HARDWARE_REVISION_STRING: UUID = UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb")
        val FIRMWARE_REVISION_STRING: UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")
        val SOFTWARE_REVISION_STRING: UUID = UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb")
        val SERIAL_NUMBER_STRING: UUID = UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb")
        val SYSTEM_ID: UUID = UUID.fromString("00002a23-0000-1000-8000-00805f9b34fb")
        val IEEE_11073_20601: UUID = UUID.fromString("00002a2a-0000-1000-8000-00805f9b34fb")
        val PNP_ID: UUID = UUID.fromString("00002a50-0000-1000-8000-00805f9b34fb")

        const val SYSTEM_ID_HEX: String = "SYSTEM_ID_HEX"
    }
}
