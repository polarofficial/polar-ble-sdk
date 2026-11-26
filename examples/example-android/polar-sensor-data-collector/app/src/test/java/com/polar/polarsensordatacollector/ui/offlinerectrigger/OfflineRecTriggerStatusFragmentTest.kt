package com.polar.polarsensordatacollector.ui.offlinerectrigger

import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.testutil.HiltTestSupport
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.model.PolarOfflineRecordingTrigger
import com.polar.sdk.api.model.PolarOfflineRecordingTriggerMode
import com.polar.sdk.api.model.PolarSensorSetting
import com.polar.sdk.impl.BDBleApiImpl
import io.mockk.coEvery
import io.mockk.coVerify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OfflineRecTriggerStatusFragmentTest {

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

    private fun launch(): OfflineRecTriggerStatusFragment {
        val fragment = OfflineRecTriggerStatusFragment()
        HiltTestSupport.host(fragment, Bundle().apply { putString(OFFLINE_REC_TRIG_KEY_DEVICE_ID, deviceId) })
        HiltTestSupport.awaitUntil { fragment.view != null }
        return fragment
    }

    private fun OfflineRecTriggerStatusFragment.text(id: Int) = requireView().findViewById<TextView>(id)
    private fun OfflineRecTriggerStatusFragment.visibility(id: Int) = requireView().findViewById<View>(id).visibility

    private fun settings() = PolarSensorSetting(
        mapOf(
            PolarSensorSetting.SettingType.SAMPLE_RATE to 52,
            PolarSensorSetting.SettingType.RESOLUTION to 16,
            PolarSensorSetting.SettingType.RANGE to 8,
            PolarSensorSetting.SettingType.CHANNELS to 3
        )
    )

    private fun awaitMode(fragment: OfflineRecTriggerStatusFragment, mode: String) {
        HiltTestSupport.awaitUntil { fragment.text(R.id.trigger_status_mode).text == mode }
    }

    @Test
    fun `status shows feature settings when trigger is system start`() {
        // Arrange
        coEvery { api.getOfflineRecordingTriggerSetup(deviceId) } returns PolarOfflineRecordingTrigger(
            PolarOfflineRecordingTriggerMode.TRIGGER_SYSTEM_START,
            mapOf(PolarDeviceDataType.ACC to settings(), PolarDeviceDataType.HR to null)
        )

        // Act
        val fragment = launch()

        // Assert
        awaitMode(fragment, "SYSTEM START")
        val acc = fragment.requireView().findViewById<View>(R.id.acc_trigger_status)
        assertEquals(View.VISIBLE, acc.visibility)
        assertEquals("ACC", acc.findViewById<TextView>(R.id.feature_selection_feature_header).text)
        assertEquals("52", acc.findViewById<TextView>(R.id.feature_selection_sample_rate).text)
        assertEquals("16", acc.findViewById<TextView>(R.id.feature_selection_resolution).text)
        assertEquals("8", acc.findViewById<TextView>(R.id.feature_selection_range).text)
        assertEquals("3", acc.findViewById<TextView>(R.id.feature_selection_channels).text)
        assertEquals(View.VISIBLE, fragment.visibility(R.id.hr_trigger_status))
        assertEquals(View.GONE, fragment.visibility(R.id.ecg_trigger_status))
        assertEquals(View.VISIBLE, fragment.visibility(R.id.trigger_status_settings_header))
        assertEquals(View.GONE, fragment.visibility(R.id.trigger_status_fetching_progress))
    }

    @Test
    fun `status hides settings when trigger is disabled`() {
        // Arrange
        coEvery { api.getOfflineRecordingTriggerSetup(deviceId) } returns PolarOfflineRecordingTrigger(
            PolarOfflineRecordingTriggerMode.TRIGGER_DISABLED,
            mapOf(PolarDeviceDataType.ACC to settings())
        )

        // Act
        val fragment = launch()

        // Assert
        awaitMode(fragment, "DISABLED")
        assertEquals(View.GONE, fragment.visibility(R.id.acc_trigger_status))
        assertEquals(View.GONE, fragment.visibility(R.id.trigger_status_settings_header))
        assertEquals(View.VISIBLE, fragment.visibility(R.id.trigger_status_mode_header))
    }

    @Test
    fun `status shows all feature headers when all features are triggered`() {
        // Arrange
        val expected = mapOf(
            PolarDeviceDataType.ECG to Pair(R.id.ecg_trigger_status, "ECG"),
            PolarDeviceDataType.PPG to Pair(R.id.ppg_trigger_status, "PPG"),
            PolarDeviceDataType.PPI to Pair(R.id.ppi_trigger_status, "PPI"),
            PolarDeviceDataType.GYRO to Pair(R.id.gyr_trigger_status, "GYR"),
            PolarDeviceDataType.MAGNETOMETER to Pair(R.id.mag_trigger_status, "MAG"),
            PolarDeviceDataType.PRESSURE to Pair(R.id.pressure_trigger_status, "PRE"),
            PolarDeviceDataType.LOCATION to Pair(R.id.loc_trigger_status, "LOC"),
            PolarDeviceDataType.TEMPERATURE to Pair(R.id.temperature_trigger_status, "TEM"),
            PolarDeviceDataType.SKIN_TEMPERATURE to Pair(R.id.skin_temperature_trigger_status, "SKIN_TEM")
        )
        coEvery { api.getOfflineRecordingTriggerSetup(deviceId) } returns PolarOfflineRecordingTrigger(
            PolarOfflineRecordingTriggerMode.TRIGGER_EXERCISE_START,
            expected.keys.associateWith { null }
        )

        // Act
        val fragment = launch()

        // Assert
        awaitMode(fragment, "EXERCISE START")
        expected.values.forEach { (viewId, header) ->
            val view = fragment.requireView().findViewById<View>(viewId)
            assertEquals(View.VISIBLE, view.visibility)
            assertEquals(header, view.findViewById<TextView>(R.id.feature_selection_feature_header).text)
        }
    }

    @Test
    fun `status shows progress when fetch never completes`() {
        // Arrange
        coEvery { api.getOfflineRecordingTriggerSetup(deviceId) } coAnswers { kotlinx.coroutines.awaitCancellation() }

        // Act
        val fragment = launch()

        // Assert
        assertEquals(fragment.getString(R.string.fetching_status), fragment.text(R.id.trigger_status_mode).text)
        assertEquals(View.VISIBLE, fragment.visibility(R.id.trigger_status_fetching_progress))
    }

    @Test
    fun `status hides mode and settings when fetch fails`() {
        // Arrange
        coEvery { api.getOfflineRecordingTriggerSetup(deviceId) } throws IllegalStateException("boom")

        // Act
        val fragment = launch()

        // Assert
        HiltTestSupport.awaitUntil { fragment.visibility(R.id.trigger_status_fetching_progress) == View.GONE }
        assertEquals(View.GONE, fragment.visibility(R.id.trigger_status_mode))
        assertEquals(View.GONE, fragment.visibility(R.id.trigger_status_settings_header))
        coVerify(exactly = 1) { api.getOfflineRecordingTriggerSetup(deviceId) }
    }

    @Test
    fun `status hides settings button of hr and ppi when trigger is system start`() {
        // Arrange
        coEvery { api.getOfflineRecordingTriggerSetup(deviceId) } returns PolarOfflineRecordingTrigger(
            PolarOfflineRecordingTriggerMode.TRIGGER_SYSTEM_START,
            mapOf(PolarDeviceDataType.HR to null, PolarDeviceDataType.PPI to null, PolarDeviceDataType.ACC to settings())
        )

        // Act
        val fragment = launch()

        // Assert
        awaitMode(fragment, "SYSTEM START")
        fun settingsButton(id: Int) = fragment.requireView().findViewById<View>(id).findViewById<View>(R.id.feature_selection_settings_button)
        assertEquals(View.GONE, settingsButton(R.id.hr_trigger_status).visibility)
        assertEquals(View.GONE, settingsButton(R.id.ppi_trigger_status).visibility)
    }
}
