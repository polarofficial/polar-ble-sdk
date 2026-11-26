package com.polar.polarsensordatacollector.ui.activity

import android.net.Uri
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import com.polar.polarsensordatacollector.repository.DeviceConnectionState
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.repository.ResultOfRequest
import com.polar.polarsensordatacollector.ui.utils.FileUtils
import com.polar.sdk.api.PolarBleApi.PolarActivityDataType
import com.polar.sdk.impl.utils.CaloriesType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ActivityRecordingDataViewModelTest {

    private val repository = mockk<PolarDeviceRepository>(relaxed = true)
    private val fileUtils = mockk<FileUtils>()
    private val connection = MutableStateFlow<DeviceConnectionState>(DeviceConnectionState.DeviceConnected("dev1"))
    private val fileUri: Uri = Uri.parse("content://test/file.json")
    private val from = LocalDate.of(2024, 3, 1)
    private val to = LocalDate.of(2024, 3, 2)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repository.deviceConnectionStatus } returns connection
        every { fileUtils.saveToFile(any(), any()) } returns fileUri
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun state(type: String, extra: Map<String, String> = emptyMap()) = SavedStateHandle(
        mapOf(
            "deviceIdFragmentArgument" to "dev1",
            "activityTypeFragmentArgument" to type,
            "activityStartDateFragmentArgument" to "20240301",
            "activityEndDateFragmentArgument" to "20240302"
        ) + extra
    )

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < deadline) { "Condition not reached" }
            Thread.sleep(10)
        }
    }

    private fun stub(type: PolarActivityDataType, result: ResultOfRequest<List<Any>>) {
        when (type) {
            PolarActivityDataType.SLEEP -> coEvery { repository.getSleepData("dev1", from, to) } returns (result as ResultOfRequest<List<com.polar.sdk.api.model.sleep.PolarSleepData>>)
            PolarActivityDataType.STEPS -> coEvery { repository.getStepsData("dev1", from, to) } returns (result as ResultOfRequest<List<com.polar.sdk.api.model.activity.PolarStepsData>>)
            PolarActivityDataType.CALORIES -> coEvery { repository.getCaloriesData("dev1", from, to, any()) } returns (result as ResultOfRequest<List<com.polar.sdk.api.model.activity.PolarCaloriesData>>)
            PolarActivityDataType.HR_SAMPLES -> coEvery { repository.get247HrSamplesData("dev1", from, to) } returns (result as ResultOfRequest<List<com.polar.sdk.api.model.activity.Polar247HrSamplesData>>)
            PolarActivityDataType.NIGHTLY_RECHARGE -> coEvery { repository.getNightlyRechargeData("dev1", from, to) } returns (result as ResultOfRequest<List<com.polar.sdk.api.model.sleep.PolarNightlyRechargeData>>)
            PolarActivityDataType.PPI_SAMPLES -> coEvery { repository.get247PPiSamples("dev1", from, to) } returns (result as ResultOfRequest<List<com.polar.sdk.api.model.activity.Polar247PPiSamplesData>>)
            PolarActivityDataType.SKIN_TEMPERATURE -> coEvery { repository.getSkinTemperatureData("dev1", from, to) } returns (result as ResultOfRequest<List<com.polar.sdk.api.model.PolarSkinTemperatureData>>)
            PolarActivityDataType.ACTIVE_TIME -> coEvery { repository.getActiveTimeData("dev1", from, to) } returns (result as ResultOfRequest<List<com.polar.sdk.api.model.activity.PolarActiveTimeData>>)
            PolarActivityDataType.ACTIVITY_SAMPLES -> coEvery { repository.getActivitySamplesData("dev1", from, to) } returns (result as ResultOfRequest<List<com.polar.sdk.api.model.activity.PolarActivitySamplesDayData>>)
            PolarActivityDataType.DAILY_SUMMARY -> coEvery { repository.getDailySummaryData("dev1", from, to) } returns (result as ResultOfRequest<List<com.polar.sdk.api.model.activity.PolarDailySummaryData>>)
            PolarActivityDataType.SPO2_TEST -> coEvery { repository.getSpo2TestData("dev1", from, to) } returns (result as ResultOfRequest<List<com.polar.sdk.api.model.PolarSpo2TestData>>)
            else -> error("Unsupported type $type")
        }
    }

    private val supported = mapOf(
        PolarActivityDataType.SLEEP to "/SLEEP/2024-03-01-sleep.json",
        PolarActivityDataType.STEPS to "/STEPS/2024-03-01-steps.json",
        PolarActivityDataType.CALORIES to "/CALORIES/2024-03-01-calories.json",
        PolarActivityDataType.HR_SAMPLES to "/HR_SAMPLES/2024-03-01-hr-samples.json",
        PolarActivityDataType.NIGHTLY_RECHARGE to "/NIGHTLY_RECHARGE/2024-03-01-nightly-recharge.json",
        PolarActivityDataType.PPI_SAMPLES to "/PPI_SAMPLES/2024-03-01-ppi-samples.json",
        PolarActivityDataType.SKIN_TEMPERATURE to "/SKIN_TEMPERATURE/2024-03-01-skin-temperature.json",
        PolarActivityDataType.ACTIVE_TIME to "/ACTIVE_TIME/2024-03-01-active-time.json",
        PolarActivityDataType.ACTIVITY_SAMPLES to "/ACTIVITY_SAMPLES/2024-03-01-activity-samples.json",
        PolarActivityDataType.DAILY_SUMMARY to "/DAILY_SUMMARY/2024-03-01-daily_summary.json",
        PolarActivityDataType.SPO2_TEST to "/SPO2_TEST/2024-03-01-spo2-test.json"
    )

    @Test
    fun `init throws when argument is missing`() {
        // Act and Assert
        assertThrows(Exception::class.java) {
            ActivityRecordingDataViewModel(repository, fileUtils, SavedStateHandle())
        }
    }

    @Test
    fun `fetch saves file and publishes data for every supported type`() {
        supported.forEach { (type, path) ->
            // Arrange
            stub(type, ResultOfRequest.Success(emptyList()))
            val name = slot<String>()
            every { fileUtils.saveToFile(any(), capture(name)) } returns fileUri

            // Act
            val viewModel = ActivityRecordingDataViewModel(repository, fileUtils, state(type.name))
            await { viewModel.activityDataUiState is ActivityDataUiState.FetchedData }

            // Assert
            val data = (viewModel.activityDataUiState as ActivityDataUiState.FetchedData).data
            assertEquals(type.name, type, data.dataType)
            assertEquals(type.name, fileUri, data.uri)
            assertEquals(type.name, "2024-03-01", data.startDate)
            assertEquals(type.name, path, name.captured)
        }
    }

    @Test
    fun `fetch publishes failure when data is empty for every supported type`() {
        supported.keys.forEach { type ->
            // Arrange
            stub(type, ResultOfRequest.Success())

            // Act
            val viewModel = ActivityRecordingDataViewModel(repository, fileUtils, state(type.name))
            await { viewModel.activityDataUiState is ActivityDataUiState.Failure }

            // Assert
            val failure = viewModel.activityDataUiState as ActivityDataUiState.Failure
            assertTrue(type.name, failure.message.contains("empty data"))
            assertNull(type.name, failure.throwable)
        }
    }

    @Test
    fun `fetch publishes failure with cause when request fails for every supported type`() {
        supported.keys.forEach { type ->
            // Arrange
            val cause = IllegalStateException("boom")
            stub(type, ResultOfRequest.Failure("request failed", cause))

            // Act
            val viewModel = ActivityRecordingDataViewModel(repository, fileUtils, state(type.name))
            await { viewModel.activityDataUiState is ActivityDataUiState.Failure }

            // Assert
            val failure = viewModel.activityDataUiState as ActivityDataUiState.Failure
            assertEquals(type.name, "request failed", failure.message)
            assertEquals(type.name, cause, failure.throwable)
        }
    }

    @Test
    fun `fetch calories uses calories type from arguments`() {
        // Arrange
        stub(PolarActivityDataType.CALORIES, ResultOfRequest.Success(emptyList()))

        // Act
        val viewModel = ActivityRecordingDataViewModel(
            repository, fileUtils, state("CALORIES", mapOf("caloriesTypeArgument" to "TRAINING"))
        )
        await { viewModel.activityDataUiState is ActivityDataUiState.FetchedData }

        // Assert
        coVerify { repository.getCaloriesData("dev1", from, to, CaloriesType.TRAINING) }
    }

    @Test
    fun `fetch calories uses activity type when argument is missing`() {
        // Arrange
        stub(PolarActivityDataType.CALORIES, ResultOfRequest.Success(emptyList()))

        // Act
        val viewModel = ActivityRecordingDataViewModel(repository, fileUtils, state("CALORIES"))
        await { viewModel.activityDataUiState is ActivityDataUiState.FetchedData }

        // Assert
        coVerify { repository.getCaloriesData("dev1", from, to, CaloriesType.ACTIVITY) }
    }

    @Test
    fun `fetch stays fetching for type without implementation`() {
        // Arrange
        val unsupported = PolarActivityDataType.values().first { it !in supported.keys }

        // Act
        val viewModel = ActivityRecordingDataViewModel(repository, fileUtils, state(unsupported.name))
        Thread.sleep(200)

        // Assert
        assertTrue(viewModel.activityDataUiState is ActivityDataUiState.IsFetching)
    }

    @Test
    fun `dev connection state follows repository connection status`() {
        // Arrange
        stub(PolarActivityDataType.STEPS, ResultOfRequest.Success(emptyList()))
        val viewModel = ActivityRecordingDataViewModel(repository, fileUtils, state("STEPS"))
        assertTrue(viewModel.devConnectionState.value.isConnected)

        // Act and Assert
        connection.value = DeviceConnectionState.DeviceConnecting("dev1")
        assertFalse(viewModel.devConnectionState.value.isConnected)
        connection.value = DeviceConnectionState.DeviceConnected("dev1")
        assertTrue(viewModel.devConnectionState.value.isConnected)
        connection.value = DeviceConnectionState.DeviceDisconnecting("dev1")
        assertFalse(viewModel.devConnectionState.value.isConnected)
        connection.value = DeviceConnectionState.DeviceNotConnected()
        assertFalse(viewModel.devConnectionState.value.isConnected)
    }
}
