// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.BleDeviceListener.BlePowerStateChangedCallback
import com.polar.androidcommunications.api.ble.BleDeviceListener.BleSearchPreFilter
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.BleLogger.Companion.setLoggerInterface
import com.polar.androidcommunications.api.ble.exceptions.*
import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.api.ble.model.BleDeviceSession.DeviceSessionState
import com.polar.androidcommunications.api.ble.model.advertisement.BleAdvertisementContent
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.client.BleBattClient
import com.polar.androidcommunications.api.ble.model.gatt.client.BleBattClient.Companion.BATTERY_SERVICE
import com.polar.androidcommunications.api.ble.model.gatt.client.BleDisClient
import com.polar.androidcommunications.api.ble.model.gatt.client.BleHrClient
import com.polar.androidcommunications.api.ble.model.gatt.client.BleHrClient.*
import com.polar.androidcommunications.api.ble.model.gatt.client.BleHrClient.Companion.HR_SERVICE
import com.polar.androidcommunications.api.ble.model.gatt.client.BleHrClient.Companion.HR_SERVICE_16BIT_UUID
import com.polar.androidcommunications.api.ble.model.gatt.client.BleHtsClient
import com.polar.androidcommunications.api.ble.model.gatt.client.BlePfcClient
import com.polar.androidcommunications.api.ble.model.gatt.client.BlePfcClient.Companion.PFC_SERVICE
import com.polar.androidcommunications.api.ble.model.gatt.client.BlePfcClient.PfcMessage
import com.polar.androidcommunications.api.ble.model.gatt.client.ChargeState
import com.polar.androidcommunications.api.ble.model.gatt.client.HealthThermometer
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.*
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdControlPointResponse.PmdControlPointResponseCode
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.*
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.PFTP_SERVICE_16BIT_UUID
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.PftpResponseError
import com.polar.androidcommunications.api.ble.model.offlinerecording.OfflineRecordingData
import com.polar.androidcommunications.api.ble.model.offlinerecording.OfflineRecordingUtility.mapOfflineRecordingFileNameToMeasurementType
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility.Companion.getFileSystemType
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility.Companion.isRecordingSupported
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility.FileSystemType
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.BDDeviceListenerImpl
import com.polar.androidcommunications.http.client.HttpResponseCodes
import com.polar.androidcommunications.http.client.RetrofitClient
import com.polar.androidcommunications.http.fwu.FirmwareUpdateApi
import com.polar.androidcommunications.http.fwu.FirmwareUpdateRequest
import com.polar.sdk.api.model.PolarExerciseSession
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallbackProvider
import com.polar.sdk.api.PolarBleLowLevelApi
import com.polar.sdk.api.PolarD2HNotificationData
import com.polar.sdk.api.PolarOfflineExerciseV2Api
import com.polar.sdk.api.PolarTestApi
import com.polar.sdk.api.model.PolarSpo2TestData
import com.polar.sdk.impl.utils.PolarTestUtils
import com.polar.sdk.api.PolarH10OfflineExerciseApi
import com.polar.sdk.api.RestApiEventPayload
import com.polar.sdk.api.errors.*
import com.polar.sdk.api.model.*
import com.polar.sdk.api.model.restapi.PolarDeviceRestApiServiceDescription
import com.polar.sdk.api.model.restapi.PolarDeviceRestApiServices
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionReference
import com.polar.sdk.api.PolarTrainingSessionApi
import com.polar.sdk.impl.utils.PolarBackupManager
import com.polar.sdk.impl.utils.PolarDataUtils
import com.polar.sdk.impl.utils.PolarDataUtils.mapPMDClientLocationDataToPolarLocationData
import com.polar.sdk.impl.utils.PolarDataUtils.mapPMDClientOfflineHrDataToPolarHrData
import com.polar.sdk.impl.utils.PolarDataUtils.mapPMDClientOfflineTemperatureDataToPolarTemperatureData
import com.polar.sdk.impl.utils.PolarDataUtils.mapPMDClientPpgDataToPolarPpg
import com.polar.sdk.impl.utils.PolarDataUtils.mapPMDClientPpiDataToPolarPpiData
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdClientAccDataToPolarAcc
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdClientFeatureToPolarFeature
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdClientGyroDataToPolarGyro
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdClientMagDataToPolarMagnetometer
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdClientPressureDataToPolarPressure
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdClientSkinTemperatureDataToPolarTemperatureData
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdClientTemperatureDataToPolarTemperature
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdSettingsToPolarSettings
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdTriggerToPolarTrigger
import com.polar.sdk.impl.utils.PolarDataUtils.mapPolarFeatureToPmdClientMeasurementType
import com.polar.sdk.impl.utils.PolarDataUtils.mapPolarOfflineTriggerToPmdOfflineTrigger
import com.polar.sdk.impl.utils.PolarDataUtils.mapPolarSecretToPmdSecret
import com.polar.sdk.impl.utils.PolarDataUtils.mapPolarSettingsToPmdSettings
import com.polar.sdk.impl.utils.PolarFirmwareUpdateUtils
import com.polar.sdk.impl.utils.PolarOfflineRecordingUtils
import com.polar.sdk.impl.utils.receiveRestApiEvents
import com.polar.sdk.impl.utils.observeDeviceToHostNotifications
import fi.polar.remote.representation.protobuf.AutomaticSamples.PbAutomaticSampleSessions
import fi.polar.remote.representation.protobuf.ExerciseSamples.PbExerciseSamples
import fi.polar.remote.representation.protobuf.PhysData
import fi.polar.remote.representation.protobuf.Types.*
import fi.polar.remote.representation.protobuf.UserIds
import java.util.concurrent.ConcurrentHashMap
import protocol.PftpError.PbPFtpError
import protocol.PftpNotification
import protocol.PftpRequest
import protocol.PftpResponse
import protocol.PftpResponse.PbPFtpDirectory
import protocol.PftpResponse.PbRequestRecordingStatusResult
import com.polar.sdk.impl.utils.PolarAutomaticSamplesUtils
import com.polar.sdk.impl.utils.PolarNightlyRechargeUtils
import com.polar.sdk.impl.utils.PolarSkinTemperatureUtils
import com.polar.sdk.api.model.activity.Polar247HrSamplesData
import com.polar.sdk.api.model.activity.Polar247PPiSamplesData
import com.polar.sdk.api.model.activity.PolarActivitySamplesDayData
import com.polar.sdk.api.model.activity.PolarDailySummaryData
import com.polar.sdk.api.model.activity.PolarDistanceData
import com.polar.sdk.api.model.activity.PolarStepsData
import com.polar.sdk.api.model.sleep.PolarNightlyRechargeData
import com.polar.sdk.api.model.sleep.PolarSleepApiServiceEventPayload
import com.polar.sdk.api.model.activity.PolarActiveTimeData
import com.polar.sdk.api.model.activity.PolarCaloriesData
import com.polar.sdk.api.model.sleep.PolarSleepData
import com.polar.sdk.impl.utils.CaloriesType
import com.polar.sdk.api.model.trainingsession.PolarTrainingSession
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionFetchResult
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionProgress
import com.polar.sdk.impl.utils.PolarActivityUtils
import fi.polar.remote.representation.protobuf.UserDeviceSettings
import fi.polar.remote.representation.protobuf.UserDeviceSettings.PbUserDeviceSettings
import fi.polar.remote.representation.protobuf.UserDeviceSettings.PbUserDeviceTelemetrySettings
import com.polar.sdk.impl.utils.PolarTimeUtils
import com.polar.sdk.impl.utils.PolarTimeUtils.javaLocalDateTimeToPbPftpSetLocalTime
import com.polar.sdk.impl.utils.PolarTimeUtils.pbLocalTimeToJavaLocalDateTime
import com.polar.sdk.impl.utils.PolarTimeUtils.pbLocalTimeToZonedDateTime
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import fi.polar.remote.representation.protobuf.Structures
import com.polar.sdk.impl.utils.PolarFileUtils
import com.polar.sdk.impl.utils.PolarFileUtils.pFtpWriteOperation
import com.polar.sdk.impl.utils.PolarServiceClientUtils
import com.polar.sdk.impl.utils.PolarServiceClientUtils.fetchSession
import com.polar.sdk.impl.utils.PolarSleepUtils
import com.polar.sdk.impl.utils.PolarTrainingSessionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The default implementation of the Polar API
 * @Suppress
 */
class BDBleApiImpl private constructor(context: Context, features: Set<PolarBleSdkFeature>) : PolarBleApi(features), BlePowerStateChangedCallback, PolarTrainingSessionApi,
    PolarBleLowLevelApi, PolarOfflineExerciseV2Api, PolarTestApi {

    private val connectSubscriptions: MutableMap<String, Job> = mutableMapOf()
    private val apiScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val readyFeaturesMap = ConcurrentHashMap<String, Set<PolarBleApi.PolarBleSdkFeature>>()
    private val deviceDataMonitorJob: MutableMap<String?, Job> = mutableMapOf()
    private val deviceAvailableFeaturesJob: MutableMap<String?, Job> = mutableMapOf()
    private val stopPmdStreamingJob: MutableMap<String?, Job> = mutableMapOf()
    private val filter =
        BleSearchPreFilter { content: BleAdvertisementContent -> content.polarDeviceId.isNotEmpty() && content.polarDeviceType != "mobile" }
    private var listener: BleDeviceListener? = null
    private var devicesStateMonitorJob: Job? = null
    private var deviceSessionState: DeviceSessionState? = null
    private var callback: PolarBleApiCallbackProvider? = null
    private var logger: PolarBleApiLogger? = null
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ENGLISH)
    private val PMDFilePath = "/PMDFILES.TXT"
    private lateinit var offlineExerciseV2Api: PolarOfflineExerciseV2ApiImpl

    init {
        val clients: MutableSet<Class<out BleGattBase>> = mutableSetOf()
        for (feature in features) {
            when (feature) {
                PolarBleSdkFeature.FEATURE_HR -> clients.add(BleHrClient::class.java)
                PolarBleSdkFeature.FEATURE_DEVICE_INFO -> clients.add(BleDisClient::class.java)
                PolarBleSdkFeature.FEATURE_BATTERY_INFO -> clients.add(BleBattClient::class.java)
                PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING -> {
                    clients.add(BleHrClient::class.java)
                    clients.add(BlePMDClient::class.java)
                }

                PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING -> {
                    clients.add(BlePMDClient::class.java)
                    clients.add(BlePsFtpClient::class.java)
                }

                PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING -> clients.add(
                    BlePsFtpClient::class.java
                )

                PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2 -> {
                    // No specific client required - feature based on file system type only
                }

                PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP -> clients.add(BlePsFtpClient::class.java)
                PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE -> clients.add(BlePMDClient::class.java)
                PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER -> clients.add(BlePsFtpClient::class.java)
                PolarBleSdkFeature.FEATURE_HTS -> clients.add(BleHtsClient::class.java)
                PolarBleSdkFeature.FEATURE_POLAR_LED_ANIMATION -> clients.add(BlePsFtpClient::class.java)
                PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE -> clients.add(BlePsFtpClient::class.java)
                PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA -> clients.add(BlePsFtpClient::class.java)
                PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA -> clients.add(BlePsFtpClient::class.java)
                PolarBleSdkFeature.FEATURE_POLAR_TEMPERATURE_DATA -> clients.add(BlePsFtpClient::class.java)
                PolarBleSdkFeature.FEATURE_POLAR_TRAINING_DATA -> clients.add(BlePsFtpClient::class.java)
                PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL -> clients.add(BlePsFtpClient::class.java)
                PolarBleSdkFeature.FEATURE_POLAR_FEATURES_CONFIGURATION_SERVICE -> clients.add(BlePfcClient::class.java)
                PolarBleSdkFeature.FEATURE_POLAR_SPO2_TEST_DATA -> clients.add(BlePsFtpClient::class.java)
            }
        }

        val bdDeviceListenerImpl = BDDeviceListenerImpl(context, clients)
        bdDeviceListenerImpl.setScanPreFilter(filter)
        bdDeviceListenerImpl.setBlePowerStateCallback(this)
        listener = bdDeviceListenerImpl
        setLoggerInterface(object : BleLogger.BleLoggerInterface {
            override fun d(tag: String, msg: String) {
                log("$tag/$msg")
            }

            override fun e(tag: String, msg: String) {
                logError("$tag/$msg")
            }

            override fun w(tag: String, msg: String) {
                log("$tag/$msg")
            }

            override fun i(tag: String, msg: String) {
                log("$tag/$msg")
            }

            override fun d_hex(tag: String, msg: String, data: ByteArray) {
                log("$tag/$msg hex: ${data.joinToString(" ") { "%02x".format(it) }}")
            }
        })

        try {
            BlePolarDeviceCapabilitiesUtility.initialize(context.applicationContext)
        } catch (e: SecurityException) {
            BleLogger.w(TAG, "Cannot initialize Polar capabilities yet, missing permission $e")
        } catch (e: Exception) {
            BleLogger.e(TAG, "Unexpected error initializing Polar capabilities $e")
        }

        listener?.let {
            offlineExerciseV2Api = PolarOfflineExerciseV2ApiImpl(it)
        }
    }

    override fun setMtu(mtu: Int) {
        try {
            listener?.setPreferredMtu(mtu)
        } catch (e: BleInvalidMtu) {
            BleLogger.e(TAG, "Invalid MTU $mtu value given. Must be zero or positive.")
        }
    }

    override fun shutDown() {
        apiScope.cancel()
        devicesStateMonitorJob = null
        listener?.shutDown()
        logger = null
        callback = null
        listener = null
        clearInstance()
    }

    override fun cleanup() {
        devicesStateMonitorJob?.cancel()
        devicesStateMonitorJob = null
        listener?.removeAllSessions()
    }

    override fun setPolarFilter(enable: Boolean) {
        if (enable) {
            listener?.setScanPreFilter(filter)
        } else {
            listener?.setScanPreFilter(null)
        }
    }

    override fun isFeatureReady(deviceId: String, feature: PolarBleSdkFeature): Boolean {
        return try {
            return when (feature) {
                PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING -> {
                    PolarServiceClientUtils.sessionHrClientReady(deviceId, listener)
                    PolarServiceClientUtils.sessionPmdClientReady(deviceId, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_HR -> {
                    PolarServiceClientUtils.sessionHrClientReady(deviceId, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_DEVICE_INFO -> {
                    PolarServiceClientUtils.sessionServiceReady(deviceId, BleDisClient.DIS_SERVICE, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_BATTERY_INFO -> {
                    PolarServiceClientUtils.sessionServiceReady(deviceId, BleBattClient.BATTERY_SERVICE, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING -> {
                    PolarServiceClientUtils.sessionPmdClientReady(deviceId, listener)
                    PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP -> {
                    PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING -> {
                    val session = PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, listener)
                    FileSystemType.H10_FILE_SYSTEM == getFileSystemType(session.polarDeviceType)
                }

                PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2 -> {
                    try {
                        val session = PolarServiceClientUtils.fetchSession(deviceId, listener)
                        session?.let { checkOfflineExerciseV2Support(it) } ?: false
                    } catch (e: Throwable) {
                        false
                    }
                }

                PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE -> {
                    PolarServiceClientUtils.sessionPmdClientReady(deviceId, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER -> {
                    PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_HTS -> {
                    PolarServiceClientUtils.sessionServiceReady(deviceId, HealthThermometer.HTS_SERVICE, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_LED_ANIMATION -> {
                    PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE -> {
                    PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA -> {
                    val deviceType = getDeviceName(deviceId).let {
                        if (it.startsWith("Polar ")) it.removePrefix("Polar ")
                        else it }.replace( Regex(" [0-9A-Fa-f]{8}$"), "").trim()
                    BlePolarDeviceCapabilitiesUtility.isActivityDataSupported(deviceType)
                }

                PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA -> {
                    PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_TEMPERATURE_DATA -> {
                    PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_TRAINING_DATA -> {
                    PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL -> {
                    PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_FEATURES_CONFIGURATION_SERVICE -> {
                    PolarServiceClientUtils.sessionPsPfcClientReady(deviceId, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_SPO2_TEST_DATA -> {
                    PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, listener)
                    true
                }
            }
        } catch (ignored: Throwable) {
            false
        }
    }

    private fun checkOfflineExerciseV2Support(session: BleDeviceSession): Boolean {
        return try {
            val fsType = getFileSystemType(session.polarDeviceType)
            fsType == FileSystemType.H10_FILE_SYSTEM
        } catch (ignored: Throwable) {
            false
        }
    }

    override fun setApiCallback(callback: PolarBleApiCallbackProvider) {
        this.callback = callback
        listener?.let {
            callback.blePowerStateChanged(it.bleActive())
        }
    }

    override fun setApiLogger(logger: PolarBleApiLogger) {
        this.logger = logger
    }

    override fun setAutomaticReconnection(enable: Boolean) {
        listener?.setAutomaticReconnection(enable)
    }

    private fun getAutomaticReconnection() : Boolean? {
        return listener?.getAutomaticReconnection();
    }

    override suspend fun setLocalTime(identifier: String, localTime: LocalDateTime) {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()

        BleLogger.d(TAG, "set local time to $localTime device $identifier")
        val pbLocalTime = javaLocalDateTimeToPbPftpSetLocalTime(localTime)
        try {
            setSystemTime(client, localTime)
        } catch (ignored: Throwable) {
            // ignore system time error, proceed with local time
        }
        client.query(
            PftpRequest.PbPFtpQuery.SET_LOCAL_TIME_VALUE,
            pbLocalTime.toByteArray()
        )
    }

    private suspend fun setSystemTime(client: BlePsFtpClient, localDataTime: LocalDateTime) {
        val pbTime = javaLocalDateTimeToPbPftpSetLocalTime(localDataTime)
        client.query(PftpRequest.PbPFtpQuery.SET_SYSTEM_TIME_VALUE, pbTime.toByteArray())
    }

    @Deprecated(
        "Use getLocalTimeWithZone() instead to also get timezone",
        replaceWith = ReplaceWith("getLocalTimeWithZone(identifier)")
    )
    override suspend fun getLocalTime(identifier: String): LocalDateTime {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()

        BleLogger.d(TAG, "get local time from device $identifier")
        return try {
            val result = client.query(PftpRequest.PbPFtpQuery.GET_LOCAL_TIME_VALUE, null)
            val dateTime = PftpRequest.PbPFtpSetLocalTimeParams.parseFrom(result.toByteArray())
            pbLocalTimeToJavaLocalDateTime(dateTime)
        } catch (throwable: Throwable) {
            if (throwable is PftpResponseError && throwable.errorCode == PbPFtpError.NOT_IMPLEMENTED) {
                throw BleNotSupported("${session.name} does not support getTime")
            } else {
                throw throwable
            }
        }
    }

    override suspend fun getLocalTimeWithZone(identifier: String): ZonedDateTime {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()

        BleLogger.d(TAG, "get local time with zone from device $identifier")
        return try {
            val result = client.query(PftpRequest.PbPFtpQuery.GET_LOCAL_TIME_VALUE, null)
            val dateTime = PftpRequest.PbPFtpSetLocalTimeParams.parseFrom(result.toByteArray())
            pbLocalTimeToZonedDateTime(dateTime)
        } catch (throwable: Throwable) {
            if (throwable is PftpResponseError && throwable.errorCode == PbPFtpError.NOT_IMPLEMENTED) {
                throw BleNotSupported("${session.name} does not support getTime")
            } else {
                throw throwable
            }
        }
    }

    override suspend fun requestStreamSettings(
        identifier: String,
        feature: PolarDeviceDataType
    ): PolarSensorSetting {
        BleLogger.d(TAG, "Request online stream settings. Feature: $feature Device: $identifier")
        return when (feature) {
            PolarDeviceDataType.ECG -> querySettings(identifier, PmdMeasurementType.ECG, PmdRecordingType.ONLINE)
            PolarDeviceDataType.ACC -> querySettings(identifier, PmdMeasurementType.ACC, PmdRecordingType.ONLINE)
            PolarDeviceDataType.PPG -> querySettings(identifier, PmdMeasurementType.PPG, PmdRecordingType.ONLINE)
            PolarDeviceDataType.GYRO -> querySettings(identifier, PmdMeasurementType.GYRO, PmdRecordingType.ONLINE)
            PolarDeviceDataType.MAGNETOMETER -> querySettings(identifier, PmdMeasurementType.MAGNETOMETER, PmdRecordingType.ONLINE)
            PolarDeviceDataType.PRESSURE -> querySettings(identifier, PmdMeasurementType.PRESSURE, PmdRecordingType.ONLINE)
            PolarDeviceDataType.LOCATION -> querySettings(identifier, PmdMeasurementType.LOCATION, PmdRecordingType.ONLINE)
            PolarDeviceDataType.TEMPERATURE -> querySettings(identifier, PmdMeasurementType.TEMPERATURE, PmdRecordingType.ONLINE)
            PolarDeviceDataType.SKIN_TEMPERATURE -> querySettings(identifier, PmdMeasurementType.SKIN_TEMP, PmdRecordingType.ONLINE)
            PolarDeviceDataType.HR,
            PolarDeviceDataType.PPI -> throw PolarOperationNotSupported()
        }
    }

    override suspend fun requestFullStreamSettings(
        identifier: String,
        feature: PolarDeviceDataType
    ): PolarSensorSetting {
        BleLogger.d(TAG, "Request full online stream settings. Feature: $feature Device: $identifier")
        return when (feature) {
            PolarDeviceDataType.ECG -> queryFullSettings(identifier, PmdMeasurementType.ECG, PmdRecordingType.ONLINE)
            PolarDeviceDataType.ACC -> queryFullSettings(identifier, PmdMeasurementType.ACC, PmdRecordingType.ONLINE)
            PolarDeviceDataType.PPG -> queryFullSettings(identifier, PmdMeasurementType.PPG, PmdRecordingType.ONLINE)
            PolarDeviceDataType.GYRO -> queryFullSettings(identifier, PmdMeasurementType.GYRO, PmdRecordingType.ONLINE)
            PolarDeviceDataType.MAGNETOMETER -> queryFullSettings(identifier, PmdMeasurementType.MAGNETOMETER, PmdRecordingType.ONLINE)
            PolarDeviceDataType.PPI,
            PolarDeviceDataType.HR,
            PolarDeviceDataType.PRESSURE,
            PolarDeviceDataType.LOCATION,
            PolarDeviceDataType.TEMPERATURE,
            PolarDeviceDataType.SKIN_TEMPERATURE -> throw PolarOperationNotSupported()
        }
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
            PolarDeviceDataType.PPI -> throw PolarOperationNotSupported()
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
            PolarDeviceDataType.SKIN_TEMPERATURE -> throw PolarOperationNotSupported()
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

    override fun foregroundEntered() {
        listener?.scanRestart()
    }

    override fun checkIfDeviceDisconnectedDueRemovedPairing(identifier: String): Pair<Boolean, Int> {
        // If listener is null, we cannot know if the device is disconnected due to removed pairing or not,
        // return false with unknown device state (-1).
        return listener?.getIndicatesPairingProblem(identifier)
            ?: Pair(false, -1)
    }

    override suspend fun autoConnectToDevice(
        rssiLimit: Int,
        service: String?,
        timeout: Int,
        unit: TimeUnit,
        polarDeviceType: String?
    ) {
        if (service != null && !service.matches(Regex("([0-9a-fA-F]{4})"))) {
            throw PolarInvalidArgument("Invalid service string format")
        }
        val it = listener ?: throw PolarBleSdkInstanceException("PolarBleApi instance is shutdown")
        var start = 0L
        val timeoutMillis = unit.toMillis(timeout.toLong())
        val collected = mutableSetOf<BleDeviceSession>()
        it.search(false)
            .filter { bleDeviceSession: BleDeviceSession ->
                if (bleDeviceSession.medianRssi >= rssiLimit && bleDeviceSession.isConnectableAdvertisement
                    && (polarDeviceType == null || polarDeviceType == bleDeviceSession.polarDeviceType)
                    && (service == null || bleDeviceSession.advertisementContent.containsService(service))
                ) {
                    if (start == 0L) {
                        start = System.currentTimeMillis()
                    }
                    true
                } else {
                    false
                }
            }
            .takeWhile {
                if (start == 0L) return@takeWhile true
                val diff = System.currentTimeMillis() - start
                diff < timeoutMillis
            }
            .collect { session -> collected.add(session) }
        val list = collected.sortedWith { s1, s2 -> if (s1.rssi > s2.rssi) -1 else 1 }
        openConnection(list[0])
        log("auto connect search complete")
    }

    override suspend fun autoConnectToDevice(
        rssiLimit: Int,
        service: String?,
        polarDeviceType: String?
    ) {
        autoConnectToDevice(rssiLimit, service, 2, TimeUnit.SECONDS, polarDeviceType)
    }

    override fun getDeviceName(deviceId: String): String {
        return fetchSession(identifier = deviceId, listener)?.name ?: ""
    }

    @Throws(PolarInvalidArgument::class)
    override fun connectToDevice(identifier: String) {
        val session = fetchSession(identifier, listener)
        if (session == null || session.sessionState == DeviceSessionState.SESSION_CLOSED) {
            if (connectSubscriptions.containsKey(identifier)) {
                connectSubscriptions[identifier]?.cancel()
                connectSubscriptions.remove(identifier)
            }
            if (session != null) {
                openConnection(session)
            } else {
                    listener?.let {
                        connectSubscriptions[identifier]?.cancel()
                        connectSubscriptions[identifier] = apiScope.launch {
                            try {
                                it.search(false)
                                    .filter { bleDeviceSession: BleDeviceSession ->
                                        if (identifier.contains(":")) bleDeviceSession.address == identifier
                                        else bleDeviceSession.polarDeviceId == identifier
                                    }
                                    .take(1)
                                    .collect { session: BleDeviceSession -> openConnection(session) }
                                log("connect search completed for $identifier")
                            } catch (error: Throwable) {
                                logError("connect search error with device: $identifier error: ${error.message}")
                            }
                        }
                    }
                }
        }
    }

    @Throws(PolarInvalidArgument::class)
    override fun disconnectFromDevice(identifier: String) {
        val session = fetchSession(identifier, listener)
        session?.let {
            if (session.sessionState == DeviceSessionState.SESSION_OPEN ||
                session.sessionState == DeviceSessionState.SESSION_OPENING ||
                session.sessionState == DeviceSessionState.SESSION_OPEN_PARK
            ) {
                listener?.closeSessionDirect(session)
            }
        }
        if (connectSubscriptions.containsKey(identifier)) {
            connectSubscriptions[identifier]?.cancel()
            connectSubscriptions.remove(identifier)
        }
    }

    override suspend fun startRecording(
        identifier: String,
        exerciseId: String,
        interval: PolarH10OfflineExerciseApi.RecordingInterval?,
        type: PolarH10OfflineExerciseApi.SampleType
    ) {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        if (!isRecordingSupported(session.polarDeviceType)) throw PolarOperationNotSupported()
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val pbSampleType =
            if (type == PolarH10OfflineExerciseApi.SampleType.HR) PbSampleType.SAMPLE_TYPE_HEART_RATE else PbSampleType.SAMPLE_TYPE_RR_INTERVAL
        val recordingInterval =
            interval?.value ?: PolarH10OfflineExerciseApi.RecordingInterval.INTERVAL_1S.value
        val duration = PbDuration.newBuilder().setSeconds(recordingInterval).build()
        val params = PftpRequest.PbPFtpRequestStartRecordingParams.newBuilder()
            .setSampleDataIdentifier(exerciseId)
            .setSampleType(pbSampleType)
            .setRecordingInterval(duration)
            .build()
        try {
            client.query(
                PftpRequest.PbPFtpQuery.REQUEST_START_RECORDING_VALUE,
                params.toByteArray()
            )
        } catch (throwable: Throwable) {
            throw handleError(throwable)
        }
    }

    override suspend fun stopRecording(identifier: String) {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        if (!isRecordingSupported(session.polarDeviceType)) throw PolarOperationNotSupported()
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        try {
            client.query(PftpRequest.PbPFtpQuery.REQUEST_STOP_RECORDING_VALUE, null)
        } catch (throwable: Throwable) {
            throw handleError(throwable)
        }
    }

    override suspend fun requestRecordingStatus(identifier: String): androidx.core.util.Pair<Boolean, String> {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        if (!isRecordingSupported(session.polarDeviceType)) throw PolarOperationNotSupported()
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        return try {
            val byteArrayOutputStream = client.query(PftpRequest.PbPFtpQuery.REQUEST_RECORDING_STATUS_VALUE, null)
            val result = PbRequestRecordingStatusResult.parseFrom(byteArrayOutputStream.toByteArray())
            androidx.core.util.Pair(
                result.recordingOn,
                if (result.hasSampleDataIdentifier()) result.sampleDataIdentifier else ""
            )
        } catch (throwable: Throwable) {
            throw handleError(throwable)
        }
    }

    override fun listOfflineRecordings(identifier: String): Flow<PolarOfflineRecordingEntry> {
        return kotlinx.coroutines.flow.flow {
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

    private suspend fun deviceSupportsFasterOfflineRecordListing(identifier: String): ByteArray {
        return try {
            getFile(identifier, PMDFilePath)
        } catch (e: Exception) {
            BleLogger.e(TAG, "Failed to check if device supports fast offline record listing: $e")
            byteArrayOf()
        }
    }

    private fun mapOfflineRecordingTypeToPolarDeviceDataType(offlineRecordingDataType: String) : PolarDeviceDataType {
        return when (offlineRecordingDataType) {
            "SKINTEMP" -> PolarDeviceDataType.SKIN_TEMPERATURE
            else -> PolarDeviceDataType.valueOf(offlineRecordingDataType)
        }
    }

    override fun listExercises(identifier: String): Flow<PolarExerciseEntry> {
        val session = try {
            PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        } catch (error: Throwable) {
            return kotlinx.coroutines.flow.flow { throw error }
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: return kotlinx.coroutines.flow.flow { throw PolarServiceNotAvailable() }

        return when (getFileSystemType(session.polarDeviceType)) {
            FileSystemType.POLAR_FILE_SYSTEM_V2 -> {
                PolarFileUtils.fetchRecursively(client = client,
                    path = "/U/0/",
                    condition = { entry ->
                        entry.matches(Regex("^([0-9]{8})(/)")) ||
                                entry.matches(Regex("^([0-9]{6})(/)")) ||
                                entry == "E/" ||
                                entry == "SAMPLES.BPB" ||
                                entry == "00/"
                    },
                    tag = TAG,
                    recurseDeep = true)
                    .map { entry: Pair<String, Long> ->
                        val components = entry.first.split("/").toTypedArray()
                        val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.getDefault())
                        val date = LocalDateTime.parse(components[3] + " " + components[5], dateTimeFormatter)
                        PolarExerciseEntry(entry.first, date, components[3] + components[5])
                    }
                    .catch { throwable -> throw handleError(throwable) }
            }

            FileSystemType.H10_FILE_SYSTEM -> {
                PolarFileUtils.fetchRecursively(client = client,
                    path = "/",
                    condition = { entry -> entry.endsWith("/") || entry == "SAMPLES.BPB" },
                    tag = TAG,
                    recurseDeep = true)
                    .map { entry: Pair<String, Long> ->
                        val components = entry.first.split("/").toTypedArray()
                        PolarExerciseEntry(entry.first, LocalDateTime.now(), components[1])
                    }
                    .catch { throwable -> throw handleError(throwable) }
            }

            else -> kotlinx.coroutines.flow.flow { throw PolarOperationNotSupported() }
        }
    }

    override suspend fun fetchExercise(
        identifier: String,
        entry: PolarExerciseEntry
    ): PolarExerciseData {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()

        val builder = PftpRequest.PbPFtpOperation.newBuilder()
        builder.command = PftpRequest.PbPFtpOperation.Command.GET
        builder.path = entry.path

        return try {
            val byteArrayOutputStream = client.request(builder.build().toByteArray())
            val samples = PbExerciseSamples.parseFrom(byteArrayOutputStream.toByteArray())
            if (samples.hasRrSamples()) {
                PolarExerciseData(
                    samples.recordingInterval.seconds,
                    samples.rrSamples.rrIntervalsList
                )
            } else {
                PolarExerciseData(
                    samples.recordingInterval.seconds,
                    samples.heartRateSamplesList
                )
            }
        } catch (throwable: Throwable) {
            throw handleError(throwable)
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

        send(
            PolarOfflineRecordingResult.Progress(
                bytesDownloaded = 0L,
                totalBytes = totalBytes,
                progressPercent = 0
            )
        )

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
            trySend(
                PolarOfflineRecordingResult.Progress(
                    bytesDownloaded = currentBytes,
                    totalBytes = totalBytes,
                    progressPercent = percent
                )
            )
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

    private class OfflineRecordingAccumulator {
        var accData: PolarOfflineRecordingData.AccOfflineRecording? = null
        var gyroData: PolarOfflineRecordingData.GyroOfflineRecording? = null
        var magData: PolarOfflineRecordingData.MagOfflineRecording? = null
        var ppgData: PolarOfflineRecordingData.PpgOfflineRecording? = null
        var ppiData: PolarOfflineRecordingData.PpiOfflineRecording? = null
        var hrData: PolarOfflineRecordingData.HrOfflineRecording? = null
        var temperatureData: PolarOfflineRecordingData.TemperatureOfflineRecording? = null
        var skinTemperatureData: PolarOfflineRecordingData.SkinTemperatureOfflineRecording? = null

        fun getResult(): PolarOfflineRecordingData? =
            ppiData ?: ppgData ?: accData ?: gyroData ?: magData ?: hrData ?: temperatureData ?: skinTemperatureData
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
        lastTimestamp: ULong = 0uL
    ): OfflineRecordingData<*> {
        val pmdSecret = secret?.let { mapPolarSecretToPmdSecret(it) }
        return OfflineRecordingData.parseDataFromOfflineFile(
            byteArrayOutputStream.toByteArray(),
            mapPolarFeatureToPmdClientMeasurementType(entry.type),
            pmdSecret,
            lastTimestamp
        )
    }

    private fun processOfflineData(
        offlineRecData: OfflineRecordingData<*>,
        accumulator: OfflineRecordingAccumulator
    ): PolarOfflineRecordingData {
        return when (val offlineData = offlineRecData.data) {
            is AccData -> processAccData(offlineData, offlineRecData, accumulator.accData).also { accumulator.accData = it }
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
        BleLogger.d(TAG, "Offline record get. Device: $identifier Path: ${entry.path} Secret used: ${secret != null}")
        return try {
            val byteArrayOutputStream = client.request(buildPftpGetRequest(entry.path))
            val offlineRecData = parseOfflineRecordingData(byteArrayOutputStream, entry, secret)
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
            BleLogger.d(TAG, "Offline record get. Device: $identifier Path: $subRecordingPath Secret used: ${secret != null}, lastTimestamp: $lastTimestamp")
            val byteArrayOutputStream = client.request(buildPftpGetRequest(subRecordingPath))
            val offlineRecordingData = parseOfflineRecordingData(byteArrayOutputStream, entry, secret, lastTimestamp)
            processOfflineData(offlineRecordingData, accumulator)
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
            ?: PolarOfflineRecordingData.AccOfflineRecording(
                polarAcc,
                offlineRecordingData.startTime,
                polarSettings
            )
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
            ?: PolarOfflineRecordingData.GyroOfflineRecording(
                polarGyr,
                offlineRecordingData.startTime,
                polarSettings
            )
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
            ?: PolarOfflineRecordingData.MagOfflineRecording(
                polarMag,
                offlineRecordingData.startTime,
                polarSettings
            )
    }

    private fun processPpgData(
        offlineData: PpgData,
        offlineRecordingData: OfflineRecordingData<*>,
        existingData: PolarOfflineRecordingData.PpgOfflineRecording?
    ): PolarOfflineRecordingData.PpgOfflineRecording {
        val polarSettings = offlineRecordingData.recordingSettings?.let {
            mapPmdSettingsToPolarSettings(it, fromSelected = false)
        } ?: throw PolarOfflineRecordingError("getOfflineRecord failed.  Ppg data is missing settings")

        val polarPpg = mapPMDClientPpgDataToPolarPpg(offlineData)
        return existingData?.appendPpgData(existingData, polarPpg)
            ?: PolarOfflineRecordingData.PpgOfflineRecording(
                polarPpg,
                offlineRecordingData.startTime,
                polarSettings
            )
    }

    private fun processPpiData(
        offlineData: PpiData,
        offlineRecordingData: OfflineRecordingData<*>,
        existingData: PolarOfflineRecordingData.PpiOfflineRecording?
    ): PolarOfflineRecordingData.PpiOfflineRecording {
        return existingData?.appendPpiData(
            existingData,
            PolarOfflineRecordingData.PpiOfflineRecording(
                mapPMDClientPpiDataToPolarPpiData(offlineData),
                offlineRecordingData.startTime
            ).data
        )
            ?: PolarOfflineRecordingData.PpiOfflineRecording(
                mapPMDClientPpiDataToPolarPpiData(offlineData),
                offlineRecordingData.startTime
            )
    }

    private fun processHrData(
        offlineData: OfflineHrData,
        offlineRecordingData: OfflineRecordingData<*>,
        existingData: PolarOfflineRecordingData.HrOfflineRecording?
    ): PolarOfflineRecordingData.HrOfflineRecording {
        return existingData?.appendHrData(
            existingData,
            mapPMDClientOfflineHrDataToPolarHrData(offlineData)
        ) ?: PolarOfflineRecordingData.HrOfflineRecording(
            mapPMDClientOfflineHrDataToPolarHrData(offlineData),
            offlineRecordingData.startTime
        )
    }

    private fun processTemperatureData(
        offlineData: TemperatureData,
        offlineRecordingData: OfflineRecordingData<*>,
        existingData: PolarOfflineRecordingData.TemperatureOfflineRecording?
    ): PolarOfflineRecordingData.TemperatureOfflineRecording {
        return existingData?.appendTemperatureData(
            existingData,
            mapPMDClientOfflineTemperatureDataToPolarTemperatureData(offlineData)
        ) ?: PolarOfflineRecordingData.TemperatureOfflineRecording(
            mapPMDClientOfflineTemperatureDataToPolarTemperatureData(offlineData),
            offlineRecordingData.startTime
        )
    }

    private fun processSkinTemperatureData(
        offlineData: SkinTemperatureData,
        offlineRecordingData: OfflineRecordingData<*>,
        existingData: PolarOfflineRecordingData.SkinTemperatureOfflineRecording?
    ): PolarOfflineRecordingData.SkinTemperatureOfflineRecording {
        return existingData?.appendSkinTemperatureData(
            existingData,
            mapPmdClientSkinTemperatureDataToPolarTemperatureData(offlineData)
        ) ?: PolarOfflineRecordingData.SkinTemperatureOfflineRecording(
            mapPmdClientSkinTemperatureDataToPolarTemperatureData(offlineData),
            offlineRecordingData.startTime
        )
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
                    BleLogger.w(TAG, "Directory not found for path ${entry.path}, returning empty counts")
                    Pair(0, 0)
                } else {
                    throw throwable
                }
            } else {
                throw throwable
            }
        }
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
                        ?: throw PolarInvalidArgument("Listing offline recording failed. Cannot parse create data from date ${components[3]} and time ${components[5]}")
                    val type = mapPmdClientFeatureToPolarFeature(
                        mapOfflineRecordingFileNameToMeasurementType(components[6])
                    )
                    PolarOfflineRecordingEntry(
                        path = entry.first,
                        size = entry.second,
                        date = date,
                        type = type
                    )
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

        BleLogger.d(TAG, "Split offline record get. Device: $identifier Path: ${entry.path} Secret used: ${secret != null}")
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

    override suspend fun removeExercise(identifier: String, entry: PolarExerciseEntry) {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()

        when (getFileSystemType(session.polarDeviceType)) {
            FileSystemType.POLAR_FILE_SYSTEM_V2 -> throw PolarBleSdkInternalException("Other than H10 sensor is not supported by removeExercise API method. For other than H10 sensor use API deleteTrainingSession API method instead.")
            FileSystemType.H10_FILE_SYSTEM -> {
                val builder = PftpRequest.PbPFtpOperation.newBuilder()
                builder.command = PftpRequest.PbPFtpOperation.Command.REMOVE
                builder.path = entry.path
                try {
                    client.request(builder.build().toByteArray())
                } catch (throwable: Throwable) {
                    throw handleError(throwable)
                }
            }
            FileSystemType.UNKNOWN_FILE_SYSTEM -> throw PolarOperationNotSupported()
        }
    }

    override suspend fun deleteTrainingSession(identifier: String, reference: PolarTrainingSessionReference) {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        PolarTrainingSessionUtils.deleteTrainingSession(client, reference)
    }

    override suspend fun removeOfflineRecord(
        identifier: String,
        entry: PolarOfflineRecordingEntry
    ) {
        BleLogger.d(TAG, "Remove offline record from device $identifier path ${entry.path}")
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

    private suspend fun checkIfDirectoryIsEmpty(directoryPath: String, client: BlePsFtpClient): Boolean {
        var path = directoryPath
        if (!path.endsWith("/")) {
            path = path.plus("/")
        }
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
                // The file or directory was not found.
                if (errorId == 103) false else throw throwable
            } else {
                throw throwable
            }
        }
    }

    override fun searchForDevice(): Flow<PolarDeviceInfo>  {
        return searchForDevice(withDeviceNameFilterPrefix = null)
    }

    override fun searchForDevice(withDeviceNameFilterPrefix: String?): Flow<PolarDeviceInfo> {
        val l = listener ?: return flow { throw PolarBleSdkInstanceException("PolarBleApi instance is shutdown") }
        return l.search(false)
            .filter { bleDeviceSession: BleDeviceSession ->
                val name = bleDeviceSession.advertisementContent.name
                return@filter withDeviceNameFilterPrefix == null || name.startsWith(
                    withDeviceNameFilterPrefix
                )
            }
            .map { bleDeviceSession: BleDeviceSession ->
                val hasSAGRFCFileSystem = getFileSystemType(bleDeviceSession.polarDeviceType) == FileSystemType.POLAR_FILE_SYSTEM_V2
                PolarDeviceInfo(
                    deviceId = bleDeviceSession.polarDeviceId,
                    address = bleDeviceSession.address!!,
                    rssi = bleDeviceSession.rssi,
                    name = bleDeviceSession.name,
                    isConnectable = bleDeviceSession.isConnectableAdvertisement,
                    hasHeartRateService = bleDeviceSession.advertisementContent.containsService(HR_SERVICE_16BIT_UUID),
                    hasFileSystemService = bleDeviceSession.advertisementContent.containsService(PFTP_SERVICE_16BIT_UUID),
                    hasSAGRFCFileSystem = hasSAGRFCFileSystem
                )
            }
    }

    override fun startListenForPolarHrBroadcasts(deviceIds: Set<String>?): Flow<PolarHrBroadcastData> {
        val l = listener ?: return flow { throw PolarBleSdkInstanceException("PolarBleApi instance is shutdown") }
        BleLogger.d(TAG, "Start Hr broadcast listener. Filtering: ${deviceIds != null}")
        return l.search(false)
            .filter { bleDeviceSession: BleDeviceSession ->
                (deviceIds == null || deviceIds.contains(bleDeviceSession.polarDeviceId)) &&
                        bleDeviceSession.advertisementContent.polarHrAdvertisement.isPresent &&
                        bleDeviceSession.advertisementContent.polarHrAdvertisement.isHrDataUpdated
            }
            .map { bleDeviceSession: BleDeviceSession ->
                val advertisement = bleDeviceSession.blePolarHrAdvertisement
                PolarHrBroadcastData(
                    PolarDeviceInfo(
                        bleDeviceSession.polarDeviceId,
                        bleDeviceSession.address!!,
                        bleDeviceSession.rssi,
                        bleDeviceSession.name,
                        bleDeviceSession.isConnectableAdvertisement
                    ),
                    advertisement.hrForDisplay,
                    advertisement.batteryStatus != 0
                )
            }
    }

    override suspend fun getDiskSpace(identifier: String): PolarDiskSpaceData {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        return try {
            val result = client.query(PftpRequest.PbPFtpQuery.GET_DISK_SPACE_VALUE, null)
            val proto = PftpResponse.PbPFtpDiskSpaceResult.parseFrom(result.toByteArray())
            PolarDiskSpaceData.fromProto(proto)
        } catch (it: Throwable) {
            if (it is PftpResponseError && it.errorCode == PbPFtpError.NOT_IMPLEMENTED) {
                throw BleNotSupported("${session.name} do not support getDiskSpace")
            } else {
                throw it
            }
        }
    }

    override suspend fun setLedConfig(identifier: String, ledConfig: LedConfig) {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val builder = PftpRequest.PbPFtpOperation.newBuilder()
        builder.command = PftpRequest.PbPFtpOperation.Command.PUT
        builder.path = LedConfig.LED_CONFIG_FILENAME
        val sdkModeLedByte = if (ledConfig.sdkModeLedEnabled) LedConfig.LED_ANIMATION_ENABLE_BYTE else LedConfig.LED_ANIMATION_DISABLE_BYTE
        val ppiModeLedByte = if (ledConfig.ppiModeLedEnabled) LedConfig.LED_ANIMATION_ENABLE_BYTE else LedConfig.LED_ANIMATION_DISABLE_BYTE
        val data = ByteArrayInputStream(byteArrayOf(sdkModeLedByte, ppiModeLedByte))
        client.write(builder.build().toByteArray(), data).collect {}
    }

    override suspend fun listRestApiServices(identifier: String): PolarDeviceRestApiServices {
        val byteArray = getFile(identifier = identifier, path = "/REST/SERVICE.API")
        val map: Map<String, Any> = Gson().fromJson(byteArray.toString(Charsets.UTF_8), object: TypeToken<Map<String,Any>>() {}.type)
        return PolarDeviceRestApiServices(map)
    }

    override suspend fun getRestApiDescription(identifier: String, path: String): PolarDeviceRestApiServiceDescription {
        val map = getJSONMapFromPath(identifier = identifier, path = path)
        return PolarDeviceRestApiServiceDescription(map)
    }

    private suspend fun getJSONMapFromPath(identifier: String, path: String): Map<String,Any> {
        return getJSONDecodableFromPath(
            identifier = identifier,
            path = path,
            mapper = { jsonString ->
                Gson().fromJson(jsonString, object: TypeToken<Map<String,Any>>() {}.type)
            }
        )
    }

    private suspend fun <T:Any> getJSONDecodableFromPath(identifier: String, path: String, mapper:((jsonString: String) -> T)): T {
        val byteArray = getFile(identifier = identifier, path = path)
        return mapper(byteArray.toString(Charsets.UTF_8))
    }

    override suspend fun getFile(identifier: String, path: String): ByteArray {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        return when (getFileSystemType(session.polarDeviceType)) {
            FileSystemType.POLAR_FILE_SYSTEM_V2 -> {
                val builder = PftpRequest.PbPFtpOperation.newBuilder()
                builder.command = PftpRequest.PbPFtpOperation.Command.GET
                builder.path = path
                try {
                    client.request(builder.build().toByteArray()).toByteArray()
                } catch (throwable: Throwable) {
                    throw handleError(throwable)
                }
            }
            else -> throw PolarOperationNotSupported()
        }
    }

    override fun <T : RestApiEventPayload>receiveRestApiEvents(identifier: String, mapper:((jsonString: String) -> T)): Flow<List<T>> {
        return flow {
            val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
            val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                ?: throw PolarServiceNotAvailable()
            client.receiveRestApiEvents(identifier = identifier)
                .collect { list -> emit(list.map(mapper)) }
        }
    }

    override fun observeDeviceToHostNotifications(identifier: String): Flow<PolarD2HNotificationData> {
        return flow {
            val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
            val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                ?: throw PolarServiceNotAvailable()
            client.observeDeviceToHostNotifications(identifier = identifier)
                .collect { emit(it) }
        }
    }

    override suspend fun putNotification(identifier: String, notification: String, path: String) {
        pFtpPutOperation(identifier = identifier, path = path, data = notification.toByteArray())
    }

    private suspend fun pFtpPutOperation(identifier: String, path: String, data: ByteArray) {
        pFtpWriteOperation(identifier = identifier, listener, data = data, path = path, tag = TAG)
    }

    @Deprecated("Use method doFactoryReset(identifier: String) instead.")
    override suspend fun doFactoryReset(identifier: String, preservePairingInformation: Boolean) {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val params = PftpNotification.PbPFtpFactoryResetParams.newBuilder()
        params.sleep = false
        params.otaFwupdate = preservePairingInformation
        BleLogger.d(TAG, "send factory reset notification to device $identifier")
        client.sendNotification(PftpNotification.PbPFtpHostToDevNotification.RESET.ordinal, params.build().toByteArray())
    }

    override suspend fun doFactoryReset(identifier: String) {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val params = PftpNotification.PbPFtpFactoryResetParams.newBuilder()
        params.sleep = false
        BleLogger.d(TAG, "send factory reset notification to device $identifier")
        client.sendNotification(PftpNotification.PbPFtpHostToDevNotification.RESET.ordinal, params.build().toByteArray())
    }

    override suspend fun doRestart(identifier: String) {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val params = PftpNotification.PbPFtpFactoryResetParams.newBuilder()
        params.sleep = false
        params.doFactoryDefaults = false
        BleLogger.d(TAG, "send restart notification to device $identifier")
        client.sendNotification(PftpNotification.PbPFtpHostToDevNotification.RESET.ordinal, params.build().toByteArray())
    }

    override suspend fun doFirstTimeUse(identifier: String, ftuConfig: PolarFirstTimeUseConfig) {
        BleLogger.d(TAG, "doFirstTimeUse(identifier: $identifier): started")
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()

        val ftuData = ByteArrayOutputStream().use { baos ->
            ftuConfig.toProto().writeTo(baos)
            baos.toByteArray()
        }
        val ftuBuilder = PftpRequest.PbPFtpOperation.newBuilder().apply {
            command = PftpRequest.PbPFtpOperation.Command.PUT
            path = PolarFirstTimeUseConfig.FTU_CONFIG_FILENAME
        }
        val userIdentifier = UserIdentifierType.create().toProto()
        val userIdData = ByteArrayOutputStream().use { baos ->
            userIdentifier.writeTo(baos)
            baos.toByteArray()
        }
        val userIdBuilder = PftpRequest.PbPFtpOperation.newBuilder().apply {
            command = PftpRequest.PbPFtpOperation.Command.PUT
            path = UserIdentifierType.USER_IDENTIFIER_FILENAME
        }
        val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH)
        val localTime = LocalDateTime.parse(ftuConfig.deviceTime, dateTimeFormatter)

        try {
            sendInitializationAndStartSyncNotifications(identifier)
            BleLogger.d(TAG, "doFirstTimeUse(identifier: $identifier): set local time")
            setLocalTime(identifier, localTime)
            client.write(ftuBuilder.build().toByteArray(), ByteArrayInputStream(ftuData)).collect {}
            client.write(userIdBuilder.build().toByteArray(), ByteArrayInputStream(userIdData)).collect {}
            BleLogger.d(TAG, "doFirstTimeUse(identifier: $identifier): completed")
            sendTerminateAndStopSyncNotifications(identifier)
        } catch (error: Throwable) {
            BleLogger.e(TAG, "doFirstTimeUse(identifier: $identifier): error $error")
            sendTerminateAndStopSyncNotifications(identifier)
            throw error
        }
    }

    override suspend fun isFtuDone(identifier: String): Boolean {
        val byteArray = getFile(identifier, UserIdentifierType.USER_IDENTIFIER_FILENAME)
        return try {
            UserIds.PbUserIdentifier.parseFrom(byteArray).hasMasterIdentifier()
        } catch (e: Exception) {
            BleLogger.e(TAG, "Failed to check if the first time use has been done: $e")
            throw e
        }
    }

    override suspend fun getUserPhysicalConfiguration(identifier: String): PolarPhysicalConfiguration? {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        return try {
            val response = client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(PolarFirstTimeUseConfig.FTU_CONFIG_FILENAME)
                    .build()
                    .toByteArray()
            )
            val pbUserPhysData = PhysData.PbUserPhysData.parseFrom(response.toByteArray())
            pbUserPhysData.toPolarPhysicalConfiguration()
        } catch (throwable: Throwable) {
            val error = (throwable as? PftpResponseError)?.error
            if (error == PbPFtpError.NO_SUCH_FILE_OR_DIRECTORY.number) {
                BleLogger.d(TAG, "Phys data file does not exist on device $identifier")
                null
            } else {
                BleLogger.e(TAG, "Unexpected error reading phys data file for device $identifier: ${throwable.message}")
                throw throwable
            }
        }
    }

    override suspend fun startExercise(identifier: String, profile: PolarExerciseSession.SportProfile) {
        BleLogger.d(TAG, "Start exercise pressed for $identifier with profile=$profile")
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = (session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as? BlePsFtpClient)
            ?: throw PolarServiceNotAvailable()
        try {
            val params = PftpRequest.PbPFtpStartExerciseParams.newBuilder()
                .setSportIdentifier(Structures.PbSportIdentifier.newBuilder().setValue(profile.id.toLong()).build())
                .build()
            client.query(PftpRequest.PbPFtpQuery.START_EXERCISE_VALUE, params.toByteArray())
            BleLogger.d(TAG, "Start exercise succeeded for $identifier")
        } catch (t: Throwable) {
            BleLogger.e(TAG, "Start exercise failed for $identifier: ${t.message ?: "unknown error"}")
            throw handleError(t)
        }
    }

    override suspend fun pauseExercise(identifier: String) {
        BleLogger.d(TAG, "Pause exercise pressed for $identifier")
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = (session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as? BlePsFtpClient)
            ?: throw PolarServiceNotAvailable()
        try {
            client.query(PftpRequest.PbPFtpQuery.PAUSE_EXERCISE_VALUE, byteArrayOf())
            BleLogger.d(TAG, "Pause exercise succeeded for $identifier")
        } catch (t: Throwable) {
            BleLogger.e(TAG, "Pause exercise failed for $identifier: ${t.message ?: "unknown error"}")
            throw handleError(t)
        }
    }

    override suspend fun resumeExercise(identifier: String) {
        BleLogger.d(TAG, "Resume exercise pressed for $identifier")
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = (session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as? BlePsFtpClient)
            ?: throw PolarServiceNotAvailable()
        try {
            client.query(PftpRequest.PbPFtpQuery.RESUME_EXERCISE_VALUE, byteArrayOf())
            BleLogger.d(TAG, "Resume exercise succeeded for $identifier")
        } catch (t: Throwable) {
            BleLogger.e(TAG, "Resume exercise failed for $identifier: ${t.message ?: "unknown error"}")
            throw handleError(t)
        }
    }

    override suspend fun stopExercise(identifier: String) {
        BleLogger.d(TAG, "Stop exercise pressed for $identifier")
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = (session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as? BlePsFtpClient)
            ?: throw PolarServiceNotAvailable()
        try {
            val params = PftpRequest.PbPFtpStopExerciseParams.newBuilder().setSave(true).build()
            client.query(PftpRequest.PbPFtpQuery.STOP_EXERCISE_VALUE, params.toByteArray())
            BleLogger.d(TAG, "Stop exercise succeeded for $identifier")
        } catch (t: Throwable) {
            BleLogger.e(TAG, "Stop exercise failed for $identifier: ${t.message ?: "unknown error"}")
            throw handleError(t)
        }
    }

    override suspend fun getExerciseStatus(identifier: String): PolarExerciseSession.ExerciseInfo {
        BleLogger.d(TAG, "Get exercise status pressed for $identifier")
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = (session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as? BlePsFtpClient)
            ?: throw PolarServiceNotAvailable()
        return try {
            val out = client.query(PftpRequest.PbPFtpQuery.GET_EXERCISE_STATUS_VALUE, byteArrayOf())
            val info = parseExerciseStatus(out.toByteArray())
            BleLogger.d(TAG, "Get exercise status succeeded for $identifier: $info")
            info
        } catch (t: Throwable) {
            BleLogger.e(TAG, "Get exercise status failed for $identifier: ${t.message ?: "unknown error"}")
            throw handleError(t)
        }
    }

    override fun observeExerciseStatus(identifier: String): Flow<PolarExerciseSession.ExerciseInfo> {
        BleLogger.d(TAG, "Start observing exercise status for $identifier")
        return flow {
            val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
            val client = (session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as? BlePsFtpClient)
                ?: throw PolarServiceNotAvailable()
            client.waitForNotification()
                .filter { notification -> notification.id == PftpNotification.PbPFtpDevToHostNotification.EXERCISE_STATUS_VALUE }
                .map { notification ->
                    val data = notification.byteArrayOutputStream.toByteArray()
                    parseExerciseStatus(data)
                }
                .catch { t -> throw handleError(t) }
                .collect { info ->
                    BleLogger.d(TAG, "Exercise status notification received for $identifier: $info")
                    emit(info)
                }
        }
    }

    override suspend fun readFile(identifier: String, filePath: String): ByteArray? {
        return PolarFileUtils.readFile(identifier, filePath, listener, TAG)
    }

    override suspend fun writeFile(identifier: String, filePath: String, fileData: ByteArray) {
        PolarFileUtils.writeFile(identifier, filePath, listener, fileData, TAG)
    }

    override suspend fun deleteFileOrDirectory(identifier: String, filePath: String) {
        PolarFileUtils.removeFileOrDirectory(identifier, filePath, listener, TAG)
    }

    override suspend fun getFileList(identifier: String, filePath: String, recurseDeep: Boolean): List<String> {
        return PolarFileUtils.getFileList(identifier, filePath, recurseDeep, listener, TAG)
    }

    private fun parseExerciseStatus(data: ByteArray): PolarExerciseSession.ExerciseInfo {
        val proto = PftpResponse.PbPftpGetExerciseStatusResult.parseFrom(data)
        BleLogger.d(TAG, "EX_STATUS raw: state=${proto.exerciseState} hasSport=${proto.hasSportIdentifier()} sport=${if (proto.hasSportIdentifier()) proto.sportIdentifier.value else -1} startTime=${proto.startTime}")

        val status = when (proto.exerciseState) {
            PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState.EXERCISE_STATE_RUNNING -> PolarExerciseSession.ExerciseStatus.IN_PROGRESS
            PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState.EXERCISE_STATE_PAUSED  -> PolarExerciseSession.ExerciseStatus.PAUSED
            PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState.EXERCISE_STATE_OFF     -> PolarExerciseSession.ExerciseStatus.STOPPED
            else -> PolarExerciseSession.ExerciseStatus.NOT_STARTED
        }

        val sport = if (proto.hasSportIdentifier()) {
            PolarExerciseSession.SportProfile.fromId(proto.sportIdentifier.value.toInt())
        } else {
            PolarExerciseSession.SportProfile.UNKNOWN
        }

        val startTime: LocalDateTime? = if (proto.hasStartTime()) {
            try {
                PolarTimeUtils.pbLocalDateTimeToLocalDateTimeWithOptionalTz(proto.startTime)
            } catch (e: Exception) {
                BleLogger.e(TAG, "Failed to parse exercise start time: ${e.message}")
                null
            }
        } else null

        return PolarExerciseSession.ExerciseInfo(
            status = status,
            sportProfile = sport,
            startTime = startTime
        )
    }

    override suspend fun setWareHouseSleep(identifier: String) {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val params = PftpNotification.PbPFtpFactoryResetParams.newBuilder()
        params.sleep = true
        params.doFactoryDefaults = true
        BleLogger.d(TAG, "send factory reset notification to device $identifier and set warehouse sleep setting to true")
        client.sendNotification(PftpNotification.PbPFtpHostToDevNotification.RESET.ordinal, params.build().toByteArray())
    }

    override suspend fun turnDeviceOff(identifier: String) {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val params = PftpNotification.PbPFtpFactoryResetParams.newBuilder()
        params.sleep = true
        params.doFactoryDefaults = false
        BleLogger.d(TAG, "turn off device device $identifier by setting sleep setting to true and disconnecting from device.")
        client.sendNotification(PftpNotification.PbPFtpHostToDevNotification.RESET.ordinal, params.build().toByteArray())
    }

    private fun <T : Any> startStreaming(identifier: String, type: PmdMeasurementType, setting: PolarSensorSetting, observer: (BlePMDClient) -> Flow<T>): Flow<T> {
        return flow {
            val session = PolarServiceClientUtils.sessionPmdClientReady(identifier, listener)
            val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient?
                ?: throw PolarServiceNotAvailable()
            client.startMeasurement(type, mapPolarSettingsToPmdSettings(setting))
            try {
                observer(client)
                    .catch { throwable -> throw handleError(throwable) }
                    .collect { emit(it) }
            } finally {
                stopPmdStreaming(session, client, type)
            }
        }
    }

    private fun openConnection(session: BleDeviceSession) {
        listener?.let { bleListener ->
            if (devicesStateMonitorJob == null || devicesStateMonitorJob?.isActive == false) {
                devicesStateMonitorJob = apiScope.launch {
                    bleListener.monitorDeviceSessionState().collect { pair ->
                        val bleSession = pair.first
                        val state = pair.second
                        deviceSessionState = state
                        val hasSAGRFCFileSystem = getFileSystemType(bleSession.polarDeviceType) == FileSystemType.POLAR_FILE_SYSTEM_V2
                        val deviceId = bleSession.polarDeviceId.ifEmpty { bleSession.address }
                        val info = deviceId?.let { id -> bleSession.address?.let { addr ->
                            PolarDeviceInfo(id, addr, bleSession.rssi, bleSession.name, true, hasSAGRFCFileSystem = hasSAGRFCFileSystem)
                        }}
                        when (state) {
                            DeviceSessionState.SESSION_OPEN -> {
                                withContext(Dispatchers.Main) { info?.let { i -> callback?.deviceConnected(i) } }
                                setupDevice(bleSession)
                            }
                            DeviceSessionState.SESSION_CLOSED -> {
                                if (bleSession.previousState == DeviceSessionState.SESSION_OPEN ||
                                    bleSession.previousState == DeviceSessionState.SESSION_OPENING ||
                                    bleSession.previousState == DeviceSessionState.SESSION_OPEN_PARK ||
                                    bleSession.previousState == DeviceSessionState.SESSION_CLOSING) {
                                    withContext(Dispatchers.Main) { info?.let { i -> callback?.deviceDisconnected(i) } }
                                }
                                tearDownDevice(bleSession)
                                // Cancel the monitor when no sessions remain open or opening
                                val hasActiveSessions = listener?.deviceSessions()?.any {
                                    it?.sessionState == DeviceSessionState.SESSION_OPEN ||
                                    it?.sessionState == DeviceSessionState.SESSION_OPENING ||
                                    it?.sessionState == DeviceSessionState.SESSION_OPEN_PARK
                                } ?: false
                                if (!hasActiveSessions) {
                                    BleLogger.d(TAG, "No active sessions remaining, cancelling devicesStateMonitorJob")
                                    devicesStateMonitorJob?.cancel()
                                    devicesStateMonitorJob = null
                                }
                            }
                            DeviceSessionState.SESSION_OPENING -> {
                                withContext(Dispatchers.Main) { info?.let { i -> callback?.deviceConnecting(i) } }
                            }
                            else -> {}
                        }
                    }
                }
            }
            bleListener.openSessionDirect(session)
        }
    }

    override suspend fun startOfflineRecording(identifier: String, feature: PolarDeviceDataType, settings: PolarSensorSetting?, secret: PolarRecordingSecret?) {
        val session = PolarServiceClientUtils.sessionPmdClientReady(identifier, listener)
        val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient?
            ?: throw PolarServiceNotAvailable()
        val pmdSecret = secret?.let { mapPolarSecretToPmdSecret(it) }
        client.startMeasurement(mapPolarFeatureToPmdClientMeasurementType(feature), mapPolarSettingsToPmdSettings(settings), PmdRecordingType.OFFLINE, pmdSecret)
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

    override suspend fun setOfflineRecordingTrigger(identifier: String, trigger: PolarOfflineRecordingTrigger, secret: PolarRecordingSecret?) {
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

    override fun startHrStreaming(identifier: String): Flow<PolarHrData> {
        val session = try {
            PolarServiceClientUtils.sessionServiceReady(identifier, HR_SERVICE, listener)
        } catch (e: Exception) {
            return flow { throw e }
        }
        val bleHrClient = session.fetchClient(HR_SERVICE) as BleHrClient?
            ?: return flow { throw PolarServiceNotAvailable() }
        BleLogger.d(TAG, "start Hr online streaming. Device: $identifier")
        return bleHrClient.observeHrNotifications(true)
            .map { hrNotificationData: HrNotificationData ->
                val sample = PolarHrData.PolarHrSample(
                    hrNotificationData.hrValue, 0, 0,
                    hrNotificationData.rrsMs, hrNotificationData.rrPresent,
                    hrNotificationData.sensorContact, hrNotificationData.sensorContactSupported
                )
                PolarHrData(listOf(sample))
            }
    }

    override suspend fun stopHrStreaming(identifier: String) {
        val session = PolarServiceClientUtils.sessionServiceReady(identifier, HR_SERVICE, listener)
        val bleHrClient = session.fetchClient(HR_SERVICE) as BleHrClient?
            ?: throw PolarServiceNotAvailable()
        BleLogger.d(TAG, "Stop heart rate online streaming. Device: $identifier")
        bleHrClient.stopObserveHrNotifications(true)
    }

    override fun startEcgStreaming(identifier: String, sensorSetting: PolarSensorSetting): Flow<PolarEcgData> {
        return startStreaming(identifier, PmdMeasurementType.ECG, sensorSetting, observer = { client: BlePMDClient ->
            client.monitorEcgNotifications(true)
                .map { ecgData: EcgData -> PolarDataUtils.mapPmdClientEcgDataToPolarEcg(ecgData) }
        })
    }

    override fun startAccStreaming(identifier: String, sensorSetting: PolarSensorSetting): Flow<PolarAccelerometerData> {
        return startStreaming(identifier, PmdMeasurementType.ACC, sensorSetting, observer = { client: BlePMDClient ->
            client.monitorAccNotifications(true)
                .map { accData: AccData -> mapPmdClientAccDataToPolarAcc(accData) }
        })
    }

    override fun startPpgStreaming(identifier: String, sensorSetting: PolarSensorSetting): Flow<PolarPpgData> {
        return startStreaming(identifier, PmdMeasurementType.PPG, sensorSetting, observer = { client: BlePMDClient ->
            client.monitorPpgNotifications(true)
                .map { ppgData: PpgData -> mapPMDClientPpgDataToPolarPpg(ppgData) }
        })
    }

    override fun startPpiStreaming(identifier: String): Flow<PolarPpiData> {
        return startStreaming(identifier, PmdMeasurementType.PPI, PolarSensorSetting(emptyMap())) { client: BlePMDClient ->
            client.monitorPpiNotifications(true)
                .map { ppiData: PpiData -> mapPMDClientPpiDataToPolarPpiData(ppiData) }
        }
    }

    override fun startMagnetometerStreaming(identifier: String, sensorSetting: PolarSensorSetting): Flow<PolarMagnetometerData> {
        return startStreaming(identifier, PmdMeasurementType.MAGNETOMETER, sensorSetting, observer = { client: BlePMDClient ->
            client.monitorMagnetometerNotifications(true)
                .map { mag: MagData -> mapPmdClientMagDataToPolarMagnetometer(mag) }
        })
    }

    override fun startGyroStreaming(identifier: String, sensorSetting: PolarSensorSetting): Flow<PolarGyroData> {
        return startStreaming(identifier, PmdMeasurementType.GYRO, sensorSetting, observer = { client: BlePMDClient ->
            client.monitorGyroNotifications(true)
                .map { gyro: GyrData -> mapPmdClientGyroDataToPolarGyro(gyro) }
        })
    }

    override fun startPressureStreaming(identifier: String, sensorSetting: PolarSensorSetting): Flow<PolarPressureData> {
        return startStreaming(identifier, PmdMeasurementType.PRESSURE, sensorSetting) { client: BlePMDClient ->
            client.monitorPressureNotifications(true)
                .map { pressure: PressureData -> mapPmdClientPressureDataToPolarPressure(pressure) }
        }
    }

    override fun startLocationStreaming(identifier: String, sensorSetting: PolarSensorSetting): Flow<PolarLocationData> {
        return startStreaming(identifier, PmdMeasurementType.LOCATION, sensorSetting) { client: BlePMDClient ->
            client.monitorLocationNotifications(true)
                .map { gnssLocationData: GnssLocationData -> mapPMDClientLocationDataToPolarLocationData(gnssLocationData) }
        }
    }

    override fun startTemperatureStreaming(identifier: String, sensorSetting: PolarSensorSetting): Flow<PolarTemperatureData> {
        return startStreaming(identifier, PmdMeasurementType.TEMPERATURE, sensorSetting) { client: BlePMDClient ->
            client.monitorTemperatureNotifications(true)
                .map { temperature: TemperatureData -> mapPmdClientTemperatureDataToPolarTemperature(temperature) }
        }
    }

    override fun startSkinTemperatureStreaming(identifier: String, sensorSetting: PolarSensorSetting): Flow<PolarTemperatureData> {
        return startStreaming(identifier, PmdMeasurementType.SKIN_TEMP, sensorSetting) { client: BlePMDClient ->
            client.monitorSkinTemperatureNotifications(true)
                .map { skinTemperature: SkinTemperatureData -> mapPmdClientSkinTemperatureDataToPolarTemperatureData(skinTemperature) }
        }
    }

    override fun stopStreaming(identifier: String, type: PmdMeasurementType) {
        val session = PolarServiceClientUtils.sessionPmdClientReady(identifier, listener)
        val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient?
        if (client != null) {
            stopPmdStreaming(session, client, type)
        }
    }

    override suspend fun enableSDKMode(identifier: String) {
        val session = PolarServiceClientUtils.sessionPmdClientReady(identifier, listener)
        val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient?
            ?: throw PolarServiceNotAvailable()
        if (!client.isServiceDiscovered) throw PolarServiceNotAvailable()
        try {
            client.startSDKMode()
        } catch (error: Throwable) {
            if (error is BleControlPointCommandError && PmdControlPointResponseCode.ERROR_ALREADY_IN_STATE == error.error) {
                return
            }
            throw error
        }
    }

    override suspend fun disableSDKMode(identifier: String) {
        val session = PolarServiceClientUtils.sessionPmdClientReady(identifier, listener)
        val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient?
            ?: throw PolarServiceNotAvailable()
        if (!client.isServiceDiscovered) throw PolarServiceNotAvailable()
        try {
            client.stopSDKMode()
        } catch (error: Throwable) {
            if (error is BleControlPointCommandError && PmdControlPointResponseCode.ERROR_ALREADY_IN_STATE == error.error) {
                return
            }
            throw error
        }
    }

    override suspend fun isSDKModeEnabled(identifier: String): Boolean {
        val session = PolarServiceClientUtils.sessionPmdClientReady(identifier, listener)
        val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient?
            ?: throw PolarServiceNotAvailable()
        return client.isSdkModeEnabled() != PmdSdkMode.DISABLED
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

    override suspend fun getAvailableOnlineStreamDataTypes(identifier: String): Set<PolarDeviceDataType> {
        val session = PolarServiceClientUtils.sessionPmdClientReady(identifier, listener)
        val blePMDClient = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient?
            ?: throw PolarServiceNotAvailable()
        val bleHrClient = session.fetchClient(HR_SERVICE) as BleHrClient?
        blePMDClient.clientReady(true)
        val pmdFeature = blePMDClient.readFeature(true)
        val deviceData: MutableSet<PolarDeviceDataType> = mutableSetOf()
        if (bleHrClient != null) deviceData.add(PolarDeviceDataType.HR)
        if (pmdFeature.contains(PmdMeasurementType.ECG)) deviceData.add(PolarDeviceDataType.ECG)
        if (pmdFeature.contains(PmdMeasurementType.ACC)) deviceData.add(PolarDeviceDataType.ACC)
        if (pmdFeature.contains(PmdMeasurementType.PPG)) deviceData.add(PolarDeviceDataType.PPG)
        if (pmdFeature.contains(PmdMeasurementType.PPI)) deviceData.add(PolarDeviceDataType.PPI)
        if (pmdFeature.contains(PmdMeasurementType.GYRO)) deviceData.add(PolarDeviceDataType.GYRO)
        if (pmdFeature.contains(PmdMeasurementType.MAGNETOMETER)) deviceData.add(PolarDeviceDataType.MAGNETOMETER)
        if (pmdFeature.contains(PmdMeasurementType.PRESSURE)) deviceData.add(PolarDeviceDataType.PRESSURE)
        if (pmdFeature.contains(PmdMeasurementType.LOCATION)) deviceData.add(PolarDeviceDataType.LOCATION)
        if (pmdFeature.contains(PmdMeasurementType.TEMPERATURE)) deviceData.add(PolarDeviceDataType.TEMPERATURE)
        if (pmdFeature.contains(PmdMeasurementType.SKIN_TEMP)) deviceData.add(PolarDeviceDataType.SKIN_TEMPERATURE)
        return deviceData
    }

    override suspend fun getAvailableHRServiceDataTypes(identifier: String): Set<PolarDeviceDataType> {
        val session = PolarServiceClientUtils.sessionServiceReady(identifier, HR_SERVICE, listener)
        val bleHrClient = session.fetchClient(HR_SERVICE) as BleHrClient?
        val deviceData: MutableSet<PolarDeviceDataType> = mutableSetOf()
        if (bleHrClient != null && bleHrClient.isServiceDiscovered) {
            deviceData.add(PolarDeviceDataType.HR)
        }
        return deviceData
    }

    override suspend fun getLogConfig(identifier: String): LogConfig {
        val byteArray = getFile(identifier, LogConfig.LOG_CONFIG_FILENAME)
        return try {
            LogConfig.fromBytes(byteArray)
        } catch (e: Exception) {
            BleLogger.e(TAG, "Failed to get LogConfig: $e")
            throw e
        }
    }

    override suspend fun setLogConfig(identifier: String, logConfig: LogConfig) {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val builder = PftpRequest.PbPFtpOperation.newBuilder()
        builder.command = PftpRequest.PbPFtpOperation.Command.PUT
        builder.path = LogConfig.LOG_CONFIG_FILENAME
        val data = ByteArrayInputStream(logConfig.toProto().toByteArray())
        client.write(builder.build().toByteArray(), data).collect {}
    }

    override fun updateFirmware(identifier: String, firmwareUrl: String): Flow<FirmwareUpdateStatus> = flow {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val hasH10FileSystem = getFileSystemType(session.polarDeviceType) == FileSystemType.H10_FILE_SYSTEM
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient
        sendInitializationAndStartSyncNotifications(identifier)

        val backupManager = PolarBackupManager(client)
        var backupList: List<PolarBackupManager.BackupFileData> = listOf()
        var firmwareVersionInfo = ""
        val automaticReconnection = listener?.getAutomaticReconnection()
        listener?.setAutomaticReconnection(true)

        var wasMultiConnectionEnabled = false
        try {
            wasMultiConnectionEnabled = getMultiBLEConnectionMode(identifier)
            if (wasMultiConnectionEnabled) {
                setMultiBLEConnectionMode(identifier, false)
                BleLogger.d(TAG, "Temporarily disabled multi-BLE connection for firmware update on $identifier")
            }
        } catch (ex: Exception) {
            BleLogger.e(TAG, "Failed to read/disable multi-BLE connection mode: ${ex.message}")
        }

        val fwUrlProvider = if (firmwareUrl.isNotBlank()) {
            Triple(File(firmwareUrl).name, firmwareUrl, FirmwareUpdateStatus.PreparingDeviceForFwUpdate("Preparing for firmware update"))
        } else {
            checkFirmwareUrlAvailability(client, identifier)
        }

        try {
            val (availableVersionInfo, url, updateStatus) = fwUrlProvider
            firmwareVersionInfo = availableVersionInfo ?: "new version"

            if (url.isNullOrBlank()) {
                emit(FirmwareUpdateStatus.FwUpdateNotAvailable("Firmware update not available"))
                return@flow
            }
            emit(updateStatus)
            emit(FirmwareUpdateStatus.FetchingFwUpdatePackage("Fetching firmware package to $firmwareVersionInfo"))
            val firmwareFiles = getFirmwareUpdatePackage(url)
            if (firmwareFiles.isEmpty()) {
                emit(FirmwareUpdateStatus.FwUpdateNotAvailable("Can not update, firmware files were not available"))
                throw Throwable("Firmware files were not available")
            }
            if (!hasH10FileSystem) {
                emit(FirmwareUpdateStatus.PreparingDeviceForFwUpdate("Backing up"))
                backupList = backupManager.backupDevice()
            }
            emit(FirmwareUpdateStatus.PreparingDeviceForFwUpdate("Performing factory reset"))
            doFactoryReset(identifier, true)
            emit(FirmwareUpdateStatus.PreparingDeviceForFwUpdate("Reconnecting after factory reset"))
            waitDeviceSessionWithPftpToOpen(identifier, 6 * 60L, waitForDeviceDownSeconds = 10L)
            sendInitializationAndStartSyncNotifications(identifier)
            try {
                writeFirmwareToDevice(this, client, firmwareFiles)
            } catch (error: Throwable) {
                val t = if (error is RuntimeException && error.cause != null) error.cause!! else error
                if (t !is BleDisconnected) throw t
            }
            emit(FirmwareUpdateStatus.FinalizingFwUpdate("Reconnecting after updating to $firmwareVersionInfo"))
            waitDeviceSessionWithPftpToOpen(identifier, 6 * 60L, waitForDeviceDownSeconds = 10L)
            if (!hasH10FileSystem) {
                sendInitializationAndStartSyncNotifications(identifier)
                emit(FirmwareUpdateStatus.FinalizingFwUpdate("Restoring backup on device"))
                backupManager.restoreBackup(backupList)
            }
            backupList = listOf()
            emit(FirmwareUpdateStatus.FinalizingFwUpdate("Setting device time"))
            setLocalTime(identifier, LocalDateTime.now())
            if (BlePolarDeviceCapabilitiesUtility.isDeviceSensor(session.polarDeviceType)) {
                emit(FirmwareUpdateStatus.FinalizingFwUpdate("Stopping sync"))
                sendTerminateAndStopSyncNotifications(identifier)
            } else {
                emit(FirmwareUpdateStatus.FinalizingFwUpdate("Restarting device"))
                doRestart(identifier)
                emit(FirmwareUpdateStatus.FinalizingFwUpdate("Restarting and reconnecting"))
                waitDeviceSessionWithPftpToOpen(identifier, 6 * 60L, waitForDeviceDownSeconds = 10L)
            }
            emit(FirmwareUpdateStatus.FwUpdateCompletedSuccessfully("Firmware update to $firmwareVersionInfo completed successfully"))
        } catch (error: Throwable) {
            if (!hasH10FileSystem) {
                if (backupList.isNotEmpty()) {
                    BleLogger.e(TAG, "Error during updateFirmware() to $firmwareVersionInfo, restoring backup, error: $error")
                    sendInitializationAndStartSyncNotifications(identifier)
                    backupManager.restoreBackup(backupList)
                    sendTerminateAndStopSyncNotifications(identifier)
                    emit(FirmwareUpdateStatus.FwUpdateFailed("Error during updateFirmware() to $firmwareVersionInfo, backup restored, error: $error"))
                } else {
                    emit(FirmwareUpdateStatus.FwUpdateFailed("Error during updateFirmware() to $firmwareVersionInfo, backup not available, error: $error"))
                }
            } else {
                emit(FirmwareUpdateStatus.FwUpdateFailed("Error during updateFirmware() to $firmwareVersionInfo, error: $error"))
            }
        }
        try {
            if (wasMultiConnectionEnabled) {
                setMultiBLEConnectionMode(identifier, true)
            }
        } catch (ex: Exception) {
            BleLogger.e(TAG, "Failed to restore multi-BLE connection mode: ${ex.message}")
        }
        automaticReconnection?.let { listener?.setAutomaticReconnection(it) }
    }

    override fun updateFirmware(identifier: String): Flow<FirmwareUpdateStatus> {
        return updateFirmware(identifier, firmwareUrl = "")
    }

    override fun checkFirmwareUpdate(identifier: String): Flow<CheckFirmwareUpdateStatus> = flow {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as? BlePsFtpClient
            ?: throw PolarServiceNotAvailable()
        val (availableVersion, _, firmwareStatus) = checkFirmwareUrlAvailability(client, identifier)
        emit(when (firmwareStatus) {
            is FirmwareUpdateStatus.FetchingFwUpdatePackage -> CheckFirmwareUpdateStatus.CheckFwUpdateAvailable(version = availableVersion ?: "Unknown")
            is FirmwareUpdateStatus.PreparingDeviceForFwUpdate,
            is FirmwareUpdateStatus.WritingFwUpdatePackage,
            is FirmwareUpdateStatus.FinalizingFwUpdate -> CheckFirmwareUpdateStatus.CheckFwUpdateFailed(details = "Firmware update is currently in progress; update cannot be performed now.")
            is FirmwareUpdateStatus.FwUpdateCompletedSuccessfully -> CheckFirmwareUpdateStatus.CheckFwUpdateNotAvailable(details = "Firmware update already completed successfully.")
            is FirmwareUpdateStatus.FwUpdateNotAvailable -> CheckFirmwareUpdateStatus.CheckFwUpdateNotAvailable(details = firmwareStatus.details)
            is FirmwareUpdateStatus.FwUpdateFailed -> CheckFirmwareUpdateStatus.CheckFwUpdateFailed(details = firmwareStatus.details)
        })
    }

    // Returns availableVersion, firmwareURL, FirmwareUpdateStatus
    private suspend fun checkFirmwareUrlAvailability(client: BlePsFtpClient, identifier: String): Triple<String?, String?, FirmwareUpdateStatus> {
        val deviceInfo = PolarFirmwareUpdateUtils.readDeviceFirmwareInfo(client, identifier)
        val httpClient = RetrofitClient.createRetrofitInstance()
        val firmwareUpdateApi = httpClient.create(FirmwareUpdateApi::class.java)
        val request = FirmwareUpdateRequest(
            clientId = "polar-sensor-data-collector-android",
            uuid = PolarDeviceUuid.fromDeviceId(identifier),
            firmwareVersion = deviceInfo.deviceFwVersion,
            hardwareCode = deviceInfo.deviceHardwareCode
        )
        val response = firmwareUpdateApi.checkFirmwareUpdate(request)
        return when (response.code()) {
            HttpResponseCodes.OK -> {
                val firmwareUpdateResponse = response.body()
                BleLogger.d(TAG, "Received firmware update response: $firmwareUpdateResponse")
                if (firmwareUpdateResponse != null &&
                    PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(deviceInfo.deviceFwVersion, firmwareUpdateResponse.version)) {
                    Triple(firmwareUpdateResponse.version, firmwareUpdateResponse.fileUrl, FirmwareUpdateStatus.FetchingFwUpdatePackage("Firmware available, fetching"))
                } else {
                    Triple(null, null, FirmwareUpdateStatus.FwUpdateNotAvailable("No fw update available, device firmware version ${deviceInfo.deviceFwVersion}"))
                }
            }
            HttpResponseCodes.NO_CONTENT -> Triple(null, null, FirmwareUpdateStatus.FwUpdateNotAvailable("No firmware update available"))
            HttpResponseCodes.BAD_REQUEST -> {
                val errorBody = try { response.errorBody()?.string() ?: "Failed to read error body" } catch (e: Exception) { "Error reading error body: ${e.message}" }
                BleLogger.e("TAG", "Bad request to firmware update API: $errorBody")
                Triple(null, null, FirmwareUpdateStatus.FwUpdateFailed("Bad request to firmware update API: $errorBody"))
            }
            else -> Triple(null, null, FirmwareUpdateStatus.FwUpdateFailed("Unexpected response code: ${response.code()}"))
        }
    }

    private suspend fun getFirmwareUpdatePackage(firmwareUrl: String): List<Pair<String, ByteArray>> {
        return if (firmwareUrl.startsWith("file://")) {
            val file = File(URI.create(firmwareUrl).path)
            BleLogger.d(TAG, "FW package read from local file: ${file.absolutePath}, size: ${file.length()} bytes")
            parseFirmwareZip(file.readBytes())
        } else {
            val httpClient = RetrofitClient.createRetrofitInstance()
            val firmwareUpdateApi = httpClient.create(FirmwareUpdateApi::class.java)
            val firmwareBytes = firmwareUpdateApi.getFirmwareUpdatePackage(firmwareUrl)
            BleLogger.d(TAG, "FW package downloaded, size: ${firmwareBytes.contentLength()} bytes")
            parseFirmwareZip(firmwareBytes.bytes())
        }
    }

    private fun parseFirmwareZip(bytes: ByteArray): List<Pair<String, ByteArray>> {
        val firmwareFiles = mutableListOf<Pair<String, ByteArray>>()
        val zipInputStream = ZipInputStream(ByteArrayInputStream(bytes))
        var entry: ZipEntry?
        val buffer = ByteArray(PolarFirmwareUpdateUtils.BUFFER_SIZE)
        while (zipInputStream.nextEntry.also { entry = it } != null) {
            val entryFileName = entry!!.name
            // Polar H10 FW package has this file
            if (entryFileName.equals("readme.txt")) {
                BleLogger.d(TAG, "Skipping file $entryFileName")
                zipInputStream.closeEntry()
                continue
            }

            val byteArrayOutputStream = ByteArrayOutputStream()
            var length: Int
            while (zipInputStream.read(buffer).also { length = it } != -1) {
                byteArrayOutputStream.write(buffer, 0, length)
            }
            val fileName = entry!!.name
            BleLogger.d(TAG, "Extracted firmware file: $fileName")
            firmwareFiles.add(Pair(fileName, byteArrayOutputStream.toByteArray()))
            zipInputStream.closeEntry()
        }
        zipInputStream.close()

        firmwareFiles.sortWith { f1, f2 ->
            PolarFirmwareUpdateUtils.FwFileComparator()
                .compare(File(f1.first), File(f2.first))
        }
        return firmwareFiles
    }

    private suspend fun writeFirmwareToDevice(collector: FlowCollector<FirmwareUpdateStatus>,
                                              client: BlePsFtpClient,
                                              firmwareFiles: List<Pair<String, ByteArray>>,
                                              minPercentageIncrement: Long = 0) {
        for (firmwareFile in firmwareFiles) {
            var lastBytesWritten = 0L
            BleLogger.d(TAG, "Prepare firmware update for ${firmwareFile.first}")
            client.query(PftpRequest.PbPFtpQuery.PREPARE_FIRMWARE_UPDATE_VALUE, null)
            BleLogger.d(TAG, "Start ${firmwareFile.first} write")
            val builder = PftpRequest.PbPFtpOperation.newBuilder()
            builder.command = PftpRequest.PbPFtpOperation.Command.PUT
            builder.path = "/${firmwareFile.first}"
            try {
                var lastEmitTime = 0L
                client.write(builder.build().toByteArray(), ByteArrayInputStream(firmwareFile.second))
                    .collect { bytesWritten: Long ->
                        val now = System.currentTimeMillis()
                        val delta = bytesWritten - lastBytesWritten
                        val deltaPercentage = if (firmwareFile.second.isNotEmpty()) delta * 100 / firmwareFile.second.size else 0
                        val timeSinceLastEmit = now - lastEmitTime
                        val updateDownstream = lastBytesWritten == 0L || bytesWritten >= firmwareFile.second.size || deltaPercentage > minPercentageIncrement || timeSinceLastEmit >= 5000
                        if (updateDownstream) {
                            lastBytesWritten = bytesWritten
                            lastEmitTime = now
                            val percentage = if (firmwareFile.second.isNotEmpty()) bytesWritten * 100 / firmwareFile.second.size else 0
                            BleLogger.d(TAG, "Writing firmware update file, bytes written: $bytesWritten/${firmwareFile.second.size}")
                            collector.emit(FirmwareUpdateStatus.WritingFwUpdatePackage(
                                "Writing firmware update file ${firmwareFile.first} ($percentage%), bytes written: $bytesWritten/${firmwareFile.second.size}"
                            ))
                        }
                    }
            } catch (error: Throwable) {
                if (error is PftpResponseError && error.error == PbPFtpError.REBOOTING.number) {
                    BleLogger.d(TAG, "REBOOTING after writing ${firmwareFile.first}")
                } else {
                    throw error
                }
            }
            if (firmwareFile.first.contains("SYSUPDAT.IMG")) {
                BleLogger.d(TAG, "Firmware file is SYSUPDAT.IMG, waiting for reboot")
            }
        }
    }

    private suspend fun waitDeviceSessionWithPftpToOpen(
        deviceId: String,
        timeoutSeconds: Long,
        waitForDeviceDownSeconds: Long = 0L
    ) {
        BleLogger.d(TAG, "waitDeviceSessionWithPftpToOpen(), seconds: $timeoutSeconds, waitForDeviceDownSeconds: $waitForDeviceDownSeconds")
        val pollIntervalMs = 5_000L
        val timeoutMs = timeoutSeconds * 1000L

        if (waitForDeviceDownSeconds > 0) {
            delay(waitForDeviceDownSeconds * 1000L)
        }

        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, listener)
                BleLogger.d(TAG, "Session with PsFtpClient opened, deviceId: $deviceId")
                return
            } catch (error: Throwable) {
                BleLogger.d(TAG, "Waiting for session with PsFtpClient, deviceId $deviceId, error (ignored) $error")
            }
            BleLogger.d(TAG, "Continuing to wait for device session PsFtpClient to open, deviceId: $deviceId ...")
            delay(pollIntervalMs)
        }
        throw Throwable("Timeout reached while waiting for device session with PsFtpClient to open, deviceId: $deviceId")
    }

    override suspend fun deleteStoredDeviceData(identifier: String, dataType: PolarStoredDataType, until: LocalDate?) {
        var folderPath = "/U/0"
        val entryPattern = dataType.type
        val cond: PolarFileUtils.FetchRecursiveCondition

        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()

        when (dataType.type) {
            PolarStoredDataType.AUTO_SAMPLE.type -> {
                folderPath = "/U/0/AUTOS"
                cond = PolarFileUtils.FetchRecursiveCondition { entry: String ->
                    entry.matches(Regex("^(\\d{8})(/)")) ||
                            entry.contains(".BPB")
                }
            }

            PolarStoredDataType.SDLOGS.type -> {
                folderPath = "/SDLOGS"
                cond = PolarFileUtils.FetchRecursiveCondition { entry: String ->
                    entry.matches(Regex("^(\\d{8})(/)")) ||
                            entry == "${entryPattern}/" ||
                            entry.contains(".SLG") ||
                            entry.contains(".TXT")
                }
            }

            else -> {
                cond = PolarFileUtils.FetchRecursiveCondition { entry: String ->
                    entry.matches(Regex("^(\\d{8})(/)")) ||
                            entry == "${dateFormatter.format(until).toString().replace("-", "")}/" ||
                            entry == "${entryPattern}/" ||
                            entry.contains(".BPB") &&
                            !entry.contains("USERID.BPB") &&
                            !entry.contains("HIST")
                }
            }
        }

        try {
            val deletedFiles = mutableListOf<String>()
            PolarFileUtils.listFiles(identifier, folderPath, condition = cond, listener, tag = TAG)
                .collect { filename ->
                    if (dataType.type != PolarStoredDataType.AUTO_SAMPLE.type && dataType.type != PolarStoredDataType.SDLOGS.type) {
                        val dateFromFileName = LocalDate.parse(filename.split("/")[3], dateFormatter)
                        if (until != null && (until.isAfter(dateFromFileName) || until == dateFromFileName)) {
                            PolarFileUtils.removeSingleFile(identifier, filename, listener, TAG)
                            deletedFiles.add(filename)
                        }
                    } else if (dataType.type == PolarStoredDataType.AUTO_SAMPLE.type) {
                        val byteArray = getFile(identifier, filename)
                        if (byteArray != null) {
                            val proto = PbAutomaticSampleSessions.parseFrom(byteArray)
                            val date = PolarTimeUtils.pbDateToLocalDate(proto.day)
                            if (until != null && (date.isBefore(until) || date == until)) {
                                PolarFileUtils.removeSingleFile(identifier, filename, listener, TAG)
                                deletedFiles.add(filename)
                            }
                        }
                    } else if (dataType.type == PolarStoredDataType.SDLOGS.type) {
                        PolarFileUtils.removeSingleFile(identifier, filename, listener, TAG)
                    }
                }

            if (dataType.type != PolarStoredDataType.AUTO_SAMPLE.type && dataType.type != PolarStoredDataType.SDLOGS.type) {
                val dirs = mutableListOf<String>()
                for (file in deletedFiles) {
                    if (file != "") {
                        var currentDir = file.substringBeforeLast("/")
                        while (currentDir != "/U/0") {
                            dirs.add(currentDir)
                            currentDir = currentDir.substringBeforeLast("/")
                        }
                    }
                }
                for (dir in dirs) {
                    val isEmpty = checkIfDirectoryIsEmpty(dir, client)
                    if (isEmpty) {
                        PolarFileUtils.removeSingleFile(identifier, dir, listener, TAG)
                    }
                }
            }
        } catch (error: Throwable) {
            BleLogger.e(TAG, "Error while trying to delete offline recordings from device $identifier, error: $error")
        }
    }

    override suspend fun deleteDeviceDateFolders(identifier: String, fromDate: LocalDate?, toDate: LocalDate?) {
        BleLogger.d(TAG, "Delete empty day folders between: $fromDate to $toDate.")
        val dateFormatter = DateTimeFormatter.BASIC_ISO_DATE
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()

        if (fromDate != null && toDate != null && !fromDate.isAfter(toDate)) {
            val dates = generateSequence(fromDate) { it.plusDays(1) }.takeWhile { !it.isAfter(toDate) }.toList()
            for (date in dates) {
                val path = "/U/0/${dateFormatter.format(date).plus("/")}"
                val builder = PftpRequest.PbPFtpOperation.newBuilder()
                builder.command = PftpRequest.PbPFtpOperation.Command.REMOVE
                builder.path = path.trimEnd('/')
                try {
                    client.request(builder.build().toByteArray())
                } catch (throwable: Throwable) {
                    if (throwable.message?.contains("PFTP error") == true &&
                        throwable.message?.contains(PbPFtpError.NO_SUCH_FILE_OR_DIRECTORY.number.toString()) == true) {
                        BleLogger.d(TAG, "Day directory for date $date was not found.")
                    } else {
                        throw throwable
                    }
                }
            }
        }
    }

    override suspend fun deleteTelemetryData(identifier: String) {
        BleLogger.d(TAG, "Delete all telemetry data from device.")

        val cond = PolarFileUtils.FetchRecursiveCondition { entry: String ->
            entry.matches(Regex("([A-Za-z]{3}[0-9]{1,3}).BIN$")) &&
                    entry.startsWith("TRC")
        }

        try {
            PolarFileUtils.listFiles(identifier, "/", condition = cond, listener, TAG)
                .collect { filename ->
                    PolarFileUtils.removeSingleFile(identifier, filename, listener, TAG)
                }
        } catch (error: Throwable) {
            BleLogger.e(TAG, "Error while trying to delete telemetry files from device $identifier, error: $error")
        }
    }

    override suspend fun setMultiBLEConnectionMode(identifier: String, enable: Boolean) {
        val session = PolarServiceClientUtils.sessionPsPfcClientReady(identifier, listener)
        val client = session.fetchClient(PFC_SERVICE) as BlePfcClient?
            ?: throw PolarServiceNotAvailable()
        BleLogger.d(TAG, "Send multi BLE enable notification to device $identifier with mode $enable.")
        val pfcResponse = client.sendControlPointCommand(PfcMessage.PFC_CONFIGURE_MULTI_CONNECTION_SETTING, if (enable) 1 else 0)
        if (pfcResponse.status.toInt() != 1) {
            throw PolarOperationNotSupported()
        }
    }

    override suspend fun getMultiBLEConnectionMode(identifier: String): Boolean {
        val session = PolarServiceClientUtils.sessionPsPfcClientReady(identifier, listener)
        val client = session.fetchClient(PFC_SERVICE) as BlePfcClient?
            ?: throw PolarServiceNotAvailable()
        BleLogger.d(TAG, "Request multi BLE mode status from device $identifier.")
        val pfcResponse = client.sendControlPointCommand(PfcMessage.PFC_REQUEST_MULTI_CONNECTION_SETTING, null)
        return pfcResponse.payload?.get(0)?.toInt() == 1
    }

    override suspend fun setAutomaticOHRMeasurementEnabled(identifier: String, enabled: Boolean) {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val currentProto = getUserDeviceSettingsProto(client, session.polarDeviceType)
        val builder = currentProto.toBuilder()
        val automaticMeasurementBuilder = if (currentProto.hasAutomaticMeasurementSettings()) {
            currentProto.automaticMeasurementSettings.toBuilder()
        } else {
            UserDeviceSettings.PbUserAutomaticMeasurementSettings.newBuilder()
        }
        val autosBuilder = UserDeviceSettings.PbAutomaticMeasurementSettings.newBuilder()
            .setState(if (enabled) UserDeviceSettings.PbAutomaticMeasurementSettings.PbAutomaticMeasurementState.ALWAYS_ON
            else UserDeviceSettings.PbAutomaticMeasurementSettings.PbAutomaticMeasurementState.OFF)
        if (!enabled) {
            autosBuilder.clearTimedSettings()
            autosBuilder.clearIntelligentTimedSettings()
        }
        automaticMeasurementBuilder.setAutomaticOhrMeasurement(autosBuilder.build())
        builder.setAutomaticMeasurementSettings(automaticMeasurementBuilder)
        setUserDeviceSettingsProto(identifier, builder.build())
        BleLogger.d(TAG, "AUTOS files enabled = $enabled written for $identifier")
    }

    override suspend fun get247HrSamples(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<Polar247HrSamplesData> {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        return PolarAutomaticSamplesUtils.read247HrSamples(client, fromDate, toDate)
    }

    override suspend fun get247PPiSamples(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<Polar247PPiSamplesData> {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        return PolarAutomaticSamplesUtils.read247PPiSamples(client, fromDate, toDate)
    }

    override suspend fun getNightlyRecharge(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<PolarNightlyRechargeData> {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val dates = generateSequence(fromDate) { it.plusDays(1) }.takeWhile { !it.isAfter(toDate) }.toList()
        val result = mutableListOf<PolarNightlyRechargeData>()
        for (date in dates) {
            PolarNightlyRechargeUtils.readNightlyRechargeData(client, date)?.let { result.add(it) }
        }
        return result
    }

    override suspend fun getSkinTemperature(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<PolarSkinTemperatureData> {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val dates = generateSequence(fromDate) { it.plusDays(1) }.takeWhile { !it.isAfter(toDate) }.toList()
        val result = mutableListOf<PolarSkinTemperatureData>()
        for (date in dates) {
            PolarSkinTemperatureUtils.readSkinTemperatureDataFromDayDirectory(client, date)?.let { skinTempResult ->
                result.add(PolarSkinTemperatureData(date, skinTempResult))
            }
        }
        return result
    }

    override suspend fun getSpo2Test(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<PolarSpo2TestData> {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val dates = generateSequence(fromDate) { it.plusDays(1) }.takeWhile { !it.isAfter(toDate) }.toList()
        val result = mutableListOf<PolarSpo2TestData>()
        for (date in dates) {
            val entries = PolarTestUtils.readSpo2TestProtoFromDayDirectory(client, date)
            for (entry in entries) {
                try {
                    result.add(PolarTestUtils.mapSpo2TestEntry(entry))
                } catch (error: Throwable) {
                    BleLogger.w(TAG, "getSpo2Test() failed to parse SPO2 proto for date $date timedir ${entry.timeDirName}, error: $error")
                }
            }
        }
        return result
    }


    override fun getTrainingSessionReferences(identifier: String, fromDate: LocalDate?, toDate: LocalDate?): Flow<PolarTrainingSessionReference> {
        return flow {
            val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
            val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                ?: throw PolarServiceNotAvailable()
            emitAll(
                PolarTrainingSessionUtils.getTrainingSessionReferences(client, fromDate, toDate)
            )
        }
    }

    override suspend fun getTrainingSession(
        identifier: String,
        trainingSessionReference: PolarTrainingSessionReference
    ): PolarTrainingSession {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        return PolarTrainingSessionUtils.readTrainingSession(client, trainingSessionReference)
    }

    override fun getTrainingSessionWithProgress(
        identifier: String,
        trainingSessionReference: PolarTrainingSessionReference
    ): Flow<PolarTrainingSessionFetchResult> {
        return flow {
            val totalBytes = trainingSessionReference.fileSize
            val accumulatedBytes = AtomicLong(0L)
            emit(
                PolarTrainingSessionFetchResult.Progress(
                    progress = PolarTrainingSessionProgress(
                        totalBytes = totalBytes,
                        completedBytes = 0L,
                        progressPercent = 0,
                        currentFileName = null
                    )
                )
            )
            val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
            val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                ?: throw PolarServiceNotAvailable()
            client.setProgressCallback(object : BlePsFtpClient.ProgressCallback {
                override fun onProgressUpdate(bytesReceived: Long) {
                    val currentBytes = accumulatedBytes.addAndGet(bytesReceived)
                    val percent = if (totalBytes > 0) ((currentBytes * 100L) / totalBytes).toInt().coerceIn(0, 100) else 0
                    BleLogger.d(TAG, "Training session fetch progress: $currentBytes/$totalBytes ($percent%)")
                }
            })
            val result = PolarTrainingSessionUtils.readTrainingSessionWithProgress(client, trainingSessionReference)
            emit(PolarTrainingSessionFetchResult.Complete(result))
        }
    }

    override suspend fun waitForConnection(identifier: String) {
        while (true) {
            val session = fetchSession(identifier, listener)
            if (session != null && session.sessionState == DeviceSessionState.SESSION_OPEN) {
                return
            }
            delay(100)
        }
    }

    override suspend fun sendInitializationAndStartSyncNotifications(identifier: String): Boolean {
        BleLogger.d(TAG, "Sending initialize session and start sync notifications")
        return try {
            val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
            val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                ?: return false
            client.query(PftpRequest.PbPFtpQuery.REQUEST_SYNCHRONIZATION_VALUE, null)
            client.sendNotification(
                PftpNotification.PbPFtpHostToDevNotification.INITIALIZE_SESSION_VALUE,
                null
            )
            client.sendNotification(
                PftpNotification.PbPFtpHostToDevNotification.START_SYNC_VALUE,
                null
            )
            true
        } catch (e: Throwable) {
            BleLogger.e(TAG, "sendInitializationAndStartSyncNotifications failed: $e")
            false
        }
    }

    override suspend fun sendTerminateAndStopSyncNotifications(identifier: String) {
        BleLogger.d(TAG, "Sending terminate session and stop sync notifications")
        try {
            val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
            val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                ?: return
            client.sendNotification(
                PftpNotification.PbPFtpHostToDevNotification.STOP_SYNC_VALUE,
                PftpNotification.PbPFtpStopSyncParams.newBuilder().setCompleted(true).build().toByteArray()
            )
            client.sendNotification(
                PftpNotification.PbPFtpHostToDevNotification.TERMINATE_SESSION_VALUE,
                null
            )
        } catch (e: Throwable) {
            BleLogger.e(TAG, "sendTerminateAndStopSyncNotifications failed: $e")
        }
    }

    private suspend fun getUserDeviceSettingsProto(client: BlePsFtpClient, polarDeviceType: String): PbUserDeviceSettings {
        val path = when (getFileSystemType(polarDeviceType)) {
            FileSystemType.POLAR_FILE_SYSTEM_V2 -> PolarUserDeviceSettings.DEVICE_SETTINGS_FILENAME
            FileSystemType.H10_FILE_SYSTEM -> PolarUserDeviceSettings.SENSOR_SETTINGS_FILENAME
            else -> throw PolarOperationNotSupported()
        }
        val builder = PftpRequest.PbPFtpOperation.newBuilder().apply {
            command = PftpRequest.PbPFtpOperation.Command.GET
            this.path = path
        }
        return try {
            val byteArray = client.request(builder.build().toByteArray()).toByteArray()
            PbUserDeviceSettings.parseFrom(byteArray)
        } catch (e: Throwable) {
            BleLogger.e(TAG, "Failed to get device user settings: $e")
            throw e
        }
    }

    private suspend fun setUserDeviceSettingsProto(identifier: String, deviceUserSetting: PbUserDeviceSettings) {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val settingsPath = when (getFileSystemType(session.polarDeviceType)) {
            FileSystemType.H10_FILE_SYSTEM -> PolarUserDeviceSettings.SENSOR_SETTINGS_FILENAME
            else -> PolarUserDeviceSettings.DEVICE_SETTINGS_FILENAME
        }
        val deviceSettingsBuilder = PftpRequest.PbPFtpOperation.newBuilder().apply {
            command = PftpRequest.PbPFtpOperation.Command.PUT
            path = settingsPath
        }
        val deviceSettingsData = ByteArrayOutputStream().use { baos ->
            deviceUserSetting.writeTo(baos)
            baos.toByteArray()
        }
        client.write(deviceSettingsBuilder.build().toByteArray(), ByteArrayInputStream(deviceSettingsData)).collect {}
    }

    override suspend fun getSteps(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<PolarStepsData> {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val dates = generateSequence(fromDate) { it.plusDays(1) }.takeWhile { !it.isAfter(toDate) }.toList()
        return dates.map { date ->
            PolarStepsData(date, PolarActivityUtils.readStepsFromDayDirectory(client, date))
        }
    }

    override suspend fun getActivitySampleData(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<PolarActivitySamplesDayData> {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val dates = generateSequence(fromDate) { it.plusDays(1) }.takeWhile { !it.isAfter(toDate) }.toList()
        return dates.map { date -> PolarActivityUtils.readActivitySamplesDataFromDayDirectory(client, date) }
    }

    override suspend fun getDailySummaryData(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<PolarDailySummaryData> {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val dates = generateSequence(fromDate) { it.plusDays(1) }.takeWhile { !it.isAfter(toDate) }.toList()
        return dates.map { date -> PolarActivityUtils.readDailySummaryDataFromDayDirectory(client, date) }
    }

    override suspend fun getDistance(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<PolarDistanceData> {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val dates = generateSequence(fromDate) { it.plusDays(1) }.takeWhile { !it.isAfter(toDate) }.toList()
        return dates.map { date ->
            PolarDistanceData(date, PolarActivityUtils.readDistanceFromDayDirectory(client, date))
        }
    }

    override suspend fun getSleepRecordingState(identifier: String): Boolean {
        return observeSleepRecordingState(identifier)
            .filter { it.isNotEmpty() }
            .take(1)
            .map { it.last() }
            .let { flow ->
                var result = false
                flow.collect { result = it }
                result
            }
    }

    override fun observeSleepRecordingState(identifier: String): Flow<Array<Boolean>> {
        return flow {
            val deviceType = fetchSession(identifier, listener)?.polarDeviceType
                ?: throw PolarServiceNotAvailable()
            if (!BlePolarDeviceCapabilitiesUtility.isActivityDataSupported(deviceType)) {
                throw PolarServiceNotAvailable()
            }
            putNotification(
                identifier = identifier,
                notification = "{}",
                path = "/REST/SLEEP.API?cmd=subscribe&event=sleep_recording_state&details=[enabled]"
            )
            emitAll(
                receiveRestApiEvents(identifier, mapper = { jsonString ->
                    com.google.gson.Gson().fromJson(jsonString, PolarSleepApiServiceEventPayload::class.java)
                }).map { list ->
                    list.map { it.sleep_recording_state.enabled == 1 }.toTypedArray()
                }
            )
        }
    }

    override suspend fun stopSleepRecording(identifier: String) {
        putNotification(
            identifier = identifier,
            notification = "{}",
            path = "/REST/SLEEP.API?cmd=post&endpoint=stop_sleep_recording"
        )
    }

    override suspend fun getSleep(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<PolarSleepData> {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val dates = generateSequence(fromDate) { it.plusDays(1) }.takeWhile { !it.isAfter(toDate) }.toList()
        return dates.mapNotNull { date ->
            try {
                PolarSleepData(date, PolarSleepUtils.readSleepDataFromDayDirectory(client, date))
            } catch (e: Throwable) {
                BleLogger.w(TAG, "Failed to read sleep data for $date: $e")
                null
            }
        }
    }

    override suspend fun getCalories(identifier: String, fromDate: LocalDate, toDate: LocalDate, caloriesType: CaloriesType): List<PolarCaloriesData> {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val dates = generateSequence(fromDate) { it.plusDays(1) }.takeWhile { !it.isAfter(toDate) }.toList()
        return dates.map { date ->
            PolarCaloriesData(date, PolarActivityUtils.readSpecificCaloriesFromDayDirectory(client, date, caloriesType))
        }
    }

    private fun getDatesBetween(startDate: LocalDate, endDate: LocalDate): List<LocalDate> {
        return generateSequence(startDate) { it.plusDays(1) }.takeWhile { !it.isAfter(endDate) }.toList()
    }

    override suspend fun getActiveTime(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<PolarActiveTimeData> {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val dates = generateSequence(fromDate) { it.plusDays(1) }.takeWhile { !it.isAfter(toDate) }.toList()
        return dates.map { date -> PolarActivityUtils.readActiveTimeFromDayDirectory(client, date) }
    }

    @Deprecated("Use setting specific methods instead")
    override suspend fun setUserDeviceSettings(identifier: String, deviceUserSetting: PolarUserDeviceSettings) {
        setUserDeviceSettingsProto(identifier, deviceUserSetting.toProto())
    }

    override suspend fun getUserDeviceSettings(identifier: String): PolarUserDeviceSettings {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val proto = getUserDeviceSettingsProto(client, session.polarDeviceType)
        return PolarUserDeviceSettings().fromBytes(proto.toByteArray())
    }

    override suspend fun setUserDeviceLocation(identifier: String, location: Int) {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val currentProto = getUserDeviceSettingsProto(client, session.polarDeviceType)
        val generalSettings = currentProto.generalSettings.toBuilder()
            .setDeviceLocation(fi.polar.remote.representation.protobuf.Types.PbDeviceLocation.forNumber(location))
            .build()
        val updated = currentProto.toBuilder().setGeneralSettings(generalSettings).build()
        setUserDeviceSettingsProto(identifier, updated)
        BleLogger.d(TAG, "Device location set to $location for $identifier")
    }

    override suspend fun setUsbConnectionMode(identifier: String, enabled: Boolean) {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val currentProto = getUserDeviceSettingsProto(client, session.polarDeviceType)
        val usbSettings = UserDeviceSettings.PbUsbConnectionSettings.newBuilder()
            .setMode(
                if (enabled) UserDeviceSettings.PbUsbConnectionSettings.PbUsbConnectionMode.ON
                else UserDeviceSettings.PbUsbConnectionSettings.PbUsbConnectionMode.OFF
            ).build()
        val updated = currentProto.toBuilder().setUsbConnectionSettings(usbSettings).build()
        setUserDeviceSettingsProto(identifier, updated)
        BleLogger.d(TAG, "USB connection mode set to $enabled for $identifier")
    }

    override suspend fun setAutomaticTrainingDetectionSettings(
        identifier: String,
        automaticTrainingDetectionMode: Boolean,
        automaticTrainingDetectionSensitivity: Int,
        minimumTrainingDurationSeconds: Int
    ) {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val currentProto = getUserDeviceSettingsProto(client, session.polarDeviceType)
        val atdSettings = UserDeviceSettings.PbAutomaticTrainingDetectionSettings.newBuilder()
            .setState(
                if (automaticTrainingDetectionMode)
                    UserDeviceSettings.PbAutomaticTrainingDetectionSettings.PbAutomaticTrainingDetectionState.ON
                else
                    UserDeviceSettings.PbAutomaticTrainingDetectionSettings.PbAutomaticTrainingDetectionState.OFF
            )
            .setSensitivity(automaticTrainingDetectionSensitivity)
            .setMinimumTrainingDurationSeconds(minimumTrainingDurationSeconds)
            .build()
        val autoMeasBuilder = if (currentProto.hasAutomaticMeasurementSettings())
            currentProto.automaticMeasurementSettings.toBuilder()
        else
            UserDeviceSettings.PbUserAutomaticMeasurementSettings.newBuilder()
        autoMeasBuilder.setAutomaticTrainingDetectionSettings(atdSettings)
        val updated = currentProto.toBuilder()
            .setAutomaticMeasurementSettings(autoMeasBuilder.build())
            .build()
        setUserDeviceSettingsProto(identifier, updated)
        BleLogger.d(TAG, "Automatic training detection set to mode=$automaticTrainingDetectionMode sensitivity=$automaticTrainingDetectionSensitivity for $identifier")
    }

    override suspend fun setDaylightSavingTime(identifier: String) {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val localTime = LocalDateTime.now()
        BleLogger.d(TAG, "setDaylightSavingTime: setting local time $localTime for $identifier")
        val pbLocalTime = javaLocalDateTimeToPbPftpSetLocalTime(localTime)
        client.query(PftpRequest.PbPFtpQuery.SET_LOCAL_TIME_VALUE, pbLocalTime.toByteArray())
    }

    override suspend fun setTelemetryEnabled(deviceId: String, enabled: Boolean) {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val currentProto = getUserDeviceSettingsProto(client, session.polarDeviceType)
        val telemetrySettings = PbUserDeviceTelemetrySettings.newBuilder()
            .setTelemetryEnabled(enabled)
            .build()
        val updated = currentProto.toBuilder().setTelemetrySettings(telemetrySettings).build()
        setUserDeviceSettingsProto(deviceId, updated)
        BleLogger.d(TAG, "Telemetry enabled=$enabled for $deviceId")
    }

    private fun sessionByDeviceId(deviceId: String): BleDeviceSession? {
        listener?.let {
            val sessions = it.deviceSessions()
            if (sessions != null) {
                for (session in sessions) {
                    if (session != null) {
                        if (session.advertisementContent.polarDeviceId == deviceId) {
                            return session
                        }
                    }
                }
            }
        }
        return null
    }

    private fun stopPmdStreaming(session: BleDeviceSession, client: BlePMDClient, type: PmdMeasurementType) {
        if (session.sessionState == DeviceSessionState.SESSION_OPEN) {
            stopPmdStreamingJob[session.address] = apiScope.launch {
                try {
                    client.stopMeasurement(type)
                } catch (throwable: Throwable) {
                    logError("failed to stop pmd stream: " + throwable.localizedMessage)
                }
            }
        }
    }

    @Throws(PolarBleSdkInternalException::class)
    override fun getBatteryLevel(identifier: String): Int {
        val session: BleDeviceSession
        val client: BleBattClient
        try {
            session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
            client = session.fetchClient(BATTERY_SERVICE) as BleBattClient?
                ?: throw PolarServiceNotAvailable()
        } catch (e: Throwable) {
            throw PolarBleSdkInternalException("Error while fetching battery level percentage: ${e.localizedMessage}")
        }

        return client.getBatteryLevel()
    }

    @Throws(PolarBleSdkInternalException::class)
    override fun getChargerState(identifier: String): ChargeState {
        val session: BleDeviceSession
        val client: BleBattClient
        try {
            session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
            client = session.fetchClient(BATTERY_SERVICE) as BleBattClient?
                ?: throw PolarServiceNotAvailable()
        } catch (e: Throwable) {
            throw PolarBleSdkInternalException("Error while fetching charger state: ${e.localizedMessage}")
        }

        return client.getChargerStatus()
    }

    override suspend fun startOfflineExerciseV2(
        identifier: String,
        sportProfile: PolarExerciseSession.SportProfile
    ): PolarOfflineExerciseV2Api.OfflineExerciseStartResult {
        if (!isFeatureReady(identifier, PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2)) {
            throw PolarOperationNotSupported()
        }
        return offlineExerciseV2Api.startOfflineExerciseV2(identifier, sportProfile)
    }

    override suspend fun stopOfflineExerciseV2(identifier: String) {
        if (!isFeatureReady(identifier, PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2)) {
            throw PolarOperationNotSupported()
        }
        offlineExerciseV2Api.stopOfflineExerciseV2(identifier)
    }

    override suspend fun getOfflineExerciseStatusV2(identifier: String): Boolean {
        if (!isFeatureReady(identifier, PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2)) {
            throw PolarOperationNotSupported()
        }
        return offlineExerciseV2Api.getOfflineExerciseStatusV2(identifier)
    }

    override fun listOfflineExercisesV2(identifier: String, directoryPath: String): Flow<PolarExerciseEntry> {
        if (!isFeatureReady(identifier, PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2)) {
            return flow { throw PolarOperationNotSupported() }
        }
        return offlineExerciseV2Api.listOfflineExercisesV2(identifier, directoryPath)
    }

    override suspend fun fetchOfflineExerciseV2(
        identifier: String,
        entry: PolarExerciseEntry
    ): PolarExerciseData {
        if (!isFeatureReady(identifier, PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2)) {
            throw PolarOperationNotSupported()
        }
        return offlineExerciseV2Api.fetchOfflineExerciseV2(identifier, entry)
    }

    override suspend fun removeOfflineExerciseV2(
        identifier: String,
        entry: PolarExerciseEntry
    ) {
        if (!isFeatureReady(identifier, PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2)) {
            throw PolarOperationNotSupported()
        }
        offlineExerciseV2Api.removeOfflineExerciseV2(identifier, entry)
    }

    override suspend fun isOfflineExerciseV2Supported(identifier: String): Boolean {
        if (!isFeatureReady(identifier, PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2)) {
            throw PolarOperationNotSupported()
        }
        return offlineExerciseV2Api.isOfflineExerciseV2Supported(identifier)
    }

    private fun setupDevice(session: BleDeviceSession) {
        val deviceId = session.polarDeviceId.ifEmpty { session.address }
        val requestedFeatures = PolarBleSdkFeature.entries.filter { features.contains(it) }

        val featureCheckJob = apiScope.launch {
            try {
                val discoveredServices = session.monitorServicesDiscovered(false).await()
                val results = requestedFeatures.map { feature ->
                    try { checkAndReportFeatureReadiness(session, discoveredServices, feature) }
                    catch (e: Throwable) { Pair(feature, false) }
                }
                val ready = results.filter { it.second }.map { it.first }
                val unavailable = results.filter { !it.second }.map { it.first }
                BleLogger.d(TAG, "Features readiness check completed. Ready: $ready, Unavailable: $unavailable")
                withContext(Dispatchers.Main) {
                    if (deviceId != null) callback?.bleSdkFeaturesReadiness(deviceId, ready, unavailable)
                }
            } catch (throwable: Throwable) {
                BleLogger.e(TAG, "Error while checking available features: $throwable")
                val ready = readyFeaturesMap[deviceId]?.toList() ?: emptyList()
                val unavailable = requestedFeatures.filter { !ready.contains(it) }
                withContext(Dispatchers.Main) {
                    if (deviceId != null) callback?.bleSdkFeaturesReadiness(deviceId, ready, unavailable)
                }
            }
        }
        deviceAvailableFeaturesJob[session.address] = featureCheckJob

        val hrClient = session.fetchClient(HR_SERVICE) as? BleHrClient
        if (hrClient != null) {
            if (deviceId != null) {
                callback?.bleSdkFeatureReady(deviceId, PolarBleSdkFeature.FEATURE_HR)
            }
            apiScope.launch {
                hrClient.observeHrNotifications(true)
                    .collect { data ->
                        withContext(Dispatchers.Main) {
                            if (deviceId != null) {
                                callback?.hrNotificationReceived(
                                    deviceId,
                                    PolarHrData.PolarHrSample(
                                        data.hrValue, 0, 0,
                                        data.rrsMs, data.rrPresent,
                                        data.sensorContact, data.sensorContactSupported
                                    )
                                )
                            }
                        }
                    }
            }
        }

        val dataMonitorJob = apiScope.launch {
            try {
                val discoveredServices = session.monitorServicesDiscovered(true).await()
                for (uuid in discoveredServices) {
                    val client = session.fetchClient(uuid) ?: continue
                    when (uuid) {
                        HR_SERVICE -> {
                            if (deviceId != null) withContext(Dispatchers.Main) {
                                callback?.bleSdkFeatureReady(deviceId, PolarBleSdkFeature.FEATURE_HR)
                            }
                            val bleHrClient = client as BleHrClient
                            launch {
                                bleHrClient.observeHrNotifications(true)
                                    .collect { data ->
                                        withContext(Dispatchers.Main) {
                                            if (deviceId != null) {
                                                callback?.hrNotificationReceived(
                                                    deviceId,
                                                    PolarHrData.PolarHrSample(
                                                        data.hrValue, 0, 0,
                                                        data.rrsMs, data.rrPresent,
                                                        data.sensorContact, data.sensorContactSupported
                                                    )
                                                )
                                            }
                                        }
                                    }
                            }
                        }
                        BleBattClient.BATTERY_SERVICE -> {
                            val bleBattClient = client as BleBattClient
                            launch {
                                bleBattClient.monitorBatteryStatus(true)
                                    .collect { level ->
                                        withContext(Dispatchers.Main) {
                                            if (deviceId != null) callback?.batteryLevelReceived(deviceId, level)
                                        }
                                    }
                            }
                            launch {
                                bleBattClient.monitorChargingStatus(true)
                                    .collect { state ->
                                        withContext(Dispatchers.Main) {
                                            if (deviceId != null) callback?.batteryChargingStatusReceived(deviceId, state)
                                        }
                                    }
                            }
                            launch {
                                bleBattClient.monitorPowerSourcesState(true)
                                    .collect { state ->
                                        withContext(Dispatchers.Main) {
                                            if (deviceId != null) callback?.powerSourcesStateReceived(deviceId, state)
                                        }
                                    }
                            }
                        }
                        BlePMDClient.PMD_SERVICE -> {
                            val blePMDClient = client as BlePMDClient
                            blePMDClient.clientReady(true)
                            val pmdFeature = blePMDClient.readFeature(true)
                            withContext(Dispatchers.Main) {
                                if (deviceId != null) {
                                    callback?.bleSdkFeatureReady(deviceId, PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)
                                    if (pmdFeature.contains(PmdMeasurementType.SDK_MODE)) {
                                        callback?.bleSdkFeatureReady(deviceId, PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE)
                                    }
                                }
                            }
                        }
                        BleDisClient.DIS_SERVICE -> {
                            val bleDisClient = client as BleDisClient
                            launch {
                                bleDisClient.observeDisInfo(true)
                                    .collect { pair ->
                                        withContext(Dispatchers.Main) {
                                            if (deviceId != null) callback?.disInformationReceived(deviceId, pair.first!!, pair.second!!)
                                        }
                                    }
                            }
                            launch {
                                bleDisClient.observeDisInfoWithKeysAsStrings(true)
                                    .collect { disInfo ->
                                        withContext(Dispatchers.Main) {
                                            if (deviceId != null) callback?.disInformationReceived(deviceId, disInfo)
                                        }
                                    }
                            }
                        }
                        BlePsFtpUtils.RFC77_PFTP_SERVICE -> {
                            val blePsftpClient = client as BlePsFtpClient
                            blePsftpClient.clientReady(true)
                            withContext(Dispatchers.Main) {
                                if (deviceId != null) callback?.bleSdkFeatureReady(deviceId, PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER)
                            }
                        }
                        HealthThermometer.HTS_SERVICE -> {
                            val bleHtsClient = client as BleHtsClient
                            launch {
                                bleHtsClient.observeHtsNotifications(true)
                                    .collect { data ->
                                        withContext(Dispatchers.Main) {
                                            if (deviceId != null) callback?.htsNotificationReceived(
                                                deviceId,
                                                PolarHealthThermometerData(data.temperatureCelsius, data.temperatureFahrenheit)
                                            )
                                        }
                                    }
                            }
                        }
                    }
                }
                BleLogger.d(TAG, "Service monitoring complete")
            } catch (throwable: Throwable) {
                BleLogger.e(TAG, "Error while monitoring session services: $throwable")
            }
        }
        deviceDataMonitorJob[session.address] = dataMonitorJob
    }

    private suspend fun checkAndReportFeatureReadiness(
        session: BleDeviceSession,
        discoveredServices: List<UUID>,
        feature: PolarBleSdkFeature
    ): Pair<PolarBleSdkFeature, Boolean> {
        val deviceId = session.polarDeviceId.ifEmpty { session.address }
        val available = when (feature) {
            PolarBleSdkFeature.FEATURE_HR -> isHeartRateFeatureAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_DEVICE_INFO -> isDeviceInfoFeatureAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_BATTERY_INFO -> isBatteryInfoFeatureAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING -> isOnlineStreamingAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING -> isOfflineRecordingAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP -> isPolarDeviceTimeFeatureAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE -> isSdkModeFeatureAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING -> isH10ExerciseFeatureAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2 -> isOfflineExerciseV2FeatureAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER -> isPsftpServiceAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_HTS -> isHealthThermometerFeatureAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_POLAR_LED_ANIMATION -> isLedAnimationFeatureAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE -> isPolarFirmwareUpdateFeatureAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA -> isActivityDataFeatureAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA -> isActivityDataFeatureAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_POLAR_TEMPERATURE_DATA -> isActivityDataFeatureAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_POLAR_TRAINING_DATA -> isActivityDataFeatureAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL -> isPsftpServiceAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_POLAR_FEATURES_CONFIGURATION_SERVICE -> isPolarFeaturesConfigurationServiceFeatureAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_POLAR_SPO2_TEST_DATA -> isPsftpServiceAvailable(discoveredServices, session)
        }
        if (available && deviceId != null) {
            withContext(Dispatchers.Main) {
                callback?.bleSdkFeatureReady(deviceId, feature)
                readyFeaturesMap.merge(deviceId, setOf(feature)) { existing, new -> existing + new }
            }
        }
        return Pair(feature, available)
    }

    private suspend fun isHealthThermometerFeatureAvailable(discoveredServices: List<UUID>, session: BleDeviceSession): Boolean {
        if (!discoveredServices.contains(HealthThermometer.HTS_SERVICE)) return false
        val bleHtsClient = session.fetchClient(HealthThermometer.HTS_SERVICE) as BleHtsClient? ?: return false
        return try { bleHtsClient.clientReady(true); true } catch (e: Throwable) { false }
    }

    private suspend fun isPolarDeviceTimeFeatureAvailable(discoveredServices: List<UUID>, session: BleDeviceSession): Boolean {
        if (!discoveredServices.contains(BlePsFtpUtils.RFC77_PFTP_SERVICE)) return false
        val blePsftpClient = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return false
        return try { blePsftpClient.clientReady(true); true } catch (e: Throwable) { false }
    }

    private suspend fun isBatteryInfoFeatureAvailable(discoveredServices: List<UUID>, session: BleDeviceSession): Boolean {
        if (!discoveredServices.contains(BleBattClient.BATTERY_SERVICE)) return false
        val bleBattClient = session.fetchClient(BleBattClient.BATTERY_SERVICE) as BleBattClient? ?: return false
        return try { bleBattClient.clientReady(true); true } catch (e: Throwable) { false }
    }

    private suspend fun isDeviceInfoFeatureAvailable(discoveredServices: List<UUID>, session: BleDeviceSession): Boolean {
        if (!discoveredServices.contains(BleDisClient.DIS_SERVICE)) return false
        val bleDisClient = session.fetchClient(BleDisClient.DIS_SERVICE) as BleDisClient? ?: return false
        return try { bleDisClient.clientReady(true); true } catch (e: Throwable) { false }
    }

    private suspend fun isHeartRateFeatureAvailable(discoveredServices: List<UUID>, session: BleDeviceSession): Boolean {
        if (!discoveredServices.contains(HR_SERVICE)) return false
        val bleHrClient = session.fetchClient(HR_SERVICE) as BleHrClient? ?: return false
        return try { bleHrClient.clientReady(true); true } catch (e: Throwable) { false }
    }

    private suspend fun isH10ExerciseFeatureAvailable(discoveredServices: List<UUID>, session: BleDeviceSession): Boolean {
        if (!discoveredServices.contains(BlePsFtpUtils.RFC77_PFTP_SERVICE) || !isRecordingSupported(session.polarDeviceType)) return false
        val blePsftpClient = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return false
        return try { blePsftpClient.clientReady(true); true } catch (e: Throwable) { false }
    }

    private fun isOfflineExerciseV2FeatureAvailable(discoveredServices: List<UUID>, session: BleDeviceSession): Boolean {
        return getFileSystemType(session.polarDeviceType) == FileSystemType.H10_FILE_SYSTEM
    }

    private suspend fun isSdkModeFeatureAvailable(discoveredServices: List<UUID>, session: BleDeviceSession): Boolean {
        if (!discoveredServices.contains(BlePMDClient.PMD_SERVICE)) return false
        val blePMDClient = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient? ?: return false
        return try {
            blePMDClient.clientReady(true)
            blePMDClient.readFeature(true).contains(PmdMeasurementType.SDK_MODE)
        } catch (e: Throwable) { false }
    }

    private suspend fun isOnlineStreamingAvailable(discoveredServices: List<UUID>, session: BleDeviceSession): Boolean {
        if (!discoveredServices.contains(BlePMDClient.PMD_SERVICE)) return false
        val blePMDClient = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient? ?: return false
        return try {
            if (discoveredServices.contains(HR_SERVICE)) {
                (session.fetchClient(HR_SERVICE) as? BleHrClient)?.clientReady(true)
            }
            blePMDClient.clientReady(true)
            true
        } catch (e: Throwable) { false }
    }

    private suspend fun isPsftpServiceAvailable(discoveredServices: List<UUID>, session: BleDeviceSession): Boolean {
        if (!discoveredServices.contains(BlePsFtpUtils.RFC77_PFTP_SERVICE)) return false
        val blePsftpClient = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return false
        return try { blePsftpClient.clientReady(true); true } catch (e: Throwable) { false }
    }

    private suspend fun isOfflineRecordingAvailable(discoveredServices: List<UUID>, session: BleDeviceSession): Boolean {
        if (!discoveredServices.contains(BlePMDClient.PMD_SERVICE) || !discoveredServices.contains(BlePsFtpUtils.RFC77_PFTP_SERVICE)) return false
        val blePMDClient = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient? ?: return false
        val blePsftpClient = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return false
        return try {
            blePMDClient.clientReady(true)
            blePsftpClient.clientReady(true)
            blePMDClient.readFeature(true).contains(PmdMeasurementType.OFFLINE_RECORDING)
        } catch (e: Throwable) { false }
    }

    private suspend fun isLedAnimationFeatureAvailable(discoveredServices: List<UUID>, session: BleDeviceSession): Boolean {
        if (!discoveredServices.contains(BlePMDClient.PMD_SERVICE) || !discoveredServices.contains(BlePsFtpUtils.RFC77_PFTP_SERVICE)) return false
        val blePMDClient = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient? ?: return false
        val blePsftpClient = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return false
        return try {
            blePMDClient.clientReady(true)
            blePsftpClient.clientReady(true)
            blePMDClient.readFeature(true).contains(PmdMeasurementType.SDK_MODE)
        } catch (e: Throwable) { false }
    }

    private suspend fun isPolarFirmwareUpdateFeatureAvailable(discoveredServices: List<UUID>, session: BleDeviceSession): Boolean {
        if (!discoveredServices.contains(BlePsFtpUtils.RFC77_PFTP_SERVICE) || !BlePolarDeviceCapabilitiesUtility.isFirmwareUpdateSupported(session.polarDeviceType)) return false
        val blePsftpClient = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return false
        return try { blePsftpClient.clientReady(true); true } catch (e: Throwable) { false }
    }

    private suspend fun isActivityDataFeatureAvailable(discoveredServices: List<UUID>, session: BleDeviceSession): Boolean {
        if (!discoveredServices.contains(BlePsFtpUtils.RFC77_PFTP_SERVICE) || !BlePolarDeviceCapabilitiesUtility.isActivityDataSupported(session.polarDeviceType)) return false
        val blePsftpClient = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return false
        return try { blePsftpClient.clientReady(true); true } catch (e: Throwable) { false }
    }

    private suspend fun isPolarFeaturesConfigurationServiceFeatureAvailable(discoveredServices: List<UUID>, session: BleDeviceSession): Boolean {
        if (!discoveredServices.contains(PFC_SERVICE)) return false
        val blePfcClient = session.fetchClient(PFC_SERVICE) as BlePfcClient? ?: return false
        return try { blePfcClient.clientReady(true); true } catch (e: Throwable) { false }
    }

    private fun tearDownDevice(session: BleDeviceSession) {
        val address = session.address
        if (deviceDataMonitorJob.containsKey(address)) {
            deviceDataMonitorJob[address]?.cancel()
            deviceDataMonitorJob.remove(address)
        }

        if (deviceAvailableFeaturesJob.containsKey(address)) {
            deviceAvailableFeaturesJob[address]?.cancel()
            deviceAvailableFeaturesJob.remove(address)
        }
        readyFeaturesMap.remove(session.polarDeviceId.ifEmpty { address })
    }

    private fun handleError(throwable: Throwable): Exception {
        if (throwable is BleDisconnected) {
            return PolarDeviceDisconnected()
        } else if (throwable is PftpResponseError) {
            val errorId = throwable.error
            val pftpError = PbPFtpError.forNumber(errorId)
            if (pftpError != null) return Exception(pftpError.toString())
        }
        return Exception(throwable)
    }

    override fun stateChanged(power: Boolean) {
        callback?.blePowerStateChanged(power)
    }

    private fun log(message: String) {
        logger?.message("" + message)
    }

    private fun logError(message: String) {
        logger?.message("Error: $message")
    }

    companion object {
        private const val TAG = "BDBleApiImpl"
        private var instance: BDBleApiImpl? = null

        @Throws(PolarBleSdkInstanceException::class, BleNotAvailableInDevice::class)
        fun getInstance(context: Context, features: Set<PolarBleSdkFeature>): BDBleApiImpl {
            val resolvedFeatures = if (features.isEmpty()) PolarBleSdkFeature.entries.toSet() else features
            return instance?.let {
                if (it.features == resolvedFeatures) {
                    it
                } else {
                    throw PolarBleSdkInstanceException("Attempt to create Polar BLE API with features " + resolvedFeatures + ". Instance with features " + instance!!.features + " already exists")
                }
            } ?: run {
                instance = BDBleApiImpl(context, resolvedFeatures)
                instance!!
            }
        }

        @androidx.annotation.VisibleForTesting
        internal fun clearInstance() {
            instance = null
        }
    }

    override suspend fun  getRSSIValue(identifier: String): Int {
        return PolarServiceClientUtils.getRSSIValue(identifier, listener)
    }
}