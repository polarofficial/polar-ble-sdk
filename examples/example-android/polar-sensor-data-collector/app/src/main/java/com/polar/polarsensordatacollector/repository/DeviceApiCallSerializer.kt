package com.polar.polarsensordatacollector.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The SDK rejects a guarded API call when another guarded call for the same device is active.
 * This class makes such calls wait in turn for each device. Calls for different devices run in parallel.
 * Hilt provides one instance for the whole app, so all repositories share the same locks.
 *
 * The lock is not reentrant. Do not start a serialized call from inside another serialized call
 * for the same device.
 */
@Singleton
class DeviceApiCallSerializer @Inject constructor() {
    private val locks = ConcurrentHashMap<String, Mutex>()

    private fun lockFor(identifier: String): Mutex = locks.getOrPut(identifier) { Mutex() }

    /**
     * Wait at most [waitTimeoutMs] for the lock. The block itself has no time limit.
     * Throw [DeviceBusyException] if another call holds the lock for the whole wait time.
     */
    private suspend fun acquire(identifier: String, waitTimeoutMs: Long): Mutex {
        val mutex = lockFor(identifier)
        val acquired = withTimeoutOrNull(waitTimeoutMs) { mutex.lock(); true } ?: false
        if (!acquired) throw DeviceBusyException(identifier)
        return mutex
    }

    suspend fun <T> serialize(identifier: String, waitTimeoutMs: Long = DEFAULT_WAIT_TIMEOUT_MS, block: suspend () -> T): T {
        val mutex = acquire(identifier, waitTimeoutMs)
        try {
            return block()
        } finally {
            mutex.unlock()
        }
    }

    /** Hold the lock from the start of the collection until the flow completes or is cancelled. */
    fun <T> serializeFlow(identifier: String, waitTimeoutMs: Long = DEFAULT_WAIT_TIMEOUT_MS, source: () -> Flow<T>): Flow<T> = flow {
        val mutex = acquire(identifier, waitTimeoutMs)
        try {
            source().collect { emit(it) }
        } finally {
            mutex.unlock()
        }
    }

    companion object {
        const val DEFAULT_WAIT_TIMEOUT_MS = 30_000L

        /** Use this for quick status reads. A long download then does not block them. */
        const val STATUS_WAIT_TIMEOUT_MS = 1_000L
    }
}

/** Another API call for the same device holds the lock and does not finish in time. */
class DeviceBusyException(identifier: String) :
    Exception("Device $identifier is busy with another operation. Try again later.")
