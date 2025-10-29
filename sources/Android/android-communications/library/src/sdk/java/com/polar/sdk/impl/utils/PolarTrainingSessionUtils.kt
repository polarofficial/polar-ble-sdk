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
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionProgress
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionFetchResult
import fi.polar.remote.representation.protobuf.ExerciseSamples
import fi.polar.remote.representation.protobuf.ExerciseSamples2
import fi.polar.remote.representation.protobuf.Training
import fi.polar.remote.representation.protobuf.TrainingSession
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import protocol.PftpRequest
import protocol.PftpResponse
import java.io.ByteArrayInputStream
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
            .collectInto(references) { refs, (path, fileSize) ->
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
                        val date = dateTimeFormatter.parse("$dateStr$timeStr") ?: Date(0)
                        BleLogger.d(TAG, "Parsed date: $date")

                        val existingReference = refs.find { it.date == date }

                        if (dataType is PolarTrainingSessionDataTypes) {
                            if (existingReference != null) {
                                if (!existingReference.trainingDataTypes.contains(dataType)) {
                                    val updatedReference = existingReference.copy(
                                        trainingDataTypes = existingReference.trainingDataTypes + dataType,
                                        fileSize = existingReference.fileSize + fileSize
                                    )
                                    refs[refs.indexOf(existingReference)] = updatedReference
                                }
                            } else {
                                refs.add(
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
                                    val mergedDataTypes = (existingExercise.exerciseDataTypes + dataType).distinct()
                                    val mergedFileSizes = existingExercise.fileSizes + (fileName to fileSize)
                                    val mergedExercise = existingExercise.copy(
                                        exerciseDataTypes = mergedDataTypes,
                                        fileSizes = mergedFileSizes
                                    )
                                    updatedExercises[updatedExercises.indexOf(existingExercise)] = mergedExercise
                                } else {
                                    updatedExercises.add(newExercise)
                                }

                                val updatedReference = existingReference.copy(
                                    exercises = updatedExercises,
                                    fileSize = existingReference.fileSize + fileSize
                                )
                                refs[refs.indexOf(existingReference)] = updatedReference
                            } else {
                                refs.add(
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
            .doOnSuccess { refs ->
                BleLogger.d(TAG, "Collected ${refs.size} training session references:")
                refs.forEachIndexed { index, ref ->
                    BleLogger.d(TAG, "[$index] date=${ref.date}, totalSize=${ref.fileSize} bytes, path=${ref.path}")
                    BleLogger.d(TAG, "  Training data types: ${ref.trainingDataTypes}")
                    ref.exercises.forEach { exercise ->
                        val exerciseTotalSize = exercise.fileSizes.values.sum()
                        BleLogger.d(TAG, "  Exercise ${exercise.index}: $exerciseTotalSize bytes")
                        exercise.fileSizes.forEach { (fileName, size) ->
                            BleLogger.d(TAG, "    - $fileName: $size bytes")
                        }
                    }
                }
            }
            .flatMapPublisher { refs ->
                Flowable.fromIterable(refs)
            }
    }

    fun readTrainingSessionWithProgress(
        client: BlePsFtpClient,
        reference: PolarTrainingSessionReference
    ): Single<PolarTrainingSession> {

        return Single.create { emitter ->
            try {
                val tsessOp = PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(reference.path)
                    .build()

                val disposable = client.request(tsessOp.toByteArray())
                    .flatMap { response ->
                        val sessionSummary = TrainingSession.PbTrainingSession.parseFrom(response.toByteArray())
                        BleLogger.d(TAG, "Session summary received, processing ${reference.exercises.size} exercises")

                        val exerciseSingles = reference.exercises.map { exercise ->
                            fetchExerciseData(client, exercise)
                        }

                        if (exerciseSingles.isEmpty()) {
                            Single.just(PolarTrainingSession(reference, sessionSummary, emptyList()))
                        } else {
                            Single.zip(exerciseSingles) { exercisesArray ->
                                @Suppress("UNCHECKED_CAST")
                                val exercises = exercisesArray.toList() as List<PolarExercise>
                                BleLogger.d(TAG, "All exercises combined: ${exercises.size}")
                                PolarTrainingSession(reference, sessionSummary, exercises)
                            }
                        }
                    }
                    .subscribe(
                        { session ->
                            BleLogger.d(TAG, "Training session fetch completed successfully")
                            emitter.onSuccess(session)
                        },
                        { error ->
                            BleLogger.e(TAG, "Training session fetch failed: ${error.message}")
                            emitter.onError(error)
                        }
                    )

                emitter.setDisposable(disposable)

            } catch (e: Exception) {
                BleLogger.e(TAG, "Failed to start training session fetch: ${e.message}")
                emitter.onError(e)
            }
        }
    }

    private fun fetchExerciseData(
        client: BlePsFtpClient,
        exercise: PolarExercise
    ): Single<PolarExercise> {
        BleLogger.d(TAG, "Fetching exercise ${exercise.index} data, path: ${exercise.path}")

        val basePath = exercise.path.substringBeforeLast("/")

        val dataTypeRequests = exercise.exerciseDataTypes.map { dataType ->
            val fileName = dataType.deviceFileName
            val filePath = "$basePath/$fileName"

            BleLogger.d(TAG, "  Fetching file: $filePath")

            val operation = PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                .setPath(filePath)
                .build()

            client.request(operation.toByteArray())
                .map { fileResponse ->
                    val data = if (filePath.endsWith(".GZB")) {
                        BleLogger.d(TAG, "Unzipping: $fileName")
                        unzipData(fileResponse.toByteArray())
                    } else {
                        fileResponse.toByteArray()
                    }
                    BleLogger.d(TAG, "$fileName received: ${data.size} bytes")
                    Pair(dataType, data)
                }
                .onErrorReturn { error ->
                    BleLogger.e(TAG, "Failed to fetch $fileName: ${error.message}")
                    Pair(dataType, ByteArray(0))
                }
        }

        return if (dataTypeRequests.isEmpty()) {
            Single.just(exercise)
        } else {
            Single.zip(dataTypeRequests) { resultsArray ->
                val results = resultsArray.toList() as List<Pair<PolarExerciseDataTypes, ByteArray>>
                parseExerciseData(exercise, results)
            }
        }
    }

    private fun unzipData(data: ByteArray): ByteArray {
        return try {
            ByteArrayInputStream(data).use { byteStream ->
                GZIPInputStream(byteStream).use { gzipStream ->
                    gzipStream.readBytes()
                }
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
                    PolarExerciseDataTypes.EXERCISE_SUMMARY -> {
                        summary = Training.PbExerciseBase.parseFrom(data)
                    }
                    PolarExerciseDataTypes.ROUTE, PolarExerciseDataTypes.ROUTE_GZIP -> {
                        route = ExerciseRouteSamples.PbExerciseRouteSamples.parseFrom(data)
                    }
                    PolarExerciseDataTypes.ROUTE_ADVANCED_FORMAT, PolarExerciseDataTypes.ROUTE_ADVANCED_FORMAT_GZIP -> {
                        routeAdv = ExerciseRouteSamples2.PbExerciseRouteSamples2.parseFrom(data)
                    }
                    PolarExerciseDataTypes.SAMPLES, PolarExerciseDataTypes.SAMPLES_GZIP -> {
                        samples = ExerciseSamples.PbExerciseSamples.parseFrom(data)
                    }
                    PolarExerciseDataTypes.SAMPLES_ADVANCED_FORMAT_GZIP -> {
                        samplesAdv = ExerciseSamples2.PbExerciseSamples2.parseFrom(data)
                    }
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

    fun readTrainingSession(
        client: BlePsFtpClient,
        reference: PolarTrainingSessionReference
    ): Single<PolarTrainingSession> {
        return readTrainingSessionWithProgress(client, reference)
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