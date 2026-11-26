package com.polar.polarsensordatacollector.ui.genericapi

import android.content.SharedPreferences
import android.os.Looper
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.polar.polarsensordatacollector.di.PolarBleSdkModule
import com.polar.polarsensordatacollector.repository.DeviceConnectionState
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.sdk.impl.BDBleApiImpl
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class GenericApiActivityTest {

    private val controllers = mutableListOf<ActivityController<GenericApiActivity>>()

    @Before
    fun setUp() {
        mockkStatic(MasterKeys::class, EncryptedSharedPreferences::class)
        every { MasterKeys.getOrCreate(any()) } returns "test-master-key"
        every { EncryptedSharedPreferences.create(any<String>(), any<String>(), any(), any(), any()) } returns
            mockk<SharedPreferences>(relaxed = true)
        mockkObject(PolarBleSdkModule)
        every { PolarBleSdkModule.providePolarBleSdkApi(any()) } returns mockk<BDBleApiImpl>(relaxed = true)
    }

    @After
    fun tearDown() {
        controllers.forEach { runCatching { it.pause().stop().destroy() } }
        unmockkAll()
    }

    private fun build(): ActivityController<GenericApiActivity> =
        Robolectric.buildActivity(GenericApiActivity::class.java).also { controllers.add(it) }

    @Suppress("UNCHECKED_CAST")
    private fun connect(repository: PolarDeviceRepository, id: String) {
        val field = PolarDeviceRepository::class.java.getDeclaredField("_deviceConnectionStatus")
        field.isAccessible = true
        (field.get(repository) as MutableStateFlow<DeviceConnectionState>).value =
            DeviceConnectionState.DeviceConnected(id)
    }

    @Test
    fun `on create shows toast and finishes when no device connected`() {
        // Act
        val controller = build().create()

        // Assert
        assertEquals("No device connected", ShadowToast.getTextOfLatestToast())
        assertTrue(controller.get().isFinishing)
    }

    @Test
    fun `on create keeps activity open when device connected`() {
        // Arrange
        val first = build().create()
        connect(first.get().polarDeviceRepository, "DEV1")

        // Act
        val second = build().create().start().resume()
        shadowOf(Looper.getMainLooper()).idle()

        // Assert
        assertFalse(second.get().isFinishing)
        assertEquals("DEV1", second.get().deviceId)
    }

    @Test
    fun `launch starts activity when called`() {
        // Arrange
        val context = build().create().get()

        // Act
        GenericApiActivity.launch(context)

        // Assert
        val started = shadowOf(context).nextStartedActivity
        assertEquals(GenericApiActivity::class.java.name, started.component?.className)
    }
}
