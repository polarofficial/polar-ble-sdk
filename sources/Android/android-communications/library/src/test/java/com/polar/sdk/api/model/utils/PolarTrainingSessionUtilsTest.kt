package com.polar.sdk.api.model.utils

import com.google.protobuf.ByteString
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionDataTypes
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionReference
import com.polar.sdk.impl.utils.PolarTrainingSessionUtils
import fi.polar.remote.representation.protobuf.Structures
import fi.polar.remote.representation.protobuf.Training
import fi.polar.remote.representation.protobuf.TrainingSession
import fi.polar.remote.representation.protobuf.Types
import com.polar.sdk.api.model.trainingsession.PolarExercise
import com.polar.sdk.api.model.trainingsession.PolarExerciseDataTypes
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.rxjava3.core.Single
import junit.framework.TestCase.assertEquals
import org.junit.Test
import protocol.PftpRequest
import protocol.PftpResponse.PbPFtpDirectory
import protocol.PftpResponse.PbPFtpEntry
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

class PolarTrainingSessionUtilsTest {

    private val dateTimeFormatter = SimpleDateFormat("yyyyMMddHHmmss", Locale.ENGLISH)

    @Test
    fun `getTrainingSessionReferences() should return all training session references`() {
        // Arrange
        val client = mockk<BlePsFtpClient>()

        val dateDirectories = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(
                    listOf(
                        PbPFtpEntry.newBuilder().setName("20250101/").setSize(8192L).build(),
                        PbPFtpEntry.newBuilder().setName("20250202/").setSize(8192L).build()
                    )
                ).build().writeTo(this)
        }

        val exerciseDirectory = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(
                    listOf(
                        PbPFtpEntry.newBuilder().setName("E/").setSize(4096L).build()
                    )
                ).build().writeTo(this)
        }

        val timeDirectory = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(
                    listOf(
                        PbPFtpEntry.newBuilder().setName("204507/").setSize(2048L).build()
                    )
                ).build().writeTo(this)
        }

        val trainingSessionSummaryEntry = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(
                    listOf(
                        PbPFtpEntry.newBuilder().setName("TSESS.BPB").setSize(1024L).build()
                    )
                ).build().writeTo(this)
        }

        val timeDirectory2 = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(
                    listOf(
                        PbPFtpEntry.newBuilder().setName("163020/").setSize(2048L).build()
                    )
                ).build().writeTo(this)
        }

        val exerciseIndexDirectory = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(
                    listOf(
                        PbPFtpEntry.newBuilder().setName("00/").setSize(1024L).build(),
                        PbPFtpEntry.newBuilder().setName("01/").setSize(1024L).build()
                    )
                ).build().writeTo(this)
        }

        val exerciseBaseEntry = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(
                    listOf(
                        PbPFtpEntry.newBuilder().setName("BASE.BPB").setSize(512L).build()
                    )
                ).build().writeTo(this)
        }

        val expectedReferences = listOf(
            dateTimeFormatter.parse("20250101204507")?.let {
                PolarTrainingSessionReference(
                    date = it,
                    path = "/U/0/20250101/E/204507/TSESS.BPB",
                    trainingDataTypes = listOf(PolarTrainingSessionDataTypes.TRAINING_SESSION_SUMMARY),
                    exercises = emptyList()
                )
            },
            dateTimeFormatter.parse("20250202163020")?.let {
                PolarTrainingSessionReference(
                    date = it,
                    path = "/U/0/20250202/E/163020/00/BASE.BPB",
                    trainingDataTypes = emptyList(),
                    exercises = listOf(
                        PolarExercise(
                            index = 0,
                            path = "/U/0/20250202/E/163020/00/BASE.BPB",
                            exerciseDataTypes = listOf(PolarExerciseDataTypes.EXERCISE_SUMMARY),
                            exerciseSummary = null
                        ),
                        PolarExercise(
                            index = 0,
                            path = "/U/0/20250202/E/163020/01/BASE.BPB",
                            exerciseDataTypes = listOf(PolarExerciseDataTypes.EXERCISE_SUMMARY),
                            exerciseSummary = null
                        )
                    )
                )
            }
        )

        every { client.request(any<ByteArray>()) } returns Single.just(dateDirectories) andThen
                Single.just(exerciseDirectory) andThen
                Single.just(timeDirectory) andThen
                Single.just(trainingSessionSummaryEntry) andThen
                Single.just(exerciseDirectory) andThen
                Single.just(timeDirectory2) andThen
                Single.just(exerciseIndexDirectory) andThen
                Single.just(exerciseBaseEntry) andThen
                Single.just(exerciseBaseEntry)

        // Act
        val testObserver = PolarTrainingSessionUtils.getTrainingSessionReferences(client).test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValueSequence(expectedReferences)

        verify {
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath("/U/0/")
                    .build()
                    .toByteArray()
            )
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath("/U/0/20250101/")
                    .build()
                    .toByteArray()
            )
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath("/U/0/20250101/E/")
                    .build()
                    .toByteArray()
            )
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath("/U/0/20250101/E/204507/")
                    .build()
                    .toByteArray()
            )
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath("/U/0/20250202/")
                    .build()
                    .toByteArray()
            )
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath("/U/0/20250202/E/")
                    .build()
                    .toByteArray()
            )
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath("/U/0/20250202/E/163020/")
                    .build()
                    .toByteArray()
            )
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath("/U/0/20250202/E/163020/00/")
                    .build()
                    .toByteArray()
            )
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath("/U/0/20250202/E/163020/01/")
                    .build()
                    .toByteArray()
            )
        }

        confirmVerified(client)
    }

    @Test
    fun `readTrainingSession() should return training session data`() {
        // Arrange
        val client = mockk<BlePsFtpClient>()
        val reference = PolarTrainingSessionReference(
            date = dateTimeFormatter.parse("20250101101200")!!,
            path = "/U/0/20250101/E/101200/TSESS.BPB",
            trainingDataTypes = listOf(PolarTrainingSessionDataTypes.TRAINING_SESSION_SUMMARY),
            exercises = listOf(
                PolarExercise(
                    index = 0,
                    path = "/U/0/20250101/E/101200/BASE.BPB",
                    exerciseDataTypes = listOf(PolarExerciseDataTypes.EXERCISE_SUMMARY)
                )
            )
        )

        val mockProto = TrainingSession.PbTrainingSession.newBuilder()
            .setStart(
                Types.PbLocalDateTime.newBuilder()
                    .setDate(Types.PbDate.newBuilder().setYear(2025).setMonth(1).setDay(1))
                    .setTime(Types.PbTime.newBuilder().setHour(10).setMinute(12).setSeconds(0))
                    .setOBSOLETETrusted(true)
            )
            .setEnd(
                Types.PbLocalDateTime.newBuilder()
                    .setDate(Types.PbDate.newBuilder().setYear(2025).setMonth(1).setDay(1))
                    .setTime(Types.PbTime.newBuilder().setHour(11).setMinute(12).setSeconds(0))
                    .setOBSOLETETrusted(true)
            )
            .setExerciseCount(1)
            .setDeviceId("123ABC")
            .setModelName("Polar 360")
            .setDuration(Types.PbDuration.newBuilder().setSeconds(3600))
            .setDistance(12.5f)
            .setCalories(400)
            .setHeartRate(
                TrainingSession.PbSessionHeartRateStatistics.newBuilder()
                    .setAverage(110)
                    .setMaximum(150)
            )
            .addHeartRateZoneDuration(Types.PbDuration.newBuilder().setSeconds(300))
            .setTrainingLoad(Structures.PbTrainingLoad.newBuilder().setTrainingLoadVal(50))
            .setSessionName(Structures.PbOneLineText.newBuilder().setText("Running"))
            .setFeeling(4.5f)
            .setNote(Structures.PbMultiLineText.newBuilder().setText("Note"))
            .setPlace(Structures.PbOneLineText.newBuilder().setText("Place"))
            .setLatitude(60.1699)
            .setLongitude(24.9384)
            .setBenefit(Types.PbExerciseFeedback.FEEDBACK_1)
            .setSport(Structures.PbSportIdentifier.newBuilder().setValue(3))
            .setCardioLoad(
                Types.PbCardioLoad.newBuilder().setExerciseLoad(100f).setActivityLoad(50f)
            )
            .setCardioLoadInterpretation(3)
            .setMuscleLoad(200.5f)
            .setMuscleLoadInterpretation(4)
            .setPeriodUuid(ByteString.copyFromUtf8("123e4567-e89b-12d3-a456-426614174000"))
            .setStartTrigger(TrainingSession.PbTrainingSession.PbTrainingStartTrigger.MANUAL)
            .build()

        val mockResponseContent = ByteArrayOutputStream().apply {
            mockProto.writeTo(this)
        }

        val exerciseBaseProto = Training.PbExerciseBase.newBuilder()
            .setStart(
                Types.PbLocalDateTime.newBuilder()
                    .setDate(Types.PbDate.newBuilder().setYear(2025).setMonth(1).setDay(1))
                    .setTime(Types.PbTime.newBuilder().setHour(10).setMinute(12).setSeconds(0))
                    .setOBSOLETETrusted(true)
            )
            .setDuration(Types.PbDuration.newBuilder().setSeconds(3600))
            .setSport(Structures.PbSportIdentifier.newBuilder().setValue(3))
            .setCalories(400)
            .setDistance(12.5f)
            .setTrainingLoad(Structures.PbTrainingLoad.newBuilder().setTrainingLoadVal(50))
            .addAllAvailableSensorFeatures(
                listOf(
                    Types.PbFeatureType.FEATURE_TYPE_HEART_RATE,
                    Types.PbFeatureType.FEATURE_TYPE_GPS_LOCATION
                )
            )
            .setRunningIndex(Structures.PbRunningIndex.newBuilder().setValue(55).build())
            .setAscent(100.5f)
            .setDescent(90.3f)
            .setLatitude(60.1699)
            .setLongitude(24.9384)
            .setPlace("Place")
            .setExerciseCounters(Training.PbExerciseCounters.newBuilder().setSprintCount(10))
            .setWalkingDistance(5.0f)
            .setWalkingDuration(Types.PbDuration.newBuilder().setSeconds(1800))
            .setAccumulatedTorque(150)
            .setCyclingPowerEnergy(200)
            .setCardioLoad(Types.PbCardioLoad.newBuilder().setExerciseLoad(100f).setActivityLoad(50f))
            .setCardioLoadInterpretation(3)
            .setPerceivedLoad(
                Types.PbPerceivedLoad.newBuilder()
                    .setSessionRpe(Types.PbSessionRPE.RPE_HARD)
                    .setDuration(3600)
            )
            .setPerceivedLoadInterpretation(2)
            .setMuscleLoad(200.5f)
            .setMuscleLoadInterpretation(4)
            .setLastModified(
                Types.PbSystemDateTime.newBuilder()
                    .setDate(
                        Types.PbDate.newBuilder()
                            .setYear(2025)
                            .setMonth(1)
                            .setDay(2)
                    )
                    .setTime(
                        Types.PbTime.newBuilder()
                            .setHour(12)
                            .setMinute(0)
                            .setSeconds(0)
                    ).setTrusted(true)
            ).build()


        val exerciseBaseResponseContent = ByteArrayOutputStream().apply {
            exerciseBaseProto.writeTo(this)
        }

        every { client.request(any<ByteArray>()) } returns Single.just(mockResponseContent) andThen Single.just(
            exerciseBaseResponseContent
        )

        // Act
        val testObserver = PolarTrainingSessionUtils.readTrainingSession(client, reference).test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()

        val trainingSession = testObserver.values().first()

        val session = trainingSession.sessionSummary!!
        val exercise = trainingSession.exercises.first()

        assertEquals(dateTimeFormatter.parse("20250101101200"), session.start)
        assertEquals(dateTimeFormatter.parse("20250101111200"), session.end)
        assertEquals(1, session.exerciseCount)
        assertEquals("123ABC", session.deviceId)
        assertEquals("Polar 360", session.modelName)
        assertEquals(3600, session.duration?.seconds)
        assertEquals(12.5f, session.distance)
        assertEquals(400, session.calories)
        assertEquals(110, session.heartRate?.average)
        assertEquals(150, session.heartRate?.maximum)
        assertEquals("Running", session.sessionName)
        assertEquals(4.5f, session.feeling)
        assertEquals("Note", session.note)
        assertEquals("Place", session.place)
        assertEquals(60.1699, session.latitude)
        assertEquals(24.9384, session.longitude)
        assertEquals("FEEDBACK_1", session.benefit?.feedback)
        assertEquals(3, session.sport?.id)
        assertEquals(100.0f, session.cardioLoad?.exerciseLoad)
        assertEquals(50.0f, session.cardioLoad?.activityLoad)
        assertEquals(3, session.cardioLoadInterpretation)
        assertEquals(200.5f, session.muscleLoad)
        assertEquals(4, session.muscleLoadInterpretation)
        assertEquals("123e4567-e89b-12d3-a456-426614174000", session.periodUuid!!.toStringUtf8())
        assertEquals(TrainingSession.PbTrainingSession.PbTrainingStartTrigger.MANUAL.name, session.startTrigger?.name)

        assertEquals(3600, exercise.exerciseSummary?.duration?.seconds)
        assertEquals(3, exercise.exerciseSummary?.sport?.id)
        assertEquals(400, exercise.exerciseSummary?.calories)
        assertEquals(12.5f, exercise.exerciseSummary?.distance)
        assertEquals(100.5f, exercise.exerciseSummary?.ascent)
        assertEquals(90.3f, exercise.exerciseSummary?.descent)
        assertEquals(60.1699, exercise.exerciseSummary?.latitude)
        assertEquals(24.9384, exercise.exerciseSummary?.longitude)
        assertEquals("Place", exercise.exerciseSummary?.place)
        assertEquals(100f, exercise.exerciseSummary?.cardioLoad?.exerciseLoad)
        assertEquals(50f, exercise.exerciseSummary?.cardioLoad?.activityLoad)
        assertEquals(3, exercise.exerciseSummary?.cardioLoadInterpretation)
        assertEquals(200.5f, exercise.exerciseSummary?.muscleLoad)
        assertEquals(4, exercise.exerciseSummary?.muscleLoadInterpretation)
        assertEquals(150, exercise.exerciseSummary?.accumulatedTorque)
        assertEquals(200, exercise.exerciseSummary?.cyclingPowerEnergy)
        assertEquals(5.0f, exercise.exerciseSummary?.walkingDistance)
        assertEquals(1800, exercise.exerciseSummary?.walkingDuration?.seconds)
        assertEquals(55, exercise.exerciseSummary?.runningIndex)
        assertEquals(10, exercise.exerciseSummary?.exerciseCounters?.sprintCount)
        assertEquals(dateTimeFormatter.parse("20250102120000"), exercise.exerciseSummary?.lastModified)
        verify {
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(reference.path)
                    .build()
                    .toByteArray()
            )
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath("/U/0/20250101/E/101200/BASE.BPB")
                    .build()
                    .toByteArray()
            )
        }
        confirmVerified(client)
    }
}