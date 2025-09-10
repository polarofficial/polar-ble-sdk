package com.polar.sdk.api.model.trainingsession

import fi.polar.remote.representation.protobuf.ExerciseRouteSamples
import fi.polar.remote.representation.protobuf.ExerciseRouteSamples2
import fi.polar.remote.representation.protobuf.ExerciseSamples
import fi.polar.remote.representation.protobuf.ExerciseSamples2
import fi.polar.remote.representation.protobuf.TrainingSession
import fi.polar.remote.representation.protobuf.Training
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
    val sessionSummary: TrainingSession.PbTrainingSession? = null,
    val exercises: List<PolarExercise> = emptyList()
)

data class PolarExercise(
    val index: Int,
    @Transient val path: String,
    val exerciseDataTypes: List<PolarExerciseDataTypes> = emptyList(),
    val exerciseSummary: Training.PbExerciseBase? = null,
    val route: ExerciseRouteSamples.PbExerciseRouteSamples? = null,
    val routeAdvanced: ExerciseRouteSamples2.PbExerciseRouteSamples2? = null,
    val samples: ExerciseSamples.PbExerciseSamples? = null,
    val samplesAdvanced: ExerciseSamples2.PbExerciseSamples2? = null
)

enum class PolarExerciseDataTypes(val deviceFileName: String) {
    EXERCISE_SUMMARY("BASE.BPB"),
    ROUTE("ROUTE.BPB"),
    ROUTE_GZIP("ROUTE.GZB"),
    ROUTE_ADVANCED_FORMAT("ROUTE2.BPB"),
    ROUTE_ADVANCED_FORMAT_GZIP("ROUTE2.GZB"),
    SAMPLES("SAMPLES.BPB"),
    SAMPLES_GZIP("SAMPLES.GZB"),
    SAMPLES_ADVANCED_FORMAT_GZIP("SAMPLES2.GZB"),
}