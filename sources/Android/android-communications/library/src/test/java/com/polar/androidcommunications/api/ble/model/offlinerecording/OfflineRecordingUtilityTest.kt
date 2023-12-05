package com.polar.androidcommunications.api.ble.model.offlinerecording

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.androidcommunications.api.ble.model.offlinerecording.OfflineRecordingUtility.mapOfflineRecordingFileNameToMeasurementType
import org.junit.Assert
import org.junit.Test

class OfflineRecordingUtilityTest {

    @Test
    fun `mapOfflineRecordingFileNameToMeasurementType() maps file names to correct measurement types`() {
        Assert.assertEquals(
            PmdMeasurementType.ACC,
            mapOfflineRecordingFileNameToMeasurementType("ACC.REC")
        )
        Assert.assertEquals(
            PmdMeasurementType.GYRO,
            mapOfflineRecordingFileNameToMeasurementType("GYRO.REC")
        )
        Assert.assertEquals(
            PmdMeasurementType.MAGNETOMETER,
            mapOfflineRecordingFileNameToMeasurementType("MAG.REC")
        )
        Assert.assertEquals(
            PmdMeasurementType.PPG,
            mapOfflineRecordingFileNameToMeasurementType("PPG.REC")
        )
        Assert.assertEquals(
            PmdMeasurementType.PPI,
            mapOfflineRecordingFileNameToMeasurementType("PPI.REC")
        )
        Assert.assertEquals(
            PmdMeasurementType.OFFLINE_HR,
            mapOfflineRecordingFileNameToMeasurementType("HR.REC")
        )
        Assert.assertEquals(
            PmdMeasurementType.ACC,
            mapOfflineRecordingFileNameToMeasurementType("ACC0.REC")
        )
        Assert.assertEquals(
            PmdMeasurementType.GYRO,
            mapOfflineRecordingFileNameToMeasurementType("GYRO5.REC")
        )
        Assert.assertEquals(
            PmdMeasurementType.MAGNETOMETER,
            mapOfflineRecordingFileNameToMeasurementType("MAG18.REC")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mapOfflineRecordingFileNameToMeasurementType() throws IllegalArgumentException if file name is not supported`() {
        mapOfflineRecordingFileNameToMeasurementType("INVALID.REC")
    }
}