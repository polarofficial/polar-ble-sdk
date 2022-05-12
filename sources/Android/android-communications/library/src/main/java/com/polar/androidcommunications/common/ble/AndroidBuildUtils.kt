package com.polar.androidcommunications.common.ble

import android.os.Build

class AndroidBuildUtils {
    companion object {
        /**
         * Get the Android SDK version running on this device.
         *
         * This wrapper helps unit test decoupling from Android platform
         */
        @JvmStatic
        fun getBuildVersion(): Int {
            return Build.VERSION.SDK_INT
        }
    }
}