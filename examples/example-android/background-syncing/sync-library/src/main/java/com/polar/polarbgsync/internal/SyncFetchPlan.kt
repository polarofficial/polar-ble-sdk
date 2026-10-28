// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.polarbgsync.internal

import com.polar.polarbgsync.api.SyncableDataType

/** Preserves cycle progress and per-type retry budgets across reconnections. */
internal class SyncFetchPlan(private val maxRetries: Int) {
    private var active = false
    private val pending = mutableListOf<SyncableDataType>()
    private val failures = mutableMapOf<SyncableDataType, Int>()
    private val exhausted = mutableSetOf<SyncableDataType>()

    val next: SyncableDataType? get() = pending.firstOrNull()
    val nextIsRetry: Boolean get() = (failures[next] ?: 0) > 0
    val exhaustedTypes: Set<SyncableDataType> get() = exhausted.toSet()

    fun beginCycle(types: List<SyncableDataType>): Boolean {
        if (active) return false
        active = true
        pending.clear()
        pending.addAll(types)
        failures.clear()
        exhausted.clear()
        return true
    }

    fun complete(type: SyncableDataType) {
        pending.removeAll { it == type }
    }

    fun recordFailure(type: SyncableDataType): Boolean {
        complete(type)
        val count = (failures[type] ?: 0) + 1
        failures[type] = count
        if (count <= maxRetries) {
            pending.add(type)
            return true
        }
        exhausted.add(type)
        return false
    }

    fun finishCycle() {
        active = false
    }
}
