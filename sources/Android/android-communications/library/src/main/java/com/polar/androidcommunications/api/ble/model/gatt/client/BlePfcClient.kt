package com.polar.androidcommunications.api.ble.model.gatt.client

import android.util.Pair
import androidx.annotation.VisibleForTesting
import com.polar.androidcommunications.api.ble.BleLogger.Companion.w
import com.polar.androidcommunications.api.ble.exceptions.BleAttributeError
import com.polar.androidcommunications.api.ble.exceptions.BleCharacteristicNotificationNotEnabled
import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected
import com.polar.androidcommunications.api.ble.exceptions.BleNotSupported
import com.polar.androidcommunications.api.ble.exceptions.BleTimeout
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

class BlePfcClient(txInterface: BleGattTxInterface) : BleGattBase(txInterface, PFC_SERVICE) {
    @VisibleForTesting
    val pfcCpInputQueue: LinkedBlockingQueue<Pair<ByteArray, Int>> = LinkedBlockingQueue()
    private var pfcFeature: PfcFeature? = null
    private val mutexFeature = ReentrantLock()
    private val pfcCpEnabled: AtomicInteger?
    private val pfcMutex = Any()

    enum class PfcMessage(val numVal: Int) {
        PFC_UNKNOWN(0),
        PFC_CONFIGURE_BROADCAST(1),
        PFC_REQUEST_BROADCAST_SETTING(2),
        PFC_CONFIGURE_5KHZ(3),
        PFC_REQUEST_5KHZ_SETTING(4),
        PFC_CONFIGURE_WHISPER_MODE(5),
        PFC_REQUEST_WHISPER_MODE(6),
        PFC_CONFIGURE_BLE_MODE(7),
        PFC_CONFIGURE_MULTI_CONNECTION_SETTING(8),
        PFC_REQUEST_MULTI_CONNECTION_SETTING(9),
        PFC_CONFIGURE_ANT_PLUS_SETTING(10),
        PFC_REQUEST_ANT_PLUS_SETTING(11),
        PFC_REQUEST_SECURITY_MODE(12),
        PFC_CONFIGURE_SENSOR_INITIATED_SECURITY_MODE(14),
        PFC_REQUEST_SENSOR_INITIATED_SECURITY_MODE(15)
    }

    class PfcResponse {
        var responseCode: Byte = 0
            private set
        var opCode: PfcMessage? = null
            private set
        var status: Byte = 0
            private set
        var payload: ByteArray? = null
            private set

        constructor()

        constructor(data: ByteArray) {
            responseCode = data[0]
            opCode = mapOpCode(data[1].toInt())
            status = data[2]
            if (data.size > 3) {
                payload = ByteArray(data.size - 3)
                System.arraycopy(data, 3, payload, 0, data.size - 3)
            }
        }

        override fun toString(): String {
            val stringBuffer = StringBuffer()
            if (payload != null) {
                for (b in payload!!) {
                    stringBuffer.append(String.format("%02x ", b))
                }
            }
            return "Response code: " + String.format("%02x", responseCode) +
                    " op code: " + opCode +
                    " status: " + String.format("%02x", status) + " payload: " + stringBuffer
        }

        private fun mapOpCode(value: Int): PfcMessage {
            for (m in PfcMessage.entries) {
                if (m.numVal == value) {
                    return m
                }
            }
            return PfcMessage.PFC_UNKNOWN
        }
    }

    class PfcFeature : Cloneable {
        // feature boolean's
        var broadcastSupported: Boolean = false
        var khzSupported: Boolean = false
        var whisperModeSupported: Boolean = false
        var otaUpdateSupported: Boolean = false
        var bleModeConfigureSupported: Boolean = false
        var multiConnectionSupported: Boolean = false
        var antSupported: Boolean = false
        var securityModeSupported: Boolean = false
        var sensorInitiatedSecurityModeSupported: Boolean = false

        constructor(data: ByteArray) {
            broadcastSupported = (data[0].toInt() and 0x01) == 1
            khzSupported = ((data[0].toInt() and 0x02) shr 1) == 1
            otaUpdateSupported = ((data[0].toInt() and 0x04) shr 2) == 1
            whisperModeSupported = ((data[0].toInt() and 0x10) shr 4) == 1
            bleModeConfigureSupported = ((data[0].toInt() and 0x40) shr 6) == 1
            multiConnectionSupported = ((data[0].toInt() and 0x80) shr 7) == 1
            antSupported = (data[1].toInt() and 0x01) == 1
            securityModeSupported = ((data[1].toInt() and 0x02) shr 1) == 1
            sensorInitiatedSecurityModeSupported = ((data[1].toInt() and 0x08) shr 3) === 1
        }

        constructor(clone: PfcFeature) {
            this.broadcastSupported = clone.broadcastSupported
            this.khzSupported = clone.khzSupported
            this.otaUpdateSupported = clone.otaUpdateSupported
            this.whisperModeSupported = clone.whisperModeSupported
            this.bleModeConfigureSupported = clone.bleModeConfigureSupported
            this.multiConnectionSupported = clone.multiConnectionSupported
            this.antSupported = clone.antSupported
            this.securityModeSupported = clone.securityModeSupported
        }
    }

    init {
        addCharacteristicRead(PFC_FEATURE)
        addCharacteristicNotification(PFC_CP)
        pfcCpEnabled = getNotificationAtomicInteger(PFC_CP)
    }

    override fun reset() {
        super.reset()
        pfcCpInputQueue.clear()
        synchronized(mutexFeature) {
            pfcFeature = null
            (mutexFeature as Object).notifyAll()
        }
    }

    override fun processServiceData(
        characteristic: UUID,
        data: ByteArray,
        status: Int,
        notifying: Boolean
    ) {
        if (characteristic == PFC_CP) {
            pfcCpInputQueue.add(Pair(data, status))
        } else if (characteristic == PFC_FEATURE) {
            if (status == ATT_SUCCESS) {
                synchronized(mutexFeature) {
                    if (status == 0) {
                        pfcFeature = PfcFeature(data)
                    }
                    (mutexFeature as Object).notifyAll()
                }
            } else {
                w(
                    TAG,
                    "Process service data with status: $status, skipped"
                )
            }
        }
    }

    override fun processServiceDataWritten(characteristic: UUID, status: Int) {
        // add some implementation later if needed
    }

    override fun toString(): String {
        return "PFC service with values broadcast supported: " + pfcFeature?.broadcastSupported + " 5khz supported: " + pfcFeature?.khzSupported
    }

    @Throws(Exception::class)
    private fun sendPfcCommandAndProcessResponse(packet: ByteArray): PfcResponse {
        txInterface.transmitMessages(PFC_SERVICE, PFC_CP, listOf(packet), true)
        val pair = pfcCpInputQueue.poll(30, TimeUnit.SECONDS)
        if (pair != null) {
            if (pair.second == 0) {
                return PfcResponse(pair.first)
            } else {
                throw BleAttributeError("pfc attribute ", pair.second)
            }
        }
        throw BleTimeout("Pfc response failed to receive in timeline")
    }

    // API
    suspend fun sendControlPointCommand(command: PfcMessage, param: Int): PfcResponse {
        return sendControlPointCommand(command, byteArrayOf(param.toByte()))
    }

    override suspend fun clientReady(checkConnection: Boolean) {
        waitNotificationEnabled(PFC_CP, checkConnection)
    }

    /**
     * Send a control point command to the device.
     *
     * @param command see [PfcMessage]
     * @param params  optional parameters, depends on command
     * @return [PfcResponse] on success
     * @throws Throwable on any error
     */
    suspend fun sendControlPointCommand(command: PfcMessage, params: ByteArray? = null): PfcResponse = withContext(Dispatchers.IO) {
        if (params == null) return@withContext PfcResponse()
        synchronized(pfcMutex) {
            if (pfcCpEnabled?.get() == ATT_SUCCESS) {
                pfcCpInputQueue.clear()
                when (command) {
                    PfcMessage.PFC_CONFIGURE_ANT_PLUS_SETTING,
                    PfcMessage.PFC_CONFIGURE_MULTI_CONNECTION_SETTING,
                    PfcMessage.PFC_CONFIGURE_BLE_MODE,
                    PfcMessage.PFC_CONFIGURE_WHISPER_MODE,
                    PfcMessage.PFC_CONFIGURE_BROADCAST,
                    PfcMessage.PFC_CONFIGURE_5KHZ,
                    PfcMessage.PFC_CONFIGURE_SENSOR_INITIATED_SECURITY_MODE -> {
                        val bb = ByteBuffer.allocate(1 + params.size)
                        bb.put(command.numVal.toByte())
                        bb.put(params)
                        return@synchronized sendPfcCommandAndProcessResponse(bb.array())
                    }
                    PfcMessage.PFC_REQUEST_MULTI_CONNECTION_SETTING,
                    PfcMessage.PFC_REQUEST_ANT_PLUS_SETTING,
                    PfcMessage.PFC_REQUEST_WHISPER_MODE,
                    PfcMessage.PFC_REQUEST_BROADCAST_SETTING,
                    PfcMessage.PFC_REQUEST_5KHZ_SETTING,
                    PfcMessage.PFC_REQUEST_SECURITY_MODE,
                    PfcMessage.PFC_REQUEST_SENSOR_INITIATED_SECURITY_MODE -> {
                        val packet = byteArrayOf(command.numVal.toByte())
                        return@synchronized sendPfcCommandAndProcessResponse(packet)
                    }
                    else -> throw BleNotSupported("Unknown pfc command acquired")
                }
            } else {
                throw BleCharacteristicNotificationNotEnabled("PFC control point not enabled")
            }
        }
    }

    /**
     * Read features from PFC service.
     *
     * @return [PfcFeature] on success
     * @throws Throwable on any error
     */
    suspend fun readFeature(): PfcFeature = withContext(Dispatchers.IO) {
        synchronized(mutexFeature) {
            if (pfcFeature == null) {
                (mutexFeature as Object).wait()
            }
            pfcFeature?.let { return@withContext it }
            if (!txInterface.isConnected()) {
                throw BleDisconnected()
            } else {
                throw BleNotSupported("PFC feature read failed")
            }
        }
    }

    companion object {
        private val TAG: String = BlePfcClient::class.java.simpleName

        const val SUCCESS: Byte = 0x01
        const val ERROR_NOT_SUPPORTED: Byte = 0x02
        const val ERROR_INVALID_PARAMETER: Byte = 0x03
        const val ERROR_OPERATION_FAILED: Byte = 0x04
        const val ERROR_NOT_ALLOWED: Byte = 0x05

        const val RESPONSE_CODE: Byte = 0xF0.toByte()

        val PFC_SERVICE: UUID =
            UUID.fromString("6217FF4B-FB31-1140-AD5A-A45545D7ECF3") /* Polar Features Configuration Service (PFCS)*/
        val PFC_FEATURE: UUID = UUID.fromString("6217FF4C-C8EC-B1FB-1380-3AD986708E2D")
        val PFC_CP: UUID = UUID.fromString("6217FF4D-91BB-91D0-7E2A-7CD3BDA8A1F3")
    }
}