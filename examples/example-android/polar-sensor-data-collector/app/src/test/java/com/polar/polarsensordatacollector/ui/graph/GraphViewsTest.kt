package com.polar.polarsensordatacollector.ui.graph

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GraphViewsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        runCatching { Dispatchers.resetMain() }
        composeRule.mainClock.autoAdvance = false
        clearHolders()
    }

    @After
    fun tearDown() {
        clearHolders()
    }

    private fun clearHolders() {
        HrDataHolder.clear()
        AccDataHolder.clear()
        EcgDataHolder.clear()
    }

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent { content() }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun draw() {
        composeRule.mainClock.advanceTimeByFrame()
    }

    @Test
    fun `hr graph view shows zero hr when no samples`() {
        // Act
        show { HrGraphView(onClose = {}) }
        draw()

        // Assert
        composeRule.onNodeWithText("Hr: 0 bpm").assertIsDisplayed()
    }

    @Test
    fun `hr graph view shows latest hr when samples exist`() {
        // Arrange
        listOf(55, 60, 120, 130, 125).forEach { HrDataHolder.updateHr(it) }

        // Act
        show { HrGraphView(onClose = {}) }
        draw()

        // Assert
        composeRule.onNodeWithText("Hr: 125 bpm").assertIsDisplayed()
    }

    @Test
    fun `hr graph view handles extreme values without crash`() {
        // Arrange
        listOf(30, 230, 41, 219).forEach { HrDataHolder.updateHr(it) }

        // Act
        show { HrGraphView(onClose = {}) }
        draw()

        // Assert
        composeRule.onNodeWithText("Hr: 219 bpm").assertIsDisplayed()
    }

    @Test
    fun `hr graph view calls on close when close clicked`() {
        // Arrange
        var closed = 0
        show { HrGraphView(onClose = { closed++ }) }

        // Act
        composeRule.onNodeWithText("CLOSE").performClick()

        // Assert
        assertEquals(1, closed)
    }

    @Test
    fun `hr graph view updates when new sample arrives`() {
        // Arrange
        show { HrGraphView(onClose = {}) }

        // Act
        HrDataHolder.updateHr(88)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()

        // Assert
        composeRule.onNodeWithText("Hr: 88 bpm").assertIsDisplayed()
    }

    @Test
    fun `acc graph view shows placeholders when no samples`() {
        // Act
        show { AccGraphView(onClose = {}) }
        draw()

        // Assert
        composeRule.onNodeWithText("ACC x:-- y:-- z:--").assertIsDisplayed()
    }

    @Test
    fun `acc graph view shows latest sample when samples exist`() {
        // Arrange
        AccDataHolder.updateAcc(1, 2, 3)
        AccDataHolder.updateAcc(-100, 50, 980)
        AccDataHolder.updateAcc(10, 10, 10)

        // Act
        show { AccGraphView(onClose = {}) }
        composeRule.mainClock.advanceTimeByFrame()
        draw()

        // Assert
        composeRule.onNodeWithText("ACC x:10 y:10 z:10").assertIsDisplayed()
    }

    @Test
    fun `acc graph view calls on close when close clicked`() {
        // Arrange
        var closed = 0
        show { AccGraphView(onClose = { closed++ }) }

        // Act
        composeRule.onNodeWithText("CLOSE").performClick()

        // Assert
        assertEquals(1, closed)
    }

    @Test
    fun `ecg graph view shows no data value when no samples`() {
        // Act
        show { EcgGraphView(onClose = {}) }
        draw()

        // Assert
        composeRule.onNode(hasText("ECG -- µV")).assertIsDisplayed()
        composeRule.onNodeWithText("CLOSE").assertIsDisplayed()
    }

    @Test
    fun `ecg graph view draws samples when samples exist`() {
        // Arrange
        (0 until 400).forEach { EcgDataHolder.updateEcg((it % 50) * 20 - 400) }

        // Act
        show { EcgGraphView(onClose = {}) }
        composeRule.mainClock.advanceTimeByFrame()
        draw()

        // Assert
        composeRule.onNode(hasText("ECG 580 µV")).assertIsDisplayed()
        composeRule.onNodeWithText("CLOSE").assertIsDisplayed()
    }

    @Test
    fun `ecg graph view draws flat signal when all samples equal`() {
        // Arrange
        (0 until 20).forEach { EcgDataHolder.updateEcg(100) }

        // Act
        show { EcgGraphView(onClose = {}) }
        composeRule.mainClock.advanceTimeByFrame()
        draw()

        // Assert
        composeRule.onNode(hasText("ECG 100 µV")).assertIsDisplayed()
        composeRule.onNodeWithText("CLOSE").assertIsDisplayed()
    }

    @Test
    fun `ecg graph view calls on close when close clicked`() {
        // Arrange
        var closed = 0
        show { EcgGraphView(onClose = { closed++ }) }

        // Act
        composeRule.onNodeWithText("CLOSE").performClick()

        // Assert
        assertEquals(1, closed)
    }
}
