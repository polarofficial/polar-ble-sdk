package com.polar.polarsensordatacollector.ui.exercisev2

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.polar.polarsensordatacollector.repository.DeviceApiCallSerializer
import com.polar.polarsensordatacollector.repository.ExerciseV2Repository
import com.polar.polarsensordatacollector.ui.exercise.ExerciseRepository
import com.polar.polarsensordatacollector.ui.landing.ONLINE_OFFLINE_KEY_DEVICE_ID
import com.polar.sdk.api.PolarOfflineExerciseV2Api
import com.polar.sdk.api.model.PolarExerciseData
import com.polar.sdk.api.model.PolarExerciseEntry
import com.polar.sdk.api.model.PolarExerciseSession
import com.polar.sdk.impl.BDBleApiImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExerciseV2ViewModelTest {

    private val deviceId = "dev1"
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val repository = mockk<ExerciseV2Repository>(relaxed = true)
    private val onlineApi = mockk<BDBleApiImpl>(relaxed = true)
    private val serializer = mockk<DeviceApiCallSerializer>(relaxed = true)
    private val onlineState = MutableStateFlow(
        PolarExerciseSession.ExerciseInfo(
            PolarExerciseSession.ExerciseStatus.NOT_STARTED,
            PolarExerciseSession.SportProfile.RUNNING
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkConstructor(ExerciseRepository::class)
        every { anyConstructed<ExerciseRepository>().state() } returns onlineState
    }

    @After
    fun tearDown() {
        unmockkConstructor(ExerciseRepository::class)
        Dispatchers.resetMain()
    }

    private fun newViewModel() = ExerciseV2ViewModel(
        repository,
        onlineApi,
        serializer,
        context,
        SavedStateHandle(mapOf(ONLINE_OFFLINE_KEY_DEVICE_ID to deviceId))
    )

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Condition not reached" }
            Thread.sleep(10)
        }
    }

    private fun entry(): PolarExerciseEntry = mockk(relaxed = true)

    @Test
    fun `init throws when identifier is missing`() {
        // Act and Assert
        assertThrows(Exception::class.java) {
            ExerciseV2ViewModel(repository, onlineApi, serializer, context, SavedStateHandle())
        }
    }

    @Test
    fun `online exercise state exposes repository state`() {
        // Act
        val viewModel = newViewModel()

        // Assert
        assertSame(onlineState, viewModel.onlineExerciseState)
    }

    @Test
    fun `start offline exercise calls on result when start succeeds`() {
        // Arrange
        coEvery { repository.startOfflineExerciseV2(deviceId, any()) } returns
            PolarOfflineExerciseV2Api.OfflineExerciseStartResult(PolarOfflineExerciseV2Api.StartResult.SUCCESS, "/dir")
        val results = CopyOnWriteArrayList<String>()

        // Act
        newViewModel().startOfflineExercise({ results.add("ok") }, { results.add("err:$it") })
        await { results.isNotEmpty() }

        // Assert
        assertEquals(listOf("ok"), results)
    }

    @Test
    fun `start offline exercise calls on error when result is not success`() {
        // Arrange
        coEvery { repository.startOfflineExerciseV2(deviceId, any()) } returns
            PolarOfflineExerciseV2Api.OfflineExerciseStartResult(PolarOfflineExerciseV2Api.StartResult.LOW_BATTERY, "")
        val results = CopyOnWriteArrayList<String>()

        // Act
        newViewModel().startOfflineExercise({ results.add("ok") }, { results.add(it) })
        await { results.isNotEmpty() }

        // Assert
        assertEquals(listOf("Start failed: LOW_BATTERY"), results)
    }

    @Test
    fun `start offline exercise calls on error when repository throws`() {
        // Arrange
        coEvery { repository.startOfflineExerciseV2(deviceId, any()) } throws IllegalStateException("boom")
        val results = CopyOnWriteArrayList<String>()

        // Act
        newViewModel().startOfflineExercise({ results.add("ok") }, { results.add(it) })
        await { results.isNotEmpty() }

        // Assert
        assertEquals(listOf("boom"), results)
    }

    @Test
    fun `stop offline exercise calls on result when stop succeeds`() {
        // Arrange
        val results = CopyOnWriteArrayList<String>()

        // Act
        newViewModel().stopOfflineExercise({ results.add("ok") }, { results.add(it) })
        await { results.isNotEmpty() }

        // Assert
        assertEquals(listOf("ok"), results)
        coVerify { repository.stopOfflineExerciseV2(deviceId) }
    }

    @Test
    fun `stop offline exercise calls on error when repository throws`() {
        // Arrange
        coEvery { repository.stopOfflineExerciseV2(deviceId) } throws IllegalStateException()
        val results = CopyOnWriteArrayList<String>()

        // Act
        newViewModel().stopOfflineExercise({ results.add("ok") }, { results.add(it) })
        await { results.isNotEmpty() }

        // Assert
        assertEquals(listOf("Unknown error"), results)
    }

    @Test
    fun `list offline exercises streams entries then completes`() {
        // Arrange
        val first = entry()
        val second = entry()
        every { repository.listOfflineExercisesV2(deviceId, any()) } returns flowOf(first, second)
        val events = CopyOnWriteArrayList<Any>()

        // Act
        newViewModel().listOfflineExercises({ events.add(it) }, { events.add("err:$it") }, { events.add("done") })
        await { events.size == 3 }

        // Assert
        assertEquals(listOf<Any>(first, second, "done"), events.toList())
    }

    @Test
    fun `list offline exercises calls on error when flow fails`() {
        // Arrange
        every { repository.listOfflineExercisesV2(deviceId, any()) } throws IllegalStateException("list failed")
        val events = CopyOnWriteArrayList<String>()

        // Act
        newViewModel().listOfflineExercises({ events.add("next") }, { events.add(it) }, { events.add("done") })
        await { events.isNotEmpty() }

        // Assert
        assertEquals(listOf("list failed"), events)
    }

    @Test
    fun `list offline exercises reports first entry when entries exist`() {
        // Arrange
        val first = entry()
        every { repository.listOfflineExercisesV2(deviceId, any()) } returns flowOf(first, entry())
        val results = CopyOnWriteArrayList<Pair<Boolean, PolarExerciseEntry?>>()

        // Act
        newViewModel().listOfflineExercises({ has, e -> results.add(has to e) }, { })
        await { results.isNotEmpty() }

        // Assert
        assertEquals(true, results.first().first)
        assertSame(first, results.first().second)
    }

    @Test
    fun `list offline exercises reports no entry when list is empty`() {
        // Arrange
        every { repository.listOfflineExercisesV2(deviceId, any()) } returns flowOf()
        val results = CopyOnWriteArrayList<Pair<Boolean, PolarExerciseEntry?>>()

        // Act
        newViewModel().listOfflineExercises({ has, e -> results.add(has to e) }, { })
        await { results.isNotEmpty() }

        // Assert
        assertFalse(results.first().first)
        assertNull(results.first().second)
    }

    @Test
    fun `list offline exercises reports error when list throws`() {
        // Arrange
        every { repository.listOfflineExercisesV2(deviceId, any()) } throws IllegalStateException("bad list")
        val errors = CopyOnWriteArrayList<String>()

        // Act
        newViewModel().listOfflineExercises({ _, _ -> }, { errors.add(it) })
        await { errors.isNotEmpty() }

        // Assert
        assertEquals(listOf("bad list"), errors)
    }

    @Test
    fun `fetch offline exercise returns data when entry exists`() {
        // Arrange
        val first = entry()
        val data = mockk<PolarExerciseData>()
        every { repository.listOfflineExercisesV2(deviceId, any()) } returns flowOf(first)
        coEvery { repository.fetchOfflineExerciseV2(deviceId, first) } returns data
        val results = CopyOnWriteArrayList<Any>()

        // Act
        newViewModel().fetchOfflineExercise({ results.add(it) }, { results.add(it) })
        await { results.isNotEmpty() }

        // Assert
        assertSame(data, results.first())
    }

    @Test
    fun `fetch offline exercise calls on error when no entries`() {
        // Arrange
        every { repository.listOfflineExercisesV2(deviceId, any()) } returns flowOf()
        val results = CopyOnWriteArrayList<Any>()

        // Act
        newViewModel().fetchOfflineExercise({ results.add(it) }, { results.add(it) })
        await { results.isNotEmpty() }

        // Assert
        assertEquals("No exercises found", results.first())
    }

    @Test
    fun `remove offline exercise removes first entry`() {
        // Arrange
        val first = entry()
        every { repository.listOfflineExercisesV2(deviceId, any()) } returns flowOf(first)
        val results = CopyOnWriteArrayList<String>()

        // Act
        newViewModel().removeOfflineExercise({ results.add("ok") }, { results.add(it) })
        await { results.isNotEmpty() }

        // Assert
        assertEquals(listOf("ok"), results)
        coVerify { repository.removeOfflineExerciseV2(deviceId, first) }
    }

    @Test
    fun `remove offline exercise calls on error when no entries`() {
        // Arrange
        every { repository.listOfflineExercisesV2(deviceId, any()) } returns flowOf()
        val results = CopyOnWriteArrayList<String>()

        // Act
        newViewModel().removeOfflineExercise({ results.add("ok") }, { results.add(it) })
        await { results.isNotEmpty() }

        // Assert
        assertEquals(listOf("No exercises found"), results)
    }

    @Test
    fun `get offline exercise status reports running flag`() {
        // Arrange
        coEvery { repository.getOfflineExerciseStatusV2(deviceId) } returns true
        val results = CopyOnWriteArrayList<Boolean>()

        // Act
        newViewModel().getOfflineExerciseStatus({ results.add(it) }, { })
        await { results.isNotEmpty() }

        // Assert
        assertTrue(results.first())
    }

    @Test
    fun `get offline exercise status calls on error when repository throws`() {
        // Arrange
        coEvery { repository.getOfflineExerciseStatusV2(deviceId) } throws IllegalStateException("status failed")
        val errors = CopyOnWriteArrayList<String>()

        // Act
        newViewModel().getOfflineExerciseStatus({ }, { errors.add(it) })
        await { errors.isNotEmpty() }

        // Assert
        assertEquals(listOf("status failed"), errors)
    }

    @Test
    fun `pause and resume exercise call online repository`() {
        // Arrange
        coEvery { anyConstructed<ExerciseRepository>().pause() } returns Unit
        coEvery { anyConstructed<ExerciseRepository>().resume() } returns Unit
        val results = CopyOnWriteArrayList<String>()
        val viewModel = newViewModel()

        // Act
        viewModel.pauseExercise({ results.add("paused") }, { results.add(it) })
        viewModel.resumeExercise({ results.add("resumed") }, { results.add(it) })
        await { results.size == 2 }

        // Assert
        assertEquals(setOf("paused", "resumed"), results.toSet())
    }

    @Test
    fun `pause and resume exercise report errors when online repository throws`() {
        // Arrange
        coEvery { anyConstructed<ExerciseRepository>().pause() } throws IllegalStateException("pause failed")
        coEvery { anyConstructed<ExerciseRepository>().resume() } throws IllegalStateException("resume failed")
        val errors = CopyOnWriteArrayList<String>()
        val viewModel = newViewModel()

        // Act
        viewModel.pauseExercise({ }, { errors.add(it) })
        viewModel.resumeExercise({ }, { errors.add(it) })
        await { errors.size == 2 }

        // Assert
        assertEquals(setOf("pause failed", "resume failed"), errors.toSet())
    }

    @Test
    fun `refresh online exercise status refreshes online repository`() {
        // Arrange
        coEvery { anyConstructed<ExerciseRepository>().refresh() } returns Unit
        val viewModel = newViewModel()

        // Act
        viewModel.refreshOnlineExerciseStatus()

        // Assert
        coVerify(timeout = 5_000) { anyConstructed<ExerciseRepository>().refresh() }
    }
}
