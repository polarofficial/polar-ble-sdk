package com.polar.androidcommunications.api.ble.model.gatt

import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.*

private val SOME_BLE_SERVICE_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-aabbccddeeff")

class BleGattBaseTest {

    @MockK
    lateinit var mockGattTxInterface: BleGattTxInterface

    class ConcreteImplBleGattBase(txInterface: BleGattTxInterface?) :
        BleGattBase(txInterface, SOME_BLE_SERVICE_UUID) {
        override fun processServiceData(
            characteristic: UUID?,
            data: ByteArray?,
            status: Int,
            notifying: Boolean
        ) {
        }

        override fun processServiceDataWritten(characteristic: UUID?, status: Int) {}
    }

    private lateinit var bleGattBase: ConcreteImplBleGattBase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        bleGattBase = ConcreteImplBleGattBase(mockGattTxInterface)
    }

    @Test
    fun `add notification characteristic`() {
        //Arrange
        val someBleCharacteristicsUUID: UUID =
            UUID.fromString("ffffffff-1234-1234-1234-1234567890ab")

        // Act
        bleGattBase.addCharacteristicNotification(someBleCharacteristicsUUID)

        //Assert
        assertTrue(bleGattBase.containsNotifyCharacteristic(someBleCharacteristicsUUID))
        assertEquals(-1, bleGattBase.getNotificationAtomicInteger(someBleCharacteristicsUUID).get())

        assertTrue(bleGattBase.containsCharacteristic(someBleCharacteristicsUUID))

        assertFalse(bleGattBase.containsCharacteristicRead(someBleCharacteristicsUUID))
    }

    @Test
    fun `remove notification characteristic`() {
        //Arrange
        val someBleCharacteristicsToRemoveUUID: UUID =
            UUID.fromString("ffffffff-1234-1234-1234-1234567890ab")
        val someBleCharacteristicToKeepUUID: UUID =
            UUID.fromString("eeeeeeee-1234-1234-1234-1234567890ab")
        val someBleCharacteristicToRemoveWhichNotFoundUUID: UUID =
            UUID.fromString("bbbbbbbb-1234-1234-1234-1234567890ab")

        // Act
        bleGattBase.addCharacteristicNotification(someBleCharacteristicToKeepUUID)
        bleGattBase.addCharacteristicNotification(someBleCharacteristicsToRemoveUUID)
        bleGattBase.removeCharacteristicNotification(someBleCharacteristicsToRemoveUUID)
        bleGattBase.removeCharacteristicNotification(someBleCharacteristicToRemoveWhichNotFoundUUID)

        //Assert
        assertFalse(bleGattBase.containsNotifyCharacteristic(someBleCharacteristicsToRemoveUUID))
        assertFalse(bleGattBase.containsCharacteristic(someBleCharacteristicsToRemoveUUID))
        assertFalse(bleGattBase.containsCharacteristicRead(someBleCharacteristicsToRemoveUUID))

        assertTrue(bleGattBase.containsNotifyCharacteristic(someBleCharacteristicToKeepUUID))
        assertTrue(bleGattBase.containsCharacteristic(someBleCharacteristicToKeepUUID))
    }
}