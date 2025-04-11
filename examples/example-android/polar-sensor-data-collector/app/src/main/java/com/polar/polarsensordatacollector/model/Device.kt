package com.polar.polarsensordatacollector.model

data class Device(
    val address: String,
    val deviceId: String,
    val name: String,
    val imageUrl: String = ""
)
