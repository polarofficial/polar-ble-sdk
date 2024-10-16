// Copyright © 2024 Polar Electro Oy. All rights reserved.

package com.polar.sdk.api.model

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

import fi.polar.remote.representation.protobuf.UserIds.PbUserIdentifier
import fi.polar.remote.representation.protobuf.Types

data class UserIdentifierType private constructor(
    val userIdLastModified: String
) {
    companion object {
        const val USER_IDENTIFIER_FILENAME = "/U/0/USERID.BPB"
        private const val MASTER_IDENTIFIER = ULong.MAX_VALUE

        fun create(): UserIdentifierType {
            val currentTime = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            return UserIdentifierType(userIdLastModified = currentTime)
        }
    }

    fun toProto(): PbUserIdentifier {
        val builder = PbUserIdentifier.newBuilder()
            .setMasterIdentifier(MASTER_IDENTIFIER.toLong())

        val dateTimeParsed = LocalDateTime.parse(this.userIdLastModified, DateTimeFormatter.ISO_DATE_TIME)
        val lastModified = Types.PbSystemDateTime.newBuilder()
            .setDate(Types.PbDate.newBuilder()
                .setYear(dateTimeParsed.year)
                .setMonth(dateTimeParsed.monthValue)
                .setDay(dateTimeParsed.dayOfMonth)
                .build())
            .setTime(Types.PbTime.newBuilder()
                .setHour(dateTimeParsed.hour)
                .setMinute(dateTimeParsed.minute)
                .setSeconds(dateTimeParsed.second)
                .build())
            .setTrusted(true)
            .build()

        builder.setUserIdLastModified(lastModified)

        return builder.build()
    }
}
