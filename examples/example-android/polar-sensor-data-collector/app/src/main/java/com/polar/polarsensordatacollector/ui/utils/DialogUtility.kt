package com.polar.polarsensordatacollector.ui.utils

import android.app.Activity
import android.app.Dialog
import android.util.Log
import android.view.View
import android.view.View.VISIBLE
import android.view.View.GONE
import android.view.Window
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.radiobutton.MaterialRadioButton
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.ui.landing.SensorListAdapter
import com.polar.sdk.api.model.PolarDerivedMeasurementMethod
import com.polar.sdk.api.model.PolarDerivedMeasurementSettingsGroup
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarSensorSetting.SettingType
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.util.*

/**
 * Selected derived measurement settings captured from the settings dialog.
 * null when the user did not configure any derived measurement in the dialog.
 *
 * All values are constrained to options advertised by the device's settings group,
 * so any combination in this class is guaranteed to be valid for Request Measurement Start.
 */
data class DerivedDialogResult(
    val selectedMethods: Set<PolarDerivedMeasurementMethod>,
    val selectedSourceRate: Int,        // Hz — from group.sourceSampleRates
    val selectedTimeWindowMs: Int       // ms — from group.timeWindowOptions
)

object DialogUtility {
    private const val TAG = "DialogUtility"

    /** View references returned by [setupDerivedSection] for use in the dismiss callback. */
    private data class DerivedSectionRefs(
        val hasDerivedSection: Boolean,
        val enableCheckbox: CheckBox,
        val sourceRateRg: RadioGroup,
        val timeWindowRg: RadioGroup,
        val methodCheckBoxes: List<CheckBox>
    )

    fun showAllSettingsDialog(
        activity: Activity,
        available: Map<SettingType, Set<Int>>,
        all: Map<SettingType, Set<Int>>,
        selectedSettings: Map<SettingType, Int?>?,
        derivedSettingsGroup: PolarDerivedMeasurementSettingsGroup? = null,
        previousDerivedSettings: DerivedDialogResult? = null
    ): Single<Triple<Map<SettingType, Int>, DerivedDialogResult?, Boolean>> {

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.sensor_settings_dialog)

        val samplingRateRg = dialog.findViewById<RadioGroup>(R.id.sampling_rate_group)
        val resolutionRg   = dialog.findViewById<RadioGroup>(R.id.resolution_group)
        val rangeRg        = dialog.findViewById<RadioGroup>(R.id.range_group)
        val channelsRg     = dialog.findViewById<RadioGroup>(R.id.channel_group)

        // Remove derived output cadence rates from the raw ACC sample-rate list so that
        // the user cannot accidentally pick a rate that belongs to the derived output.
        val derivedOutputRates: Set<Int> = derivedSettingsGroup
            ?.timeWindowOptions
            ?.mapNotNull { ms -> if (ms > 0) (1000.0 / ms).toInt().takeIf { it > 0 } else null }
            ?.toSet()
            ?: emptySet()
        fun filterSampleRates(rates: Set<Int>?) =
            rates?.minus(derivedOutputRates)?.takeIf { it.isNotEmpty() } ?: rates ?: emptySet()
        val availableForRate = if (derivedSettingsGroup != null)
            available.toMutableMap().also { it[SettingType.SAMPLE_RATE] = filterSampleRates(available[SettingType.SAMPLE_RATE]) }
        else available
        val allForRate = if (derivedSettingsGroup != null)
            all.toMutableMap().also { it[SettingType.SAMPLE_RATE] = filterSampleRates(all[SettingType.SAMPLE_RATE]) }
        else all

        drawAllAndAvailableSettingsGroup(activity, samplingRateRg, SettingType.SAMPLE_RATE, availableForRate, allForRate, selectedSettings)
        drawAllAndAvailableSettingsGroup(activity, resolutionRg, SettingType.RESOLUTION, available, all, selectedSettings)
        drawAllAndAvailableSettingsGroup(activity, rangeRg, SettingType.RANGE, available, all, selectedSettings)
        drawAllAndAvailableSettingsGroup(activity, channelsRg, SettingType.CHANNELS, available, all, selectedSettings)

        val derived = setupDerivedSection(activity, dialog, derivedSettingsGroup, previousDerivedSettings)

        val ok = dialog.findViewById<Button>(R.id.dialog_ok_button)
        ok.setOnClickListener { dialog.dismiss() }

        return Single.create { emitter ->
            dialog.setOnDismissListener {
                val selected: MutableMap<SettingType, Int> = EnumMap(SettingType::class.java)
                handleSettingsGroupSelection(dialog, samplingRateRg, selected, SettingType.SAMPLE_RATE)
                handleSettingsGroupSelection(dialog, resolutionRg, selected, SettingType.RESOLUTION)
                handleSettingsGroupSelection(dialog, rangeRg, selected, SettingType.RANGE)
                handleSettingsGroupSelection(dialog, channelsRg, selected, SettingType.CHANNELS)

                val derivedResult = buildDerivedResult(derived, derivedSettingsGroup)
                if (!emitter.isDisposed) emitter.onSuccess(Triple(selected.toMap(), derivedResult, derived.hasDerivedSection))
            }
            dialog.setOnCancelListener {
                val defaults: MutableMap<SettingType, Int> = EnumMap(SettingType::class.java)
                selectedSettings?.forEach { (type, value) -> if (value != null) defaults[type] = value }
                // On cancel, preserve existing derived settings so nothing changes.
                if (!emitter.isDisposed) emitter.onSuccess(Triple(defaults.toMap(), previousDerivedSettings, false))
            }
            dialog.show()
            dialog.window?.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                (activity.resources.displayMetrics.heightPixels * 0.85).toInt()
            )
        }.subscribeOn(AndroidSchedulers.mainThread())
    }

    /**
     * Inflates and populates the derived-measurement section of the settings dialog.
     * Returns a [DerivedSectionRefs] with the view references needed when the dialog is dismissed.
     * When [derivedSettingsGroup] is null the section stays hidden and the refs are inert.
     */
    private fun setupDerivedSection(
        activity: Activity,
        dialog: Dialog,
        derivedSettingsGroup: PolarDerivedMeasurementSettingsGroup?,
        previousDerivedSettings: DerivedDialogResult?
    ): DerivedSectionRefs {
        val derivedSectionDivider   = dialog.findViewById<View>(R.id.derived_section_divider)
        val derivedSectionLabel     = dialog.findViewById<TextView>(R.id.derived_section_label)
        val derivedEnableCheckbox   = dialog.findViewById<CheckBox>(R.id.derived_enable_checkbox)
        val derivedSourceRateLabel  = dialog.findViewById<TextView>(R.id.derived_source_rate_label)
        val derivedSourceRateRg     = dialog.findViewById<RadioGroup>(R.id.derived_source_rate_group)
        val derivedTimeWindowLabel  = dialog.findViewById<TextView>(R.id.derived_time_window_label)
        val derivedTimeWindowRg     = dialog.findViewById<RadioGroup>(R.id.derived_time_window_group)
        val derivedMethodsLabel     = dialog.findViewById<TextView>(R.id.derived_methods_label)
        val derivedMethodsContainer = dialog.findViewById<LinearLayout>(R.id.derived_methods_container)

        val methodCheckBoxes = mutableListOf<CheckBox>()

        if (derivedSettingsGroup == null) {
            return DerivedSectionRefs(
                hasDerivedSection = false,
                enableCheckbox    = derivedEnableCheckbox,
                sourceRateRg      = derivedSourceRateRg,
                timeWindowRg      = derivedTimeWindowRg,
                methodCheckBoxes  = methodCheckBoxes
            )
        }

        listOf(derivedSectionDivider, derivedSectionLabel, derivedEnableCheckbox)
            .forEach { it.visibility = VISIBLE }

        val subViews = listOf(
            derivedSourceRateLabel, derivedSourceRateRg,
            derivedTimeWindowLabel, derivedTimeWindowRg,
            derivedMethodsLabel, derivedMethodsContainer
        )
        val wasEnabled = previousDerivedSettings != null
        derivedEnableCheckbox.isChecked = wasEnabled
        fun updateSubVisibility(enabled: Boolean) = subViews.forEach { it.visibility = if (enabled) VISIBLE else GONE }
        updateSubVisibility(wasEnabled)
        derivedEnableCheckbox.setOnCheckedChangeListener { _, isChecked -> updateSubVisibility(isChecked) }

        // Source rate buttons — limit to 50 Hz if available, as that is the only rate current FW supports
        val validSourceRates = derivedSettingsGroup.sourceSampleRates.let { rates ->
            if (50 in rates) listOf(50) else rates.sorted()
        }
        val prevRate = previousDerivedSettings?.selectedSourceRate
        validSourceRates.forEach { hz ->
            val rb = MaterialRadioButton(activity)
            rb.tag = hz
            rb.text = "$hz Hz"
            rb.isEnabled = true
            derivedSourceRateRg.addView(rb)
            if (hz == prevRate) rb.isChecked = true
        }
        if (derivedSourceRateRg.checkedRadioButtonId == -1)
            (derivedSourceRateRg.getChildAt(0) as? MaterialRadioButton)?.isChecked = true

        // Time window buttons
        val prevWindow = previousDerivedSettings?.selectedTimeWindowMs
        derivedSettingsGroup.timeWindowOptions.sorted().forEach { ms ->
            val rb = MaterialRadioButton(activity)
            rb.text = formatTimeWindow(ms)
            rb.tag = ms
            derivedTimeWindowRg.addView(rb)
            if (ms == prevWindow) rb.isChecked = true
        }
        if (derivedTimeWindowRg.checkedRadioButtonId == -1) {
            val preferred1000 = (0 until derivedTimeWindowRg.childCount)
                .map { derivedTimeWindowRg.getChildAt(it) as MaterialRadioButton }
                .firstOrNull { (it.tag as? Int) == 1000 }
            (preferred1000 ?: derivedTimeWindowRg.getChildAt(0) as? MaterialRadioButton)?.isChecked = true
        }

        // Method checkboxes
        val prevMethods = previousDerivedSettings?.selectedMethods ?: setOf(PolarDerivedMeasurementMethod.DOWNSAMPLE)
        derivedSettingsGroup.supportedMethods.sortedBy { it.id }.forEach { mode ->
            val cb = CheckBox(activity)
            cb.text = derivedMethodDisplayName(activity, mode)
            cb.tag = mode
            cb.isChecked = mode in prevMethods
            methodCheckBoxes.add(cb)
            derivedMethodsContainer.addView(cb)
        }
        if (methodCheckBoxes.none { it.isChecked }) methodCheckBoxes.firstOrNull()?.isChecked = true

        return DerivedSectionRefs(
            hasDerivedSection = true,
            enableCheckbox    = derivedEnableCheckbox,
            sourceRateRg      = derivedSourceRateRg,
            timeWindowRg      = derivedTimeWindowRg,
            methodCheckBoxes  = methodCheckBoxes
        )
    }

    /**
     * Reads the derived-section views and constructs a [DerivedDialogResult].
     * Returns null when the section was absent or the enable checkbox was unchecked.
     */
    private fun buildDerivedResult(
        refs: DerivedSectionRefs,
        derivedSettingsGroup: PolarDerivedMeasurementSettingsGroup?
    ): DerivedDialogResult? {
        if (!refs.hasDerivedSection || !refs.enableCheckbox.isChecked || refs.methodCheckBoxes.isEmpty()) return null

        val checkedMethods = refs.methodCheckBoxes
            .filter { it.isChecked }
            .mapNotNull { it.tag as? PolarDerivedMeasurementMethod }
            .toSet()
        val methods = checkedMethods.ifEmpty {
            listOfNotNull(refs.methodCheckBoxes.firstOrNull()?.tag as? PolarDerivedMeasurementMethod).toSet()
        }

        val sourceRateRb = refs.sourceRateRg.findViewById<MaterialRadioButton>(refs.sourceRateRg.checkedRadioButtonId)
        val sourceRate = (sourceRateRb?.tag as? Int)
            ?: derivedSettingsGroup?.sourceSampleRates?.maxOrNull()
            ?: 50

        val timeWindowRb = refs.timeWindowRg.findViewById<MaterialRadioButton>(refs.timeWindowRg.checkedRadioButtonId)
        val timeWindowMs = (timeWindowRb?.tag as? Int)
            ?: derivedSettingsGroup?.timeWindowOptions?.minOrNull()
            ?: 1000

        return DerivedDialogResult(
            selectedMethods      = methods,
            selectedSourceRate   = sourceRate,
            selectedTimeWindowMs = timeWindowMs
        )
    }

    private fun formatTimeWindow(ms: Int): String {
        val duration = when {
            ms < 1_000               -> "$ms ms"
            ms < 60_000              -> "${ms / 1_000} s"
            ms < 3_600_000           -> "${ms / 60_000} min"
            else                     -> "${ms / 3_600_000} h"
        }
        // Compute the output cadence: 1000 ms / window ms = Hz (may be fractional for long windows)
        val outputLabel = when {
            ms <= 0          -> ""
            ms <= 1_000      -> " — ${1000 / ms} Hz output"
            ms < 60_000      -> " — 1 sample / ${ms / 1_000} s"
            ms < 3_600_000   -> " — 1 sample / ${ms / 60_000} min"
            else             -> " — 1 sample / ${ms / 3_600_000} h"
        }
        return "$duration$outputLabel"
    }

    private fun derivedMethodDisplayName(activity: Activity, method: PolarDerivedMeasurementMethod): String = when (method) {
        PolarDerivedMeasurementMethod.DOWNSAMPLE    -> activity.getString(R.string.derived_method_downsample)
        PolarDerivedMeasurementMethod.MIN           -> activity.getString(R.string.derived_method_min)
        PolarDerivedMeasurementMethod.MAX           -> activity.getString(R.string.derived_method_max)
        PolarDerivedMeasurementMethod.AVG           -> activity.getString(R.string.derived_method_avg)
        PolarDerivedMeasurementMethod.STD           -> activity.getString(R.string.derived_method_std)
        PolarDerivedMeasurementMethod.NORM          -> activity.getString(R.string.derived_method_norm)
        PolarDerivedMeasurementMethod.MIN_OF_NORMS  -> activity.getString(R.string.derived_method_min_of_norms)
        PolarDerivedMeasurementMethod.MAX_OF_NORMS  -> activity.getString(R.string.derived_method_max_of_norms)
        PolarDerivedMeasurementMethod.STD_OF_NORMS  -> activity.getString(R.string.derived_method_std_of_norms)
        PolarDerivedMeasurementMethod.NORM_OF_STDS  -> activity.getString(R.string.derived_method_norm_of_stds)
    }

    private fun handleSettingsGroupSelection(
        dialog: Dialog,
        rg: RadioGroup,
        selected: MutableMap<SettingType, Int>,
        type: SettingType
    ) {
        if (rg.childCount != 0) {
            val s = rg.indexOfChild(dialog.findViewById(rg.checkedRadioButtonId))
            val button = rg.getChildAt(s) as MaterialRadioButton
            // Prefer the numeric tag (set by drawAllAndAvailableSettingsGroup) so that
            // display labels like "1 Hz (requires derived)" don't confuse Integer.valueOf.
            val value = (button.tag as? Int) ?: Integer.valueOf(button.text.toString())
            selected[type] = value
        }
    }

    private fun drawAllAndAvailableSettingsGroup(
        activity: Activity,
        rg: RadioGroup,
        type: SettingType,
        availableSettings: Map<SettingType, Set<Int>>,
        allSettings: Map<SettingType, Set<Int>>,
        selectedSettings: Map<SettingType, Int?>?
    ) {
        if (availableSettings.containsKey(type) && allSettings.containsKey(type)) {
            val availableValues = availableSettings[type]?.toList()
            allSettings[type]?.toList()?.sorted()?.let { allValues ->
                for (value in allValues) {
                    val rb = MaterialRadioButton(activity)
                    rb.tag = value   // always store numeric value in tag for reliable retrieval
                    var rbText: String
                    if (availableValues?.contains(value) == true) {
                        rb.isEnabled = true
                        rbText = value.toString()
                    } else {
                        rb.isEnabled = false
                        rbText = "$value Available in SDK mode"
                    }
                    rb.text = rbText
                    rg.addView(rb)
                }
            }

        } else if (availableSettings.containsKey(type)) {
            availableSettings[type]?.toList()?.sorted()?.let { availableValues ->
                for (availableValue in availableValues) {
                    val rb = MaterialRadioButton(activity)
                    rb.text = availableValue.toString()
                    rg.addView(rb)
                }
            }
        }

        // Choose one to set selected
        val rgChildCount = rg.childCount
        if (rgChildCount > 0) {
            var index = 0
            if (selectedSettings != null) {
                while (index < rgChildCount) {
                    val button = rg.getChildAt(index) as MaterialRadioButton
                    // Match by tag (Int) first; fall back to text comparison for legacy buttons.
                    val buttonValue = (button.tag as? Int) ?: button.text.toString().toIntOrNull()
                    if (button.isEnabled && selectedSettings[type] != null && buttonValue == selectedSettings[type]) {
                        break
                    }
                    index++
                }
                // If the previously-selected value is no longer present (or was disabled),
                // fall back to the first enabled button instead of going out-of-bounds.
                if (index >= rgChildCount) {
                    index = (0 until rgChildCount).firstOrNull {
                        (rg.getChildAt(it) as? MaterialRadioButton)?.isEnabled == true
                    } ?: 0
                }
            } else {
                while (index < rgChildCount) {
                    val button = rg.getChildAt(index) as MaterialRadioButton
                    if (button.isEnabled) {
                        break
                    }
                    index++
                }
                // If every button is disabled fall back to index 0 to avoid out-of-bounds.
                if (index >= rgChildCount) index = 0
            }
            val button = rg.getChildAt(index) as? MaterialRadioButton
            button?.isChecked = true
        }
    }


    fun showSensorSelection(activity: Activity, itemSelected: SensorListAdapter.ItemSelected, flowable: Flow<PolarDeviceInfo>, connectedDeviceIds: Set<String> = emptySet()) {

        // custom dialog
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.sensor_selection_dialog)
        val rawDevices: MutableList<PolarDeviceInfo> = ArrayList()
        var scanJob: Job? = null
        val adapter = SensorListAdapter(connectedDeviceIds) { info: PolarDeviceInfo? ->
            Log.d("", "selected: $info")
            itemSelected.itemSelected(info)
            dialog.dismiss()
            scanJob?.cancel()
        }
        val recyclerView: RecyclerView = dialog.findViewById(R.id.sensors_list)
        val layoutManager = LinearLayoutManager(activity)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        val itemDecorator = DividerItemDecoration(activity, DividerItemDecoration.VERTICAL)
        ContextCompat.getDrawable(activity, R.drawable.divider)
            ?.let { itemDecorator.setDrawable(it) }
        recyclerView.addItemDecoration(itemDecorator)
        dialog.setOnCancelListener {
            itemSelected.itemSelected(null)
            scanJob?.cancel()
        }

        dialog.show()
        scanJob = CoroutineScope(Dispatchers.Main).launch {
            flowable
                .flowOn(Dispatchers.IO)
                .catch { throwable ->
                    dialog.dismiss()
                    Log.e(TAG, "${throwable.message}")
                    Toast.makeText(activity, throwable.message, Toast.LENGTH_SHORT).show()
                }
                .collect { polarDeviceInfo ->
                    if (!rawDevices.any { it.deviceId == polarDeviceInfo.deviceId }) {
                        rawDevices.add(polarDeviceInfo)
                        adapter.updateDevices(rawDevices)
                    }
                }
        }
    }
}