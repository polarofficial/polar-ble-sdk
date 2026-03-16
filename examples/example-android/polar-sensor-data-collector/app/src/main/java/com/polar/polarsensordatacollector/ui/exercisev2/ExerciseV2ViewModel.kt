package com.polar.polarsensordatacollector.ui.exercisev2

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.polar.polarsensordatacollector.repository.ExerciseV2Repository
import com.polar.polarsensordatacollector.ui.landing.ONLINE_OFFLINE_KEY_DEVICE_ID
import com.polar.sdk.api.model.PolarExerciseEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import javax.inject.Inject

private const val TAG = "ExerciseV2ViewModel"

@HiltViewModel
class ExerciseV2ViewModel @Inject constructor(
    private val repository: ExerciseV2Repository,
    state: SavedStateHandle
) : ViewModel() {

    private val disposables = CompositeDisposable()

    private val deviceId: String = state.get<String>(ONLINE_OFFLINE_KEY_DEVICE_ID)
        ?: throw Exception("ExerciseV2ViewModel must know the deviceId")

    fun startOfflineExercise(onResult: () -> Unit, onError: (String) -> Unit) {
        val disposable = repository.startOfflineExerciseV2(deviceId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result ->
                Log.d(TAG, "startOfflineExercise: result=${result.result}, path=${result.directoryPath}")
                if (result.result.name == "SUCCESS") {
                    onResult()
                } else {
                    val msg = "Start failed: ${result.result}"
                    Log.e(TAG, msg)
                    onError(msg)
                }
            }, { e ->
                Log.e(TAG, "startOfflineExercise error: ${e.message}")
                onError(e.message ?: "Unknown error")
            })
        disposables.add(disposable)
    }

    fun stopOfflineExercise(onResult: () -> Unit, onError: (String) -> Unit) {
        val disposable = repository.stopOfflineExerciseV2(deviceId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                Log.d(TAG, "stopOfflineExercise: success")
                onResult()
            }, { e ->
                Log.e(TAG, "stopOfflineExercise error: ${e.message}")
                onError(e.message ?: "Unknown error")
            })
        disposables.add(disposable)
    }

    fun listOfflineExercises(onNext: (PolarExerciseEntry) -> Unit, onError: (String) -> Unit, onComplete: () -> Unit) {
        val disposable = repository.listOfflineExercisesV2(deviceId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { entry ->
                    Log.d(TAG, "listOfflineExercises: Found exercise ${entry.identifier}")
                    onNext(entry)
                },
                { e ->
                    Log.e(TAG, "listOfflineExercises error: ${e.message}")
                    onError(e.message ?: "Unknown error")
                },
                {
                    Log.d(TAG, "listOfflineExercises: Complete")
                    onComplete()
                }
            )
        disposables.add(disposable)
    }

    fun fetchOfflineExercise(
        onResult: (com.polar.sdk.api.model.PolarExerciseData) -> Unit,
        onError: (String) -> Unit
    ) {
        val disposable = repository.listOfflineExercisesV2(deviceId)
            .firstOrError()
            .flatMap { entry ->
                Log.d(TAG, "fetchOfflineExercise: Fetching first exercise ${entry.identifier}")
                repository.fetchOfflineExerciseV2(deviceId, entry)
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ data ->
                Log.d(TAG, "fetchOfflineExercise: Success")
                onResult(data)
            }, { e ->
                Log.e(TAG, "fetchOfflineExercise error: ${e.message}")
                onError(e.message ?: "Unknown error")
            })
        disposables.add(disposable)
    }

    fun removeOfflineExercise(onResult: () -> Unit, onError: (String) -> Unit) {
        val disposable = repository.listOfflineExercisesV2(deviceId)
            .firstOrError()
            .flatMapCompletable { entry ->
                Log.d(TAG, "removeOfflineExercise: Removing first exercise ${entry.identifier}")
                repository.removeOfflineExerciseV2(deviceId, entry)
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                Log.d(TAG, "removeOfflineExercise: Success")
                onResult()
            }, { e ->
                Log.e(TAG, "removeOfflineExercise error: ${e.message}")
                onError(e.message ?: "Unknown error")
            })
        disposables.add(disposable)
    }

    fun getOfflineExerciseStatus(onResult: (Boolean) -> Unit, onError: (String) -> Unit) {
        val disposable = repository.getOfflineExerciseStatusV2(deviceId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ running ->
                Log.d(TAG, "getOfflineExerciseStatus: running=$running")
                onResult(running)
            }, { e ->
                Log.e(TAG, "getOfflineExerciseStatus error: ${e.message}")
                onError(e.message ?: "Unknown error")
            })
        disposables.add(disposable)
    }

    fun checkStatusAndListOfflineExercises(
        onResult: (isRunning: Boolean, hasExercise: Boolean, entry: PolarExerciseEntry?) -> Unit,
        onError: (String) -> Unit
    ) {
        val disposable = repository.getOfflineExerciseStatusV2(deviceId)
            .flatMap { running ->
                repository.listOfflineExercisesV2(deviceId)
                    .toList()
                    .map { entries ->
                        Log.d(TAG, "checkStatusAndList: running=$running, exerciseCount=${entries.size}")
                        Triple(running, entries.isNotEmpty(), entries.firstOrNull())
                    }
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result ->
                onResult(result.first, result.second, result.third)
            }, { e ->
                Log.e(TAG, "checkStatusAndList error: ${e.message}")
                onError(e.message ?: "Unknown error")
            })
        disposables.add(disposable)
    }

    override fun onCleared() {
        super.onCleared()
        disposables.clear()
    }
}



