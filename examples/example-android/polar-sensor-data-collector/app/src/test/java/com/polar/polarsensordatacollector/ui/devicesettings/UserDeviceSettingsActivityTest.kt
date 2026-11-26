package com.polar.polarsensordatacollector.ui.devicesettings

import android.content.Intent
import android.os.Looper
import android.widget.Button
import androidx.test.core.app.ApplicationProvider
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.testutil.HiltTestSupport
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class UserDeviceSettingsActivityTest {

    private val controllers = mutableListOf<ActivityController<UserDeviceSettingsActivity>>()

    @Before
    fun setUp() {
        HiltTestSupport.install()
    }

    @After
    fun tearDown() {
        controllers.forEach { runCatching { it.pause().stop().destroy() } }
        HiltTestSupport.uninstall()
    }

    private fun start(): UserDeviceSettingsActivity {
        val intent = Intent(ApplicationProvider.getApplicationContext(), UserDeviceSettingsActivity::class.java)
            .putExtra(UserDeviceSettingsViewModel.KEY_IDENTIFIER, "DEV1")
        val controller = Robolectric.buildActivity(UserDeviceSettingsActivity::class.java, intent)
        controllers.add(controller)
        val activity = controller.create().start().resume().get()
        shadowOf(Looper.getMainLooper()).idle()
        return activity
    }

    @Test
    fun `on create inflates views when started`() {
        // Act
        val activity = start()

        // Assert
        assertNotNull(activity.findViewById<Button>(R.id.submit_device_settings_button))
        assertNotNull(activity.findViewById<Button>(R.id.cancel_device_settings_button))
    }

    @Test
    fun `cancel button shows not saved toast when clicked`() {
        // Arrange
        val activity = start()

        // Act
        activity.findViewById<Button>(R.id.cancel_device_settings_button).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        // Assert
        val toasts = generateSequence { ShadowToast.getTextOfLatestToast() }.take(1).toList()
        assertNotNull(toasts.firstOrNull())
    }

    @Test
    fun `ok button saves settings and finishes when clicked`() {
        // Arrange
        val activity = start()

        // Act
        activity.findViewById<Button>(R.id.submit_device_settings_button).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        // Assert
        HiltTestSupport.awaitUntil { activity.isFinishing }
        assertTrue(activity.isFinishing)
    }
}
