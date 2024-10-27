package com.polar.sdk.api.model

import fi.polar.remote.representation.protobuf.Types.PbSystemDateTime
import fi.polar.remote.representation.protobuf.Types.PbDate
import fi.polar.remote.representation.protobuf.Types.PbTime
import fi.polar.remote.representation.protobuf.Types.PbDeviceLocation
import fi.polar.remote.representation.protobuf.UserDeviceSettings.PbUserDeviceGeneralSettings
import fi.polar.remote.representation.protobuf.UserDeviceSettings.PbUserDeviceSettings
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import java.util.Calendar

data class PolarUserDeviceSettings(val deviceLocation: Int?) {

    enum class DeviceLocation(val value: Int) {
        UNDEFINED(0),
        OTHER(1),
        WRIST_LEFT(2),
        WRIST_RIGHT(3),
        NECKLACE(4),
        CHEST(5),
        UPPER_BACK(6),
        FOOT_LEFT(7),
        FOOT_RIGHT(8),
        LOWER_ARM_LEFT(9),
        LOWER_ARM_RIGHT(10),
        UPPER_ARM_LEFT(11),
        UPPER_ARM_RIGHT(12),
        BIKE_MOUNT(13);
    }

    companion object {
        infix fun from(value: Int): DeviceLocation? = DeviceLocation.values().firstOrNull {it.value == value}
        const val DEVICE_SETTINGS_FILENAME = "/U/0/S/UDEVSET.BPB"
    }

    fun toProto(): PbUserDeviceSettings {
        val pbSettingsWithDeviceLocation = PbUserDeviceGeneralSettings.newBuilder()
            .setDeviceLocation(deviceLocation?.let { PbDeviceLocation.forNumber(it) })
        return PbUserDeviceSettings.newBuilder()
            .setGeneralSettings(pbSettingsWithDeviceLocation)
            .setLastModified(createTimeStamp())
            .build()
    }

    fun fromBytes(bytes: ByteArray): PolarUserDeviceSettings {
        val proto = PbUserDeviceSettings.parseFrom(bytes)
        if (proto.hasGeneralSettings() && proto.generalSettings.hasDeviceLocation()) {
            return PolarUserDeviceSettings(proto.generalSettings.deviceLocation.number)
        }
        return PolarUserDeviceSettings(null)
    }
}

private fun createTimeStamp(): PbSystemDateTime {

    val builder = PbSystemDateTime.newBuilder()
    val date = PbDate.newBuilder()
    val time = PbTime.newBuilder()

    val dt = DateTime(Calendar.getInstance())
    val utcTime: DateTime = dt.withZone(DateTimeZone.forID("UTC"))

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