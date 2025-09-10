package com.polar.polarsensordatacollector.ui.physicalconfig

import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.datepicker.MaterialDatePicker
import com.polar.sdk.api.model.PolarFirstTimeUseConfig
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import java.text.SimpleDateFormat
import java.util.*
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.di.PolarBleSdkModule


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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.physical_config)

        initViews()
        setupListeners()

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

        radioGroupSex.check(R.id.radioButtonMale)
        editTextHeight.setText("185")
        editTextWeight.setText("85")

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
        textViewBirthday.setText("2000-01-01")

        buttonOk.setOnClickListener {
            saveUserData()
        }
    }

    private fun initializeHeartRateSpinners() {
        val maxHeartRateValues = (PolarFirstTimeUseConfig.MAX_HEART_RATE_MIN..PolarFirstTimeUseConfig.MAX_HEART_RATE_MAX).toList()
        val restingHeartRateValues = (PolarFirstTimeUseConfig.RESTING_HEART_RATE_MIN..PolarFirstTimeUseConfig.RESTING_HEART_RATE_MAX).toList()

        spinnerMaxHeartRate.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, maxHeartRateValues)
        spinnerMaxHeartRate.setSelection(maxHeartRateValues.indexOf(180))
        spinnerRestingHeartRate.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, restingHeartRateValues)
        spinnerRestingHeartRate.setSelection(restingHeartRateValues.indexOf(50))
    }

    private fun initializeTrainingBackgroundSpinner() {
        spinnerTrainingBackground.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, PolarFirstTimeUseConfig.TRAINING_BACKGROUND_VALUES)
        spinnerTrainingBackground.setSelection( PolarFirstTimeUseConfig.TRAINING_BACKGROUND_VALUES.indexOf(30))
    }

    private fun initializeNumberPickerVO2max() {
        numberPickerVO2max.minValue = PolarFirstTimeUseConfig.VO2_MAX_MIN
        numberPickerVO2max.maxValue = PolarFirstTimeUseConfig.VO2_MAX_MAX
        numberPickerVO2max.value = 50
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

        val deviceId = intent.getStringExtra("DEVICE_ID") ?: run {
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
