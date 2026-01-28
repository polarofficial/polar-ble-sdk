package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.sleep.*
import com.polar.services.datamodels.protobuf.SleepSkinTemperatureResult
import fi.polar.remote.representation.protobuf.SleepanalysisResult
import io.reactivex.rxjava3.core.Single
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
    fun readSleepDataFromDayDirectory(client: BlePsFtpClient, date: LocalDate): Single<PolarSleepAnalysisResult> {
        return Single.create { emitter ->
            val disposable = readSleepData(client, date).subscribe() { response ->
               readSleepSkintemperatureResult(client, date, response).subscribe()
               { response ->
                   emitter.onSuccess(response)
               }
           }
            emitter.setDisposable(disposable)
        }
    }

    /**
     * Read sleep data.
     */
    fun readSleepData(client: BlePsFtpClient, date: LocalDate): Single<PolarSleepAnalysisResult> {
        BleLogger.d(TAG, "readSleepData: $date")
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
                            PolarTimeUtils.pbLocalDateTimeToZonedDateTime(proto.sleepStartTime),
                            PolarTimeUtils.pbLocalDateTimeToZonedDateTime(proto.sleepEndTime),
                            PolarTimeUtils.pbSystemDateTimeToZonedDateTime(proto.lastModified),
                            proto.sleepGoalMinutes,
                            fromPbSleepwakePhasesListProto(proto.sleepwakePhasesList),
                            convertSnoozeTimeListToZonedDateTimeList(proto.snoozeTimeList),
                            if (proto.hasAlarmTime()) { PolarTimeUtils.pbLocalDateTimeToZonedDateTime(proto.alarmTime) } else null,
                            proto.sleepStartOffsetSeconds ?: null,
                            proto.sleepEndOffsetSeconds ?: null,
                            if (proto.hasUserSleepRating()) { SleepRating.from(proto.userSleepRating.number) } else null,
                            proto.recordingDevice.deviceId ?: null,
                            proto.batteryRanOut ?: null,
                            fromPbSleepCyclesList(proto.sleepCyclesList),
                            PolarTimeUtils.pbDateToLocalDate(proto.sleepResultDate) ?: null,
                            if (proto.hasOriginalSleepRange()) {fromPbOriginalSleepRange(proto.originalSleepRange)} else null,
                            null
                        )
                    )
                },
                { error ->
                    emitter.onSuccess(PolarSleepAnalysisResult(
                        null, null, null, null,
                        null, null, null, null,
                        null, null, null, null,
                        null, null, null, null)
                    )
                }
            )
            emitter.setDisposable(disposable)
        }
    }

    /**
     * Read skintemperaturedata data.
     */
    fun readSleepSkintemperatureResult(client: BlePsFtpClient, date: LocalDate, sleepAnalysisResult: PolarSleepAnalysisResult): Single<PolarSleepAnalysisResult> {
        BleLogger.d(TAG, "readSleepSkintemperatureResult: $date")
        var result = sleepAnalysisResult
        return Single.create { emitter ->
            val sleepFilePath = "$ARABICA_USER_ROOT_FOLDER${date.format(dateFormatter)}/${NRST_DIRECTORY}${NRST_PROTO}"
            val disposable = client.request(PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                .setPath(sleepFilePath)
                .build()
                .toByteArray()
            ).subscribe(
                { response ->
                    val proto = SleepSkinTemperatureResult.PbSleepSkinTemperatureResult.parseFrom(response.toByteArray())
                    if (proto.hasSleepDate()) {
                        result.sleepSkinTemperatureResult = fromPbSleepSkinTemperatureResult(proto)
                    }
                    emitter.onSuccess(
                        result
                    )
                },
                { _ ->
                    emitter.onSuccess(result)
                }
            )
            emitter.setDisposable(disposable)
        }
    }
}