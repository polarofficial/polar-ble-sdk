package com.polar.androidcommunications.enpoints.ble.bluedroid.host

import android.bluetooth.BluetoothGatt
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection.ConnectionHandler
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import junit.framework.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class GetGattCallbackTest {

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

        val initializedLatch = CountDownLatch(1)
        every { connectionHandler.connectionInitialized(deviceSession) } answers {
            initializedLatch.countDown()
        }

        // Act
        val sut = GattCallback(connectionHandler, sessions)

        // method invocation
        sut.onConnectionStateChange(
            gatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothGatt.STATE_CONNECTED
        )

        // Assert. Required assertions (code uses 500 ms delay before callback)
        val invoked = initializedLatch.await(2, TimeUnit.SECONDS)
        assertTrue("Expected connectionInitialized to be called", invoked)
        verify(exactly = 1) { connectionHandler.connectionInitialized(deviceSession) }
        verify(exactly = 0) { connectionHandler.deviceDisconnected(any()) }
        verify(exactly = 0) { gatt.close() }
    }


}