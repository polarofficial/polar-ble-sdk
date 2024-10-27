package com.polar.androidcommunications.http.fwu

import com.google.gson.annotations.SerializedName

data class FirmwareUpdateRequest(
    @SerializedName("clientId")
    val clientId: String,
    @SerializedName("uuid")
    val uuid: String,
    @SerializedName("firmwareVersion")
    val firmwareVersion: String,
    @SerializedName("hardwareCode")
    val hardwareCode: String
)

data class FirmwareUpdateResponse(
    @SerializedName("version")
    val version: String,
    @SerializedName("fileUrl")
    val fileUrl: String
)