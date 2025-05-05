package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.trainingsession.PolarExercise
import com.polar.sdk.api.model.trainingsession.PolarExerciseDataTypes
import com.polar.sdk.api.model.trainingsession.PolarExerciseSummary
import com.polar.sdk.api.model.trainingsession.PolarTrainingSession
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionDataTypes
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionReference
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionSummary
import fi.polar.remote.representation.protobuf.Training
import fi.polar.remote.representation.protobuf.TrainingSession
import fi.polar.remote.representation.protobuf.Types
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import protocol.PftpRequest
import protocol.PftpResponse
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
                    val match = Regex("/U/0/(\\d{8})/E/(\\d{6})(?:/(\\d+)/)?/").find(path)
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
                                val updatedReference = existingReference.copy(
                                    trainingDataTypes = existingReference.trainingDataTypes + dataType
                                )
                                refs[refs.indexOf(existingReference)] = updatedReference
                            } else {
                                val newReference = PolarTrainingSessionReference(
                                    date = date,
                                    path = path,
                                    trainingDataTypes = listOf(dataType),
                                    exercises = emptyList()
                                )
                                refs.add(newReference)
                            }
                        } else if (dataType is PolarExerciseDataTypes) {
                            val exercise = PolarExercise(
                                index = exerciseIndex ?: 0,
                                path = path,
                                exerciseDataTypes = listOf(dataType)
                            )

                            if (existingReference != null) {
                                val updatedReference = existingReference.copy(
                                    exercises = existingReference.exercises + exercise
                                )
                                references[references.indexOf(existingReference)] = updatedReference
                            } else {
                                val newReference = PolarTrainingSessionReference(
                                    date = date,
                                    path = path,
                                    trainingDataTypes = emptyList(),
                                    exercises = listOf(exercise)
                                )
                                references.add(newReference)
                            }
                        }
                    }
                }
            }
            .flatMapPublisher { refs ->
                Flowable.fromIterable(refs)
            }
    }

    fun readTrainingSession(client: BlePsFtpClient, reference: PolarTrainingSessionReference): Single<PolarTrainingSession> {
        BleLogger.d(TAG, "readTrainingSession: reading from $reference.path")

        return Single.create { emitter ->
            val disposable = client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(reference.path)
                    .build()
                    .toByteArray()
            ).flatMap { response ->
                try {
                    val proto = TrainingSession.PbTrainingSession.parseFrom(response.toByteArray())
                    val summary = PolarTrainingSessionSummary.fromProto(proto)

                    Flowable.fromIterable(reference.exercises)
                        .flatMapSingle { exercise ->
                            client.request(
                                PftpRequest.PbPFtpOperation.newBuilder()
                                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                                    .setPath(exercise.path)
                                    .build()
                                    .toByteArray()
                            ).map { exerciseResponse ->
                                val exerciseProto = Training.PbExerciseBase.parseFrom(exerciseResponse.toByteArray())
                                val exerciseSummary = PolarExerciseSummary.fromProto(exerciseProto)

                                PolarExercise(
                                    index = exercise.index,
                                    path = exercise.path,
                                    exerciseDataTypes = exercise.exerciseDataTypes,
                                    exerciseSummary = exerciseSummary
                                ).also {
                                    BleLogger.d(TAG, "Parsed PolarExerciseSummary: $exerciseSummary")
                                }
                            }
                        }
                        .toList()
                        .map { exercises ->
                            PolarTrainingSession(
                                reference = reference,
                                sessionSummary = summary,
                                exercises = exercises
                            )
                        }
                } catch (e: Exception) {
                    Single.error<PolarTrainingSession>(e)
                }
            }.subscribe(
                { polarTrainingSession ->
                    emitter.onSuccess(polarTrainingSession)
                },
                { error ->
                    emitter.onError(error)
                }
            )
            emitter.setDisposable(disposable)
        }
    }

    fun parseDateTime(dateTime: Types.PbLocalDateTime): Date {
        val calendar = Calendar.getInstance()

        calendar.set(Calendar.YEAR, dateTime.date.year)
        calendar.set(Calendar.MONTH, dateTime.date.month - 1)
        calendar.set(Calendar.DAY_OF_MONTH, dateTime.date.day)
        calendar.set(Calendar.HOUR_OF_DAY, dateTime.time.hour)
        calendar.set(Calendar.MINUTE, dateTime.time.minute)
        calendar.set(Calendar.SECOND, dateTime.time.seconds)
        calendar.set(Calendar.MILLISECOND, dateTime.time.millis)
        return calendar.time
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