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
import com.polar.androidcommunications.api.ble.model.gatt.client.BleHrClient.HrNotificationData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.*
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdControlPointResponse.PmdControlPointResponseCode
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.*
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.PftpResponseError
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility.Companion.getFileSystemType
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility.Companion.isRecordingSupported
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility.FileSystemType
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.BDDeviceListenerImpl
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallbackProvider
import com.polar.sdk.api.errors.*
import com.polar.sdk.api.model.*
import com.polar.sdk.api.model.utils.PolarDataUtils
import com.polar.sdk.api.model.utils.PolarDataUtils.mapPMDClientOhrDataToPolarOhr
import com.polar.sdk.api.model.utils.PolarDataUtils.mapPMDClientPpiDataToPolarOhrPpiData
import com.polar.sdk.api.model.utils.PolarDataUtils.mapPmdClientAccDataToPolarAcc
import com.polar.sdk.api.model.utils.PolarDataUtils.mapPmdClientGyroDataToPolarGyro
import com.polar.sdk.api.model.utils.PolarDataUtils.mapPmdClientMagDataToPolarMagnetometer
import com.polar.sdk.api.model.utils.PolarDataUtils.mapPmdSettingsToPolarSettings
import com.polar.sdk.api.model.utils.PolarDataUtils.mapPolarSettingsToPmdSettings
import com.polar.sdk.api.model.utils.PolarTimeUtils.javaCalendarToPbPftpSetLocalTime
import com.polar.sdk.api.model.utils.PolarTimeUtils.javaCalendarToPbPftpSetSystemTime
import com.polar.sdk.api.model.utils.PolarTimeUtils.pbLocalTimeToJavaCalendar
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
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.schedulers.Timed
import org.reactivestreams.Publisher
import protocol.PftpError.PbPFtpError
import protocol.PftpNotification
import protocol.PftpRequest
import protocol.PftpResponse.PbPFtpDirectory
import protocol.PftpResponse.PbRequestRecordingStatusResult
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * The default implementation of the Polar API
 */
class BDBleApiImpl private constructor(context: Context, features: Int) : PolarBleApi(features), BlePowerStateChangedCallback {
    private val connectSubscriptions: MutableMap<String, Disposable> = mutableMapOf()
    private val deviceDataMonitorDisposable: MutableMap<String, Disposable> = mutableMapOf()
    private val stopPmdStreamingDisposable: MutableMap<String, Disposable> = mutableMapOf()
    private val filter = BleSearchPreFilter { content: BleAdvertisementContent -> content.polarDeviceId.isNotEmpty() && content.polarDeviceType != "mobile" }
    private var listener: BleDeviceListener?
    private var devicesStateMonitorDisposable: Disposable? = null
    private var callback: PolarBleApiCallbackProvider? = null
    private var logger: PolarBleApiLogger? = null

    init {
        val clients: MutableSet<Class<out BleGattBase>> = HashSet()
        if (this.features and FEATURE_HR != 0) {
            clients.add(BleHrClient::class.java)
        }
        if (this.features and FEATURE_DEVICE_INFO != 0) {
            clients.add(BleDisClient::class.java)
        }
        if (this.features and FEATURE_BATTERY_INFO != 0) {
            clients.add(BleBattClient::class.java)
        }
        if (this.features and FEATURE_POLAR_SENSOR_STREAMING != 0) {
            clients.add(BlePMDClient::class.java)
        }
        if (this.features and FEATURE_POLAR_FILE_TRANSFER != 0) {
            clients.add(BlePsFtpClient::class.java)
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
        })
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

    override fun isFeatureReady(deviceId: String, feature: Int): Boolean {
        return try {
            return when (feature) {
                FEATURE_POLAR_FILE_TRANSFER -> {
                    sessionPsFtpClientReady(deviceId)
                    true
                }
                FEATURE_POLAR_SENSOR_STREAMING -> {
                    sessionPmdClientReady(deviceId)
                    true
                }
                else -> false
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
        return try {
            val session = sessionPsFtpClientReady(identifier)
            val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return Completable.error(PolarServiceNotAvailable())

            BleLogger.d(TAG, "set local time to ${calendar.time} device $identifier")
            val pbLocalTime = javaCalendarToPbPftpSetLocalTime(calendar)
            setSystemTime(client, calendar)
                .onErrorComplete()
                .andThen(
                    client.query(PftpRequest.PbPFtpQuery.SET_LOCAL_TIME_VALUE, pbLocalTime.toByteArray())
                        .ignoreElement()
                )

        } catch (error: Throwable) {
            Completable.error(error)
        }
    }

    private fun setSystemTime(client: BlePsFtpClient, calendar: Calendar): Completable {
        val pbTime = javaCalendarToPbPftpSetSystemTime(calendar)
        return client.query(PftpRequest.PbPFtpQuery.SET_SYSTEM_TIME_VALUE, pbTime.toByteArray())
            .ignoreElement()
    }

    override fun getLocalTime(identifier: String): Single<Calendar> {
        return try {
            val session = sessionPsFtpClientReady(identifier)
            val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            if (client != null) {
                BleLogger.d(TAG, "get local time from device $identifier")
                client.query(PftpRequest.PbPFtpQuery.GET_LOCAL_TIME_VALUE, null)
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
            } else {
                Single.error(PolarServiceNotAvailable())
            }
        } catch (error: Throwable) {
            Single.error(error)
        }
    }

    override fun requestStreamSettings(identifier: String, feature: DeviceStreamingFeature): Single<PolarSensorSetting> {
        return when (feature) {
            DeviceStreamingFeature.ECG -> querySettings(identifier, PmdMeasurementType.ECG)
            DeviceStreamingFeature.ACC -> querySettings(identifier, PmdMeasurementType.ACC)
            DeviceStreamingFeature.PPG -> querySettings(identifier, PmdMeasurementType.PPG)
            DeviceStreamingFeature.PPI -> Single.error(PolarOperationNotSupported())
            DeviceStreamingFeature.GYRO -> querySettings(identifier, PmdMeasurementType.GYRO)
            DeviceStreamingFeature.MAGNETOMETER -> querySettings(identifier, PmdMeasurementType.MAGNETOMETER)
        }
    }

    override fun requestFullStreamSettings(identifier: String, feature: DeviceStreamingFeature): Single<PolarSensorSetting> {
        return when (feature) {
            DeviceStreamingFeature.ECG -> queryFullSettings(identifier, PmdMeasurementType.ECG)
            DeviceStreamingFeature.ACC -> queryFullSettings(identifier, PmdMeasurementType.ACC)
            DeviceStreamingFeature.PPG -> queryFullSettings(identifier, PmdMeasurementType.PPG)
            DeviceStreamingFeature.PPI -> Single.error(PolarOperationNotSupported())
            DeviceStreamingFeature.GYRO -> queryFullSettings(identifier, PmdMeasurementType.GYRO)
            DeviceStreamingFeature.MAGNETOMETER -> queryFullSettings(identifier, PmdMeasurementType.MAGNETOMETER)
        }
    }

    private fun querySettings(identifier: String, type: PmdMeasurementType): Single<PolarSensorSetting> {
        return try {
            val session = sessionPmdClientReady(identifier)
            val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient?
            if (client != null) {
                client.querySettings(type)
                    .map { setting: PmdSetting -> mapPmdSettingsToPolarSettings(setting, fromSelected = false) }
            } else {
                Single.error(PolarServiceNotAvailable())
            }
        } catch (e: Throwable) {
            Single.error(e)
        }
    }

    private fun queryFullSettings(identifier: String, type: PmdMeasurementType): Single<PolarSensorSetting> {
        return try {
            val session = sessionPmdClientReady(identifier)
            val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient?
            if (client != null) {
                client.queryFullSettings(type)
                    .map { setting: PmdSetting -> mapPmdSettingsToPolarSettings(setting, fromSelected = false) }
            } else {
                Single.error(PolarServiceNotAvailable())
            }
        } catch (e: Throwable) {
            Single.error(e)
        }
    }

    @Deprecated("in release 3.2.8. Move to the background is not relevant information for SDK starting from release 3.2.8")
    override fun backgroundEntered() {
        BleLogger.w(TAG, "call of deprecated backgroundEntered() method")
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

    override fun startRecording(identifier: String, exerciseId: String, interval: RecordingInterval?, type: SampleType): Completable {
        return try {
            val session = sessionPsFtpClientReady(identifier)
            val recordingSupported = isRecordingSupported(session.polarDeviceType)
            if (recordingSupported) {
                val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                if (client != null) {
                    val pbSampleType = if (type === SampleType.HR) PbSampleType.SAMPLE_TYPE_HEART_RATE else PbSampleType.SAMPLE_TYPE_RR_INTERVAL
                    val recordingInterval = interval?.value ?: RecordingInterval.INTERVAL_1S.value
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
                    Completable.error(PolarServiceNotAvailable())
                }
            } else {
                Completable.error(PolarOperationNotSupported())
            }
        } catch (error: Throwable) {
            Completable.error(error)
        }
    }

    override fun stopRecording(identifier: String): Completable {
        return try {
            val session = sessionPsFtpClientReady(identifier)
            if (isRecordingSupported(session.polarDeviceType)) {
                val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                if (client != null) {
                    client.query(PftpRequest.PbPFtpQuery.REQUEST_STOP_RECORDING_VALUE, null)
                        .toObservable()
                        .ignoreElements()
                        .onErrorResumeNext { throwable: Throwable -> Completable.error(handleError(throwable)) }
                } else {
                    Completable.error(PolarServiceNotAvailable())
                }
            } else Completable.error(PolarOperationNotSupported())
        } catch (error: Throwable) {
            Completable.error(error)
        }
    }

    override fun requestRecordingStatus(identifier: String): Single<androidx.core.util.Pair<Boolean, String>> {
        return try {
            val session = sessionPsFtpClientReady(identifier)
            if (isRecordingSupported(session.polarDeviceType)) {
                val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                if (client != null) {
                    client.query(PftpRequest.PbPFtpQuery.REQUEST_RECORDING_STATUS_VALUE, null)
                        .map { byteArrayOutputStream: ByteArrayOutputStream ->
                            val result = PbRequestRecordingStatusResult.parseFrom(byteArrayOutputStream.toByteArray())
                            androidx.core.util.Pair(result.recordingOn, if (result.hasSampleDataIdentifier()) result.sampleDataIdentifier else "")
                        }.onErrorResumeNext { throwable: Throwable -> Single.error(handleError(throwable)) }
                } else Single.error(PolarServiceNotAvailable())
            } else Single.error(PolarOperationNotSupported())
        } catch (error: Throwable) {
            Single.error(error)
        }
    }

    override fun listExercises(identifier: String): Flowable<PolarExerciseEntry> {
        try {
            val session = sessionPsFtpClientReady(identifier)
            val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            client?.let {
                when (getFileSystemType(session.polarDeviceType)) {
                    FileSystemType.SAGRFC2_FILE_SYSTEM -> {
                        return fetchRecursively(
                            client = client,
                            path = "/U/0/",
                            condition = { entry ->
                                entry.matches(Regex("^([0-9]{8})(/)")) ||
                                        entry.matches(Regex("^([0-9]{6})(/)")) ||
                                        entry == "E/" ||
                                        entry == "SAMPLES.BPB" ||
                                        entry == "00/"
                            }
                        ).map { entry: Pair<String, Long> ->
                            val components = entry.first.split("/").toTypedArray()
                            val format = SimpleDateFormat("yyyyMMdd HHmmss", Locale.getDefault())
                            val date = format.parse(components[3] + " " + components[5])
                            PolarExerciseEntry(entry.first, date, components[3] + components[5])
                        }.onErrorResumeNext { throwable: Throwable -> Flowable.error(handleError(throwable)) }
                    }
                    FileSystemType.H10_FILE_SYSTEM -> {
                        return fetchRecursively(
                            client = client,
                            path = "/",
                            condition = { entry -> entry.endsWith("/") || entry == "SAMPLES.BPB" }
                        ).map { entry: Pair<String, Long> ->
                            val components = entry.first.split("/").toTypedArray()
                            PolarExerciseEntry(entry.first, Date(), components[1])
                        }
                            .onErrorResumeNext { throwable: Throwable -> Flowable.error(handleError(throwable)) }
                    }
                    else -> return Flowable.error(PolarOperationNotSupported())
                }
            }
            return Flowable.error(PolarServiceNotAvailable())
        } catch (error: Throwable) {
            return Flowable.error(error)
        }
    }

    override fun fetchExercise(identifier: String, entry: PolarExerciseEntry): Single<PolarExerciseData> {
        return try {
            val session = sessionPsFtpClientReady(identifier)
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

            beforeFetch
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
        } catch (error: Throwable) {
            Single.error(error)
        }
    }

    override fun removeExercise(identifier: String, entry: PolarExerciseEntry): Completable {
        return try {
            val session = sessionPsFtpClientReady(identifier)
            val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            if (client != null) {
                val fsType = getFileSystemType(session.polarDeviceType)
                if (fsType === FileSystemType.SAGRFC2_FILE_SYSTEM) {
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
                } else if (fsType === FileSystemType.H10_FILE_SYSTEM) {
                    val builder = PftpRequest.PbPFtpOperation.newBuilder()
                    builder.command = PftpRequest.PbPFtpOperation.Command.REMOVE
                    builder.path = entry.path
                    return client.request(builder.build().toByteArray())
                        .toObservable()
                        .ignoreElements()
                        .onErrorResumeNext { throwable: Throwable -> Completable.error(handleError(throwable)) }
                }
                Completable.error(PolarOperationNotSupported())
            } else {
                Completable.error(PolarServiceNotAvailable())
            }
        } catch (error: Throwable) {
            Completable.error(error)
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

    private fun <T : Any> startStreaming(identifier: String, type: PmdMeasurementType, setting: PolarSensorSetting, observer: Function<BlePMDClient, Flowable<T>>): Flowable<T> {
        return try {
            val session = sessionPmdClientReady(identifier)
            val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient?
            if (client != null) {
                client.startMeasurement(type, mapPolarSettingsToPmdSettings(setting))
                    .andThen(observer.apply(client)
                        .onErrorResumeNext { throwable: Throwable -> Flowable.error(handleError(throwable)) }
                        .doFinally { stopPmdStreaming(session, client, type) })
            } else {
                Flowable.error(PolarServiceNotAvailable())
            }
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

    override fun startOhrStreaming(identifier: String, sensorSetting: PolarSensorSetting): Flowable<PolarOhrData> {
        return startStreaming(identifier, PmdMeasurementType.PPG, sensorSetting, observer = { client: BlePMDClient ->
            client.monitorPpgNotifications(true)
                .map { ppgData: PpgData -> mapPMDClientOhrDataToPolarOhr(ppgData) }
        })
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
            val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient?
            return if (client != null && client.isServiceDiscovered) {
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
            val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient?
            if (client != null && client.isServiceDiscovered) {
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
            val disposable = client.stopMeasurement(type).subscribe(
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
        val disposable = session.monitorServicesDiscovered(true)
            .toFlowable()
            .flatMapIterable { uuids: List<UUID> -> uuids }
            .flatMap { uuid: UUID ->
                if (session.fetchClient(uuid) != null) {
                    when (uuid) {
                        BleHrClient.HR_SERVICE -> {
                            Completable.fromAction { callback?.hrFeatureReady(deviceId) }
                                .subscribeOn(AndroidSchedulers.mainThread())
                                .subscribe()

                            val bleHrClient = session.fetchClient(BleHrClient.HR_SERVICE) as BleHrClient?
                            bleHrClient?.observeHrNotifications(true)
                                ?.observeOn(AndroidSchedulers.mainThread())
                                ?.subscribe(
                                    { hrNotificationData: HrNotificationData ->
                                        callback?.hrNotificationReceived(
                                            deviceId,
                                            PolarHrData(
                                                hrNotificationData.hrValue,
                                                hrNotificationData.rrs,
                                                hrNotificationData.sensorContact,
                                                hrNotificationData.sensorContactSupported,
                                                hrNotificationData.rrPresent
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
                                                val deviceStreamingFeatures: MutableSet<DeviceStreamingFeature> = HashSet()
                                                if (pmdFeature.contains(PmdMeasurementType.ECG)) {
                                                    deviceStreamingFeatures.add(DeviceStreamingFeature.ECG)
                                                }
                                                if (pmdFeature.contains(PmdMeasurementType.ACC)) {
                                                    deviceStreamingFeatures.add(DeviceStreamingFeature.ACC)
                                                }
                                                if (pmdFeature.contains(PmdMeasurementType.PPG)) {
                                                    deviceStreamingFeatures.add(DeviceStreamingFeature.PPG)
                                                }
                                                if (pmdFeature.contains(PmdMeasurementType.PPI)) {
                                                    deviceStreamingFeatures.add(DeviceStreamingFeature.PPI)
                                                }
                                                if (pmdFeature.contains(PmdMeasurementType.GYRO)) {
                                                    deviceStreamingFeatures.add(DeviceStreamingFeature.GYRO)
                                                }
                                                if (pmdFeature.contains(PmdMeasurementType.MAGNETOMETER)) {
                                                    deviceStreamingFeatures.add(DeviceStreamingFeature.MAGNETOMETER)
                                                }
                                                callback?.streamingFeaturesReady(deviceId, deviceStreamingFeatures)
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

    private fun tearDownDevice(session: BleDeviceSession) {
        val address = session.address
        if (deviceDataMonitorDisposable.containsKey(address)) {
            deviceDataMonitorDisposable[address]?.dispose()
            deviceDataMonitorDisposable.remove(address)
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
        fun getInstance(context: Context, features: Int): BDBleApiImpl {
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