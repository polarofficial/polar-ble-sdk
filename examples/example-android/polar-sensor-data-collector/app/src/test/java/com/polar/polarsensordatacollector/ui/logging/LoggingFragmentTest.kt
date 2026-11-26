package com.polar.polarsensordatacollector.ui.logging

import android.os.Bundle
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import com.google.android.material.switchmaterial.SwitchMaterial
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.testutil.HiltTestSupport
import com.polar.polarsensordatacollector.ui.landing.ONLINE_OFFLINE_KEY_DEVICE_ID
import com.polar.sdk.api.model.HrSensorConfig
import com.polar.sdk.api.model.LogConfig
import com.polar.sdk.impl.BDBleApiImpl
import io.mockk.coEvery
import io.mockk.coVerify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class LoggingFragmentTest {

    private val deviceId = "dev1"
    private lateinit var api: BDBleApiImpl

    @Before
    fun setUp() {
        api = HiltTestSupport.install()
    }

    @After
    fun tearDown() {
        HiltTestSupport.uninstall()
    }

    private fun config(on: Boolean, hr: HrSensorConfig? = null) = LogConfig(
        ohrLogEnabled = on,
        ppiLogEnabled = on,
        accelerationLogEnabled = on,
        skinTemperatureLogEnabled = on,
        metLogEnabled = on,
        caloriesLogEnabled = on,
        sleepLogEnabled = on,
        hrSensorConfig = hr
    )

    private fun launch(config: LogConfig = config(false)): LoggingFragment {
        coEvery { api.getLogConfig(deviceId) } returns config
        val fragment = LoggingFragment()
        HiltTestSupport.host(fragment, Bundle().apply { putString(ONLINE_OFFLINE_KEY_DEVICE_ID, deviceId) })
        HiltTestSupport.awaitUntil { fragment.view != null }
        return fragment
    }

    private fun LoggingFragment.button(id: Int) = requireView().findViewById<Button>(id)

    @Test
    fun `log config state shows enable texts when all logs are off`() {
        // Act
        val fragment = launch(config(false))

        // Assert
        HiltTestSupport.awaitUntil { fragment.button(R.id.enable_ohr_logging_button).text == fragment.getString(R.string.ohr_logging_enable) }
        assertEquals(fragment.getString(R.string.ppi_logging_enable), fragment.button(R.id.enable_ppi_logging_button).text)
        assertEquals(fragment.getString(R.string.acc_logging_enable), fragment.button(R.id.enable_acc_logging_button).text)
        assertEquals(fragment.getString(R.string.sleep_logging_enable), fragment.button(R.id.enable_sleep_logging_button).text)
    }

    @Test
    fun `log config state shows disable texts and hr config when all logs are on`() {
        // Arrange
        val config = config(true, HrSensorConfig("H10 12345", true))

        // Act
        val fragment = launch(config)

        // Assert
        HiltTestSupport.awaitUntil { fragment.button(R.id.enable_ohr_logging_button).text == fragment.getString(R.string.ohr_logging_disable) }
        assertEquals(fragment.getString(R.string.ppi_logging_disable), fragment.button(R.id.enable_ppi_logging_button).text)
        assertEquals(fragment.getString(R.string.acc_logging_disable), fragment.button(R.id.enable_acc_logging_button).text)
        assertEquals(fragment.getString(R.string.skin_temp_logging_disable), fragment.button(R.id.enable_skin_temperature_logging_button).text)
        assertEquals(fragment.getString(R.string.met_logging_disable), fragment.button(R.id.enable_met_logging_button).text)
        assertEquals(fragment.getString(R.string.calories_logging_disable), fragment.button(R.id.enable_calories_logging_button).text)
        assertEquals(fragment.getString(R.string.sleep_logging_disable), fragment.button(R.id.enable_sleep_logging_button).text)
        assertEquals("H10 12345", fragment.requireView().findViewById<EditText>(R.id.set_hr_log_settings_input).text.toString())
        assertTrue(fragment.requireView().findViewById<SwitchMaterial>(R.id.wait_for_connection_toggle).isChecked)
    }

    @Test
    fun `logging buttons write toggled config when clicked`() {
        // Arrange
        val fragment = launch(config(true))
        // Wait until the ViewModel has the stored config. A null state fails the toggle.
        HiltTestSupport.awaitUntil { fragment.button(R.id.enable_ohr_logging_button).text == fragment.getString(R.string.ohr_logging_disable) }
        val written = mutableListOf<LogConfig>()
        coEvery { api.setLogConfig(any(), capture(written)) } returns Unit
        val buttons = listOf(
            R.id.enable_ohr_logging_button,
            R.id.enable_ppi_logging_button,
            R.id.enable_acc_logging_button,
            R.id.enable_skin_temperature_logging_button,
            R.id.enable_met_logging_button,
            R.id.enable_calories_logging_button,
            R.id.enable_sleep_logging_button
        )

        // Act
        // Assert
        buttons.forEachIndexed { index, id ->
            fragment.button(id).performClick()
            coVerify(timeout = 5_000, atLeast = index + 1) { api.setLogConfig(deviceId, any()) }
        }
        val last = written.toList()
        val fields = listOf<(LogConfig) -> Boolean?>(
            { it.ohrLogEnabled }, { it.ppiLogEnabled }, { it.accelerationLogEnabled },
            { it.skinTemperatureLogEnabled }, { it.metLogEnabled }, { it.caloriesLogEnabled },
            { it.sleepLogEnabled }
        )
        fields.forEachIndexed { index, field -> assertEquals(false, field(last[index])) }
    }

    @Test
    fun `hr sensor button saves config and shows toast when clicked`() {
        // Arrange
        val fragment = launch()
        coEvery { api.setLogConfig(any(), any()) } returns Unit
        fragment.requireView().findViewById<EditText>(R.id.set_hr_log_settings_input).setText("Sensor A")

        // Act
        fragment.button(R.id.set_hr_log_settings_button).performClick()

        // Assert
        assertEquals(fragment.getString(R.string.hr_sensor_log_updated), ShadowToast.getTextOfLatestToast())
        coVerify(timeout = 5_000) {
            api.setLogConfig(deviceId, match { it.hrSensorConfig?.deviceInformation == "Sensor A" })
        }
    }

    @Test
    fun `export device logs button enables button again when device returns error`() {
        // Arrange
        val fragment = launch()
        coEvery { api.exportDeviceLogs(any()) } throws IllegalStateException("boom")

        // Act
        fragment.button(R.id.export_device_logs_button).performClick()

        // Assert
        HiltTestSupport.awaitUntil { fragment.button(R.id.export_device_logs_button).isEnabled }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(fragment.button(R.id.export_device_logs_button).isEnabled)
    }
}
