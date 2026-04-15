package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.activity.PolarActiveTime
import com.polar.sdk.api.model.activity.PolarActiveTimeData
import com.polar.sdk.api.model.activity.PolarActivitySamplesData
import com.polar.sdk.api.model.activity.PolarActivitySamplesDayData
import com.polar.sdk.api.model.activity.PolarDailySummaryData
import com.polar.sdk.api.model.activity.parsePbActivityInfo
import com.polar.sdk.api.model.activity.parsePbDailySummary
import com.polar.sdk.api.model.activity.polarActiveTimeFromProto
import fi.polar.remote.representation.protobuf.ActivitySamples
import fi.polar.remote.representation.protobuf.DailySummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import protocol.PftpRequest
import protocol.PftpResponse.PbPFtpDirectory
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val ARABICA_USER_ROOT_FOLDER = "/U/0/"
private const val ACTIVITY_DIRECTORY = "ACT/"
private const val DAILY_SUMMARY_DIRECTORY = "DSUM/"
private const val DAILY_SUMMARY_PROTO = "DSUM.BPB"
private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
private const val TAG = "PolarActivityUtils"

enum class CaloriesType {
    ACTIVITY, TRAINING, BMR
}

internal object PolarActivityUtils {

    /**
     * Read step count for given [date].
     */
    suspend fun readStepsFromDayDirectory(client: BlePsFtpClient, date: LocalDate): Int {
        BleLogger.d(TAG, "readStepsFromDayDirectory: $date")
        val activityFileDir = "$ARABICA_USER_ROOT_FOLDER${date.format(dateFormatter)}/$ACTIVITY_DIRECTORY"

        return try {
            val files = listFiles(client, activityFileDir) { entry ->
                entry.matches(Regex("^$activityFileDir/")) ||
                        entry == "ASAMPL" ||
                        entry.contains(".BPB")
            }.toList()

            if (files.isEmpty()) {
                BleLogger.w(TAG, "readStepsFromDayDirectory() could not find files to read for date $date.")
                return 0
            }

            var stepCount = 0
            for (file in files) {
                try {
                    val response = client.request(
                        PftpRequest.PbPFtpOperation.newBuilder()
                            .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                            .setPath(file)
                            .build()
                            .toByteArray()
                    )
                    val proto = ActivitySamples.PbActivitySamples.parseFrom(response.toByteArray())
                    stepCount += proto.stepsSamplesList.sum()
                } catch (error: Throwable) {
                    BleLogger.w(TAG, "readStepsFromDayDirectory() failed for file: $file, error: $error")
                    return 0
                }
            }
            stepCount
        } catch (error: Throwable) {
            BleLogger.w(TAG, "readStepsFromDayDirectory() failed while listing files, error occurred $error.")
            0
        }
    }

    suspend fun readDistanceFromDayDirectory(client: BlePsFtpClient, date: LocalDate): Float {
        BleLogger.d(TAG, "readDistanceFromDayDirectory: $date")
        val dailySummaryFilePath =
            "$ARABICA_USER_ROOT_FOLDER${date.format(dateFormatter)}/$DAILY_SUMMARY_DIRECTORY$DAILY_SUMMARY_PROTO"
        var distance = 0F
        try {
            val response = client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(dailySummaryFilePath)
                    .build()
                    .toByteArray())
            distance = DailySummary.PbDailySummary.parseFrom(response.toByteArray()).activityDistance
        } catch (error: Throwable) {
            BleLogger.w(TAG, "readDistanceFromDayDirectory() failed for path: $dailySummaryFilePath, error: $error")
        }
        return distance
    }

    suspend fun readActiveTimeFromDayDirectory(client: BlePsFtpClient, date: LocalDate): PolarActiveTimeData {
        BleLogger.d(TAG, "readActiveTimeFromDayDirectory: $date")
        val dailySummaryFilePath =
            "$ARABICA_USER_ROOT_FOLDER${date.format(dateFormatter)}/$DAILY_SUMMARY_DIRECTORY$DAILY_SUMMARY_PROTO"
        return try {
            val response = client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(dailySummaryFilePath)
                    .build()
                    .toByteArray()
            )
            val proto = DailySummary.PbDailySummary.parseFrom(response.toByteArray())
            PolarActiveTimeData(
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
        } catch (error: Throwable) {
            BleLogger.w(TAG, "readActiveTimeFromDayDirectory() failed for path: $dailySummaryFilePath, error: $error")
            PolarActiveTimeData(date, PolarActiveTime())
        }
    }

    suspend fun readSpecificCaloriesFromDayDirectory(
        client: BlePsFtpClient,
        date: LocalDate,
        caloriesType: CaloriesType
    ): Int {
        BleLogger.d(TAG, "readSpecificCaloriesFromDayDirectory: $date, type: $caloriesType")
        val dailySummaryFilePath =
            "$ARABICA_USER_ROOT_FOLDER${date.format(dateFormatter)}/$DAILY_SUMMARY_DIRECTORY$DAILY_SUMMARY_PROTO"
        return try {
            val response = client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(dailySummaryFilePath)
                    .build()
                    .toByteArray()
            )
            val proto = DailySummary.PbDailySummary.parseFrom(response.toByteArray())
            when (caloriesType) {
                CaloriesType.ACTIVITY -> proto.activityCalories
                CaloriesType.TRAINING -> proto.trainingCalories
                CaloriesType.BMR -> proto.bmrCalories
            }
        } catch (error: Throwable) {
            BleLogger.w(TAG, "readSpecificCaloriesFromDayDirectory() failed for path: $dailySummaryFilePath, error: $error")
            0
        }
    }

    /**
     * Read and return activity samples data for a given date.
     */
    suspend fun readActivitySamplesDataFromDayDirectory(
        client: BlePsFtpClient,
        date: LocalDate
    ): PolarActivitySamplesDayData {
        BleLogger.d(TAG, "readActivitySamplesDataFromDayDirectory: $date")
        val activityFileDir = "$ARABICA_USER_ROOT_FOLDER${date.format(dateFormatter)}/$ACTIVITY_DIRECTORY"
        val dayData = PolarActivitySamplesDayData()
        val sampleList = mutableListOf<PolarActivitySamplesData>()

        return try {
            val files = listFiles(client, activityFileDir) { entry ->
                entry.matches(Regex("^$activityFileDir/")) ||
                        entry == "ASAMPL" ||
                        entry.contains(".BPB")
            }.toList()

            if (files.isEmpty()) {
                BleLogger.w(TAG, "readActivitySamplesDataFromDayDirectory() could not find files to read for date $date.")
                return dayData
            }

            for (file in files) {
                try {
                    val response = client.request(
                        PftpRequest.PbPFtpOperation.newBuilder()
                            .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                            .setPath(file)
                            .build()
                            .toByteArray()
                    )
                    val proto = ActivitySamples.PbActivitySamples.parseFrom(response.toByteArray())
                    val activitySamplesData = PolarActivitySamplesData().apply {
                        startTime = PolarTimeUtils.pbLocalDateTimeToLocalDateTime(proto.startTime)
                        stepSamples = proto.stepsSamplesList
                        stepRecordingInterval = PolarTimeUtils.pbDurationToInt(proto.stepsRecordingInterval) / 1000
                        metSamples = proto.metSamplesList
                        metRecordingInterval = PolarTimeUtils.pbDurationToInt(proto.metRecordingInterval) / 1000
                        activityInfoList = parsePbActivityInfo(proto.activityInfoList)
                    }
                    sampleList.add(activitySamplesData)
                } catch (error: Throwable) {
                    BleLogger.w(TAG, "readActivitySamplesDataFromDayDirectory() failed for file: $file, error: $error")
                    return dayData
                }
            }

            dayData.polarActivitySamplesDataList = sampleList
            dayData
        } catch (error: Throwable) {
            BleLogger.w(TAG, "readActivitySamplesDataFromDayDirectory() failed while listing files, error occurred $error.")
            dayData
        }
    }

    /**
     * Read and return daily summary data for a given date.
     */
    suspend fun readDailySummaryDataFromDayDirectory(
        client: BlePsFtpClient,
        date: LocalDate
    ): PolarDailySummaryData {
        BleLogger.d(TAG, "readDailySummaryDataFromDayDirectory: $date")
        val dailySummaryFilePath =
            "$ARABICA_USER_ROOT_FOLDER${date.format(dateFormatter)}/$DAILY_SUMMARY_DIRECTORY$DAILY_SUMMARY_PROTO"
        return try {
            val response = client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(dailySummaryFilePath)
                    .build()
                    .toByteArray()
            )
            val proto = DailySummary.PbDailySummary.parseFrom(response.toByteArray())
            parsePbDailySummary(proto)
        } catch (error: Throwable) {
            BleLogger.w(TAG, "readDailySummaryDataFromDayDirectory() failed for file: $dailySummaryFilePath, error: $error")
            PolarDailySummaryData()
        }
    }

    private fun listFiles(
        client: BlePsFtpClient,
        folderPath: String = "/",
        condition: PolarFileUtils.FetchRecursiveCondition
    ): Flow<String> {
        var path = folderPath
        if (path.firstOrNull() != '/') path = "/$path"
        if (path.lastOrNull() != '/') path = "$path/"

        return fetchRecursively(client, path, condition)
            .map { it.first }
            .catch { throwable ->
                BleLogger.w(TAG, "listFiles failed for $path: $throwable")
                emitAll(emptyFlow())
            }
    }

    private fun fetchRecursively(
        client: BlePsFtpClient,
        path: String,
        condition: PolarFileUtils.FetchRecursiveCondition
    ): Flow<Pair<String, Long>> = flow {
        val byteArrayOutputStream: ByteArrayOutputStream = client.request(
            PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                .setPath(path)
                .build()
                .toByteArray()
        )
        val dir = PbPFtpDirectory.parseFrom(byteArrayOutputStream.toByteArray())

        for (entry in dir.entriesList) {
            if (condition.include(entry.name)) {
                val entryPath = path + entry.name
                if (entryPath.endsWith("/")) {
                    emitAll(fetchRecursively(client, entryPath, condition))
                } else {
                    emit(entryPath to entry.size)
                }
            }
        }
    }
}