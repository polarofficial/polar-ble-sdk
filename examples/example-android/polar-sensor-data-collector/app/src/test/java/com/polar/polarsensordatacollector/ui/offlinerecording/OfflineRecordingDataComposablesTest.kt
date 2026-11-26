package com.polar.polarsensordatacollector.ui.offlinerecording

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
import com.polar.polarsensordatacollector.ui.utils.DataViewer
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarSensorSetting
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h2400dp")
class OfflineRecordingDataComposablesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val path = "/U/0/20220824/R/075122/ACC.REC"
    private val uri = Uri.parse("content://test/acc.txt")
    private val settings = PolarSensorSetting(
        mapOf(
            PolarSensorSetting.SettingType.SAMPLE_RATE to 52,
            PolarSensorSetting.SettingType.RESOLUTION to 16,
            PolarSensorSetting.SettingType.RANGE to 8,
            PolarSensorSetting.SettingType.CHANNELS to 3
        )
    )

    @Before
    fun setUp() {
        runCatching { Dispatchers.resetMain() }
        composeRule.mainClock.autoAdvance = false
        // The composables read a private top-level path. The fragment normally sets it.
        previousPath = pathField.get(null)
        pathField.set(null, path)
    }

    @After
    fun tearDown() {
        pathField.set(null, previousPath)
    }

    private val pathField = Class.forName("com.polar.polarsensordatacollector.ui.offlinerecording.OfflineRecordingDataFragmentKt")
        .getDeclaredField("path").apply { isAccessible = true }
    private var previousPath: Any? = null

    private fun data(usedSettings: PolarSensorSetting? = settings) =
        RecordingDataUiState.FetchedData(RecordingData("2022-08-24 07:51:22", usedSettings, uri, 2048L, 12.5))

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent { MaterialTheme { content() } }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun showState(state: RecordingDataUiState, viewModel: RecordingDataViewModel = mockk(relaxed = true), onBack: () -> Boolean = { true }) {
        every { viewModel.recordingDataUiState } returns state
        show { ShowRecordingData(viewModel, onBack) }
    }

    @Test
    fun `show recording data shows loading without progress when deleting`() {
        // Act
        showState(RecordingDataUiState.IsDeleting)

        // Assert
        composeRule.onNodeWithText("Loading Offline Recording...").assertIsDisplayed()
    }

    @Test
    fun `show recording data shows progress and path when loading`() {
        // Act
        showState(RecordingDataUiState.Loading(2048, PolarBleApi.PolarDeviceDataType.ACC, OfflineRecordingProgress(512, 2048, 25)))

        // Assert
        composeRule.onNodeWithText("25%").assertIsDisplayed()
        composeRule.onNodeWithText(path).assertIsDisplayed()
    }

    @Test
    fun `show recording data shows failure when fetch fails`() {
        // Act
        showState(RecordingDataUiState.Failure("Fetch failed", IllegalStateException("boom")))

        // Assert
        composeRule.onNodeWithText("Fetch failed").assertIsDisplayed()
        composeRule.onNodeWithText("java.lang.IllegalStateException: boom").assertIsDisplayed()
    }

    @Test
    fun `show recording data hides details when failure has no throwable`() {
        // Act
        showState(RecordingDataUiState.Failure("Fetch failed", null))

        // Assert
        composeRule.onNodeWithText("Details:").assertDoesNotExist()
    }

    @Test
    fun `show recording data shows size rate and settings when data is fetched`() {
        // Act
        showState(data())

        // Assert
        composeRule.onNodeWithText("2.00 kB").assertIsDisplayed()
        composeRule.onNodeWithText("12.50 kB/s").assertIsDisplayed()
        composeRule.onNodeWithText(path).assertIsDisplayed()
        composeRule.onNodeWithText("2022-08-24 07:51:22").assertIsDisplayed()
        composeRule.onNodeWithText("52Hz").assertIsDisplayed()
        composeRule.onNodeWithText("16bits").assertIsDisplayed()
    }

    @Test
    fun `show data hides settings when recording has no settings`() {
        // Act
        showState(data(usedSettings = null))

        // Assert
        composeRule.onNodeWithText("Recording settings").assertDoesNotExist()
    }

    @Test
    fun `show recording data starts share intent when share is clicked`() {
        // Arrange
        showState(data())

        // Act
        composeRule.onNodeWithText("Share").performClick()

        // Assert
        val intent = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.action)
    }

    @Test
    fun `show recording data deletes recording when delete is clicked`() {
        // Arrange
        val viewModel = mockk<RecordingDataViewModel>(relaxed = true)
        showState(data(), viewModel)

        // Act
        composeRule.onNodeWithText("Delete").performClick()

        // Assert
        verify { viewModel.deleteRecording() }
    }

    @Test
    fun `show data opens data viewer when view is clicked`() {
        // Arrange
        show {
            val context = LocalContext.current
            ShowData(onShare = {}, onDelete = {}, recording = data(), path = path, context = context)
        }

        // Act
        composeRule.onNodeWithText("View").performClick()

        // Assert
        val intent = shadowOf(app).nextStartedActivity
        assertEquals(DataViewer::class.java.name, intent.component?.className)
        assertEquals(uri.toString(), intent.getStringExtra("DATA_URI"))
    }

    @Test
    fun `show recording data navigates back and shows toast when recording is deleted`() {
        // Arrange
        var navigatedBack = false

        // Act
        showState(RecordingDataUiState.RecordingDeleted, onBack = { navigatedBack = true; true })
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        // Assert
        assertEquals(true, navigatedBack)
    }

    @Test
    fun `show recording data shows nothing when idle`() {
        // Act
        showState(RecordingDataUiState.Idle)

        // Assert
        composeRule.onNodeWithText("Delete").assertDoesNotExist()
    }
}
