package com.polar.polarsensordatacollector.ui.offlinerecording

import androidx.compose.material.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarOfflineRecordingEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
class OfflineRecordingsListComposablesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val acc = entry("ACC", PolarBleApi.PolarDeviceDataType.ACC)
    private val hr = entry("HR", PolarBleApi.PolarDeviceDataType.HR)

    private fun entry(name: String, type: PolarBleApi.PolarDeviceDataType) =
        PolarOfflineRecordingEntry("/U/0/20220824/R/075122/$name.REC", 2048L, LocalDateTime.of(2022, 8, 24, 7, 51, 22), type)

    @Before
    fun setUp() {
        runCatching { Dispatchers.resetMain() }
        composeRule.mainClock.autoAdvance = false
    }

    private fun show(
        state: OfflineRecordingsUiState,
        onClick: (String, String) -> Unit = { _, _ -> },
        onDelete: (PolarOfflineRecordingEntry) -> Unit = {}
    ) {
        composeRule.setContent {
            MaterialTheme { RecordedItemsList(onRefresh = {}, onClickItem = onClick, onDelete = onDelete, offlineRecordingUiState = state) }
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    @Test
    fun `recorded items list shows name date and size when fetch succeeds`() {
        // Act
        show(OfflineRecordingsUiState(OfflineRecordingFetch.Success(listOf(acc))))

        // Assert
        composeRule.onNodeWithText("ACC.REC").assertIsDisplayed()
        composeRule.onNodeWithText("2022-08-24 07:51:22").assertIsDisplayed()
        composeRule.onNodeWithText("Size: 2.00 KB").assertIsDisplayed()
    }

    @Test
    fun `recorded items list shows empty text when no recordings`() {
        // Act
        show(OfflineRecordingsUiState(OfflineRecordingFetch.Success(emptyList())))

        // Assert
        composeRule.onNodeWithText("No recordings").assertIsDisplayed()
    }

    @Test
    fun `recorded items list shows message and details when fetch fails`() {
        // Act
        show(OfflineRecordingsUiState(OfflineRecordingFetch.Failure("Listing failed", IllegalStateException("boom"))))

        // Assert
        composeRule.onNodeWithText("Listing failed").assertIsDisplayed()
        composeRule.onNodeWithText("Details:").assertIsDisplayed()
    }

    @Test
    fun `recorded items list hides details when failure has no throwable`() {
        // Act
        show(OfflineRecordingsUiState(OfflineRecordingFetch.Failure("Listing failed", null)))

        // Assert
        composeRule.onNodeWithText("Details:").assertDoesNotExist()
    }

    @Test
    fun `recorded items list disables delete when fetch is in progress`() {
        // Act
        show(OfflineRecordingsUiState(OfflineRecordingFetch.InProgress(listOf(acc, hr))))

        // Assert
        composeRule.onNodeWithText("ACC.REC").assertIsDisplayed()
        composeRule.onNodeWithText("HR.REC").assertIsDisplayed()
        val deleteButtons = composeRule.onAllDeleteButtons()
        assertEquals(2, deleteButtons.size)
        deleteButtons.forEach { it.assertIsNotEnabled() }
    }

    @Test
    fun `recorded items list calls on click with path and name when item is clicked`() {
        // Arrange
        var clicked: Pair<String, String>? = null
        show(OfflineRecordingsUiState(OfflineRecordingFetch.Success(listOf(acc))), onClick = { path, name -> clicked = path to name })

        // Act
        composeRule.onNodeWithText("ACC.REC").performClick()

        // Assert
        assertEquals(acc.path to "ACC.REC", clicked)
    }

    @Test
    fun `recorded items list calls on delete when delete is clicked`() {
        // Arrange
        var deleted: PolarOfflineRecordingEntry? = null
        show(OfflineRecordingsUiState(OfflineRecordingFetch.Success(listOf(acc))), onDelete = { deleted = it })

        // Act
        composeRule.onNodeWithContentDescription("Delete file").assertIsEnabled().performClick()

        // Assert
        assertEquals(acc, deleted)
    }

    @Test
    fun `recorded items list disables other items when one item is deleting`() {
        // Act
        show(OfflineRecordingsUiState(OfflineRecordingFetch.Success(listOf(acc, hr)), deletingEntryPath = acc.path))

        // Assert
        val deleteButtons = composeRule.onAllDeleteButtons()
        assertEquals(1, deleteButtons.size)
        deleteButtons.forEach { it.assertIsNotEnabled() }
    }

    @Test
    fun `recording shows item for every data type`() {
        // Arrange
        val types = listOf(
            PolarBleApi.PolarDeviceDataType.ACC,
            PolarBleApi.PolarDeviceDataType.GYRO,
            PolarBleApi.PolarDeviceDataType.MAGNETOMETER,
            PolarBleApi.PolarDeviceDataType.PPG,
            PolarBleApi.PolarDeviceDataType.PPI,
            PolarBleApi.PolarDeviceDataType.HR,
            PolarBleApi.PolarDeviceDataType.ECG
        )
        val entries = types.map { entry(it.name, it) }

        // Act
        composeRule.setContent {
            MaterialTheme {
                androidx.compose.foundation.layout.Column {
                    entries.forEach { Recording(it, onClick = { _, _ -> }, onDelete = {}) }
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()

        // Assert
        types.forEach { composeRule.onNodeWithText("${it.name}.REC").assertExists() }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllDeleteButtons() =
        onAllNodesWithContentDescription("Delete file").fetchSemanticsNodes().indices.map {
            onAllNodesWithContentDescription("Delete file")[it]
        }
}
