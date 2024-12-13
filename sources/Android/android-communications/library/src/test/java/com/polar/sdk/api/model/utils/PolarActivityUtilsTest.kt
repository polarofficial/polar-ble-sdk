package com.polar.sdk.api.model.utils

import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.activity.PolarActiveTime
import com.polar.sdk.api.model.activity.PolarActiveTimeData
import com.polar.sdk.impl.utils.CaloriesType
import com.polar.sdk.impl.utils.PolarActivityUtils
import fi.polar.remote.representation.protobuf.ActivitySamples
import fi.polar.remote.representation.protobuf.DailySummary
import fi.polar.remote.representation.protobuf.Types
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import org.junit.Test
import protocol.PftpRequest
import protocol.PftpNotification
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class PolarActivityUtilsTest {

    private val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.ENGLISH)

    @Test
    fun `readStepsFromDayDirectory() should return sum of step samples`() {
        // Arrange
        val client = mockk<BlePsFtpClient>()
        val date = Date()
        val expectedSteps = 23000
        val outputStream = ByteArrayOutputStream()
        val expectedPath = "/U/0/${dateFormat.format(date)}/ACT/ASAMPL0.BPB"

        val proto = ActivitySamples.PbActivitySamples.newBuilder()
            .addStepsSamples(10000)
            .addStepsSamples(5000)
            .addStepsSamples(8000)
            .build()

        proto.writeTo(outputStream)

        every { client.request(any()) } returns Single.just(outputStream)

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
                    .setPath(expectedPath)
                    .build()
                    .toByteArray()
            )
        }
        confirmVerified(client)
    }

    @Test
    fun `readStepsFromDayDirectory() should return 0 if activity file not found`() {
        // Arrange
        val client = mockk<BlePsFtpClient>()
        val date = Date()
        val expectedPath = "/U/0/${dateFormat.format(date)}/ACT/ASAMPL0.BPB"
        val expectedError = Throwable("File not found")

        every { client.request(any()) } returns Single.error(expectedError)

        // Act
        val testObserver = PolarActivityUtils.readStepsFromDayDirectory(client, date).test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(0)

        verify {
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
    fun `readDistanceFromDayDirectory() should return daily distance`() {
        // Arrange
        val client = mockk<BlePsFtpClient>()
        val date = Date()
        val expectedDistance = 1234.56f
        val outputStream = ByteArrayOutputStream()
        val expectedPath = "/U/0/${dateFormat.format(date)}/DSUM/DSUM.BPB"

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
            client.sendNotification(
                PftpNotification.PbPFtpHostToDevNotification.START_SYNC.number,
                null
            )
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
        val date = Date()
        val expectedPath = "/U/0/${dateFormat.format(date)}/DSUM/DSUM.BPB"
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
            client.sendNotification(
                PftpNotification.PbPFtpHostToDevNotification.START_SYNC.number,
                null
            )
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
        val date = Date()
        val caloriesType = CaloriesType.ACTIVITY
        val expectedCalories = 500
        val expectedPath = "/U/0/${dateFormat.format(date)}/DSUM/DSUM.BPB"

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
            client.sendNotification(
                PftpNotification.PbPFtpHostToDevNotification.START_SYNC.number,
                null
            )
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
        val date = Date()
        val caloriesType = CaloriesType.ACTIVITY
        val expectedPath = "/U/0/${dateFormat.format(date)}/DSUM/DSUM.BPB"
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
            client.sendNotification(
                PftpNotification.PbPFtpHostToDevNotification.START_SYNC.number,
                null
            )
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
        val date = Date()
        val expectedPath = "/U/0/${dateFormat.format(date)}/DSUM/DSUM.BPB"

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
            client.sendNotification(
                PftpNotification.PbPFtpHostToDevNotification.START_SYNC.number,
                null
            )
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
        val date = Date()
        val expectedPath = "/U/0/${dateFormat.format(date)}/DSUM/DSUM.BPB"
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
            client.sendNotification(
                PftpNotification.PbPFtpHostToDevNotification.START_SYNC.number,
                null
            )
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
