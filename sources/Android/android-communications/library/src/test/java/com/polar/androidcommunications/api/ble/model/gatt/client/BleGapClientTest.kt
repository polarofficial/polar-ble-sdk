package com.polar.androidcommunications.api.ble.model.gatt.client

import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.common.ble.AtomicSet
import com.polar.androidcommunications.common.ble.ChannelUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class BleGapClientTest {

    private lateinit var txInterface: BleGattTxInterface
    private lateinit var sut: BleGapClient

    @Before
    fun setUp() {
        mockkObject(ChannelUtils.Companion)
        every { ChannelUtils.postDisconnectedAndClearList(any<AtomicSet<Channel<Any>>>()) } answers {
            @Suppress("UNCHECKED_CAST")
            val list = firstArg<AtomicSet<Channel<Any>>>()
            val error = BleDisconnected()
            list.objects().forEach { it.close(error) }
            list.clear()
        }
        txInterface = mockk(relaxed = true)
        every { txInterface.isConnected() } returns true
        sut = BleGapClient(txInterface)
    }

    /**
     * Simulates BLE service discovery so that availableReadableCharacteristics is populated.
     * Without this, hasAllAvailableReadableCharacteristics always returns false (empty list guard),
     * meaning onComplete is never called.
     */
    private fun simulateServiceDiscovered() {
        sut.processCharacteristicDiscovered(BleGapClient.GAP_DEVICE_NAME_CHARACTERISTIC, BleGattBase.PROPERTY_READ)
        sut.processCharacteristicDiscovered(BleGapClient.GAP_APPEARANCE_CHARACTERISTIC, BleGattBase.PROPERTY_READ)
    }

    @Test
    fun reset_clearsGapInformationAndPostsDisconnectedToObservers() = runTest {
        var caughtError: Throwable? = null
        val job = launch {
            try { sut.observeGapInfo(true).collect { } }
            catch (e: Throwable) { caughtError = e }
        }
        testScheduler.advanceUntilIdle()

        sut.processServiceData(BleGapClient.GAP_DEVICE_NAME_CHARACTERISTIC, "Polar H10".toByteArray(), BleGattBase.ATT_SUCCESS, false)
        sut.reset()
        testScheduler.advanceUntilIdle()
        job.join()

        assertTrue(caughtError is BleDisconnected)
    }

    @Test
    fun reset_whenSubscribedAfterReset_doesNotReplayOldValues() = runTest {
        sut.processServiceData(BleGapClient.GAP_DEVICE_NAME_CHARACTERISTIC, "Polar H10".toByteArray(), BleGattBase.ATT_SUCCESS, false)
        sut.reset()

        val values = mutableListOf<HashMap<UUID, String>>()
        val job = launch { sut.observeGapInfo(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertTrue(values.isEmpty())
    }

    @Test
    fun processServiceData_whenSuccess_emitsMapWithCharacteristicEntry() = runTest {
        val values = mutableListOf<HashMap<UUID, String>>()
        val job = launch { sut.observeGapInfo(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        sut.processServiceData(BleGapClient.GAP_DEVICE_NAME_CHARACTERISTIC, "Polar H10".toByteArray(), BleGattBase.ATT_SUCCESS, false)
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(1, values.size)
        assertTrue(values[0].containsKey(BleGapClient.GAP_DEVICE_NAME_CHARACTERISTIC))
    }

    @Test
    fun processServiceData_whenBothCharacteristicsReceived_emitsAndCompletes() = runTest {
        simulateServiceDiscovered()
        val values = mutableListOf<HashMap<UUID, String>>()
        val job = launch { sut.observeGapInfo(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        sut.processServiceData(BleGapClient.GAP_DEVICE_NAME_CHARACTERISTIC, "Polar H10".toByteArray(), BleGattBase.ATT_SUCCESS, false)
        sut.processServiceData(BleGapClient.GAP_APPEARANCE_CHARACTERISTIC, byteArrayOf(0x00, 0x01), BleGattBase.ATT_SUCCESS, false)
        testScheduler.advanceUntilIdle()
        job.join() // flow completes on its own after all chars received

        assertEquals(2, values.size)
    }

    @Test
    fun processServiceData_whenStatusNotSuccess_doesNotEmit() = runTest {
        val values = mutableListOf<HashMap<UUID, String>>()
        val job = launch { sut.observeGapInfo(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        sut.processServiceData(BleGapClient.GAP_DEVICE_NAME_CHARACTERISTIC, "Polar H10".toByteArray(), BleGattBase.ATT_INVALID_HANDLE, false)
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertTrue(values.isEmpty())
    }

    @Test
    fun processServiceData_whenUnknownCharacteristic_doesNotIncludeInMap() = runTest {
        val values = mutableListOf<HashMap<UUID, String>>()
        val job = launch { sut.observeGapInfo(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        sut.processServiceData(UUID.randomUUID(), "somedata".toByteArray(), BleGattBase.ATT_SUCCESS, false)
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(1, values.size)
        assertFalse(values[0].containsKey(BleGapClient.GAP_DEVICE_NAME_CHARACTERISTIC))
        assertFalse(values[0].containsKey(BleGapClient.GAP_APPEARANCE_CHARACTERISTIC))
    }

    @Test
    fun processServiceDataWritten_doesNothingAndDoesNotThrow() {
        sut.processServiceDataWritten(BleGapClient.GAP_DEVICE_NAME_CHARACTERISTIC, BleGattBase.ATT_SUCCESS)
        assertTrue(true)
    }

    // endregion

    // region toString

    @Test
    fun toString_returnsExpectedValue() {
        assertEquals("GAP service with values device name: ", sut.toString())
    }

    @Test
    fun observeGapInfo_whenDisconnectedAndCheckEnabled_emitsBleDisconnected() = runTest {
        every { txInterface.isConnected() } returns false
        var caughtError: Throwable? = null
        val job = launch {
            try { sut.observeGapInfo(true).collect { } }
            catch (e: Throwable) { caughtError = e }
        }
        testScheduler.advanceUntilIdle()
        job.join()

        assertTrue(caughtError is BleDisconnected)
    }

    @Test
    fun observeGapInfo_whenDisconnectedButCheckDisabled_doesNotError() = runTest {
        every { txInterface.isConnected() } returns false
        val values = mutableListOf<HashMap<UUID, String>>()
        val job = launch { sut.observeGapInfo(false).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertTrue(values.isEmpty())
    }

    @Test
    fun observeGapInfo_whenExistingValuesPresent_replaysToNewSubscriber() = runTest {
        sut.processServiceData(BleGapClient.GAP_DEVICE_NAME_CHARACTERISTIC, "Polar H10".toByteArray(), BleGattBase.ATT_SUCCESS, false)

        val values = mutableListOf<HashMap<UUID, String>>()
        val job = launch { sut.observeGapInfo(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(1, values.size)
        assertTrue(values[0].containsKey(BleGapClient.GAP_DEVICE_NAME_CHARACTERISTIC))
    }

    @Test
    fun observeGapInfo_whenAllCharacteristicsAlreadyPresent_replaysAndCompletes() = runTest {
        simulateServiceDiscovered()
        sut.processServiceData(BleGapClient.GAP_DEVICE_NAME_CHARACTERISTIC, "Polar H10".toByteArray(), BleGattBase.ATT_SUCCESS, false)
        sut.processServiceData(BleGapClient.GAP_APPEARANCE_CHARACTERISTIC, byteArrayOf(0x00, 0x01), BleGattBase.ATT_SUCCESS, false)

        val values = mutableListOf<HashMap<UUID, String>>()
        val job = launch { sut.observeGapInfo(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()
        job.join() // completes naturally

        assertEquals(1, values.size)
        assertTrue(values[0].containsKey(BleGapClient.GAP_DEVICE_NAME_CHARACTERISTIC))
        assertTrue(values[0].containsKey(BleGapClient.GAP_APPEARANCE_CHARACTERISTIC))
    }

    @Test
    fun observeGapInfo_whenNoValuesPresent_doesNotReplay() = runTest {
        val values = mutableListOf<HashMap<UUID, String>>()
        val job = launch { sut.observeGapInfo(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertTrue(values.isEmpty())
    }

    @Test
    fun observeGapInfo_multipleSubscribers_eachReceiveEmittedValues() = runTest {
        val values1 = mutableListOf<HashMap<UUID, String>>()
        val values2 = mutableListOf<HashMap<UUID, String>>()
        val job1 = launch { sut.observeGapInfo(true).collect { values1.add(it) } }
        val job2 = launch { sut.observeGapInfo(true).collect { values2.add(it) } }
        testScheduler.advanceUntilIdle()

        sut.processServiceData(BleGapClient.GAP_DEVICE_NAME_CHARACTERISTIC, "Polar H10".toByteArray(), BleGattBase.ATT_SUCCESS, false)
        testScheduler.advanceUntilIdle()
        job1.cancel()
        job2.cancel()

        assertEquals(1, values1.size)
        assertEquals(1, values2.size)
    }
}
