// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl

import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.PftpResponseError
import com.polar.sdk.api.PolarTrainingSessionApi
import com.polar.sdk.api.errors.PolarDeviceDisconnected
import com.polar.sdk.api.errors.PolarServiceNotAvailable
import com.polar.sdk.api.model.PolarExerciseSession
import com.polar.sdk.api.model.trainingsession.PolarTrainingSession
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionFetchResult
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionProgress
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionReference
import com.polar.sdk.impl.utils.PolarServiceClientUtils
import com.polar.sdk.impl.utils.PolarTimeUtils
import com.polar.sdk.impl.utils.PolarTrainingSessionUtils
import fi.polar.remote.representation.protobuf.Structures
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import protocol.PftpError.PbPFtpError
import protocol.PftpNotification
import protocol.PftpRequest
import protocol.PftpResponse
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong

/**
 * Implementation of [PolarTrainingSessionApi].
 *
 * Handles training session retrieval, exercise session control, and related operations
 * on Polar devices via the PFTP file transfer protocol.
 *
 * Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TRAINING_DATA].
 */
internal class PolarTrainingSessionApiImpl(
    private val listener: BleDeviceListener
) : PolarTrainingSessionApi {

    companion object {
        private const val TAG = "PolarTrainingSessionApiImpl"
    }


    override fun getTrainingSessionReferences(
        identifier: String,
        fromDate: LocalDate?,
        toDate: LocalDate?
    ): Flow<PolarTrainingSessionReference> {
        return flow {
            val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
            val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                ?: throw PolarServiceNotAvailable()
            emitAll(PolarTrainingSessionUtils.getTrainingSessionReferences(client, fromDate, toDate))
        }
    }

    override suspend fun getTrainingSession(
        identifier: String,
        trainingSessionReference: PolarTrainingSessionReference
    ): PolarTrainingSession {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        return PolarTrainingSessionUtils.readTrainingSession(client, trainingSessionReference)
    }

    override fun getTrainingSessionWithProgress(
        identifier: String,
        trainingSessionReference: PolarTrainingSessionReference
    ): Flow<PolarTrainingSessionFetchResult> {
        return flow {
            val totalBytes = trainingSessionReference.fileSize
            val accumulatedBytes = AtomicLong(0L)
            emit(
                PolarTrainingSessionFetchResult.Progress(
                    progress = PolarTrainingSessionProgress(
                        totalBytes = totalBytes,
                        completedBytes = 0L,
                        progressPercent = 0,
                        currentFileName = null
                    )
                )
            )
            val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
            val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                ?: throw PolarServiceNotAvailable()
            client.setProgressCallback(object : BlePsFtpClient.ProgressCallback {
                override fun onProgressUpdate(bytesReceived: Long) {
                    val currentBytes = accumulatedBytes.addAndGet(bytesReceived)
                    val percent = if (totalBytes > 0) ((currentBytes * 100L) / totalBytes).toInt().coerceIn(0, 100) else 0
                    BleLogger.d(TAG, "Training session fetch progress: $currentBytes/$totalBytes ($percent%)")
                }
            })
            val result = PolarTrainingSessionUtils.readTrainingSessionWithProgress(client, trainingSessionReference)
            emit(PolarTrainingSessionFetchResult.Complete(result))
        }
    }

    override suspend fun deleteTrainingSession(
        identifier: String,
        reference: PolarTrainingSessionReference
    ) {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        PolarTrainingSessionUtils.deleteTrainingSession(client, reference)
    }

    override suspend fun startExercise(identifier: String, profile: PolarExerciseSession.SportProfile) {
        BleLogger.d(TAG, "Start exercise pressed for $identifier with profile=$profile")
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = (session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as? BlePsFtpClient)
            ?: throw PolarServiceNotAvailable()
        try {
            val params = PftpRequest.PbPFtpStartExerciseParams.newBuilder()
                .setSportIdentifier(Structures.PbSportIdentifier.newBuilder().setValue(profile.id.toLong()).build())
                .build()
            client.query(PftpRequest.PbPFtpQuery.START_EXERCISE_VALUE, params.toByteArray())
            BleLogger.d(TAG, "Start exercise succeeded for $identifier")
        } catch (t: Throwable) {
            BleLogger.e(TAG, "Start exercise failed for $identifier: ${t.message ?: "unknown error"}")
            throw handleError(t)
        }
    }

    override suspend fun pauseExercise(identifier: String) {
        BleLogger.d(TAG, "Pause exercise pressed for $identifier")
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = (session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as? BlePsFtpClient)
            ?: throw PolarServiceNotAvailable()
        try {
            client.query(PftpRequest.PbPFtpQuery.PAUSE_EXERCISE_VALUE, byteArrayOf())
            BleLogger.d(TAG, "Pause exercise succeeded for $identifier")
        } catch (t: Throwable) {
            BleLogger.e(TAG, "Pause exercise failed for $identifier: ${t.message ?: "unknown error"}")
            throw handleError(t)
        }
    }

    override suspend fun resumeExercise(identifier: String) {
        BleLogger.d(TAG, "Resume exercise pressed for $identifier")
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = (session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as? BlePsFtpClient)
            ?: throw PolarServiceNotAvailable()
        try {
            client.query(PftpRequest.PbPFtpQuery.RESUME_EXERCISE_VALUE, byteArrayOf())
            BleLogger.d(TAG, "Resume exercise succeeded for $identifier")
        } catch (t: Throwable) {
            BleLogger.e(TAG, "Resume exercise failed for $identifier: ${t.message ?: "unknown error"}")
            throw handleError(t)
        }
    }

    override suspend fun stopExercise(identifier: String) {
        BleLogger.d(TAG, "Stop exercise pressed for $identifier")
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = (session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as? BlePsFtpClient)
            ?: throw PolarServiceNotAvailable()
        try {
            val params = PftpRequest.PbPFtpStopExerciseParams.newBuilder().setSave(true).build()
            client.query(PftpRequest.PbPFtpQuery.STOP_EXERCISE_VALUE, params.toByteArray())
            BleLogger.d(TAG, "Stop exercise succeeded for $identifier")
        } catch (t: Throwable) {
            BleLogger.e(TAG, "Stop exercise failed for $identifier: ${t.message ?: "unknown error"}")
            throw handleError(t)
        }
    }

    override suspend fun getExerciseStatus(identifier: String): PolarExerciseSession.ExerciseInfo {
        BleLogger.d(TAG, "Get exercise status pressed for $identifier")
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = (session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as? BlePsFtpClient)
            ?: throw PolarServiceNotAvailable()
        return try {
            val out = client.query(PftpRequest.PbPFtpQuery.GET_EXERCISE_STATUS_VALUE, byteArrayOf())
            val info = parseExerciseStatus(out.toByteArray())
            BleLogger.d(TAG, "Get exercise status succeeded for $identifier: $info")
            info
        } catch (t: Throwable) {
            BleLogger.e(TAG, "Get exercise status failed for $identifier: ${t.message ?: "unknown error"}")
            throw handleError(t)
        }
    }

    override fun observeExerciseStatus(identifier: String): Flow<PolarExerciseSession.ExerciseInfo> {
        BleLogger.d(TAG, "Start observing exercise status for $identifier")
        return flow {
            val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
            val client = (session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as? BlePsFtpClient)
                ?: throw PolarServiceNotAvailable()
            client.waitForNotification()
                .filter { notification ->
                    notification.id == PftpNotification.PbPFtpDevToHostNotification.EXERCISE_STATUS_VALUE
                }
                .map { notification -> parseExerciseStatus(notification.byteArrayOutputStream.toByteArray()) }
                .catch { t -> throw handleError(t) }
                .collect { info ->
                    BleLogger.d(TAG, "Exercise status notification received for $identifier: $info")
                    emit(info)
                }
        }
    }


    private fun parseExerciseStatus(data: ByteArray): PolarExerciseSession.ExerciseInfo {
        val proto = PftpResponse.PbPftpGetExerciseStatusResult.parseFrom(data)
        BleLogger.d(
            TAG,
            "EX_STATUS raw: state=${proto.exerciseState} hasSport=${proto.hasSportIdentifier()} " +
                "sport=${if (proto.hasSportIdentifier()) proto.sportIdentifier.value else -1} startTime=${proto.startTime}"
        )
        val status = when (proto.exerciseState) {
            PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState.EXERCISE_STATE_RUNNING ->
                PolarExerciseSession.ExerciseStatus.IN_PROGRESS
            PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState.EXERCISE_STATE_PAUSED ->
                PolarExerciseSession.ExerciseStatus.PAUSED
            PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState.EXERCISE_STATE_OFF ->
                PolarExerciseSession.ExerciseStatus.STOPPED
            else -> PolarExerciseSession.ExerciseStatus.NOT_STARTED
        }
        val sport = if (proto.hasSportIdentifier()) {
            PolarExerciseSession.SportProfile.fromId(proto.sportIdentifier.value.toInt())
        } else {
            PolarExerciseSession.SportProfile.UNKNOWN
        }
        val startTime: LocalDateTime? = if (proto.hasStartTime()) {
            try {
                PolarTimeUtils.pbLocalDateTimeToLocalDateTimeWithOptionalTz(proto.startTime)
            } catch (e: Exception) {
                BleLogger.e(TAG, "Failed to parse exercise start time: ${e.message}")
                null
            }
        } else null
        return PolarExerciseSession.ExerciseInfo(status = status, sportProfile = sport, startTime = startTime)
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
}

