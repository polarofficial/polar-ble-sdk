package com.polar.sdk.impl

import com.polar.sdk.api.errors.PolarSdkConcurrentApiCallException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PolarApiCallGuardTest {

    @Test
    fun `guardSuspend releases the lock after success`() = runTest {
        val guard = PolarApiCallGuard()

        assertEquals("first", guard.guardSuspend("read", "A1") { "first" })
        assertEquals("second", guard.guardSuspend("read", "A2") { "second" })
    }

    @Test
    fun `guardSuspend releases the lock after failure`() = runTest {
        val guard = PolarApiCallGuard()

        val exception = try {
            guard.guardSuspend("read", "A1") { throw RuntimeException("boom") }
            fail("Expected the guarded suspend call to fail")
            throw AssertionError("Unreachable")
        } catch (e: RuntimeException) {
            e
        }

        assertEquals("boom", exception.message)
        assertEquals("recovered", guard.guardSuspend("read", "A2") { "recovered" })
    }

    @Test
    fun `guardSuspend rejects overlapping guarded calls`() = runTest {
        val guard = PolarApiCallGuard()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val first = async {
            guard.guardSuspend("first", "device-1") {
                started.complete(Unit)
                release.await()
                "first-result"
            }
        }

        started.await()

        val exception = try {
            guard.guardSuspend("second", "device-1") { "second-result" }
            fail("Expected the second guarded call to fail")
            throw AssertionError("Unreachable")
        } catch (e: PolarSdkConcurrentApiCallException) {
            e
        }

        assertTrue(exception.message!!.contains("Cannot start second"))
        assertTrue(exception.message!!.contains("first(device-1)"))

        release.complete(Unit)
        assertEquals("first-result", first.await())
    }

    @Test
    fun `guardFlow and guard preserve single-flight locking`() = runBlocking {
        val guard = PolarApiCallGuard()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val first = async(Dispatchers.Default) {
            guard.guardFlow("first", "device-1") {
                started.complete(Unit)
                emit("one")
                release.await()
                emit("two")
            }.toList()
        }

        started.await()

        val exception = assertThrows(PolarSdkConcurrentApiCallException::class.java) {
            guard.guard("second", "device-1") { "second-result" }
        }

        assertTrue(exception.message!!.contains("Cannot start second"))
        assertTrue(exception.message!!.contains("first(device-1)"))

        release.complete(Unit)
        assertEquals(listOf("one", "two"), first.await())
    }

    @Test
    fun `calls for different devices may overlap`() = runTest {
        val guard = PolarApiCallGuard()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val first = async {
            guard.guardSuspend("first", "device-1") {
                started.complete(Unit)
                release.await()
                "first-result"
            }
        }

        started.await()

        assertEquals("second-result", guard.guardSuspend("second", "device-2") { "second-result" })

        release.complete(Unit)
        assertEquals("first-result", first.await())
    }

    @Test
    fun `concurrent flow does not hold the exclusive lock`() = runTest {
        val guard = PolarApiCallGuard()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val observer = async {
            guard.guardFlow("observe", "device-1", allowConcurrent = true) {
                started.complete(Unit)
                release.await()
                emit("done")
            }.toList()
        }

        started.await()

        assertEquals("ok", guard.guardSuspend("read", "device-1") { "ok" })

        release.complete(Unit)
        assertEquals(listOf("done"), observer.await())
    }

    @Test
    fun `guard supports explicit opt-out for intentionally concurrent reads`() = runTest {
        val guard = PolarApiCallGuard()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val first = async {
            guard.guardSuspend("read", "A1", allowConcurrent = true) {
                started.complete(Unit)
                release.await()
                "first-result"
            }
        }

        started.await()

        assertEquals("second-result", guard.guardSuspend("read", "A2", allowConcurrent = true) {
            "second-result"
        })

        release.complete(Unit)
        assertEquals("first-result", first.await())
    }
}
