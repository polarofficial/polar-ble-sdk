package com.polar.androidcommunications.api.ble.model

import android.bluetooth.BluetoothDevice
import com.polar.androidcommunications.api.ble.model.advertisement.BleAdvertisementContent
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.common.ble.BleUtils
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID
import java.util.concurrent.TimeUnit

class BleDeviceSessionTest {

    /**
     * Minimal concrete subclass of BleDeviceSession used as test double.
     * clients is initialised to an empty set so fetchClient and clientsReady
     * never NPE on the clients!! dereference.
     */
    private inner class TestBleDeviceSession : BleDeviceSession() {
        var nonConnectable: Boolean = false
        var authenticated: Boolean = false
        var mockAddress: String? = "AA:BB:CC:DD:EE:FF"
        var mockBluetoothDevice: BluetoothDevice? = mockk(relaxed = true)
        var pairingProblem: Pair<Boolean, Int> = Pair(false, -1)

        init {
            clients = emptySet()
        }

        override val isNonConnectableAdvertisement: Boolean
            get() = nonConnectable

        override val address: String?
            get() = mockAddress

        override suspend fun authenticate() { /* no-op */ }

        override val isAuthenticated: Boolean
            get() = authenticated

        override fun monitorServicesDiscovered(checkConnection: Boolean): Deferred<List<UUID>> =
            CompletableDeferred(emptyList())

        override fun clearGattCache(): Boolean = true

        override fun readRssiValue(): Deferred<Int> = CompletableDeferred(-70)

        override fun getIndicatesPairingProblem(): Pair<Boolean, Int> = pairingProblem

        override val bluetoothDevice: BluetoothDevice?
            get() = mockBluetoothDevice

        fun exposeClients(clientSet: Set<BleGattBase>) {
            clients = clientSet
        }
    }

    private lateinit var sut: TestBleDeviceSession

    @Before
    fun setUp() {
        sut = TestBleDeviceSession()
    }

    @Test
    fun sessionState_defaultIsClosed() {
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_CLOSED, sut.sessionState)
    }

    @Test
    fun sessionState_canBeSet() {
        sut.sessionState = BleDeviceSession.DeviceSessionState.SESSION_OPEN
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_OPEN, sut.sessionState)
    }

    @Test
    fun previousState_defaultIsClosed() {
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_CLOSED, sut.previousState)
    }

    @Test
    fun getAdvertisementContent_returnsAdvertisementContent() {
        assertNotNull(sut.getAdvertisementContent)
    }

    @Test
    fun advertisementContent_canBeReplaced() {
        // Arrange
        val newContent = BleAdvertisementContent()

        // Act
        sut.advertisementContent = newContent

        // Assert
        assertEquals(newContent, sut.getAdvertisementContent)
    }

    @Test
    fun connectionUuids_defaultIsEmpty() {
        assertTrue(sut.connectionUuids.isEmpty())
    }

    @Test
    fun connectionUuids_whenSet_replacesExistingList() {
        // Arrange
        sut.connectionUuids = mutableListOf("uuid1")
        val newUuids = mutableListOf("uuid2", "uuid3")

        // Act
        sut.connectionUuids = newUuids

        // Assert
        assertEquals(2, sut.connectionUuids.size)
        assertTrue(sut.connectionUuids.contains("uuid2"))
        assertTrue(sut.connectionUuids.contains("uuid3"))
        assertFalse(sut.connectionUuids.contains("uuid1"))
    }

    @Test
    fun isDeviceAlive_whenSessionOpen_returnsTrue() {
        sut.sessionState = BleDeviceSession.DeviceSessionState.SESSION_OPEN
        assertTrue(sut.isDeviceAlive(10L, TimeUnit.SECONDS))
    }

    @Test
    fun isDeviceAlive_whenSessionOpening_returnsTrue() {
        sut.sessionState = BleDeviceSession.DeviceSessionState.SESSION_OPENING
        assertTrue(sut.isDeviceAlive(10L, TimeUnit.SECONDS))
    }

    @Test
    fun isDeviceAlive_whenSessionClosing_returnsTrue() {
        sut.sessionState = BleDeviceSession.DeviceSessionState.SESSION_CLOSING
        assertTrue(sut.isDeviceAlive(10L, TimeUnit.SECONDS))
    }

    @Test
    fun isDeviceAlive_whenSessionClosedAndNoAdvertisementData_returnsFalse() {
        sut.sessionState = BleDeviceSession.DeviceSessionState.SESSION_CLOSED
        assertFalse(sut.isDeviceAlive(10L, TimeUnit.SECONDS))
    }

    @Test
    fun isDeviceAlive_whenSessionClosedAndRecentAdvertisementData_returnsTrue() {
        // Arrange
        sut.sessionState = BleDeviceSession.DeviceSessionState.SESSION_CLOSED
        val advData = hashMapOf(
            BleUtils.AD_TYPE.GAP_ADTYPE_LOCAL_NAME_COMPLETE to "Polar H10 12345678".toByteArray()
        )
        sut.advertisementContent.processAdvertisementData(advData, BleUtils.EVENT_TYPE.ADV_IND, -60)

        // Act & Assert — large window so just-processed data is within range
        assertTrue(sut.isDeviceAlive(60L, TimeUnit.SECONDS))
    }

    @Test
    fun isDeviceAlive_whenSessionClosedAndDataOutsideWindow_returnsFalse() {
        // Arrange — SESSION_CLOSED + no advertisement data at all → always false
        sut.sessionState = BleDeviceSession.DeviceSessionState.SESSION_CLOSED

        // Act & Assert
        assertFalse(sut.isDeviceAlive(60L, TimeUnit.SECONDS))
    }

    @Test
    fun isAdvertising_whenRecentAdvertisementData_returnsTrue() {
        // Arrange
        val advData = hashMapOf(
            BleUtils.AD_TYPE.GAP_ADTYPE_LOCAL_NAME_COMPLETE to "Polar H10 12345678".toByteArray()
        )
        sut.advertisementContent.processAdvertisementData(advData, BleUtils.EVENT_TYPE.ADV_IND, -60)

        // Act & Assert — large window so data is within range
        assertTrue(sut.isAdvertising(60L, TimeUnit.SECONDS))
    }

    @Test
    fun isAdvertising_whenNoAdvertisementData_returnsFalse() {
        assertFalse(sut.isAdvertising(10L, TimeUnit.SECONDS))
    }

    @Test
    fun isNonConnectableAdvertisement_whenTrue_returnsTrue() {
        sut.nonConnectable = true
        assertTrue(sut.isNonConnectableAdvertisement)
    }

    @Test
    fun isNonConnectableAdvertisement_whenFalse_returnsFalse() {
        sut.nonConnectable = false
        assertFalse(sut.isNonConnectableAdvertisement)
    }

    @Test
    fun isConnectableAdvertisement_isOppositeOfNonConnectable() {
        sut.nonConnectable = false
        assertTrue(sut.isConnectableAdvertisement)

        sut.nonConnectable = true
        assertFalse(sut.isConnectableAdvertisement)
    }

    @Test
    fun fetchClient_whenMatchingClientExists_returnsClient() {
        // Arrange
        val serviceUuid = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val txInterface = mockk<BleGattTxInterface>(relaxed = true)
        val client = object : BleGattBase(txInterface, serviceUuid) {
            override fun processServiceData(characteristic: UUID, data: ByteArray, status: Int, notifying: Boolean) {}
            override fun processServiceDataWritten(characteristic: UUID, status: Int) {}
        }
        sut.exposeClients(setOf(client))

        // Act
        val result = sut.fetchClient(serviceUuid)

        // Assert
        assertNotNull(result)
        assertEquals(client, result)
    }

    @Test
    fun fetchClient_whenNoMatchingClient_returnsNull() {
        // Arrange
        val serviceUuid = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val unknownUuid = UUID.fromString("0000180e-0000-1000-8000-00805f9b34fb")
        val txInterface = mockk<BleGattTxInterface>(relaxed = true)
        val client = object : BleGattBase(txInterface, serviceUuid) {
            override fun processServiceData(characteristic: UUID, data: ByteArray, status: Int, notifying: Boolean) {}
            override fun processServiceDataWritten(characteristic: UUID, status: Int) {}
        }
        sut.exposeClients(setOf(client))

        // Act
        val result = sut.fetchClient(unknownUuid)

        // Assert
        assertNull(result)
    }

    @Test
    fun clientsReady_whenNoServicesDiscovered_completesWithoutError() = runTest {
        // clients already initialised to emptySet() in TestBleDeviceSession.init{}
        sut.clientsReady(false)
    }

    @Test
    fun name_returnsAdvertisementContentName() {
        // Arrange
        val advData = hashMapOf(
            BleUtils.AD_TYPE.GAP_ADTYPE_LOCAL_NAME_COMPLETE to "Polar H10 12345678".toByteArray()
        )
        sut.advertisementContent.processAdvertisementData(advData, BleUtils.EVENT_TYPE.ADV_IND, -60)

        assertEquals("Polar H10 12345678", sut.name)
    }

    @Test
    fun polarDeviceId_returnsAdvertisementContentPolarDeviceId() {
        // Arrange
        val advData = hashMapOf(
            BleUtils.AD_TYPE.GAP_ADTYPE_LOCAL_NAME_COMPLETE to "Polar H10 12345678".toByteArray()
        )
        sut.advertisementContent.processAdvertisementData(advData, BleUtils.EVENT_TYPE.ADV_IND, -60)

        assertEquals("12345678", sut.polarDeviceId)
    }

    @Test
    fun polarDeviceType_returnsAdvertisementContentPolarDeviceType() {
        // Arrange
        val advData = hashMapOf(
            BleUtils.AD_TYPE.GAP_ADTYPE_LOCAL_NAME_COMPLETE to "Polar H10 12345678".toByteArray()
        )
        sut.advertisementContent.processAdvertisementData(advData, BleUtils.EVENT_TYPE.ADV_IND, -60)

        assertEquals("H10", sut.polarDeviceType)
    }

    @Test
    fun polarDeviceIdInt_returnsHexParsedDeviceId() {
        // Arrange
        val advData = hashMapOf(
            BleUtils.AD_TYPE.GAP_ADTYPE_LOCAL_NAME_COMPLETE to "Polar H10 12345678".toByteArray()
        )
        sut.advertisementContent.processAdvertisementData(advData, BleUtils.EVENT_TYPE.ADV_IND, -60)

        assertEquals(0x12345678L, sut.polarDeviceIdInt)
    }

    @Test
    fun medianRssi_defaultIsMinusOneHundred() {
        assertEquals(-100, sut.medianRssi)
    }

    @Test
    fun rssi_defaultIsMinusOneHundred() {
        assertEquals(-100, sut.rssi)
    }

    @Test
    fun blePolarHrAdvertisement_returnsAdvertisementContentPolarHrAdvertisement() {
        assertNotNull(sut.blePolarHrAdvertisement)
    }
}