package com.polar.polarsensordatacollector.ui.exercise

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import com.polar.polarsensordatacollector.repository.DeviceConnectionState
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.testutil.HiltTestSupport
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class ExerciseActivityTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val controllers = mutableListOf<ActivityController<ExerciseActivity>>()

    @Before
    fun setUp() {
        HiltTestSupport.install()
        composeRule.mainClock.autoAdvance = false
    }

    @After
    fun tearDown() {
        controllers.forEach { runCatching { it.pause().stop().destroy() } }
        HiltTestSupport.uninstall()
    }

    private fun build(): ActivityController<ExerciseActivity> =
        Robolectric.buildActivity(ExerciseActivity::class.java).also { controllers.add(it) }

    @Suppress("UNCHECKED_CAST")
    private fun connect(repository: PolarDeviceRepository, id: String) {
        val field = PolarDeviceRepository::class.java.getDeclaredField("_deviceConnectionStatus")
        field.isAccessible = true
        (field.get(repository) as MutableStateFlow<DeviceConnectionState>).value =
            DeviceConnectionState.DeviceConnected(id)
    }

    private fun startConnected(): ExerciseActivity {
        val first = build().create()
        connect(first.get().polarDeviceRepository, "DEV1")
        val second = build().create().start().resume().visible()
        shadowOf(Looper.getMainLooper()).idle()
        return second.get()
    }

    private fun frames() = repeat(3) {
        Snapshot.sendApplyNotifications()
        shadowOf(Looper.getMainLooper()).idle()
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
    fun `on create shows controlling toast when device connected`() {
        // Act
        val activity = startConnected()

        // Assert
        assertFalse(activity.isFinishing)
        assertTrue(ShadowToast.shownToastCount() > 0)
        assertEquals("Controlling device DEV1", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `start button starts service when clicked`() {
        // Arrange
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val activity = startConnected()
        frames()

        // Act
        composeRule.onNodeWithText("Start")
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        frames()

        // Assert
        assertEquals("Starting…", ShadowToast.getTextOfLatestToast())
        val service = shadowOf(activity.applicationContext as Application).nextStartedService
        assertEquals(ExerciseService::class.java.name, service?.component?.className)
        assertEquals("DEV1", service?.getStringExtra(ExerciseService.EXTRA_DEVICE_ID))
    }

    @Test
    fun `launch starts activity when called`() {
        // Arrange
        val context = build().create().get()

        // Act
        ExerciseActivity.launch(context)

        // Assert
        val started = shadowOf(context).nextStartedActivity
        assertEquals(ExerciseActivity::class.java.name, started.component?.className)
    }
}
