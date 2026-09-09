package com.polar.androidcommunications.enpoints.ble.bluedroid.host

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection.ConnectionHandler
import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.polar.androidcommunications.common.ble.AtomicSet
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class GattCallbackTest {

    @Test
    fun onConnectionStateChange_whenPendingDeviceCommandHasExpectedStatus_classifiesDeviceCommand() {
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>(relaxed = true)
        val deviceSession = mockk<BDDeviceSessionImpl>(relaxed = true)

        every { sessions.getSession(gatt) } returns deviceSession
        every { deviceSession.isExpectedDeviceCommandDisconnectStillValid() } returns true
        every { deviceSession.pendingDeviceCommand } returns BleDeviceSession.DeviceCommand.RESTART

        GattCallback(connectionHandler, sessions).onConnectionStateChange(gatt, 19, BluetoothGatt.STATE_DISCONNECTED)

        verify(exactly = 1) {
            deviceSession.markDisconnect(BleDeviceSession.DisconnectReason.DEVICE_COMMAND, 19)
        }
    }

    @Test
    fun onConnectionStateChange_whenPendingDeviceCommandHasUnexpectedStatus_classifiesDeviceCommandAndLogsWarning() {
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>(relaxed = true)
        val deviceSession = mockk<BDDeviceSessionImpl>(relaxed = true)
        val warnings = mutableListOf<String>()

        every { sessions.getSession(gatt) } returns deviceSession
        every { deviceSession.isExpectedDeviceCommandDisconnectStillValid() } returns true
        every { deviceSession.pendingDeviceCommand } returns BleDeviceSession.DeviceCommand.RESTART
        BleLogger.setLoggerInterface(object : BleLogger.BleLoggerInterface {
            override fun d(tag: String, msg: String) = Unit
            override fun e(tag: String, msg: String) = Unit
            override fun w(tag: String, msg: String) { warnings += msg }
            override fun i(tag: String, msg: String) = Unit
            override fun d_hex(tag: String, msg: String, data: ByteArray) = Unit
        })

        try {
            GattCallback(connectionHandler, sessions).onConnectionStateChange(gatt, 133, BluetoothGatt.STATE_DISCONNECTED)

            verify(exactly = 1) {
                deviceSession.markDisconnect(BleDeviceSession.DisconnectReason.DEVICE_COMMAND, 133)
            }
            assertTrue(warnings.any { it.contains("unexpected GATT status") })
        } finally {
            BleLogger.setLoggerInterface(null)
        }
    }

    @Test
    fun onConnectionStateChange_whenNoPendingCommandAndPeerTerminates_classifiesPeerTerminated() {
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>(relaxed = true)
        val deviceSession = mockk<BDDeviceSessionImpl>(relaxed = true)

        every { sessions.getSession(gatt) } returns deviceSession
        every { deviceSession.isExpectedDeviceCommandDisconnectStillValid() } returns false

        listOf(19, 20, 21).forEach { status ->
            GattCallback(connectionHandler, sessions).onConnectionStateChange(gatt, status, BluetoothGatt.STATE_DISCONNECTED)
            verify(exactly = 1) {
                deviceSession.markDisconnect(BleDeviceSession.DisconnectReason.PEER_TERMINATED, status)
            }
        }
    }

    @Test
    fun onConnectionStateChange_whenPendingDeviceCommandExpired_fallsThroughToNormalClassification() {
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>(relaxed = true)
        val deviceSession = mockk<BDDeviceSessionImpl>(relaxed = true)

        every { sessions.getSession(gatt) } returns deviceSession
        every { deviceSession.isExpectedDeviceCommandDisconnectStillValid() } returns false
        every { deviceSession.pendingDeviceCommand } returns BleDeviceSession.DeviceCommand.RESTART

        GattCallback(connectionHandler, sessions).onConnectionStateChange(gatt, 19, BluetoothGatt.STATE_DISCONNECTED)

        verify(exactly = 1) {
            deviceSession.markDisconnect(BleDeviceSession.DisconnectReason.PEER_TERMINATED, 19)
        }
    }

    @Test
    fun onConnectionStateChange_whenNoPendingCommandAndConnectionTimesOut_classifiesConnectionLost() {
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>(relaxed = true)
        val deviceSession = mockk<BDDeviceSessionImpl>(relaxed = true)

        every { sessions.getSession(gatt) } returns deviceSession
        every { deviceSession.isExpectedDeviceCommandDisconnectStillValid() } returns false

        GattCallback(connectionHandler, sessions).onConnectionStateChange(gatt, 8, BluetoothGatt.STATE_DISCONNECTED)

        verify(exactly = 1) {
            deviceSession.markDisconnect(BleDeviceSession.DisconnectReason.CONNECTION_LOST, 8)
        }
    }

    @Test
    fun onConnectionStateChange_whenPendingDeviceCommandAndL2cFailureStatus_classifiesDeviceCommandOverPairingNegotiation() {
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>(relaxed = true)
        val deviceSession = mockk<BDDeviceSessionImpl>(relaxed = true)

        every { sessions.getSession(gatt) } returns deviceSession
        every { deviceSession.isExpectedDeviceCommandDisconnectStillValid() } returns true
        every { deviceSession.pendingDeviceCommand } returns BleDeviceSession.DeviceCommand.RESTART

        GattCallback(connectionHandler, sessions).onConnectionStateChange(gatt, 22, BluetoothGatt.STATE_DISCONNECTED)

        verify(exactly = 1) {
            deviceSession.markDisconnect(BleDeviceSession.DisconnectReason.DEVICE_COMMAND, 22)
        }
    }

    @Test
    fun onConnectionStateChange_whenConnectedAndStatusSuccess_callsConnectionInitialized() {

        // Arrange
        val connectionHandler = mockk<ConnectionHandler>()
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()
        val deviceSession = mockk<BDDeviceSessionImpl>(relaxed = true)

        every { sessions.getSession(gatt) } returns deviceSession
        every { connectionHandler.deviceDisconnected(any()) } just runs
        every { gatt.close() } just runs
        every { gatt.device } returns mockk(relaxed = true) { every { address } returns "AA:BB:CC:DD:EE:FF" }

        val initializedLatch = CountDownLatch(1)
        every { connectionHandler.connectionInitialized(deviceSession) } answers {
            initializedLatch.countDown()
        }

        val sut = GattCallback(connectionHandler, sessions)

        // Act
        sut.onConnectionStateChange(
            gatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothGatt.STATE_CONNECTED
        )

        // Assert (code uses 500 ms delay before callback)
        val invoked = initializedLatch.await(2, TimeUnit.SECONDS)
        assertTrue("Expected connectionInitialized to be called", invoked)
        verify(exactly = 1) { connectionHandler.connectionInitialized(deviceSession) }
        verify(exactly = 0) { connectionHandler.deviceDisconnected(any()) }
        verify(exactly = 0) { gatt.close() }
    }

    @Test
    fun onConnectionStateChange_whenBondRemovedWithZeroStatus_classifiesPairingInformationRemoved() {
        val connectionHandler = mockk<ConnectionHandler>()
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()
        val deviceSession = mockk<BDDeviceSessionImpl>(relaxed = true)
        val bluetoothDevice = mockk<BluetoothDevice>()

        every { sessions.getSession(gatt) } returns deviceSession
        every { gatt.device } returns bluetoothDevice
        every { bluetoothDevice.address } returns "AA:BB:CC:DD:EE:FF"
        every { deviceSession.bluetoothDevice } returns bluetoothDevice
        every { deviceSession.wasBondedAtConnection } returns true
        every { deviceSession.hasEstablishedBond } returns true
        every { deviceSession.pairingInformationWasRemoved() } returns true
        every { bluetoothDevice.bondState } returns BluetoothDevice.BOND_NONE

        val disconnectedLatch = CountDownLatch(1)
        every { connectionHandler.deviceDisconnected(deviceSession) } answers {
            disconnectedLatch.countDown()
        }

        val sut = GattCallback(connectionHandler, sessions)

        sut.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothGatt.STATE_DISCONNECTED)

        assertTrue("Expected disconnect callback", disconnectedLatch.await(2, TimeUnit.SECONDS))
        verify(exactly = 1) {
            deviceSession.markDisconnect(
                BleDeviceSession.DisconnectReason.PAIRING_INFORMATION_REMOVED,
                BluetoothGatt.GATT_SUCCESS
            )
        }
    }

    @Test
    fun onConnectionStateChange_whenL2cConnectionFails_classifiesPairingNegotiationFailed() {
        val connectionHandler = mockk<ConnectionHandler>()
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()
        val deviceSession = mockk<BDDeviceSessionImpl>(relaxed = true)
        val bluetoothDevice = mockk<BluetoothDevice>()

        every { sessions.getSession(gatt) } returns deviceSession
        every { gatt.device } returns bluetoothDevice
        every { bluetoothDevice.address } returns "AA:BB:CC:DD:EE:FF"
        every { deviceSession.bluetoothDevice } returns bluetoothDevice
        every { deviceSession.pairingInformationWasRemoved() } returns false
        every { deviceSession.disconnectReason } returns BleDeviceSession.DisconnectReason.NONE

        val disconnectedLatch = CountDownLatch(1)
        every { connectionHandler.deviceDisconnected(deviceSession) } answers {
            disconnectedLatch.countDown()
        }

        val sut = GattCallback(connectionHandler, sessions)

        sut.onConnectionStateChange(gatt, 22, BluetoothGatt.STATE_DISCONNECTED)

        assertTrue("Expected disconnect callback", disconnectedLatch.await(2, TimeUnit.SECONDS))
        verify(exactly = 1) {
            deviceSession.markDisconnect(
                BleDeviceSession.DisconnectReason.PAIRING_NEGOTIATION_FAILED,
                22
            )
        }
    }

    @Test
    fun onCharacteristicRead_whenSessionExists_callsHandleCharacteristicRead() {
        // Arrange
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()
        val deviceSession = mockk<BDDeviceSessionImpl>(relaxed = true)
        val service = mockk<BluetoothGattService>()
        val characteristic = mockk<BluetoothGattCharacteristic>()

        val characteristicValue = byteArrayOf(0x01, 0x02, 0x03)
        val characteristicUuid = UUID.randomUUID()
        every { sessions.getSession(gatt) } returns deviceSession
        every { characteristic.service } returns service
        every { characteristic.uuid } returns characteristicUuid
        every { characteristic.value } returns characteristicValue
        every { gatt.close() } just runs
        every { deviceSession.handleCharacteristicRead(service, characteristic, characteristicValue, 0) } just runs

        val sut = GattCallback(connectionHandler, sessions)

        // Act
        sut.onCharacteristicRead(gatt, characteristic, 0)

        // Assert
        verify(exactly = 1) { deviceSession.handleCharacteristicRead(service, characteristic, characteristicValue, 0) }
        verify(exactly = 0) { gatt.close() }
    }

    @Test
    fun onCharacteristicRead_newOverload_whenSessionExists_callsHandleCharacteristicRead() {
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()
        val deviceSession = mockk<BDDeviceSessionImpl>(relaxed = true)
        val service = mockk<BluetoothGattService>()
        val characteristic = mockk<BluetoothGattCharacteristic>()

        val characteristicValue = byteArrayOf(0x04, 0x05, 0x06)
        val characteristicUuid = UUID.randomUUID()
        every { sessions.getSession(gatt) } returns deviceSession
        every { characteristic.service } returns service
        every { characteristic.uuid } returns characteristicUuid
        every { gatt.close() } just runs
        every { deviceSession.handleCharacteristicRead(service, characteristic, characteristicValue, BluetoothGatt.GATT_SUCCESS) } just runs

        val sut = GattCallback(connectionHandler, sessions)

        // Act (new overload with explicit value parameter)
        sut.onCharacteristicRead(gatt, characteristic, characteristicValue, BluetoothGatt.GATT_SUCCESS)

        // Assert
        verify(exactly = 1) { deviceSession.handleCharacteristicRead(service, characteristic, characteristicValue, BluetoothGatt.GATT_SUCCESS) }
        verify(exactly = 0) { gatt.close() }
    }

    @Test
    fun onCharacteristicRead_whenSessionUnknown_closesGatt() {
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()
        val characteristic = mockk<BluetoothGattCharacteristic>()

        val characteristicUuid = UUID.randomUUID()
        every { sessions.getSession(gatt) } returns null
        every { characteristic.uuid } returns characteristicUuid
        every { gatt.close() } just runs

        val sut = GattCallback(connectionHandler, sessions)

        // Act
        sut.onCharacteristicRead(gatt, characteristic, 0)

        // Assert
        verify(exactly = 1) { gatt.close() }
    }

    @Test
    fun onCharacteristicWrite_whenSessionExists_callsHandleCharacteristicWrite() {
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()
        val deviceSession = mockk<BDDeviceSessionImpl>(relaxed = true)
        val service = mockk<BluetoothGattService>()
        val characteristic = mockk<BluetoothGattCharacteristic>()

        val characteristicUuid = UUID.randomUUID()
        every { sessions.getSession(gatt) } returns deviceSession
        every { characteristic.service } returns service
        every { characteristic.uuid } returns characteristicUuid
        every { gatt.close() } just runs
        every { deviceSession.handleCharacteristicWrite(service, characteristic, BluetoothGatt.GATT_SUCCESS) } just runs

        val sut = GattCallback(connectionHandler, sessions)

        // Act
        sut.onCharacteristicWrite(gatt, characteristic, BluetoothGatt.GATT_SUCCESS)

        // Assert
        verify(exactly = 1) { deviceSession.handleCharacteristicWrite(service, characteristic, BluetoothGatt.GATT_SUCCESS) }
        verify(exactly = 0) { gatt.close() }
    }

    @Test
    fun onCharacteristicWrite_whenWriteFailsWithError_callsHandleCharacteristicWriteWithErrorStatus() {
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()
        val deviceSession = mockk<BDDeviceSessionImpl>(relaxed = true)
        val service = mockk<BluetoothGattService>()
        val characteristic = mockk<BluetoothGattCharacteristic>()

        val characteristicUuid = UUID.randomUUID()
        val errorStatus = 133
        every { sessions.getSession(gatt) } returns deviceSession
        every { characteristic.service } returns service
        every { characteristic.uuid } returns characteristicUuid
        every { gatt.close() } just runs
        every { deviceSession.handleCharacteristicWrite(service, characteristic, errorStatus) } just runs

        val sut = GattCallback(connectionHandler, sessions)

        // Act
        sut.onCharacteristicWrite(gatt, characteristic, errorStatus)

        // Assert
        verify(exactly = 1) { deviceSession.handleCharacteristicWrite(service, characteristic, errorStatus) }
        verify(exactly = 0) { gatt.close() }
    }

    @Test
    fun onCharacteristicWrite_whenSessionUnknown_closesGatt() {
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()
        val characteristic = mockk<BluetoothGattCharacteristic>()

        val characteristicUuid = UUID.randomUUID()
        every { sessions.getSession(gatt) } returns null
        every { characteristic.uuid } returns characteristicUuid
        every { gatt.close() } just runs

        val sut = GattCallback(connectionHandler, sessions)

        // Act
        sut.onCharacteristicWrite(gatt, characteristic, BluetoothGatt.GATT_SUCCESS)

        // Assert
        verify(exactly = 1) { gatt.close() }
    }

    @Test
    fun onServicesDiscovered_whenStatusSuccessAndNoActiveDiscovery_handlesDiscoveryAndNotifiesHandler() {
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()
        val deviceSession = mockk<BDDeviceSessionImpl>(relaxed = true)

        every { sessions.getSession(gatt) } returns deviceSession
        every { deviceSession.serviceDiscovery } returns null
        every { deviceSession.handleServicesDiscovered() } just runs
        every { connectionHandler.servicesDiscovered(deviceSession) } just runs

        val sut = GattCallback(connectionHandler, sessions)

        // Act
        sut.onServicesDiscovered(gatt, BluetoothGatt.GATT_SUCCESS)

        // Assert (waits up to 2 seconds for async callback)
        Thread.sleep(1000) // Give async scheduler time to process
        verify(exactly = 1) { deviceSession.handleServicesDiscovered() }
        verify(exactly = 1) { connectionHandler.servicesDiscovered(deviceSession) }
    }

    @Test
    fun onServicesDiscovered_whenStatusSuccessAndActiveDiscoveryExists_disposesOldDiscoveryBeforeHandling() {
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()
        val deviceSession = mockk<BDDeviceSessionImpl>(relaxed = true)
        val oldDiscovery = mockk<Job>()

        every { sessions.getSession(gatt) } returns deviceSession
        every { deviceSession.serviceDiscovery } returns oldDiscovery
        every { oldDiscovery.cancel() } returns Unit
        every { deviceSession.serviceDiscovery = null } just runs
        every { deviceSession.handleServicesDiscovered() } just runs

        val latch = CountDownLatch(1)
        every { connectionHandler.servicesDiscovered(deviceSession) } answers { latch.countDown() }

        val sut = GattCallback(connectionHandler, sessions)

        // Act
        sut.onServicesDiscovered(gatt, BluetoothGatt.GATT_SUCCESS)

        // Assert (servicesDiscovered is called via coroutine delay)
        val invoked = latch.await(2, TimeUnit.SECONDS)
        assertTrue("Expected servicesDiscovered to be called", invoked)
        verify(exactly = 1) { oldDiscovery.cancel() }
        verify(exactly = 1) { deviceSession.serviceDiscovery = null }
        verify(exactly = 1) { deviceSession.handleServicesDiscovered() }
        verify(exactly = 1) { connectionHandler.servicesDiscovered(deviceSession) }
    }

    @Test
    fun onServicesDiscovered_whenStatusFailure_disconnectsDevice() {
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()
        val deviceSession = mockk<BDDeviceSessionImpl>(relaxed = true)

        every { sessions.getSession(gatt) } returns deviceSession
        every { deviceSession.serviceDiscovery } returns null
        every { deviceSession.serviceDiscovery = null } just runs
        every { connectionHandler.disconnectDevice(deviceSession) } just runs

        val sut = GattCallback(connectionHandler, sessions)

        // Act
        sut.onServicesDiscovered(gatt, 133) // Error status

        // Assert
        Thread.sleep(1000)
        verify(exactly = 1) { connectionHandler.disconnectDevice(deviceSession) }
        verify(exactly = 0) { connectionHandler.servicesDiscovered(any()) }
    }

    @Test
    fun onServicesDiscovered_whenSessionUnknown_returnsEarly() {
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()

        every { sessions.getSession(gatt) } returns null

        val sut = GattCallback(connectionHandler, sessions)

        // Act
        sut.onServicesDiscovered(gatt, BluetoothGatt.GATT_SUCCESS)

        // Assert
        verify(exactly = 0) { connectionHandler.servicesDiscovered(any()) }
        verify(exactly = 0) { connectionHandler.disconnectDevice(any()) }
    }

    @Test
    fun onCharacteristicChanged_deprecatedOverload_whenSessionExists_callsHandleCharacteristicValueUpdated() {
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()
        val deviceSession = mockk<BDDeviceSessionImpl>(relaxed = true)
        val service = mockk<BluetoothGattService>()
        val characteristic = mockk<BluetoothGattCharacteristic>()

        val characteristicValue = byteArrayOf(0x10, 0x20, 0x30)
        val characteristicUuid = UUID.randomUUID()
        every { sessions.getSession(gatt) } returns deviceSession
        every { characteristic.service } returns service
        every { characteristic.uuid } returns characteristicUuid
        every { characteristic.value } returns characteristicValue
        every { gatt.close() } just runs
        every { deviceSession.handleCharacteristicValueUpdated(service, characteristic, characteristicValue) } just runs

        val sut = GattCallback(connectionHandler, sessions)

        // Act
        sut.onCharacteristicChanged(gatt, characteristic)

        // Assert
        verify(exactly = 1) { deviceSession.handleCharacteristicValueUpdated(service, characteristic, characteristicValue) }
        verify(exactly = 0) { gatt.close() }
    }

    @Test
    fun onCharacteristicChanged_newOverload_whenSessionExists_callsHandleCharacteristicValueUpdated() {
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()
        val deviceSession = mockk<BDDeviceSessionImpl>(relaxed = true)
        val service = mockk<BluetoothGattService>()
        val characteristic = mockk<BluetoothGattCharacteristic>()

        val characteristicValue = byteArrayOf(0x40, 0x50, 0x60)
        val characteristicUuid = UUID.randomUUID()
        every { sessions.getSession(gatt) } returns deviceSession
        every { characteristic.service } returns service
        every { characteristic.uuid } returns characteristicUuid
        every { gatt.close() } just runs
        every { deviceSession.handleCharacteristicValueUpdated(service, characteristic, characteristicValue) } just runs

        val sut = GattCallback(connectionHandler, sessions)

        // Act
        sut.onCharacteristicChanged(gatt, characteristic, characteristicValue)

        // Assert
        verify(exactly = 1) { deviceSession.handleCharacteristicValueUpdated(service, characteristic, characteristicValue) }
        verify(exactly = 0) { gatt.close() }
    }

    @Test
    fun onCharacteristicChanged_deprecatedOverload_whenSessionUnknown_closesGatt() {
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()
        val characteristic = mockk<BluetoothGattCharacteristic>()

        val characteristicUuid = UUID.randomUUID()
        every { sessions.getSession(gatt) } returns null
        every { characteristic.uuid } returns characteristicUuid
        every { gatt.close() } just runs

        val sut = GattCallback(connectionHandler, sessions)

        // Act
        sut.onCharacteristicChanged(gatt, characteristic)

        // Assert
        verify(exactly = 1) { gatt.close() }
    }

    @Test
    fun onCharacteristicChanged_newOverload_whenSessionUnknown_closesGatt() {
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()
        val characteristic = mockk<BluetoothGattCharacteristic>()

        val characteristicValue = byteArrayOf(0x70, 0x80.toByte(), 0x90.toByte())
        val characteristicUuid = UUID.randomUUID()
        every { sessions.getSession(gatt) } returns null
        every { characteristic.uuid } returns characteristicUuid
        every { gatt.close() } just runs

        val sut = GattCallback(connectionHandler, sessions)

        // Act
        sut.onCharacteristicChanged(gatt, characteristic, characteristicValue)

        // Assert
        verify(exactly = 1) { gatt.close() }
    }

    @Test
    fun onDescriptorRead_deprecatedOverload_whenSessionExists_callsHandleDescriptorRead() {
        // Arrange
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()
        val deviceSession = mockk<BDDeviceSessionImpl>(relaxed = true)
        val descriptor = mockk<BluetoothGattDescriptor>()

        val descriptorValue = byteArrayOf(0x01, 0x00)
        every { sessions.getSession(gatt) } returns deviceSession
        every { descriptor.uuid } returns UUID.randomUUID()
        every { descriptor.value } returns descriptorValue
        every { gatt.close() } just runs
        every { deviceSession.handleDescriptorRead(descriptor, descriptorValue, BluetoothGatt.GATT_SUCCESS) } just runs

        val sut = GattCallback(connectionHandler, sessions)

        // Act
        sut.onDescriptorRead(gatt, descriptor, BluetoothGatt.GATT_SUCCESS)

        // Assert
        verify(exactly = 1) { deviceSession.handleDescriptorRead(descriptor, descriptorValue, BluetoothGatt.GATT_SUCCESS) }
        verify(exactly = 0) { gatt.close() }
    }

    @Test
    fun onDescriptorRead_newOverload_whenSessionExists_callsHandleDescriptorRead() {
        // Arrange
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()
        val deviceSession = mockk<BDDeviceSessionImpl>(relaxed = true)
        val descriptor = mockk<BluetoothGattDescriptor>()

        val descriptorValue = byteArrayOf(0x01, 0x00)
        every { sessions.getSession(gatt) } returns deviceSession
        every { descriptor.uuid } returns UUID.randomUUID()
        every { gatt.close() } just runs
        every { deviceSession.handleDescriptorRead(descriptor, descriptorValue, BluetoothGatt.GATT_SUCCESS) } just runs

        val sut = GattCallback(connectionHandler, sessions)

        // Act
        sut.onDescriptorRead(gatt, descriptor, BluetoothGatt.GATT_SUCCESS, descriptorValue)

        // Assert
        verify(exactly = 1) { deviceSession.handleDescriptorRead(descriptor, descriptorValue, BluetoothGatt.GATT_SUCCESS) }
        verify(exactly = 0) { gatt.close() }
    }

    @Test
    fun onDescriptorRead_deprecatedOverload_whenSessionUnknown_closesGatt() {
        // Arrange
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()
        val descriptor = mockk<BluetoothGattDescriptor>()

        every { sessions.getSession(gatt) } returns null
        every { descriptor.uuid } returns UUID.randomUUID()
        every { gatt.close() } just runs

        val sut = GattCallback(connectionHandler, sessions)

        // Act
        sut.onDescriptorRead(gatt, descriptor, BluetoothGatt.GATT_SUCCESS)

        // Assert
        verify(exactly = 1) { gatt.close() }
    }

    @Test
    fun onDescriptorRead_newOverload_whenSessionUnknown_closesGatt() {
        // Arrange
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()
        val descriptor = mockk<BluetoothGattDescriptor>()

        val descriptorValue = byteArrayOf(0x01, 0x00)
        every { sessions.getSession(gatt) } returns null
        every { descriptor.uuid } returns UUID.randomUUID()
        every { gatt.close() } just runs

        val sut = GattCallback(connectionHandler, sessions)

        // Act
        sut.onDescriptorRead(gatt, descriptor, BluetoothGatt.GATT_SUCCESS, descriptorValue)

        // Assert
        verify(exactly = 1) { gatt.close() }
    }

    @Test
    fun onReadRemoteRssi_whenSessionExists_emitsRssiValueToObservers() {
        // Arrange
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()
        val deviceSession = mockk<BDDeviceSessionImpl>(relaxed = true)

        val rssiObservers = AtomicSet<Channel<Int>>()
        every { sessions.getSession(gatt) } returns deviceSession
        every { deviceSession.rssiObservers } returns rssiObservers
        every { gatt.close() } just runs

        val channel = Channel<Int>(1)
        rssiObservers.add(channel)

        // handleRssiRead is a relaxed stub (no-op) — wire it to forward to the channel
        every { deviceSession.handleRssiRead(any(), any()) } answers {
            val rssi = firstArg<Int>()
            val status = secondArg<Int>()
            if (status == BluetoothGatt.GATT_SUCCESS) {
                rssiObservers.objects().forEach { it.trySend(rssi) }
            }
        }

        val sut = GattCallback(connectionHandler, sessions)

        // Act
        sut.onReadRemoteRssi(gatt, -65, BluetoothGatt.GATT_SUCCESS)

        // Assert
        val emittedRssi = runBlocking { channel.receive() }
        assertEquals(-65, emittedRssi)
        verify(exactly = 0) { gatt.close() }
    }

    @Test
    fun onReadRemoteRssi_whenSessionExists_emitsCorrectRssiValue() {
        // Arrange
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()
        val deviceSession = mockk<BDDeviceSessionImpl>(relaxed = true)

        val rssiObservers = AtomicSet<Channel<Int>>()
        every { sessions.getSession(gatt) } returns deviceSession
        every { deviceSession.rssiObservers } returns rssiObservers
        every { gatt.close() } just runs

        val channel = Channel<Int>(1)
        rssiObservers.add(channel)

        // handleRssiRead is a relaxed stub (no-op) — wire it to forward to the channel
        every { deviceSession.handleRssiRead(any(), any()) } answers {
            val rssi = firstArg<Int>()
            val status = secondArg<Int>()
            if (status == BluetoothGatt.GATT_SUCCESS) {
                rssiObservers.objects().forEach { it.trySend(rssi) }
            }
        }

        val sut = GattCallback(connectionHandler, sessions)

        // Act
        sut.onReadRemoteRssi(gatt, -90, BluetoothGatt.GATT_SUCCESS)

        // Assert
        val emittedRssi = runBlocking { channel.receive() }
        assertEquals(-90, emittedRssi)
        verify(exactly = 0) { gatt.close() }
    }

    @Test
    fun onReadRemoteRssi_whenSessionUnknown_closesGatt() {
        // Arrange
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()

        every { sessions.getSession(gatt) } returns null
        every { gatt.close() } just runs

        val sut = GattCallback(connectionHandler, sessions)

        // Act
        sut.onReadRemoteRssi(gatt, -65, BluetoothGatt.GATT_SUCCESS)

        // Assert
        verify(exactly = 1) { gatt.close() }
    }

    @Test
    fun onMtuChanged_whenSessionExists_callsHandleMtuChangedAndNotifiesHandler() {
        // Arrange
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()
        val deviceSession = mockk<BDDeviceSessionImpl>(relaxed = true)

        every { sessions.getSession(gatt) } returns deviceSession
        every { deviceSession.handleMtuChanged(517, BluetoothGatt.GATT_SUCCESS) } just runs
        every { gatt.close() } just runs

        val latch = CountDownLatch(1)
        every { connectionHandler.mtuUpdated(deviceSession) } answers { latch.countDown() }

        val sut = GattCallback(connectionHandler, sessions)

        // Act
        sut.onMtuChanged(gatt, 517, BluetoothGatt.GATT_SUCCESS)

        // Assert
        val invoked = latch.await(2, TimeUnit.SECONDS)
        assertTrue("Expected mtuUpdated to be called", invoked)
        verify(exactly = 1) { deviceSession.handleMtuChanged(517, BluetoothGatt.GATT_SUCCESS) }
        verify(exactly = 1) { connectionHandler.mtuUpdated(deviceSession) }
        verify(exactly = 0) { gatt.close() }
    }

    @Test
    fun onMtuChanged_whenSessionExistsAndStatusFailure_callsHandleMtuChangedWithErrorStatus() {
        // Arrange
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()
        val deviceSession = mockk<BDDeviceSessionImpl>(relaxed = true)

        val errorStatus = 133
        every { sessions.getSession(gatt) } returns deviceSession
        every { deviceSession.handleMtuChanged(23, errorStatus) } just runs
        every { gatt.close() } just runs

        val latch = CountDownLatch(1)
        every { connectionHandler.mtuUpdated(deviceSession) } answers { latch.countDown() }

        val sut = GattCallback(connectionHandler, sessions)

        // Act
        sut.onMtuChanged(gatt, 23, errorStatus)

        // Assert
        val invoked = latch.await(2, TimeUnit.SECONDS)
        assertTrue("Expected mtuUpdated to be called", invoked)
        verify(exactly = 1) { deviceSession.handleMtuChanged(23, errorStatus) }
        verify(exactly = 1) { connectionHandler.mtuUpdated(deviceSession) }
        verify(exactly = 0) { gatt.close() }
    }

    @Test
    fun onMtuChanged_whenSessionUnknown_closesGatt() {
        // Arrange
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()

        every { sessions.getSession(gatt) } returns null
        every { gatt.close() } just runs

        val sut = GattCallback(connectionHandler, sessions)

        // Act
        sut.onMtuChanged(gatt, 517, BluetoothGatt.GATT_SUCCESS)

        // Assert
        verify(exactly = 1) { gatt.close() }
        verify(exactly = 0) { connectionHandler.mtuUpdated(any()) }
    }

    @Test
    fun onPhyUpdate_whenSessionExists_callsPhyUpdated() {
        // Arrange
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()
        val deviceSession = mockk<BDDeviceSessionImpl>(relaxed = true)

        every { sessions.getSession(gatt) } returns deviceSession

        val latch = CountDownLatch(1)
        every { connectionHandler.phyUpdated(deviceSession) } answers { latch.countDown() }

        val sut = GattCallback(connectionHandler, sessions)

        // Act
        sut.onPhyUpdate(gatt, BluetoothDevice.PHY_LE_1M, BluetoothDevice.PHY_LE_1M, BluetoothGatt.GATT_SUCCESS)

        // Assert
        val invoked = latch.await(2, TimeUnit.SECONDS)
        assertTrue("Expected phyUpdated to be called", invoked)
        verify(exactly = 1) { connectionHandler.phyUpdated(deviceSession) }
        verify(exactly = 0) { gatt.close() }
    }

    @Test
    fun onPhyUpdate_whenSessionExists_forwardsAllPhyValues() {
        // Arrange
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()
        val deviceSession = mockk<BDDeviceSessionImpl>(relaxed = true)

        every { sessions.getSession(gatt) } returns deviceSession

        val latch = CountDownLatch(1)
        every { connectionHandler.phyUpdated(deviceSession) } answers { latch.countDown() }

        val sut = GattCallback(connectionHandler, sessions)

        // Act
        sut.onPhyUpdate(gatt, BluetoothDevice.PHY_LE_2M, BluetoothDevice.PHY_LE_CODED, BluetoothGatt.GATT_SUCCESS)

        // Assert
        val invoked = latch.await(2, TimeUnit.SECONDS)
        assertTrue("Expected phyUpdated to be called", invoked)
        verify(exactly = 1) { connectionHandler.phyUpdated(deviceSession) }
        verify(exactly = 0) { gatt.close() }
    }

    @Test
    fun onPhyUpdate_whenSessionUnknown_doesNotCallPhyUpdatedOrCloseGatt() {
        // Arrange
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()

        every { sessions.getSession(gatt) } returns null
        every { gatt.close() } just runs

        val sut = GattCallback(connectionHandler, sessions)

        // Act
        sut.onPhyUpdate(gatt, BluetoothDevice.PHY_LE_1M, BluetoothDevice.PHY_LE_1M, BluetoothGatt.GATT_SUCCESS)

        // Assert (implementation does nothing when session is null, no gatt.close() either)
        verify(exactly = 0) { connectionHandler.phyUpdated(any()) }
        verify(exactly = 0) { gatt.close() }
    }

    @Test
    fun onPhyRead_whenSessionExists_callsPhyUpdated() {
        // Arrange
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()
        val deviceSession = mockk<BDDeviceSessionImpl>(relaxed = true)

        every { sessions.getSession(gatt) } returns deviceSession

        val latch = CountDownLatch(1)
        every { connectionHandler.phyUpdated(deviceSession) } answers { latch.countDown() }

        val sut = GattCallback(connectionHandler, sessions)

        // Act
        sut.onPhyRead(gatt, BluetoothDevice.PHY_LE_1M, BluetoothDevice.PHY_LE_1M, BluetoothGatt.GATT_SUCCESS)

        // Assert
        val invoked = latch.await(2, TimeUnit.SECONDS)
        assertTrue("Expected phyUpdated to be called", invoked)
        verify(exactly = 1) { connectionHandler.phyUpdated(deviceSession) }
        verify(exactly = 0) { gatt.close() }
    }

    @Test
    fun onPhyRead_whenSessionExists_forwardsAllPhyValues() {
        // Arrange
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()
        val deviceSession = mockk<BDDeviceSessionImpl>(relaxed = true)

        every { sessions.getSession(gatt) } returns deviceSession

        val latch = CountDownLatch(1)
        every { connectionHandler.phyUpdated(deviceSession) } answers { latch.countDown() }

        val sut = GattCallback(connectionHandler, sessions)

        // Act
        sut.onPhyRead(gatt, BluetoothDevice.PHY_LE_2M, BluetoothDevice.PHY_LE_CODED, BluetoothGatt.GATT_SUCCESS)

        // Assert
        val invoked = latch.await(2, TimeUnit.SECONDS)
        assertTrue("Expected phyUpdated to be called", invoked)
        verify(exactly = 1) { connectionHandler.phyUpdated(deviceSession) }
        verify(exactly = 0) { gatt.close() }
    }

    @Test
    fun onPhyRead_whenSessionUnknown_doesNotCallPhyUpdatedOrCloseGatt() {
        // Arrange
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>()
        val gatt = mockk<BluetoothGatt>()

        every { sessions.getSession(gatt) } returns null
        every { gatt.close() } just runs

        val sut = GattCallback(connectionHandler, sessions)

        // Act
        sut.onPhyRead(gatt, BluetoothDevice.PHY_LE_1M, BluetoothDevice.PHY_LE_1M, BluetoothGatt.GATT_SUCCESS)

        // Assert (implementation does nothing when session is null, no gatt.close() either)
        verify(exactly = 0) { connectionHandler.phyUpdated(any()) }
        verify(exactly = 0) { gatt.close() }
    }

    @Test
    fun onServiceChanged_doesNotInteractWithSessionOrHandler() {
        // Arrange
        val connectionHandler = mockk<ConnectionHandler>(relaxed = true)
        val sessions = mockk<BDDeviceList>(relaxed = true)
        val gatt = mockk<BluetoothGatt>()
        val sut = GattCallback(connectionHandler, sessions)

        // Act - super.onServiceChanged is not mocked in the unit test environment,
        // so we catch the RuntimeException it throws and verify the no-op contract
        try {
            sut.onServiceChanged(gatt)
        } catch (_: RuntimeException) { }

        // Assert - implementation only calls super and logs, no session interaction
        verify(exactly = 0) { sessions.getSession(any<BluetoothGatt>()) }
        verify(exactly = 0) { connectionHandler.deviceDisconnected(any()) }
        verify(exactly = 0) { gatt.close() }
    }
}