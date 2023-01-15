package com.polar.sdk.api.model.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdSetting
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.*
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.errors.PolarBleSdkInternalException
import com.polar.sdk.api.model.*
import java.util.*

internal object PolarDataUtils {
    private const val TAG = "PolarDataUtils"

    fun mapPMDClientOhrDataToPolarOhr(ohrData: PpgData): PolarOhrData {
        var type: PolarOhrData.OhrDataType = PolarOhrData.OhrDataType.UNKNOWN
        val listOfSamples = mutableListOf<PolarOhrData.PolarOhrSample>()
        for (sample in ohrData.ppgSamples) {
            when (sample) {
                is PpgData.PpgDataFrameType0 -> {
                    type = PolarOhrData.OhrDataType.PPG3_AMBIENT1
                    val channelsData = mutableListOf<Int>()
                    channelsData.addAll(sample.ppgDataSamples)
                    channelsData.add(sample.ambientSample)
                    listOfSamples.add(PolarOhrData.PolarOhrSample(sample.timeStamp.toLong(), channelsData))
                }
                else -> {
                    BleLogger.w(TAG, "Not supported PPG sample type: $sample")
                }
            }
        }
        return PolarOhrData(listOfSamples, type, ohrData.timeStamp.toLong())
    }

    fun mapPMDClientPpiDataToPolarOhrPpiData(ppiData: PpiData): PolarOhrPPIData {
        val samples: MutableList<PolarOhrPPIData.PolarOhrPPISample> = mutableListOf()
        for ((hr, ppInMs, ppErrorEstimate, blockerBit, skinContactStatus, skinContactSupported) in ppiData.ppiSamples) {
            samples.add(
                PolarOhrPPIData.PolarOhrPPISample(ppInMs, ppErrorEstimate, hr, blockerBit != 0, skinContactStatus != 0, skinContactSupported != 0)
            )
        }
        return PolarOhrPPIData(0L, samples)
    }

    fun mapPmdClientEcgDataToPolarEcg(ecgData: EcgData): PolarEcgData {
        val ecgDataSamples = mutableListOf<PolarEcgData.PolarEcgDataSample>()
        for (sample in ecgData.ecgSamples) {
            when (sample) {
                is EcgData.EcgSample -> {
                    ecgDataSamples.add(PolarEcgData.PolarEcgDataSample(timeStamp = sample.timeStamp.toLong(), voltage = sample.microVolts))
                }
                else -> {
                    BleLogger.w(TAG, "Not supported ECG sample type: $sample")
                }
            }
        }
        return PolarEcgData(ecgDataSamples, ecgData.timeStamp.toLong())
    }

    fun mapPmdClientAccDataToPolarAcc(accData: AccData): PolarAccelerometerData {
        val samples: MutableList<PolarAccelerometerData.PolarAccelerometerDataSample> = mutableListOf()
        for ((timeStamp, x, y, z) in accData.accSamples) {
            samples.add(PolarAccelerometerData.PolarAccelerometerDataSample(timeStamp.toLong(), x, y, z))
        }
        return PolarAccelerometerData(samples, accData.timeStamp.toLong())
    }

    fun mapPmdClientGyroDataToPolarGyro(gyroData: GyrData): PolarGyroData {
        val samples: MutableList<PolarGyroData.PolarGyroDataSample> = mutableListOf()
        for ((timeStamp, x, y, z) in gyroData.gyrSamples) {
            samples.add(PolarGyroData.PolarGyroDataSample(timeStamp.toLong(), x, y, z))
        }
        return PolarGyroData(samples, gyroData.timeStamp.toLong())
    }

    fun mapPmdClientMagDataToPolarMagnetometer(magData: MagData): PolarMagnetometerData {
        val samples: MutableList<PolarMagnetometerData.PolarMagnetometerDataSample> = mutableListOf()
        for ((timeStamp, x, y, z) in magData.magSamples) {
            samples.add(PolarMagnetometerData.PolarMagnetometerDataSample(timeStamp.toLong(), x, y, z))
        }
        return PolarMagnetometerData(samples, magData.timeStamp.toLong())
    }

    fun mapPolarFeatureToPmdClientMeasurementType(polarFeature: PolarBleApi.DeviceStreamingFeature): PmdMeasurementType {
        return when (polarFeature) {
            PolarBleApi.DeviceStreamingFeature.ECG -> PmdMeasurementType.ECG
            PolarBleApi.DeviceStreamingFeature.ACC -> PmdMeasurementType.ACC
            PolarBleApi.DeviceStreamingFeature.PPG -> PmdMeasurementType.PPG
            PolarBleApi.DeviceStreamingFeature.PPI -> PmdMeasurementType.PPI
            PolarBleApi.DeviceStreamingFeature.GYRO -> PmdMeasurementType.GYRO
            PolarBleApi.DeviceStreamingFeature.MAGNETOMETER -> PmdMeasurementType.MAGNETOMETER
        }
    }

    fun mapPmdClientFeaturesToPolarFeatures(pmdMeasurementType: Set<PmdMeasurementType>): List<PolarBleApi.DeviceStreamingFeature> {
        val polarFeatures: MutableList<PolarBleApi.DeviceStreamingFeature> = mutableListOf()
        for (feature in pmdMeasurementType) {
            polarFeatures.add(mapPmdClientFeatureToPolarFeature(feature))
        }
        return polarFeatures.toList()
    }

    fun mapPmdClientFeatureToPolarFeature(pmdMeasurementType: PmdMeasurementType): PolarBleApi.DeviceStreamingFeature {
        return when (pmdMeasurementType) {
            PmdMeasurementType.ECG -> PolarBleApi.DeviceStreamingFeature.ECG
            PmdMeasurementType.PPG -> PolarBleApi.DeviceStreamingFeature.PPG
            PmdMeasurementType.ACC -> PolarBleApi.DeviceStreamingFeature.ACC
            PmdMeasurementType.PPI -> PolarBleApi.DeviceStreamingFeature.PPI
            PmdMeasurementType.GYRO -> PolarBleApi.DeviceStreamingFeature.GYRO
            PmdMeasurementType.MAGNETOMETER -> PolarBleApi.DeviceStreamingFeature.MAGNETOMETER
            else -> throw PolarBleSdkInternalException("Error when map measurement type  $pmdMeasurementType to Polar feature")
        }
    }

    /**
     * Helper to map from PolarSensorSetting to PmdSetting
     *
     * @return PmdSetting
     */
    fun mapPolarSettingsToPmdSettings(polarSensorSetting: PolarSensorSetting): PmdSetting {
        val selected: MutableMap<PmdSetting.PmdSettingType, Int> = mutableMapOf()
        for ((key, value) in polarSensorSetting.settings) {
            selected[PmdSetting.PmdSettingType.values()[key.numVal]] = Collections.max(value)
        }
        return PmdSetting(selected)
    }

    /**
     * Helper to map from PolarSensorSetting to PmdSetting
     *
     * @return PmdSetting
     */
    fun mapPmdSettingsToPolarSettings(pmd: PmdSetting, fromSelected: Boolean): PolarSensorSetting {
        return if (fromSelected) {
            val settings: MutableMap<PolarSensorSetting.SettingType, Int> = mutableMapOf()
            for ((key, value) in pmd.selected) {
                when (key) {
                    PmdSetting.PmdSettingType.SAMPLE_RATE -> settings[PolarSensorSetting.SettingType.SAMPLE_RATE] = value
                    PmdSetting.PmdSettingType.RESOLUTION -> settings[PolarSensorSetting.SettingType.RESOLUTION] = value
                    PmdSetting.PmdSettingType.RANGE -> settings[PolarSensorSetting.SettingType.RANGE] = value
                    PmdSetting.PmdSettingType.RANGE_MILLIUNIT -> settings[PolarSensorSetting.SettingType.RANGE_MILLIUNIT] = value
                    PmdSetting.PmdSettingType.CHANNELS -> settings[PolarSensorSetting.SettingType.CHANNELS] = value
                    else -> {
                        //nop
                    }
                }
            }
            PolarSensorSetting(settings.toMap())
        } else {
            val settings: MutableMap<PolarSensorSetting.SettingType, Set<Int>> = mutableMapOf()
            for ((key, value) in pmd.settings) {
                when (key) {
                    PmdSetting.PmdSettingType.SAMPLE_RATE -> settings[PolarSensorSetting.SettingType.SAMPLE_RATE] = value
                    PmdSetting.PmdSettingType.RESOLUTION -> settings[PolarSensorSetting.SettingType.RESOLUTION] = value
                    PmdSetting.PmdSettingType.RANGE -> settings[PolarSensorSetting.SettingType.RANGE] = value
                    PmdSetting.PmdSettingType.RANGE_MILLIUNIT -> settings[PolarSensorSetting.SettingType.RANGE_MILLIUNIT] = value
                    PmdSetting.PmdSettingType.CHANNELS -> settings[PolarSensorSetting.SettingType.CHANNELS] = value
                    else -> {
                        //nop
                    }
                }
            }
            PolarSensorSetting(settings.toList())
        }
    }
}