package com.polar.sdk.api.model.utils

import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.activity.AutomaticSampleTriggerType
import com.polar.sdk.api.model.activity.IntervalStatus
import com.polar.sdk.api.model.activity.Movement
import com.polar.sdk.api.model.activity.PPiSampleStatus
import com.polar.sdk.api.model.activity.PPiSampleTriggerType
import com.polar.sdk.api.model.activity.SkinContact
import com.polar.sdk.impl.utils.PolarAutomaticSamplesUtils
import fi.polar.remote.representation.protobuf.AutomaticSamples.PbAutomaticHeartRateSamples
import fi.polar.remote.representation.protobuf.AutomaticSamples.PbAutomaticSampleSessions
import fi.polar.remote.representation.protobuf.AutomaticSamples.PbMeasTriggerType
import fi.polar.remote.representation.protobuf.AutomaticSamples.PbPpIntervalAutoSamples
import fi.polar.remote.representation.protobuf.PpIntervals.PbPpIntervalSamples
import fi.polar.remote.representation.protobuf.Types
import fi.polar.remote.representation.protobuf.Types.PbTime
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.rxjava3.core.Single
import org.junit.Test
import protocol.PftpResponse.PbPFtpDirectory
import protocol.PftpResponse.PbPFtpEntry
import java.io.ByteArrayOutputStream
import java.time.LocalTime
import java.util.Calendar

class PolarAutomaticSamplesUtilsTest {

    private val mockClient = mockk<BlePsFtpClient>()

    @Test
    fun `read247HrSamples() should correctly filter samples by date and parse all trigger types`() {
        // Arrange
        val fromDate = Calendar.getInstance().apply { set(2024, 10, 10, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.time
        val toDate = Calendar.getInstance().apply { set(2024, 10, 18, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.time

        val mockDirectoryContent = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(
                    listOf(
                        PbPFtpEntry.newBuilder().setName("AUTOS000.BPB").setSize(333L).build(),
                        PbPFtpEntry.newBuilder().setName("AUTOS001.BPB").setSize(444L).build()
                    )
                ).build().writeTo(this)
        }

        val mockFileContent1 = ByteArrayOutputStream().apply {
            PbAutomaticSampleSessions.newBuilder()
                .addAllSamples(
                    listOf(
                        PbAutomaticHeartRateSamples.newBuilder()
                            .addAllHeartRate(listOf(60, 61, 63))
                            .setTime(
                                Types.PbTime.newBuilder()
                                    .setHour(10)
                                    .setMinute(12)
                                    .setSeconds(34)
                                    .build()
                            )
                            .setTriggerType(PbMeasTriggerType.TRIGGER_TYPE_HIGH_ACTIVITY)
                            .build(),
                        PbAutomaticHeartRateSamples.newBuilder()
                            .addAllHeartRate(listOf(80, 81, 83))
                            .setTime(
                                Types.PbTime.newBuilder()
                                    .setHour(12)
                                    .setMinute(0)
                                    .setSeconds(0)
                                    .build()
                            )
                            .setTriggerType(PbMeasTriggerType.TRIGGER_TYPE_MANUAL)
                            .build()
                    )
                ).setDay(
                    Types.PbDate.newBuilder()
                        .setYear(2024)
                        .setMonth(11)
                        .setDay(18)
                        .build()
                )
                .build()
                .writeTo(this)
        }

        val mockFileContent2 = ByteArrayOutputStream().apply {
            PbAutomaticSampleSessions.newBuilder()
                .addAllSamples(
                    listOf(
                        PbAutomaticHeartRateSamples.newBuilder()
                            .addAllHeartRate(listOf(70, 72, 74))
                            .setTime(
                                Types.PbTime.newBuilder()
                                    .setHour(16)
                                    .setMinute(49)
                                    .setSeconds(36)
                                    .build()
                            )
                            .setTriggerType(PbMeasTriggerType.TRIGGER_TYPE_LOW_ACTIVITY)
                            .build(),
                        PbAutomaticHeartRateSamples.newBuilder()
                            .addAllHeartRate(listOf(90, 91, 93))
                            .setTime(
                                Types.PbTime.newBuilder()
                                    .setHour(18)
                                    .setMinute(0)
                                    .setSeconds(0)
                                    .build()
                            )
                            .setTriggerType(PbMeasTriggerType.TRIGGER_TYPE_TIMED)
                            .build()
                    )
                ).setDay(
                    Types.PbDate.newBuilder()
                        .setYear(2024)
                        .setMonth(11)
                        .setDay(15)
                        .build()
                )
                .build()
                .writeTo(this)
        }

        every { mockClient.request(any<ByteArray>()) } returns Single.just(mockDirectoryContent) andThen Single.just(mockFileContent1) andThen Single.just(mockFileContent2)

        // Act
        val result = PolarAutomaticSamplesUtils.read247HrSamples(mockClient, fromDate, toDate).blockingGet()

        // Assert
        verify { mockClient.request(any<ByteArray>()) }
        confirmVerified(mockClient)

        assert(result.size == 4)
        val date1 = Calendar.getInstance().apply { set(2024, 10, 18, 10, 12, 34); set(Calendar.MILLISECOND, 0) }
        assert(result[0].date == date1.time)
        assert(result[0].hrSamples == listOf(60, 61, 63))
        assert(result[0].triggerType == AutomaticSampleTriggerType.TRIGGER_TYPE_HIGH_ACTIVITY)

        val date2 = Calendar.getInstance().apply { set(2024, 10, 18, 12, 0, 0); set(Calendar.MILLISECOND, 0) }
        assert(result[1].date == date2.time)
        assert(result[1].hrSamples == listOf(80, 81, 83))
        assert(result[1].triggerType == AutomaticSampleTriggerType.TRIGGER_TYPE_MANUAL)

        val date3 = Calendar.getInstance().apply { set(2024, 10, 15, 16, 49, 36); set(Calendar.MILLISECOND, 0) }
        assert(result[2].date == date3.time)
        assert(result[2].hrSamples == listOf(70, 72, 74))
        assert(result[2].triggerType == AutomaticSampleTriggerType.TRIGGER_TYPE_LOW_ACTIVITY)

        val date4 = Calendar.getInstance().apply { set(2024, 10, 15, 18, 0, 0); set(Calendar.MILLISECOND, 0) }
        assert(result[3].date == date4.time)
        assert(result[3].hrSamples == listOf(90, 91, 93))
        assert(result[3].triggerType == AutomaticSampleTriggerType.TRIGGER_TYPE_TIMED)
    }

    @Test
    fun `read247HrSamples() should filter out samples outside the date range`() {
        // Arrange
        val fromDate = Calendar.getInstance().apply { set(2024, 10, 10, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.time
        val toDate = Calendar.getInstance().apply { set(2024, 10, 18, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.time

        val mockDirectoryContent = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(
                    listOf(
                        PbPFtpEntry.newBuilder().setName("AUTOS000.BPB").setSize(333L).build()
                    )
                ).build().writeTo(this)
        }

        val mockFileContent = ByteArrayOutputStream().apply {
            PbAutomaticSampleSessions.newBuilder()
                .addAllSamples(
                    listOf(
                        PbAutomaticHeartRateSamples.newBuilder()
                            .addAllHeartRate(listOf(60, 61, 63))
                            .setTime(
                                Types.PbTime.newBuilder()
                                    .setHour(10)
                                    .setMinute(12)
                                    .setSeconds(34)
                                    .build()
                            )
                            .setTriggerType(PbMeasTriggerType.TRIGGER_TYPE_HIGH_ACTIVITY)
                            .build(),
                        PbAutomaticHeartRateSamples.newBuilder()
                            .addAllHeartRate(listOf(70, 72, 74))
                            .setTime(
                                Types.PbTime.newBuilder()
                                    .setHour(14)
                                    .setMinute(30)
                                    .setSeconds(0)
                                    .build()
                            )
                            .setTriggerType(PbMeasTriggerType.TRIGGER_TYPE_LOW_ACTIVITY)
                            .build()
                    )
                ).setDay(
                    Types.PbDate.newBuilder()
                        .setYear(2024)
                        .setMonth(11)
                        .setDay(20)
                        .build()
                ).build().writeTo(this)
        }

        val mockFileContent2 = ByteArrayOutputStream().apply {
            PbAutomaticSampleSessions.newBuilder()
                .addAllSamples(
                    listOf(
                        PbAutomaticHeartRateSamples.newBuilder()
                            .addAllHeartRate(listOf(80, 81, 83))
                            .setTime(
                                Types.PbTime.newBuilder()
                                    .setHour(16)
                                    .setMinute(45)
                                    .setSeconds(0)
                                    .build()
                            )
                            .setTriggerType(PbMeasTriggerType.TRIGGER_TYPE_MANUAL)
                            .build()
                    )
                ).setDay(
                    Types.PbDate.newBuilder()
                        .setYear(2024)
                        .setMonth(11)
                        .setDay(9)
                        .build()
                ).build().writeTo(this)
        }

        every { mockClient.request(any<ByteArray>()) } returns Single.just(mockDirectoryContent) andThen Single.just(mockFileContent) andThen Single.just(mockFileContent2)

        // Act
        val result = PolarAutomaticSamplesUtils.read247HrSamples(mockClient, fromDate, toDate).blockingGet()

        // Assert
        verify { mockClient.request(any<ByteArray>()) }
        confirmVerified(mockClient)

        assert(result.isEmpty())
    }

    @Test
    fun `read247ppiSamples() should correctly calculate ppi samples and parse all sample status types`() {
        // Arrange
        val fromDate = Calendar.getInstance().apply { set(2024, 10, 10, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.time
        val toDate = Calendar.getInstance().apply { set(2024, 10, 18, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.time

        val mockDirectoryContent = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(
                    listOf(
                        PbPFtpEntry.newBuilder().setName("AUTOS000.BPB").setSize(333L).build(),
                        PbPFtpEntry.newBuilder().setName("AUTOS001.BPB").setSize(444L).build(),
                    )
                ).build().writeTo(this)
        }

        val mockFileContent1 = ByteArrayOutputStream().apply {
            PbAutomaticSampleSessions.newBuilder()
                .addAllPpiSamples(
                    listOf(
                        PbPpIntervalAutoSamples.newBuilder()
                            .setRecordingTime(PbTime.newBuilder()
                                .setHour(1)
                                .setMinute(1)
                                .setSeconds(1)
                                .setMillis(1)
                                .build()
                            )
                            .setTriggerType(PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType.PPI_TRIGGER_TYPE_AUTOMATIC)
                            .setPpi(PbPpIntervalSamples.newBuilder()
                                .addAllPpiDelta(listOf(2500, -634, 20, -100))
                                .addAllPpiErrorEstimateDelta(listOf(700, 0, 600, -50))
                                .addAllStatus(listOf(1,2,3,4))
                                .build()
                            )
                            .build(),
                        PbPpIntervalAutoSamples.newBuilder()
                            .setRecordingTime(PbTime.newBuilder()
                                .setHour(2)
                                .setMinute(2)
                                .setSeconds(2)
                                .setMillis(2)
                                .build()
                            )
                            .setTriggerType(PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType.PPI_TRIGGER_TYPE_AUTOMATIC)
                            .setPpi(PbPpIntervalSamples.newBuilder()
                                .addAllPpiDelta(listOf(1333, 10, -133, -555))
                                .addAllPpiErrorEstimateDelta(listOf(500, 55, -55, -500))
                                .addAllStatus(listOf(1,2,3,4))
                                .build()
                            )
                            .build()
                    )
                ).setDay(
                    Types.PbDate.newBuilder()
                        .setYear(2024)
                        .setMonth(11)
                        .setDay(18)
                        .build()
                )
                .build()
                .writeTo(this)
        }

        val mockFileContent2 = ByteArrayOutputStream().apply {
            PbAutomaticSampleSessions.newBuilder()
                .addAllPpiSamples(
                    listOf(
                        PbPpIntervalAutoSamples.newBuilder()
                            .setRecordingTime(PbTime.newBuilder()
                                .setHour(1)
                                .setMinute(1)
                                .setSeconds(1)
                                .setMillis(1)
                                .build()
                            )
                            .setTriggerType(PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType.PPI_TRIGGER_TYPE_AUTOMATIC)
                            .setPpi(PbPpIntervalSamples.newBuilder()
                                .addAllPpiDelta(listOf(2500, -634, 20, -100))
                                .addAllPpiErrorEstimateDelta(listOf(700, 0, 600, -50))
                                .addAllStatus(listOf(1,2,3,4))
                                .build()
                            )
                            .build()
                    )
                ).setDay(
                    Types.PbDate.newBuilder()
                        .setYear(2024)
                        .setMonth(11)
                        .setDay(12)
                        .build()
                )
                .build()
                .writeTo(this)
        }

        every { mockClient.request(any<ByteArray>()) } returns Single.just(mockDirectoryContent) andThen Single.just(mockFileContent1) andThen Single.just(mockFileContent2)

        // Act
        val result = PolarAutomaticSamplesUtils.read247PPiSamples(mockClient, fromDate, toDate).blockingGet()

        // Assert
        verify { mockClient.request(any<ByteArray>()) }
        confirmVerified(mockClient)

        assert(result.size == 3)
        val date = Calendar.getInstance().apply { set(2024, 10, 18, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
        assert(result[0].date == date.time)
        assert(result[0].samples.startTime == LocalTime.parse("01:01:01"))
        assert(result[0].samples.ppiValueList == listOf(2500, 1866, 1886, 1786))
        assert(result[0].samples.ppiErrorEstimateList == listOf(700, 700, 1300, 1250))
        assert(result[0].samples.triggerType == PPiSampleTriggerType.TRIGGER_TYPE_AUTOMATIC)
        assert(result[0].samples.statusList[0] == PPiSampleStatus(skinContact = SkinContact.SKIN_CONTACT_DETECTED, movement = Movement.NO_MOVING_DETECTED, intervalStatus = IntervalStatus.INTERVAL_IS_ONLINE))
        assert(result[0].samples.statusList[1] == PPiSampleStatus(skinContact = SkinContact.NO_SKIN_CONTACT, movement = Movement.MOVING_DETECTED, intervalStatus = IntervalStatus.INTERVAL_IS_ONLINE))
        assert(result[0].samples.statusList[2] == PPiSampleStatus(skinContact = SkinContact.SKIN_CONTACT_DETECTED, movement = Movement.MOVING_DETECTED, intervalStatus = IntervalStatus.INTERVAL_IS_ONLINE))
        assert(result[0].samples.statusList[3] == PPiSampleStatus(skinContact = SkinContact.NO_SKIN_CONTACT, movement = Movement.NO_MOVING_DETECTED, intervalStatus = IntervalStatus.INTERVAL_DENOTES_OFFLINE_PERIOD))
    }

    @Test
    fun `read247ppiSamples() should filter out dates outside of range`() {
        // Arrange
        val fromDate = Calendar.getInstance().apply { set(2024, 10, 10, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.time
        val toDate = Calendar.getInstance().apply { set(2024, 10, 18, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.time

        val mockDirectoryContent = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(
                    listOf(
                        PbPFtpEntry.newBuilder().setName("AUTOS000.BPB").setSize(333L).build(),
                        PbPFtpEntry.newBuilder().setName("AUTOS001.BPB").setSize(444L).build(),
                        PbPFtpEntry.newBuilder().setName("AUTOS002.BPB").setSize(555L).build(),
                    )
                ).build().writeTo(this)
        }

        val mockFileContentBeforeFromDate = ByteArrayOutputStream().apply {
            PbAutomaticSampleSessions.newBuilder()
                .addAllPpiSamples(
                    listOf(
                        PbPpIntervalAutoSamples.newBuilder()
                            .setRecordingTime(PbTime.newBuilder()
                                .setHour(1)
                                .setMinute(1)
                                .setSeconds(1)
                                .setMillis(1)
                                .build()
                            )
                            .setTriggerType(PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType.PPI_TRIGGER_TYPE_AUTOMATIC)
                            .setPpi(PbPpIntervalSamples.newBuilder()
                                .addAllPpiDelta(listOf(2500, -634, 20, -100))
                                .addAllPpiErrorEstimateDelta(listOf(700, 0, 600, -50))
                                .addAllStatus(listOf(1,2,3,4))
                                .build()
                            )
                            .build()
                    )
                ).setDay(
                    Types.PbDate.newBuilder()
                        .setYear(2024)
                        .setMonth(11)
                        .setDay(9)
                        .build()
                )
                .build()
                .writeTo(this)
        }

        val mockFileContentAfterToDate = ByteArrayOutputStream().apply {
            PbAutomaticSampleSessions.newBuilder()
                .addAllPpiSamples(
                    listOf(
                        PbPpIntervalAutoSamples.newBuilder()
                            .setRecordingTime(PbTime.newBuilder()
                                .setHour(1)
                                .setMinute(1)
                                .setSeconds(1)
                                .setMillis(1)
                                .build()
                            )
                            .setTriggerType(PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType.PPI_TRIGGER_TYPE_AUTOMATIC)
                            .setPpi(PbPpIntervalSamples.newBuilder()
                                .addAllPpiDelta(listOf(2500, -634, 20, -100))
                                .addAllPpiErrorEstimateDelta(listOf(700, 0, 600, -50))
                                .addAllStatus(listOf(1,2,3,4))
                                .build()
                            )
                            .build()
                    )
                ).setDay(
                    Types.PbDate.newBuilder()
                        .setYear(2024)
                        .setMonth(11)
                        .setDay(19)
                        .build()
                )
                .build()
                .writeTo(this)
        }

        val mockFileContentInsideRange = ByteArrayOutputStream().apply {
            PbAutomaticSampleSessions.newBuilder()
                .addAllPpiSamples(
                    listOf(
                        PbPpIntervalAutoSamples.newBuilder()
                            .setRecordingTime(PbTime.newBuilder()
                                .setHour(1)
                                .setMinute(1)
                                .setSeconds(1)
                                .setMillis(1)
                                .build()
                            )
                            .setTriggerType(PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType.PPI_TRIGGER_TYPE_AUTOMATIC)
                            .setPpi(PbPpIntervalSamples.newBuilder()
                                .addAllPpiDelta(listOf(2500, -634, 20, -100))
                                .addAllPpiErrorEstimateDelta(listOf(700, 0, 600, -50))
                                .addAllStatus(listOf(1,2,3,4))
                                .build()
                            )
                            .build()
                    )
                ).setDay(
                    Types.PbDate.newBuilder()
                        .setYear(2024)
                        .setMonth(11)
                        .setDay(15)
                        .build()
                )
                .build()
                .writeTo(this)
        }

        every { mockClient.request(any<ByteArray>()) } returns Single.just(mockDirectoryContent) andThen Single.just(mockFileContentBeforeFromDate) andThen Single.just(mockFileContentAfterToDate) andThen Single.just(mockFileContentInsideRange)

        // Act
        val result = PolarAutomaticSamplesUtils.read247PPiSamples(mockClient, fromDate, toDate).blockingGet()

        // Assert
        verify { mockClient.request(any<ByteArray>()) }
        confirmVerified(mockClient)

        assert(result.size == 1)
    }
}