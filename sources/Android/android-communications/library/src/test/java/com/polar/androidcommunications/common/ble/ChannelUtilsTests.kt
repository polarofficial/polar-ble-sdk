package com.polar.androidcommunications.common.ble

import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelUtilsTests {

    @Test
    fun postDisconnectedAndClearList_postsBleDisconnectedAndClearsList() = runTest {
        val observers = AtomicSet<Channel<Int>>()

        val job = launch {
            ChannelUtils.monitorNotifications(observers, mockk(relaxed = true), false).collect { }
        }
        testScheduler.advanceUntilIdle()
        assertEquals(1, observers.size())

        ChannelUtils.postDisconnectedAndClearList(observers)
        testScheduler.advanceUntilIdle()

        // observers list is cleared immediately
        assertEquals(0, observers.size())
        job.cancel()
    }

    @Test
    fun postExceptionAndClearList_postsGivenThrowableAndClearsList() = runTest {
        val observers = AtomicSet<Channel<Int>>()
        val error = IllegalStateException("boom")

        val job = launch {
            ChannelUtils.monitorNotifications(observers, mockk(relaxed = true), false).collect { }
        }
        testScheduler.advanceUntilIdle()

        ChannelUtils.postExceptionAndClearList(observers, error)
        testScheduler.advanceUntilIdle()

        // observers list is cleared immediately
        assertEquals(0, observers.size())
        job.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun postError_closesChannelWithError() {
        val channel = Channel<Int>()
        val observers = AtomicSet<Channel<Int>>()
        observers.add(channel)

        ChannelUtils.postError(observers, IllegalArgumentException("x"))

        assertTrue(channel.isClosedForSend)
    }

    @Test
    fun emitNext_emitsToAllItemsInSet() {
        val observers = AtomicSet<Int>()
        observers.add(1)
        observers.add(2)
        observers.add(3)

        var sum = 0
        ChannelUtils.emitNext(observers) { sum += it }

        assertEquals(6, sum)
    }

    @Test
    fun complete_completesFlowAndClearsList() = runTest {
        val observers = AtomicSet<Channel<Int>>()
        val values = mutableListOf<Int>()
        var completed = false

        val job = launch {
            ChannelUtils.monitorNotifications(observers, mockk(relaxed = true), false)
                .collect { values.add(it) }
            completed = true
        }
        testScheduler.advanceUntilIdle()
        assertEquals(1, observers.size())

        ChannelUtils.complete(observers)
        testScheduler.advanceUntilIdle()
        job.join()

        assertTrue(completed)
        assertEquals(0, observers.size())
    }

    @Test
    fun complete_withEmptySet_doesNotThrow() {
        val observers = AtomicSet<Channel<Int>>()
        ChannelUtils.complete(observers)
        assertEquals(0, observers.size())
    }

    @Test
    fun monitorNotifications_whenConnected_addsObserverAndRemovesOnCancel() = runTest {
        val observers = AtomicSet<Channel<Int>>()
        val transport = mockk<BleGattTxInterface>()
        every { transport.isConnected() } returns true

        val job = launch {
            ChannelUtils.monitorNotifications(observers, transport, true).collect { }
        }
        testScheduler.advanceUntilIdle()
        assertEquals(1, observers.size())

        job.cancel()
        testScheduler.advanceUntilIdle()
        assertEquals(0, observers.size())
    }

    @Test
    fun monitorNotifications_whenDisconnectedAndCheckEnabled_emitsBleDisconnected() = runTest {
        val observers = AtomicSet<Channel<Int>>()
        val transport = mockk<BleGattTxInterface>()
        every { transport.isConnected() } returns false

        var caughtError: Throwable? = null
        val job = launch {
            try { ChannelUtils.monitorNotifications(observers, transport, true).collect { } }
            catch (e: Throwable) { caughtError = e }
        }
        testScheduler.advanceUntilIdle()
        job.join()

        assertTrue(caughtError is BleDisconnected)
        assertEquals(0, observers.size())
    }

    @Test
    fun monitorNotifications_whenCheckDisabled_doesNotCallIsConnected() = runTest {
        val observers = AtomicSet<Channel<Int>>()
        val transport = mockk<BleGattTxInterface>(relaxed = true)

        val job = launch {
            ChannelUtils.monitorNotifications(observers, transport, false).collect { }
        }
        testScheduler.advanceUntilIdle()

        assertEquals(1, observers.size())
        verify(exactly = 0) { transport.isConnected() }

        job.cancel()
        testScheduler.advanceUntilIdle()
        assertEquals(0, observers.size())
    }

    @Test
    fun updateView_doesNothingAndDoesNotThrow() {
        ChannelUtils.updateView("value")
    }
}

