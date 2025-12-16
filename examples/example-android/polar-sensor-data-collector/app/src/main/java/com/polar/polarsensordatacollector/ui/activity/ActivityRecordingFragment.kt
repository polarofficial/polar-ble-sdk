package com.polar.polarsensordatacollector.ui.activity

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
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
import com.polar.polarsensordatacollector.ui.landing.ONLINE_OFFLINE_KEY_DEVICE_ID
import com.polar.polarsensordatacollector.ui.utils.showSnackBar
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionDataTypes
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
    private lateinit var sleepDataButton: Button
    private lateinit var stepsDataButton: Button
    private lateinit var caloriesDataButton: Button
    private lateinit var hrSamplesButton: Button
    private lateinit var nightlyRechargeButton: Button
    private lateinit var ppiSamplesButton: Button
    private lateinit var skinTemperatureButton: Button
    private lateinit var activeTimeButton: Button
    private lateinit var activitySamplesButton: Button
    private lateinit var dailySummaryButton: Button

    private lateinit var trainingSessionsButton: Button

    private var fromDate: LocalDate? = null
    private var toDate: LocalDate? = null
    private lateinit var type: Any

    private val viewModel: ActivityRecordingViewModel by viewModels()
    private var selectedCaloriesType: CaloriesType = CaloriesType.ACTIVITY

    private lateinit var selectedDeviceId: String

    companion object {
        private const val TAG = "ActivityRecFragment"
    }

    override fun onPause() {
        super.onPause()
        viewModel.initView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        selectedDeviceId = arguments?.getString(ONLINE_OFFLINE_KEY_DEVICE_ID) ?: throw Exception("ActivityRecordingFragment has no deviceId")
        setupViews(view)

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
            type = PolarBleApi.PolarActivityDataType.SKIN_TEMPERATURE
            showDateRangePicker()
        }

        trainingSessionsButton.setOnClickListener {
            viewModel.initView()
            type = PolarTrainingSessionDataTypes.TRAINING_SESSION_SUMMARY
            showDateRangePicker()
        }

        activeTimeButton.setOnClickListener {
            viewModel.initView()
            type = PolarBleApi.PolarActivityDataType.ACTIVE_TIME
            showDateRangePicker()
        }

        activitySamplesButton.setOnClickListener {
            viewModel.initView()
            type = PolarBleApi.PolarActivityDataType.ACTIVITY_SAMPLES
            showDateRangePicker()
        }

        dailySummaryButton.setOnClickListener {
            viewModel.initView()
            type = PolarBleApi.PolarActivityDataType.DAILY_SUMMARY
            showDateRangePicker()
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
                if (type is PolarTrainingSessionDataTypes) {
                    val navigateAction = MainFragmentDirections.loadNavigateToListAction(
                        selectedDeviceId,
                        fromDate!!.format(this.dateFormatter),
                        toDate!!.format(this.dateFormatter)
                    )
                    findNavController().navigate(navigateAction)
                } else {
                    val polarActivityDataType = type as PolarBleApi.PolarActivityDataType
                    val navigateAction = MainFragmentDirections.activityToActivityTriggerAction(
                        viewModel.deviceId,
                        fromDate!!.format(this.dateFormatter),
                        toDate!!.format(this.dateFormatter),
                        polarActivityDataType.name,
                        selectedCaloriesType.name
                    )
                    findNavController().navigate(navigateAction)
                }

            }
        }
        dateRange.addOnCancelListener {
            findNavController().navigate(ActivityRecordingFragmentDirections.activityToHome())
        }
    }

    private fun setupViews(view: View) {
        sleepDataButton = view.findViewById(R.id.activity_sleep_button)
        stepsDataButton = view.findViewById(R.id.activity_steps_button)
        caloriesDataButton = view.findViewById(R.id.activity_calories_button)
        hrSamplesButton = view.findViewById(R.id.hr_samples_button)
        nightlyRechargeButton = view.findViewById(R.id.nightly_recharge_button)
        ppiSamplesButton = view.findViewById(R.id.ppi_samples_recording_button)
        skinTemperatureButton = view.findViewById(R.id.activity_skin_temperature_recording_button)
        activeTimeButton = view.findViewById(R.id.active_time_button)
        trainingSessionsButton = view.findViewById(R.id.button_get_training_sessions)
        activitySamplesButton = view.findViewById(R.id.button_get_activity_samples)
        dailySummaryButton = view.findViewById(R.id.button_get_daily_summary)
    }
}