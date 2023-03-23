package com.polar.androidcommunications.api.ble.model.offlinerecording

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType

internal object OfflineRecordingUtility {

    fun mapOfflineRecordingFileNameToMeasurementType(fileName: String): PmdMeasurementType {
        return when (fileName) {
            "ACC.REC" -> PmdMeasurementType.ACC
            "GYRO.REC" -> PmdMeasurementType.GYRO
            "MAG.REC" -> PmdMeasurementType.MAGNETOMETER
            "PPG.REC" -> PmdMeasurementType.PPG
            "PPI.REC" -> PmdMeasurementType.PPI
            "HR.REC" -> PmdMeasurementType.OFFLINE_HR
            else -> throw Exception("Unknown offline file $fileName")
        }
    }
}

