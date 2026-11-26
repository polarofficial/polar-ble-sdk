package com.polar.polarsensordatacollector.ui.logging

import androidx.lifecycle.SavedStateHandle
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.repository.ResultOfRequest
import com.polar.polarsensordatacollector.ui.landing.ONLINE_OFFLINE_KEY_DEVICE_ID
import com.polar.sdk.api.model.Errorlog
import com.polar.sdk.api.model.HrSensorConfig
import com.polar.sdk.api.model.LogConfig
import com.polar.sdk.api.model.PolarDeviceLog
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import android.os.Looper

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LoggingViewModelTest {
    private val deviceId = "A1B2C3"
    private val repository = mockk<PolarDeviceRepository>(relaxed = true)
    private val initialConfig = LogConfig(
        ohrLogEnabled = false, ppiLogEnabled = false, accelerationLogEnabled = false,
        skinTemperatureLogEnabled = false, metLogEnabled = false, caloriesLogEnabled = false, sleepLogEnabled = false
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { repository.getLogConfig(deviceId) } returns ResultOfRequest.Success(initialConfig)
        coEvery { repository.setLogConfig(any(), any()) } returns ResultOfRequest.Success(0)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() =
        LoggingViewModel(repository, SavedStateHandle(mapOf(ONLINE_OFFLINE_KEY_DEVICE_ID to deviceId)))

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (true) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            check(System.currentTimeMillis() < deadline) { "Condition not reached" }
            Thread.sleep(10)
        }
    }

    @Test
    fun `init throws when identifier is missing`() {
        // Act and Assert
        assertThrows(Exception::class.java) { LoggingViewModel(repository, SavedStateHandle()) }
    }

    @Test
    fun `init loads log config when read succeeds`() {
        // Act
        val viewModel = newViewModel()

        // Assert
        assertEquals(deviceId, viewModel.uiIdentifier)
        assertEquals(false, viewModel.uiLogConfigState.value.ohrLogEnabled)
    }

    @Test
    fun `init keeps default config when read fails`() {
        // Arrange
        coEvery { repository.getLogConfig(deviceId) } returns ResultOfRequest.Failure("err", null)

        // Act
        val viewModel = newViewModel()

        // Assert
        assertEquals(LogConfig(), viewModel.uiLogConfigState.value)
    }

    @Test
    fun `logging toggles flip each setting and write to device`() {
        // Arrange
        val viewModel = newViewModel()
        val toggles: List<Pair<() -> Unit, (LogConfig) -> Boolean?>> = listOf(
            { viewModel.ohrLogging() } to { c: LogConfig -> c.ohrLogEnabled },
            { viewModel.ppiLogging() } to { c: LogConfig -> c.ppiLogEnabled },
            { viewModel.accLogging() } to { c: LogConfig -> c.accelerationLogEnabled },
            { viewModel.skinTempLogging() } to { c: LogConfig -> c.skinTemperatureLogEnabled },
            { viewModel.metLogging() } to { c: LogConfig -> c.metLogEnabled },
            { viewModel.caloriesLogging() } to { c: LogConfig -> c.caloriesLogEnabled },
            { viewModel.sleepLogging() } to { c: LogConfig -> c.sleepLogEnabled },
        )

        for ((toggle, read) in toggles) {
            // Act
            toggle()

            // Assert
            await { read(viewModel.uiLogConfigState.value) == true }
        }
        coVerify(exactly = toggles.size) { repository.setLogConfig(deviceId, any()) }
    }

    @Test
    fun `set hr sensor log config writes and stores sensor config`() {
        // Arrange
        val viewModel = newViewModel()
        val written = slot<LogConfig>()
        coEvery { repository.setLogConfig(deviceId, capture(written)) } returns ResultOfRequest.Success(0)

        // Act
        viewModel.setHrSensorLogConfig("Polar H10 1234", true)
        await { viewModel.uiLogConfigState.value.hrSensorConfig != null }

        // Assert
        val expected = HrSensorConfig("Polar H10 1234", true)
        assertEquals(expected, written.captured.hrSensorConfig)
        assertEquals(expected, viewModel.uiLogConfigState.value.hrSensorConfig)
    }

    @Test
    fun `fetch error log posts error log when fetch succeeds`() {
        // Arrange
        val errorLog = mockk<Errorlog>()
        coEvery { repository.fetchErrorLog(deviceId) } returns ResultOfRequest.Success(errorLog)
        val viewModel = newViewModel()
        viewModel.errorlogLiveData.observeForever { }

        // Act
        viewModel.fetchErrorLog()
        await { viewModel.errorlogLiveData.value != null }

        // Assert
        assertSame(errorLog, viewModel.errorlogLiveData.value)
    }

    @Test
    fun `fetch error log does not throw when repository throws`() {
        // Arrange
        coEvery { repository.fetchErrorLog(deviceId) } throws RuntimeException("boom")
        val viewModel = newViewModel()

        // Act
        viewModel.fetchErrorLog()

        // Assert
        coVerify(timeout = 5_000) { repository.fetchErrorLog(deviceId) }
        assertEquals(null, viewModel.errorlogLiveData.value)
    }

    @Test
    fun `export device logs calls on success when logs returned`() {
        // Arrange
        val logs = listOf(PolarDeviceLog("/a", byteArrayOf(1)))
        coEvery { repository.exportDeviceLogs(deviceId) } returns ResultOfRequest.Success(logs)
        var result: List<PolarDeviceLog>? = null

        // Act
        newViewModel().exportDeviceLogs(onSuccess = { result = it }, onError = {})

        // Assert
        assertSame(logs, result)
    }

    @Test
    fun `export device logs calls on error when no logs or failure`() {
        // Arrange
        val errors = mutableListOf<String>()
        val viewModel = newViewModel()
        coEvery { repository.exportDeviceLogs(deviceId) } returns ResultOfRequest.Success(null)

        // Act
        viewModel.exportDeviceLogs(onSuccess = {}, onError = { errors.add(it) })
        coEvery { repository.exportDeviceLogs(deviceId) } returns ResultOfRequest.Failure("export failed", null)
        viewModel.exportDeviceLogs(onSuccess = {}, onError = { errors.add(it) })

        // Assert
        assertEquals(listOf("No logs returned from device", "export failed"), errors)
    }

    @Test
    fun `wear logging can be enabled`() = verifyWearToggle(false, true)

    @Test
    fun `wear logging can be disabled`() = verifyWearToggle(true, false)

    @Test
    fun `absent wear flag can be enabled without crashing`() = verifyWearToggle(null, true)

    private fun verifyWearToggle(initialWear: Boolean?, expectedWear: Boolean) = runBlocking {
        val initial = LogConfig(ohrLogEnabled = true, ppiLogEnabled = false, wearLogEnabled = initialWear)
        val expected = initial.copy(wearLogEnabled = expectedWear)
        coEvery { repository.getLogConfig(deviceId) } returns ResultOfRequest.Success(initial)
        coEvery { repository.setLogConfig(deviceId, expected) } returns ResultOfRequest.Success(0)
        val viewModel = LoggingViewModel(repository, SavedStateHandle(mapOf(ONLINE_OFFLINE_KEY_DEVICE_ID to deviceId)))
        assertEquals(initial, viewModel.uiLogConfigState.value)

        viewModel.wearLogging()

        val updated = withTimeout(5_000) {
            viewModel.uiLogConfigState.first { it.wearLogEnabled == expectedWear }
        }
        assertEquals(expected, updated)
        coVerify(exactly = 1) { repository.setLogConfig(deviceId, expected) }
    }
}