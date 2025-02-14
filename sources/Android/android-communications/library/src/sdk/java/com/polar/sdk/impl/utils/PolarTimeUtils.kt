package com.polar.sdk.impl.utils

import fi.polar.remote.representation.protobuf.Types.PbDate
import fi.polar.remote.representation.protobuf.Types.PbLocalDateTime
import fi.polar.remote.representation.protobuf.Types.PbSystemDateTime
import fi.polar.remote.representation.protobuf.Types.PbTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import java.time.LocalDate
import java.time.LocalDateTime
import protocol.PftpRequest
import java.time.LocalTime
import java.util.*
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
            .tzOffset = TimeUnit.MINUTES.convert(calendar[Calendar.ZONE_OFFSET].toLong()
                + calendar[Calendar.DST_OFFSET].toLong(), TimeUnit.MILLISECONDS).toInt()

        return builder.build()
    }

    fun javaCalendarToPbPftpSetSystemTime(calendar: Calendar): PftpRequest.PbPFtpSetSystemTimeParams {
        val dt = DateTime(calendar)
        val utcTime: DateTime = dt.withZone(DateTimeZone.forID("UTC"))
        val builder = PftpRequest.PbPFtpSetSystemTimeParams.newBuilder()
        val date = PbDate.newBuilder()
        val time = PbTime.newBuilder()

        date.day = utcTime.dayOfMonth
        date.month = utcTime.monthOfYear
        date.year = utcTime.year

        time.hour = utcTime.hourOfDay
        time.minute = utcTime.minuteOfHour
        time.seconds = utcTime.secondOfMinute
        time.millis = utcTime.millisOfSecond

        builder.setDate(date)
        builder.setTime(time)
        builder.trusted = true
        return builder.build()
    }

    fun pbLocalTimeToJavaCalendar(pbLocalTime: PftpRequest.PbPFtpSetLocalTimeParams): Calendar {
        val offsetInMillis = TimeUnit.MILLISECONDS.convert(pbLocalTime.tzOffset.toLong(), TimeUnit.MINUTES)
        val timeZone = DateTimeZone.forOffsetMillis(offsetInMillis.toInt())
        val dt = DateTime(
            pbLocalTime.date.year,
            pbLocalTime.date.month,
            pbLocalTime.date.day,
            pbLocalTime.time.hour,
            pbLocalTime.time.minute,
            pbLocalTime.time.seconds,
            pbLocalTime.time.millis,
            timeZone
        )
        return dt.toCalendar(null)
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

    fun pbDateToLocalDate(pbDate: PbDate): LocalDate {
        return LocalDate.of(pbDate.year, pbDate.month, pbDate.day)
    }

    private fun pbLocalTimeToJavaCalendarWithTimezone(pbLocalTime: PftpRequest.PbPFtpSetLocalTimeParams): Calendar {
        val offsetInMillis = TimeUnit.MILLISECONDS.convert(pbLocalTime.tzOffset.toLong(), TimeUnit.MINUTES)
        val timeZone = DateTimeZone.forOffsetMillis(offsetInMillis.toInt())
        val dt = DateTime(
            pbLocalTime.date.year,
            pbLocalTime.date.month,
            pbLocalTime.date.day,
            pbLocalTime.time.hour,
            pbLocalTime.time.minute,
            pbLocalTime.time.seconds,
            pbLocalTime.time.millis,
            timeZone
        )

        return dt.toCalendar(null)
    }

    fun pbTimeToLocalTime(pbTime: PbTime): LocalTime {

        return LocalTime.of(
            pbTime.hour,
            pbTime.minute,
            pbTime.seconds
        )
    }
}