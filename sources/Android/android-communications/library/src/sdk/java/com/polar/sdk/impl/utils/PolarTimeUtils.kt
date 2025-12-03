package com.polar.sdk.impl.utils

import fi.polar.remote.representation.protobuf.Types.PbDate
import fi.polar.remote.representation.protobuf.Types.PbDuration
import fi.polar.remote.representation.protobuf.Types.PbLocalDateTime
import fi.polar.remote.representation.protobuf.Types.PbSystemDateTime
import fi.polar.remote.representation.protobuf.Types.PbTime
import java.time.LocalDate
import java.time.LocalDateTime
import protocol.PftpRequest
import java.time.Instant
import java.time.LocalTime
import java.util.*
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.ZoneId
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

internal object PolarTimeUtils {
    private const val TAG = "PolarTimeUtils"

    fun javaCalendarToPbPftpSetLocalTime(calendar: Calendar): PftpRequest.PbPFtpSetLocalTimeParams {
        val builder = PftpRequest.PbPFtpSetLocalTimeParams.newBuilder()
        val date = PbDate.newBuilder()
        val time = PbTime.newBuilder()
        date.day = calendar[Calendar.DAY_OF_MONTH]
        date.month = calendar[Calendar.MONTH] + 1
        date.year = calendar[Calendar.YEAR]

        time.hour = calendar[Calendar.HOUR_OF_DAY]
        time.minute = calendar[Calendar.MINUTE]
        time.seconds = calendar[Calendar.SECOND]
        time.millis = calendar[Calendar.MILLISECOND]

        builder
            .setDate(date)
            .setTime(time)
            .tzOffset = TimeUnit.MINUTES.convert(
            calendar[Calendar.ZONE_OFFSET].toLong()
                    + calendar[Calendar.DST_OFFSET].toLong(), TimeUnit.MILLISECONDS
        ).toInt()

        return builder.build()
    }

    fun javaCalendarToPbPftpSetSystemTime(calendar: Calendar): PftpRequest.PbPFtpSetSystemTimeParams {
        val utcTime = calendar.toInstant().atZone(ZoneId.of("UTC"))

        val builder = PftpRequest.PbPFtpSetSystemTimeParams.newBuilder()
        val date = PbDate.newBuilder()
        val time = PbTime.newBuilder()

        date.day = utcTime.dayOfMonth
        date.month = utcTime.monthValue
        date.year = utcTime.year

        time.hour = utcTime.hour
        time.minute = utcTime.minute
        time.seconds = utcTime.second
        time.millis = utcTime.nano / 1_000_000

        builder.setDate(date)
        builder.setTime(time)
        builder.trusted = true
        return builder.build()
    }

    fun javaInstantToPbPftpSetSystemTime(instant: Instant): PbSystemDateTime {
        val utcTime = instant.atZone(ZoneId.of("UTC"))

        val builder = PbSystemDateTime.newBuilder()
        val date = PbDate.newBuilder()
        val time = PbTime.newBuilder()

        date.day = utcTime.dayOfMonth
        date.month = utcTime.monthValue
        date.year = utcTime.year

        time.hour = utcTime.hour
        time.minute = utcTime.minute
        time.seconds = utcTime.second
        time.millis = utcTime.nano / 1_000_000

        builder.setDate(date)
        builder.setTime(time)
        builder.trusted = true
        return builder.build()
    }

    fun pbLocalTimeToJavaCalendar(pbLocalTime: PftpRequest.PbPFtpSetLocalTimeParams): Calendar {
        val offsetInMillis =
            TimeUnit.MILLISECONDS.convert(pbLocalTime.tzOffset.toLong(), TimeUnit.MINUTES)
        val zoneOffset = ZoneOffset.ofTotalSeconds((offsetInMillis / 1000).toInt())
        val zdt = ZonedDateTime.of(
            pbLocalTime.date.year,
            pbLocalTime.date.month,
            pbLocalTime.date.day,
            pbLocalTime.time.hour,
            pbLocalTime.time.minute,
            pbLocalTime.time.seconds,
            pbLocalTime.time.millis * 1_000_000,
            zoneOffset
        )
        return Calendar.getInstance(TimeZone.getTimeZone(zoneOffset)).apply {
            timeInMillis = zdt.toInstant().toEpochMilli()
        }
    }

    fun pbLocalDateTimeToZonedDateTime(pbDateTime: PbLocalDateTime): ZonedDateTime {
        val zoneId = ZoneOffset.ofTotalSeconds(pbDateTime.timeZoneOffset * 60)
        return ZonedDateTime.of(
            pbDateTime.date.year,
            pbDateTime.date.month,
            pbDateTime.date.day,
            pbDateTime.time.hour,
            pbDateTime.time.minute,
            pbDateTime.time.seconds,
            pbDateTime.time.millis * 1000000,
            zoneId
        )
    }
    fun pbLocalDateTimeToLocalDateTime(pbDateTime: PbLocalDateTime): LocalDateTime {
        
        return LocalDateTime.of(
            pbDateTime.date.year,
            pbDateTime.date.month,
            pbDateTime.date.day,
            pbDateTime.time.hour,
            pbDateTime.time.minute,
            pbDateTime.time.seconds,
            pbDateTime.time.millis * 1000000
        )
    }

    fun pbSystemDateTimeToLocalDateTime(pbSystemDateTime: PbSystemDateTime): LocalDateTime {

        return LocalDateTime.of(
            pbSystemDateTime.date.year,
            pbSystemDateTime.date.month,
            pbSystemDateTime.date.day,
            pbSystemDateTime.time.hour,
            pbSystemDateTime.time.minute,
            pbSystemDateTime.time.seconds,
            pbSystemDateTime.time.millis * 1000000
        )
    }

    fun pbSystemDateTimeToZonedDateTime(pbSystemDateTime: PbSystemDateTime): ZonedDateTime {

        return ZonedDateTime.of(
            pbSystemDateTime.date.year,
            pbSystemDateTime.date.month,
            pbSystemDateTime.date.day,
            pbSystemDateTime.time.hour,
            pbSystemDateTime.time.minute,
            pbSystemDateTime.time.seconds,
            pbSystemDateTime.time.millis * 1000000,
            ZoneOffset.UTC
        )
    }

    fun pbDateToLocalDate(pbDate: PbDate): LocalDate {
        return LocalDate.of(pbDate.year, pbDate.month, pbDate.day)
    }

    fun pbTimeToLocalTime(pbTime: PbTime): LocalTime {

        return LocalTime.of(
            pbTime.hour,
            pbTime.minute,
            pbTime.seconds
        )
    }

    // Returns duration in milliseconds
    fun pbDurationToInt(pbDuration: PbDuration): Int {
        return (pbDuration.hours*3.6E6+pbDuration.minutes*6E4+pbDuration.seconds*1E3+pbDuration.millis).toInt()
    }
}