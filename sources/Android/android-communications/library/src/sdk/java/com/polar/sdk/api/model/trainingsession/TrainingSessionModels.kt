package com.polar.sdk.api.model.trainingsession

import com.google.protobuf.ByteString
import com.polar.sdk.impl.utils.PolarTimeUtils
import com.polar.sdk.impl.utils.PolarTrainingSessionUtils.parseDateTime
import fi.polar.remote.representation.protobuf.TrainingSession
import fi.polar.remote.representation.protobuf.Training
import java.util.Calendar
import java.util.Date

data class PolarTrainingSessionReference(
    val date: Date,
    val path: String,
    val trainingDataTypes: List<PolarTrainingSessionDataTypes>,
    val exercises: List<PolarExercise>
)

enum class PolarTrainingSessionDataTypes(val deviceFileName: String) {
    TRAINING_SESSION_SUMMARY("TSESS.BPB");
}

data class PolarTrainingSession(
    val reference: PolarTrainingSessionReference,
    val sessionSummary: PolarTrainingSessionSummary? = null,
    val exercises: List<PolarExercise> = emptyList()
)

data class PolarExercise(
    val index: Int,
    val path: String,
    val exerciseDataTypes: List<PolarExerciseDataTypes> = emptyList(),
    val exerciseSummary: PolarExerciseSummary? = null
)

enum class PolarExerciseDataTypes(val deviceFileName: String) {
    EXERCISE_SUMMARY("BASE.BPB")
}


data class PolarSessionHeartRateStatistics(
    val average: Int?,
    val maximum: Int?
)

data class PolarDuration(
    val hours: Int = 0,
    val minutes: Int = 0,
    val seconds: Int = 0,
    val millis: Int = 0
) {
    companion object {
        fun fromSeconds(totalSeconds: Int): PolarDuration {
            return PolarDuration(
                hours = totalSeconds / 3600,
                minutes = (totalSeconds % 3600) / 60,
                seconds = totalSeconds % 60,
                millis = 0
            )
        }
    }
}

data class PolarTrainingLoad(val load: Int?)
data class PolarExerciseFeedback(val feedback: String?)
data class PolarSportIdentifier(val id: Int?)
data class PolarCardioLoad(val activityLoad: Float?, val exerciseLoad: Float?)

data class PolarTrainingSessionSummary(
    val start: Date,
    val end: Date? = null,
    val exerciseCount: Int,
    val deviceId: String? = null,
    val modelName: String? = null,
    val duration: PolarDuration? = null,
    val distance: Float? = null,
    val calories: Int? = null,
    val heartRate: PolarSessionHeartRateStatistics? = null,
    val heartRateZoneDuration: List<PolarDuration>? = null,
    val trainingLoad: PolarTrainingLoad? = null,
    val sessionName: String? = null,
    val feeling: Float? = null,
    val note: String? = null,
    val place: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val benefit: PolarExerciseFeedback? = null,
    val sport: PolarSportIdentifier? = null,
    val cardioLoad: PolarCardioLoad? = null,
    val cardioLoadInterpretation: Int? = null,
    val muscleLoad: Float? = null,
    val muscleLoadInterpretation: Int? = null,
    val periodUuid: ByteString? = null,
    val startTrigger: TrainingStartTrigger? = null
) {
    enum class TrainingStartTrigger {
        MANUAL,
        AUTOMATIC_TRAINING_DETECTION
    }

    companion object {
        fun fromProto(proto: TrainingSession.PbTrainingSession): PolarTrainingSessionSummary {
            return PolarTrainingSessionSummary(
                start = parseDateTime(proto.start),
                end = proto.end?.let { parseDateTime(it) },
                exerciseCount = proto.exerciseCount,
                deviceId = proto.deviceId,
                modelName = proto.modelName,
                duration = proto.duration?.let {
                    PolarDuration(
                        hours = it.hours,
                        minutes = it.minutes,
                        seconds = it.seconds,
                        millis = it.millis
                    )
                },
                distance = proto.distance,
                calories = proto.calories,
                heartRate = proto.heartRate?.let {
                    PolarSessionHeartRateStatistics(
                        average = it.average,
                        maximum = it.maximum
                    )
                },
                heartRateZoneDuration = proto.heartRateZoneDurationList.map {
                    PolarDuration(seconds = it.seconds)
                },
                trainingLoad = proto.trainingLoad?.let {
                    PolarTrainingLoad(it.trainingLoadVal)
                },
                sessionName = proto.sessionName.text,
                feeling = proto.feeling,
                note = proto.note.text,
                place = proto.place.text,
                latitude = proto.latitude,
                longitude = proto.longitude,
                benefit = proto.benefit?.let {
                    PolarExerciseFeedback(it.name)
                },
                sport = proto.sport?.let {
                    PolarSportIdentifier(it.value.toInt())
                },
                cardioLoad = proto.cardioLoad?.let {
                    PolarCardioLoad(it.activityLoad, it.exerciseLoad)
                },
                cardioLoadInterpretation = proto.cardioLoadInterpretation,
                muscleLoad = proto.muscleLoad,
                muscleLoadInterpretation = proto.muscleLoadInterpretation,
                periodUuid = proto.periodUuid,
                startTrigger = proto.startTrigger?.let {
                    TrainingStartTrigger.valueOf(it.name)
                }
            )
        }
    }
}
data class PolarExerciseSummary(
    val start: Date,
    val duration: PolarDuration,
    val sport: PolarSportIdentifier,
    val distance: Float? = null,
    val calories: Int? = null,
    val trainingLoad: PolarTrainingLoad? = null,
    val availableSensorFeatures: List<String>? = null,
    val runningIndex: Int? = null,
    val ascent: Float? = null,
    val descent: Float? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val place: String? = null,
    val exerciseCounters: PolarExerciseCounters? = null,
    val walkingDistance: Float? = null,
    val walkingDuration: PolarDuration? = null,
    val accumulatedTorque: Int? = null,
    val cyclingPowerEnergy: Int? = null,
    val cardioLoad: PolarCardioLoad? = null,
    val cardioLoadInterpretation: Int? = null,
    val perceivedLoad: PolarPerceivedLoad? = null,
    val perceivedLoadInterpretation: Int? = null,
    val muscleLoad: Float? = null,
    val muscleLoadInterpretation: Int? = null,
    val lastModified: Date? = null
) {
    companion object {
        fun fromProto(proto: Training.PbExerciseBase): PolarExerciseSummary {
            return PolarExerciseSummary(
                start = parseDateTime(proto.start),
                duration = PolarDuration(
                    hours = proto.duration.hours,
                    minutes = proto.duration.minutes,
                    seconds = proto.duration.seconds,
                    millis = proto.duration.millis
                ),
                sport = PolarSportIdentifier(proto.sport.value.toInt()),
                distance = proto.distance,
                calories = proto.calories,
                trainingLoad = proto.trainingLoad?.let {
                    PolarTrainingLoad(it.trainingLoadVal)
                },
                availableSensorFeatures = proto.availableSensorFeaturesList.map { it.name },
                runningIndex = proto.runningIndex?.value,
                ascent = proto.ascent,
                descent = proto.descent,
                latitude = proto.latitude,
                longitude = proto.longitude,
                place = proto.place,
                exerciseCounters = proto.exerciseCounters?.let {
                    PolarExerciseCounters(sprintCount = it.sprintCount)
                },
                walkingDistance = proto.walkingDistance,
                walkingDuration = proto.walkingDuration?.let {
                    PolarDuration(
                        hours = it.hours,
                        minutes = it.minutes,
                        seconds = it.seconds,
                        millis = it.millis
                    )
                },
                accumulatedTorque = proto.accumulatedTorque,
                cyclingPowerEnergy = proto.cyclingPowerEnergy,
                cardioLoad = proto.cardioLoad?.let {
                    PolarCardioLoad(it.activityLoad, it.exerciseLoad)
                },
                cardioLoadInterpretation = proto.cardioLoadInterpretation,
                perceivedLoad = PolarPerceivedLoad(
                    sessionRpe = PolarSessionRpe.entries.getOrNull(proto.perceivedLoad.sessionRpe.ordinal),
                    duration = proto.perceivedLoad.duration?.let { seconds ->
                        PolarDuration.fromSeconds(seconds)
                    }),
                muscleLoad = proto.muscleLoad,
                muscleLoadInterpretation = proto.muscleLoadInterpretation,
                lastModified = proto.lastModified?.let { pbSystemDateTime ->
                    val calendar = Calendar.getInstance().apply {
                        set(Calendar.YEAR, pbSystemDateTime.date.year)
                        set(Calendar.MONTH, if (pbSystemDateTime.date.month in 1..12) pbSystemDateTime.date.month - 1 else 0) // Default to January if invalid
                        set(Calendar.DAY_OF_MONTH, pbSystemDateTime.date.day)
                        set(Calendar.HOUR_OF_DAY, pbSystemDateTime.time.hour)
                        set(Calendar.MINUTE, pbSystemDateTime.time.minute)
                        set(Calendar.SECOND, pbSystemDateTime.time.seconds)
                        set(Calendar.MILLISECOND, pbSystemDateTime.time.millis)
                    }
                    Date(calendar.timeInMillis)
                }
            )
        }
    }
}

data class PolarExerciseCounters(
    val sprintCount: Int? = null
)

data class PolarPerceivedLoad(
    val sessionRpe: PolarSessionRpe?,
    val duration: PolarDuration?
)

enum class PolarSessionRpe(val rpe: Int) {
    NONE(0),
    EASY(1),
    LIGHT(2),
    FAIRLY_BRISK(3),
    BRISK(4),
    MODERATE(5),
    FAIRLY_HARD(6),
    HARD(7),
    EXHAUSTING(8),
    EXTREME(9);
}