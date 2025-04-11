package com.polar.polarsensordatacollector.ui.utils

import android.app.Activity
import android.app.Dialog
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.Button
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
import com.polar.polarsensordatacollector.ui.landing.SensorListAdapter.ItemSelected
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarSensorSetting.SettingType
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.*
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.*

object DialogUtility {
    private const val TAG = "DialogUtility"

    fun showAllSettingsDialog(
        activity: Activity,
        available: Map<SettingType, Set<Int>>,
        all: Map<SettingType, Set<Int>>,
        selectedSettings: Map<SettingType, Int?>?
    ): Single<Map<SettingType, Int>> {

        // custom dialog
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.sensor_settings_dialog)
        val samplingRateRg = dialog.findViewById<RadioGroup>(R.id.sampling_rate_group)
        val resolutionRg = dialog.findViewById<RadioGroup>(R.id.resolution_group)
        val rangeRg = dialog.findViewById<RadioGroup>(R.id.range_group)
        val channelsRg = dialog.findViewById<RadioGroup>(R.id.channel_group)
        drawAllAndAvailableSettingsGroup(
            activity,
            samplingRateRg,
            SettingType.SAMPLE_RATE,
            available,
            all,
            selectedSettings
        )
        drawAllAndAvailableSettingsGroup(
            activity,
            resolutionRg,
            SettingType.RESOLUTION,
            available,
            all,
            selectedSettings
        )
        drawAllAndAvailableSettingsGroup(
            activity,
            rangeRg,
            SettingType.RANGE,
            available,
            all,
            selectedSettings
        )
        drawAllAndAvailableSettingsGroup(
            activity,
            channelsRg,
            SettingType.CHANNELS,
            available,
            all,
            selectedSettings
        )
        if (samplingRateRg.childCount == 0) {
            val noValues = dialog.findViewById<TextView>(R.id.sampling_rate_no_values)
            noValues.visibility = View.VISIBLE
        }
        if (resolutionRg.childCount == 0) {
            val noValues = dialog.findViewById<TextView>(R.id.resolution_no_values)
            noValues.visibility = View.VISIBLE
        }
        if (rangeRg.childCount == 0) {
            val noValues = dialog.findViewById<TextView>(R.id.range_no_values)
            noValues.visibility = View.VISIBLE
        }
        if (channelsRg.childCount == 0) {
            val noValues = dialog.findViewById<TextView>(R.id.channel_no_values)
            noValues.visibility = View.VISIBLE
        }
        val ok = dialog.findViewById<Button>(R.id.dialog_ok_button)
        ok.setOnClickListener { dialog.dismiss() }
        return Single.create { e: SingleEmitter<Map<SettingType, Int>> ->
            val selected: MutableMap<SettingType, Int> = EnumMap(SettingType::class.java)

            dialog.setOnDismissListener {
                handleSettingsGroupSelection(dialog, samplingRateRg, selected, SettingType.SAMPLE_RATE)
                handleSettingsGroupSelection(dialog, resolutionRg, selected, SettingType.RESOLUTION)
                handleSettingsGroupSelection(dialog, rangeRg, selected, SettingType.RANGE)
                handleSettingsGroupSelection(dialog, channelsRg, selected, SettingType.CHANNELS)
                e.onSuccess(selected)
            }
            dialog.setOnCancelListener {
                e.tryOnError(
                    Throwable("")
                )
            }
            dialog.show()
        }
            .subscribeOn(AndroidSchedulers.mainThread())
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
            val value = Integer.valueOf(button.text.toString())
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
                    if (button.isEnabled && selectedSettings[type] != null && button.text == selectedSettings[type].toString()) {
                        break
                    }
                    index++
                }
            } else {
                while (index < rgChildCount) {
                    val button = rg.getChildAt(index) as MaterialRadioButton
                    if (button.isEnabled) {
                        break
                    }
                    index++
                }
            }
            val button = rg.getChildAt(index) as MaterialRadioButton
            button.isChecked = true
        }
    }

    fun showSensorSelection(activity: Activity, itemSelected: ItemSelected, flowable: Flowable<PolarDeviceInfo>) {

        // custom dialog
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.sensor_selection_dialog)
        val dataItemList: MutableList<PolarDeviceInfo> = ArrayList()
        var disposable: Disposable? = null
        val adapter = SensorListAdapter(dataItemList) { info: PolarDeviceInfo? ->
            Log.d("", "selected: $info")
            itemSelected.itemSelected(info)
            dialog.dismiss()
            disposable?.dispose()
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
            disposable?.dispose()
        }
        disposable = Completable.create { e: CompletableEmitter ->
            dialog.show()
            e.onComplete()
        }
            .subscribeOn(AndroidSchedulers.mainThread())
            .observeOn(Schedulers.io())
            .andThen(flowable)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { polarDeviceInfo: PolarDeviceInfo ->
                    dataItemList.add(polarDeviceInfo)
                    dataItemList.sortWith { t1: PolarDeviceInfo, t2: PolarDeviceInfo -> if (t1.rssi > t2.rssi) -1 else 0 }
                    adapter.notifyDataSetChanged()
                },
                { throwable: Throwable ->
                    dialog.dismiss()
                    Log.e(TAG, "${throwable.message}")
                    Toast.makeText(activity, throwable.message, Toast.LENGTH_SHORT).show()
                }
            )
    }
}