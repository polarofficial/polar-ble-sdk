package com.polar.androidcommunications.common.ble

import com.polar.androidcommunications.api.ble.BleLogger

object PhoneUtils {
    private const val TAG = "PhoneUtils"

    /**
     * List of phones which are known to have problems with MTU negotiation
     * Brand and model are based listing in https://storage.googleapis.com/play_public/supported_devices.html
     */
    private val phonesWithMtuNegotiationProblem = listOf(
        //Brand, Model
        Pair("Motorola", "moto g20"),
        Pair("Motorola", "moto e20"),
        Pair("Motorola", "moto e30"),
        Pair("Motorola", "moto e32"),
        Pair("Motorola", "moto e40"),
        Pair("Nokia", "Nokia G21"),
        Pair("Nokia", "Nokia G11"),
        Pair("Nokia", "Nokia T20"),

        Pair("Realme", "RMX3261"), //C21Y
        Pair("Realme", "RMX3262"), //C21Y
        Pair("Realme", "RMX3265"), //C25Y
        Pair("Realme", "RMX3269"), //C25Y
        Pair("Realme", "RMX3624"), //C33
        Pair("Realme", "RMX3760"), //C53
        Pair("Realme", "RMP2105"), //Pad Mini
        Pair("Realme", "RMP2106"), //Pad Mini

        Pair("Infinix", "Infinix X675"), //Hot 11 2022

        Pair("HTC", "Wildfire E2 plus"),

        Pair("Micromax", "IN_2b"),
        Pair("Micromax", "IN_2c"),

        Pair("Samsung", "SM-X200"), //Galaxy Tab A8
    )

    /**
     * Some of the phone models have a bug with MTU negotiation. Returns true if the current phone is suspected to have MTU negotiation problem
     */
    @JvmStatic
    fun isMtuNegotiationBroken(brand: String, model: String): Boolean {
        val modelWithoutParentheses = model.replace("(", "").replace(")", "")
        return if (phonesWithMtuNegotiationProblem.any {
                it.first.equals(brand, ignoreCase = true)
                        && it.second.equals(modelWithoutParentheses, ignoreCase = true)
            }) {
            BleLogger.d(TAG, "MTU problematic phone detected. Manufacturer: $brand Model: $model")
            true
        } else {
            false
        }
    }
}