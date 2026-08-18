// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl

import android.content.Context
import android.os.Build
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
import com.polar.androidcommunications.api.ble.model.gatt.client.BleMdsClient
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
import com.polar.androidcommunications.api.ble.model.offlinerecording.OfflineRecordingError
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
import com.polar.sdk.api.DeviceTelemetryConfiguration
import com.polar.sdk.api.DeviceTelemetryEvent
import com.polar.sdk.api.DeviceTelemetrySupport
import com.polar.sdk.api.PolarDeviceTelemetryType
import com.polar.sdk.api.model.PolarExerciseSession
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallbackProvider
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.PolarDerivedMeasurementApi
import com.polar.sdk.api.PolarBleLowLevelApi
import com.polar.sdk.api.PolarBleTelemetryApi
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
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdClientDerivedAccDataToPolarDerivedAcc
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
import com.polar.sdk.impl.utils.PolarTimeUtils.javaCalendarToPbPftpSetSystemTime
import com.polar.sdk.impl.utils.PolarTimeUtils.pbLocalTimeToJavaLocalDateTime
import com.polar.sdk.impl.utils.PolarTimeUtils.pbLocalTimeToZonedDateTime
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.time.LocalDate
import java.time.LocalDateTime
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
import com.polar.sdk.impl.utils.PolarWatchFaceUtils
import com.polar.sdk.api.model.PolarWatchFaceComplication
import com.polar.sdk.api.model.PolarWatchFaceConfig
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
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The default implementation of the Polar API
 * @Suppress
 */
class BDBleApiImpl private constructor(context: Context, features: Set<PolarBleSdkFeature>) : PolarBleApi(features), BlePowerStateChangedCallback, PolarTrainingSessionApi,
    PolarBleLowLevelApi, PolarOfflineExerciseV2Api, PolarTestApi, PolarDerivedMeasurementApi, PolarBleTelemetryApi{

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
    private lateinit var offlineExerciseV2Api: PolarOfflineExerciseV2ApiImpl
    private lateinit var loggingApiImpl: PolarLoggingApiImpl
    private lateinit var telemetryApi: PolarTelemetryApiImpl
    private lateinit var activityApiImpl: PolarActivityApiImpl
    private lateinit var testApiImpl: PolarTestApiImpl
    private lateinit var sleepApiImpl: PolarSleepApiImpl
    private lateinit var trainingSessionApiImpl: PolarTrainingSessionApiImpl
    private lateinit var offlineRecordingApiImpl: PolarOfflineRecordingApiImpl
    private lateinit var telemetryAvailabilityMap: MutableMap<String, MutableList<PolarDeviceTelemetryType>?>

    init {
        val clients: MutableSet<Class<out BleGattBase>> = mutableSetOf()
        telemetryAvailabilityMap = mutableMapOf()
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
                PolarBleSdkFeature.FEATURE_WATCH_FACES_CONFIGURATION -> clients.add(BlePsFtpClient::class.java)
                PolarBleSdkFeature.FEATURE_TELEMETRY -> clients.add(BleMdsClient::class.java)
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
            loggingApiImpl = PolarLoggingApiImpl(it)
            activityApiImpl = PolarActivityApiImpl(it)
            testApiImpl = PolarTestApiImpl(it)
            sleepApiImpl = PolarSleepApiImpl(it)
            trainingSessionApiImpl = PolarTrainingSessionApiImpl(it)
            offlineRecordingApiImpl = PolarOfflineRecordingApiImpl(it)
        }

        listener?.let {
            telemetryApi = PolarTelemetryApiImpl(it)
        }

        logSdkInitialization()
    }

    override fun setMtu(mtu: Int) {
        logApiCall("setMtu", "mtu" to mtu)
        try {
            listener?.setPreferredMtu(mtu)
        } catch (e: BleInvalidMtu) {
            BleLogger.e(TAG, "Invalid MTU $mtu value given. Must be zero or positive.")
        }
    }

    override fun shutDown() {
       logApiCall("shutDown")
        apiScope.cancel()
        devicesStateMonitorJob = null
        listener?.shutDown()
        logger = null
        callback = null
        listener = null
        clearInstance()
    }

    override fun cleanup() {
       logApiCall("cleanup")
        devicesStateMonitorJob?.cancel()
        devicesStateMonitorJob = null
        listener?.removeAllSessions()
        telemetryAvailabilityMap = mutableMapOf()
        

    }

    override fun setPolarFilter(enable: Boolean) {
        logApiCall("setPolarFilter", "enable" to enable)
        if (enable) {
            listener?.setScanPreFilter(filter)
        } else {
            listener?.setScanPreFilter(null)
        }
    }

    override fun isFeatureReady(identifier: String, feature: PolarBleSdkFeature): Boolean {
        logApiCall("isFeatureReady")
        return try {
            return when (feature) {
                PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING -> {
                    PolarServiceClientUtils.sessionHrClientReady(identifier, listener)
                    PolarServiceClientUtils.sessionPmdClientReady(identifier, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_HR -> {
                    PolarServiceClientUtils.sessionHrClientReady(identifier, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_DEVICE_INFO -> {
                    PolarServiceClientUtils.sessionServiceReady(identifier, BleDisClient.DIS_SERVICE, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_BATTERY_INFO -> {
                    PolarServiceClientUtils.sessionServiceReady(identifier, BleBattClient.BATTERY_SERVICE, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING -> {
                    PolarServiceClientUtils.sessionPmdClientReady(identifier, listener)
                    PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP -> {
                    PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING -> {
                    val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
                    FileSystemType.H10_FILE_SYSTEM == getFileSystemType(session.polarDeviceType)
                }

                PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2 -> {
                    try {
                        val session = PolarServiceClientUtils.fetchSession(identifier, listener)
                        session?.let { checkOfflineExerciseV2Support(it) } ?: false
                    } catch (e: Throwable) {
                        false
                    }
                }

                PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE -> {
                    PolarServiceClientUtils.sessionPmdClientReady(identifier, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER -> {
                    PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_HTS -> {
                    PolarServiceClientUtils.sessionServiceReady(identifier, HealthThermometer.HTS_SERVICE, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_LED_ANIMATION -> {
                    PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE -> {
                    PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA -> {
                    val deviceType = getDeviceName(identifier).let {
                        if (it.startsWith("Polar ")) it.removePrefix("Polar ")
                        else it }.replace( Regex(" [0-9A-Fa-f]{8}$"), "").trim()
                    BlePolarDeviceCapabilitiesUtility.isActivityDataSupported(deviceType)
                }

                PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA -> {
                    PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_TEMPERATURE_DATA -> {
                    PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_TRAINING_DATA -> {
                    PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL -> {
                    PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_FEATURES_CONFIGURATION_SERVICE -> {
                    PolarServiceClientUtils.sessionPsPfcClientReady(identifier, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_SPO2_TEST_DATA -> {
                    PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_WATCH_FACES_CONFIGURATION -> {
                    PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
                    true
                }

                PolarBleSdkFeature.FEATURE_TELEMETRY -> {
                    PolarServiceClientUtils.sessionMdsClientReady(identifier, listener)
                    telemetryAvailabilityMap.getOrPut(identifier) { mutableListOf() }
                        ?.add(PolarDeviceTelemetryType.memfault_mds)
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
       logApiCall("setApiCallback")
        this.callback = callback
        listener?.let {
            callback.blePowerStateChanged(it.bleActive())
        }
    }

    override fun setApiLogger(logger: PolarBleApiLogger) {
        logApiCall("setApiLogger")
        this.logger = logger
        logSdkInitialization()
    }

    override fun setAutomaticReconnection(enable: Boolean) {
        logApiCall("setAutomaticReconnection", "enable" to enable)
        listener?.setAutomaticReconnection(enable)
    }

    private fun getAutomaticReconnection() : Boolean? {
        return listener?.getAutomaticReconnection();
    }

    override suspend fun setLocalTime(identifier: String, localTime: LocalDateTime) {
        logApiCall("setLocalTime", "identifier" to identifier, "time" to localTime)
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
        val utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = localDataTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        val pbTime = javaCalendarToPbPftpSetSystemTime(utcCalendar)
        client.query(PftpRequest.PbPFtpQuery.SET_SYSTEM_TIME_VALUE, pbTime.toByteArray())
    }

    @Deprecated(
        "Use getLocalTimeWithZone() instead to also get timezone",
        replaceWith = ReplaceWith("getLocalTimeWithZone(identifier)")
    )
    override suspend fun getLocalTime(identifier: String): LocalDateTime {
        logApiCall("getLocalTime", "identifier" to identifier)
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
        logApiCall("getLocalTimeWithZone", "identifier" to identifier)
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()

        BleLogger.d(TAG, "get local time and timezone from device $identifier")
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
        logApiCall("requestStreamSettings", "identifier" to identifier, "feature" to feature)
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
        logApiCall("requestFullStreamSettings", "identifier" to identifier, "feature" to feature)
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
        logApiCall("requestOfflineRecordingSettings", "identifier" to identifier, "feature" to feature)
        return offlineRecordingApiImpl.requestOfflineRecordingSettings(identifier, feature)
    }

    override suspend fun requestFullOfflineRecordingSettings(
        identifier: String,
        feature: PolarDeviceDataType
    ): PolarSensorSetting {
        logApiCall("requestFullOfflineRecordingSettings", "identifier" to identifier, "feature" to feature)
        return offlineRecordingApiImpl.requestFullOfflineRecordingSettings(identifier, feature)
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
        logApiCall("foregroundEntered")
        listener?.scanRestart()
    }

    override fun checkIfDeviceDisconnectedDueRemovedPairing(identifier: String): Pair<Boolean, Int> {
        logApiCall("checkIfDeviceDisconnectedDueRemovedPairing", "identifier" to identifier)
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
        logApiCall("autoConnectToDevice")
        log("[autoConnect] startAutoConnectToDevice: rssi=$rssiLimit service=${service ?: "nil"} deviceType=${polarDeviceType ?: "nil"}")
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
        if (list.isEmpty()) {
            log("[autoConnect] search complete — no matching device found")
            return
        }
        log("[autoConnect] connecting to best candidate: device=${list[0].polarDeviceId} rssi=${list[0].rssi}")
        openConnection(list[0])
        log("[autoConnect] auto connect search complete")
    }

    override suspend fun autoConnectToDevice(
        rssiLimit: Int,
        service: String?,
        polarDeviceType: String?
    ) {
        logApiCall("autoConnectToDevice")
        autoConnectToDevice(rssiLimit, service, 2, TimeUnit.SECONDS, polarDeviceType)
    }

    override fun getDeviceName(deviceId: String): String {
        logApiCall("getDeviceName")
        return fetchSession(identifier = deviceId, listener)?.name ?: ""
    }

    @Throws(PolarInvalidArgument::class)
    override fun connectToDevice(identifier: String) {
        logApiCall("connectToDevice", "identifier" to identifier)
        val session = fetchSession(identifier, listener)
        if (session == null || session.sessionState == DeviceSessionState.SESSION_CLOSED || 
            session.sessionState == DeviceSessionState.SESSION_OPEN_PARK) {
            if (connectSubscriptions.containsKey(identifier)) {
                connectSubscriptions[identifier]?.cancel()
                connectSubscriptions.remove(identifier)
            }
            if (session != null) {
                // For SESSION_OPEN_PARK (parked) devices, use openSessionDirect to trigger reconnection
                // This calls connectionHandler.connectDevice() which sends CONNECT_DEVICE action
                listener?.openSessionDirect(session)
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
        logApiCall("disconnectFromDevice", "identifier" to identifier)
        BleLogger.w(TAG, "disconnectFromDevice requested for $identifier")
        val session = fetchSession(identifier, listener)
        session?.let {
            BleLogger.w(TAG, "disconnectFromDevice session state for $identifier is ${session.sessionState}")
            if (session.sessionState == DeviceSessionState.SESSION_OPEN ||
                session.sessionState == DeviceSessionState.SESSION_OPENING ||
                session.sessionState == DeviceSessionState.SESSION_OPEN_PARK
            ) {
                BleLogger.w(TAG, "disconnectFromDevice will close session for $identifier. Stack: ${Throwable().stackTrace.take(6).joinToString(" <- ")}")
                listener?.closeSessionDirect(session)
            }
        }
        if (connectSubscriptions.containsKey(identifier)) {
            connectSubscriptions[identifier]?.cancel()
            connectSubscriptions.remove(identifier)
        }
        telemetryAvailabilityMap = mutableMapOf()
    }

    override suspend fun startRecording(
        identifier: String,
        exerciseId: String,
        interval: PolarH10OfflineExerciseApi.RecordingInterval?,
        type: PolarH10OfflineExerciseApi.SampleType
    ) {
        logApiCall(
            "startRecording",
            "identifier" to identifier,
            "exerciseId" to exerciseId,
            "interval" to (interval?.value ?: PolarH10OfflineExerciseApi.RecordingInterval.INTERVAL_1S.value),
            "sampleType" to type
        )
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
        logApiCall("stopRecording", "identifier" to identifier)
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
        logApiCall("requestRecordingStatus", "identifier" to identifier)
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        if (!isRecordingSupported(session.polarDeviceType)) throw PolarOperationNotSupported()
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        return try {
            val byteArrayOutputStream = client.query(PftpRequest.PbPFtpQuery.REQUEST_RECORDING_STATUS_VALUE, null)
            val bytes = byteArrayOutputStream.toByteArray()
            if (bytes.isEmpty()) {
                log("request recording status for $identifier returned empty data, defaulting to not recording")
                return androidx.core.util.Pair(false, "")
            }
            val result = PbRequestRecordingStatusResult.parseFrom(bytes)
            androidx.core.util.Pair(
                result.recordingOn,
                if (result.hasSampleDataIdentifier()) result.sampleDataIdentifier else ""
            )
        } catch (throwable: Throwable) {
            throw handleError(throwable)
        }
    }

    override fun listOfflineRecordings(identifier: String): Flow<PolarOfflineRecordingEntry> {
        logApiCall("listOfflineRecordings", "identifier" to identifier)
        return offlineRecordingApiImpl.listOfflineRecordings(identifier)
    }


    override fun listExercises(identifier: String): Flow<PolarExerciseEntry> {
        logApiCall("listExercises", "identifier" to identifier)
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
        logApiCall("fetchExercise", "identifier" to identifier, "entry.path" to entry.path, "entry.date" to entry.date)
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
        logApiCall("getOfflineRecord", "identifier" to identifier, "entry.type" to entry.type, "entry.size" to entry.size, "entry.date" to entry.date, "secret" to (secret != null))
        return offlineRecordingApiImpl.getOfflineRecord(identifier, entry, secret)
    }

    override fun getOfflineRecordWithProgress(
        identifier: String,
        entry: PolarOfflineRecordingEntry,
        secret: PolarRecordingSecret?
    ): Flow<PolarOfflineRecordingResult> {
        logApiCall("getOfflineRecordWithProgress", "identifier" to identifier, "entry.type" to entry.type, "entry.size" to entry.size, "entry.date" to entry.date, "secret" to (secret != null))
        return offlineRecordingApiImpl.getOfflineRecordWithProgress(identifier, entry, secret)
    }


    @Suppress("OVERRIDE_DEPRECATION")
    override fun listSplitOfflineRecordings(identifier: String): Flow<PolarOfflineRecordingEntry> {
        logApiCall("listSplitOfflineRecordings", "identifier" to identifier)
        return offlineRecordingApiImpl.listSplitOfflineRecordings(identifier)
    }


    @Deprecated("Use getOfflineRecordWithProgress method instead")
    override suspend fun getSplitOfflineRecord(
        identifier: String,
        entry: PolarOfflineRecordingEntry,
        secret: PolarRecordingSecret?
    ): PolarOfflineRecordingData {
        logApiCall("getSplitOfflineRecord", "identifier" to identifier)
        return offlineRecordingApiImpl.getSplitOfflineRecord(identifier, entry, secret)
    }


    override suspend fun removeExercise(identifier: String, entry: PolarExerciseEntry) {
        logApiCall("removeExercise", "identifier" to identifier)
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
        logApiCall("deleteTrainingSession", "identifier" to identifier)
        trainingSessionApiImpl.deleteTrainingSession(identifier, reference)
    }

    override suspend fun removeOfflineRecord(
        identifier: String,
        entry: PolarOfflineRecordingEntry
    ) {
        logApiCall("removeOfflineRecord", "identifier" to identifier, "entry.type" to entry.type, "entry.size" to entry.size, "entry.date" to entry.date)
        offlineRecordingApiImpl.removeOfflineRecord(identifier, entry)
    }


    override fun searchForDevice(): Flow<PolarDeviceInfo>  {
       logApiCall("searchForDevice")
        return searchForDevice(withDeviceNameFilterPrefix = null)
    }

    override fun searchForDevice(withDeviceNameFilterPrefix: String?): Flow<PolarDeviceInfo> {
        logApiCall("searchForDevice", "withDeviceNameFilterPrefix" to withDeviceNameFilterPrefix)
        val l = listener ?: return flow { throw PolarBleSdkInstanceException("PolarBleApi instance is shutdown") }
        return l.search(true)
            .filter { bleDeviceSession: BleDeviceSession ->
                // Only show: actively connected (OPEN), waiting to reconnect (OPEN_PARK), or fully closed (CLOSED)
                // Exclude: actively connecting (OPENING) or disconnecting (CLOSING) - these are transient states
                val state = bleDeviceSession.sessionState
                val isValidState = state == DeviceSessionState.SESSION_CLOSED || 
                                   state == DeviceSessionState.SESSION_OPEN ||
                                   state == DeviceSessionState.SESSION_OPEN_PARK
                return@filter isValidState
            }
            .filter { bleDeviceSession: BleDeviceSession ->
                // CRITICAL: validate device has real data - filter out phantom/stale entries with no name
                val name = bleDeviceSession.advertisementContent.name ?: ""
                val polarDeviceId = bleDeviceSession.polarDeviceId ?: ""
                val hasValidName = name.isNotEmpty()
                val hasValidId = polarDeviceId.isNotEmpty()
                val hasValidAddress = bleDeviceSession.address != null
                
                return@filter hasValidName && hasValidId && hasValidAddress && 
                        (withDeviceNameFilterPrefix == null || name.startsWith(withDeviceNameFilterPrefix))
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

    override fun startListenForPolarHrBroadcasts(identifiers: Set<String>?): Flow<PolarHrBroadcastData> {
        logApiCall("startListenForPolarHrBroadcasts")
        val l = listener ?: return flow { throw PolarBleSdkInstanceException("PolarBleApi instance is shutdown") }
        BleLogger.d(TAG, "Start Hr broadcast listener. Filtering: ${identifiers != null}")
        return l.search(false)
            .filter { bleDeviceSession: BleDeviceSession ->
                (identifiers == null || identifiers.contains(bleDeviceSession.polarDeviceId)) &&
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
        logApiCall("getDiskSpace", "identifier" to identifier)
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
        logApiCall("setLedConfig", "identifier" to identifier, "ledConfig" to ledConfig)
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
        logApiCall("listRestApiServices", "identifier" to identifier)
        val byteArray = getFile(identifier = identifier, path = "/REST/SERVICE.API")
        val map: Map<String, Any> = Gson().fromJson(byteArray.toString(Charsets.UTF_8), object: TypeToken<Map<String,Any>>() {}.type)
        return PolarDeviceRestApiServices(map)
    }

    override suspend fun getRestApiDescription(identifier: String, path: String): PolarDeviceRestApiServiceDescription {
        logApiCall("getRestApiDescription", "identifier" to identifier)
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
        logApiCall("getFile", "identifier" to identifier, "path" to path)
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

    override suspend fun exportDeviceLogs(identifier: String): List<PolarDeviceLog> {
        logApiCall("exportDeviceLogs", "identifier" to identifier)
        return loggingApiImpl.exportDeviceLogs(identifier)
    }

    override fun <T : RestApiEventPayload>receiveRestApiEvents(identifier: String, mapper:((jsonString: String) -> T)): Flow<List<T>> {
        logApiCall("receiveRestApiEvents", "identifier" to identifier)
        return flow {
            val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
            val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                ?: throw PolarServiceNotAvailable()
            client.receiveRestApiEvents(identifier = identifier)
                .collect { list -> emit(list.map(mapper)) }
        }
    }

    override fun observeDeviceToHostNotifications(identifier: String): Flow<PolarD2HNotificationData> {
        logApiCall("observeDeviceToHostNotifications", "identifier" to identifier)
        return flow {
            val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
            val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                ?: throw PolarServiceNotAvailable()
            client.observeDeviceToHostNotifications(identifier = identifier)
                .collect { emit(it) }
        }
    }

    override suspend fun putNotification(identifier: String, notification: String, path: String) {
        logApiCall("putNotification", "identifier" to identifier)
        pFtpPutOperation(identifier = identifier, path = path, data = notification.toByteArray())
    }

    private suspend fun pFtpPutOperation(identifier: String, path: String, data: ByteArray) {
        pFtpWriteOperation(identifier = identifier, listener, data = data, path = path, tag = TAG)
    }

    @Deprecated("Use method doFactoryReset(identifier: String) instead.")
    override suspend fun doFactoryReset(identifier: String, preservePairingInformation: Boolean) {
        logApiCall("doFactoryReset", "identifier" to identifier, "preservePairingInformation" to preservePairingInformation)
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
        logApiCall("doFactoryReset", "identifier" to identifier)
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val params = PftpNotification.PbPFtpFactoryResetParams.newBuilder()
        params.sleep = false
        BleLogger.d(TAG, "send factory reset notification to device $identifier")
        client.sendNotification(PftpNotification.PbPFtpHostToDevNotification.RESET.ordinal, params.build().toByteArray())
    }

    override suspend fun doRestart(identifier: String) {
        logApiCall("doRestart", "identifier" to identifier)
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val params = PftpNotification.PbPFtpFactoryResetParams.newBuilder()
        params.sleep = false
        params.doFactoryDefaults = false
        BleLogger.d(TAG, "send restart notification to device $identifier")
        try {
            client.sendNotification(PftpNotification.PbPFtpHostToDevNotification.RESET.ordinal, params.build().toByteArray())
        } catch (e: BleDisconnected) {
            BleLogger.d(TAG, "doRestart() gattDisconnected")
        }
    }

    override suspend fun doFirstTimeUse(identifier: String, ftuConfig: PolarFirstTimeUseConfig) {
        logApiCall("doFirstTimeUse", "identifier" to identifier)
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
            BleLogger.d(TAG, "doFirstTimeUse(identifier: $identifier): user physical data written")
            client.write(userIdBuilder.build().toByteArray(), ByteArrayInputStream(userIdData)).collect {}
            BleLogger.d(TAG, "doFirstTimeUse(identifier: $identifier): user data written")
            BleLogger.d(TAG, "doFirstTimeUse(identifier: $identifier): completed")
            sendTerminateAndStopSyncNotifications(identifier)
        } catch (error: Throwable) {
            BleLogger.e(TAG, "doFirstTimeUse(identifier: $identifier): error $error")
            sendTerminateAndStopSyncNotifications(identifier)
            throw error
        }
    }

    override suspend fun isFtuDone(identifier: String): Boolean {
        logApiCall("isFtuDone", "identifier" to identifier)
        BleLogger.d(TAG, "Check if FTU has been done for device $identifier")
        val byteArray = getFile(identifier, UserIdentifierType.USER_IDENTIFIER_FILENAME)
        return try {
            UserIds.PbUserIdentifier.parseFrom(byteArray).hasMasterIdentifier()
        } catch (e: Exception) {
            BleLogger.e(TAG, "Failed to check if the first time use has been done: $e")
            throw e
        }
    }

    override suspend fun getUserPhysicalConfiguration(identifier: String): PolarPhysicalConfiguration? {
        logApiCall("getUserPhysicalConfiguration", "identifier" to identifier)
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
        logApiCall("startExercise", "identifier" to identifier, "profile" to profile)
        trainingSessionApiImpl.startExercise(identifier, profile)
    }

    override suspend fun pauseExercise(identifier: String) {
        logApiCall("pauseExercise", "identifier" to identifier)
        trainingSessionApiImpl.pauseExercise(identifier)
    }

    override suspend fun resumeExercise(identifier: String) {
        logApiCall("resumeExercise", "identifier" to identifier)
        trainingSessionApiImpl.resumeExercise(identifier)
    }

    override suspend fun stopExercise(identifier: String) {
        logApiCall("stopExercise", "identifier" to identifier)
        trainingSessionApiImpl.stopExercise(identifier)
    }

    override suspend fun getExerciseStatus(identifier: String): PolarExerciseSession.ExerciseInfo {
        logApiCall("getExerciseStatus", "identifier" to identifier)
        return trainingSessionApiImpl.getExerciseStatus(identifier)
    }

    override fun observeExerciseStatus(identifier: String): Flow<PolarExerciseSession.ExerciseInfo> {
        logApiCall("observeExerciseStatus", "identifier" to identifier)
        return trainingSessionApiImpl.observeExerciseStatus(identifier)
    }


    override suspend fun readFile(identifier: String, filePath: String): ByteArray? {
        logApiCall("readFile", "identifier" to identifier, "filePath" to filePath)
        return PolarFileUtils.readFile(identifier, filePath, listener, TAG)
    }

    override suspend fun writeFile(identifier: String, filePath: String, fileData: ByteArray) {
        logApiCall("writeFile", "identifier" to identifier, "filePath" to filePath, "fileData" to fileData)
        PolarFileUtils.writeFile(identifier, filePath, listener, fileData, TAG)
    }

    override suspend fun deleteFileOrDirectory(identifier: String, filePath: String) {
        logApiCall("deleteFileOrDirectory", "identifier" to identifier, "filePath" to filePath)
        PolarFileUtils.removeFileOrDirectory(identifier, filePath, listener, TAG)
    }

    override suspend fun getFileList(identifier: String, filePath: String, recurseDeep: Boolean): List<String> {
        logApiCall("getFileList", "identifier" to identifier, "directoryPath" to filePath, "recurseDeep" to recurseDeep)
        return PolarFileUtils.getFileList(identifier, filePath, recurseDeep, listener, TAG)
    }

    override suspend fun createFolder(identifier: String, folderPath: String) {
        logApiCall("createFolder", "identifier" to identifier, "folderPath" to folderPath)
        PolarFileUtils.createFolder(identifier, folderPath, listener, TAG)
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
        logApiCall("setWarehouseSleep", "identifier" to identifier)
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val params = PftpNotification.PbPFtpFactoryResetParams.newBuilder()
        params.sleep = true
        params.doFactoryDefaults = true
        BleLogger.d(TAG, "Setting warehouse sleep to true, device: $identifier.")
        client.sendNotification(PftpNotification.PbPFtpHostToDevNotification.RESET.ordinal, params.build().toByteArray())
    }

    override suspend fun setHibernateMode(identifier: String) {
        logApiCall("setHibernateMode", "identifier" to identifier)
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val params = PftpNotification.PbPFtpFactoryResetParams.newBuilder()
        params.sleep = true
        params.doFactoryDefaults = false
        params.hibernate = true
        BleLogger.d(TAG, "send hibernate notification to device $identifier")
        client.sendNotification(PftpNotification.PbPFtpHostToDevNotification.RESET.ordinal, params.build().toByteArray())
    }

    override suspend fun turnDeviceOff(identifier: String) {
        logApiCall("turnDeviceOff", "identifier" to identifier)
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val params = PftpNotification.PbPFtpFactoryResetParams.newBuilder()
        params.sleep = true
        params.doFactoryDefaults = false
        BleLogger.d(TAG, "Turn off device $identifier.")
        client.sendNotification(PftpNotification.PbPFtpHostToDevNotification.RESET.ordinal, params.build().toByteArray())
        telemetryAvailabilityMap.getOrPut(identifier) { mutableListOf() }
            ?.remove(PolarDeviceTelemetryType.memfault_mds)
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
                        val deviceAddress = bleSession.address ?: ""
                        val deviceId = bleSession.polarDeviceId.ifEmpty {
                            if (deviceAddress.isNotEmpty()) deviceAddress else bleSession.name
                        }
                        val info = if (deviceId.isNotEmpty() && deviceAddress.isNotEmpty()) {
                            PolarDeviceInfo(deviceId, deviceAddress, bleSession.rssi, bleSession.name, true, hasSAGRFCFileSystem = hasSAGRFCFileSystem)
                        } else {
                            null
                        }
                        when (state) {
                            DeviceSessionState.SESSION_OPEN -> {
                                withContext(Dispatchers.Main) {
                                    if (info != null) {
                                        callback?.deviceConnected(info)
                                    } else {
                                        BleLogger.w(TAG, "SESSION_OPEN callback skipped due missing info: id='$deviceId' addr='$deviceAddress' name='${bleSession.name}'")
                                    }
                                }
                                setupDevice(bleSession)
                            }
                            DeviceSessionState.SESSION_CLOSED -> {
                                if (bleSession.previousState == DeviceSessionState.SESSION_OPEN ||
                                    bleSession.previousState == DeviceSessionState.SESSION_OPENING ||
                                    bleSession.previousState == DeviceSessionState.SESSION_OPEN_PARK ||
                                    bleSession.previousState == DeviceSessionState.SESSION_CLOSING) {
                                    withContext(Dispatchers.Main) {
                                        if (info != null) {
                                            callback?.deviceDisconnected(info)
                                        } else {
                                            BleLogger.w(TAG, "SESSION_CLOSED callback skipped due missing info: id='$deviceId' addr='$deviceAddress' name='${bleSession.name}'")
                                        }
                                    }
                                    telemetryAvailabilityMap.getOrPut(deviceId as String) { mutableListOf() }
                                        ?.remove(PolarDeviceTelemetryType.memfault_mds)
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
                                withContext(Dispatchers.Main) {
                                    if (info != null) {
                                        callback?.deviceConnecting(info)
                                    } else {
                                        BleLogger.w(TAG, "SESSION_OPENING callback skipped due missing info: id='$deviceId' addr='$deviceAddress' name='${bleSession.name}'")
                                    }
                                }
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
        logApiCall("startOfflineRecording", "identifier" to identifier, "feature" to feature, "settings" to settings, "secret" to (secret != null))
        offlineRecordingApiImpl.startOfflineRecording(identifier, feature, settings, secret)
    }

    override suspend fun stopOfflineRecording(identifier: String, feature: PolarDeviceDataType) {
        logApiCall("stopOfflineRecording", "identifier" to identifier, "feature" to feature)
        offlineRecordingApiImpl.stopOfflineRecording(identifier, feature)
    }

    override suspend fun getOfflineRecordingStatus(identifier: String): List<PolarDeviceDataType> {
        logApiCall("getOfflineRecordingStatus", "identifier" to identifier)
        return offlineRecordingApiImpl.getOfflineRecordingStatus(identifier)
    }

    override suspend fun setOfflineRecordingTrigger(identifier: String, trigger: PolarOfflineRecordingTrigger, secret: PolarRecordingSecret?) {
        logApiCall("setOfflineRecordingTrigger", "identifier" to identifier, "trigger" to trigger, "secret" to (secret != null))
        offlineRecordingApiImpl.setOfflineRecordingTrigger(identifier, trigger, secret)
    }

    override suspend fun getOfflineRecordingTriggerSetup(identifier: String): PolarOfflineRecordingTrigger {
        logApiCall("getOfflineRecordingTriggerSetup", "identifier" to identifier)
        return offlineRecordingApiImpl.getOfflineRecordingTriggerSetup(identifier)
    }

    override suspend fun requestDerivedMeasurementGroupIds(
        identifier: String,
        sourceType: PolarDeviceDataType
    ): Set<Int> {
        logApiCall("requestDerivedMeasurementGroupIds", "identifier" to identifier)
        return offlineRecordingApiImpl.requestDerivedMeasurementGroupIds(identifier, sourceType)
    }

    override suspend fun requestDerivedMeasurementSettingsGroup(
        identifier: String,
        groupId: Int
    ): PolarDerivedMeasurementSettingsGroup {
        logApiCall("requestDerivedMeasurementSettingsGroup", "identifier" to identifier)
        return offlineRecordingApiImpl.requestDerivedMeasurementSettingsGroup(identifier, groupId)
    }

    override suspend fun startDerivedOfflineRecording(
        identifier: String,
        settings: PolarDerivedMeasurementSettings,
        secret: PolarRecordingSecret?
    ) {
        logApiCall("startDerivedOfflineRecording", "identifier" to identifier)
        offlineRecordingApiImpl.startDerivedOfflineRecording(identifier, settings, secret)
    }

    override suspend fun stopDerivedOfflineRecording(identifier: String) {
        logApiCall("stopDerivedOfflineRecording", "identifier" to identifier)
        offlineRecordingApiImpl.stopDerivedOfflineRecording(identifier)
    }


    override fun startHrStreaming(identifier: String): Flow<PolarHrData> {
        logApiCall("startHrStreaming", "identifier" to identifier)
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
                    hrNotificationData.rrs,
                    hrNotificationData.rrsMs, hrNotificationData.rrPresent,
                    hrNotificationData.sensorContact, hrNotificationData.sensorContactSupported
                )
                PolarHrData(listOf(sample))
            }
    }

    override suspend fun stopHrStreaming(identifier: String) {
        logApiCall("stopHrStreaming", "identifier" to identifier)
        val session = PolarServiceClientUtils.sessionServiceReady(identifier, HR_SERVICE, listener)
        val bleHrClient = session.fetchClient(HR_SERVICE) as BleHrClient?
            ?: throw PolarServiceNotAvailable()
        BleLogger.d(TAG, "Stop heart rate online streaming. Device: $identifier")
        bleHrClient.stopObserveHrNotifications(true)
    }

    override fun startEcgStreaming(identifier: String, sensorSetting: PolarSensorSetting): Flow<PolarEcgData> {
        logApiCall("startEcgStreaming", "identifier" to identifier, "settings" to sensorSetting)
        return startStreaming(identifier, PmdMeasurementType.ECG, sensorSetting, observer = { client: BlePMDClient ->
            client.monitorEcgNotifications(true)
                .map { ecgData: EcgData -> PolarDataUtils.mapPmdClientEcgDataToPolarEcg(ecgData) }
        })
    }

    override fun startAccStreaming(identifier: String, sensorSetting: PolarSensorSetting): Flow<PolarAccelerometerData> {
        logApiCall("startAccStreaming", "identifier" to identifier, "settings" to sensorSetting)
        return startStreaming(identifier, PmdMeasurementType.ACC, sensorSetting, observer = { client: BlePMDClient ->
            client.monitorAccNotifications(true)
                .map { accData: AccData -> mapPmdClientAccDataToPolarAcc(accData) }
        })
    }

    override fun startPpgStreaming(identifier: String, sensorSetting: PolarSensorSetting): Flow<PolarPpgData> {
        logApiCall("startPpgStreaming", "identifier" to identifier, "settings" to sensorSetting)
        return startStreaming(identifier, PmdMeasurementType.PPG, sensorSetting, observer = { client: BlePMDClient ->
            client.monitorPpgNotifications(true)
                .map { ppgData: PpgData -> mapPMDClientPpgDataToPolarPpg(ppgData) }
        })
    }

    override fun startPpiStreaming(identifier: String): Flow<PolarPpiData> {
        logApiCall("startPpiStreaming", "identifier" to identifier)
        return startStreaming(identifier, PmdMeasurementType.PPI, PolarSensorSetting(emptyMap())) { client: BlePMDClient ->
            client.monitorPpiNotifications(true)
                .map { ppiData: PpiData -> mapPMDClientPpiDataToPolarPpiData(ppiData) }
        }
    }

    override fun startMagnetometerStreaming(identifier: String, sensorSetting: PolarSensorSetting): Flow<PolarMagnetometerData> {
        logApiCall("startMagnetometerStreaming", "identifier" to identifier, "settings" to sensorSetting)
        return startStreaming(identifier, PmdMeasurementType.MAGNETOMETER, sensorSetting, observer = { client: BlePMDClient ->
            client.monitorMagnetometerNotifications(true)
                .map { mag: MagData -> mapPmdClientMagDataToPolarMagnetometer(mag) }
        })
    }

    override fun startGyroStreaming(identifier: String, sensorSetting: PolarSensorSetting): Flow<PolarGyroData> {
        logApiCall("startGyroStreaming", "identifier" to identifier, "settings" to sensorSetting)
        return startStreaming(identifier, PmdMeasurementType.GYRO, sensorSetting, observer = { client: BlePMDClient ->
            client.monitorGyroNotifications(true)
                .map { gyro: GyrData -> mapPmdClientGyroDataToPolarGyro(gyro) }
        })
    }

    override fun startPressureStreaming(identifier: String, sensorSetting: PolarSensorSetting): Flow<PolarPressureData> {
        logApiCall("startPressureStreaming", "identifier" to identifier, "settings" to sensorSetting)
        return startStreaming(identifier, PmdMeasurementType.PRESSURE, sensorSetting) { client: BlePMDClient ->
            client.monitorPressureNotifications(true)
                .map { pressure: PressureData -> mapPmdClientPressureDataToPolarPressure(pressure) }
        }
    }

    override fun startLocationStreaming(identifier: String, sensorSetting: PolarSensorSetting): Flow<PolarLocationData> {
        logApiCall("startLocationStreaming", "identifier" to identifier)
        return startStreaming(identifier, PmdMeasurementType.LOCATION, sensorSetting) { client: BlePMDClient ->
            client.monitorLocationNotifications(true)
                .map { gnssLocationData: GnssLocationData -> mapPMDClientLocationDataToPolarLocationData(gnssLocationData) }
        }
    }

    override fun startTemperatureStreaming(identifier: String, sensorSetting: PolarSensorSetting): Flow<PolarTemperatureData> {
        logApiCall("startTemperatureStreaming", "identifier" to identifier, "settings" to sensorSetting)
        return startStreaming(identifier, PmdMeasurementType.TEMPERATURE, sensorSetting) { client: BlePMDClient ->
            client.monitorTemperatureNotifications(true)
                .map { temperature: TemperatureData -> mapPmdClientTemperatureDataToPolarTemperature(temperature) }
        }
    }

    override fun startSkinTemperatureStreaming(identifier: String, sensorSetting: PolarSensorSetting): Flow<PolarTemperatureData> {
        logApiCall("startSkinTemperatureStreaming", "identifier" to identifier, "settings" to sensorSetting)
        return startStreaming(identifier, PmdMeasurementType.SKIN_TEMP, sensorSetting) { client: BlePMDClient ->
            client.monitorSkinTemperatureNotifications(true)
                .map { skinTemperature: SkinTemperatureData -> mapPmdClientSkinTemperatureDataToPolarTemperatureData(skinTemperature) }
        }
    }

    override fun stopStreaming(identifier: String, type: PmdMeasurementType) {
        logApiCall("stopStreaming", "identifier" to identifier)
        val session = PolarServiceClientUtils.sessionPmdClientReady(identifier, listener)
        val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient?
        if (client != null) {
            stopPmdStreaming(session, client, type)
        }
    }

    override suspend fun enableSDKMode(identifier: String) {
        logApiCall("enableSDKMode", "identifier" to identifier)
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
        logApiCall("disableSDKMode", "identifier" to identifier)
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
        logApiCall("isSDKModeEnabled", "identifier" to identifier)
        val session = PolarServiceClientUtils.sessionPmdClientReady(identifier, listener)
        val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient?
            ?: throw PolarServiceNotAvailable()
        return client.isSdkModeEnabled() != PmdSdkMode.DISABLED
    }

    override suspend fun getAvailableOfflineRecordingDataTypes(identifier: String): Set<PolarDeviceDataType> {
        logApiCall("getAvailableOfflineRecordingDataTypes", "identifier" to identifier)
        return offlineRecordingApiImpl.getAvailableOfflineRecordingDataTypes(identifier)
    }


    override suspend fun getAvailableOnlineStreamDataTypes(identifier: String): Set<PolarDeviceDataType> {
        logApiCall("getAvailableOnlineStreamDataTypes", "identifier" to identifier)
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
        logApiCall("getAvailableHRServiceDataTypes", "identifier" to identifier)
        val session = PolarServiceClientUtils.sessionServiceReady(identifier, HR_SERVICE, listener)
        val bleHrClient = session.fetchClient(HR_SERVICE) as BleHrClient?
        val deviceData: MutableSet<PolarDeviceDataType> = mutableSetOf()
        if (bleHrClient != null && bleHrClient.isServiceDiscovered) {
            deviceData.add(PolarDeviceDataType.HR)
        }
        return deviceData
    }

    override suspend fun getLogConfig(identifier: String): LogConfig {
        logApiCall("getLogConfig", "identifier" to identifier)
        return loggingApiImpl.getLogConfig(identifier)
    }

    override suspend fun setLogConfig(identifier: String, logConfig: LogConfig) {
        logApiCall("setLogConfig", "identifier" to identifier, "logConfig" to logConfig)
        loggingApiImpl.setLogConfig(identifier, logConfig)
    }

    override fun updateFirmware(identifier: String, firmwareUrl: String): Flow<FirmwareUpdateStatus> = flow {
        logApiCall("updateFirmware", "identifier" to identifier, "firmwareUrl" to firmwareUrl)
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
                backupManager.restoreBackup(identifier, backupList, listener)
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
                    backupManager.restoreBackup(identifier, backupList, listener)
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
        logApiCall("updateFirmware", "identifier" to identifier)
        return updateFirmware(identifier, firmwareUrl = "")
    }

    override fun checkFirmwareUpdate(identifier: String): Flow<CheckFirmwareUpdateStatus> = flow {
        logApiCall("checkFirmwareUpdate", "identifier" to identifier)
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
                BleLogger.e(TAG, "Bad request to firmware update API: $errorBody")
                Triple(null, null, FirmwareUpdateStatus.FwUpdateFailed("Bad request to firmware update API: $errorBody"))
            }
            else -> Triple(null, null, FirmwareUpdateStatus.FwUpdateFailed("Unexpected response code: ${response.code()}"))
        }
    }

    private suspend fun getFirmwareUpdatePackage(firmwareUrl: String): List<Pair<String, ByteArray>> {
        return if (firmwareUrl.startsWith("file://")) {
            val file = File(URI.create(firmwareUrl).path)
            BleLogger.d(TAG, "FW package read from local file: ${sanitizePathForLog(file.absolutePath)}, size: ${file.length()} bytes")
            parseFirmwareZip(file.readBytes())
        } else {
            val httpClient = RetrofitClient.createRetrofitInstance()
            val firmwareUpdateApi = httpClient.create(FirmwareUpdateApi::class.java)
            val firmwareBytes = firmwareUpdateApi.getFirmwareUpdatePackage(firmwareUrl)
            BleLogger.d(TAG, "Firmware package downloaded, size: ${firmwareBytes.contentLength()} bytes")
            parseFirmwareZip(firmwareBytes.bytes())
        }
    }

    private fun parseFirmwareZip(bytes: ByteArray): List<Pair<String, ByteArray>> {
        val firmwareFiles = mutableListOf<Pair<String, ByteArray>>()
        val zipInputStream = ZipInputStream(ByteArrayInputStream(bytes))
        var entry: ZipEntry?
        val buffer = ByteArray(PolarFirmwareUpdateUtils.BUFFER_SIZE)
        try {
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
            val totalSize = firmwareFiles.sumOf { it.second.size }
            BleLogger.d(TAG, "Firmware package unzipped, total size: $totalSize bytes")
            return firmwareFiles
        } catch (e: Exception) {
            BleLogger.e(TAG, "Failed to unzip firmware package")
            throw e
        }
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
                    BleLogger.d(TAG, "PFTP firmware file write success - device is rebooting")
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
        logApiCall("deleteStoredDeviceData", "identifier" to identifier)
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

    private suspend fun checkIfDirectoryIsEmpty(directoryPath: String, client: BlePsFtpClient): Boolean {
        var path = directoryPath
        if (!path.endsWith("/")) path = path.plus("/")
        val builder = PftpRequest.PbPFtpOperation.newBuilder()
        builder.command = PftpRequest.PbPFtpOperation.Command.GET
        builder.path = path
        return try {
            val byteArrayOutputStream = client.request(builder.build().toByteArray())
            val directory = PftpResponse.PbPFtpDirectory.parseFrom(byteArrayOutputStream.toByteArray())
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

    override suspend fun deleteDeviceDateFolders(identifier: String, fromDate: LocalDate?, toDate: LocalDate?) {
        logApiCall("deleteDeviceDateFolders", "identifier" to identifier)
        BleLogger.d(TAG, "Delete empty day folders between: $fromDate to $toDate.")
        val dateFormatter = DateTimeFormatter.BASIC_ISO_DATE
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()

        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            BleLogger.e(TAG, "deleteDeviceDateFolders: Invalid date range: toDate $toDate is before fromDate $fromDate")
            return
        }
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
        logApiCall("deleteTelemetryData", "identifier" to identifier)
        BleLogger.d(TAG, "Delete all telemetry data from device.")

        val cond = PolarFileUtils.FetchRecursiveCondition { entry: String ->
            entry.matches(Regex("([A-Za-z]{3}[0-9]{1,3}).BIN$")) &&
                    entry.startsWith("TRC")
        }

        try {
            PolarFileUtils.listFiles(identifier, "/", condition = cond, listener, TAG)
                .collect { filename ->
                    PolarFileUtils.removeSingleFile(identifier, filename, listener, TAG)
                    BleLogger.d(TAG, "Successfully deleted telemetry data $filename from device $identifier.")
                }
        } catch (error: Throwable) {
            BleLogger.e(TAG, "Error while trying to delete telemetry files from device $identifier, error: $error")
        }
    }

    override suspend fun setMultiBLEConnectionMode(identifier: String, enable: Boolean) {
        logApiCall("setMultiBLEConnectionMode", "identifier" to identifier)
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
        logApiCall("getMultiBLEConnectionMode", "identifier" to identifier)
        val session = PolarServiceClientUtils.sessionPsPfcClientReady(identifier, listener)
        val client = session.fetchClient(PFC_SERVICE) as BlePfcClient?
            ?: throw PolarServiceNotAvailable()
        BleLogger.d(TAG, "Request multi BLE mode status from device $identifier.")
        val pfcResponse = client.sendControlPointCommand(PfcMessage.PFC_REQUEST_MULTI_CONNECTION_SETTING, null)
        return pfcResponse.payload?.get(0)?.toInt() == 1
    }

    override suspend fun setSensorInitiatedSecurityMode(identifier: String, enable: Boolean) {
        logApiCall("setSensorInitiatedSecurityMode", "identifier" to identifier)
        val session = PolarServiceClientUtils.sessionPsPfcClientReady(identifier, listener)
        val client = session.fetchClient(PFC_SERVICE) as BlePfcClient?
            ?: throw PolarServiceNotAvailable()
        BleLogger.d(TAG, "Send sensor initiated security mode value to device $identifier with mode $enable.")
        val pfcResponse = client.sendControlPointCommand(PfcMessage.PFC_CONFIGURE_SENSOR_INITIATED_SECURITY_MODE, if (enable) 1 else 0)
        if (pfcResponse.status.toInt() != 1) {
            throw PolarOperationNotSupported()
        }
    }

    override suspend fun getSensorInitiatedSecurityMode(identifier: String): Boolean {
        logApiCall("getSensorInitiatedSecurityMode", "identifier" to identifier)
        val session = PolarServiceClientUtils.sessionPsPfcClientReady(identifier, listener)
        val client = session.fetchClient(PFC_SERVICE) as BlePfcClient?
            ?: throw PolarServiceNotAvailable()
        BleLogger.d(TAG, "Request sensor initiated security mode value from device $identifier.")
        val pfcResponse = client.sendControlPointCommand(PfcMessage.PFC_REQUEST_SENSOR_INITIATED_SECURITY_MODE, 0)
        return pfcResponse.payload?.get(0)?.toInt() == 1
    }

    override suspend fun setAutomaticOHRMeasurementEnabled(identifier: String, enabled: Boolean) {
        logApiCall("setAutomaticOHRMeasurementEnabled", "identifier" to identifier)
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
        BleLogger.d(TAG, "AUTOS files enabled=$enabled written for $identifier")
    }

    override suspend fun get247HrSamples(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<Polar247HrSamplesData> {
        logApiCall("get247HrSamples", "identifier" to identifier, "fromDate" to fromDate, "toDate" to toDate)
        return activityApiImpl.get247HrSamples(identifier, fromDate, toDate)
    }

    override suspend fun get247PPiSamples(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<Polar247PPiSamplesData> {
        logApiCall("get247PPiSamples", "identifier" to identifier, "fromDate" to fromDate, "toDate" to toDate)
        return activityApiImpl.get247PPiSamples(identifier, fromDate, toDate)
    }

    override suspend fun getNightlyRecharge(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<PolarNightlyRechargeData> {
        logApiCall("getNightlyRecharge", "identifier" to identifier, "fromDate" to fromDate, "toDate" to toDate)
        return activityApiImpl.getNightlyRecharge(identifier, fromDate, toDate)
    }

    override suspend fun getSkinTemperature(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<PolarSkinTemperatureData> {
        logApiCall("getSkinTemperature", "identifier" to identifier)
        return activityApiImpl.getSkinTemperature(identifier, fromDate, toDate)
    }

    override suspend fun getSpo2TestData(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<PolarSpo2TestData> {
        logApiCall("getSpo2Test", "identifier" to identifier)
        return testApiImpl.getSpo2Test(identifier, fromDate, toDate)
    }

    @Deprecated("Use getSpo2TestData instead", ReplaceWith("getSpo2TestData(identifier, fromDate, toDate)"))
    override suspend fun getSpo2Test(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<PolarSpo2TestData> {
        return testApiImpl.getSpo2Test(identifier, fromDate, toDate)
    }

    override fun getTrainingSessionReferences(identifier: String, fromDate: LocalDate?, toDate: LocalDate?): Flow<PolarTrainingSessionReference> {
        logApiCall("getTrainingSessionReferences", "identifier" to identifier)
        return trainingSessionApiImpl.getTrainingSessionReferences(identifier, fromDate, toDate)
    }

    override suspend fun getTrainingSession(
        identifier: String,
        trainingSessionReference: PolarTrainingSessionReference
    ): PolarTrainingSession {
        logApiCall("getTrainingSession", "identifier" to identifier)
        return trainingSessionApiImpl.getTrainingSession(identifier, trainingSessionReference)
    }

    override fun getTrainingSessionWithProgress(
        identifier: String,
        trainingSessionReference: PolarTrainingSessionReference
    ): Flow<PolarTrainingSessionFetchResult> {
        logApiCall("getTrainingSessionWithProgress", "identifier" to identifier)
        return trainingSessionApiImpl.getTrainingSessionWithProgress(identifier, trainingSessionReference)
    }


    override suspend fun waitForConnection(identifier: String) {
        logApiCall("waitForConnection", "identifier" to identifier)
        while (true) {
            val session = fetchSession(identifier, listener)
            if (session != null && session.sessionState == DeviceSessionState.SESSION_OPEN) {
                return
            }
            delay(100)
        }
    }

    override suspend fun sendInitializationAndStartSyncNotifications(identifier: String): Boolean {
        logApiCall("sendInitializationAndStartSyncNotifications", "identifier" to identifier)
        BleLogger.d(TAG, "Sending initialize session and start sync notifications for $identifier")
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
            BleLogger.e(TAG, "sendInitializationAndStartSyncNotifications: error $identifier: $e")
            false
        }
    }

    override suspend fun sendTerminateAndStopSyncNotifications(identifier: String) {
        logApiCall("sendTerminateAndStopSyncNotifications", "identifier" to identifier)
        BleLogger.d(TAG, "Sending terminate session and stop sync notifications for $identifier")
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
            BleLogger.e(TAG, "sendTerminateAndStopSyncNotifications: error $identifier: $e")
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
        val byteArray = try {
            client.request(builder.build().toByteArray()).toByteArray()
        } catch (e: Throwable) {
            if (e is PftpResponseError && e.error == PbPFtpError.NO_SUCH_FILE_OR_DIRECTORY.number) {
                BleLogger.e(TAG, "User device settings file missing on device at path ${sanitizePathForLog(path)}")
                throw PolarBleSdkInternalException("User device settings file is missing on device.")
            }
            BleLogger.e(TAG, "Failed to read user device settings from ${sanitizePathForLog(path)}: $e")
            throw e
        }

        if (byteArray.isEmpty()) {
            BleLogger.e(TAG, "User device settings file at ${sanitizePathForLog(path)} is empty")
            throw PolarBleSdkInternalException("User device settings file is empty and not decodable.")
        }

        return try {
            PbUserDeviceSettings.parseFrom(byteArray)
        } catch (e: Throwable) {
            BleLogger.e(TAG, "User device settings decode failed for ${sanitizePathForLog(path)}, bytes=${byteArray.size}: $e")
            throw PolarBleSdkInternalException("User device settings file is unreadable or undecodable.")
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
        logApiCall("getSteps", "identifier" to identifier, "fromDate" to fromDate, "toDate" to toDate)
        return activityApiImpl.getSteps(identifier, fromDate, toDate)
    }

    override suspend fun getActivitySampleData(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<PolarActivitySamplesDayData> {
        logApiCall("getActivitySampleData", "identifier" to identifier, "fromDate" to fromDate, "toDate" to toDate)
        return activityApiImpl.getActivitySampleData(identifier, fromDate, toDate)
    }

    override suspend fun getDailySummaryData(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<PolarDailySummaryData> {
        logApiCall("getDailySummaryData", "identifier" to identifier, "fromDate" to fromDate, "toDate" to toDate)
        return activityApiImpl.getDailySummaryData(identifier, fromDate, toDate)
    }

    override suspend fun getDistance(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<PolarDistanceData> {
        logApiCall("getDistance", "identifier" to identifier, "fromDate" to fromDate, "toDate" to toDate)
        return activityApiImpl.getDistance(identifier, fromDate, toDate)
    }


    override suspend fun getSleepRecordingState(identifier: String, timeoutMs: Long): Boolean {
        logApiCall("getSleepRecordingState", "identifier" to identifier)
        return sleepApiImpl.getSleepRecordingState(identifier, timeoutMs)
    }

    override fun observeSleepRecordingState(identifier: String): Flow<Array<Boolean>> {
        logApiCall("observeSleepRecordingState", "identifier" to identifier)
        return sleepApiImpl.observeSleepRecordingState(identifier)
    }

    override suspend fun stopSleepRecording(identifier: String) {
        logApiCall("stopSleepRecording", "identifier" to identifier)
        sleepApiImpl.stopSleepRecording(identifier)
    }

    override suspend fun getSleep(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<PolarSleepData> {
        logApiCall("getSleep", "identifier" to identifier)
        return sleepApiImpl.getSleep(identifier, fromDate, toDate)
    }


    override suspend fun getCalories(identifier: String, fromDate: LocalDate, toDate: LocalDate, caloriesType: CaloriesType): List<PolarCaloriesData> {
        logApiCall("getCalories", "identifier" to identifier, "fromDate" to fromDate, "toDate" to toDate, "caloriesType" to caloriesType)
        return activityApiImpl.getCalories(identifier, fromDate, toDate, caloriesType)
    }

    override suspend fun getActiveTime(identifier: String, fromDate: LocalDate, toDate: LocalDate): List<PolarActiveTimeData> {
        logApiCall("getActiveTime", "identifier" to identifier)
        return activityApiImpl.getActiveTime(identifier, fromDate, toDate)
    }


    @Deprecated("Use setting specific methods instead")
    override suspend fun setUserDeviceSettings(identifier: String, deviceUserSetting: PolarUserDeviceSettings) {
        logApiCall("setPolarUserDeviceSettings", "identifier" to identifier)
        BleLogger.d(TAG, "setPolarUserDeviceSettings: writing settings for $identifier")
        setUserDeviceSettingsProto(identifier, deviceUserSetting.toProto())
    }

    override suspend fun getUserDeviceSettings(identifier: String): PolarUserDeviceSettings {
        logApiCall("getPolarUserDeviceSettings", "identifier" to identifier)
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val proto = getUserDeviceSettingsProto(client, session.polarDeviceType)
        return PolarUserDeviceSettings().fromBytes(proto.toByteArray())
    }

    override suspend fun setUserDeviceLocation(identifier: String, location: Int) {
        logApiCall("setUserDeviceLocation", "identifier" to identifier, "location" to location)
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
        logApiCall("setUsbConnectionMode", "identifier" to identifier, "enabled" to enabled)
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
        logApiCall(
            "setAutomaticTrainingDetectionSettings",
            "identifier" to identifier,
            "mode" to automaticTrainingDetectionMode,
            "sensitivity" to automaticTrainingDetectionSensitivity,
            "minimumDuration" to minimumTrainingDurationSeconds
        )
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
        logApiCall("setDaylightSavingTime", "identifier" to identifier)
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val localTime = LocalDateTime.now()
        BleLogger.d(TAG, "setDaylightSavingTime: setting local time $localTime for $identifier")
        val pbLocalTime = javaLocalDateTimeToPbPftpSetLocalTime(localTime)
        client.query(PftpRequest.PbPFtpQuery.SET_LOCAL_TIME_VALUE, pbLocalTime.toByteArray())
    }

    override suspend fun setTelemetryEnabled(identifier: String, enabled: Boolean) {
        logApiCall("setTelemetryEnabled", "identifier" to identifier, "enabled" to enabled)
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        val currentProto = getUserDeviceSettingsProto(client, session.polarDeviceType)
        val telemetrySettings = PbUserDeviceTelemetrySettings.newBuilder()
            .setTelemetryEnabled(enabled)
            .build()
        val updated = currentProto.toBuilder().setTelemetrySettings(telemetrySettings).build()
        setUserDeviceSettingsProto(identifier, updated)
        BleLogger.d(TAG, "Telemetry enabled=$enabled written for $identifier")
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
        logApiCall("getBatteryLevel", "identifier" to identifier)
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
        logApiCall("getChargerState", "identifier" to identifier)
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
        logApiCall("startOfflineExerciseV2", "identifier" to identifier)
        if (!isFeatureReady(identifier, PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2)) {
            throw PolarOperationNotSupported()
        }
        return offlineExerciseV2Api.startOfflineExerciseV2(identifier, sportProfile)
    }

    override suspend fun stopOfflineExerciseV2(identifier: String) {
        logApiCall("stopOfflineExerciseV2", "identifier" to identifier)
        if (!isFeatureReady(identifier, PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2)) {
            throw PolarOperationNotSupported()
        }
        offlineExerciseV2Api.stopOfflineExerciseV2(identifier)
    }

    override suspend fun getOfflineExerciseStatusV2(identifier: String): Boolean {
        logApiCall("getOfflineExerciseStatusV2", "identifier" to identifier)
        if (!isFeatureReady(identifier, PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2)) {
            throw PolarOperationNotSupported()
        }
        return offlineExerciseV2Api.getOfflineExerciseStatusV2(identifier)
    }

    override fun listOfflineExercisesV2(identifier: String, directoryPath: String): Flow<PolarExerciseEntry> {
        logApiCall("listOfflineExercisesV2", "identifier" to identifier)
        if (!isFeatureReady(identifier, PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2)) {
            return flow { throw PolarOperationNotSupported() }
        }
        return offlineExerciseV2Api.listOfflineExercisesV2(identifier, directoryPath)
    }

    override suspend fun fetchOfflineExerciseV2(
        identifier: String,
        entry: PolarExerciseEntry
    ): PolarExerciseData {
        logApiCall("fetchOfflineExerciseV2", "identifier" to identifier)
        if (!isFeatureReady(identifier, PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2)) {
            throw PolarOperationNotSupported()
        }
        return offlineExerciseV2Api.fetchOfflineExerciseV2(identifier, entry)
    }

    override suspend fun removeOfflineExerciseV2(
        identifier: String,
        entry: PolarExerciseEntry
    ) {
        logApiCall("removeOfflineExerciseV2", "identifier" to identifier)
        if (!isFeatureReady(identifier, PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_EXERCISE_V2)) {
            throw PolarOperationNotSupported()
        }
        offlineExerciseV2Api.removeOfflineExerciseV2(identifier, entry)
    }

    override suspend fun isOfflineExerciseV2Supported(identifier: String): Boolean {
        logApiCall("isOfflineExerciseV2Supported", "identifier" to identifier)
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
                BleLogger.i(TAG, "Feature check: waiting for services discovery for $deviceId")
                val discoveredServices = session.monitorServicesDiscovered(false).await()
                BleLogger.i(TAG, "Feature check: discovered ${discoveredServices.size} services for $deviceId: $discoveredServices")
                val results = requestedFeatures.map { feature ->
                    try { checkAndReportFeatureReadiness(session, discoveredServices, feature) }
                    catch (e: Throwable) { Pair(feature, false) }
                }
                val ready = results.filter { it.second }.map { it.first }
                val unavailable = results.filter { !it.second }.map { it.first }
                BleLogger.i(TAG, "Features readiness check COMPLETED for $deviceId. Ready: ${ready.size}/${requestedFeatures.size}, Unavailable: $unavailable")
                withContext(Dispatchers.Main) {
                    if (deviceId != null) callback?.bleSdkFeaturesReadiness(deviceId, ready, unavailable)
                }
            } catch (throwable: Throwable) {
                BleLogger.e(TAG, "CRITICAL: Error while checking available features for $deviceId: ${throwable.message} (${throwable.javaClass.simpleName}), Stack: ${throwable.stackTrace.firstOrNull()}")
                val ready = readyFeaturesMap[deviceId]?.toList() ?: emptyList()
                val unavailable = requestedFeatures.filter { !ready.contains(it) }
                BleLogger.w(TAG, "Feature check error recovery: using cached features for $deviceId. Ready: ${ready.size}, Unavailable: ${unavailable.size}")
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
                try {
                    hrClient.observeHrNotifications(true)
                        .collect { data ->
                            withContext(Dispatchers.Main) {
                                if (deviceId != null) {
                                    callback?.hrNotificationReceived(
                                        deviceId,
                                        PolarHrData.PolarHrSample(
                                            data.hrValue, 0, 0,
                                            data.rrs,
                                            data.rrsMs, data.rrPresent,
                                            data.sensorContact, data.sensorContactSupported
                                        )
                                    )
                                }
                            }
                        }
                } catch (error: Throwable) {
                    BleLogger.e(TAG, "HR notification error: $error")
                }
            }
        }

        val dataMonitorJob = apiScope.launch {
            try {
                BleLogger.i(TAG, "Data monitor: waiting for services to be discovered for $deviceId")
                val discoveredServices = session.monitorServicesDiscovered(true).await()
                BleLogger.i(TAG, "Data monitor: discovered ${discoveredServices.size} services for $deviceId, starting service clients initialization")
                for (uuid in discoveredServices) {
                    val client = session.fetchClient(uuid) ?: continue
                    when (uuid) {
                        HR_SERVICE -> {
                            if (deviceId != null) withContext(Dispatchers.Main) {
                                callback?.bleSdkFeatureReady(deviceId, PolarBleSdkFeature.FEATURE_HR)
                            }
                            val bleHrClient = client as BleHrClient
                            launch {
                                try {
                                    bleHrClient.observeHrNotifications(true)
                                        .collect { data ->
                                            withContext(Dispatchers.Main) {
                                                if (deviceId != null) {
                                                    callback?.hrNotificationReceived(
                                                        deviceId,
                                                        PolarHrData.PolarHrSample(
                                                            data.hrValue, 0, 0,
                                                            data.rrs,
                                                            data.rrsMs, data.rrPresent,
                                                            data.sensorContact, data.sensorContactSupported
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                } catch (error: Throwable) {
                                    BleLogger.e(TAG, "HR notification error: $error")
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
                BleLogger.e(TAG, "CRITICAL: Error while monitoring session services for $deviceId: ${throwable.message} (${throwable.javaClass.simpleName}), Stack: ${throwable.stackTrace.firstOrNull()}")
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
            PolarBleSdkFeature.FEATURE_WATCH_FACES_CONFIGURATION -> deviceIsWatchAndPsftpIsEnabled(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_TELEMETRY -> isMdsServiceAvailable(discoveredServices, session)
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

    private suspend fun deviceIsWatchAndPsftpIsEnabled(discoveredServices: List<UUID>, session: BleDeviceSession): Boolean {
        if (BlePolarDeviceCapabilitiesUtility.isDeviceSensor(session.polarDeviceType)) return false
        return isPsftpServiceAvailable(discoveredServices, session)
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

    private suspend fun isMdsServiceAvailable(discoveredServices: List<UUID>, session: BleDeviceSession): Boolean {
        if (!discoveredServices.contains(BleMdsClient.MDS_SERVICE)) return false
        val bleMdsClient = session.fetchClient(BleMdsClient.MDS_SERVICE) as BleMdsClient? ?: return false
        return try { bleMdsClient.clientReady(true); true } catch (e: Throwable) { false }
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

    override fun stateChanged(power: Boolean) {
        logApiCall("stateChanged")
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
        logApiCall("getRSSIValue", "identifier" to identifier)
        return PolarServiceClientUtils.getRSSIValue(identifier, listener)
    }

    override suspend fun getWatchFaceConfig(identifier: String): PolarWatchFaceConfig {
        logApiCall("getWatchFaceConfig", "identifier" to identifier)
        BleLogger.d(TAG, "getWatchFaceConfig: device=$identifier  key=${PolarWatchFaceUtils.WATCH_FACE_CONFIG_KVS_KEY}")
        val fields = PolarWatchFaceUtils.readWatchFaceConfigFields(identifier, listener, ::handleError)
        val complications = fields.complicationIds.mapNotNull { id ->
            val c = PolarWatchFaceComplication.fromId(id)
            if (c == null) BleLogger.w(TAG, "getWatchFaceConfig: id=$id not in enum, skipping")
            c
        }
        BleLogger.d(TAG, "getWatchFaceConfig: resolved complications = ${complications.map { it.name }}")
        return PolarWatchFaceConfig(complications)
    }

    override suspend fun setWatchFaceConfig(identifier: String, config: PolarWatchFaceConfig) {
        logApiCall("setWatchFaceConfig", "identifier" to identifier)
        val ids = config.enabledComplications.map { it.id }
        BleLogger.d(TAG, "setWatchFaceConfig: device=$identifier ids=$ids")
        PolarWatchFaceUtils.writeWatchFaceComplicationInts(identifier, ids, listener, ::handleError)
    }

    @Throws(PolarOperationNotSupported::class, PolarServiceNotAvailable::class)
    override suspend fun startTelemetry(identifier: String, telemetryType: PolarDeviceTelemetryType): Flow<DeviceTelemetryEvent> {
        logApiCall("startTelemetry", "telemetryType" to telemetryType, "identifier" to identifier)
        if (!isFeatureReady(identifier, PolarBleSdkFeature.FEATURE_TELEMETRY)) {
            throw PolarOperationNotSupported()
        }
        return telemetryApi.startTelemetry(identifier, telemetryType)
    }

    override suspend fun stopTelemetry(identifier: String, telemetryType: PolarDeviceTelemetryType): Boolean {
        logApiCall("stopTelemetry", "telemetryType" to telemetryType, "identifier" to identifier)
        if (!isFeatureReady(identifier, PolarBleSdkFeature.FEATURE_TELEMETRY)) {
            throw PolarOperationNotSupported()
        }
        BleLogger.d(TAG, "stopTelemetry for $identifier")
        return telemetryApi.stopTelemetry(identifier, telemetryType)
    }

    override fun getDeviceTelemetryConfiguration(identifier: String, telemetryType: PolarDeviceTelemetryType): DeviceTelemetryConfiguration {
        logApiCall("getDeviceTelemetryConfiguration", "telemetryType" to telemetryType, "identifier" to identifier)
        return telemetryApi.getDeviceTelemetryConfiguration(identifier, telemetryType)
    }

    override suspend fun getAvailableTelemetryTypes(identifier: String): DeviceTelemetrySupport {
        logApiCall("getAvailableTelemetryTypes", "identifier" to identifier)
        return telemetryApi.getAvailableTelemetryTypes(identifier)
    }

    private fun logSdkInitialization() {
        val featureSummary = features.map { it.name }.sorted().joinToString(",")
        val message = "SDK initialized sdkVersion=${PolarBleApiDefaultImpl.versionInfo()} platform=Android osVersion=${Build.VERSION.RELEASE ?: "unknown"} apiLevel=${Build.VERSION.SDK_INT} features=[$featureSummary]"
        BleLogger.d(TAG, message)
        logger?.message(message)
    }

    private fun logApiCall(method: String, vararg params: Pair<String, Any?>) {
        val arguments = params.joinToString(", ") { (name, value) ->
            "$name=${sanitizeForLog(name, value)}"
        }
        val message = if (arguments.isEmpty()) "[API] $method" else "[API] $method($arguments)"
        BleLogger.d(TAG, message)
        logger?.message(message)
    }

    private fun sanitizeForLog(name: String, value: Any?): String {
        if (value == null) return "nil"

        if (value is String) {
            if (isBleAddressFormat(value)) return sanitizeBleAddress(value)
            if (isPolarDeviceIdFormat(value)) return sanitizePolarDeviceId(value)
            if (isPathParameter(name)) return sanitizePathForLog(value)
        }

        if (isSensitiveParameter(name)) return "<suppressed>"
        return when (value) {
            is ByteArray -> "<${value.size} bytes>"
            is Collection<*> -> "size=${value.size}"
            is Map<*, *> -> "size=${value.size}"
            is Number, is Boolean, is Enum<*>, is CharSequence,
            is java.time.temporal.TemporalAccessor, is java.util.Date -> value.toString()
            else -> "<${value::class.simpleName ?: "object"}>"
        }
    }

    private fun isPolarDeviceIdFormat(value: String): Boolean {
        return value.matches(Regex("^[0-9A-Fa-f]{8}$"))
    }

    private fun sanitizePolarDeviceId(value: String): String {
        if (value.length != 8) return "<suppressed>"
        return "XXXXXX" + value.takeLast(2)
    }

    private fun isBleAddressFormat(value: String): Boolean {
        return value.matches(Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$"))
    }

    private fun sanitizeBleAddress(value: String): String {
        val parts = value.split(":")
        if (parts.size != 6) return value
        return "${parts.first()}:XX:XX:XX:XX:${parts.last()}"
    }

    private fun isSensitiveParameter(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower.contains("identifier") ||
            lower.contains("deviceid") ||
            lower.contains("address") ||
            lower.contains("secret") ||
            lower.contains("token") ||
            lower.contains("password") ||
            lower.contains("email") ||
            lower.contains("phone") ||
            lower.contains("user") ||
            lower.contains("notification")
    }

    private fun isPathParameter(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower == "path" ||
            lower == "filepath" ||
            lower == "directorypath" ||
            lower == "folderpath" ||
            lower.endsWith(".path")
    }

    private fun sanitizePathForLog(path: String): String {
        val normalized = path.replace('\\', '/')
        val lastSegment = normalized.substringAfterLast('/', "")
        return if (lastSegment.isEmpty()) "<path>" else ".../$lastSegment"
    }
}