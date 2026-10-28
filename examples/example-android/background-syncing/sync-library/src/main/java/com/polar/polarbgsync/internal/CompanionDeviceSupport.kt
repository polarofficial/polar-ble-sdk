// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.polarbgsync.internal

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

internal class CompanionAssociationAttempt(val onFinished: (String?) -> Unit) {
    private enum class Outcome { PENDING, FINISHED, CANCELLED }
    private val outcome = AtomicReference(Outcome.PENDING)

    val cancelled: Boolean get() = outcome.get() == Outcome.CANCELLED

    fun tryFinish(cancelled: Boolean = false): Boolean =
        outcome.compareAndSet(Outcome.PENDING, if (cancelled) Outcome.CANCELLED else Outcome.FINISHED)
}

internal class CompanionDeviceRegistry(savedAddresses: Map<String, String>) {
    private val addresses = ConcurrentHashMap(savedAddresses)

    fun remember(deviceId: String, address: String) {
        addresses[deviceId] = address.uppercase()
    }

    fun resolve(identifier: String): String =
        addresses.entries.firstOrNull {
            it.key.equals(identifier, ignoreCase = true) ||
                it.value.equals(identifier, ignoreCase = true)
        }?.key ?: identifier

    fun address(deviceId: String): String = addresses[resolve(deviceId)] ?: deviceId

    fun forget(deviceId: String) {
        addresses.remove(resolve(deviceId))
    }
}

internal fun connectionSetupTimeoutMillis(waitForAssociation: Boolean): Long =
    if (waitForAssociation) 90_000L else 30_000L
