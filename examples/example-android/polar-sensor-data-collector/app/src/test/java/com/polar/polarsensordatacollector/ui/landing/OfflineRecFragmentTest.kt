package com.polar.polarsensordatacollector.ui.landing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.Navigation
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.testutil.HiltTestSupport
import com.polar.sdk.api.PolarBleApi.PolarBleSdkFeature
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.PolarBleApiCallbackProvider
import com.polar.sdk.impl.BDBleApiImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class OfflineRecFragmentTest {

    class ParentFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
            FrameLayout(requireContext()).apply { id = CONTAINER_ID }
    }

    private val deviceId = "dev1"
    private val callback = slot<PolarBleApiCallbackProvider>()
    private val navController = mockk<NavController>(relaxed = true)
    private lateinit var api: BDBleApiImpl

    @Before
    fun setUp() {
        api = HiltTestSupport.install()
        every { api.setApiCallback(capture(callback)) } returns Unit
        coEvery { api.getOfflineRecordingStatus(deviceId) } returns emptyList()
    }

    @After
    fun tearDown() {
        HiltTestSupport.uninstall()
    }

    private fun launch(vararg types: PolarDeviceDataType): OfflineRecFragment {
        val parent = ParentFragment()
        HiltTestSupport.host(parent)
        val child = OfflineRecFragment()
        child.arguments = Bundle().apply { putString(ONLINE_OFFLINE_KEY_DEVICE_ID, deviceId) }
        parent.childFragmentManager.beginTransaction().add(CONTAINER_ID, child).commitNow()
        HiltTestSupport.awaitUntil { child.view != null }
        Navigation.setViewNavController(child.requireView(), navController)
        coEvery { api.getAvailableOfflineRecordingDataTypes(deviceId) } returns types.toSet()
        callback.captured.bleSdkFeaturesReadiness(
            deviceId,
            listOf(PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING),
            emptyList()
        )
        return child
    }

    private fun View.section(id: Int): View = findViewById(id)

    private fun View.startStop(sectionId: Int): Button =
        section(sectionId).findViewById(R.id.offline_recording_controls_start_stop_button)

    private fun View.startSelected(): Button = findViewById(R.id.offline_recording_button)

    private fun awaitEnabled(fragment: OfflineRecFragment) {
        HiltTestSupport.awaitUntil {
            fragment.requireView().startSelected().text == fragment.getString(R.string.start_selected)
        }
    }

    @Test
    fun `sections show only available types when offline types are received`() {
        // Act
        val fragment = launch(PolarDeviceDataType.HR, PolarDeviceDataType.ACC)

        // Assert
        val root = fragment.requireView()
        HiltTestSupport.awaitUntil { root.section(R.id.hr_status_and_settings).visibility == View.VISIBLE }
        assertEquals(View.VISIBLE, root.section(R.id.acc_status_and_settings).visibility)
        assertEquals(View.GONE, root.section(R.id.ecg_status_and_settings).visibility)
        assertEquals("HR", root.section(R.id.hr_status_and_settings).findViewById<TextView>(R.id.offline_recording_controls_header).text)
        assertEquals(
            View.GONE,
            root.section(R.id.hr_status_and_settings).findViewById<Button>(R.id.offline_recording_controls_settings_button).visibility
        )
    }

    @Test
    fun `start selected button is disabled when status is fetching`() {
        // Act
        val fragment = launch()

        // Assert
        val button = fragment.requireView().startSelected()
        assertFalse(button.isEnabled)
    }

    @Test
    fun `start button starts recording when feature start is clicked`() {
        // Arrange
        val fragment = launch(PolarDeviceDataType.HR)
        awaitEnabled(fragment)

        // Act
        fragment.requireView().startStop(R.id.hr_status_and_settings).performClick()

        // Assert
        HiltTestSupport.awaitUntil {
            runCatching { coVerify { api.startOfflineRecording(deviceId, PolarDeviceDataType.HR, null, any()) } }.isSuccess
        }
    }

    @Test
    fun `start stop button shows stop when feature is recording`() {
        // Arrange
        coEvery { api.getOfflineRecordingStatus(deviceId) } returns listOf(PolarDeviceDataType.HR)

        // Act
        val fragment = launch(PolarDeviceDataType.HR)
        awaitEnabled(fragment)

        // Assert
        val root = fragment.requireView()
        assertEquals(fragment.getString(R.string.stop_uppercase), root.startStop(R.id.hr_status_and_settings).text)
        val checkBox = root.section(R.id.hr_status_and_settings).findViewById<CheckBox>(R.id.offline_recording_controls_select_check_box)
        assertTrue(checkBox.isChecked)
        assertFalse(checkBox.isEnabled)
    }

    @Test
    fun `stop button stops recording when feature is recording`() {
        // Arrange
        coEvery { api.getOfflineRecordingStatus(deviceId) } returns listOf(PolarDeviceDataType.HR)
        val fragment = launch(PolarDeviceDataType.HR)
        awaitEnabled(fragment)

        // Act
        fragment.requireView().startStop(R.id.hr_status_and_settings).performClick()

        // Assert
        HiltTestSupport.awaitUntil {
            runCatching { coVerify { api.stopOfflineRecording(deviceId, PolarDeviceDataType.HR) } }.isSuccess
        }
    }

    @Test
    fun `start selected button shows toast when nothing is selected`() {
        // Arrange
        val fragment = launch(PolarDeviceDataType.HR)
        awaitEnabled(fragment)

        // Act
        fragment.requireView().startSelected().performClick()

        // Assert
        assertEquals(fragment.getString(R.string.none_selected), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `start selected button starts checked features when clicked`() {
        // Arrange
        val fragment = launch(PolarDeviceDataType.HR, PolarDeviceDataType.PPI)
        awaitEnabled(fragment)
        val root = fragment.requireView()
        root.section(R.id.hr_status_and_settings)
            .findViewById<CheckBox>(R.id.offline_recording_controls_select_check_box).isChecked = true

        // Act
        root.startSelected().performClick()

        // Assert
        HiltTestSupport.awaitUntil {
            runCatching { coVerify { api.startOfflineRecording(deviceId, PolarDeviceDataType.HR, null, any()) } }.isSuccess
        }
        coVerify(exactly = 0) { api.startOfflineRecording(deviceId, PolarDeviceDataType.PPI, any(), any()) }
    }

    @Test
    fun `start selected button does not start derived measurement when only acc is selected`() {
        // Arrange
        val fragment = launch(PolarDeviceDataType.ACC)
        awaitEnabled(fragment)
        val root = fragment.requireView()
        root.section(R.id.acc_status_and_settings)
            .findViewById<CheckBox>(R.id.offline_recording_controls_select_check_box).isChecked = true

        // Act
        root.startSelected().performClick()

        // Assert
        coVerify(timeout = 5_000) { api.startOfflineRecording(deviceId, PolarDeviceDataType.ACC, any(), any()) }
        coVerify(exactly = 0) { api.startOfflineRecording(deviceId, PolarDeviceDataType.DERIVED_MEASUREMENT, any(), any()) }
    }

    @Test
    fun `navigation buttons navigate when clicked`() {
        // Arrange
        val fragment = launch(PolarDeviceDataType.HR)
        val root = fragment.requireView()

        // Act
        root.findViewById<Button>(R.id.get_offline_recordings_button).performClick()
        root.findViewById<Button>(R.id.set_offline_triggers_button).performClick()

        // Assert
        verify(exactly = 2) { navController.navigate(any<NavDirections>()) }
    }

    private companion object {
        const val CONTAINER_ID = 4242
    }
}
