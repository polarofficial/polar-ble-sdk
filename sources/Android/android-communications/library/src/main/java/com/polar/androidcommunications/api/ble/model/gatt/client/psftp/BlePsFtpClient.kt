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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Polar simple file transfer client declaration.
 *
 * ## Disconnect-cancellation mechanism
 *
 * Operations ([request], [query], [write], [sendNotification]) can suspend for up to 90 seconds
 * while waiting for BLE packets from the device. Without an early-exit path, a BLE disconnection
 * would leave those coroutines blocked on the internal channels — holding [operationMutex] and
 * preventing any subsequent operation from starting.
 *
 * To solve this, every operation participates in a two-sided race via Kotlin's [select]:
 *
 * ```
 * withTimeoutOrNull(timeoutMillis) {
 *     select {
 *         channel.onReceive { ... }           // normal path: BLE packet arrives
 *         disconnectSignal.onAwait { ... }    // fast path: disconnect fires
 *     }
 * }
 * ```
 *
 * **`disconnectSignal`** is a snapshot of [disconnectDeferred] taken at the start of each
 * operation, *before* the mutex is acquired. When [reset] is called on disconnection:
 * 1. [disconnectDeferred] is replaced with a fresh `CompletableDeferred` for the next connection.
 * 2. The *old* deferred is completed exceptionally with [BleDisconnected].
 *
 * Any coroutine suspended in `onAwait` on the old deferred is immediately unblocked: because the
 * deferred completed *exceptionally*, `onAwait` re-throws [BleDisconnected] rather than executing
 * its lambda. That exception propagates through `withTimeoutOrNull` (which only catches
 * `TimeoutCancellationException`), out of the suspend helper, and up to `operationMutex.withLock`,
 * whose `finally` block releases the lock — freeing the next waiting operation immediately.
 *
 * **Capture-before-lock ordering** prevents a race: even if [reset] runs between the snapshot
 * (`val disconnectSignal = disconnectDeferred`) and the first suspension point, the old deferred
 * is already completed exceptionally, so `onAwait` throws the moment the `select` starts.
 */
class BlePsFtpClient(txInterface: BleGattTxInterface) :
    BleGattBase(txInterface, BlePsFtpUtils.RFC77_PFTP_SERVICE, true) {
    private val pftpMtuEnabled: AtomicInteger?
    private val pftpD2HNotificationEnabled: AtomicInteger?
    private val mtuResponseChannel = Channel<Pair<ByteArray, Int>>(Channel.UNLIMITED)
    private val notificationChannel = Channel<Pair<ByteArray, Int>>(Channel.UNLIMITED)
    private val packetsWrittenChannel = Channel<Unit>(Channel.UNLIMITED)
    private val packetsWrittenWithResponseChannel = Channel<Unit>(Channel.UNLIMITED)
    private val notificationPacketsWrittenChannel = Channel<Unit>(Channel.UNLIMITED)
    private val mtuWaiting = AtomicBoolean(false)
    private val currentOperationWrite = AtomicBoolean(false)
    private val packetsCount = AtomicInteger(5) // default every 5th packet is written with response
    private val extendedWriteTimeoutFilePaths = listOf("/SYNCPART.TGZ")

    /**
     * true  = uses attribute operation WRITE
     * false = uses attribute operation WRITE_NO_RESPONSE
     */
    private val useAttributeLevelResponse = AtomicBoolean(false)
    private val operationMutex = Mutex()
    private val notificationWriteMutex = Mutex()
    private val notificationMutex = Mutex()
    /**
     * Disconnect signal shared by all in-flight operations. Each operation captures a snapshot of
     * this field before acquiring the mutex; [reset] swaps in a fresh instance and completes the
     * old one exceptionally with [BleDisconnected], immediately unblocking any `select.onAwait`
     * waiting on the old deferred. See the class-level KDoc for the full mechanism.
     */
    @Volatile private var disconnectDeferred = CompletableDeferred<Unit>()
    // Single-producer hot SharedFlow: one coroutine consumes from notificationChannel and
    // broadcasts to ALL subscribers, eliminating competition between concurrent consumers.
    private var notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sharedWaitNotificationFlow: Flow<PftpNotificationMessage> =
        buildNotificationFlow().shareIn(notificationScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0L), replay = 0)

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
        // Swap to a fresh signal first so new operations after reconnect get an incomplete deferred,
        // then complete the old one exceptionally to unblock any coroutine currently suspended in
        // a select.onAwait. The exception propagates through withTimeoutOrNull (which only catches
        // TimeoutCancellationException) and releases operationMutex via its finally block.
        val oldDeferred = disconnectDeferred
        disconnectDeferred = CompletableDeferred()
        oldDeferred.completeExceptionally(BleDisconnected("Device disconnected"))
        currentOperationWrite.set(false)
        while (mtuResponseChannel.tryReceive().isSuccess) { /* drain pending MTU responses */ }
        while (packetsWrittenChannel.tryReceive().isSuccess) { /* drain pending write acks */ }
        while (packetsWrittenWithResponseChannel.tryReceive().isSuccess) { /* drain pending write-with-response acks */ }
        while (notificationPacketsWrittenChannel.tryReceive().isSuccess) { /* drain pending notification write acks */ }
        while (notificationChannel.tryReceive().isSuccess) { /* drain buffered notifications */ }
        mtuWaiting.set(false)
        // Cancel and recreate the notification scope so that any active SharedFlow subscribers
        // are terminated and the new SharedFlow starts fresh for the next connection.
        notificationScope.cancel()
        notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        sharedWaitNotificationFlow = buildNotificationFlow()
            .shareIn(notificationScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0L), replay = 0)
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
                mtuResponseChannel.trySend(Pair(data, status))
                progressCallback?.onProgressUpdate(data.size.toLong())
                if (currentOperationWrite.get() && mtuWaiting.get() && data.size == 3) {
                    // special case: stream cancellation received before ATT write response
                    packetsWrittenChannel.trySend(Unit)
                }
            } else if (characteristic == BlePsFtpUtils.RFC77_PFTP_D2H_CHARACTERISTIC) {
                notificationChannel.trySend(Pair(data, status))
            }
        } else {
            e(TAG, "Received 0 length packet")
        }
    }

    override fun processServiceDataWritten(characteristic: UUID, status: Int) {
        if (status == 0) {
            if (characteristic == BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC) {
                packetsWrittenChannel.trySend(Unit)
            } else if (characteristic == BlePsFtpUtils.RFC77_PFTP_H2D_CHARACTERISTIC) {
                notificationPacketsWrittenChannel.trySend(Unit)
            }
        } else {
            e(TAG, "Failed to write chr UUID: $characteristic status: $status")
        }
    }

    override fun processServiceDataWrittenWithResponse(characteristic: UUID, status: Int) {
        if (status == 0) {
            if (characteristic == BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC) {
                packetsWrittenWithResponseChannel.trySend(Unit)
            }
        }
        processServiceDataWritten(characteristic, status)
    }

    override fun toString(): String {
        return "RFC77 Service"
    }

    private fun resetMtuPipe() {
        d(TAG, "mtu reseted")
        while (mtuResponseChannel.tryReceive().isSuccess) { /* drain */ }
        while (packetsWrittenChannel.tryReceive().isSuccess) { /* drain */ }
        while (packetsWrittenWithResponseChannel.tryReceive().isSuccess) { /* drain stale write-with-response acks */ }
        mtuWaiting.set(false)
    }

    private fun resetNotificationPipe() {
        while (notificationPacketsWrittenChannel.tryReceive().isSuccess) { /* drain */ }
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
        val disconnectSignal = disconnectDeferred
        try {
            operationMutex.withLock {
                if (pftpMtuEnabled?.get() == ATT_SUCCESS) {
                    d(TAG, "Start request")
                    val previousCallback = this@BlePsFtpClient.progressCallback
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
                        val requestData = BlePsFtpUtils.buildRfc76MessageFrameAll(
                            totalStream, mtuSize.get(), sequenceNumber
                        )
                        val outputStream = ByteArrayOutputStream()
                        txInterface.transmitMessages(
                            BlePsFtpUtils.RFC77_PFTP_SERVICE,
                            BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC,
                            requestData, false
                        )
                        suspendWaitPacketsWritten(packetsWrittenChannel, requestData.size, PROTOCOL_TIMEOUT_SECONDS * 1000L, disconnectSignal)
                        suspendReadResponse(outputStream, PROTOCOL_TIMEOUT_SECONDS * 1000L, disconnectSignal)
                        this@BlePsFtpClient.progressCallback = previousCallback
                        outputStream
                    } catch (ex: CancellationException) {
                        this@BlePsFtpClient.progressCallback = previousCallback
                        throw ex
                    } catch (ex: Exception) {
                        this@BlePsFtpClient.progressCallback = previousCallback
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

    @Throws(BleDisconnected::class, PftpOperationTimeout::class)
    private suspend fun suspendWaitPacketsWritten(
        channel: Channel<Unit>,
        count: Int,
        timeoutMillis: Long,
        disconnectSignal: CompletableDeferred<Unit>
    ) {
        mtuWaiting.set(true)
        try {
            repeat(count) {
                withTimeoutOrNull(timeoutMillis) {
                    select<Unit> {
                        channel.onReceive { }
                        // When reset() fires, disconnectSignal completes exceptionally with
                        // BleDisconnected. onAwait re-throws that exception into the select,
                        // bypassing withTimeoutOrNull and unblocking this coroutine immediately.
                        disconnectSignal.onAwait { }
                    }
                } ?: if (!txInterface.isConnected()) {
                    throw BleDisconnected("Connection lost during waiting packets to be written")
                } else {
                    throw PftpOperationTimeout("Operation timeout while waiting packets written")
                }
            }
        } finally {
            mtuWaiting.set(false)
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
        val disconnectSignal = disconnectDeferred
        try {
            operationMutex.withLock {
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
                        val temp = next
                        val airPacket = BlePsFtpUtils.buildRfc76MessageFrame(
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
                                suspendWaitPacketsWritten(packetsWrittenWithResponseChannel, 1, timeoutSeconds * 1000L, disconnectSignal)
                                while (packetsWrittenChannel.tryReceive().isSuccess) { /* drain accumulated write acks */ }
                            }
                            val cancelPacket = mtuResponseChannel.tryReceive().getOrNull()
                            if (cancelPacket != null && cancelPacket.second == 0) {
                                e(TAG, "Frame sending interrupted by device!")
                                val response = BlePsFtpUtils.processRfc76MessageFrameHeader(cancelPacket.first)
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
                    } while (totalStream.available() != 0)

                    currentOperationWrite.set(false)
                    val response = ByteArrayOutputStream()
                    suspendReadResponse(response, timeoutSeconds * 1000L, disconnectSignal)
                    // channel completes naturally on scope exit
                } else {
                    throw BleCharacteristicNotificationNotEnabled("PS-FTP MTU not enabled")
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            throw ex
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

    /**
     * Sends a query to device.
     *
     * @param id         query id value
     * @param parameters optional parameters
     * @return [ByteArrayOutputStream] containing the query response
     * @throws Throwable on any error
     */
    suspend fun query(id: Int, parameters: ByteArray?): ByteArrayOutputStream = withContext(Dispatchers.IO) {
        val disconnectSignal = disconnectDeferred
        try {
            operationMutex.withLock {
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
                    val requs = BlePsFtpUtils.buildRfc76MessageFrameAll(totalStream, mtuSize.get(), sequenceNumber)
                    val response = ByteArrayOutputStream()
                    txInterface.transmitMessages(
                        BlePsFtpUtils.RFC77_PFTP_SERVICE,
                        BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC,
                        requs, false
                    )
                    suspendWaitPacketsWritten(packetsWrittenChannel, requs.size, PROTOCOL_TIMEOUT_SECONDS * 1000L, disconnectSignal)
                    suspendReadResponse(response, PROTOCOL_TIMEOUT_SECONDS * 1000L, disconnectSignal)
                    response
                } else {
                    e(TAG, "Query $id failed. PS-FTP MTU not enabled")
                    throw BleCharacteristicNotificationNotEnabled("PS-FTP MTU not enabled")
                }
            }
        } catch (ex: CancellationException) {
            throw ex
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
        val disconnectSignal = disconnectDeferred
        try {
            notificationWriteMutex.withLock {
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
                        suspendWaitPacketsWritten(notificationPacketsWrittenChannel, requs.size, PROTOCOL_TIMEOUT_SECONDS * 1000L, disconnectSignal)
                    } else {
                        e(TAG, "Send notification id: $id failed. PS-FTP notification not enabled")
                        throw BleCharacteristicNotificationNotEnabled("PS-FTP notification not enabled")
                    }
                } else {
                    e(TAG, "Send notification id: $id failed. BLE disconnected")
                    throw BleDisconnected()
                }
            }
        } catch (ex: CancellationException) {
            throw ex
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
    fun waitForNotification(): Flow<PftpNotificationMessage> = sharedWaitNotificationFlow

    private fun buildNotificationFlow(): Flow<PftpNotificationMessage> = channelFlow {
        while (true) {
            notificationMutex.withLock {
                if (pftpD2HNotificationEnabled?.get() != ATT_SUCCESS) {
                    throw BleCharacteristicNotificationNotEnabled("PS-FTP d2h notification not enabled")
                }
                val packet = notificationChannel.receive()
                if (packet.second == 0) {
                    var response = BlePsFtpUtils.processRfc76MessageFrameHeader(packet.first)
                    if (response.payload != null) {
                        if (response.next == 0) {
                            val msg = PftpNotificationMessage()
                            msg.id = response.payload!![0].toInt()
                            response.payload?.let {
                                msg.byteArrayOutputStream.write(it, 1, response.payload!!.size - 1)
                            }
                            var status = response.status
                            while (status == BlePsFtpUtils.RFC76_STATUS_MORE) {
                                val nextPacket = withTimeoutOrNull(PROTOCOL_TIMEOUT_SECONDS * 1000L) {
                                    notificationChannel.receive()
                                } ?: throw Throwable("Failed to receive notification packet in timeline")
                                if (nextPacket.second == 0) {
                                    response = BlePsFtpUtils.processRfc76MessageFrameHeader(nextPacket.first)
                                    status = response.status
                                    d(TAG, "Message frame sub sequent packet successfully received")
                                    response.payload?.let {
                                        msg.byteArrayOutputStream.write(it, 0, response.payload!!.size)
                                    }
                                } else {
                                    throw Throwable("Failed to receive notification packet in timeline")
                                }
                            }
                            send(msg)
                        } else {
                            e(TAG, "wait notification not in sync, take next")
                        }
                    }
                } else {
                    throw BleAttributeError("ps-ftp wait notification failure ", packet.second)
                }
            }
        }
    }

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
    suspend fun suspendReadResponse(
        outputStream: ByteArrayOutputStream,
        timeoutMillis: Long,
        disconnectSignal: CompletableDeferred<Unit>
    ) {
        var status: Long = 0
        var next = 0
        val sequenceNumber = Rfc76SequenceNumber()
        val response = PftpRfc76ResponseHeader()
        do {
            if (!txInterface.isConnected()) {
                throw BleDisconnected("Connection lost during read response")
            }
            val packet: Pair<ByteArray, Int>? = withTimeoutOrNull(timeoutMillis) {
                select {
                    mtuResponseChannel.onReceive { it }
                    // When reset() fires, disconnectSignal completes exceptionally with
                    // BleDisconnected. onAwait re-throws that exception into the select,
                    // bypassing withTimeoutOrNull and unblocking this coroutine immediately.
                    disconnectSignal.onAwait { null }
                }
            }
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
                        suspendWaitPacketsWritten(packetsWrittenChannel, 1, timeoutMillis, disconnectSignal)
                        d(TAG, "Sequence number mismatch. Stream cancel has been successfully send")
                    } else {
                        while (mtuResponseChannel.tryReceive().isSuccess) { /* drain stale packets */ }
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
    private fun handlePacketError(packet: Pair<ByteArray, Int>?) {
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