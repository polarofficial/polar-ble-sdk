package com.polar.polarsensordatacollector.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.polar.polarsensordatacollector.R

class OnlineStreamService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "online_stream_service_channel"
        private const val CHANNEL_NAME = "Online Stream Service"

        @Volatile
        private var activeStreamCount = 0

        @Synchronized
        fun startService(context: Context) {
            activeStreamCount++
            if (activeStreamCount == 1) {
                val appContext = context.applicationContext
                val intent = Intent(appContext, OnlineStreamService::class.java)
                ContextCompat.startForegroundService(appContext, intent)
            }
        }

        @Synchronized
        fun stopService(context: Context) {
            if (activeStreamCount > 0) {
                activeStreamCount--
                if (activeStreamCount == 0) {
                    val appContext = context.applicationContext
                    val intent = Intent(appContext, OnlineStreamService::class.java)
                    appContext.stopService(intent)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(channel)

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.online_recording_notification_title))
            .setContentText(getString(R.string.online_recording_notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
    }
}