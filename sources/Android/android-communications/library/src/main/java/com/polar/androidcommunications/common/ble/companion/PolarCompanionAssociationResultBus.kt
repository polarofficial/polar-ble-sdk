// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.androidcommunications.common.ble.companion

import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-process signal that a [PolarCompanionAssociationActivity] launched by the SDK has finished.
 * Listeners should re-check association state rather than rely on the raw activity result.
 */
internal object PolarCompanionAssociationResultBus {

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun notifyAssociationFlowFinished() {
        listeners.forEach { it() }
    }
}


