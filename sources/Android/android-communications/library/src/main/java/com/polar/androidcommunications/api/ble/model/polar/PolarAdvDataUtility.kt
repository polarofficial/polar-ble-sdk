package com.polar.androidcommunications.api.ble.model.polar

import com.polar.androidcommunications.api.ble.model.advertisement.BleAdvertisementContent.Companion.BLE_ADV_POLAR_PREFIX_IN_LOCAL_NAME

object PolarAdvDataUtility {

    fun getPolarModelNameFromAdvLocalName(advLocalName: String): String {
        return if (isPolarDevice(advLocalName)) {
            val modelName = advLocalName.trim()
                .replaceFirst(("$BLE_ADV_POLAR_PREFIX_IN_LOCAL_NAME "), "")
            val endIndex = modelName.lastIndexOf(" ")
            modelName.substring(0, endIndex)
        } else {
            ""
        }
    }

    fun isPolarDevice(name: String): Boolean {
        return name.trim().startsWith(BLE_ADV_POLAR_PREFIX_IN_LOCAL_NAME) && name.trim()
            .split(" ").size > 2
    }
}