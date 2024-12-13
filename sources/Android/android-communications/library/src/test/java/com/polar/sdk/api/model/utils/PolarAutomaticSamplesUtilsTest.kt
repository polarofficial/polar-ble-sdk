package com.polar.sdk.api.model.utils

import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.activity.AutomaticSampleTriggerType
import com.polar.sdk.impl.utils.PolarAutomaticSamplesUtils
import fi.polar.remote.representation.protobuf.AutomaticSamples.*
import fi.polar.remote.representation.protobuf.Types
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.rxjava3.core.Single
import org.junit.Test
import protocol.PftpResponse.*
import java.io.ByteArrayOutputStream
import java.util.*

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
}