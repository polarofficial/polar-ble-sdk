// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.bgsyncsample.service

import android.content.SharedPreferences

internal class SavedSyncSessions(private val prefs: SharedPreferences) {
    companion object {
        const val PREFS_NAME = "bg_sync_prefs"
        private const val KEY_DEVICE_IDS = "saved_device_ids"
        private const val KEY_ENABLED = "previously_connected"
    }

    val deviceIds: Set<String>
        get() = prefs.getStringSet(KEY_DEVICE_IDS, emptySet())?.toSet() ?: emptySet()

    val enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)

    fun shouldRestore(deviceId: String): Boolean =
        enabled && deviceIds.any { it.equals(deviceId, ignoreCase = true) }

    fun save(deviceIds: Set<String>, enabled: Boolean) {
        prefs.edit()
            .putStringSet(KEY_DEVICE_IDS, deviceIds)
            .putBoolean(KEY_ENABLED, enabled && deviceIds.isNotEmpty())
            .apply()
    }
}
