package com.polar.androidcommunications.api.ble.model.gatt.client

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.exceptions.BleAttributeError
import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected
import com.polar.androidcommunications.api.ble.exceptions.BleTimeout
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.common.ble.AtomicSet
import com.polar.androidcommunications.common.ble.ChannelUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The `BleMdsClient` class implements BLE MDS (Memfault Diagnostic Service) client for receiving
 * telemetry diagnostics data from a BLE MDS service.
 *
 * Service flow:
 * 1. After connection [MDS_SUPPORTED_FEATURES], [MDS_DEVICE_IDENTIFIER], [MDS_DATA_URI] and
 *    [MDS_AUTHORIZATION] are auto-read, and [MDS_DATA_EXPORT] notifications are auto-enabled.
 * 2. Call [startMdsNotifications] to register an observer and enable streaming via
 *    [MDS_DATA_EXPORT].
 * 3. Observe [TelemetryConfiguration] for continuous streaming; each
 *    emission carries the latest device context together with the export data chunk.
 *
 * @property txInterface The BLE GATT transmitter interface used to send and receive GATT requests
 * and responses.
 */
class BleMdsClient(txInterface: BleGattTxInterface) : BleGattBase(txInterface, MDS_SERVICE) {

    companion object {
        private const val TAG = "BleMdsClient"
        val MDS_SERVICE: UUID = UUID.fromString("54220000-f6a5-4007-a371-722f4ebd8436")
        val MDS_SUPPORTED_FEATURES: UUID = UUID.fromString("54220001-f6a5-4007-a371-722f4ebd8436")
        val MDS_DEVICE_IDENTIFIER: UUID = UUID.fromString("54220002-f6a5-4007-a371-722f4ebd8436")
        val MDS_DATA_URI: UUID = UUID.fromString("54220003-f6a5-4007-a371-722f4ebd8436")
        val MDS_AUTHORIZATION: UUID = UUID.fromString("54220004-f6a5-4007-a371-722f4ebd8436")
        val MDS_DATA_EXPORT: UUID = UUID.fromString("54220005-f6a5-4007-a371-722f4ebd8436")
        val MDS_CONFIGURATOR_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    /**
     * Container that carries the latest MDS device context together with a single chunk of
     * streaming export data received from the [MDS_DATA_EXPORT] notification.
     *
     * All context fields are populated from auto-reads performed after service discovery and are
     * `null` until their respective reads complete.
     *
     * [deviceIdentifier] — unique identifier of the device (from [MDS_DEVICE_IDENTIFIER]).
     *
     * [dataUri] — Memfault data ingestion URI (from [MDS_DATA_URI]).
     *
     * [authorization] — HTTP authorization header value to include when forwarding data to
     * Memfault, e.g. `"Memfault-Project-Key:YOUR_PROJECT_KEY"` (from [MDS_AUTHORIZATION]).
     *
     * [exportData] — raw bytes of one [MDS_DATA_EXPORT] notification frame.
     */
    data class TelemetryConfiguration(
        val deviceIdentifier: String?,
        val dataUri: String?,
        val authorization: String?,
        val exportData: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as TelemetryConfiguration
            return deviceIdentifier == other.deviceIdentifier &&
                    dataUri == other.dataUri &&
                    authorization == other.authorization &&
                    exportData.contentEquals(other.exportData)
        }

        override fun hashCode(): Int {
            var result = deviceIdentifier?.hashCode() ?: 0
            result = 31 * result + (dataUri?.hashCode() ?: 0)
            result = 31 * result + (authorization?.hashCode() ?: 0)
            result = 31 * result + exportData.contentHashCode()
            return result
        }
    }

    // Device context — populated as reads complete after service discovery.
    @Volatile internal var deviceIdentifier: String? = null
    @Volatile internal var dataUri: String? = null
    @Volatile internal var authorization: String? = null
    @Volatile internal var supportedFeatures: Int? = null

    // Modulo-32 sequence counter from the last accepted DATA_EXPORT notification (-1 = none yet).
    @Volatile private var lastSeqCounter: Int = -1

    private val mdsObserverAtomicList = AtomicSet<Channel<TelemetryConfiguration>>()
    private val supportedFeaturesReadLock = Any()
    @Volatile private var supportedFeaturesRead = CompletableDeferred<Int>()
    val mdsResponseQueue = LinkedBlockingQueue<Pair<ByteArray, Int>>()

    init {
        // Auto-read readable characteristics after service discovery.
        addCharacteristicRead(MDS_SUPPORTED_FEATURES)
        addCharacteristicRead(MDS_DEVICE_IDENTIFIER)
        addCharacteristicRead(MDS_DATA_URI)
        addCharacteristicRead(MDS_AUTHORIZATION)
        // Register DATA_EXPORT for auto-notification setup and write-callback routing.
        addCharacteristic(MDS_DATA_EXPORT, PROPERTY_NOTIFY or PROPERTY_WRITE or PROPERTY_WRITE_NO_RESPONSE)
    }

    override fun reset() {
        super.reset()
        deviceIdentifier = null
        dataUri = null
        authorization = null
        supportedFeatures = null
        synchronized(supportedFeaturesReadLock) {
            supportedFeaturesRead.completeExceptionally(BleDisconnected())
            supportedFeaturesRead = CompletableDeferred()
        }
        lastSeqCounter = -1
        mdsResponseQueue.clear()
        ChannelUtils.postDisconnectedAndClearList(mdsObserverAtomicList)
    }

    override suspend fun clientReady(checkConnection: Boolean) {
        val currentSupportedFeaturesRead = synchronized(supportedFeaturesReadLock) {
            supportedFeaturesRead
        }
        waitNotificationEnabled(MDS_DATA_EXPORT, checkConnection)
        currentSupportedFeaturesRead.await()
    }

    override fun processServiceData(
        characteristic: UUID,
        data: ByteArray,
        status: Int,
        notifying: Boolean
    ) {
        when (characteristic) {
            MDS_SUPPORTED_FEATURES -> {
                if (status == ATT_SUCCESS) {
                    val features = data.foldIndexed(0) { i, acc, byte -> acc or ((byte.toInt() and 0xFF) shl (8 * i)) }
                    supportedFeatures = features
                    synchronized(supportedFeaturesReadLock) {
                        supportedFeaturesRead.complete(features)
                    }
                    BleLogger.d(TAG, "MDS Supported Features received (${data.size} bytes): ${data.map { it }}")
                } else {
                    BleLogger.e(TAG, "MDS Supported Features read error: $status")
                    synchronized(supportedFeaturesReadLock) {
                        supportedFeaturesRead.completeExceptionally(
                            BleAttributeError("MDS Supported Features attribute ", status)
                        )
                    }
                }
            }
            MDS_DEVICE_IDENTIFIER -> {
                if (status == ATT_SUCCESS) {
                    deviceIdentifier = data.toString(Charsets.UTF_8)
                    BleLogger.d(TAG, "MDS Device Identifier received: $deviceIdentifier")
                } else {
                    BleLogger.e(TAG, "MDS Device Identifier read error: $status")
                }
            }
            MDS_DATA_URI -> {
                if (status == ATT_SUCCESS) {
                    dataUri = data.toString(Charsets.UTF_8)
                    BleLogger.d(TAG, "MDS Data URI received: $dataUri")
                } else {
                    BleLogger.e(TAG, "MDS Data URI read error: $status")
                }
            }
            MDS_AUTHORIZATION -> {
                if (status == ATT_SUCCESS) {
                    authorization = data.toString(Charsets.UTF_8)
                    BleLogger.d(TAG, "MDS Authorization received: $authorization")
                } else {
                    BleLogger.e(TAG, "MDS Authorization read error: $status")
                }
            }
            MDS_DATA_EXPORT -> {
                if (notifying) {
                    BleLogger.d(TAG, "MDS Data Export notification received (${data.size} bytes)")

                    if (data.isEmpty()) {
                        BleLogger.w(TAG, "MDS Data Export: empty notification, ignoring")
                        return
                    }

                    val seqCounter = data[0].toInt() and 0x1F
                    val payload = data.sliceArray(1 until data.size)

                    if (lastSeqCounter != -1) {
                        if (seqCounter == lastSeqCounter) {
                            BleLogger.d(TAG, "MDS Data Export: duplicate chunk seq=$seqCounter — dropped")
                            return
                        }
                        val dropped = (seqCounter - lastSeqCounter - 1 + 32) % 32
                        if (dropped > 0) {
                            BleLogger.w(TAG, "MDS Data Export: $dropped chunk(s) lost (seq $lastSeqCounter → $seqCounter)")
                        }
                    }
                    lastSeqCounter = seqCounter

                    ChannelUtils.emitNext(mdsObserverAtomicList) { observer ->
                        val notificationData = TelemetryConfiguration(deviceIdentifier, dataUri, authorization, payload)
                        observer.trySend(notificationData)
                    }
                }
            }
            else -> BleLogger.d(TAG, "processServiceData: unhandled characteristic $characteristic status: $status")
        }
    }

    override fun processServiceDataWritten(characteristic: UUID, status: Int) {
        BleLogger.d(TAG, "processServiceDataWritten characteristic: $characteristic status: $status")
        if (characteristic == MDS_DATA_EXPORT) {
            if (status != ATT_SUCCESS) {
                BleLogger.e(TAG, "MDS Data Export write failed with status: $status")
            }
            mdsResponseQueue.add(Pair(byteArrayOf(), status))
        }
    }

    fun startMdsNotifications(checkConnection: Boolean): Flow<TelemetryConfiguration> {
        if (checkConnection && !txInterface.isConnected()) {
            throw BleDisconnected()
        }

        lastSeqCounter = -1
        val observer = Channel<TelemetryConfiguration>(Channel.BUFFERED)
        mdsObserverAtomicList.add(observer)

        try {
            sendControlPointCommand(1)
        } catch (error: Throwable) {
            mdsObserverAtomicList.remove(observer)
            observer.close()
            throw error
        }

        return flow {
            try {
                for (notification in observer) {
                    emit(notification)
                }
            } finally {
                mdsObserverAtomicList.remove(observer)
                observer.close()
            }
        }
    }

    fun monitorMdsNotifications(checkConnection: Boolean): Flow<TelemetryConfiguration> {
        return ChannelUtils.monitorNotifications(mdsObserverAtomicList, txInterface, checkConnection)
    }

    fun sendControlPointCommand(command: Int): Int {
        mdsResponseQueue.clear()
        txInterface.transmitMessages(MDS_SERVICE, MDS_DATA_EXPORT, listOf(byteArrayOf(command.toByte())), true)
        val pair = mdsResponseQueue.poll(30, TimeUnit.SECONDS)
        if (pair != null) {
            if (pair.second == ATT_SUCCESS) {
                return 0
            } else {
                throw BleAttributeError("mds attribute ", pair.second)
            }
        }
        throw BleTimeout("Mds response failed to receive in timeline")
    }
}