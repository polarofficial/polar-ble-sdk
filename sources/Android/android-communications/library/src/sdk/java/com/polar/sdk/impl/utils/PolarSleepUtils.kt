package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.sleep.*
import fi.polar.remote.representation.protobuf.SleepanalysisResult
import io.reactivex.rxjava3.core.Single
import protocol.PftpRequest
import java.lang.Exception
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private const val ARABICA_USER_ROOT_FOLDER = "/U/0/"
private const val SLEEP_DIRECTORY = "SLEEP/"
private const val SLEEP_PROTO = "SLEEPRES.BPB"
private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ENGLISH)
private const val TAG = "PolarSleepUtils"

internal object PolarSleepUtils {

    /**
     * Read sleep data for a given date.
     */
    fun readSleepDataFromDayDirectory(client: BlePsFtpClient, date: LocalDate): Single<PolarSleepAnalysisResult> {
        BleLogger.d(TAG, "readSleepDataFromDayDirectory: $date")
        return Single.create { emitter ->
            val sleepFilePath = "$ARABICA_USER_ROOT_FOLDER${date.format(dateFormatter)}/${SLEEP_DIRECTORY}${SLEEP_PROTO}"
            val disposable = client.request(PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                .setPath(sleepFilePath)
                .build()
                .toByteArray()
            ).subscribe(
                { response ->
                    val proto = SleepanalysisResult.PbSleepAnalysisResult.parseFrom(response.toByteArray())
                    emitter.onSuccess(
                        PolarSleepAnalysisResult(
                            PolarTimeUtils.pbLocalDateTimeToLocalDateTime(proto.sleepStartTime),
                            PolarTimeUtils.pbLocalDateTimeToLocalDateTime(proto.sleepEndTime),
                            PolarTimeUtils.pbSystemDateTimeToLocalDateTime(proto.lastModified),
                            proto.sleepGoalMinutes,
                            fromPbSleepwakePhasesListProto(proto.sleepwakePhasesList),
                            convertSnoozeTimeListToLocalTime(proto.snoozeTimeList),
                            if (proto.hasAlarmTime()) { PolarTimeUtils.pbLocalDateTimeToLocalDateTime(proto.alarmTime) } else null,
                            proto.sleepStartOffsetSeconds ?: null,
                            proto.sleepEndOffsetSeconds ?: null,
                            if (proto.hasUserSleepRating()) { SleepRating.from(proto.userSleepRating.number) } else null,
                            proto.recordingDevice.deviceId ?: null,
                            proto.batteryRanOut ?: null,
                            fromPbSleepCyclesList(proto.sleepCyclesList),
                            PolarTimeUtils.pbDateToLocalDate(proto.sleepResultDate) ?: null,
                            if (proto.hasOriginalSleepRange()) {fromPbOriginalSleepRange(proto.originalSleepRange)} else null
                        )
                    )
                },
                { error ->
                    emitter.onSuccess(PolarSleepAnalysisResult(
                        null, null, null, null,
                        null, null, null, null,
                        null, null, null, null,
                        null, null, null)
                    )
                }
            )
            emitter.setDisposable(disposable)
        }
    }
}