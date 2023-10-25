// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl

import android.content.Context
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
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.*
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdControlPointResponse.PmdControlPointResponseCode
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.*
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.PftpResponseError
import com.polar.androidcommunications.api.ble.model.offlinerecording.OfflineRecordingData
import com.polar.androidcommunications.api.ble.model.offlinerecording.OfflineRecordingUtility.mapOfflineRecordingFileNameToMeasurementType
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility.Companion.getFileSystemType
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility.Companion.isRecordingSupported
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility.FileSystemType
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.BDDeviceListenerImpl
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallbackProvider
import com.polar.sdk.api.PolarH10OfflineExerciseApi
import com.polar.sdk.api.errors.*
import com.polar.sdk.api.model.*
import com.polar.sdk.impl.utils.PolarDataUtils
import com.polar.sdk.impl.utils.PolarDataUtils.mapPMDClientOfflineHrDataToPolarHrData
import com.polar.sdk.impl.utils.PolarDataUtils.mapPMDClientOhrDataToPolarOhr
import com.polar.sdk.impl.utils.PolarDataUtils.mapPMDClientPpgDataToPolarPpg
import com.polar.sdk.impl.utils.PolarDataUtils.mapPMDClientPpiDataToPolarOhrPpiData
import com.polar.sdk.impl.utils.PolarDataUtils.mapPMDClientPpiDataToPolarPpiData
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdClientAccDataToPolarAcc
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdClientFeatureToPolarFeature
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdClientGyroDataToPolarGyro
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdClientMagDataToPolarMagnetometer
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdSettingsToPolarSettings
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdTriggerToPolarTrigger
import com.polar.sdk.impl.utils.PolarDataUtils.mapPolarFeatureToPmdClientMeasurementType
import com.polar.sdk.impl.utils.PolarDataUtils.mapPolarOfflineTriggerToPmdOfflineTrigger
import com.polar.sdk.impl.utils.PolarDataUtils.mapPolarSecretToPmdSecret
import com.polar.sdk.impl.utils.PolarDataUtils.mapPolarSettingsToPmdSettings
import com.polar.sdk.impl.utils.PolarTimeUtils.javaCalendarToPbPftpSetLocalTime
import com.polar.sdk.impl.utils.PolarTimeUtils.javaCalendarToPbPftpSetSystemTime
import com.polar.sdk.impl.utils.PolarTimeUtils.pbLocalTimeToJavaCalendar
import fi.polar.remote.representation.protobuf.ExerciseSamples.PbExerciseSamples
import fi.polar.remote.representation.protobuf.Types.*
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.CompletableEmitter
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
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
import protocol.PftpRequest
import protocol.PftpResponse
import protocol.PftpResponse.PbPFtpDirectory
import protocol.PftpResponse.PbRequestRecordingStatusResult
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * The default implementation of the Polar API
 * @Suppress
 */
class BDBleApiImpl private constructor(context: Context, features: Set<PolarBleSdkFeature>) : PolarBleApi(features), BlePowerStateChangedCallback {
    private val connectSubscriptions: MutableMap<String, Disposable> = mutableMapOf()
    private val deviceDataMonitorDisposable: MutableMap<String, Disposable> = mutableMapOf()
    private val deviceAvailableFeaturesDisposable: MutableMap<String, Disposable> = mutableMapOf()
    private val stopPmdStreamingDisposable: MutableMap<String, Disposable> = mutableMapOf()
    private val filter = BleSearchPreFilter { content: BleAdvertisementContent -> content.polarDeviceId.isNotEmpty() && content.polarDeviceType != "mobile" }
    private var listener: BleDeviceListener?
    private var devicesStateMonitorDisposable: Disposable? = null
    private var callback: PolarBleApiCallbackProvider? = null
    private var logger: PolarBleApiLogger? = null

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
                PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING -> clients.add(BlePsFtpClient::class.java)
                PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP -> clients.add(BlePsFtpClient::class.java)
                PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE -> clients.add(BlePMDClient::class.java)
                PolarBleSdkFeature.FEATURE_POLAR_LED_ANIMATION -> clients.add(BlePsFtpClient::class.java)
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
            Thread.currentThread().uncaughtExceptionHandler?.uncaughtException(Thread.currentThread(), e)
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
                PolarBleSdkFeature.FEATURE_POLAR_LED_ANIMATION -> {
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
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return Completable.error(PolarServiceNotAvailable())

        BleLogger.d(TAG, "set local time to ${calendar.time} device $identifier")
        val pbLocalTime = javaCalendarToPbPftpSetLocalTime(calendar)
        return setSystemTime(client, calendar)
            .onErrorComplete()
            .andThen(
                client.query(PftpRequest.PbPFtpQuery.SET_LOCAL_TIME_VALUE, pbLocalTime.toByteArray())
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
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return Single.error(PolarServiceNotAvailable())

        BleLogger.d(TAG, "get local time from device $identifier")
        return client.query(PftpRequest.PbPFtpQuery.GET_LOCAL_TIME_VALUE, null)
            .map {
                val dateTime: PftpRequest.PbPFtpSetLocalTimeParams = PftpRequest.PbPFtpSetLocalTimeParams.parseFrom(it.toByteArray())
                pbLocalTimeToJavaCalendar(dateTime)
            }.onErrorResumeNext {
                if (it is PftpResponseError && it.error == 201) {
                    Single.error(BleNotSupported("${session.name} do not support getTime"))
                } else {
                    Single.error(it)
                }
            }
    }

    override fun requestStreamSettings(identifier: String, feature: PolarDeviceDataType): Single<PolarSensorSetting> {
        BleLogger.d(TAG, "Request online stream settings. Feature: $feature Device: $identifier")
        return when (feature) {
            PolarDeviceDataType.ECG -> querySettings(identifier, PmdMeasurementType.ECG, PmdRecordingType.ONLINE)
            PolarDeviceDataType.ACC -> querySettings(identifier, PmdMeasurementType.ACC, PmdRecordingType.ONLINE)
            PolarDeviceDataType.PPG -> querySettings(identifier, PmdMeasurementType.PPG, PmdRecordingType.ONLINE)
            PolarDeviceDataType.GYRO -> querySettings(identifier, PmdMeasurementType.GYRO, PmdRecordingType.ONLINE)
            PolarDeviceDataType.MAGNETOMETER -> querySettings(identifier, PmdMeasurementType.MAGNETOMETER, PmdRecordingType.ONLINE)
            PolarDeviceDataType.HR,
            PolarDeviceDataType.PPI -> Single.error(PolarOperationNotSupported())
            else -> Single.error(PolarOperationNotSupported())

        }
    }

    override fun requestFullStreamSettings(identifier: String, feature: PolarDeviceDataType): Single<PolarSensorSetting> {
        BleLogger.d(TAG, "Request full online stream settings. Feature: $feature Device: $identifier")
        return when (feature) {
            PolarDeviceDataType.ECG -> queryFullSettings(identifier, PmdMeasurementType.ECG, PmdRecordingType.ONLINE)
            PolarDeviceDataType.ACC -> queryFullSettings(identifier, PmdMeasurementType.ACC, PmdRecordingType.ONLINE)
            PolarDeviceDataType.PPG -> queryFullSettings(identifier, PmdMeasurementType.PPG, PmdRecordingType.ONLINE)
            PolarDeviceDataType.GYRO -> queryFullSettings(identifier, PmdMeasurementType.GYRO, PmdRecordingType.ONLINE)
            PolarDeviceDataType.MAGNETOMETER -> queryFullSettings(identifier, PmdMeasurementType.MAGNETOMETER, PmdRecordingType.ONLINE)
            PolarDeviceDataType.PPI,
            PolarDeviceDataType.HR -> Single.error(PolarOperationNotSupported())
            else -> Single.error(PolarOperationNotSupported())
        }
    }

    override fun requestOfflineRecordingSettings(identifier: String, feature: PolarDeviceDataType): Single<PolarSensorSetting> {
        BleLogger.d(TAG, "Request offline recording settings. Feature: $feature Device: $identifier")
        return when (feature) {
            PolarDeviceDataType.ECG -> querySettings(identifier, PmdMeasurementType.ECG, PmdRecordingType.OFFLINE)
            PolarDeviceDataType.ACC -> querySettings(identifier, PmdMeasurementType.ACC, PmdRecordingType.OFFLINE)
            PolarDeviceDataType.PPG -> querySettings(identifier, PmdMeasurementType.PPG, PmdRecordingType.OFFLINE)
            PolarDeviceDataType.GYRO -> querySettings(identifier, PmdMeasurementType.GYRO, PmdRecordingType.OFFLINE)
            PolarDeviceDataType.MAGNETOMETER -> querySettings(identifier, PmdMeasurementType.MAGNETOMETER, PmdRecordingType.OFFLINE)
            PolarDeviceDataType.HR,
            PolarDeviceDataType.PPI -> Single.error(PolarOperationNotSupported())
            else -> Single.error(PolarOperationNotSupported())
        }
    }

    override fun requestFullOfflineRecordingSettings(identifier: String, feature: PolarDeviceDataType): Single<PolarSensorSetting> {
        BleLogger.d(TAG, "Request full offline recording settings. Feature: $feature Device: $identifier")
        return when (feature) {
            PolarDeviceDataType.ECG -> queryFullSettings(identifier, PmdMeasurementType.ECG, PmdRecordingType.OFFLINE)
            PolarDeviceDataType.ACC -> queryFullSettings(identifier, PmdMeasurementType.ACC, PmdRecordingType.OFFLINE)
            PolarDeviceDataType.PPG -> queryFullSettings(identifier, PmdMeasurementType.PPG, PmdRecordingType.OFFLINE)
            PolarDeviceDataType.GYRO -> queryFullSettings(identifier, PmdMeasurementType.GYRO, PmdRecordingType.OFFLINE)
            PolarDeviceDataType.MAGNETOMETER -> queryFullSettings(identifier, PmdMeasurementType.MAGNETOMETER, PmdRecordingType.OFFLINE)
            PolarDeviceDataType.PPI,
            PolarDeviceDataType.HR -> Single.error(PolarOperationNotSupported())
            else -> Single.error(PolarOperationNotSupported())
        }
    }

    private fun querySettings(identifier: String, type: PmdMeasurementType, recordingType: PmdRecordingType): Single<PolarSensorSetting> {
        return try {
            val session = sessionPmdClientReady(identifier)
            val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient? ?: return Single.error(PolarServiceNotAvailable())
            client.querySettings(type, recordingType)
                .map { setting: PmdSetting -> mapPmdSettingsToPolarSettings(setting, fromSelected = false) }
        } catch (e: Throwable) {
            Single.error(e)
        }
    }

    private fun queryFullSettings(identifier: String, type: PmdMeasurementType, recordingType: PmdRecordingType): Single<PolarSensorSetting> {
        return try {
            val session = sessionPmdClientReady(identifier)
            val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient? ?: return Single.error(PolarServiceNotAvailable())
            client.queryFullSettings(type, recordingType)
                .map { setting: PmdSetting -> mapPmdSettingsToPolarSettings(setting, fromSelected = false) }
        } catch (e: Throwable) {
            Single.error(e)
        }
    }

    override fun foregroundEntered() {
        listener?.scanRestart()
    }

    override fun autoConnectToDevice(rssiLimit: Int, service: String?, timeout: Int, unit: TimeUnit, polarDeviceType: String?): Completable {
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
                            && (service == null || bleDeviceSession.advertisementContent.containsService(service))
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
                    .reduce(HashSet(), BiFunction { objects: MutableSet<BleDeviceSession>, bleDeviceSessionTimed: Timed<BleDeviceSession> ->
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

    override fun autoConnectToDevice(rssiLimit: Int, service: String?, polarDeviceType: String?): Completable {
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

    override fun startRecording(identifier: String, exerciseId: String, interval: PolarH10OfflineExerciseApi.RecordingInterval?, type: PolarH10OfflineExerciseApi.SampleType): Completable {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Completable.error(error)
        }
        if (isRecordingSupported(session.polarDeviceType)) {
            val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return Completable.error(PolarServiceNotAvailable())
            val pbSampleType = if (type == PolarH10OfflineExerciseApi.SampleType.HR) PbSampleType.SAMPLE_TYPE_HEART_RATE else PbSampleType.SAMPLE_TYPE_RR_INTERVAL
            val recordingInterval = interval?.value ?: PolarH10OfflineExerciseApi.RecordingInterval.INTERVAL_1S.value
            val duration = PbDuration.newBuilder().setSeconds(recordingInterval).build()
            val params = PftpRequest.PbPFtpRequestStartRecordingParams.newBuilder().setSampleDataIdentifier(exerciseId)
                .setSampleType(pbSampleType)
                .setRecordingInterval(duration)
                .build()
            return client.query(PftpRequest.PbPFtpQuery.REQUEST_START_RECORDING_VALUE, params.toByteArray())
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
            val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return Completable.error(PolarServiceNotAvailable())
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
            val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return Single.error(PolarServiceNotAvailable())
            return client.query(PftpRequest.PbPFtpQuery.REQUEST_RECORDING_STATUS_VALUE, null)
                .map { byteArrayOutputStream: ByteArrayOutputStream ->
                    val result = PbRequestRecordingStatusResult.parseFrom(byteArrayOutputStream.toByteArray())
                    androidx.core.util.Pair(result.recordingOn, if (result.hasSampleDataIdentifier()) result.sampleDataIdentifier else "")
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
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return Flowable.error(PolarServiceNotAvailable())

        when (getFileSystemType(session.polarDeviceType)) {
            FileSystemType.SAGRFC2_FILE_SYSTEM -> {
                BleLogger.d(TAG, "Start offline recording listing in device: $identifier")
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
                    val type = mapPmdClientFeatureToPolarFeature(mapOfflineRecordingFileNameToMeasurementType(components[6]))
                    PolarOfflineRecordingEntry(path = entry.first, size = entry.second, date = date, type = type)
                }.onErrorResumeNext { throwable: Throwable -> Flowable.error(handleError(throwable)) }
            }
            else -> return Flowable.error(PolarOperationNotSupported())
        }
    }

    override fun listExercises(identifier: String): Flowable<PolarExerciseEntry> {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Flowable.error(error)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return Flowable.error(PolarServiceNotAvailable())

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
                    .onErrorResumeNext { throwable: Throwable -> Flowable.error(handleError(throwable)) }
            }
            FileSystemType.H10_FILE_SYSTEM -> {
                return fetchRecursively(client = client,
                    path = "/",
                    condition = { entry -> entry.endsWith("/") || entry == "SAMPLES.BPB" })
                    .map { entry: Pair<String, Long> ->
                        val components = entry.first.split("/").toTypedArray()
                        PolarExerciseEntry(entry.first, Date(), components[1])
                    }
                    .onErrorResumeNext { throwable: Throwable -> Flowable.error(handleError(throwable)) }
            }
            else -> return Flowable.error(PolarOperationNotSupported())
        }
    }


    override fun fetchExercise(identifier: String, entry: PolarExerciseEntry): Single<PolarExerciseData> {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Single.error(error)
        }

        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return Single.error(PolarServiceNotAvailable())
        val fsType = getFileSystemType(session.polarDeviceType)
        val beforeFetch = if (fsType == FileSystemType.H10_FILE_SYSTEM) {
            client.sendNotification(PftpNotification.PbPFtpHostToDevNotification.INITIALIZE_SESSION_VALUE, null)
                .andThen(client.sendNotification(PftpNotification.PbPFtpHostToDevNotification.START_SYNC_VALUE, null))
        } else {
            Completable.complete()
        }

        val afterFetch = if (fsType == FileSystemType.H10_FILE_SYSTEM) {
            client.sendNotification(PftpNotification.PbPFtpHostToDevNotification.STOP_SYNC_VALUE, null)
                .andThen(client.sendNotification(PftpNotification.PbPFtpHostToDevNotification.TERMINATE_SESSION_VALUE, null))
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
                    return@map PolarExerciseData(samples.recordingInterval.seconds, samples.rrSamples.rrIntervalsList)
                } else {
                    return@map PolarExerciseData(samples.recordingInterval.seconds, samples.heartRateSamplesList)
                }
            }
            .onErrorResumeNext { throwable: Throwable -> Single.error(handleError(throwable)) }
            .doFinally {
                afterFetch
                    .onErrorComplete()
                    .subscribe()
            }
    }

    override fun getOfflineRecord(identifier: String, entry: PolarOfflineRecordingEntry, secret: PolarRecordingSecret?): Single<PolarOfflineRecordingData> {
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (e: Exception) {
            return Single.error(e)
        }

        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return Single.error(PolarServiceNotAvailable())
        val fsType = getFileSystemType(session.polarDeviceType)
        return if (fsType == FileSystemType.SAGRFC2_FILE_SYSTEM) {
            val builder = PftpRequest.PbPFtpOperation.newBuilder()
            builder.command = PftpRequest.PbPFtpOperation.Command.GET
            builder.path = entry.path

            BleLogger.d(TAG, "Offline record get. Device: $identifier Path: ${entry.path} Secret used: ${secret != null}")
            client.sendNotification(PftpNotification.PbPFtpHostToDevNotification.INITIALIZE_SESSION_VALUE, null)
                .andThen(client.request(builder.build().toByteArray()))
                .map { byteArrayOutputStream: ByteArrayOutputStream ->
                    val pmdSecret = secret?.let { mapPolarSecretToPmdSecret(it) }
                    OfflineRecordingData.parseDataFromOfflineFile(byteArrayOutputStream.toByteArray(), mapPolarFeatureToPmdClientMeasurementType(entry.type), pmdSecret)
                }
                .map { offlineRecData ->
                    val polarSettings = offlineRecData.recordingSettings?.let { mapPmdSettingsToPolarSettings(it, fromSelected = false) }
                    val startTime = offlineRecData.startTime
                    when (val offlineData = offlineRecData.data) {
                        is AccData -> {
                            polarSettings ?: throw PolarOfflineRecordingError("getOfflineRecord failed. Acc data is missing settings")
                            PolarOfflineRecordingData.AccOfflineRecording(mapPmdClientAccDataToPolarAcc(offlineData), startTime, polarSettings)
                        }
                        is GyrData -> {
                            polarSettings ?: throw PolarOfflineRecordingError("getOfflineRecord failed. Gyro data is missing settings")
                            PolarOfflineRecordingData.GyroOfflineRecording(mapPmdClientGyroDataToPolarGyro(offlineData), startTime, polarSettings)
                        }
                        is MagData -> {
                            polarSettings ?: throw PolarOfflineRecordingError("getOfflineRecord failed. Magnetometer data is missing settings")
                            PolarOfflineRecordingData.MagOfflineRecording(mapPmdClientMagDataToPolarMagnetometer(offlineData), startTime, polarSettings)
                        }
                        is PpgData -> {
                            polarSettings ?: throw PolarOfflineRecordingError("getOfflineRecord failed. Ppg data is missing settings")
                            PolarOfflineRecordingData.PpgOfflineRecording(mapPMDClientPpgDataToPolarPpg(offlineData), startTime, polarSettings)
                        }
                        is PpiData -> PolarOfflineRecordingData.PpiOfflineRecording(mapPMDClientPpiDataToPolarPpiData(offlineData), startTime)
                        is OfflineHrData -> PolarOfflineRecordingData.HrOfflineRecording(mapPMDClientOfflineHrDataToPolarHrData(offlineData), startTime)
                        else -> throw PolarOfflineRecordingError("Data type is not supported.")
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
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return Completable.error(PolarServiceNotAvailable())

        when (getFileSystemType(session.polarDeviceType)) {
            FileSystemType.SAGRFC2_FILE_SYSTEM -> {
                val builder = PftpRequest.PbPFtpOperation.newBuilder()
                builder.command = PftpRequest.PbPFtpOperation.Command.GET
                val components = entry.path.split("/").toTypedArray()
                val exerciseParent = "/U/0/" + components[3] + "/E/"
                builder.path = exerciseParent

                return client.request(builder.build().toByteArray())
                    .flatMap { byteArrayOutputStream: ByteArrayOutputStream ->
                        val directory = PbPFtpDirectory.parseFrom(byteArrayOutputStream.toByteArray())
                        val removeBuilder = PftpRequest.PbPFtpOperation.newBuilder()
                        removeBuilder.command = PftpRequest.PbPFtpOperation.Command.REMOVE
                        if (directory.entriesCount <= 1) {
                            // remove entire directory
                            removeBuilder.path = "/U/0/" + components[3] + "/"
                        } else {
                            // remove only exercise
                            removeBuilder.path = "/U/0/" + components[3] + "/E/" + components[5] + "/"
                        }
                        client.request(removeBuilder.build().toByteArray())
                    }
                    .toObservable()
                    .ignoreElements()
                    .onErrorResumeNext { throwable: Throwable -> Completable.error(handleError(throwable)) }
            }

            FileSystemType.H10_FILE_SYSTEM -> {
                val builder = PftpRequest.PbPFtpOperation.newBuilder()
                builder.command = PftpRequest.PbPFtpOperation.Command.REMOVE
                builder.path = entry.path
                return client.request(builder.build().toByteArray())
                    .toObservable()
                    .ignoreElements()
                    .onErrorResumeNext { throwable: Throwable -> Completable.error(handleError(throwable)) }
            }
            FileSystemType.UNKNOWN_FILE_SYSTEM -> {
                return Completable.error(PolarOperationNotSupported())
            }
        }
    }

    override fun removeOfflineRecord(identifier: String, entry: PolarOfflineRecordingEntry): Completable {
        BleLogger.d(TAG, "Remove offline record from device $identifier path ${entry.path}")
        val session = try {
            sessionPsFtpClientReady(identifier)
        } catch (error: Throwable) {
            return Completable.error(error)
        }

        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return Completable.error(PolarServiceNotAvailable())
        val fsType = getFileSystemType(session.polarDeviceType)
        return if (fsType == FileSystemType.SAGRFC2_FILE_SYSTEM) {
            removeOfflineFilesRecursively(client, entry.path, whileContaining = Regex("/\\d{8}/"))
        } else {
            Completable.error(PolarOperationNotSupported())
        }
    }

    private fun removeOfflineFilesRecursively(client: BlePsFtpClient, deletePath: String, whileContaining: Regex? = null): Completable {
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
                val parentDirEntries = PbPFtpDirectory.parseFrom(byteArrayOutputStream.toByteArray())
                val isParentDirValid = whileContaining?.let { parentDir.contains(it) } ?: true

                if (parentDirEntries.entriesCount <= 1 && isParentDirValid) {
                    // the parent directory is valid to be deleted
                    return@flatMapCompletable removeOfflineFilesRecursively(client, parentDir, whileContaining)
                } else {
                    val removeBuilder = PftpRequest.PbPFtpOperation.newBuilder()
                    removeBuilder.command = PftpRequest.PbPFtpOperation.Command.REMOVE
                    removeBuilder.path = deletePath
                    BleLogger.d(TAG, "Remove offline recording from the path $deletePath")
                    return@flatMapCompletable client.request(removeBuilder.build().toByteArray()).toObservable().ignoreElements()
                }
            }
    }

    override fun searchForDevice(): Flowable<PolarDeviceInfo> {
        listener?.let {
            return it.search(false)
                .distinct()
                .map { bleDeviceSession: BleDeviceSession ->
                    PolarDeviceInfo(
                        bleDeviceSession.polarDeviceId,
                        bleDeviceSession.address,
                        bleDeviceSession.rssi,
                        bleDeviceSession.name,
                        bleDeviceSession.isConnectableAdvertisement
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
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return Single.error(PolarServiceNotAvailable())
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
                val sdkModeLedByte = if (ledConfig.sdkModeLedEnabled) LedConfig.LED_ANIMATION_ENABLE_BYTE else LedConfig.LED_ANIMATION_DISABLE_BYTE
                val ppiModeLedByte = if (ledConfig.ppiModeLedEnabled) LedConfig.LED_ANIMATION_ENABLE_BYTE else LedConfig.LED_ANIMATION_DISABLE_BYTE
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

    override fun startOhrStreaming(identifier: String, sensorSetting: PolarSensorSetting): Flowable<PolarOhrData> {
        return startStreaming(identifier, PmdMeasurementType.PPG, sensorSetting, observer = { client: BlePMDClient ->
            client.monitorPpgNotifications(true)
                .map { ppgData: PpgData -> mapPMDClientOhrDataToPolarOhr(ppgData) }
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

    override fun startOhrPPIStreaming(identifier: String): Flowable<PolarOhrPPIData> {
        return startStreaming(identifier, PmdMeasurementType.PPI, PolarSensorSetting(emptyMap())) { client: BlePMDClient ->
            client.monitorPpiNotifications(true)
                .map { ppiData: PpiData ->
                    mapPMDClientPpiDataToPolarOhrPpiData(ppiData)
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
                if (pmdFeature.contains(PmdMeasurementType.OFFLINE_HR)) deviceData.add(PolarDeviceDataType.HR)
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

                        deviceData
                    })
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
                            Completable.fromAction { callback?.hrFeatureReady(deviceId) }
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
                                                val deviceData: MutableSet<PolarDeviceDataType> = HashSet()
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
                                                callback?.streamingFeaturesReady(deviceId, deviceData)
                                                if (pmdFeature.contains(PmdMeasurementType.SDK_MODE)) {
                                                    callback?.sdkModeFeatureAvailable(deviceId)
                                                }
                                            })
                                    .toFlowable()
                            }
                        }
                        BleDisClient.DIS_SERVICE -> {
                            val bleDisClient = session.fetchClient(BleDisClient.DIS_SERVICE) as BleDisClient?
                            if (bleDisClient != null) {
                                return@flatMap bleDisClient.observeDisInfo(true)
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .doOnNext { pair: android.util.Pair<UUID?, String?> ->
                                        callback?.disInformationReceived(deviceId, pair.first!!, pair.second!!)
                                    }
                            }
                        }
                        BlePsFtpUtils.RFC77_PFTP_SERVICE -> {
                            val blePsftpClient = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                            if (blePsftpClient != null) {
                                return@flatMap blePsftpClient.clientReady(true)
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .doOnComplete {
                                        callback?.polarFtpFeatureReady(deviceId)
                                    }.toFlowable<Any>()
                            }
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
            PolarBleSdkFeature.FEATURE_POLAR_LED_ANIMATION -> isLedAnimationFeatureAvailable(discoveredServices, session)
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