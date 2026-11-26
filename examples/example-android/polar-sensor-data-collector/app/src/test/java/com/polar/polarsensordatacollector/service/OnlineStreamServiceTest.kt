package com.polar.polarsensordatacollector.service

import android.app.Application
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class OnlineStreamServiceTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun setUp() {
        // The counter is static. Reset it so that tests do not affect each other.
        OnlineStreamService::class.java.getDeclaredField("activeStreamCount").apply {
            isAccessible = true
            setInt(null, 0)
        }
    }

    private fun nextStarted(): Intent? = shadowOf(app).nextStartedService

    private fun nextStopped(): Intent? = shadowOf(app).nextStoppedService

    @Test
    fun `start service starts foreground service once when first stream starts`() {
        // Act
        OnlineStreamService.startService(app)

        // Assert
        assertEquals(OnlineStreamService::class.java.name, nextStarted()?.component?.className)
        assertNull(nextStarted())
    }

    @Test
    fun `start service does not start again when service already runs`() {
        // Arrange
        OnlineStreamService.startService(app)
        nextStarted()

        // Act
        OnlineStreamService.startService(app)

        // Assert
        assertNull(nextStarted())
    }

    @Test
    fun `stop service keeps service running while other streams are active`() {
        // Arrange
        OnlineStreamService.startService(app)
        OnlineStreamService.startService(app)

        // Act
        OnlineStreamService.stopService(app)

        // Assert
        assertNull(nextStopped())
    }

    @Test
    fun `stop service stops service when last stream stops`() {
        // Arrange
        OnlineStreamService.startService(app)
        OnlineStreamService.startService(app)
        OnlineStreamService.stopService(app)

        // Act
        OnlineStreamService.stopService(app)

        // Assert
        assertEquals(OnlineStreamService::class.java.name, nextStopped()?.component?.className)
    }

    @Test
    fun `stop service does nothing when no stream is active`() {
        // Act
        OnlineStreamService.stopService(app)

        // Assert
        assertNull(nextStopped())
    }

    @Test
    fun `stop service does not go below zero when called too often`() {
        // Arrange
        OnlineStreamService.stopService(app)
        OnlineStreamService.stopService(app)

        // Act
        OnlineStreamService.startService(app)

        // Assert
        assertNotNull(nextStarted())
    }

    @Test
    fun `on start command starts foreground with connected device type and returns sticky`() {
        // Arrange
        val service = Robolectric.buildService(OnlineStreamService::class.java).create().get()

        // Act
        val result = service.onStartCommand(Intent(), 0, 1)

        // Assert
        val shadow = shadowOf(service)
        assertEquals(Service.START_STICKY, result)
        assertNotNull(shadow.lastForegroundNotification)
        assertEquals(1, shadow.lastForegroundNotificationId)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE, service.foregroundServiceType)
    }

    @Test
    fun `on start command creates low importance channel`() {
        // Arrange
        val service = Robolectric.buildService(OnlineStreamService::class.java).create().get()

        // Act
        service.onStartCommand(null, 0, 1)

        // Assert
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel("online_stream_service_channel")
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertEquals("Online Stream Service", channel.name.toString())
    }

    @Test
    fun `on bind returns null`() {
        // Arrange
        val service = Robolectric.buildService(OnlineStreamService::class.java).create().get()

        // Assert
        assertNull(service.onBind(Intent()))
    }
}
