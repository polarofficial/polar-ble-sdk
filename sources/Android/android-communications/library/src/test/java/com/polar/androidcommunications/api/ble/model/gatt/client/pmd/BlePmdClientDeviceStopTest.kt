package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.errors.BleOnlineStreamClosed
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BlePmdClientDeviceStopTest {
    private val transport = mockk<BleGattTxInterface>(relaxed = true)
    private val client = BlePMDClient(transport)

    private fun streams(): Map<PmdMeasurementType, Flow<*>> = mapOf(
        PmdMeasurementType.ECG to client.monitorEcgNotifications(false),
        PmdMeasurementType.PPG to client.monitorPpgNotifications(false),
        PmdMeasurementType.ACC to client.monitorAccNotifications(false),
        PmdMeasurementType.PPI to client.monitorPpiNotifications(false),
        PmdMeasurementType.GYRO to client.monitorGyroNotifications(false),
        PmdMeasurementType.MAGNETOMETER to client.monitorMagnetometerNotifications(false),
        PmdMeasurementType.LOCATION to client.monitorLocationNotifications(false),
        PmdMeasurementType.PRESSURE to client.monitorPressureNotifications(false),
        PmdMeasurementType.TEMPERATURE to client.monitorTemperatureNotifications(false),
        PmdMeasurementType.SKIN_TEMP to client.monitorSkinTemperatureNotifications(false)
    )

    private fun indicate(vararg bytes: Byte, status: Int = 0) {
        client.processServiceData(BlePMDClient.PMD_CP, bytes, status, true)
    }

    @Test
    fun `device stop terminates every listed measurement with device stop error`() = runTest {
        val errors = mutableMapOf<PmdMeasurementType, Throwable>()
        val streams = streams()
        val collectors = streams.map { (type, stream) ->
            backgroundScope.launch {
                try {
                    stream.collect {}
                } catch (error: Throwable) {
                    errors[type] = error
                }
            }
        }
        runCurrent()

        indicate(0x01, *streams.keys.map { it.numVal.toByte() }.toByteArray())
        runCurrent()

        assertTrue(collectors.all { it.isCompleted })
        assertEquals(streams.size, errors.size)
        errors.values.forEach { error ->
            assertTrue(generateSequence(error) { it.cause }.any { it is BleOnlineStreamClosed })
        }
        assertTrue(client.pmdCpResponseQueue.isEmpty())
    }

    @Test
    fun `unlisted measurements and new collectors remain active`() = runTest {
        val acc = backgroundScope.launch { client.monitorAccNotifications(false).collect {} }
        val ecg = backgroundScope.launch { client.monitorEcgNotifications(false).collect {} }
        runCurrent()

        indicate(0x01, 0x02, 0x02)
        runCurrent()
        assertTrue(acc.isCompleted)
        assertTrue(ecg.isActive)

        val restarted = backgroundScope.launch { client.monitorAccNotifications(false).collect {} }
        runCurrent()
        assertTrue(restarted.isActive)
        indicate(0x01, 0x02)
        runCurrent()
        assertTrue(restarted.isCompleted)
        assertTrue(ecg.isActive)
    }

    @Test
    fun `failed empty and unknown indications do not stop streams`() = runTest {
        val acc = backgroundScope.launch { client.monitorAccNotifications(false).collect {} }
        runCurrent()

        indicate(0x01, 0x02, status = 5)
        indicate()
        indicate(0x01)
        indicate(0x7f, 0x02)
        indicate(0x01, 0x3f)
        runCurrent()

        assertTrue(acc.isActive)
        assertTrue(client.pmdCpResponseQueue.isEmpty())
    }

    @Test
    fun `unknown measurement does not prevent handling subsequent measurement`() = runTest {
        val acc = backgroundScope.launch { client.monitorAccNotifications(false).collect {} }
        runCurrent()
        indicate(0x01, 0x3f, 0x02)
        runCurrent()
        assertTrue(acc.isCompleted)
    }

    @Test
    fun `command responses are still queued separately from device commands`() {
        val response = byteArrayOf(0xf0.toByte(), 0x01, 0x02, 0x00, 0x00)
        client.processServiceData(BlePMDClient.PMD_CP, response, 0, true)
        indicate(0x01, 0x02)

        assertEquals(1, client.pmdCpResponseQueue.size)
        assertArrayEquals(response, client.pmdCpResponseQueue.remove().first)
    }

    @Test
    fun `device stop clears only affected timestamp history`() {
        for (type in listOf(PmdMeasurementType.ACC, PmdMeasurementType.ECG)) {
            val frame = byteArrayOf(type.numVal.toByte(), 42, 0, 0, 0, 0, 0, 0, 0, 0) + ByteArray(6)
            client.processServiceData(BlePMDClient.PMD_DATA, frame, 0, true)
        }
        val frameType = PmdDataFrame.PmdDataFrameType.TYPE_0
        assertEquals(42UL, client.getPreviousFrameTimeStamp(PmdMeasurementType.ACC, frameType))

        indicate(0x01, 0x02)

        assertEquals(0UL, client.getPreviousFrameTimeStamp(PmdMeasurementType.ACC, frameType))
        assertEquals(42UL, client.getPreviousFrameTimeStamp(PmdMeasurementType.ECG, frameType))
    }
}
