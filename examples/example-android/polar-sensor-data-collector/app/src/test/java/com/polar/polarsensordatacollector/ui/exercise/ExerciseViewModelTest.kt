package com.polar.polarsensordatacollector.ui.exercise

import com.polar.polarsensordatacollector.R
import com.polar.sdk.api.model.PolarExerciseSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExerciseViewModelTest {

    private class FakeStringProvider : StringProvider {
        override fun get(id: Int, vararg args: Any): String = "$id|${args.joinToString(",")}"
    }

    private val stateFlow = MutableStateFlow(info(PolarExerciseSession.ExerciseStatus.NOT_STARTED))
    private val errorFlow = MutableStateFlow<String?>(null)
    private val repo = mockk<ExerciseRepository>(relaxed = true)

    private fun info(
        status: PolarExerciseSession.ExerciseStatus,
        profile: PolarExerciseSession.SportProfile = PolarExerciseSession.SportProfile.RUNNING,
        sportId: Long = profile.id.toLong(),
        startTime: LocalDateTime? = null
    ) = PolarExerciseSession.ExerciseInfo(status, profile, startTime, sportId)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repo.state() } returns stateFlow
        every { repo.errorEvent } returns errorFlow
        every { repo.startObservingExerciseStatus() } returns mockk<Job>(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = ExerciseViewModel(repo, FakeStringProvider())

    @Test
    fun `init refreshes and starts observing`() {
        // Act
        newViewModel()

        // Assert
        coVerify { repo.refresh() }
        verify { repo.startObservingExerciseStatus() }
    }

    @Test
    fun `apply info enables only start when not started`() {
        // Act
        val viewModel = newViewModel()

        // Assert
        assertEquals("${R.string.status_not_started}|", viewModel.statusText.value)
        assertTrue(viewModel.canStart.value!!)
        assertFalse(viewModel.canPause.value!!)
        assertFalse(viewModel.canResume.value!!)
        assertFalse(viewModel.canStop.value!!)
    }

    @Test
    fun `apply info enables pause and stop when in progress`() {
        // Arrange
        val viewModel = newViewModel()
        val start = LocalDateTime.of(2024, 1, 1, 10, 0)

        // Act
        stateFlow.value = info(PolarExerciseSession.ExerciseStatus.IN_PROGRESS, startTime = start)

        // Assert
        assertEquals("${R.string.status_in_progress}|RUNNING", viewModel.statusText.value)
        assertFalse(viewModel.canStart.value!!)
        assertTrue(viewModel.canPause.value!!)
        assertFalse(viewModel.canResume.value!!)
        assertTrue(viewModel.canStop.value!!)
        assertEquals(start, viewModel.startTime.value)
    }

    @Test
    fun `apply info enables resume and stop when paused`() {
        // Arrange
        val viewModel = newViewModel()

        // Act
        stateFlow.value = info(PolarExerciseSession.ExerciseStatus.PAUSED)

        // Assert
        assertEquals("${R.string.status_paused}|RUNNING", viewModel.statusText.value)
        assertTrue(viewModel.canResume.value!!)
        assertTrue(viewModel.canStop.value!!)
        assertFalse(viewModel.canPause.value!!)
    }

    @Test
    fun `apply info enables start when stopped`() {
        // Arrange
        val viewModel = newViewModel()

        // Act
        stateFlow.value = info(PolarExerciseSession.ExerciseStatus.STOPPED)

        // Assert
        assertEquals("${R.string.status_stopped}|RUNNING", viewModel.statusText.value)
        assertTrue(viewModel.canStart.value!!)
        assertFalse(viewModel.canStop.value!!)
    }

    @Test
    fun `apply info shows sync required text when sync required`() {
        // Arrange
        val viewModel = newViewModel()

        // Act
        stateFlow.value = info(PolarExerciseSession.ExerciseStatus.SYNC_REQUIRED)

        // Assert
        assertEquals("${R.string.status_sync_required}|", viewModel.statusText.value)
        assertFalse(viewModel.canStart.value!!)
    }

    @Test
    fun `apply info uses sport id label when profile is unknown`() {
        // Arrange
        val viewModel = newViewModel()

        // Act
        stateFlow.value = info(
            PolarExerciseSession.ExerciseStatus.IN_PROGRESS,
            profile = PolarExerciseSession.SportProfile.UNKNOWN,
            sportId = 99L
        )

        // Assert
        assertEquals(
            "${R.string.status_in_progress}|${R.string.exercise_sport_id}|99",
            viewModel.statusText.value
        )
    }

    @Test
    fun `error event is published when repository reports error`() {
        // Arrange
        val viewModel = newViewModel()

        // Act
        errorFlow.value = "boom"

        // Assert
        assertEquals("boom", viewModel.errorEvent.value)
    }

    @Test
    fun `toggle notification observation starts and stops`() {
        // Arrange
        val viewModel = newViewModel()

        // Act
        viewModel.toggleNotificationObservation()

        // Assert
        assertTrue(viewModel.isObservingNotifications.value!!)
        assertEquals("${R.string.status_not_started}|", viewModel.notificationEvent.value)

        // Act
        stateFlow.value = info(PolarExerciseSession.ExerciseStatus.PAUSED)

        // Assert
        assertEquals("${R.string.status_paused}|RUNNING", viewModel.notificationEvent.value)

        // Act
        viewModel.toggleNotificationObservation()

        // Assert
        assertFalse(viewModel.isObservingNotifications.value!!)
        verify { repo.stopObservingExerciseStatus() }
    }

    @Test
    fun `start uses selected sport id`() {
        // Arrange
        val viewModel = newViewModel()
        viewModel.selectSportId(42L)

        // Act
        viewModel.start()

        // Assert
        coVerify { repo.start(42L) }
    }

    @Test
    fun `start pause resume do not throw when repository fails`() {
        // Arrange
        coEvery { repo.start(any()) } throws IllegalStateException("fail")
        coEvery { repo.pause() } throws IllegalStateException("fail")
        coEvery { repo.resume() } throws IllegalStateException("fail")
        val viewModel = newViewModel()

        // Act
        viewModel.start()
        viewModel.pause()
        viewModel.resume()

        // Assert
        coVerify { repo.start(any()) }
        coVerify { repo.pause() }
        coVerify { repo.resume() }
        assertNull(viewModel.notificationEvent.value)
    }

    @Test
    fun `stop forwards save flag`() {
        // Arrange
        val viewModel = newViewModel()

        // Act
        viewModel.stop(false)

        // Assert
        coVerify { repo.stop(false) }
    }

    @Test
    fun `on cleared stops observing`() {
        // Arrange
        val viewModel = newViewModel()
        val method = ExerciseViewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true

        // Act
        method.invoke(viewModel)

        // Assert
        verify(atLeast = 1) { repo.stopObservingExerciseStatus() }
    }
}
