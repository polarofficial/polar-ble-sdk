// Copyright © 2022 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model

import java.util.Calendar

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
    class AccOfflineRecording(
        val data: PolarAccelerometerData,
        startTime: Calendar,
        settings: PolarSensorSetting
    ) : PolarOfflineRecordingData(startTime, settings) {
        internal fun appendAccData(
            existingRecording: AccOfflineRecording,
            newData: PolarAccelerometerData,
            settings: PolarSensorSetting
        ): AccOfflineRecording {
            val mergedSamples = mutableListOf<PolarAccelerometerData.PolarAccelerometerDataSample>()
            mergedSamples.addAll(existingRecording.data.samples)
            mergedSamples.addAll(newData.samples)
            return AccOfflineRecording(
                PolarAccelerometerData(
                    mergedSamples
                ),
                startTime,
                settings
            )
        }
    }

    /**
     * Gyroscope Offline recording data
     *
     * @property data gyro data
     * @property startTime the time recording was started in UTC time
     * @property settings the settings used while recording
     */
    class GyroOfflineRecording(
        val data: PolarGyroData,
        startTime: Calendar,
        settings: PolarSensorSetting
    ) : PolarOfflineRecordingData(startTime, settings) {
        internal fun appendGyroData(
            existingRecording: GyroOfflineRecording,
            newData: PolarGyroData,
            settings: PolarSensorSetting
        ): GyroOfflineRecording {
            val mergedSamples = mutableListOf<PolarGyroData.PolarGyroDataSample>()
            mergedSamples.addAll(existingRecording.data.samples)
            mergedSamples.addAll(newData.samples)
            return GyroOfflineRecording(
                PolarGyroData(
                    mergedSamples
                ),
                startTime,
                settings
            )
        }
    }

    /**
     * Magnetometer offline recording data
     *
     * @property data magnetometer data
     * @property startTime the time recording was started in UTC time
     * @property settings the settings used while recording
     */
    class MagOfflineRecording(
        val data: PolarMagnetometerData,
        startTime: Calendar,
        settings: PolarSensorSetting?
    ) : PolarOfflineRecordingData(startTime, settings) {
        internal fun appendMagData(
            existingRecording: MagOfflineRecording,
            newData: PolarMagnetometerData
        ): MagOfflineRecording {
            val mergedSamples = mutableListOf<PolarMagnetometerData.PolarMagnetometerDataSample>()
            mergedSamples.addAll(existingRecording.data.samples)
            mergedSamples.addAll(newData.samples)
            return MagOfflineRecording(
                PolarMagnetometerData(
                    mergedSamples
                ),
                startTime,
                settings
            )
        }
    }

    /**
     * PPG (Photoplethysmography) offline recording data
     *
     * @property data ppg data
     * @property startTime the time recording was started in UTC time
     * @property settings the settings used while recording
     */
    class PpgOfflineRecording(
        val data: PolarPpgData,
        startTime: Calendar,
        settings: PolarSensorSetting?
    ) : PolarOfflineRecordingData(startTime, settings) {
        internal fun appendPpgData(
            existingRecording: PpgOfflineRecording,
            newData: PolarPpgData
        ): PpgOfflineRecording {
            val mergedSamples = mutableListOf<PolarPpgData.PolarPpgSample>()
            mergedSamples.addAll(existingRecording.data.samples)
            mergedSamples.addAll(newData.samples)
            return PpgOfflineRecording(
                PolarPpgData(
                    mergedSamples,
                    newData.type
                ),
                startTime,
                settings
            )
        }
    }

    /**
     * PPI (Peak-to-peak interval) offline recording data
     *
     * @property data ppi data
     * @property startTime the time recording was started in UTC time
     */
    class PpiOfflineRecording(val data: PolarPpiData, startTime: Calendar) :
        PolarOfflineRecordingData(startTime, null) {
        internal fun appendPpiData(
            existingRecording: PpiOfflineRecording,
            newData: PolarPpiData
        ): PpiOfflineRecording {
            val mergedSamples = mutableListOf<PolarPpiData.PolarPpiSample>()
            mergedSamples.addAll(existingRecording.data.samples)
            mergedSamples.addAll(newData.samples)
            return PpiOfflineRecording(
                PolarPpiData(mergedSamples),
                startTime
            )
        }
    }

    /**
     * Heart rate offline recording data
     *
     * @property data heart rate data
     * @property startTime the time recording was started in UTC time
     */
    class HrOfflineRecording(val data: PolarHrData, startTime: Calendar) :
        PolarOfflineRecordingData(startTime, null) {
        internal fun appendHrData(
            existingRecording: HrOfflineRecording,
            newData: PolarHrData
        ): HrOfflineRecording {
            val mergedSamples = mutableListOf<PolarHrData.PolarHrSample>()
            mergedSamples.addAll(existingRecording.data.samples)
            mergedSamples.addAll(newData.samples)
            return HrOfflineRecording(
                PolarHrData(mergedSamples),
                startTime
            )
        }
    }

    /**
     * Temperature offline recording data
     *
     * @property data temperature data
     * @property startTime the time recording was started in UTC time
     */
    class TemperatureOfflineRecording(val data: PolarTemperatureData, startTime: Calendar) :
        PolarOfflineRecordingData(startTime, null) {
        internal fun appendTemperatureData(
            existingTemperatureData: TemperatureOfflineRecording,
            newData: PolarTemperatureData
        ): TemperatureOfflineRecording {
            val mergedSamples = mutableListOf<PolarTemperatureData.PolarTemperatureDataSample>()
            mergedSamples.addAll(existingTemperatureData.data.samples)
            mergedSamples.addAll(newData.samples)
            return TemperatureOfflineRecording(
                PolarTemperatureData(mergedSamples),
                startTime
            )
        }
    }

    /**
     * Skin temperature offline recording data
     *
     * @property data skin temperature data
     * @property startTime the time recording was started in UTC time
     */
    class SkinTemperatureOfflineRecording(val data: PolarTemperatureData, startTime: Calendar) :
        PolarOfflineRecordingData(startTime, null) {
        internal fun appendTemperatureData(
            existingTemperatureData: TemperatureOfflineRecording,
            newData: PolarTemperatureData
        ): TemperatureOfflineRecording {
            val mergedSamples = mutableListOf<PolarTemperatureData.PolarTemperatureDataSample>()
            mergedSamples.addAll(existingTemperatureData.data.samples)
            mergedSamples.addAll(newData.samples)
            return TemperatureOfflineRecording(
                PolarTemperatureData(mergedSamples),
                startTime
            )
        }
    }
}
