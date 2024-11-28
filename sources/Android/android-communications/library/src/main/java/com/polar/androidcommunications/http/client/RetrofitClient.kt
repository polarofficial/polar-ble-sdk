package com.polar.androidcommunications.http.client

import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory

class RetrofitClient {
    companion object {
        fun createRetrofitInstance(): Retrofit {
            return Retrofit.Builder()
                .baseUrl("https://firmware-management.polar.com")
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
                .build()
        }
    }
}

object HttpResponseCodes {
    const val OK = 200
    const val NO_CONTENT = 204
    const val BAD_REQUEST = 400
}