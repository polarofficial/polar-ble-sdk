// Copyright © 2023 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl

import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.PftpResponseError
import com.polar.sdk.api.PolarOfflineExerciseV2Api
import com.polar.sdk.api.errors.PolarDeviceDisconnected
import com.polar.sdk.api.errors.PolarServiceNotAvailable
import com.polar.sdk.api.model.PolarExerciseData
import com.polar.sdk.api.model.PolarExerciseEntry
import com.polar.sdk.api.model.PolarExerciseSession
import com.polar.sdk.impl.utils.PolarFileUtils
import com.polar.sdk.impl.utils.PolarServiceClientUtils
import fi.polar.remote.representation.protobuf.Device
import fi.polar.remote.representation.protobuf.ExerciseSamples.PbExerciseSamples
import fi.polar.remote.representation.protobuf.Structures
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import protocol.PftpError.PbPFtpError
import protocol.PftpRequest
import protocol.PftpResponse
import java.time.LocalDateTime

/**
 * Implementation of [PolarOfflineExerciseV2Api].
 *
 * Manages offline exercise sessions on Polar devices using the Data Merge protocol.
 */
class PolarOfflineExerciseV2ApiImpl internal constructor(
    private val listener: BleDeviceListener,
    private val apiCallGuard: PolarApiCallGuard
) : PolarOfflineExerciseV2Api {

    companion object {
        private const val TAG = "PolarOfflineExerciseV2Api"
        private const val DEVICE_INFO_PATH = "/DEVICE.BPB"
        private const val DM_EXERCISE_CAPABILITY = "dm_exercise"
    }

    override suspend fun startOfflineExerciseV2(
        identifier: String,
        sportProfile: PolarExerciseSession.SportProfile
    ): PolarOfflineExerciseV2Api.OfflineExerciseStartResult =
        apiCallGuard.guardSuspend("startOfflineExerciseV2", identifier) {
            val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
            val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as? BlePsFtpClient
                ?: throw PolarServiceNotAvailable()

            val sportIdentifier = Structures.PbSportIdentifier.newBuilder()
                .setValue(sportProfile.id.toLong())
                .build()

            val params = PftpRequest.PbPFtpStartDmExerciseParams.newBuilder()
                .setSportIdentifier(sportIdentifier)
                .build()

            val outputStream = client.query(
                PftpRequest.PbPFtpQuery.START_DM_EXERCISE_VALUE,
                params.toByteArray()
            )

            val proto = PftpResponse.PbPftpStartDmExerciseResult.parseFrom(outputStream.toByteArray())

            val mappedResult = when (proto.result) {
                PftpResponse.PbPftpStartDmExerciseResult.PbStartDmExerciseResult.RESULT_SUCCESS ->
                    PolarOfflineExerciseV2Api.StartResult.SUCCESS
                PftpResponse.PbPftpStartDmExerciseResult.PbStartDmExerciseResult.RESULT_EXE_ONGOING ->
                    PolarOfflineExerciseV2Api.StartResult.EXERCISE_ONGOING
                PftpResponse.PbPftpStartDmExerciseResult.PbStartDmExerciseResult.RESULT_LOW_BATTERY ->
                    PolarOfflineExerciseV2Api.StartResult.LOW_BATTERY
                PftpResponse.PbPftpStartDmExerciseResult.PbStartDmExerciseResult.RESULT_SDK_MODE ->
                    PolarOfflineExerciseV2Api.StartResult.SDK_MODE
                PftpResponse.PbPftpStartDmExerciseResult.PbStartDmExerciseResult.RESULT_UNKNOWN_SPORT ->
                    PolarOfflineExerciseV2Api.StartResult.UNKNOWN_SPORT
                else -> PolarOfflineExerciseV2Api.StartResult.OTHER
            }

            val path = if (proto.hasDmDirectoryPath()) proto.dmDirectoryPath else "/"

            PolarOfflineExerciseV2Api.OfflineExerciseStartResult(
                result = mappedResult,
                directoryPath = path
            )
        }

    override suspend fun stopOfflineExerciseV2(identifier: String) {
        apiCallGuard.guardSuspend("stopOfflineExerciseV2", identifier) {
            val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
            val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as? BlePsFtpClient
                ?: throw PolarServiceNotAvailable()

            val params = PftpRequest.PbPFtpStopExerciseParams.newBuilder()
                .setSave(true)
                .build()

            client.query(
                PftpRequest.PbPFtpQuery.STOP_EXERCISE_VALUE,
                params.toByteArray()
            )
        }
    }

    override suspend fun getOfflineExerciseStatusV2(identifier: String): Boolean =
        apiCallGuard.guardSuspend("getOfflineExerciseStatusV2", identifier) {
            val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
            val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as? BlePsFtpClient
                ?: throw PolarServiceNotAvailable()

            isDmExerciseRunning(client)
        }

    override fun listOfflineExercisesV2(identifier: String, directoryPath: String): Flow<PolarExerciseEntry> =
        apiCallGuard.guardFlow("listOfflineExercisesV2", identifier) {
            val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
            val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as? BlePsFtpClient
                ?: throw PolarServiceNotAvailable()

            emitAll(
                PolarFileUtils.fetchRecursively(
                    client = client,
                    path = directoryPath,
                    condition = { entry ->
                        entry == PolarOfflineExerciseV2Api.EXERCISE_SAMPLES_FILE ||
                                entry.endsWith("/${PolarOfflineExerciseV2Api.EXERCISE_SAMPLES_FILE}") ||
                                entry.endsWith("/")
                    },
                    tag = TAG,
                    recurseDeep = true
                )
                    .filter { entry ->
                        !entry.first.endsWith("/") &&
                                entry.first.endsWith(PolarOfflineExerciseV2Api.EXERCISE_SAMPLES_FILE)
                    }
                    .map { entry ->
                        val components = entry.first.split("/").toTypedArray()
                        val fileName = components.lastOrNull() ?: entry.first
                        PolarExerciseEntry(
                            path = entry.first,
                            date = LocalDateTime.now(),
                            identifier = fileName
                        )
                    }
                    .catch { throwable ->
                        if (isSystemBusy(throwable)) {
                            BleLogger.e(TAG, "Device BUSY (202) - Stop exercise first")
                            throw Exception("Device is BUSY (202) while listing. This can happen while an exercise is running - if so, stop it and try again.")
                        } else {
                            throw handleError(throwable)
                        }
                    }
            )
        }

    override suspend fun fetchOfflineExerciseV2(
        identifier: String,
        entry: PolarExerciseEntry
    ): PolarExerciseData =
        apiCallGuard.guardSuspend("fetchOfflineExerciseV2", identifier) {
            val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
            val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as? BlePsFtpClient
                ?: throw PolarServiceNotAvailable()

            val builder = PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                .setPath(entry.path)

            try {
                val byteArrayOutputStream = client.request(builder.build().toByteArray())
                val samples = PbExerciseSamples.parseFrom(byteArrayOutputStream.toByteArray())
                if (samples.hasRrSamples()) {
                    PolarExerciseData(samples.recordingInterval.seconds, samples.rrSamples.rrIntervalsList)
                } else {
                    PolarExerciseData(samples.recordingInterval.seconds, samples.heartRateSamplesList)
                }
            } catch (throwable: Throwable) {
                if (isSystemBusy(throwable)) {
                    BleLogger.e(TAG, "Device BUSY (202) - Stop exercise first")
                    // SYSTEM_BUSY (202) is most commonly seen while a data merge exercise is
                    // running, but that is not confirmed to be the only possible cause, so the
                    // message does not state it as fact.
                    throw Exception("Device is BUSY (202) while fetching. This can happen while an exercise is running - if so, stop it and try again.")
                } else {
                    throw handleError(throwable)
                }
            }
        }

    override suspend fun removeOfflineExerciseV2(
        identifier: String,
        entry: PolarExerciseEntry
    ) {
        apiCallGuard.guardSuspend("removeOfflineExerciseV2", identifier) {
            val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
            val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as? BlePsFtpClient
                ?: throw PolarServiceNotAvailable()

            val builder = PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.REMOVE)
                .setPath(entry.path)

            try {
                client.request(builder.build().toByteArray())
            } catch (throwable: Throwable) {
                throw handleError(throwable)
            }
        }
    }

    override suspend fun isOfflineExerciseV2Supported(identifier: String): Boolean =
        apiCallGuard.guardSuspend("isOfflineExerciseV2Supported", identifier) {
            val session = try {
                PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
            } catch (error: Throwable) {
                BleLogger.e(TAG, "Failed to get session for device $identifier: ${error.message}")
                throw error
            }

            val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as? BlePsFtpClient
                ?: run {
                    BleLogger.e(TAG, "PFTP client not available for device $identifier")
                    throw PolarServiceNotAvailable()
                }

            checkDmExerciseSupport(client)
        }

    private fun handleError(throwable: Throwable): Exception {
        return when {
            throwable is BleDisconnected -> PolarDeviceDisconnected()
            throwable is PftpResponseError -> {
                val pftpError = PbPFtpError.forNumber(throwable.error)
                if (pftpError != null) Exception(pftpError.toString()) else Exception(throwable)
            }
            else -> Exception(throwable)
        }
    }

    /**
     * Check if device supports offline exercise (dm_exercise capability).
     * Reads DEVICE.BPB to check if "dm_exercise" is in the capabilities list.
     *
     * While a data merge exercise is running the device answers SYSTEM_BUSY to file reads, so
     * DEVICE.BPB cannot be read. In that case the exercise status query (which is still served
     * during an exercise) is used instead: a running data merge exercise implies support.
     */
    private suspend fun checkDmExerciseSupport(client: BlePsFtpClient): Boolean {
        val builder = PftpRequest.PbPFtpOperation.newBuilder()
            .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
            .setPath(DEVICE_INFO_PATH)

        return try {
            val byteArrayOutputStream = client.request(builder.build().toByteArray())
            val deviceInfo = Device.PbDeviceInfo.parseFrom(byteArrayOutputStream.toByteArray())
            deviceInfo.capabilitiesList.contains(DM_EXERCISE_CAPABILITY)
        } catch (error: Throwable) {
            if (isSystemBusy(error)) {
                BleLogger.d(TAG, "DEVICE.BPB read returned SYSTEM_BUSY, checking exercise status instead")
                return try {
                    isDmExerciseRunning(client)
                } catch (statusError: Throwable) {
                    if (isSystemBusy(statusError)) {
                        // The device is busy for the exercise-status query too, so dm_exercise
                        // support genuinely cannot be determined right now — it is NOT known to
                        // be unsupported. Propagate instead of returning false, which would claim
                        // the opposite of the truth.
                        BleLogger.e(TAG, "Exercise status query also returned SYSTEM_BUSY, dm_exercise support could not be determined")
                        throw handleError(statusError)
                    }
                    BleLogger.e(TAG, "Failed to query exercise status: ${statusError.message}")
                    false
                }
            }
            BleLogger.e(TAG, "Failed to check dm_exercise capability: ${error.message}")
            false
        }
    }

    private fun isSystemBusy(throwable: Throwable?): Boolean {
        var t = throwable
        while (t != null) {
            if (t is PftpResponseError && t.error == PbPFtpError.SYSTEM_BUSY.number) return true
            t = t.cause
        }
        return false
    }
    private suspend fun isDmExerciseRunning(client: BlePsFtpClient): Boolean {
        val bytes = client.query(
            PftpRequest.PbPFtpQuery.GET_EXERCISE_STATUS_VALUE,
            byteArrayOf()
        )
        val proto = PftpResponse.PbPftpGetExerciseStatusResult.parseFrom(bytes.toByteArray())
        return proto.exerciseType ==
                PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseType.EXERCISE_TYPE_DATA_MERGE &&
                proto.exerciseState ==
                PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState.EXERCISE_STATE_RUNNING
    }

}