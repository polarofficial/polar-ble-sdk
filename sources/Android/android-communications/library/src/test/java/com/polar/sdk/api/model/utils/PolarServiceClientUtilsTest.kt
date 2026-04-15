package com.polar.sdk.api.model.utils

import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.api.ble.model.advertisement.BleAdvertisementContent
import com.polar.androidcommunications.api.ble.model.gatt.client.BleHrClient
import com.polar.androidcommunications.api.ble.model.gatt.client.BleHrClient.Companion.HR_SERVICE
import com.polar.androidcommunications.api.ble.model.gatt.client.BlePfcClient
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.errors.PolarDeviceDisconnected
import com.polar.sdk.api.errors.PolarDeviceNotFound
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.errors.PolarServiceNotAvailable
import com.polar.sdk.impl.utils.PolarServiceClientUtils
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class PolarServiceClientUtilsTest {

    @Test
    fun testSessionHrClientReady() {
        // Arrange
        val deviceId = "E123456F"

        val client = mockk<BleHrClient>()
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(any()) } returns client
        every { client.isServiceDiscovered } returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0) // 0 is BleGattBase.ATT_SUCCESS

        // Act
        val testHrSession = PolarServiceClientUtils.sessionHrClientReady(deviceId, listener)

        // Assert
        Assert.assertEquals(testHrSession.sessionState, BleDeviceSession.DeviceSessionState.SESSION_OPEN)
    }

    @Test
    fun testSessionHrClientThrows() {
        // Arrange
        val deviceId = "E123456F"

        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_CLOSED // No open connection, throws

        // Act&Assert
        try {
            PolarServiceClientUtils.sessionHrClientReady(deviceId, listener)
            fail("testSessionHrClientReadyThrows, sessionHrClientReady did not throw when no connection to device.")
        } catch (e: Exception) {
            Assert.assertEquals(true,  PolarDeviceDisconnected().toString().contentEquals(e.toString()))
        }
    }

    @Test
    fun testSessionPmdClientReady() {
        // Arrange
        val deviceId = "E123456F"

        val client = mockk<BlePMDClient>()
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(any()) } returns client
        every { client.isServiceDiscovered } returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0) // 0 is BleGattBase.ATT_SUCCESS

        // Act
        val testPMDSession = PolarServiceClientUtils.sessionPmdClientReady(deviceId, listener)

        // Assert
        Assert.assertEquals(testPMDSession.sessionState, BleDeviceSession.DeviceSessionState.SESSION_OPEN)
    }

    @Test
    fun testSessionPmdClientThrows() {
        // Arrange
        val deviceId = "E123456F"

        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_CLOSED

        // Act&Assert
        try {
            PolarServiceClientUtils.sessionPmdClientReady(deviceId, listener)
            fail("Test sessionPmdClientThrows failed: sessionPmdClientReady did not throw when no connection to device.")
        } catch (e: Exception) {
            Assert.assertEquals(true,  PolarDeviceDisconnected().toString().contentEquals(e.toString()))
        }
    }

    @Test
    fun testSessionFtpClientReady() {
        // Arrange
        val deviceId = "E123456F"

        val client = mockk<BlePsFtpClient>()
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(any()) } returns client
        every { client.isServiceDiscovered } returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0) // 0 is BleGattBase.ATT_SUCCESS

        // Act
        val testFtpSession = PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, listener)

        // Assert
        Assert.assertEquals(testFtpSession.sessionState, BleDeviceSession.DeviceSessionState.SESSION_OPEN)
    }

    @Test
    fun testSessionFtpClientThrows() {
        // Arrange
        val deviceId = "E123456F"

        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_CLOSED

        // Act&Assert
        try {
            PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, listener)
            fail("Test sessionPsFtpClientReady failed: sessionPsFtpClientReady did not throw when no connection to device.")
        } catch (e: Exception) {
            Assert.assertEquals(true,  PolarDeviceDisconnected().toString().contentEquals(e.toString()))
        }
    }

    @Test
    fun testSessionPfcClientReady() {
        // Arrange
        val deviceId = "E123456F"

        val client = mockk<BlePfcClient>()
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(any()) } returns client
        every { client.isServiceDiscovered } returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0) // 0 is BleGattBase.ATT_SUCCESS

        // Act
        val testPfcSession = PolarServiceClientUtils.sessionPsPfcClientReady(deviceId, listener)

        // Assert
        Assert.assertEquals(testPfcSession.sessionState, BleDeviceSession.DeviceSessionState.SESSION_OPEN)
    }

    @Test
    fun testSessionPfcClientThrows() {
        // Arrange
        val deviceId = "E123456F"

        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_CLOSED

        // Act&Assert
        try {
            PolarServiceClientUtils.sessionPsPfcClientReady(deviceId, listener)
            fail("Test sessionPsPfcClientReady failed: sessionPsPfcClientReady did not throw when no connection to device.")
        } catch (e: Exception) {
            Assert.assertEquals(true,  PolarDeviceDisconnected().toString().contentEquals(e.toString()))
        }
    }

    @Test
    fun testSessionServiceReady() {
        // Arrange
        val deviceId = "E123456F"

        val client = mockk<BlePfcClient>()
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(any()) } returns client
        every { client.isServiceDiscovered } returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0) // 0 is BleGattBase.ATT_SUCCESS

        // Act
        val testSessionService = PolarServiceClientUtils.sessionServiceReady(deviceId, HR_SERVICE, listener)

        // Assert
        Assert.assertEquals(testSessionService.sessionState, BleDeviceSession.DeviceSessionState.SESSION_OPEN)
    }

    @Test
    fun testSessionService_Throws_When_DeviceNotFound() {
        // Arrange
        val deviceId = "E123456F"
        val listener = mockk<BleDeviceListener>()

        every { listener.deviceSessions() } returns null

        // Act&Assert
        try {
            PolarServiceClientUtils.sessionServiceReady(deviceId, HR_SERVICE, listener)
            fail("Test testSessionServiceThrowsWhenDeviceNotFound failed: sessionServiceReady did not throw when no device found.")
        } catch (e: Exception) {
            Assert.assertEquals(true,  PolarDeviceNotFound().toString().contentEquals(e.toString()))
        }
    }

    @Test
    fun testSessionService_Throws_When_DeviceDisconnected() {
        // Arrange
        val deviceId = "E123456F"
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns sessions
        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_CLOSED

        // Act&Assert
        try {
            PolarServiceClientUtils.sessionServiceReady(deviceId, HR_SERVICE, listener)
            fail("Test testSessionServiceThrowsWhenDeviceDisconnected failed: sessionServiceReady did not throw when no connection to device.")
        } catch (e: Exception) {
            Assert.assertEquals(true,  PolarDeviceDisconnected().toString().contentEquals(e.toString()))
        }
    }

    @Test
    fun testSessionService_Throws_When_NoDeviceBleClient() {
        // Arrange
        val deviceId = "E123456F"
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(any()) } returns null

        // Act&Assert
        try {
            PolarServiceClientUtils.sessionServiceReady(deviceId, UUID.randomUUID(), listener)
            fail("Test testSessionServiceThrowsWhenNoDeviceBleClient failed: sessionServiceReady did not throw when device does not support required Ble client.")
        } catch (e: Exception) {
            Assert.assertEquals(true,  PolarServiceNotAvailable().toString().contentEquals(e.toString()))
        }
    }

    @Test
    fun testSessionService_Throws_When_NoBleService() {
        // Arrange
        val deviceId = "E123456F"
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()
        val client = mockk<BlePsFtpClient>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(any()) } returns client
        every { client.isServiceDiscovered } returns false

        // Act&Assert
        try {
            PolarServiceClientUtils.sessionServiceReady(deviceId, UUID.randomUUID(), listener)
            fail("Test testSessionServiceThrowsWhenNoClientService failed: sessionServiceReady did not throw when device Ble client does not have required service.")
        } catch (e: Exception) {
            Assert.assertEquals(true,  PolarServiceNotAvailable().toString().contentEquals(e.toString()))
        }
    }

    @Test
    fun testFetchSession() {
        // Arrange
        val deviceId = "E123456F"

        val client = mockk<BlePfcClient>()
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(any()) } returns client
        every { client.isServiceDiscovered } returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0) // 0 is BleGattBase.ATT_SUCCESS

        // Act
        val testSession = PolarServiceClientUtils.fetchSession(deviceId, listener)

        // Assert
        if (testSession != null) {
            Assert.assertEquals(testSession.sessionState, BleDeviceSession.DeviceSessionState.SESSION_OPEN)
        } else {
            fail("testFetchSession: testSession.sessionState was null")
        }
    }

    @Test
    fun testFetchSessionThrows() {
        // Arrange
        val deviceId = "Not-A-DeviceId"
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()

        every { session.advertisementContent.polarDeviceId } returns deviceId


        // Act&Assert
        try {
            PolarServiceClientUtils.sessionServiceReady(deviceId, UUID.randomUUID(), listener)
            fail("Test testFetchSessionThrows failed: fetchSession did not throw when device does not match deviceID filters.")
        } catch (e: Exception) {
            Assert.assertEquals(true,  PolarInvalidArgument().toString().contentEquals(e.toString()))
        }
    }

    @Test
    fun testFetchSessionThrows_when_device_disconnected() {
        // Arrange
        val deviceId = "E123456F"
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_CLOSED

        every { session.advertisementContent.polarDeviceId } returns deviceId

        // Act&Assert
        try {
            PolarServiceClientUtils.sessionServiceReady(deviceId, UUID.randomUUID(), listener)
            fail("Test testFetchSessionThrows failed: fetchSession did not throw when device does not match deviceID filters.")
        } catch (e: Exception) {
            Assert.assertEquals(true,  PolarDeviceDisconnected().toString().contentEquals(e.toString()))
        }
    }

    @Test
    fun getRSSIValue_whenMatchingDeviceFound_returnsSessionRssi() {
        // Arrange
        val deviceId = "E123456F"
        val expectedRssi = -55

        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns setOf(session)
        every { session.advertisementContent } returns advContent
        every { advContent.polarDeviceId } returns deviceId
        every { session.rssi } returns expectedRssi

        // Act
        val result = PolarServiceClientUtils.getRSSIValue(deviceId, listener)

        // Assert
        Assert.assertEquals(expectedRssi, result)
    }

    @Test
    fun getRSSIValue_whenNoMatchingDevice_returnsMinusOne() {
        // Arrange
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns setOf(session)
        every { session.advertisementContent } returns advContent
        every { advContent.polarDeviceId } returns "AABBCCDD"

        // Act
        val result = PolarServiceClientUtils.getRSSIValue("12345678", listener)

        // Assert
        Assert.assertEquals(-1, result)
    }

    @Test
    fun getRSSIValue_whenListenerIsNull_returnsMinusOne() {
        // Act
        val result = PolarServiceClientUtils.getRSSIValue("E123456F", null)

        // Assert
        Assert.assertEquals(-1, result)
    }

    @Test
    fun getRSSIValue_whenDeviceSessionsReturnsNull_returnsMinusOne() {
        // Arrange
        val listener = mockk<BleDeviceListener>()
        every { listener.deviceSessions() } returns null

        // Act
        val result = PolarServiceClientUtils.getRSSIValue("E123456F", listener)

        // Assert
        Assert.assertEquals(-1, result)
    }
}