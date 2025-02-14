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
import com.polar.androidcommunications.api.ble.model.gatt.client.BleDisClient
import com.polar.androidcommunications.api.ble.model.gatt.client.BleHrClient
import com.polar.androidcommunications.api.ble.model.gatt.client.BleHrClient.*
import com.polar.androidcommunications.api.ble.model.gatt.client.BleHrClient.Companion.HR_MEASUREMENT
import com.polar.androidcommunications.api.ble.model.gatt.client.BleHrClient.Companion.HR_SERVICE
import com.polar.androidcommunications.api.ble.model.gatt.client.BleHrClient.Companion.HR_SERVICE_16BIT_UUID
import com.polar.androidcommunications.api.ble.model.gatt.client.BleHtsClient
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
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallbackProvider
import com.polar.sdk.api.PolarH10OfflineExerciseApi
import com.polar.sdk.api.RestApiEventPayload
import com.polar.sdk.api.errors.*
import com.polar.sdk.api.model.*
import com.polar.sdk.api.model.activity.Polar247HrSamplesData
import com.polar.sdk.api.model.activity.PolarActiveTimeData
import com.polar.sdk.api.model.activity.PolarCaloriesData
import com.polar.sdk.api.model.activity.PolarDistanceData
import com.polar.sdk.api.model.activity.PolarStepsData
import com.polar.sdk.api.model.restapi.PolarDeviceRestApiServiceDescription
import com.polar.sdk.api.model.restapi.PolarDeviceRestApiServices
import com.polar.sdk.api.model.sleep.PolarNightlyRechargeData
import com.polar.sdk.api.model.sleep.PolarSleepAnalysisResult
import com.polar.sdk.api.model.sleep.PolarSleepData
import com.polar.sdk.api.model.sleep.PolarSleepApiServiceEventPayload
import com.polar.sdk.impl.BDBleApiImpl.FetchRecursiveCondition
import com.polar.sdk.impl.utils.CaloriesType
import com.polar.sdk.impl.utils.PolarActivityUtils
import com.polar.sdk.impl.utils.PolarAutomaticSamplesUtils
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
import com.polar.sdk.impl.utils.PolarNightlyRechargeUtils
import com.polar.sdk.impl.utils.PolarSkinTemperatureUtils
import com.polar.sdk.impl.utils.PolarSleepUtils
import com.polar.sdk.impl.utils.PolarTimeUtils
import com.polar.sdk.impl.utils.PolarTimeUtils.javaCalendarToPbPftpSetLocalTime
import com.polar.sdk.impl.utils.PolarTimeUtils.javaCalendarToPbPftpSetSystemTime
import com.polar.sdk.impl.utils.PolarTimeUtils.pbLocalTimeToJavaCalendar
import com.polar.sdk.impl.utils.receiveRestApiEvents
import com.polar.sdk.impl.utils.toObject
import fi.polar.remote.representation.protobuf.AutomaticSamples.PbAutomaticSampleSessions
import fi.polar.remote.representation.protobuf.ExerciseSamples.PbExerciseSamples
import fi.polar.remote.representation.protobuf.Types.*
import fi.polar.remote.representation.protobuf.UserIds
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.*
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.BiFunction
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.schedulers.Timed
import org.reactivestreams.Publisher
import protocol.PftpError.PbPFtpError
import protocol.PftpNotification
import protocol.PftpNotification.PbPFtpStopSyncParams
import protocol.PftpRequest
import protocol.PftpResponse
import protocol.PftpResponse.PbPFtpDirectory
import protocol.PftpResponse.PbRequestRecordingStatusResult
import com.polar.sdk.api.model.activity.Polar247PPiSamplesData
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream


/**
 * The default implementation of the Polar API
 * @Suppress
 */
class BDBleApiImpl private constructor(context: Context, features: Set<PolarBleSdkFeature>) : PolarBleApi(features), BlePowerStateChangedCallback {
    private val connectSubscriptions: MutableMap<String, Disposable> = mutableMapOf()
    private val deviceDataMonitorDisposable: MutableMap<String, Disposable> = mutableMapOf()
    private val deviceAvailableFeaturesDisposable: MutableMap<String, Disposable> = mutableMapOf()
    private val stopPmdStreamingDisposable: MutableMap<String, Disposable> = mutableMapOf()
    private val filter =
        BleSearchPreFilter { content: BleAdvertisementContent -> content.polarDeviceId.isNotEmpty() && content.polarDeviceType != "mobile" }
    private var listener: BleDeviceListener?
    private var devicesStateMonitorDisposable: Disposable? = null
    private var deviceSessionState: DeviceSessionState? = null
    private var callback: PolarBleApiCallbackProvider? = null
    private var logger: PolarBleApiLogger? = null
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ENGLISH)

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

                PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP -> clients.add(BlePsFtpClient::class.java)
                PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE -> clients.add(BlePMDClient::class.java)
                PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER -> clients.add(BlePsFtpClient::class.java)
                PolarBleSdkFeature.FEATURE_HTS -> clients.add(BleHtsClient::class.java)
                PolarBleSdkFeature.FEATURE_POLAR_LED_ANIMATION -> clients.add(BlePsFtpClient::class.java)
                PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE -> clients.add(BlePsFtpClient::class.java)
                PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA -> clients.add(BlePsFtpClient::class.java)
                PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA -> clients.add(BlePsFtpClient::class.java)
                PolarBleSdkFeature.FEATURE_POLAR_TEMPERATURE_DATA -> clients.add(BlePsFtpClient::class.java)
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

        RxJavaPlugins.setErrorHandler { e: Throwable ->
            if (e.cause is BleDisconnected) {
                // fine, BleDisconnection occasionally causes UndeliverableException
                // Read more in https://github.com/ReactiveX/RxJava/blob/3.x/docs/What's-different-in-2.0.md#error-handling
                return@setErrorHandler
            }
            BleLogger.e(TAG, "Undeliverable exception received, not sure what to do $e")
            Thread.currentThread().uncaughtExceptionHandler?.uncaughtException(
                Thread.currentThread(),
                e
            )
        }
    }

    override fun setMtu(mtu: Int) {
        try {
            listener?.preferredMtu = mtu
        } catch (e: BleInvalidMtu) {
            BleLogger.e(TAG, "Invalid MTU $mtu value given. Must be zero or positive.")
        }
    }

    override fun shutDown() {
        for (dataDisposable in deviceDataMonitorDisposable.values) {
            if (!dataDisposable.isDisposed) {
                dataDisposable.dispose()
            }
        }

        for (disposable in deviceAvailableFeaturesDisposable.values) {
            if (!disposable.isDisposed) {
                disposable.dispose()
            }
        }

        devicesStateMonitorDisposable?.dispose()
        devicesStateMonitorDisposable = null

        for (connectSubscription in connectSubscriptions.values) {
            if (!connectSubscription.isDisposed) {
                connectSubscription.dispose()
            }
        }
        for (pmdStreamingDisposable in stopPmdStreamingDisposable.values) {
            if (!pmdStreamingDisposable.isDisposed) {
                pmdStreamingDisposable.dispose()
            }
        }
        listener?.shutDown()
        logger = null
        callback = null
        listener = null
        clearInstance()
    }

    override fun cleanup() {
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
                    sessionHrClientReady(deviceId)
                    sessionPmdClientReady(deviceId)
                    true
                }

                PolarBleSdkFeature.FEATURE_HR -> {
                    sessionHrClientReady(deviceId)
                    true
                }

                PolarBleSdkFeature.FEATURE_DEVICE_INFO -> {
                    sessionServiceReady(deviceId, BleDisClient.DIS_SERVICE)
                    true
                }

                PolarBleSdkFeature.FEATURE_BATTERY_INFO -> {
                    sessionServiceReady(deviceId, BleBattClient.BATTERY_SERVICE)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING -> {
                    sessionPmdClientReady(deviceId)
                    sessionPsFtpClientReady(deviceId)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP -> {
                    sessionPsFtpClientReady(deviceId)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING -> {
                    val session = sessionPsFtpClientReady(deviceId)
                    FileSystemType.H10_FILE_SYSTEM == getFileSystemType(session.polarDeviceType)
                }

                PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE -> {
                    sessionPmdClientReady(deviceId)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER -> {
                    sessionPsFtpClientReady(deviceId)
                    true
                }

                PolarBleSdkFeature.FEATURE_HTS -> {
                    sessionServiceReady(deviceId, HealthThermometer.HTS_SERVICE)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_LED_ANIMATION -> {
                    sessionPsFtpClientReady(deviceId)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE -> {
                    sessionPsFtpClientReady(deviceId)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA -> {
                    sessionPsFtpClientReady(deviceId)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA -> {
                    sessionPsFtpClientReady(deviceId)
                    true
                }

                PolarBleSdkFeature.FEATURE_POLAR_TEMPERATURE_DATA -> {
                    sessionPsFtpClientReady(deviceId)
                    true
                }
            }
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

    override fun setLocalTime(identifier: String, calendar: Calendar): Completable {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Completable.error(error)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: return Completable.error(PolarServiceNotAvailable())

        BleLogger.d(TAG, "set local time to ${calendar.time} device $identifier")
        val pbLocalTime = javaCalendarToPbPftpSetLocalTime(calendar)
        return setSystemTime(client, calendar)
            .onErrorComplete()
            .andThen(
                client.query(
                    PftpRequest.PbPFtpQuery.SET_LOCAL_TIME_VALUE,
                    pbLocalTime.toByteArray()
                )
                    .ignoreElement()
            )
    }

    private fun setSystemTime(client: BlePsFtpClient, calendar: Calendar): Completable {
        val pbTime = javaCalendarToPbPftpSetSystemTime(calendar)
        return client.query(PftpRequest.PbPFtpQuery.SET_SYSTEM_TIME_VALUE, pbTime.toByteArray())
            .ignoreElement()
    }

    override fun getLocalTime(identifier: String): Single<Calendar> {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Single.error(error)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: return Single.error(PolarServiceNotAvailable())

        BleLogger.d(TAG, "get local time from device $identifier")
        return client.query(PftpRequest.PbPFtpQuery.GET_LOCAL_TIME_VALUE, null)
            .map {
                val dateTime: PftpRequest.PbPFtpSetLocalTimeParams =
                    PftpRequest.PbPFtpSetLocalTimeParams.parseFrom(it.toByteArray())
                pbLocalTimeToJavaCalendar(dateTime)
            }.onErrorResumeNext {
                if (it is PftpResponseError && it.error == 201) {
                    Single.error(BleNotSupported("${session.name} do not support getTime"))
                } else {
                    Single.error(it)
                }
            }
    }

    override fun requestStreamSettings(
        identifier: String,
        feature: PolarDeviceDataType
    ): Single<PolarSensorSetting> {
        BleLogger.d(TAG, "Request online stream settings. Feature: $feature Device: $identifier")
        return when (feature) {
            PolarDeviceDataType.ECG -> querySettings(
                identifier,
                PmdMeasurementType.ECG,
                PmdRecordingType.ONLINE
            )

            PolarDeviceDataType.ACC -> querySettings(
                identifier,
                PmdMeasurementType.ACC,
                PmdRecordingType.ONLINE
            )

            PolarDeviceDataType.PPG -> querySettings(
                identifier,
                PmdMeasurementType.PPG,
                PmdRecordingType.ONLINE
            )

            PolarDeviceDataType.GYRO -> querySettings(
                identifier,
                PmdMeasurementType.GYRO,
                PmdRecordingType.ONLINE
            )

            PolarDeviceDataType.MAGNETOMETER -> querySettings(
                identifier,
                PmdMeasurementType.MAGNETOMETER,
                PmdRecordingType.ONLINE
            )

            PolarDeviceDataType.PRESSURE -> querySettings(
                identifier,
                PmdMeasurementType.PRESSURE,
                PmdRecordingType.ONLINE
            )

            PolarDeviceDataType.LOCATION -> querySettings(
                identifier,
                PmdMeasurementType.LOCATION,
                PmdRecordingType.ONLINE
            )

            PolarDeviceDataType.TEMPERATURE -> querySettings(
                identifier,
                PmdMeasurementType.TEMPERATURE,
                PmdRecordingType.ONLINE
            )

            PolarDeviceDataType.SKIN_TEMPERATURE -> querySettings(
                identifier,
                PmdMeasurementType.SKIN_TEMP,
                PmdRecordingType.ONLINE
            )

            PolarDeviceDataType.HR,
            PolarDeviceDataType.PPI -> Single.error(PolarOperationNotSupported())

            else -> Single.error(PolarOperationNotSupported())

        }
    }

    override fun requestFullStreamSettings(
        identifier: String,
        feature: PolarDeviceDataType
    ): Single<PolarSensorSetting> {
        BleLogger.d(
            TAG,
            "Request full online stream settings. Feature: $feature Device: $identifier"
        )
        return when (feature) {
            PolarDeviceDataType.ECG -> queryFullSettings(
                identifier,
                PmdMeasurementType.ECG,
                PmdRecordingType.ONLINE
            )

            PolarDeviceDataType.ACC -> queryFullSettings(
                identifier,
                PmdMeasurementType.ACC,
                PmdRecordingType.ONLINE
            )

            PolarDeviceDataType.PPG -> queryFullSettings(
                identifier,
                PmdMeasurementType.PPG,
                PmdRecordingType.ONLINE
            )

            PolarDeviceDataType.GYRO -> queryFullSettings(
                identifier,
                PmdMeasurementType.GYRO,
                PmdRecordingType.ONLINE
            )

            PolarDeviceDataType.MAGNETOMETER -> queryFullSettings(
                identifier,
                PmdMeasurementType.MAGNETOMETER,
                PmdRecordingType.ONLINE
            )

            PolarDeviceDataType.PPI,
            PolarDeviceDataType.HR,
            PolarDeviceDataType.PRESSURE,
            PolarDeviceDataType.LOCATION,
            PolarDeviceDataType.TEMPERATURE,
            PolarDeviceDataType.SKIN_TEMPERATURE -> Single.error(PolarOperationNotSupported())
        }
    }

    override fun requestOfflineRecordingSettings(
        identifier: String,
        feature: PolarDeviceDataType
    ): Single<PolarSensorSetting> {
        BleLogger.d(
            TAG,
            "Request offline recording settings. Feature: $feature Device: $identifier"
        )
        return when (feature) {
            PolarDeviceDataType.ECG -> querySettings(
                identifier,
                PmdMeasurementType.ECG,
                PmdRecordingType.OFFLINE
            )

            PolarDeviceDataType.ACC -> querySettings(
                identifier,
                PmdMeasurementType.ACC,
                PmdRecordingType.OFFLINE
            )

            PolarDeviceDataType.PPG -> querySettings(
                identifier,
                PmdMeasurementType.PPG,
                PmdRecordingType.OFFLINE
            )

            PolarDeviceDataType.GYRO -> querySettings(
                identifier,
                PmdMeasurementType.GYRO,
                PmdRecordingType.OFFLINE
            )

            PolarDeviceDataType.MAGNETOMETER -> querySettings(
                identifier,
                PmdMeasurementType.MAGNETOMETER,
                PmdRecordingType.OFFLINE
            )

            PolarDeviceDataType.PRESSURE -> querySettings(
                identifier,
                PmdMeasurementType.PRESSURE,
                PmdRecordingType.OFFLINE
            )

            PolarDeviceDataType.LOCATION -> querySettings(
                identifier,
                PmdMeasurementType.LOCATION,
                PmdRecordingType.OFFLINE
            )

            PolarDeviceDataType.TEMPERATURE -> querySettings(
                identifier,
                PmdMeasurementType.TEMPERATURE,
                PmdRecordingType.OFFLINE
            )

            PolarDeviceDataType.SKIN_TEMPERATURE -> querySettings(
                identifier,
                PmdMeasurementType.SKIN_TEMP,
                PmdRecordingType.OFFLINE
            )

            PolarDeviceDataType.HR,
            PolarDeviceDataType.PPI -> Single.error(PolarOperationNotSupported())

            else -> Single.error(PolarOperationNotSupported())
        }
    }

    override fun requestFullOfflineRecordingSettings(
        identifier: String,
        feature: PolarDeviceDataType
    ): Single<PolarSensorSetting> {
        BleLogger.d(
            TAG,
            "Request full offline recording settings. Feature: $feature Device: $identifier"
        )
        return when (feature) {
            PolarDeviceDataType.ECG -> queryFullSettings(
                identifier,
                PmdMeasurementType.ECG,
                PmdRecordingType.OFFLINE
            )

            PolarDeviceDataType.ACC -> queryFullSettings(
                identifier,
                PmdMeasurementType.ACC,
                PmdRecordingType.OFFLINE
            )

            PolarDeviceDataType.PPG -> queryFullSettings(
                identifier,
                PmdMeasurementType.PPG,
                PmdRecordingType.OFFLINE
            )

            PolarDeviceDataType.GYRO -> queryFullSettings(
                identifier,
                PmdMeasurementType.GYRO,
                PmdRecordingType.OFFLINE
            )

            PolarDeviceDataType.MAGNETOMETER -> queryFullSettings(
                identifier,
                PmdMeasurementType.MAGNETOMETER,
                PmdRecordingType.OFFLINE
            )

            PolarDeviceDataType.PPI,
            PolarDeviceDataType.HR,
            PolarDeviceDataType.PRESSURE,
            PolarDeviceDataType.LOCATION,
            PolarDeviceDataType.TEMPERATURE,
            PolarDeviceDataType.SKIN_TEMPERATURE -> Single.error(PolarOperationNotSupported())
        }
    }

    private fun querySettings(
        identifier: String,
        type: PmdMeasurementType,
        recordingType: PmdRecordingType
    ): Single<PolarSensorSetting> {
        return try {
            val session = sessionPmdClientReady(identifier)
            val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient?
                ?: return Single.error(PolarServiceNotAvailable())
            client.querySettings(type, recordingType)
                .map { setting: PmdSetting ->
                    mapPmdSettingsToPolarSettings(
                        setting,
                        fromSelected = false
                    )
                }
        } catch (e: Throwable) {
            Single.error(e)
        }
    }

    private fun queryFullSettings(
        identifier: String,
        type: PmdMeasurementType,
        recordingType: PmdRecordingType
    ): Single<PolarSensorSetting> {
        return try {
            val session = sessionPmdClientReady(identifier)
            val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient?
                ?: return Single.error(PolarServiceNotAvailable())
            client.queryFullSettings(type, recordingType)
                .map { setting: PmdSetting ->
                    mapPmdSettingsToPolarSettings(
                        setting,
                        fromSelected = false
                    )
                }
        } catch (e: Throwable) {
            Single.error(e)
        }
    }

    override fun foregroundEntered() {
        listener?.scanRestart()
    }

    override fun autoConnectToDevice(
        rssiLimit: Int,
        service: String?,
        timeout: Int,
        unit: TimeUnit,
        polarDeviceType: String?
    ): Completable {
        var start = 0L
        listener?.let {
            return Completable.create { emitter: CompletableEmitter ->
                if (service == null || service.matches(Regex("([0-9a-fA-F]{4})"))) {
                    emitter.onComplete()
                } else {
                    emitter.tryOnError(PolarInvalidArgument("Invalid service string format"))
                }
            }.andThen(
                it.search(false)
                    .filter { bleDeviceSession: BleDeviceSession ->
                        if (bleDeviceSession.medianRssi >= rssiLimit && bleDeviceSession.isConnectableAdvertisement
                            && (polarDeviceType == null || polarDeviceType == bleDeviceSession.polarDeviceType)
                            && (service == null || bleDeviceSession.advertisementContent.containsService(
                                service
                            ))
                        ) {
                            if (start == 0L) {
                                start = System.currentTimeMillis()
                            }
                            return@filter true
                        }
                        false
                    }
                    .timestamp()
                    .takeUntil { bleDeviceSessionTimed: Timed<BleDeviceSession> ->
                        val diff = bleDeviceSessionTimed.time(TimeUnit.MILLISECONDS) - start
                        diff >= unit.toMillis(timeout.toLong())
                    }
                    .reduce(
                        HashSet(),
                        BiFunction { objects: MutableSet<BleDeviceSession>, bleDeviceSessionTimed: Timed<BleDeviceSession> ->
                            objects.add(bleDeviceSessionTimed.value())
                            objects
                        } as BiFunction<MutableSet<BleDeviceSession>, Timed<BleDeviceSession>, MutableSet<BleDeviceSession>>)
                    .doOnSuccess { set: Set<BleDeviceSession> ->
                        val list: MutableList<BleDeviceSession> = ArrayList(set)
                        list.sortWith { s1: BleDeviceSession, s2: BleDeviceSession -> if (s1.rssi > s2.rssi) -1 else 1 }
                        openConnection(list[0])
                        log("auto connect search complete")
                    }
                    .toObservable()
                    .ignoreElements())
        }
        return Completable.error(PolarBleSdkInstanceException("PolarBleApi instance is shutdown"))
    }

    override fun autoConnectToDevice(
        rssiLimit: Int,
        service: String?,
        polarDeviceType: String?
    ): Completable {
        return autoConnectToDevice(rssiLimit, service, 2, TimeUnit.SECONDS, polarDeviceType)
    }

    @Throws(PolarInvalidArgument::class)
    override fun connectToDevice(identifier: String) {
        val session = fetchSession(identifier)
        if (session == null || session.sessionState == DeviceSessionState.SESSION_CLOSED) {
            if (connectSubscriptions.containsKey(identifier)) {
                connectSubscriptions[identifier]?.dispose()
                connectSubscriptions.remove(identifier)
            }
            if (session != null) {
                openConnection(session)
            } else {
                listener?.let {
                    connectSubscriptions[identifier] = it.search(false)
                        .filter { bleDeviceSession: BleDeviceSession -> if (identifier.contains(":")) bleDeviceSession.address == identifier else bleDeviceSession.polarDeviceId == identifier }
                        .take(1)
                        .observeOn(Schedulers.io())
                        .subscribe(
                            { session: BleDeviceSession -> openConnection(session) },
                            { error: Throwable -> logError("connect search error with device: " + identifier + " error: " + error.message) },
                            { log("connect search completed for $identifier") })
                }
            }
        }
    }

    @Throws(PolarInvalidArgument::class)
    override fun disconnectFromDevice(identifier: String) {
        val session = fetchSession(identifier)
        session?.let {
            if (session.sessionState == DeviceSessionState.SESSION_OPEN ||
                session.sessionState == DeviceSessionState.SESSION_OPENING ||
                session.sessionState == DeviceSessionState.SESSION_OPEN_PARK
            ) {
                listener?.closeSessionDirect(session)
            }
        }
        if (connectSubscriptions.containsKey(identifier)) {
            connectSubscriptions[identifier]?.dispose()
            connectSubscriptions.remove(identifier)
        }
    }

    override fun startRecording(
        identifier: String,
        exerciseId: String,
        interval: PolarH10OfflineExerciseApi.RecordingInterval?,
        type: PolarH10OfflineExerciseApi.SampleType
    ): Completable {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Completable.error(error)
        }
        if (isRecordingSupported(session.polarDeviceType)) {
            val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                ?: return Completable.error(PolarServiceNotAvailable())
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
            return client.query(
                PftpRequest.PbPFtpQuery.REQUEST_START_RECORDING_VALUE,
                params.toByteArray()
            )
                .toObservable()
                .ignoreElements()
                .onErrorResumeNext { throwable: Throwable -> Completable.error(handleError(throwable)) }
        } else {
            return Completable.error(PolarOperationNotSupported())
        }
    }

    override fun stopRecording(identifier: String): Completable {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Completable.error(error)
        }
        if (isRecordingSupported(session.polarDeviceType)) {
            val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                ?: return Completable.error(PolarServiceNotAvailable())
            return client.query(PftpRequest.PbPFtpQuery.REQUEST_STOP_RECORDING_VALUE, null)
                .toObservable()
                .ignoreElements()
                .onErrorResumeNext { throwable: Throwable -> Completable.error(handleError(throwable)) }

        } else return Completable.error(PolarOperationNotSupported())
    }

    override fun requestRecordingStatus(identifier: String): Single<androidx.core.util.Pair<Boolean, String>> {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Single.error(error)
        }

        if (isRecordingSupported(session.polarDeviceType)) {
            val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                ?: return Single.error(PolarServiceNotAvailable())
            return client.query(PftpRequest.PbPFtpQuery.REQUEST_RECORDING_STATUS_VALUE, null)
                .map { byteArrayOutputStream: ByteArrayOutputStream ->
                    val result =
                        PbRequestRecordingStatusResult.parseFrom(byteArrayOutputStream.toByteArray())
                    androidx.core.util.Pair(
                        result.recordingOn,
                        if (result.hasSampleDataIdentifier()) result.sampleDataIdentifier else ""
                    )
                }.onErrorResumeNext { throwable: Throwable -> Single.error(handleError(throwable)) }

        } else {
            return Single.error(PolarOperationNotSupported())
        }
    }

    override fun listOfflineRecordings(identifier: String): Flowable<PolarOfflineRecordingEntry> {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Flowable.error(error)
        }
        val client =
            session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                ?: return Flowable.error(PolarServiceNotAvailable())

        return when (getFileSystemType(session.polarDeviceType)) {
            FileSystemType.SAGRFC2_FILE_SYSTEM -> {
                BleLogger.d(TAG, "Start offline recording listing in device: $identifier")

                fetchRecursively(
                    client = client,
                    path = "/U/0/",
                    condition = { entry ->
                        entry.matches(Regex("^(\\d{8})(/)")) ||
                                entry == "R/" ||
                                entry.matches(Regex("^(\\d{6})(/)")) ||
                                entry.contains(".REC")
                    }
                )
                    .map { entry: Pair<String, Long> ->
                        val components = entry.first.split("/").toTypedArray()
                        val format = SimpleDateFormat("yyyyMMdd HHmmss", Locale.getDefault())
                        val date = format.parse(components[3] + " " + components[5])
                            ?: throw PolarInvalidArgument(
                                "Listing offline recording failed. Cannot parse create data from date ${components[3]} and time ${components[5]}"
                            )
                        val type = mapPmdClientFeatureToPolarFeature(
                            mapOfflineRecordingFileNameToMeasurementType(components[6])
                        )
                        PolarOfflineRecordingEntry(
                            path = entry.first,
                            size = entry.second,
                            date = date,
                            type = type
                        )
                    }
                    .groupBy { entry -> entry.date }
                    .onBackpressureBuffer(2048, null, BackpressureOverflowStrategy.DROP_LATEST)
                    .flatMap { groupedEntries ->
                        groupedEntries
                            .toList()
                            .flatMapPublisher { entriesList ->
                                var totalSize = 0
                                entriesList.forEach { (_, size) ->
                                    totalSize += size.toInt()
                                }
                                Flowable.fromIterable(
                                    entriesList.map {
                                        PolarOfflineRecordingEntry(
                                            path = it.path.replace(
                                                Regex("\\d+\\.REC$"),
                                                ".REC"
                                            ),
                                            size = totalSize.toLong(),
                                            date = it.date,
                                            type = it.type
                                        )
                                    }
                                )
                            }
                            .distinct { entry -> entry.date }
                            .onErrorResumeNext { throwable: Throwable ->
                                Flowable.error(handleError(throwable))
                            }
                    }
            }

            else -> Flowable.error(PolarServiceNotAvailable())
        }
    }

    override fun listExercises(identifier: String): Flowable<PolarExerciseEntry> {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Flowable.error(error)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: return Flowable.error(PolarServiceNotAvailable())

        when (getFileSystemType(session.polarDeviceType)) {
            FileSystemType.SAGRFC2_FILE_SYSTEM -> {
                return fetchRecursively(client = client,
                    path = "/U/0/",
                    condition = { entry ->
                        entry.matches(Regex("^([0-9]{8})(/)")) ||
                                entry.matches(Regex("^([0-9]{6})(/)")) ||
                                entry == "E/" ||
                                entry == "SAMPLES.BPB" ||
                                entry == "00/"
                    })
                    .map { entry: Pair<String, Long> ->
                        val components = entry.first.split("/").toTypedArray()
                        val format = SimpleDateFormat("yyyyMMdd HHmmss", Locale.getDefault())
                        val date = format.parse(components[3] + " " + components[5])
                        PolarExerciseEntry(entry.first, date, components[3] + components[5])
                    }
                    .onErrorResumeNext { throwable: Throwable ->
                        Flowable.error(
                            handleError(
                                throwable
                            )
                        )
                    }
            }

            FileSystemType.H10_FILE_SYSTEM -> {
                return fetchRecursively(client = client,
                    path = "/",
                    condition = { entry -> entry.endsWith("/") || entry == "SAMPLES.BPB" })
                    .map { entry: Pair<String, Long> ->
                        val components = entry.first.split("/").toTypedArray()
                        PolarExerciseEntry(entry.first, Date(), components[1])
                    }
                    .onErrorResumeNext { throwable: Throwable ->
                        Flowable.error(
                            handleError(
                                throwable
                            )
                        )
                    }
            }

            else -> return Flowable.error(PolarOperationNotSupported())
        }
    }

    override fun fetchExercise(
        identifier: String,
        entry: PolarExerciseEntry
    ): Single<PolarExerciseData> {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Single.error(error)
        }

        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: return Single.error(PolarServiceNotAvailable())
        val fsType = getFileSystemType(session.polarDeviceType)
        val beforeFetch = if (fsType == FileSystemType.H10_FILE_SYSTEM) {
            client.sendNotification(
                PftpNotification.PbPFtpHostToDevNotification.INITIALIZE_SESSION_VALUE,
                null
            )
                .andThen(
                    client.sendNotification(
                        PftpNotification.PbPFtpHostToDevNotification.START_SYNC_VALUE,
                        null
                    )
                )
        } else {
            Completable.complete()
        }

        val afterFetch = if (fsType == FileSystemType.H10_FILE_SYSTEM) {
            client.sendNotification(
                PftpNotification.PbPFtpHostToDevNotification.STOP_SYNC_VALUE,
                null
            )
                .andThen(
                    client.sendNotification(
                        PftpNotification.PbPFtpHostToDevNotification.TERMINATE_SESSION_VALUE,
                        null
                    )
                )
        } else {
            Completable.complete()
        }

        val builder = PftpRequest.PbPFtpOperation.newBuilder()
        builder.command = PftpRequest.PbPFtpOperation.Command.GET
        builder.path = entry.path

        return beforeFetch
            .andThen(client.request(builder.build().toByteArray()))
            .map { byteArrayOutputStream: ByteArrayOutputStream ->
                val samples = PbExerciseSamples.parseFrom(byteArrayOutputStream.toByteArray())
                if (samples.hasRrSamples()) {
                    return@map PolarExerciseData(
                        samples.recordingInterval.seconds,
                        samples.rrSamples.rrIntervalsList
                    )
                } else {
                    return@map PolarExerciseData(
                        samples.recordingInterval.seconds,
                        samples.heartRateSamplesList
                    )
                }
            }
            .onErrorResumeNext { throwable: Throwable -> Single.error(handleError(throwable)) }
            .doFinally {
                afterFetch
                    .onErrorComplete()
                    .subscribe()
            }
    }

    override fun getOfflineRecord(
        identifier: String,
        entry: PolarOfflineRecordingEntry,
        secret: PolarRecordingSecret?
    ): Single<PolarOfflineRecordingData> {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (e: Exception) {
            return Single.error(e)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: return Single.error(PolarServiceNotAvailable())
        val fsType = getFileSystemType(session.polarDeviceType)

        var polarAccData: PolarOfflineRecordingData.AccOfflineRecording? = null
        var polarGyroData: PolarOfflineRecordingData.GyroOfflineRecording? = null
        var polarMagData: PolarOfflineRecordingData.MagOfflineRecording? = null
        var polarPpgData: PolarOfflineRecordingData.PpgOfflineRecording? = null
        var polarPpiData: PolarOfflineRecordingData.PpiOfflineRecording? = null
        var polarHrData: PolarOfflineRecordingData.HrOfflineRecording? = null
        var polarTemperatureData: PolarOfflineRecordingData.TemperatureOfflineRecording? = null
        return if (fsType == FileSystemType.SAGRFC2_FILE_SYSTEM) {
            getSubRecordingCount(identifier, entry)
                .flatMap { count ->
                    Single.create<PolarOfflineRecordingData> { emitter ->
                        // Old format
                        if (count == 0) {
                            val builder = PftpRequest.PbPFtpOperation.newBuilder()
                            builder.command = PftpRequest.PbPFtpOperation.Command.GET
                            builder.path = entry.path

                            BleLogger.d(
                                TAG,
                                "Offline record get. Device: $identifier Path: ${entry.path} Secret used: ${secret != null}"
                            )
                            client.sendNotification(
                                PftpNotification.PbPFtpHostToDevNotification.INITIALIZE_SESSION_VALUE,
                                null
                            )
                                .andThen(client.request(builder.build().toByteArray()))
                                .map { byteArrayOutputStream: ByteArrayOutputStream ->
                                    val pmdSecret = secret?.let { mapPolarSecretToPmdSecret(it) }
                                    OfflineRecordingData.parseDataFromOfflineFile(
                                        byteArrayOutputStream.toByteArray(),
                                        mapPolarFeatureToPmdClientMeasurementType(entry.type),
                                        pmdSecret
                                    )
                                }
                                .map { offlineRecData ->
                                    val polarSettings = offlineRecData.recordingSettings?.let {
                                        mapPmdSettingsToPolarSettings(
                                            it,
                                            fromSelected = false
                                        )
                                    }
                                    val startTime = offlineRecData.startTime
                                    when (val offlineData = offlineRecData.data) {
                                        is AccData -> {
                                            polarSettings
                                                ?: throw PolarOfflineRecordingError("getOfflineRecord failed. Acc data is missing settings")
                                            PolarOfflineRecordingData.AccOfflineRecording(
                                                mapPmdClientAccDataToPolarAcc(offlineData),
                                                startTime,
                                                polarSettings
                                            )
                                        }

                                        is GyrData -> {
                                            polarSettings
                                                ?: throw PolarOfflineRecordingError("getOfflineRecord failed. Gyro data is missing settings")
                                            PolarOfflineRecordingData.GyroOfflineRecording(
                                                mapPmdClientGyroDataToPolarGyro(offlineData),
                                                startTime,
                                                polarSettings
                                            )
                                        }

                                        is MagData -> {
                                            polarSettings
                                                ?: throw PolarOfflineRecordingError("getOfflineRecord failed. Magnetometer data is missing settings")
                                            PolarOfflineRecordingData.MagOfflineRecording(
                                                mapPmdClientMagDataToPolarMagnetometer(offlineData),
                                                startTime,
                                                polarSettings
                                            )
                                        }

                                        is PpgData -> {
                                            polarSettings
                                                ?: throw PolarOfflineRecordingError("getOfflineRecord failed. Ppg data is missing settings")
                                            PolarOfflineRecordingData.PpgOfflineRecording(
                                                mapPMDClientPpgDataToPolarPpg(offlineData),
                                                startTime,
                                                polarSettings
                                            )
                                        }

                                        is PpiData -> PolarOfflineRecordingData.PpiOfflineRecording(
                                            mapPMDClientPpiDataToPolarPpiData(offlineData),
                                            startTime
                                        )

                                        is OfflineHrData -> PolarOfflineRecordingData.HrOfflineRecording(
                                            mapPMDClientOfflineHrDataToPolarHrData(offlineData),
                                            startTime
                                        )

                                        is TemperatureData -> PolarOfflineRecordingData.TemperatureOfflineRecording(
                                            mapPMDClientOfflineTemperatureDataToPolarTemperatureData(
                                                offlineData
                                            ), startTime
                                        )

                                        else -> throw PolarOfflineRecordingError("Data type is not supported.")
                                    }
                                }.onErrorResumeNext { throwable: Throwable ->
                                    Single.error(handleError(throwable))
                                }
                                .doFinally {
                                    client.sendNotification(
                                        PftpNotification.PbPFtpHostToDevNotification.TERMINATE_SESSION_VALUE,
                                        null
                                    )
                                        .onErrorComplete()
                                        .subscribe()
                                }
                                .subscribe { polarOfflineRecordingData ->
                                    emitter.onSuccess(
                                        polarOfflineRecordingData
                                    )
                                }
                        }
                        var lastTimestamp = 0uL
                        Observable.fromIterable(0 until count)
                            .concatMapSingle { subRecordingIndex ->
                                val subRecordingPath = if (entry.path.matches(Regex(".*\\.REC$"))) {
                                    entry.path.replace(
                                        Regex("(\\.REC)$"),
                                        "$subRecordingIndex.REC"
                                    )
                                } else {
                                    entry.path.replace(
                                        Regex("""\d(?=\D*$)"""),
                                        subRecordingIndex.toString()
                                    )
                                }

                                val builder = PftpRequest.PbPFtpOperation.newBuilder()
                                builder.command = PftpRequest.PbPFtpOperation.Command.GET
                                builder.path = subRecordingPath.ifBlank {
                                    entry.path
                                }

                                BleLogger.d(
                                    TAG,
                                    "Offline record get. Device: $identifier Path: ${builder.path} Secret used: ${secret != null}, lastTimestamp: $lastTimestamp"
                                )

                                client.sendNotification(
                                    PftpNotification.PbPFtpHostToDevNotification.INITIALIZE_SESSION_VALUE,
                                    null
                                )
                                    .andThen(client.request(builder.build().toByteArray()))
                                    .flatMap { byteArrayOutputStream ->
                                        val pmdSecret =
                                            secret?.let { mapPolarSecretToPmdSecret(it) }

                                        val offlineRecordingData =
                                            OfflineRecordingData.parseDataFromOfflineFile(
                                                byteArrayOutputStream.toByteArray(),
                                                mapPolarFeatureToPmdClientMeasurementType(entry.type),
                                                pmdSecret,
                                                lastTimestamp
                                            )

                                        when (val offlineData = offlineRecordingData.data) {
                                            is AccData -> {
                                                val polarSettings =
                                                    offlineRecordingData.recordingSettings?.let {
                                                        mapPmdSettingsToPolarSettings(
                                                            it,
                                                            fromSelected = false
                                                        )
                                                    }
                                                        ?: throw PolarOfflineRecordingError("getOfflineRecord failed. Acc data is missing settings")

                                                val polarAcc =
                                                    mapPmdClientAccDataToPolarAcc(offlineData)
                                                lastTimestamp =
                                                    polarAcc.samples.last().timeStamp.toULong()
                                                polarAccData?.let { existingData ->
                                                    polarAccData = existingData.appendAccData(
                                                        existingData,
                                                        polarAcc,
                                                        polarSettings
                                                    )
                                                } ?: run {
                                                    polarAccData =
                                                        PolarOfflineRecordingData.AccOfflineRecording(
                                                            polarAcc,
                                                            offlineRecordingData.startTime,
                                                            polarSettings
                                                        )
                                                }
                                            }

                                            is GyrData -> {
                                                val polarSettings =
                                                    offlineRecordingData.recordingSettings?.let {
                                                        mapPmdSettingsToPolarSettings(
                                                            it,
                                                            fromSelected = false
                                                        )
                                                    }
                                                        ?: throw PolarOfflineRecordingError("getOfflineRecord failed. Gyro data is missing settings")

                                                val polarGyr =
                                                    mapPmdClientGyroDataToPolarGyro(offlineData)
                                                lastTimestamp =
                                                    polarGyr.samples.last().timeStamp.toULong()
                                                polarGyroData?.let { existingData ->
                                                    polarGyroData = existingData.appendGyroData(
                                                        existingData,
                                                        polarGyr,
                                                        polarSettings
                                                    )
                                                } ?: run {
                                                    polarGyroData =
                                                        PolarOfflineRecordingData.GyroOfflineRecording(
                                                            polarGyr,
                                                            offlineRecordingData.startTime,
                                                            polarSettings
                                                        )
                                                }
                                            }

                                            is MagData -> {
                                                val polarSettings =
                                                    offlineRecordingData.recordingSettings?.let {
                                                        mapPmdSettingsToPolarSettings(
                                                            it,
                                                            fromSelected = false
                                                        )
                                                    }
                                                        ?: throw PolarOfflineRecordingError("getOfflineRecord failed. Magnetometer data is missing settings")

                                                val polarMag =
                                                    mapPmdClientMagDataToPolarMagnetometer(
                                                        offlineData
                                                    )
                                                lastTimestamp =
                                                    polarMag.samples.last().timeStamp.toULong()
                                                polarMagData?.let { existingData ->
                                                    polarMagData = existingData.appendMagData(
                                                        existingData,
                                                        polarMag
                                                    )
                                                } ?: run {
                                                    polarMagData =
                                                        PolarOfflineRecordingData.MagOfflineRecording(
                                                            polarMag,
                                                            offlineRecordingData.startTime,
                                                            polarSettings
                                                        )
                                                }
                                            }

                                            is PpgData -> {
                                                val polarSettings =
                                                    offlineRecordingData.recordingSettings?.let {
                                                        mapPmdSettingsToPolarSettings(
                                                            it,
                                                            fromSelected = false
                                                        )
                                                    }
                                                        ?: throw PolarOfflineRecordingError("getOfflineRecord failed. Ppg data is missing settings")

                                                val polarPpg =
                                                    mapPMDClientPpgDataToPolarPpg(offlineData)
                                                lastTimestamp =
                                                    polarPpg.samples.last().timeStamp.toULong()
                                                polarPpgData?.let { existingData ->
                                                    polarPpgData = existingData.appendPpgData(
                                                        existingData,
                                                        polarPpg
                                                    )
                                                } ?: run {
                                                    polarPpgData =
                                                        PolarOfflineRecordingData.PpgOfflineRecording(
                                                            polarPpg,
                                                            offlineRecordingData.startTime,
                                                            polarSettings
                                                        )
                                                }
                                            }

                                            is PpiData -> {
                                                if (polarPpiData == null) {
                                                    polarPpiData =
                                                        PolarOfflineRecordingData.PpiOfflineRecording(
                                                            mapPMDClientPpiDataToPolarPpiData(
                                                                offlineData
                                                            ),
                                                            offlineRecordingData.startTime
                                                        )
                                                } else {
                                                    val existingData = polarPpiData

                                                    polarPpiData = existingData?.appendPpiData(
                                                        existingData,
                                                        PolarOfflineRecordingData.PpiOfflineRecording(
                                                            mapPMDClientPpiDataToPolarPpiData(
                                                                offlineData
                                                            ),
                                                            offlineRecordingData.startTime
                                                        ).data
                                                    )
                                                }
                                            }

                                            is OfflineHrData -> {
                                                polarHrData?.let { existingData ->
                                                    polarHrData = existingData.appendHrData(
                                                        existingData,
                                                        mapPMDClientOfflineHrDataToPolarHrData(
                                                            offlineData
                                                        )
                                                    )
                                                } ?: run {
                                                    polarHrData =
                                                        PolarOfflineRecordingData.HrOfflineRecording(
                                                            mapPMDClientOfflineHrDataToPolarHrData(
                                                                offlineData
                                                            ),
                                                            offlineRecordingData.startTime
                                                        )
                                                }
                                            }

                                            is TemperatureData -> {
                                                polarTemperatureData?.let { existingData ->
                                                    polarTemperatureData =
                                                        existingData.appendTemperatureData(
                                                            existingData,
                                                            mapPMDClientOfflineTemperatureDataToPolarTemperatureData(
                                                                offlineData
                                                            )
                                                        )
                                                } ?: run {
                                                    polarTemperatureData =
                                                        PolarOfflineRecordingData.TemperatureOfflineRecording(
                                                            mapPMDClientOfflineTemperatureDataToPolarTemperatureData(
                                                                offlineData
                                                            ),
                                                            offlineRecordingData.startTime
                                                        )
                                                }
                                            }

                                            else -> throw PolarOfflineRecordingError("Data type is not supported.")
                                        }
                                        Single.just(true)
                                    }
                            }
                            .toList()
                            .subscribe(
                                {
                                    polarPpiData?.let {
                                        emitter.onSuccess(
                                            polarPpiData as PolarOfflineRecordingData
                                        )
                                    }

                                    polarPpgData?.let {
                                        emitter.onSuccess(
                                            polarPpgData as PolarOfflineRecordingData
                                        )
                                    }

                                    polarAccData?.let {
                                        emitter.onSuccess(
                                            polarAccData as PolarOfflineRecordingData
                                        )
                                    }

                                    polarGyroData?.let {
                                        emitter.onSuccess(
                                            polarGyroData as PolarOfflineRecordingData
                                        )
                                    }

                                    polarMagData?.let {
                                        emitter.onSuccess(
                                            polarMagData as PolarOfflineRecordingData
                                        )
                                    }

                                    polarHrData?.let {
                                        emitter.onSuccess(
                                            polarHrData as PolarOfflineRecordingData
                                        )
                                    }

                                    polarTemperatureData?.let {
                                        emitter.onSuccess(
                                            polarTemperatureData as PolarOfflineRecordingData
                                        )
                                    }
                                },
                                { throwable ->
                                    emitter.onError(throwable)
                                }
                            )
                    }
                }
        } else {
            Single.error(PolarOperationNotSupported())
        }
    }

    private fun getSubRecordingCount(
        identifier: String,
        entry: PolarOfflineRecordingEntry
    ): Single<Int> {
        return Single.defer {
            try {
                val session = sessionPsFtpClientReady(identifier)
                val client =
                    session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                        ?: throw PolarServiceNotAvailable()

                val builder = PftpRequest.PbPFtpOperation.newBuilder()
                builder.command = PftpRequest.PbPFtpOperation.Command.GET
                val directoryPath = entry.path.substring(0, entry.path.lastIndexOf("/") + 1)
                builder.path = directoryPath

                client.request(builder.build().toByteArray())
                    .map { byteArrayOutputStream ->
                        val directory =
                            PbPFtpDirectory.parseFrom(byteArrayOutputStream.toByteArray())
                        val prefix = entry.path.substringAfterLast("/").substringBefore(".REC")
                        val matchingEntries = directory.entriesList.filter {
                            it.name.startsWith(prefix) && Regex("\\d\\.").containsMatchIn(it.name)
                        }
                        matchingEntries.size
                    }
                    .onErrorResumeNext { throwable: Throwable ->
                        Single.error(throwable)
                    }
            } catch (error: Throwable) {
                Single.error(error)
            }
        }
    }

    override fun listSplitOfflineRecordings(identifier: String): Flowable<PolarOfflineRecordingEntry> {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Flowable.error(error)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: return Flowable.error(PolarServiceNotAvailable())

        when (getFileSystemType(session.polarDeviceType)) {
            FileSystemType.SAGRFC2_FILE_SYSTEM -> {
                BleLogger.d(TAG, "Start split offline recording listing in device: $identifier")
                return fetchRecursively(
                    client = client,
                    path = "/U/0/",
                    condition = { entry ->
                        entry.matches(Regex("^(\\d{8})(/)")) ||
                                entry == "R/" ||
                                entry.matches(Regex("^(\\d{6})(/)")) ||
                                entry.contains(".REC")
                    }
                ).map { entry: Pair<String, Long> ->
                    val components = entry.first.split("/").toTypedArray()
                    val format = SimpleDateFormat("yyyyMMdd HHmmss", Locale.getDefault())
                    val date = format.parse(components[3] + " " + components[5])
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
                }.onErrorResumeNext { throwable: Throwable ->
                    Flowable.error(
                        handleError(
                            throwable
                        )
                    )
                }
            }

            else -> return Flowable.error(PolarOperationNotSupported())
        }
    }

    override fun getSplitOfflineRecord(
        identifier: String,
        entry: PolarOfflineRecordingEntry,
        secret: PolarRecordingSecret?
    ): Single<PolarOfflineRecordingData> {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (e: Exception) {
            return Single.error(e)
        }

        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: return Single.error(PolarServiceNotAvailable())
        val fsType = getFileSystemType(session.polarDeviceType)
        return if (fsType == FileSystemType.SAGRFC2_FILE_SYSTEM) {
            val builder = PftpRequest.PbPFtpOperation.newBuilder()
            builder.command = PftpRequest.PbPFtpOperation.Command.GET
            builder.path = entry.path

            BleLogger.d(
                TAG,
                "Split offline record get. Device: $identifier Path: ${entry.path} Secret used: ${secret != null}"
            )
            client.sendNotification(
                PftpNotification.PbPFtpHostToDevNotification.INITIALIZE_SESSION_VALUE,
                null
            )
                .andThen(client.request(builder.build().toByteArray()))
                .map { byteArrayOutputStream: ByteArrayOutputStream ->
                    val pmdSecret = secret?.let { mapPolarSecretToPmdSecret(it) }
                    OfflineRecordingData.parseDataFromOfflineFile(
                        byteArrayOutputStream.toByteArray(),
                        mapPolarFeatureToPmdClientMeasurementType(entry.type),
                        pmdSecret
                    )
                }
                .map { offlineRecData ->
                    val polarSettings = offlineRecData.recordingSettings?.let {
                        mapPmdSettingsToPolarSettings(
                            it,
                            fromSelected = false
                        )
                    }
                    val startTime = offlineRecData.startTime
                    when (val offlineData = offlineRecData.data) {
                        is AccData -> {
                            polarSettings
                                ?: throw PolarOfflineRecordingError("getSplitOfflineRecord failed. Acc data is missing settings")
                            PolarOfflineRecordingData.AccOfflineRecording(
                                mapPmdClientAccDataToPolarAcc(offlineData),
                                startTime,
                                polarSettings
                            )
                        }

                        is GyrData -> {
                            polarSettings
                                ?: throw PolarOfflineRecordingError("getSplitOfflineRecord failed. Gyro data is missing settings")
                            PolarOfflineRecordingData.GyroOfflineRecording(
                                mapPmdClientGyroDataToPolarGyro(offlineData),
                                startTime,
                                polarSettings
                            )
                        }

                        is MagData -> {
                            polarSettings
                                ?: throw PolarOfflineRecordingError("getSplitOfflineRecord failed. Magnetometer data is missing settings")
                            PolarOfflineRecordingData.MagOfflineRecording(
                                mapPmdClientMagDataToPolarMagnetometer(offlineData),
                                startTime,
                                polarSettings
                            )
                        }

                        is PpgData -> {
                            polarSettings
                                ?: throw PolarOfflineRecordingError("getSplitOfflineRecord failed. Ppg data is missing settings")
                            PolarOfflineRecordingData.PpgOfflineRecording(
                                mapPMDClientPpgDataToPolarPpg(offlineData),
                                startTime,
                                polarSettings
                            )
                        }

                        is PpiData -> PolarOfflineRecordingData.PpiOfflineRecording(
                            mapPMDClientPpiDataToPolarPpiData(offlineData),
                            startTime
                        )

                        is OfflineHrData -> PolarOfflineRecordingData.HrOfflineRecording(
                            mapPMDClientOfflineHrDataToPolarHrData(offlineData),
                            startTime
                        )

                        is TemperatureData -> PolarOfflineRecordingData.TemperatureOfflineRecording(
                            mapPMDClientOfflineTemperatureDataToPolarTemperatureData(offlineData),
                            startTime
                        )

                        else -> throw PolarOfflineRecordingError("getSplitOfflineRecord failed. Data type is not supported.")
                    }
                }.onErrorResumeNext { throwable: Throwable -> Single.error(handleError(throwable)) }
                .doFinally {
                    client.sendNotification(
                        PftpNotification.PbPFtpHostToDevNotification.TERMINATE_SESSION_VALUE,
                        null
                    )
                        .onErrorComplete()
                        .subscribe()
                }
        } else Single.error(PolarOperationNotSupported())
    }

    override fun removeExercise(identifier: String, entry: PolarExerciseEntry): Completable {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Completable.error(error)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: return Completable.error(PolarServiceNotAvailable())

        when (getFileSystemType(session.polarDeviceType)) {
            FileSystemType.SAGRFC2_FILE_SYSTEM -> {
                val builder = PftpRequest.PbPFtpOperation.newBuilder()
                builder.command = PftpRequest.PbPFtpOperation.Command.GET
                val components = entry.path.split("/").toTypedArray()
                val exerciseParent = "/U/0/" + components[3] + "/E/"
                builder.path = exerciseParent

                return client.request(builder.build().toByteArray())
                    .flatMap { byteArrayOutputStream: ByteArrayOutputStream ->
                        val directory =
                            PbPFtpDirectory.parseFrom(byteArrayOutputStream.toByteArray())
                        val removeBuilder = PftpRequest.PbPFtpOperation.newBuilder()
                        removeBuilder.command = PftpRequest.PbPFtpOperation.Command.REMOVE
                        if (directory.entriesCount <= 1) {
                            // remove entire directory
                            removeBuilder.path = "/U/0/" + components[3] + "/"
                        } else {
                            // remove only exercise
                            removeBuilder.path =
                                "/U/0/" + components[3] + "/E/" + components[5] + "/"
                        }
                        client.request(removeBuilder.build().toByteArray())
                    }
                    .toObservable()
                    .ignoreElements()
                    .onErrorResumeNext { throwable: Throwable ->
                        Completable.error(
                            handleError(
                                throwable
                            )
                        )
                    }
            }

            FileSystemType.H10_FILE_SYSTEM -> {
                val builder = PftpRequest.PbPFtpOperation.newBuilder()
                builder.command = PftpRequest.PbPFtpOperation.Command.REMOVE
                builder.path = entry.path
                return client.request(builder.build().toByteArray())
                    .toObservable()
                    .ignoreElements()
                    .onErrorResumeNext { throwable: Throwable ->
                        Completable.error(
                            handleError(
                                throwable
                            )
                        )
                    }
            }

            FileSystemType.UNKNOWN_FILE_SYSTEM -> {
                return Completable.error(PolarOperationNotSupported())
            }
        }
    }

    override fun removeOfflineRecord(
        identifier: String,
        entry: PolarOfflineRecordingEntry
    ): Completable {
        val fileDeletionMap: MutableMap<String, Boolean> = mutableMapOf()
        BleLogger.d(TAG, "Remove offline record from device $identifier path ${entry.path}")
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Completable.error(error)
        }

        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: return Completable.error(PolarServiceNotAvailable())
        val fsType = getFileSystemType(session.polarDeviceType)
        return if (fsType == FileSystemType.SAGRFC2_FILE_SYSTEM) {
            getSubRecordingCount(identifier, entry)
                .flatMap { count ->
                    if (count == 0 || entry.path.contains(Regex("""(\D+)(\d+)\.REC"""))) {
                        val builder = PftpRequest.PbPFtpOperation.newBuilder()
                        builder.command = PftpRequest.PbPFtpOperation.Command.REMOVE
                        builder.path = entry.path
                        return@flatMap client.request(builder.build().toByteArray())
                    } else {
                        Observable.fromIterable(0 until count)
                            .flatMap { subRecordingIndex ->
                                val recordingPath = entry.path.replace(
                                    Regex("(\\.REC)$"),
                                    "$subRecordingIndex.REC"
                                )

                                fileDeletionMap.put(
                                    listOf(
                                        recordingPath.split("/")
                                            .subList(
                                                0, recordingPath.split("/")
                                                    .lastIndex - 1
                                            )
                                    )[0].joinToString(separator = "/"), false
                                )
                                val builder = PftpRequest.PbPFtpOperation.newBuilder()
                                builder.command = PftpRequest.PbPFtpOperation.Command.REMOVE
                                builder.path = recordingPath

                                client.request(builder.build().toByteArray()).toObservable()
                            }.ignoreElements().toSingleDefault(count)
                    }
                }.doFinally {
                    val dir = "/U/0"
                    var fileList: MutableList<String> = mutableListOf()
                    listFiles(identifier, dir,
                        condition = { entry: String ->
                            entry.matches(Regex("^(\\d{8})(/)")) ||
                                    entry.matches(Regex("^(\\d{6})(/)")) ||
                                    entry == "R/" ||
                                    entry.contains(".REC") &&
                                    !entry.contains(".BPB") &&
                                    !entry.contains("HIST")
                        })
                        .map {
                            fileList.add(it)
                        }
                        .doFinally {
                            if (fileList.isEmpty()) {
                                deleteDataDirectories(identifier).subscribe()
                            }
                        }
                       .doOnError { error ->
                            BleLogger.e(
                                TAG,
                                "Failed to list files from directory $dir from device $identifier. Error: $error"
                            )
                            Completable.error(error)
                        }
                        .subscribe()
                }.ignoreElement()
        } else {
            Completable.error(PolarOperationNotSupported())
        }
    }

    private fun removeOfflineFilesRecursively(
        client: BlePsFtpClient,
        deletePath: String,
        whileContaining: Regex? = null
    ): Completable {
        require(whileContaining?.let { deletePath.contains(it) } ?: true) {
            Completable.error(PolarOfflineRecordingError(detailMessage = "Not valid offline recording path to delete $deletePath"))
        }

        val parentDir = if (deletePath.last() == '/') {
            deletePath.substringBeforeLast("/").dropLastWhile { it != '/' }
        } else {
            deletePath.dropLastWhile { it != '/' }
        }

        val builder = PftpRequest.PbPFtpOperation.newBuilder()
        builder.command = PftpRequest.PbPFtpOperation.Command.GET
        builder.path = parentDir

        return client.request(builder.build().toByteArray())
            .toFlowable()
            .flatMapCompletable { byteArrayOutputStream: ByteArrayOutputStream ->
                val parentDirEntries =
                    PbPFtpDirectory.parseFrom(byteArrayOutputStream.toByteArray())
                val isParentDirValid = whileContaining?.let { parentDir.contains(it) } ?: true

                if (parentDirEntries.entriesCount <= 1 && isParentDirValid) {
                    // the parent directory is valid to be deleted
                    return@flatMapCompletable removeOfflineFilesRecursively(
                        client,
                        parentDir,
                        whileContaining
                    )
                } else {
                    val removeBuilder = PftpRequest.PbPFtpOperation.newBuilder()
                    removeBuilder.command = PftpRequest.PbPFtpOperation.Command.REMOVE
                    removeBuilder.path = deletePath
                    BleLogger.d(TAG, "Remove offline recording from the path $deletePath")
                    return@flatMapCompletable client.request(removeBuilder.build().toByteArray())
                        .toObservable().ignoreElements()
                }
            }
    }

    override fun searchForDevice(): Flowable<PolarDeviceInfo> {
        listener?.let {
            return it.search(false)
                .distinct()
                .map { bleDeviceSession: BleDeviceSession ->
                    PolarDeviceInfo(
                        deviceId = bleDeviceSession.polarDeviceId,
                        address = bleDeviceSession.address,
                        rssi = bleDeviceSession.rssi,
                        name = bleDeviceSession.name,
                        isConnectable = bleDeviceSession.isConnectableAdvertisement,
                        hasHeartRateService = bleDeviceSession.advertisementContent.containsService(
                            HR_SERVICE_16BIT_UUID
                        ),
                        hasFileSystemService = bleDeviceSession.advertisementContent.containsService(
                            PFTP_SERVICE_16BIT_UUID
                        ),
                    )
                }
        }
        return Flowable.error(PolarBleSdkInstanceException("PolarBleApi instance is shutdown"))
    }

    override fun startListenForPolarHrBroadcasts(deviceIds: Set<String>?): Flowable<PolarHrBroadcastData> {
        listener?.let {
            BleLogger.d(TAG, "Start Hr broadcast listener. Filtering: ${deviceIds != null}")
            return it.search(false)
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
                            bleDeviceSession.address,
                            bleDeviceSession.rssi,
                            bleDeviceSession.name,
                            bleDeviceSession.isConnectableAdvertisement
                        ),
                        advertisement.hrForDisplay,
                        advertisement.batteryStatus != 0
                    )
                }
        }
        return Flowable.error(PolarBleSdkInstanceException("PolarBleApi instance is shutdown"))
    }

    override fun getDiskSpace(identifier: String): Single<PolarDiskSpaceData> {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Single.error(error)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: return Single.error(PolarServiceNotAvailable())
        return client.query(PftpRequest.PbPFtpQuery.GET_DISK_SPACE_VALUE, null)
            .map {
                val proto = PftpResponse.PbPFtpDiskSpaceResult.parseFrom(it.toByteArray())
                PolarDiskSpaceData.fromProto(proto)
            }.onErrorResumeNext {
                if (it is PftpResponseError && it.error == 201) {
                    Single.error(BleNotSupported("${session.name} do not support getDiskSpace"))
                } else {
                    Single.error(it)
                }
            }
    }

    override fun setLedConfig(identifier: String, ledConfig: LedConfig): Completable {
        return Completable.create { emitter ->
            try {
                val session = sessionPsFtpClientReady(identifier)
                val client =
                    session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                        ?: throw PolarServiceNotAvailable()
                val builder = PftpRequest.PbPFtpOperation.newBuilder()
                builder.command = PftpRequest.PbPFtpOperation.Command.PUT
                builder.path = LedConfig.LED_CONFIG_FILENAME
                val sdkModeLedByte =
                    if (ledConfig.sdkModeLedEnabled) LedConfig.LED_ANIMATION_ENABLE_BYTE else LedConfig.LED_ANIMATION_DISABLE_BYTE
                val ppiModeLedByte =
                    if (ledConfig.ppiModeLedEnabled) LedConfig.LED_ANIMATION_ENABLE_BYTE else LedConfig.LED_ANIMATION_DISABLE_BYTE
                val data = ByteArrayInputStream(byteArrayOf(sdkModeLedByte, ppiModeLedByte))

                client.write(builder.build().toByteArray(), data)
                    .doOnError { error ->
                        emitter.onError(error)
                    }
                    .subscribe()
                emitter.onComplete()
            } catch (error: Throwable) {
                BleLogger.e(TAG, "setLedConfig() error: $error")
                emitter.onError(error)
            }
        }
    }

    override fun listRestApiServices(identifier: String): Single<PolarDeviceRestApiServices> {
        val observable: Single<PolarDeviceRestApiServices> =
            getJSONDecodableFromPath<PolarDeviceRestApiServices>(
                identifier = identifier,
                path = "/REST/SERVICE.API",
                mapper = { jsonString ->
                    val map: Map<String, Any> = Gson().fromJson(jsonString, object: TypeToken<Map<String,Any>>() {}.type)
                    val services = PolarDeviceRestApiServices(map)
                    services
                }
            )
        return observable
    }

    override fun getRestApiDescription(identifier: String, path: String): Single<PolarDeviceRestApiServiceDescription> {
        val observable: Single<PolarDeviceRestApiServiceDescription> =
            getJSONMapFromPath(identifier = identifier, path = path)
                .map { map ->
                    val description = PolarDeviceRestApiServiceDescription(map)
                    description
                }
        return observable
    }

    private fun getJSONMapFromPath(identifier: String, path: String): Single<Map<String,Any>> {
        return getJSONDecodableFromPath(
            identifier = identifier,
            path = path,
            mapper =  { jsonString ->
                Gson().fromJson(jsonString, object: TypeToken<Map<String,Any>>() {}.type)
            }
        )
    }

    private fun <T:Any> getJSONDecodableFromPath(identifier: String, path: String, mapper:((jsonString: String) -> T)): Single<T> {
        return getFile(identifier = identifier, path = path)
            .map { byteArray ->
                mapper(byteArray.toString(kotlin.text.Charsets.UTF_8))
            }
    }

    override fun getFile(identifier: String, path: String): Single<ByteArray> {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Single.error(error)
        }

        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: return Single.error(PolarServiceNotAvailable())
        return when (getFileSystemType(session.polarDeviceType)) {
            FileSystemType.SAGRFC2_FILE_SYSTEM -> {
                val builder = PftpRequest.PbPFtpOperation.newBuilder()
                builder.command = PftpRequest.PbPFtpOperation.Command.GET
                builder.path = path
                return client.request(builder.build().toByteArray())
                    .map {
                        it.toByteArray()
                    }.onErrorResumeNext { throwable: Throwable ->
                        Single.error(handleError(throwable))
                    }
            }
            else -> Single.error(PolarOperationNotSupported())
        }
    }

    override fun <T : RestApiEventPayload>receiveRestApiEvents (identifier: String, mapper:((jsonString: String) -> T)): Flowable<List<T>> {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Flowable.error(error)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: return Flowable.error(PolarServiceNotAvailable())
        return client.receiveRestApiEvents(identifier = identifier).map { list ->
            list.map(mapper)
        }
    }

    override fun putNotification(identifier: String, notification: String, path: String): Completable {
        return pFtpPutOperation(
            identifier = identifier,
            path = path,
            data = notification.toByteArray()
        )
    }

    private fun pFtpPutOperation(identifier: String, path: String, data: ByteArray): Completable {
        return pFtpWriteOperation(
            identifier = identifier,
            command = PftpRequest.PbPFtpOperation.Command.PUT,
            path = path,
            data = data
        )
    }

    private fun pFtpWriteOperation(identifier: String,
                                    command: PftpRequest.PbPFtpOperation.Command,
                                    data: ByteArray,
                                    path: String): Completable {
        return Completable.create { emitter ->
            try {
                val session = sessionPsFtpClientReady(identifier)
                val client =
                    session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                        ?: throw PolarServiceNotAvailable()
                val builder = PftpRequest.PbPFtpOperation.newBuilder()
                builder.command = command
                builder.path = path
                val dataInputStream = ByteArrayInputStream(data)

                sendInitializationAndStartSyncNotifications(client)

                client.write(builder.build().toByteArray(), dataInputStream)
                    .subscribe({
                        BleLogger.d(TAG, "pFtpWriteOperation client write progress $it: $path")
                    },{ error ->
                        BleLogger.e(TAG, "pFtpWriteOperation() client write $path error: $error")
                        emitter.onError(error)
                    },{
                        BleLogger.d(TAG, "pFtpWriteOperation client write completed for $path")
                        emitter.onComplete()
                    })
            } catch (error: Throwable) {
                BleLogger.e(TAG, "pFtpWriteOperation() $path error: $error")
                emitter.onError(error)
            }
        }
    }

    override fun doFactoryReset(identifier: String, preservePairingInformation: Boolean): Completable {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Completable.error(error)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return Completable.error(PolarServiceNotAvailable())
        val params = PftpNotification.PbPFtpFactoryResetParams.newBuilder()
        params.sleep = false
        params.otaFwupdate = preservePairingInformation
        BleLogger.d(TAG, "send factory reset notification to device $identifier")
        return client.sendNotification(PftpNotification.PbPFtpHostToDevNotification.RESET.ordinal, params.build().toByteArray())
    }

    override fun doRestart(identifier: String): Completable {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Completable.error(error)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: return Completable.error(PolarServiceNotAvailable())
        val params = PftpNotification.PbPFtpFactoryResetParams.newBuilder()
        params.sleep = false
        params.doFactoryDefaults = false
        BleLogger.d(TAG, "send restart notification to device $identifier")
        return client.sendNotification(PftpNotification.PbPFtpHostToDevNotification.RESET.ordinal, params.build().toByteArray())
    }

    override fun doFirstTimeUse(identifier: String, ftuConfig: PolarFirstTimeUseConfig): Completable {
        return Completable.defer {
            try {
                BleLogger.d(TAG, "doFirstTimeUse(identifier: $identifier): started")

                val session = sessionPsFtpClientReady(identifier)
                val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                        ?: throw PolarServiceNotAvailable()

                val ftuBuilder = PftpRequest.PbPFtpOperation.newBuilder().apply {
                    command = PftpRequest.PbPFtpOperation.Command.PUT
                    path = PolarFirstTimeUseConfig.FTU_CONFIG_FILENAME
                }

                val ftuData = ByteArrayOutputStream().use { baos ->
                    ftuConfig.toProto().writeTo(baos)
                    baos.toByteArray()
                }

                val ftuInputStream = ByteArrayInputStream(ftuData)
                return@defer client.write(ftuBuilder.build().toByteArray(), ftuInputStream)
                        .concatWith(
                                Completable.defer {
                                    try {
                                        val userIdBuilder = PftpRequest.PbPFtpOperation.newBuilder().apply {
                                            command = PftpRequest.PbPFtpOperation.Command.PUT
                                            path = UserIdentifierType.USER_IDENTIFIER_FILENAME
                                        }

                                        val userIdentifier = UserIdentifierType.create()
                                        val protoUserIdentifier = userIdentifier.toProto()

                                        val userIdData = ByteArrayOutputStream().use { baos ->
                                            protoUserIdentifier.writeTo(baos)
                                            baos.toByteArray()
                                        }

                                        BleLogger.d(TAG, "doFirstTimeUse(identifier: $identifier): write user identifier")

                                        val userIdInputStream = ByteArrayInputStream(userIdData)
                                        client.write(userIdBuilder.build().toByteArray(), userIdInputStream)
                                                .ignoreElements()
                                                .concatWith(
                                                        Completable.defer {
                                                            val calendar = Calendar.getInstance().apply {
                                                                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                                                                isoFormat.timeZone = TimeZone.getTimeZone("UTC")
                                                                time = try {
                                                                    isoFormat.parse(ftuConfig.deviceTime)
                                                                } catch (e: ParseException) {
                                                                    throw IllegalArgumentException("Invalid deviceTime format: ${ftuConfig.deviceTime}", e)
                                                                }
                                                            }
                                                            BleLogger.d(TAG, "doFirstTimeUse(identifier: $identifier): set local time")
                                                            setLocalTime(identifier, calendar)
                                                        }
                                                )
                                    } catch (error: Throwable) {
                                        BleLogger.e(TAG, "doFirstTimeUse(identifier: $identifier): write user identifier error: $error")
                                        Completable.error(error)
                                    }
                                }
                        )
                        .ignoreElements()
                        .doOnComplete {
                            BleLogger.d(TAG, "doFirstTimeUse(identifier: $identifier): completed")
                            sendTerminateAndStopSyncNotifications(client)
                        }
                        .doOnError { error ->
                            BleLogger.e(TAG, "doFirstTimeUse(identifier: $identifier): error $error")
                        }
            } catch (error: Throwable) {
                BleLogger.e(TAG, "doFirstTimeUse(identifier: $identifier): error $error")
                return@defer Completable.error(error)
            }
        }
    }

    override fun isFtuDone(identifier: String): Single<Boolean> {
        return Single.create { emitter ->
            val disposable = getFile(identifier, UserIdentifierType.USER_IDENTIFIER_FILENAME)
                .subscribe(
                    { byteArray ->
                        try {
                            emitter.onSuccess(UserIds.PbUserIdentifier.parseFrom(byteArray).hasMasterIdentifier())
                        } catch (e: Exception) {
                            BleLogger.e(TAG, "Failed to check if the first time use has been done: $e")
                            emitter.onError(e)
                        }
                    },
                    { error ->
                        BleLogger.e(TAG, "Failed to check if the first time use has been done: $error")
                        emitter.onError(error)
                    }
                )
            emitter.setCancellable { disposable.dispose() }
        }
    }

    override fun setWareHouseSleep(identifier: String): Completable {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Completable.error(error)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return Completable.error(PolarServiceNotAvailable())
        val params = PftpNotification.PbPFtpFactoryResetParams.newBuilder()
        params.sleep = true
        params.doFactoryDefaults = true
        BleLogger.d(TAG, "send factory reset notification to device $identifier and set warehouse sleep setting to true")
        return client.sendNotification(PftpNotification.PbPFtpHostToDevNotification.RESET.ordinal, params.build().toByteArray())
    }

    private fun <T : Any> startStreaming(identifier: String, type: PmdMeasurementType, setting: PolarSensorSetting, observer: Function<BlePMDClient, Flowable<T>>): Flowable<T> {
        return try {
            val session = sessionPmdClientReady(identifier)
            val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient? ?: return Flowable.error(PolarServiceNotAvailable())
            client.startMeasurement(type, mapPolarSettingsToPmdSettings(setting))
                .andThen(observer.apply(client)
                    .onErrorResumeNext { throwable: Throwable -> Flowable.error(handleError(throwable)) }
                    .doFinally { stopPmdStreaming(session, client, type) })
        } catch (t: Throwable) {
            Flowable.error(t)
        }
    }

    private fun openConnection(session: BleDeviceSession) {
        listener?.let {
            if (devicesStateMonitorDisposable == null || devicesStateMonitorDisposable?.isDisposed == true) {
                devicesStateMonitorDisposable = it.monitorDeviceSessionState().subscribe(deviceStateMonitorObserver)
            }
            it.openSessionDirect(session)
        }
    }

    override fun startOfflineRecording(identifier: String, feature: PolarDeviceDataType, settings: PolarSensorSetting?, secret: PolarRecordingSecret?): Completable {
        val session = try {
            sessionPmdClientReady(identifier)
        } catch (t: Throwable) {
            return Completable.error(t)
        }
        val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient? ?: return Completable.error(PolarServiceNotAvailable())
        val pmdSecret = secret?.let { mapPolarSecretToPmdSecret(it) }
        return client.startMeasurement(mapPolarFeatureToPmdClientMeasurementType(feature), mapPolarSettingsToPmdSettings(settings), PmdRecordingType.OFFLINE, pmdSecret)
    }

    override fun stopOfflineRecording(identifier: String, feature: PolarDeviceDataType): Completable {
        val session = try {
            sessionPmdClientReady(identifier)
        } catch (t: Throwable) {
            return Completable.error(t)
        }
        val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient? ?: return Completable.error(PolarServiceNotAvailable())

        BleLogger.d(TAG, "Stop offline recording. Feature: ${feature.name} Device $identifier")
        return client.stopMeasurement(mapPolarFeatureToPmdClientMeasurementType(feature))
    }

    override fun getOfflineRecordingStatus(identifier: String): Single<List<PolarDeviceDataType>> {
        val session = try {
            sessionPmdClientReady(identifier)
        } catch (t: Throwable) {
            return Single.error(t)
        }
        val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient? ?: return Single.error(PolarServiceNotAvailable())

        BleLogger.d(TAG, "Get offline recording status. Device: $identifier")
        return client.readMeasurementStatus()
            .map { pmdMeasurementStatus ->
                val offlineRecs: MutableList<PolarDeviceDataType> = mutableListOf()
                pmdMeasurementStatus.filter {
                    it.value == PmdActiveMeasurement.OFFLINE_MEASUREMENT_ACTIVE ||
                            it.value == PmdActiveMeasurement.ONLINE_AND_OFFLINE_ACTIVE
                }
                    .map {
                        offlineRecs.add(mapPmdClientFeatureToPolarFeature(it.key))
                    }
                offlineRecs.toList()
            }
    }

    override fun setOfflineRecordingTrigger(identifier: String, trigger: PolarOfflineRecordingTrigger, secret: PolarRecordingSecret?): Completable {
        val session = try {
            sessionPmdClientReady(identifier)
        } catch (t: Throwable) {
            return Completable.error(t)
        }
        val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient? ?: return Completable.error(PolarServiceNotAvailable())

        val pmdOfflineTrigger = mapPolarOfflineTriggerToPmdOfflineTrigger(trigger)
        val pmdSecret = secret?.let { mapPolarSecretToPmdSecret(it) }
        BleLogger.d(TAG, "Setup offline recording trigger. Trigger mode: ${trigger.triggerMode} Trigger features: ${trigger.triggerFeatures.keys.joinToString(", ")} Device: $identifier Secret used: ${secret != null}")
        return client.setOfflineRecordingTrigger(pmdOfflineTrigger, pmdSecret)
    }

    override fun getOfflineRecordingTriggerSetup(identifier: String): Single<PolarOfflineRecordingTrigger> {
        val session = try {
            sessionPmdClientReady(identifier)
        } catch (t: Throwable) {
            return Single.error(t)
        }
        val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient? ?: return Single.error(PolarServiceNotAvailable())

        BleLogger.d(TAG, "Get offline recording trigger setup. Device: $identifier")
        return client.getOfflineRecordingTriggerStatus()
            .map { mapPmdTriggerToPolarTrigger(it) }
    }

    override fun startHrStreaming(identifier: String): Flowable<PolarHrData> {
        val session = try {
            sessionServiceReady(identifier, HR_SERVICE)
        } catch (e: Exception) {
            return Flowable.error(e)
        }
        val bleHrClient = session.fetchClient(HR_SERVICE) as BleHrClient? ?: return Flowable.error(PolarServiceNotAvailable())
        BleLogger.d(TAG, "start Hr online streaming. Device: $identifier")
        return bleHrClient.observeHrNotifications(true)
            .map { hrNotificationData: HrNotificationData ->
                val sample = PolarHrData.PolarHrSample(
                    hrNotificationData.hrValue,
                    0,
                    0,
                    hrNotificationData.rrsMs,
                    hrNotificationData.rrPresent,
                    hrNotificationData.sensorContact,
                    hrNotificationData.sensorContactSupported
                )
                PolarHrData(listOf(sample))
            }
    }

    override fun startEcgStreaming(identifier: String, sensorSetting: PolarSensorSetting): Flowable<PolarEcgData> {
        return startStreaming(identifier, PmdMeasurementType.ECG, sensorSetting, observer = { client: BlePMDClient ->
            client.monitorEcgNotifications(true)
                .map { ecgData: EcgData -> PolarDataUtils.mapPmdClientEcgDataToPolarEcg(ecgData) }
        })
    }

    override fun startAccStreaming(identifier: String, sensorSetting: PolarSensorSetting): Flowable<PolarAccelerometerData> {
        return startStreaming(identifier, PmdMeasurementType.ACC, sensorSetting, observer = { client: BlePMDClient ->
            client.monitorAccNotifications(true)
                .map { accData: AccData -> mapPmdClientAccDataToPolarAcc(accData) }
        })
    }

    override fun startPpgStreaming(identifier: String, sensorSetting: PolarSensorSetting): Flowable<PolarPpgData> {
        return startStreaming(identifier, PmdMeasurementType.PPG, sensorSetting, observer = { client: BlePMDClient ->
            client.monitorPpgNotifications(true)
                .map { ppgData: PpgData -> mapPMDClientPpgDataToPolarPpg(ppgData) }
        })
    }

    override fun startPpiStreaming(identifier: String): Flowable<PolarPpiData> {
        return startStreaming(identifier, PmdMeasurementType.PPI, PolarSensorSetting(emptyMap())) { client: BlePMDClient ->
            client.monitorPpiNotifications(true)
                .map { ppiData: PpiData ->
                    mapPMDClientPpiDataToPolarPpiData(ppiData)
                }
        }
    }

    override fun startMagnetometerStreaming(identifier: String, sensorSetting: PolarSensorSetting): Flowable<PolarMagnetometerData> {
        return startStreaming(identifier, PmdMeasurementType.MAGNETOMETER, sensorSetting, observer = { client: BlePMDClient ->
            client.monitorMagnetometerNotifications(true)
                .map { mag: MagData ->
                    mapPmdClientMagDataToPolarMagnetometer(mag)
                }
        })
    }

    override fun startGyroStreaming(identifier: String, sensorSetting: PolarSensorSetting): Flowable<PolarGyroData> {
        return startStreaming(identifier, PmdMeasurementType.GYRO, sensorSetting, observer = { client: BlePMDClient ->
            client.monitorGyroNotifications(true)
                .map { gyro: GyrData ->
                    mapPmdClientGyroDataToPolarGyro(gyro)
                }
        })
    }

    override fun startPressureStreaming(identifier: String, sensorSetting: PolarSensorSetting): Flowable<PolarPressureData> {
        return startStreaming(identifier, PmdMeasurementType.PRESSURE, sensorSetting) { client: BlePMDClient ->
            client.monitorPressureNotifications(true)
                .map { pressure: PressureData ->
                    mapPmdClientPressureDataToPolarPressure(pressure)
                }
        }
    }

    override fun startLocationStreaming(identifier: String, sensorSetting: PolarSensorSetting): Flowable<PolarLocationData> {
        return startStreaming(identifier, PmdMeasurementType.LOCATION, sensorSetting) { client: BlePMDClient ->
            client.monitorLocationNotifications(true)
                .map { gnssLocationData: GnssLocationData -> mapPMDClientLocationDataToPolarLocationData(gnssLocationData) }
        }
    }

    override fun startTemperatureStreaming(identifier: String, sensorSetting: PolarSensorSetting): Flowable<PolarTemperatureData> {
        return startStreaming(identifier, PmdMeasurementType.TEMPERATURE, sensorSetting) { client: BlePMDClient ->
            client.monitorTemperatureNotifications(true)
                    .map { temperature: TemperatureData ->
                        mapPmdClientTemperatureDataToPolarTemperature(temperature)
                    }
        }
    }

    override fun startSkinTemperatureStreaming(identifier: String, sensorSetting: PolarSensorSetting): Flowable<PolarTemperatureData> {
        return startStreaming(identifier, PmdMeasurementType.SKIN_TEMP, sensorSetting) { client: BlePMDClient ->
            client.monitorSkinTemperatureNotifications(true)
                .map { skinTemperature: SkinTemperatureData ->
                    mapPmdClientSkinTemperatureDataToPolarTemperatureData(skinTemperature)
                }
        }
    }

    override fun enableSDKMode(identifier: String): Completable {
        try {
            val session = sessionPmdClientReady(identifier)
            val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient? ?: return Completable.error(PolarServiceNotAvailable())
            return if (client.isServiceDiscovered) {
                client.startSDKMode()
                    .onErrorResumeNext { error: Throwable ->
                        if (error is BleControlPointCommandError && PmdControlPointResponseCode.ERROR_ALREADY_IN_STATE == error.error) {
                            return@onErrorResumeNext Completable.complete()
                        }
                        Completable.error(error)
                    }
            } else Completable.error(PolarServiceNotAvailable())
        } catch (t: Throwable) {
            return Completable.error(t)
        }
    }

    override fun disableSDKMode(identifier: String): Completable {
        return try {
            val session = sessionPmdClientReady(identifier)
            val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient? ?: return Completable.error(PolarServiceNotAvailable())
            if (client.isServiceDiscovered) {
                client.stopSDKMode()
                    .onErrorResumeNext { error: Throwable ->
                        if (error is BleControlPointCommandError && PmdControlPointResponseCode.ERROR_ALREADY_IN_STATE == error.error) {
                            return@onErrorResumeNext Completable.complete()
                        }
                        Completable.error(error)
                    }
            } else Completable.error(PolarServiceNotAvailable())
        } catch (t: Throwable) {
            Completable.error(t)
        }
    }

    override fun isSDKModeEnabled(identifier: String): Single<Boolean> {
        val session = try {
            sessionPmdClientReady(identifier)
        } catch (t: Throwable) {
            return Single.error(t)
        }
        val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient? ?: return Single.error(PolarServiceNotAvailable())
        return client.isSdkModeEnabled()
            .map {
                it != PmdSdkMode.DISABLED
            }
    }

    override fun getAvailableOfflineRecordingDataTypes(identifier: String): Single<Set<PolarDeviceDataType>> {
        val session = try {
            sessionPmdClientReady(identifier)
        } catch (e: Exception) {
            return Single.error(e)
        }
        val blePMDClient = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient? ?: return Single.error(PolarServiceNotAvailable())

        return blePMDClient.readFeature(true)
            .observeOn(AndroidSchedulers.mainThread())
            .map { pmdFeature: Set<PmdMeasurementType> ->
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
                deviceData
            }
    }

    override fun getAvailableOnlineStreamDataTypes(identifier: String): Single<Set<PolarDeviceDataType>> {
        val session = try {
            sessionPmdClientReady(identifier)
        } catch (e: Exception) {
            return Single.error(e)
        }

        val blePMDClient = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient? ?: return Single.error(PolarServiceNotAvailable())
        val bleHrClient = session.fetchClient(HR_SERVICE) as BleHrClient?
        return blePMDClient.clientReady(true)
            .andThen(
                blePMDClient.readFeature(true)
                    .observeOn(AndroidSchedulers.mainThread())
                    .map { pmdFeature: Set<PmdMeasurementType> ->
                        val deviceData: MutableSet<PolarDeviceDataType> = mutableSetOf()
                        if (bleHrClient != null) {
                            deviceData.add(PolarDeviceDataType.HR)
                        }
                        if (pmdFeature.contains(PmdMeasurementType.ECG)) {
                            deviceData.add(PolarDeviceDataType.ECG)
                        }
                        if (pmdFeature.contains(PmdMeasurementType.ACC)) {
                            deviceData.add(PolarDeviceDataType.ACC)
                        }
                        if (pmdFeature.contains(PmdMeasurementType.PPG)) {
                            deviceData.add(PolarDeviceDataType.PPG)
                        }
                        if (pmdFeature.contains(PmdMeasurementType.PPI)) {
                            deviceData.add(PolarDeviceDataType.PPI)
                        }
                        if (pmdFeature.contains(PmdMeasurementType.GYRO)) {
                            deviceData.add(PolarDeviceDataType.GYRO)
                        }
                        if (pmdFeature.contains(PmdMeasurementType.MAGNETOMETER)) {
                            deviceData.add(PolarDeviceDataType.MAGNETOMETER)
                        }
                        if (pmdFeature.contains(PmdMeasurementType.PRESSURE)) {
                            deviceData.add(PolarDeviceDataType.PRESSURE)
                        }
                        if (pmdFeature.contains(PmdMeasurementType.LOCATION)) {
                            deviceData.add(PolarDeviceDataType.LOCATION)
                        }
                        if (pmdFeature.contains(PmdMeasurementType.TEMPERATURE)) {
                            deviceData.add(PolarDeviceDataType.TEMPERATURE)
                        }
                        if (pmdFeature.contains(PmdMeasurementType.SKIN_TEMP)) {
                            deviceData.add(PolarDeviceDataType.SKIN_TEMPERATURE)
                        }

                        deviceData
                    })
    }

    override fun getLogConfig(identifier: String): Single<LogConfig> {
        return Single.create { emitter ->
            val disposable = getFile(identifier, LogConfig.LOG_CONFIG_FILENAME)
                .subscribe(
                    { byteArray ->
                        try {
                            val logConfig = LogConfig.fromBytes(byteArray)
                            emitter.onSuccess(logConfig)
                        } catch (e: Exception) {
                            BleLogger.e(TAG, "Failed to get LogConfig: $e")
                            emitter.onError(e)
                        }
                    },
                    { error ->
                        BleLogger.e(TAG, "Failed to get file: $error")
                        emitter.onError(error)
                    }
                )
            emitter.setCancellable { disposable.dispose() }
        }
    }

    override fun setLogConfig(identifier: String, logConfig: LogConfig): Completable {
        return Completable.create { emitter ->
            try {
                val session = sessionPsFtpClientReady(identifier)
                val client =
                    session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                        ?: throw PolarServiceNotAvailable()
                val builder = PftpRequest.PbPFtpOperation.newBuilder()
                builder.command = PftpRequest.PbPFtpOperation.Command.PUT
                builder.path = LogConfig.LOG_CONFIG_FILENAME
                val data = ByteArrayInputStream(logConfig.toProto().toByteArray())
                client.write(builder.build().toByteArray(), data)
                    .doOnError { error ->
                        emitter.onError(error)
                    }
                    .subscribe()
                emitter.onComplete()
            } catch (error: Throwable) {
                BleLogger.e(TAG, "Failed to set log config: $error")
                emitter.onError(error)
            }
        }
    }

    override fun updateFirmware(identifier: String): Flowable<FirmwareUpdateStatus> {
        val session = sessionPsFtpClientReady(identifier)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient
        sendInitializationAndStartSyncNotifications(client)

        val deviceInfo = PolarFirmwareUpdateUtils.readDeviceFirmwareInfo(client, identifier).blockingGet()
        val httpClient = RetrofitClient.createRetrofitInstance()
        val firmwareUpdateApi = httpClient.create(FirmwareUpdateApi::class.java)

        val request = FirmwareUpdateRequest(
                clientId = "polar-sensor-data-collector-android",
                uuid = PolarDeviceUuid.fromDeviceId(identifier),
                firmwareVersion = deviceInfo.deviceFwVersion,
                hardwareCode = deviceInfo.deviceHardwareCode
        )

        return firmwareUpdateApi.checkFirmwareUpdate(request)
                .toFlowable()
                .flatMap { response ->
                    when (response.code()) {
                        HttpResponseCodes.OK -> {
                            val firmwareUpdateResponse = response.body()
                            BleLogger.d(TAG, "Received firmware update response: $firmwareUpdateResponse")
                            if (firmwareUpdateResponse != null &&
                                    PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                                            deviceInfo.deviceFwVersion, firmwareUpdateResponse.version
                                    )
                            ) {
                                val isDeviceSensor = BlePolarDeviceCapabilitiesUtility.isDeviceSensor(deviceInfo.deviceModelName)
                                val factoryResetMaxWaitTimeSeconds = 6 * 60L
                                val rebootMaxWaitTimeSeconds = 60L
                                val rebootTriggeredWaitTimeSeconds = if (isDeviceSensor) 5L else 20L
                                val backupManager = PolarBackupManager(client)
                                val backup: MutableList<PolarBackupManager.BackupFileData> = mutableListOf()

                                Flowable.just<FirmwareUpdateStatus>(
                                        FirmwareUpdateStatus.PreparingDeviceForFwUpdate("Preparing device for firmware update to version ${firmwareUpdateResponse.version}")
                                ).concatWith(
                                        backupManager.backupDevice()
                                                .toFlowable()
                                                .flatMap {
                                                    backup.addAll(it)
                                                    firmwareUpdateApi.getFirmwareUpdatePackage(firmwareUpdateResponse.fileUrl)
                                                            .toFlowable()
                                                            .flatMap { firmwareBytes ->
                                                                val contentLength = firmwareBytes.contentLength()
                                                                BleLogger.d(TAG, "FW package for version ${firmwareUpdateResponse.version} downloaded, size: $contentLength bytes")
                                                                val firmwareFiles = mutableListOf<Pair<String, ByteArray>>()
                                                                val zipInputStream = ZipInputStream(ByteArrayInputStream(firmwareBytes.bytes()))
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
                                                                    PolarFirmwareUpdateUtils.FwFileComparator().compare(File(f1.first), File(f2.first))
                                                                }

                                                                doFactoryReset(identifier, true)
                                                                        .andThen(Completable.timer(30, TimeUnit.SECONDS))
                                                                        .andThen(waitDeviceSessionToOpen(identifier, factoryResetMaxWaitTimeSeconds, waitForDeviceDownSeconds = 10L))
                                                                        .andThen(Completable.timer(5, TimeUnit.SECONDS))
                                                                        .andThen(
                                                                                Flowable.fromIterable(firmwareFiles)
                                                                                        .concatMap { firmwareFile ->
                                                                                            writeFirmwareToDevice(identifier, firmwareFile.first, firmwareFile.second)
                                                                                                    .map<FirmwareUpdateStatus> { bytesWritten ->
                                                                                                        FirmwareUpdateStatus.WritingFwUpdatePackage(
                                                                                                                "Writing firmware update file ${firmwareFile.first}, bytes written: $bytesWritten/${firmwareFile.second.size}"
                                                                                                        )
                                                                                                    }
                                                                                        }
                                                                        )
                                                            }
                                                            .onErrorReturn { error ->
                                                                BleLogger.e(TAG, "FW package download fetch failed for version ${firmwareUpdateResponse.version}: $error")
                                                                FirmwareUpdateStatus.FwUpdateFailed(error.toString())
                                                            }
                                                            .concatWith(Flowable.just<FirmwareUpdateStatus>(FirmwareUpdateStatus.FinalizingFwUpdate()))
                                                            .concatMap { status ->
                                                                if (status is FirmwareUpdateStatus.FinalizingFwUpdate) {
                                                                    BleLogger.d(TAG, "Starting finalization of firmware update")
                                                                    BleLogger.d(TAG, "Waiting for device session to open after reboot")
                                                                    waitDeviceSessionToOpen(identifier, factoryResetMaxWaitTimeSeconds, if (isDeviceSensor) 0L else 120L)
                                                                            .andThen(Completable.defer {
                                                                                BleLogger.d(TAG, "Performing factory reset while preserving pairing information")
                                                                                return@defer doFactoryReset(identifier, true)
                                                                            })
                                                                            .andThen(Completable.defer {
                                                                                BleLogger.d(TAG, "Waiting for device session to open after factory reset")
                                                                                return@defer waitDeviceSessionToOpen(identifier, factoryResetMaxWaitTimeSeconds, waitForDeviceDownSeconds = 10L)
                                                                            })
                                                                            .andThen(Completable.defer {
                                                                                BleLogger.d(TAG, "Restoring backup to device after version ${firmwareUpdateResponse.version}")
                                                                                client.waitPsFtpClientReady(true)
                                                                                    .andThen(
                                                                                        Completable.fromAction {
                                                                                            sendInitializationAndStartSyncNotifications(client)
                                                                                        }
                                                                                    )
                                                                                    .andThen(
                                                                                        return@defer backupManager.restoreBackup(backup)
                                                                                    )
                                                                            })
                                                                            .andThen(Flowable.just(status))
                                                                } else {
                                                                    Flowable.just(status)
                                                                }
                                                            }
                                                            .concatMap { status ->
                                                                if (status is FirmwareUpdateStatus.FinalizingFwUpdate) {
                                                                    waitDeviceSessionToOpen(identifier, factoryResetMaxWaitTimeSeconds, if (isDeviceSensor) 0L else 60L)
                                                                            .andThen(Flowable.just(FirmwareUpdateStatus.FwUpdateCompletedSuccessfully(firmwareUpdateResponse.version)))
                                                                } else {
                                                                    Flowable.just(status)
                                                                }
                                                            }
                                                            .onErrorResumeNext { error ->
                                                                if (backup.isNotEmpty()) {
                                                                    BleLogger.e(TAG, "Error during updateFirmware() for version ${firmwareUpdateResponse.version}, restoring backup, error: $error")
                                                                    sendInitializationAndStartSyncNotifications(client)
                                                                    backupManager.restoreBackup(backup)
                                                                            .andThen(Flowable.error(error))
                                                                } else {
                                                                    BleLogger.e(TAG, "Error during updateFirmware() for version ${firmwareUpdateResponse.version}, backup not available, error: $error")
                                                                    Flowable.error(error)
                                                                }
                                                            }
                                                            .doFinally {
                                                                val disposable = setLocalTime(identifier, Calendar.getInstance())
                                                                        .andThen(Completable.fromAction {
                                                                            sendTerminateAndStopSyncNotifications(client)
                                                                        })
                                                                        .subscribe({
                                                                        }, { error ->
                                                                            BleLogger.e(TAG, "Error setting local time for identifier: $identifier, error: ${error.message}")
                                                                        })
                                                            }
                                                }
                                )
                            } else {
                                Flowable.just(FirmwareUpdateStatus.FwUpdateNotAvailable("No fw update available, device firmware version ${deviceInfo.deviceFwVersion}"))
                            }
                        }
                        HttpResponseCodes.NO_CONTENT -> Flowable.just(FirmwareUpdateStatus.FwUpdateNotAvailable("No firmware update available"))
                        HttpResponseCodes.BAD_REQUEST -> {
                            val errorBody = try {
                                response.errorBody()?.string() ?: "Failed to read error body"
                            } catch (e: Exception) {
                                "Error reading error body: ${e.message}"
                            }

                            BleLogger.e(TAG, "Bad request to firmware update API: $errorBody")
                            Flowable.error(Throwable("Bad request to firmware update API: $errorBody"))
                        }
                        else -> Flowable.error(Throwable("Unexpected response code: ${response.code()}"))
                    }
                }
                .onErrorResumeNext { error ->
                    Flowable.just(FirmwareUpdateStatus.FwUpdateFailed(error.toString()))
                }
    }

    override fun getSteps(identifier: String, fromDate: Date, toDate: Date): Single<List<PolarStepsData>> {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Single.error(error)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                ?: return Single.error(PolarServiceNotAvailable())

        val stepsDataList = mutableListOf<Pair<Date, Int>>()

        val calendar = Calendar.getInstance()
        calendar.time = fromDate

        val datesList = mutableListOf<Date>()

        while (!calendar.time.after(toDate)) {
            datesList.add(calendar.time)
            calendar.add(Calendar.DATE, 1)
        }

        return Observable.fromIterable(datesList)
                .flatMapSingle { date ->
                    PolarActivityUtils.readStepsFromDayDirectory(client, date)
                            .map { steps ->
                                Pair(date, steps)
                            }
                }
                .toList()
                .map { pairs ->
                    pairs.forEach { pair ->
                        stepsDataList.add(Pair(pair.first, pair.second))
                    }
                    stepsDataList.map { PolarStepsData(it.first, it.second) }
                }
    }

    override fun getDistance(identifier: String, fromDate: Date, toDate: Date): Single<List<PolarDistanceData>> {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Single.error(error)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: return Single.error(PolarServiceNotAvailable())

        val distanceDataList = mutableListOf<Pair<Date, Float>>()

        val calendar = Calendar.getInstance()
        calendar.time = fromDate

        val datesList = mutableListOf<Date>()

        while (!calendar.time.after(toDate)) {
            datesList.add(calendar.time)
            calendar.add(Calendar.DATE, 1)
        }

        return Observable.fromIterable(datesList)
            .flatMapSingle { date ->
                PolarActivityUtils.readDistanceFromDayDirectory(client, date)
                    .map { steps ->
                        Pair(date, steps)
                    }
            }
            .toList()
            .map { pairs ->
                pairs.forEach { pair ->
                    distanceDataList.add(Pair(pair.first, pair.second))
                }
                distanceDataList.map { PolarDistanceData(it.first, it.second) }
            }
    }

    override fun getSleepRecordingState(identifier: String): Single<Boolean> {
        return observeSleepRecordingState(identifier = identifier)
            .filter {
                it.isEmpty() == false
            }
            .take(1)
            .map { array -> array.last() }
            .singleOrError()
    }

    override fun observeSleepRecordingState(identifier: String):  Flowable<Array<Boolean>> {
        val receive: Flowable<Array<Boolean>> =
            receiveRestApiEvents<PolarSleepApiServiceEventPayload>(identifier, mapper = { it.toObject() })
                .map { array ->
                    array.map { it.sleep_recording_state.enabled == 1 }
                    .toTypedArray()
                }
        val subscribe = putNotification(identifier = identifier, notification = "{}",
            path = "/REST/SLEEP.API?cmd=subscribe&event=sleep_recording_state&details=[enabled]")
        return subscribe.andThen(receive)
    }

    override fun stopSleepRecording(identifier: String): Completable {
        return putNotification(
            identifier = identifier,
            notification = "{}",
            path = "/REST/SLEEP.API?cmd=post&endpoint=stop_sleep_recording"
        )
    }

    override fun getSleep(identifier: String, fromDate: LocalDate, toDate: LocalDate): Single<List<PolarSleepData>> {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Single.error(error)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: return Single.error(PolarServiceNotAvailable())
        val sleepDataList = mutableListOf<Pair<LocalDate, PolarSleepAnalysisResult>>()

        val datesList = getDatesBetween(fromDate, toDate)

        return Observable.fromIterable(datesList)
            .flatMapSingle { date ->
                PolarSleepUtils.readSleepDataFromDayDirectory(client, date)
                    .map { data: PolarSleepAnalysisResult ->
                        Pair(date, data)
                    }
            }
            .toList()
            .map { pairs ->
                pairs.forEach { pair ->
                    sleepDataList.add(Pair(pair.first, pair.second))
                }
                sleepDataList.map { PolarSleepData(it.first, it.second) }

            }
    }

    override fun getCalories(identifier: String, fromDate: Date, toDate: Date, caloriesType: CaloriesType): Single<List<PolarCaloriesData>> {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Single.error(error)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: return Single.error(PolarServiceNotAvailable())

        val caloriesDataList = mutableListOf<Pair<Date, Int>>()

        val calendar = Calendar.getInstance()
        calendar.time = fromDate

        val datesList = mutableListOf<Date>()

        while (!calendar.time.after(toDate)) {
            datesList.add(calendar.time)
            calendar.add(Calendar.DATE, 1)
        }

        return Observable.fromIterable(datesList)
            .flatMapSingle { date ->
                PolarActivityUtils.readSpecificCaloriesFromDayDirectory(client, date, caloriesType)
                    .map { calories ->
                        Pair(date, calories)
                    }
            }
            .toList()
            .map { pairs ->
                pairs.forEach { pair ->
                    caloriesDataList.add(Pair(pair.first, pair.second))
                }
                caloriesDataList.map { PolarCaloriesData(it.first, it.second) }
            }
    }

    private fun getDatesBetween(startDate: LocalDate, endDate: LocalDate): MutableList<LocalDate> {
        var theDate: LocalDate = startDate
        var datesList = mutableListOf<LocalDate>()

        while (theDate == endDate || endDate.isAfter(theDate)) {
            datesList.add(theDate)
            theDate = theDate.plusDays(1)
        }

        return datesList
    }

    override fun getActiveTime(identifier: String, fromDate: Date, toDate: Date): Single<List<PolarActiveTimeData>> {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Single.error(error)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: return Single.error(PolarServiceNotAvailable())

        val calendar = Calendar.getInstance()
        calendar.time = fromDate

        val datesList = mutableListOf<Date>()

        while (!calendar.time.after(toDate)) {
            datesList.add(calendar.time)
            calendar.add(Calendar.DATE, 1)
        }

        return Observable.fromIterable(datesList)
            .flatMapSingle { date ->
                PolarActivityUtils.readActiveTimeFromDayDirectory(client, date)
                    .map { polarActiveTimeData ->
                        Pair(date, polarActiveTimeData)
                    }
            }
            .toList()
            .map { pairs ->
                pairs.map { pair ->
                    PolarActiveTimeData(
                        date = pair.first,
                        timeNonWear = pair.second.timeNonWear,
                        timeSleep = pair.second.timeSleep,
                        timeSedentary = pair.second.timeSedentary,
                        timeLightActivity = pair.second.timeLightActivity,
                        timeContinuousModerateActivity = pair.second.timeContinuousModerateActivity,
                        timeIntermittentModerateActivity = pair.second.timeIntermittentModerateActivity,
                        timeContinuousVigorousActivity = pair.second.timeContinuousVigorousActivity,
                        timeIntermittentVigorousActivity = pair.second.timeIntermittentVigorousActivity
                    )
                }
            }
    }

    override fun setUserDeviceSettings(
        identifier: String,
        deviceUserSetting: PolarUserDeviceSettings
    ): Completable {
        return Completable.create { emitter ->
            try {
                val session = sessionPsFtpClientReady(identifier)
                val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                    ?: throw PolarServiceNotAvailable()

                if (deviceUserSetting != null) {
                    val deviceSettingsBuilder = PftpRequest.PbPFtpOperation.newBuilder().apply {
                        command = PftpRequest.PbPFtpOperation.Command.PUT
                        path = PolarUserDeviceSettings.DEVICE_SETTINGS_FILENAME
                    }

                    val deviceSettingsData = ByteArrayOutputStream().use { baos ->
                        deviceUserSetting?.toProto()?.writeTo(baos)
                        baos.toByteArray()
                    }

                    val inputStream = ByteArrayInputStream(deviceSettingsData)
                    client.write(deviceSettingsBuilder.build().toByteArray(), inputStream)
                        .subscribe(
                            { emitter.onComplete() },
                            { error -> emitter.onError(error) }
                        )
                }
            } catch (error: Throwable) {
                BleLogger.e(TAG, "writeDeviceUserSetting() error: $error")
                emitter.onError(error)
            }
        }
    }

    override fun getUserDeviceSettings(identifier: String): Single<PolarUserDeviceSettings> {
        return Single.create { emitter ->
            try {
                val session = sessionPsFtpClientReady(identifier)
                val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                    ?: throw PolarServiceNotAvailable()
                var settings = PolarUserDeviceSettings(0)
                when (getFileSystemType(session.polarDeviceType)) {
                    FileSystemType.SAGRFC2_FILE_SYSTEM -> {
                        val builder = PftpRequest.PbPFtpOperation.newBuilder()
                        builder.command = PftpRequest.PbPFtpOperation.Command.GET
                        builder.path = PolarUserDeviceSettings.DEVICE_SETTINGS_FILENAME
                        client.request(builder.build().toByteArray())
                            .map {
                                it.toByteArray()
                            }.onErrorResumeNext { throwable: Throwable ->
                                Single.error(handleError(throwable))
                            }
                    }
                    else -> Single.error(PolarOperationNotSupported())
                }.subscribe(
                    { byteArray ->
                        try {
                            val userDeviceSettings = settings.fromBytes(byteArray)
                            emitter.onSuccess(userDeviceSettings)
                        } catch (e: Exception) {
                            BleLogger.e(TAG, "Error in converting proto to user device settings: $e")
                            emitter.onError(e)
                        }
                    },
                    { error ->
                        BleLogger.e(TAG, "Failed to get device user settings: $error")
                        emitter.onError(error)
                    }
                )
            } catch (error: Throwable) {
                BleLogger.e(TAG, "Failed to get device user settings: $error")
            }
        }
    }

    override fun get247HrSamples(identifier: String, fromDate: Date, toDate: Date): Single<List<Polar247HrSamplesData>> {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Single.error(error)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                ?: return Single.error(PolarServiceNotAvailable())

        return PolarAutomaticSamplesUtils.read247HrSamples(client, fromDate, toDate)
    }

    override fun get247PPiSamples(identifier: String, fromDate: Date, toDate: Date): Single<List<Polar247PPiSamplesData>> {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Single.error(error)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: return Single.error(PolarServiceNotAvailable())

        return PolarAutomaticSamplesUtils.read247PPiSamples(client, fromDate, toDate)
    }

    override fun getNightlyRecharge(identifier: String, fromDate: Date, toDate: Date): Single<List<PolarNightlyRechargeData>> {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Single.error(error)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                ?: return Single.error(PolarServiceNotAvailable())

        val nightlyRechargeDataList = mutableListOf<PolarNightlyRechargeData>()

        val calendar = Calendar.getInstance()
        calendar.time = fromDate

        val datesList = mutableListOf<Date>()

        while (!calendar.time.after(toDate)) {
            datesList.add(calendar.time)
            calendar.add(Calendar.DATE, 1)
        }

        return Observable.fromIterable(datesList)
                .flatMapMaybe { date ->
                    PolarNightlyRechargeUtils.readNightlyRechargeData(client, date)
                            .doOnSuccess { nightlyRechargeData ->
                                nightlyRechargeDataList.add(nightlyRechargeData)
                            }
                }
                .toList()
                .flatMap { list ->
                    Single.just(nightlyRechargeDataList)
                }
    }

    override fun getSkinTemperature(identifier: String, fromDate: LocalDate, toDate: LocalDate): Single<List<PolarSkinTemperatureData>> {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Single.error(error)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: return Single.error(PolarServiceNotAvailable())
        val skinTemperatureDataList = mutableListOf<Pair<LocalDate, PolarSkinTemperatureResult>>()

        val datesList = getDatesBetween(fromDate, toDate)

        return Observable.fromIterable(datesList)
            .flatMapMaybe { date ->
                PolarSkinTemperatureUtils.readSkinTemperatureDataFromDayDirectory(client, date)
                    .map { data: PolarSkinTemperatureResult ->
                        Pair(date, data)
                    }
            }
            .toList()
            .map { pairs ->
                pairs.forEach { pair ->
                    skinTemperatureDataList.add(Pair(pair.first, pair.second))
                }
                skinTemperatureDataList.map { PolarSkinTemperatureData(it.first, it.second) }

            }
    }

    private fun sendInitializationAndStartSyncNotifications(client: BlePsFtpClient) {
        BleLogger.d(TAG, "Sending initialize session and start sync notifications")
        client.sendNotification(
                PftpNotification.PbPFtpHostToDevNotification.INITIALIZE_SESSION_VALUE,
                null
        ).subscribe()
        client.sendNotification(
                PftpNotification.PbPFtpHostToDevNotification.START_SYNC_VALUE,
                null
        ).subscribe()
    }

    private fun sendTerminateAndStopSyncNotifications(client: BlePsFtpClient) {
        BleLogger.d(TAG, "Sending terminate session and stop sync notifications")
        client.sendNotification(
                PftpNotification.PbPFtpHostToDevNotification.STOP_SYNC_VALUE,
                PbPFtpStopSyncParams.newBuilder().setCompleted(true).build().toByteArray()
        ).subscribe()
        client.sendNotification(
                PftpNotification.PbPFtpHostToDevNotification.TERMINATE_SESSION_VALUE,
                null
        ).subscribe()
    }

    private fun writeFirmwareToDevice(deviceId: String, firmwareFilePath: String, firmwareBytes: ByteArray): Flowable<Long> {
        return Flowable.create({ emitter ->
            try {
                val session = sessionPsFtpClientReady(deviceId)
                val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient

                BleLogger.d(TAG, "Initialize session")
                client.sendNotification(
                        PftpNotification.PbPFtpHostToDevNotification.INITIALIZE_SESSION_VALUE,
                        null)
                        .subscribe()

                client.sendNotification(
                        PftpNotification.PbPFtpHostToDevNotification.START_SYNC_VALUE,
                        null)
                        .subscribe()

                BleLogger.d(TAG, "Prepare firmware update")
                val disposable = client.query(PftpRequest.PbPFtpQuery.PREPARE_FIRMWARE_UPDATE_VALUE, null)
                        .flatMapCompletable {
                            BleLogger.d(TAG, "Start $firmwareFilePath write")
                            val builder = PftpRequest.PbPFtpOperation.newBuilder()
                            builder.command = PftpRequest.PbPFtpOperation.Command.PUT
                            builder.path = "/$firmwareFilePath"
                            client.write(
                                    builder.build().toByteArray(),
                                    ByteArrayInputStream(firmwareBytes)
                            )
                                    .throttleFirst(5, TimeUnit.SECONDS)
                                    .doOnNext { bytesWritten: Long ->
                                        BleLogger.d(TAG, "Writing firmware update file, bytes written: $bytesWritten/${firmwareBytes.size}")
                                        emitter.onNext(bytesWritten)
                                    }
                                    .ignoreElements()
                        }
                        .subscribe({
                            if (firmwareFilePath.contains("SYSUPDAT.IMG")) {
                                BleLogger.d(TAG, "Firmware file is SYSUPDAT.IMG, waiting for reboot")
                            }
                            emitter.onComplete()
                        }, { error ->
                            if (error is PftpResponseError && error.error == PbPFtpError.REBOOTING.number) {
                                BleLogger.d(TAG, "REBOOTING")
                                emitter.onComplete()
                            } else {
                                emitter.onError(error)
                            }
                        })
                emitter.setCancellable { disposable.dispose() }
            } catch (error: Throwable) {
                emitter.onError(error)
            }
        }, BackpressureStrategy.BUFFER)
    }

    private fun waitDeviceSessionToOpen(
            deviceId: String,
            timeoutSeconds: Long,
            waitForDeviceDownSeconds: Long = 0L
    ): Completable {
        BleLogger.d(TAG, "waitDeviceSessionToOpen(), seconds: $timeoutSeconds, waitForDeviceDownSeconds: $waitForDeviceDownSeconds")
        val pollIntervalSeconds = 5L

        return Observable.timer(waitForDeviceDownSeconds, TimeUnit.SECONDS)
                .ignoreElements()
                .andThen(Completable.create { emitter ->
                    var disposable: Disposable? = null
                    disposable = Observable.interval(pollIntervalSeconds, TimeUnit.SECONDS)
                            .takeUntil(Observable.timer(timeoutSeconds, TimeUnit.SECONDS))
                            .timeout(timeoutSeconds, TimeUnit.SECONDS)
                            .subscribe({
                                if (deviceSessionState == DeviceSessionState.SESSION_OPEN) {
                                    BleLogger.d(TAG, "Session opened, deviceId: $deviceId")
                                    disposable?.dispose()
                                    emitter.onComplete()
                                } else {
                                    BleLogger.d(TAG, "Waiting for device session to open, deviceId: $deviceId, current state: $deviceSessionState")
                                }
                            }, { error ->
                                BleLogger.e(TAG, "Timeout reached while waiting for device session to open, deviceId: $deviceId")
                                emitter.onError(error)
                            })
                    emitter.setCancellable {
                        disposable.dispose()
                    }
                })
    }

    override fun deleteStoredDeviceData(identifier: String, dataType: PolarStoredDataType, until: LocalDate?): Flowable<ConcurrentLinkedQueue<String>> {

        var dataDeletionStats = DataDeletionStats()
        var folderPath = "/U/0"
        val entryPattern = dataType.type
        var cond: FetchRecursiveCondition?

        when (dataType.type) {
            PolarStoredDataType.AUTO_SAMPLE.type -> {
                folderPath = "/U/0/AUTOS"
                cond = FetchRecursiveCondition { entry: String ->
                    entry.matches(Regex("^(\\d{8})(/)")) ||
                            entry.contains( ".BPB")
                }
            }

            PolarStoredDataType.SDLOGS.type -> {
                folderPath = "/SDLOGS"
                cond = FetchRecursiveCondition { entry: String ->
                    entry.matches(Regex("^(\\d{8})(/)")) ||
                            entry == "${entryPattern}/" ||
                            entry.contains(".SLG") ||
                            entry.contains(".TXT")
                }
            }

            else -> {
                cond = FetchRecursiveCondition { entry: String ->
                    entry.matches(Regex("^(\\d{8})(/)")) ||
                            entry == "${entryPattern}/" ||
                            entry.contains(".BPB") &&
                            !entry.contains("USERID.BPB") &&
                            !entry.contains("HIST")
                }
            }
        }

        listFiles(identifier, folderPath, condition = cond)
            .map {
                dataDeletionStats.fileDeletionMap[it] = false
            }.doOnError { error ->
                BleLogger.e(TAG, "Encountered exception while deleting files in device $identifier.. Error: $error")
                Flowable.error<Throwable>(error)
            }.blockingSubscribe()

        if (dataDeletionStats.fileDeletionMap.size == 0) {
            var emptyQueue = ConcurrentLinkedQueue<String>()
            emptyQueue.add("No ${dataType.type} files found for deletion.")
            return Flowable.just(emptyQueue)
        }

        when (dataType.type) {
            PolarStoredDataType.AUTO_SAMPLE.type -> {
                BleLogger.d(TAG, "Starting to delete files from /U/0/AUTOS/ folder from device $identifier.")
               return deleteAutoSampleFiles(identifier, dataDeletionStats)
                    .doOnError { error ->
                        BleLogger.e(TAG, "Encountered exception while deleting AUTO_SAMPLE files in device $identifier. Error: $error")
                        Flowable.error<Throwable>(error)
                    }
            }

            PolarStoredDataType.SDLOGS.type -> {
                BleLogger.d(TAG, "Starting to delete files from SDLOGS folder from device $identifier.")
                return deleteSdLogFiles(identifier, dataDeletionStats)
                    .doOnError { error ->
                        BleLogger.e(TAG, "Encountered exception while deleting SDLOGS files in device $identifier. Error: $error")
                        Flowable.error<Throwable>(error)
                    }
            }

            else -> {
                BleLogger.d(
                    TAG,
                    "Starting to delete files from /U/0 directory, file type: $dataType.name from device $identifier."
                )
                return deleteDataDirectories(identifier, until, dataDeletionStats)
                    .doOnError { error ->
                        BleLogger.e(TAG, "Encountered exception while deleting ${dataType.type} files in device $identifier. Error: $error")
                        Flowable.error<Throwable>(error)
                    }
            }
        }
    }

    private fun deleteAutoSampleFiles(identifier: String, dataDeletionStats: DataDeletionStats): Flowable<ConcurrentLinkedQueue<String>> {

        return Flowable.fromIterable(dataDeletionStats.fileDeletionMap.asIterable()).doOnEach() { item ->
            val file = item.value
            if (file != null && !file.value) {
                getFile(identifier, file.key)
                    .subscribe(
                        { byteArray ->
                            val proto = PbAutomaticSampleSessions.parseFrom(byteArray)
                            val date = PolarTimeUtils.pbDateToLocalDate(proto.day)
                            // Delete all files but leave files from today.
                            if (date.isBefore(LocalDate.now())) {
                                dataDeletionStats.fileDeletionMap[file.key] = true
                                removeSingleFile(identifier, file.key)
                                    .map { _ ->
                                        dataDeletionStats.fileDeletionMap[file.key] = true
                                    }.doOnError { error ->
                                        BleLogger.e(TAG, "Failed to delete autosync file $file.key from device $identifier. Error: $error")
                                    }.doOnSuccess {
                                        dataDeletionStats.deletedDirs.add(file.key)
                                    }.subscribe()
                            }
                        },
                        { error ->
                            BleLogger.e(TAG, "Failed to load file ${file.key} from device $identifier. Error: $error")
                        }
                    )
            }
        }.map { _ ->
            dataDeletionStats.deletedDirs
        }
    }

    private fun deleteSdLogFiles(identifier: String, dataDeletionStats: DataDeletionStats): Flowable<ConcurrentLinkedQueue<String>> {

        return Flowable.fromIterable(dataDeletionStats.fileDeletionMap.asIterable()).doOnEach() { item ->
            val file = item.value
            if (file != null && !file.value) {
                removeSingleFile(identifier, file.key)
                    .observeOn(Schedulers.io())
                    .map { _ ->
                        dataDeletionStats.fileDeletionMap[file.key] = true
                    }.doOnError { error ->
                        BleLogger.e(
                            TAG,
                            "Failed to delete Log file $file.key from device $identifier. Error: $error"
                        )
                    }.doOnSuccess {
                        dataDeletionStats.deletedDirs.add(file.key)
                    }.subscribe()
            }
        }.map { _ ->
            dataDeletionStats.deletedDirs
        }
    }

    private fun deleteDataDirectories(identifier: String, until: LocalDate?, dataDeletionStats: DataDeletionStats): Flowable<ConcurrentLinkedQueue<String>> {

        return Flowable.fromIterable(dataDeletionStats.fileDeletionMap.asIterable())
            .doOnEach { item ->
                val file = item.value
                var fileDateIsBefore = false
                val pattern: Pattern =
                    Pattern.compile("(?<!\\d)\\d{8}(?!\\d)")
                if (file != null) {
                    val matcher: Matcher = pattern.matcher(file.key)
                    val found = matcher.find()
                    if (found) {
                        var entryDate = LocalDate.parse(matcher.group(), dateFormatter)
                        if (until != null && entryDate.isBefore(until)) {
                            fileDateIsBefore = true
                        }
                    }
                    if (fileDateIsBefore) {
                        val path = listOf(file.key.split("/").subList(0, file.key.split("/").lastIndex))[0].joinToString(separator = "/")
                        var deleteDir = true
                        for (dir in dataDeletionStats.deletedDirs) {
                            if (dir.equals(path)) {
                                deleteDir = false
                                break
                            }
                        }
                        if (deleteDir) {
                            dataDeletionStats.deletedDirs.add(path)
                            removeSingleFile(identifier, path)
                                .observeOn(Schedulers.io())
                                .doOnError { error ->
                                    dataDeletionStats.deletedDirs.remove(path)
                                    BleLogger.e(
                                        TAG,
                                        "Failed to delete data directory $path from device $identifier. Error: $error"
                                    )
                                }.doAfterSuccess() {
                                    deleteDayDirectory(
                                        identifier,
                                        listOf(
                                            file.key.split("/")
                                                .subList(0, file.key.split("/").lastIndex - 1)
                                        )[0].joinToString(separator = "/")
                                    ).subscribe()
                                }.subscribe()
                        }
                    }
                }
            }.map { _ ->
                dataDeletionStats.deletedDirs
            }
    }

    private fun deleteDataDirectories(identifier: String): Flowable<Unit> {

        val fileDeletionMap: MutableMap<String, Boolean> = mutableMapOf()
        return Flowable.fromIterable(fileDeletionMap.asIterable())
            .map { file ->
                val dir = listOf(file.key.split("/").subList(0, file.key.split("/").lastIndex))[0].joinToString(separator = "/")
                removeSingleFile(identifier, dir)
                    .observeOn(Schedulers.io())
                    .doOnError { error ->
                        BleLogger.e(
                            TAG,
                            "Failed to delete data directory $dir from device $identifier. Error: $error"
                        )
                    }
                    .doOnSuccess {
                        deleteDayDirectory(identifier, listOf(file.key.split("/").subList(0,file.key.split("/").lastIndex - 1))[0].joinToString(separator = "/")).subscribe()
                    }.subscribe()
            }
    }

    private fun deleteDayDirectory(identifier: String, dir: String): Flowable<String> {

        var fileList: MutableList<String> = mutableListOf()

        return Flowable.create({ _ ->
            listFiles(identifier, dir,
                condition = { entry ->
                    entry.matches(Regex("^(\\d{8})(/)")) ||
                            entry.matches(Regex("^([A-Z-0-9]{1,8}[0-9])(/)")) ||
                            entry.contains(".BPB") ||
                            entry.contains(".REC")
                })
                .map {
                    fileList.add(it)
                }
                .doFinally {
                    if (fileList.isEmpty()) {
                        removeSingleFile(identifier, dir)
                            .observeOn(Schedulers.io())
                            .doOnError { error ->
                                BleLogger.e(
                                    TAG,
                                    "Failed to delete day directory $dir from device $identifier. Error: $error"
                                )
                            }.subscribe()
                    }
                }
                .doOnError { error ->
                    BleLogger.e(
                        TAG,
                        "Failed to list files from day directory $dir from device $identifier. Error: $error"
                    )
                    throw PolarBleSdkInstanceException("Failed to delete day directory $dir from device $identifier. Error: $error")
                }.subscribe()
        }, BackpressureStrategy.BUFFER)
    }

    private fun removeSingleFile(identifier: String, filePath: String): Single<ByteArrayOutputStream> {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Single.error(error)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return Single.error(PolarServiceNotAvailable())

        val builder = PftpRequest.PbPFtpOperation.newBuilder()
        builder.command = PftpRequest.PbPFtpOperation.Command.REMOVE
        builder.path = filePath
        return client.request(builder.build().toByteArray()).onErrorResumeNext { throwable: Throwable ->
            Single.error(handleError(throwable))
        }
    }

    private fun listFiles(identifier: String, folderPath: String = "/", condition: FetchRecursiveCondition): Flowable<String> {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Flowable.error(error)
        }

        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return Flowable.error(PolarServiceNotAvailable())
        return when (getFileSystemType(session.polarDeviceType)) {
            FileSystemType.SAGRFC2_FILE_SYSTEM -> {
                var path = folderPath.ifEmpty { "/" }
                path = if (path.first() != '/') "/$path" else path
                path = if (path.last() != '/') "$path/" else path
                fetchRecursively(
                    client = client,
                    path = path,
                    condition = condition)
                    .map {
                        it.first
                    }.onErrorResumeNext { throwable: Throwable ->
                        Flowable.error(handleError(throwable))
                    }
            }
            else -> Flowable.error(PolarOperationNotSupported())
        }
    }

    @Throws(PolarInvalidArgument::class)
    fun fetchSession(identifier: String): BleDeviceSession? {
        if (identifier.matches(Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$"))) {
            return sessionByAddress(identifier)
        } else if (identifier.matches(Regex("([0-9a-fA-F]){6,8}"))) {
            return sessionByDeviceId(identifier)
        }
        throw PolarInvalidArgument()
    }

    private fun sessionByAddress(address: String): BleDeviceSession? {
        listener?.let {
            val sessions = it.deviceSessions()
            if (sessions != null) {
                for (session in sessions) {
                    if (session.address == address) {
                        return session
                    }
                }
            }
        }
        return null
    }

    private fun sessionByDeviceId(deviceId: String): BleDeviceSession? {
        listener?.let {
            val sessions = it.deviceSessions()
            if (sessions != null) {
                for (session in sessions) {
                    if (session.advertisementContent.polarDeviceId == deviceId) {
                        return session
                    }
                }
            }
        }
        return null
    }

    @Throws(Throwable::class)
    private fun sessionServiceReady(identifier: String, service: UUID): BleDeviceSession {
        val session = fetchSession(identifier)
        if (session != null) {
            if (session.sessionState == DeviceSessionState.SESSION_OPEN) {
                val client = session.fetchClient(service)
                if (client != null && client.isServiceDiscovered) {
                    return session
                }
                throw PolarServiceNotAvailable()
            }
            throw PolarDeviceDisconnected()
        }
        throw PolarDeviceNotFound()
    }

    @Throws(Throwable::class)
    fun sessionHrClientReady(identifier: String): BleDeviceSession {
        val session = sessionServiceReady(identifier, HR_SERVICE)
        val client = session.fetchClient(HR_SERVICE) as BleHrClient? ?: throw PolarServiceNotAvailable()
        val hrMeasurementChr = client.getNotificationAtomicInteger(HR_MEASUREMENT)
        if (hrMeasurementChr != null && hrMeasurementChr.get() == BleGattBase.ATT_SUCCESS) {
            return session
        }
        throw PolarNotificationNotEnabled()
    }

    @Throws(Throwable::class)
    fun sessionPmdClientReady(identifier: String): BleDeviceSession {
        val session = sessionServiceReady(identifier, BlePMDClient.PMD_SERVICE)
        val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient? ?: throw PolarServiceNotAvailable()
        val pair = client.getNotificationAtomicInteger(BlePMDClient.PMD_CP)
        val pairData = client.getNotificationAtomicInteger(BlePMDClient.PMD_DATA)
        if (pair != null && pairData != null && pair.get() == BleGattBase.ATT_SUCCESS && pairData.get() == BleGattBase.ATT_SUCCESS) {
            return session
        }
        throw PolarNotificationNotEnabled()
    }

    @Throws(Throwable::class)
    protected fun sessionPsFtpClientReady(identifier: String): BleDeviceSession {
        val session = sessionServiceReady(identifier, BlePsFtpUtils.RFC77_PFTP_SERVICE)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: throw PolarServiceNotAvailable()
        val pair = client.getNotificationAtomicInteger(BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC)
        if (pair != null && pair.get() == BleGattBase.ATT_SUCCESS) {
            return session
        }
        throw PolarNotificationNotEnabled()

    }

    private fun stopPmdStreaming(session: BleDeviceSession, client: BlePMDClient, type: PmdMeasurementType) {
        if (session.sessionState == DeviceSessionState.SESSION_OPEN) {
            // stop streaming
            val disposable = client.stopMeasurement(type)
                .subscribe(
                    {},
                    { throwable: Throwable ->
                        logError("failed to stop pmd stream: " + throwable.localizedMessage)
                    }
                )
            stopPmdStreamingDisposable[session.address] = disposable
        }
    }

    private val deviceStateMonitorObserver = Consumer { deviceSessionStatePair: androidx.core.util.Pair<BleDeviceSession?, DeviceSessionState?> ->
        val session = deviceSessionStatePair.first
        val sessionState = deviceSessionStatePair.second

        //Guard
        if (session == null || sessionState == null) {
            return@Consumer
        }
        deviceSessionState = sessionState
        val info = PolarDeviceInfo(session.polarDeviceId.ifEmpty { session.address }, session.address, session.rssi, session.name, true)
        when (Objects.requireNonNull(sessionState)) {
            DeviceSessionState.SESSION_OPEN -> {
                callback?.let {
                    Completable.fromAction { it.deviceConnected(info) }
                        .subscribeOn(AndroidSchedulers.mainThread())
                        .subscribe()
                }
                setupDevice(session)
            }
            DeviceSessionState.SESSION_CLOSED -> {
                callback?.let {
                    if (session.previousState == DeviceSessionState.SESSION_OPEN ||
                        session.previousState == DeviceSessionState.SESSION_OPENING ||
                        session.previousState == DeviceSessionState.SESSION_OPEN_PARK ||
                        session.previousState == DeviceSessionState.SESSION_CLOSING
                    ) {
                        Completable.fromAction { it.deviceDisconnected(info) }
                            .subscribeOn(AndroidSchedulers.mainThread())
                            .subscribe()
                    }
                }
                tearDownDevice(session)
            }
            DeviceSessionState.SESSION_OPENING -> {
                callback?.let {
                    Completable.fromAction { it.deviceConnecting(info) }
                        .subscribeOn(AndroidSchedulers.mainThread())
                        .subscribe()
                }
            }
            else -> {
                //Nop
            }
        }
    }

    private fun setupDevice(session: BleDeviceSession) {
        val deviceId = session.polarDeviceId.ifEmpty { session.address }
        val disposableAvailableFeatures = session.monitorServicesDiscovered(true)
            .flatMapCompletable { discoveredServices ->
                val availableFeaturesList: MutableList<Completable> = mutableListOf()
                for (feature in PolarBleSdkFeature.values()) {
                    if (features.contains(feature)) {
                        availableFeaturesList.add(makeFeatureCallbackIfNeeded(session, discoveredServices, feature))
                    }
                }
                Completable.concat(availableFeaturesList)
            }
            .subscribe(
                { log("completed available features check ") },
                { throwable: Throwable -> logError("Error while available features are checked: $throwable") },
            )

        deviceAvailableFeaturesDisposable[session.address] = disposableAvailableFeatures
        val disposable = session.monitorServicesDiscovered(true)
            .toFlowable()
            .flatMapIterable { uuids: List<UUID> -> uuids }
            .flatMap { uuid: UUID ->
                if (session.fetchClient(uuid) != null) {
                    when (uuid) {
                        HR_SERVICE -> {
                            Completable.fromAction { callback?.bleSdkFeatureReady(deviceId, PolarBleSdkFeature.FEATURE_HR) }
                                .subscribeOn(AndroidSchedulers.mainThread())
                                .subscribe()

                            val bleHrClient = session.fetchClient(HR_SERVICE) as BleHrClient?
                            bleHrClient?.observeHrNotifications(true)
                                ?.observeOn(AndroidSchedulers.mainThread())
                                ?.subscribe(
                                    { hrNotificationData: HrNotificationData ->
                                        callback?.hrNotificationReceived(
                                            deviceId,
                                            PolarHrData.PolarHrSample(
                                                hrNotificationData.hrValue,
                                                0, 0,
                                                hrNotificationData.rrsMs,
                                                hrNotificationData.rrPresent,
                                                hrNotificationData.sensorContact,
                                                hrNotificationData.sensorContactSupported
                                            )
                                        )
                                    },
                                    { error: Throwable -> if (error.message != null) logError(error.message!!) },
                                    {})
                        }
                        BleBattClient.BATTERY_SERVICE -> {
                            val bleBattClient = session.fetchClient(BleBattClient.BATTERY_SERVICE) as BleBattClient?
                            bleBattClient?.monitorBatteryStatus(true)
                                ?.observeOn(AndroidSchedulers.mainThread())
                                ?.subscribe(
                                    { batteryLevel: Int? ->
                                        callback?.batteryLevelReceived(deviceId, batteryLevel!!)
                                    },
                                    { error: Throwable -> if (error.message != null) logError(error.message!!) },
                                    {}
                                )
                        }
                        BlePMDClient.PMD_SERVICE -> {
                            val blePMDClient = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient?
                            if (blePMDClient != null) {
                                return@flatMap blePMDClient.clientReady(true)
                                    .andThen(
                                        blePMDClient.readFeature(true)
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .doOnSuccess { pmdFeature: Set<PmdMeasurementType> ->
                                                callback?.bleSdkFeatureReady(deviceId, PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)
                                                if (pmdFeature.contains(PmdMeasurementType.SDK_MODE)) {
                                                    callback?.bleSdkFeatureReady(deviceId, PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE)
                                                }
                                            }
                                    )
                                    .toFlowable()
                            }
                        }
                        BleDisClient.DIS_SERVICE -> {
                            val bleDisClient = session.fetchClient(BleDisClient.DIS_SERVICE) as BleDisClient?
                            if (bleDisClient != null) {
                                return@flatMap Flowable.merge(bleDisClient.observeDisInfo(true)
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .doOnNext { pair: android.util.Pair<UUID?, String?> ->
                                        callback?.disInformationReceived(deviceId, pair.first!!, pair.second!!)
                                    }, bleDisClient.observeDisInfoWithKeysAsStrings(true)
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .doOnNext { disInfo ->
                                        callback?.disInformationReceived(deviceId, disInfo)
                                    })
                            }
                        }
                        BlePsFtpUtils.RFC77_PFTP_SERVICE -> {
                            val blePsftpClient = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                            if (blePsftpClient != null) {
                                return@flatMap blePsftpClient.clientReady(true)
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .doOnComplete {
                                        callback?.bleSdkFeatureReady(deviceId, PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER)
                                    }
                                    .toFlowable<Any>()
                            }
                        }
                        HealthThermometer.HTS_SERVICE -> {
                            val bleHtsClient = session.fetchClient(HealthThermometer.HTS_SERVICE) as BleHtsClient?
                            bleHtsClient?.observeHtsNotifications(true)
                                ?.observeOn(AndroidSchedulers.mainThread())
                                ?.subscribe(
                                    { healthThermometerData ->
                                        callback?.htsNotificationReceived(
                                            deviceId,
                                            PolarHealthThermometerData(
                                                healthThermometerData.temperatureCelsius,
                                                healthThermometerData.temperatureFahrenheit
                                            )
                                        )
                                    },
                                    { error: Throwable -> if (error.message != null) logError(error.message!!) },
                                    {})

                        }
                    }
                }
                Flowable.empty()
            }
            .subscribe(
                { },
                { throwable: Throwable -> logError("Error while monitoring session services: $throwable") },
                { log("complete") }
            )
        deviceDataMonitorDisposable[session.address] = disposable
    }

    private fun makeFeatureCallbackIfNeeded(session: BleDeviceSession, discoveredServices: List<UUID>, featurePolarOfflineRecording: PolarBleSdkFeature): Completable {
        val isFeatureAvailable = when (featurePolarOfflineRecording) {
            PolarBleSdkFeature.FEATURE_HR -> isHeartRateFeatureAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_DEVICE_INFO -> isDeviceInfoFeatureAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_BATTERY_INFO -> isBatteryInfoFeatureAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING -> isOnlineStreamingAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING -> isOfflineRecordingAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP -> isPolarDeviceTimeFeatureAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE -> isSdkModeFeatureAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING -> isH10ExerciseFeatureAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER -> isPsftpServiceAvailable(discoveredServices,session)
            PolarBleSdkFeature.FEATURE_HTS -> isHealthThermometerFeatureAvailable(discoveredServices,session)
            PolarBleSdkFeature.FEATURE_POLAR_LED_ANIMATION -> isLedAnimationFeatureAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE -> isPolarFirmwareUpdateFeatureAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA -> isActivityDataFeatureAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA -> isActivityDataFeatureAvailable(discoveredServices, session)
            PolarBleSdkFeature.FEATURE_POLAR_TEMPERATURE_DATA -> isActivityDataFeatureAvailable(discoveredServices, session)
        }

        return isFeatureAvailable.flatMapCompletable {
            if (it) {
                Completable.fromAction {
                    callback?.bleSdkFeatureReady(session.polarDeviceId, featurePolarOfflineRecording)
                }
            } else {
                Completable.complete()
            }
        }
    }

    private fun isHealthThermometerFeatureAvailable(discoveredServices: List<UUID>, session: BleDeviceSession): Single<Boolean> {
        return if (discoveredServices.contains(HealthThermometer.HTS_SERVICE)) {
            val bleHtsClient = session.fetchClient(HealthThermometer.HTS_SERVICE) as BleHtsClient? ?: return Single.just(false)
            bleHtsClient.clientReady(true)
                .toSingle {
                    return@toSingle true
                }
        } else {
            Single.just(false)
        }
    }

    private fun isPolarDeviceTimeFeatureAvailable(discoveredServices: List<UUID>, session: BleDeviceSession): Single<Boolean> {
        return if (discoveredServices.contains(BlePsFtpUtils.RFC77_PFTP_SERVICE)) {
            val blePsftpClient = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return Single.just(false)
            blePsftpClient.clientReady(true)
                .toSingle {
                    return@toSingle true
                }

        } else {
            Single.just(false)
        }
    }

    private fun isBatteryInfoFeatureAvailable(discoveredServices: List<UUID>, session: BleDeviceSession): Single<Boolean> {
        return if (discoveredServices.contains(BleBattClient.BATTERY_SERVICE)) {
            val bleBattClient = session.fetchClient(BleBattClient.BATTERY_SERVICE) as BleBattClient? ?: return Single.just(false)
            bleBattClient.clientReady(true)
                .toSingle {
                    return@toSingle true
                }
        } else {
            Single.just(false)
        }
    }

    private fun isDeviceInfoFeatureAvailable(discoveredServices: List<UUID>, session: BleDeviceSession): Single<Boolean> {
        return if (discoveredServices.contains(BleDisClient.DIS_SERVICE)) {
            val bleDisClient = session.fetchClient(BleDisClient.DIS_SERVICE) as BleDisClient? ?: return Single.just(false)
            bleDisClient.clientReady(true)
                .toSingle {
                    return@toSingle true
                }
        } else {
            Single.just(false)
        }
    }

    private fun isHeartRateFeatureAvailable(discoveredServices: List<UUID>, session: BleDeviceSession): Single<Boolean> {
        return if (discoveredServices.contains(HR_SERVICE)) {
            val bleHrClient = session.fetchClient(HR_SERVICE) as BleHrClient? ?: return Single.just(false)
            bleHrClient.clientReady(true)
                .toSingle {
                    return@toSingle true
                }

        } else {
            Single.just(false)
        }
    }

    private fun isH10ExerciseFeatureAvailable(discoveredServices: List<UUID>, session: BleDeviceSession): Single<Boolean> {
        return if (discoveredServices.contains(BlePsFtpUtils.RFC77_PFTP_SERVICE) && isRecordingSupported(session.polarDeviceType)) {
            val blePsftpClient = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return Single.just(false)
            blePsftpClient.clientReady(true)
                .toSingle {
                    return@toSingle true
                }

        } else {
            Single.just(false)
        }
    }

    private fun isSdkModeFeatureAvailable(discoveredServices: List<UUID>, session: BleDeviceSession): Single<Boolean> {
        return if (discoveredServices.contains(BlePMDClient.PMD_SERVICE)) {
            val blePMDClient = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient? ?: return Single.just(false)
            blePMDClient.clientReady(true)
                .andThen(
                    blePMDClient.readFeature(true)
                        .map { pmdFeatures: Set<PmdMeasurementType> ->
                            pmdFeatures.contains(PmdMeasurementType.SDK_MODE)
                        }
                )
        } else {
            Single.just(false)
        }
    }

    private fun isOnlineStreamingAvailable(discoveredServices: List<UUID>, session: BleDeviceSession): Single<Boolean> {
        val isClientReady = if (discoveredServices.contains(HR_SERVICE)) {
            val bleHrClient = session.fetchClient(HR_SERVICE) as BleHrClient? ?: return Single.just(false)
            bleHrClient.clientReady(true)
        } else {
            Completable.complete()
        }

        return if (discoveredServices.contains(BlePMDClient.PMD_SERVICE)) {
            val blePMDClient = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient? ?: return Single.just(false)
            isClientReady.andThen(
                blePMDClient.clientReady(true)
            ).toSingle {
                return@toSingle true
            }
        } else {
            Single.just(false)
        }
    }

    private fun isPsftpServiceAvailable(discoveredServices: List<UUID>, session: BleDeviceSession): Single<Boolean> {
        return if (discoveredServices.contains(BlePsFtpUtils.RFC77_PFTP_SERVICE)) {
            val blePsftpClient = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return Single.just(false)
            blePsftpClient.clientReady(true).toSingle {
                return@toSingle true
            }
        } else {
            Single.just(false)
        }
    }

    private fun isOfflineRecordingAvailable(discoveredServices: List<UUID>, session: BleDeviceSession): Single<Boolean> {
        return if (discoveredServices.contains(BlePMDClient.PMD_SERVICE) && discoveredServices.contains(BlePsFtpUtils.RFC77_PFTP_SERVICE)) {
            val blePMDClient = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient? ?: return Single.just(false)
            val blePsftpClient = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return Single.just(false)
            Completable.concatArray(
                blePMDClient.clientReady(true),
                blePsftpClient.clientReady(true)
            ).andThen(
                blePMDClient.readFeature(true)
                    .map { pmdFeatures: Set<PmdMeasurementType> ->
                        pmdFeatures.contains(PmdMeasurementType.OFFLINE_RECORDING)
                    }
            )
        } else {
            Single.just(false)
        }
    }

    private fun isLedAnimationFeatureAvailable(discoveredServices: List<UUID>, session: BleDeviceSession): Single<Boolean> {
        return if (discoveredServices.contains(BlePMDClient.PMD_SERVICE) && discoveredServices.contains(BlePsFtpUtils.RFC77_PFTP_SERVICE)) {
            val blePMDClient = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient? ?: return Single.just(false)
            val blePsftpClient = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return Single.just(false)
            Completable.concatArray(
                blePMDClient.clientReady(true),
                blePsftpClient.clientReady(true)
            ).andThen(
                blePMDClient.readFeature(true)
                    .map { pmdFeatures: Set<PmdMeasurementType> ->
                        pmdFeatures.contains(PmdMeasurementType.SDK_MODE)
                    }
            )
        } else {
            Single.just(false)
        }
    }

    private fun isPolarFirmwareUpdateFeatureAvailable(
            discoveredServices: List<UUID>,
            session: BleDeviceSession
    ): Single<Boolean> {
        return if (discoveredServices.contains(BlePsFtpUtils.RFC77_PFTP_SERVICE) && BlePolarDeviceCapabilitiesUtility.isFirmwareUpdateSupported(
                        session.polarDeviceType
                )
        ) {
            val blePsftpClient =
                    session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                            ?: return Single.just(false)
            blePsftpClient.clientReady(true)
                    .toSingle {
                        return@toSingle true
                    }
        } else {
            Single.just(false)
        }
    }

    private fun isActivityDataFeatureAvailable(
        discoveredServices: List<UUID>,
        session: BleDeviceSession
    ): Single<Boolean> {
        return if (discoveredServices.contains(BlePsFtpUtils.RFC77_PFTP_SERVICE) && BlePolarDeviceCapabilitiesUtility.isActivityDataSupported(
                session.polarDeviceType
            )
        ) {
            val blePsftpClient =
                session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                    ?: return Single.just(false)
            blePsftpClient.clientReady(true)
                .toSingle {
                    return@toSingle true
                }
        } else {
            Single.just(false)
        }
    }

    private fun tearDownDevice(session: BleDeviceSession) {
        val address = session.address
        if (deviceDataMonitorDisposable.containsKey(address)) {
            deviceDataMonitorDisposable[address]?.dispose()
            deviceDataMonitorDisposable.remove(address)
        }

        if (deviceAvailableFeaturesDisposable.containsKey(address)) {
            deviceAvailableFeaturesDisposable[address]?.dispose()
            deviceAvailableFeaturesDisposable.remove(address)
        }
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

    fun interface FetchRecursiveCondition {
        fun include(entry: String): Boolean
    }

    private fun fetchRecursively(client: BlePsFtpClient, path: String, condition: FetchRecursiveCondition): Flowable<Pair<String, Long>> {
        val builder = PftpRequest.PbPFtpOperation.newBuilder()
        builder.command = PftpRequest.PbPFtpOperation.Command.GET
        builder.path = path
        return client.request(builder.build().toByteArray())
            .toFlowable()
            .flatMap(Function<ByteArrayOutputStream, Publisher<Pair<String, Long>>> { byteArrayOutputStream: ByteArrayOutputStream ->
                val dir = PbPFtpDirectory.parseFrom(byteArrayOutputStream.toByteArray())
                val entries: MutableMap<String, Long> = mutableMapOf()

                for (entry in dir.entriesList) {
                    if (condition.include(entry.name)) {
                        entries[path + entry.name] = entry.size
                    }
                }

                if (entries.isNotEmpty()) {
                    return@Function Flowable.fromIterable(entries.toList())
                        .flatMap { entry ->
                            if (entry.first.endsWith("/")) {
                                return@flatMap fetchRecursively(client, entry.first, condition)
                            } else {
                                return@flatMap Flowable.just(entry)
                            }
                        }
                }
                Flowable.empty()
            })
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
            return instance?.let {
                if (it.features == features) {
                    it
                } else {
                    throw PolarBleSdkInstanceException("Attempt to create Polar BLE API with features " + features + ". Instance with features " + instance!!.features + " already exists")
                }
            } ?: run {
                instance = BDBleApiImpl(context, features)
                instance!!
            }
        }

        private fun clearInstance() {
            instance = null
        }
    }
}

data class DataDeletionStats(
    var deletedDirs: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue(),
    val fileDeletionMap: ConcurrentHashMap<String, Boolean> = ConcurrentHashMap()
)