package com.polar.androidcommunications.http.fwu

import io.reactivex.rxjava3.core.Single
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Streaming
import retrofit2.http.Url

interface FirmwareUpdateApi {
    @POST("/api/v1/firmware-update/check")
    @Headers(
            "Accept: application/json",
            "Content-Type: application/json"
    )
    fun checkFirmwareUpdate(@Body firmwareUpdateRequest: FirmwareUpdateRequest):  Single<Response<FirmwareUpdateResponse>>

    @Streaming
    @GET
    fun getFirmwareUpdatePackage(@Url url: String): Single<ResponseBody>
}