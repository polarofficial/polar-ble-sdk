package com.polar.androidcommunications.enpoints.ble.bluedroid.host

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.polar.androidcommunications.api.ble.model.BleDeviceSession.DeviceSessionState
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class BDDeviceSessionImplTest {

    private lateinit var context: Context
    private lateinit var bluetoothDevice: BluetoothDevice
    private lateinit var bleScanCallback: BDScanCallback
    private lateinit var bondingManager: BDBondingListener
    private lateinit var factory: BleGattFactory
    private lateinit var looper: Looper
    private lateinit var handler: Handler

    @Before
    fun setUp() {
        looper = mockk(relaxed = true)
        handler = mockk(relaxed = true)
        context = mockk(relaxed = true)
        bluetoothDevice = mockk(relaxed = true)
        bleScanCallback = mockk(relaxed = true)
        bondingManager = mockk(relaxed = true)
        factory = mockk(relaxed = true)

        every { context.mainLooper } returns looper
        every { factory.getRemoteServices(any()) } returns emptySet()
        every { bluetoothDevice.address } returns "00:11:22:33:44:55"
    }

    private fun createSut(): BDDeviceSessionImpl =
        BDDeviceSessionImpl(context, bluetoothDevice, bleScanCallback, bondingManager, factory)

    @Test
    fun address_returnsBluetoothDeviceAddress() {
        // Arrange
        val sut = createSut()

        // Act
        val result = sut.address

        // Assert
        assertEquals("00:11:22:33:44:55", result)
    }

    @Test
    fun isConnected_whenSessionOpen_returnsTrue() {
        // Arrange
        val sut = createSut()
        sut.sessionState = DeviceSessionState.SESSION_OPEN

        // Act & Assert
        assertTrue(sut.isConnected())
    }

    @Test
    fun isConnected_whenSessionClosed_returnsFalse() {
        // Arrange
        val sut = createSut()
        sut.sessionState = DeviceSessionState.SESSION_CLOSED

        // Act & Assert
        assertFalse(sut.isConnected())
    }

    @Test
    fun isConnected_whenSessionOpening_returnsFalse() {
        // Arrange
        val sut = createSut()
        sut.sessionState = DeviceSessionState.SESSION_OPENING

        // Act & Assert
        assertFalse(sut.isConnected())
    }

    @Test
    fun transportQueueSize_whenNoOperations_returnsZero() {
        // Arrange
        val sut = createSut()

        // Act & Assert
        assertEquals(0, sut.transportQueueSize())
    }

    @Test
    fun resetGatt_whenGattIsNull_doesNothing() {
        // Arrange
        val sut = createSut()
        sut.gatt = null

        // Act & Assert - should not throw
        sut.resetGatt()
        assertEquals(null, sut.gatt)
    }

    @Test
    fun resetGatt_whenGattIsNotNull_closesAndNullsGatt() {
        // Arrange
        val sut = createSut()
        val gatt = mockk<BluetoothGatt>(relaxed = true)
        sut.gatt = gatt

        // Act
        sut.resetGatt()

        // Assert
        verify(exactly = 1) { gatt.close() }
        assertEquals(null, sut.gatt)
    }

    @Test
    fun reset_callsResetGatt() {
        // Arrange
        val sut = createSut()
        val gatt = mockk<BluetoothGatt>(relaxed = true)
        sut.gatt = gatt

        // Act
        sut.reset()

        // Assert
        verify(exactly = 1) { gatt.close() }
        assertEquals(null, sut.gatt)
    }

    @Test
    fun handleMtuChanged_whenStatusSuccess_setsMtuOnAllClients() {
        // Arrange
        val client1 = mockk<BleGattBase>(relaxed = true)
        val client2 = mockk<BleGattBase>(relaxed = true)
        every { factory.getRemoteServices(any()) } returns setOf(client1, client2)
        val sut = createSut()

        // Act
        sut.handleMtuChanged(512, BleGattBase.ATT_SUCCESS)

        // Assert
        verify(exactly = 1) { client1.setMtuSize(512) }
        verify(exactly = 1) { client2.setMtuSize(512) }
    }

    @Test
    fun handleMtuChanged_whenStatusFailure_doesNotSetMtuOnClients() {
        // Arrange
        val client = mockk<BleGattBase>(relaxed = true)
        every { factory.getRemoteServices(any()) } returns setOf(client)
        val sut = createSut()

        // Act
        sut.handleMtuChanged(512, BleGattBase.ATT_INSUFFICIENT_AUTHENTICATION)

        // Assert
        verify(exactly = 0) { client.setMtuSize(any()) }
    }

    @Test
    fun handleDescriptorRead_doesNotThrow() {
        // Arrange
        val sut = createSut()
        val descriptor = mockk<BluetoothGattDescriptor>(relaxed = true)

        // Act & Assert - only calls processNextAttributeOperation, no queue so it's a no-op
        sut.handleDescriptorRead(descriptor, byteArrayOf(0x01), BleGattBase.ATT_SUCCESS)
    }

    @Test
    fun handleDescriptorRead_withNullDescriptorAndValue_doesNotThrow() {
        // Arrange
        val sut = createSut()

        // Act & Assert
        sut.handleDescriptorRead(null, null, BleGattBase.ATT_SUCCESS)
    }

    @Test
    fun handleCharacteristicValueUpdated_whenClientFound_processesServiceData() {
        // Arrange
        val serviceUuid = UUID.randomUUID()
        val characteristicUuid = UUID.randomUUID()
        val value = byteArrayOf(0x01, 0x02)

        val client = mockk<BleGattBase>(relaxed = true)
        every { client.serviceBelongsToClient(serviceUuid) } returns true
        every { client.containsCharacteristic(characteristicUuid) } returns true
        every { factory.getRemoteServices(any()) } returns setOf(client)

        val sut = createSut()
        val service = mockk<BluetoothGattService>()
        val characteristic = mockk<BluetoothGattCharacteristic>()
        every { service.uuid } returns serviceUuid
        every { characteristic.uuid } returns characteristicUuid

        // Act
        sut.handleCharacteristicValueUpdated(service, characteristic, value)

        // Assert
        verify(exactly = 1) {
            client.processServiceData(characteristicUuid, value, BleGattBase.ATT_SUCCESS, true)
        }
    }

    @Test
    fun handleCharacteristicValueUpdated_whenClientNotFound_doesNotProcessServiceData() {
        // Arrange
        val serviceUuid = UUID.randomUUID()
        val characteristicUuid = UUID.randomUUID()

        val client = mockk<BleGattBase>(relaxed = true)
        every { client.serviceBelongsToClient(serviceUuid) } returns false
        every { factory.getRemoteServices(any()) } returns setOf(client)

        val sut = createSut()
        val service = mockk<BluetoothGattService>()
        val characteristic = mockk<BluetoothGattCharacteristic>()
        every { service.uuid } returns serviceUuid
        every { characteristic.uuid } returns characteristicUuid

        // Act
        sut.handleCharacteristicValueUpdated(service, characteristic, byteArrayOf())

        // Assert
        verify(exactly = 0) { client.processServiceData(any(), any(), any(), any()) }
    }

    @Test
    fun handleCharacteristicWrite_whenStatusSuccessAndClientFound_processesServiceDataWritten() {
        // Arrange
        val serviceUuid = UUID.randomUUID()
        val characteristicUuid = UUID.randomUUID()

        val client = mockk<BleGattBase>(relaxed = true)
        every { client.serviceBelongsToClient(serviceUuid) } returns true
        every { client.containsCharacteristic(characteristicUuid) } returns true
        every { factory.getRemoteServices(any()) } returns setOf(client)

        val sut = createSut()
        val service = mockk<BluetoothGattService>()
        val characteristic = mockk<BluetoothGattCharacteristic>()
        every { service.uuid } returns serviceUuid
        every { characteristic.uuid } returns characteristicUuid

        // Act
        sut.handleCharacteristicWrite(service, characteristic, BleGattBase.ATT_SUCCESS)

        // Assert
        verify(exactly = 1) { client.processServiceDataWritten(characteristicUuid, BleGattBase.ATT_SUCCESS) }
    }

    @Test
    fun handleCharacteristicWrite_whenClientNotFound_doesNotProcessServiceData() {
        // Arrange
        val serviceUuid = UUID.randomUUID()
        val characteristicUuid = UUID.randomUUID()

        val client = mockk<BleGattBase>(relaxed = true)
        every { client.serviceBelongsToClient(serviceUuid) } returns false
        every { factory.getRemoteServices(any()) } returns setOf(client)

        val sut = createSut()
        val service = mockk<BluetoothGattService>()
        val characteristic = mockk<BluetoothGattCharacteristic>()
        every { service.uuid } returns serviceUuid
        every { characteristic.uuid } returns characteristicUuid

        // Act
        sut.handleCharacteristicWrite(service, characteristic, BleGattBase.ATT_SUCCESS)

        // Assert
        verify(exactly = 0) { client.processServiceDataWritten(any(), any()) }
    }

    @Test
    fun handleCharacteristicRead_whenStatusSuccessAndClientFound_processesServiceData() {
        // Arrange
        val serviceUuid = UUID.randomUUID()
        val characteristicUuid = UUID.randomUUID()
        val value = byteArrayOf(0x01, 0x02)

        val client = mockk<BleGattBase>(relaxed = true)
        every { client.serviceBelongsToClient(serviceUuid) } returns true
        every { client.containsCharacteristic(characteristicUuid) } returns true
        every { factory.getRemoteServices(any()) } returns setOf(client)

        val sut = createSut()
        val service = mockk<BluetoothGattService>()
        val characteristic = mockk<BluetoothGattCharacteristic>()
        every { service.uuid } returns serviceUuid
        every { characteristic.uuid } returns characteristicUuid

        // Act
        sut.handleCharacteristicRead(service, characteristic, value, BleGattBase.ATT_SUCCESS)

        // Assert
        verify(exactly = 1) {
            client.processServiceData(characteristicUuid, value, BleGattBase.ATT_SUCCESS, false)
        }
    }

    @Test
    fun handleCharacteristicRead_whenClientNotFound_doesNotProcessServiceData() {
        // Arrange
        val serviceUuid = UUID.randomUUID()
        val characteristicUuid = UUID.randomUUID()

        val client = mockk<BleGattBase>(relaxed = true)
        every { client.serviceBelongsToClient(serviceUuid) } returns false
        every { factory.getRemoteServices(any()) } returns setOf(client)

        val sut = createSut()
        val service = mockk<BluetoothGattService>()
        val characteristic = mockk<BluetoothGattCharacteristic>()
        every { service.uuid } returns serviceUuid
        every { characteristic.uuid } returns characteristicUuid

        // Act
        sut.handleCharacteristicRead(service, characteristic, byteArrayOf(), BleGattBase.ATT_SUCCESS)

        // Assert
        verify(exactly = 0) { client.processServiceData(any(), any(), any(), any()) }
    }

    @Test
    fun handleDescriptorWrite_whenStatusSuccessAndClientFound_writesDescriptor() {
        // Arrange
        val serviceUuid = UUID.randomUUID()
        val characteristicUuid = UUID.randomUUID()
        // ENABLE_NOTIFICATION_VALUE = [0x01, 0x00] — raw bytes to avoid Android framework dependency
        val value = byteArrayOf(0x01, 0x00)

        val client = mockk<BleGattBase>(relaxed = true)
        every { client.serviceBelongsToClient(serviceUuid) } returns true
        every { client.containsCharacteristic(characteristicUuid) } returns true
        every { factory.getRemoteServices(any()) } returns setOf(client)

        val sut = createSut()
        val service = mockk<BluetoothGattService>()
        val characteristic = mockk<BluetoothGattCharacteristic>()
        every { service.uuid } returns serviceUuid
        every { characteristic.uuid } returns characteristicUuid

        // Act
        sut.handleDescriptorWrite(service, characteristic, value, BleGattBase.ATT_SUCCESS)

        // Assert - ENABLE_NOTIFICATION_VALUE is not [0x00, 0x00] so activated = true
        verify(exactly = 1) { client.descriptorWritten(characteristicUuid, true, BleGattBase.ATT_SUCCESS) }
    }

    @Test
    fun handleDescriptorWrite_whenValueIsDisable_writesDescriptorWithActivatedFalse() {
        // Arrange
        val serviceUuid = UUID.randomUUID()
        val characteristicUuid = UUID.randomUUID()
        // DISABLE_NOTIFICATION_VALUE = [0x00, 0x00] — use raw bytes to avoid Android framework dependency
        val value = byteArrayOf(0x00, 0x00)

        val client = mockk<BleGattBase>(relaxed = true)
        every { client.serviceBelongsToClient(serviceUuid) } returns true
        every { client.containsCharacteristic(characteristicUuid) } returns true
        every { factory.getRemoteServices(any()) } returns setOf(client)

        val sut = createSut()
        val service = mockk<BluetoothGattService>()
        val characteristic = mockk<BluetoothGattCharacteristic>()
        every { service.uuid } returns serviceUuid
        every { characteristic.uuid } returns characteristicUuid

        // Act
        sut.handleDescriptorWrite(service, characteristic, value, BleGattBase.ATT_SUCCESS)

        // Assert - [0x00, 0x00] means disabled, so activated = false
        verify(exactly = 1) { client.descriptorWritten(characteristicUuid, false, BleGattBase.ATT_SUCCESS) }
    }

    @Test
    fun handleDescriptorWrite_whenStatusNonSuccess_writesDescriptorWithActivatedFalse() {
        // Arrange
        val serviceUuid = UUID.randomUUID()
        val characteristicUuid = UUID.randomUUID()
        // Use ENABLE value (non-zero) with a generic non-success status
        val value = byteArrayOf(0x01, 0x00) // ENABLE_NOTIFICATION_VALUE
        val errorStatus = 0x02 // non-success status that doesn't trigger special auth handling

        val client = mockk<BleGattBase>(relaxed = true)
        every { client.serviceBelongsToClient(serviceUuid) } returns true
        every { client.containsCharacteristic(characteristicUuid) } returns true
        every { factory.getRemoteServices(any()) } returns setOf(client)

        val sut = createSut()
        val service = mockk<BluetoothGattService>()
        val characteristic = mockk<BluetoothGattCharacteristic>()
        every { service.uuid } returns serviceUuid
        every { characteristic.uuid } returns characteristicUuid

        // Act - non-success status forces activated = false regardless of value
        sut.handleDescriptorWrite(service, characteristic, value, errorStatus)

        // Assert
        verify(exactly = 1) { client.descriptorWritten(characteristicUuid, false, errorStatus) }
    }

    @Test
    fun handleAuthenticationComplete_notifiesAllClients() {
        // Arrange
        val client1 = mockk<BleGattBase>(relaxed = true)
        val client2 = mockk<BleGattBase>(relaxed = true)
        every { factory.getRemoteServices(any()) } returns setOf(client1, client2)
        val sut = createSut()

        // Act
        sut.handleAuthenticationComplete()

        // Assert
        verify(exactly = 1) { client1.authenticationCompleted() }
        verify(exactly = 1) { client2.authenticationCompleted() }
    }

    @Test
    fun handleDisconnection_resetsAllClientsAndClearsObservers() {
        // Arrange
        val client = mockk<BleGattBase>(relaxed = true)
        every { factory.getRemoteServices(any()) } returns setOf(client)
        val sut = createSut()

        // Act
        sut.handleDisconnection()

        // Assert
        verify(exactly = 1) { client.reset() }
    }

    @Test
    fun handleDisconnection_disposesServiceDiscovery_whenNotNull() {
        // Arrange
        val sut = createSut()
        val discovery = mockk<Job>()
        every { discovery.cancel() } returns Unit
        sut.serviceDiscovery = discovery

        // Act
        sut.handleDisconnection()

        // Assert
        verify(exactly = 1) { discovery.cancel() }
        assertEquals(null, sut.serviceDiscovery)
    }

    @Test
    fun handleDisconnection_whenServiceDiscoveryIsNull_doesNotThrow() {
        // Arrange
        val sut = createSut()
        sut.serviceDiscovery = null

        // Act & Assert
        sut.handleDisconnection()
    }

    @Test
    fun processNextAttributeOperation_whenQueueIsEmpty_doesNothing() {
        // Arrange
        val sut = createSut()

        // Act & Assert - empty queue is a no-op
        sut.processNextAttributeOperation(false)
        assertEquals(0, sut.transportQueueSize())
    }

    @Test
    fun isAuthenticationNeeded_whenClientRequiresEncryptionAndGattHasService_returnsTrue() {
        // Arrange
        val serviceUuid = UUID.randomUUID()
        val client = mockk<BleGattBase>(relaxed = true)
        every { client.serviceBelongsToClient(serviceUuid) } returns true
        every { client.isEncryptionRequired } returns true
        every { factory.getRemoteServices(any()) } returns setOf(client)

        val sut = createSut()
        val gatt = mockk<BluetoothGatt>(relaxed = true)
        val service = mockk<BluetoothGattService>()
        every { service.uuid } returns serviceUuid
        every { gatt.services } returns listOf(service)
        sut.gatt = gatt

        // Act & Assert
        assertTrue(sut.isAuthenticationNeeded)
    }

    @Test
    fun isAuthenticationNeeded_whenNoClientRequiresEncryption_returnsFalse() {
        // Arrange
        val serviceUuid = UUID.randomUUID()
        val client = mockk<BleGattBase>(relaxed = true)
        every { client.serviceBelongsToClient(serviceUuid) } returns true
        every { client.isEncryptionRequired } returns false
        every { factory.getRemoteServices(any()) } returns setOf(client)

        val sut = createSut()
        val gatt = mockk<BluetoothGatt>(relaxed = true)
        val service = mockk<BluetoothGattService>()
        every { service.uuid } returns serviceUuid
        every { gatt.services } returns listOf(service)
        sut.gatt = gatt

        // Act & Assert
        assertFalse(sut.isAuthenticationNeeded)
    }

    @Test
    fun isAuthenticationNeeded_whenGattIsNull_returnsFalse() {
        // Arrange
        val sut = createSut()
        sut.gatt = null

        // Act & Assert
        assertFalse(sut.isAuthenticationNeeded)
    }
}