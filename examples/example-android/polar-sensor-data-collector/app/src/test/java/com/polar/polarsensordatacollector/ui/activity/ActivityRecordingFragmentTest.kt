package com.polar.polarsensordatacollector.ui.activity

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.testutil.HiltTestSupport
import com.polar.polarsensordatacollector.ui.landing.ONLINE_OFFLINE_KEY_DEVICE_ID
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
class ActivityRecordingFragmentTest {

    @Before
    fun setUp() {
        HiltTestSupport.install()
    }

    @After
    fun tearDown() {
        HiltTestSupport.uninstall()
    }

    private fun launch(): ActivityRecordingFragment {
        val fragment = ActivityRecordingFragment()
        HiltTestSupport.host(fragment, Bundle().apply { putString(ONLINE_OFFLINE_KEY_DEVICE_ID, "dev1") })
        HiltTestSupport.awaitUntil { fragment.view != null }
        return fragment
    }

    private fun ActivityRecordingFragment.pickerShown() =
        childFragmentManager.findFragmentByTag("DATE_RANGE_PICKER") != null

    @Test
    fun `data buttons show date range picker when clicked`() {
        // Arrange
        val ids = listOf(
            R.id.activity_sleep_button,
            R.id.activity_steps_button,
            R.id.hr_samples_button,
            R.id.nightly_recharge_button,
            R.id.ppi_samples_recording_button,
            R.id.activity_skin_temperature_recording_button,
            R.id.active_time_button,
            R.id.button_get_training_sessions,
            R.id.button_get_activity_samples,
            R.id.button_get_daily_summary,
            R.id.button_get_spo2_test
        )

        ids.forEach { id ->
            // Act
            val fragment = launch()
            assertNull(fragment.childFragmentManager.findFragmentByTag("DATE_RANGE_PICKER"))
            fragment.requireView().findViewById<Button>(id).performClick()
            shadowOf(android.os.Looper.getMainLooper()).idle()

            // Assert
            assertNotNull("Picker missing for button $id", fragment.childFragmentManager.findFragmentByTag("DATE_RANGE_PICKER"))
            HiltTestSupport.uninstall()
            HiltTestSupport.install()
        }
    }

    @Test
    fun `calories button shows type dialog then picker when item is selected`() {
        // Arrange
        val fragment = launch()

        // Act
        fragment.requireView().findViewById<Button>(R.id.activity_calories_button).performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        shadowOf(dialog).clickOnItem(0)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        // Assert
        assertNotNull(fragment.childFragmentManager.findFragmentByTag("DATE_RANGE_PICKER"))
    }

    @Test
    fun `calories button does not show picker when dialog is cancelled`() {
        // Arrange
        val fragment = launch()

        // Act
        fragment.requireView().findViewById<Button>(R.id.activity_calories_button).performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        // Assert
        assertNull(fragment.childFragmentManager.findFragmentByTag("DATE_RANGE_PICKER"))
    }
}
