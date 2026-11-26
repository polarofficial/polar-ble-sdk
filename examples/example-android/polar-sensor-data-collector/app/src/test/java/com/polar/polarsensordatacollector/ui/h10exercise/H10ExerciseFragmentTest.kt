package com.polar.polarsensordatacollector.ui.h10exercise

import android.os.Bundle
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.testutil.HiltTestSupport
import com.polar.polarsensordatacollector.ui.landing.ONLINE_OFFLINE_KEY_DEVICE_ID
import com.polar.polarsensordatacollector.ui.utils.DataViewer
import com.polar.sdk.api.PolarH10OfflineExerciseApi
import com.polar.sdk.api.model.PolarExerciseData
import com.polar.sdk.api.model.PolarExerciseEntry
import com.polar.sdk.impl.BDBleApiImpl
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
class H10ExerciseFragmentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<DataViewer>()

    private val deviceId = "h10"
    private lateinit var api: BDBleApiImpl
    private val entry = PolarExerciseEntry("/U/0/E/1", LocalDateTime.of(2024, 1, 1, 10, 0), "1")

    @Before
    fun setUp() {
        runCatching { Dispatchers.resetMain() }
        com.polar.polarsensordatacollector.testutil.FileProviderTestSupport.reset()
        composeRule.mainClock.autoAdvance = false
        api = HiltTestSupport.install()
        coEvery { api.requestRecordingStatus(deviceId) } returns PolarH10OfflineExerciseApi.PolarRecordingStatus(false, "", true)
    }

    @After
    fun tearDown() {
        HiltTestSupport.uninstall()
    }

    private fun showFragment() {
        val fragment = H10ExerciseFragment()
        fragment.arguments = Bundle().apply { putString(ONLINE_OFFLINE_KEY_DEVICE_ID, deviceId) }
        composeRule.runOnUiThread {
            composeRule.activity.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow()
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun string(id: Int) = composeRule.activity.getString(id)

    private fun click(id: Int) {
        composeRule.onNodeWithText(string(id)).performClick()
    }

    private fun awaitText(id: Int) {
        HiltTestSupport.awaitUntil {
            composeRule.mainClock.advanceTimeByFrame()
            runCatching { composeRule.onNodeWithText(string(id)).assertExists() }.isSuccess
        }
    }

    @Test
    fun `list exercises shows found when device has exercises`() {
        // Arrange
        coEvery { api.listExercises(deviceId) } returns flowOf(entry)
        showFragment()

        // Act
        click(R.string.list_exercises)

        // Assert
        awaitText(R.string.exercises_found)
    }

    @Test
    fun `list exercises shows not found when device has no exercises`() {
        // Arrange
        coEvery { api.listExercises(deviceId) } returns flowOf()
        showFragment()

        // Act
        click(R.string.list_exercises)

        // Assert
        awaitText(R.string.no_exercise_found)
    }

    @Test
    fun `remove exercise shows success when exercise exists`() {
        // Arrange
        coEvery { api.listExercises(deviceId) } returns flowOf(entry)
        coEvery { api.removeExercise(deviceId, entry) } returns Unit
        showFragment()

        // Act
        click(R.string.remove_exercise)

        // Assert
        awaitText(R.string.exercise_removed_successfully)
        coVerify { api.removeExercise(deviceId, entry) }
    }

    @Test
    fun `remove exercise shows error when no exercise exists`() {
        // Arrange
        coEvery { api.listExercises(deviceId) } returns flowOf()
        showFragment()

        // Act
        click(R.string.remove_exercise)

        // Assert
        awaitText(R.string.no_exercise_to_remove)
    }

    @Test
    fun `read exercise shows error when no exercise exists`() {
        // Arrange
        coEvery { api.listExercises(deviceId) } returns flowOf()
        showFragment()

        // Act
        click(R.string.read_exercise)

        // Assert
        awaitText(R.string.no_exercise_found)
    }

    @Test
    fun `read exercise shares exercise data when exercise exists`() {
        // Arrange
        coEvery { api.listExercises(deviceId) } returns flowOf(entry)
        coEvery { api.fetchExercise(deviceId, entry) } returns PolarExerciseData(1, listOf(60, 61))
        showFragment()

        // Act
        click(R.string.read_exercise)

        // Assert
        coVerify(timeout = 5_000) { api.fetchExercise(deviceId, entry) }
        var chooser: android.content.Intent? = null
        HiltTestSupport.awaitUntil {
            composeRule.mainClock.advanceTimeByFrame()
            chooser = shadowOf(composeRule.activity).peekNextStartedActivity()
            chooser != null
        }
        assertEquals(null, ShadowToast.getLatestToast())
        assertEquals(android.content.Intent.ACTION_CHOOSER, chooser!!.action)
        val sent = chooser!!.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)!!
        assertEquals(android.content.Intent.ACTION_SEND, sent.action)
        assertEquals("application/json", sent.type)
        assertNotNull(sent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM))
    }

    @Test
    fun `toggle recording starts and stops recording when button is clicked`() {
        // Arrange
        coEvery { api.startRecording(any(), any(), any(), any()) } returns Unit
        coEvery { api.stopRecording(deviceId) } returns Unit
        showFragment()

        // Act
        click(R.string.start_h10_recording)

        // Assert
        coVerify(timeout = 5_000) { api.startRecording(deviceId, any(), any(), any()) }
        awaitText(R.string.stop_h10_recording)
        click(R.string.stop_h10_recording)
        coVerify(timeout = 5_000) { api.stopRecording(deviceId) }
        awaitText(R.string.start_h10_recording)
    }
}
