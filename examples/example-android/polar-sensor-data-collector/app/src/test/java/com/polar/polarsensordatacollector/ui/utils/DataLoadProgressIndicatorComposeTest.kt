package com.polar.polarsensordatacollector.ui.utils

import androidx.compose.material.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataLoadProgressIndicatorComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        // Another test can leave a test Main dispatcher. Remove it so Compose can become idle.
        runCatching { Dispatchers.resetMain() }
        // Stop the automatic clock. A Compose clock that never gets idle can block the test.
        composeRule.mainClock.autoAdvance = false
    }

    @Test
    fun `indicator shows only loading text when progress is null`() {
        // Arrange
        composeRule.setContent { MaterialTheme { DataLoadProgressIndicator(progress = null, dataType = "ACC") } }
        composeRule.mainClock.advanceTimeByFrame()

        // Assert
        composeRule.onNodeWithText("Loading ACC...").assertIsDisplayed()
        composeRule.onNodeWithText("%", substring = true).assertDoesNotExist()
    }

    @Test
    fun `indicator shows percent and bytes when progress is set`() {
        // Arrange
        val progress = DataLoadProgress(completedBytes = 512, totalBytes = 2048, progressPercent = 25)
        composeRule.setContent { MaterialTheme { DataLoadProgressIndicator(progress, "HR") } }
        composeRule.mainClock.advanceTimeByFrame()

        // Assert
        composeRule.onNodeWithText("Loading HR...").assertIsDisplayed()
        composeRule.onNodeWithText("25%").assertIsDisplayed()
        composeRule.onNodeWithText("${formatBytes(512)} / ${formatBytes(2048)}").assertIsDisplayed()
    }

    @Test
    fun `indicator shows path when progress has path`() {
        // Arrange
        val progress = DataLoadProgress(1, 2, 50, path = "/U/0/FILE.BPB")
        composeRule.setContent { MaterialTheme { DataLoadProgressIndicator(progress, "PPG") } }
        composeRule.mainClock.advanceTimeByFrame()

        // Assert
        composeRule.onNodeWithText("/U/0/FILE.BPB").assertIsDisplayed()
    }

    @Test
    fun `indicator hides path when progress has no path`() {
        // Arrange
        val progress = DataLoadProgress(1, 2, 50)
        composeRule.setContent { MaterialTheme { DataLoadProgressIndicator(progress, "PPG") } }
        composeRule.mainClock.advanceTimeByFrame()

        // Assert
        composeRule.onNodeWithText("FILE.BPB", substring = true).assertDoesNotExist()
    }
}
