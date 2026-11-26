package com.polar.polarsensordatacollector.ui.exercise

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarTrainingSessionApi
import com.polar.sdk.api.model.PolarExerciseSession.ExerciseInfo
import com.polar.sdk.api.model.PolarExerciseSession.ExerciseStatus
import com.polar.sdk.api.model.PolarExerciseSession.SportProfile
import io.mockk.coEvery
import io.mockk.coVerify
import com.polar.polarsensordatacollector.testutil.HiltTestSupport
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController

@RunWith(RobolectricTestRunner::class)
class ExerciseServiceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val api = mockk<PolarTrainingSessionApi>(relaxed = true)
    private val bleApi = mockk<PolarBleApi>(relaxed = true)

    @Before
    fun setUp() {
        ServiceLocator.exerciseApi = api
        ServiceLocator.bleApi = bleApi
        every { api.observeExerciseStatus(any()) } returns flow { awaitCancellation() }
        coEvery { api.getExerciseStatus(any()) } returns ExerciseInfo(ExerciseStatus.NOT_STARTED, SportProfile.UNKNOWN, null, 0L)
    }

    private val controllers = mutableListOf<ServiceController<ExerciseService>>()

    // Destroy each service. This stops its 5 second refresh loop.
    @After
    fun tearDown() {
        controllers.forEach { runCatching { it.destroy() } }
        controllers.clear()
    }

    private fun controller(): ServiceController<ExerciseService> =
        Robolectric.buildService(ExerciseService::class.java).also { controllers.add(it) }

    private fun intent(action: String? = null, deviceId: String? = "dev1") =
        Intent(context, ExerciseService::class.java).apply {
            this.action = action
            deviceId?.let { putExtra(ExerciseService.EXTRA_DEVICE_ID, it) }
        }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun notificationManager() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Test
    fun `on create creates notification channel`() {
        // Act
        controller().create()

        // Assert
        assertNotNull(notificationManager().getNotificationChannel("exercise_channel"))
    }

    @Test
    fun `on start command returns not sticky when device id is missing`() {
        // Arrange
        val service = controller().create().get()

        // Act
        val result = service.onStartCommand(intent(deviceId = null), 0, 1)

        // Assert
        assertEquals(Service.START_NOT_STICKY, result)
        assertNull(shadowOf(service).lastForegroundNotification)
    }

    @Test
    fun `on start command returns not sticky when intent is null`() {
        // Arrange
        val service = controller().create().get()

        // Assert
        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(null, 0, 1))
    }

    @Test
    fun `on start command starts foreground and observes status when device id is set`() {
        // Arrange
        val service = controller().create().get()

        // Act
        val result = service.onStartCommand(intent(), 0, 1)
        idle()

        // Assert
        assertEquals(Service.START_STICKY, result)
        assertNotNull(shadowOf(service).lastForegroundNotification)
        io.mockk.verify(timeout = 5_000) { api.observeExerciseStatus("dev1") }
    }

    @Test
    fun `on start command creates repository once when called twice`() {
        // Arrange
        val service = controller().create().get()

        // Act
        service.onStartCommand(intent(), 0, 1)
        service.onStartCommand(intent(), 0, 2)

        // Assert
        idle()
        io.mockk.verify(atLeast = 1, timeout = 5_000) { api.observeExerciseStatus("dev1") }
        // Give a late second call time to happen.
        Thread.sleep(500)
        idle()
        io.mockk.verify(exactly = 1) { api.observeExerciseStatus("dev1") }
    }

    @Test
    fun `on start command pauses exercise when action is pause`() {
        // Arrange
        val service = controller().create().get()
        coEvery { api.pauseExercise(any()) } returns Unit

        // Act
        service.onStartCommand(intent(ExerciseService.ACTION_PAUSE), 0, 1)
        idle()

        // Assert
        coVerify(timeout = 5_000) { api.pauseExercise("dev1") }
    }

    @Test
    fun `on start command resumes exercise when action is resume`() {
        // Arrange
        val service = controller().create().get()
        coEvery { api.resumeExercise(any()) } returns Unit

        // Act
        service.onStartCommand(intent(ExerciseService.ACTION_RESUME), 0, 1)
        idle()

        // Assert
        coVerify(timeout = 5_000) { api.resumeExercise("dev1") }
    }

    @Test
    fun `on start command stops exercise when action is stop`() {
        // Arrange
        val service = controller().create().get()
        coEvery { api.stopExercise(any(), any()) } returns Unit

        // Act
        service.onStartCommand(intent(ExerciseService.ACTION_STOP), 0, 1)
        idle()

        // Assert
        coVerify(timeout = 5_000) { api.stopExercise("dev1", any()) }
    }

    @Test
    fun `on start command survives failure when action fails`() {
        // Arrange
        val service = controller().create().get()
        coEvery { api.pauseExercise(any()) } throws IllegalStateException("boom")

        // Act
        val result = service.onStartCommand(intent(ExerciseService.ACTION_PAUSE), 0, 1)
        idle()

        // Assert
        assertEquals(Service.START_STICKY, result)
    }

    private fun assertNotificationText(info: ExerciseInfo, expectedText: String) {
        // Arrange
        shadowOf(context as android.app.Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        every { api.observeExerciseStatus(any()) } returns flow { emit(info); awaitCancellation() }
        val service = controller().create().get()

        // Act
        service.onStartCommand(intent(), 0, 1)

        // Assert
        val deadline = System.currentTimeMillis() + 5_000
        var text: String? = null
        while (System.currentTimeMillis() < deadline) {
            idle()
            text = shadowOf(notificationManager()).getNotification(4821)?.extras?.getString("android.text")
            if (text == expectedText) break
            Thread.sleep(20)
        }
        assertEquals(expectedText, text)
    }

    @Test
    fun `state posts in progress text when exercise is in progress`() {
        assertNotificationText(
            ExerciseInfo(ExerciseStatus.IN_PROGRESS, SportProfile.RUNNING, null, SportProfile.RUNNING.id.toLong()),
            context.getString(com.polar.polarsensordatacollector.R.string.exercise_in_progress)
        )
    }

    @Test
    fun `state posts paused text when exercise is paused`() {
        assertNotificationText(
            ExerciseInfo(ExerciseStatus.PAUSED, SportProfile.UNKNOWN, null, 99L),
            context.getString(com.polar.polarsensordatacollector.R.string.exercise_paused)
        )
    }

    @Test
    fun `state posts syncing text when sync is required`() {
        assertNotificationText(
            ExerciseInfo(ExerciseStatus.SYNC_REQUIRED, SportProfile.RUNNING, null, 1L),
            context.getString(com.polar.polarsensordatacollector.R.string.exercise_syncing)
        )
    }

    @Test
    fun `state posts idle text when exercise is not started`() {
        assertNotificationText(
            ExerciseInfo(ExerciseStatus.NOT_STARTED, SportProfile.RUNNING, null, 1L),
            context.getString(com.polar.polarsensordatacollector.R.string.exercise_idle)
        )
    }

    @Test
    fun `on bind returns null`() {
        // Arrange
        val service = controller().create().get()

        // Assert
        assertNull(service.onBind(intent()))
    }

    @Test
    fun `on destroy cancels status observation`() {
        // Arrange
        val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
        every { api.observeExerciseStatus(any()) } returns flow {
            try { awaitCancellation() } finally { cancelled.set(true) }
        }
        val controller = controller().create()
        controller.get().onStartCommand(intent(), 0, 1)
        idle()

        // Act
        controller.destroy()
        idle()

        // Assert
        HiltTestSupport.awaitUntil { cancelled.get() }
        assertTrue(cancelled.get())
    }
}
