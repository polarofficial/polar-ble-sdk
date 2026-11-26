package com.polar.polarsensordatacollector.ui.exercise

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.MutableLiveData
import com.polar.sdk.api.model.PolarExerciseSession
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h2400dp")
class ExerciseScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val status = MutableLiveData(PolarExerciseSession.ExerciseStatus.NOT_STARTED)
    private val statusText = MutableLiveData("Not started")
    private val canStart = MutableLiveData(true)
    private val canPause = MutableLiveData(false)
    private val canResume = MutableLiveData(false)
    private val canStop = MutableLiveData(false)
    private val startTime = MutableLiveData<LocalDateTime?>(null)
    private val observing = MutableLiveData(false)
    private val notification = MutableLiveData<String?>(null)
    private val error = MutableLiveData<String?>(null)

    private lateinit var vm: ExerciseViewModel
    private val toasts = mutableListOf<String>()
    private var started = 0
    private var paused = 0
    private var resumed = 0
    private val stops = mutableListOf<Boolean>()

    @Before
    fun setUp() {
        runCatching { Dispatchers.resetMain() }
        composeRule.mainClock.autoAdvance = false
        vm = mockk(relaxed = true)
        every { vm.status } returns status
        every { vm.statusText } returns statusText
        every { vm.canStart } returns canStart
        every { vm.canPause } returns canPause
        every { vm.canResume } returns canResume
        every { vm.canStop } returns canStop
        every { vm.startTime } returns startTime
        every { vm.isObservingNotifications } returns observing
        every { vm.notificationEvent } returns notification
        every { vm.errorEvent } returns error
        every { vm.sportProfiles } returns listOf(
            PolarExerciseSession.SportProfile.RUNNING,
            PolarExerciseSession.SportProfile.CYCLING
        )
    }

    private fun show() {
        composeRule.setContent {
            MaterialTheme {
                ExerciseScreen(
                    vm = vm,
                    onStart = { started++ },
                    onPause = { paused++ },
                    onResume = { resumed++ },
                    onStop = { stops.add(it) },
                    onStatusToast = { toasts.add(it) }
                )
            }
        }
        frames()
    }

    private fun frames() = repeat(3) {
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeByFrame()
    }

    @Test
    fun `exercise screen shows status text when shown`() {
        // Act
        show()

        // Assert
        composeRule.onNodeWithText("Not started").assertExists()
        composeRule.onNodeWithText("Running").assertExists()
        composeRule.onNodeWithText("Start").assertIsEnabled()
        composeRule.onNodeWithText("Pause").assertIsNotEnabled()
    }

    @Test
    fun `exercise screen calls callbacks when buttons clicked`() {
        // Arrange
        canPause.value = true
        canResume.value = true
        canStop.value = true
        show()

        // Act
        composeRule.onNodeWithText("Start").performClick()
        composeRule.onNodeWithText("Pause").performClick()
        composeRule.onNodeWithText("Resume").performClick()
        composeRule.onNodeWithText("Stop").performClick()

        // Assert
        assertEquals(1, started)
        assertEquals(1, paused)
        assertEquals(1, resumed)
        assertEquals(listOf(true), stops)
        verify { vm.selectSportId(PolarExerciseSession.SportProfile.RUNNING.id.toLong()) }
    }

    @Test
    fun `exercise screen stops without save when checkbox cleared`() {
        // Arrange
        canStop.value = true
        show()

        // Act
        composeRule.onNode(isToggleable()).performClick()
        frames()
        composeRule.onNodeWithText("Stop").performClick()
        frames()

        // Assert
        assertEquals(listOf(false), stops)
    }

    @Test
    fun `exercise screen disables start when custom sport id invalid`() {
        // Arrange
        show()

        // Act
        composeRule.onNodeWithText("Custom sport ID (optional)").performTextInput("abc")
        frames()

        // Assert
        composeRule.onNodeWithText("Start").assertIsNotEnabled()
    }

    @Test
    fun `exercise screen selects custom sport id when number entered`() {
        // Arrange
        show()

        // Act
        composeRule.onNodeWithText("Custom sport ID (optional)").performTextInput("42")
        frames()

        // Assert
        verify { vm.selectSportId(42L) }
    }

    @Test
    fun `exercise screen changes profile when dropdown item clicked`() {
        // Arrange
        show()

        // Act
        composeRule.onNodeWithText("Running").performClick()
        frames()
        composeRule.onNodeWithText("Cycling").performClick()
        frames()

        // Assert
        composeRule.onNodeWithText("Cycling").assertExists()
        verify { vm.selectSportId(PolarExerciseSession.SportProfile.CYCLING.id.toLong()) }
    }

    @Test
    fun `exercise screen emits toasts when status changes`() {
        // Arrange
        show()

        // Act
        status.value = PolarExerciseSession.ExerciseStatus.IN_PROGRESS
        frames()
        status.value = PolarExerciseSession.ExerciseStatus.PAUSED
        frames()
        status.value = PolarExerciseSession.ExerciseStatus.IN_PROGRESS
        frames()
        status.value = PolarExerciseSession.ExerciseStatus.SYNC_REQUIRED
        frames()
        status.value = PolarExerciseSession.ExerciseStatus.STOPPED
        frames()

        // Assert
        assertEquals(
            listOf("In progress", "Paused", "In progress", "Syncing...", "No active exercise"),
            toasts
        )
    }

    @Test
    fun `exercise screen emits error toast when error event set`() {
        // Arrange
        show()

        // Act
        error.value = "Boom"
        frames()

        // Assert
        assertEquals(listOf("Boom"), toasts)
    }

    @Test
    fun `exercise screen shows start time and notification when set`() {
        // Arrange
        show()

        // Act
        startTime.value = LocalDateTime.of(2024, 1, 2, 3, 4)
        notification.value = "Event X"
        frames()

        // Assert
        composeRule.onNodeWithText("Exercise start time: 2024-01-02T03:04").assertExists()
        composeRule.onNodeWithText("Event X").assertExists()
    }

    @Test
    fun `exercise screen toggles observation when button clicked`() {
        // Arrange
        show()

        // Act
        composeRule.onNodeWithText("Observe exercise state notifications").performClick()
        observing.value = true
        frames()

        // Assert
        verify { vm.toggleNotificationObservation() }
        composeRule.onNodeWithText("Stop observing").assertExists()
    }
}
