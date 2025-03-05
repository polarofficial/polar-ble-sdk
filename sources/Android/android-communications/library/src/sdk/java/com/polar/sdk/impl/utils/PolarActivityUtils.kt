package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.activity.PolarActiveTime
import com.polar.sdk.api.model.activity.PolarActiveTimeData
import com.polar.sdk.impl.BDBleApiImpl.FetchRecursiveCondition
import fi.polar.remote.representation.protobuf.ActivitySamples
import fi.polar.remote.representation.protobuf.DailySummary
import fi.polar.remote.representation.protobuf.Types.PbDuration
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Function
import org.reactivestreams.Publisher
import protocol.PftpNotification
import protocol.PftpRequest
import protocol.PftpResponse.PbPFtpDirectory
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ARABICA_USER_ROOT_FOLDER = "/U/0/"
private const val ACTIVITY_DIRECTORY = "ACT/"
private const val DAILY_SUMMARY_DIRECTORY = "DSUM/"
private const val DAILY_SUMMARY_PROTO = "DSUM.BPB"
private val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.ENGLISH)
private const val TAG = "PolarActivityUtils"

enum class CaloriesType {
    ACTIVITY, TRAINING, BMR
}

internal object PolarActivityUtils {

    /**
     * Read step count for given [date].
     */
    fun readStepsFromDayDirectory(client: BlePsFtpClient, date: Date): Single<Int> {//: Disposable {
        BleLogger.d(TAG, "readStepsFromDayDirectory: $date")
        return Single.create { emitter ->
            val activityFileDir = "$ARABICA_USER_ROOT_FOLDER${dateFormat.format(date)}/${ACTIVITY_DIRECTORY}"
            var fileList = mutableListOf<String>()
            var stepCount = 0

            listFiles(client, activityFileDir,
                condition = { entry: String ->
                    entry.matches(Regex("^${activityFileDir}/")) ||
                            entry == "ASAMPL" ||
                            entry.contains(".BPB")})
                .map {
                    fileList.add(it)
                }.doFinally {
                    var index = 0
                    if (fileList.isNotEmpty()) {
                            for (file in fileList) {
                                client.request(
                                    PftpRequest.PbPFtpOperation.newBuilder()
                                        .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                                        .setPath(file)
                                        .build()
                                        .toByteArray()
                                ).subscribe(
                                    { response ->
                                        val proto =
                                            ActivitySamples.PbActivitySamples.parseFrom(response.toByteArray())
                                        stepCount += proto.stepsSamplesList.sum()
                                        if (++index == fileList.size) {
                                            emitter.onSuccess(stepCount)
                                        }
                                    },
                                    { error ->
                                        BleLogger.w(
                                            TAG,
                                            "readStepsFromDayDirectory() failed for file: $file, error: $error"
                                        )
                                        emitter.onSuccess(0)
                                    }
                                )
                            }
                        } else {
                            emitter.onSuccess(0)
                    }
                }.doOnError {
                    emitter.onSuccess(0)
                }.subscribe()
        }
    }

    fun readDistanceFromDayDirectory(client: BlePsFtpClient, date: Date): Single<Float> {
        BleLogger.d(TAG, "readDistanceFromDayDirectory: $date")
        return sendSyncStart(client)
            .andThen(Single.create { emitter ->
                val dailySummaryFilePath = "$ARABICA_USER_ROOT_FOLDER${dateFormat.format(date)}/${DAILY_SUMMARY_DIRECTORY}${DAILY_SUMMARY_PROTO}"
                val disposable = client.request(
                    PftpRequest.PbPFtpOperation.newBuilder()
                        .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                        .setPath(dailySummaryFilePath)
                        .build()
                        .toByteArray()
                )
                    .subscribe(
                        { response ->
                            val proto = DailySummary.PbDailySummary.parseFrom(response.toByteArray())
                            val distance = proto.activityDistance
                            emitter.onSuccess(distance)
                        },
                        { error ->
                            BleLogger.w(TAG, "readDistanceFromDayDirectory() failed for path: $dailySummaryFilePath, error: $error")
                            emitter.onSuccess(0F)
                        }
                    )
                emitter.setDisposable(disposable)
            })
    }

    fun readActiveTimeFromDayDirectory(client: BlePsFtpClient, date: Date): Single<PolarActiveTimeData> {
        BleLogger.d(TAG, "readActiveTimeFromDayDirectory: $date")
        return sendSyncStart(client)
            .andThen(Single.create { emitter ->
                val dailySummaryFilePath = "$ARABICA_USER_ROOT_FOLDER${dateFormat.format(date)}/${DAILY_SUMMARY_DIRECTORY}${DAILY_SUMMARY_PROTO}"
                val disposable = client.request(
                    PftpRequest.PbPFtpOperation.newBuilder()
                        .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                        .setPath(dailySummaryFilePath)
                        .build()
                        .toByteArray()
                )
                    .subscribe(
                        { response ->
                            val proto = DailySummary.PbDailySummary.parseFrom(response.toByteArray())
                            val polarActiveTimeData = PolarActiveTimeData(
                                date = date,
                                timeNonWear = polarActiveTimeFromProto(proto.activityClassTimes.timeNonWear),
                                timeSleep = polarActiveTimeFromProto(proto.activityClassTimes.timeSleep),
                                timeSedentary = polarActiveTimeFromProto(proto.activityClassTimes.timeSedentary),
                                timeLightActivity = polarActiveTimeFromProto(proto.activityClassTimes.timeLightActivity),
                                timeContinuousModerateActivity = polarActiveTimeFromProto(proto.activityClassTimes.timeContinuousModerate),
                                timeIntermittentModerateActivity = polarActiveTimeFromProto(proto.activityClassTimes.timeIntermittentModerate),
                                timeContinuousVigorousActivity = polarActiveTimeFromProto(proto.activityClassTimes.timeContinuousVigorous),
                                timeIntermittentVigorousActivity = polarActiveTimeFromProto(proto.activityClassTimes.timeIntermittentVigorous)
                            )
                            emitter.onSuccess(polarActiveTimeData)

                        },
                        { error ->
                            BleLogger.w(TAG, "readActiveTimeFromDayDirectory() failed for path: $dailySummaryFilePath, error: $error")
                            emitter.onSuccess(PolarActiveTimeData(date, PolarActiveTime()))
                        }
                    )
                emitter.setDisposable(disposable)
            })
    }

    fun readSpecificCaloriesFromDayDirectory(client: BlePsFtpClient, date: Date, caloriesType: CaloriesType): Single<Int> {
        BleLogger.d(TAG, "readSpecificCaloriesFromDayDirectory: $date, type: $caloriesType")
        return sendSyncStart(client)
            .andThen(Single.create { emitter ->
                val dailySummaryFilePath = "$ARABICA_USER_ROOT_FOLDER${dateFormat.format(date)}/${DAILY_SUMMARY_DIRECTORY}${DAILY_SUMMARY_PROTO}"
                val disposable = client.request(
                    PftpRequest.PbPFtpOperation.newBuilder()
                        .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                        .setPath(dailySummaryFilePath)
                        .build()
                        .toByteArray()
                )
                    .subscribe(
                        { response ->
                            val proto = DailySummary.PbDailySummary.parseFrom(response.toByteArray())
                            val caloriesValue = when (caloriesType) {
                                CaloriesType.ACTIVITY -> proto.activityCalories
                                CaloriesType.TRAINING -> proto.trainingCalories
                                CaloriesType.BMR -> proto.bmrCalories
                            }
                            emitter.onSuccess(caloriesValue)
                        },
                        { error ->
                            BleLogger.w(TAG, "readSpecificCaloriesFromDayDirectory() failed for path: $dailySummaryFilePath, error: $error")
                            emitter.onSuccess(0)
                        }
                    )
                emitter.setDisposable(disposable)
            })
    }

    // Send sync start to generate daily summary for the current date
    private fun sendSyncStart(client: BlePsFtpClient): Completable {
        return client.sendNotification(
            PftpNotification.PbPFtpHostToDevNotification.START_SYNC.number,
            null
        )
    }

    private fun polarActiveTimeFromProto(proto: PbDuration): PolarActiveTime {
        return PolarActiveTime(
            hours = proto.hours,
            minutes = proto.minutes,
            seconds = proto.seconds,
            millis = proto.millis
        )
    }

    private fun listFiles(client: BlePsFtpClient, folderPath: String = "/", condition: FetchRecursiveCondition): Flowable<String> {

        var path = folderPath
        if (path.first() != '/') {
            path = '/' + path
        }
        if (path.last() != '/') {
            path = path + '/'
        }

        return fetchRecursively(
            client = client,
            path = path,
            condition = condition)
            .map {
                it.first
            }.onErrorResumeNext { throwable: Throwable ->
                Flowable.error(Exception(throwable))
            }
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
}