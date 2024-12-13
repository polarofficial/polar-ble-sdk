package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.sleep.PolarNightlyRechargeData
import fi.polar.remote.representation.protobuf.NightlyRecovery.PbNightlyRecoveryStatus
import io.reactivex.rxjava3.core.Maybe
import protocol.PftpRequest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val ARABICA_USER_ROOT_FOLDER = "/U/0/"
private const val NIGHTLY_RECOVERY_DIRECTORY = "NR/"
private const val NIGHTLY_RECOVERY_PROTO = "NR.BPB"
private val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.ENGLISH)
private const val TAG = "PolarNightlyRechargeUtils"

internal object PolarNightlyRechargeUtils {

    /**
     * Read nightly recharge data for given date range.
     */
    fun readNightlyRechargeData(client: BlePsFtpClient, date: Date): Maybe<PolarNightlyRechargeData> {
        BleLogger.d(TAG, "readNightlyRechargeData: $date")
        return Maybe.create { emitter ->
            val nightlyRecoveryFilePath = "$ARABICA_USER_ROOT_FOLDER${dateFormat.format(date)}/$NIGHTLY_RECOVERY_DIRECTORY$NIGHTLY_RECOVERY_PROTO"
            val disposable = client.request(PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(nightlyRecoveryFilePath)
                    .build()
                    .toByteArray()
            ).subscribe(
                    { response ->
                        val recoveryStatus = PbNightlyRecoveryStatus.parseFrom(response.toByteArray())
                        val recoveryDateProto = recoveryStatus.sleepResultDate
                        val recoveryDate = Calendar.getInstance().apply {
                            set(recoveryDateProto.year, recoveryDateProto.month - 1, recoveryDateProto.day, 0, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.time

                        val createdTimestamp = PolarTimeUtils.pbSystemDateTimeToLocalDateTime(recoveryStatus.createdTimestamp)
                        val modifiedTimestamp = if (recoveryStatus.hasModifiedTimestamp()) {
                            PolarTimeUtils.pbSystemDateTimeToLocalDateTime(recoveryStatus.modifiedTimestamp)
                        } else {
                            null
                        }

                        val nightlyRechargeData = PolarNightlyRechargeData(
                                createdTimestamp = createdTimestamp,
                                modifiedTimestamp = modifiedTimestamp,
                                ansStatus = recoveryStatus.ansStatus,
                                recoveryIndicator = recoveryStatus.recoveryIndicator,
                                recoveryIndicatorSubLevel = recoveryStatus.recoveryIndicatorSubLevel,
                                ansRate = recoveryStatus.ansRate,
                                scoreRateObsolete = recoveryStatus.scoreRateOBSOLETE,
                                meanNightlyRecoveryRRI = recoveryStatus.meanNightlyRecoveryRRI,
                                meanNightlyRecoveryRMSSD = recoveryStatus.meanNightlyRecoveryRMSSD,
                                meanNightlyRecoveryRespirationInterval = recoveryStatus.meanNightlyRecoveryRespirationInterval,
                                meanBaselineRRI = recoveryStatus.meanBaselineRRI,
                                sdBaselineRRI = recoveryStatus.sdBaselineRRI,
                                meanBaselineRMSSD = recoveryStatus.meanBaselineRMSSD,
                                sdBaselineRMSSD = recoveryStatus.sdBaselineRMSSD,
                                meanBaselineRespirationInterval = recoveryStatus.meanBaselineRespirationInterval,
                                sdBaselineRespirationInterval = recoveryStatus.sdBaselineRespirationInterval,
                                sleepTip = recoveryStatus.sleepTip,
                                vitalityTip = recoveryStatus.vitalityTip,
                                exerciseTip = recoveryStatus.exerciseTip,
                                sleepResultDate = recoveryDate
                        )
                        emitter.onSuccess(nightlyRechargeData)
                    },
                    { error ->
                        BleLogger.w(TAG, "Failed to fetch nightly recharge for date: $date, error: $error")
                        emitter.onComplete()
                    }
            )
            emitter.setDisposable(disposable)
        }
    }
}