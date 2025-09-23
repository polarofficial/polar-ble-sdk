package com.polar.sdk.api.model.utils

import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.activity.PolarActiveTime
import com.polar.sdk.api.model.activity.PolarActiveTimeData
import com.polar.sdk.api.model.activity.PolarActivityClass
import com.polar.sdk.api.model.activity.PolarActivityInfo
import com.polar.sdk.impl.utils.CaloriesType
import com.polar.sdk.impl.utils.PolarActivityUtils
import fi.polar.remote.representation.protobuf.ActivitySamples.PbActivityInfo
import fi.polar.remote.representation.protobuf.ActivitySamples.PbActivitySamples
import fi.polar.remote.representation.protobuf.DailySummary
import fi.polar.remote.representation.protobuf.Types
import fi.polar.remote.representation.protobuf.Types.PbDate
import fi.polar.remote.representation.protobuf.Types.PbDuration
import fi.polar.remote.representation.protobuf.Types.PbLocalDateTime
import fi.polar.remote.representation.protobuf.Types.PbTime
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import org.junit.Test
import protocol.PftpRequest
import protocol.PftpResponse.PbPFtpDirectory
import protocol.PftpResponse.PbPFtpEntry
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class PolarActivityUtilsTest {

    private val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.ENGLISH)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    @Test
    fun `readStepsFromDayDirectory() should return sum of step samples`() {
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

        every { client.request(any<ByteArray>()) } returns Single.just(mockDirectoryContent) andThen Single.just(mockFileContent1)

        // Act
        val testObserver = PolarActivityUtils.readStepsFromDayDirectory(client, date).test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(expectedSteps)

        verify(atLeast = 1) {
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(expectedFilePath)
                    .build()
                    .toByteArray()
            )
        }

        verify(atLeast = 1) {
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
    fun `readStepsFromDayDirectory() should return sum of step samples from multiple sample files`() {
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

        val mockFileContent2 = ByteArrayOutputStream().apply {
            PbActivitySamples.newBuilder()
                .addStepsSamples(1000)
                .addStepsSamples(500)
                .addStepsSamples(800)
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

        val mockFileContent3 = ByteArrayOutputStream().apply {
            PbActivitySamples.newBuilder()
                .addStepsSamples(100)
                .addStepsSamples(50)
                .addStepsSamples(80)
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
                        PbPFtpEntry.newBuilder().setName("ASAMPL1.BPB").setSize(333L).build(),
                        PbPFtpEntry.newBuilder().setName("ASAMPL2.BPB").setSize(333L).build()
                    )
                ).build().writeTo(this)
        }

        val date = LocalDate.now()
        val expectedSteps = 23000 + 2300 + 230
        val expectedDirectoryPath = "/U/0/${date.format(dateFormatter)}/ACT/"

        every { client.request(any<ByteArray>()) } returns Single.just(mockDirectoryContent) andThen Single.just(mockFileContent1) andThen Single.just(mockFileContent2) andThen Single.just(mockFileContent3)

        // Act
        val testObserver = PolarActivityUtils.readStepsFromDayDirectory(client, date).test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(expectedSteps)

        verify {
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
    fun `readStepsFromDayDirectory() should return 0 if activity file not found`() {
        // Arrange
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.now()
        val expectedDirectoryPath = "/U/0/${date.format(dateFormatter)}/ACT/"
        val expectedError = Throwable("No files found for date $date")

        every { client.request(any()) } returns Single.error(expectedError)

        // Act
        val testObserver = PolarActivityUtils.readStepsFromDayDirectory(client, date).test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(0)

        verify(atLeast = 1) {
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
    fun `readDistanceFromDayDirectory() should return daily distance`() {
        // Arrange
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.now()
        val expectedDistance = 1234.56f
        val outputStream = ByteArrayOutputStream()
        val expectedPath = "/U/0/${date.format(dateFormatter)}/DSUM/DSUM.BPB"

        val proto = DailySummary.PbDailySummary.newBuilder().setActivityDistance(1234.56f).build()
        proto.writeTo(outputStream)

        every { client.request(any()) } returns Single.just(outputStream)
        every { client.sendNotification(any(), null) } returns Completable.complete()

        // Act
        val testObserver = PolarActivityUtils.readDistanceFromDayDirectory(client, date).test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(expectedDistance)

        verifyOrder {
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
    fun `readDistanceFromDayDirectory() should return 0 if activity file not found`() {
        // Arrange
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.now()
        val expectedPath = "/U/0/${date.format(dateFormatter)}/DSUM/DSUM.BPB"
        val expectedError = Throwable("File not found")

        every { client.request(any()) } returns Single.error(expectedError)
        every { client.sendNotification(any(), null) } returns Completable.complete()

        // Act
        val testObserver = PolarActivityUtils.readDistanceFromDayDirectory(client, date).test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(0f)

        verifyOrder {
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
    fun `readSpecificCaloriesFromDayDirectory() should return specific calories value`() {
        // Arrange
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.now()
        val caloriesType = CaloriesType.ACTIVITY
        val expectedCalories = 500
        val expectedPath = "/U/0/${date.format(dateFormatter)}/DSUM/DSUM.BPB"

        val dailySummaryBuilder = DailySummary.PbDailySummary.newBuilder()
        when (caloriesType) {
            CaloriesType.ACTIVITY -> dailySummaryBuilder.activityCalories = expectedCalories
            CaloriesType.TRAINING -> dailySummaryBuilder.trainingCalories = expectedCalories
            CaloriesType.BMR -> dailySummaryBuilder.bmrCalories = expectedCalories
        }
        val dailySummary = dailySummaryBuilder.build()
        val outputStream = ByteArrayOutputStream()
        dailySummary.writeTo(outputStream)

        every { client.request(any()) } returns Single.just(outputStream)
        every { client.sendNotification(any(), null) } returns Completable.complete()

        // Act
        val testObserver = PolarActivityUtils.readSpecificCaloriesFromDayDirectory(client, date, caloriesType).test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(expectedCalories)

        verifyOrder {
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
    fun `readSpecificCaloriesFromDayDirectory() should return 0 if activity file not found`() {
        // Arrange
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.now()
        val caloriesType = CaloriesType.ACTIVITY
        val expectedPath = "/U/0/${date.format(dateFormatter)}/DSUM/DSUM.BPB"
        val expectedError = Throwable("File not found")

        every { client.request(any()) } returns Single.error(expectedError)
        every { client.sendNotification(any(), null) } returns Completable.complete()

        // Act
        val testObserver = PolarActivityUtils.readSpecificCaloriesFromDayDirectory(client, date, caloriesType).test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(0)

        verifyOrder {
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
    fun `readActiveTimeFromDayDirectory() should return PolarActiveTimeData`() {
        // Arrange
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.now()
        val expectedPath = "/U/0/${date.format(dateFormatter)}/DSUM/DSUM.BPB"

        val proto = DailySummary.PbDailySummary.newBuilder()
            .setActivityClassTimes(
                DailySummary.PbActivityClassTimes.newBuilder()
                    .setTimeNonWear(
                        Types.PbDuration.newBuilder().setHours(1).setMinutes(30).setSeconds(0)
                            .setMillis(0).build()
                    )
                    .setTimeSleep(
                        Types.PbDuration.newBuilder().setHours(2).setMinutes(15).setSeconds(0)
                            .setMillis(0).build()
                    )
                    .setTimeSedentary(
                        Types.PbDuration.newBuilder().setHours(0).setMinutes(45).setSeconds(30)
                            .setMillis(0).build()
                    )
                    .setTimeLightActivity(
                        Types.PbDuration.newBuilder().setHours(3).setMinutes(0).setSeconds(0)
                            .setMillis(0).build()
                    )
                    .setTimeContinuousModerate(
                        Types.PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(0)
                            .setMillis(500).build()
                    )
                    .setTimeIntermittentModerate(
                        Types.PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(30)
                            .setMillis(0).build()
                    )
                    .setTimeContinuousVigorous(
                        Types.PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(0)
                            .setMillis(0).build()
                    )
                    .setTimeIntermittentVigorous(
                        Types.PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(45)
                            .setMillis(0).build()
                    )
                    .build()
            )
            .build()

        val outputStream = ByteArrayOutputStream()
        proto.writeTo(outputStream)

        every { client.request(any()) } returns Single.just(outputStream)
        every { client.sendNotification(any(), null) } returns Completable.complete()

        // Act
        val testObserver = PolarActivityUtils.readActiveTimeFromDayDirectory(client, date).test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(
            PolarActiveTimeData(
                date = date,
                timeNonWear = PolarActiveTime(1, 30, 0, 0),
                timeSleep = PolarActiveTime(2, 15, 0, 0),
                timeSedentary = PolarActiveTime(0, 45, 30, 0),
                timeLightActivity = PolarActiveTime(3, 0, 0, 0),
                timeContinuousModerateActivity = PolarActiveTime(0, 0, 0, 500),
                timeIntermittentModerateActivity = PolarActiveTime(0, 0, 30, 0),
                timeContinuousVigorousActivity = PolarActiveTime(0, 0, 0, 0),
                timeIntermittentVigorousActivity = PolarActiveTime(0, 0, 45, 0)
            )
        )

        verifyOrder {
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
    fun `readActiveTimeFromDayDirectory() should return default PolarActiveTimeData if error occurs`() {
        // Arrange
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.now()
        val expectedPath = "/U/0/${date.format(dateFormatter)}/DSUM/DSUM.BPB"
        val expectedError = Throwable("File not found")

        every { client.request(any()) } returns Single.error(expectedError)
        every { client.sendNotification(any(), null) } returns Completable.complete()

        // Act
        val testObserver = PolarActivityUtils.readActiveTimeFromDayDirectory(client, date).test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(
            PolarActiveTimeData(
                date = date,
                timeNonWear = PolarActiveTime(0, 0, 0, 0),
                timeSleep = PolarActiveTime(0, 0, 0, 0),
                timeSedentary = PolarActiveTime(0, 0, 0, 0),
                timeLightActivity = PolarActiveTime(0, 0, 0, 0),
                timeContinuousModerateActivity = PolarActiveTime(0, 0, 0, 0),
                timeIntermittentModerateActivity = PolarActiveTime(0, 0, 0, 0),
                timeContinuousVigorousActivity = PolarActiveTime(0, 0, 0, 0),
                timeIntermittentVigorousActivity = PolarActiveTime(0, 0, 0, 0)
            )
        )

        verifyOrder {
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
    fun `readActivitySamplesFromDayDirectory() should return all activity samples`() {
        // Arrange
        val client = mockk<BlePsFtpClient>()

        val mockFileContent1 = ByteArrayOutputStream().apply {
            PbActivitySamples.newBuilder()
                .addStepsSamples(1)
                .addStepsSamples(2)
                .addStepsSamples(3)
                .addMetSamples(1.0f)
                .addMetSamples(2.0f)
                .addMetSamples(3.0f)
                .setMetRecordingInterval(PbDuration.newBuilder().setSeconds(30).build())
                .setStepsRecordingInterval(PbDuration.newBuilder().setSeconds(60).build())
                .addActivityInfo(
                    0,
                    PbActivityInfo.newBuilder().setValue(PbActivityInfo.ActivityClass.valueOf("LIGHT"))
                        .setFactor(1f)
                        .setTimeStamp(PbLocalDateTime.newBuilder()
                            .setDate(PbDate.newBuilder().setDay(1).setMonth(1).setYear(2525))
                            .setTime(PbTime.newBuilder().setHour(8).setMinute(0).setSeconds(0))
                            .setOBSOLETETrusted(true)
                        ).build()
                ).setStartTime(
                    PbLocalDateTime.newBuilder()
                        .setDate(PbDate.newBuilder().setDay(1).setMonth(1).setYear(2525))
                        .setTime(PbTime.newBuilder().setHour(8).setMinute(0).setSeconds(0))
                        .setOBSOLETETrusted(true)
                ).build()
                .writeTo(this)
        }

        val mockDirectoryContent = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(
                    listOf(
                        PbPFtpEntry.newBuilder().setName("ASAMPL0.BPB").setSize(333L).build(),
                        PbPFtpEntry.newBuilder().setName("ASAMPL1.BPB").setSize(333L).build(),
                        PbPFtpEntry.newBuilder().setName("ASAMPL2.BPB").setSize(333L).build(),
                    )
                ).build().writeTo(this)
        }

        val date = Date()
        val expectedDirectoryPath = "/U/0/${dateFormat.format(date)}/ACT/"
        val expectedFilePath1 = "/U/0/${dateFormat.format(date)}/ACT/ASAMPL0.BPB"
        val expectedFilePath2 = "/U/0/${dateFormat.format(date)}/ACT/ASAMPL1.BPB"
        val expectedFilePath3 = "/U/0/${dateFormat.format(date)}/ACT/ASAMPL2.BPB"

        val expectedMetSamples = listOf(1.0f, 2.0f, 3.0f)
        val expectedStepSamples = listOf(1, 2, 3)
        val expectedActivityInfo = PolarActivityInfo(PolarActivityClass.LIGHT, LocalDateTime.of(2525, 1, 1,8,0,0),1.0f)
        val expectedStepRecordingInterval = 60
        val expectedMetRecordingInterval = 30
        val expectedStartTime = LocalDateTime.of(2525, 1, 1,8,0,0)
        every { client.request(any<ByteArray>()) } returns Single.just(mockDirectoryContent) andThen Single.just(mockFileContent1)

        // Act
        val testObserver = PolarActivityUtils.readActivitySamplesDataFromDayDirectory(client, date).test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()

        assert(testObserver.values()[0].polarActivitySamplesDataList?.size == 3)

        for (activityInfo in testObserver.values()[0].polarActivitySamplesDataList!!) {
            assert(activityInfo.activityInfoList?.get(0)?.activityClass == expectedActivityInfo.activityClass)
            assert(activityInfo.activityInfoList?.get(0)?.factor == expectedActivityInfo.factor)
            assert(activityInfo.activityInfoList?.get(0)?.timeStamp == expectedActivityInfo.timeStamp)
            assert(activityInfo.metSamples == expectedMetSamples)
            assert(activityInfo.stepSamples == expectedStepSamples)
            assert(activityInfo.stepRecordingInterval == expectedStepRecordingInterval)
            assert(activityInfo.metRecordingInterval == expectedMetRecordingInterval)
            assert(activityInfo.startTime == expectedStartTime)
        }

        verify(exactly = 1) {
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(expectedFilePath1)
                    .build()
                    .toByteArray()
            )
        }

        verify(exactly = 1) {
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(expectedFilePath2)
                    .build()
                    .toByteArray()
            )
        }

        verify(exactly = 1) {
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(expectedFilePath3)
                    .build()
                    .toByteArray()
            )
        }

        verify(exactly = 1) {
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
    fun `readActivitySamplesFromDayDirectory() should return default PolarActivitySamplesDayData if error occurs`() {

        // Arrange
        val client = mockk<BlePsFtpClient>()
        val date = Date()
        val expectedPath = "/U/0/${dateFormat.format(date)}/ACT/"
        val expectedError = Throwable("File not found")

        every { client.request(any()) } returns Single.error(expectedError)

        // Act
        val testObserver = PolarActivityUtils.readActivitySamplesDataFromDayDirectory(client, date).test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()

        verifyOrder {
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
}
