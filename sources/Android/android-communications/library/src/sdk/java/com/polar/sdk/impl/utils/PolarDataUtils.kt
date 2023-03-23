package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.*
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.*
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.errors.PolarBleSdkInternalException
import com.polar.sdk.api.model.*
import java.util.*
import kotlin.math.roundToInt

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

    fun mapPMDClientPpgDataToPolarPpg(ppgData: PpgData): PolarPpgData {
        var type: PolarPpgData.PpgDataType = PolarPpgData.PpgDataType.UNKNOWN
        val listOfSamples = mutableListOf<PolarPpgData.PolarPpgSample>()
        for (sample in ppgData.ppgSamples) {
            when (sample) {
                is PpgData.PpgDataFrameType0 -> {
                    type = PolarPpgData.PpgDataType.PPG3_AMBIENT1
                    val channelsData = mutableListOf<Int>()
                    channelsData.addAll(sample.ppgDataSamples)
                    channelsData.add(sample.ambientSample)
                    listOfSamples.add(PolarPpgData.PolarPpgSample(sample.timeStamp.toLong(), channelsData))
                }
                else -> {
                    BleLogger.w(TAG, "Not supported PPG sample type: $sample")
                }
            }
        }
        return PolarPpgData(listOfSamples, type)
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

    fun mapPMDClientPpiDataToPolarPpiData(ppiData: PpiData): PolarPpiData {
        val samples: MutableList<PolarPpiData.PolarPpiSample> = mutableListOf()
        for ((hr, ppInMs, ppErrorEstimate, blockerBit, skinContactStatus, skinContactSupported) in ppiData.ppiSamples) {
            samples.add(
                PolarPpiData.PolarPpiSample(ppInMs, ppErrorEstimate, hr, blockerBit != 0, skinContactStatus != 0, skinContactSupported != 0)
            )
        }
        return PolarPpiData(samples)
    }

    fun mapPMDClientOfflineHrDataToPolarHrData(offlineHrData: OfflineHrData): PolarHrData {
        val samples: MutableList<PolarHrData.PolarHrSample> = mutableListOf()
        for (sample in offlineHrData.hrSamples) {
            samples.add(
                PolarHrData.PolarHrSample(
                    hr = sample.hr,
                    rrsMs = emptyList(),
                    rrAvailable = false,
                    contactStatus = false,
                    contactStatusSupported = false
                )
            )
        }
        return PolarHrData(samples)
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

    fun mapPolarFeatureToPmdClientMeasurementType(polarFeature: PolarBleApi.PolarDeviceDataType): PmdMeasurementType {
        return when (polarFeature) {
            PolarBleApi.PolarDeviceDataType.ECG -> PmdMeasurementType.ECG
            PolarBleApi.PolarDeviceDataType.ACC -> PmdMeasurementType.ACC
            PolarBleApi.PolarDeviceDataType.PPG -> PmdMeasurementType.PPG
            PolarBleApi.PolarDeviceDataType.PPI -> PmdMeasurementType.PPI
            PolarBleApi.PolarDeviceDataType.GYRO -> PmdMeasurementType.GYRO
            PolarBleApi.PolarDeviceDataType.MAGNETOMETER -> PmdMeasurementType.MAGNETOMETER
            PolarBleApi.PolarDeviceDataType.HR -> PmdMeasurementType.OFFLINE_HR
            else -> {
                throw PolarBleSdkInternalException("Error when map $polarFeature to PMD measurement type")
            }
        }
    }

    fun mapPmdClientFeatureToPolarFeature(pmdMeasurementType: PmdMeasurementType): PolarBleApi.PolarDeviceDataType {
        return when (pmdMeasurementType) {
            PmdMeasurementType.ECG -> PolarBleApi.PolarDeviceDataType.ECG
            PmdMeasurementType.PPG -> PolarBleApi.PolarDeviceDataType.PPG
            PmdMeasurementType.ACC -> PolarBleApi.PolarDeviceDataType.ACC
            PmdMeasurementType.PPI -> PolarBleApi.PolarDeviceDataType.PPI
            PmdMeasurementType.GYRO -> PolarBleApi.PolarDeviceDataType.GYRO
            PmdMeasurementType.MAGNETOMETER -> PolarBleApi.PolarDeviceDataType.MAGNETOMETER
            PmdMeasurementType.OFFLINE_HR -> PolarBleApi.PolarDeviceDataType.HR
            else -> throw PolarBleSdkInternalException("Error when map measurement type $pmdMeasurementType to Polar feature")
        }
    }

    private fun mapPolarOfflineModeTriggerToPmdOfflineTriggerMode(offlineTrigger: PolarOfflineRecordingTriggerMode): PmdOfflineRecTriggerMode {
        return when (offlineTrigger) {
            PolarOfflineRecordingTriggerMode.TRIGGER_SYSTEM_START -> PmdOfflineRecTriggerMode.TRIGGER_SYSTEM_START
            PolarOfflineRecordingTriggerMode.TRIGGER_EXERCISE_START -> PmdOfflineRecTriggerMode.TRIGGER_EXERCISE_START
            PolarOfflineRecordingTriggerMode.TRIGGER_DISABLED -> PmdOfflineRecTriggerMode.TRIGGER_DISABLE
        }
    }

    private fun mapPmdOfflineTriggerModeToPolarOfflineTriggerMode(pmdTriggerType: PmdOfflineRecTriggerMode): PolarOfflineRecordingTriggerMode {
        return when (pmdTriggerType) {
            PmdOfflineRecTriggerMode.TRIGGER_DISABLE -> PolarOfflineRecordingTriggerMode.TRIGGER_DISABLED
            PmdOfflineRecTriggerMode.TRIGGER_SYSTEM_START -> PolarOfflineRecordingTriggerMode.TRIGGER_SYSTEM_START
            PmdOfflineRecTriggerMode.TRIGGER_EXERCISE_START -> PolarOfflineRecordingTriggerMode.TRIGGER_EXERCISE_START
        }
    }

    /**
     * Helper to map from PolarSensorSetting to PmdSetting
     *
     * @return PmdSetting
     */
    fun mapPolarSettingsToPmdSettings(polarSensorSetting: PolarSensorSetting?): PmdSetting {
        val selected: MutableMap<PmdSetting.PmdSettingType, Int> = mutableMapOf()
        if (polarSensorSetting != null) {
            for ((key, value) in polarSensorSetting.settings) {
                selected[PmdSetting.PmdSettingType.values()[key.numVal]] = Collections.max(value)
            }
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

    fun mapPmdTriggerToPolarTrigger(pmdOfflineTrigger: PmdOfflineTrigger): PolarOfflineRecordingTrigger {
        val triggerMode = mapPmdOfflineTriggerModeToPolarOfflineTriggerMode(pmdOfflineTrigger.triggerMode)
        val polarTriggerSettings: MutableMap<PolarBleApi.PolarDeviceDataType, PolarSensorSetting?> = mutableMapOf()

        for (setting in pmdOfflineTrigger.triggers) {
            val dataType = mapPmdClientFeatureToPolarFeature(setting.key)
            val triggerStatus = setting.value.first

            if (triggerStatus == PmdOfflineRecTriggerStatus.TRIGGER_ENABLED) {
                // Map only the enabled
                val polarSettings = setting.value.second?.let {
                    mapPmdSettingsToPolarSettings(it, false)
                }
                polarTriggerSettings[dataType] = polarSettings
            }
        }
        return PolarOfflineRecordingTrigger(
            triggerMode = triggerMode,
            triggerFeatures = polarTriggerSettings
        )
    }

    fun mapPolarOfflineTriggerToPmdOfflineTrigger(polarTrigger: PolarOfflineRecordingTrigger): PmdOfflineTrigger {
        val pmdTriggerMode = mapPolarOfflineModeTriggerToPmdOfflineTriggerMode(polarTrigger.triggerMode)
        val pmdTriggers: MutableMap<PmdMeasurementType, Pair<PmdOfflineRecTriggerStatus, PmdSetting?>> = mutableMapOf()

        for (trigger in polarTrigger.triggerFeatures) {
            val pmdMeasurementType = mapPolarFeatureToPmdClientMeasurementType(trigger.key)
            val pmdSettings = trigger.value?.let { mapPolarSettingsToPmdSettings(it) }
            pmdTriggers[pmdMeasurementType] = Pair(PmdOfflineRecTriggerStatus.TRIGGER_ENABLED, pmdSettings)
        }

        return PmdOfflineTrigger(triggerMode = pmdTriggerMode, triggers = pmdTriggers)
    }

    fun mapPolarSecretToPmdSecret(polarSecret: PolarRecordingSecret): PmdSecret {
        return PmdSecret(
            strategy = PmdSecret.SecurityStrategy.AES128,
            key = polarSecret.secret
        )
    }
}

