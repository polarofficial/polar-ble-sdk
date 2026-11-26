package com.polar.polarsensordatacollector.ui.landing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.model.Device
import com.polar.polarsensordatacollector.testutil.HiltTestSupport
import com.polar.sdk.api.PolarBleApi.PolarBleSdkFeature
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.PolarBleApiCallbackProvider
import com.polar.sdk.api.model.EcgSample
import com.polar.sdk.api.model.FecgSample
import com.polar.sdk.api.model.GpsCoordinatesSample
import com.polar.sdk.api.model.PolarAccelerometerData
import com.polar.sdk.api.model.PolarEcgData
import com.polar.sdk.api.model.PolarGyroData
import com.polar.sdk.api.model.PolarLocationData
import com.polar.sdk.api.model.PolarMagnetometerData
import com.polar.sdk.api.model.PolarPpgData
import com.polar.sdk.api.model.PolarPpiData
import com.polar.sdk.api.model.PolarPressureData
import com.polar.sdk.api.model.PolarTemperatureData
import com.polar.sdk.impl.BDBleApiImpl
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OnlineRecFragmentLiveDataTest {

    class ParentFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
            FrameLayout(requireContext()).apply { id = CONTAINER_ID }
    }

    private val deviceId = "dev1"
    private val callback = slot<PolarBleApiCallbackProvider>()
    private lateinit var api: BDBleApiImpl
    private lateinit var fragment: OnlineRecFragment
    private lateinit var viewModel: OnlineRecordingViewModel

    @Before
    fun setUp() {
        api = HiltTestSupport.install()
        every { api.setApiCallback(capture(callback)) } returns Unit
        every { api.getDeviceName(deviceId) } returns "Polar H10 $deviceId"

        val parent = ParentFragment()
        val activity = HiltTestSupport.host(parent)
        ViewModelProvider(activity)[MainViewModel::class.java].selectedDevice = Device(deviceId, deviceId, "Polar H10 $deviceId")
        fragment = OnlineRecFragment()
        fragment.arguments = Bundle().apply { putString(ONLINE_OFFLINE_KEY_DEVICE_ID, deviceId) }
        parent.childFragmentManager.beginTransaction().add(CONTAINER_ID, fragment).commitNow()
        HiltTestSupport.awaitUntil { fragment.view != null }
        coEvery { api.getAvailableOnlineStreamDataTypes(deviceId) } returns PolarDeviceDataType.entries.toSet()
        callback.captured.bleSdkFeaturesReadiness(
            deviceId,
            listOf(PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING),
            emptyList()
        )
        viewModel = ViewModelProvider(parent.viewModelStore, fragment.defaultViewModelProviderFactory, fragment.defaultViewModelCreationExtras)[
            "${OnlineRecordingViewModel::class.java.name}:$deviceId", OnlineRecordingViewModel::class.java
        ]
    }

    @After
    fun tearDown() {
        HiltTestSupport.uninstall()
    }

    @Suppress("UNCHECKED_CAST")
    private fun push(flowName: String, state: Any) {
        val field = OnlineRecordingViewModel::class.java.getDeclaredField(flowName)
        field.isAccessible = true
        (field.get(viewModel) as MutableStateFlow<Any>).value = state
    }

    private fun text(section: Int, id: Int): String =
        fragment.requireView().findViewById<View>(section).findViewById<TextView>(id).text.toString()

    private fun awaitText(section: Int, id: Int, expected: String) {
        HiltTestSupport.awaitUntil { text(section, id) == expected }
    }

    @Test
    fun `acc section shows last sample when acc data arrives`() {
        // Arrange
        val sample = mockk<PolarAccelerometerData.PolarAccelerometerDataSample>(relaxed = true)
        every { sample.x } returns 1
        every { sample.y } returns 2
        every { sample.z } returns 3
        val data = mockk<PolarAccelerometerData>()
        every { data.samples } returns listOf(sample)

        // Act
        push("_uiAccStreamDataState", AccSampleDataUiState(deviceId, 52.0, data))

        // Assert
        awaitText(R.id.acc_data_section, R.id.data_2, "3")
        assertEquals("1", text(R.id.acc_data_section, R.id.data_0))
        assertEquals("2", text(R.id.acc_data_section, R.id.data_1))
        assertEquals("52.00", text(R.id.acc_data_section, R.id.meas_sampleRate))
    }

    @Test
    fun `gyro section shows last sample when gyro data arrives`() {
        // Arrange
        val sample = mockk<PolarGyroData.PolarGyroDataSample>(relaxed = true)
        every { sample.x } returns 1.5f
        val data = mockk<PolarGyroData>()
        every { data.samples } returns listOf(sample)

        // Act
        push("_uiGyroStreamDataState", GyroSampleDataUiState(deviceId, 52.0, data))

        // Assert
        awaitText(R.id.gyro_data_section, R.id.data_0, "1.5")
    }

    @Test
    fun `mag section shows last sample when mag data arrives`() {
        // Arrange
        val sample = mockk<PolarMagnetometerData.PolarMagnetometerDataSample>(relaxed = true)
        every { sample.x } returns 0.25f
        val data = mockk<PolarMagnetometerData>()
        every { data.samples } returns listOf(sample)

        // Act
        push("_uiMagnStreamDataState", MagnSampleDataUiState(deviceId, 50.0, data))

        // Assert
        awaitText(R.id.magnetometer_data_section, R.id.data_0, "0.250")
    }

    @Test
    fun `pressure section shows sample when pressure data arrives`() {
        // Arrange
        val sample = mockk<PolarPressureData.PolarPressureDataSample>(relaxed = true)
        every { sample.pressure } returns 1013.25f
        val data = mockk<PolarPressureData>()
        every { data.samples } returns listOf(sample)

        // Act
        push("_uiPressureStreamDataState", PressureSampleDataUiState(deviceId, 1.0, data))

        // Assert
        awaitText(R.id.pressure_data_section, R.id.data_0, "1013.25")
    }

    @Test
    fun `location section shows coordinates when location data arrives`() {
        // Arrange
        val sample = mockk<GpsCoordinatesSample>(relaxed = true)
        every { sample.latitude } returns 60.5
        every { sample.longitude } returns 24.25
        val data = mockk<PolarLocationData>()
        every { data.samples } returns listOf(sample)

        // Act
        push("_uiLocationStreamDataState", LocationSampleDataUiState(deviceId, 1.0, data))

        // Assert
        awaitText(R.id.loc_data_section, R.id.data_1, "60.5")
        assertEquals("24.25", text(R.id.loc_data_section, R.id.data_2))
    }

    @Test
    fun `temperature sections show temperature when temperature data arrives`() {
        // Arrange
        val sample = mockk<PolarTemperatureData.PolarTemperatureDataSample>(relaxed = true)
        every { sample.temperature } returns 36.6f
        val data = mockk<PolarTemperatureData>()
        every { data.samples } returns listOf(sample)

        // Act
        push("_uiTemperatureStreamDataState", TemperatureSampleDataUiState(deviceId, 1.0, data))
        push("_uiSkinTemperatureStreamDataState", SkinTemperatureSampleDataUiState(deviceId, 1.0, data))

        // Assert
        awaitText(R.id.temperature_data_section, R.id.data_0, "36.6")
        awaitText(R.id.skin_temperature_data_section, R.id.data_0, "36.6")
    }

    @Test
    fun `ecg section shows voltage when ecg sample arrives`() {
        // Arrange
        val sample = mockk<EcgSample>(relaxed = true)
        every { sample.voltage } returns 120
        val data = mockk<PolarEcgData>()
        every { data.samples } returns listOf(sample)

        // Act
        push("_uiEcgStreamDataState", EcgSampleDataUiState(deviceId, 130.0, data))

        // Assert
        awaitText(R.id.ecg_data_section, R.id.data_0, "120")
    }

    @Test
    fun `ecg section shows ecg bioz status when fecg sample arrives`() {
        // Arrange
        val sample = mockk<FecgSample>(relaxed = true)
        every { sample.ecg } returns 5
        every { sample.bioz } returns 6
        every { sample.status } returns 7.toUByte()
        val data = mockk<PolarEcgData>()
        every { data.samples } returns listOf(sample)

        // Act
        push("_uiEcgStreamDataState", EcgSampleDataUiState(deviceId, 130.0, data))

        // Assert
        awaitText(R.id.ecg_data_section, R.id.data_2, "7")
        assertEquals("5", text(R.id.ecg_data_section, R.id.data_0))
        assertEquals("6", text(R.id.ecg_data_section, R.id.data_1))
    }

    @Test
    fun `ppi section shows ppi when ppi data arrives`() {
        // Arrange
        val sample = mockk<PolarPpiData.PolarPpiSample>(relaxed = true)
        every { sample.ppi } returns 800
        every { sample.errorEstimate } returns 12
        // Act
        push("_uiPpiStreamDataState", PpiSampleDataUiState(deviceId, sample))

        // Assert
        awaitText(R.id.ppi_data_section, R.id.data_0, "800")
        assertEquals("12", text(R.id.ppi_data_section, R.id.data_1))
    }

    @Test
    fun `ppg section shows channels and headers for every ppg frame type`() {
        // Each frame type shows other channels. The channel values are unique for each frame type.
        PolarPpgData.PpgDataType.entries.forEach { type ->
            // Arrange
            val base = (type.ordinal + 1) * 1000
            val sample = mockk<PolarPpgData.PolarPpgSample>(relaxed = true)
            every { sample.channelSamples } returns List(25) { base + it }
            every { sample.statusBits } returns listOf(0, 1)
            val data = mockk<PolarPpgData>()
            every { data.type } returns type
            every { data.samples } returns listOf(sample)
            val expected: Pair<String, Int>? = when (type) {
                PolarPpgData.PpgDataType.PPG3_AMBIENT1 -> "ppg0:" to base
                PolarPpgData.PpgDataType.FRAME_TYPE_7 -> "ppg0:" to base
                PolarPpgData.PpgDataType.FRAME_TYPE_10 -> "red:" to base + 7
                PolarPpgData.PpgDataType.FRAME_TYPE_13 -> "PPG0:" to base
                PolarPpgData.PpgDataType.FRAME_TYPE_14 -> "NUMINT_TS1:" to base
                else -> null
            }

            // Act
            push("_uiPpgStreamDataState", PpgSampleDataUiState(deviceId, 50.0 + type.ordinal, data))

            // Assert
            if (expected != null) {
                awaitText(R.id.ppg_data_section, R.id.data_0, expected.second.toString())
                assertEquals(expected.first, text(R.id.ppg_data_section, R.id.data_0_header))
            } else {
                // Unknown frame types print no channel values. Wait for the sample rate of this update.
                awaitText(R.id.ppg_data_section, R.id.meas_sampleRate, "%.2f".format(50.0 + type.ordinal))
            }
        }
    }

    @Test
    fun `ppg section shows no live data when samples empty`() {
        // Arrange
        val data = mockk<PolarPpgData>()
        every { data.samples } returns emptyList()

        // Act
        push("_uiPpgStreamDataState", PpgSampleDataUiState(deviceId, 55.0, data))

        // Assert
        awaitText(R.id.ppg_data_section, R.id.data_0_header, "Ppg recording is ON - No live data")
    }

    private companion object {
        const val CONTAINER_ID = 4343
    }
}
