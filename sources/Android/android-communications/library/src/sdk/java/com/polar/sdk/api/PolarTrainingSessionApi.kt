package com.polar.sdk.api

import com.polar.sdk.api.model.trainingsession.PolarTrainingSession
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionReference
import com.polar.sdk.api.model.PolarExerciseSession
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import java.util.Date

/**
* Polar training session API.
*/
interface PolarTrainingSessionApi {

    /**
     * Get training session references for a given period.
     *
     * @param identifier The Polar device ID or BT address.
     * @param fromDate The starting date of the period to retrieve training session references from. Optional.
     * @param toDate The ending date of the period to retrieve training session references from. Optional.
     * @return A [Flowable] emitting [PolarTrainingSessionReference] objects representing the training session references for the specified period.
     */
    fun getTrainingSessionReferences(
        identifier: String,
        fromDate: Date? = null,
        toDate: Date? = null
    ): Flowable<PolarTrainingSessionReference>

    /**
     * Get training session.
     *
     * @param identifier The Polar device ID or BT address.
     * @param trainingSessionReference The reference to the training session to retrieve.
     * @return A [Single] emitting a [PolarTrainingSession] object representing the training session data.
     */
    fun getTrainingSession(
        identifier: String,
        trainingSessionReference: PolarTrainingSessionReference
    ): Single<PolarTrainingSession>
    
    /**
     * Start an exercise session on the device.
     *
     * @param identifier The Polar device ID or BT address.
     * @param profile The sport profile to use for the exercise session.
     * @return A [Completable] that completes when the command has been delivered to the device.
     */
    fun startExercise(identifier: String, profile: PolarExerciseSession.SportProfile): Completable

    /**
     * Pause an ongoing exercise session.
     *
     * @param identifier The Polar device ID or BT address.
     * @return A [Completable] that completes when the command has been delivered to the device.
     */
    fun pauseExercise(identifier: String): Completable

    /**
     * Resume a paused exercise session.
     *
     * @param identifier The Polar device ID or BT address.
     * @return A [Completable] that completes when the command has been delivered to the device.
     */
    fun resumeExercise(identifier: String): Completable

    /**
     * Stop the current exercise session.
     *
     * By default, the session is saved on the device.
     *
     * @param identifier The Polar device ID or BT address.
     * @return A [Completable] that completes when the command has been delivered to the device.
     */
    fun stopExercise(identifier: String): Completable

    /**
     * Get the current exercise session status from the device.
     *
     * @param identifier The Polar device ID or BT address.
     * @return A [Single] emitting the current [PolarExerciseSession.ExerciseInfo] for the device.
     */
    fun getExerciseStatus(identifier: String): Single<PolarExerciseSession.ExerciseInfo>
}