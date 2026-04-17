package com.polar.androidcommunications.api.ble

import com.polar.androidcommunications.BuildConfig

object BleRefApiVersion {
    // just use simplified latest tag version
    const val VERSION_STRING: String = BuildConfig.GIT_VERSION

    fun major(): Int {
        val components =
            BuildConfig.GIT_VERSION.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
        if (components.size != 0) {
            return components[0].toInt()
        }
        return 0
    }

    fun minor(): Int {
        val components =
            BuildConfig.GIT_VERSION.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
        if (components.size > 1) {
            return components[1].toInt()
        }
        return 0
    }

    fun patch(): Int {
        val components =
            BuildConfig.GIT_VERSION.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
        if (components.size > 2) {
            return components[2].toInt()
        }
        return 0
    }
}
