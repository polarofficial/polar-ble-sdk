package com.polar.androidcommunications.api.ble.model.polar

object PolarAdvDataUtility {

    fun getDeviceModelNameFromAdvLocalName(advLocalName: String, withPrefixToTrim: String = "Polar"): String {
        return if (isValidDevice(advLocalName, withPrefixToTrim)) {
            val modelName = advLocalName.trim()
                .replaceFirst((if (withPrefixToTrim != "") "$withPrefixToTrim " else ""), "")
            val endIndex = modelName.lastIndexOf(" ")
            modelName.substring(0, endIndex)
        } else {
            ""
        }
    }

    fun isValidDevice(name: String, requiredPrefix: String = "Polar"): Boolean {
        return name.trim().startsWith(requiredPrefix) && name.trim()
            .split(" ").size > 2
    }
}