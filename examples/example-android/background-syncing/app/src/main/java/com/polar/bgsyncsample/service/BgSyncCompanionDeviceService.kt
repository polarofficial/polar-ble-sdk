// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.bgsyncsample.service

import android.companion.AssociationInfo
import android.content.Context
import android.util.Log
import com.polar.androidcommunications.common.ble.companion.PolarCompanionDeviceServiceBase
import com.polar.bgsyncsample.log.SyncLogStore
import com.polar.polarbgsync.api.PolarBackgroundSync

class BgSyncCompanionDeviceService : PolarCompanionDeviceServiceBase() {
    private var forwardingAssociationInfo = false

    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        val address = associationInfo.deviceMacAddress?.toString() ?: return
        if (!restoreSync(address)) return
        forwardingAssociationInfo = true
        try {
            super.onDeviceAppeared(associationInfo)
        } finally {
            forwardingAssociationInfo = false
        }
    }

    @Suppress("DEPRECATION")
    override fun onDeviceAppeared(address: String) {
        // The framework's AssociationInfo callback also invokes this legacy overload.
        if (forwardingAssociationInfo) return
        if (restoreSync(address)) super.onDeviceAppeared(address)
    }

    private fun restoreSync(address: String): Boolean {
        val saved = SavedSyncSessions(getSharedPreferences(SavedSyncSessions.PREFS_NAME, Context.MODE_PRIVATE))
        if (!saved.enabled || saved.deviceIds.isEmpty()) return false

        // Initialize SDK listeners before forwarding the event, including on a cold process.
        val syncApi = PolarBackgroundSync.create(this)
        val deviceId = syncApi.getDeviceIdForCompanionAddress(address)
        if (!saved.shouldRestore(deviceId)) return false

        val message = "[$deviceId] CDM presence detected; restoring background sync"
        Log.i("BgSyncCompanionService", message)
        SyncLogStore.getInstance(this).addEntry(message)
        return BgSyncForegroundService.start(this)
    }
}
