package com.androidcommunications.polar.api.ble.model.gatt.client

import com.androidcommunications.polar.api.ble.exceptions.BleDisconnected
import com.androidcommunications.polar.api.ble.model.gatt.BleGattTxInterface
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import io.reactivex.rxjava3.subscribers.TestSubscriber
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.*


class BleBattClientTest {

    @MockK
    private lateinit var txInterface: BleGattTxInterface

    private lateinit var bleBattClient: BleBattClient

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        bleBattClient = BleBattClient(txInterface)
    }

    @Test
    fun test_batteryNotificationReceived() {
        //Arrange
        val characteristic: UUID = BleBattClient.BATTERY_LEVEL_CHARACTERISTIC
        val status = 0
        val notifying = true
        val expectedBatteryLevel1 = 100
        val expectedBatteryLevel2 = 90
        val expectedBatteryLevel3 = 0
        val dataFromBatteryService1 = byteArrayOf(0x64.toByte())
        val dataFromBatteryService2 = byteArrayOf(0x5A.toByte())
        val dataFromBatteryService3 = byteArrayOf(0x00.toByte())
        //Act
        val result = bleBattClient.monitorBatteryLevelUpdate(false)
        val testObserver = TestSubscriber<Int>()
        result.subscribe(testObserver)
        bleBattClient.processServiceData(characteristic, dataFromBatteryService1, status, notifying)
        bleBattClient.processServiceData(characteristic, dataFromBatteryService2, status, notifying)
        bleBattClient.processServiceData(characteristic, dataFromBatteryService3, status, notifying)

        //Assert
        testObserver.assertNoErrors()
        testObserver.assertValueCount(3)
        val batteryData1 = testObserver.values()[0]
        assertEquals(expectedBatteryLevel1, batteryData1)
        val batteryData2 = testObserver.values()[1]
        assertEquals(expectedBatteryLevel2, batteryData2)
        val batteryData3 = testObserver.values()[2]
        assertEquals(expectedBatteryLevel3, batteryData3)
    }

    @Test
    fun test_batteryNotificationError() {
        //Arrange
        val characteristic: UUID = BleBattClient.BATTERY_LEVEL_CHARACTERISTIC
        val status = 0
        val notifying = true
        val expectedBatteryLevel = 100
        val dataFromBatteryService = byteArrayOf(0x64.toByte())

        //Act
        val testObserver = TestSubscriber.create<Int>()
        val result = bleBattClient.monitorBatteryLevelUpdate(false)
                .subscribe(testObserver)
        bleBattClient.processServiceData(characteristic, dataFromBatteryService, status, notifying)
        bleBattClient.reset()

        //Assert
        testObserver.assertError(BleDisconnected::class.java)
        testObserver.assertValueCount(1)
        val batteryData = testObserver.values()[0]
        assertEquals(expectedBatteryLevel, batteryData)
    }
}