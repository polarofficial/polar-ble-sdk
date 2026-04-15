package com.polar.polarsensordatacollector.ui.exercise

import android.content.SharedPreferences
import android.util.Log
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarTrainingSessionApi
import com.polar.sdk.api.model.PolarExerciseSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class ExerciseRepository(
    private val api: PolarTrainingSessionApi,
    private val bleApi: PolarBleApi,
    private val deviceId: String,
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val _state = MutableStateFlow(
        PolarExerciseSession.ExerciseInfo(
            status = PolarExerciseSession.ExerciseStatus.NOT_STARTED,
            sportProfile = PolarExerciseSession.SportProfile.UNKNOWN
        )
    )

    private val _errorEvent = MutableStateFlow<String?>(null)
    val errorEvent: StateFlow<String?> = _errorEvent.asStateFlow()

    private var observeJob: Job? = null
    private var autoRefreshJob: Job? = null

    private var lastKnownSport: PolarExerciseSession.SportProfile
        get() = prefs.getInt(KEY_LAST_SPORT_ID, PolarExerciseSession.SportProfile.OTHER_OUTDOOR.id)
            .let { id -> PolarExerciseSession.SportProfile.fromId(id) }
        set(value) { prefs.edit().putInt(KEY_LAST_SPORT_ID, value.id).apply() }

    fun state(): StateFlow<PolarExerciseSession.ExerciseInfo> = _state.asStateFlow()

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

    fun startObservingExerciseStatus(): Job {
        observeJob?.cancel()
        observeJob = scope.launch {
            api.observeExerciseStatus(deviceId)
                .catch { error ->
                    Log.w(TAG, "Exercise status observation error: ${error.message}")
                    _errorEvent.value = "Exercise status error: ${error.message ?: "unknown error"}"
                }
                .collect { info ->
                    Log.d(TAG, "Exercise status notification received: $info")
                    _state.value = info
                }
        }
        return observeJob!!
    }

    fun stopObservingExerciseStatus() {
        observeJob?.cancel()
        observeJob = null
    }

    suspend fun refresh() {
        try {
            val info = mergeWithMemory(api.getExerciseStatus(deviceId))
            _state.value = info
            _errorEvent.value = null
        } catch (e: Exception) {
            Log.w(TAG, "refresh failed: ${e.message}")
            _errorEvent.value = "Failed to refresh: ${e.message ?: "unknown error"}"
        }
    }

    suspend fun start(profile: PolarExerciseSession.SportProfile) {
        try {
            lastKnownSport = profile
            api.startExercise(deviceId, profile)
            _state.value = PolarExerciseSession.ExerciseInfo(
                status = PolarExerciseSession.ExerciseStatus.IN_PROGRESS,
                sportProfile = profile
            )
            _errorEvent.value = null
        } catch (e: Exception) {
            Log.w(TAG, "start failed: ${e.message}")
            _errorEvent.value = "Failed to start exercise: ${e.message ?: "unknown error"}"
            throw e
        }
    }

    suspend fun pause() {
        try {
            api.pauseExercise(deviceId)
            _state.value = _state.value.copy(status = PolarExerciseSession.ExerciseStatus.PAUSED)
            _errorEvent.value = null
        } catch (e: Exception) {
            Log.w(TAG, "pause failed: ${e.message}")
            _errorEvent.value = "Failed to pause exercise: ${e.message ?: "unknown error"}"
            throw e
        }
    }

    suspend fun resume() {
        try {
            api.resumeExercise(deviceId)
            _state.value = _state.value.copy(status = PolarExerciseSession.ExerciseStatus.IN_PROGRESS)
            _errorEvent.value = null
        } catch (e: Exception) {
            Log.w(TAG, "resume failed: ${e.message}")
            _errorEvent.value = "Failed to resume exercise: ${e.message ?: "unknown error"}"
            throw e
        }
    }

    suspend fun stop() {
        api.stopExercise(deviceId)
        _state.value = PolarExerciseSession.ExerciseInfo(
            status = PolarExerciseSession.ExerciseStatus.SYNC_REQUIRED,
            sportProfile = lastKnownSport
        )
        try {
            bleApi.sendInitializationAndStartSyncNotifications(deviceId)
        } catch (e: Exception) {
            Log.w(TAG, "startSync failed: ${e.message}")
        }
        try {
            waitUntilLikelySynced(timeoutMs = 90_000L, pollMs = 2_000L)
        } catch (e: Exception) {
            Log.w(TAG, "waitUntilLikelySynced timed out or failed: ${e.message}")
        }
        try {
            bleApi.sendTerminateAndStopSyncNotifications(deviceId)
        } catch (e: Exception) {
            Log.w(TAG, "stopSync failed: ${e.message}")
        }
        refresh()
    }

    private suspend fun waitUntilLikelySynced(timeoutMs: Long, pollMs: Long) {
        withTimeout(timeoutMs) {
            flow {
                while (true) {
                    emit(api.getExerciseStatus(deviceId))
                    delay(pollMs)
                }
            }
                .map(::mergeWithMemory)
                .onEach { _state.value = it }
                .filter { info ->
                    info.status == PolarExerciseSession.ExerciseStatus.STOPPED ||
                            info.status == PolarExerciseSession.ExerciseStatus.NOT_STARTED
                }
                .first()
        }
    }

    fun startAutoRefresh(intervalMs: Long = 5_000L): Job {
        autoRefreshJob?.cancel()
        autoRefreshJob = scope.launch {
            while (true) {
                delay(intervalMs)
                try {
                    refresh()
                } catch (e: Exception) {
                    Log.w(TAG, "autoRefresh error: ${e.message}")
                }
            }
        }
        return autoRefreshJob!!
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    private companion object {
        private const val TAG = "ExerciseRepository"
        private const val KEY_LAST_SPORT_ID = "last_sport_id"
    }
}
