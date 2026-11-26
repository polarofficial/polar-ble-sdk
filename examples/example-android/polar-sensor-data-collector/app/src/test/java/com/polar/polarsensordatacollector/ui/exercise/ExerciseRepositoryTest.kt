package com.polar.polarsensordatacollector.ui.exercise

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.polar.polarsensordatacollector.repository.DeviceApiCallSerializer
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarTrainingSessionApi
import com.polar.sdk.api.model.PolarExerciseSession.ExerciseInfo
import com.polar.sdk.api.model.PolarExerciseSession.ExerciseStatus
import com.polar.sdk.api.model.PolarExerciseSession.SportProfile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExerciseRepositoryTest {

    private val id = "dev1"
    private val api = mockk<PolarTrainingSessionApi>(relaxed = true)
    private val bleApi = mockk<PolarBleApi>(relaxed = true)
    private lateinit var prefs: SharedPreferences
    private lateinit var repository: ExerciseRepository

    private fun info(status: ExerciseStatus, profile: SportProfile = SportProfile.RUNNING, sportId: Long = profile.id.toLong()) =
        ExerciseInfo(status, profile, null, sportId)

    @Before
    fun setUp() {
        prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        repository = ExerciseRepository(api, bleApi, id, prefs, DeviceApiCallSerializer(), CoroutineScope(UnconfinedTestDispatcher()))
    }

    @Test
    fun `state starts not started with unknown profile`() {
        // Assert
        assertEquals(ExerciseStatus.NOT_STARTED, repository.state().value.status)
        assertEquals(SportProfile.UNKNOWN, repository.state().value.sportProfile)
    }

    @Test
    fun `refresh publishes status and clears error`() = runBlocking {
        // Arrange
        coEvery { api.getExerciseStatus(id) } returns info(ExerciseStatus.IN_PROGRESS, SportProfile.CYCLING)

        // Act
        repository.refresh()

        // Assert
        assertEquals(ExerciseStatus.IN_PROGRESS, repository.state().value.status)
        assertEquals(SportProfile.CYCLING, repository.state().value.sportProfile)
        assertNull(repository.errorEvent.value)
    }

    @Test
    fun `refresh sets error when api fails`() = runBlocking {
        // Arrange
        coEvery { api.getExerciseStatus(id) } throws IllegalStateException("down")

        // Act
        repository.refresh()

        // Assert
        assertEquals("Failed to refresh: down", repository.errorEvent.value)
    }

    @Test
    fun `refresh restores last sport when stopped status has no sport`() = runBlocking {
        // Arrange
        coEvery { api.getExerciseStatus(id) } returns info(ExerciseStatus.IN_PROGRESS, SportProfile.CYCLING)
        repository.refresh()
        coEvery { api.getExerciseStatus(id) } returns info(ExerciseStatus.STOPPED, SportProfile.UNKNOWN, 0L)

        // Act
        repository.refresh()

        // Assert
        assertEquals(SportProfile.CYCLING, repository.state().value.sportProfile)
        assertEquals(SportProfile.CYCLING.id.toLong(), repository.state().value.sportId)
    }

    @Test
    fun `start uses default start and publishes in progress when no sport given`() = runBlocking {
        // Act
        repository.start()

        // Assert
        coVerify { api.startExercise(id) }
        assertEquals(ExerciseStatus.IN_PROGRESS, repository.state().value.status)
        assertEquals(SportProfile.OTHER_OUTDOOR, repository.state().value.sportProfile)
    }

    @Test
    fun `start uses given sport id`() = runBlocking {
        // Arrange
        val sportId = SportProfile.RUNNING.id.toLong()

        // Act
        repository.start(sportId)

        // Assert
        coVerify { api.startExercise(id, sportId) }
        assertEquals(SportProfile.RUNNING, repository.state().value.sportProfile)
    }

    @Test
    fun `start sets error and rethrows when api fails`() {
        // Arrange
        coEvery { api.startExercise(id) } throws IllegalStateException("nope")

        // Act and Assert
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.start() } }
        assertEquals("Failed to start exercise: nope", repository.errorEvent.value)
    }

    @Test
    fun `pause publishes paused when device confirms`() = runBlocking {
        // Arrange
        coEvery { api.getExerciseStatus(id) } returns info(ExerciseStatus.PAUSED)

        // Act
        repository.pause()

        // Assert
        assertEquals(ExerciseStatus.PAUSED, repository.state().value.status)
        assertNull(repository.errorEvent.value)
    }

    @Test
    fun `pause throws when device does not pause`() {
        // Arrange
        coEvery { api.getExerciseStatus(id) } returns info(ExerciseStatus.IN_PROGRESS)

        // Act and Assert
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.pause() } }
        assertTrue(repository.errorEvent.value!!.contains("Failed to pause exercise"))
    }

    @Test
    fun `resume publishes in progress when device confirms`() = runBlocking {
        // Arrange
        coEvery { api.getExerciseStatus(id) } returns info(ExerciseStatus.IN_PROGRESS)

        // Act
        repository.resume()

        // Assert
        assertEquals(ExerciseStatus.IN_PROGRESS, repository.state().value.status)
    }

    @Test
    fun `resume throws when device stays paused`() {
        // Arrange
        coEvery { api.getExerciseStatus(id) } returns info(ExerciseStatus.PAUSED)

        // Act and Assert
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.resume() } }
        assertTrue(repository.errorEvent.value!!.contains("Failed to resume exercise"))
    }

    @Test
    fun `stop without save publishes not started without sync`() = runBlocking {
        // Arrange
        coEvery { api.getExerciseStatus(id) } returns info(ExerciseStatus.NOT_STARTED, SportProfile.UNKNOWN, 0L)

        // Act
        repository.stop(save = false)

        // Assert
        coVerify { api.stopExercise(id, false) }
        coVerify(exactly = 0) { bleApi.sendInitializationAndStartSyncNotifications(any()) }
        assertEquals(ExerciseStatus.NOT_STARTED, repository.state().value.status)
    }

    @Test
    fun `stop with save runs sync and waits for stopped status`() = runBlocking {
        // Arrange
        coEvery { api.getExerciseStatus(id) } returns info(ExerciseStatus.STOPPED)

        // Act
        repository.stop(save = true)

        // Assert
        coVerify { api.stopExercise(id, true) }
        coVerify { bleApi.sendInitializationAndStartSyncNotifications(id) }
        coVerify { bleApi.sendTerminateAndStopSyncNotifications(id) }
        assertEquals(ExerciseStatus.STOPPED, repository.state().value.status)
    }

    @Test
    fun `stop with save still stops sync when start sync fails`() = runBlocking {
        // Arrange
        coEvery { bleApi.sendInitializationAndStartSyncNotifications(id) } throws IllegalStateException("sync")
        coEvery { api.getExerciseStatus(id) } returns info(ExerciseStatus.STOPPED)

        // Act
        repository.stop(save = true)

        // Assert
        coVerify { bleApi.sendTerminateAndStopSyncNotifications(id) }
    }

    @Test
    fun `start observing exercise status publishes notifications`() {
        // Arrange
        val flow = MutableSharedFlow<ExerciseInfo>(extraBufferCapacity = 1)
        every { api.observeExerciseStatus(id) } returns flow

        // Act
        repository.startObservingExerciseStatus()
        flow.tryEmit(info(ExerciseStatus.PAUSED, SportProfile.CYCLING))

        // Assert
        assertEquals(ExerciseStatus.PAUSED, repository.state().value.status)
    }

    @Test
    fun `start observing exercise status sets error when flow fails`() {
        // Arrange
        every { api.observeExerciseStatus(id) } returns flow { throw IllegalStateException("obs") }

        // Act
        repository.startObservingExerciseStatus()

        // Assert
        assertEquals("Exercise status error: obs", repository.errorEvent.value)
    }

    @Test
    fun `stop observing exercise status cancels observation`() {
        // Arrange
        val flow = MutableSharedFlow<ExerciseInfo>(extraBufferCapacity = 1)
        every { api.observeExerciseStatus(id) } returns flow
        val job = repository.startObservingExerciseStatus()

        // Act
        repository.stopObservingExerciseStatus()

        // Assert
        assertTrue(job.isCancelled)
    }

    @Test
    fun `auto refresh refreshes periodically until stopped`() = runBlocking {
        // Arrange
        coEvery { api.getExerciseStatus(id) } returns info(ExerciseStatus.IN_PROGRESS)
        val realRepo = ExerciseRepository(api, bleApi, id, prefs, DeviceApiCallSerializer(), CoroutineScope(Dispatchers.IO))

        // Act
        val job = realRepo.startAutoRefresh(intervalMs = 10)
        val deadline = System.currentTimeMillis() + 5_000
        while (realRepo.state().value.status != ExerciseStatus.IN_PROGRESS && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        realRepo.stopAutoRefresh()

        // Assert
        assertTrue(job.isCancelled)
        assertEquals(ExerciseStatus.IN_PROGRESS, realRepo.state().value.status)
    }
}
