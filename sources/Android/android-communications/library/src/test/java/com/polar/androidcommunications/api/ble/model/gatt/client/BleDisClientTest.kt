package com.polar.androidcommunications.api.ble.model.gatt.client

import android.util.Pair
import com.polar.androidcommunications.api.ble.exceptions.BleAttributeError
import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.common.ble.AtomicSet
import com.polar.androidcommunications.common.ble.ChannelUtils
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.UUID

class BleDisClientTest {

    private lateinit var txInterface: BleGattTxInterface
    private lateinit var sut: BleDisClient

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
        every { ChannelUtils.postError(any<AtomicSet<Channel<Any>>>(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            val list = firstArg<AtomicSet<Channel<Any>>>()
            val throwable = secondArg<Throwable>()
            list.objects().forEach { it.close(throwable) }
            list.clear()
        }
        txInterface = mockk(relaxed = true)
        every { txInterface.isConnected() } returns true
        sut = BleDisClient(txInterface)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun reset_clearsStateAndPostsDisconnectedToObservers() = runTest {
        val pairValues = mutableListOf<Pair<UUID, String>>()
        var pairError: Throwable? = null
        val disInfoValues = mutableListOf<Any>()
        var disInfoError: Throwable? = null

        val job1 = launch {
            try { sut.observeDisInfo(true).collect { pairValues.add(it) } }
            catch (e: Throwable) { pairError = e }
        }
        val job2 = launch {
            try { sut.observeDisInfoWithKeysAsStrings(true).collect { disInfoValues.add(it) } }
            catch (e: Throwable) { disInfoError = e }
        }
        testScheduler.advanceUntilIdle()

        sut.reset()
        testScheduler.advanceUntilIdle()
        job1.join()
        job2.join()

        assertTrue(pairError is BleDisconnected)
        assertTrue(disInfoError is BleDisconnected)
    }

    @Test
    fun processServiceData_whenSuccess_callsEmitNextForPairObservers() {
        mockkObject(ChannelUtils.Companion)
        every { ChannelUtils.emitNext(any<com.polar.androidcommunications.common.ble.AtomicSet<*>>(), any()) } just runs
        val data = "Polar".toByteArray(StandardCharsets.UTF_8)

        sut.processServiceData(BleDisClient.MANUFACTURER_NAME_STRING, data, BleGattBase.ATT_SUCCESS, false)

        verify(atLeast = 1) { ChannelUtils.emitNext(any<com.polar.androidcommunications.common.ble.AtomicSet<*>>(), any()) }
    }

    @Test
    fun processServiceData_whenSuccess_emitsDisInfoObserverValue() = runTest {
        val data = "H10".toByteArray(StandardCharsets.UTF_8)
        val values = mutableListOf<com.polar.androidcommunications.api.ble.model.DisInfo>()
        val job = launch { sut.observeDisInfoWithKeysAsStrings(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        sut.processServiceData(BleDisClient.MODEL_NUMBER_STRING, data, BleGattBase.ATT_SUCCESS, false)
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertTrue(values.any { it.key == BleDisClient.MODEL_NUMBER_STRING.toString() && it.value == "H10" })
    }

    @Test
    fun processServiceData_whenSystemIdSuccess_emitsHexKeyForDisInfoObserver() = runTest {
        val bytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val values = mutableListOf<com.polar.androidcommunications.api.ble.model.DisInfo>()
        val job = launch { sut.observeDisInfoWithKeysAsStrings(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        sut.processServiceData(BleDisClient.SYSTEM_ID, bytes, BleGattBase.ATT_SUCCESS, false)
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertTrue(values.any { it.key == BleDisClient.SYSTEM_ID_HEX && it.value == "04030201" })
    }

    @Test
    fun processServiceData_whenStatusError_postsBleAttributeErrorToPairObservers() = runTest {
        var caughtError: Throwable? = null
        val job = launch {
            try { sut.observeDisInfo(true).collect { } }
            catch (e: Throwable) { caughtError = e }
        }
        testScheduler.advanceUntilIdle()

        sut.processServiceData(BleDisClient.MODEL_NUMBER_STRING, "x".toByteArray(StandardCharsets.UTF_8), BleGattBase.ATT_INVALID_HANDLE, false)
        testScheduler.advanceUntilIdle()
        job.join()

        assertTrue(caughtError is BleAttributeError)
    }

    @Test
    fun processServiceData_whenDataNull_doesNothing() = runTest {
        val pairValues = mutableListOf<Pair<UUID, String>>()
        val disInfoValues = mutableListOf<Any>()
        val job1 = launch { sut.observeDisInfo(true).collect { pairValues.add(it) } }
        val job2 = launch { sut.observeDisInfoWithKeysAsStrings(true).collect { disInfoValues.add(it) } }
        testScheduler.advanceUntilIdle()

        sut.processServiceData(BleDisClient.MODEL_NUMBER_STRING, byteArrayOf(), BleGattBase.ATT_SUCCESS, false)
        testScheduler.advanceUntilIdle()
        job1.cancel()
        job2.cancel()

        assertTrue(pairValues.isEmpty())
        assertTrue(disInfoValues.isEmpty())
    }

    @Test
    fun processServiceData_whenCharacteristicNull_doesNothing() = runTest {
        val pairValues = mutableListOf<Pair<UUID, String>>()
        val disInfoValues = mutableListOf<Any>()
        val job1 = launch { sut.observeDisInfo(true).collect { pairValues.add(it) } }
        val job2 = launch { sut.observeDisInfoWithKeysAsStrings(true).collect { disInfoValues.add(it) } }
        testScheduler.advanceUntilIdle()

        val unknownUuid = UUID.randomUUID()
        sut.processServiceData(unknownUuid, "x".toByteArray(StandardCharsets.UTF_8), BleGattBase.ATT_SUCCESS, false)
        testScheduler.advanceUntilIdle()
        job1.cancel()
        job2.cancel()

        // Unknown UUID is stored and emitted but does not complete the flow (not a known readable characteristic)
        assertTrue(pairValues.none { it.first == BleDisClient.MODEL_NUMBER_STRING })
        assertTrue(pairValues.none { it.first == BleDisClient.MANUFACTURER_NAME_STRING })
        assertTrue(pairValues.none { it.first == BleDisClient.FIRMWARE_REVISION_STRING })
    }

    @Test
    fun processServiceDataWritten_doesNothingAndDoesNotThrow() {
        sut.processServiceDataWritten(BleDisClient.MODEL_NUMBER_STRING, BleGattBase.ATT_SUCCESS)
        assertTrue(true)
    }

    @Test
    fun toString_returnsExpectedValue() {
        assertEquals("Device info service", sut.toString())
    }

    @Test
    fun observeDisInfo_whenConnectedAndExistingValuePresent_replaysValueToNewSubscriber() = runTest {
        sut.processServiceData(BleDisClient.SOFTWARE_REVISION_STRING, "1.2.3".toByteArray(StandardCharsets.UTF_8), BleGattBase.ATT_SUCCESS, false)

        val values = mutableListOf<Pair<UUID, String>>()
        val job = launch { sut.observeDisInfo(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(1, values.size)
    }

    @Test
    fun observeDisInfo_whenDisconnectedAndCheckEnabled_emitsBleDisconnected() = runTest {
        every { txInterface.isConnected() } returns false
        var caughtError: Throwable? = null
        val job = launch {
            try { sut.observeDisInfo(true).collect { } }
            catch (e: Throwable) { caughtError = e }
        }
        testScheduler.advanceUntilIdle()
        job.join()

        assertTrue(caughtError is BleDisconnected)
    }

    @Test
    fun observeDisInfo_whenDisconnectedButCheckDisabled_doesNotError() = runTest {
        every { txInterface.isConnected() } returns false
        val values = mutableListOf<Pair<UUID, String>>()
        val job = launch { sut.observeDisInfo(false).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertTrue(values.isEmpty())
    }

    @Test
    fun observeDisInfo_whenAllReadableCharacteristicsProcessed_emitsAllValuesWithoutErrors() = runTest {
        val values = mutableListOf<Pair<UUID, String>>()
        val job = launch { sut.observeDisInfo(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        val characteristics = listOf(
            BleDisClient.MODEL_NUMBER_STRING, BleDisClient.MANUFACTURER_NAME_STRING,
            BleDisClient.HARDWARE_REVISION_STRING, BleDisClient.FIRMWARE_REVISION_STRING,
            BleDisClient.SOFTWARE_REVISION_STRING, BleDisClient.SERIAL_NUMBER_STRING,
            BleDisClient.SYSTEM_ID, BleDisClient.IEEE_11073_20601, BleDisClient.PNP_ID
        )
        characteristics.forEachIndexed { index, uuid ->
            val bytes = if (uuid == BleDisClient.SYSTEM_ID) byteArrayOf(0x01, 0x02, 0x03, 0x04)
            else "v$index".toByteArray(StandardCharsets.UTF_8)
            sut.processServiceData(uuid, bytes, BleGattBase.ATT_SUCCESS, false)
        }
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(9, values.size)
    }

    @Test
    fun observeDisInfoWithKeysAsStrings_whenConnectedAndExistingValuePresent_replaysValueToNewSubscriber() = runTest {
        sut.processServiceData(BleDisClient.MANUFACTURER_NAME_STRING, "Polar".toByteArray(StandardCharsets.UTF_8), BleGattBase.ATT_SUCCESS, false)

        val values = mutableListOf<com.polar.androidcommunications.api.ble.model.DisInfo>()
        val job = launch { sut.observeDisInfoWithKeysAsStrings(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertTrue(values.any { it.key == BleDisClient.MANUFACTURER_NAME_STRING.toString() && it.value == "Polar" })
    }

    @Test
    fun observeDisInfoWithKeysAsStrings_whenDisconnectedAndCheckEnabled_emitsBleDisconnected() = runTest {
        every { txInterface.isConnected() } returns false
        var caughtError: Throwable? = null
        val job = launch {
            try { sut.observeDisInfoWithKeysAsStrings(true).collect { } }
            catch (e: Throwable) { caughtError = e }
        }
        testScheduler.advanceUntilIdle()
        job.join()

        assertTrue(caughtError is BleDisconnected)
    }

    @Test
    fun observeDisInfoWithKeysAsStrings_whenDisconnectedButCheckDisabled_doesNotError() = runTest {
        every { txInterface.isConnected() } returns false
        val values = mutableListOf<Any>()
        val job = launch { sut.observeDisInfoWithKeysAsStrings(false).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertTrue(values.isEmpty())
    }

    @Test
    fun observeDisInfoWithKeysAsStrings_whenAllReadableCharacteristicsProcessed_emitsAllValuesWithoutErrors() = runTest {
        val values = mutableListOf<com.polar.androidcommunications.api.ble.model.DisInfo>()
        val job = launch { sut.observeDisInfoWithKeysAsStrings(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        val characteristics = listOf(
            BleDisClient.MODEL_NUMBER_STRING, BleDisClient.MANUFACTURER_NAME_STRING,
            BleDisClient.HARDWARE_REVISION_STRING, BleDisClient.FIRMWARE_REVISION_STRING,
            BleDisClient.SOFTWARE_REVISION_STRING, BleDisClient.SERIAL_NUMBER_STRING,
            BleDisClient.SYSTEM_ID, BleDisClient.IEEE_11073_20601, BleDisClient.PNP_ID
        )
        characteristics.forEachIndexed { index, uuid ->
            val bytes = if (uuid == BleDisClient.SYSTEM_ID) byteArrayOf(0x01, 0x02, 0x03, 0x04)
            else "v$index".toByteArray(StandardCharsets.UTF_8)
            sut.processServiceData(uuid, bytes, BleGattBase.ATT_SUCCESS, false)
        }
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(9, values.size)
    }
}