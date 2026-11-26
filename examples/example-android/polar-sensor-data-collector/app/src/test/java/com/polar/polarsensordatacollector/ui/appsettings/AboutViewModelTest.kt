package com.polar.polarsensordatacollector.ui.appsettings

import com.polar.polarsensordatacollector.BuildConfig
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class AboutViewModelTest {

    private val sdkVersion = MutableStateFlow("1.2.3")
    private val repository = mockk<PolarDeviceRepository>(relaxed = true) {
        every { polarBleSdkVersion } returns sdkVersion
    }

    @Test
    fun `ui app version returns build version name`() {
        // Act
        val viewModel = AboutViewModel(repository)

        // Assert
        assertEquals(BuildConfig.VERSION_NAME, viewModel.uiAppVersion.value)
    }

    @Test
    fun `ui polar ble sdk version follows repository`() {
        // Arrange
        val viewModel = AboutViewModel(repository)

        // Act
        sdkVersion.value = "4.5.6"

        // Assert
        assertEquals("4.5.6", viewModel.uiPolarBleSdkVersion.value)
    }
}
