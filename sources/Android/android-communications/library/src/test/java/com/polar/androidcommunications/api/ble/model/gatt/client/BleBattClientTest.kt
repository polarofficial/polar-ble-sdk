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
        val batteryStatusData = byteArrayOf(0b00000000, 0b11100011.toByte()) // bit6=1, bit5=1 → 0x60 = value 3

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

    // GIVEN that BLE Battery Service client receives battery status updates
    // WHEN battery power sources state observable is subscribed
    // THEN the latest cached power sources state value is emitted
    @Test
    fun powerSourcesStateCachedValue() {
        //Arrange
        val characteristic: UUID = BleBattClient.BATTERY_LEVEL_STATUS_CHARACTERISTIC
        val status = 0
        val notifying = true

        // Bit#0 Battery present
        // 0 = No
        // 1 = Yes
        // Bits#1-2 Wired External Power Source Connected
        // Bits#3-4 Wireless External Power Source Connected
        // 0 = No
        // 1 = Yes
        // 2 = Unknown
        // 3 = RFU

        // Battery present, wireless not connected, wired not connected
        val batteryStatusDataWiredNotConnected              = byteArrayOf(0x00, 0b10100001.toByte())
        // Battery present, wireless not connected, wired connected
        val batteryStatusDataWiredConnected                 = byteArrayOf(0x00, 0b10100011.toByte())

        //Act
        bleBattClient.processServiceData(
            characteristic,
            batteryStatusDataWiredNotConnected,
            status,
            notifying
        )
        bleBattClient.processServiceData(
            characteristic,
            batteryStatusDataWiredConnected,
            status,
            notifying
        )

        val testObserver = TestSubscriber.create<PowerSourcesState>()
        bleBattClient.monitorPowerSourcesState(true)
            .subscribe(testObserver)

        //Assert
        testObserver.assertNoErrors()
        testObserver.assertValueCount(1)
        assertEquals(PowerSourceState.CONNECTED, testObserver.values()[0].wiredExternalPowerConnected)
        assertEquals(PowerSourceState.NOT_CONNECTED, testObserver.values()[0].wirelessExternalPowerConnected)
        assertEquals(BatteryPresentState.PRESENT, testObserver.values()[0].batteryPresent)
    }

    @Test
    fun `processServiceData() should emit all power sources states correctly`()  {
        
        // Arrange
        val characteristic: UUID = BleBattClient.BATTERY_LEVEL_STATUS_CHARACTERISTIC
        val status = 0
        val notifying = true

        val batteryStatusDataBatteryNotPresent              = byteArrayOf(0x00, 0b10100000.toByte())
        val batteryStatusDataBatteryPresent                 = byteArrayOf(0x00, 0b10100001.toByte())

        val batteryStatusDataWiredNotConnected              = byteArrayOf(0x00, 0b10100001.toByte())
        val batteryStatusDataWiredConnected                 = byteArrayOf(0x00, 0b10100011.toByte())
        val batteryStatusDataWiredUnknown                   = byteArrayOf(0x00, 0b10100101.toByte())
        val batteryStatusDataWiredReservedForFutureUse      = byteArrayOf(0x00, 0b10100111.toByte())

        val batteryStatusDataWirelessNotConnected           = byteArrayOf(0x00, 0b10100011.toByte())
        val batteryStatusDataWirelessConnected              = byteArrayOf(0x00, 0b10101011.toByte())
        val batteryStatusDataWirelessUnknown                = byteArrayOf(0x00, 0b10110011.toByte())
        val batteryStatusDataWirelessReservedForFutureUse   = byteArrayOf(0x00, 0b10111011.toByte())

        // Act
        val testObserver = TestSubscriber.create<PowerSourcesState>()
        bleBattClient.monitorPowerSourcesState(true)
            .subscribe(testObserver)

        bleBattClient.processServiceData(characteristic, batteryStatusDataBatteryNotPresent, status, notifying)
        bleBattClient.processServiceData(characteristic, batteryStatusDataBatteryPresent, status, notifying)
        
        bleBattClient.processServiceData(characteristic, batteryStatusDataWiredNotConnected, status, notifying)
        bleBattClient.processServiceData(characteristic, batteryStatusDataWiredConnected, status, notifying)
        bleBattClient.processServiceData(characteristic, batteryStatusDataWiredUnknown, status, notifying)
        bleBattClient.processServiceData(characteristic, batteryStatusDataWiredReservedForFutureUse, status, notifying)
        
        bleBattClient.processServiceData(characteristic, batteryStatusDataWirelessNotConnected, status, notifying)
        bleBattClient.processServiceData(characteristic, batteryStatusDataWirelessConnected, status, notifying)
        bleBattClient.processServiceData(characteristic, batteryStatusDataWirelessUnknown, status, notifying)
        bleBattClient.processServiceData(characteristic, batteryStatusDataWirelessReservedForFutureUse, status, notifying)
        
        // Assert
        testObserver.assertNoErrors()
        val events = testObserver.values()
        
        assertEquals(events[1].batteryPresent, BatteryPresentState.NOT_PRESENT)
        assertEquals(events[2].batteryPresent, BatteryPresentState.PRESENT)

        assertEquals(events[3].wiredExternalPowerConnected, PowerSourceState.NOT_CONNECTED)
        assertEquals(events[4].wiredExternalPowerConnected, PowerSourceState.CONNECTED)
        assertEquals(events[5].wiredExternalPowerConnected, PowerSourceState.UNKNOWN)
        assertEquals(events[6].wiredExternalPowerConnected, PowerSourceState.RESERVED_FOR_FUTURE_USE)

        assertEquals(events[7].wirelessExternalPowerConnected, PowerSourceState.NOT_CONNECTED)
        assertEquals(events[8].wirelessExternalPowerConnected, PowerSourceState.CONNECTED)
        assertEquals(events[9].wirelessExternalPowerConnected, PowerSourceState.UNKNOWN)
        assertEquals(events[10].wirelessExternalPowerConnected, PowerSourceState.RESERVED_FOR_FUTURE_USE)

    }
}