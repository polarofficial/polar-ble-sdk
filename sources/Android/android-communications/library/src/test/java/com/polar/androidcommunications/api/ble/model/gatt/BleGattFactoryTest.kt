package com.polar.androidcommunications.api.ble.model.gatt

import com.polar.androidcommunications.api.ble.model.gatt.client.BleBattClient
import com.polar.androidcommunications.api.ble.model.gatt.client.BleDisClient
import com.polar.androidcommunications.api.ble.model.gatt.client.BleHrClient
import com.polar.androidcommunications.api.ble.model.gatt.client.BlePfcClient
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class BleGattFactoryTest {

    @Test
    fun getRemoteServices_whenClassesHaveTxConstructor_createsInstances() {
        // Arrange
        val tx = mockk<BleGattTxInterface>(relaxed = true)
        val clients = setOf(
            BleHrClient::class.java,
            BleDisClient::class.java
        )
        val factory = BleGattFactory(clients)

        // Act
        val result = factory.getRemoteServices(tx)

        // Assert
        assertEquals(2, result.size)
        assertTrue(result.any { it is BleHrClient })
        assertTrue(result.any { it is BleDisClient })
    }

    @Test
    fun getRemoteServices_whenOneClassFailsReflection_usesFallbackAndReturnsKnownClientsOnly() {
        // Arrange
        val tx = mockk<BleGattTxInterface>(relaxed = true)
        val clients = setOf(
            TestGattClientNoTxCtor::class.java as Class<out BleGattBase>,
            BleHrClient::class.java,
            BleDisClient::class.java,
            BleBattClient::class.java,
            BlePfcClient::class.java,
            BlePMDClient::class.java,
            BlePsFtpClient::class.java
        )
        val factory = BleGattFactory(clients)

        // Act
        val result = factory.getRemoteServices(tx)

        // Assert
        assertEquals(6, result.size)
        assertTrue(result.any { it is BleHrClient })
        assertTrue(result.any { it is BleDisClient })
        assertTrue(result.any { it is BleBattClient })
        assertTrue(result.any { it is BlePfcClient })
        assertTrue(result.any { it is BlePMDClient })
        assertTrue(result.any { it is BlePsFtpClient })
        assertTrue(result.none { it is TestGattClientNoTxCtor })
    }

    @Test
    fun getRemoteServices_whenInputEmpty_returnsEmptySet() {
        // Arrange
        val tx = mockk<BleGattTxInterface>(relaxed = true)
        val factory = BleGattFactory(emptySet())

        // Act
        val result = factory.getRemoteServices(tx)

        // Assert
        assertTrue(result.isEmpty())
    }

    // Simulate constructor not found for TxInterface to trigger fallback path in factory
    private class TestGattClientNoTxCtor : BleGattBase(
        mockk(relaxed = true),
        UUID.randomUUID()
    ) {
        override fun processServiceData(
            characteristic: UUID,
            data: ByteArray,
            status: Int,
            notifying: Boolean
        ) {
            // no-op for fallback-path test
        }

        override fun processServiceDataWritten(characteristic: UUID, status: Int) {
            // no-op for fallback-path test
        }
    }
}
