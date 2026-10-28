// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.polarbgsync.internal

import android.content.Context
import android.os.BatteryManager
import android.util.Log
import com.polar.androidcommunications.api.ble.model.gatt.client.ChargeState
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Monitors and logs battery levels for both the phone and connected Polar devices.
 *
 * Call [updateDeviceBattery] whenever a battery level is received from a device via the SDK,
 * [updateDeviceChargingState] whenever device charging status arrives, then call [logEvent]
 * at sync/cleanup boundaries for diagnostic correlation.
 */
internal class BatteryLevelMonitor(private val context: Context) {

    data class BatterySnapshot(
        val phoneBatteryPercent: Int?,
        val phoneChargingState: String,
        val deviceBatteryPercent: Int?,
        val deviceChargingState: String
    ) {
        val hasKnownDeviceCharging: Boolean
            get() = deviceChargingState.uppercase() != "UNKNOWN"
    }

    companion object {
        const val LOG_TAG = "POLARBAT"
    }

    private val deviceBatteryLevels = ConcurrentHashMap<String, Int>()
    private val deviceChargingStates = ConcurrentHashMap<String, String>()

    fun updateDeviceBattery(deviceId: String, level: Int) {
        deviceBatteryLevels[deviceId] = level
    }

    fun updateDeviceChargingState(deviceId: String, chargingState: ChargeState) {
        val state = when (chargingState) {
            ChargeState.CHARGING -> "CHARGING"
            ChargeState.DISCHARGING_ACTIVE -> "DISCHARGING_ACTIVE"
            ChargeState.DISCHARGING_INACTIVE -> "DISCHARGING_INACTIVE"
            ChargeState.UNKNOWN -> "UNKNOWN"
        }
        deviceChargingStates[deviceId] = state
    }

    fun clearDevice(deviceId: String) {
        deviceBatteryLevels.remove(deviceId)
        deviceChargingStates.remove(deviceId)
    }

    fun currentSnapshot(deviceId: String): BatterySnapshot {
        val phoneBat = getPhoneBatteryLevel().takeIf { it >= 0 }
        val phoneCharging = if (isPhoneCharging()) "CHARGING" else "UNPLUGGED"
        val deviceBat = deviceBatteryLevels[deviceId]
        val deviceCharging = deviceChargingStates[deviceId] ?: "UNKNOWN"
        return BatterySnapshot(phoneBat, phoneCharging, deviceBat, deviceCharging)
    }

    /**
     * Builds BAT params for PLOG emission.
     * Returns null until phone battery, device battery and (optionally) known device charging are present.
     */
    fun plogBatteryParams(deviceId: String, requireKnownDeviceCharging: Boolean = true): String? {
        val snap = currentSnapshot(deviceId)
        val phone = snap.phoneBatteryPercent ?: return null
        val device = snap.deviceBatteryPercent ?: return null
        if (requireKnownDeviceCharging && !snap.hasKnownDeviceCharging) return null
        return "phone=$phone phone_charging=${snap.phoneChargingState} device=$device device_charging=${snap.deviceChargingState}"
    }

    /**
     * Logs a POLARBAT diagnostic line and returns it.
     */
    fun logEvent(deviceId: String, event: String): String {
        val timestamp = Instant.now().toString()
        val snap = currentSnapshot(deviceId)
        val phoneBatStr = snap.phoneBatteryPercent?.toString() ?: "UNKNOWN"
        val deviceBatStr = snap.deviceBatteryPercent?.toString() ?: "UNKNOWN"

        val line = "POLARBAT|$timestamp" +
            "|device=$deviceId" +
            "|event=$event" +
            "|phone_bat=${phoneBatStr}%" +
            "|phone_charging=${snap.phoneChargingState}" +
            "|device_bat=${deviceBatStr}%" +
            "|device_charging=${snap.deviceChargingState}"

        Log.i(LOG_TAG, line)
        return line
    }

    private fun getPhoneBatteryLevel(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    }

    private fun isPhoneCharging(): Boolean {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return bm?.isCharging ?: false
    }
}
