package com.polar.polarsensordatacollector.ui.appsettings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.polar.polarsensordatacollector.BuildConfig
import com.polar.polarsensordatacollector.testutil.HiltTestSupport
import com.polar.polarsensordatacollector.ui.utils.DataViewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AboutFragmentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<DataViewer>()

    @Before
    fun setUp() {
        // Another test can leave a test Main dispatcher. Remove it so Compose can become idle.
        runCatching { Dispatchers.resetMain() }
        // Stop the automatic clock. A Compose clock that never gets idle can block the test.
        composeRule.mainClock.autoAdvance = false
        HiltTestSupport.install()
    }

    @After
    fun tearDown() {
        HiltTestSupport.uninstall()
    }

    private fun showFragment() {
        composeRule.runOnUiThread {
            composeRule.activity.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, AboutFragment())
                .commitNow()
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    @Test
    fun `on create view shows labels for both versions`() {
        // Act
        showFragment()

        // Assert
        composeRule.onNodeWithText("PSDC version").assertIsDisplayed()
        composeRule.onNodeWithText("Using Polar BLE SDK").assertIsDisplayed()
    }

    @Test
    fun `on create view shows app version from build config`() {
        // Act
        showFragment()

        // Assert
        composeRule.onNodeWithText(BuildConfig.VERSION_NAME).assertIsDisplayed()
    }
}
