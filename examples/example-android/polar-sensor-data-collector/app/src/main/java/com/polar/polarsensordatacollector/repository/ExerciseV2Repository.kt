package com.polar.polarsensordatacollector.repository

import android.util.Log
import com.polar.sdk.api.PolarOfflineExerciseV2Api
import com.polar.sdk.api.model.PolarExerciseData
import com.polar.sdk.api.model.PolarExerciseEntry
import com.polar.sdk.api.model.PolarExerciseSession
import com.polar.sdk.impl.BDBleApiImpl
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseV2Repository @Inject constructor(
    private val api: BDBleApiImpl
) : PolarOfflineExerciseV2Api {

    companion object {
        private const val TAG = "ExerciseV2Repository"
    }

    /**
     * Start a new offline exercise on the device.
     *
     * @param deviceId the identifier of the Polar device
     * @param sportProfile the sport profile for the exercise (default: OTHER_OUTDOOR)
     * @return Single stream of [PolarOfflineExerciseV2Api.OfflineExerciseStartResult]
     */
    override fun startOfflineExerciseV2(
        identifier: String,
        sportProfile: PolarExerciseSession.SportProfile
    ): Single<PolarOfflineExerciseV2Api.OfflineExerciseStartResult> {
        return try {
            (api as PolarOfflineExerciseV2Api).startOfflineExerciseV2(identifier, sportProfile)
                .doOnSuccess { result ->
                    Log.d(TAG, "startOfflineExerciseV2 success: ${result.result}, path: ${result.directoryPath}")
                }
                .doOnError { e ->
                    Log.e(TAG, "startOfflineExerciseV2 failed: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "startOfflineExerciseV2 error: ${e.message}")
            Single.error(e)
        }
    }

    /**
     * Stop an ongoing offline exercise.
     *
     * @param identifier the identifier of the Polar device
     * @return Completable stream
     */
    override fun stopOfflineExerciseV2(identifier: String): Completable {
        return try {
            (api as PolarOfflineExerciseV2Api).stopOfflineExerciseV2(identifier)
                .doOnComplete {
                    Log.d(TAG, "stopOfflineExerciseV2 success")
                }
                .doOnError { e ->
                    Log.e(TAG, "stopOfflineExerciseV2 failed: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "stopOfflineExerciseV2 error: ${e.message}")
            Completable.error(e)
        }
    }

    /**
     * Get the current offline exercise status.
     *
     * @param identifier the identifier of the Polar device
     * @return Single<Boolean> true if exercise is running
     */
    override fun getOfflineExerciseStatusV2(identifier: String): Single<Boolean> {
        return try {
            (api as PolarOfflineExerciseV2Api).getOfflineExerciseStatusV2(identifier)
                .doOnSuccess { isRunning ->
                    Log.d(TAG, "getOfflineExerciseStatusV2 success: isRunning=$isRunning")
                }
                .doOnError { e ->
                    Log.e(TAG, "getOfflineExerciseStatusV2 failed: ${e.message}")
                    if (isSystemBusyError(e)) {
                        Log.e(TAG, "Device BUSY (202) - Exercise running")
                    }
                }
                .onErrorResumeNext { throwable: Throwable ->
                    if (isSystemBusyError(throwable)) {
                        Log.e(TAG, "Exercise running, system busy (202)")
                        Single.error(Exception("Exercise running, system busy (202)"))
                    } else {
                        Single.error(throwable)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "getOfflineExerciseStatusV2 error: ${e.message}")
            Single.error(e)
        }
    }

    /**
     * List all offline exercises stored in the device.
     *
     * @param identifier the identifier of the Polar device
     * @param directoryPath Optional directory path to search (default: "/")
     * @return Flowable of [PolarExerciseEntry] entries
     */
    override  fun listOfflineExercisesV2(identifier: String, directoryPath: String): Flowable<PolarExerciseEntry> {
        return try {
            (api as PolarOfflineExerciseV2Api).listOfflineExercisesV2(identifier, directoryPath)
                .doOnNext { entry ->
                    Log.d(TAG, "listOfflineExercisesV2 found exercise: ${entry.identifier}")
                }
                .doOnError { e ->
                    Log.e(TAG, "listOfflineExercisesV2 failed: ${e.message}")
                    if (isSystemBusyError(e)) {
                        Log.e(TAG, "Device BUSY (202) - Stop exercise first")
                    }
                }
                .onErrorResumeNext { throwable: Throwable ->
                    if (isSystemBusyError(throwable)) {
                        Log.e(TAG, "Exercise running. Stop exercise first before listing.")
                        Flowable.error(Exception("Exercise running, system busy (202)"))
                    } else {
                        Flowable.error(throwable)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "listOfflineExercisesV2 error: ${e.message}")
            Flowable.error(e)
        }
    }

    /**
     * Fetch an offline exercise from the device.
     *
     * @param identifier the identifier of the Polar device
     * @param entry [PolarExerciseEntry] object to fetch
     * @return Single stream of [PolarExerciseData]
     */
    override  fun fetchOfflineExerciseV2(identifier: String, entry: PolarExerciseEntry): Single<PolarExerciseData> {
        return try {
            (api as PolarOfflineExerciseV2Api).fetchOfflineExerciseV2(identifier, entry)
                .doOnSuccess { data ->
                    Log.d(TAG, "fetchOfflineExerciseV2 success: ${data.hrSamples.size} samples")
                }
                .doOnError { e ->
                    Log.e(TAG, "fetchOfflineExerciseV2 failed: ${e.message}")
                    if (isSystemBusyError(e)) {
                        Log.e(TAG, "Device BUSY (202) - Stop exercise first")
                    }
                }
                .onErrorResumeNext { throwable: Throwable ->
                    if (isSystemBusyError(throwable)) {
                        Log.e(TAG, "Exercise running. Stop exercise first before fetching.")
                        Single.error(Exception("Exercise running, system busy (202)"))
                    } else {
                        Single.error(throwable)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "fetchOfflineExerciseV2 error: ${e.message}")
            Single.error(e)
        }
    }

    /**
     * Remove an offline exercise from the device.
     *
     * @param identifier the identifier of the Polar device
     * @param entry [PolarExerciseEntry] object to remove
     * @return Completable stream
     */
    override fun removeOfflineExerciseV2(identifier: String, entry: PolarExerciseEntry): Completable {
        return try {
            (api as PolarOfflineExerciseV2Api).removeOfflineExerciseV2(identifier, entry)
                .doOnComplete {
                    Log.d(TAG, "removeOfflineExerciseV2 success: removed ${entry.path}")
                }
                .doOnError { e ->
                    Log.e(TAG, "removeOfflineExerciseV2 failed: ${e.message}")
                }
                .onErrorResumeNext { throwable: Throwable ->
                    if (isSystemBusyError(throwable)) {
                        Log.e(TAG, "Exercise running. Stop exercise first before removing.")
                        Completable.error(Exception("Exercise running, system busy (202)"))
                    } else {
                        Completable.error(throwable)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "removeOfflineExerciseV2 error: ${e.message}")
            Completable.error(e)
        }
    }

    /**
     * Check if device supports offline exercise V2 (dm_exercise capability).
     *
     * @param identifier the identifier of the Polar device
     * @return Single<Boolean> true if dm_exercise capability is present
     */
    override fun isOfflineExerciseV2Supported(identifier: String): Single<Boolean> {
        Log.d(TAG, "========== isOfflineExerciseV2Supported called for device: $identifier ==========")
        return try {
            (api as PolarOfflineExerciseV2Api).isOfflineExerciseV2Supported(identifier)
                .doOnSuccess { isSupported ->
                    Log.d(TAG, "========== CAPABILITY CHECK RESULT ==========")
                    Log.d(TAG, "Device: $identifier")
                    Log.d(TAG, "dm_exercise supported: $isSupported")
                    if (isSupported) {
                        Log.i(TAG, "✓ Device SUPPORTS offline exercise V2")
                    } else {
                        Log.w(TAG, "✗ Device DOES NOT support offline exercise V2")
                    }
                    Log.d(TAG, "==========================================")
                }
                .doOnError { e ->
                    Log.e(TAG, "========== CAPABILITY CHECK ERROR ==========")
                    Log.e(TAG, "Device: $identifier")
                    Log.e(TAG, "Error: ${e.message}")
                    Log.e(TAG, "Error type: ${e.javaClass.simpleName}")
                    e.printStackTrace()
                    Log.e(TAG, "==========================================")
                }
        } catch (e: Exception) {
            Log.e(TAG, "isOfflineExerciseV2Supported exception: ${e.message}")
            e.printStackTrace()
            Single.error(e)
        }
    }

    private fun isSystemBusyError(throwable: Throwable): Boolean {
        return throwable.message?.contains("202") == true ||
                throwable.message?.contains("SYSTEM_BUSY") == true ||
                throwable.message?.contains("Exercise running") == true ||
                throwable.message?.contains("BUSY") == true
    }
}







