package com.polar.androidcommunications.common.ble

import android.os.Build

/**
 * Wrapper for Android OS [Build]. Helps unit test decoupling from Android platform
 */
class AndroidBuildUtils {
    companion object {
        /**
         * Get the Android SDK version running on this device.
         */
        @JvmStatic
        fun getBuildVersion(): Int {
            return Build.VERSION.SDK_INT
        }

        /**
         * Get the manufacturer of this device.
         */
        @JvmStatic
        fun getBrand(): String {
            return Build.BRAND
        }

        /**
         * Get the model of this device.
         */
        @JvmStatic
        fun getModel(): String {
            return Build.MODEL
        }
    }
}