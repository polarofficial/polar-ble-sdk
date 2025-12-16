package com.polar.polarsensordatacollector.ui.exercise

import android.content.SharedPreferences
import android.util.Log
import com.polar.sdk.api.PolarTrainingSessionApi
import com.polar.sdk.api.model.PolarExerciseSession
import com.polar.sdk.api.PolarBleApi
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.util.concurrent.TimeUnit

class ExerciseRepository(
    private val api: PolarTrainingSessionApi,
    private val bleApi: PolarBleApi,
    private val deviceId: String,
    private val prefs: SharedPreferences
) {
    private val stateSubject = BehaviorSubject.createDefault(
        PolarExerciseSession.ExerciseInfo(
            status = PolarExerciseSession.ExerciseStatus.NOT_STARTED,
            sportProfile = PolarExerciseSession.SportProfile.UNKNOWN
        )
    )

    private var exerciseStatusDisposable: Disposable? = null

    private var lastKnownSport: PolarExerciseSession.SportProfile
        get() = prefs.getInt(KEY_LAST_SPORT_ID, PolarExerciseSession.SportProfile.OTHER_OUTDOOR.id)
            .let { id -> PolarExerciseSession.SportProfile.fromId(id) }
        set(value) { prefs.edit().putInt(KEY_LAST_SPORT_ID, value.id).apply() }

    fun state(): Observable<PolarExerciseSession.ExerciseInfo> =
        stateSubject.hide().distinctUntilChanged()

    private fun mergeWithMemory(newInfo: PolarExerciseSession.ExerciseInfo): PolarExerciseSession.ExerciseInfo {
        if (newInfo.status == PolarExerciseSession.ExerciseStatus.IN_PROGRESS ||
            newInfo.status == PolarExerciseSession.ExerciseStatus.PAUSED
        ) {
            lastKnownSport = newInfo.sportProfile
            return newInfo
        }

        val sportMissing = newInfo.sportProfile == PolarExerciseSession.SportProfile.UNKNOWN
        val isStoppedLike = when (newInfo.status) {
            PolarExerciseSession.ExerciseStatus.STOPPED,
            PolarExerciseSession.ExerciseStatus.NOT_STARTED,
            PolarExerciseSession.ExerciseStatus.SYNC_REQUIRED -> true
            else -> false
        }

        return if (isStoppedLike && sportMissing &&
            lastKnownSport != PolarExerciseSession.SportProfile.OTHER_OUTDOOR
        ) {
            newInfo.copy(sportProfile = lastKnownSport)
        } else {
            newInfo
        }
    }

    fun startObservingExerciseStatus(): Disposable {
        exerciseStatusDisposable?.dispose()

        exerciseStatusDisposable = api.observeExerciseStatus(deviceId)
            .subscribe(
                { info ->
                    Log.d(TAG, "Exercise status notification received: $info")
                    stateSubject.onNext(info)
                },
                { error ->
                    Log.w(TAG, "Exercise status observation error: ${error.message}")
                }
            )

        return exerciseStatusDisposable!!
    }

    fun stopObservingExerciseStatus() {
        exerciseStatusDisposable?.dispose()
        exerciseStatusDisposable = null
    }

    fun refresh(): Completable =
        api.getExerciseStatus(deviceId)
            .subscribeOn(Schedulers.io())
            .map(::mergeWithMemory)
            .doOnSuccess(stateSubject::onNext)
            .ignoreElement()
            .doOnError { e -> Log.w(TAG, "refresh failed: ${e.message}") }
            .onErrorComplete()

    fun start(profile: PolarExerciseSession.SportProfile): Completable =
        Completable.fromAction { lastKnownSport = profile }
            .andThen(api.startExercise(deviceId, profile).subscribeOn(Schedulers.io()))
            .andThen(api.getExerciseStatus(deviceId).subscribeOn(Schedulers.io()))
            .map(::mergeWithMemory)
            .doOnSuccess(stateSubject::onNext)
            .ignoreElement()

    fun pause(): Completable =
        api.pauseExercise(deviceId)
            .subscribeOn(Schedulers.io())
            .andThen(api.getExerciseStatus(deviceId))
            .map(::mergeWithMemory)
            .doOnSuccess(stateSubject::onNext)
            .ignoreElement()

    fun resume(): Completable =
        api.resumeExercise(deviceId)
            .subscribeOn(Schedulers.io())
            .andThen(api.getExerciseStatus(deviceId))
            .map(::mergeWithMemory)
            .doOnSuccess(stateSubject::onNext)
            .ignoreElement()

    fun stop(): Completable =
        api.stopExercise(deviceId)
            .subscribeOn(Schedulers.io())
            .andThen(
                Completable.fromAction {
                    stateSubject.onNext(
                        PolarExerciseSession.ExerciseInfo(
                            status = PolarExerciseSession.ExerciseStatus.SYNC_REQUIRED,
                            sportProfile = lastKnownSport
                        )
                    )
                }
            )
            .andThen(startSyncIfAvailable())
            .andThen(waitUntilLikelySynced(90, 2))
            .onErrorComplete()
            .andThen(stopSyncIfAvailable())
            .andThen(refresh())


    private fun startSyncIfAvailable(): Completable = bleApi
            .sendInitializationAndStartSyncNotifications(deviceId)
            .subscribeOn(Schedulers.io())
            .ignoreElement()

    private fun stopSyncIfAvailable(): Completable = bleApi
            .sendTerminateAndStopSyncNotifications(deviceId)
            .subscribeOn(Schedulers.io())

    private fun waitUntilLikelySynced(timeoutSec: Long, pollSec: Long): Completable =
        Observable.interval(0, pollSec, TimeUnit.SECONDS)
            .subscribeOn(Schedulers.io())
            .flatMapSingle { api.getExerciseStatus(deviceId).subscribeOn(Schedulers.io()) }
            .map(::mergeWithMemory)
            .doOnNext(stateSubject::onNext)
            .filter { info ->
                info.status == PolarExerciseSession.ExerciseStatus.STOPPED ||
                        info.status == PolarExerciseSession.ExerciseStatus.NOT_STARTED
            }
            .take(1)
            .timeout(timeoutSec, TimeUnit.SECONDS)
            .ignoreElements()

    fun startAutoRefresh(intervalSec: Long = 5): Disposable =
        Observable.interval(0, intervalSec, TimeUnit.SECONDS)
            .flatMapCompletable { refresh() }
            .subscribe({ /* ok */ }, { e -> Log.w(TAG, "autoRefresh error: ${e.message}") })

    private companion object {
        private const val TAG = "ExerciseRepository"
        private const val KEY_LAST_SPORT_ID = "last_sport_id"
    }
}
