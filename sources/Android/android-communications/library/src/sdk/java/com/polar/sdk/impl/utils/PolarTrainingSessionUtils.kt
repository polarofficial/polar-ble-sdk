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
import fi.polar.remote.representation.protobuf.ExerciseSamples
import fi.polar.remote.representation.protobuf.ExerciseSamples2
import fi.polar.remote.representation.protobuf.Training
import fi.polar.remote.representation.protobuf.TrainingSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import protocol.PftpRequest
import protocol.PftpResponse
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.GZIPInputStream

private const val ARABICA_USER_ROOT_FOLDER = "/U/0/"
private const val TAG = "PolarTrainingSessionUtils"

internal object PolarTrainingSessionUtils {

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ENGLISH)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ENGLISH)

    fun getTrainingSessionReferences(
        client: BlePsFtpClient,
        fromDate: LocalDate? = null,
        toDate: LocalDate? = null
    ): Flow<PolarTrainingSessionReference> = flow {
        BleLogger.d(TAG, "getTrainingSessions: fromDate=$fromDate, toDate=$toDate")

        val startDateInt = fromDate?.let { dateFormatter.format(it).toInt() } ?: Int.MIN_VALUE
        val endDateInt = toDate?.let { dateFormatter.format(it).toInt() } ?: Int.MAX_VALUE

        val references = mutableListOf<PolarTrainingSessionReference>()
        val trainingSessionSummaryPaths = hashSetOf<String>()

        PolarFileUtils.fetchRecursively(
            client = client,
            path = ARABICA_USER_ROOT_FOLDER,
            condition = PolarFileUtils.FetchRecursiveCondition { name ->
                name.matches(Regex("^\\d{8}/$")) ||
                        name.matches(Regex("^\\d{6}/$")) ||
                        name.matches(Regex("^\\d+/$")) ||
                        name.endsWith(".BPB") ||
                        name.endsWith(".GZB") ||
                        name == "E/"
            },
            tag = TAG,
            recurseDeep = true
        ).collect { (path, fileSize) ->
            BleLogger.d(TAG, "path: $path, size: $fileSize bytes")
            val dataType =
                PolarTrainingSessionDataTypes.entries.firstOrNull { path.endsWith(it.deviceFileName) }
                    ?: PolarExerciseDataTypes.entries.firstOrNull { path.endsWith(it.deviceFileName) }

            if (dataType != null) {
                BleLogger.d(TAG, "Data type matched: $dataType, file size: $fileSize bytes")
                val match = Regex("/U/0/(\\d{8})/E/(\\d{6})(?:/(\\d+))?/[^/]+$").find(path)
                val dateStr = match?.groups?.get(1)?.value
                val timeStr = match?.groups?.get(2)?.value
                val exerciseIndex = match?.groups?.get(3)?.value?.toIntOrNull()
                val dateInt = dateStr?.toIntOrNull()

                if (dateStr != null && timeStr != null && dateInt != null && dateInt in startDateInt..endDateInt) {
                    val date = LocalDate.parse("$dateStr$timeStr", dateTimeFormatter)
                    BleLogger.d(TAG, "Parsed date: $date")
                    val existingReference = references.find { it.date == date }

                    if (dataType is PolarTrainingSessionDataTypes) {
                        trainingSessionSummaryPaths.add(path)
                        if (existingReference != null) {
                            if (!existingReference.trainingDataTypes.contains(dataType)) {
                                val updatedReference = existingReference.copy(
                                    trainingDataTypes = existingReference.trainingDataTypes + dataType,
                                    fileSize = existingReference.fileSize + fileSize
                                )
                                references[references.indexOf(existingReference)] = updatedReference
                            }
                        } else {
                            references.add(
                                PolarTrainingSessionReference(
                                    date = date,
                                    path = path,
                                    trainingDataTypes = listOf(dataType),
                                    exercises = emptyList(),
                                    fileSize = fileSize
                                )
                            )
                        }
                    } else if (dataType is PolarExerciseDataTypes) {
                        val exIndex = exerciseIndex ?: 0
                        val fileName = path.substringAfterLast("/")
                        val summaryPrefix = "/U/0/$dateStr/E/$timeStr"
                        val possibleSummary = trainingSessionSummaryPaths.find { path.startsWith(summaryPrefix) }

                        if (possibleSummary != null) {
                            val newExercise = PolarExercise(
                                index = exIndex,
                                path = path,
                                exerciseDataTypes = listOf(dataType),
                                fileSizes = mapOf(fileName to fileSize)
                            )
                            if (existingReference != null) {
                                val updatedExercises = existingReference.exercises.toMutableList()
                                val existingExercise = updatedExercises.find { it.index == exIndex }
                                if (existingExercise != null) {
                                    val mergedExercise = existingExercise.copy(
                                        exerciseDataTypes = (existingExercise.exerciseDataTypes + dataType).distinct(),
                                        fileSizes = existingExercise.fileSizes + (fileName to fileSize)
                                    )
                                    updatedExercises[updatedExercises.indexOf(existingExercise)] = mergedExercise
                                } else {
                                    updatedExercises.add(newExercise)
                                }
                                val updatedReference = existingReference.copy(
                                    exercises = updatedExercises,
                                    fileSize = existingReference.fileSize + fileSize
                                )
                                references[references.indexOf(existingReference)] = updatedReference
                            } else {
                                references.add(
                                    PolarTrainingSessionReference(
                                        date = date,
                                        path = path,
                                        trainingDataTypes = emptyList(),
                                        exercises = listOf(newExercise),
                                        fileSize = fileSize
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        BleLogger.d(TAG, "Collected ${references.size} training session references:")
        references.forEachIndexed { index, ref ->
            BleLogger.d(TAG, "[$index] date=${ref.date}, totalSize=${ref.fileSize} bytes, path=${ref.path}")
            BleLogger.d(TAG, "  Training data types: ${ref.trainingDataTypes}")
            ref.exercises.forEach { exercise ->
                BleLogger.d(TAG, "  Exercise ${exercise.index}: ${exercise.fileSizes.values.sum()} bytes")
                exercise.fileSizes.forEach { (fileName, size) ->
                    BleLogger.d(TAG, "    - $fileName: $size bytes")
                }
            }
        }

        references.forEach { emit(it) }
    }

    suspend fun readTrainingSessionWithProgress(
        client: BlePsFtpClient,
        reference: PolarTrainingSessionReference
    ): PolarTrainingSession {
        val tsessOp = PftpRequest.PbPFtpOperation.newBuilder()
            .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
            .setPath(reference.path)
            .build()

        val response = client.request(tsessOp.toByteArray())
        val sessionSummary = TrainingSession.PbTrainingSession.parseFrom(response.toByteArray())
        BleLogger.d(TAG, "Session summary received, processing ${reference.exercises.size} exercises")

        val exercises = reference.exercises.map { fetchExerciseData(client, it) }
        BleLogger.d(TAG, "All exercises combined: ${exercises.size}")
        return PolarTrainingSession(reference, sessionSummary, exercises)
    }

    private suspend fun fetchExerciseData(
        client: BlePsFtpClient,
        exercise: PolarExercise
    ): PolarExercise {
        BleLogger.d(TAG, "Fetching exercise ${exercise.index} data, path: ${exercise.path}")
        val basePath = exercise.path.substringBeforeLast("/")

        val results = exercise.exerciseDataTypes.map { dataType ->
            val filePath = "$basePath/${dataType.deviceFileName}"
            BleLogger.d(TAG, "  Fetching file: $filePath")
            val operation = PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                .setPath(filePath)
                .build()
            val data = try {
                val fileResponse = client.request(operation.toByteArray())
                val raw = fileResponse.toByteArray()
                if (filePath.endsWith(".GZB")) {
                    BleLogger.d(TAG, "Unzipping: ${dataType.deviceFileName}")
                    unzipData(raw)
                } else raw
            } catch (e: Exception) {
                BleLogger.e(TAG, "Failed to fetch ${dataType.deviceFileName}: ${e.message}")
                ByteArray(0)
            }
            BleLogger.d(TAG, "${dataType.deviceFileName} received: ${data.size} bytes")
            Pair(dataType, data)
        }

        return parseExerciseData(exercise, results)
    }

    private fun unzipData(data: ByteArray): ByteArray {
        return try {
            ByteArrayInputStream(data).use { bs ->
                GZIPInputStream(bs).use { gz -> gz.readBytes() }
            }
        } catch (e: Exception) {
            BleLogger.e(TAG, "Failed to unzip data: ${e.message}")
            data
        }
    }

    private fun parseExerciseData(
        exercise: PolarExercise,
        results: List<Pair<PolarExerciseDataTypes, ByteArray>>
    ): PolarExercise {
        var summary: Training.PbExerciseBase? = null
        var route: ExerciseRouteSamples.PbExerciseRouteSamples? = null
        var routeAdv: ExerciseRouteSamples2.PbExerciseRouteSamples2? = null
        var samples: ExerciseSamples.PbExerciseSamples? = null
        var samplesAdv: ExerciseSamples2.PbExerciseSamples2? = null

        for ((type, data) in results) {
            if (data.isEmpty()) continue
            try {
                when (type) {
                    PolarExerciseDataTypes.EXERCISE_SUMMARY -> summary = Training.PbExerciseBase.parseFrom(data)
                    PolarExerciseDataTypes.ROUTE, PolarExerciseDataTypes.ROUTE_GZIP ->
                        route = ExerciseRouteSamples.PbExerciseRouteSamples.parseFrom(data)
                    PolarExerciseDataTypes.ROUTE_ADVANCED_FORMAT, PolarExerciseDataTypes.ROUTE_ADVANCED_FORMAT_GZIP ->
                        routeAdv = ExerciseRouteSamples2.PbExerciseRouteSamples2.parseFrom(data)
                    PolarExerciseDataTypes.SAMPLES, PolarExerciseDataTypes.SAMPLES_GZIP ->
                        samples = ExerciseSamples.PbExerciseSamples.parseFrom(data)
                    PolarExerciseDataTypes.SAMPLES_ADVANCED_FORMAT_GZIP ->
                        samplesAdv = ExerciseSamples2.PbExerciseSamples2.parseFrom(data)
                }
            } catch (e: Exception) {
                BleLogger.e(TAG, "  Failed to parse $type: ${e.message}")
            }
        }

        return exercise.copy(
            exerciseSummary = summary,
            route = route,
            routeAdvanced = routeAdv,
            samples = samples,
            samplesAdvanced = samplesAdv
        )
    }

    suspend fun readTrainingSession(
        client: BlePsFtpClient,
        reference: PolarTrainingSessionReference
    ): PolarTrainingSession = readTrainingSessionWithProgress(client, reference)

    suspend fun deleteTrainingSession(client: BlePsFtpClient, reference: PolarTrainingSessionReference) {
        val components = reference.path.split("/").toTypedArray()
        val exerciseParent = ARABICA_USER_ROOT_FOLDER + components[3] + "/E/"

        val listOp = PftpRequest.PbPFtpOperation.newBuilder()
            .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
            .setPath(exerciseParent)
            .build()

        try {
            val listResponse = client.request(listOp.toByteArray())
            val directory = PftpResponse.PbPFtpDirectory.parseFrom(listResponse.toByteArray())
            val removePath = if (directory.entriesCount <= 1) {
                "/U/0/${components[3]}/E/"
            } else {
                "/U/0/${components[3]}/E/${components[5]}/"
            }
            val removeOp = PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.REMOVE)
                .setPath(removePath)
                .build()
            client.request(removeOp.toByteArray())
            BleLogger.d(TAG, "Deleted training session at $removePath")
        } catch (throwable: Throwable) {
            BleLogger.e(TAG, "Failed to delete: ${throwable.message}")
            throw throwable
        }
    }
}