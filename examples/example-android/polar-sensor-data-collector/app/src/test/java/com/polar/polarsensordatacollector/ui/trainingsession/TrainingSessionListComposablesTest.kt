package com.polar.polarsensordatacollector.ui.trainingsession

import androidx.compose.material.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class TrainingSessionListComposablesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val session = PolarTrainingSessionReference(LocalDate.of(2024, 3, 5), "/U/0/20240305/E/101010", emptyList(), emptyList())

    @Before
    fun setUp() {
        runCatching { Dispatchers.resetMain() }
        composeRule.mainClock.autoAdvance = false
    }

    private fun show(
        status: TrainingSessionFetch,
        onClick: (String, String) -> Unit = { _, _ -> },
        onDelete: (PolarTrainingSessionReference) -> Unit = {},
        onRefresh: () -> Unit = {}
    ) {
        composeRule.setContent {
            MaterialTheme {
                TrainingSessionItemsList(onRefresh, onClick, TrainingSessionsUiState(status), onDelete)
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    @Test
    fun `items list shows loading indicator when fetch has no items yet`() {
        // Act
        show(TrainingSessionFetch.InProgress(emptyList()))

        // Assert
        composeRule.onNodeWithText("Loading Training Sessions...").assertIsDisplayed()
    }

    @Test
    fun `items list shows items when fetch in progress with items`() {
        // Act
        show(TrainingSessionFetch.InProgress(listOf(session)))

        // Assert
        composeRule.onNodeWithText(session.path).assertIsDisplayed()
        composeRule.onNodeWithText("2024-03-05").assertIsDisplayed()
    }

    @Test
    fun `items list shows empty text when fetch succeeds without items`() {
        // Act
        show(TrainingSessionFetch.Success(emptyList()))

        // Assert
        composeRule.onNodeWithText("No training sessions").assertIsDisplayed()
    }

    @Test
    fun `items list shows message and details when fetch fails`() {
        // Act
        show(TrainingSessionFetch.Failure("Fetch failed", IllegalStateException("boom")))

        // Assert
        composeRule.onNodeWithText("Fetch failed").assertIsDisplayed()
        composeRule.onNodeWithText("Details:").assertIsDisplayed()
        composeRule.onNodeWithText("java.lang.IllegalStateException: boom").assertIsDisplayed()
    }

    @Test
    fun `items list hides details when failure has no throwable`() {
        // Act
        show(TrainingSessionFetch.Failure("Fetch failed", null))

        // Assert
        composeRule.onNodeWithText("Fetch failed").assertIsDisplayed()
        composeRule.onNodeWithText("Details:").assertDoesNotExist()
    }

    @Test
    fun `items list calls on click when item is clicked`() {
        // Arrange
        var clicked: Pair<String, String>? = null
        show(TrainingSessionFetch.Success(listOf(session)), onClick = { path, name -> clicked = path to name })

        // Act
        composeRule.onNodeWithText(session.path).performClick()

        // Assert
        assertEquals(session.path to session.path, clicked)
    }

    @Test
    fun `items list calls on delete when delete is clicked`() {
        // Arrange
        var deleted: PolarTrainingSessionReference? = null
        show(TrainingSessionFetch.Success(listOf(session)), onDelete = { deleted = it })

        // Act
        composeRule.onNodeWithContentDescription("Delete file").performClick()

        // Assert
        assertEquals(session, deleted)
    }

    @Test
    fun `training session shows progress instead of delete when deleting`() {
        // Act
        composeRule.setContent {
            MaterialTheme { TrainingSession(session, onClick = { _, _ -> }, onDelete = {}, isDeleting = true) }
        }
        composeRule.mainClock.advanceTimeByFrame()

        // Assert
        composeRule.onNodeWithContentDescription("Delete file").assertDoesNotExist()
        composeRule.onNodeWithText(session.path).assertIsDisplayed()
    }
}
