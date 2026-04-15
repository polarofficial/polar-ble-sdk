package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.sleep.*
import com.polar.services.datamodels.protobuf.SleepSkinTemperatureResult
import fi.polar.remote.representation.protobuf.SleepanalysisResult
import protocol.PftpRequest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val ARABICA_USER_ROOT_FOLDER = "/U/0/"
private const val SLEEP_DIRECTORY = "SLEEP/"
private const val SLEEP_PROTO = "SLEEPRES.BPB"
private const val NRST_DIRECTORY = "NSTRESUL/"
private const val NRST_PROTO = "NSTRCONT.BPB"
private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ENGLISH)
private const val TAG = "PolarSleepUtils"

internal object PolarSleepUtils {

    /**
     * Read sleep data for a given date.
     */
    suspend fun readSleepDataFromDayDirectory(
        client: BlePsFtpClient,
        date: LocalDate
    ): PolarSleepAnalysisResult {
        val response = readSleepData(client, date)
        return readSleepSkinTemperatureResult(client, date, response)
    }

    /**
     * Read sleep data.
     */
    private suspend fun readSleepData(client: BlePsFtpClient, date: LocalDate): PolarSleepAnalysisResult {
        BleLogger.d(TAG, "readSleepData: $date")
        return try {
            val sleepFilePath = "$ARABICA_USER_ROOT_FOLDER${date.format(dateFormatter)}/${SLEEP_DIRECTORY}${SLEEP_PROTO}"
            val response = client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(sleepFilePath)
                    .build()
                    .toByteArray()
            )
            val proto = SleepanalysisResult.PbSleepAnalysisResult.parseFrom(response.toByteArray())
            PolarSleepAnalysisResult(
                PolarTimeUtils.pbLocalDateTimeToZonedDateTime(proto.sleepStartTime),
                PolarTimeUtils.pbLocalDateTimeToZonedDateTime(proto.sleepEndTime),
                PolarTimeUtils.pbSystemDateTimeToZonedDateTime(proto.lastModified),
                proto.sleepGoalMinutes,
                fromPbSleepwakePhasesListProto(proto.sleepwakePhasesList),
                convertSnoozeTimeListToZonedDateTimeList(proto.snoozeTimeList),
                if (proto.hasAlarmTime()) {
                    PolarTimeUtils.pbLocalDateTimeToZonedDateTime(proto.alarmTime)
                } else null,
                proto.sleepStartOffsetSeconds ?: null,
                proto.sleepEndOffsetSeconds ?: null,
                if (proto.hasUserSleepRating()) {
                    SleepRating.from(proto.userSleepRating.number)
                } else null,
                proto.recordingDevice.deviceId ?: null,
                proto.batteryRanOut ?: null,
                fromPbSleepCyclesList(proto.sleepCyclesList),
                PolarTimeUtils.pbDateToLocalDate(proto.sleepResultDate) ?: null,
                if (proto.hasOriginalSleepRange()) {
                    fromPbOriginalSleepRange(proto.originalSleepRange)
                } else null,
                null
            )
        } catch (_: Throwable) {
            PolarSleepAnalysisResult(
                null, null, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null
            )
        }
    }

    /**
     * Read skin temperature data.
     */
    private suspend fun readSleepSkinTemperatureResult(
        client: BlePsFtpClient,
        date: LocalDate,
        sleepAnalysisResult: PolarSleepAnalysisResult
    ): PolarSleepAnalysisResult {
        BleLogger.d(TAG, "readSleepSkinTemperatureResult: $date")
        return try {
            val result = sleepAnalysisResult
            val sleepFilePath = "$ARABICA_USER_ROOT_FOLDER${date.format(dateFormatter)}/${NRST_DIRECTORY}${NRST_PROTO}"
            val response = client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(sleepFilePath)
                    .build()
                    .toByteArray()
            )
            val proto = SleepSkinTemperatureResult.PbSleepSkinTemperatureResult.parseFrom(response.toByteArray())
            if (proto.hasSleepDate()) {
                result.sleepSkinTemperatureResult = fromPbSleepSkinTemperatureResult(proto)
            }
            result
        } catch (_: Throwable) {
            sleepAnalysisResult
        }
    }
}