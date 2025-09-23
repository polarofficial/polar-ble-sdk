package com.polar.polarsensordatacollector.ui.physicalconfig

import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.datepicker.MaterialDatePicker
import com.polar.sdk.api.model.PolarFirstTimeUseConfig
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import java.text.SimpleDateFormat
import java.util.*
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.di.PolarBleSdkModule
import com.polar.polarsensordatacollector.ui.devicesettings.DeviceSettingsViewModel
import com.polar.polarsensordatacollector.ui.landing.ONLINE_OFFLINE_KEY_DEVICE_ID
import com.polar.sdk.api.model.PolarPhysicalConfiguration
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val TAG = "PhysicalConfigActivity"

@AndroidEntryPoint
class PhysicalConfigActivity : AppCompatActivity() {

    private lateinit var radioGroupSex: RadioGroup
    private lateinit var textViewBirthday: TextView
    private lateinit var editTextHeight: EditText
    private lateinit var editTextWeight: EditText
    private lateinit var spinnerMaxHeartRate: Spinner
    private lateinit var spinnerRestingHeartRate: Spinner
    private lateinit var spinnerTrainingBackground: Spinner
    private lateinit var numberPickerVO2max: NumberPicker
    private lateinit var typicalDaySpinner: Spinner
    private lateinit var sleepGoalHoursPicker: NumberPicker
    private lateinit var sleepGoalMinutesPicker: NumberPicker
    private lateinit var buttonOk: Button

    private val api = PolarBleSdkModule.providePolarBleSdkApi(this)
    private val deviceSettingsViewModel: DeviceSettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val info = try {
                deviceSettingsViewModel.getUserPhysicalInfo()
                deviceSettingsViewModel.physInfo
                    .filterNotNull()
                    .first()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get user physical information from device, using defaults", e)
                showToast(getString(R.string.ftu_failed_to_get_user_physical_information_from_device_using_defaults))
                PolarPhysicalConfiguration(
                    gender = PolarFirstTimeUseConfig.Gender.MALE,
                    birthDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse("2000-01-01")!!,
                    height = 185f,
                    weight = 85f,
                    maxHeartRate = 180,
                    restingHeartRate = 50,
                    vo2Max = 50,
                    trainingBackground = 30,
                    typicalDay = PolarFirstTimeUseConfig.TypicalDay.MOSTLY_SITTING,
                    sleepGoalMinutes = 8 * 60,
                    deviceTime = LocalDate.now().toString()
                )
            }

            setContentView(R.layout.physical_config)
            initViews()
            setupListeners()

            val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            when (info.gender) {
                PolarFirstTimeUseConfig.Gender.MALE -> radioGroupSex.check(R.id.radioButtonMale)
                PolarFirstTimeUseConfig.Gender.FEMALE -> radioGroupSex.check(R.id.radioButtonFemale)
            }

            val birthDate = info.birthDate
            textViewBirthday.text = df.format(birthDate)

            editTextHeight.setText(info.height.toString())
            editTextWeight.run { setText(info.weight.toString()) }

            val maxHr = info.maxHeartRate
            spinnerMaxHeartRate.setSelection(
                (PolarFirstTimeUseConfig.MAX_HEART_RATE_MIN..PolarFirstTimeUseConfig.MAX_HEART_RATE_MAX)
                    .indexOf(maxHr)
            )

            val restHr = info.restingHeartRate
            spinnerRestingHeartRate.setSelection(
                (PolarFirstTimeUseConfig.RESTING_HEART_RATE_MIN..PolarFirstTimeUseConfig.RESTING_HEART_RATE_MAX)
                    .indexOf(restHr)
            )

            numberPickerVO2max.value = info.vo2Max

            spinnerTrainingBackground.setSelection(
                PolarFirstTimeUseConfig.TRAINING_BACKGROUND_VALUES
                    .indexOf(info.trainingBackground)
            )

            typicalDaySpinner.setSelection(
                info.typicalDay.ordinal
            )

            val totalMinutes = info.sleepGoalMinutes
            sleepGoalHoursPicker.value = totalMinutes / 60
            sleepGoalMinutesPicker.value = totalMinutes % 60
        }
    }

    private fun initViews() {
        radioGroupSex = findViewById(R.id.radioGroupSexual)
        textViewBirthday = findViewById(R.id.textViewBirthday)
        editTextHeight = findViewById(R.id.editTextHeight)
        editTextWeight = findViewById(R.id.editTextWeight)
        spinnerMaxHeartRate = findViewById(R.id.spinnerMaxHeartRate)
        spinnerRestingHeartRate = findViewById(R.id.spinnerRestingHeartRate)
        spinnerTrainingBackground = findViewById(R.id.spinnerTrainingBackground)
        numberPickerVO2max = findViewById(R.id.numberPickerVO2max)
        typicalDaySpinner = findViewById(R.id.typicalDaySpinner)
        sleepGoalHoursPicker = findViewById(R.id.sleepGoalPickerHours)
        sleepGoalMinutesPicker = findViewById(R.id.sleepGoalPickerMinutes)
        buttonOk = findViewById(R.id.submit_physical_button)

        initializeHeartRateSpinners()
        initializeNumberPickerVO2max()
        initializeTrainingBackgroundSpinner()
        initializeTypicalDaySpinner()
        initializeSleepGoalPicker()
    }

    private fun setupListeners() {
        textViewBirthday.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.birthday_hint)
                .setTheme(R.style.MaterialCalendarTheme)
                .build()

            datePicker.show(supportFragmentManager, "MATERIAL_DATE_PICKER")

            datePicker.addOnPositiveButtonClickListener { selection ->
                val selectedDate = Calendar.getInstance()
                selectedDate.timeInMillis = selection

                val formattedDate = String.format(
                    Locale.getDefault(),
                    "%d-%02d-%02d",
                    selectedDate.get(Calendar.YEAR),
                    selectedDate.get(Calendar.MONTH) + 1,
                    selectedDate.get(Calendar.DAY_OF_MONTH)
                )
                textViewBirthday.text = formattedDate
            }
        }

        buttonOk.setOnClickListener {
            saveUserData()
        }
    }

    private fun initializeHeartRateSpinners() {
        val maxHeartRateValues = (PolarFirstTimeUseConfig.MAX_HEART_RATE_MIN..PolarFirstTimeUseConfig.MAX_HEART_RATE_MAX).toList()
        val restingHeartRateValues = (PolarFirstTimeUseConfig.RESTING_HEART_RATE_MIN..PolarFirstTimeUseConfig.RESTING_HEART_RATE_MAX).toList()

        spinnerMaxHeartRate.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, maxHeartRateValues)
        spinnerRestingHeartRate.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, restingHeartRateValues)
    }

    private fun initializeTrainingBackgroundSpinner() {
        spinnerTrainingBackground.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, PolarFirstTimeUseConfig.TRAINING_BACKGROUND_VALUES)
    }

    private fun initializeNumberPickerVO2max() {
        numberPickerVO2max.minValue = PolarFirstTimeUseConfig.VO2_MAX_MIN
        numberPickerVO2max.maxValue = PolarFirstTimeUseConfig.VO2_MAX_MAX
    }

    private fun initializeTypicalDaySpinner() {
        val typicalDays = PolarFirstTimeUseConfig.TypicalDay.values()
        val displayedValues = typicalDays.map { it.name.replace('_', ' ') }
        typicalDaySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, displayedValues)
        typicalDaySpinner.setSelection(typicalDays.indexOf(PolarFirstTimeUseConfig.TypicalDay.MOSTLY_STANDING))
    }

    private fun initializeSleepGoalPicker() {
        sleepGoalHoursPicker.minValue = 5
        sleepGoalHoursPicker.maxValue = 11
        sleepGoalMinutesPicker.minValue = 0
        sleepGoalMinutesPicker.maxValue = 59

        sleepGoalHoursPicker.setOnValueChangedListener { _, _, hours ->
            if (hours == 11) {
                sleepGoalMinutesPicker.value = 0
                sleepGoalMinutesPicker.isEnabled = false
            } else {
                sleepGoalMinutesPicker.isEnabled = true
            }
        }
        sleepGoalHoursPicker.value = 8
    }

    private fun parseBirthday(birthdayStr: String): Date {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(birthdayStr) ?: Date()
    }

    private fun saveUserData() {
        val selectedGenderId = radioGroupSex.checkedRadioButtonId
        val genderValue = when (selectedGenderId) {
            R.id.radioButtonMale -> PolarFirstTimeUseConfig.Gender.MALE
            R.id.radioButtonFemale -> PolarFirstTimeUseConfig.Gender.FEMALE
            else -> throw IllegalStateException("Invalid gender selection")
        }
        val birthDate = parseBirthday(textViewBirthday.text.toString())
        val height = editTextHeight.text.toString().toFloatOrNull() ?: 0.0f
        val weight = editTextWeight.text.toString().toFloatOrNull() ?: 0.0f
        val maxHeartRate = spinnerMaxHeartRate.selectedItem.toString().toInt()
        val restingHeartRate = spinnerRestingHeartRate.selectedItem.toString().toInt()
        val trainingBackground = spinnerTrainingBackground.selectedItem.toString().toInt()
        val vo2Max = numberPickerVO2max.value
        val typicalDay = PolarFirstTimeUseConfig.TypicalDay.values()[typicalDaySpinner.selectedItemPosition]
        val sleepGoalMinutes = sleepGoalHoursPicker.value * 60 + sleepGoalMinutesPicker.value

        val polarFirstTimeUseConfig = PolarFirstTimeUseConfig(
                gender = genderValue,
                birthDate = birthDate,
                height = height,
                weight = weight,
                maxHeartRate = maxHeartRate,
                restingHeartRate = restingHeartRate,
                vo2Max = vo2Max,
                trainingBackground = trainingBackground,
                typicalDay = typicalDay,
                sleepGoalMinutes = sleepGoalMinutes,
                deviceTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Calendar.getInstance().time))

        val deviceId = intent.getStringExtra(ONLINE_OFFLINE_KEY_DEVICE_ID) ?: run {
            showToast("Device ID not found")
            return
        }

        api.doFirstTimeUse(deviceId, ftuConfig = polarFirstTimeUseConfig)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())
            .subscribe({
                showToast("Physical data sent successfully")
            }, { error ->
                Log.e("FirstTimeUseActivity", "Error sending Physical data: ${error.localizedMessage}", error)
                showToast("Error sending Physical data")
            })
    }


    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
