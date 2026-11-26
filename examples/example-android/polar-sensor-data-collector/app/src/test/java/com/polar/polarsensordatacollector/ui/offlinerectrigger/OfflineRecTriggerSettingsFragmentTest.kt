package com.polar.polarsensordatacollector.ui.offlinerectrigger

import android.os.Bundle
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.testutil.HiltTestSupport
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallbackProvider
import com.polar.sdk.api.model.PolarOfflineRecordingTrigger
import com.polar.sdk.api.model.PolarOfflineRecordingTriggerMode
import com.polar.sdk.api.model.PolarSensorSetting
import com.polar.sdk.impl.BDBleApiImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class OfflineRecTriggerSettingsFragmentTest {

    private val deviceId = "dev1"
    private lateinit var api: BDBleApiImpl
    private val callback = slot<PolarBleApiCallbackProvider>()

    @Before
    fun setUp() {
        api = HiltTestSupport.install()
        io.mockk.every { api.setApiCallback(capture(callback)) } returns Unit
    }

    @After
    fun tearDown() {
        HiltTestSupport.uninstall()
    }

    private fun launch(): OfflineRecTriggerSettingsFragment {
        val fragment = OfflineRecTriggerSettingsFragment()
        HiltTestSupport.host(fragment, Bundle().apply { putString(OFFLINE_REC_TRIG_KEY_DEVICE_ID, deviceId) })
        HiltTestSupport.awaitUntil { fragment.view != null }
        return fragment
    }

    private fun makeAvailable(vararg types: PolarBleApi.PolarDeviceDataType) {
        coEvery { api.getAvailableOfflineRecordingDataTypes(deviceId) } returns types.toSet()
        callback.captured.bleSdkFeaturesReadiness(deviceId, listOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING), emptyList())
    }

    private fun OfflineRecTriggerSettingsFragment.v(id: Int) = requireView().findViewById<View>(id)
    private fun View.checkBox() = findViewById<CheckBox>(R.id.feature_selection_select_check_box)
    private fun OfflineRecTriggerSettingsFragment.setMode(mode: String) {
        (v(R.id.offline_trigger_settings_setup_type).findViewById<AutoCompleteTextView>(R.id.offline_trigger_settings_setup_type_text_field))
            .setText(mode, false)
    }

    @Test
    fun `features show only available ones when offline features are ready`() {
        // Arrange
        val fragment = launch()

        // Act
        makeAvailable(PolarBleApi.PolarDeviceDataType.ACC, PolarBleApi.PolarDeviceDataType.HR)

        // Assert
        HiltTestSupport.awaitUntil { fragment.v(R.id.acc_trigger_settings).visibility == View.VISIBLE }
        assertEquals("ACC", fragment.v(R.id.acc_trigger_settings).findViewById<TextView>(R.id.feature_selection_feature_header).text)
        assertEquals(View.VISIBLE, fragment.v(R.id.hr_trigger_settings).visibility)
        assertEquals(View.GONE, fragment.v(R.id.ecg_trigger_settings).visibility)
    }

    @Test
    fun `settings button is gone for hr and ppi`() {
        // Act
        val fragment = launch()

        // Assert
        assertEquals(View.GONE, fragment.v(R.id.hr_trigger_settings).findViewById<View>(R.id.feature_selection_settings_button).visibility)
        assertEquals(View.GONE, fragment.v(R.id.ppi_trigger_settings).findViewById<View>(R.id.feature_selection_settings_button).visibility)
    }

    @Test
    fun `setup button is disabled when no offline feature is available`() {
        // Act
        val fragment = launch()

        // Assert
        assertFalse(fragment.v(R.id.trigger_setup_button).isEnabled)
    }

    @Test
    fun `feature selection is disabled when mode is disabled`() {
        // Arrange
        val fragment = launch()

        // Act
        makeAvailable(PolarBleApi.PolarDeviceDataType.ACC)
        HiltTestSupport.awaitUntil { fragment.v(R.id.acc_trigger_settings).visibility == View.VISIBLE }

        // Assert
        assertFalse(fragment.v(R.id.acc_trigger_settings).checkBox().isEnabled)
    }

    @Test
    fun `feature selection is enabled when mode is system start`() {
        // Arrange
        val fragment = launch()
        makeAvailable(PolarBleApi.PolarDeviceDataType.ACC)
        HiltTestSupport.awaitUntil { fragment.v(R.id.acc_trigger_settings).visibility == View.VISIBLE }

        // Act
        fragment.setMode("SYSTEM START")

        // Assert
        assertTrue(fragment.v(R.id.acc_trigger_settings).checkBox().isEnabled)
    }

    @Test
    fun `setup button sets disabled trigger when mode is disabled`() {
        // Arrange
        val fragment = launch()
        makeAvailable(PolarBleApi.PolarDeviceDataType.ACC)
        HiltTestSupport.awaitUntil { fragment.v(R.id.acc_trigger_settings).visibility == View.VISIBLE }
        coEvery { api.setOfflineRecordingTrigger(any(), any(), any()) } returns Unit

        // Act
        fragment.v(R.id.trigger_setup_button).performClick()

        // Assert
        coVerify(timeout = 5_000) {
            api.setOfflineRecordingTrigger(deviceId, match<PolarOfflineRecordingTrigger> { it.triggerMode == PolarOfflineRecordingTriggerMode.TRIGGER_DISABLED }, any())
        }
    }

    @Test
    fun `setup button shows none selected when mode is active and nothing is checked`() {
        // Arrange
        val fragment = launch()
        makeAvailable(PolarBleApi.PolarDeviceDataType.ACC)
        HiltTestSupport.awaitUntil { fragment.v(R.id.acc_trigger_settings).visibility == View.VISIBLE }
        fragment.setMode("EXERCISE START")

        // Act
        fragment.v(R.id.trigger_setup_button).performClick()

        // Assert
        assertEquals(fragment.getString(R.string.none_selected), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `setup button sets trigger with max settings when feature is checked`() {
        // Arrange
        val fragment = launch()
        makeAvailable(PolarBleApi.PolarDeviceDataType.ACC)
        HiltTestSupport.awaitUntil { fragment.v(R.id.acc_trigger_settings).visibility == View.VISIBLE }
        coEvery { api.requestOfflineRecordingSettings(deviceId, PolarBleApi.PolarDeviceDataType.ACC) } returns
            PolarSensorSetting(mapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to 52))
        coEvery { api.setOfflineRecordingTrigger(any(), any(), any()) } returns Unit
        fragment.setMode("SYSTEM START")
        fragment.v(R.id.acc_trigger_settings).checkBox().isChecked = true

        // Act
        fragment.v(R.id.trigger_setup_button).performClick()

        // Assert
        coVerify(timeout = 5_000) {
            api.setOfflineRecordingTrigger(
                deviceId,
                match<PolarOfflineRecordingTrigger> {
                    it.triggerMode == PolarOfflineRecordingTriggerMode.TRIGGER_SYSTEM_START &&
                        it.triggerFeatures[PolarBleApi.PolarDeviceDataType.ACC]?.settings?.get(PolarSensorSetting.SettingType.SAMPLE_RATE)?.first() == 52
                },
                any()
            )
        }
    }

    @Test
    fun `settings button requests settings and shows dialog when clicked`() {
        // Arrange
        val fragment = launch()
        makeAvailable(PolarBleApi.PolarDeviceDataType.ACC)
        HiltTestSupport.awaitUntil { fragment.v(R.id.acc_trigger_settings).visibility == View.VISIBLE }
        coEvery { api.requestOfflineRecordingSettings(deviceId, PolarBleApi.PolarDeviceDataType.ACC) } returns
            PolarSensorSetting(mapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to 52))
        fragment.setMode("SYSTEM START")
        val button = fragment.v(R.id.acc_trigger_settings).findViewById<Button>(R.id.feature_selection_settings_button)

        // Act
        button.performClick()

        // Assert
        coVerify(timeout = 5_000) { api.requestOfflineRecordingSettings(deviceId, PolarBleApi.PolarDeviceDataType.ACC) }
        HiltTestSupport.awaitUntil { ShadowDialog.getLatestDialog() != null }
        assertTrue(ShadowDialog.getLatestDialog().isShowing)
    }
}
