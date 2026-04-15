package com.polar.sdk.api.model.utils

import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.activity.PolarActiveTime
import com.polar.sdk.api.model.activity.PolarActiveTimeData
import com.polar.sdk.api.model.activity.PolarActivityClass
import com.polar.sdk.api.model.activity.PolarActivityInfo
import com.polar.sdk.api.model.activity.PolarDailyBalanceFeedBack
import com.polar.sdk.api.model.activity.PolarReadinessForSpeedAndStrengthTraining
import com.polar.sdk.impl.utils.CaloriesType
import com.polar.sdk.impl.utils.PolarActivityUtils
import fi.polar.remote.representation.protobuf.ActivitySamples.PbActivityInfo
import fi.polar.remote.representation.protobuf.ActivitySamples.PbActivitySamples
import fi.polar.remote.representation.protobuf.DailySummary
import fi.polar.remote.representation.protobuf.DailySummary.PbActivityGoalSummary
import fi.polar.remote.representation.protobuf.DailySummary.PbDailySummary
import fi.polar.remote.representation.protobuf.Types
import fi.polar.remote.representation.protobuf.Types.PbDate
import fi.polar.remote.representation.protobuf.Types.PbDuration
import fi.polar.remote.representation.protobuf.Types.PbLocalDateTime
import fi.polar.remote.representation.protobuf.Types.PbTime
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.test.runTest
import org.junit.Test
import protocol.PftpRequest
import protocol.PftpResponse.PbPFtpDirectory
import protocol.PftpResponse.PbPFtpEntry
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class PolarActivityUtilsTest {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    @Test
    fun `readStepsFromDayDirectory() should return sum of step samples`() = runTest {
        // Arrange
        val client = mockk<BlePsFtpClient>()

        val mockFileContent1 = ByteArrayOutputStream().apply {
            PbActivitySamples.newBuilder()
                .addStepsSamples(10000)
                .addStepsSamples(5000)
                .addStepsSamples(8000)
                .setMetRecordingInterval(PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(0).setMillis(0))
                .setStepsRecordingInterval(PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(0).setMillis(0))
                .setStartTime(
                    PbLocalDateTime.newBuilder()
                        .setDate(PbDate.newBuilder().setDay(1).setMonth(1).setYear(2525))
                        .setTime(PbTime.newBuilder().setHour(8).setMinute(0).setSeconds(0))
                        .setOBSOLETETrusted(true)
                )
                .build()
                .writeTo(this)
        }

        val mockDirectoryContent = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(
                    listOf(
                        PbPFtpEntry.newBuilder().setName("ASAMPL0.BPB").setSize(333L).build(),
                    )
                ).build().writeTo(this)
        }

        val date = LocalDate.now()
        val expectedSteps = 23000
        val expectedDirectoryPath = "/U/0/${date.format(dateFormatter)}/ACT/"
        val expectedFilePath = "/U/0/${date.format(dateFormatter)}/ACT/ASAMPL0.BPB"

        coEvery { client.request(any<ByteArray>()) } answers { mockDirectoryContent } andThen mockFileContent1

        // Act
        val result = PolarActivityUtils.readStepsFromDayDirectory(client, date)

        // Assert
        assert(result == expectedSteps)

        coVerify(atLeast = 1) {
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(expectedFilePath)
                    .build()
                    .toByteArray()
            )
        }

        coVerify(atLeast = 1) {
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(expectedDirectoryPath)
                    .build()
                    .toByteArray()
            )
        }

        confirmVerified(client)
    }

    @Test
    fun `readStepsFromDayDirectory() should return sum of step samples from multiple sample files`() = runTest {
        // Arrange
        val client = mockk<BlePsFtpClient>()

        val mockFileContent1 = ByteArrayOutputStream().apply {
            PbActivitySamples.newBuilder()
                .addStepsSamples(10000).addStepsSamples(5000).addStepsSamples(8000)
                .setMetRecordingInterval(PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(0).setMillis(0))
                .setStepsRecordingInterval(PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(0).setMillis(0))
                .setStartTime(PbLocalDateTime.newBuilder()
                    .setDate(PbDate.newBuilder().setDay(1).setMonth(1).setYear(2525))
                    .setTime(PbTime.newBuilder().setHour(8).setMinute(0).setSeconds(0))
                    .setOBSOLETETrusted(true))
                .build().writeTo(this)
        }
        val mockFileContent2 = ByteArrayOutputStream().apply {
            PbActivitySamples.newBuilder()
                .addStepsSamples(1000).addStepsSamples(500).addStepsSamples(800)
                .setMetRecordingInterval(PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(0).setMillis(0))
                .setStepsRecordingInterval(PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(0).setMillis(0))
                .setStartTime(PbLocalDateTime.newBuilder()
                    .setDate(PbDate.newBuilder().setDay(1).setMonth(1).setYear(2525))
                    .setTime(PbTime.newBuilder().setHour(8).setMinute(0).setSeconds(0))
                    .setOBSOLETETrusted(true))
                .build().writeTo(this)
        }
        val mockFileContent3 = ByteArrayOutputStream().apply {
            PbActivitySamples.newBuilder()
                .addStepsSamples(100).addStepsSamples(50).addStepsSamples(80)
                .setMetRecordingInterval(PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(0).setMillis(0))
                .setStepsRecordingInterval(PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(0).setMillis(0))
                .setStartTime(PbLocalDateTime.newBuilder()
                    .setDate(PbDate.newBuilder().setDay(1).setMonth(1).setYear(2525))
                    .setTime(PbTime.newBuilder().setHour(8).setMinute(0).setSeconds(0))
                    .setOBSOLETETrusted(true))
                .build().writeTo(this)
        }
        val mockDirectoryContent = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(listOf(
                    PbPFtpEntry.newBuilder().setName("ASAMPL0.BPB").setSize(333L).build(),
                    PbPFtpEntry.newBuilder().setName("ASAMPL1.BPB").setSize(333L).build(),
                    PbPFtpEntry.newBuilder().setName("ASAMPL2.BPB").setSize(333L).build()
                )).build().writeTo(this)
        }

        val date = LocalDate.now()
        val expectedSteps = 23000 + 2300 + 230
        val expectedDirectoryPath = "/U/0/${date.format(dateFormatter)}/ACT/"

        coEvery { client.request(any<ByteArray>()) } answers { mockDirectoryContent } andThen mockFileContent1 andThen mockFileContent2 andThen mockFileContent3

        // Act
        val result = PolarActivityUtils.readStepsFromDayDirectory(client, date)

        // Assert
        assert(result == expectedSteps)

        coVerify {
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(expectedDirectoryPath)
                    .build()
                    .toByteArray()
            )
        }
    }

    @Test
    fun `readStepsFromDayDirectory() should return 0 if activity file not found`() = runTest {
        // Arrange
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.now()
        val expectedDirectoryPath = "/U/0/${date.format(dateFormatter)}/ACT/"

        coEvery { client.request(any()) } throws Throwable("No files found for date $date")

        // Act
        val result = PolarActivityUtils.readStepsFromDayDirectory(client, date)

        // Assert
        assert(result == 0)

        coVerify(atLeast = 1) {
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(expectedDirectoryPath)
                    .build()
                    .toByteArray()
            )
        }

        confirmVerified(client)
    }

    @Test
    fun `readDistanceFromDayDirectory() should return daily distance`() = runTest {
        // Arrange
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.now()
        val expectedDistance = 1234.56f
        val outputStream = ByteArrayOutputStream()
        val expectedPath = "/U/0/${date.format(dateFormatter)}/DSUM/DSUM.BPB"

        val proto = PbDailySummary.newBuilder()
            .setDate(PbDate.newBuilder().setDay(1).setMonth(1).setYear(2525))
            .setActivityDistance(1234.56f)
            .setActivityCalories(100)
            .setBmrCalories(2000)
            .setTrainingCalories(500)
            .setActivityClassTimes(
                DailySummary.PbActivityClassTimes.newBuilder()
                    .setTimeLightActivity(PbDuration.newBuilder().setHours(5).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeSleep(PbDuration.newBuilder().setHours(8).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeSedentary(PbDuration.newBuilder().setHours(7).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeContinuousModerate(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeContinuousVigorous(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeIntermittentModerate(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeIntermittentVigorous(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeNonWear(PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(0).setMillis(0))
            )
            .setActivityGoalSummary(PbActivityGoalSummary.newBuilder().setActivityGoal(100f).setAchievedActivity(50f))
            .setDailyBalanceFeedback(Types.PbDailyBalanceFeedback.DB_YOU_COULD_DO_MORE_TRAINING)
            .setReadinessForSpeedAndStrengthTraining(Types.PbReadinessForSpeedAndStrengthTraining.RSST_A1_RECOVERED_READY_FOR_ALL_TRAINING)
            .setSteps(10000)
            .build()
        proto.writeTo(outputStream)

        coEvery { client.request(any()) } returns outputStream

        // Act
        val result = PolarActivityUtils.readDistanceFromDayDirectory(client, date)

        // Assert
        assert(result == expectedDistance)

        coVerifyOrder {
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(expectedPath)
                    .build()
                    .toByteArray()
            )
        }
        confirmVerified(client)
    }

    @Test
    fun `readDistanceFromDayDirectory() should return 0 if activity file not found`() = runTest {
        // Arrange
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.now()
        val expectedPath = "/U/0/${date.format(dateFormatter)}/DSUM/DSUM.BPB"

        coEvery { client.request(any()) } throws Throwable("File not found")

        // Act
        val result = PolarActivityUtils.readDistanceFromDayDirectory(client, date)

        // Assert
        assert(result == 0f)

        coVerifyOrder {
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(expectedPath)
                    .build()
                    .toByteArray()
            )
        }
        confirmVerified(client)
    }

    @Test
    fun `readSpecificCaloriesFromDayDirectory() should return specific calories value`() = runTest {
        // Arrange
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.now()
        val expectedCalories = 500
        val expectedPath = "/U/0/${date.format(dateFormatter)}/DSUM/DSUM.BPB"

        val dailySummaryBuilder = DailySummary.PbDailySummary.newBuilder()
        val calories = arrayOf(Pair(CaloriesType.ACTIVITY, 500), Pair(CaloriesType.BMR, 500), Pair(CaloriesType.TRAINING, 500))
        for (caloriesType in calories) {
            when (caloriesType.first) {
                CaloriesType.ACTIVITY -> dailySummaryBuilder.activityCalories = expectedCalories
                CaloriesType.TRAINING -> dailySummaryBuilder.trainingCalories = expectedCalories
                CaloriesType.BMR -> dailySummaryBuilder.bmrCalories = expectedCalories
            }
        }
        dailySummaryBuilder
            .setDate(PbDate.newBuilder().setDay(1).setMonth(1).setYear(2525))
            .setActivityClassTimes(
                DailySummary.PbActivityClassTimes.newBuilder()
                    .setTimeLightActivity(PbDuration.newBuilder().setHours(5).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeSleep(PbDuration.newBuilder().setHours(8).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeSedentary(PbDuration.newBuilder().setHours(7).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeContinuousModerate(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeContinuousVigorous(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeIntermittentModerate(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeIntermittentVigorous(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeNonWear(PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(0).setMillis(0))
            )
            .setActivityGoalSummary(PbActivityGoalSummary.newBuilder().setActivityGoal(100f).setAchievedActivity(50f))
            .setDailyBalanceFeedback(Types.PbDailyBalanceFeedback.DB_YOU_COULD_DO_MORE_TRAINING)
            .setReadinessForSpeedAndStrengthTraining(Types.PbReadinessForSpeedAndStrengthTraining.RSST_A1_RECOVERED_READY_FOR_ALL_TRAINING)
            .setSteps(10000)
        val outputStream = ByteArrayOutputStream()
        dailySummaryBuilder.build().writeTo(outputStream)

        coEvery { client.request(any()) } returns outputStream

        // Act
        val result1 = PolarActivityUtils.readSpecificCaloriesFromDayDirectory(client, date, calories[0].first)
        val result2 = PolarActivityUtils.readSpecificCaloriesFromDayDirectory(client, date, calories[1].first)
        val result3 = PolarActivityUtils.readSpecificCaloriesFromDayDirectory(client, date, calories[2].first)

        // Assert
        assert(result1 == expectedCalories)
        assert(result2 == expectedCalories)
        assert(result3 == expectedCalories)

        coVerify(exactly = 3) {
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(expectedPath)
                    .build()
                    .toByteArray()
            )
        }
    }

    @Test
    fun `readSpecificCaloriesFromDayDirectory() should return 0 if activity file not found`() = runTest {
        // Arrange
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.now()
        val caloriesType = CaloriesType.ACTIVITY
        val expectedPath = "/U/0/${date.format(dateFormatter)}/DSUM/DSUM.BPB"

        coEvery { client.request(any()) } throws Throwable("File not found")

        // Act
        val result = PolarActivityUtils.readSpecificCaloriesFromDayDirectory(client, date, caloriesType)

        // Assert
        assert(result == 0)

        coVerifyOrder {
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(expectedPath)
                    .build()
                    .toByteArray()
            )
        }
        confirmVerified(client)
    }

    @Test
    fun `readActiveTimeFromDayDirectory() should return PolarActiveTimeData`() = runTest {
        // Arrange
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.now()
        val expectedPath = "/U/0/${date.format(dateFormatter)}/DSUM/DSUM.BPB"

        val proto = PbDailySummary.newBuilder()
            .setDate(PbDate.newBuilder().setDay(1).setMonth(1).setYear(2525))
            .setActivityDistance(1234.56f)
            .setActivityCalories(100)
            .setBmrCalories(2000)
            .setTrainingCalories(500)
            .setActivityClassTimes(
                DailySummary.PbActivityClassTimes.newBuilder()
                    .setTimeNonWear(Types.PbDuration.newBuilder().setHours(1).setMinutes(30).setSeconds(0).setMillis(0).build())
                    .setTimeSleep(Types.PbDuration.newBuilder().setHours(2).setMinutes(15).setSeconds(0).setMillis(0).build())
                    .setTimeSedentary(Types.PbDuration.newBuilder().setHours(0).setMinutes(45).setSeconds(30).setMillis(0).build())
                    .setTimeLightActivity(Types.PbDuration.newBuilder().setHours(3).setMinutes(0).setSeconds(0).setMillis(0).build())
                    .setTimeContinuousModerate(Types.PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(0).setMillis(500).build())
                    .setTimeIntermittentModerate(Types.PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(30).setMillis(0).build())
                    .setTimeContinuousVigorous(Types.PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(0).setMillis(0).build())
                    .setTimeIntermittentVigorous(Types.PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(45).setMillis(0).build())
                    .build()
            )
            .setActivityGoalSummary(PbActivityGoalSummary.newBuilder().setActivityGoal(100f).setAchievedActivity(50f))
            .setDailyBalanceFeedback(Types.PbDailyBalanceFeedback.DB_YOU_COULD_DO_MORE_TRAINING)
            .setReadinessForSpeedAndStrengthTraining(Types.PbReadinessForSpeedAndStrengthTraining.RSST_A1_RECOVERED_READY_FOR_ALL_TRAINING)
            .setSteps(10000)
            .build()

        val outputStream = ByteArrayOutputStream()
        proto.writeTo(outputStream)

        coEvery { client.request(any()) } returns outputStream

        // Act
        val result = PolarActivityUtils.readActiveTimeFromDayDirectory(client, date)

        // Assert
        assert(result == PolarActiveTimeData(
            date = date,
            timeNonWear = PolarActiveTime(1, 30, 0, 0),
            timeSleep = PolarActiveTime(2, 15, 0, 0),
            timeSedentary = PolarActiveTime(0, 45, 30, 0),
            timeLightActivity = PolarActiveTime(3, 0, 0, 0),
            timeContinuousModerateActivity = PolarActiveTime(0, 0, 0, 500),
            timeIntermittentModerateActivity = PolarActiveTime(0, 0, 30, 0),
            timeContinuousVigorousActivity = PolarActiveTime(0, 0, 0, 0),
            timeIntermittentVigorousActivity = PolarActiveTime(0, 0, 45, 0)
        ))

        coVerifyOrder {
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(expectedPath)
                    .build()
                    .toByteArray()
            )
        }
        confirmVerified(client)
    }

    @Test
    fun `readActiveTimeFromDayDirectory() should return default PolarActiveTimeData if error occurs`() = runTest {
        // Arrange
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.now()
        val expectedPath = "/U/0/${date.format(dateFormatter)}/DSUM/DSUM.BPB"

        coEvery { client.request(any()) } throws Throwable("File not found")

        // Act
        val result = PolarActivityUtils.readActiveTimeFromDayDirectory(client, date)

        // Assert
        assert(result == PolarActiveTimeData(
            date = date,
            timeNonWear = PolarActiveTime(0, 0, 0, 0),
            timeSleep = PolarActiveTime(0, 0, 0, 0),
            timeSedentary = PolarActiveTime(0, 0, 0, 0),
            timeLightActivity = PolarActiveTime(0, 0, 0, 0),
            timeContinuousModerateActivity = PolarActiveTime(0, 0, 0, 0),
            timeIntermittentModerateActivity = PolarActiveTime(0, 0, 0, 0),
            timeContinuousVigorousActivity = PolarActiveTime(0, 0, 0, 0),
            timeIntermittentVigorousActivity = PolarActiveTime(0, 0, 0, 0)
        ))

        coVerifyOrder {
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(expectedPath)
                    .build()
                    .toByteArray()
            )
        }
        confirmVerified(client)
    }

    @Test
    fun `readActivitySamplesFromDayDirectory() should return all activity samples`() = runTest {
        // Arrange
        val client = mockk<BlePsFtpClient>()

        val mockFileContent1 = ByteArrayOutputStream().apply {
            PbActivitySamples.newBuilder()
                .addStepsSamples(1).addStepsSamples(2).addStepsSamples(3)
                .addMetSamples(1.0f).addMetSamples(2.0f).addMetSamples(3.0f)
                .setMetRecordingInterval(PbDuration.newBuilder().setSeconds(30).build())
                .setStepsRecordingInterval(PbDuration.newBuilder().setSeconds(60).build())
                .addActivityInfo(0, PbActivityInfo.newBuilder()
                    .setValue(PbActivityInfo.ActivityClass.valueOf("LIGHT"))
                    .setFactor(1f)
                    .setTimeStamp(PbLocalDateTime.newBuilder()
                        .setDate(PbDate.newBuilder().setDay(1).setMonth(1).setYear(2525))
                        .setTime(PbTime.newBuilder().setHour(8).setMinute(0).setSeconds(0))
                        .setOBSOLETETrusted(true))
                    .build())
                .setStartTime(PbLocalDateTime.newBuilder()
                    .setDate(PbDate.newBuilder().setDay(1).setMonth(1).setYear(2525))
                    .setTime(PbTime.newBuilder().setHour(8).setMinute(0).setSeconds(0))
                    .setOBSOLETETrusted(true))
                .build().writeTo(this)
        }

        val mockDirectoryContent = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(listOf(
                    PbPFtpEntry.newBuilder().setName("ASAMPL0.BPB").setSize(333L).build(),
                    PbPFtpEntry.newBuilder().setName("ASAMPL1.BPB").setSize(333L).build(),
                    PbPFtpEntry.newBuilder().setName("ASAMPL2.BPB").setSize(333L).build(),
                )).build().writeTo(this)
        }

        val date = LocalDate.now()
        val expectedDirectoryPath = "/U/0/${date.format(dateFormatter)}/ACT/"
        val expectedFilePath1 = "/U/0/${date.format(dateFormatter)}/ACT/ASAMPL0.BPB"
        val expectedFilePath2 = "/U/0/${date.format(dateFormatter)}/ACT/ASAMPL1.BPB"
        val expectedFilePath3 = "/U/0/${date.format(dateFormatter)}/ACT/ASAMPL2.BPB"

        val expectedMetSamples = listOf(1.0f, 2.0f, 3.0f)
        val expectedStepSamples = listOf(1, 2, 3)
        val expectedActivityInfo = PolarActivityInfo(PolarActivityClass.LIGHT, LocalDateTime.of(2525, 1, 1, 8, 0, 0), 1.0f)
        val expectedStepRecordingInterval = 60
        val expectedMetRecordingInterval = 30
        val expectedStartTime = LocalDateTime.of(2525, 1, 1, 8, 0, 0)

        coEvery { client.request(any<ByteArray>()) } answers { mockDirectoryContent } andThen mockFileContent1

        // Act
        val result = PolarActivityUtils.readActivitySamplesDataFromDayDirectory(client, date)

        // Assert
        assert(result.polarActivitySamplesDataList?.size == 3)

        for (activityInfo in result.polarActivitySamplesDataList!!) {
            assert(activityInfo.activityInfoList?.get(0)?.activityClass == expectedActivityInfo.activityClass)
            assert(activityInfo.activityInfoList?.get(0)?.factor == expectedActivityInfo.factor)
            assert(activityInfo.activityInfoList?.get(0)?.timeStamp == expectedActivityInfo.timeStamp)
            assert(activityInfo.metSamples == expectedMetSamples)
            assert(activityInfo.stepSamples == expectedStepSamples)
            assert(activityInfo.stepRecordingInterval == expectedStepRecordingInterval)
            assert(activityInfo.metRecordingInterval == expectedMetRecordingInterval)
            assert(activityInfo.startTime == expectedStartTime)
        }

        coVerify(exactly = 1) {
            client.request(PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath(expectedFilePath1).build().toByteArray())
        }
        coVerify(exactly = 1) {
            client.request(PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath(expectedFilePath2).build().toByteArray())
        }
        coVerify(exactly = 1) {
            client.request(PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath(expectedFilePath3).build().toByteArray())
        }
        coVerify(exactly = 1) {
            client.request(PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath(expectedDirectoryPath).build().toByteArray())
        }
        confirmVerified(client)
    }

    @Test
    fun `readActivitySamplesFromDayDirectory() should return default PolarActivitySamplesDayData if error occurs`() = runTest {
        // Arrange
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.now()
        val expectedPath = "/U/0/${date.format(dateFormatter)}/ACT/"

        coEvery { client.request(any()) } throws Throwable("File not found")

        // Act
        val result = PolarActivityUtils.readActivitySamplesDataFromDayDirectory(client, date)

        // Assert
        assert(result.polarActivitySamplesDataList == null || result.polarActivitySamplesDataList!!.isEmpty())

        coVerifyOrder {
            client.request(PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath(expectedPath).build().toByteArray())
        }
        confirmVerified(client)
    }

    @Test
    fun `readDailySummaryDataFromDayDirectory() should return all daily summary data`() = runTest {
        // Arrange
        val client = mockk<BlePsFtpClient>()

        val proto = PbDailySummary.newBuilder()
            .setDate(PbDate.newBuilder().setDay(1).setMonth(1).setYear(2525))
            .setActivityDistance(2000.01f)
            .setActivityCalories(100)
            .setBmrCalories(2000)
            .setTrainingCalories(500)
            .setActivityClassTimes(
                DailySummary.PbActivityClassTimes.newBuilder()
                    .setTimeLightActivity(PbDuration.newBuilder().setHours(5).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeSleep(PbDuration.newBuilder().setHours(8).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeSedentary(PbDuration.newBuilder().setHours(7).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeContinuousModerate(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeContinuousVigorous(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeIntermittentModerate(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeIntermittentVigorous(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeNonWear(PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(0).setMillis(0))
                    .build()
            )
            .setActivityGoalSummary(PbActivityGoalSummary.newBuilder()
                .setActivityGoal(100f)
                .setAchievedActivity(50f)
                .setTimeToGoUp(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0))
                .setTimeToGoJog(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0))
                .setTimeToGoWalk(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0).setMinutes(0))
            )
            .setDailyBalanceFeedback(Types.PbDailyBalanceFeedback.DB_YOU_COULD_DO_MORE_TRAINING)
            .setReadinessForSpeedAndStrengthTraining(Types.PbReadinessForSpeedAndStrengthTraining.RSST_A1_RECOVERED_READY_FOR_ALL_TRAINING)
            .setSteps(10000)
            .build()

        val outputStream = ByteArrayOutputStream()
        proto.writeTo(outputStream)

        coEvery { client.request(any()) } returns outputStream

        val date = LocalDate.now()
        val expectedFilePath = "/U/0/${date.format(dateFormatter)}/DSUM/DSUM.BPB"
        val expectedDate = LocalDate.of(2525, 1, 1)
        val expectedDistance = 2000.01f
        val expectedSteps = 10000
        val expectedCalories = 2600
        val expectedActiveTime = 24
        val expectedReadiness = PolarReadinessForSpeedAndStrengthTraining.RECOVERED_READY_FOR_ALL_TRAINING
        val expectedDBFeedBack = PolarDailyBalanceFeedBack.YOU_COULD_DO_MORE_TRAINING
        val expectedActivityGoal = 100f
        val expectedAchievedGoal = 50f
        val expectedTimeToGo = PolarActiveTime(1)

        // Act
        val result = PolarActivityUtils.readDailySummaryDataFromDayDirectory(client, date)

        // Assert
        assert(result.activityGoalSummary?.activityGoal == expectedActivityGoal)
        assert(result.activityGoalSummary?.achievedActivity == expectedAchievedGoal)
        assert(result.activityDistance == expectedDistance)
        assert(result.activityGoalSummary?.timeToGoUp == expectedTimeToGo)
        assert(result.activityGoalSummary?.timeToGoJog == expectedTimeToGo)
        assert(result.activityGoalSummary?.timeToGoWalk == expectedTimeToGo)
        assert(result.date == expectedDate)
        assert(result.steps == expectedSteps)
        assert((result.activityCalories!! + result.bmrCalories!! + result.trainingCalories!!) == expectedCalories)
        assert((result.activityClassTimes!!.timeLightActivity.hours +
                result.activityClassTimes!!.timeSleep.hours +
                result.activityClassTimes!!.timeSedentary.hours +
                result.activityClassTimes!!.timeContinuousModerateActivity.hours +
                result.activityClassTimes!!.timeContinuousVigorousActivity.hours +
                result.activityClassTimes!!.timeIntermittentModerateActivity.hours +
                result.activityClassTimes!!.timeIntermittentVigorousActivity.hours +
                result.activityClassTimes!!.timeNonWear.hours) == expectedActiveTime)
        assert(result.readinessForSpeedAndStrengthTraining == expectedReadiness)
        assert(result.dailyBalanceFeedback == expectedDBFeedBack)

        coVerify(exactly = 1) {
            client.request(PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath(expectedFilePath).build().toByteArray())
        }
        confirmVerified(client)
    }

    @Test
    fun `readDailySummaryDataFromDayDirectory() should return default PolarDailySummaryData if error occurs`() = runTest {
        // Arrange
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.now()
        val expectedPath = "/U/0/${date.format(dateFormatter)}/DSUM/DSUM.BPB"

        coEvery { client.request(any()) } throws Throwable("File not found")

        // Act
        val result = PolarActivityUtils.readDailySummaryDataFromDayDirectory(client, date)

        // Assert — returns default/empty DailySummaryData without throwing
        assert(result.activityDistance == null || result.activityDistance == 0f)

        coVerifyOrder {
            client.request(PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath(expectedPath).build().toByteArray())
        }
        confirmVerified(client)
    }
}