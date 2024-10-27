package com.polar.sdk.api.model.utils

import com.polar.androidcommunications.testrules.BleLoggerTestRule
import com.polar.sdk.impl.utils.PolarTimeUtils
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import protocol.PftpRequest
import java.time.LocalDateTime
import fi.polar.remote.representation.protobuf.Types.PbDate
import fi.polar.remote.representation.protobuf.Types.PbLocalDateTime
import fi.polar.remote.representation.protobuf.Types.PbSystemDateTime
import fi.polar.remote.representation.protobuf.Types.PbTime
import java.time.LocalDate
import java.util.*
import java.util.concurrent.TimeUnit

internal class PolarTimeUtilsTest {
    @Rule
    @JvmField
    val bleLoggerTestRule = BleLoggerTestRule()

    @Test
    fun `test calendar conversion from GMT+1 to pbSystemTime`() {
        //Arrange
        val (localYear, pbUtcYear) = 2022 to 2022
        val (localMonth, pbUtcMonth) = Calendar.JANUARY to 1
        val (localDate, pbUtcDate) = 1 to 1
        val (localHourOfDay, pbUtcHourOfDay) = 11 to 10
        val (localMinute, pbUtcMinute) = 59 to 59
        val (localSecond, pbUtcSecond) = 1 to 1
        val (localMilliSecond, pbUtcMilliSecond) = 999 to 999

        val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+01:00"))
        cal.set(Calendar.YEAR, localYear)
        cal.set(Calendar.MONTH, localMonth)
        cal.set(Calendar.DAY_OF_MONTH, localDate)
        cal.set(Calendar.HOUR_OF_DAY, localHourOfDay)
        cal.set(Calendar.MINUTE, localMinute)
        cal.set(Calendar.SECOND, localSecond)
        cal.set(Calendar.MILLISECOND, localMilliSecond)

        //Act
        val result = PolarTimeUtils.javaCalendarToPbPftpSetSystemTime(cal)

        //Assert
        Assert.assertEquals(pbUtcYear, result.date.year)
        Assert.assertEquals(pbUtcMonth, result.date.month)
        Assert.assertEquals(pbUtcDate, result.date.day)
        Assert.assertEquals(pbUtcHourOfDay, result.time.hour)
        Assert.assertEquals(pbUtcMinute, result.time.minute)
        Assert.assertEquals(pbUtcSecond, result.time.seconds)
        Assert.assertEquals(pbUtcMilliSecond, result.time.millis)
        Assert.assertTrue(result.trusted)
    }

    @Test
    fun `test calendar conversion from GMT-1 to pbSystemTime`() {
        //Arrange
        val (localYear, pbUtcYear) = 2022 to 2022
        val (localMonth, pbUtcMonth) = Calendar.JANUARY to 1
        val (localDate, pbUtcDate) = 1 to 1
        val (localHourOfDay, pbUtcHourOfDay) = 11 to 12
        val (localMinute, pbUtcMinute) = 59 to 59
        val (localSecond, pbUtcSecond) = 1 to 1
        val (localMilliSecond, pbUtcMilliSecond) = 0 to 0

        val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT-01:00"))
        cal.set(Calendar.YEAR, localYear)
        cal.set(Calendar.MONTH, localMonth)
        cal.set(Calendar.DAY_OF_MONTH, localDate)
        cal.set(Calendar.HOUR_OF_DAY, localHourOfDay)
        cal.set(Calendar.MINUTE, localMinute)
        cal.set(Calendar.SECOND, localSecond)
        cal.set(Calendar.MILLISECOND, localMilliSecond)

        //Act
        val result = PolarTimeUtils.javaCalendarToPbPftpSetSystemTime(cal)

        //Assert
        Assert.assertEquals(pbUtcYear, result.date.year)
        Assert.assertEquals(pbUtcMonth, result.date.month)
        Assert.assertEquals(pbUtcDate, result.date.day)
        Assert.assertEquals(pbUtcHourOfDay, result.time.hour)
        Assert.assertEquals(pbUtcMinute, result.time.minute)
        Assert.assertEquals(pbUtcSecond, result.time.seconds)
        Assert.assertEquals(pbUtcMilliSecond, result.time.millis)
        Assert.assertTrue(result.trusted)
    }

    @Test
    fun `test calendar conversion to pbSystemTime when local date is tomorrow`() {
        //Arrange
        val (localYear, pbUtcYear) = 2022 to 2021
        val (localMonth, pbUtcMonth) = Calendar.JANUARY to 12
        val (localDate, pbUtcDate) = 1 to 31
        val (localHourOfDay, pbUtcHourOfDay) = 1 to 22
        val (localMinute, pbUtcMinute) = 59 to 59
        val (localSecond, pbUtcSecond) = 1 to 1
        val (localMilliSecond, pbUtcMilliSecond) = 99 to 99

        val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+03:00"))
        cal.set(Calendar.YEAR, localYear)
        cal.set(Calendar.MONTH, localMonth)
        cal.set(Calendar.DAY_OF_MONTH, localDate)
        cal.set(Calendar.HOUR_OF_DAY, localHourOfDay)
        cal.set(Calendar.MINUTE, localMinute)
        cal.set(Calendar.SECOND, localSecond)
        cal.set(Calendar.MILLISECOND, localMilliSecond)

        //Act
        val result = PolarTimeUtils.javaCalendarToPbPftpSetSystemTime(cal)

        //Assert
        Assert.assertEquals(pbUtcYear, result.date.year)
        Assert.assertEquals(pbUtcMonth, result.date.month)
        Assert.assertEquals(pbUtcDate, result.date.day)
        Assert.assertEquals(pbUtcHourOfDay, result.time.hour)
        Assert.assertEquals(pbUtcMinute, result.time.minute)
        Assert.assertEquals(pbUtcSecond, result.time.seconds)
        Assert.assertEquals(pbUtcMilliSecond, result.time.millis)
        Assert.assertTrue(result.trusted)
    }

    @Test
    fun `test calendar conversion to pbSystemTime when local date is yesterday`() {
        //Arrange
        val (localYear, pbUtcYear) = 2021 to 2022
        val (localMonth, pbUtcMonth) = Calendar.DECEMBER to 1
        val (localDate, pbUtcDate) = 31 to 1
        val (localHourOfDay, pbUtcHourOfDay) = 22 to 1
        val (localMinute, pbUtcMinute) = 59 to 59
        val (localSecond, pbUtcSecond) = 1 to 1
        val (localMilliSecond, pbUtcMilliSecond) = 1 to 1

        val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT-03:00"))
        cal.set(Calendar.YEAR, localYear)
        cal.set(Calendar.MONTH, localMonth)
        cal.set(Calendar.DAY_OF_MONTH, localDate)
        cal.set(Calendar.HOUR_OF_DAY, localHourOfDay)
        cal.set(Calendar.MINUTE, localMinute)
        cal.set(Calendar.SECOND, localSecond)
        cal.set(Calendar.MILLISECOND, localMilliSecond)

        //Act
        val result = PolarTimeUtils.javaCalendarToPbPftpSetSystemTime(cal)

        //Assert
        Assert.assertEquals(pbUtcYear, result.date.year)
        Assert.assertEquals(pbUtcMonth, result.date.month)
        Assert.assertEquals(pbUtcDate, result.date.day)
        Assert.assertEquals(pbUtcHourOfDay, result.time.hour)
        Assert.assertEquals(pbUtcMinute, result.time.minute)
        Assert.assertEquals(pbUtcSecond, result.time.seconds)
        Assert.assertEquals(pbUtcMilliSecond, result.time.millis)
        Assert.assertTrue(result.trusted)
    }

    @Test
    fun `test calendar conversion to pbSystemTime when local date is leap date `() {
        //Arrange
        val (localYear, pbUtcYear) = 2024 to 2024
        val (localMonth, pbUtcMonth) = Calendar.FEBRUARY to 2
        val (localDate, pbUtcDate) = 29 to 29
        val (localHourOfDay, pbUtcHourOfDay) = 22 to 21
        val (localMinute, pbUtcMinute) = 59 to 59
        val (localSecond, pbUtcSecond) = 1 to 1
        val (localMilliSecond, pbUtcMilliSecond) = 1 to 1

        val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+01:00"))
        cal.set(Calendar.YEAR, localYear)
        cal.set(Calendar.MONTH, localMonth)
        cal.set(Calendar.DAY_OF_MONTH, localDate)
        cal.set(Calendar.HOUR_OF_DAY, localHourOfDay)
        cal.set(Calendar.MINUTE, localMinute)
        cal.set(Calendar.SECOND, localSecond)
        cal.set(Calendar.MILLISECOND, localMilliSecond)

        //Act
        val result = PolarTimeUtils.javaCalendarToPbPftpSetSystemTime(cal)

        //Assert
        Assert.assertEquals(pbUtcYear, result.date.year)
        Assert.assertEquals(pbUtcMonth, result.date.month)
        Assert.assertEquals(pbUtcDate, result.date.day)
        Assert.assertEquals(pbUtcHourOfDay, result.time.hour)
        Assert.assertEquals(pbUtcMinute, result.time.minute)
        Assert.assertEquals(pbUtcSecond, result.time.seconds)
        Assert.assertEquals(pbUtcMilliSecond, result.time.millis)
        Assert.assertTrue(result.trusted)
    }

    @Test
    fun `test calendar conversion to pbSystemTime when dst +1hour `() {
        //Arrange
        val (localYear, pbUtcYear) = 2024 to 2024
        val (localMonth, pbUtcMonth) = Calendar.FEBRUARY to 2
        val (localDate, pbUtcDate) = 29 to 29
        val (localHourOfDay, pbUtcHourOfDay) = 22 to 20
        val (localMinute, pbUtcMinute) = 59 to 59
        val (localSecond, pbUtcSecond) = 1 to 1
        val (localMilliSecond, pbUtcMilliSecond) = 1 to 1

        val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+01:00"))
        cal.set(Calendar.YEAR, localYear)
        cal.set(Calendar.MONTH, localMonth)
        cal.set(Calendar.DAY_OF_MONTH, localDate)
        cal.set(Calendar.HOUR_OF_DAY, localHourOfDay)
        cal.set(Calendar.MINUTE, localMinute)
        cal.set(Calendar.SECOND, localSecond)
        cal.set(Calendar.MILLISECOND, localMilliSecond)
        cal.set(Calendar.DST_OFFSET, TimeUnit.MILLISECONDS.convert(1L, TimeUnit.HOURS).toInt())

        //Act
        val result = PolarTimeUtils.javaCalendarToPbPftpSetSystemTime(cal)

        //Assert
        Assert.assertEquals(pbUtcYear, result.date.year)
        Assert.assertEquals(pbUtcMonth, result.date.month)
        Assert.assertEquals(pbUtcDate, result.date.day)
        Assert.assertEquals(pbUtcHourOfDay, result.time.hour)
        Assert.assertEquals(pbUtcMinute, result.time.minute)
        Assert.assertEquals(pbUtcSecond, result.time.seconds)
        Assert.assertEquals(pbUtcMilliSecond, result.time.millis)
        Assert.assertTrue(result.trusted)
    }

    @Test
    fun `test calendar conversion from GMT+1 to PbPftpSetLocalTime`() {
        //Arrange
        val (localYear, pbLocalYear) = 2022 to 2022
        val (localMonth, pbLocalMonth) = Calendar.JANUARY to 1
        val (localDate, pbLocalDate) = 1 to 1
        val (localHourOfDay, pbLocalHourOfDay) = 11 to 11
        val (localMinute, pbLocalMinute) = 59 to 59
        val (localSecond, pbLocalSecond) = 1 to 1
        val (localMilliSecond, pbLocalMilliSecond) = 0 to 0
        val (localTimeZone, pbLocalTimeZone) = "GMT+1:00" to 60

        val cal = Calendar.getInstance(TimeZone.getTimeZone(localTimeZone))
        cal.set(Calendar.YEAR, localYear)
        cal.set(Calendar.MONTH, localMonth)
        cal.set(Calendar.DAY_OF_MONTH, localDate)
        cal.set(Calendar.HOUR_OF_DAY, localHourOfDay)
        cal.set(Calendar.MINUTE, localMinute)
        cal.set(Calendar.SECOND, localSecond)
        cal.set(Calendar.MILLISECOND, localMilliSecond)

        //Act
        val result = PolarTimeUtils.javaCalendarToPbPftpSetLocalTime(cal)

        //Assert
        Assert.assertEquals(pbLocalYear, result.date.year)
        Assert.assertEquals(pbLocalMonth, result.date.month)
        Assert.assertEquals(pbLocalDate, result.date.day)
        Assert.assertEquals(pbLocalHourOfDay, result.time.hour)
        Assert.assertEquals(pbLocalMinute, result.time.minute)
        Assert.assertEquals(pbLocalSecond, result.time.seconds)
        Assert.assertEquals(pbLocalMilliSecond, result.time.millis)
        Assert.assertEquals(pbLocalTimeZone, result.tzOffset)
    }

    @Test
    fun `test calendar conversion from GMT-1 to PbLocalTime`() {
        //Arrange
        val (localYear, pbLocalYear) = 2022 to 2022
        val (localMonth, pbLocalMonth) = Calendar.JANUARY to 1
        val (localDate, pbLocalDate) = 1 to 1
        val (localHourOfDay, pbLocalHourOfDay) = 11 to 11
        val (localMinute, pbLocalMinute) = 59 to 59
        val (localSecond, pbLocalSecond) = 1 to 1
        val (localMilliSecond, pbLocalMilliSecond) = 499 to 499
        val (localTimeZone, pbLocalTimeZone) = "GMT-1:00" to -60

        val cal = Calendar.getInstance(TimeZone.getTimeZone(localTimeZone))
        cal.set(Calendar.YEAR, localYear)
        cal.set(Calendar.MONTH, localMonth)
        cal.set(Calendar.DAY_OF_MONTH, localDate)
        cal.set(Calendar.HOUR_OF_DAY, localHourOfDay)
        cal.set(Calendar.MINUTE, localMinute)
        cal.set(Calendar.SECOND, localSecond)
        cal.set(Calendar.MILLISECOND, localMilliSecond)

        //Act
        val result = PolarTimeUtils.javaCalendarToPbPftpSetLocalTime(cal)

        //Assert
        Assert.assertEquals(pbLocalYear, result.date.year)
        Assert.assertEquals(pbLocalMonth, result.date.month)
        Assert.assertEquals(pbLocalDate, result.date.day)
        Assert.assertEquals(pbLocalHourOfDay, result.time.hour)
        Assert.assertEquals(pbLocalMinute, result.time.minute)
        Assert.assertEquals(pbLocalSecond, result.time.seconds)
        Assert.assertEquals(pbLocalMilliSecond, result.time.millis)
        Assert.assertEquals(pbLocalTimeZone, result.tzOffset)
    }

    @Test
    fun `test calendar conversion from GMT-12 to PbLocalTime`() {
        //Arrange
        val (localYear, pbLocalYear) = 2022 to 2022
        val (localMonth, pbLocalMonth) = Calendar.JANUARY to 1
        val (localDate, pbLocalDate) = 1 to 1
        val (localHourOfDay, pbLocalHourOfDay) = 11 to 11
        val (localMinute, pbLocalMinute) = 59 to 59
        val (localSecond, pbLocalSecond) = 1 to 1
        val (localMilliSecond, pbLocalMilliSecond) = 999 to 999
        val (localTimeZone, pbLocalTimeZone) = "GMT-12:00" to -720

        val cal = Calendar.getInstance(TimeZone.getTimeZone(localTimeZone))
        cal.set(Calendar.YEAR, localYear)
        cal.set(Calendar.MONTH, localMonth)
        cal.set(Calendar.DAY_OF_MONTH, localDate)
        cal.set(Calendar.HOUR_OF_DAY, localHourOfDay)
        cal.set(Calendar.MINUTE, localMinute)
        cal.set(Calendar.SECOND, localSecond)
        cal.set(Calendar.MILLISECOND, localMilliSecond)

        //Act
        val result = PolarTimeUtils.javaCalendarToPbPftpSetLocalTime(cal)

        //Assert
        Assert.assertEquals(pbLocalYear, result.date.year)
        Assert.assertEquals(pbLocalMonth, result.date.month)
        Assert.assertEquals(pbLocalDate, result.date.day)
        Assert.assertEquals(pbLocalHourOfDay, result.time.hour)
        Assert.assertEquals(pbLocalMinute, result.time.minute)
        Assert.assertEquals(pbLocalSecond, result.time.seconds)
        Assert.assertEquals(pbLocalMilliSecond, result.time.millis)
        Assert.assertEquals(pbLocalTimeZone, result.tzOffset)

    }

    @Test
    fun `test calendar conversion from GMT+14 to PbLocalTime`() {
        //Arrange
        val (localYear, pbLocalYear) = 2022 to 2022
        val (localMonth, pbLocalMonth) = Calendar.JANUARY to 1
        val (localDate, pbLocalDate) = 1 to 1
        val (localHourOfDay, pbLocalHourOfDay) = 23 to 23
        val (localMinute, pbLocalMinute) = 59 to 59
        val (localSecond, pbLocalSecond) = 1 to 1
        val (localMilliSecond, pbLocalMilliSecond) = 1 to 1
        val (localTimeZone, pbLocalTimeZone) = "GMT+14:00" to 840

        val cal = Calendar.getInstance(TimeZone.getTimeZone(localTimeZone))
        cal.set(Calendar.YEAR, localYear)
        cal.set(Calendar.MONTH, localMonth)
        cal.set(Calendar.DAY_OF_MONTH, localDate)
        cal.set(Calendar.HOUR_OF_DAY, localHourOfDay)
        cal.set(Calendar.MINUTE, localMinute)
        cal.set(Calendar.SECOND, localSecond)
        cal.set(Calendar.MILLISECOND, localMilliSecond)

        //Act
        val result = PolarTimeUtils.javaCalendarToPbPftpSetLocalTime(cal)

        //Assert
        Assert.assertEquals(pbLocalYear, result.date.year)
        Assert.assertEquals(pbLocalMonth, result.date.month)
        Assert.assertEquals(pbLocalDate, result.date.day)
        Assert.assertEquals(pbLocalHourOfDay, result.time.hour)
        Assert.assertEquals(pbLocalMinute, result.time.minute)
        Assert.assertEquals(pbLocalSecond, result.time.seconds)
        Assert.assertEquals(pbLocalMilliSecond, result.time.millis)
        Assert.assertEquals(pbLocalTimeZone, result.tzOffset)
    }

    @Test
    fun `test PbLocalTime conversion from GMT+1 to calendar `() {
        //Arrange
        val (pbLocalYear, localYear) = 2022 to 2022
        val (pbLocalMonth, localMonth) = 1 to Calendar.JANUARY
        val (pbLocalDate, localDate) = 1 to 1
        val (pbLocalHourOfDay, localHourOfDay) = 23 to 23
        val (pbLocalMinute, localMinute) = 59 to 59
        val (pbLocalSecond, localSecond) = 1 to 1
        val (pbLocalMilliSecond, localMilliSecond) = 1 to 1
        val (pbLocalTimeZone, localTimeZone) = 60 to "GMT+01:00"

        val builder = PftpRequest.PbPFtpSetLocalTimeParams.newBuilder()
        val date = PbDate.newBuilder()
        val time = PbTime.newBuilder()
        date.day = pbLocalDate
        date.month = pbLocalMonth
        date.year = pbLocalYear

        time.hour = pbLocalHourOfDay
        time.minute = pbLocalMinute
        time.seconds = pbLocalSecond
        time.millis = pbLocalMilliSecond

        val pbLocalTime = builder
            .setDate(date)
            .setTime(time)
            .setTzOffset(pbLocalTimeZone)
            .build()

        //Act
        val result = PolarTimeUtils.pbLocalTimeToJavaCalendar(pbLocalTime)

        //Assert
        Assert.assertEquals(localYear, result[Calendar.YEAR])
        Assert.assertEquals(localMonth, result[Calendar.MONTH])
        Assert.assertEquals(localDate, result[Calendar.DAY_OF_MONTH])
        Assert.assertEquals(localHourOfDay, result[Calendar.HOUR_OF_DAY])
        Assert.assertEquals(localMinute, result[Calendar.MINUTE])
        Assert.assertEquals(localSecond, result[Calendar.SECOND])
        Assert.assertEquals(localMilliSecond, result[Calendar.MILLISECOND])
        Assert.assertEquals(localTimeZone, result.timeZone.id)
    }

    @Test
    fun `test PbLocalTime conversion from GMT-1 to calendar `() {
        //Arrange
        val (pbLocalYear, localYear) = 2022 to 2022
        val (pbLocalMonth, localMonth) = 2 to Calendar.FEBRUARY
        val (pbLocalDate, localDate) = 1 to 1
        val (pbLocalHourOfDay, localHourOfDay) = 23 to 23
        val (pbLocalMinute, localMinute) = 59 to 59
        val (pbLocalSecond, localSecond) = 1 to 1
        val (pbLocalMilliSecond, localMilliSecond) = 999 to 999
        val (pbLocalTimeZone, localTimeZone) = -60 to "GMT-01:00"

        val builder = PftpRequest.PbPFtpSetLocalTimeParams.newBuilder()
        val date = PbDate.newBuilder()
        val time = PbTime.newBuilder()
        date.day = pbLocalDate
        date.month = pbLocalMonth
        date.year = pbLocalYear

        time.hour = pbLocalHourOfDay
        time.minute = pbLocalMinute
        time.seconds = pbLocalSecond
        time.millis = pbLocalMilliSecond

        val pbLocalTime = builder
            .setDate(date)
            .setTime(time)
            .setTzOffset(pbLocalTimeZone)
            .build()

        //Act
        val result = PolarTimeUtils.pbLocalTimeToJavaCalendar(pbLocalTime)

        //Assert
        Assert.assertEquals(localYear, result[Calendar.YEAR])
        Assert.assertEquals(localMonth, result[Calendar.MONTH])
        Assert.assertEquals(localDate, result[Calendar.DAY_OF_MONTH])
        Assert.assertEquals(localHourOfDay, result[Calendar.HOUR_OF_DAY])
        Assert.assertEquals(localMinute, result[Calendar.MINUTE])
        Assert.assertEquals(localSecond, result[Calendar.SECOND])
        Assert.assertEquals(localMilliSecond, result[Calendar.MILLISECOND])
        Assert.assertEquals(localTimeZone, result.timeZone.id)
    }

    @Test
    fun `test PbLocalTime conversion from GMT+14 to calendar `() {
        //Arrange
        val (pbLocalYear, localYear) = 2025 to 2025
        val (pbLocalMonth, localMonth) = 12 to Calendar.DECEMBER
        val (pbLocalDate, localDate) = 1 to 1
        val (pbLocalHourOfDay, localHourOfDay) = 11 to 11
        val (pbLocalMinute, localMinute) = 59 to 59
        val (pbLocalSecond, localSecond) = 59 to 59
        val (pbLocalMilliSecond, localMilliSecond) = 1 to 1
        val (pbLocalTimeZone, localTimeZone) = 840 to "GMT+14:00"

        val builder = PftpRequest.PbPFtpSetLocalTimeParams.newBuilder()
        val date = PbDate.newBuilder()
        val time = PbTime.newBuilder()
        date.day = pbLocalDate
        date.month = pbLocalMonth
        date.year = pbLocalYear

        time.hour = pbLocalHourOfDay
        time.minute = pbLocalMinute
        time.seconds = pbLocalSecond
        time.millis = pbLocalMilliSecond

        val pbLocalTime = builder
            .setDate(date)
            .setTime(time)
            .setTzOffset(pbLocalTimeZone)
            .build()

        //Act
        val result = PolarTimeUtils.pbLocalTimeToJavaCalendar(pbLocalTime)

        //Assert
        Assert.assertEquals(localYear, result[Calendar.YEAR])
        Assert.assertEquals(localMonth, result[Calendar.MONTH])
        Assert.assertEquals(localDate, result[Calendar.DAY_OF_MONTH])
        Assert.assertEquals(localHourOfDay, result[Calendar.HOUR_OF_DAY])
        Assert.assertEquals(localMinute, result[Calendar.MINUTE])
        Assert.assertEquals(localSecond, result[Calendar.SECOND])
        Assert.assertEquals(localMilliSecond, result[Calendar.MILLISECOND])
        Assert.assertEquals(localTimeZone, result.timeZone.id)
    }

    @Test
    fun `test PbLocalTime conversion from GMT-12 to calendar `() {
        //Arrange
        val (pbLocalYear, localYear) = 2025 to 2025
        val (pbLocalMonth, localMonth) = 12 to Calendar.DECEMBER
        val (pbLocalDate, localDate) = 1 to 1
        val (pbLocalHourOfDay, localHourOfDay) = 11 to 11
        val (pbLocalMinute, localMinute) = 59 to 59
        val (pbLocalSecond, localSecond) = 59 to 59
        val (pbLocalMilliSecond, localMilliSecond) = 1 to 1
        val (pbLocalTimeZone, localTimeZone) = -720 to "GMT-12:00"

        val builder = PftpRequest.PbPFtpSetLocalTimeParams.newBuilder()
        val date = PbDate.newBuilder()
        val time = PbTime.newBuilder()
        date.day = pbLocalDate
        date.month = pbLocalMonth
        date.year = pbLocalYear

        time.hour = pbLocalHourOfDay
        time.minute = pbLocalMinute
        time.seconds = pbLocalSecond
        time.millis = pbLocalMilliSecond

        val pbLocalTime = builder
            .setDate(date)
            .setTime(time)
            .setTzOffset(pbLocalTimeZone)
            .build()

        //Act
        val result = PolarTimeUtils.pbLocalTimeToJavaCalendar(pbLocalTime)

        //Assert
        Assert.assertEquals(localYear, result[Calendar.YEAR])
        Assert.assertEquals(localMonth, result[Calendar.MONTH])
        Assert.assertEquals(localDate, result[Calendar.DAY_OF_MONTH])
        Assert.assertEquals(localHourOfDay, result[Calendar.HOUR_OF_DAY])
        Assert.assertEquals(localMinute, result[Calendar.MINUTE])
        Assert.assertEquals(localSecond, result[Calendar.SECOND])
        Assert.assertEquals(localMilliSecond, result[Calendar.MILLISECOND])
        Assert.assertEquals(localTimeZone, result.timeZone.id)
    }

    @Test
    fun `test PbLocalTime conversion from from leap day to calendar `() {
        //Arrange
        val (pbLocalYear, localYear) = 2024 to 2024
        val (pbLocalMonth, localMonth) = 2 to Calendar.FEBRUARY
        val (pbLocalDate, localDate) = 29 to 29
        val (pbLocalHourOfDay, localHourOfDay) = 21 to 21
        val (pbLocalMinute, localMinute) = 59 to 59
        val (pbLocalSecond, localSecond) = 59 to 59
        val (pbLocalMilliSecond, localMilliSecond) = 1 to 1
        val (pbLocalTimeZone, localTimeZone) = 720 to "GMT+12:00"

        val builder = PftpRequest.PbPFtpSetLocalTimeParams.newBuilder()
        val date = PbDate.newBuilder()
        val time = PbTime.newBuilder()
        date.day = pbLocalDate
        date.month = pbLocalMonth
        date.year = pbLocalYear

        time.hour = pbLocalHourOfDay
        time.minute = pbLocalMinute
        time.seconds = pbLocalSecond
        time.millis = pbLocalMilliSecond

        val pbLocalTime = builder
            .setDate(date)
            .setTime(time)
            .setTzOffset(pbLocalTimeZone)
            .build()

        //Act
        val result = PolarTimeUtils.pbLocalTimeToJavaCalendar(pbLocalTime)

        //Assert
        Assert.assertEquals(localYear, result[Calendar.YEAR])
        Assert.assertEquals(localMonth, result[Calendar.MONTH])
        Assert.assertEquals(localDate, result[Calendar.DAY_OF_MONTH])
        Assert.assertEquals(localHourOfDay, result[Calendar.HOUR_OF_DAY])
        Assert.assertEquals(localMinute, result[Calendar.MINUTE])
        Assert.assertEquals(localSecond, result[Calendar.SECOND])
        Assert.assertEquals(localMilliSecond, result[Calendar.MILLISECOND])
        Assert.assertEquals(localTimeZone, result.timeZone.id)
    }

    @Test
    fun `test PbLocalTime conversion without tz_offset to calendar `() {
        //Arrange
        val (pbLocalYear, localYear) = 2025 to 2025
        val (pbLocalMonth, localMonth) = 12 to Calendar.DECEMBER
        val (pbLocalDate, localDate) = 1 to 1
        val (pbLocalHourOfDay, localHourOfDay) = 11 to 11
        val (pbLocalMinute, localMinute) = 59 to 59
        val (pbLocalSecond, localSecond) = 59 to 59
        val (pbLocalMilliSecond, localMilliSecond) = 1 to 1

        val builder = PftpRequest.PbPFtpSetLocalTimeParams.newBuilder()
        val date = PbDate.newBuilder()
        val time = PbTime.newBuilder()
        date.day = pbLocalDate
        date.month = pbLocalMonth
        date.year = pbLocalYear

        time.hour = pbLocalHourOfDay
        time.minute = pbLocalMinute
        time.seconds = pbLocalSecond
        time.millis = pbLocalMilliSecond

        val pbLocalTime = builder
            .setDate(date)
            .setTime(time)
            .build()

        //Act
        val result = PolarTimeUtils.pbLocalTimeToJavaCalendar(pbLocalTime)

        //Assert
        Assert.assertEquals(localYear, result[Calendar.YEAR])
        Assert.assertEquals(localMonth, result[Calendar.MONTH])
        Assert.assertEquals(localDate, result[Calendar.DAY_OF_MONTH])
        Assert.assertEquals(localHourOfDay, result[Calendar.HOUR_OF_DAY])
        Assert.assertEquals(localMinute, result[Calendar.MINUTE])
        Assert.assertEquals(localSecond, result[Calendar.SECOND])
        Assert.assertEquals(localMilliSecond, result[Calendar.MILLISECOND])
    }

    @Test
    fun `test calendar conversion from pbLocaltimeTime with DST`() {
        //Arrange. DST start is at 3am 2024-3-31 in Helsinki time zone
        val pbLocalYear = 2024
        val pbLocalMonth = 3
        val pbLocalDayOfMonth = 31
        val pbLocalHourOfDay = 3
        val pbLocalMinute = 0
        val pbLocalSecond = 0
        val pbLocalMilliSecond = 0

        val builder = PftpRequest.PbPFtpSetLocalTimeParams.newBuilder()
        val date = PbDate.newBuilder()
        val time = PbTime.newBuilder()
        date.day = pbLocalDayOfMonth
        date.month = pbLocalMonth
        date.year = pbLocalYear

        time.hour = pbLocalHourOfDay
        time.minute = pbLocalMinute
        time.seconds = pbLocalSecond
        time.millis = pbLocalMilliSecond

        val pbLocalTime = builder
            .setDate(date)
            .setTime(time)
            .setTzOffset(TimeUnit.MINUTES.convert(2L, TimeUnit.HOURS).toInt())
            .build()

        //Act
        val calendar = PolarTimeUtils.pbLocalTimeToJavaCalendar(pbLocalTime)

        val timeZone = TimeZone.getTimeZone("Europe/Helsinki")
        calendar.timeZone = timeZone

        val result =
            org.joda.time.LocalDateTime(calendar.timeInMillis, org.joda.time.DateTimeZone.forID("Europe/Helsinki"))

        //Assert
        Assert.assertEquals(pbLocalYear, result.year)
        Assert.assertEquals(pbLocalMonth, result.monthOfYear)
        Assert.assertEquals(pbLocalDayOfMonth, result.dayOfMonth)
        Assert.assertEquals(4, result.hourOfDay)
        Assert.assertEquals(pbLocalMinute, result.minuteOfHour)
        Assert.assertEquals(pbLocalSecond, result.secondOfMinute)
        Assert.assertEquals(pbLocalMilliSecond, result.millisOfSecond)
    }

    @Test
    fun `test calendar conversion from pbLocaltimeTime to LocalDateTime`() {

        var localDateTime: LocalDateTime
        val pbLocalDateTime = PbLocalDateTime.newBuilder()
            .setTime(PbTime.newBuilder()
                .setHour(1)
                .setMinute(2)
                .setSeconds(3)
                .setMillis(4)
                .build())
            .setDate(PbDate.newBuilder()
                .setYear(2525)
                .setMonth(12)
                .setDay(30)
                .build())
            .setOBSOLETETrusted(true)
            .build()

        localDateTime = PolarTimeUtils.pbLocalDateTimeToLocalDateTime(pbLocalDateTime)

        Assert.assertEquals(1, localDateTime.hour)
        Assert.assertEquals(2, localDateTime.minute)
        Assert.assertEquals(3, localDateTime.second)
        Assert.assertEquals(4 * 1000000, localDateTime.nano)
        Assert.assertEquals(2525, localDateTime.year)
        Assert.assertEquals(java.time.Month.DECEMBER, localDateTime.month)
        Assert.assertEquals(30, localDateTime.dayOfMonth)
    }

    @Test
    fun `test calendar conversion from pbSystemDateTime to LocalDateTime`() {

        var localDateTime: LocalDateTime
        val pbSystemDateTime = PbSystemDateTime.newBuilder()
            .setTime(PbTime.newBuilder()
                .setHour(1)
                .setMinute(2)
                .setSeconds(3)
                .setMillis(4)
                .build())
            .setDate(PbDate.newBuilder()
                .setYear(2525)
                .setMonth(12)
                .setDay(30)
                .build())
            .setTrusted(true)
            .build()

        localDateTime = PolarTimeUtils.pbSystemDateTimeToLocalDateTime(pbSystemDateTime)

        Assert.assertEquals(1, localDateTime.hour)
        Assert.assertEquals(2, localDateTime.minute)
        Assert.assertEquals(3, localDateTime.second)
        Assert.assertEquals(4 * 1000000, localDateTime.nano)
        Assert.assertEquals(2525, localDateTime.year)
        Assert.assertEquals(java.time.Month.DECEMBER, localDateTime.month)
        Assert.assertEquals(30, localDateTime.dayOfMonth)
    }

    @Test
    fun `test calendar conversion from pbDateToLocalDate to LocalDate`() {

        var localDate: LocalDate
        val pbDate = PbDate.newBuilder()
            .setYear(2525)
            .setMonth(12)
            .setDay(30)
            .build()

        localDate = PolarTimeUtils.pbDateToLocalDate(pbDate)

        Assert.assertEquals(2525, localDate.year)
        Assert.assertEquals(java.time.Month.DECEMBER, localDate.month)
        Assert.assertEquals(30, localDate.dayOfMonth)
    }
}