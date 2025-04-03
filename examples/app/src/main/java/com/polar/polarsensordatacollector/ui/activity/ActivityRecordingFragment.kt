package com.polar.polarsensordatacollector.ui.activity

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.util.Pair
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.CalendarConstraints.DateValidator
import com.google.android.material.datepicker.CompositeDateValidator
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.ui.landing.MainFragmentDirections
import com.polar.polarsensordatacollector.ui.utils.showSnackBar
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.impl.utils.CaloriesType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale


@AndroidEntryPoint
class ActivityRecordingFragment : Fragment(R.layout.fragment_activity_recording) {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ENGLISH)
    private lateinit var activityDataView: View
    private lateinit var sleepDataButton: Button
    private lateinit var stepsDataButton: Button
    private lateinit var caloriesDataButton: Button
    private lateinit var hrSamplesButton: Button
    private lateinit var nightlyRechargeButton: Button
    private lateinit var ppiSamplesButton: Button
    private lateinit var skinTemperatureButton: Button

    private lateinit var sleepRecordingStateHeader: TextView
    private lateinit var forceStopSleepButton: Button

    private var fromDate: LocalDate? = null
    private var toDate: LocalDate? = null
    private lateinit var type: PolarBleApi.PolarActivityDataType

    private val viewModel: ActivityRecordingViewModel by viewModels()
    private var selectedCaloriesType: CaloriesType = CaloriesType.ACTIVITY


    companion object {
        private const val TAG = "ActivityRecFragment"
    }

    override fun onPause() {
        super.onPause()
        viewModel.initView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupViews(view)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sleepRecordingState.collect {
                   updateSleepRecordingStateUi(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiShowError.collect {
                    if (it.header.isNotEmpty()) {
                        showSnackBar(rootView = requireView(), it.header, it.description ?: "", showAsError = true)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiShowInfo.collect {
                    if (it.header.isNotEmpty()) {
                        showSnackBar(rootView = requireView(), it.header, it.description ?: "")
                    }
                }
            }
        }

        sleepDataButton.setOnClickListener {
            viewModel.initView()
            type = PolarBleApi.PolarActivityDataType.SLEEP
            showDateRangePicker()
        }

        stepsDataButton.setOnClickListener {
            viewModel.initView()
            type = PolarBleApi.PolarActivityDataType.STEPS
            showDateRangePicker()
        }

        caloriesDataButton.setOnClickListener {
            viewModel.initView()
            type = PolarBleApi.PolarActivityDataType.CALORIES
            showCaloriesTypePicker()
        }

        hrSamplesButton.setOnClickListener {
            viewModel.initView()
            type = PolarBleApi.PolarActivityDataType.HR_SAMPLES
            showDateRangePicker()
        }

        nightlyRechargeButton.setOnClickListener {
            viewModel.initView()
            type = PolarBleApi.PolarActivityDataType.NIGHTLY_RECHARGE
            showDateRangePicker()
        }

        ppiSamplesButton.setOnClickListener {
            viewModel.initView()
            type = PolarBleApi.PolarActivityDataType.PPI_SAMPLES
            showDateRangePicker()
        }

        skinTemperatureButton.setOnClickListener {
            viewModel.initView()
            type = PolarBleApi.PolarActivityDataType.SKIN_TEMPERATURE
            showDateRangePicker()
        }

        forceStopSleepButton.setOnClickListener {
            viewModel.initView()
            try {
                viewModel.forceStopSleep()
            } catch (e: Exception) {
                Log.e(ActivityRecordingFragment.TAG, "An error occurred while forcing sleep recording to stop: ", e)
                Toast.makeText(this.context, "An error occurred while forcing sleep recording to stop. Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showCaloriesTypePicker() {
        val caloriesTypes = CaloriesType.values()
        val caloriesTypeNames = caloriesTypes.map { it.name }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Select calories type")
            .setItems(caloriesTypeNames) { dialog, which ->
                selectedCaloriesType = caloriesTypes[which]
                showDateRangePicker()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showDateRangePicker() {
        val constraints = CalendarConstraints.Builder()
        val dateValidatorMax: DateValidator = DateValidatorPointBackward.before(Date().toInstant().toEpochMilli())
        val listValidators = ArrayList<DateValidator>()

        listValidators.apply {
            add(dateValidatorMax)
        }
        val validators = CompositeDateValidator.allOf(listValidators)
        constraints.setValidator(validators)

        val dateRange: MaterialDatePicker<Pair<Long, Long>> = MaterialDatePicker
            .Builder
            .dateRangePicker()
            .setTitleText("Select date range")
            .setTheme(R.style.MaterialCalendarTheme)
            .setCalendarConstraints(constraints.build())
            .build()
        dateRange.show(this.childFragmentManager, "DATE_RANGE_PICKER")
        dateRange.addOnPositiveButtonClickListener {
            this.fromDate = Instant.ofEpochMilli(it.first.toLong()).atZone(ZoneId.systemDefault()).toLocalDate()
            this.toDate = Instant.ofEpochMilli(it.second.toLong()).atZone(ZoneId.systemDefault()).toLocalDate()

            if (fromDate != null && toDate != null) {
                val navigateAction = MainFragmentDirections.activityToActivityTriggerAction(
                    viewModel.deviceId,
                    fromDate!!.format(this.dateFormatter),
                    toDate!!.format(this.dateFormatter),
                    type.name,
                    selectedCaloriesType.name
                )
                findNavController().navigate(navigateAction)
            }
        }
        dateRange.addOnCancelListener {
            findNavController().navigate(ActivityRecordingFragmentDirections.activityToHome())
        }
    }

    private fun updateSleepRecordingStateUi(state: ActivityRecordingViewModel.SleepRecordingState) {
        val stringKey =
            when (state.enabled) {
                true -> R.string.sleep_recording_state_on
                false -> R.string.sleep_recording_state_off
                null -> R.string.sleep_recording_state_unavailable
            }
        sleepRecordingStateHeader.text = getString(stringKey)
        forceStopSleepButton.isEnabled = state.enabled != null && state.enabled!! == true
    }

    private fun setupViews(view: View) {
        sleepDataButton = view.findViewById(R.id.activity_sleep_button)
        stepsDataButton = view.findViewById(R.id.activity_steps_button)
        caloriesDataButton = view.findViewById(R.id.activity_calories_button)
        hrSamplesButton = view.findViewById(R.id.hr_samples_button)
        nightlyRechargeButton = view.findViewById(R.id.nightly_recharge_button)
        ppiSamplesButton = view.findViewById(R.id.ppi_samples_button)
        skinTemperatureButton = view.findViewById(R.id.activity_skin_temperature_recording_button)
        activityDataView = view.findViewById(R.id.activity_recording_group)
        val sleepHeader: TextView = view.findViewById(R.id.activity_sleep_recording_header)
        sleepHeader.text = "Sleep analysis result"
        val stepsHeader: TextView = view.findViewById(R.id.activity_steps_recording_header)
        stepsHeader.text = "Steps analysis result"
        val caloriesHeader: TextView = view.findViewById(R.id.activity_calories_recording_header)
        caloriesHeader.text = "Calories analysis result"
        val skinTemperatureHeader: TextView = view.findViewById(R.id.activity_skin_temperature_recording_header)
        skinTemperatureHeader.text = "Skin temperature result"
        forceStopSleepButton = view.findViewById(R.id.force_stop_sleep_button)
        sleepRecordingStateHeader = view.findViewById(R.id.force_stop_sleep_header)
    }
}