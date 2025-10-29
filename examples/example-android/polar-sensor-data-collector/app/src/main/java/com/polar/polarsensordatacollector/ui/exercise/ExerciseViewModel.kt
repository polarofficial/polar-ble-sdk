package com.polar.polarsensordatacollector.ui.exercise

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.polar.polarsensordatacollector.R
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarExerciseSession
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import java.util.Date

interface StringProvider {
    fun get(id: Int, vararg args: Any): String
}
class AndroidStringProvider(private val context: Context) : StringProvider {
    override fun get(id: Int, vararg args: Any): String = context.getString(id, *args)
}

class ExerciseViewModel(
    private val repo: ExerciseRepository,
    private val sp: StringProvider
) : ViewModel() {

    private val _status = MutableLiveData(PolarExerciseSession.ExerciseStatus.NOT_STARTED)
    val status: LiveData<PolarExerciseSession.ExerciseStatus> = _status

    private val _statusText = MutableLiveData("Not started")
    val statusText: LiveData<String> = _statusText

    private val _canStart = MutableLiveData(true)
    val canStart: LiveData<Boolean> = _canStart

    private val _canPause = MutableLiveData(false)
    val canPause: LiveData<Boolean> = _canPause

    private val _canResume = MutableLiveData(false)
    val canResume: LiveData<Boolean> = _canResume

    private val _canStop = MutableLiveData(false)
    val canStop: LiveData<Boolean> = _canStop

    private val _startTime = MutableLiveData<Date?>(null)
    val startTime: LiveData<Date?> = _startTime

    val sportProfiles = listOf(
        PolarExerciseSession.SportProfile.RUNNING,
        PolarExerciseSession.SportProfile.CYCLING,
        PolarExerciseSession.SportProfile.OTHER_OUTDOOR
    )
    private var selectedSport: PolarExerciseSession.SportProfile = PolarExerciseSession.SportProfile.RUNNING

    private val disposables = CompositeDisposable()

    init {
        repo.state()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ info ->
                _status.value = info.status

                _statusText.value = when (info.status) {
                    PolarExerciseSession.ExerciseStatus.IN_PROGRESS ->
                        sp.get(R.string.status_in_progress, info.sportProfile.name)
                    PolarExerciseSession.ExerciseStatus.PAUSED ->
                        sp.get(R.string.status_paused, info.sportProfile.name)
                    PolarExerciseSession.ExerciseStatus.STOPPED ->
                        sp.get(R.string.status_stopped, info.sportProfile.name)
                    PolarExerciseSession.ExerciseStatus.NOT_STARTED ->
                        sp.get(R.string.status_not_started)
                    PolarExerciseSession.ExerciseStatus.SYNC_REQUIRED ->
                        sp.get(R.string.status_sync_required)
                }

                _canStart.value = info.status == PolarExerciseSession.ExerciseStatus.NOT_STARTED ||
                        info.status == PolarExerciseSession.ExerciseStatus.STOPPED

                _canPause.value  = info.status == PolarExerciseSession.ExerciseStatus.IN_PROGRESS
                _canResume.value = info.status == PolarExerciseSession.ExerciseStatus.PAUSED
                _canStop.value   = info.status == PolarExerciseSession.ExerciseStatus.IN_PROGRESS ||
                        info.status == PolarExerciseSession.ExerciseStatus.PAUSED

                _startTime.value = info.startTime
            }, { /* swallow to UI */ })
            .addTo(disposables)

        repo.refresh().subscribe({}, {}).addTo(disposables)
    }

    fun selectProfile(profile: PolarExerciseSession.SportProfile) {
        selectedSport = profile
    }

    fun start() = run { repo.start(selectedSport) }
    fun pause() = run { repo.pause() }
    fun resume() = run { repo.resume() }
    fun stop() = run { repo.stop() }

    private fun run(op: () -> Completable) {
        op().subscribe({}, {}).addTo(disposables)
    }

    override fun onCleared() {
        disposables.clear()
    }
}

class ExerciseViewModelFactory(
    private val api: PolarBleApi,
    private val deviceId: String,
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences("exercise_prefs", Context.MODE_PRIVATE)

        val repo = ExerciseRepository(
            api as com.polar.sdk.api.PolarTrainingSessionApi,
            api,
            deviceId,
            prefs
        )
        val sp = AndroidStringProvider(appCtx)
        return ExerciseViewModel(repo, sp) as T
    }
}