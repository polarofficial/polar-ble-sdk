package com.polar.polarsensordatacollector.ui.activity

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.polar.polarsensordatacollector.ui.utils.DataViewer
import com.polar.sdk.api.PolarBleApi
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h2400dp")
class ActivityRecordingDataComposablesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val uri = Uri.parse("content://test/hr.txt")

    @Before
    fun setUp() {
        runCatching { Dispatchers.resetMain() }
        composeRule.mainClock.autoAdvance = false
    }

    private fun data() = ActivityDataUiState.FetchedData(
        ActivityRecordingData("2022-08-24", "2022-08-25", uri, PolarBleApi.PolarActivityDataType.HR_SAMPLES)
    )

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { MaterialTheme { content() } }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun showState(state: ActivityDataUiState) {
        val viewModel = mockk<ActivityRecordingDataViewModel>(relaxed = true)
        every { viewModel.activityDataUiState } returns state
        every { viewModel.elapsedTime } returns 42L
        show { ShowActivityData(viewModel) { true } }
    }

    @Test
    fun `show activity data shows path and start date when data is fetched`() {
        // Act
        showState(data())

        // Assert
        composeRule.onNodeWithText(uri.toString()).assertIsDisplayed()
        composeRule.onNodeWithText("2022-08-24").assertIsDisplayed()
    }

    @Test
    fun `show activity data shows failure when fetch fails`() {
        // Act
        showState(ActivityDataUiState.Failure("Fetch failed", IllegalStateException("boom")))

        // Assert
        composeRule.onNodeWithText("Fetch failed").assertIsDisplayed()
        composeRule.onNodeWithText("java.lang.IllegalStateException: boom").assertIsDisplayed()
    }

    @Test
    fun `show activity data hides details when failure has no throwable`() {
        // Act
        showState(ActivityDataUiState.Failure("Fetch failed", null))

        // Assert
        composeRule.onNodeWithText("Details:").assertDoesNotExist()
    }

    @Test
    fun `show activity data shows no data when fetching`() {
        // Act
        showState(ActivityDataUiState.IsFetching)

        // Assert
        composeRule.onNodeWithText("Path").assertDoesNotExist()
    }

    @Test
    fun `show activity data starts share intent when share is clicked`() {
        // Arrange
        showState(data())

        // Act
        composeRule.onNodeWithText("Share").performClick()

        // Assert
        val intent = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.action)
        assertEquals("Recorded HR_SAMPLES data", intent.getStringExtra(Intent.EXTRA_SUBJECT))
    }

    @Test
    fun `show data opens data viewer when view is clicked`() {
        // Arrange
        show { ShowData(onShare = {}, recording = data(), context = LocalContext.current) }

        // Act
        composeRule.onNodeWithText("View").performClick()

        // Assert
        val intent = shadowOf(app).nextStartedActivity
        assertEquals(DataViewer::class.java.name, intent.component?.className)
        assertEquals(uri.toString(), intent.getStringExtra("DATA_URI"))
    }
}
