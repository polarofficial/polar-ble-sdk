package com.polar.sdk.impl.utils

import fi.polar.remote.representation.protobuf.Types
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import protocol.PftpRequest
import java.util.*
import java.util.concurrent.TimeUnit

internal object PolarTimeUtils {
    private const val TAG = "PolarTimeUtils"

    fun javaCalendarToPbPftpSetLocalTime(calendar: Calendar): PftpRequest.PbPFtpSetLocalTimeParams {
        val builder = PftpRequest.PbPFtpSetLocalTimeParams.newBuilder()
        val date = Types.PbDate.newBuilder()
        val time = Types.PbTime.newBuilder()
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
            .tzOffset = TimeUnit.MINUTES.convert(calendar[Calendar.ZONE_OFFSET].toLong(), TimeUnit.MILLISECONDS).toInt()

        return builder.build()
    }

    fun javaCalendarToPbPftpSetSystemTime(calendar: Calendar): PftpRequest.PbPFtpSetSystemTimeParams {
        val dt = DateTime(calendar)
        val utcTime: DateTime = dt.withZone(DateTimeZone.forID("UTC"))
        val builder = PftpRequest.PbPFtpSetSystemTimeParams.newBuilder()
        val date = Types.PbDate.newBuilder()
        val time = Types.PbTime.newBuilder()

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
}