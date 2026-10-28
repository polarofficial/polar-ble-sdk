// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.bgsyncsample.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.polar.bgsyncsample.R
import com.polar.bgsyncsample.log.SyncLogStore
import com.polar.polarbgsync.api.*
import java.time.format.DateTimeFormatter

/**
 * Foreground service that keeps the BLE connection alive when the app
 * is backgrounded. Started when sync begins, stopped when sync ends.
 *
 * If the system kills the process and restarts this service (START_STICKY),
 * it restores sync sessions from SharedPreferences so that BLE connections
 * are re-established without waiting for the user to open the Activity.
 */
class BgSyncForegroundService : Service() {

    companion object {
        private const val TAG = "BgSyncForegroundService"
        private const val CHANNEL_ID = "polar_bg_sync_channel"
        private const val NOTIFICATION_ID = 1001
        private const val SYNC_SUMMARY_CHANNEL_ID = "polar_sync_summary_channel"
        private var nextSummaryNotificationId = 3000

        // Must match SyncViewModel constants — the service reads the same prefs
        private const val PREFS_NAME = "bg_sync_prefs"
        private const val KEY_ENABLED_DATA_TYPES = "enabled_data_types"
        private const val KEY_AUTO_DELETE = "auto_delete_from_device"
        private const val KEY_DEVICE_RETENTION_DAYS = "device_retention_days"
        private const val KEY_OFFLINE_RECORDING_RETENTION_DAYS = "offline_recording_retention_days"
        private const val KEY_SYNC_INTERVAL_MINUTES = "sync_interval_minutes"

        fun start(context: Context): Boolean {
            val intent = Intent(context, BgSyncForegroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                return true
            } catch (e: ForegroundServiceStartNotAllowedException) {
                reportStartFailure(context, e)
            } catch (e: SecurityException) {
                reportStartFailure(context, e)
            }
            return false
        }

        private fun reportStartFailure(context: Context, error: Exception) {
            val message = "Cannot start background sync service: ${error.message}"
            Log.e(TAG, message, error)
            SyncLogStore.getInstance(context).addEntry(message)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BgSyncForegroundService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Connecting to Polar device…"))
        Log.i(TAG, "Foreground service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val saved = SavedSyncSessions(getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
        if (!saved.enabled || saved.deviceIds.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        restoreSyncSessionsIfNeeded()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Foreground service destroyed")
    }

    /**
     * If the process was killed and the service restarted via START_STICKY,
     * the PolarBackgroundSyncImpl singleton will be fresh with no active sessions.
     * Restore them from SharedPreferences so BLE connections are re-established
     * immediately — the ViewModel will attach its UI callback via updateCallback()
     * when the user eventually opens the Activity.
     */
    private fun restoreSyncSessionsIfNeeded() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = SavedSyncSessions(prefs)
        if (!saved.enabled) return

        val savedIds = saved.deviceIds.sorted()
        if (savedIds.isEmpty()) return

        val syncApi = PolarBackgroundSync.create(this)

        // Check if sessions already exist (normal start, not a process restart)
        val needsRestore = savedIds.filter { !syncApi.hasActiveSession(it) }
        if (needsRestore.isEmpty()) return

        Log.i(TAG, "Process restart detected — restoring sync for ${needsRestore.size} device(s): $needsRestore")

        val savedTypes = readEnabledDataTypes(prefs.getStringSet(KEY_ENABLED_DATA_TYPES, null))

        val config = SyncConfig(
            autoDeleteFromDevice = prefs.getBoolean(KEY_AUTO_DELETE, false),
            deviceRetentionDays = prefs.getInt(KEY_DEVICE_RETENTION_DAYS, 2),
            offlineRecordingRetentionDays = prefs.getInt(KEY_OFFLINE_RECORDING_RETENTION_DAYS, 0),
            syncIntervalMinutes = prefs.getInt(KEY_SYNC_INTERVAL_MINUTES, 0)
        )

        // Minimal callback that writes to persistent log store — the ViewModel
        // will replace it with its full UI callback via updateCallback() when it starts.
        val logStore = SyncLogStore.getInstance(this)
        val serviceCallback = object : SyncCallback {
            override fun onSyncStarted(deviceId: String) {
                logStore.addEntry("[$deviceId] Sync started (background)")
                Log.i(TAG, "[$deviceId] Sync started (service-restored session)")
            }
            override fun onDataSynced(result: SyncResult) {
                logStore.addEntry("[${result.deviceId}] Synced: ${result.dataType} (${result.date})")
                Log.d(TAG, "[${result.deviceId}] Synced: ${result.dataType}")
            }
            override fun onSyncCompleted(deviceId: String) {
                logStore.addEntry("[$deviceId] Sync completed (background)")
                Log.i(TAG, "[$deviceId] Sync completed (service-restored session)")
            }
            override fun onSyncSummary(summary: SyncSummary) {
                val isOffline = summary.perTypeStats.keys.any { it.isOfflineRecording }
                val label = if (isOffline) "Offline Recordings" else "Sync Summary"
                val lines = mutableListOf("[${summary.deviceId}] ── $label (background) ──")
                lines.add("  Total: ${summary.totalItems} items, ${summary.durationSeconds}s")
                for ((type, stats) in summary.perTypeStats) {
                    lines.add("  ${type.name}: ${stats.items} items")
                }
                lines.add("──────────────────────────────")
                logStore.addEntry(lines.joinToString("\n"))
                postSyncSummaryNotification(summary)
            }
            override fun onSyncError(deviceId: String, error: SyncError) {
                val typeStr = error.dataType?.name ?: "general"
                logStore.addEntry("[$deviceId] Error ($typeStr): ${error.message}")
                Log.w(TAG, "[$deviceId] Sync error: ${error.message}")
            }
            override fun onConnectionStateChanged(deviceId: String, state: ConnectionState) {
                logStore.addEntry("[$deviceId] Connection: $state (background)")
                Log.i(TAG, "[$deviceId] Connection: $state (service-restored session)")
            }
            override fun onDeviceCleanupStarted(deviceId: String) {
                logStore.addEntry("[$deviceId] Cleaning old data from device… (background)")
            }
            override fun onDeviceCleanupCompleted(deviceId: String, deletedTypes: Int) {
                logStore.addEntry("[$deviceId] Device cleanup done ($deletedTypes types) (background)")
            }
        }

        for (deviceId in needsRestore) {
            syncApi.startSync(deviceId, savedTypes, config, serviceCallback)
        }
    }

    private fun readEnabledDataTypes(savedNames: Set<String>?): Set<SyncableDataType> {
        val parsed = savedNames
            ?.mapNotNull { name -> SyncableDataType.entries.find { it.name == name } }
            ?.toMutableSet()
            ?: return SyncableDataType.entries.toSet()

        // Migration: old saved selections had only OFFLINE_TEMPERATURE.
        if (parsed.contains(SyncableDataType.OFFLINE_TEMPERATURE)) {
            parsed.add(SyncableDataType.OFFLINE_SKIN_TEMPERATURE)
        }
        // Migration: derived ACC recordings now have a dedicated data type.
        if (parsed.contains(SyncableDataType.OFFLINE_ACC)) {
            parsed.add(SyncableDataType.OFFLINE_DERIVED_ACC)
        }

        return parsed
    }

    fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.sync_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the Polar device connection alive for background sync"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.sync_notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }

    private fun postSyncSummaryNotification(summary: SyncSummary) {
        val manager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SYNC_SUMMARY_CHANNEL_ID,
                "Sync Summary",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Notifications when a sync cycle completes" }
            manager.createNotificationChannel(channel)
        }

        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        val isOffline = summary.perTypeStats.keys.any { it.isOfflineRecording }
        val label = if (isOffline) "Offline recordings" else "Sync complete"
        val startStr = summary.startTime.format(timeFormatter)
        val endStr = summary.endTime.format(timeFormatter)
        val title = "$label [${summary.deviceId}]"

        val lines = mutableListOf<String>()
        lines.add("$startStr → $endStr (${summary.durationSeconds}s)")
        lines.add("Total: ${summary.totalItems} items")
        for ((type, stats) in summary.perTypeStats) {
            lines.add("${type.name}: ${stats.items} items")
        }
        val bigText = lines.joinToString("\n")

        val notification = NotificationCompat.Builder(this, SYNC_SUMMARY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText("${summary.totalItems} items in ${summary.durationSeconds}s")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setAutoCancel(true)
            .build()

        manager.notify(nextSummaryNotificationId++, notification)
    }
}
