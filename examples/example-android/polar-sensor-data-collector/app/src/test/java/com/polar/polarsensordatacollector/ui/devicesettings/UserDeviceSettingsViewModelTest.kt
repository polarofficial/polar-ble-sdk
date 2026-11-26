package com.polar.polarsensordatacollector.ui.devicesettings

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.sdk.api.errors.PolarDeviceNotConnected
import com.polar.sdk.api.errors.PolarDeviceNotFound
import com.polar.sdk.api.errors.PolarServiceNotAvailable
import com.polar.sdk.api.model.PolarUserDeviceSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class UserDeviceSettingsViewModelTest {

    private val deviceId = "dev1"
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val repository = mockk<PolarDeviceRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { repository.getUserDeviceSettings(deviceId) } returns PolarUserDeviceSettings()
    }

    @After
    fun tearDown() {
        // Stop the jobs of each view model. Old jobs must not run in later tests.
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        Dispatchers.resetMain()
    }

    private val createdViewModels = mutableListOf<UserDeviceSettingsViewModel>()

    private fun newViewModel() =
        UserDeviceSettingsViewModel(repository, SavedStateHandle(mapOf(UserDeviceSettingsViewModel.KEY_IDENTIFIER to deviceId)), app)
            .also { createdViewModels += it }

    @Test
    fun `init throws when identifier is missing`() {
        // Act and Assert
        assertThrows(IllegalArgumentException::class.java) {
            UserDeviceSettingsViewModel(repository, SavedStateHandle(), app)
        }
    }

    @Test
    fun `init loads settings into ui state`() {
        // Arrange
        coEvery { repository.getUserDeviceSettings(deviceId) } returns PolarUserDeviceSettings(
            deviceLocation = 3,
            usbConnectionMode = true,
            automaticTrainingDetectionMode = true,
            automaticTrainingDetectionSensitivity = 2,
            telemetryEnabled = true,
            minimumTrainingDurationSeconds = 60,
            autosFilesEnabled = true
        )

        // Act
        val state = newViewModel().uiState.value

        // Assert
        assertEquals(
            UserDeviceSettingsViewModel.UserDeviceSettingsUiState(3, true, true, 2, 60, true, true, false),
            state
        )
    }

    @Test
    fun `init uses defaults when settings are null`() {
        // Act
        val state = newViewModel().uiState.value

        // Assert
        assertEquals(UserDeviceSettingsViewModel.UserDeviceSettingsUiState(isLoading = false), state)
    }

    @Test
    fun `init shows message when device is not connected`() {
        // Arrange
        coEvery { repository.getUserDeviceSettings(deviceId) } throws PolarDeviceNotConnected()

        // Act
        val viewModel = newViewModel()

        // Assert
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(
            app.getString(R.string.failed_to_load_settings, app.getString(R.string.user_device_settings_device_not_connected)),
            viewModel.message.value
        )
    }

    @Test
    fun `init describes device not found and service not available`() {
        // Arrange
        coEvery { repository.getUserDeviceSettings(deviceId) } throws PolarDeviceNotFound()
        val notFound = newViewModel().message.value
        coEvery { repository.getUserDeviceSettings(deviceId) } throws PolarServiceNotAvailable()

        // Act
        val noService = newViewModel().message.value

        // Assert
        assertEquals(
            app.getString(R.string.failed_to_load_settings, app.getString(R.string.user_device_settings_device_session_not_found)),
            notFound
        )
        assertEquals(
            app.getString(R.string.failed_to_load_settings, app.getString(R.string.user_device_settings_service_not_found)),
            noService
        )
    }

    @Test
    fun `init uses localized message when error is generic`() {
        // Arrange
        coEvery { repository.getUserDeviceSettings(deviceId) } throws IllegalStateException("bad state")

        // Act
        val viewModel = newViewModel()

        // Assert
        assertEquals(app.getString(R.string.failed_to_load_settings, "bad state"), viewModel.message.value)
    }

    @Test
    fun `save settings writes all settings and reports saved`() {
        // Arrange
        val viewModel = newViewModel()
        assertNull(viewModel.message.value)

        // Act
        viewModel.saveSettings(1, true, false, 2, 30, true, false)

        // Assert
        coVerify {
            repository.setUserDeviceLocation(deviceId, 1)
            repository.setUsbConnectionMode(deviceId, true)
            repository.setAutomaticTrainingDetectionSettings(deviceId, false, 2, 30)
            repository.setTelemetryEnabled(deviceId, true)
            repository.setDaylightSavingTime(deviceId)
            repository.setAutosFilesEnabled(deviceId, false)
        }
        assertEquals(app.getString(R.string.user_device_settings_saved), viewModel.message.value)
    }

    @Test
    fun `save settings reports failure when repository throws`() {
        // Arrange
        val viewModel = newViewModel()
        coEvery { repository.setUsbConnectionMode(deviceId, any()) } throws PolarDeviceNotConnected()

        // Act
        viewModel.saveSettings(1, true, false, 2, 30, true, false)

        // Assert
        val message = viewModel.message.value
        assertTrue(message!!.contains(app.getString(R.string.user_device_settings_device_not_connected)))
        coVerify(exactly = 0) { repository.setTelemetryEnabled(any(), any()) }
    }
}
