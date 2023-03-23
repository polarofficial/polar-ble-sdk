package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

import com.polar.androidcommunications.api.ble.exceptions.BleControlPointResponseError
import com.polar.androidcommunications.testrules.BleLoggerTestRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

internal class PmdOfflineTriggerTest {
    @Rule
    @JvmField
    val bleLoggerTestRule = BleLoggerTestRule()

    @Test
    fun `test triggers are disabled`() {
        //Arrange
        val pmdGetTriggerStatusResponse = byteArrayOf(
            // index 0 : trigger mode
            0x00.toByte(),
            // index 1.. : trigger status, measurement type, settings length (optional), settings (optional)
            0x00.toByte(), 0x02.toByte(),
            0x00.toByte(), 0x05.toByte(),
            0x00.toByte(), 0x06.toByte(),
            0x00.toByte(), 0x01.toByte(),
            0x00.toByte(), 0x03.toByte()
        )

        //Act
        val pmdOfflineTrigger = PmdOfflineTrigger.parseFromResponse(pmdGetTriggerStatusResponse)

        //Assert
        assertEquals(PmdOfflineRecTriggerMode.TRIGGER_DISABLE, pmdOfflineTrigger.triggerMode)
        assertTrue(pmdOfflineTrigger.triggers.keys.contains(PmdMeasurementType.PPI))
        assertTrue(pmdOfflineTrigger.triggers.keys.contains(PmdMeasurementType.ACC))
        assertTrue(pmdOfflineTrigger.triggers.keys.contains(PmdMeasurementType.PPG))
        assertTrue(pmdOfflineTrigger.triggers.keys.contains(PmdMeasurementType.GYRO))
        assertTrue(pmdOfflineTrigger.triggers.keys.contains(PmdMeasurementType.MAGNETOMETER))
        assertTrue(pmdOfflineTrigger.triggers.keys.size == 5)
        pmdOfflineTrigger.triggers.values.map {
            assertTrue(it.first == PmdOfflineRecTriggerStatus.TRIGGER_DISABLED)
            assertTrue(it.second == null)
        }
    }

    @Test
    fun `test trigger PPI enabled at system start`() {
        //Arrange
        val pmdGetTriggerStatusResponse = byteArrayOf(
            // index 0 : trigger mode
            0x01.toByte(),
            // index 1.. : trigger status, measurement type, settings length (optional), settings (optional)
            0x00.toByte(), 0x02.toByte(),
            0x00.toByte(), 0x05.toByte(),
            0x00.toByte(), 0x06.toByte(),
            0x00.toByte(), 0x01.toByte(),
            0x01.toByte(), 0x03.toByte(), 0x00.toByte()
        )

        //Act
        val pmdOfflineTrigger = PmdOfflineTrigger.parseFromResponse(pmdGetTriggerStatusResponse)

        //Assert
        assertEquals(PmdOfflineRecTriggerMode.TRIGGER_SYSTEM_START, pmdOfflineTrigger.triggerMode)
        assertTrue(pmdOfflineTrigger.triggers.keys.contains(PmdMeasurementType.PPI))
        assertTrue(pmdOfflineTrigger.triggers.keys.contains(PmdMeasurementType.ACC))
        assertTrue(pmdOfflineTrigger.triggers.keys.contains(PmdMeasurementType.PPG))
        assertTrue(pmdOfflineTrigger.triggers.keys.contains(PmdMeasurementType.GYRO))
        assertTrue(pmdOfflineTrigger.triggers.keys.contains(PmdMeasurementType.MAGNETOMETER))
        assertTrue(pmdOfflineTrigger.triggers.keys.size == 5)
        pmdOfflineTrigger.triggers.map {
            if (it.key == PmdMeasurementType.PPI) {
                assertTrue(it.value.first == PmdOfflineRecTriggerStatus.TRIGGER_ENABLED)
                assertTrue(it.value.second == null)
            } else {
                assertTrue(it.value.first == PmdOfflineRecTriggerStatus.TRIGGER_DISABLED)
                assertTrue(it.value.second == null)
            }
        }
    }

    @Test
    fun `test trigger PPI, ACC, GYRO, MAG, PPG enabled at exercise start`() {
        //Arrange
        val pmdGetTriggerStatusResponse = byteArrayOf(
            // index 0 : trigger mode
            0x02.toByte(),
            // index 1.. : trigger status, measurement type, settings length (optional), settings (optional)
            0x01.toByte(), 0x02.toByte(), 0x0f.toByte(), 0x00.toByte(), 0x01.toByte(), 0x34.toByte(), 0x00.toByte(), 0x01.toByte(), 0x01.toByte(), 0x10.toByte(), 0x00.toByte(), 0x02.toByte(), 0x01.toByte(), 0x08.toByte(), 0x00.toByte(), 0x04.toByte(), 0x01.toByte(), 0x03.toByte(),
            0x01.toByte(), 0x05.toByte(), 0x0f.toByte(), 0x00.toByte(), 0x01.toByte(), 0x34.toByte(), 0x00.toByte(), 0x01.toByte(), 0x01.toByte(), 0x10.toByte(), 0x00.toByte(), 0x02.toByte(), 0x01.toByte(), 0xd0.toByte(), 0x07.toByte(), 0x04.toByte(), 0x01.toByte(), 0x03.toByte(),
            0x01.toByte(), 0x06.toByte(), 0x0f.toByte(), 0x00.toByte(), 0x01.toByte(), 0x64.toByte(), 0x00.toByte(), 0x01.toByte(), 0x01.toByte(), 0x10.toByte(), 0x00.toByte(), 0x02.toByte(), 0x01.toByte(), 0x32.toByte(), 0x00.toByte(), 0x04.toByte(), 0x01.toByte(), 0x03.toByte(),
            0x01.toByte(), 0x01.toByte(), 0x0f.toByte(), 0x00.toByte(), 0x01.toByte(), 0x87.toByte(), 0x00.toByte(), 0x01.toByte(), 0x01.toByte(), 0x16.toByte(), 0x00.toByte(), 0x02.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x04.toByte(), 0x01.toByte(), 0x04.toByte(),
            0x01.toByte(), 0x03.toByte(), 0x00.toByte()
        )
        //Act
        val pmdOfflineTrigger = PmdOfflineTrigger.parseFromResponse(pmdGetTriggerStatusResponse)

        //Assert
        assertEquals(PmdOfflineRecTriggerMode.TRIGGER_EXERCISE_START, pmdOfflineTrigger.triggerMode)
        assertTrue(pmdOfflineTrigger.triggers.keys.contains(PmdMeasurementType.PPI))
        assertTrue(pmdOfflineTrigger.triggers.keys.contains(PmdMeasurementType.ACC))
        assertTrue(pmdOfflineTrigger.triggers.keys.contains(PmdMeasurementType.PPG))
        assertTrue(pmdOfflineTrigger.triggers.keys.contains(PmdMeasurementType.GYRO))
        assertTrue(pmdOfflineTrigger.triggers.keys.contains(PmdMeasurementType.MAGNETOMETER))
        assertTrue(pmdOfflineTrigger.triggers.keys.size == 5)
        pmdOfflineTrigger.triggers.map {
            if (it.key == PmdMeasurementType.PPI) {
                assertTrue(it.value.first == PmdOfflineRecTriggerStatus.TRIGGER_ENABLED)
                assertTrue(it.value.second == null)
            } else {
                assertTrue(it.value.first == PmdOfflineRecTriggerStatus.TRIGGER_ENABLED)
                assertTrue(it.value.second != null)
            }
        }
    }

    @Test
    fun `test trigger PMD response has wrong status`() {
        //Arrange
        val pmdGetTriggerStatusResponse = byteArrayOf(
            // index 0 : trigger mode
            0x01.toByte(),
            // index 1.. : trigger status, measurement type, settings length (optional), settings (optional)
            0x03.toByte(), 0x01.toByte()
        )
        //Act & Assert
        assertThrows(BleControlPointResponseError::class.java) {
            PmdOfflineTrigger.parseFromResponse(pmdGetTriggerStatusResponse)
        }
    }
}
