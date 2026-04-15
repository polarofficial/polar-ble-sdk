package com.polar.androidcommunications.enpoints.ble.bluedroid.host

import android.bluetooth.BluetoothDevice
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

class BDBondingListenerTest {

    private lateinit var context: Context
    private lateinit var sut: BDBondingListener

    @Before
    fun setUp() {
        // Mock IntentFilter constructor so addAction() is a no-op in BDBondingListener.init
        mockkConstructor(IntentFilter::class)
        every { anyConstructed<IntentFilter>().addAction(any()) } just runs

        context = mockk()
        every { context.registerReceiver(any(), any()) } returns null
        every { context.unregisterReceiver(any()) } just runs

        sut = BDBondingListener(context)
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
    fun addObserver_whenObserverAdded_receivesCallbackOnBondingState() {
        // Arrange
        val device = mockk<BluetoothDevice>()
        val observer = mockk<BDBondingListener.BondingObserver>(relaxed = true)
        every { observer.device } returns device
        sut.addObserver(observer)

        val intent = createBondStateIntent(device, BluetoothDevice.BOND_BONDING)

        // Act
        fireBroadcast(intent)

        // Assert
        verify(exactly = 1) { observer.bonding() }
        verify(exactly = 0) { observer.bonded() }
        verify(exactly = 0) { observer.bondNone() }
    }

    @Test
    fun addObserver_whenObserverAdded_receivesCallbackOnBondedState() {
        // Arrange
        val device = mockk<BluetoothDevice>()
        val observer = mockk<BDBondingListener.BondingObserver>(relaxed = true)
        every { observer.device } returns device
        sut.addObserver(observer)

        val intent = createBondStateIntent(device, BluetoothDevice.BOND_BONDED)

        // Act
        fireBroadcast(intent)

        // Assert
        verify(exactly = 0) { observer.bonding() }
        verify(exactly = 1) { observer.bonded() }
        verify(exactly = 0) { observer.bondNone() }
    }

    @Test
    fun addObserver_whenObserverAdded_receivesCallbackOnBondNoneState() {
        // Arrange
        val device = mockk<BluetoothDevice>()
        val observer = mockk<BDBondingListener.BondingObserver>(relaxed = true)
        every { observer.device } returns device
        sut.addObserver(observer)

        val intent = createBondStateIntent(device, BluetoothDevice.BOND_NONE)

        // Act
        fireBroadcast(intent)

        // Assert
        verify(exactly = 0) { observer.bonding() }
        verify(exactly = 0) { observer.bonded() }
        verify(exactly = 1) { observer.bondNone() }
    }

    @Test
    fun addObserver_whenObserverAddedForDifferentDevice_doesNotReceiveCallback() {
        // Arrange
        val device = mockk<BluetoothDevice>()
        val otherDevice = mockk<BluetoothDevice>()
        val observer = mockk<BDBondingListener.BondingObserver>(relaxed = true)
        every { observer.device } returns otherDevice
        sut.addObserver(observer)

        val intent = createBondStateIntent(device, BluetoothDevice.BOND_BONDED)

        // Act
        fireBroadcast(intent)

        // Assert
        verify(exactly = 0) { observer.bonding() }
        verify(exactly = 0) { observer.bonded() }
        verify(exactly = 0) { observer.bondNone() }
    }

    @Test
    fun removeObserver_whenObserverRemoved_doesNotReceiveCallback() {
        // Arrange
        val device = mockk<BluetoothDevice>()
        val observer = mockk<BDBondingListener.BondingObserver>(relaxed = true)
        every { observer.device } returns device
        sut.addObserver(observer)
        sut.removeObserver(observer)

        val intent = createBondStateIntent(device, BluetoothDevice.BOND_BONDED)

        // Act
        fireBroadcast(intent)

        // Assert
        verify(exactly = 0) { observer.bonded() }
    }

    @Test
    fun removeObserver_whenOneOfTwoObserversRemoved_remainingObserverStillReceivesCallback() {
        // Arrange
        val device = mockk<BluetoothDevice>()
        val observer1 = mockk<BDBondingListener.BondingObserver>(relaxed = true)
        val observer2 = mockk<BDBondingListener.BondingObserver>(relaxed = true)
        every { observer1.device } returns device
        every { observer2.device } returns device

        sut.addObserver(observer1)
        sut.addObserver(observer2)
        sut.removeObserver(observer1)

        val intent = createBondStateIntent(device, BluetoothDevice.BOND_BONDED)

        // Act
        fireBroadcast(intent)

        // Assert
        verify(exactly = 0) { observer1.bonded() }
        verify(exactly = 1) { observer2.bonded() }
    }

    private fun createBondStateIntent(device: BluetoothDevice, bondState: Int): Intent {
        val intent = mockk<Intent>()
        every { intent.action } returns BluetoothDevice.ACTION_BOND_STATE_CHANGED
        every { intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) } returns device
        every { intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR) } returns bondState
        return intent
    }

    private fun fireBroadcast(intent: Intent) {
        val receiverField = BDBondingListener::class.java.getDeclaredField("mReceiver")
        receiverField.isAccessible = true
        val receiver = receiverField.get(sut) as? BroadcastReceiver
        receiver?.onReceive(context, intent)
    }
}



