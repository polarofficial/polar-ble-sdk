package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.sleep.PolarNightlyRechargeData
import fi.polar.remote.representation.protobuf.NightlyRecovery.PbNightlyRecoveryStatus
import protocol.PftpRequest
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val ARABICA_USER_ROOT_FOLDER = "/U/0/"
private const val NIGHTLY_RECOVERY_DIRECTORY = "NR/"
private const val NIGHTLY_RECOVERY_PROTO = "NR.BPB"
private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
private const val TAG = "PolarNightlyRechargeUtils"

internal object PolarNightlyRechargeUtils {

    /**
     * Read nightly recharge data for given date range.
     */
    suspend fun readNightlyRechargeData(client: BlePsFtpClient, date: LocalDate): PolarNightlyRechargeData? {
        BleLogger.d(TAG, "readNightlyRechargeData: $date")
        val nightlyRecoveryFilePath = "$ARABICA_USER_ROOT_FOLDER${date.format(dateFormatter)}/$NIGHTLY_RECOVERY_DIRECTORY$NIGHTLY_RECOVERY_PROTO"
        return try {
            val response = client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(nightlyRecoveryFilePath)
                    .build()
                    .toByteArray()
            )
            val recoveryStatus = PbNightlyRecoveryStatus.parseFrom(response.toByteArray())
            val recoveryDate = LocalDate.of(
                recoveryStatus.sleepResultDate.year,
                recoveryStatus.sleepResultDate.month,
                recoveryStatus.sleepResultDate.day
            )
            val createdTimestamp = PolarTimeUtils.pbSystemDateTimeToLocalDateTime(recoveryStatus.createdTimestamp)
            val modifiedTimestamp = if (recoveryStatus.hasModifiedTimestamp()) {
                PolarTimeUtils.pbSystemDateTimeToLocalDateTime(recoveryStatus.modifiedTimestamp)
            } else {
                null
            }
            PolarNightlyRechargeData(
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
        } catch (error: Throwable) {
            BleLogger.w(TAG, "Failed to fetch nightly recharge for date: $date, error: $error")
            null
        }
    }
}