package com.polar.sdk.api

import com.polar.sdk.api.model.PolarExerciseSession
import com.polar.sdk.api.model.trainingsession.PolarTrainingSession
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionFetchResult
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionReference
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Polar training session API.
 *
 * Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TRAINING_DATA]
 */
interface PolarTrainingSessionApi {

    /**
     * Get training session references for a given period. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TRAINING_DATA]
     *
     * @param identifier The Polar device ID or BT address.
     * @param fromDate The starting date of the period to retrieve training session references from. Optional.
     * @param toDate The ending date of the period to retrieve training session references from. Optional.
     * @return A [Flow] emitting [PolarTrainingSessionReference] objects representing the training session references for the specified period.
     */
    fun getTrainingSessionReferences(
        identifier: String,
        fromDate: LocalDate? = null,
        toDate: LocalDate? = null
    ): Flow<PolarTrainingSessionReference>

    /**
     * Api for removing single training session from a Polar device. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TRAINING_DATA]
     * You can get a list of training sessions with [getTrainingSessionReferences] API.
     *
     * @param identifier The Polar device ID or BT address.
     * @param reference PolarTrainingSessionReference with path in device to the training session to be removed.
     * @throws Throwable if the operation fails
     */
    suspend fun deleteTrainingSession(identifier: String, reference: PolarTrainingSessionReference)

    /**
     * Get training session. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TRAINING_DATA]
     *
     * @param identifier The Polar device ID or BT address.
     * @param trainingSessionReference The reference to the training session to retrieve.
     * @return [PolarTrainingSession] object representing the training session data.
     * @throws Throwable if the operation fails
     */
    suspend fun getTrainingSession(
        identifier: String,
        trainingSessionReference: PolarTrainingSessionReference
    ): PolarTrainingSession

    /**
     * Get training session with progress tracking. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TRAINING_DATA]
     *
     * @param identifier The Polar device ID or BT address.
     * @param trainingSessionReference The reference to the training session to retrieve.
     * @return A [Flow] emitting [PolarTrainingSessionFetchResult] objects with progress updates and final result.
     */
    fun getTrainingSessionWithProgress(
        identifier: String,
        trainingSessionReference: PolarTrainingSessionReference
    ): Flow<PolarTrainingSessionFetchResult>

    /**
     * Start an exercise session on the device. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TRAINING_DATA]
     *
     * @param identifier The Polar device ID or BT address.
     * @param profile The sport profile to use for the exercise session.
     * @throws Throwable if the operation fails
     */
    suspend fun startExercise(identifier: String, profile: PolarExerciseSession.SportProfile)

    /**
     * Pause an ongoing exercise session. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TRAINING_DATA]
     *
     * @param identifier The Polar device ID or BT address.
     * @throws Throwable if the operation fails
     */
    suspend fun pauseExercise(identifier: String)

    /**
     * Resume a paused exercise session. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TRAINING_DATA]
     *
     * @param identifier The Polar device ID or BT address.
     * @throws Throwable if the operation fails
     */
    suspend fun resumeExercise(identifier: String)

    /**
     * Stop the current exercise session. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TRAINING_DATA]
     *
     * By default, the session is saved on the device.
     *
     * @param identifier The Polar device ID or BT address.
     * @throws Throwable if the operation fails
     */
    suspend fun stopExercise(identifier: String)

    /**
     * Get the current exercise session status from the device. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TRAINING_DATA]
     *
     * @param identifier The Polar device ID or BT address.
     * @return The current [PolarExerciseSession.ExerciseInfo] for the device.
     * @throws Throwable if the operation fails
     */
    suspend fun getExerciseStatus(identifier: String): PolarExerciseSession.ExerciseInfo

    /**
     * Observe exercise session status changes from the device. Requires feature [PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TRAINING_DATA]
     *
     * @param identifier The Polar device ID or BT address.
     * @return A [Flow] emitting [PolarExerciseSession.ExerciseInfo] whenever the session status changes.
     * Errors may include [PolarErrors].
     */
    fun observeExerciseStatus(identifier: String): Flow<PolarExerciseSession.ExerciseInfo>
}