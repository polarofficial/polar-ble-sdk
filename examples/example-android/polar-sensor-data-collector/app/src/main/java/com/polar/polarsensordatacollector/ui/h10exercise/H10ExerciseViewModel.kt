package com.polar.polarsensordatacollector.ui.h10exercise

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.polar.polarsensordatacollector.repository.H10ExerciseRepository
import com.polar.polarsensordatacollector.ui.landing.ONLINE_OFFLINE_KEY_DEVICE_ID
import com.polar.sdk.api.model.PolarExerciseData
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

private const val TAG = "H10ExerciseViewModel"

@HiltViewModel
class H10ExerciseViewModel @Inject constructor(
    private val repository: H10ExerciseRepository,
    state: SavedStateHandle
) : ViewModel() {

    private val deviceId =
        state.get<String>(ONLINE_OFFLINE_KEY_DEVICE_ID)
            ?: throw Exception("Device settings viewModel must know the deviceId")

    private val disposables = CompositeDisposable()

    private val _statusText = MutableStateFlow("")

    val featureState = repository.featureState

    init {
        _statusText.value = ""
        val disposable = repository.requestRecordingStatus(deviceId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ (enabled, _) ->
                repository.updateStatus(isSupported = true, isEnabled = enabled)
            }, { e ->
                Log.e(TAG, "requestRecordingStatus() failed", e)
            })
        disposables.add(disposable)
    }

    fun listExercises(onResult: (Int) -> Unit, onError: () -> Unit) {
        val disposable = repository.listExercises(deviceId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ entries ->
                onResult(entries.size)
            }, { e ->
                Log.e(TAG, "listExercises() failed", e)
                onError()
            })
        disposables.add(disposable)
    }

    fun readFirstExercise(
        onExercise: (PolarExerciseData) -> Unit,
        onError: () -> Unit
    ) {
        val disposable = repository.listExercises(deviceId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .flatMap { list ->
                if (list.isNotEmpty()) {
                    repository.readExercise(deviceId, list.first())
                } else {
                    io.reactivex.rxjava3.core.Single.error(Exception("No exercises"))
                }
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ exercise ->
                onExercise(exercise)
            }, { e ->
                Log.e(TAG, "readFirstExercise() failed", e)
                onError()
            })
        disposables.add(disposable)
    }

    fun removeFirstExercise(onComplete: () -> Unit, onError: () -> Unit) {
        val disposable = repository.listExercises(deviceId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .flatMapCompletable { list ->
                if (list.isNotEmpty()) {
                    repository.removeExercise(deviceId, list.first())
                } else {
                    io.reactivex.rxjava3.core.Completable.error(Exception("No exercises"))
                }
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                onComplete()
            }, { e ->
                Log.e(TAG, "removeFirstExercise() failed", e)
                onError()
            })
        disposables.add(disposable)
    }

    fun toggleRecording() {
        val feature = featureState.value
        val disposable = if (feature.isEnabled) {
            repository.stopRecording(deviceId)
        } else {
            val exerciseId = "H10_EX_${System.currentTimeMillis()}"
            repository.startRecording(deviceId, exerciseId)
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnComplete {
                repository.updateRecordingEnabled(!feature.isEnabled)
            }
            .subscribe({}, { e ->
                Log.e(TAG, "toggleRecording() failed", e)
            })
        disposables.add(disposable)
    }

    override fun onCleared() {
        super.onCleared()
        disposables.clear()
    }
}