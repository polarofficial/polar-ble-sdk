// Copyright © 2023 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl

import androidx.core.util.Pair
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
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import protocol.PftpError.PbPFtpError
import protocol.PftpRequest
import protocol.PftpResponse
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime

/**
 * Implementation of [PolarOfflineExerciseV2Api].
 *
 * Manages offline exercise sessions on Polar devices using the Data Merge protocol.
 */
class PolarOfflineExerciseV2ApiImpl(
    private val listener: BleDeviceListener
) : PolarOfflineExerciseV2Api {

    companion object {
        private const val TAG = "PolarOfflineExerciseV2Api"
        private const val DEVICE_INFO_PATH = "/DEVICE.BPB"
        private const val DM_EXERCISE_CAPABILITY = "dm_exercise"
    }

    override fun startOfflineExerciseV2(
        identifier: String,
        sportProfile: PolarExerciseSession.SportProfile
    ): Single<PolarOfflineExerciseV2Api.OfflineExerciseStartResult> {

        val session = try {
            PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        } catch (error: Throwable) {
            return Single.error(error)
        }

        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as? BlePsFtpClient
            ?: return Single.error(PolarServiceNotAvailable())

        val sportIdentifier = Structures.PbSportIdentifier.newBuilder()
            .setValue(sportProfile.id.toLong())
            .build()

        val params = PftpRequest.PbPFtpStartDmExerciseParams.newBuilder()
            .setSportIdentifier(sportIdentifier)
            .build()

        return client.query(
            PftpRequest.PbPFtpQuery.START_DM_EXERCISE_VALUE,
            params.toByteArray()
        ).map { outputStream: ByteArrayOutputStream ->
            val bytes = outputStream.toByteArray()
            val proto = PftpResponse.PbPftpStartDmExerciseResult.parseFrom(bytes)

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
    }

    override fun stopOfflineExerciseV2(identifier: String): Completable {
        val session = try {
            PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        } catch (error: Throwable) {
            return Completable.error(error)
        }

        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as? BlePsFtpClient
            ?: return Completable.error(PolarServiceNotAvailable())

        val params = PftpRequest.PbPFtpStopExerciseParams.newBuilder()
            .setSave(true)
            .build()

        return client.query(
            PftpRequest.PbPFtpQuery.STOP_EXERCISE_VALUE,
            params.toByteArray()
        ).ignoreElement()
    }

    override fun getOfflineExerciseStatusV2(identifier: String): Single<Boolean> {
        val session = try {
            PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        } catch (error: Throwable) {
            return Single.error(error)
        }

        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as? BlePsFtpClient
            ?: return Single.error(PolarServiceNotAvailable())

        return client.query(
            PftpRequest.PbPFtpQuery.GET_EXERCISE_STATUS_VALUE,
            byteArrayOf()
        ).map { bytes ->
            val proto = PftpResponse.PbPftpGetExerciseStatusResult.parseFrom(bytes.toByteArray())
            proto.exerciseType ==
                    PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseType.EXERCISE_TYPE_DATA_MERGE &&
                    proto.exerciseState ==
                    PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState.EXERCISE_STATE_RUNNING
        }
    }

    override fun listOfflineExercisesV2(identifier: String, directoryPath: String): Flowable<PolarExerciseEntry> {
        val session = try {
            PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        } catch (error: Throwable) {
            return Flowable.error(error)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: return Flowable.error(PolarServiceNotAvailable())

        return PolarFileUtils.fetchRecursively(
            client = client,
            path = directoryPath,
            condition = { entry -> entry == PolarOfflineExerciseV2Api.EXERCISE_SAMPLES_FILE || entry.endsWith("/${PolarOfflineExerciseV2Api.EXERCISE_SAMPLES_FILE}") || entry.endsWith("/") },
            tag = TAG,
            recurseDeep = true
        )
            .filter { entry -> !entry.first.endsWith("/") && entry.first.endsWith(PolarOfflineExerciseV2Api.EXERCISE_SAMPLES_FILE) }
            .map { entry ->
                val components = entry.first.split("/").toTypedArray()
                val fileName = components.lastOrNull() ?: entry.first
                PolarExerciseEntry(
                    path = entry.first,
                    date = LocalDateTime.now(),
                    identifier = fileName
                )
            }
            .doOnError { throwable ->
                val isBusy = throwable is PftpResponseError && throwable.error == PbPFtpError.SYSTEM_BUSY.number
                if (isBusy) {
                    BleLogger.e(TAG, "Device BUSY (202) - Stop exercise first")
                }
            }
            .onErrorResumeNext { throwable: Throwable ->
                if (throwable is PftpResponseError && throwable.error == PbPFtpError.SYSTEM_BUSY.number) {
                    Flowable.error(Exception("Device is BUSY (202) - Exercise running. Stop exercise first before listing."))
                } else {
                    Flowable.error(handleError(throwable))
                }
            }
    }

    override fun fetchOfflineExerciseV2(
        identifier: String,
        entry: PolarExerciseEntry
    ): Single<PolarExerciseData> {
        val session = try {
            PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        } catch (error: Throwable) {
            return Single.error(error)
        }

        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: return Single.error(PolarServiceNotAvailable())

        val builder = PftpRequest.PbPFtpOperation.newBuilder()
            .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
            .setPath(entry.path)

        return client.request(builder.build().toByteArray())
            .map { byteArrayOutputStream: ByteArrayOutputStream ->
                val samples = PbExerciseSamples.parseFrom(byteArrayOutputStream.toByteArray())
                if (samples.hasRrSamples()) {
                    PolarExerciseData(
                        samples.recordingInterval.seconds,
                        samples.rrSamples.rrIntervalsList
                    )
                } else {
                    PolarExerciseData(
                        samples.recordingInterval.seconds,
                        samples.heartRateSamplesList
                    )
                }
            }
            .onErrorResumeNext { throwable: Throwable ->
                if (throwable is PftpResponseError && throwable.error == PbPFtpError.SYSTEM_BUSY.number) {
                    BleLogger.e(TAG, "Device BUSY (202) - Stop exercise first")
                    Single.error(Exception("Device is BUSY (202) - Exercise running. Stop exercise first before fetching."))
                } else {
                    Single.error(handleError(throwable))
                }
            }
    }

    override fun removeOfflineExerciseV2(
        identifier: String,
        entry: PolarExerciseEntry
    ): Completable {
        val session = try {
            PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        } catch (error: Throwable) {
            return Completable.error(error)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: return Completable.error(PolarServiceNotAvailable())

        val builder = PftpRequest.PbPFtpOperation.newBuilder()
            .setCommand(PftpRequest.PbPFtpOperation.Command.REMOVE)
            .setPath(entry.path)

        return client.request(builder.build().toByteArray())
            .toObservable()
            .ignoreElements()
            .onErrorResumeNext { throwable: Throwable ->
                Completable.error(handleError(throwable))
            }
    }

    override fun isOfflineExerciseV2Supported(identifier: String): Single<Boolean> {
        val session = try {
            PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        } catch (error: Throwable) {
            BleLogger.e(TAG, "Failed to get session for device $identifier: ${error.message}")
            return Single.error(error)
        }

        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
        if (client == null) {
            BleLogger.e(TAG, "PFTP client not available for device $identifier")
            return Single.error(PolarServiceNotAvailable())
        }

        return checkDmExerciseSupport(client)
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

    /**
     * Check if device supports offline exercise (dm_exercise capability).
     * Reads DEVICE.BPB to check if "dm_exercise" is in the capabilities list.
     *
     * @param client PFTP client
     * @return Single that emits true if dm_exercise is supported, false otherwise
     */
    private fun checkDmExerciseSupport(client: BlePsFtpClient): Single<Boolean> {
        val builder = PftpRequest.PbPFtpOperation.newBuilder()
            .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
            .setPath(DEVICE_INFO_PATH)

        return client.request(builder.build().toByteArray())
            .map { byteArrayOutputStream: ByteArrayOutputStream ->
                val deviceInfo = Device.PbDeviceInfo.parseFrom(byteArrayOutputStream.toByteArray())
                deviceInfo.capabilitiesList.contains(DM_EXERCISE_CAPABILITY)
            }
            .doOnError { error ->
                BleLogger.e(TAG, "Failed to check dm_exercise capability: ${error.message}")
            }
            .onErrorReturnItem(false)
    }
}