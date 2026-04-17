package com.polar.androidcommunications.api.ble.model.gatt.client.psftp

import androidx.annotation.VisibleForTesting
import androidx.core.util.Pair
import com.polar.androidcommunications.api.ble.BleLogger.Companion.d
import com.polar.androidcommunications.api.ble.BleLogger.Companion.e
import com.polar.androidcommunications.api.ble.BleLogger.Companion.w
import com.polar.androidcommunications.api.ble.exceptions.BleAttributeError
import com.polar.androidcommunications.api.ble.exceptions.BleCharacteristicNotificationNotEnabled
import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.PftpNotificationMessage
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.PftpOperationTimeout
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.PftpResponseError
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.PftpRfc76ResponseHeader
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.Rfc76SequenceNumber
import com.polar.androidcommunications.api.ble.model.proto.CommunicationsPftpRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Polar simple file transfer client declaration.
 */
class BlePsFtpClient(txInterface: BleGattTxInterface) :
    BleGattBase(txInterface, BlePsFtpUtils.RFC77_PFTP_SERVICE, true) {
    private val pftpMtuEnabled: AtomicInteger?
    private val pftpD2HNotificationEnabled: AtomicInteger?
    private val mtuInputQueue = LinkedBlockingQueue<Pair<ByteArray, Int>>()
    private val notificationInputQueue = LinkedBlockingQueue<Pair<ByteArray, Int>>()
    private val packetsWritten = AtomicInteger(0)
    private val packetsWrittenWithResponse = AtomicInteger(0)
    private val mtuWaiting = AtomicBoolean(false)
    private val currentOperationWrite = AtomicBoolean(false)
    private val notificationWaiting = AtomicBoolean(false)
    private val notificationPacketsWritten = AtomicInteger(0)
    private val packetsCount = AtomicInteger(5) // default every 5th packet is written with response
    private val extendedWriteTimeoutFilePaths = listOf("/SYNCPART.TGZ")

    /**
     * true  = uses attribute operation WRITE
     * false = uses attribute operation WRITE_NO_RESPONSE
     */
    private val useAttributeLevelResponse = AtomicBoolean(false)
    private val pftpOperationMutex = Any()
    private val pftpNotificationMutex = Any()
    private val pftpWaitNotificationMutex = Any()
    private val pftpWaitNotificationSharedMutex = Any()
    @Volatile private var _sharedWaitNotificationFlow: Flow<PftpNotificationMessage>? = null

    fun interface ProgressCallback {
        fun onProgressUpdate(bytesReceived: Long)
    }

    private var progressCallback: ProgressCallback? = null

    init {
        addCharacteristicNotification(BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC)
        addCharacteristicNotification(BlePsFtpUtils.RFC77_PFTP_D2H_CHARACTERISTIC)
        addCharacteristic(BlePsFtpUtils.RFC77_PFTP_H2D_CHARACTERISTIC)
        pftpMtuEnabled = getNotificationAtomicInteger(BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC)
        pftpD2HNotificationEnabled =
            getNotificationAtomicInteger(BlePsFtpUtils.RFC77_PFTP_D2H_CHARACTERISTIC)
        isPrimaryService = true
    }

    fun setProgressCallback(callback: ProgressCallback?) {
        this.progressCallback = callback
    }

    /**
     * Set amount of packets written consecutive with BLE ATT WRITE before adding the BLE ATT WRITE REQUEST.
     *
     * @param count number of packets
     */
    fun setPacketsCount(count: Int) {
        packetsCount.set(count)
    }

    fun getPacketsCount(): Int {
        return packetsCount.get()
    }

    override fun reset() {
        super.reset()
        currentOperationWrite.set(false)
        mtuInputQueue.clear()
        synchronized(mtuInputQueue) {
            (mtuInputQueue as Object).notifyAll()
        }

        packetsWritten.set(0)
        synchronized(packetsWritten) {
            (packetsWritten as Object).notifyAll()
        }

        packetsWrittenWithResponse.set(0)
        synchronized(packetsWrittenWithResponse) {
            (packetsWrittenWithResponse as Object).notifyAll()
        }

        notificationInputQueue.clear()
        synchronized(notificationInputQueue) {
            (notificationInputQueue as Object).notifyAll()
        }

        notificationPacketsWritten.set(0)
        synchronized(notificationPacketsWritten) {
            (notificationPacketsWritten as Object).notifyAll()
        }

        mtuWaiting.set(false)
        notificationWaiting.set(false)
    }

    override fun processServiceData(
        characteristic: UUID,
        data: ByteArray,
        status: Int,
        notifying: Boolean
    ) {
        if (status != ATT_SUCCESS) {
            w(TAG, "Process service data with status $status, skipping data")
        } else if (data.isNotEmpty()) {
            if (characteristic == BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC) {
                synchronized(mtuInputQueue) {
                    mtuInputQueue.add(Pair(data, status))
                    (mtuInputQueue as Object).notifyAll()
                }

                progressCallback?.onProgressUpdate(data.size.toLong())

                if (currentOperationWrite.get() && mtuWaiting.get() && data.size == 3) {
                    // special case stream cancellation has been received before att response
                    synchronized(packetsWritten) {
                        packetsWritten.incrementAndGet()
                        (packetsWritten as Object).notifyAll()
                    }
                }
            } else if (characteristic == BlePsFtpUtils.RFC77_PFTP_D2H_CHARACTERISTIC) {
                synchronized(notificationInputQueue) {
                    notificationInputQueue.add(Pair(data, status))
                    (notificationInputQueue as Object).notifyAll()
                }
            }
        } else {
            e(TAG, "Received 0 length packet")
        }
    }

    override fun processServiceDataWritten(characteristic: UUID, status: Int) {
        if (status == 0) {
            if (characteristic == BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC) {
                synchronized(packetsWritten) {
                    packetsWritten.incrementAndGet()
                    (packetsWritten as Object).notifyAll()
                }
            } else if (characteristic == BlePsFtpUtils.RFC77_PFTP_H2D_CHARACTERISTIC) {
                synchronized(notificationPacketsWritten) {
                    notificationPacketsWritten.incrementAndGet()
                    (notificationPacketsWritten as Object).notifyAll()
                }
            }
        } else {
            e(TAG, "Failed to write chr UUID: $characteristic status: $status")
        }
    }

    override fun processServiceDataWrittenWithResponse(characteristic: UUID, status: Int) {
        if (status == 0) {
            if (characteristic == BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC) {
                synchronized(packetsWrittenWithResponse) {
                    packetsWrittenWithResponse.incrementAndGet()
                    (packetsWrittenWithResponse as Object).notifyAll()
                }
            }
        }
        processServiceDataWritten(characteristic, status)
    }

    override fun toString(): String {
        return "RFC77 Service"
    }

    private fun resetMtuPipe() {
        d(TAG, "mtu reseted")
        mtuInputQueue.clear()
        packetsWritten.set(0)
        mtuWaiting.set(false)
    }

    private fun resetNotificationPipe() {
        notificationPacketsWritten.set(0)
        notificationWaiting.set(false)
    }

    override suspend fun clientReady(checkConnection: Boolean) {
        waitPsFtpClientReady(checkConnection)
    }

    /**
     * Sends a request to the device and returns the response.
     *
     * @param header protobuf pftp operation bytes
     * @param progressCallback optional callback for progress updates
     * @return [ByteArrayOutputStream] containing the response
     * @throws Throwable on any transfer error
     */
    suspend fun request(
        header: ByteArray,
        progressCallback: ProgressCallback? = null
    ): ByteArrayOutputStream = withContext(Dispatchers.IO) {
        txInterface.gattClientRequestStopScanning()
        try {
            var requestData: MutableList<ByteArray> = ArrayList()
            var previousCallback: ProgressCallback? = null
            synchronized(pftpOperationMutex) {
                if (pftpMtuEnabled?.get() == ATT_SUCCESS) {
                    d(TAG, "Start request")
                    previousCallback = this@BlePsFtpClient.progressCallback
                    if (progressCallback != null) {
                        this@BlePsFtpClient.progressCallback = progressCallback
                    }
                    try {
                        resetMtuPipe()
                        val totalStream = BlePsFtpUtils.makeCompleteMessageStream(
                            ByteArrayInputStream(header), null,
                            BlePsFtpUtils.MessageType.REQUEST, 0
                        )
                        val sequenceNumber = Rfc76SequenceNumber()
                        requestData = BlePsFtpUtils.buildRfc76MessageFrameAll(
                            totalStream, mtuSize.get(), sequenceNumber
                        ).toMutableList()
                        val outputStream = ByteArrayOutputStream()
                        txInterface.transmitMessages(
                            BlePsFtpUtils.RFC77_PFTP_SERVICE,
                            BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC,
                            requestData, false
                        )
                        waitPacketsWritten(
                            packetsWritten, mtuWaiting, requestData.size,
                            PROTOCOL_TIMEOUT_SECONDS.toLong()
                        )
                        requestData.clear()
                        readResponse(outputStream, PROTOCOL_TIMEOUT_SECONDS.toLong())
                        this@BlePsFtpClient.progressCallback = previousCallback
                        return@synchronized outputStream
                    } catch (ex: InterruptedException) {
                        e(TAG, "Request interrupted. Exception: " + ex.message)
                        this@BlePsFtpClient.progressCallback = previousCallback
                        handleMtuInterrupted(true, requestData.size)
                        throw ex
                    } catch (ex: Exception) {
                        if (previousCallback !== this@BlePsFtpClient.progressCallback) {
                            this@BlePsFtpClient.progressCallback = previousCallback
                        }
                        throw toPftpException(ex)
                    }
                } else {
                    e(TAG, "Request failed. PS-FTP MTU not enabled")
                    throw BleCharacteristicNotificationNotEnabled("PS-FTP MTU not enabled")
                }
            }
        } finally {
            txInterface.gattClientResumeScanning()
        }
    }

    @Throws(InterruptedException::class, BleDisconnected::class, PftpOperationTimeout::class)
    private fun waitPacketsWritten(
        written: AtomicInteger,
        waiting: AtomicBoolean,
        count: Int,
        timeoutSeconds: Long
    ) {
        try {
            waiting.set(true)
            while (written.get() < count) {
                synchronized(written) {
                    if (written.get() != count) {
                        val was = written.get()
                        (written as Object).wait(timeoutSeconds * 1000)
                        if (was == written.get()) {
                            if (!txInterface.isConnected()) {
                                throw BleDisconnected("Connection lost during waiting packets to be written")
                            } else {
                                throw PftpOperationTimeout("Operation timeout while waiting packets written")
                            }
                        }
                    }
                }
                if (!txInterface.isConnected()) {
                    throw BleDisconnected("Connection lost during waiting packets to be written")
                }
            }
        } finally {
            waiting.set(false)
            written.set(0)
        }
    }

    /**
     * Writes file to a device, atomic operation.
     *
     * @param header protobuf pftp operation bytes
     * @param data   actual file data
     * @return [Flow] emitting current byte offset progress; completes after device confirms write
     * @throws Throwable on any transfer error
     */
    fun write(
        header: ByteArray,
        data: ByteArrayInputStream?
    ): Flow<Long> = channelFlow {
        txInterface.gattClientRequestStopScanning()
        try {
            synchronized(pftpOperationMutex) {
                if (pftpMtuEnabled?.get() == ATT_SUCCESS) {
                    d(TAG, "Start write")
                    currentOperationWrite.set(true)
                    var pCounter: Long = 0
                    resetMtuPipe()
                    val headerSize = header.size
                    val totalStream = BlePsFtpUtils.makeCompleteMessageStream(
                        ByteArrayInputStream(header), data,
                        BlePsFtpUtils.MessageType.REQUEST, 0
                    )
                    var next = 0
                    val totalPayload = totalStream.available().toLong()
                    val sequenceNumber = Rfc76SequenceNumber()
                    val timeoutSeconds = getWriteTimeoutForFilePath(
                        CommunicationsPftpRequest.PbPFtpOperation.parseFrom(header).path
                    )
                    var lastEmitTime = 0L
                    do {
                        val airPacket: ByteArray
                        var counter = 0
                        try {
                            val temp = next
                            airPacket = BlePsFtpUtils.buildRfc76MessageFrame(
                                totalStream, temp, mtuSize.get(), sequenceNumber
                            )
                            next = 1
                            useAttributeLevelResponse.set((++pCounter % packetsCount.get()) == 0L)
                            txInterface.transmitMessage(
                                BlePsFtpUtils.RFC77_PFTP_SERVICE,
                                BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC,
                                airPacket,
                                useAttributeLevelResponse.get()
                            )
                            if (totalStream.available() != 0) {
                                if (useAttributeLevelResponse.get()) {
                                    packetsWrittenWithResponse.set(0)
                                    waitPacketsWritten(packetsWrittenWithResponse, mtuWaiting, 1, timeoutSeconds)
                                    packetsWritten.set(0)
                                    counter = 0
                                } else {
                                    ++counter
                                }
                                val packet = mtuInputQueue.poll()
                                if (packet != null && packet.second == 0) {
                                    e(TAG, "Frame sending interrupted by device!")
                                    val response = BlePsFtpUtils.processRfc76MessageFrameHeader(packet.first)
                                    if (response.status == 0) {
                                        throw PftpResponseError("Stream canceled: ", response.error)
                                    } else {
                                        throw Throwable("Stream canceled")
                                    }
                                }
                            }
                            val bytesWritten = totalPayload - totalStream.available() - headerSize - 2
                            val now = System.currentTimeMillis()
                            val isFirst = lastEmitTime == 0L
                            val isDone = totalStream.available() == 0
                            val isTimeToEmit = (now - lastEmitTime) >= 5000L
                            if (isFirst || isDone || isTimeToEmit) {
                                lastEmitTime = now
                                trySend(bytesWritten)
                            }
                        } catch (ex: InterruptedException) {
                            e(TAG, "Frame sending interrupted!")
                            handleMtuInterrupted(totalStream.available() != 0, counter)
                            return@synchronized
                        }
                    } while (totalStream.available() != 0)

                    currentOperationWrite.set(false)
                    val response = ByteArrayOutputStream()
                    try {
                        readResponse(response, timeoutSeconds)
                    } catch (ex: InterruptedException) {
                        e(TAG, "write interrupted while reading response")
                        return@synchronized
                    }
                    // channel completes naturally on scope exit
                } else {
                    throw BleCharacteristicNotificationNotEnabled("PS-FTP MTU not enabled")
                }
            }
        } finally {
            txInterface.gattClientResumeScanning()
            currentOperationWrite.set(false)
        }
    }.flowOn(Dispatchers.IO)

    private fun getWriteTimeoutForFilePath(filePath: String): Long {
        for (path in extendedWriteTimeoutFilePaths) {
            if (filePath.startsWith(path)) {
                return PROTOCOL_TIMEOUT_EXTENDED_SECONDS.toLong()
            }
        }
        return PROTOCOL_TIMEOUT_SECONDS.toLong()
    }

    private fun handleMtuInterrupted(dataAvailable: Boolean, lastRequest: Int) {
        if (pftpMtuEnabled?.get() == ATT_SUCCESS && dataAvailable) {
            val cancelPacket = byteArrayOf(0x00, 0x00, 0x00)
            try {
                if (mtuWaiting.get()) {
                    waitPacketsWritten(packetsWritten, mtuWaiting, lastRequest, PROTOCOL_TIMEOUT_SECONDS.toLong())
                }
                txInterface.transmitMessages(
                    BlePsFtpUtils.RFC77_PFTP_SERVICE,
                    BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC,
                    listOf(cancelPacket),
                    useAttributeLevelResponse.get()
                )
                waitPacketsWritten(packetsWritten, mtuWaiting, 1, PROTOCOL_TIMEOUT_SECONDS.toLong())
                d(TAG, "MTU interrupted. Stream cancel has been successfully send")
            } catch (throwable: Throwable) {
                e(TAG, "Exception while trying to cancel streaming")
            }
        }
    }

    /**
     * Sends a query to device.
     *
     * @param id         query id value
     * @param parameters optional parameters
     * @return [ByteArrayOutputStream] containing the query response
     * @throws Throwable on any error
     */
    suspend fun query(id: Int, parameters: ByteArray?): ByteArrayOutputStream = withContext(Dispatchers.IO) {
        try {
            synchronized(pftpOperationMutex) {
                if (pftpMtuEnabled?.get() == ATT_SUCCESS) {
                    d(TAG, "Send query id: $id")
                    resetMtuPipe()
                    val totalStream = BlePsFtpUtils.makeCompleteMessageStream(
                        if (parameters != null) ByteArrayInputStream(parameters) else null,
                        null,
                        BlePsFtpUtils.MessageType.QUERY,
                        id
                    )
                    val sequenceNumber = Rfc76SequenceNumber()
                    var requs = BlePsFtpUtils.buildRfc76MessageFrameAll(totalStream, mtuSize.get(), sequenceNumber)
                    val response = ByteArrayOutputStream()
                    try {
                        txInterface.transmitMessages(
                            BlePsFtpUtils.RFC77_PFTP_SERVICE,
                            BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC,
                            requs, false
                        )
                        waitPacketsWritten(packetsWritten, mtuWaiting, requs.size, PROTOCOL_TIMEOUT_SECONDS.toLong())
                        requs = mutableListOf()
                        readResponse(response, PROTOCOL_TIMEOUT_SECONDS.toLong())
                        return@synchronized response
                    } catch (ex: InterruptedException) {
                        e(TAG, "Query $id interrupted")
                        if (requs.isEmpty()) {
                            handleMtuInterrupted(true, requs.size)
                        }
                        throw ex
                    }
                } else {
                    e(TAG, "Query $id failed. PS-FTP MTU not enabled")
                    throw BleCharacteristicNotificationNotEnabled("PS-FTP MTU not enabled")
                }
            }
        } catch (ex: Exception) {
            e(TAG, "Query $id failed. Exception: ${ex.message}")
            throw toPftpException(ex)
        }
    }

    /**
     * Sends a single notification to device.
     *
     * @param id         one of the PbPFtpHostToDevNotification values
     * @param parameters matching parameter for PbPFtpHostToDevNotification value if any
     * @throws Throwable on any error
     */
    suspend fun sendNotification(id: Int, parameters: ByteArray?) = withContext(Dispatchers.IO) {
        try {
            synchronized(pftpNotificationMutex) {
                if (txInterface.isConnected()) {
                    if (pftpD2HNotificationEnabled?.get() == ATT_SUCCESS) {
                        d(TAG, "Send notification id: $id")
                        resetNotificationPipe()
                        val totalStream = BlePsFtpUtils.makeCompleteMessageStream(
                            if (parameters != null) ByteArrayInputStream(parameters) else null,
                            null,
                            BlePsFtpUtils.MessageType.NOTIFICATION,
                            id
                        )
                        val sequenceNumber = Rfc76SequenceNumber()
                        val requs = BlePsFtpUtils.buildRfc76MessageFrameAll(totalStream, mtuSize.get(), sequenceNumber)
                        txInterface.transmitMessages(
                            BlePsFtpUtils.RFC77_PFTP_SERVICE,
                            BlePsFtpUtils.RFC77_PFTP_H2D_CHARACTERISTIC,
                            requs, false
                        )
                        waitPacketsWritten(notificationPacketsWritten, notificationWaiting, requs.size, PROTOCOL_TIMEOUT_SECONDS.toLong())
                    } else {
                        e(TAG, "Send notification id: $id failed. PS-FTP notification not enabled")
                        throw BleCharacteristicNotificationNotEnabled("PS-FTP notification not enabled")
                    }
                } else {
                    e(TAG, "Send notification id: $id failed. BLE disconnected")
                    throw BleDisconnected()
                }
            }
        } catch (ex: Exception) {
            e(TAG, "Send notification id: $id failed. Exception: ${ex.message}")
            throw toPftpException(ex)
        }
    }

    /**
     * Wait endlessly for notifications from device using a shared [Flow].
     *
     * @return [Flow] emitting [PftpNotificationMessage] for each complete notification received.
     * Throws on error; never completes on its own.
     */
    fun waitForNotification(): Flow<PftpNotificationMessage> {
        _sharedWaitNotificationFlow?.let { return it }
        synchronized(pftpWaitNotificationSharedMutex) {
            if (_sharedWaitNotificationFlow == null) {
                _sharedWaitNotificationFlow = buildNotificationFlow()
            }
        }
        return _sharedWaitNotificationFlow!!
    }

    private fun buildNotificationFlow(): Flow<PftpNotificationMessage> = channelFlow {
        while (true) {
            var pendingMessage: PftpNotificationMessage? = null
            synchronized(pftpWaitNotificationMutex) {
                if (pftpD2HNotificationEnabled?.get() == ATT_SUCCESS) {
                    try {
                        synchronized(notificationInputQueue) {
                            if (notificationInputQueue.isEmpty()) {
                                (notificationInputQueue as Object).wait()
                            }
                        }
                    } catch (ex: InterruptedException) {
                        e(TAG, "Wait notification interrupted")
                        return@synchronized
                    }
                } else {
                    throw BleCharacteristicNotificationNotEnabled("PS-FTP d2h notification not enabled")
                }
                try {
                    var packet = notificationInputQueue.take()
                    if (packet?.second == 0) {
                        var response = BlePsFtpUtils.processRfc76MessageFrameHeader(packet.first)
                        if (response.payload != null) {
                            if (response.next == 0) {
                                val msg = PftpNotificationMessage()
                                msg.id = response.payload!![0].toInt()
                                response.payload?.let {
                                    msg.byteArrayOutputStream.write(
                                        it, 1, response.payload!!.size - 1
                                    )
                                }
                                var status = response.status
                                while (status == BlePsFtpUtils.RFC76_STATUS_MORE) {
                                    packet = notificationInputQueue.poll(PROTOCOL_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
                                    if (packet != null && packet.second == 0) {
                                        response = BlePsFtpUtils.processRfc76MessageFrameHeader(packet.first)
                                        status = response.status
                                        d(TAG, "Message frame sub sequent packet successfully received")
                                        response.payload?.let {
                                            msg.byteArrayOutputStream.write(
                                                it, 0, response.payload!!.size
                                            )
                                        }
                                    } else {
                                        throw Throwable("Failed to receive notification packet in timeline")
                                    }
                                }
                                pendingMessage = msg
                            } else {
                                e(TAG, "wait notification not in sync, take next")
                            }
                        }
                    } else {
                        throw BleAttributeError("ps-ftp wait notification failure ", packet.second)
                    }
                } catch (ex: InterruptedException) {
                    e(TAG, "wait notification interrupted")
                    return@synchronized
                } catch (throwable: Exception) {
                    throw Exception("Notification receive failed", throwable)
                }
            }
            pendingMessage?.let { send(it) }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Suspend until both PS-FTP MTU and D2H notifications are enabled.
     *
     * @param checkConnection if true, checks connection state before waiting
     * @throws Throwable if notifications cannot be enabled or connection is lost
     */
    suspend fun waitPsFtpClientReady(checkConnection: Boolean) = withContext(Dispatchers.IO) {
        waitNotificationEnabled(BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC, checkConnection)
        waitNotificationEnabled(BlePsFtpUtils.RFC77_PFTP_D2H_CHARACTERISTIC, checkConnection)
    }

    @VisibleForTesting
    @Throws(Exception::class)
    fun readResponse(outputStream: ByteArrayOutputStream, timeoutSeconds: Long) {
        var status: Long = 0
        var next = 0
        val sequenceNumber = Rfc76SequenceNumber()
        val response = PftpRfc76ResponseHeader()
        do {
            if (txInterface.isConnected()) {
                synchronized(mtuInputQueue) {
                    if (mtuInputQueue.isEmpty()) {
                        (mtuInputQueue as Object).wait(timeoutSeconds * 1000L)
                    }
                }
            } else {
                throw BleDisconnected("Connection lost during read response")
            }
            val packet = mtuInputQueue.poll(PROTOCOL_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
            if (packet != null && packet.second == 0) {
                BlePsFtpUtils.processRfc76MessageFrameHeader(response, packet.first)
                if (sequenceNumber.seq != response.sequenceNumber) {
                    if (response.status == BlePsFtpUtils.RFC76_STATUS_MORE) {
                        val cancelPacket = byteArrayOf(0x00, 0x00, 0x00)
                        txInterface.transmitMessages(
                            BlePsFtpUtils.RFC77_PFTP_SERVICE,
                            BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC,
                            listOf(cancelPacket), true
                        )
                        waitPacketsWritten(packetsWritten, mtuWaiting, 1, timeoutSeconds)
                        d(TAG, "Sequence number mismatch. Stream cancel has been successfully send")
                    }
                    throw PftpResponseError("Air packet lost!", 303)
                }
                sequenceNumber.increment()
                status = response.status.toLong()
                if (next == response.next) {
                    next = 1
                    when (response.status) {
                        BlePsFtpUtils.RFC76_STATUS_LAST,
                        BlePsFtpUtils.RFC76_STATUS_MORE -> {
                            outputStream.write(response.payload, 0, response.payload?.size ?: 0)
                        }
                        BlePsFtpUtils.RFC76_STATUS_ERROR_OR_RESPONSE -> {
                            if (response.error == 0) return
                            throw PftpResponseError("Request failed: ", response.error)
                        }
                        else -> throw PftpResponseError("Protocol error, undefined status received", 200)
                    }
                } else {
                    throw PftpResponseError("Protocol error stream is out of sync", 200)
                }
            } else {
                handlePacketError(packet)
            }
        } while (status == BlePsFtpUtils.RFC76_STATUS_MORE.toLong())
        d(TAG, "RFC76 message has read successfully")
    }

    @Throws(Exception::class)
    private fun handlePacketError(packet: Pair<ByteArray, Int>) {
        if (!txInterface.isConnected()) {
            throw BleDisconnected("Connection lost during packet read")
        } else if (packet == null) {
            throw PftpOperationTimeout("Air packet was not received in required timeline")
        } else {
            throw PftpResponseError("Response error: " + packet.second, packet.second)
        }
    }

    /**
     * Converts any PftpResponseError into a descriptive Exception with enum name.
     */
    private fun toPftpException(throwable: Throwable): Exception {
        if (throwable is PftpResponseError) {
            val name = throwable.errorName
            val code = throwable.error
            return Exception("PFTP error: " + (name ?: "UNKNOWN") + " (" + code + ")", throwable)
        }
        return throwable as? Exception ?: Exception(throwable)
    }

    companion object {
        private const val TAG = "BlePsFtpClient"
        private const val PROTOCOL_TIMEOUT_SECONDS = 90
        private const val PROTOCOL_TIMEOUT_EXTENDED_SECONDS = 900
    }
}