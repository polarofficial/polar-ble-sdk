package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

import androidx.annotation.VisibleForTesting
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.exceptions.*
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType.Companion.fromId
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdSetting.PmdSettingType
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.errors.BleOnlineStreamClosed
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.*
import com.polar.androidcommunications.common.ble.AtomicSet
import com.polar.androidcommunications.common.ble.BleUtils
import com.polar.androidcommunications.common.ble.ChannelUtils
import com.polar.androidcommunications.common.ble.TypeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
    @VisibleForTesting
    val pmdCpResponseQueue = LinkedBlockingQueue<Pair<ByteArray, Int>>()
    private val ecgObservers = AtomicSet<Channel<EcgData>>()
    private val accObservers = AtomicSet<Channel<AccData>>()
    private val gyroObservers = AtomicSet<Channel<GyrData>>()
    private val magnetometerObservers = AtomicSet<Channel<MagData>>()
    private val ppgObservers = AtomicSet<Channel<PpgData>>()
    private val ppiObservers = AtomicSet<Channel<PpiData>>()
    private val pressureObservers = AtomicSet<Channel<PressureData>>()
    private val locationObservers = AtomicSet<Channel<GnssLocationData>>()
    private val temperatureObservers = AtomicSet<Channel<TemperatureData>>()
    private val skinTemperatureObservers = AtomicSet<Channel<SkinTemperatureData>>()
    private var pmdFeatureData: ByteArray? = null
    private val controlPointMutex = Object()
    private val mutexFeature = Object()
    private var previousTimeStampMap = mutableMapOf<Pair<PmdMeasurementType, PmdDataFrame.PmdDataFrameType?>, ULong>()

    @VisibleForTesting
    val currentSettings: MutableMap<PmdMeasurementType, PmdSetting> = mutableMapOf()
    private val pmdCpEnabled: AtomicInteger?
    private val pmdDataEnabled: AtomicInteger?

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
        previousTimeStampMap = mutableMapOf()
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
    fun getPreviousFrameTimeStamp(type: PmdMeasurementType, frameType: PmdDataFrame.PmdDataFrameType): ULong {
        return previousTimeStampMap[Pair(type, frameType)] ?: 0UL
    }

    override fun processServiceData(characteristic: UUID, data: ByteArray, status: Int, notifying: Boolean) {
        if (data.isNotEmpty()) {
            if (characteristic == PMD_CP) {
                if (notifying) {
                    processPmdCpCommand(data, status)
                } else {
                    if (status == ATT_SUCCESS) {
                        // feature read
                        synchronized(mutexFeature) {
                            pmdFeatureData = data
                            mutexFeature.notifyAll()
                        }
                    } else {
                        BleLogger.w(TAG, "Process service data with status $status, skipped")
                    }
                }
            } else if (characteristic == PMD_DATA) {
                if (status == ATT_SUCCESS) {
                    processPmdData(data)
                } else {
                    BleLogger.e(TAG, "pmd data attribute error")
                }
            }
        }
    }

    private fun processPmdCpCommand(data: ByteArray, status: Int) {
        BleLogger.d_hex(TAG, "pmd command. Status: $status. Data: ", data)
        if (data.isNotEmpty() && data[0] != PmdControlPointResponse.CONTROL_POINT_RESPONSE_CODE) {
            when (PmdControlPointCommandServiceToClient.fromByte(data[0])) {
                PmdControlPointCommandServiceToClient.ONLINE_MEASUREMENT_STOPPED -> {
                    val errorDescription = "Stop command from device"
                    data.drop(1).forEach { dataType ->
                        when (PmdMeasurementType.fromId(dataType)) {
                            PmdMeasurementType.ECG -> ChannelUtils.postError(ecgObservers, BleOnlineStreamClosed(errorDescription))
                            PmdMeasurementType.PPG -> ChannelUtils.postError(ppgObservers, BleOnlineStreamClosed(errorDescription))
                            PmdMeasurementType.ACC -> ChannelUtils.postError(accObservers, BleOnlineStreamClosed(errorDescription))
                            PmdMeasurementType.PPI -> ChannelUtils.postError(ppiObservers, BleOnlineStreamClosed(errorDescription))
                            PmdMeasurementType.GYRO -> ChannelUtils.postError(gyroObservers, BleOnlineStreamClosed(errorDescription))
                            PmdMeasurementType.MAGNETOMETER -> ChannelUtils.postError(magnetometerObservers, BleOnlineStreamClosed(errorDescription))
                            PmdMeasurementType.LOCATION -> ChannelUtils.postError(locationObservers, BleOnlineStreamClosed(errorDescription))
                            PmdMeasurementType.PRESSURE -> ChannelUtils.postError(pressureObservers, BleOnlineStreamClosed(errorDescription))
                            PmdMeasurementType.TEMPERATURE -> ChannelUtils.postError(temperatureObservers, BleOnlineStreamClosed(errorDescription))
                            PmdMeasurementType.SKIN_TEMP -> ChannelUtils.postError(skinTemperatureObservers, BleOnlineStreamClosed(errorDescription))
                            else -> {
                                BleLogger.e(TAG, "PMD CP, not supported PmdMeasurementType for Measurement stop. Measurement type value $dataType ")
                            }
                        }
                    }
                }
                null -> {
                    BleLogger.e(TAG, "PMD CP, not supported CP command from server. Command ${data[0]} ")
                }
            }
        } else {
            pmdCpResponseQueue.add(Pair(data, status))
        }
    }

    private fun processPmdData(data: ByteArray) {
        BleLogger.d_hex(TAG, "pmd data: ", data)
        val frame = PmdDataFrame(data, this::getPreviousFrameTimeStamp, this::getFactor, this::getSampleRate)
        previousTimeStampMap[Pair(frame.measurementType, frame.frameType)] = frame.timeStamp

        when (frame.measurementType) {
            PmdMeasurementType.ECG -> ChannelUtils.emitNext(ecgObservers) { it.trySend(EcgData.parseDataFromDataFrame(frame)) }
            PmdMeasurementType.PPG -> ChannelUtils.emitNext(ppgObservers) { it.trySend(PpgData.parseDataFromDataFrame(frame)) }
            PmdMeasurementType.ACC -> ChannelUtils.emitNext(accObservers) { it.trySend(AccData.parseDataFromDataFrame(frame)) }
            PmdMeasurementType.PPI -> ChannelUtils.emitNext(ppiObservers) { it.trySend(PpiData.parseDataFromDataFrame(frame)) }
            PmdMeasurementType.GYRO -> ChannelUtils.emitNext(gyroObservers) { it.trySend(GyrData.parseDataFromDataFrame(frame)) }
            PmdMeasurementType.MAGNETOMETER -> ChannelUtils.emitNext(magnetometerObservers) { it.trySend(MagData.parseDataFromDataFrame(frame)) }
            PmdMeasurementType.PRESSURE -> ChannelUtils.emitNext(pressureObservers) { it.trySend(PressureData.parseDataFromDataFrame(frame)) }
            PmdMeasurementType.LOCATION -> ChannelUtils.emitNext(locationObservers) { it.trySend(GnssLocationData.parseDataFromDataFrame(frame)) }
            PmdMeasurementType.TEMPERATURE -> ChannelUtils.emitNext(temperatureObservers) { it.trySend(TemperatureData.parseDataFromDataFrame(frame)) }
            PmdMeasurementType.SKIN_TEMP -> ChannelUtils.emitNext(skinTemperatureObservers) { it.trySend(SkinTemperatureData.parseDataFromDataFrame(frame)) }
            else -> BleLogger.w(TAG, "Unknown or not supported PMD type ${frame.measurementType} received")
        }
    }

    override fun processServiceDataWritten(characteristic: UUID, status: Int) {
        // do nothing
    }

    override fun toString(): String {
        return "PMD Client"
    }

    @Throws(Exception::class)
    private fun receiveControlPointPacket(command: Byte): ByteArray {

        var pair: Pair<ByteArray, Int>? = null
        var dataFetchedFromQueue = false

        do {
            val headPair = pmdCpResponseQueue.poll(30, TimeUnit.SECONDS)
            if (headPair == null) {
                dataFetchedFromQueue = true
            } else if (headPair.second != 0) {
                BleLogger.e(TAG, "Received PMD CP packet with nonzero status ${headPair.second}")
            } else if (headPair.first.size < 2) {
                BleLogger.e(TAG, "Received PMD CP packet with unexpected byte array size ${headPair.first.size}")
            } else if (headPair.first[1] != command) {
                BleLogger.e(TAG, "Received PMD CP packet with unexpected command byte ${headPair.first[1]}, expected ${command}")
            } else { // valid
                pair = headPair
                dataFetchedFromQueue = true
            }

        } while (!dataFetchedFromQueue)

        if (pair != null) {
            return pair.first
        }
        throw Exception("Pmd response failed to receive in timeline")
    }

    @Throws(Exception::class)
    private fun sendPmdCommand(packet: ByteArray): PmdControlPointResponse {
        txInterface.transmitMessage(PMD_SERVICE, PMD_CP, packet, true)
        val command = packet[0]
        val first = receiveControlPointPacket(command)
        val response = PmdControlPointResponse(first)
        var more = response.more
        while (more) {
            val moreParameters = receiveControlPointPacket(command)
            val moreResponse = PmdControlPointResponse(moreParameters)
            more = moreResponse.more
            response.parameters = response.parameters.copyOf() + moreParameters.copyOfRange(5, moreParameters.size)
        }
        return response
    }

    private fun sendControlPointCommand(command: PmdControlPointCommandClientToService, value: Byte): PmdControlPointResponse {
        return sendControlPointCommand(command, byteArrayOf(value))
    }

    private fun sendControlPointCommand(command: PmdControlPointCommandClientToService, params: ByteArray = byteArrayOf()): PmdControlPointResponse {
        synchronized(controlPointMutex) {
            if (pmdCpEnabled != null && pmdDataEnabled != null) {
                if (pmdCpEnabled.get() == ATT_SUCCESS && pmdDataEnabled.get() == ATT_SUCCESS) {
                    val bb = ByteBuffer.allocate(1 + params.size)
                    bb.put(byteArrayOf(command.code.toByte()))
                    if (params.isNotEmpty()) bb.put(params)
                    BleLogger.d(TAG, "Send control point command $command")
                    val response = sendPmdCommand(bb.array())
                    BleLogger.d(TAG, "Response of control point command $command with status ${response.status}")
                    if (response.status == PmdControlPointResponse.PmdControlPointResponseCode.SUCCESS) {
                        return response
                    }
                    throw BleControlPointCommandError("pmd cp command $command error:", response.status)
                }
                throw BleCharacteristicNotificationNotEnabled()
            }
            throw BleCharacteristicNotificationNotEnabled()
        }
    }

    /**
     * Query settings by type
     *
     * @return [PmdSetting] on success, throws on error
     */
    suspend fun querySettings(type: PmdMeasurementType, recordingType: PmdRecordingType = PmdRecordingType.ONLINE): PmdSetting = withContext(Dispatchers.IO) {
        val measurementType = type.numVal
        val requestByte = recordingType.asBitField() or measurementType
        val response = sendControlPointCommand(PmdControlPointCommandClientToService.GET_MEASUREMENT_SETTINGS, requestByte.toByte())
        PmdSetting(response.parameters)
    }

    /**
     * Query full settings by type
     *
     * @return [PmdSetting] on success, throws on error
     */
    suspend fun queryFullSettings(type: PmdMeasurementType, recordingType: PmdRecordingType = PmdRecordingType.ONLINE): PmdSetting = withContext(Dispatchers.IO) {
        val measurementType = type.numVal
        val requestByte = recordingType.asBitField() or measurementType
        val response = sendControlPointCommand(PmdControlPointCommandClientToService.GET_SDK_MODE_MEASUREMENT_SETTINGS, requestByte.toByte())
        PmdSetting(response.parameters)
    }

    /**
     * Read available PMD measurement types from device feature data.
     *
     * @return [Set] of [PmdMeasurementType] on success, throws on error
     */
    suspend fun readFeature(checkConnection: Boolean): Set<PmdMeasurementType> = withContext(Dispatchers.IO) {
        if (!checkConnection || txInterface.isConnected()) {
            synchronized(mutexFeature) {
                if (pmdFeatureData == null) {
                    mutexFeature.wait()
                }
                pmdFeatureData?.let { return@withContext PmdMeasurementType.fromByteArray(it) }
                if (!txInterface.isConnected()) {
                    throw BleDisconnected()
                } else {
                    throw Exception("Undefined device error")
                }
            }
        }
        throw BleDisconnected()
    }

    /**
     * Start measurement of the given type.
     *
     * @throws Throwable on failure
     */
    suspend fun startMeasurement(type: PmdMeasurementType, setting: PmdSetting, recordingType: PmdRecordingType = PmdRecordingType.ONLINE, secret: PmdSecret? = null) = withContext(Dispatchers.IO) {
        val measurementType = type.numVal
        val firstByte = recordingType.asBitField() or measurementType
        var settingsBytes = setting.serializeSelected()
        settingsBytes += secret?.serializeToPmdSettings() ?: byteArrayOf()
        val bb = ByteBuffer.allocate(1 + settingsBytes.size)
        bb.put(firstByte.toByte())
        bb.put(settingsBytes)
        currentSettings[type] = setting
        BleLogger.d(TAG, "start measurement. Measurement type: $type Recording type: $recordingType Secret provided: ${secret != null}")
        val response = sendControlPointCommand(PmdControlPointCommandClientToService.REQUEST_MEASUREMENT_START, bb.array())
        try {
            currentSettings[type]?.updateSelectedFromStartResponse(response.parameters)
        } catch (e: Exception) {
            throw BleControlPointResponseError("Failed to parse PMD control point response from device. Measurement type: $type. Exception: $e \n Response: $response")
        }
    }

    /**
     * Read current measurement status from device.
     *
     * @return [Map] of [PmdMeasurementType] to [PmdActiveMeasurement]
     */
    suspend fun readMeasurementStatus(): Map<PmdMeasurementType, PmdActiveMeasurement> = withContext(Dispatchers.IO) {
        val response = sendControlPointCommand(PmdControlPointCommandClientToService.GET_MEASUREMENT_STATUS)
        val measurementStatus: MutableMap<PmdMeasurementType, PmdActiveMeasurement> = mutableMapOf()
        for (parameter in response.parameters) {
            when (fromId(parameter)) {
                PmdMeasurementType.ECG -> measurementStatus[PmdMeasurementType.ECG] = PmdActiveMeasurement.fromStatusResponse(parameter)
                PmdMeasurementType.PPG -> measurementStatus[PmdMeasurementType.PPG] = PmdActiveMeasurement.fromStatusResponse(parameter)
                PmdMeasurementType.ACC -> measurementStatus[PmdMeasurementType.ACC] = PmdActiveMeasurement.fromStatusResponse(parameter)
                PmdMeasurementType.PPI -> measurementStatus[PmdMeasurementType.PPI] = PmdActiveMeasurement.fromStatusResponse(parameter)
                PmdMeasurementType.GYRO -> measurementStatus[PmdMeasurementType.GYRO] = PmdActiveMeasurement.fromStatusResponse(parameter)
                PmdMeasurementType.MAGNETOMETER -> measurementStatus[PmdMeasurementType.MAGNETOMETER] = PmdActiveMeasurement.fromStatusResponse(parameter)
                PmdMeasurementType.LOCATION -> measurementStatus[PmdMeasurementType.LOCATION] = PmdActiveMeasurement.fromStatusResponse(parameter)
                PmdMeasurementType.PRESSURE -> measurementStatus[PmdMeasurementType.PRESSURE] = PmdActiveMeasurement.fromStatusResponse(parameter)
                PmdMeasurementType.TEMPERATURE -> measurementStatus[PmdMeasurementType.TEMPERATURE] = PmdActiveMeasurement.fromStatusResponse(parameter)
                PmdMeasurementType.SKIN_TEMP -> measurementStatus[PmdMeasurementType.SKIN_TEMP] = PmdActiveMeasurement.fromStatusResponse(parameter)
                PmdMeasurementType.OFFLINE_HR -> measurementStatus[PmdMeasurementType.OFFLINE_HR] = PmdActiveMeasurement.fromStatusResponse(parameter)
                else -> {}
            }
        }
        measurementStatus
    }

    internal suspend fun setOfflineRecordingTrigger(offlineRecordingTrigger: PmdOfflineTrigger, secret: PmdSecret?) = withContext(Dispatchers.IO) {
        setOfflineRecordingTriggerMode(offlineRecordingTrigger.triggerMode)
        if (offlineRecordingTrigger.triggerMode != PmdOfflineRecTriggerMode.TRIGGER_DISABLE) {
            val pmdOfflineTriggers = getOfflineRecordingTriggerStatus()
            for (availableMeasurementType in pmdOfflineTriggers.triggers.keys) {
                if (offlineRecordingTrigger.triggers.keys.contains(availableMeasurementType)) {
                    val settings = offlineRecordingTrigger.triggers[availableMeasurementType]?.second
                    BleLogger.d(TAG, "Enable trigger $availableMeasurementType")
                    setOfflineRecordingTriggerSetting(PmdOfflineRecTriggerStatus.TRIGGER_ENABLED, availableMeasurementType, settings, secret)
                } else {
                    BleLogger.d(TAG, "Disable trigger $availableMeasurementType")
                    setOfflineRecordingTriggerSetting(PmdOfflineRecTriggerStatus.TRIGGER_DISABLED, availableMeasurementType)
                }
            }
        }
    }

    /**
     * Poll measurement status until the given type is inactive or timeout is reached.
     *
     * @throws Throwable on timeout or error
     */
    suspend fun waitForMeasurementInactive(
        type: PmdMeasurementType,
        pollIntervalMs: Long = 200L,
        timeoutMs: Long = 5000L
    ) = withContext(Dispatchers.IO) {
        withTimeout(timeoutMs) {
            while (true) {
                val status = readMeasurementStatus()[type] ?: PmdActiveMeasurement.NO_ACTIVE_MEASUREMENT
                if (status == PmdActiveMeasurement.NO_ACTIVE_MEASUREMENT) break
                delay(pollIntervalMs)
            }
        }
    }

    private suspend fun setOfflineRecordingTriggerMode(triggerMode: PmdOfflineRecTriggerMode) = withContext(Dispatchers.IO) {
        val parameter = byteArrayOf(triggerMode.value.toByte())
        sendControlPointCommand(PmdControlPointCommandClientToService.SET_OFFLINE_RECORDING_TRIGGER_MODE, parameter)
    }

    private suspend fun setOfflineRecordingTriggerSetting(triggerStatus: PmdOfflineRecTriggerStatus, type: PmdMeasurementType, setting: PmdSetting? = null, secret: PmdSecret? = null) = withContext(Dispatchers.IO) {
        if (!type.isDataType()) {
            throw Exception("Invalid PmdMeasurementType: $type")
        }
        val triggerStatusByte: UByte = triggerStatus.value
        val measurementTypeByte: UByte = type.numVal
        val settingsBytes = if (triggerStatus == PmdOfflineRecTriggerStatus.TRIGGER_ENABLED) {
            val settingBytes = setting?.serializeSelected() ?: byteArrayOf()
            val securityBytes = secret?.serializeToPmdSettings() ?: byteArrayOf()
            byteArrayOf((settingBytes + securityBytes).size.toByte()) + settingBytes + securityBytes
        } else {
            byteArrayOf()
        }
        val parameters = byteArrayOf(triggerStatusByte.toByte(), measurementTypeByte.toByte()) + settingsBytes
        try {
            sendControlPointCommand(PmdControlPointCommandClientToService.SET_OFFLINE_RECORDING_TRIGGER_SETTINGS, parameters)
        } catch (it: BleControlPointCommandError) {
            throw BleControlPointCommandError(
                message = "$type $triggerStatus Trigger Setting failed",
                error = it.error
            )
        }
    }

    internal suspend fun getOfflineRecordingTriggerStatus(): PmdOfflineTrigger = withContext(Dispatchers.IO) {
        val response = sendControlPointCommand(PmdControlPointCommandClientToService.GET_OFFLINE_RECORDING_TRIGGER_STATUS)
        PmdOfflineTrigger.parseFromResponse(response.parameters)
    }

    /**
     * Request to start SDK mode.
     *
     * @throws Throwable on failure
     */
    suspend fun startSDKMode() = withContext(Dispatchers.IO) {
        sendControlPointCommand(PmdControlPointCommandClientToService.REQUEST_MEASUREMENT_START, PmdMeasurementType.SDK_MODE.numVal.toByte())
        clearStreamObservers(BleOperationModeChange("SDK mode enabled"))
    }

    /**
     * Request to stop SDK mode.
     *
     * @throws Throwable on failure
     */
    suspend fun stopSDKMode() = withContext(Dispatchers.IO) {
        sendControlPointCommand(PmdControlPointCommandClientToService.STOP_MEASUREMENT, PmdMeasurementType.SDK_MODE.numVal.toByte())
        clearStreamObservers(BleOperationModeChange("SDK mode disabled"))
    }

    /**
     * Check if SDK mode is enabled.
     *
     * @return [PmdSdkMode] on success, throws on error
     */
    internal suspend fun isSdkModeEnabled(): PmdSdkMode = withContext(Dispatchers.IO) {
        val response = sendControlPointCommand(PmdControlPointCommandClientToService.GET_SDK_MODE_STATUS)
        PmdSdkMode.fromResponse(response.parameters.first())
    }

    /**
     * Request to stop measurement.
     *
     * @param type measurement to stop
     * @throws Throwable on failure
     */
    suspend fun stopMeasurement(type: PmdMeasurementType) = withContext(Dispatchers.IO) {
        sendControlPointCommand(PmdControlPointCommandClientToService.STOP_MEASUREMENT, byteArrayOf(type.numVal.toByte()))
        previousTimeStampMap = mutableMapOf()
    }

    internal fun monitorEcgNotifications(checkConnection: Boolean): Flow<EcgData> {
        return ChannelUtils.monitorNotifications(ecgObservers, txInterface, checkConnection)
    }

    internal fun monitorAccNotifications(checkConnection: Boolean): Flow<AccData> {
        return ChannelUtils.monitorNotifications(accObservers, txInterface, checkConnection)
    }

    internal fun monitorPpgNotifications(checkConnection: Boolean): Flow<PpgData> {
        return ChannelUtils.monitorNotifications(ppgObservers, txInterface, checkConnection)
    }

    internal fun monitorPpiNotifications(checkConnection: Boolean): Flow<PpiData> {
        return ChannelUtils.monitorNotifications(ppiObservers, txInterface, checkConnection)
    }

    internal fun monitorMagnetometerNotifications(checkConnection: Boolean): Flow<MagData> {
        return ChannelUtils.monitorNotifications(magnetometerObservers, txInterface, checkConnection)
    }

    internal fun monitorGyroNotifications(checkConnection: Boolean): Flow<GyrData> {
        return ChannelUtils.monitorNotifications(gyroObservers, txInterface, checkConnection)
    }

    internal fun monitorPressureNotifications(checkConnection: Boolean): Flow<PressureData> {
        return ChannelUtils.monitorNotifications(pressureObservers, txInterface, checkConnection)
    }

    internal fun monitorLocationNotifications(checkConnection: Boolean): Flow<GnssLocationData> {
        return ChannelUtils.monitorNotifications(locationObservers, txInterface, checkConnection)
    }

    internal fun monitorTemperatureNotifications(checkConnection: Boolean): Flow<TemperatureData> {
        return ChannelUtils.monitorNotifications(temperatureObservers, txInterface, checkConnection)
    }

    internal fun monitorSkinTemperatureNotifications(checkConnection: Boolean): Flow<SkinTemperatureData> {
        return ChannelUtils.monitorNotifications(skinTemperatureObservers, txInterface, checkConnection)
    }

    override suspend fun clientReady(checkConnection: Boolean) {
        waitNotificationEnabled(PMD_CP, checkConnection)
        waitNotificationEnabled(PMD_DATA, checkConnection)
    }

    private fun clearStreamObservers(throwable: Throwable) {
        ChannelUtils.postExceptionAndClearList(ecgObservers, throwable)
        ChannelUtils.postExceptionAndClearList(accObservers, throwable)
        ChannelUtils.postExceptionAndClearList(ppgObservers, throwable)
        ChannelUtils.postExceptionAndClearList(ppiObservers, throwable)
        ChannelUtils.postExceptionAndClearList(gyroObservers, throwable)
        ChannelUtils.postExceptionAndClearList(magnetometerObservers, throwable)
        ChannelUtils.postExceptionAndClearList(pressureObservers, throwable)
        ChannelUtils.postExceptionAndClearList(locationObservers, throwable)
        ChannelUtils.postExceptionAndClearList(temperatureObservers, throwable)
        ChannelUtils.postExceptionAndClearList(skinTemperatureObservers, throwable)
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
            val samples: MutableList<List<Int>> = mutableListOf(refSamples)
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
                    samples.add(nextSamples)
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

