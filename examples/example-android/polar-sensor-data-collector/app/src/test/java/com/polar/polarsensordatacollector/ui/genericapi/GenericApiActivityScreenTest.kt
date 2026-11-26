package com.polar.polarsensordatacollector.ui.genericapi

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
import com.polar.polarsensordatacollector.ui.exercise.GenericApiActivityScreen
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h2400dp")
class GenericApiActivityScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var viewModel: GenericApiViewModel

    @Before
    fun setUp() {
        runCatching { Dispatchers.resetMain() }
        composeRule.mainClock.autoAdvance = false
        viewModel = mockk(relaxed = true)
        composeRule.setContent { MaterialTheme { GenericApiActivityScreen(viewModel) } }
        frames()
    }

    private fun frames() = repeat(3) {
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun type(label: String, text: String) {
        composeRule.onNodeWithText(label).performScrollTo()
            .performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString(text)) }
        frames()
    }

    private fun click(label: String) {
        composeRule.onNodeWithText(label).performClick()
        frames()
    }

    @Test
    fun `list button lists files non recursively when checkbox not checked`() {
        // Arrange
        type("List files from path", "/U/0/")

        // Act
        click("List")

        // Assert
        verify { viewModel.listFiles("/U/0/", false) }
    }

    @Test
    fun `list button lists files recursively when checkbox checked`() {
        // Arrange
        type("List files from path", "/U/0/")
        composeRule.onNode(isToggleable()).performClick()
        frames()

        // Act
        click("List")

        // Assert
        verify { viewModel.listFiles("/U/0/", true) }
    }

    @Test
    fun `read button reads file when path entered`() {
        // Arrange
        type("File name to read", "/a.txt")

        // Act
        click("Read")

        // Assert
        verify { viewModel.readFile("/a.txt") }
    }

    @Test
    fun `write button writes file when path and data entered`() {
        // Arrange
        type("File path to write", "/b.txt")
        type("File data to write", "abc")

        // Act
        click("Write")

        // Assert
        verify { viewModel.writeFile("/b.txt", match { it.contentEquals("abc".toByteArray()) }) }
    }

    @Test
    fun `delete button deletes file when path entered`() {
        // Arrange
        type("Delete file or directory", "/c.txt")

        // Act
        click("Delete")

        // Assert
        verify { viewModel.deleteFile("/c.txt") }
    }

    @Test
    fun `create button creates folder when path entered`() {
        // Arrange
        type("Folder path to create", "/dir/")

        // Act
        click("Create")

        // Assert
        verify { viewModel.createFolder("/dir/") }
    }
}
