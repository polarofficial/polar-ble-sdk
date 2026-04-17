package com.polar.androidcommunications.api.ble.model.gatt.client

import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.common.ble.AtomicSet
import com.polar.androidcommunications.common.ble.ChannelUtils
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkObject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
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
        mockkObject(ChannelUtils.Companion)
        every { ChannelUtils.postDisconnectedAndClearList(any<AtomicSet<Channel<Any>>>()) } answers {
            @Suppress("UNCHECKED_CAST")
            val list = firstArg<AtomicSet<Channel<Any>>>()
            val error = BleDisconnected()
            list.objects().forEach { it.close(error) }
            list.clear()
        }
        every { txInterface.isConnected() } returns true
        bleBattClient = BleBattClient(txInterface)
    }

    // GIVEN that BLE Battery Service client receives battery data updates
    // WHEN battery status observable is subscribed
    // THEN the latest cached battery value is emitted
    @Test
    fun cachedValue() = runTest {
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

        val values = mutableListOf<Int>()
        val job = launch { bleBattClient.monitorBatteryStatus(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()
        job.cancel()

        //Assert
        assertEquals(1, values.size)
        assertEquals(deviceNotifyingBatteryData[1], values[0])
    }

    // GIVEN that BLE Battery Service client receives battery data updates
    // WHEN battery status observable is subscribed
    // THEN the correct values are received by observer
    @Test
    fun batteryNotificationReceived() = runTest {
        //Arrange
        val characteristic: UUID = BleBattClient.BATTERY_LEVEL_CHARACTERISTIC
        val status = 0
        val notifying = true
        val deviceNotifyingBatteryData = intArrayOf(100, 80, 255, 0, -1)

        //Act
        val values = mutableListOf<Int>()
        val job = launch { bleBattClient.monitorBatteryStatus(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

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
        testScheduler.advanceUntilIdle()
        job.cancel()

        //Assert
        assertEquals(3, values.size)
        assertEquals(deviceNotifyingBatteryData[0], values[0])
        assertEquals(deviceNotifyingBatteryData[1], values[1])
        assertEquals(deviceNotifyingBatteryData[3], values[2])
    }

    // GIVEN that BLE Battery Service client receives battery data updates
    // WHEN battery level observable is subscribed
    // THEN at some point device connection is lost
    @Test
    fun deviceDisconnectsWhileStreaming() = runTest {
        //Arrange
        val characteristic: UUID = BleBattClient.BATTERY_LEVEL_CHARACTERISTIC
        val status = 0
        val notifying = true
        val expectedBatteryLevel = 100
        val dataFromBatteryService = byteArrayOf(0x64.toByte())

        //Act
        val values = mutableListOf<Int>()
        var caughtError: Throwable? = null
        val job = launch {
            try {
                bleBattClient.monitorBatteryStatus(false).collect { values.add(it) }
            } catch (e: Throwable) {
                caughtError = e
            }
        }
        testScheduler.advanceUntilIdle()

        bleBattClient.processServiceData(characteristic, dataFromBatteryService, status, notifying)
        testScheduler.advanceUntilIdle()
        bleBattClient.reset()
        testScheduler.advanceUntilIdle()
        job.join()

        //Assert
        assertEquals(1, values.size)
        assertEquals(expectedBatteryLevel, values[0])
        assert(caughtError is BleDisconnected)
    }

    @Test
    fun `processServiceData() should emit unknown charge state when battery status notification is received with unknown charge state`() = runTest {
        // Arrange
        val characteristic: UUID = BleBattClient.BATTERY_LEVEL_STATUS_CHARACTERISTIC
        val status = 0
        val notifying = true
        val batteryStatusData = byteArrayOf(0b00000000, 0b00000000)

        // Act
        val values = mutableListOf<ChargeState>()
        val job = launch { bleBattClient.monitorChargingStatus(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        bleBattClient.processServiceData(characteristic, batteryStatusData, status, notifying)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        assertEquals(ChargeState.UNKNOWN, values[1])
    }

    @Test
    fun `processServiceData() should emit discharging inactive charge state when battery status notification is received with discharging inactive charge state`() = runTest {
        // Arrange
        val characteristic: UUID = BleBattClient.BATTERY_LEVEL_STATUS_CHARACTERISTIC
        val status = 0
        val notifying = true
        val batteryStatusData = byteArrayOf(0b00000000, 0b11100011.toByte()) // bit6=1, bit5=1 → 0x60 = value 3

        // Act
        val values = mutableListOf<ChargeState>()
        val job = launch { bleBattClient.monitorChargingStatus(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        bleBattClient.processServiceData(characteristic, batteryStatusData, status, notifying)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        assertEquals(ChargeState.DISCHARGING_INACTIVE, values[1])
    }

    @Test
    fun `processServiceData() should emit discharging active charge state when battery status notification is received with discharging active charge state`() = runTest {
        // Arrange
        val characteristic: UUID = BleBattClient.BATTERY_LEVEL_STATUS_CHARACTERISTIC
        val status = 0
        val notifying = true
        val batteryStatusData = byteArrayOf(0b00000000, 0b11000001.toByte())

        // Act
        val values = mutableListOf<ChargeState>()
        val job = launch { bleBattClient.monitorChargingStatus(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        bleBattClient.processServiceData(characteristic, batteryStatusData, status, notifying)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        assertEquals(ChargeState.DISCHARGING_ACTIVE, values[1])
    }

    @Test
    fun `processServiceData() should emit charging charge state when battery status notification is received with charging charge state`() = runTest {
        // Arrange
        val characteristic: UUID = BleBattClient.BATTERY_LEVEL_STATUS_CHARACTERISTIC
        val status = 0
        val notifying = true
        val batteryStatusData = byteArrayOf(0b00000000, 0b10100011.toByte())

        // Act
        val values = mutableListOf<ChargeState>()
        val job = launch { bleBattClient.monitorChargingStatus(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        bleBattClient.processServiceData(characteristic, batteryStatusData, status, notifying)
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        assertEquals(ChargeState.CHARGING, values[1])
    }

    // GIVEN that BLE Battery Service client receives battery status updates
    // WHEN battery power sources state observable is subscribed
    // THEN the latest cached power sources state value is emitted
    @Test
    fun powerSourcesStateCachedValue() = runTest {
        //Arrange
        val characteristic: UUID = BleBattClient.BATTERY_LEVEL_STATUS_CHARACTERISTIC
        val status = 0
        val notifying = true
        val batteryStatusDataWiredNotConnected = byteArrayOf(0x00, 0b10100001.toByte())
        val batteryStatusDataWiredConnected    = byteArrayOf(0x00, 0b10100011.toByte())

        //Act
        bleBattClient.processServiceData(characteristic, batteryStatusDataWiredNotConnected, status, notifying)
        bleBattClient.processServiceData(characteristic, batteryStatusDataWiredConnected, status, notifying)

        val values = mutableListOf<PowerSourcesState>()
        val job = launch { bleBattClient.monitorPowerSourcesState(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()
        job.cancel()

        //Assert
        assertEquals(1, values.size)
        assertEquals(PowerSourceState.CONNECTED, values[0].wiredExternalPowerConnected)
        assertEquals(PowerSourceState.NOT_CONNECTED, values[0].wirelessExternalPowerConnected)
        assertEquals(BatteryPresentState.PRESENT, values[0].batteryPresent)
    }

    @Test
    fun `processServiceData() should emit all power sources states correctly`() = runTest {
        // Arrange
        val characteristic: UUID = BleBattClient.BATTERY_LEVEL_STATUS_CHARACTERISTIC
        val status = 0
        val notifying = true

        val batteryStatusDataBatteryNotPresent            = byteArrayOf(0x00, 0b10100000.toByte())
        val batteryStatusDataBatteryPresent               = byteArrayOf(0x00, 0b10100001.toByte())
        val batteryStatusDataWiredNotConnected            = byteArrayOf(0x00, 0b10100001.toByte())
        val batteryStatusDataWiredConnected               = byteArrayOf(0x00, 0b10100011.toByte())
        val batteryStatusDataWiredUnknown                 = byteArrayOf(0x00, 0b10100101.toByte())
        val batteryStatusDataWiredReservedForFutureUse    = byteArrayOf(0x00, 0b10100111.toByte())
        val batteryStatusDataWirelessNotConnected         = byteArrayOf(0x00, 0b10100011.toByte())
        val batteryStatusDataWirelessConnected            = byteArrayOf(0x00, 0b10101011.toByte())
        val batteryStatusDataWirelessUnknown              = byteArrayOf(0x00, 0b10110011.toByte())
        val batteryStatusDataWirelessReservedForFutureUse = byteArrayOf(0x00, 0b10111011.toByte())

        // Act
        val values = mutableListOf<PowerSourcesState>()
        val job = launch { bleBattClient.monitorPowerSourcesState(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

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
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        assertEquals(BatteryPresentState.NOT_PRESENT, values[1].batteryPresent)
        assertEquals(BatteryPresentState.PRESENT, values[2].batteryPresent)

        assertEquals(PowerSourceState.NOT_CONNECTED, values[3].wiredExternalPowerConnected)
        assertEquals(PowerSourceState.CONNECTED, values[4].wiredExternalPowerConnected)
        assertEquals(PowerSourceState.UNKNOWN, values[5].wiredExternalPowerConnected)
        assertEquals(PowerSourceState.RESERVED_FOR_FUTURE_USE, values[6].wiredExternalPowerConnected)

        assertEquals(PowerSourceState.NOT_CONNECTED, values[7].wirelessExternalPowerConnected)
        assertEquals(PowerSourceState.CONNECTED, values[8].wirelessExternalPowerConnected)
        assertEquals(PowerSourceState.UNKNOWN, values[9].wirelessExternalPowerConnected)
        assertEquals(PowerSourceState.RESERVED_FOR_FUTURE_USE, values[10].wirelessExternalPowerConnected)
    }

    @Test
    fun `getBatteryLevel_should_return_newest_battery_level_value`() = runTest {
        // Arrange
        val characteristic: UUID = BleBattClient.BATTERY_LEVEL_CHARACTERISTIC
        val status = 0
        val notifying = true
        val deviceNotifyingBatteryData = intArrayOf(100, 80, 255, 0, -1)

        //Act
        val values = mutableListOf<Int>()
        val job = launch { bleBattClient.monitorBatteryStatus(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        bleBattClient.processServiceData(
            characteristic,
            byteArrayOf(deviceNotifyingBatteryData[0].toByte()),
            status,
            notifying
        )
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        assertEquals(0, values.indexOf(values.first()))
        assertEquals(100, bleBattClient.getBatteryLevel())
    }

    @Test
    fun `getBatteryLevel_should_return_undefined_battery_percentage`() = runTest {
        // Arrange
        val characteristic: UUID = UUID.randomUUID()
        val status = 0
        val notifying = true

        // Act
        val values = mutableListOf<Int>()
        val job = launch { bleBattClient.monitorBatteryStatus(true).collect { values.add(it) } }
        testScheduler.advanceUntilIdle()

        bleBattClient.processServiceData(
            characteristic,
            byteArrayOf(0x64.toByte()),
            status,
            notifying
        )
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Assert
        assertEquals(-1, bleBattClient.getBatteryLevel())
    }

    @Test
    fun `getChargerStatus_should_return_newest_charger_status`() {
        // Arrange
        val characteristic: UUID = BleBattClient.BATTERY_LEVEL_STATUS_CHARACTERISTIC
        val status = 0
        val notifying = true
        val batteryStatusDataWiredConnected = byteArrayOf(0x00, 0b10100011.toByte())

        //Act
        bleBattClient.processServiceData(
            characteristic,
            batteryStatusDataWiredConnected,
            status,
            notifying
        )

        //Assert
        assertEquals(ChargeState.CHARGING, bleBattClient.getChargerStatus())
    }

    @Test
    fun `getChargerStatus_should_return_undefined_battery_percentage`() {
        // Arrange
        val characteristic: UUID = UUID.randomUUID()
        val status = 0
        val notifying = true
        val batteryStatusDataWiredConnected = byteArrayOf(0x00, 0b10100011.toByte())

        //Act
        bleBattClient.processServiceData(
            characteristic,
            batteryStatusDataWiredConnected,
            status,
            notifying
        )

        //Assert
        assertEquals(ChargeState.UNKNOWN, bleBattClient.getChargerStatus())
    }
}