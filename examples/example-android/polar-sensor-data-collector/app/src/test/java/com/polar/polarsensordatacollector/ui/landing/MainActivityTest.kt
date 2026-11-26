package com.polar.polarsensordatacollector.ui.landing

import android.content.Intent
import android.content.SharedPreferences
import android.os.Environment
import android.os.Looper
import android.view.WindowManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility
import com.polar.polarsensordatacollector.di.PolarBleSdkModule
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.sdk.impl.BDBleApiImpl
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    private val controllers = mutableListOf<ActivityController<MainActivity>>()

    @Before
    fun setUp() {
        mockkStatic(MasterKeys::class, EncryptedSharedPreferences::class)
        every { MasterKeys.getOrCreate(any()) } returns "test-master-key"
        every { EncryptedSharedPreferences.create(any<String>(), any<String>(), any(), any(), any()) } returns
            mockk<SharedPreferences>(relaxed = true)
        mockkStatic(Environment::class)
        every { Environment.isExternalStorageManager() } returns false
        mockkObject(PolarBleSdkModule)
        every { PolarBleSdkModule.providePolarBleSdkApi(any()) } returns mockk<BDBleApiImpl>(relaxed = true)
    }

    @After
    fun tearDown() {
        controllers.forEach { runCatching { it.pause().stop().destroy() } }
        unmockkAll()
    }

    private fun start(): ActivityController<MainActivity> =
        Robolectric.buildActivity(MainActivity::class.java).also { controllers.add(it) }
            .create().start().resume().also { shadowOf(Looper.getMainLooper()).idle() }

    @Test
    fun `on create keeps screen on and requests permissions when started`() {
        // Act
        val activity = start().get()

        // Assert
        val flags = activity.window.attributes.flags
        assertTrue(flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
        val requested = shadowOf(activity).lastRequestedPermission
        assertNotNull(requested)
    }

    @Test
    fun `on create requests all files access when storage not managed`() {
        // Act
        val activity = start().get()

        // Assert
        val next = shadowOf(activity).nextStartedActivityForResult
        assertNotNull(next)
    }

    private fun sendResult(activity: MainActivity, requestCode: Int) {
        val method = MainActivity::class.java.getDeclaredMethod(
            "onActivityResult", Int::class.java, Int::class.java, Intent::class.java
        )
        method.isAccessible = true
        method.invoke(activity, requestCode, 0, Intent())
    }

    @Test
    fun `on activity result does not initialize capabilities when all files access denied`() {
        // Arrange
        mockkObject(BlePolarDeviceCapabilitiesUtility)
        every { BlePolarDeviceCapabilitiesUtility.initialize(any()) } returns Unit
        val activity = start().get()

        // Act
        sendResult(activity, 99)

        // Assert
        verify(exactly = 0) { BlePolarDeviceCapabilitiesUtility.initialize(any()) }
    }

    @Test
    fun `on activity result initializes capabilities when all files access granted`() {
        // Arrange
        mockkObject(BlePolarDeviceCapabilitiesUtility)
        every { BlePolarDeviceCapabilitiesUtility.initialize(any()) } returns Unit
        val activity = start().get()
        every { Environment.isExternalStorageManager() } returns true

        // Act
        sendResult(activity, 99)

        // Assert
        verify(exactly = 1) { BlePolarDeviceCapabilitiesUtility.initialize(any()) }
    }

    @Test
    fun `on activity result ignores result when request code is unknown`() {
        // Arrange
        mockkObject(BlePolarDeviceCapabilitiesUtility)
        every { BlePolarDeviceCapabilitiesUtility.initialize(any()) } returns Unit
        every { Environment.isExternalStorageManager() } returns true
        val activity = start().get()

        // Act
        sendResult(activity, 1234)
        activity.onRequestPermissionsResult(1, arrayOf(), intArrayOf())

        // Assert
        verify(exactly = 0) { BlePolarDeviceCapabilitiesUtility.initialize(any()) }
    }

    @Test
    fun `on destroy shuts down sdk when activity destroyed`() {
        // Arrange
        val controller = start()
        val repository: PolarDeviceRepository = io.mockk.spyk(controller.get().polarDeviceRepository)
        controller.get().polarDeviceRepository = repository

        // Act
        controller.pause().stop().destroy()

        // Assert
        verify { repository.sdkShutDown() }
    }
}
