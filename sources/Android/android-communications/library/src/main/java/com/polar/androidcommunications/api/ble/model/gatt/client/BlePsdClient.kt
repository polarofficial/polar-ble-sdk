package com.polar.androidcommunications.api.ble.model.gatt.client

import android.util.Pair
import com.polar.androidcommunications.api.ble.BleLogger.Companion.w
import com.polar.androidcommunications.api.ble.exceptions.BleAttributeError
import com.polar.androidcommunications.api.ble.exceptions.BleCharacteristicNotificationNotEnabled
import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected
import com.polar.androidcommunications.api.ble.exceptions.BleNotSupported
import com.polar.androidcommunications.api.ble.exceptions.BleTimeout
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.common.ble.AtomicSet
import com.polar.androidcommunications.common.ble.ChannelUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Arrays
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class BlePsdClient(txInterface: BleGattTxInterface) : BleGattBase(txInterface, PSD_SERVICE) {
    private val psdMutex = Any()
    private val psdCpEnabled: AtomicInteger?
    private var psdFeature: PsdFeature? = null
    private val mutexFeature = Any()
    private val psdCpInputQueue = LinkedBlockingQueue<Pair<ByteArray, Int>>()

    private val ppObservers = AtomicSet<Channel<PPData>>()

    enum class PsdMessage(val numVal: Int) {
        PSD_UNKNOWN(0),
        PSD_START_OHR_PP_STREAM(7),
        PSD_STOP_OHR_PP_STREAM(8)
    }

    class PsdData(val payload: ByteArray)

    class PPData(data: ByteArray) {
        val rc: Int = (data[0].toLong() and 0xFFL).toInt()
        val hr: Int = (data[1].toLong() and 0xFFL).toInt()
        val ppInMs: Int =
            ((data[2].toLong() and 0xFFL) or ((data[3].toLong() and 0xFFL) shl 8)).toInt()
        val ppErrorEstimate: Int =
            ((data[4].toLong() and 0xFFL) or ((data[5].toLong() and 0xFFL) shl 8)).toInt()
        val blockerBit: Int = data[6].toInt() and 0x01
        val skinContactStatus: Int = (data[6].toInt() and 0x02) shr 1
        val skinContactSupported: Int = (data[6].toInt() and 0x04) shr 2
    }

    class PsdResponse {
        var responseCode: Byte = 0
            private set
        var opCode: PsdMessage? = null
            private set
        var status: Byte = 0
            private set
        var payload: Byte = 0
            private set

        constructor()

        constructor(data: ByteArray) {
            if (data.size > 2) {
                responseCode = data[0]
                opCode = PsdMessage.entries[data[1].toInt()]
                status = data[2]
                if (data.size > 3) {
                    payload = data[3]
                }
            } else {
                opCode = PsdMessage.PSD_UNKNOWN
                responseCode = 0
                status = 0
                payload = 0
            }
        }

        override fun toString(): String {
            return "Response code: " + String.format("%02x", responseCode) +
                    " op code: " + opCode +
                    " status: " + String.format("%02x", status) +
                    " payload: " + String.format("%02x", payload)
        }
    }

    class PsdFeature {
        // feature boolean's
        var ecgSupported: Boolean = false
        var accSupported: Boolean = false
        var ohrSupported: Boolean = false
        var ppSupported: Boolean = false

        constructor()

        constructor(data: ByteArray) {
            ecgSupported = (data[0].toInt() and 0x01) == 1
            ohrSupported = ((data[0].toInt() and 0x02) shr 1) == 1
            accSupported = ((data[0].toInt() and 0x04) shr 2) == 1
            ppSupported = ((data[0].toInt() and 0x08) != 0)
        }

        internal constructor(clone: PsdFeature) {
            ecgSupported = clone.ecgSupported
            ohrSupported = clone.ohrSupported
            accSupported = clone.accSupported
            ppSupported = clone.ppSupported
        }
    }

    init {
        addCharacteristicRead(PSD_FEATURE)
        addCharacteristicNotification(PSD_CP)
        addCharacteristicNotification(PSD_PP)
        psdCpEnabled = getNotificationAtomicInteger(PSD_CP)
    }

    override fun reset() {
        super.reset()
        psdCpInputQueue.clear()

        synchronized(mutexFeature) {
            psdFeature = null
            (mutexFeature as Object).notifyAll()
        }

        ChannelUtils.postDisconnectedAndClearList(ppObservers)
    }

    override fun processServiceData(
        characteristic: UUID,
        data: ByteArray,
        status: Int,
        notifying: Boolean
    ) {
        if (data.isNotEmpty()) {
            if (characteristic == PSD_CP) {
                psdCpInputQueue.add(Pair(data, status))
            } else if (characteristic == PSD_FEATURE) {
                synchronized(mutexFeature) {
                    if (status == ATT_SUCCESS) {
                        psdFeature = PsdFeature(data)
                    } else {
                        w(
                            TAG,
                            "Process service data for feature characteristics with status $status, skipped"
                        )
                    }
                    (mutexFeature as Object).notifyAll()
                }
            } else if (status == ATT_SUCCESS) {
                if (characteristic == PSD_PP) {
                    val list = splitPP(data)
                    for (packet in list) {
                        ChannelUtils.emitNext(ppObservers) { observer -> observer.trySend(PPData(packet)) }
                    }
                }
            } else {
                w(
                    TAG,
                    "Process service data with status $status, skipped"
                )
            }
        }
    }

    private fun splitPP(data: ByteArray): List<ByteArray> {
        var offset = 0
        val components: MutableList<ByteArray> = ArrayList()
        while (offset < data.size) {
            components.add(Arrays.copyOfRange(data, offset, offset + 7))
            offset += 7
        }
        return components
    }

    override fun processServiceDataWritten(characteristic: UUID, status: Int) {
        // add some implementation later if needed
    }

    override fun toString(): String {
        return "psd client"
    }

    @Throws(Exception::class)
    private fun sendPsdCommandAndProcessResponse(packet: ByteArray): PsdResponse {
        txInterface.transmitMessages(PSD_SERVICE, PSD_CP, listOf(packet), true)
        val pair = psdCpInputQueue.poll(30, TimeUnit.SECONDS)
        if (pair != null) {
            if (pair.second == 0) {
                return PsdResponse(pair.first)
            } else {
                throw BleAttributeError("Psd attribute ", pair.second)
            }
        }
        throw BleTimeout("Psd response failed in receive in timeline")
    }

    // API
    override suspend fun clientReady(checkConnection: Boolean) {
        waitNotificationEnabled(PSD_CP, checkConnection)
    }

    /**
     * Send a control point command and return the device response.
     *
     * @param command psd command
     * @param params  optional parameters if any
     * @return [PsdResponse] on success
     * @throws Throwable on any error
     */
    suspend fun sendControlPointCommand(command: PsdMessage, params: ByteArray? = null): PsdResponse = withContext(Dispatchers.IO) {
        synchronized(psdMutex) {
            if (psdCpEnabled?.get() == ATT_SUCCESS) {
                psdCpInputQueue.clear()
                val packet = byteArrayOf(command.numVal.toByte())
                sendPsdCommandAndProcessResponse(packet)
            } else {
                throw BleCharacteristicNotificationNotEnabled("PSD control point not enabled")
            }
        }
    }

    /**
     * Read the PSD feature characteristic from the device.
     *
     * @return [PsdFeature] on success
     * @throws Throwable on any error
     */
    suspend fun readFeature(): PsdFeature = withContext(Dispatchers.IO) {
        synchronized(mutexFeature) {
            if (psdFeature == null) {
                (mutexFeature as Object).wait()
            }
            psdFeature?.let { return@withContext it }
            if (!txInterface.isConnected()) {
                throw BleDisconnected()
            } else {
                throw BleNotSupported("PSD feature characteristic read failed")
            }
        }
    }

    /**
     * start raw pp monitoring
     *
     * @return Flow stream Produces:
     * - onNext for every air packet received <BR></BR>
     * - onComplete non produced if stream is not further configured <BR></BR>
     * - onError BleDisconnected produced on disconnection <BR></BR>
     */
    fun monitorPPNotifications(checkConnection: Boolean): Flow<PPData> {
        return ChannelUtils.monitorNotifications(ppObservers, txInterface, checkConnection)
    }

    companion object {
        private const val TAG = "BlePsdClient"
        const val SUCCESS: Byte = 0x01
        const val OP_CODE_NOT_SUPPORTED: Byte = 0x02
        const val INVALID_PARAMETER: Byte = 0x03
        const val OPERATION_FAILED: Byte = 0x04
        const val NOT_ALLOWED: Byte = 0x05

        val PSD_SERVICE: UUID = UUID.fromString("FB005C20-02E7-F387-1CAD-8ACD2D8DF0C8")
        val PSD_FEATURE: UUID = UUID.fromString("FB005C21-02E7-F387-1CAD-8ACD2D8DF0C8")
        val PSD_CP: UUID = UUID.fromString("FB005C22-02E7-F387-1CAD-8ACD2D8DF0C8")
        val PSD_PP: UUID = UUID.fromString("FB005C26-02E7-F387-1CAD-8ACD2D8DF0C8")

        const val OP_CODE_START_ECG_STREAM: Byte = 0x01
        const val OP_CODE_STOP_ECG_STREAM: Byte = 0x02
        const val OP_CODE_START_OHR_STREAM: Byte = 0x03
        const val OP_CODE_STOP_OHR_STREAM: Byte = 0x04
        const val OP_CODE_START_ACC_STREAM: Byte = 0x05
        const val OP_CODE_STOP_ACC_STREAM: Byte = 0x06
        const val RESPONSE_CODE: Byte = 0xF0.toByte()
    }
}
