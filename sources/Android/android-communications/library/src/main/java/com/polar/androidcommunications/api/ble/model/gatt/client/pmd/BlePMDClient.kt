package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

import android.util.Pair
import androidx.annotation.VisibleForTesting
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.exceptions.*
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdSetting.PmdSettingType
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.*
import com.polar.androidcommunications.common.ble.AtomicSet
import com.polar.androidcommunications.common.ble.BleUtils
import com.polar.androidcommunications.common.ble.RxUtils
import com.polar.androidcommunications.common.ble.TypeUtils
import io.reactivex.rxjava3.core.*
import io.reactivex.rxjava3.schedulers.Schedulers
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil

/**
 * BLE Client for Polar Measurement Data Service (aka. PMD service)
 * */
class BlePMDClient(txInterface: BleGattTxInterface) : BleGattBase(txInterface, PMD_SERVICE) {
    private val pmdCpInputQueue = LinkedBlockingQueue<Pair<ByteArray, Int>>()
    private val ecgObservers = AtomicSet<FlowableEmitter<in EcgData>>()
    private val accObservers = AtomicSet<FlowableEmitter<in AccData>>()
    private val gyroObservers = AtomicSet<FlowableEmitter<in GyrData>>()
    private val magnetometerObservers = AtomicSet<FlowableEmitter<in MagData>>()
    private val ppgObservers = AtomicSet<FlowableEmitter<in PpgData>>()
    private val ppiObservers = AtomicSet<FlowableEmitter<in PpiData>>()
    private val pressureObservers = AtomicSet<FlowableEmitter<in PressureData>>()
    private val locationObservers = AtomicSet<FlowableEmitter<in GnssLocationData>>()
    private val temperatureObservers = AtomicSet<FlowableEmitter<in TemperatureData>>()
    private var pmdFeatureData: ByteArray? = null
    private val controlPointMutex = Object()
    private val mutexFeature = Object()
    private val previousTimeStampMap: EnumMap<PmdMeasurementType, ULong> = EnumMap(PmdMeasurementType.values().associateWith { 0UL })

    @VisibleForTesting
    val currentSettings: MutableMap<PmdMeasurementType, PmdSetting> = mutableMapOf()
    private val pmdCpEnabled: AtomicInteger
    private val pmdDataEnabled: AtomicInteger

    enum class PmdDataFieldEncoding {
        FLOAT_IEEE754,
        DOUBLE_IEEE754,
        SIGNED_INT,
        UNSIGNED_BYTE,
        UNSIGNED_INT,
        UNSIGNED_LONG,
        BOOLEAN
    }

    override fun reset() {
        super.reset()
        clearStreamObservers(BleDisconnected())
        synchronized(mutexFeature) {
            pmdFeatureData = null
            mutexFeature.notifyAll()
        }
        previousTimeStampMap.replaceAll { _, _ -> 0uL }
    }

    private fun getFactor(type: PmdMeasurementType): Float {
        currentSettings[type]?.selected?.get(PmdSettingType.FACTOR)?.let {
            val ieee754 = it
            return java.lang.Float.intBitsToFloat(ieee754)
        }
        BleLogger.w(TAG, "No factor found for type: $type")
        return 1.0f
    }

    private fun getSampleRate(type: PmdMeasurementType): Int {
        return currentSettings[type]?.selected?.get(PmdSettingType.SAMPLE_RATE) ?: 0
    }

    @VisibleForTesting
    fun getPreviousFrameTimeStamp(type: PmdMeasurementType): ULong {
        return previousTimeStampMap[type] ?: 0UL
    }

    override fun processServiceData(characteristic: UUID, data: ByteArray, status: Int, notifying: Boolean) {
        if (characteristic == PMD_CP) {
            if (notifying) {
                pmdCpInputQueue.add(Pair(data, status))
            } else {
                // feature read
                synchronized(mutexFeature) {
                    pmdFeatureData = data
                    mutexFeature.notifyAll()
                }
            }
        } else if (characteristic == PMD_DATA) {
            if (status == 0) {
                BleLogger.d_hex(TAG, "pmd data: ", data)
                val frame = PmdDataFrame(data, this::getPreviousFrameTimeStamp, this::getFactor, this::getSampleRate)
                previousTimeStampMap[frame.measurementType] = frame.timeStamp

                when (frame.measurementType) {
                    PmdMeasurementType.ECG -> {
                        RxUtils.emitNext(ecgObservers) { emitter: FlowableEmitter<in EcgData> ->
                            emitter.onNext(
                                EcgData.parseDataFromDataFrame(frame)
                            )
                        }
                    }
                    PmdMeasurementType.PPG -> {
                        RxUtils.emitNext(ppgObservers) { emitter: FlowableEmitter<in PpgData> ->
                            emitter.onNext(
                                PpgData.parseDataFromDataFrame(frame)
                            )
                        }
                    }
                    PmdMeasurementType.ACC -> {
                        RxUtils.emitNext(accObservers) { emitter: FlowableEmitter<in AccData> ->
                            emitter.onNext(
                                AccData.parseDataFromDataFrame(frame)
                            )
                        }
                    }
                    PmdMeasurementType.PPI -> {
                        RxUtils.emitNext(ppiObservers) { emitter: FlowableEmitter<in PpiData> ->
                            emitter.onNext(
                                PpiData.parseDataFromDataFrame(frame)
                            )
                        }
                    }
                    PmdMeasurementType.GYRO -> {
                        RxUtils.emitNext(gyroObservers) { emitter: FlowableEmitter<in GyrData> ->
                            emitter.onNext(
                                GyrData.parseDataFromDataFrame(frame)
                            )
                        }
                    }
                    PmdMeasurementType.MAGNETOMETER -> {
                        RxUtils.emitNext(magnetometerObservers) { emitter: FlowableEmitter<in MagData> ->
                            emitter.onNext(
                                MagData.parseDataFromDataFrame(frame)
                            )
                        }
                    }
                    PmdMeasurementType.PRESSURE -> {
                        RxUtils.emitNext(pressureObservers) { emitter: FlowableEmitter<in PressureData> ->
                            emitter.onNext(
                                PressureData.parseDataFromDataFrame(frame)
                            )
                        }
                    }
                    PmdMeasurementType.LOCATION -> {
                        RxUtils.emitNext(locationObservers) { emitter: FlowableEmitter<in GnssLocationData> ->
                            emitter.onNext(
                                GnssLocationData.parseDataFromDataFrame(frame)
                            )
                        }
                    }
                    PmdMeasurementType.TEMPERATURE -> {
                        RxUtils.emitNext(temperatureObservers) { emitter: FlowableEmitter<in TemperatureData> ->
                            emitter.onNext(
                                TemperatureData.parseDataFromDataFrame(frame)
                            )
                        }
                    }
                    else -> {
                        BleLogger.w(TAG, "Unknown or not supported PMD type ${frame.measurementType} received")
                    }
                }
            } else {
                BleLogger.e(TAG, "pmd data attribute error")
            }
        }
    }

    override fun processServiceDataWritten(characteristic: UUID, status: Int) {
        // do nothing
    }

    override fun toString(): String {
        return "PMD Client"
    }


    @Throws(Exception::class)
    private fun receiveControlPointPacket(): ByteArray {
        val pair = pmdCpInputQueue.poll(30, TimeUnit.SECONDS)
        if (pair != null) {
            if (pair.second == 0) {
                return pair.first
            }
            throw BleAttributeError("pmd cp attribute error: ", pair.second)
        }
        throw Exception("Pmd response failed to receive in timeline")
    }

    @Throws(Exception::class)
    private fun sendPmdCommand(packet: ByteArray): PmdControlPointResponse {
        txInterface.transmitMessage(PMD_SERVICE, PMD_CP, packet, true)
        val first = receiveControlPointPacket()
        val response = PmdControlPointResponse(first)
        var more = response.more
        while (more) {
            val moreParameters = receiveControlPointPacket()
            more = moreParameters[0].toInt() != 0
            response.parameters = response.parameters.copyOf() + moreParameters.copyOfRange(1, moreParameters.size)
        }
        return response
    }

    private fun sendControlPointCommand(command: PmdControlPointCommand, value: Byte): Single<PmdControlPointResponse> {
        return sendControlPointCommand(command, byteArrayOf(value))
    }

    private fun sendControlPointCommand(command: PmdControlPointCommand, params: ByteArray = byteArrayOf()): Single<PmdControlPointResponse> {
        return Single.create(SingleOnSubscribe { subscriber: SingleEmitter<PmdControlPointResponse> ->
            synchronized(controlPointMutex) {
                try {
                    if (pmdCpEnabled.get() == ATT_SUCCESS && pmdDataEnabled.get() == ATT_SUCCESS) {
                        val bb = ByteBuffer.allocate(1 + params.size)
                        bb.put(byteArrayOf(command.numVal.toByte()))
                        if (params.isNotEmpty()) bb.put(params)
                        val response = sendPmdCommand(bb.array())
                        if (response.status === PmdControlPointResponse.PmdControlPointResponseCode.SUCCESS) {
                            subscriber.onSuccess(response)
                            return@SingleOnSubscribe
                        }
                        throw BleControlPointCommandError("pmd cp command $command error:", response.status)
                    }
                    throw BleCharacteristicNotificationNotEnabled()
                } catch (throwable: Throwable) {
                    if (!subscriber.isDisposed) {
                        subscriber.tryOnError(throwable)
                    }
                }
            }
        } as SingleOnSubscribe<PmdControlPointResponse>)
            .subscribeOn(Schedulers.io())
    }

    /**
     * Query settings by type
     *
     * @return Single stream
     * - onSuccess settings query success, the queried settings emitted
     * - onError settings query failed
     */
    fun querySettings(type: PmdMeasurementType): Single<PmdSetting> {
        return sendControlPointCommand(PmdControlPointCommand.GET_MEASUREMENT_SETTINGS, type.numVal.toByte())
            .map { pmdControlPointResponse: PmdControlPointResponse -> PmdSetting(pmdControlPointResponse.parameters) }
    }

    /**
     * Query full settings by type
     *
     * @return Single stream
     * - onSuccess full settings query success, the queried settings emitted
     * - onError full settings query failed
     */
    fun queryFullSettings(type: PmdMeasurementType): Single<PmdSetting> {
        return sendControlPointCommand(PmdControlPointCommand.GET_SDK_MODE_MEASUREMENT_SETTINGS, type.numVal.toByte())
            .map { pmdControlPointResponse: PmdControlPointResponse -> PmdSetting(pmdControlPointResponse.parameters) }
    }

    /**
     * @return Single stream
     */
    fun readFeature(checkConnection: Boolean): Single<Set<PmdMeasurementType>> {
        return Single.create(SingleOnSubscribe { emitter: SingleEmitter<Set<PmdMeasurementType>> ->
            try {
                if (!checkConnection || txInterface.isConnected) {
                    synchronized(mutexFeature) {
                        if (pmdFeatureData == null) {
                            mutexFeature.wait()
                        }
                        pmdFeatureData?.let {
                            emitter.onSuccess(PmdMeasurementType.fromByteArray(it))
                            return@SingleOnSubscribe
                        }
                        if (!txInterface.isConnected) {
                            throw BleDisconnected()
                        } else {
                            throw Exception("Undefined device error")
                        }
                    }
                }
                throw BleDisconnected()
            } catch (ex: Exception) {
                if (!emitter.isDisposed) {
                    emitter.tryOnError(ex)
                }
            }
        }).subscribeOn(Schedulers.io())
    }

    /**
     * request to start a specific measurement
     *
     * @param type    measurement to start
     * @param setting desired settings
     * @return Completable stream
     */
    fun startMeasurement(type: PmdMeasurementType, setting: PmdSetting): Completable {
        val set = setting.serializeSelected()
        val bb = ByteBuffer.allocate(1 + set.size)
        bb.put(type.numVal.toByte())
        bb.put(set)
        currentSettings[type] = setting

        return sendControlPointCommand(PmdControlPointCommand.REQUEST_MEASUREMENT_START, bb.array())
            .doOnSuccess { pmdControlPointResponse: PmdControlPointResponse -> currentSettings[type]!!.updateSelectedFromStartResponse(pmdControlPointResponse.parameters) }
            .toObservable()
            .ignoreElements()
    }

    /**
     * Request to start SDK mode
     *
     * @return Completable stream
     * - onComplete start SDK mode request completed successfully
     * - onError start SDK mode request failed
     */
    fun startSDKMode(): Completable {
        return sendControlPointCommand(PmdControlPointCommand.REQUEST_MEASUREMENT_START, PmdMeasurementType.SDK_MODE.numVal.toByte())
            .toObservable()
            .doOnComplete { clearStreamObservers(BleOperationModeChange("SDK mode enabled")) }
            .ignoreElements()
    }

    /**
     * Request to stop SDK mode
     *
     * @return Completable stream
     * - onComplete stop SDK mode request completed successfully
     * - onError stop SDK mode request failed
     */
    fun stopSDKMode(): Completable {
        return sendControlPointCommand(PmdControlPointCommand.STOP_MEASUREMENT, PmdMeasurementType.SDK_MODE.numVal.toByte())
            .toObservable()
            .doOnComplete { clearStreamObservers(BleOperationModeChange("SDK mode disabled")) }
            .ignoreElements()
    }

    /**
     * Request to stop measurement
     *
     * @param type measurement to stop
     * @return Completable stream
     */
    fun stopMeasurement(type: PmdMeasurementType): Completable {
        return sendControlPointCommand(PmdControlPointCommand.STOP_MEASUREMENT, byteArrayOf(type.numVal.toByte()))
            .toObservable()
            .doOnComplete { previousTimeStampMap[type] = 0UL }
            .ignoreElements()
    }

    /**
     * start raw ecg monitoring
     *
     * @return Flowable stream Produces:
     * - onNext for every air packet received <BR></BR>
     * - onComplete non produced if stream is not further configured <BR></BR>
     * - onError BleDisconnected produced on disconnection <BR></BR>
     */
    internal fun monitorEcgNotifications(checkConnection: Boolean): Flowable<EcgData> {
        return RxUtils.monitorNotifications(ecgObservers, txInterface, checkConnection)
    }

    /**
     * start raw acc monitoring
     *
     * @return Flowable stream Produces:
     * - onNext for every air packet received <BR></BR>
     * - onComplete non produced if stream is not further configured <BR></BR>
     * - onError BleDisconnected produced on disconnection <BR></BR>
     */
    internal fun monitorAccNotifications(checkConnection: Boolean): Flowable<AccData> {
        return RxUtils.monitorNotifications(accObservers, txInterface, checkConnection)
    }

    /**
     * start raw ppg monitoring
     *
     * @return Flowable stream Produces:
     * - onNext for every air packet received <BR></BR>
     * - onComplete non produced if stream is not further configured <BR></BR>
     * - onError BleDisconnected produced on disconnection <BR></BR>
     */
    internal fun monitorPpgNotifications(checkConnection: Boolean): Flowable<PpgData> {
        return RxUtils.monitorNotifications(ppgObservers, txInterface, checkConnection)
    }

    /**
     * start raw ppi monitoring
     *
     * @return Flowable stream Produces:
     * - onNext for every air packet received <BR></BR>
     * - onComplete non produced if stream is not further configured <BR></BR>
     * - onError BleDisconnected produced on disconnection <BR></BR>
     */
    internal fun monitorPpiNotifications(checkConnection: Boolean): Flowable<PpiData> {
        return RxUtils.monitorNotifications(ppiObservers, txInterface, checkConnection)
    }

    internal fun monitorMagnetometerNotifications(checkConnection: Boolean): Flowable<MagData> {
        return RxUtils.monitorNotifications(magnetometerObservers, txInterface, checkConnection)
    }

    internal fun monitorGyroNotifications(checkConnection: Boolean): Flowable<GyrData> {
        return RxUtils.monitorNotifications(gyroObservers, txInterface, checkConnection)
    }

    internal fun monitorPressureNotifications(checkConnection: Boolean): Flowable<PressureData> {
        return RxUtils.monitorNotifications(pressureObservers, txInterface, checkConnection)
    }

    internal fun monitorLocationNotifications(checkConnection: Boolean): Flowable<GnssLocationData> {
        return RxUtils.monitorNotifications(locationObservers, txInterface, checkConnection)
    }

    internal fun monitorTemperatureNotifications(checkConnection: Boolean): Flowable<TemperatureData> {
        return RxUtils.monitorNotifications(temperatureObservers, txInterface, checkConnection)
    }

    override fun clientReady(checkConnection: Boolean): Completable {
        return Completable.concatArray(
            waitNotificationEnabled(PMD_CP, checkConnection),
            waitNotificationEnabled(PMD_DATA, checkConnection)
        )
    }

    private fun clearStreamObservers(throwable: Throwable) {
        RxUtils.postExceptionAndClearList(ecgObservers, throwable)
        RxUtils.postExceptionAndClearList(accObservers, throwable)
        RxUtils.postExceptionAndClearList(ppgObservers, throwable)
        RxUtils.postExceptionAndClearList(ppiObservers, throwable)
        RxUtils.postExceptionAndClearList(gyroObservers, throwable)
        RxUtils.postExceptionAndClearList(magnetometerObservers, throwable)
        RxUtils.postExceptionAndClearList(pressureObservers, throwable)
        RxUtils.postExceptionAndClearList(locationObservers, throwable)
        RxUtils.postExceptionAndClearList(temperatureObservers, throwable)
    }

    companion object {
        private const val TAG = "BlePMDClient"

        @JvmField
        val PMD_DATA: UUID = UUID.fromString("FB005C82-02E7-F387-1CAD-8ACD2D8DF0C8")

        @JvmField
        val PMD_CP: UUID = UUID.fromString("FB005C81-02E7-F387-1CAD-8ACD2D8DF0C8")

        @JvmField
        val PMD_SERVICE: UUID = UUID.fromString("FB005C80-02E7-F387-1CAD-8ACD2D8DF0C8")

        private fun parseDeltaFrame(bytes: ByteArray, channels: Int, bitWidth: Int, totalBitLength: Int): List<List<Int>> {
            var offset = 0
            val bitSet: MutableList<Boolean> = ArrayList()
            for (b in bytes) {
                for (i in 0..7) {
                    bitSet.add(b.toInt() and (0x01 shl i) != 0)
                }
            }
            val samples: MutableList<List<Int>> = ArrayList()
            val mask = Int.MAX_VALUE shl bitWidth - 1
            while (offset < totalBitLength) {
                val channelSamples: MutableList<Int> = ArrayList()
                var channelCount = 0
                while (channelCount++ < channels) {
                    val bits: List<Boolean> = bitSet.subList(offset, offset + bitWidth)
                    var value = 0
                    for (i in bits.indices) {
                        value = value or ((if (bits[i]) 0x01 else 0x00) shl i)
                    }
                    if (value and mask != 0) {
                        value = value or mask
                    }
                    offset += bitWidth
                    channelSamples.add(value)
                }
                samples.add(channelSamples)
            }
            return samples
        }

        @VisibleForTesting
        fun parseDeltaFrameRefSamples(bytes: ByteArray, channels: Int, resolution: Int, type: PmdDataFieldEncoding): List<Int> {
            val samples: MutableList<Int> = ArrayList()
            var offset = 0
            var channelCount = 0
            val mask = -0x1 shl resolution - 1
            val resolutionInBytes = ceil(resolution / 8.0).toInt()
            while (channelCount++ < channels) {
                var sample: Int
                if (type == PmdDataFieldEncoding.SIGNED_INT) {
                    sample = TypeUtils.convertArrayToSignedInt(bytes, offset, resolutionInBytes)
                    if (sample and mask != 0) {
                        sample = sample or mask
                    }
                } else {
                    sample = TypeUtils.convertArrayToUnsignedInt(bytes, offset, resolutionInBytes).toInt()
                }
                offset += resolutionInBytes
                samples.add(sample)
            }
            return samples
        }

        fun parseDeltaFramesAll(value: ByteArray, channels: Int, resolution: Int, type: PmdDataFieldEncoding): List<List<Int>> {
            var offset = 0
            val refSamples = parseDeltaFrameRefSamples(value, channels, resolution, type)
            offset += (channels * ceil(resolution / 8.0)).toInt()
            val samples: MutableList<List<Int>> = ArrayList(setOf(refSamples))
            BleUtils.validate(refSamples.size == channels, "incorrect number of ref channels")
            while (offset < value.size) {
                val deltaSize: Int = value[offset++].toInt() and 0xFF
                val sampleCount: Int = value[offset++].toInt() and 0xFF
                val bitLength = sampleCount * deltaSize * channels
                val length = ceil(bitLength / 8.0).toInt()
                val deltaFrame = ByteArray(length)
                System.arraycopy(value, offset, deltaFrame, 0, deltaFrame.size)
                val deltaSamples = parseDeltaFrame(deltaFrame, channels, deltaSize, bitLength)
                for (delta in deltaSamples) {
                    BleUtils.validate(delta.size == channels, "incorrect number of delta channels")
                    val lastSample = samples[samples.size - 1]
                    val nextSamples: MutableList<Int> = ArrayList()
                    for (i in 0 until channels) {
                        val sample = lastSample[i] + delta[i]
                        nextSamples.add(sample)
                    }
                    samples.addAll(setOf<List<Int>>(nextSamples))
                }
                offset += length
            }
            return samples
        }
    }

    init {
        addCharacteristicNotification(PMD_CP)
        addCharacteristicRead(PMD_CP)
        addCharacteristicNotification(PMD_DATA)
        pmdCpEnabled = getNotificationAtomicInteger(PMD_CP)
        pmdDataEnabled = getNotificationAtomicInteger(PMD_DATA)
    }
}