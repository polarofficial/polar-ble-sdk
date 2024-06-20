package com.polar.androidcommunications.api.ble.model.offlinerecording

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType

internal object OfflineRecordingUtility {

    fun mapOfflineRecordingFileNameToMeasurementType(fileName: String): PmdMeasurementType {
        val fileNameWithoutExtension = fileName.substringBeforeLast(".")
        return when (fileNameWithoutExtension.replace(Regex("\\d+"), "")) {
            "ACC" -> PmdMeasurementType.ACC
            "GYRO" -> PmdMeasurementType.GYRO
            "MAG" -> PmdMeasurementType.MAGNETOMETER
            "PPG" -> PmdMeasurementType.PPG
            "PPI" -> PmdMeasurementType.PPI
            "HR" -> PmdMeasurementType.OFFLINE_HR
            "TEMP" -> PmdMeasurementType.OFFLINE_TEMP
            else -> throw IllegalArgumentException("Unknown offline file $fileName")
        }
    }
}

