package com.polar.sdk.api.model

import java.util.Date
import java.util.Calendar

import fi.polar.remote.representation.protobuf.Types
import java.time.format.DateTimeFormatter
import fi.polar.remote.representation.protobuf.PhysData
import fi.polar.remote.representation.protobuf.PhysData.PbUserTypicalDay.TypicalDay
import java.time.ZoneOffset
import java.time.ZonedDateTime


data class PolarFirstTimeUseConfig(
    val gender: Gender, // MALE or FEMALE
    val birthDate: Date, // String
    val height: Float, // cm, valid range [90-240]
    val weight: Float, // kg, valid range [15-300]
    val maxHeartRate: Int, // bpm, valid range [100-240]
    val vo2Max: Int, // valid Range: [10-95] ml/kg/min]
    val restingHeartRate: Int, // bpm, valid range [20-120]
    val trainingBackground: Int, // valid values [10, 20, 30, 40, 50, 60]
    val deviceTime: String, // ISO 8601 DateTime String
    val typicalDay: TypicalDay, // One of [TypicalDay] values
    val sleepGoalMinutes: Int // Minutes, valid range [300-660]
) {
    enum class Gender {
        MALE, FEMALE;
    }

    enum class TypicalDay(val index: Int) {
        MOSTLY_SITTING(1),
        MOSTLY_STANDING(2),
        MOSTLY_MOVING(3);
    }
    init {
        require(height in HEIGHT_MIN..HEIGHT_MAX) { "Height must be between $HEIGHT_MIN and $HEIGHT_MAX cm" }
        require(weight in WEIGHT_MIN..WEIGHT_MAX) { "Weight must be between $WEIGHT_MIN and $WEIGHT_MAX kg" }
        require(maxHeartRate in MAX_HEART_RATE_MIN..MAX_HEART_RATE_MAX) { "Max heart rate must be between $MAX_HEART_RATE_MIN and $MAX_HEART_RATE_MAX bpm" }
        require(restingHeartRate in RESTING_HEART_RATE_MIN..RESTING_HEART_RATE_MAX) { "Resting heart rate must be between $RESTING_HEART_RATE_MIN and $RESTING_HEART_RATE_MAX bpm" }
        require(vo2Max in VO2_MAX_MIN..VO2_MAX_MAX) { "VO2 max must be between $VO2_MAX_MIN and $VO2_MAX_MAX" }
        require(trainingBackground in TRAINING_BACKGROUND_VALUES) { "Training background must be one of the following values: ${TRAINING_BACKGROUND_VALUES.joinToString()}" }
        require(sleepGoalMinutes in SLEEP_GOAL_RANGE_MINUTES) { "Sleep goal must be between ${SLEEP_GOAL_RANGE_MINUTES.first} and ${SLEEP_GOAL_RANGE_MINUTES.last} minutes" }
    }

    companion object {
        const val HEIGHT_MIN = 90.0f
        const val HEIGHT_MAX = 240.0f
        const val WEIGHT_MIN = 15.0f
        const val WEIGHT_MAX = 300.0f
        const val MAX_HEART_RATE_MIN = 100
        const val MAX_HEART_RATE_MAX = 240
        const val RESTING_HEART_RATE_MIN = 20
        const val RESTING_HEART_RATE_MAX = 120
        const val VO2_MAX_MIN = 10
        const val VO2_MAX_MAX = 95
        val TRAINING_BACKGROUND_VALUES = listOf(10, 20, 30, 40, 50, 60)
        val SLEEP_GOAL_RANGE_MINUTES = 300..660
        const val FTU_CONFIG_FILENAME = "/U/0/S/PHYSDATA.BPB"
    }
}

    fun PolarFirstTimeUseConfig.toProto(): PhysData.PbUserPhysData {
    val deviceTimeUtc = ZonedDateTime.parse(deviceTime, DateTimeFormatter.ISO_DATE_TIME)
            .withZoneSameInstant(ZoneOffset.UTC)
    val lastModified = Types.PbSystemDateTime.newBuilder()
            .setDate(Types.PbDate.newBuilder()
                    .setYear(deviceTimeUtc.year)
                    .setMonth(deviceTimeUtc.monthValue)
                    .setDay(deviceTimeUtc.dayOfMonth)
                    .build())
            .setTime(Types.PbTime.newBuilder()
                    .setHour(deviceTimeUtc.hour)
                    .setMinute(deviceTimeUtc.minute)
                    .setSeconds(deviceTimeUtc.second)
                    .build())
            .setTrusted(true)
            .build()

        val birthdayParsed = Calendar.getInstance().apply { time = birthDate }
        val birthday = PhysData.PbUserBirthday.newBuilder().apply {
            setValue(Types.PbDate.newBuilder()
                .setYear(birthdayParsed.get(Calendar.YEAR))
                .setMonth(birthdayParsed.get(Calendar.MONTH) + 1)
                .setDay(birthdayParsed.get(Calendar.DAY_OF_MONTH))
                .build())
            setLastModified(lastModified)
        }.build()

        val gender = PhysData.PbUserGender.newBuilder().apply {
            setValue(when (gender) {
                PolarFirstTimeUseConfig.Gender.MALE -> PhysData.PbUserGender.Gender.MALE
                PolarFirstTimeUseConfig.Gender.FEMALE -> PhysData.PbUserGender.Gender.FEMALE
            })
            setLastModified(lastModified)
        }.build()

        val weightBuilder = PhysData.PbUserWeight.newBuilder().apply {
            setValue(weight)
            setLastModified(lastModified)
        }.build()

        val heightBuilder = PhysData.PbUserHeight.newBuilder().apply {
            setValue(height)
            setLastModified(lastModified)
        }.build()

        val maxHeartRateBuilder = PhysData.PbUserHrAttribute.newBuilder().apply {
            setValue(maxHeartRate)
            setLastModified(lastModified)
        }.build()

        val restingHeartRateBuilder = PhysData.PbUserHrAttribute.newBuilder().apply {
            setValue(restingHeartRate)
            setLastModified(lastModified)
        }.build()

        val trainingBackgroundBuilder = PhysData.PbUserTrainingBackground.newBuilder().apply {
            setValue(PhysData.PbUserTrainingBackground.TrainingBackground.forNumber(trainingBackground))
            setLastModified(lastModified)
        }.build()

        val vo2MaxBuilder = PhysData.PbUserVo2Max.newBuilder().apply {
            setValue(vo2Max) // Range: [10-95] ml/kg/min
            setLastModified(lastModified)
        }.build()

        val typicalDay = PhysData.PbUserTypicalDay.newBuilder()
                .setValue(TypicalDay.forNumber(typicalDay.index))
                .setLastModified(lastModified)
                .build()

        val sleepGoal = PhysData.PbSleepGoal.newBuilder()
                .setSleepGoalMinutes(sleepGoalMinutes)
                .setLastModified(lastModified)
                .build()

        return PhysData.PbUserPhysData.newBuilder().apply {
            setBirthday(birthday)
            setGender(gender)
            setWeight(weightBuilder)
            setHeight(heightBuilder)
            setMaximumHeartrate(maxHeartRateBuilder)
            setRestingHeartrate(restingHeartRateBuilder)
            setTrainingBackground(trainingBackgroundBuilder)
            setVo2Max(vo2MaxBuilder)
            setTypicalDay(typicalDay)
            setSleepGoal(sleepGoal)
            setLastModified(lastModified)
        }.build()
    }


