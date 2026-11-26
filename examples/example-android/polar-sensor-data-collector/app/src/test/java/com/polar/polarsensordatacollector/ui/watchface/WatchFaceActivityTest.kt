package com.polar.polarsensordatacollector.ui.watchface

import android.os.Looper
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
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
class WatchFaceActivityTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val controllers = mutableListOf<ActivityController<WatchFaceActivity>>()

    @Before
    fun setUp() {
        HiltTestSupport.install()
    }

    @After
    fun tearDown() {
        controllers.forEach { runCatching { it.pause().stop().destroy() } }
        HiltTestSupport.uninstall()
    }

    private fun build(): ActivityController<WatchFaceActivity> =
        Robolectric.buildActivity(WatchFaceActivity::class.java).also { controllers.add(it) }

    @Suppress("UNCHECKED_CAST")
    private fun connect(repository: PolarDeviceRepository, id: String) {
        val field = PolarDeviceRepository::class.java.getDeclaredField("_deviceConnectionStatus")
        field.isAccessible = true
        (field.get(repository) as MutableStateFlow<DeviceConnectionState>).value =
            DeviceConnectionState.DeviceConnected(id)
    }

    private fun startConnected(): WatchFaceActivity {
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
    fun `on create shows title and slots when device connected`() {
        // Act
        val activity = startConnected()
        frames()

        // Assert
        assertFalse(activity.isFinishing)
        composeRule.onNodeWithText("Watch Face Complications").assertExists()
        (1..SLOT_COUNT).forEach { composeRule.onNodeWithText("Slot $it").assertExists() }
        composeRule.onNodeWithText("Apply to device").assertExists()
    }
}
