package com.polar.polarsensordatacollector.ui.utils

import android.app.Dialog
import android.os.Looper
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.polar.polarsensordatacollector.R
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarDerivedMeasurementMethod
import com.polar.sdk.api.model.PolarDerivedMeasurementSettingsGroup
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarSensorSetting.SettingType
import com.polar.polarsensordatacollector.ui.landing.SensorListAdapter
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
class DialogUtilityTest {

    private lateinit var activity: AppCompatActivity

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(AppCompatActivity::class.java).create().get()
        activity.setTheme(R.style.AppTheme)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun latestDialog(): Dialog = ShadowDialog.getLatestDialog()

    private fun settings(vararg pairs: Pair<SettingType, Set<Int>>) = mapOf(*pairs)

    private fun group(
        rates: Set<Int> = setOf(26, 50),
        windows: Set<Int> = setOf(1000),
        methods: Set<PolarDerivedMeasurementMethod> = setOf(PolarDerivedMeasurementMethod.DOWNSAMPLE, PolarDerivedMeasurementMethod.AVG)
    ) = PolarDerivedMeasurementSettingsGroup(1, setOf(PolarBleApi.PolarDeviceDataType.ACC), rates, windows, methods)

    private fun checkedTag(group: RadioGroup): Any? = group.findViewById<RadioButton>(group.checkedRadioButtonId)?.tag

    @Test
    fun `show all settings dialog returns first enabled values when nothing is selected`() {
        // Arrange
        val available = settings(SettingType.SAMPLE_RATE to setOf(52, 104), SettingType.RESOLUTION to setOf(16))
        val all = settings(SettingType.SAMPLE_RATE to setOf(26, 52, 104), SettingType.RESOLUTION to setOf(16))
        val observer = DialogUtility.showAllSettingsDialog(activity, available, all, null).test()
        idle()

        // Act
        latestDialog().findViewById<Button>(R.id.dialog_ok_button).performClick()
        idle()

        // Assert
        val result = observer.values().single()
        assertEquals(mapOf(SettingType.SAMPLE_RATE to 52, SettingType.RESOLUTION to 16), result.first)
        assertNull(result.second)
        assertFalse(result.third)
    }

    @Test
    fun `show all settings dialog marks unavailable values disabled`() {
        // Arrange
        val available = settings(SettingType.SAMPLE_RATE to setOf(52))
        val all = settings(SettingType.SAMPLE_RATE to setOf(26, 52))
        DialogUtility.showAllSettingsDialog(activity, available, all, null).test()
        idle()

        // Act
        val rg = latestDialog().findViewById<RadioGroup>(R.id.sampling_rate_group)

        // Assert
        assertEquals(2, rg.childCount)
        assertFalse(rg.getChildAt(0).isEnabled)
        assertTrue((rg.getChildAt(0) as RadioButton).text.toString().contains("SDK mode"))
        assertTrue(rg.getChildAt(1).isEnabled)
    }

    @Test
    fun `show all settings dialog keeps previous selection when value is available`() {
        // Arrange
        val values = settings(SettingType.SAMPLE_RATE to setOf(26, 52), SettingType.RANGE to setOf(2, 4))
        val observer = DialogUtility.showAllSettingsDialog(
            activity, values, values, mapOf(SettingType.SAMPLE_RATE to 52, SettingType.RANGE to 4)
        ).test()
        idle()

        // Act
        latestDialog().findViewById<Button>(R.id.dialog_ok_button).performClick()
        idle()

        // Assert
        assertEquals(mapOf(SettingType.SAMPLE_RATE to 52, SettingType.RANGE to 4), observer.values().single().first)
    }

    @Test
    fun `show all settings dialog falls back to first enabled when previous value is missing`() {
        // Arrange
        val values = settings(SettingType.CHANNELS to setOf(1, 3))
        val observer = DialogUtility.showAllSettingsDialog(activity, values, values, mapOf(SettingType.CHANNELS to 9)).test()
        idle()

        // Act
        latestDialog().findViewById<Button>(R.id.dialog_ok_button).performClick()
        idle()

        // Assert
        assertEquals(mapOf(SettingType.CHANNELS to 1), observer.values().single().first)
    }

    @Test
    fun `show all settings dialog uses available values only when all settings have no type`() {
        // Arrange
        val available = settings(SettingType.RESOLUTION to setOf(14, 16))
        val observer = DialogUtility.showAllSettingsDialog(activity, available, emptyMap(), null).test()
        idle()

        // Act
        latestDialog().findViewById<Button>(R.id.dialog_ok_button).performClick()
        idle()

        // Assert
        assertEquals(mapOf(SettingType.RESOLUTION to 14), observer.values().single().first)
    }

    @Test
    fun `show all settings dialog returns previous values when cancelled`() {
        // Arrange
        val values = settings(SettingType.SAMPLE_RATE to setOf(26, 52))
        val previous = DerivedDialogResult(setOf(PolarDerivedMeasurementMethod.MIN), 50, 1000)
        val observer = DialogUtility.showAllSettingsDialog(
            activity, values, values, mapOf(SettingType.SAMPLE_RATE to 52, SettingType.RANGE to null), group(), previous
        ).test()
        idle()

        // Act
        latestDialog().cancel()
        idle()

        // Assert
        val result = observer.values().single()
        assertEquals(mapOf(SettingType.SAMPLE_RATE to 52), result.first)
        assertEquals(previous, result.second)
        assertFalse(result.third)
    }

    @Test
    fun `show all settings dialog hides derived section when no group`() {
        // Arrange
        DialogUtility.showAllSettingsDialog(activity, emptyMap(), emptyMap(), null).test()
        idle()

        // Act
        val checkbox = latestDialog().findViewById<CheckBox>(R.id.derived_enable_checkbox)

        // Assert
        assertTrue(checkbox.visibility != android.view.View.VISIBLE)
    }

    @Test
    fun `show all settings dialog returns null derived result when derived is not enabled`() {
        // Arrange
        val observer = DialogUtility.showAllSettingsDialog(activity, emptyMap(), emptyMap(), null, group()).test()
        idle()

        // Act
        latestDialog().findViewById<Button>(R.id.dialog_ok_button).performClick()
        idle()

        // Assert
        val result = observer.values().single()
        assertNull(result.second)
        assertTrue(result.third)
    }

    @Test
    fun `show all settings dialog returns derived defaults when derived is enabled`() {
        // Arrange
        val observer = DialogUtility.showAllSettingsDialog(
            activity, emptyMap(), emptyMap(), null, group(rates = setOf(26, 50, 100), windows = setOf(500, 1000, 5000))
        ).test()
        idle()
        val dialog = latestDialog()
        val methods = dialog.findViewById<LinearLayout>(R.id.derived_methods_container)

        // Act
        dialog.findViewById<CheckBox>(R.id.derived_enable_checkbox).isChecked = true
        val subViewVisible = dialog.findViewById<RadioGroup>(R.id.derived_source_rate_group).visibility == android.view.View.VISIBLE
        dialog.findViewById<Button>(R.id.dialog_ok_button).performClick()
        idle()

        // Assert
        val derived = observer.values().single().second!!
        assertTrue(subViewVisible)
        assertEquals(2, methods.childCount)
        assertEquals(setOf(PolarDerivedMeasurementMethod.DOWNSAMPLE), derived.selectedMethods)
        assertEquals(50, derived.selectedSourceRate)
        assertEquals(1000, derived.selectedTimeWindowMs)
    }

    @Test
    fun `show all settings dialog restores previous derived settings`() {
        // Arrange
        val previous = DerivedDialogResult(setOf(PolarDerivedMeasurementMethod.AVG), 26, 5000)
        val observer = DialogUtility.showAllSettingsDialog(
            activity, emptyMap(), emptyMap(), null,
            group(rates = setOf(26, 100), windows = setOf(500, 1000, 5000)), previous
        ).test()
        idle()

        // Act
        latestDialog().findViewById<Button>(R.id.dialog_ok_button).performClick()
        idle()

        // Assert
        assertEquals(previous, observer.values().single().second)
    }

    @Test
    fun `show all settings dialog selects first method when previous methods are not supported`() {
        // Arrange
        val previous = DerivedDialogResult(setOf(PolarDerivedMeasurementMethod.STD), 50, 1000)
        val observer = DialogUtility.showAllSettingsDialog(
            activity, emptyMap(), emptyMap(), null, group(), previous
        ).test()
        idle()

        // Act
        latestDialog().findViewById<Button>(R.id.dialog_ok_button).performClick()
        idle()

        // Assert
        assertEquals(setOf(PolarDerivedMeasurementMethod.DOWNSAMPLE), observer.values().single().second!!.selectedMethods)
    }

    @Test
    fun `show all settings dialog removes derived output rates from sample rates`() {
        // Arrange
        val values = settings(SettingType.SAMPLE_RATE to setOf(1, 26, 52))
        DialogUtility.showAllSettingsDialog(activity, values, values, null, group(windows = setOf(1000))).test()
        idle()

        // Act
        val rg = latestDialog().findViewById<RadioGroup>(R.id.sampling_rate_group)

        // Assert
        assertEquals(listOf<Any?>(26, 52), (0 until rg.childCount).map { rg.getChildAt(it).tag })
    }

    @Test
    fun `show all settings dialog keeps all rates when filter would remove everything`() {
        // Arrange
        val values = settings(SettingType.SAMPLE_RATE to setOf(1))
        DialogUtility.showAllSettingsDialog(activity, values, values, null, group(windows = setOf(1000))).test()
        idle()

        // Act
        val rg = latestDialog().findViewById<RadioGroup>(R.id.sampling_rate_group)

        // Assert
        assertEquals(1, rg.childCount)
    }

    @Test
    fun `show all settings dialog describes time windows with output cadence`() {
        // Arrange
        DialogUtility.showAllSettingsDialog(
            activity, emptyMap(), emptyMap(), null,
            group(windows = setOf(500, 5_000, 120_000, 7_200_000, 1_000))
        ).test()
        idle()

        // Act
        val rg = latestDialog().findViewById<RadioGroup>(R.id.derived_time_window_group)
        val labels = (0 until rg.childCount).map { (rg.getChildAt(it) as RadioButton).text.toString() }

        // Assert
        assertEquals(
            listOf(
                "500 ms — 2 Hz output",
                "1 s — 1 Hz output",
                "5 s — 1 sample / 5 s",
                "2 min — 1 sample / 2 min",
                "2 h — 1 sample / 2 h"
            ),
            labels
        )
        assertEquals(1000, checkedTag(rg))
    }

    @Test
    fun `show all settings dialog checks first time window when no thousand millisecond option`() {
        // Arrange
        DialogUtility.showAllSettingsDialog(activity, emptyMap(), emptyMap(), null, group(windows = setOf(2_000, 4_000))).test()
        idle()

        // Act
        val rg = latestDialog().findViewById<RadioGroup>(R.id.derived_time_window_group)

        // Assert
        assertEquals(2_000, checkedTag(rg))
    }

    @Test
    fun `show all settings dialog shows name for every derived method`() {
        // Arrange
        DialogUtility.showAllSettingsDialog(
            activity, emptyMap(), emptyMap(), null, group(methods = PolarDerivedMeasurementMethod.values().toSet())
        ).test()
        idle()

        // Act
        val container = latestDialog().findViewById<LinearLayout>(R.id.derived_methods_container)
        val names = (0 until container.childCount).map { (container.getChildAt(it) as CheckBox).text.toString() }

        // Assert
        assertEquals(PolarDerivedMeasurementMethod.values().size, names.size)
        assertEquals(names.size, names.toSet().size)
        assertTrue(names.all { it.isNotBlank() })
    }

    @Test
    fun `show all settings dialog uses single fifty hertz source rate when available`() {
        // Arrange
        DialogUtility.showAllSettingsDialog(activity, emptyMap(), emptyMap(), null, group(rates = setOf(26, 50, 100))).test()
        idle()

        // Act
        val rg = latestDialog().findViewById<RadioGroup>(R.id.derived_source_rate_group)

        // Assert
        assertEquals(listOf<Any?>(50), (0 until rg.childCount).map { rg.getChildAt(it).tag })
    }

    private fun device(id: String, rssi: Int = -50): PolarDeviceInfo {
        val info = mockk<PolarDeviceInfo>(relaxed = true)
        every { info.deviceId } returns id
        every { info.name } returns "Polar $id"
        every { info.rssi } returns rssi
        return info
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            idle()
            check(System.currentTimeMillis() < deadline) { "Condition not reached" }
            Thread.sleep(10)
        }
    }

    @Test
    fun `show sensor selection lists scanned devices without duplicates`() {
        // Arrange
        val selected = mutableListOf<PolarDeviceInfo?>()
        val a = device("A")
        val flowable = flowOf(a, device("A"), device("B"))

        // Act
        DialogUtility.showSensorSelection(activity, SensorListAdapter.ItemSelected { selected.add(it) }, flowable)
        val recycler = latestDialog().findViewById<RecyclerView>(R.id.sensors_list)
        waitUntil { recycler.adapter!!.itemCount == 2 }

        // Assert
        assertEquals(2, recycler.adapter!!.itemCount)
        assertTrue(selected.isEmpty())
    }

    @Test
    fun `show sensor selection reports null when dialog is cancelled`() {
        // Arrange
        val selected = mutableListOf<PolarDeviceInfo?>()
        DialogUtility.showSensorSelection(activity, SensorListAdapter.ItemSelected { selected.add(it) }, flow { awaitCancellation() })

        // Act
        latestDialog().cancel()
        idle()

        // Assert
        assertEquals(listOf<PolarDeviceInfo?>(null), selected)
    }

    @Test
    fun `show sensor selection dismisses dialog when scan fails`() {
        // Arrange
        val flowable = flow<PolarDeviceInfo> { throw IllegalStateException("scan failed") }

        // Act
        DialogUtility.showSensorSelection(activity, SensorListAdapter.ItemSelected { }, flowable)
        val dialog = latestDialog()
        waitUntil { !dialog.isShowing }

        // Assert
        assertFalse(dialog.isShowing)
    }

    @Test
    fun `show sensor selection reports device when item is clicked`() {
        // Arrange
        val selected = mutableListOf<PolarDeviceInfo?>()
        val a = device("A")
        DialogUtility.showSensorSelection(activity, SensorListAdapter.ItemSelected { selected.add(it) }, flowOf(a), setOf("A"))
        val dialog = latestDialog()
        val recycler = dialog.findViewById<RecyclerView>(R.id.sensors_list)
        waitUntil { recycler.adapter!!.itemCount > 0 }
        recycler.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1000, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(2000, android.view.View.MeasureSpec.EXACTLY)
        )
        recycler.layout(0, 0, 1000, 2000)

        // Act
        val deviceRow = (0 until recycler.childCount).map { recycler.getChildAt(it) }.first { it.hasOnClickListeners() }
        deviceRow.performClick()

        // Assert
        assertEquals(listOf(a), selected)
        assertFalse(dialog.isShowing)
    }
}
