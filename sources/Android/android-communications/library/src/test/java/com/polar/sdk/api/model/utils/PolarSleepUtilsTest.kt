package com.polar.sdk.api.model.utils

import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.sleep.OriginalSleepRange
import com.polar.sdk.api.model.sleep.PolarSleepAnalysisResult
import com.polar.sdk.api.model.sleep.SleepCycle
import com.polar.sdk.api.model.sleep.SleepRating
import com.polar.sdk.api.model.sleep.SleepWakePhase
import com.polar.sdk.api.model.sleep.SleepWakeState
import com.polar.sdk.impl.utils.PolarSleepUtils
import fi.polar.remote.representation.protobuf.SleepanalysisResult
import fi.polar.remote.representation.protobuf.Structures
import fi.polar.remote.representation.protobuf.Types
import fi.polar.remote.representation.protobuf.Types.PbDate
import fi.polar.remote.representation.protobuf.Types.PbLocalDateTime
import fi.polar.remote.representation.protobuf.Types.PbTime
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.rxjava3.core.Single
import org.junit.Test
import protocol.PftpRequest
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class PolarSleepUtilsTest {

    @Test
    fun `readSleepFromDayDirectory() should return sleep analysis data`() {

        val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ENGLISH)

        val mockClient = mockk<BlePsFtpClient>()
        val date = LocalDate.now()
        val outputStream = ByteArrayOutputStream()
        val expectedPath = "/U/0/${date.format(dateFormatter)}/SLEEP/SLEEPRES.BPB"
        val expectedResult = createSleepAnalysisResult()

        val proto = SleepanalysisResult.PbSleepAnalysisResult.newBuilder()
            .addSleepwakePhases(createPbSleepWakePhasesMock())
            .addSleepCycles(createPbSleepCycleMock())
            .addSnoozeTime(createPbLocalDateTime(23, 59, 59, 59, 1, 2, 2525))
            .setSleepStartTime(createPbLocalDateTime(23, 45, 45, 1, 1, 2, 2525))
            .setSleepEndTime(createPbLocalDateTime(7, 5, 7, 6, 2, 2, 2525))
            .setLastModified(Types.PbSystemDateTime.newBuilder()
                .setTime(createPbTime(4,3,2,1))
                .setDate(createPbDate(4,3,2525))
                .setTrusted(true)
                .build()
            )
            .setSleepGoalMinutes(420)
            .setAlarmTime(createPbLocalDateTime(7, 0, 0, 0, 2, 2, 2525))
            .setSleepStartOffsetSeconds(1)
            .setSleepEndOffsetSeconds(1)
            .setOriginalSleepRange(Types.PbLocalDateTimeRange.newBuilder()
                .setStartTime(createPbLocalDateTime(23, 59, 59, 59, 1, 2, 2525))
                .setEndTime(createPbLocalDateTime(7, 0, 0, 0, 2, 2, 2525)).build()
            )
            .setBatteryRanOut(false)
            .setRecordingDevice(Structures.PbDeviceId.newBuilder().setDeviceId("C8D9G10F11H12").build())
            .setUserSleepRating(Types.PbSleepUserRating.valueOf("PB_SLEPT_WELL"))
            .setSleepResultDate(Types.PbDate.newBuilder().setDay(1).setMonth(2).setYear(2525).build())
            .setCreatedTimestamp(Types.PbSystemDateTime.newBuilder()
                .setTime(createPbTime(1,2,3,4))
                .setDate(createPbDate(2,2,2525))
                .setTrusted(true)
                .build()
                )

            .build()

        proto.writeTo(outputStream)

        every { mockClient.request(any()) } returns Single.just(outputStream)

        // Act
        val testObserver = PolarSleepUtils.readSleepDataFromDayDirectory(mockClient, date).test()

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

    private fun createPbSleepCycleMock(): SleepanalysisResult.PbSleepCycle {
        return SleepanalysisResult.PbSleepCycle.newBuilder()
            .setSleepDepthStart(1.0f)
            .setSecondsFromSleepStart(2)
            .build()
    }

    private fun createPbSleepWakePhasesMock(): SleepanalysisResult.PbSleepWakePhase {
        return SleepanalysisResult.PbSleepWakePhase.newBuilder()
            .setSleepwakeState(SleepanalysisResult.PbSleepWakeState.PB_WAKE)
            .setSecondsFromSleepStart(1).build()
    }

    private fun createPbLocalDateTime(hour: Int, minute: Int, second: Int, millis: Int, day: Int, month: Int, year: Int): PbLocalDateTime {
        return PbLocalDateTime.newBuilder()
            .setTime(PbTime.newBuilder().setHour(hour).setMinute(minute).setSeconds(second).setMillis(millis).build())
            .setDate(Types.PbDate.newBuilder().setDay(day).setMonth(month).setYear(year).build())
            .setTimeZoneOffset(0)
            .setOBSOLETETrusted(true)
            .build()
    }

    private fun createPbDate(day: Int, month: Int, year: Int): PbDate {
        return PbDate.newBuilder()
            .setDay(day)
            .setMonth(month)
            .setYear(year)
            .build()
    }

    private fun createPbTime(hour: Int, minute: Int, second: Int, millis: Int): PbTime {
        return PbTime.newBuilder()
            .setHour(hour)
            .setMinute(minute)
            .setSeconds(second)
            .setMillis(millis)
            .build()
    }

    private fun createSleepAnalysisResult(): PolarSleepAnalysisResult {

        var snoozeTimes = mutableListOf<LocalDateTime>()
        snoozeTimes.add(LocalDateTime.of(2525, 2, 1, 23, 59, 59, 59 * 1000000))
            //.addSnoozeTime(createPbLocalDateTime(23, 59, 59, 59, 1, 2, 2525))

        return PolarSleepAnalysisResult(
            LocalDateTime.of(2525, 2, 1, 23, // Sleep start
            45, 45, 1 * 1000000, ),
            LocalDateTime.of(2525, 2, 2, 7, // Sleep end
                5, 7, 6 * 1000000, ),
            LocalDateTime.of(2525, 3, 4, 4, // Last modified
                3, 2, 1 * 1000000, ),
            420,
            mockSleepWakePhases(),
            snoozeTimes,
            LocalDateTime.of(2525, 2, 2, 7, 0, 0, 0),
            1,
            1,
            SleepRating.SLEPT_WELL,
            "C8D9G10F11H12",
            false,
            mockSleepCycles(),
            LocalDate.of(2525, 2, 1),
            mockOriginalSleepRange()
        )
    }

    private fun mockSleepWakePhases(): List<SleepWakePhase> {

        var sleepWakePhaseMockList = mutableListOf<SleepWakePhase>()
        var sleepWakePhaseMock = SleepWakePhase(1, SleepWakeState.WAKE)

        sleepWakePhaseMockList.add(sleepWakePhaseMock)

        return  sleepWakePhaseMockList
    }

    private fun mockSleepCycles(): List<SleepCycle> {

        var sleepWakeCycleMockList = mutableListOf<SleepCycle>()
        var sleepWakeCycleMock = SleepCycle(2, 1.0f)
        sleepWakeCycleMockList.add(sleepWakeCycleMock)

        return  sleepWakeCycleMockList
    }

    private fun mockOriginalSleepRange(): OriginalSleepRange {
        return OriginalSleepRange(
            LocalDateTime.of(2525, 2, 1, 23, 59,59,59 * 1000000),
            LocalDateTime.of(2525, 2, 2, 7, 0, 0, 0)
        )
    }
}
