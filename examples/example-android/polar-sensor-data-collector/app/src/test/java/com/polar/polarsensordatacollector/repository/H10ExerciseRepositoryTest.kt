package com.polar.polarsensordatacollector.repository

import com.polar.sdk.api.PolarH10OfflineExerciseApi
import com.polar.sdk.api.model.PolarExerciseData
import com.polar.sdk.api.model.PolarExerciseEntry
import com.polar.sdk.impl.BDBleApiImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class H10ExerciseRepositoryTest {

    private val id = "dev1"
    private val api = mockk<BDBleApiImpl>(relaxed = true)
    private val repository = H10ExerciseRepository(api)

    private fun entry(): PolarExerciseEntry = mockk(relaxed = true)

    @Test
    fun `feature state is unsupported and idle by default`() {
        // Assert
        assertEquals(H10RecordingFeature(false, false), repository.featureState.value)
    }

    @Test
    fun `update status sets both flags`() {
        // Act
        repository.updateStatus(isSupported = true, isOngoing = true)

        // Assert
        assertEquals(H10RecordingFeature(true, true), repository.featureState.value)
    }

    @Test
    fun `update recording enabled keeps support flag`() {
        // Arrange
        repository.updateStatus(isSupported = true, isOngoing = false)

        // Act
        repository.updateRecordingEnabled(true)

        // Assert
        assertTrue(repository.featureState.value.isSupported)
        assertTrue(repository.featureState.value.isOngoing)
    }

    @Test
    fun `request recording status returns api result`() = runBlocking {
        // Arrange
        val status = PolarH10OfflineExerciseApi.PolarRecordingStatus(true, "ex1", true)
        coEvery { api.requestRecordingStatus(id) } returns status

        // Act and Assert
        assertSame(status, repository.requestRecordingStatus(id))
    }

    @Test
    fun `list exercises returns all entries`() = runBlocking {
        // Arrange
        val a = entry()
        val b = entry()
        every { api.listExercises(id) } returns flowOf(a, b)

        // Act
        val result = repository.listExercises(id)

        // Assert
        assertEquals(listOf(a, b), result)
    }

    @Test
    fun `list exercises rethrows when flow fails`() {
        // Arrange
        every { api.listExercises(id) } returns flow { throw IllegalStateException("list") }

        // Act and Assert
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.listExercises(id) } }
    }

    @Test
    fun `list exercises as flow emits entries and rethrows errors`() = runBlocking {
        // Arrange
        val a = entry()
        every { api.listExercises(id) } returns flow { emit(a); throw IllegalStateException("late") }
        val received = mutableListOf<PolarExerciseEntry>()

        // Act
        val error = runCatching { repository.listExercisesAsFlow(id).collect { received.add(it) } }.exceptionOrNull()

        // Assert
        assertEquals(listOf(a), received)
        assertEquals("late", error?.message)
    }

    @Test
    fun `read exercise returns data`() = runBlocking {
        // Arrange
        val e = entry()
        val data = mockk<PolarExerciseData>()
        coEvery { api.fetchExercise(id, e) } returns data

        // Act and Assert
        assertSame(data, repository.readExercise(id, e))
    }

    @Test
    fun `read exercise rethrows when api fails`() {
        // Arrange
        val e = entry()
        coEvery { api.fetchExercise(id, e) } throws IllegalStateException("fetch")

        // Act and Assert
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.readExercise(id, e) } }
    }

    @Test
    fun `remove exercise calls api`() = runBlocking {
        // Arrange
        val e = entry()

        // Act
        repository.removeExercise(id, e)

        // Assert
        coVerify { api.removeExercise(id, e) }
    }

    @Test
    fun `remove exercise rethrows when api fails`() {
        // Arrange
        val e = entry()
        coEvery { api.removeExercise(id, e) } throws IllegalStateException("remove")

        // Act and Assert
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.removeExercise(id, e) } }
    }

    @Test
    fun `start recording uses one second hr recording`() = runBlocking {
        // Act
        repository.startRecording(id, "ex1")

        // Assert
        coVerify {
            api.startRecording(
                id,
                "ex1",
                PolarH10OfflineExerciseApi.RecordingInterval.INTERVAL_1S,
                sampleType = PolarH10OfflineExerciseApi.SampleType.HR
            )
        }
    }

    @Test
    fun `start recording rethrows when api fails`() {
        // Arrange
        coEvery { api.startRecording(any(), any(), any(), any()) } throws IllegalStateException("start")

        // Act and Assert
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.startRecording(id, "ex1") } }
    }

    @Test
    fun `stop recording calls api`() = runBlocking {
        // Act
        repository.stopRecording(id)

        // Assert
        coVerify { api.stopRecording(id) }
    }

    @Test
    fun `stop recording rethrows when api fails`() {
        // Arrange
        coEvery { api.stopRecording(id) } throws IllegalStateException("stop")

        // Act and Assert
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.stopRecording(id) } }
    }
}
