package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.activity.PolarActiveTime
import com.polar.sdk.api.model.activity.PolarActiveTimeData
import com.polar.sdk.api.model.activity.PolarActivitySamplesData
import com.polar.sdk.api.model.activity.PolarActivitySamplesDayData
import com.polar.sdk.api.model.activity.parsePbActivityInfo
import com.polar.sdk.impl.BDBleApiImpl.FetchRecursiveCondition
import fi.polar.remote.representation.protobuf.ActivitySamples
import fi.polar.remote.representation.protobuf.DailySummary
import fi.polar.remote.representation.protobuf.Types.PbDuration
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Function
import org.reactivestreams.Publisher
import protocol.PftpRequest
import protocol.PftpResponse.PbPFtpDirectory
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import java.util.Date
import java.util.Locale

private const val ARABICA_USER_ROOT_FOLDER = "/U/0/"
private const val ACTIVITY_DIRECTORY = "ACT/"
private const val DAILY_SUMMARY_DIRECTORY = "DSUM/"
private const val DAILY_SUMMARY_PROTO = "DSUM.BPB"
private val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.ENGLISH)
private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
private const val TAG = "PolarActivityUtils"

enum class CaloriesType {
    ACTIVITY, TRAINING, BMR
}

internal object PolarActivityUtils {

    /**
     * Read step count for given [date].
     */
    fun readStepsFromDayDirectory(client: BlePsFtpClient, date: LocalDate): Single<Int> {
        BleLogger.d(TAG, "readStepsFromDayDirectory: $date")
        return Single.create { emitter ->
            val activityFileDir = "$ARABICA_USER_ROOT_FOLDER${date.format(dateFormatter)}/${ACTIVITY_DIRECTORY}"
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
                        BleLogger.w(TAG, "readActivitySamplesDataFromDayDirectory() could not find files to read for date $date.")
                        emitter.onSuccess(0)
                    }
                }.doOnError { error ->
                    BleLogger.w(TAG, "readStepsFromDayDirectory() failed while listing files, error occurred $error.")
                    emitter.onSuccess(0)
                }.subscribe()
        }
    }

    fun readDistanceFromDayDirectory(client: BlePsFtpClient, date: LocalDate): Single<Float> {
        BleLogger.d(TAG, "readDistanceFromDayDirectory: $date")
        return Single.create { emitter ->
                val dailySummaryFilePath = "$ARABICA_USER_ROOT_FOLDER${date.format(dateFormatter)}/${DAILY_SUMMARY_DIRECTORY}${DAILY_SUMMARY_PROTO}"
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
            }
    }

    fun readActiveTimeFromDayDirectory(client: BlePsFtpClient, date: LocalDate): Single<PolarActiveTimeData> {
        BleLogger.d(TAG, "readActiveTimeFromDayDirectory: $date")
        return Single.create { emitter ->
                val dailySummaryFilePath = "$ARABICA_USER_ROOT_FOLDER${date.format(dateFormatter)}/${DAILY_SUMMARY_DIRECTORY}${DAILY_SUMMARY_PROTO}"
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
            }
    }

    fun readSpecificCaloriesFromDayDirectory(client: BlePsFtpClient, date: LocalDate, caloriesType: CaloriesType): Single<Int> {
        BleLogger.d(TAG, "readSpecificCaloriesFromDayDirectory: $date, type: $caloriesType")
        return Single.create { emitter ->
                val dailySummaryFilePath = "$ARABICA_USER_ROOT_FOLDER${date.format(dateFormatter)}/${DAILY_SUMMARY_DIRECTORY}${DAILY_SUMMARY_PROTO}"
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
            }
    }

    /**
     * Read and return activity samples data for a given date.
     */
    fun readActivitySamplesDataFromDayDirectory(client: BlePsFtpClient, date: Date): Single<PolarActivitySamplesDayData> {
        BleLogger.d(TAG, "readActivitySamplesDataFromDayDirectory: $date")
        return Single.create { emitter ->
            val activityFileDir = "$ARABICA_USER_ROOT_FOLDER${dateFormat.format(date)}/${ACTIVITY_DIRECTORY}"
            var fileList = mutableListOf<String>()
            var activitySamplesDataList: MutableList<PolarActivitySamplesData> = mutableListOf()
            var activitySamplesDayData = PolarActivitySamplesDayData()
            var activitySamplesData = PolarActivitySamplesData()
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

                                    activitySamplesData.startTime = PolarTimeUtils.pbLocalDateTimeToLocalDateTime(proto.startTime)
                                    activitySamplesData.stepSamples = proto.stepsSamplesList
                                    activitySamplesData.stepRecordingInterval = PolarTimeUtils.pbDurationToInt(proto.stepsRecordingInterval)/1E3.toInt()
                                    activitySamplesData.metSamples = proto.metSamplesList
                                    activitySamplesData.metRecordingInterval = PolarTimeUtils.pbDurationToInt(proto.metRecordingInterval)/1E3.toInt()
                                    activitySamplesData.activityInfoList = parsePbActivityInfo(proto.activityInfoList)
                                    activitySamplesDataList.add(activitySamplesData)
                                    activitySamplesDayData.polarActivitySamplesDataList = activitySamplesDataList
                                    if (++index == fileList.size) {
                                        emitter.onSuccess(activitySamplesDayData)
                                    }
                                },
                                { error ->
                                    BleLogger.w(
                                        TAG,
                                        "readActivitySamplesDataFromDayDirectory() failed for file: $file, error: $error"
                                    )
                                    emitter.onSuccess(activitySamplesDayData)
                                }
                            )
                        }
                    } else {
                        BleLogger.w(TAG, "readActivitySamplesDataFromDayDirectory() could not find files to read for date $date.")
                        emitter.onSuccess(activitySamplesDayData)
                    }
                }.doOnError { error ->
                    BleLogger.w(TAG, "readActivitySamplesDataFromDayDirectory() failed while listing files, error occurred $error.")
                    emitter.onSuccess(activitySamplesDayData)
                }.subscribe()
        }
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
            path = "/$path"
        }
        if (path.last() != '/') {
            path = "$path/"
        }

        return fetchRecursively(
            client = client,
            path = path,
            condition = condition
        )
            .map { it.first }
            .onErrorResumeNext { throwable: Throwable ->
                BleLogger.w(TAG, "listFiles failed for $path: $throwable")
                Flowable.empty()
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