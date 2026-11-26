package com.polar.polarsensordatacollector.ui.trainingsession

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.polar.polarsensordatacollector.testutil.FileProviderTestSupport
import com.polar.polarsensordatacollector.ui.utils.DataViewer
import com.polar.sdk.api.model.trainingsession.PolarTrainingSession
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionProgress
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionReference
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class TrainingSessionDataComposablesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val reference = PolarTrainingSessionReference(LocalDate.of(2024, 3, 5), "/U/0/20240305/E/101010", emptyList(), emptyList())
    private val fetched = TrainingSessionDataUiState.FetchedData(PolarTrainingSession(reference))

    @Before
    fun setUp() {
        runCatching { Dispatchers.resetMain() }
        FileProviderTestSupport.reset()
        composeRule.mainClock.autoAdvance = false
    }

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent { MaterialTheme { content() } }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun showState(state: TrainingSessionDataUiState) {
        val viewModel = mockk<TrainingSessionDataViewModel>(relaxed = true)
        every { viewModel.trainingSessionDataUiState } returns state
        show { ShowTrainingSessionData(viewModel, reference.path) }
    }

    @Test
    fun `show training session data shows loading when fetch has started`() {
        // Act
        showState(TrainingSessionDataUiState.IsFetching)

        // Assert
        composeRule.onNodeWithText("Loading Training Session...").assertIsDisplayed()
    }

    @Test
    fun `show training session data shows progress with path when fetching`() {
        // Act
        showState(TrainingSessionDataUiState.Fetching(PolarTrainingSessionProgress(200, 50, 25)))

        // Assert
        composeRule.onNodeWithText("25%").assertIsDisplayed()
        composeRule.onNodeWithText(reference.path).assertIsDisplayed()
    }

    @Test
    fun `show training session data shows failure when fetch fails`() {
        // Act
        showState(TrainingSessionDataUiState.Failure("Fetch failed", IllegalStateException("boom")))

        // Assert
        composeRule.onNodeWithText("Fetch failed").assertIsDisplayed()
        composeRule.onNodeWithText("java.lang.IllegalStateException: boom").assertIsDisplayed()
    }

    @Test
    fun `show failed hides details when throwable is missing`() {
        // Act
        show { ShowFailed(TrainingSessionDataUiState.Failure("Fetch failed", null)) }

        // Assert
        composeRule.onNodeWithText("Details:").assertDoesNotExist()
    }

    @Test
    fun `show training session data shows data and shares file when fetched`() {
        // Arrange
        showState(fetched)
        composeRule.onNodeWithText("2024-03-05").assertIsDisplayed()

        // Act
        composeRule.onNodeWithText("Share").performClick()

        // Assert
        val chooser = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
    }

    @Test
    fun `show data opens data viewer when view is clicked`() {
        // Arrange
        val uri = Uri.parse("content://test/data.json")
        show {
            val context = LocalContext.current
            ShowData(onShare = {}, trainingSession = fetched, fileUri = uri, context = context)
        }

        // Act
        composeRule.onNodeWithText("View").performClick()

        // Assert
        val intent = shadowOf(app).nextStartedActivity
        assertNotNull(intent)
        assertEquals(DataViewer::class.java.name, intent.component?.className)
        assertEquals(uri.toString(), intent.getStringExtra("DATA_URI"))
    }

    @Test
    fun `show data hides view label when context is missing`() {
        // Arrange
        show { ShowData(onShare = {}, trainingSession = fetched, fileUri = Uri.parse("content://test/data.json"), context = null) }

        // Assert
        composeRule.onNodeWithText("Share").assertIsDisplayed()
        composeRule.onNodeWithText("View").assertDoesNotExist()
    }
}
