// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl

import com.polar.sdk.api.errors.PolarSdkConcurrentApiCallException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import com.polar.androidcommunications.api.ble.BleLogger
import java.util.concurrent.ConcurrentHashMap

/**
 * Single-flight guard for device-side API calls. Calls targeting the same device identifier are mutually
 * exclusive; calls targeting different devices may run in parallel. Calls without an identifier share one key.
 * The guard is not reentrant: a guarded call must not invoke another guarded call on the same device.
 */
internal class PolarApiCallGuard {
    private val activeCalls = ConcurrentHashMap<String, ActiveCall>()

    /**
     * Ensures a suspendable device-side API call runs without overlap with any other guarded call.
     *
     * @param callName Logical name of the API being invoked for diagnostics and error reporting.
     * @param identifier Optional target device identifier included in the thrown exception message.
     * @param allowConcurrent True when the API intentionally permits overlapping read/observe calls.
     * @param block The guarded suspend operation to execute while the lock is held.
     * @return The result produced by [block].
     * @throws PolarSdkConcurrentApiCallException if another guarded call is already active.
     */
    suspend fun <T> guardSuspend(
        callName: String,
        identifier: String? = null,
        allowConcurrent: Boolean = false,
        block: suspend () -> T
    ): T {
        val token = acquire(callName, identifier, allowConcurrent)
        return try {
            currentCoroutineContext().ensureActive()
            block()
        } finally {
            release(token)
        }
    }

    /**
     * Wraps a flow-producing operation in the same single-flight lock.
     *
     * @param callName Logical name of the API being invoked for diagnostics and error reporting.
     * @param identifier Optional target device identifier included in the thrown exception message.
     * @param allowConcurrent True when the API intentionally permits overlapping read/observe calls.
     * @param block Flow collection logic to execute while the lock is held.
     * @return A flow that acquires the lock when collection starts and releases it on completion or cancellation.
     * @throws PolarSdkConcurrentApiCallException if another guarded call is already active.
     */
    fun <T> guardFlow(
        callName: String,
        identifier: String? = null,
        allowConcurrent: Boolean = false,
        block: suspend FlowCollector<T>.() -> Unit
    ): Flow<T> =
        flow {
            val token = acquire(callName, identifier, allowConcurrent)
            try {
                currentCoroutineContext().ensureActive()
                block()
            } finally {
                release(token)
            }
        }

    /**
     * Applies the same single-flight guard to a synchronous callback or helper that cannot be suspendable.
     *
     * @param callName Logical name of the API being invoked for diagnostics and error reporting.
     * @param identifier Optional target device identifier included in the thrown exception message.
     * @param allowConcurrent True when the API intentionally permits overlapping read/observe calls.
     * @param block The synchronous work to run while the lock is held.
     * @return The result produced by [block].
     * @throws PolarSdkConcurrentApiCallException if another guarded call is already active.
     */
    fun <T> guard(
        callName: String,
        identifier: String? = null,
        allowConcurrent: Boolean = false,
        block: () -> T
    ): T {
        val token = acquire(callName, identifier, allowConcurrent)
        return try {
            block()
        } finally {
            release(token)
        }
    }

    /**
     * Acquires the lock for the next guarded call.
     *
     * @param callName Logical name of the API being invoked.
     * @param identifier Optional device identifier used to improve diagnostics.
     * @param allowConcurrent True when the API intentionally permits overlapping read/observe traffic.
     * @return A token representing the lock for the identifier, or null when the call intentionally bypasses the lock.
     * @throws PolarSdkConcurrentApiCallException if a different guarded call is already active.
     */
    private fun acquire(callName: String, identifier: String?, allowConcurrent: Boolean): ActiveCall? {
        if (allowConcurrent) {
            return null
        }

        val next = ActiveCall(callName = callName, identifier = identifier)
        val current = activeCalls.putIfAbsent(keyOf(identifier), next)
        if (current != null) {
            BleLogger.w(TAG, "Rejected $callName: ${current.description()} is still in progress")
            throw PolarSdkConcurrentApiCallException(
                "Cannot start $callName while ${current.description()} is still in progress"
            )
        }
        return next
    }

    /**
     * Releases the lock associated with an active guarded call.
     *
     * @param token The acquired lock token to release.
     */
    private fun release(token: ActiveCall?) {
        token ?: return
        activeCalls.remove(keyOf(token.identifier), token)
    }

    private fun keyOf(identifier: String?): String = identifier ?: GLOBAL_KEY

    /**
     * Describes the currently active guarded call for logging and exception messages.
     * Intentionally not a data class so that release matches by reference.
     */
    private class ActiveCall(
        val callName: String,
        val identifier: String?
    ) {
        /**
         * Formats the active call into a human-readable description.
         */
        fun description(): String = identifier?.let { "$callName($it)" } ?: callName
    }

    private companion object {
        const val TAG = "PolarApiCallGuard"
        const val GLOBAL_KEY = "<global>"
    }
}
