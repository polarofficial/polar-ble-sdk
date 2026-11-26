package com.polar.polarsensordatacollector.ui.hrbroadcast

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.testutil.HiltTestSupport
import com.polar.polarsensordatacollector.ui.utils.DataViewer
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHrBroadcastData
import com.polar.sdk.impl.BDBleApiImpl
import io.mockk.every
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HrBroadcastDialogFragmentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<DataViewer>()

    private lateinit var api: BDBleApiImpl
    private val broadcasts = MutableSharedFlow<PolarHrBroadcastData>(extraBufferCapacity = 8)

    @Before
    fun setUp() {
        runCatching { Dispatchers.resetMain() }
        composeRule.mainClock.autoAdvance = false
        api = HiltTestSupport.install()
        every { api.startListenForPolarHrBroadcasts(any()) } returns broadcasts
    }

    @After
    fun tearDown() {
        HiltTestSupport.uninstall()
    }

    private fun showDialog(): HrBroadcastDialogFragment {
        val dialog = HrBroadcastDialogFragment.newInstance()
        composeRule.runOnUiThread { 
            dialog.show(composeRule.activity.supportFragmentManager, HrBroadcastDialogFragment.TAG)
            composeRule.activity.supportFragmentManager.executePendingTransactions()
        }
        composeRule.mainClock.advanceTimeByFrame()
        return dialog
    }

    private fun string(id: Int) = composeRule.activity.getString(id)

    private fun awaitText(text: String) {
        HiltTestSupport.awaitUntil {
            composeRule.mainClock.advanceTimeByFrame()
            runCatching { composeRule.onNodeWithText(text).assertExists() }.isSuccess
        }
    }

    private fun emit(id: String, name: String, hr: Int) {
        // Wait until the ViewModel collects the flow.
        HiltTestSupport.awaitUntil {
            composeRule.mainClock.advanceTimeByFrame()
            broadcasts.subscriptionCount.value > 0
        }
        broadcasts.tryEmit(PolarHrBroadcastData(PolarDeviceInfo(id, "addr", -50, name, true), hr, false))
    }

    @Test
    fun `dialog shows title and note when no device broadcasts`() {
        // Act
        showDialog()

        // Assert
        composeRule.onNodeWithText(string(R.string.hr_broadcast_listener)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.devices_will_appear_here_note)).assertIsDisplayed()
    }

    @Test
    fun `dialog shows device card when broadcast is received`() {
        // Arrange
        showDialog()

        // Act
        emit("A1", "Polar H10 A1", 77)

        // Assert
        awaitText("Polar H10 A1")
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.hr_value, 77)).assertIsDisplayed()
        HiltTestSupport.awaitUntil {
            composeRule.mainClock.advanceTimeByFrame()
            runCatching { composeRule.onNodeWithText(string(R.string.devices_will_appear_here_note)).assertDoesNotExist() }.isSuccess
        }
    }

    @Test
    fun `device card shows start listening when stop is clicked`() {
        // Arrange
        showDialog()
        emit("A1", "Polar H10 A1", 77)
        awaitText("Polar H10 A1")

        // Act
        composeRule.onNodeWithText(string(R.string.stop_listening)).performClick()

        // Assert
        awaitText(string(R.string.start_listening))
        composeRule.onNodeWithText("---").assertIsDisplayed()
    }

    @Test
    fun `device card shows stop listening when start is clicked after stop`() {
        // Arrange
        showDialog()
        emit("A1", "Polar H10 A1", 77)
        awaitText("Polar H10 A1")
        composeRule.onNodeWithText(string(R.string.stop_listening)).performClick()
        awaitText(string(R.string.start_listening))

        // Act
        composeRule.onNodeWithText(string(R.string.start_listening)).performClick()

        // Assert
        awaitText(string(R.string.stop_listening))
    }

    @Test
    fun `close button dismisses dialog when clicked`() {
        // Arrange
        val dialog = showDialog()
        assertTrue(dialog.isAdded)

        // Act
        composeRule.onNodeWithText(string(R.string.close)).performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.runOnUiThread { composeRule.activity.supportFragmentManager.executePendingTransactions() }

        // Assert
        assertFalse(dialog.isAdded)
    }
}
