// Copyright © 2022 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model

import java.util.*

/**
 * Polar Offline recording data
 *
 * @property startTime the time recording was started in UTC time
 */
sealed class PolarOfflineRecordingData(val startTime: Calendar, val settings: PolarSensorSetting?) {
    /**
     * Accelerometer offline recording data
     *
     * @property data acc data
     * @property startTime the time recording was started in UTC time
     * @property settings the settings used while recording
     */
    class AccOfflineRecording(val data: PolarAccelerometerData, startTime: Calendar, settings: PolarSensorSetting) : PolarOfflineRecordingData(startTime, settings)

    /**
     * Gyroscope Offline recording data
     *
     * @property data gyro data
     * @property startTime the time recording was started in UTC time
     * @property settings the settings used while recording
     */
    class GyroOfflineRecording(val data: PolarGyroData, startTime: Calendar, settings: PolarSensorSetting) : PolarOfflineRecordingData(startTime, settings)

    /**
     * Magnetometer offline recording data
     *
     * @property data magnetometer data
     * @property startTime the time recording was started in UTC time
     * @property settings the settings used while recording
     */
    class MagOfflineRecording(val data: PolarMagnetometerData, startTime: Calendar, settings: PolarSensorSetting) : PolarOfflineRecordingData(startTime, settings)

    /**
     * PPG (Photoplethysmography) offline recording data
     *
     * @property data ppg data
     * @property startTime the time recording was started in UTC time
     * @property settings the settings used while recording
     */
    class PpgOfflineRecording(val data: PolarPpgData, startTime: Calendar, settings: PolarSensorSetting) : PolarOfflineRecordingData(startTime, settings)

    /**
     * PPI (Peak-to-peak interval) offline recording data
     *
     * @property data ppi data
     * @property startTime the time recording was started in UTC time
     */
    class PpiOfflineRecording(val data: PolarPpiData, startTime: Calendar) : PolarOfflineRecordingData(startTime, null)

    /**
     * Heart rate offline recording data
     *
     * @property data heart rate data
     * @property startTime the time recording was started in UTC time
     */
    class HrOfflineRecording(val data: PolarHrData, startTime: Calendar) : PolarOfflineRecordingData(startTime, null)
}
