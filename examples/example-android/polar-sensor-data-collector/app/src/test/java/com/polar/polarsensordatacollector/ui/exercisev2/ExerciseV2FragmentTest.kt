package com.polar.polarsensordatacollector.ui.exercisev2

import android.app.Application
import android.os.Bundle
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.polar.polarsensordatacollector.testutil.HiltTestSupport
import com.polar.polarsensordatacollector.ui.landing.ONLINE_OFFLINE_KEY_DEVICE_ID
import com.polar.polarsensordatacollector.ui.utils.DataViewer
import com.polar.sdk.api.PolarOfflineExerciseV2Api
import com.polar.sdk.api.model.PolarExerciseData
import com.polar.sdk.api.model.PolarExerciseEntry
import com.polar.sdk.api.model.PolarExerciseSession
import com.polar.sdk.impl.BDBleApiImpl
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
class ExerciseV2FragmentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<DataViewer>()

    private val deviceId = "dev1"
    private lateinit var api: BDBleApiImpl
    private val entry = PolarExerciseEntry("/U/0/E/1", LocalDateTime.of(2024, 1, 1, 10, 0), "1")

    @Before
    fun setUp() {
        runCatching { Dispatchers.resetMain() }
        composeRule.mainClock.autoAdvance = false
        api = HiltTestSupport.install()
        coEvery { api.getOfflineExerciseStatusV2(deviceId) } returns false
        coEvery { api.getExerciseStatus(deviceId) } returns info(PolarExerciseSession.ExerciseStatus.NOT_STARTED)
    }

    @After
    fun tearDown() {
        HiltTestSupport.uninstall()
    }

    private fun info(status: PolarExerciseSession.ExerciseStatus) =
        PolarExerciseSession.ExerciseInfo(status, PolarExerciseSession.SportProfile.RUNNING)

    private fun showFragment() {
        val fragment = ExerciseV2Fragment()
        fragment.arguments = Bundle().apply { putString(ONLINE_OFFLINE_KEY_DEVICE_ID, deviceId) }
        composeRule.runOnUiThread {
            composeRule.activity.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow()
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun awaitText(text: String) {
        HiltTestSupport.awaitUntil {
            composeRule.mainClock.advanceTimeByFrame()
            runCatching { composeRule.onNodeWithText(text, substring = true).assertExists() }.isSuccess
        }
    }

    private fun click(text: String) {
        composeRule.onNodeWithText(text).performClick()
    }

    private fun startRunning(paused: Boolean = false) {
        coEvery { api.getOfflineExerciseStatusV2(deviceId) } returns true
        val status = if (paused) PolarExerciseSession.ExerciseStatus.PAUSED else PolarExerciseSession.ExerciseStatus.IN_PROGRESS
        coEvery { api.getExerciseStatus(deviceId) } returns info(status)
    }

    @Test
    fun `screen shows not running when no exercise is running`() {
        // Act
        showFragment()

        // Assert
        awaitText("No exercise running")
        composeRule.onNodeWithText("Start Exercise").assertExists()
        composeRule.onNodeWithText("Pause").assertDoesNotExist()
    }

    @Test
    fun `screen shows stop and pause when exercise is running`() {
        // Arrange
        startRunning()

        // Act
        showFragment()

        // Assert
        awaitText("Exercise is running")
        composeRule.onNodeWithText("Stop Exercise").assertExists()
        composeRule.onNodeWithText("Pause", substring = true).assertExists()
    }

    @Test
    fun `screen shows resume when exercise is paused`() {
        // Arrange
        startRunning(paused = true)

        // Act
        showFragment()

        // Assert
        awaitText("Exercise is running")
        HiltTestSupport.awaitUntil {
            composeRule.mainClock.advanceTimeByFrame()
            runCatching { composeRule.onNodeWithText("Resume", substring = true).assertExists() }.isSuccess
        }
    }

    @Test
    fun `screen shows failure when status request fails`() {
        // Arrange
        coEvery { api.getOfflineExerciseStatusV2(deviceId) } throws IllegalStateException("no link")

        // Act
        showFragment()

        // Assert
        awaitText("Failed to get status: no link")
    }

    @Test
    fun `start button starts exercise and refreshes status when success`() {
        // Arrange
        coEvery { api.startOfflineExerciseV2(deviceId, any()) } returns
            PolarOfflineExerciseV2Api.OfflineExerciseStartResult(PolarOfflineExerciseV2Api.StartResult.SUCCESS, "/U/0/E")
        showFragment()
        awaitText("No exercise running")
        startRunning()

        // Act
        click("Start Exercise")

        // Assert
        awaitText("Exercise is running")
        coVerify { api.startOfflineExerciseV2(deviceId, any()) }
    }

    @Test
    fun `start button shows failure when device rejects start`() {
        // Arrange
        coEvery { api.startOfflineExerciseV2(deviceId, any()) } returns
            PolarOfflineExerciseV2Api.OfflineExerciseStartResult(PolarOfflineExerciseV2Api.StartResult.LOW_BATTERY, "")
        showFragment()
        awaitText("No exercise running")

        // Act
        click("Start Exercise")

        // Assert
        awaitText("Failed to start: Start failed: LOW_BATTERY")
    }

    @Test
    fun `stop button stops exercise when exercise is running`() {
        // Arrange
        startRunning()
        coEvery { api.stopOfflineExerciseV2(deviceId) } returns Unit
        showFragment()
        awaitText("Exercise is running")

        // Act
        click("Stop Exercise")

        // Assert
        coVerify(timeout = 5_000) { api.stopOfflineExerciseV2(deviceId) }
    }

    @Test
    fun `stop button shows failure when stop fails`() {
        // Arrange
        startRunning()
        coEvery { api.stopOfflineExerciseV2(deviceId) } throws IllegalStateException("stop failed")
        showFragment()
        awaitText("Exercise is running")

        // Act
        click("Stop Exercise")

        // Assert
        awaitText("Failed to stop: stop failed")
    }

    @Test
    fun `pause button shows paused when exercise is running`() {
        // Arrange
        startRunning()
        var pauseCalled = false
        coEvery { api.pauseExercise(deviceId) } coAnswers { pauseCalled = true }
        coEvery { api.getExerciseStatus(deviceId) } coAnswers {
            info(if (pauseCalled) PolarExerciseSession.ExerciseStatus.PAUSED else PolarExerciseSession.ExerciseStatus.IN_PROGRESS)
        }
        showFragment()
        awaitText("Exercise is running")

        // Act
        click("Pause")

        // Assert
        awaitText("Exercise paused")
        coVerify { api.pauseExercise(deviceId) }
    }

    @Test
    fun `resume button shows resumed when exercise is paused`() {
        // Arrange
        startRunning(paused = true)
        showFragment()
        HiltTestSupport.awaitUntil {
            composeRule.mainClock.advanceTimeByFrame()
            runCatching { composeRule.onNodeWithText("Resume").assertExists() }.isSuccess
        }

        // Act
        click("Resume")

        // Assert
        coVerify(timeout = 5_000) { api.resumeExercise(deviceId) }
    }

    @Test
    fun `pause button shows failure when pause fails`() {
        // Arrange
        startRunning()
        coEvery { api.pauseExercise(deviceId) } throws IllegalStateException("pause failed")
        showFragment()
        awaitText("Exercise is running")

        // Act
        click("Pause")

        // Assert
        awaitText("Failed to pause: pause failed")
    }

    @Test
    fun `check status shows exercise found when device has exercise`() {
        // Arrange
        coEvery { api.listOfflineExercisesV2(deviceId, any()) } returns flowOf(entry)
        showFragment()
        awaitText("No exercise running")

        // Act
        click("Check Status")

        // Assert
        awaitText("Exercise in device")
    }

    @Test
    fun `check status shows no exercises when device has none`() {
        // Arrange
        coEvery { api.listOfflineExercisesV2(deviceId, any()) } returns flowOf()
        showFragment()
        awaitText("No exercise running")

        // Act
        click("Check Status")

        // Assert
        awaitText("No exercises found")
    }

    @Test
    fun `check status shows list failure when list fails`() {
        // Arrange
        coEvery { api.listOfflineExercisesV2(deviceId, any()) } throws IllegalStateException("list failed")
        showFragment()
        awaitText("No exercise running")

        // Act
        click("Check Status")

        // Assert
        awaitText("Failed to get exercise list: list failed")
    }

    @Test
    fun `fetch button opens share chooser when exercise exists`() {
        // Arrange
        coEvery { api.listOfflineExercisesV2(deviceId, any()) } returns flowOf(entry)
        coEvery { api.fetchOfflineExerciseV2(deviceId, entry) } returns PolarExerciseData(1, listOf(60, 61))
        showFragment()
        awaitText("No exercise running")

        // Act
        click("Fetch Exercise")

        // Assert
        awaitText("Share dialog opened")
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertEquals(android.content.Intent.ACTION_CHOOSER, shadowOf(app).nextStartedActivity.action)
    }

    @Test
    fun `fetch button shows failure when no exercise exists`() {
        // Arrange
        coEvery { api.listOfflineExercisesV2(deviceId, any()) } returns flowOf()
        showFragment()
        awaitText("No exercise running")

        // Act
        click("Fetch Exercise")

        // Assert
        awaitText("Failed to fetch: No exercises found")
    }

    @Test
    fun `remove button shows removed when exercise exists`() {
        // Arrange
        coEvery { api.listOfflineExercisesV2(deviceId, any()) } returns flowOf(entry)
        coEvery { api.removeOfflineExerciseV2(deviceId, entry) } returns Unit
        showFragment()
        awaitText("No exercise running")

        // Act
        click("Remove Exercise")

        // Assert
        awaitText("Exercise removed")
    }

    @Test
    fun `remove button shows failure when no exercise exists`() {
        // Arrange
        coEvery { api.listOfflineExercisesV2(deviceId, any()) } returns flowOf()
        showFragment()
        awaitText("No exercise running")

        // Act
        click("Remove Exercise")

        // Assert
        awaitText("Failed to remove: No exercises found")
    }
}
