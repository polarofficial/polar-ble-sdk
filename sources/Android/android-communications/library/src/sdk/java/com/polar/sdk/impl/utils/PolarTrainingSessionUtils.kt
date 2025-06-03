package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import fi.polar.remote.representation.protobuf.ExerciseRouteSamples
import fi.polar.remote.representation.protobuf.ExerciseRouteSamples2
import com.polar.sdk.api.model.trainingsession.PolarExercise
import com.polar.sdk.api.model.trainingsession.PolarExerciseDataTypes
import com.polar.sdk.api.model.trainingsession.PolarTrainingSession
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionDataTypes
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionReference
import fi.polar.remote.representation.protobuf.Training
import fi.polar.remote.representation.protobuf.TrainingSession
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import protocol.PftpRequest
import protocol.PftpResponse
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream

private const val ARABICA_USER_ROOT_FOLDER = "/U/0/"
private const val TAG = "PolarTrainingSessionUtils"

internal object PolarTrainingSessionUtils {

    private val dateFormatter = SimpleDateFormat("yyyyMMdd", Locale.ENGLISH)
    private val dateTimeFormatter = SimpleDateFormat("yyyyMMddHHmmss", Locale.ENGLISH)

    fun getTrainingSessionReferences(
        client: BlePsFtpClient,
        fromDate: Date? = null,
        toDate: Date? = null
    ): Flowable<PolarTrainingSessionReference> {
        BleLogger.d(TAG, "getTrainingSessions: fromDate=$fromDate, toDate=$toDate")

        val startDateInt = fromDate?.let { dateFormatter.format(it).toInt() } ?: Int.MIN_VALUE
        val endDateInt = toDate?.let { dateFormatter.format(it).toInt() } ?: Int.MAX_VALUE

        val references = mutableListOf<PolarTrainingSessionReference>()

        return fetchRecursively(
            client = client,
            path = ARABICA_USER_ROOT_FOLDER,
            condition = object : FetchRecursiveCondition {
                override fun include(name: String): Boolean {
                    return name.matches(Regex("^\\d{8}/$")) ||
                            name.matches(Regex("^\\d{6}/$")) ||
                            name.matches(Regex("^\\d+/$")) ||
                            name.endsWith(".BPB") ||
                            name.endsWith(".GZB") ||
                            name == "E/"
                }
            }
        )
            .collectInto(references) { refs, (path, _) ->
                BleLogger.d(TAG, "path: $path")
                val dataType =
                    PolarTrainingSessionDataTypes.entries.firstOrNull { path.endsWith(it.deviceFileName) }
                        ?: PolarExerciseDataTypes.entries.firstOrNull { path.endsWith(it.deviceFileName) }

                if (dataType != null) {
                    BleLogger.d(TAG, "Data type matched: $dataType")
                    val match = Regex("/U/0/(\\d{8})/E/(\\d{6})(?:/(\\d+))?/[^/]+$").find(path)
                    val dateStr = match?.groups?.get(1)?.value
                    val timeStr = match?.groups?.get(2)?.value
                    val exerciseIndex = match?.groups?.get(3)?.value?.toIntOrNull()
                    val dateInt = dateStr?.toIntOrNull()

                    if (dateStr != null && timeStr != null && dateInt != null && dateInt in startDateInt..endDateInt) {
                        val date = dateTimeFormatter.parse("$dateStr$timeStr") ?: Date(0)
                        BleLogger.d(TAG, "Parsed date: $date")

                        val existingReference = refs.find { it.date == date }

                        if (dataType is PolarTrainingSessionDataTypes) {
                            if (existingReference != null) {
                                if (!existingReference.trainingDataTypes.contains(dataType)) {
                                    val updatedReference = existingReference.copy(
                                        trainingDataTypes = existingReference.trainingDataTypes + dataType
                                    )
                                    refs[refs.indexOf(existingReference)] = updatedReference
                                }
                            } else {
                                refs.add(
                                    PolarTrainingSessionReference(
                                        date = date,
                                        path = path,
                                        trainingDataTypes = listOf(dataType),
                                        exercises = emptyList()
                                    )
                                )
                            }
                        } else if (dataType is PolarExerciseDataTypes) {
                            val exIndex = exerciseIndex ?: 0
                            val newExercise = PolarExercise(
                                index = exIndex,
                                path = path,
                                exerciseDataTypes = listOf(dataType)
                            )

                            if (existingReference != null) {
                                val updatedExercises = existingReference.exercises.toMutableList()
                                val existingExercise = updatedExercises.find { it.index == exIndex }

                                if (existingExercise != null) {
                                    val mergedDataTypes = (existingExercise.exerciseDataTypes + dataType).distinct()
                                    val mergedExercise = existingExercise.copy(exerciseDataTypes = mergedDataTypes)
                                    updatedExercises[updatedExercises.indexOf(existingExercise)] = mergedExercise
                                } else {
                                    updatedExercises.add(newExercise)
                                }

                                val updatedReference = existingReference.copy(exercises = updatedExercises)
                                refs[refs.indexOf(existingReference)] = updatedReference
                            } else {
                                refs.add(
                                    PolarTrainingSessionReference(
                                        date = date,
                                        path = path,
                                        trainingDataTypes = emptyList(),
                                        exercises = listOf(newExercise)
                                    )
                                )
                            }
                        }
                    }
                }
            }
            .doOnSuccess { refs ->
                BleLogger.d(TAG, "Collected ${refs.size} training session references:")
                refs.forEachIndexed { index, ref ->
                    BleLogger.d(TAG, "[$index] $ref")
                }
            }
            .flatMapPublisher { refs ->
                Flowable.fromIterable(refs)
            }
    }


    fun readTrainingSession(
        client: BlePsFtpClient,
        reference: PolarTrainingSessionReference
    ): Single<PolarTrainingSession> {
        BleLogger.d(TAG, "readTrainingSession: reading from $reference.path")

        val sessionSummarySingle: Single<TrainingSession.PbTrainingSession> =
            if (reference.trainingDataTypes.contains(PolarTrainingSessionDataTypes.TRAINING_SESSION_SUMMARY)) {
                client.request(
                    PftpRequest.PbPFtpOperation.newBuilder()
                        .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                        .setPath(reference.path)
                        .build()
                        .toByteArray()
                ).map { response ->
                    TrainingSession.PbTrainingSession.parseFrom(response.toByteArray())
                }.onErrorReturn { error ->
                    BleLogger.e(TAG, "Failed to load session summary: ${error.message}")
                    TrainingSession.PbTrainingSession.getDefaultInstance()
                }
            } else {
                Single.just(TrainingSession.PbTrainingSession.getDefaultInstance())
            }

        val exercisesSingle = Observable.fromIterable(reference.exercises)
            .concatMapSingle { exercise ->
                val basePath = exercise.path.substringBeforeLast("/")
                val dataTypeSingles = exercise.exerciseDataTypes.mapNotNull { dataType ->
                    when (dataType) {
                        PolarExerciseDataTypes.EXERCISE_SUMMARY -> {
                            val path = "$basePath/${dataType.deviceFileName}"
                            client.request(
                                PftpRequest.PbPFtpOperation.newBuilder()
                                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                                    .setPath(path)
                                    .build()
                                    .toByteArray()
                            ).map { resp ->
                                Training.PbExerciseBase.parseFrom(resp.toByteArray())
                            }.onErrorReturn { error ->
                                BleLogger.e(TAG, "Error loading ${dataType.deviceFileName}: ${error.message}")
                                Training.PbExerciseBase.getDefaultInstance()
                            }.map { dataType to it }
                        }

                        PolarExerciseDataTypes.ROUTE, PolarExerciseDataTypes.ROUTE_GZIP -> {
                            val path = "$basePath/${dataType.deviceFileName}"
                            client.request(
                                PftpRequest.PbPFtpOperation.newBuilder()
                                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                                    .setPath(path)
                                    .build()
                                    .toByteArray()
                            ).map { resp ->
                                val bytes = if (dataType == PolarExerciseDataTypes.ROUTE_GZIP)
                                    GZIPInputStream(resp.toByteArray().inputStream()).readBytes()
                                else resp.toByteArray()
                                ExerciseRouteSamples.PbExerciseRouteSamples.parseFrom(bytes)
                            }.onErrorReturn { error ->
                                BleLogger.e(TAG, "Error loading ${dataType.deviceFileName}: ${error.message}")
                                ExerciseRouteSamples.PbExerciseRouteSamples.getDefaultInstance()
                            }.map { dataType to it }
                        }

                        PolarExerciseDataTypes.ROUTE_ADVANCED_FORMAT, PolarExerciseDataTypes.ROUTE_ADVANCED_FORMAT_GZIP -> {
                            val path = "$basePath/${dataType.deviceFileName}"
                            client.request(
                                PftpRequest.PbPFtpOperation.newBuilder()
                                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                                    .setPath(path)
                                    .build()
                                    .toByteArray()
                            ).map { resp ->
                                val bytes = if (dataType == PolarExerciseDataTypes.ROUTE_ADVANCED_FORMAT_GZIP)
                                    GZIPInputStream(resp.toByteArray().inputStream()).readBytes()
                                else resp.toByteArray()
                                ExerciseRouteSamples2.PbExerciseRouteSamples2.parseFrom(bytes)
                            }.onErrorReturn { error ->
                                BleLogger.e(TAG, "Error loading ${dataType.deviceFileName}: ${error.message}")
                                ExerciseRouteSamples2.PbExerciseRouteSamples2.getDefaultInstance()
                            }.map { dataType to it }
                        }
                    }
                }

                Single.zip(dataTypeSingles) { results ->
                    var summary: Training.PbExerciseBase? = null
                    var route: ExerciseRouteSamples.PbExerciseRouteSamples? = null
                    var routeAdv: ExerciseRouteSamples2.PbExerciseRouteSamples2? = null

                    for (result in results.filterIsInstance<Pair<*, *>>()) {
                        when (result.first) {
                            PolarExerciseDataTypes.EXERCISE_SUMMARY -> {
                                val s = result.second as Training.PbExerciseBase
                                summary = if (s == Training.PbExerciseBase.getDefaultInstance()) null else s
                            }
                            PolarExerciseDataTypes.ROUTE,
                            PolarExerciseDataTypes.ROUTE_GZIP -> {
                                val r = result.second as ExerciseRouteSamples.PbExerciseRouteSamples
                                route = if (r == ExerciseRouteSamples.PbExerciseRouteSamples.getDefaultInstance()) null else r
                            }
                            PolarExerciseDataTypes.ROUTE_ADVANCED_FORMAT,
                            PolarExerciseDataTypes.ROUTE_ADVANCED_FORMAT_GZIP -> {
                                val rAdv = result.second as ExerciseRouteSamples2.PbExerciseRouteSamples2
                                routeAdv = if (rAdv == ExerciseRouteSamples2.PbExerciseRouteSamples2.getDefaultInstance()) null else rAdv
                            }
                        }
                    }

                    PolarExercise(
                        index = exercise.index,
                        path = exercise.path,
                        exerciseDataTypes = exercise.exerciseDataTypes,
                        exerciseSummary = summary,
                        route = route,
                        routeAdvanced = routeAdv
                    )
                }
            }
            .toList()

        return Single.zip(
            sessionSummarySingle,
            exercisesSingle
        ) { sessionSummary, exercises ->
            val sessionSummaryNullable = if (sessionSummary == TrainingSession.PbTrainingSession.getDefaultInstance()) null else sessionSummary

            PolarTrainingSession(
                reference = reference,
                sessionSummary = sessionSummaryNullable,
                exercises = exercises
            )
        }
    }

    private fun fetchRecursively(
        client: BlePsFtpClient,
        path: String,
        condition: FetchRecursiveCondition
    ): Flowable<Pair<String, Long>> {
        BleLogger.d(TAG, "fetchRecursively: Starting fetch for path: $path")

        val builder = PftpRequest.PbPFtpOperation.newBuilder()
        builder.command = PftpRequest.PbPFtpOperation.Command.GET
        builder.path = path

        return client.request(builder.build().toByteArray())
            .toFlowable()
            .flatMap { byteArrayOutputStream ->

                val dir = PftpResponse.PbPFtpDirectory.parseFrom(byteArrayOutputStream.toByteArray())
                val entries = mutableMapOf<String, Long>()

                for (entry in dir.entriesList) {
                    BleLogger.d(TAG, "fetchRecursively: Found entry, name: ${entry.name}, size: ${entry.size}")
                    if (condition.include(entry.name)) {
                        entries[path + entry.name] = entry.size
                    }
                }

                if (entries.isNotEmpty()) {
                    return@flatMap Flowable.fromIterable(entries.toList())
                        .flatMap { entry ->
                            if (entry.first.endsWith("/")) {
                                fetchRecursively(client, entry.first, condition)
                            } else {
                                Flowable.just(entry)
                            }
                        }
                }

                BleLogger.d(TAG, "fetchRecursively: No entries found for path: $path")
                Flowable.empty()
            }
            .doOnError { error ->
                BleLogger.e(TAG, "fetchRecursively: Error occurred for path: $path, error: $error")
            }
    }

    interface FetchRecursiveCondition {
        fun include(name: String): Boolean
    }
}