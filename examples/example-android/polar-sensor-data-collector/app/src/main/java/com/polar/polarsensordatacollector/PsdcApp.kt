package com.polar.polarsensordatacollector

import android.app.Application
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PsdcApp : Application() {
    companion object {
        private const val TAG = "PsdcApp"
        const val STRICT_MODE_ENABLED = false
    }

    init {
        Log.d(TAG, "Application started")
    }

    override fun onCreate() {
        if (STRICT_MODE_ENABLED) {
            StrictMode.setThreadPolicy(
                ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }
        super.onCreate()
    }
}