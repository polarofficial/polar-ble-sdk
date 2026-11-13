package com.polar.polarsensordatacollector.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceApiCallSerializerTest {

    private val serializer = DeviceApiCallSerializer()

    @Test
    fun serialize_returnsBlockResult_whenBlockCompletes() = runTest {
        // Arrange
        val id = "ret"

        // Act
        val result = serializer.serialize(id) { 42 }

        // Assert
        assertEquals(42, result)
    }

    @Test
    fun serialize_runsInTurn_whenSameDevice() = runTest {
        // Arrange
        val id = "same"
        val gate = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        launch {
            serializer.serialize(id) { events += "a-start"; gate.await(); events += "a-end" }
        }
        runCurrent()

        // Act
        launch { serializer.serialize(id) { events += "b" } }
        runCurrent()
        val eventsWhileBlocked = events.toList()
        gate.complete(Unit)
        runCurrent()

        // Assert
        assertEquals(listOf("a-start"), eventsWhileBlocked)
        assertEquals(listOf("a-start", "a-end", "b"), events)
    }

    @Test
    fun serialize_runsInParallel_whenDifferentDevices() = runTest {
        // Arrange
        val gate = CompletableDeferred<Unit>()
        var bRan = false
        launch { serializer.serialize("par-a") { gate.await() } }
        runCurrent()

        // Act
        launch { serializer.serialize("par-b") { bRan = true } }
        runCurrent()

        // Assert
        assertTrue(bRan)
        gate.complete(Unit)
    }

    @Test
    fun serialize_releasesLock_whenBlockThrows() = runTest {
        // Arrange
        val id = "exc"
        var thrown: IllegalStateException? = null

        // Act
        try {
            serializer.serialize(id) { throw IllegalStateException("boom") }
            fail("Expected exception")
        } catch (e: IllegalStateException) {
            thrown = e
        }
        val nextResult = serializer.serialize(id) { 1 }

        // Assert
        assertEquals("boom", thrown?.message)
        assertEquals(1, nextResult)
    }

    @Test
    fun serialize_releasesLock_whenCallerIsCancelled() = runTest {
        // Arrange
        val id = "cancel"
        val job = launch { serializer.serialize(id) { CompletableDeferred<Unit>().await() } }
        runCurrent()
        var bRan = false
        launch { serializer.serialize(id) { bRan = true } }
        runCurrent()
        val bRanWhileBlocked = bRan

        // Act
        job.cancel()
        runCurrent()

        // Assert
        assertFalse(bRanWhileBlocked)
        assertTrue(bRan)
    }

    @Test
    fun serializeFlow_holdsLock_whileCollecting() = runTest {
        // Arrange
        val id = "flow-hold"
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val items = mutableListOf<Int>()
        launch {
            serializer.serializeFlow(id) {
                calls++
                flow { emit(1); gate.await(); emit(2) }
            }.collect { items += it }
        }
        runCurrent()
        var otherRan = false

        // Act
        launch { serializer.serialize(id) { otherRan = true } }
        runCurrent()
        val otherRanWhileCollecting = otherRan
        gate.complete(Unit)
        runCurrent()

        // Assert
        assertFalse(otherRanWhileCollecting)
        assertTrue(otherRan)
        assertEquals(listOf(1, 2), items)
        assertEquals(1, calls)
    }

    @Test
    fun serializeFlow_doesNotLock_whenNotCollected() = runTest {
        // Arrange
        val id = "flow-lazy"

        // Act
        serializer.serializeFlow(id) { flowOf(1) }
        val result = serializer.serialize(id) { 1 }

        // Assert
        assertEquals(1, result)
    }

    @Test
    fun serializeFlow_releasesLock_whenCollectorIsCancelled() = runTest {
        // Arrange
        val id = "flow-cancel"
        val job = launch {
            serializer.serializeFlow(id) { flow<Int> { CompletableDeferred<Unit>().await() } }.collect {}
        }
        runCurrent()

        // Act
        job.cancel()
        runCurrent()
        val result = serializer.serialize(id) { 1 }

        // Assert
        assertEquals(1, result)
    }

    @Test
    fun serializeFlow_releasesLock_whenFlowThrows() = runTest {
        // Arrange
        val id = "flow-error"
        var thrown: IllegalStateException? = null

        // Act
        try {
            serializer.serializeFlow(id) { flow<Int> { throw IllegalStateException("boom") } }.toList()
            fail("Expected exception")
        } catch (e: IllegalStateException) {
            thrown = e
        }
        val nextResult = serializer.serialize(id) { 1 }

        // Assert
        assertEquals("boom", thrown?.message)
        assertEquals(1, nextResult)
    }

    @Test
    fun serialize_throwsBusy_whenLockIsNotFreeInTime() = runTest {
        // Arrange
        val id = "busy"
        val gate = CompletableDeferred<Unit>()
        launch { serializer.serialize(id) { gate.await() } }
        runCurrent()

        // Act
        val result = runCatching { serializer.serialize(id, waitTimeoutMs = 100) { 1 } }

        // Assert
        assertTrue(result.exceptionOrNull() is DeviceBusyException)
        gate.complete(Unit)
    }

    @Test
    fun serialize_runsBlock_whenLockFreesInTime() = runTest {
        // Arrange
        val id = "frees"
        val gate = CompletableDeferred<Unit>()
        launch { serializer.serialize(id) { gate.await() } }
        runCurrent()

        // Act
        val second = async { serializer.serialize(id, waitTimeoutMs = 1_000) { "done" } }
        advanceTimeBy(100)
        gate.complete(Unit)

        // Assert
        assertEquals("done", second.await())
    }

    @Test
    fun serialize_releasesLock_whenWaitTimesOut() = runTest {
        // Arrange
        val id = "timeout-release"
        val gate = CompletableDeferred<Unit>()
        val holder = launch { serializer.serialize(id) { gate.await() } }
        runCurrent()
        runCatching { serializer.serialize(id, waitTimeoutMs = 50) { 1 } }
        gate.complete(Unit)
        holder.join()

        // Act
        val result = serializer.serialize(id, waitTimeoutMs = 50) { 2 }

        // Assert
        assertEquals(2, result)
    }

    @Test
    fun serializeFlow_throwsBusy_whenLockIsNotFreeInTime() = runTest {
        // Arrange
        val id = "flow-busy"
        val gate = CompletableDeferred<Unit>()
        launch { serializer.serialize(id) { gate.await() } }
        runCurrent()

        // Act
        val result = runCatching { serializer.serializeFlow(id, waitTimeoutMs = 100) { flowOf(1) }.toList() }

        // Assert
        assertTrue(result.exceptionOrNull() is DeviceBusyException)
        gate.complete(Unit)
    }
}
