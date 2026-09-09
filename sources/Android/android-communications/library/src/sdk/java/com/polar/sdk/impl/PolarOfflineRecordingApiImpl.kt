// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl

import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdActiveMeasurement
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdRecordingType
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdSetting
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.AccData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.DerivedAccData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.GyrData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.MagData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.OfflineHrData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.PpgData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.PpiData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.SkinTemperatureData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.TemperatureData
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.PftpResponseError
import com.polar.androidcommunications.api.ble.model.offlinerecording.OfflineRecordingData
import com.polar.androidcommunications.api.ble.model.offlinerecording.OfflineRecordingError
import com.polar.androidcommunications.api.ble.model.offlinerecording.OfflineRecordingUtility.mapOfflineRecordingFileNameToMeasurementType
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility.Companion.getFileSystemType
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility.FileSystemType
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.PolarDerivedMeasurementApi
import com.polar.sdk.api.PolarOfflineRecordingApi
import com.polar.sdk.api.errors.PolarDeviceDisconnected
import com.polar.sdk.api.errors.PolarOfflineRecordingError
import com.polar.sdk.api.errors.PolarOperationNotSupported
import com.polar.sdk.api.errors.PolarServiceNotAvailable
import com.polar.sdk.api.model.PolarOfflineRecordingData
import com.polar.sdk.api.model.PolarDerivedMeasurementSettings
import com.polar.sdk.api.model.PolarDerivedMeasurementSettingsGroup
import com.polar.sdk.api.model.PolarOfflineRecordingEntry
import com.polar.sdk.api.model.PolarOfflineRecordingResult
import com.polar.sdk.api.model.PolarOfflineRecordingTrigger
import com.polar.sdk.api.model.PolarRecordingSecret
import com.polar.sdk.api.model.PolarSensorSetting
import com.polar.sdk.impl.utils.PolarDataUtils
import com.polar.sdk.impl.utils.PolarDataUtils.mapPMDClientOfflineHrDataToPolarHrData
import com.polar.sdk.impl.utils.PolarDataUtils.mapPMDClientOfflineTemperatureDataToPolarTemperatureData
import com.polar.sdk.impl.utils.PolarDataUtils.mapPMDClientPpgDataToPolarPpg
import com.polar.sdk.impl.utils.PolarDataUtils.mapPMDClientPpiDataToPolarPpiData
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdClientAccDataToPolarAcc
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdClientDerivedAccDataToPolarDerivedAcc
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdClientFeatureToPolarFeature
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdClientGyroDataToPolarGyro
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdClientMagDataToPolarMagnetometer
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdClientSkinTemperatureDataToPolarTemperatureData
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdClientTemperatureDataToPolarTemperature
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdSettingsToPolarSettings
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdTriggerToPolarTrigger
import com.polar.sdk.impl.utils.PolarDataUtils.mapPolarFeatureToPmdClientMeasurementType
import com.polar.sdk.impl.utils.PolarDataUtils.mapPolarOfflineTriggerToPmdOfflineTrigger
import com.polar.sdk.impl.utils.PolarDataUtils.mapPolarSecretToPmdSecret
import com.polar.sdk.impl.utils.PolarDataUtils.mapPolarSettingsToPmdSettings
import com.polar.sdk.impl.utils.PolarFileUtils
import com.polar.sdk.impl.utils.PolarOfflineRecordingUtils
import com.polar.sdk.impl.utils.PolarServiceClientUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import protocol.PftpError.PbPFtpError
import protocol.PftpRequest
import protocol.PftpResponse.PbPFtpDirectory
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Implementation of [PolarOfflineRecordingApi] and [PolarDerivedMeasurementApi].
 *
 * Handles offline recording listing, fetching, deletion, trigger configuration, and
 * derived measurement recording on Polar devices via the PMD and PFTP protocols.
 *
 * Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING].
 */
internal class PolarOfflineRecordingApiImpl(
    private val listener: BleDeviceListener
) : PolarOfflineRecordingApi, PolarDerivedMeasurementApi {

    companion object {
        private const val TAG = "PolarOfflineRecordingApiImpl"
        private const val PMD_FILES_PATH = "/PMDFILES.TXT"
    }

    /** Cache of last derived method IDs per device, used for hint-based parsing of offline records. */
    val lastDerivedMethodsCache: MutableMap<String, Set<Int>> = mutableMapOf()


    private class OfflineRecordingAccumulator {
        var accData: PolarOfflineRecordingData.AccOfflineRecording? = null
        var gyroData: PolarOfflineRecordingData.GyroOfflineRecording? = null
        var magData: PolarOfflineRecordingData.MagOfflineRecording? = null
        var ppgData: PolarOfflineRecordingData.PpgOfflineRecording? = null
        var ppiData: PolarOfflineRecordingData.PpiOfflineRecording? = null
        var hrData: PolarOfflineRecordingData.HrOfflineRecording? = null
        var temperatureData: PolarOfflineRecordingData.TemperatureOfflineRecording? = null
        var skinTemperatureData: PolarOfflineRecordingData.SkinTemperatureOfflineRecording? = null
        var derivedAccData: PolarOfflineRecordingData.DerivedAccOfflineRecording? = null

        fun getResult(): PolarOfflineRecordingData? =
            ppiData ?: ppgData ?: derivedAccData ?: accData ?: gyroData ?: magData ?: hrData
                ?: temperatureData ?: skinTemperatureData
    }


    override suspend fun requestOfflineRecordingSettings(
        identifier: String,
        feature: PolarDeviceDataType
    ): PolarSensorSetting {
        BleLogger.d(TAG, "Request offline recording settings. Feature: $feature Device: $identifier")
        return when (feature) {
            PolarDeviceDataType.ECG -> querySettings(identifier, PmdMeasurementType.ECG, PmdRecordingType.OFFLINE)
            PolarDeviceDataType.ACC -> querySettings(identifier, PmdMeasurementType.ACC, PmdRecordingType.OFFLINE)
            PolarDeviceDataType.PPG -> querySettings(identifier, PmdMeasurementType.PPG, PmdRecordingType.OFFLINE)
            PolarDeviceDataType.GYRO -> querySettings(identifier, PmdMeasurementType.GYRO, PmdRecordingType.OFFLINE)
            PolarDeviceDataType.MAGNETOMETER -> querySettings(identifier, PmdMeasurementType.MAGNETOMETER, PmdRecordingType.OFFLINE)
            PolarDeviceDataType.PRESSURE -> querySettings(identifier, PmdMeasurementType.PRESSURE, PmdRecordingType.OFFLINE)
            PolarDeviceDataType.LOCATION -> querySettings(identifier, PmdMeasurementType.LOCATION, PmdRecordingType.OFFLINE)
            PolarDeviceDataType.TEMPERATURE -> querySettings(identifier, PmdMeasurementType.TEMPERATURE, PmdRecordingType.OFFLINE)
            PolarDeviceDataType.SKIN_TEMPERATURE -> querySettings(identifier, PmdMeasurementType.SKIN_TEMP, PmdRecordingType.OFFLINE)
            PolarDeviceDataType.HR,
            PolarDeviceDataType.PPI,
            PolarDeviceDataType.DERIVED_MEASUREMENT -> throw PolarOperationNotSupported()
        }
    }

    override suspend fun requestFullOfflineRecordingSettings(
        identifier: String,
        feature: PolarDeviceDataType
    ): PolarSensorSetting {
        BleLogger.d(TAG, "Request full offline recording settings. Feature: $feature Device: $identifier")
        return when (feature) {
            PolarDeviceDataType.ECG -> queryFullSettings(identifier, PmdMeasurementType.ECG, PmdRecordingType.OFFLINE)
            PolarDeviceDataType.ACC -> queryFullSettings(identifier, PmdMeasurementType.ACC, PmdRecordingType.OFFLINE)
            PolarDeviceDataType.PPG -> queryFullSettings(identifier, PmdMeasurementType.PPG, PmdRecordingType.OFFLINE)
            PolarDeviceDataType.GYRO -> queryFullSettings(identifier, PmdMeasurementType.GYRO, PmdRecordingType.OFFLINE)
            PolarDeviceDataType.MAGNETOMETER -> queryFullSettings(identifier, PmdMeasurementType.MAGNETOMETER, PmdRecordingType.OFFLINE)
            PolarDeviceDataType.PPI,
            PolarDeviceDataType.HR,
            PolarDeviceDataType.PRESSURE,
            PolarDeviceDataType.LOCATION,
            PolarDeviceDataType.TEMPERATURE,
            PolarDeviceDataType.SKIN_TEMPERATURE,
            PolarDeviceDataType.DERIVED_MEASUREMENT -> throw PolarOperationNotSupported()
        }
    }

    override suspend fun getAvailableOfflineRecordingDataTypes(identifier: String): Set<PolarDeviceDataType> {
        val session = PolarServiceClientUtils.sessionPmdClientReady(identifier, listener)
        val blePMDClient = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient?
            ?: throw PolarServiceNotAvailable()
        val pmdFeature = blePMDClient.readFeature(true)
        val deviceData: MutableSet<PolarDeviceDataType> = mutableSetOf()
        if (pmdFeature.contains(PmdMeasurementType.ECG)) deviceData.add(PolarDeviceDataType.ECG)
        if (pmdFeature.contains(PmdMeasurementType.ACC)) deviceData.add(PolarDeviceDataType.ACC)
        if (pmdFeature.contains(PmdMeasurementType.PPG)) deviceData.add(PolarDeviceDataType.PPG)
        if (pmdFeature.contains(PmdMeasurementType.PPI)) deviceData.add(PolarDeviceDataType.PPI)
        if (pmdFeature.contains(PmdMeasurementType.GYRO)) deviceData.add(PolarDeviceDataType.GYRO)
        if (pmdFeature.contains(PmdMeasurementType.MAGNETOMETER)) deviceData.add(PolarDeviceDataType.MAGNETOMETER)
        if (pmdFeature.contains(PmdMeasurementType.PRESSURE)) deviceData.add(PolarDeviceDataType.PRESSURE)
        if (pmdFeature.contains(PmdMeasurementType.LOCATION)) deviceData.add(PolarDeviceDataType.LOCATION)
        if (pmdFeature.contains(PmdMeasurementType.TEMPERATURE)) deviceData.add(PolarDeviceDataType.TEMPERATURE)
        if (pmdFeature.contains(PmdMeasurementType.OFFLINE_HR)) deviceData.add(PolarDeviceDataType.HR)
        if (pmdFeature.contains(PmdMeasurementType.SKIN_TEMP)) deviceData.add(PolarDeviceDataType.SKIN_TEMPERATURE)
        return deviceData
    }

    override fun listOfflineRecordings(identifier: String): Flow<PolarOfflineRecordingEntry> {
        return flow {
            val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
            val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                ?: throw PolarServiceNotAvailable()
            val data = deviceSupportsFasterOfflineRecordListing(identifier)
            if (data.isNotEmpty()) {
                val entries = PolarOfflineRecordingUtils.listOfflineRecordingsV2(data)
                for (entry in entries) emit(entry)
            } else {
                PolarOfflineRecordingUtils.listOfflineRecordingsV1(client) { c, path, condition ->
                    PolarFileUtils.fetchRecursively(c, path, { entry -> condition(entry) }, tag = TAG, recurseDeep = true)
                }.collect { emit(it) }
            }
        }
    }

    override suspend fun getOfflineRecord(
        identifier: String,
        entry: PolarOfflineRecordingEntry,
        secret: PolarRecordingSecret?
    ): PolarOfflineRecordingData {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        if (getFileSystemType(session.polarDeviceType) != FileSystemType.POLAR_FILE_SYSTEM_V2) {
            throw PolarOperationNotSupported()
        }
        val accumulator = OfflineRecordingAccumulator()
        val pair = getSubRecordingAndOtherFilesCount(client, entry)
        val count = pair.first
        return if (count == 0) {
            fetchSingleOfflineRecord(client, entry, secret, identifier)
        } else {
            fetchSubRecordings(client, entry, secret, identifier, count, accumulator)
        }
    }

    override fun getOfflineRecordWithProgress(
        identifier: String,
        entry: PolarOfflineRecordingEntry,
        secret: PolarRecordingSecret?
    ): Flow<PolarOfflineRecordingResult> = channelFlow {
        val totalBytes = entry.size
        val accumulatedBytes = AtomicLong(0L)
        send(PolarOfflineRecordingResult.Progress(bytesDownloaded = 0L, totalBytes = totalBytes, progressPercent = 0))

        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        if (getFileSystemType(session.polarDeviceType) != FileSystemType.POLAR_FILE_SYSTEM_V2) {
            throw PolarOperationNotSupported()
        }
        client.setProgressCallback(BlePsFtpClient.ProgressCallback { bytesReceived ->
            val currentBytes = accumulatedBytes.addAndGet(bytesReceived)
            val percent = if (totalBytes > 0) ((currentBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
            BleLogger.d(TAG, "Progress: $currentBytes/$totalBytes ($percent%)")
            trySend(PolarOfflineRecordingResult.Progress(bytesDownloaded = currentBytes, totalBytes = totalBytes, progressPercent = percent))
        })
        val accumulator = OfflineRecordingAccumulator()
        val pair = getSubRecordingAndOtherFilesCount(client, entry)
        val count = pair.first
        val data = if (count == 0) {
            fetchSingleOfflineRecord(client, entry, secret, identifier)
        } else {
            fetchSubRecordings(client, entry, secret, identifier, count, accumulator)
        }
        send(PolarOfflineRecordingResult.Progress(bytesDownloaded = totalBytes, totalBytes = totalBytes, progressPercent = 100))
        send(PolarOfflineRecordingResult.Complete(data))
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun listSplitOfflineRecordings(identifier: String): Flow<PolarOfflineRecordingEntry> {
        val session = try {
            PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        } catch (error: Throwable) {
            return flow { throw error }
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: return flow { throw PolarServiceNotAvailable() }

        return when (getFileSystemType(session.polarDeviceType)) {
            FileSystemType.POLAR_FILE_SYSTEM_V2 -> {
                BleLogger.d(TAG, "Start split offline recording listing in device: $identifier")
                PolarFileUtils.fetchRecursively(
                    client = client,
                    path = "/U/0/",
                    condition = { entry ->
                        entry.matches(Regex("^(\\d{8})(/)")) ||
                            entry == "R/" ||
                            entry.matches(Regex("^(\\d{6})(/)")) ||
                            entry.contains(".REC")
                    },
                    tag = TAG,
                    recurseDeep = true
                ).map { entry: Pair<String, Long> ->
                    val components = entry.first.split("/").toTypedArray()
                    val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.getDefault())
                    val date = LocalDateTime.parse(components[3] + " " + components[5], dateTimeFormatter)
                        ?: throw com.polar.sdk.api.errors.PolarInvalidArgument(
                            "Listing offline recording failed. Cannot parse create data from date ${components[3]} and time ${components[5]}"
                        )
                    val type = mapPmdClientFeatureToPolarFeature(
                        mapOfflineRecordingFileNameToMeasurementType(components[6])
                    )
                    PolarOfflineRecordingEntry(path = entry.first, size = entry.second, date = date, type = type)
                }.catch { throwable -> throw handleError(throwable) }
            }
            else -> flow { throw PolarOperationNotSupported() }
        }
    }

    @Deprecated("Use getOfflineRecordWithProgress method instead")
    override suspend fun getSplitOfflineRecord(
        identifier: String,
        entry: PolarOfflineRecordingEntry,
        secret: PolarRecordingSecret?
    ): PolarOfflineRecordingData {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val fsType = getFileSystemType(session.polarDeviceType)
        if (fsType != FileSystemType.POLAR_FILE_SYSTEM_V2) throw PolarOperationNotSupported()

        val builder = PftpRequest.PbPFtpOperation.newBuilder()
        builder.command = PftpRequest.PbPFtpOperation.Command.GET
        builder.path = entry.path

        BleLogger.d(TAG, "Split offline record get. Device: $identifier Path: ${sanitizePathForLog(entry.path)} Secret used: ${secret != null}")
        return try {
            val byteArrayOutputStream = client.request(builder.build().toByteArray())
            val pmdSecret = secret?.let { mapPolarSecretToPmdSecret(it) }
            val offlineRecData = OfflineRecordingData.parseDataFromOfflineFile(
                byteArrayOutputStream.toByteArray(),
                mapPolarFeatureToPmdClientMeasurementType(entry.type),
                pmdSecret
            )
            val polarSettings = offlineRecData.recordingSettings?.let { mapPmdSettingsToPolarSettings(it, fromSelected = false) }
            val startTime = offlineRecData.startTime
            when (val offlineData = offlineRecData.data) {
                is AccData -> {
                    polarSettings ?: throw PolarOfflineRecordingError("getSplitOfflineRecord failed. Acc data is missing settings")
                    PolarOfflineRecordingData.AccOfflineRecording(mapPmdClientAccDataToPolarAcc(offlineData), startTime, polarSettings)
                }
                is DerivedAccData ->
                    PolarOfflineRecordingData.DerivedAccOfflineRecording(mapPmdClientDerivedAccDataToPolarDerivedAcc(offlineData), startTime, polarSettings)
                is GyrData -> {
                    polarSettings ?: throw PolarOfflineRecordingError("getSplitOfflineRecord failed. Gyro data is missing settings")
                    PolarOfflineRecordingData.GyroOfflineRecording(mapPmdClientGyroDataToPolarGyro(offlineData), startTime, polarSettings)
                }
                is MagData -> {
                    polarSettings ?: throw PolarOfflineRecordingError("getSplitOfflineRecord failed. Magnetometer data is missing settings")
                    PolarOfflineRecordingData.MagOfflineRecording(mapPmdClientMagDataToPolarMagnetometer(offlineData), startTime, polarSettings)
                }
                is PpgData -> {
                    polarSettings ?: throw PolarOfflineRecordingError("getSplitOfflineRecord failed. Ppg data is missing settings")
                    PolarOfflineRecordingData.PpgOfflineRecording(mapPMDClientPpgDataToPolarPpg(offlineData), startTime, polarSettings)
                }
                is PpiData -> PolarOfflineRecordingData.PpiOfflineRecording(mapPMDClientPpiDataToPolarPpiData(offlineData), startTime)
                is OfflineHrData -> PolarOfflineRecordingData.HrOfflineRecording(mapPMDClientOfflineHrDataToPolarHrData(offlineData), startTime)
                is TemperatureData -> PolarOfflineRecordingData.TemperatureOfflineRecording(mapPMDClientOfflineTemperatureDataToPolarTemperatureData(offlineData), startTime)
                is SkinTemperatureData -> PolarOfflineRecordingData.SkinTemperatureOfflineRecording(mapPmdClientSkinTemperatureDataToPolarTemperatureData(offlineData), startTime)
                else -> throw PolarOfflineRecordingError("getSplitOfflineRecord failed. Data type is not supported.")
            }
        } catch (throwable: Throwable) {
            throw handleError(throwable)
        }
    }

    override suspend fun removeOfflineRecord(identifier: String, entry: PolarOfflineRecordingEntry) {
        BleLogger.d(TAG, "Remove offline record from device $identifier path ${sanitizePathForLog(entry.path)}")
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val fsType = getFileSystemType(session.polarDeviceType)
        if (fsType != FileSystemType.POLAR_FILE_SYSTEM_V2) throw PolarOperationNotSupported()

        try {
            val pair = getSubRecordingAndOtherFilesCount(client, entry)
            val otherFilesCount = pair.second
            val count = pair.first

            if (otherFilesCount == 0) {
                val parentDir = if (entry.path.last() == '/') {
                    entry.path.substringBeforeLast("/").dropLastWhile { it != '/' }
                } else {
                    entry.path.dropLastWhile { it != '/' }
                }
                val builder = PftpRequest.PbPFtpOperation.newBuilder()
                builder.command = PftpRequest.PbPFtpOperation.Command.REMOVE
                builder.path = parentDir
                client.request(builder.build().toByteArray())
            } else if (count == 0 || entry.path.contains(Regex("""(\D+)(\d+)\.REC"""))) {
                val builder = PftpRequest.PbPFtpOperation.newBuilder()
                builder.command = PftpRequest.PbPFtpOperation.Command.REMOVE
                builder.path = entry.path
                client.request(builder.build().toByteArray())
            } else {
                for (subRecordingIndex in 0 until count) {
                    val recordingPath = entry.path.replace(Regex("(\\d*.REC)$"), "$subRecordingIndex.REC")
                    val builder = PftpRequest.PbPFtpOperation.newBuilder()
                    builder.command = PftpRequest.PbPFtpOperation.Command.REMOVE
                    builder.path = recordingPath
                    client.request(builder.build().toByteArray())
                }
            }

            var currentDir = entry.path.substringBeforeLast("/")
            val dirs = mutableListOf<String>()
            while (currentDir != "/U/0") {
                dirs.add(currentDir)
                currentDir = currentDir.substringBeforeLast("/")
            }

            for (dir in dirs) {
                try {
                    val isEmpty = checkIfDirectoryIsEmpty(dir, client)
                    if (isEmpty) {
                        val builder = PftpRequest.PbPFtpOperation.newBuilder()
                        builder.command = PftpRequest.PbPFtpOperation.Command.REMOVE
                        builder.path = dir
                        client.request(builder.build().toByteArray())
                    }
                } catch (throwable: Throwable) {
                    throw handleError(throwable)
                }
            }
        } catch (error: Throwable) {
            BleLogger.e(TAG, "Error while trying to delete offline recordings from device $identifier, error: $error")
        }
    }

    override suspend fun startOfflineRecording(
        identifier: String,
        feature: PolarDeviceDataType,
        settings: PolarSensorSetting?,
        secret: PolarRecordingSecret?
    ) {
        val session = PolarServiceClientUtils.sessionPmdClientReady(identifier, listener)
        val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient?
            ?: throw PolarServiceNotAvailable()
        val pmdSecret = secret?.let { mapPolarSecretToPmdSecret(it) }
        client.startMeasurement(
            mapPolarFeatureToPmdClientMeasurementType(feature),
            mapPolarSettingsToPmdSettings(settings),
            PmdRecordingType.OFFLINE,
            pmdSecret
        )
    }

    override suspend fun stopOfflineRecording(identifier: String, feature: PolarDeviceDataType) {
        val session = PolarServiceClientUtils.sessionPmdClientReady(identifier, listener)
        val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as? BlePMDClient
            ?: throw PolarServiceNotAvailable()
        val measurementType = mapPolarFeatureToPmdClientMeasurementType(feature)
        BleLogger.d(TAG, "[$identifier] Sending STOP for ${feature.name}")
        try {
            client.stopMeasurement(measurementType)
            client.waitForMeasurementInactive(measurementType)
            BleLogger.d(TAG, "[$identifier] STOP confirmed for ${feature.name}")
        } catch (e: Throwable) {
            BleLogger.e(TAG, "[$identifier] STOP error for ${feature.name}: ${e.message}")
            BleLogger.w(TAG, "[$identifier] STOP failed for ${feature.name}, caller should update UI state.")
            throw e
        }
    }

    override suspend fun getOfflineRecordingStatus(identifier: String): List<PolarDeviceDataType> {
        val session = PolarServiceClientUtils.sessionPmdClientReady(identifier, listener)
        val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient?
            ?: throw PolarServiceNotAvailable()
        BleLogger.d(TAG, "Get offline recording status. Device: $identifier")
        val pmdMeasurementStatus = client.readMeasurementStatus()
        val offlineRecs: MutableList<PolarDeviceDataType> = mutableListOf()
        pmdMeasurementStatus.filter {
            it.value == PmdActiveMeasurement.OFFLINE_MEASUREMENT_ACTIVE ||
                it.value == PmdActiveMeasurement.ONLINE_AND_OFFLINE_ACTIVE
        }.forEach { offlineRecs.add(mapPmdClientFeatureToPolarFeature(it.key)) }
        return offlineRecs.toList()
    }

    override suspend fun setOfflineRecordingTrigger(
        identifier: String,
        trigger: PolarOfflineRecordingTrigger,
        secret: PolarRecordingSecret?
    ) {
        val session = PolarServiceClientUtils.sessionPmdClientReady(identifier, listener)
        val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient?
            ?: throw PolarServiceNotAvailable()
        val pmdOfflineTrigger = mapPolarOfflineTriggerToPmdOfflineTrigger(trigger)
        val pmdSecret = secret?.let { mapPolarSecretToPmdSecret(it) }
        BleLogger.d(TAG, "Setup offline recording trigger. Trigger mode: ${trigger.triggerMode} Trigger features: ${trigger.triggerFeatures.keys.joinToString(", ")} Device: $identifier Secret used: ${secret != null}")
        client.setOfflineRecordingTrigger(pmdOfflineTrigger, pmdSecret)
    }

    override suspend fun getOfflineRecordingTriggerSetup(identifier: String): PolarOfflineRecordingTrigger {
        val session = PolarServiceClientUtils.sessionPmdClientReady(identifier, listener)
        val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient?
            ?: throw PolarServiceNotAvailable()
        BleLogger.d(TAG, "Get offline recording trigger setup. Device: $identifier")
        return mapPmdTriggerToPolarTrigger(client.getOfflineRecordingTriggerStatus())
    }


    override suspend fun requestDerivedMeasurementGroupIds(
        identifier: String,
        sourceType: PolarDeviceDataType
    ): Set<Int> {
        val session = PolarServiceClientUtils.sessionPmdClientReady(identifier, listener)
        val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient?
            ?: throw PolarServiceNotAvailable()
        val pmdType = when (sourceType) {
            PolarDeviceDataType.ACC -> PmdMeasurementType.ACC
            PolarDeviceDataType.GYRO -> PmdMeasurementType.GYRO
            PolarDeviceDataType.MAGNETOMETER -> PmdMeasurementType.MAGNETOMETER
            else -> throw PolarOperationNotSupported()
        }
        BleLogger.d(TAG, "Request derived measurement group IDs for $sourceType. Device: $identifier")
        val pmdSetting = client.querySettings(pmdType, PmdRecordingType.OFFLINE)
        return pmdSetting.settings[PmdSetting.PmdSettingType.DERIVED_MEASUREMENT_SETTINGS_GROUP_ID] ?: emptySet()
    }

    override suspend fun requestDerivedMeasurementSettingsGroup(
        identifier: String,
        groupId: Int
    ): PolarDerivedMeasurementSettingsGroup {
        val session = PolarServiceClientUtils.sessionPmdClientReady(identifier, listener)
        val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient?
            ?: throw PolarServiceNotAvailable()
        BleLogger.d(TAG, "Request derived measurement settings group $groupId. Device: $identifier")
        val pmdSetting = client.queryDerivedMeasurementSettingsGroup(groupId)
        return PolarDataUtils.mapPmdSettingsToPolarDerivedMeasurementSettingsGroup(pmdSetting, requestedGroupId = groupId)
    }

    override suspend fun startDerivedOfflineRecording(
        identifier: String,
        settings: PolarDerivedMeasurementSettings,
        secret: PolarRecordingSecret?
    ) {
        val session = PolarServiceClientUtils.sessionPmdClientReady(identifier, listener)
        val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient?
            ?: throw PolarServiceNotAvailable()
        val pmdSetting = PolarDataUtils.mapPolarDerivedMeasurementSettingsToPmdSettings(settings)
        val pmdSecret = secret?.let { mapPolarSecretToPmdSecret(it) }
        BleLogger.d(
            TAG,
            "Start derived offline recording. Group: ${settings.groupId} " +
                "Source: ${settings.sourceMeasurementType} @ ${settings.sourceSampleRate} Hz " +
                "Window: ${settings.timeWindowMs} ms " +
                "Methods: ${settings.selectedMethods.joinToString { it.name }} " +
                "Device: $identifier"
        )
        client.startMeasurement(PmdMeasurementType.DERIVED_MEASUREMENT, pmdSetting, PmdRecordingType.OFFLINE, pmdSecret)
        val methodIds = settings.selectedMethods.map { it.id }.toSet()
        lastDerivedMethodsCache[identifier] = methodIds
    }

    override suspend fun stopDerivedOfflineRecording(identifier: String) {
        val session = PolarServiceClientUtils.sessionPmdClientReady(identifier, listener)
        val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient?
            ?: throw PolarServiceNotAvailable()
        BleLogger.d(TAG, "Stop derived offline recording. Device: $identifier")
        try {
            client.stopMeasurement(PmdMeasurementType.DERIVED_MEASUREMENT)
            client.waitForMeasurementInactive(PmdMeasurementType.DERIVED_MEASUREMENT)
        } catch (e: Throwable) {
            BleLogger.e(TAG, "[$identifier] Stop derived offline recording error: ${e.message}")
            throw e
        }
    }


    private suspend fun querySettings(
        identifier: String,
        type: PmdMeasurementType,
        recordingType: PmdRecordingType
    ): PolarSensorSetting {
        val session = PolarServiceClientUtils.sessionPmdClientReady(identifier, listener)
        val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient?
            ?: throw PolarServiceNotAvailable()
        val setting = client.querySettings(type, recordingType)
        return mapPmdSettingsToPolarSettings(setting, fromSelected = false)
    }

    private suspend fun queryFullSettings(
        identifier: String,
        type: PmdMeasurementType,
        recordingType: PmdRecordingType
    ): PolarSensorSetting {
        val session = PolarServiceClientUtils.sessionPmdClientReady(identifier, listener)
        val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient?
            ?: throw PolarServiceNotAvailable()
        val setting = client.queryFullSettings(type, recordingType)
        return mapPmdSettingsToPolarSettings(setting, fromSelected = false)
    }

    private fun buildPftpGetRequest(path: String): ByteArray {
        val builder = PftpRequest.PbPFtpOperation.newBuilder()
        builder.command = PftpRequest.PbPFtpOperation.Command.GET
        builder.path = path
        return builder.build().toByteArray()
    }

    private fun parseOfflineRecordingData(
        byteArrayOutputStream: ByteArrayOutputStream,
        entry: PolarOfflineRecordingEntry,
        secret: PolarRecordingSecret?,
        lastTimestamp: ULong = 0uL,
        hintDerivedMethods: Set<Int>? = null
    ): OfflineRecordingData<*> {
        val pmdSecret = secret?.let { mapPolarSecretToPmdSecret(it) }
        return OfflineRecordingData.parseDataFromOfflineFile(
            byteArrayOutputStream.toByteArray(),
            mapPolarFeatureToPmdClientMeasurementType(entry.type),
            pmdSecret,
            lastTimestamp,
            hintDerivedMethods
        )
    }

    private fun processOfflineData(
        offlineRecData: OfflineRecordingData<*>,
        accumulator: OfflineRecordingAccumulator
    ): PolarOfflineRecordingData {
        return when (val offlineData = offlineRecData.data) {
            is AccData -> processAccData(offlineData, offlineRecData, accumulator.accData).also { accumulator.accData = it }
            is DerivedAccData -> processDerivedAccData(offlineData, offlineRecData, accumulator.derivedAccData).also { accumulator.derivedAccData = it }
            is GyrData -> processGyroData(offlineData, offlineRecData, accumulator.gyroData).also { accumulator.gyroData = it }
            is MagData -> processMagData(offlineData, offlineRecData, accumulator.magData).also { accumulator.magData = it }
            is PpgData -> processPpgData(offlineData, offlineRecData, accumulator.ppgData).also { accumulator.ppgData = it }
            is PpiData -> processPpiData(offlineData, offlineRecData, accumulator.ppiData).also { accumulator.ppiData = it }
            is OfflineHrData -> processHrData(offlineData, offlineRecData, accumulator.hrData).also { accumulator.hrData = it }
            is TemperatureData -> processTemperatureData(offlineData, offlineRecData, accumulator.temperatureData).also { accumulator.temperatureData = it }
            is SkinTemperatureData -> processSkinTemperatureData(offlineData, offlineRecData, accumulator.skinTemperatureData).also { accumulator.skinTemperatureData = it }
            else -> throw PolarOfflineRecordingError("Data type is not supported.")
        }
    }

    private fun getSubRecordingPath(entryPath: String, subRecordingIndex: Int): String {
        return if (entryPath.matches(Regex(".*\\.REC$"))) {
            entryPath.replace(Regex("(\\.REC)$"), "$subRecordingIndex.REC")
        } else {
            entryPath.replace(Regex("""\d(?=\D*$)"""), subRecordingIndex.toString())
        }
    }

    private suspend fun fetchSingleOfflineRecord(
        client: BlePsFtpClient,
        entry: PolarOfflineRecordingEntry,
        secret: PolarRecordingSecret?,
        identifier: String
    ): PolarOfflineRecordingData {
        BleLogger.d(TAG, "Offline record get. Device: $identifier Path: ${sanitizePathForLog(entry.path)} Secret used: ${secret != null}")
        return try {
            val byteArrayOutputStream = client.request(buildPftpGetRequest(entry.path))
            val offlineRecData = parseOfflineRecordingData(
                byteArrayOutputStream,
                entry,
                secret,
                hintDerivedMethods = lastDerivedMethodsCache[identifier]
            )
            processOfflineData(offlineRecData, OfflineRecordingAccumulator())
        } catch (throwable: Throwable) {
            throw handleError(throwable)
        }
    }

    private suspend fun fetchSubRecordings(
        client: BlePsFtpClient,
        entry: PolarOfflineRecordingEntry,
        secret: PolarRecordingSecret?,
        identifier: String,
        count: Int,
        accumulator: OfflineRecordingAccumulator
    ): PolarOfflineRecordingData {
        val lastTimestamp = 0uL
        for (subRecordingIndex in 0 until count) {
            val subRecordingPath = getSubRecordingPath(entry.path, subRecordingIndex).ifBlank { entry.path }
            BleLogger.d(TAG, "Offline record get. Device: $identifier Path: ${sanitizePathForLog(subRecordingPath)} Secret used: ${secret != null}, lastTimestamp: $lastTimestamp")
            try {
                val byteArrayOutputStream = client.request(buildPftpGetRequest(subRecordingPath))
                val offlineRecordingData = parseOfflineRecordingData(
                    byteArrayOutputStream,
                    entry,
                    secret,
                    lastTimestamp,
                    hintDerivedMethods = lastDerivedMethodsCache[identifier]
                )
                processOfflineData(offlineRecordingData, accumulator)
            } catch (throwable: Throwable) {
                throw handleError(throwable)
            }
        }
        return accumulator.getResult() ?: throw PolarOfflineRecordingError("No data was recorded")
    }

    private fun processAccData(
        offlineData: AccData,
        offlineRecordingData: OfflineRecordingData<*>,
        existingData: PolarOfflineRecordingData.AccOfflineRecording?
    ): PolarOfflineRecordingData.AccOfflineRecording {
        val polarSettings = offlineRecordingData.recordingSettings?.let {
            mapPmdSettingsToPolarSettings(it, fromSelected = false)
        } ?: throw PolarOfflineRecordingError("getOfflineRecord failed. Acc data is missing settings")
        val polarAcc = mapPmdClientAccDataToPolarAcc(offlineData)
        return existingData?.appendAccData(existingData, polarAcc, polarSettings)
            ?: PolarOfflineRecordingData.AccOfflineRecording(polarAcc, offlineRecordingData.startTime, polarSettings)
    }

    private fun processDerivedAccData(
        offlineData: DerivedAccData,
        offlineRecordingData: OfflineRecordingData<*>,
        existingData: PolarOfflineRecordingData.DerivedAccOfflineRecording?
    ): PolarOfflineRecordingData.DerivedAccOfflineRecording {
        val polarSettings = offlineRecordingData.recordingSettings?.let {
            mapPmdSettingsToPolarSettings(it, fromSelected = false)
        }
        if (polarSettings == null) {
            BleLogger.w(TAG, "processDerivedAccData: recordingSettings absent from file — settings will be null in output")
        }
        val polarDerivedAcc = mapPmdClientDerivedAccDataToPolarDerivedAcc(offlineData)
        return existingData?.appendDerivedAccData(existingData, polarDerivedAcc, polarSettings)
            ?: PolarOfflineRecordingData.DerivedAccOfflineRecording(polarDerivedAcc, offlineRecordingData.startTime, polarSettings)
    }

    private fun processGyroData(
        offlineData: GyrData,
        offlineRecordingData: OfflineRecordingData<*>,
        existingData: PolarOfflineRecordingData.GyroOfflineRecording?
    ): PolarOfflineRecordingData.GyroOfflineRecording {
        val polarSettings = offlineRecordingData.recordingSettings?.let {
            mapPmdSettingsToPolarSettings(it, fromSelected = false)
        } ?: throw PolarOfflineRecordingError("getOfflineRecord failed. Gyro data is missing settings")
        val polarGyr = mapPmdClientGyroDataToPolarGyro(offlineData)
        return existingData?.appendGyroData(existingData, polarGyr, polarSettings)
            ?: PolarOfflineRecordingData.GyroOfflineRecording(polarGyr, offlineRecordingData.startTime, polarSettings)
    }

    private fun processMagData(
        offlineData: MagData,
        offlineRecordingData: OfflineRecordingData<*>,
        existingData: PolarOfflineRecordingData.MagOfflineRecording?
    ): PolarOfflineRecordingData.MagOfflineRecording {
        val polarSettings = offlineRecordingData.recordingSettings?.let {
            mapPmdSettingsToPolarSettings(it, fromSelected = false)
        } ?: throw PolarOfflineRecordingError("getOfflineRecord failed. Magnetometer data is missing settings")
        val polarMag = mapPmdClientMagDataToPolarMagnetometer(offlineData)
        return existingData?.appendMagData(existingData, polarMag)
            ?: PolarOfflineRecordingData.MagOfflineRecording(polarMag, offlineRecordingData.startTime, polarSettings)
    }

    private fun processPpgData(
        offlineData: PpgData,
        offlineRecordingData: OfflineRecordingData<*>,
        existingData: PolarOfflineRecordingData.PpgOfflineRecording?
    ): PolarOfflineRecordingData.PpgOfflineRecording {
        val polarSettings = offlineRecordingData.recordingSettings?.let {
            mapPmdSettingsToPolarSettings(it, fromSelected = false)
        } ?: throw PolarOfflineRecordingError("getOfflineRecord failed. Ppg data is missing settings")
        val polarPpg = mapPMDClientPpgDataToPolarPpg(offlineData)
        return existingData?.appendPpgData(existingData, polarPpg)
            ?: PolarOfflineRecordingData.PpgOfflineRecording(polarPpg, offlineRecordingData.startTime, polarSettings)
    }

    private fun processPpiData(
        offlineData: PpiData,
        offlineRecordingData: OfflineRecordingData<*>,
        existingData: PolarOfflineRecordingData.PpiOfflineRecording?
    ): PolarOfflineRecordingData.PpiOfflineRecording {
        val ppiResult = PolarOfflineRecordingData.PpiOfflineRecording(
            mapPMDClientPpiDataToPolarPpiData(offlineData),
            offlineRecordingData.startTime
        )
        return existingData?.appendPpiData(existingData, ppiResult.data) ?: ppiResult
    }

    private fun processHrData(
        offlineData: OfflineHrData,
        offlineRecordingData: OfflineRecordingData<*>,
        existingData: PolarOfflineRecordingData.HrOfflineRecording?
    ): PolarOfflineRecordingData.HrOfflineRecording {
        return existingData?.appendHrData(existingData, mapPMDClientOfflineHrDataToPolarHrData(offlineData))
            ?: PolarOfflineRecordingData.HrOfflineRecording(mapPMDClientOfflineHrDataToPolarHrData(offlineData), offlineRecordingData.startTime)
    }

    private fun processTemperatureData(
        offlineData: TemperatureData,
        offlineRecordingData: OfflineRecordingData<*>,
        existingData: PolarOfflineRecordingData.TemperatureOfflineRecording?
    ): PolarOfflineRecordingData.TemperatureOfflineRecording {
        return existingData?.appendTemperatureData(existingData, mapPMDClientOfflineTemperatureDataToPolarTemperatureData(offlineData))
            ?: PolarOfflineRecordingData.TemperatureOfflineRecording(mapPMDClientOfflineTemperatureDataToPolarTemperatureData(offlineData), offlineRecordingData.startTime)
    }

    private fun processSkinTemperatureData(
        offlineData: SkinTemperatureData,
        offlineRecordingData: OfflineRecordingData<*>,
        existingData: PolarOfflineRecordingData.SkinTemperatureOfflineRecording?
    ): PolarOfflineRecordingData.SkinTemperatureOfflineRecording {
        return existingData?.appendSkinTemperatureData(existingData, mapPmdClientSkinTemperatureDataToPolarTemperatureData(offlineData))
            ?: PolarOfflineRecordingData.SkinTemperatureOfflineRecording(mapPmdClientSkinTemperatureDataToPolarTemperatureData(offlineData), offlineRecordingData.startTime)
    }

    private suspend fun getSubRecordingAndOtherFilesCount(
        client: BlePsFtpClient,
        entry: PolarOfflineRecordingEntry
    ): Pair<Int, Int> {
        return try {
            val builder = PftpRequest.PbPFtpOperation.newBuilder()
            builder.command = PftpRequest.PbPFtpOperation.Command.GET
            val directoryPath = entry.path.substring(0, entry.path.lastIndexOf("/") + 1)
            builder.path = directoryPath
            val byteArrayOutputStream = client.request(builder.build().toByteArray())
            val directory = PbPFtpDirectory.parseFrom(byteArrayOutputStream.toByteArray())
            val prefix = entry.path.substringAfterLast("/").substringBefore(".REC")
            val matchingEntries = directory.entriesList.filter {
                it.name.startsWith(prefix) && Regex("\\d\\.").containsMatchIn(it.name)
            }
            val nonMatchingEntriesSize = directory.entriesList.size - matchingEntries.size
            Pair(matchingEntries.size, nonMatchingEntriesSize)
        } catch (throwable: Throwable) {
            if (throwable is PftpResponseError) {
                val errorId = throwable.error
                if (errorId == PbPFtpError.NO_SUCH_FILE_OR_DIRECTORY.number) {
                    BleLogger.w(TAG, "Directory not found for path ${sanitizePathForLog(entry.path)}, returning empty counts")
                    Pair(0, 0)
                } else {
                    throw throwable
                }
            } else {
                throw throwable
            }
        }
    }

    private suspend fun checkIfDirectoryIsEmpty(directoryPath: String, client: BlePsFtpClient): Boolean {
        var path = directoryPath
        if (!path.endsWith("/")) path = path.plus("/")
        val builder = PftpRequest.PbPFtpOperation.newBuilder()
        builder.command = PftpRequest.PbPFtpOperation.Command.GET
        builder.path = path
        return try {
            val byteArrayOutputStream = client.request(builder.build().toByteArray())
            val directory = PbPFtpDirectory.parseFrom(byteArrayOutputStream.toByteArray())
            directory.entriesList.size == 0
        } catch (throwable: Throwable) {
            if (throwable is PftpResponseError) {
                val errorId = throwable.error
                if (errorId == 103) false else throw throwable
            } else {
                throw throwable
            }
        }
    }

    private suspend fun deviceSupportsFasterOfflineRecordListing(identifier: String): ByteArray {
        return try {
            PolarFileUtils.getFile(identifier, PMD_FILES_PATH, listener, TAG)
        } catch (e: Exception) {
            BleLogger.e(TAG, "Failed to check if device supports fast offline record listing: $e")
            byteArrayOf()
        }
    }

    private fun mapOfflineRecordingTypeToPolarDeviceDataType(offlineRecordingDataType: String): PolarDeviceDataType {
        return when (offlineRecordingDataType) {
            "SKINTEMP" -> PolarDeviceDataType.SKIN_TEMPERATURE
            else -> PolarDeviceDataType.entries.find { it.name == offlineRecordingDataType }
                ?: throw com.polar.sdk.api.errors.PolarInvalidArgument(
                    "Unknown offline recording data type: '$offlineRecordingDataType'. " +
                        "Known types: ${PolarDeviceDataType.entries.joinToString()}"
                )
        }
    }

    private fun sanitizePathForLog(path: String): String {
        val normalized = path.replace('\\', '/')
        val lastSegment = normalized.substringAfterLast('/', "")
        return if (lastSegment.isEmpty()) "<path>" else ".../$lastSegment"
    }

    private fun handleError(throwable: Throwable): Exception {
        if (throwable is BleDisconnected) {
            return PolarDeviceDisconnected()
        } else if (throwable is PftpResponseError) {
            val errorId = throwable.error
            val pftpError = PbPFtpError.forNumber(errorId)
            if (pftpError != null) return Exception(pftpError.toString())
        } else if (throwable is OfflineRecordingError) {
            val message = when (throwable) {
                is OfflineRecordingError.OfflineRecordingEmptyFile -> "Offline recording file is empty"
                is OfflineRecordingError.OfflineRecordingNoPayloadData -> "Offline recording has no payload data"
                is OfflineRecordingError.OfflineRecordingHasWrongSignature -> "Offline recording has wrong signature"
                is OfflineRecordingError.OfflineRecordingErrorSecretMissing -> "Offline recording secret is missing"
                is OfflineRecordingError.OfflineRecordingErrorNoParserForData -> "No parser available for offline recording data"
                is OfflineRecordingError.OfflineRecordingSecurityStrategyMissMatch -> "Offline recording security strategy mismatch: ${throwable.message}"
                is OfflineRecordingError.OfflineRecordingErrorMetaDataParseFailed -> "Offline recording metadata parse failed: ${throwable.message}"
            }
            return PolarOfflineRecordingError(message)
        }
        return Exception(throwable)
    }
}


