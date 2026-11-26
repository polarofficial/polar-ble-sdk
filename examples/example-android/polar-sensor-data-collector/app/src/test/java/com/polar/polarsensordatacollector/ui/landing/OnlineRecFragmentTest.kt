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
import androidx.lifecycle.ViewModelProvider
import com.polar.androidcommunications.api.ble.model.gatt.client.ChargeState
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.model.Device
import com.polar.polarsensordatacollector.testutil.HiltTestSupport
import com.polar.polarsensordatacollector.ui.graph.HrGraphFragment
import com.polar.sdk.api.PolarBleApi.PolarBleSdkFeature
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.PolarBleApiCallbackProvider
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.impl.BDBleApiImpl
import io.mockk.coEvery
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
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
class OnlineRecFragmentTest {

    class ParentFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
            FrameLayout(requireContext()).apply { id = CONTAINER_ID }
    }

    private val deviceId = "dev1"
    private val callback = slot<PolarBleApiCallbackProvider>()
    private lateinit var api: BDBleApiImpl
    private lateinit var parent: ParentFragment

    @Before
    fun setUp() {
        api = HiltTestSupport.install()
        every { api.setApiCallback(capture(callback)) } returns Unit
        every { api.getDeviceName(deviceId) } returns "Polar H10 $deviceId"
    }

    @After
    fun tearDown() {
        HiltTestSupport.uninstall()
    }

    private fun launch(selectDevice: Boolean = true, vararg types: PolarDeviceDataType): OnlineRecFragment {
        parent = ParentFragment()
        val activity = HiltTestSupport.host(parent)
        if (selectDevice) {
            ViewModelProvider(activity)[MainViewModel::class.java].selectedDevice = Device(deviceId, deviceId, "Polar H10 $deviceId")
        }
        val child = OnlineRecFragment()
        child.arguments = Bundle().apply { putString(ONLINE_OFFLINE_KEY_DEVICE_ID, deviceId) }
        parent.childFragmentManager.beginTransaction().add(CONTAINER_ID, child).commitNow()
        HiltTestSupport.awaitUntil { child.view != null }
        coEvery { api.getAvailableOnlineStreamDataTypes(deviceId) } returns types.toSet()
        callback.captured.bleSdkFeaturesReadiness(
            deviceId,
            listOf(PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING),
            emptyList()
        )
        return child
    }

    private fun View.hrSection(): View = findViewById(R.id.hr_online_recording_setup)

    private fun View.hrCheckBox(): CheckBox = hrSection().findViewById(R.id.online_recording_controls_select_check_box)

    private fun View.startButton(): Button = findViewById(R.id.recording_button)

    private fun awaitHrVisible(fragment: OnlineRecFragment) {
        HiltTestSupport.awaitUntil { fragment.requireView().hrSection().visibility == View.VISIBLE }
    }

    private fun hrData(hr: Int) = PolarHrData(
        listOf(PolarHrData.PolarHrSample(hr, hr, 0, listOf(800), listOf(780), true, true, true))
    )

    @Test
    fun `view shows stream controls when online types are available`() {
        // Act
        val fragment = launch(true, PolarDeviceDataType.HR, PolarDeviceDataType.ACC)

        // Assert
        awaitHrVisible(fragment)
        val root = fragment.requireView()
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.acc_online_recording_setup).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.ecg_online_recording_setup).visibility)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.stream_recording_start_group).visibility)
        assertEquals(
            View.GONE,
            root.hrSection().findViewById<Button>(R.id.online_recording_controls_settings_button).visibility
        )
    }

    @Test
    fun `view hides stream controls when no type is available`() {
        // Act
        val fragment = launch(true)

        // Assert
        HiltTestSupport.awaitUntil { fragment.requireView().findViewById<View>(R.id.stream_recording_start_group).visibility == View.GONE }
        assertEquals(View.GONE, fragment.requireView().findViewById<View>(R.id.stream_settings_header).visibility)
    }

    @Test
    fun `start button shows toast when no stream is selected`() {
        // Arrange
        val fragment = launch(true, PolarDeviceDataType.HR)
        awaitHrVisible(fragment)

        // Act
        fragment.requireView().startButton().performClick()

        // Assert
        assertEquals(fragment.getString(R.string.no_data_stream_selected), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `start button shows toast when no device is selected`() {
        // Arrange
        val fragment = launch(false, PolarDeviceDataType.HR)
        awaitHrVisible(fragment)

        // Act
        fragment.requireView().startButton().performClick()

        // Assert
        assertEquals(fragment.getString(R.string.no_device_selected), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `start button starts hr stream when hr is checked`() {
        // Arrange
        every { api.startHrStreaming(deviceId) } returns flowOf(hrData(72))
        val fragment = launch(true, PolarDeviceDataType.HR)
        awaitHrVisible(fragment)
        fragment.requireView().hrCheckBox().isChecked = true

        // Act
        fragment.requireView().startButton().performClick()

        // Assert
        HiltTestSupport.awaitUntil { runCatching { verify { api.startHrStreaming(deviceId) } }.isSuccess }
    }

    @Test
    fun `hr section shows live data when hr sample is received`() {
        // Arrange
        val samples = MutableSharedFlow<PolarHrData>(replay = 1)
        samples.tryEmit(hrData(72))
        every { api.startHrStreaming(deviceId) } returns samples
        val fragment = launch(true, PolarDeviceDataType.HR)
        awaitHrVisible(fragment)
        fragment.requireView().hrCheckBox().isChecked = true

        // Act
        fragment.requireView().startButton().performClick()

        // Assert
        val live = fragment.requireView().findViewById<View>(R.id.hr_data_section)
        HiltTestSupport.awaitUntil { live.findViewById<TextView>(R.id.data_0).text == "72" }
        assertEquals("true", live.findViewById<TextView>(R.id.data_1).text)
        assertTrue(live.findViewById<TextView>(R.id.second_data).text.contains("RR: 800 | RR ms: 780"))
        assertEquals(View.VISIBLE, live.findViewById<Button>(R.id.open_hr_graph_button).visibility)
    }

    @Test
    fun `start button shows stop when stream is recording`() {
        // Arrange
        every { api.startHrStreaming(deviceId) } returns MutableSharedFlow()
        val fragment = launch(true, PolarDeviceDataType.HR)
        awaitHrVisible(fragment)
        fragment.requireView().hrCheckBox().isChecked = true

        // Act
        fragment.requireView().startButton().performClick()

        // Assert
        HiltTestSupport.awaitUntil {
            fragment.requireView().startButton().text == fragment.getString(R.string.stop_streaming)
        }
        assertTrue(fragment.requireView().findViewById<Button>(R.id.marker_button).isEnabled)
    }

    @Test
    fun `charging during HR recording shows and acknowledges HR warning`() {
        // Arrange
        every { api.startHrStreaming(deviceId) } returns MutableSharedFlow()
        val fragment = launch(true, PolarDeviceDataType.HR)
        awaitHrVisible(fragment)
        fragment.requireView().hrCheckBox().isChecked = true
        fragment.requireView().startButton().performClick()
        HiltTestSupport.awaitUntil {
            fragment.requireView().startButton().text == fragment.getString(R.string.stop_streaming)
        }

        // Act
        callback.captured.batteryChargingStatusReceived(deviceId, ChargeState.CHARGING)

        // Assert
        HiltTestSupport.awaitUntil {
            ShadowToast.getTextOfLatestToast() == fragment.getString(R.string.online_hr_charger_warning)
        }
        assertEquals("Device connected to charger. HR streaming may be interrupted.", ShadowToast.getTextOfLatestToast())
        val onlineViewModel = ViewModelProvider(parent)[
            "${OnlineRecordingViewModel::class.java.name}:$deviceId", OnlineRecordingViewModel::class.java
        ]
        assertFalse(onlineViewModel.uiHrChargingWarning.value)
    }

    @Test
    fun `stop button stops stream when stream is recording`() {
        // Arrange
        every { api.startHrStreaming(deviceId) } returns MutableSharedFlow()
        val fragment = launch(true, PolarDeviceDataType.HR)
        awaitHrVisible(fragment)
        fragment.requireView().hrCheckBox().isChecked = true
        fragment.requireView().startButton().performClick()
        HiltTestSupport.awaitUntil {
            fragment.requireView().startButton().text == fragment.getString(R.string.stop_streaming)
        }

        // Act
        fragment.requireView().startButton().performClick()

        // Assert
        HiltTestSupport.awaitUntil {
            fragment.requireView().startButton().text == fragment.getString(R.string.start_streaming)
        }
        assertFalse(fragment.requireView().hrCheckBox().isChecked)
    }

    @Test
    fun `graph button opens hr graph when clicked`() {
        // Arrange
        val fragment = launch(true, PolarDeviceDataType.HR)
        awaitHrVisible(fragment)

        // Act
        fragment.requireView().findViewById<View>(R.id.hr_data_section)
            .findViewById<Button>(R.id.open_hr_graph_button).performClick()
        parent.childFragmentManager.executePendingTransactions()

        // Assert
        assertTrue(parent.childFragmentManager.fragments.any { it is HrGraphFragment })
    }

    private companion object {
        const val CONTAINER_ID = 4242
    }
}
