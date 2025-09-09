package com.polar.polarsensordatacollector.ui.exercise

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.polar.polarsensordatacollector.R
import com.polar.sdk.api.model.PolarExerciseSession
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.addTo

class ExerciseService : Service() {

    private val nm by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val deviceId = intent?.getStringExtra(EXTRA_DEVICE_ID) ?: return START_NOT_STICKY
        val api = ServiceLocator.exerciseApi
        val bleApi = ServiceLocator.bleApi

        if (!::repo.isInitialized) {
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            repo = ExerciseRepository(api, bleApi, deviceId, prefs)

            autoRefresh?.dispose()
            autoRefresh = repo.startAutoRefresh(intervalSec = 5)
            observeState(deviceId)
            startForeground(
                NOTIF_ID,
                buildNotification(
                    getString(R.string.exercise_title),
                    getString(R.string.exercise_connecting)
                )
            )
        }

        when (intent.action) {
            ACTION_PAUSE  -> repo.pause().subscribe({}, { e -> Log.w(TAG, "pause failed: ${e.message}") }).addTo(ops)
            ACTION_RESUME -> repo.resume().subscribe({}, { e -> Log.w(TAG, "resume failed: ${e.message}") }).addTo(ops)
            ACTION_STOP   -> repo.stop().subscribe({}, { e -> Log.w(TAG, "stop failed: ${e.message}") }).addTo(ops)
        }
        return START_STICKY
    }

    private fun observeState(deviceId: String) {
        stateSub?.dispose()
        stateSub = repo.state().subscribe({ info ->
            val title = when (info.status) {
                PolarExerciseSession.ExerciseStatus.IN_PROGRESS,
                PolarExerciseSession.ExerciseStatus.PAUSED,
                PolarExerciseSession.ExerciseStatus.STOPPED,
                PolarExerciseSession.ExerciseStatus.SYNC_REQUIRED ->
                    getString(R.string.exercise_title_running, info.sportProfile.toString())
                else -> getString(R.string.exercise_title)
            }
            val notif = when (info.status) {
                PolarExerciseSession.ExerciseStatus.IN_PROGRESS ->
                    buildNotification(title, getString(R.string.exercise_in_progress),
                        showPause = true, showStop = true, deviceId = deviceId)
                PolarExerciseSession.ExerciseStatus.PAUSED ->
                    buildNotification(title, getString(R.string.exercise_paused),
                        showResume = true, showStop = true, deviceId = deviceId)
                PolarExerciseSession.ExerciseStatus.SYNC_REQUIRED ->
                    buildNotification(title, getString(R.string.exercise_syncing), deviceId = deviceId)
                else ->
                    buildNotification(title, getString(R.string.exercise_idle), deviceId = deviceId)
            }
            if (canPostNotifications()) {
                nm.notify(NOTIF_ID, notif)
            } else {
                Log.d(TAG, "Notification not posted: POST_NOTIFICATIONS not granted")
            }
            Log.d(TAG, "UI state -> ${info.status} ${info.sportProfile}")
        }, { e -> Log.w(TAG, "state observe failed: ${e.message}") })
    }

    override fun onDestroy() {
        stateSub?.dispose()
        autoRefresh?.dispose()
        ops.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.exercise_title),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun canPostNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun buildNotification(
        title: String,
        text: String,
        showPause: Boolean = false,
        showResume: Boolean = false,
        showStop: Boolean = false,
        deviceId: String? = null
    ): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(title)
        .setContentText(text)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setOngoing(true)
        .apply {
            deviceId?.let {
                if (showPause)  addAction(NotificationCompat.Action(0, getString(R.string.action_pause),  pending(ACTION_PAUSE, it)))
                if (showResume) addAction(NotificationCompat.Action(0, getString(R.string.action_resume), pending(ACTION_RESUME, it)))
                if (showStop)   addAction(NotificationCompat.Action(0, getString(R.string.action_stop),   pending(ACTION_STOP, it)))
            }
        }
        .build()

    private fun pending(action: String, deviceId: String): PendingIntent {
        val i = Intent(this, ExerciseService::class.java).apply {
            this.action = action
            putExtra(EXTRA_DEVICE_ID, deviceId)
        }
        return PendingIntent.getService(
            this, action.hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private lateinit var repo: ExerciseRepository
    private var autoRefresh: Disposable? = null
    private var stateSub: Disposable? = null
    private val ops = CompositeDisposable()

    companion object {
        private const val TAG = "ExerciseService"
        private const val CHANNEL_ID = "exercise_channel"
        private const val NOTIF_ID = 4821
        private const val PREFS_NAME = "exercise_prefs"

        const val EXTRA_DEVICE_ID = "extra_device_id"
        const val ACTION_PAUSE  = "exercise_action_pause"
        const val ACTION_RESUME = "exercise_action_resume"
        const val ACTION_STOP   = "exercise_action_stop"
    }
}

object ServiceLocator {
    lateinit var exerciseApi: com.polar.sdk.api.PolarTrainingSessionApi
    lateinit var bleApi: com.polar.sdk.api.PolarBleApi
}
