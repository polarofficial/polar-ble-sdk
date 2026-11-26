package com.polar.polarsensordatacollector.ui.h10exercise

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.polar.polarsensordatacollector.repository.H10ExerciseRepository
import com.polar.polarsensordatacollector.repository.H10RecordingFeature
import com.polar.polarsensordatacollector.ui.landing.ONLINE_OFFLINE_KEY_DEVICE_ID
import com.polar.sdk.api.PolarH10OfflineExerciseApi.PolarRecordingStatus
import com.polar.sdk.api.model.PolarExerciseData
import com.polar.sdk.api.model.PolarExerciseEntry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class H10ExerciseViewModelTest {

    private val deviceId = "dev1"
    private val featureState = MutableStateFlow(H10RecordingFeature())
    private val repository = mockk<H10ExerciseRepository>(relaxed = true)
    private val entry = mockk<PolarExerciseEntry>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repository.featureState } returns featureState
        coEvery { repository.requestRecordingStatus(deviceId) } returns PolarRecordingStatus(false, "", true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() =
        H10ExerciseViewModel(repository, SavedStateHandle(mapOf(ONLINE_OFFLINE_KEY_DEVICE_ID to deviceId)))

    // Wait until all work of the ViewModel ends, so the catch block also finished.
    private fun joinViewModelWork(viewModel: H10ExerciseViewModel) = runBlocking {
        viewModel.viewModelScope.coroutineContext.job.children.toList().forEach { it.join() }
    }

    private fun await(latch: CountDownLatch) {
        check(latch.await(5, TimeUnit.SECONDS)) { "Callback not called" }
    }

    @Test
    fun `init throws when device id is missing`() {
        // Act and Assert
        assertThrows(Exception::class.java) { H10ExerciseViewModel(repository, SavedStateHandle()) }
    }

    @Test
    fun `init updates status when recording status is read`() {
        // Arrange
        coEvery { repository.requestRecordingStatus(deviceId) } returns PolarRecordingStatus(true, "id", true)

        // Act
        newViewModel()

        // Assert
        verify(timeout = 5_000) { repository.updateStatus(isSupported = true, isOngoing = true) }
    }

    @Test
    fun `init does not update status when recording status fails`() {
        // Arrange
        coEvery { repository.requestRecordingStatus(deviceId) } throws RuntimeException("fail")

        // Act
        val viewModel = newViewModel()
        joinViewModelWork(viewModel)

        // Assert
        coVerify { repository.requestRecordingStatus(deviceId) }
        verify(exactly = 0) { repository.updateStatus(any(), any()) }
    }

    @Test
    fun `list exercises returns count when repository succeeds`() {
        // Arrange
        coEvery { repository.listExercises(deviceId) } returns listOf(entry, entry)
        val latch = CountDownLatch(1)
        var count = -1

        // Act
        newViewModel().listExercises(onResult = { count = it; latch.countDown() }, onError = {})
        await(latch)

        // Assert
        assertEquals(2, count)
    }

    @Test
    fun `list exercises calls on error when repository fails`() {
        // Arrange
        coEvery { repository.listExercises(deviceId) } throws RuntimeException("fail")
        val latch = CountDownLatch(1)

        // Act
        newViewModel().listExercises(onResult = {}, onError = { latch.countDown() })

        // Assert
        await(latch)
    }

    @Test
    fun `read first exercise returns exercise when entries exist`() {
        // Arrange
        val data = mockk<PolarExerciseData>()
        coEvery { repository.listExercises(deviceId) } returns listOf(entry)
        coEvery { repository.readExercise(deviceId, entry) } returns data
        val latch = CountDownLatch(1)
        var result: PolarExerciseData? = null

        // Act
        newViewModel().readFirstExercise(onExercise = { result = it; latch.countDown() }, onError = {})
        await(latch)

        // Assert
        assertSame(data, result)
    }

    @Test
    fun `read first exercise calls on error when no entries`() {
        // Arrange
        coEvery { repository.listExercises(deviceId) } returns emptyList()
        val latch = CountDownLatch(1)

        // Act
        newViewModel().readFirstExercise(onExercise = {}, onError = { latch.countDown() })

        // Assert
        await(latch)
    }

    @Test
    fun `remove first exercise removes entry and completes`() {
        // Arrange
        coEvery { repository.listExercises(deviceId) } returns listOf(entry)
        val latch = CountDownLatch(1)

        // Act
        newViewModel().removeFirstExercise(onComplete = { latch.countDown() }, onError = {})
        await(latch)

        // Assert
        coVerify { repository.removeExercise(deviceId, entry) }
    }

    @Test
    fun `remove first exercise calls on error when no entries`() {
        // Arrange
        coEvery { repository.listExercises(deviceId) } returns emptyList()
        val latch = CountDownLatch(1)

        // Act
        newViewModel().removeFirstExercise(onComplete = {}, onError = { latch.countDown() })

        // Assert
        await(latch)
        coVerify(exactly = 0) { repository.removeExercise(any(), any()) }
    }

    @Test
    fun `toggle recording starts recording when not ongoing`() {
        // Arrange
        featureState.value = H10RecordingFeature(isSupported = true, isOngoing = false)
        val viewModel = newViewModel()

        // Act
        viewModel.toggleRecording()

        // Assert
        coVerify(timeout = 5_000) { repository.startRecording(deviceId, match { it.startsWith("H10_EX_") }) }
        verify(timeout = 5_000) { repository.updateRecordingEnabled(true) }
    }

    @Test
    fun `toggle recording stops recording when ongoing`() {
        // Arrange
        featureState.value = H10RecordingFeature(isSupported = true, isOngoing = true)
        val viewModel = newViewModel()

        // Act
        viewModel.toggleRecording()

        // Assert
        coVerify(timeout = 5_000) { repository.stopRecording(deviceId) }
        verify(timeout = 5_000) { repository.updateRecordingEnabled(false) }
    }

    @Test
    fun `toggle recording does not update state when repository fails`() {
        // Arrange
        coEvery { repository.startRecording(any(), any()) } throws RuntimeException("fail")
        val viewModel = newViewModel()

        // Act
        viewModel.toggleRecording()
        joinViewModelWork(viewModel)

        // Assert
        coVerify { repository.startRecording(any(), any()) }
        verify(exactly = 0) { repository.updateRecordingEnabled(any()) }
    }
}
