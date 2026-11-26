package com.polar.polarsensordatacollector.ui.landing

import android.view.View
import android.widget.Button
import android.widget.TextView
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.testutil.HiltTestSupport
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallbackProvider
import com.polar.sdk.api.model.PolarDeviceInfo
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
class MainFragmentTest {

    private val deviceId = "AB12CD34"
    private val deviceInfo = PolarDeviceInfo(deviceId, "00:11:22:33:44:55", -50, "Polar Verity $deviceId", true)
    private val callback = slot<PolarBleApiCallbackProvider>()
    private lateinit var api: com.polar.sdk.impl.BDBleApiImpl

    @Before
    fun setUp() {
        api = HiltTestSupport.install()
        every { api.setApiCallback(capture(callback)) } returns Unit
        every { api.searchForDevice(any()) } returns emptyFlow()
    }

    @After
    fun tearDown() {
        HiltTestSupport.uninstall()
    }

    private fun launch(): MainFragment {
        val fragment = MainFragment()
        HiltTestSupport.host(fragment)
        HiltTestSupport.awaitUntil { fragment.view != null }
        callback.captured.blePowerStateChanged(true)
        return fragment
    }

    private fun MainFragment.text(id: Int) = requireView().findViewById<TextView>(id)

    private fun MainFragment.button(id: Int) = requireView().findViewById<Button>(id)

    private fun connect() {
        callback.captured.deviceConnected(deviceInfo)
        callback.captured.bleSdkFeaturesReadiness(
            deviceId,
            listOf(PolarBleApi.PolarBleSdkFeature.FEATURE_HR),
            emptyList()
        )
    }

    @Test
    fun `view shows search button when no device is connected`() {
        // Act
        val fragment = launch()

        // Assert
        HiltTestSupport.awaitUntil {
            fragment.button(R.id.search_connect_button).text == fragment.getString(R.string.search_and_connect_search)
        }
        assertEquals(View.GONE, fragment.button(R.id.disconnect_button).visibility)
        assertEquals(View.GONE, fragment.button(R.id.disconnect_all_button).visibility)
    }

    @Test
    fun `view shows ble off banner when phone bluetooth is off`() {
        // Arrange
        val fragment = launch()

        // Act
        callback.captured.blePowerStateChanged(false)

        // Assert
        HiltTestSupport.awaitUntil { fragment.text(R.id.phone_bluetooth_status).visibility == View.VISIBLE }
        assertEquals(View.GONE, fragment.button(R.id.search_connect_button).visibility)
        assertFalse(fragment.button(R.id.search_connect_button).isEnabled)
    }

    @Test
    fun `view shows connecting when device is connecting`() {
        // Arrange
        val fragment = launch()

        // Act
        callback.captured.deviceConnecting(deviceInfo)

        // Assert
        HiltTestSupport.awaitUntil {
            fragment.button(R.id.search_connect_button).text == fragment.getString(R.string.search_and_connect_connecting)
        }
        assertFalse(fragment.button(R.id.search_connect_button).isEnabled)
        assertEquals(View.VISIBLE, fragment.button(R.id.disconnect_button).visibility)
    }

    @Test
    fun `view shows connected when device is ready`() {
        // Arrange
        val fragment = launch()

        // Act
        connect()

        // Assert
        HiltTestSupport.awaitUntil {
            fragment.button(R.id.search_connect_button).text == fragment.getString(R.string.search_and_connect_connections)
        }
        assertTrue(fragment.button(R.id.search_connect_button).isEnabled)
        assertEquals(View.VISIBLE, fragment.button(R.id.disconnect_button).visibility)
        assertEquals(View.GONE, fragment.button(R.id.listen_hr_broadcasts_button).visibility)
        assertEquals(View.GONE, fragment.button(R.id.disconnect_all_button).visibility)
    }

    @Test
    fun `view shows battery level when battery level is received`() {
        // Arrange
        val fragment = launch()
        connect()
        HiltTestSupport.awaitUntil { fragment.button(R.id.disconnect_button).visibility == View.VISIBLE }

        // Act
        callback.captured.batteryLevelReceived(deviceId, 55)

        // Assert
        HiltTestSupport.awaitUntil { fragment.text(R.id.battery).text.toString() == "Battery status: 55%" }
    }

    @Test
    fun `disconnect button resets ui when clicked`() {
        // Arrange
        val fragment = launch()
        connect()
        HiltTestSupport.awaitUntil { fragment.button(R.id.disconnect_button).visibility == View.VISIBLE }

        // Act
        fragment.button(R.id.disconnect_button).performClick()

        // Assert
        assertEquals(View.GONE, fragment.button(R.id.disconnect_button).visibility)
        assertEquals(fragment.getString(R.string.search_and_connect_search), fragment.button(R.id.search_connect_button).text)
        assertEquals("", fragment.text(R.id.battery).text.toString())
    }

    @Test
    fun `connect button opens sensor selection when not connected`() {
        // Arrange
        val fragment = launch()
        HiltTestSupport.awaitUntil {
            fragment.button(R.id.search_connect_button).text == fragment.getString(R.string.search_and_connect_search)
        }

        // Act
        fragment.button(R.id.search_connect_button).performClick()

        // Assert
        assertNotNull(ShadowDialog.getLatestDialog())
        verify { api.searchForDevice(any()) }
    }

    @Test
    fun `connect button opens sensor selection when already connected`() {
        // Arrange
        val fragment = launch()
        connect()
        HiltTestSupport.awaitUntil { fragment.button(R.id.disconnect_button).visibility == View.VISIBLE }

        // Act
        fragment.button(R.id.search_connect_button).performClick()

        // Assert
        assertNotNull(ShadowDialog.getLatestDialog())
    }

    @Test
    fun `disconnect all button resets ui when clicked`() {
        // Arrange
        val fragment = launch()
        connect()
        HiltTestSupport.awaitUntil { fragment.button(R.id.disconnect_button).visibility == View.VISIBLE }

        // Act
        fragment.button(R.id.disconnect_all_button).performClick()

        // Assert
        assertEquals(View.GONE, fragment.button(R.id.disconnect_button).visibility)
        assertEquals(fragment.getString(R.string.search_and_connect_search), fragment.button(R.id.search_connect_button).text)
    }

    @Test
    fun `hr broadcast button shows dialog when clicked`() {
        // Arrange
        val fragment = launch()
        HiltTestSupport.awaitUntil { fragment.button(R.id.listen_hr_broadcasts_button).visibility == View.VISIBLE }

        // Act
        fragment.button(R.id.listen_hr_broadcasts_button).performClick()
        fragment.childFragmentManager.executePendingTransactions()

        // Assert
        assertNotNull(fragment.childFragmentManager.findFragmentByTag(HrBroadcastTag))
    }

    private companion object {
        const val HrBroadcastTag = "HrBroadcastDialogFragment"
    }
}
