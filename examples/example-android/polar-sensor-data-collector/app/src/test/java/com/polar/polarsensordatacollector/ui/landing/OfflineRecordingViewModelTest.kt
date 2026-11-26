package com.polar.polarsensordatacollector.ui.landing

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.repository.AvailableFeatures
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.repository.ResultOfRequest
import com.polar.polarsensordatacollector.ui.utils.DerivedDialogResult
import com.polar.polarsensordatacollector.ui.utils.MessageUiState
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.model.PolarDerivedMeasurementMethod
import com.polar.sdk.api.model.PolarDerivedMeasurementSettings
import com.polar.sdk.api.model.PolarDerivedMeasurementSettingsGroup
import com.polar.sdk.api.model.PolarSensorSetting
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
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
import java.util.EnumMap
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OfflineRecordingViewModelTest {

    private val id = "dev1"
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val repository = mockk<PolarDeviceRepository>(relaxed = true)
    private val features = MutableStateFlow(AvailableFeatures())

    private val group = PolarDerivedMeasurementSettingsGroup(
        groupId = 7,
        sourceTypes = setOf(PolarDeviceDataType.ACC),
        sourceSampleRates = setOf(25, 50),
        timeWindowOptions = setOf(500, 1000),
        supportedMethods = setOf(PolarDerivedMeasurementMethod.DOWNSAMPLE, PolarDerivedMeasurementMethod.MAX)
    )

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
        OfflineRecordingViewModel(context, repository, SavedStateHandle(mapOf(ONLINE_OFFLINE_KEY_DEVICE_ID to id)))

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Condition not reached" }
            Thread.sleep(10)
        }
    }

    private fun collect(flow: SharedFlow<MessageUiState>): List<MessageUiState> {
        val messages = CopyOnWriteArrayList<MessageUiState>()
        CoroutineScope(Dispatchers.Unconfined).launch { flow.collect { messages.add(it) } }
        return messages
    }

    private fun setting(vararg values: Int): PolarSensorSetting {
        val s = mockk<PolarSensorSetting>(relaxed = true)
        every { s.settings } returns mutableMapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to values.toSet())
        return s
    }

    private fun offlineFeatures(vararg types: PolarDeviceDataType, identifier: String = id): AvailableFeatures {
        val map = EnumMap<PolarDeviceDataType, Boolean>(PolarDeviceDataType::class.java)
        types.forEach { map[it] = true }
        return AvailableFeatures(identifier = identifier, availableOfflineFeatures = map)
    }

    private fun stubDerivedGroup() {
        coEvery { repository.requestDerivedMeasurementGroupIds(id, PolarDeviceDataType.ACC) } returns ResultOfRequest.Success(setOf(7))
        coEvery { repository.requestDerivedMeasurementSettingsGroup(id, 7) } returns ResultOfRequest.Success(group)
    }

    @Test
    fun `init throws when identifier is missing`() {
        // Act and Assert
        assertThrows(Exception::class.java) { OfflineRecordingViewModel(context, repository, SavedStateHandle()) }
    }

    @Test
    fun `available features are filtered by identifier and exclude derived`() {
        // Arrange
        val viewModel = newViewModel()

        // Act
        features.value = offlineFeatures(PolarDeviceDataType.ACC, PolarDeviceDataType.DERIVED_MEASUREMENT)

        // Assert
        val map = viewModel.uiAvailableOfflineRecTypesState.value.offlineRecordingsAvailableOfflineRecordingsState
        assertEquals(true, map[PolarDeviceDataType.ACC])
        assertFalse(map.containsKey(PolarDeviceDataType.DERIVED_MEASUREMENT))
    }

    @Test
    fun `available features of other device are ignored`() {
        // Arrange
        val viewModel = newViewModel()

        // Act
        features.value = offlineFeatures(PolarDeviceDataType.ACC, identifier = "other")

        // Assert
        assertEquals("", viewModel.uiAvailableOfflineRecTypesState.value.identifier)
    }

    @Test
    fun `offline features available fetches recording status`() {
        // Arrange
        coEvery { repository.requestOfflineRecordingStatus(id) } returns ResultOfRequest.Success(listOf(PolarDeviceDataType.ECG))
        val viewModel = newViewModel()

        // Act
        features.value = offlineFeatures(PolarDeviceDataType.ECG)
        await { viewModel.uiOfflineRecordingState.value is OfflineRecordingUiState.Enabled }

        // Assert
        val state = viewModel.uiOfflineRecordingState.value as OfflineRecordingUiState.Enabled
        assertEquals(listOf(PolarDeviceDataType.ECG), state.recordingFeatures)
    }

    @Test
    fun `recording status failure emits error`() {
        // Arrange
        coEvery { repository.requestOfflineRecordingStatus(id) } returns ResultOfRequest.Failure("status failed", null)
        val viewModel = newViewModel()
        val errors = collect(viewModel.uiShowError)

        // Act
        features.value = offlineFeatures(PolarDeviceDataType.ECG)
        await { errors.isNotEmpty() }

        // Assert
        assertEquals("status failed", errors.first().header)
    }

    @Test
    fun `request offline rec settings publishes settings`() {
        // Arrange
        val available = setting(25, 50)
        val all = setting(25, 50, 100)
        coEvery { repository.getOfflineRecSettings(id, PolarDeviceDataType.ACC) } returns available
        coEvery { repository.getFullOfflineRecSettings(id, PolarDeviceDataType.ACC) } returns all
        coEvery { repository.requestDerivedMeasurementGroupIds(any(), any()) } returns ResultOfRequest.Success(emptySet())
        val viewModel = newViewModel()

        // Act
        viewModel.requestOfflineRecSettings(id, PolarDeviceDataType.ACC)
        await { viewModel.uiOfflineRequestedSettingsState.value != null }

        // Assert
        val state = viewModel.uiOfflineRequestedSettingsState.value!!
        assertEquals(PolarDeviceDataType.ACC, state.feature)
        assertEquals(available, state.settings.currentlyAvailable)
        assertEquals(all, state.settings.allPossibleSettings)
        assertNull(state.settings.derivedSettingsGroup)
    }

    @Test
    fun `request offline rec settings includes derived group when available`() {
        // Arrange
        coEvery { repository.getOfflineRecSettings(id, PolarDeviceDataType.ACC) } returns setting(25)
        coEvery { repository.getFullOfflineRecSettings(id, PolarDeviceDataType.ACC) } throws IllegalStateException("no full")
        stubDerivedGroup()
        val viewModel = newViewModel()

        // Act
        viewModel.requestOfflineRecSettings(id, PolarDeviceDataType.ACC)
        await { viewModel.uiOfflineRequestedSettingsState.value != null }

        // Assert
        assertEquals(group, viewModel.uiOfflineRequestedSettingsState.value!!.settings.derivedSettingsGroup)
    }

    @Test
    fun `request offline rec settings emits error when settings are empty`() {
        // Arrange
        val empty = mockk<PolarSensorSetting>(relaxed = true)
        every { empty.settings } returns mutableMapOf()
        coEvery { repository.getOfflineRecSettings(id, PolarDeviceDataType.ACC) } returns empty
        val viewModel = newViewModel()
        val errors = collect(viewModel.uiShowError)

        // Act
        viewModel.requestOfflineRecSettings(id, PolarDeviceDataType.ACC)
        await { errors.isNotEmpty() }

        // Assert
        assertTrue(errors.first().header.contains("Settings are not available"))
        assertNull(viewModel.uiOfflineRequestedSettingsState.value)
    }

    @Test
    fun `request offline rec settings emits error when fetch throws`() {
        // Arrange
        coEvery { repository.getOfflineRecSettings(any(), any()) } throws IllegalStateException("boom")
        val viewModel = newViewModel()
        val errors = collect(viewModel.uiShowError)

        // Act
        viewModel.requestOfflineRecSettings(id, PolarDeviceDataType.ACC)
        await { errors.isNotEmpty() }

        // Assert
        assertTrue(errors.first().header.contains("Settings fetch error"))
    }

    @Test
    fun `clear offline rec settings request clears state`() {
        // Arrange
        coEvery { repository.getOfflineRecSettings(any(), any()) } returns setting(25)
        coEvery { repository.requestDerivedMeasurementGroupIds(any(), any()) } returns ResultOfRequest.Success(emptySet())
        val viewModel = newViewModel()
        viewModel.requestOfflineRecSettings(id, PolarDeviceDataType.ACC)
        await { viewModel.uiOfflineRequestedSettingsState.value != null }

        // Act
        viewModel.clearOfflineRecSettingsRequest()

        // Assert
        assertNull(viewModel.uiOfflineRequestedSettingsState.value)
    }

    @Test
    fun `start offline recording uses max settings for sensor features`() {
        // Arrange
        coEvery { repository.getOfflineRecSettings(id, PolarDeviceDataType.ACC) } returns setting(25, 100, 50)
        val captured = slot<PolarSensorSetting>()
        coEvery { repository.startOfflineRecording(id, PolarDeviceDataType.ACC, capture(captured)) } returns ResultOfRequest.Success()
        val viewModel = newViewModel()

        // Act
        viewModel.startOfflineRecording(listOf(PolarDeviceDataType.ACC))
        coVerify(timeout = 5_000) { repository.startOfflineRecording(id, PolarDeviceDataType.ACC, any()) }

        // Assert
        assertEquals(100, captured.captured.settings[PolarSensorSetting.SettingType.SAMPLE_RATE]!!.first())
    }

    @Test
    fun `start offline recording uses no settings for hr`() {
        // Arrange
        coEvery { repository.startOfflineRecording(id, PolarDeviceDataType.HR, null) } returns ResultOfRequest.Success()

        // Act
        newViewModel().startOfflineRecording(listOf(PolarDeviceDataType.HR))

        // Assert
        coVerify(timeout = 5_000) { repository.startOfflineRecording(id, PolarDeviceDataType.HR, null) }
    }

    @Test
    fun `start offline recording stops feature and shows error when start fails`() {
        // Arrange
        coEvery { repository.startOfflineRecording(id, PolarDeviceDataType.HR, null) } returns
            ResultOfRequest.Failure("start failed", null)
        coEvery { repository.stopOfflineRecording(id, PolarDeviceDataType.HR) } returns ResultOfRequest.Success()
        val viewModel = newViewModel()
        val errors = collect(viewModel.uiShowError)

        // Act
        viewModel.startOfflineRecording(listOf(PolarDeviceDataType.HR))
        await { errors.isNotEmpty() }

        // Assert
        assertEquals("start failed", errors.first().header)
        coVerify { repository.stopOfflineRecording(id, PolarDeviceDataType.HR) }
    }

    @Test
    fun `start offline recording shows error when settings cannot be read`() {
        // Arrange
        coEvery { repository.getOfflineRecSettings(any(), any()) } throws IllegalStateException("no settings")
        val viewModel = newViewModel()
        val errors = collect(viewModel.uiShowError)

        // Act
        viewModel.startOfflineRecording(listOf(PolarDeviceDataType.ACC))
        await { errors.isNotEmpty() }

        // Assert
        assertTrue(errors.first().header.contains("Couldn't get settings"))
        coVerify(exactly = 0) { repository.startOfflineRecording(any(), any(), any()) }
    }

    @Test
    fun `start offline recording starts derived recording for acc when derived settings selected`() {
        // Arrange
        coEvery { repository.getOfflineRecSettings(id, PolarDeviceDataType.ACC) } returns setting(25)
        coEvery { repository.getFullOfflineRecSettings(any(), any()) } returns setting(25)
        stubDerivedGroup()
        coEvery { repository.startDerivedOfflineRecording(any(), any()) } returns ResultOfRequest.Success()
        val viewModel = newViewModel()
        viewModel.requestOfflineRecSettings(id, PolarDeviceDataType.ACC)
        await { viewModel.uiOfflineRequestedSettingsState.value != null }
        viewModel.updateSelectedStreamSettings(
            PolarDeviceDataType.ACC,
            mapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to 25),
            DerivedDialogResult(setOf(PolarDerivedMeasurementMethod.MAX), 50, 500),
            hadDerivedSection = true
        )

        // Act
        viewModel.startOfflineRecording(listOf(PolarDeviceDataType.ACC))

        // Assert
        val expected = PolarDerivedMeasurementSettings(7, PolarDeviceDataType.ACC, 50, 500, setOf(PolarDerivedMeasurementMethod.MAX))
        coVerify(timeout = 5_000) { repository.startDerivedOfflineRecording(id, expected) }
        coVerify { repository.stopDerivedOfflineRecording(id) }
        coVerify(exactly = 0) { repository.startOfflineRecording(any(), any(), any()) }
    }

    @Test
    fun `update selected stream settings drops derived settings when section was shown without result`() {
        // Arrange
        coEvery { repository.getOfflineRecSettings(id, PolarDeviceDataType.ACC) } returns setting(25)
        coEvery { repository.getFullOfflineRecSettings(any(), any()) } returns setting(25)
        stubDerivedGroup()
        coEvery { repository.startOfflineRecording(any(), any(), any()) } returns ResultOfRequest.Success()
        val viewModel = newViewModel()
        viewModel.requestOfflineRecSettings(id, PolarDeviceDataType.ACC)
        await { viewModel.uiOfflineRequestedSettingsState.value != null }
        val settings = mapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to 25)
        viewModel.updateSelectedStreamSettings(PolarDeviceDataType.ACC, settings, DerivedDialogResult(setOf(PolarDerivedMeasurementMethod.MAX), 50, 500), true)

        // Act
        viewModel.updateSelectedStreamSettings(PolarDeviceDataType.ACC, settings, null, hadDerivedSection = true)
        viewModel.startOfflineRecording(listOf(PolarDeviceDataType.ACC))

        // Assert
        coVerify(timeout = 5_000) { repository.startOfflineRecording(id, PolarDeviceDataType.ACC, any()) }
        coVerify(exactly = 0) { repository.startDerivedOfflineRecording(any(), any()) }
    }

    @Test
    fun `start offline recording shows error when derived start fails`() {
        // Arrange
        coEvery { repository.getOfflineRecSettings(id, PolarDeviceDataType.ACC) } returns setting(25)
        coEvery { repository.getFullOfflineRecSettings(any(), any()) } returns setting(25)
        stubDerivedGroup()
        coEvery { repository.startDerivedOfflineRecording(any(), any()) } returns ResultOfRequest.Failure("derived failed", null)
        val viewModel = newViewModel()
        val errors = collect(viewModel.uiShowError)
        viewModel.requestOfflineRecSettings(id, PolarDeviceDataType.ACC)
        await { viewModel.uiOfflineRequestedSettingsState.value != null }
        viewModel.updateSelectedStreamSettings(
            PolarDeviceDataType.ACC,
            emptyMap(),
            DerivedDialogResult(setOf(PolarDerivedMeasurementMethod.MAX), 50, 500),
            true
        )

        // Act
        viewModel.startOfflineRecording(listOf(PolarDeviceDataType.ACC))
        await { errors.isNotEmpty() }

        // Assert
        assertEquals("ACC computed data recording failed", errors.first().header)
    }

    @Test
    fun `stop offline recording stops feature and refreshes status`() {
        // Arrange
        coEvery { repository.stopOfflineRecording(id, PolarDeviceDataType.ECG) } returns ResultOfRequest.Success()
        coEvery { repository.requestOfflineRecordingStatus(id) } returns ResultOfRequest.Success(emptyList())
        val viewModel = newViewModel()

        // Act
        viewModel.stopOfflineRecording(listOf(PolarDeviceDataType.ECG))

        // Assert
        coVerify(timeout = 5_000) { repository.stopOfflineRecording(id, PolarDeviceDataType.ECG) }
        coVerify(timeout = 5_000) { repository.requestOfflineRecordingStatus(id) }
    }

    @Test
    fun `stop offline recording shows error when stop fails`() {
        // Arrange
        coEvery { repository.stopOfflineRecording(id, PolarDeviceDataType.ECG) } returns ResultOfRequest.Failure("stop failed", null)
        val viewModel = newViewModel()
        val errors = collect(viewModel.uiShowError)

        // Act
        viewModel.stopOfflineRecording(listOf(PolarDeviceDataType.ECG))
        await { errors.isNotEmpty() }

        // Assert
        assertEquals("stop failed", errors.first().header)
    }

    @Test
    fun `stop offline recording stops derived recording when acc runs as derived`() {
        // Arrange
        coEvery { repository.requestOfflineRecordingStatus(id) } returns
            ResultOfRequest.Success(listOf(PolarDeviceDataType.ACC, PolarDeviceDataType.DERIVED_MEASUREMENT))
        val viewModel = newViewModel()
        features.value = offlineFeatures(PolarDeviceDataType.ACC)
        await { viewModel.uiOfflineRecordingState.value is OfflineRecordingUiState.Enabled }

        // Act
        viewModel.stopOfflineRecording(listOf(PolarDeviceDataType.ACC))

        // Assert
        coVerify(timeout = 5_000) { repository.stopDerivedOfflineRecording(id) }
        coVerify(exactly = 0) { repository.stopOfflineRecording(any(), any()) }
    }

    @Test
    fun `request derived measurement settings publishes best group with defaults`() {
        // Arrange
        stubDerivedGroup()
        val viewModel = newViewModel()

        // Act
        viewModel.requestDerivedMeasurementSettings(id)
        await { viewModel.uiDerivedSettingsState.value != null }

        // Assert
        val state = viewModel.uiDerivedSettingsState.value!!
        assertEquals(group, state.settingsGroup)
        assertEquals(50, state.selectedSourceRate)
        assertEquals(1000, state.selectedTimeWindowMs)
        assertEquals(setOf(PolarDerivedMeasurementMethod.DOWNSAMPLE), state.selectedMethods)
    }

    @Test
    fun `request derived measurement settings shows error when group ids fail`() {
        // Arrange
        coEvery { repository.requestDerivedMeasurementGroupIds(id, PolarDeviceDataType.ACC) } returns ResultOfRequest.Failure("x", null)
        val viewModel = newViewModel()
        val errors = collect(viewModel.uiShowError)

        // Act
        viewModel.requestDerivedMeasurementSettings(id)
        await { errors.isNotEmpty() }

        // Assert
        assertEquals(context.getString(R.string.derived_error_group_ids_failed), errors.first().header)
    }

    @Test
    fun `request derived measurement settings shows error when no groups`() {
        // Arrange
        coEvery { repository.requestDerivedMeasurementGroupIds(id, PolarDeviceDataType.ACC) } returns ResultOfRequest.Success(emptySet())
        val viewModel = newViewModel()
        val errors = collect(viewModel.uiShowError)

        // Act
        viewModel.requestDerivedMeasurementSettings(id)
        await { errors.isNotEmpty() }

        // Assert
        assertEquals(context.getString(R.string.derived_error_no_groups_for_acc), errors.first().header)
    }

    @Test
    fun `request derived measurement settings shows error when no group is usable`() {
        // Arrange
        coEvery { repository.requestDerivedMeasurementGroupIds(id, PolarDeviceDataType.ACC) } returns ResultOfRequest.Success(setOf(7))
        coEvery { repository.requestDerivedMeasurementSettingsGroup(id, 7) } returns ResultOfRequest.Failure("x", null)
        val viewModel = newViewModel()
        val errors = collect(viewModel.uiShowError)

        // Act
        viewModel.requestDerivedMeasurementSettings(id)
        await { errors.isNotEmpty() }

        // Assert
        assertEquals(context.getString(R.string.derived_error_no_usable_group), errors.first().header)
    }

    @Test
    fun `derived selection updates refresh ui state and ignore invalid values`() {
        // Arrange
        stubDerivedGroup()
        val viewModel = newViewModel()
        viewModel.requestDerivedMeasurementSettings(id)
        await { viewModel.uiDerivedSettingsState.value != null }

        // Act
        viewModel.updateSelectedDerivedSourceRate(25)
        viewModel.updateSelectedDerivedTimeWindowMs(500)
        viewModel.updateSelectedDerivedMethods(setOf(PolarDerivedMeasurementMethod.MAX))

        // Assert
        var state = viewModel.uiDerivedSettingsState.value!!
        assertEquals(25, state.selectedSourceRate)
        assertEquals(500, state.selectedTimeWindowMs)
        assertEquals(setOf(PolarDerivedMeasurementMethod.MAX), state.selectedMethods)

        // Act
        viewModel.updateSelectedDerivedSourceRate(999)
        viewModel.updateSelectedDerivedTimeWindowMs(999)
        viewModel.updateSelectedDerivedMethods(emptySet())

        // Assert
        state = viewModel.uiDerivedSettingsState.value!!
        assertEquals(25, state.selectedSourceRate)
        assertEquals(500, state.selectedTimeWindowMs)
        assertEquals(setOf(PolarDerivedMeasurementMethod.DOWNSAMPLE), state.selectedMethods)
    }

    @Test
    fun `derived selection updates do nothing before group is loaded`() {
        // Arrange
        val viewModel = newViewModel()

        // Act
        viewModel.updateSelectedDerivedSourceRate(25)
        viewModel.updateSelectedDerivedTimeWindowMs(500)

        // Assert
        assertNull(viewModel.uiDerivedSettingsState.value)
    }

    @Test
    fun `start derived offline recording shows error when no settings loaded`() {
        // Arrange
        val viewModel = newViewModel()
        val errors = collect(viewModel.uiShowError)

        // Act
        viewModel.startDerivedOfflineRecording()
        await { errors.isNotEmpty() }

        // Assert
        assertEquals(context.getString(R.string.derived_error_no_settings), errors.first().header)
    }

    @Test
    fun `start derived offline recording starts with selected values and shows info`() {
        // Arrange
        stubDerivedGroup()
        coEvery { repository.startDerivedOfflineRecording(any(), any()) } returns ResultOfRequest.Success()
        val viewModel = newViewModel()
        val infos = collect(viewModel.uiShowInfo)
        viewModel.requestDerivedMeasurementSettings(id)
        await { viewModel.uiDerivedSettingsState.value != null }

        // Act
        viewModel.startDerivedOfflineRecording()
        await { infos.isNotEmpty() }

        // Assert
        val expected = PolarDerivedMeasurementSettings(7, PolarDeviceDataType.ACC, 50, 1000, setOf(PolarDerivedMeasurementMethod.DOWNSAMPLE))
        coVerify { repository.startDerivedOfflineRecording(id, expected) }
        assertEquals(DerivedRecordingUiState.Recording, viewModel.uiDerivedRecordingState.value)
        assertEquals(context.getString(R.string.derived_info_recording_started), infos.first().header)
    }

    @Test
    fun `start derived offline recording resets state and shows error when start fails`() {
        // Arrange
        stubDerivedGroup()
        coEvery { repository.startDerivedOfflineRecording(any(), any()) } returns ResultOfRequest.Failure("derived failed", null)
        val viewModel = newViewModel()
        val errors = collect(viewModel.uiShowError)
        viewModel.requestDerivedMeasurementSettings(id)
        await { viewModel.uiDerivedSettingsState.value != null }

        // Act
        viewModel.startDerivedOfflineRecording()
        await { errors.isNotEmpty() }

        // Assert
        assertEquals("derived failed", errors.first().header)
        assertEquals(DerivedRecordingUiState.NotRecording, viewModel.uiDerivedRecordingState.value)
    }

    @Test
    fun `stop derived offline recording shows info when success`() {
        // Arrange
        coEvery { repository.stopDerivedOfflineRecording(id) } returns ResultOfRequest.Success()
        val viewModel = newViewModel()
        val infos = collect(viewModel.uiShowInfo)

        // Act
        viewModel.stopDerivedOfflineRecording()
        await { infos.isNotEmpty() }

        // Assert
        assertEquals(context.getString(R.string.derived_info_recording_stopped), infos.first().header)
        assertEquals(DerivedRecordingUiState.NotRecording, viewModel.uiDerivedRecordingState.value)
    }

    @Test
    fun `stop derived offline recording shows error when fails`() {
        // Arrange
        coEvery { repository.stopDerivedOfflineRecording(id) } returns ResultOfRequest.Failure("stop derived failed", null)
        val viewModel = newViewModel()
        val errors = collect(viewModel.uiShowError)

        // Act
        viewModel.stopDerivedOfflineRecording()
        await { errors.isNotEmpty() }

        // Assert
        assertEquals("stop derived failed", errors.first().header)
    }
}
