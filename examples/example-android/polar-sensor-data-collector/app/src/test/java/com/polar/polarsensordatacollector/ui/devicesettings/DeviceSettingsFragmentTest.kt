package com.polar.polarsensordatacollector.ui.devicesettings

import android.app.AlertDialog
import android.app.Application
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.testutil.HiltTestSupport
import com.polar.polarsensordatacollector.ui.landing.ONLINE_OFFLINE_KEY_DEVICE_ID
import com.polar.sdk.impl.BDBleApiImpl
import io.mockk.coEvery
import io.mockk.coVerify
import com.polar.sdk.api.model.sleep.PolarSleepRecordingStatus
import io.mockk.every
import io.mockk.mockk
import com.polar.polarsensordatacollector.repository.SdkMode
import com.polar.sdk.api.model.PolarDiskSpaceData
import com.polar.sdk.api.model.PolarPhysicalConfiguration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
class DeviceSettingsFragmentTest {

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

    private fun launch(): DeviceSettingsFragment {
        val fragment = DeviceSettingsFragment()
        HiltTestSupport.host(fragment, Bundle().apply { putString(ONLINE_OFFLINE_KEY_DEVICE_ID, deviceId) })
        HiltTestSupport.awaitUntil { fragment.view != null }
        return fragment
    }

    private fun DeviceSettingsFragment.button(id: Int): Button = requireView().findViewById(id)

    private fun confirmDialog() {
        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
    }

    private fun verifyEventually(block: suspend () -> Unit) {
        HiltTestSupport.awaitUntil { runCatching { coVerify { block() } }.isSuccess }
    }

    @Test
    fun `wait for connection text shows waiting when fragment is created`() {
        // Act
        val fragment = launch()

        // Assert
        val text = fragment.requireView().findViewById<TextView>(R.id.wait_connection_status_text)
        HiltTestSupport.awaitUntil { text.text == fragment.getString(R.string.waiting_connection) }
    }

    @Test
    fun `wait for connection button shows connected when connection succeeds`() {
        // Arrange
        val fragment = launch()

        // Act
        fragment.button(R.id.wait_connection_button).performClick()

        // Assert
        val text = fragment.requireView().findViewById<TextView>(R.id.wait_connection_status_text)
        HiltTestSupport.awaitUntil { text.text == fragment.getString(R.string.connected) }
    }

    @Test
    fun `restart button restarts device when clicked`() {
        // Arrange
        val fragment = launch()

        // Act
        fragment.button(R.id.do_restart_button).performClick()

        // Assert
        verifyEventually { api.doRestart(deviceId) }
    }

    @Test
    fun `factory reset button resets device when confirmed`() {
        // Arrange
        val fragment = launch()

        // Act
        fragment.button(R.id.do_factory_reset_button).performClick()
        confirmDialog()

        // Assert
        verifyEventually { api.doFactoryReset(deviceId, false) }
    }

    @Test
    fun `factory reset button does not reset device when cancelled`() {
        // Arrange
        val fragment = launch()

        // Act
        fragment.button(R.id.do_factory_reset_button).performClick()
        (ShadowAlertDialog.getLatestAlertDialog() as AlertDialog).getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        // Run a later command on the same IO path. When it is done, a wrongly started reset would have run too.
        fragment.button(R.id.do_restart_button).performClick()
        verifyEventually { api.doRestart(deviceId) }

        // Assert
        coVerify(exactly = 0) { api.doFactoryReset(any(), any()) }
    }

    @Test
    fun `power buttons send commands when clicked`() {
        // Arrange
        val fragment = launch()

        // Act
        fragment.button(R.id.set_warehouse_sleep_button).performClick()
        fragment.button(R.id.set_hibernate_mode_button).performClick()

        // Assert
        verifyEventually { api.setWarehouseSleep(deviceId) }
        verifyEventually { api.setHibernateMode(deviceId) }
    }

    @Test
    fun `delete telemetry button deletes data when confirmed`() {
        // Arrange
        val fragment = launch()

        // Act
        fragment.button(R.id.do_telemetry_delete_button).performClick()
        confirmDialog()

        // Assert
        verifyEventually { api.deleteTelemetryData(deviceId) }
    }

    @Test
    fun `ble signal button reads rssi when clicked`() {
        // Arrange
        coEvery { api.getRSSIValue(deviceId) } returns -60
        val fragment = launch()

        // Act
        fragment.button(R.id.ble_signal_strength_button).performClick()

        // Assert
        verifyEventually { api.getRSSIValue(deviceId) }
    }

    @Test
    fun `physical config button opens activity when clicked`() {
        // Arrange
        val fragment = launch()

        // Act
        fragment.button(R.id.do_physical_config_button).performClick()

        // Assert
        val intent = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertEquals(deviceId, intent.getStringExtra(ONLINE_OFFLINE_KEY_DEVICE_ID))
    }

    @Test
    fun `generic button opens activity after ten clicks`() {
        // Arrange
        val fragment = launch()
        val application = shadowOf(ApplicationProvider.getApplicationContext<Application>())

        // Act
        repeat(9) { fragment.button(R.id.generic_api_button).performClick() }
        val before = application.nextStartedActivity
        fragment.button(R.id.generic_api_button).performClick()

        // Assert
        assertNull(before)
        assertNotNull(application.nextStartedActivity)
    }

    @Test
    fun `view hides telemetry group when telemetry is not available`() {
        // Act
        val fragment = launch()

        // Assert
        assertEquals(View.GONE, fragment.requireView().findViewById<View>(R.id.telemetry_group).visibility)
    }

    private fun invoke(fragment: DeviceSettingsFragment, name: String, arg: Any) {
        val method = DeviceSettingsFragment::class.java.declaredMethods.first { it.name == name && it.parameterCount == 1 }
        method.isAccessible = true
        method.invoke(fragment, arg)
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test
    fun `sdk mode state change shows disable text when sdk mode is enabled`() {
        // Arrange
        val fragment = launch()

        // Act
        invoke(fragment, "sdkModeStateChange", SdkModeUiState(isAvailable = true, sdkModeState = SdkMode.STATE.ENABLED, sdkModeLedState = SdkMode.STATE.DISABLED, ppiModeLedState = SdkMode.STATE.DISABLED))

        // Assert
        val toggle = fragment.button(R.id.sdk_mode_toggle_button)
        assertEquals(fragment.getString(R.string.sdk_mode_disable), toggle.text.toString())
        assertEquals(true, toggle.isEnabled)
        assertEquals(fragment.getString(R.string.sdk_mode_led_enable), fragment.button(R.id.sdk_mode_toggle_led_button).text.toString())
        assertEquals(fragment.getString(R.string.ppi_mode_led_enable), fragment.button(R.id.ppi_mode_toggle_led_button).text.toString())
    }

    @Test
    fun `sdk mode state change shows enable text when sdk mode is disabled`() {
        // Arrange
        val fragment = launch()

        // Act
        invoke(fragment, "sdkModeStateChange", SdkModeUiState(isAvailable = true, sdkModeState = SdkMode.STATE.DISABLED))

        // Assert
        assertEquals(fragment.getString(R.string.sdk_mode_enable), fragment.button(R.id.sdk_mode_toggle_button).text.toString())
        assertEquals(fragment.getString(R.string.sdk_mode_led_disable), fragment.button(R.id.sdk_mode_toggle_led_button).text.toString())
        assertEquals(fragment.getString(R.string.ppi_mode_led_disable), fragment.button(R.id.ppi_mode_toggle_led_button).text.toString())
    }

    @Test
    fun `sdk mode state change disables toggle when state change is in progress`() {
        // Arrange
        val fragment = launch()

        // Act
        invoke(fragment, "sdkModeStateChange", SdkModeUiState(isAvailable = true, sdkModeState = SdkMode.STATE.STATE_CHANGE_IN_PROGRESS))

        // Assert
        val toggle = fragment.button(R.id.sdk_mode_toggle_button)
        assertEquals("Wait...", toggle.text.toString())
        assertEquals(false, toggle.isEnabled)
    }

    @Test
    fun `sdk mode state change disables toggle when switch is not enabled`() {
        // Arrange
        val fragment = launch()

        // Act
        invoke(fragment, "sdkModeStateChange", SdkModeUiState(isAvailable = true, isSwitchEnabled = false, isLoading = true))

        // Assert
        assertEquals(false, fragment.button(R.id.sdk_mode_toggle_button).isEnabled)
    }

    @Test
    fun `sdk mode state change hides buttons when sdk mode is not available`() {
        // Arrange
        val fragment = launch()

        // Act
        invoke(fragment, "sdkModeStateChange", SdkModeUiState(isAvailable = false))

        // Assert
        assertEquals(View.GONE, fragment.button(R.id.sdk_mode_toggle_button).visibility)
        assertEquals(View.GONE, fragment.button(R.id.sdk_mode_toggle_led_button).visibility)
        assertEquals(View.GONE, fragment.button(R.id.ppi_mode_toggle_led_button).visibility)
    }

    @Test
    fun `show disk space dialog shows free and total space when called`() {
        // Arrange
        val fragment = launch()
        val data = mockk<PolarDiskSpaceData>()
        every { data.freeSpace } returns 1024L
        every { data.totalSpace } returns 4096L

        // Act
        invoke(fragment, "showDiskSpaceDialog", data)

        // Assert
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        assertNotNull(dialog)
        assertEquals(true, dialog.isShowing)
    }

    @Test
    fun `show user physical info dialog shows dialog when called`() {
        // Arrange
        val fragment = launch()
        val info = mockk<PolarPhysicalConfiguration>(relaxed = true)
        every { info.sleepGoalMinutes } returns 480

        // Act
        invoke(fragment, "showUserPhysicalInfoDialog", info)

        // Assert
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        assertNotNull(dialog)
        assertEquals(true, dialog.isShowing)
    }

    @Test
    fun `security state change shows disable text when encryption is enabled`() {
        // Arrange
        val fragment = launch()

        // Act
        invoke(fragment, "securityStateChange", SecurityUiState(isAvailable = true, isEnabled = true))

        // Assert
        val button = fragment.button(R.id.offline_rec_security_settings_enabled)
        assertEquals(fragment.getString(R.string.offline_recording_disable_encryption), button.text.toString())
        assertEquals(true, button.isEnabled)
    }

    @Test
    fun `security state change shows enable text when encryption is disabled`() {
        // Arrange
        val fragment = launch()

        // Act
        invoke(fragment, "securityStateChange", SecurityUiState(isAvailable = true, isEnabled = false, isLoading = true))

        // Assert
        val button = fragment.button(R.id.offline_rec_security_settings_enabled)
        assertEquals(fragment.getString(R.string.offline_recording_enable_encryption), button.text.toString())
        assertEquals(false, button.isEnabled)
    }

    @Test
    fun `telemetry state change shows group when telemetry is available`() {
        // Arrange
        val fragment = launch()

        // Act
        invoke(fragment, "telemetryStateChange", TelemetryUiState(isEnabled = false, isAvailable = true))

        // Assert
        assertEquals(View.VISIBLE, fragment.requireView().findViewById<View>(R.id.telemetry_group).visibility)
    }

    @Test
    fun `ble multi connection state change shows disable text when enabled`() {
        // Arrange
        val fragment = launch()

        // Act
        invoke(fragment, "bleMultiConnectionStateChange", BleMultiConnectionUiState(isEnabled = true))

        // Assert
        val button = fragment.button(R.id.multi_ble_settings_button)
        assertEquals(fragment.getString(R.string.ble_multi_connection_disable), button.text.toString())
        assertEquals(true, button.isEnabled)
    }

    @Test
    fun `ble multi connection state change disables button when switch is not enabled`() {
        // Arrange
        val fragment = launch()

        // Act
        invoke(fragment, "bleMultiConnectionStateChange", BleMultiConnectionUiState(isSwitchEnabled = false))

        // Assert
        assertEquals(false, fragment.button(R.id.multi_ble_settings_button).isEnabled)
    }

    @Test
    fun `sensor initiated security mode state change shows disable text when enabled`() {
        // Arrange
        val fragment = launch()

        // Act
        invoke(fragment, "sensorInitiatedSecurityModeStateChange", SensorInitiatedSecurityModeUiState(isEnabled = true))

        // Assert
        val button = fragment.button(R.id.sensor_initiated_security_mode_enable_button)
        assertEquals(fragment.getString(R.string.sensor_initiated_security_mode_disable), button.text.toString())
    }

    @Test
    fun `sensor initiated security mode state change disables button when switch is not enabled`() {
        // Arrange
        val fragment = launch()

        // Act
        invoke(fragment, "sensorInitiatedSecurityModeStateChange", SensorInitiatedSecurityModeUiState(isSwitchEnabled = false))

        // Assert
        assertEquals(false, fragment.button(R.id.sensor_initiated_security_mode_enable_button).isEnabled)
    }

    @Test
    fun `update sleep recording state ui enables force stop when recording is on`() {
        // Arrange
        val fragment = launch()

        // Act
        invoke(fragment, "updateSleepRecordingStateUi", SleepRecordingState(PolarSleepRecordingStatus.ENABLED))

        // Assert
        assertEquals(true, fragment.button(R.id.force_stop_sleep_button).isEnabled)
    }

    @Test
    fun `update sleep recording state ui disables force stop when recording is unknown or missing`() {
        // Arrange
        val fragment = launch()

        // Act
        invoke(fragment, "updateSleepRecordingStateUi", SleepRecordingState(PolarSleepRecordingStatus.DISABLED))
        invoke(fragment, "updateSleepRecordingStateUi", SleepRecordingState(PolarSleepRecordingStatus.UNKNOWN))
        invoke(fragment, "updateSleepRecordingStateUi", SleepRecordingState(null, isLoading = true))

        // Assert
        assertEquals(false, fragment.button(R.id.force_stop_sleep_button).isEnabled)
    }

    @Test
    fun `time ui states update buttons when states change`() {
        // Arrange
        val fragment = launch()

        // Act
        invoke(fragment, "writeTimeUiState", StatusWriteTime.InProgress)
        invoke(fragment, "readTimeUiState", StatusReadTime.InProgress)

        // Assert
        assertEquals(false, fragment.button(R.id.time_settings_write_button).isEnabled)
        assertEquals(false, fragment.button(R.id.time_settings_read_button).isEnabled)

        // Act
        invoke(fragment, "writeTimeUiState", StatusWriteTime.Completed)
        invoke(fragment, "readTimeUiState", StatusReadTime.Completed)

        // Assert
        assertEquals(true, fragment.button(R.id.time_settings_write_button).isEnabled)
        assertEquals(true, fragment.button(R.id.time_settings_read_button).isEnabled)
    }
}
