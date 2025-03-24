package com.polar.androidcommunications.api.ble.model.gatt.client

import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import io.mockk.MockKAnnotations
import io.mockk.every
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
        every { txInterface.isConnected } returns true
        bleBattClient = BleBattClient(txInterface)
    }

    // GIVEN that BLE Battery Service client receives battery data updates
    // WHEN battery status observable is subscribed
    // THEN the latest cached battery value is emitted
    @Test
    fun cachedValue() {
        //Arrange
        val characteristic: UUID = BleBattClient.BATTERY_LEVEL_CHARACTERISTIC
        val status = 0
        val notifying = true
        val deviceNotifyingBatteryData = intArrayOf(100, 80)

        //Act
        bleBattClient.processServiceData(
            characteristic,
            byteArrayOf(deviceNotifyingBatteryData[0].toByte()),
            status,
            notifying
        )
        bleBattClient.processServiceData(
            characteristic,
            byteArrayOf(deviceNotifyingBatteryData[1].toByte()),
            status,
            notifying
        )

        val testObserver = TestSubscriber<Int>()
        bleBattClient.monitorBatteryStatus(true)
            .subscribe(testObserver)

        //Assert
        testObserver.assertNoErrors()
        testObserver.assertValueCount(1)
        assertEquals(deviceNotifyingBatteryData[1], testObserver.values()[0])
    }

    // GIVEN that BLE Battery Service client receives battery data updates
    // WHEN battery status observable is subscribed
    // THEN the correct values are received by observer
    @Test
    fun batteryNotificationReceived() {
        //Arrange
        val characteristic: UUID = BleBattClient.BATTERY_LEVEL_CHARACTERISTIC
        val status = 0
        val notifying = true
        val deviceNotifyingBatteryData = intArrayOf(100, 80, 255, 0, -1)

        //Act
        val testObserver = TestSubscriber<Int>()
        bleBattClient.monitorBatteryStatus(true)
            .subscribe(testObserver)
        bleBattClient.processServiceData(
            characteristic,
            byteArrayOf(deviceNotifyingBatteryData[0].toByte()),
            status,
            notifying
        )
        bleBattClient.processServiceData(
            characteristic,
            byteArrayOf(deviceNotifyingBatteryData[1].toByte()),
            status,
            notifying
        )
        bleBattClient.processServiceData(
            characteristic,
            byteArrayOf(deviceNotifyingBatteryData[2].toByte()),
            status,
            notifying
        )
        bleBattClient.processServiceData(
            characteristic,
            byteArrayOf(deviceNotifyingBatteryData[3].toByte()),
            status,
            notifying
        )
        bleBattClient.processServiceData(
            characteristic,
            byteArrayOf(deviceNotifyingBatteryData[4].toByte()),
            status,
            notifying
        )

        //Assert
        testObserver.assertNoErrors()
        testObserver.assertValueCount(3)
        assertEquals(deviceNotifyingBatteryData[0], testObserver.values()[0])
        assertEquals(deviceNotifyingBatteryData[1], testObserver.values()[1])
        assertEquals(deviceNotifyingBatteryData[3], testObserver.values()[2])
    }

    // GIVEN that BLE Battery Service client receives battery data updates
    // WHEN battery level observable is subscribed
    // THEN at some point device connection is lost
    @Test
    fun deviceDisconnectsWhileStreaming() {
        //Arrange
        val characteristic: UUID = BleBattClient.BATTERY_LEVEL_CHARACTERISTIC
        val status = 0
        val notifying = true
        val expectedBatteryLevel = 100
        val dataFromBatteryService = byteArrayOf(0x64.toByte())

        //Act
        val testObserver = TestSubscriber.create<Int>()
        bleBattClient.monitorBatteryStatus(false)
            .subscribe(testObserver)
        bleBattClient.processServiceData(characteristic, dataFromBatteryService, status, notifying)
        bleBattClient.reset()

        //Assert
        testObserver.assertError(BleDisconnected::class.java)
        testObserver.assertValueCount(1)
        val batteryData = testObserver.values()[0]
        assertEquals(expectedBatteryLevel, batteryData)
    }

    @Test
    fun `processServiceData() should emit unknown charge state when battery status notification is received with unknown charge state`() {
        // Arrange
        val characteristic: UUID = BleBattClient.BATTERY_LEVEL_STATUS_CHARACTERISTIC
        val status = 0
        val notifying = true
        val batteryStatusData = byteArrayOf(0b00000000, 0b00000000)

        // Act
        val testObserver = TestSubscriber.create<ChargeState>()
        bleBattClient.monitorChargingStatus(true)
                .subscribe(testObserver)

        bleBattClient.processServiceData(characteristic, batteryStatusData, status, notifying)

        // Assert
        testObserver.assertNoErrors()

        val chargeState = testObserver.values()[1]
        assertEquals(ChargeState.UNKNOWN, chargeState)
    }

    @Test
    fun `processServiceData() should emit discharging inactive charge state when battery status notification is received with discharging inactive charge state`() {
        // Arrange
        val characteristic: UUID = BleBattClient.BATTERY_LEVEL_STATUS_CHARACTERISTIC
        val status = 0
        val notifying = true
        val batteryStatusData = byteArrayOf(0b00000000, 0b11000011.toByte())

        // Act
        val testObserver = TestSubscriber.create<ChargeState>()
        bleBattClient.monitorChargingStatus(true)
                .subscribe(testObserver)

        bleBattClient.processServiceData(characteristic, batteryStatusData, status, notifying)

        // Assert
        testObserver.assertNoErrors()

        val chargeState = testObserver.values()[1]
        assertEquals(ChargeState.DISCHARGING_INACTIVE, chargeState)
    }

    @Test
    fun `processServiceData() should emit discharging active charge state when battery status notification is received with discharging active charge state`() {
        // Arrange
        val characteristic: UUID = BleBattClient.BATTERY_LEVEL_STATUS_CHARACTERISTIC
        val status = 0
        val notifying = true
        val batteryStatusData = byteArrayOf(0b00000000, 0b11000001.toByte())

        // Act
        val testObserver = TestSubscriber.create<ChargeState>()
        bleBattClient.monitorChargingStatus(true)
                .subscribe(testObserver)

        bleBattClient.processServiceData(characteristic, batteryStatusData, status, notifying)

        // Assert
        testObserver.assertNoErrors()

        val chargeState = testObserver.values()[1]
        assertEquals(ChargeState.DISCHARGING_ACTIVE, chargeState)
    }

    @Test
    fun `processServiceData() should emit charging charge state when battery status notification is received with charging charge state`() {
        // Arrange
        val characteristic: UUID = BleBattClient.BATTERY_LEVEL_STATUS_CHARACTERISTIC
        val status = 0
        val notifying = true
        val batteryStatusData = byteArrayOf(0b00000000, 0b10100011.toByte())

        // Act
        val testObserver = TestSubscriber.create<ChargeState>()
        bleBattClient.monitorChargingStatus(true)
                .subscribe(testObserver)

        bleBattClient.processServiceData(characteristic, batteryStatusData, status, notifying)

        // Assert
        testObserver.assertNoErrors()

        val chargeState = testObserver.values()[1]
        assertEquals(ChargeState.CHARGING, chargeState)
    }
}