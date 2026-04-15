package com.polar.polarsensordatacollector.ui.exercise

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.polar.polarsensordatacollector.R
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarExerciseSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDateTime

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

    private val _startTime = MutableLiveData<LocalDateTime?>(null)
    val startTime: LiveData<LocalDateTime?> = _startTime

    private val _isObservingNotifications = MutableLiveData(false)
    val isObservingNotifications: LiveData<Boolean> = _isObservingNotifications

    private val _notificationEvent = MutableLiveData<String?>(null)
    val notificationEvent: LiveData<String?> = _notificationEvent

    private val _errorEvent = MutableLiveData<String?>(null)
    val errorEvent: LiveData<String?> = _errorEvent

    val sportProfiles = listOf(
        PolarExerciseSession.SportProfile.RUNNING,
        PolarExerciseSession.SportProfile.CYCLING,
        PolarExerciseSession.SportProfile.OTHER_OUTDOOR
    )
    private var selectedSport: PolarExerciseSession.SportProfile = PolarExerciseSession.SportProfile.RUNNING

    private var notificationObserveJob: Job? = null
    private var notificationEventJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.Main) {
            repo.state().collect { info ->
                applyInfo(info)
            }
        }
        viewModelScope.launch(Dispatchers.Main) {
            repo.errorEvent.collect { error ->
                if (error != null) {
                    _errorEvent.value = error
                }
            }
        }
        viewModelScope.launch { repo.refresh() }
        repo.startObservingExerciseStatus()
    }

    private fun applyInfo(info: PolarExerciseSession.ExerciseInfo) {
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
        _canStart.value  = info.status == PolarExerciseSession.ExerciseStatus.NOT_STARTED ||
                info.status == PolarExerciseSession.ExerciseStatus.STOPPED
        _canPause.value  = info.status == PolarExerciseSession.ExerciseStatus.IN_PROGRESS
        _canResume.value = info.status == PolarExerciseSession.ExerciseStatus.PAUSED
        _canStop.value   = info.status == PolarExerciseSession.ExerciseStatus.IN_PROGRESS ||
                info.status == PolarExerciseSession.ExerciseStatus.PAUSED
        _startTime.value = info.startTime
    }

    fun selectProfile(profile: PolarExerciseSession.SportProfile) {
        selectedSport = profile
    }

    fun toggleNotificationObservation() {
        if (_isObservingNotifications.value == true) {
            stopNotificationObservation()
        } else {
            startNotificationObservation()
        }
    }

    private fun startNotificationObservation() {
        _notificationEvent.value = null
        notificationObserveJob?.cancel()
        notificationEventJob?.cancel()

        notificationObserveJob = repo.startObservingExerciseStatus()

        notificationEventJob = viewModelScope.launch(Dispatchers.Main) {
            repo.state().collect { info ->
                _notificationEvent.value = when (info.status) {
                    PolarExerciseSession.ExerciseStatus.NOT_STARTED ->
                        sp.get(R.string.status_not_started)
                    PolarExerciseSession.ExerciseStatus.IN_PROGRESS ->
                        sp.get(R.string.status_in_progress, info.sportProfile.name)
                    PolarExerciseSession.ExerciseStatus.PAUSED ->
                        sp.get(R.string.status_paused, info.sportProfile.name)
                    PolarExerciseSession.ExerciseStatus.STOPPED ->
                        sp.get(R.string.status_stopped, info.sportProfile.name)
                    PolarExerciseSession.ExerciseStatus.SYNC_REQUIRED ->
                        sp.get(R.string.status_sync_required)
                }
            }
        }
        _isObservingNotifications.value = true
    }

    private fun stopNotificationObservation() {
        repo.stopObservingExerciseStatus()
        notificationObserveJob?.cancel()
        notificationObserveJob = null
        notificationEventJob?.cancel()
        notificationEventJob = null
        _isObservingNotifications.value = false
    }

    fun start()  { viewModelScope.launch { try { repo.start(selectedSport) } catch (e: Exception) { Log.e(TAG, "Start failed", e) } } }
    fun pause()  { viewModelScope.launch { try { repo.pause() } catch (e: Exception) { Log.e(TAG, "Pause failed", e) } } }
    fun resume() { viewModelScope.launch { try { repo.resume() } catch (e: Exception) { Log.e(TAG, "Resume failed", e) } } }
    fun stop()   { viewModelScope.launch { repo.stop() } }

    override fun onCleared() {
        stopNotificationObservation()
        repo.stopObservingExerciseStatus()
    }

    companion object {
        private const val TAG = "ExerciseViewModel"
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
        return ExerciseViewModel(repo, AndroidStringProvider(appCtx)) as T
    }
}