package com.polar.polarsensordatacollector.ui.offlinerectrigger

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.repository.AvailableFeatures
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.repository.ResultOfRequest
import com.polar.polarsensordatacollector.ui.utils.MessageUiState
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarOfflineRecordingTrigger
import com.polar.sdk.api.model.PolarOfflineRecordingTriggerMode
import com.polar.sdk.api.model.PolarSensorSetting
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.EnumMap
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OfflineTriggerSettingsViewModelTest {

    private val deviceId = "dev1"
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val repository = mockk<PolarDeviceRepository>(relaxed = true)
    private val features = MutableStateFlow(AvailableFeatures())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repository.availableFeatures } returns features
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() =
        OfflineTriggerSettingsViewModel(repository, SavedStateHandle(mapOf(OFFLINE_REC_TRIG_KEY_DEVICE_ID to deviceId)), context)

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Condition not reached" }
            Thread.sleep(10)
        }
    }

    private fun collectMessages(flow: SharedFlow<MessageUiState>): List<MessageUiState> {
        val messages = CopyOnWriteArrayList<MessageUiState>()
        CoroutineScope(Dispatchers.Unconfined).launch { flow.collect { messages.add(it) } }
        return messages
    }

    private fun settingOf(vararg values: Int): PolarSensorSetting {
        val setting = mockk<PolarSensorSetting>(relaxed = true)
        every { setting.settings } returns mutableMapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to values.toSet())
        return setting
    }

    @Test
    fun `init throws when identifier is missing`() {
        // Act and Assert
        assertThrows(Exception::class.java) {
            OfflineTriggerSettingsViewModel(repository, SavedStateHandle(), context)
        }
    }

    @Test
    fun `init publishes available offline features`() {
        // Arrange
        val viewModel = newViewModel()
        val offline = EnumMap<PolarBleApi.PolarDeviceDataType, Boolean>(PolarBleApi.PolarDeviceDataType::class.java)
        offline[PolarBleApi.PolarDeviceDataType.ACC] = true

        // Act
        features.value = AvailableFeatures(identifier = deviceId, availableOfflineFeatures = offline)

        // Assert
        val state = viewModel.uiAvailableOfflineRecTypesState.value
        assertEquals(deviceId, state.identifier)
        assertEquals(true, state.offlineRecordingsAvailableOfflineRecordingsState[PolarBleApi.PolarDeviceDataType.ACC])
    }

    @Test
    fun `request stream settings publishes settings when fetch succeeds`() {
        // Arrange
        val setting = settingOf(25, 50)
        coEvery { repository.getOfflineRecSettings(deviceId, PolarBleApi.PolarDeviceDataType.ACC) } returns setting
        val viewModel = newViewModel()

        // Act
        viewModel.requestStreamSettings(PolarBleApi.PolarDeviceDataType.ACC)
        await { viewModel.uiOfflineRecTriggerSettingsState.value != null }

        // Assert
        val state = viewModel.uiOfflineRecTriggerSettingsState.value!!
        assertEquals(PolarBleApi.PolarDeviceDataType.ACC, state.feature)
        assertEquals(setting, state.settings.currentlyAvailable)
        assertNull(state.settings.selectedSettings)
    }

    @Test
    fun `request stream settings keeps selected settings when already selected`() {
        // Arrange
        val selected = mapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to 25)
        coEvery { repository.getOfflineRecSettings(deviceId, PolarBleApi.PolarDeviceDataType.ACC) } returns settingOf(25, 50)
        val viewModel = newViewModel()
        viewModel.updateSelectedStreamSettings(PolarBleApi.PolarDeviceDataType.ACC, selected)

        // Act
        viewModel.requestStreamSettings(PolarBleApi.PolarDeviceDataType.ACC)
        await { viewModel.uiOfflineRecTriggerSettingsState.value != null }

        // Assert
        assertEquals(selected, viewModel.uiOfflineRecTriggerSettingsState.value!!.settings.selectedSettings)
    }

    @Test
    fun `request stream settings emits error when fetch fails`() {
        // Arrange
        coEvery { repository.getOfflineRecSettings(any(), any()) } throws IllegalStateException("no link")
        val viewModel = newViewModel()
        val errors = collectMessages(viewModel.uiShowError)

        // Act
        viewModel.requestStreamSettings(PolarBleApi.PolarDeviceDataType.ACC)
        await { errors.isNotEmpty() }

        // Assert
        assertTrue(errors.first().header.contains("Settings fetch error"))
        assertNull(viewModel.uiOfflineRecTriggerSettingsState.value)
    }

    @Test
    fun `clear offline rec trigger settings request clears state`() {
        // Arrange
        coEvery { repository.getOfflineRecSettings(any(), any()) } returns settingOf(25)
        val viewModel = newViewModel()
        viewModel.requestStreamSettings(PolarBleApi.PolarDeviceDataType.ACC)
        await { viewModel.uiOfflineRecTriggerSettingsState.value != null }

        // Act
        viewModel.clearOfflineRecTriggerSettingsRequest()

        // Assert
        assertNull(viewModel.uiOfflineRecTriggerSettingsState.value)
    }

    @Test
    fun `set offline recording trigger uses max settings and shows info when success`() {
        // Arrange
        coEvery { repository.getOfflineRecSettings(deviceId, PolarBleApi.PolarDeviceDataType.ACC) } returns settingOf(25, 100, 50)
        val triggerSlot = slot<PolarOfflineRecordingTrigger>()
        coEvery { repository.setOfflineRecordingTrigger(deviceId, capture(triggerSlot)) } returns ResultOfRequest.Success()
        val viewModel = newViewModel()
        val infos = collectMessages(viewModel.uiShowInfo)

        // Act
        viewModel.setOfflineRecordingTrigger(
            PolarOfflineRecordingTriggerMode.TRIGGER_SYSTEM_START,
            listOf(PolarBleApi.PolarDeviceDataType.ACC, PolarBleApi.PolarDeviceDataType.HR)
        )
        await { infos.isNotEmpty() }

        // Assert
        val trigger = triggerSlot.captured
        assertEquals(PolarOfflineRecordingTriggerMode.TRIGGER_SYSTEM_START, trigger.triggerMode)
        assertEquals(
            mapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to 100),
            trigger.triggerFeatures[PolarBleApi.PolarDeviceDataType.ACC]!!.settings.mapValues { it.value.first() }
        )
        assertNotNull(trigger.triggerFeatures[PolarBleApi.PolarDeviceDataType.HR])
        assertEquals(context.getString(R.string.offline_trigger_set_successfully), infos.first().header)
        await { viewModel.uiOfflineRecTriggerSetup.value == OfflineRecSettingsTriggerUiState.ReadyToSetUpTriggers }
    }

    @Test
    fun `set offline recording trigger reuses cached settings on second call`() {
        // Arrange
        coEvery { repository.getOfflineRecSettings(deviceId, PolarBleApi.PolarDeviceDataType.ACC) } returns settingOf(25)
        coEvery { repository.setOfflineRecordingTrigger(any(), any()) } returns ResultOfRequest.Success()
        val viewModel = newViewModel()
        val infos = collectMessages(viewModel.uiShowInfo)

        // Act
        repeat(2) { index ->
            viewModel.setOfflineRecordingTrigger(
                PolarOfflineRecordingTriggerMode.TRIGGER_SYSTEM_START,
                listOf(PolarBleApi.PolarDeviceDataType.ACC)
            )
            await { infos.size == index + 1 }
        }

        // Assert
        coVerify(exactly = 1) { repository.getOfflineRecSettings(deviceId, PolarBleApi.PolarDeviceDataType.ACC) }
    }

    @Test
    fun `set offline recording trigger emits error when set fails`() {
        // Arrange
        coEvery { repository.setOfflineRecordingTrigger(any(), any()) } returns
            ResultOfRequest.Failure("trigger failed", IllegalStateException("cause"))
        val viewModel = newViewModel()
        val errors = collectMessages(viewModel.uiShowError)

        // Act
        viewModel.setOfflineRecordingTrigger(
            PolarOfflineRecordingTriggerMode.TRIGGER_EXERCISE_START,
            listOf(PolarBleApi.PolarDeviceDataType.PPI)
        )
        await { errors.isNotEmpty() }

        // Assert
        assertEquals("trigger failed", errors.first().header)
        assertEquals("cause", errors.first().description)
        await { viewModel.uiOfflineRecTriggerSetup.value == OfflineRecSettingsTriggerUiState.ReadyToSetUpTriggers }
    }
}
