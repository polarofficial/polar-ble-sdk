package com.polar.polarsensordatacollector.ui.trainingsession

import android.os.Bundle
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.polar.polarsensordatacollector.testutil.HiltTestSupport
import com.polar.polarsensordatacollector.ui.utils.DataViewer
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionReference
import com.polar.sdk.impl.BDBleApiImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class TrainingSessionListFragmentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<DataViewer>()

    private val deviceId = "dev1"
    private lateinit var api: BDBleApiImpl
    private val session = PolarTrainingSessionReference(LocalDate.of(2024, 3, 5), "/U/0/20240305/E/101010", emptyList(), emptyList())

    @Before
    fun setUp() {
        runCatching { Dispatchers.resetMain() }
        composeRule.mainClock.autoAdvance = false
        api = HiltTestSupport.install()
    }

    @After
    fun tearDown() {
        HiltTestSupport.uninstall()
    }

    private fun showFragment() {
        val fragment = TrainingSessionListFragment()
        fragment.arguments = Bundle().apply {
            putString("deviceIdFragmentArgument", deviceId)
            putString("fromDateFragmentArgument", "20240301")
            putString("toDateFragmentArgument", "20240331")
        }
        composeRule.runOnUiThread {
            composeRule.activity.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow()
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun awaitNode(text: String) {
        HiltTestSupport.awaitUntil {
            composeRule.mainClock.advanceTimeByFrame()
            runCatching { composeRule.onNodeWithText(text).assertExists() }.isSuccess
        }
    }

    @Test
    fun `on resume lists training sessions and shows them`() {
        // Arrange
        every { api.getTrainingSessionReferences(deviceId, any(), any()) } returns flowOf(session)

        // Act
        showFragment()

        // Assert
        awaitNode(session.path)
    }

    @Test
    fun `on resume shows empty text when device has no sessions`() {
        // Arrange
        every { api.getTrainingSessionReferences(deviceId, any(), any()) } returns flowOf()

        // Act
        showFragment()

        // Assert
        awaitNode("No training sessions")
    }

    @Test
    fun `on resume shows failure when listing fails`() {
        // Arrange
        every { api.getTrainingSessionReferences(deviceId, any(), any()) } returns flow { throw IllegalStateException("boom") }

        // Act
        showFragment()

        // Assert
        awaitNode("Fetching of trainingSessions failed")
    }

    @Test
    fun `delete button deletes session and removes it when clicked`() {
        // Arrange
        every { api.getTrainingSessionReferences(deviceId, any(), any()) } returns flowOf(session)
        coEvery { api.deleteTrainingSession(deviceId, any()) } returns Unit
        showFragment()
        HiltTestSupport.awaitUntil {
            composeRule.mainClock.advanceTimeByFrame()
            runCatching { composeRule.onNodeWithContentDescription("Delete file").assertExists() }.isSuccess
        }

        // Act
        composeRule.onNodeWithContentDescription("Delete file").performClick()

        // Assert
        coVerify(timeout = 5_000) { api.deleteTrainingSession(deviceId, any()) }
        awaitNode("No training sessions")
    }
}
