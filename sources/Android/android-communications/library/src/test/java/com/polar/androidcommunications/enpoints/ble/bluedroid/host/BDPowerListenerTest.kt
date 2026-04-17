package com.polar.androidcommunications.enpoints.ble.bluedroid.host

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class BDPowerListenerTest {

    private lateinit var context: Context
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var powerState: BDPowerListener.BlePowerState
    private lateinit var sut: BDPowerListener

    @Before
    fun setUp() {
        // Mock IntentFilter(String) constructor so init block doesn't call unmocked Android code
        mockkConstructor(IntentFilter::class)

        context = mockk()
        bluetoothAdapter = mockk()
        powerState = mockk(relaxed = true)

        every { context.registerReceiver(any(), any()) } returns null
        every { context.unregisterReceiver(any()) } just runs

        sut = BDPowerListener(bluetoothAdapter, context, powerState)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun stopBroadcastReceiver_whenReceiverRegistered_unregistersReceiver() {
        // Act
        sut.stopBroadcastReceiver()

        // Assert
        verify(exactly = 1) { context.unregisterReceiver(any()) }
    }

    @Test
    fun stopBroadcastReceiver_whenCalledTwice_unregistersOnlyOnce() {
        // Act
        sut.stopBroadcastReceiver()
        sut.stopBroadcastReceiver()

        // Assert
        verify(exactly = 1) { context.unregisterReceiver(any()) }
    }

    @Test
    fun onReceive_whenBluetoothStateOff_callsBlePoweredOff() {
        // Arrange
        every { bluetoothAdapter.state } returns BluetoothAdapter.STATE_OFF
        val intent = createStateChangedIntent()

        // Act
        fireBroadcast(intent)

        // Assert
        verify(exactly = 1) { powerState.blePoweredOff() }
        verify(exactly = 0) { powerState.blePoweredOn() }
    }

    @Test
    fun onReceive_whenBluetoothStateOn_callsBlePoweredOn() {
        // Arrange
        every { bluetoothAdapter.state } returns BluetoothAdapter.STATE_ON
        val intent = createStateChangedIntent()

        // Act
        fireBroadcast(intent)

        // Assert
        verify(exactly = 0) { powerState.blePoweredOff() }
        verify(exactly = 1) { powerState.blePoweredOn() }
    }

    @Test
    fun onReceive_whenBluetoothStateTurningOn_doesNotCallEitherCallback() {
        // Arrange
        every { bluetoothAdapter.state } returns BluetoothAdapter.STATE_TURNING_ON
        val intent = createStateChangedIntent()

        // Act
        fireBroadcast(intent)

        // Assert
        verify(exactly = 0) { powerState.blePoweredOff() }
        verify(exactly = 0) { powerState.blePoweredOn() }
    }

    @Test
    fun onReceive_whenBluetoothStateTurningOff_doesNotCallEitherCallback() {
        // Arrange
        every { bluetoothAdapter.state } returns BluetoothAdapter.STATE_TURNING_OFF
        val intent = createStateChangedIntent()

        // Act
        fireBroadcast(intent)

        // Assert
        verify(exactly = 0) { powerState.blePoweredOff() }
        verify(exactly = 0) { powerState.blePoweredOn() }
    }

    @Test
    fun onReceive_whenActionIsNotStateChanged_doesNotCallEitherCallback() {
        // Arrange
        every { bluetoothAdapter.state } returns BluetoothAdapter.STATE_ON
        val intent = mockk<Intent>()
        every { intent.action } returns BluetoothAdapter.ACTION_DISCOVERY_STARTED

        // Act
        fireBroadcast(intent)

        // Assert
        verify(exactly = 0) { powerState.blePoweredOff() }
        verify(exactly = 0) { powerState.blePoweredOn() }
    }

    @Test
    fun onReceive_whenActionIsNull_doesNotCallEitherCallback() {
        // Arrange
        every { bluetoothAdapter.state } returns BluetoothAdapter.STATE_ON
        val intent = mockk<Intent>()
        every { intent.action } returns null

        // Act
        fireBroadcast(intent)

        // Assert
        verify(exactly = 0) { powerState.blePoweredOff() }
        verify(exactly = 0) { powerState.blePoweredOn() }
    }

    private fun createStateChangedIntent(): Intent {
        val intent = mockk<Intent>()
        every { intent.action } returns BluetoothAdapter.ACTION_STATE_CHANGED
        return intent
    }

    private fun fireBroadcast(intent: Intent) {
        val receiverField = BDPowerListener::class.java.getDeclaredField("receiver")
        receiverField.isAccessible = true
        val receiver = receiverField.get(sut) as? BroadcastReceiver
        receiver?.onReceive(context, intent)
    }
}

