package com.polar.sdk.api.model.utils

import com.polar.androidcommunications.api.ble.BleLogger
import fi.polar.remote.representation.protobuf.Types.PbDate
import fi.polar.remote.representation.protobuf.Types.PbSystemDateTime
import fi.polar.remote.representation.protobuf.Types.PbTime
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.sleep.PolarNightlyRechargeData
import com.polar.sdk.impl.utils.PolarNightlyRechargeUtils
import com.polar.sdk.impl.utils.PolarTimeUtils
import fi.polar.remote.representation.protobuf.NightlyRecovery
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.rxjava3.core.Single
import org.junit.Test
import protocol.PftpRequest
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.*

class PolarNightlyRechargeUtilsTest {

    @Test
    fun `readNightlyRechargeData() should return nightly recharge data`() {
        // Arrange
        val dateFormatter = SimpleDateFormat("yyyyMMdd", Locale.ENGLISH)
        val mockClient = mockk<BlePsFtpClient>()
        val date = Date()
        val expectedPath = "/U/0/${dateFormatter.format(date)}/NR/NR.BPB"

        val outputStream = ByteArrayOutputStream().apply {
            val proto = NightlyRecovery.PbNightlyRecoveryStatus.newBuilder()
                    .setSleepResultDate(PbDate.newBuilder().setYear(2024).setMonth(12).setDay(5).build())
                    .setCreatedTimestamp(PbSystemDateTime.newBuilder()
                            .setDate(PbDate.newBuilder().setYear(2023).setMonth(12).setDay(5).build())
                            .setTime(PbTime.newBuilder().setHour(10).setMinute(0).setSeconds(0).setMillis(0).build())
                            .setTrusted(true)
                            .build())
                    .setModifiedTimestamp(PbSystemDateTime.newBuilder()
                            .setDate(PbDate.newBuilder().setYear(2023).setMonth(12).setDay(5).build())
                            .setTime(PbTime.newBuilder().setHour(10).setMinute(0).setSeconds(0).setMillis(0).build())
                            .setTrusted(true)
                            .build())
                    .setAnsStatus(5.5f)
                    .setRecoveryIndicator(3)
                    .setRecoveryIndicatorSubLevel(50)
                    .setAnsRate(4)
                    .setScoreRateOBSOLETE(2)
                    .setMeanNightlyRecoveryRRI(800)
                    .setMeanNightlyRecoveryRMSSD(50)
                    .setMeanNightlyRecoveryRespirationInterval(1000)
                    .setMeanBaselineRRI(750)
                    .setSdBaselineRRI(30)
                    .setMeanBaselineRMSSD(45)
                    .setSdBaselineRMSSD(20)
                    .setMeanBaselineRespirationInterval(950)
                    .setSdBaselineRespirationInterval(25)
                    .setSleepTip("Sleep tip 1")
                    .setVitalityTip("Vitality tip 2")
                    .setExerciseTip("Exercise tip 3")
                    .build()
            proto.writeTo(this)
        }

        val createdTimestamp = LocalDateTime.of(2023, 12, 5, 10, 0, 0, 0)
        val calendar = Calendar.getInstance().apply {
            set(2024, Calendar.DECEMBER, 5, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val sleepResultDate = calendar.time

        val expectedResult = PolarNightlyRechargeData(
                createdTimestamp = createdTimestamp,
                modifiedTimestamp = createdTimestamp,
                ansStatus = 5.5f,
                recoveryIndicator = 3,
                recoveryIndicatorSubLevel = 50,
                ansRate = 4,
                scoreRateObsolete = 2,
                meanNightlyRecoveryRRI = 800,
                meanNightlyRecoveryRMSSD = 50,
                meanNightlyRecoveryRespirationInterval = 1000,
                meanBaselineRRI = 750,
                sdBaselineRRI = 30,
                meanBaselineRMSSD = 45,
                sdBaselineRMSSD = 20,
                meanBaselineRespirationInterval = 950,
                sdBaselineRespirationInterval = 25,
                sleepTip = "Sleep tip 1",
                vitalityTip = "Vitality tip 2",
                exerciseTip = "Exercise tip 3",
                sleepResultDate = sleepResultDate
        )

        every { mockClient.request(any()) } returns Single.just(outputStream)

        // Act
        val testObserver = PolarNightlyRechargeUtils.readNightlyRechargeData(mockClient, date).test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(expectedResult)

        verify {
            mockClient.request(
                    PftpRequest.PbPFtpOperation.newBuilder()
                            .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                            .setPath(expectedPath)
                            .build()
                            .toByteArray()
            )
        }
        confirmVerified(mockClient)
    }

    @Test
    fun `readNightlyRechargeData() should return null when an error is thrown`() {
        // Arrange
        val dateFormatter = SimpleDateFormat("yyyyMMdd", Locale.ENGLISH)
        val mockClient = mockk<BlePsFtpClient>()
        val date = Date()
        val expectedPath = "/U/0/${dateFormatter.format(date)}/NR/NR.BPB"
        val expectedError = Throwable("No nightly recharge data found")

        every { mockClient.request(any()) } returns Single.error(expectedError)

        // Act
        val testObserver = PolarNightlyRechargeUtils.readNightlyRechargeData(mockClient, date).test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertNoValues()

        verify {
            mockClient.request(
                    PftpRequest.PbPFtpOperation.newBuilder()
                            .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                            .setPath(expectedPath)
                            .build()
                            .toByteArray()
            )
        }
        confirmVerified(mockClient)
    }
}