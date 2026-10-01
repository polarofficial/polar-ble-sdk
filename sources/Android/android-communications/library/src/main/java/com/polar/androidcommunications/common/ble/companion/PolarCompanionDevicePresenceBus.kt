// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.androidcommunications.common.ble.companion

import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight in-process event bus that bridges [android.companion.CompanionDeviceService]
 * presence callbacks (which are delivered to a manifest-declared Android Service component,
 * outside of the SDK's control) back to interested listeners registered by
 * `PolarCompanionDeviceApiImpl` / [PolarCompanionDeviceManager] consumers.
 *
 * A host application that wants to receive companion device presence events must:
 * 1. Declare a subclass of [PolarCompanionDeviceServiceBase] (or its own
 *    `CompanionDeviceService`) in `AndroidManifest.xml` bound to
 *    `android.companion.CompanionDeviceService`.
 * 2. Forward `onDeviceAppeared` / `onDeviceDisappeared` calls to
 *    [notifyDeviceAppeared] / [notifyDeviceDisappeared] (done automatically if extending
 *    [PolarCompanionDeviceServiceBase]).
 */
internal object PolarCompanionDevicePresenceBus {

    fun interface Listener {
        fun onPresenceChanged(macAddress: String, appeared: Boolean)
    }

    private val listeners = ConcurrentHashMap<String, MutableSet<Listener>>()

    fun addListener(macAddress: String, listener: Listener) {
        listeners.getOrPut(macAddress.uppercase()) { ConcurrentHashMap.newKeySet() }.add(listener)
    }

    fun removeListener(macAddress: String, listener: Listener) {
        listeners[macAddress.uppercase()]?.remove(listener)
    }

    fun removeAllListeners(macAddress: String) {
        listeners.remove(macAddress.uppercase())
    }

    fun notifyDeviceAppeared(macAddress: String) {
        listeners[macAddress.uppercase()]?.forEach { it.onPresenceChanged(macAddress, appeared = true) }
    }

    fun notifyDeviceDisappeared(macAddress: String) {
        listeners[macAddress.uppercase()]?.forEach { it.onPresenceChanged(macAddress, appeared = false) }
    }
}

