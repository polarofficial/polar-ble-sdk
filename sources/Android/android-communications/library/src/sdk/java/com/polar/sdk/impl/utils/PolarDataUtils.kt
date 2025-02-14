package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdOfflineRecTriggerMode
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdOfflineRecTriggerStatus
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdOfflineTrigger
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdSecret
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdSetting
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.AccData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.EcgData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.GnssLocationData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.GyrData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.MagData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.OfflineHrData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.PpgData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.PpiData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.PressureData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.SkinTemperatureData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.TemperatureData
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.errors.PolarBleSdkInternalException
import com.polar.sdk.api.model.*
import java.util.Collections

internal object PolarDataUtils {
    private const val TAG = "PolarDataUtils"

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
                is PpgData.PpgDataFrameType7 -> {
                    type = PolarPpgData.PpgDataType.FRAME_TYPE_7
                    val channelsData = mutableListOf<Int>()
                    channelsData.addAll(sample.ppgDataSamples)
                    channelsData.add(sample.status.toInt())
                    listOfSamples.add(PolarPpgData.PolarPpgSample(sample.timeStamp.toLong(), channelsData))
                }
                is PpgData.PpgDataFrameType8 -> {
                    type = PolarPpgData.PpgDataType.FRAME_TYPE_8
                    val channelsData = mutableListOf<Int>()
                    channelsData.addAll(sample.ppgDataSamples)
                    channelsData.add(sample.status.toInt())
                    listOfSamples.add(PolarPpgData.PolarPpgSample(sample.timeStamp.toLong(), channelsData))
                }
                is PpgData.PpgDataSampleSportId -> {
                    type = PolarPpgData.PpgDataType.SPORT_ID
                    val samplesData = mutableListOf<Int>()
                    samplesData.addAll(listOf(sample.sportId.toInt()))
                    listOfSamples.add(PolarPpgData.PolarPpgSample(sample.timeStamp.toLong(), samplesData))
                }
                is PpgData.PpgDataFrameType4 -> {
                    type = PolarPpgData.PpgDataType.FRAME_TYPE_4
                    val samplesData = mutableListOf<Int>()
                    samplesData.addAll(sample.channel1GainTs.map { it.toInt() })
                    samplesData.addAll(sample.channel2GainTs.map { it.toInt() })
                    samplesData.addAll(sample.numIntTs.map { it.toInt() })
                    listOfSamples.add(PolarPpgData.PolarPpgSample(sample.timeStamp.toLong(), samplesData))
                }
                is PpgData.PpgDataFrameType5 -> {
                    type = PolarPpgData.PpgDataType.FRAME_TYPE_5
                    val samplesData = mutableListOf<Int>()
                    samplesData.addAll(listOf((sample.operationMode).toInt()))
                    listOfSamples.add(PolarPpgData.PolarPpgSample(sample.timeStamp.toLong(), samplesData))
                }
                is PpgData.PpgDataFrameType9 -> {
                    type = PolarPpgData.PpgDataType.FRAME_TYPE_9
                    val samplesData = mutableListOf<Int>()
                    samplesData.addAll(sample.channel1GainTs.map { it.toInt() })
                    samplesData.addAll(sample.channel2GainTs.map { it.toInt() })
                    samplesData.addAll(sample.numIntTs.map { it.toInt() })
                    listOfSamples.add(PolarPpgData.PolarPpgSample(sample.timeStamp.toLong(), samplesData))
                }
                is PpgData.PpgDataFrameType10 -> {
                    type = PolarPpgData.PpgDataType.FRAME_TYPE_10
                    val samplesData = mutableListOf<Int>()
                    samplesData.addAll(sample.greenSamples.map { it })
                    samplesData.addAll(sample.redSamples.map { it })
                    samplesData.addAll(sample.irSamples.map { it })
                    listOfSamples.add(PolarPpgData.PolarPpgSample(sample.timeStamp.toLong(), samplesData))
                }
            }
        }
        return PolarPpgData(listOfSamples, type)
    }

    fun mapPMDClientPpiDataToPolarOhrPpiData(ppiData: PpiData): PolarPpiData {
        val samples: MutableList<PolarPpiData.PolarPpiSample> = mutableListOf()
        for ((hr, ppInMs, ppErrorEstimate, blockerBit, skinContactStatus, skinContactSupported) in ppiData.ppiSamples) {
            samples.add(
                PolarPpiData.PolarPpiSample(ppInMs, ppErrorEstimate, hr, blockerBit != 0, skinContactStatus != 0, skinContactSupported != 0)
            )
        }
        return PolarPpiData(samples)
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
                    ppgQuality = sample.ppgQuality,
                    correctedHr = sample.correctedHr,
                    rrsMs = emptyList(),
                    rrAvailable = false,
                    contactStatus = false,
                    contactStatusSupported = false
                )
            )
        }
        return PolarHrData(samples)
    }

    fun mapPMDClientLocationDataToPolarLocationData(location: GnssLocationData): PolarLocationData {
        val locationDataSamples = ArrayList<PolarLocationDataSample>()
        for (sample in location.gnssLocationDataSamples) {
            when (sample) {
                is GnssLocationData.GnssCoordinateSample -> {
                    val gpsCoordinatesSample = GpsCoordinatesSample(
                        timeStamp = sample.timeStamp.toLong(),
                        latitude = sample.latitude,
                        longitude = sample.longitude,
                        time = sample.date,
                        cumulativeDistance = sample.cumulativeDistance,
                        speed = sample.speed,
                        usedAccelerationSpeed = sample.usedAccelerationSpeed,
                        coordinateSpeed = sample.coordinateSpeed,
                        accelerationSpeedFactor = sample.accelerationSpeedFactor,
                        course = sample.course,
                        gpsChipSpeed = sample.gpsChipSpeed,
                        fix = sample.fix,
                        speedFlag = sample.speedFlag,
                        fusionState = sample.fusionState
                    )
                    locationDataSamples.add(gpsCoordinatesSample)
                }
                is GnssLocationData.GnssGpsNMEASample -> {
                    val gpsNMEASample = GpsNMEASample(
                        timeStamp = sample.timeStamp.toLong(),
                        measurementPeriod = sample.measurementPeriod,
                        statusFlags = sample.statusFlags,
                        nmeaMessage = sample.nmeaMessage
                    )
                    locationDataSamples.add(gpsNMEASample)
                }
                is GnssLocationData.GnssSatelliteDilutionSample -> {
                    val gpsCoordinatesSample = GpsSatelliteDilutionSample(
                        timeStamp = sample.timeStamp.toLong(),
                        dilution = sample.dilution, altitude = sample.altitude,
                        numberOfSatellites = sample.numberOfSatellites, fix = sample.fix
                    )
                    locationDataSamples.add(gpsCoordinatesSample)
                }
                is GnssLocationData.GnssSatelliteSummarySample -> {
                    val gpsSatelliteSummarySample = GpsSatelliteSummarySample(
                        timeStamp = sample.timeStamp.toLong(),
                        seenSatelliteSummaryBand1 = SatelliteSummary(
                            gpsNbrOfSat = sample.seenGnssSatelliteSummaryBand1.gpsNbrOfSat,
                            gpsMaxSnr = sample.seenGnssSatelliteSummaryBand1.gpsMaxSnr,
                            glonassNbrOfSat = sample.seenGnssSatelliteSummaryBand1.glonassNbrOfSat,
                            glonassMaxSnr = sample.seenGnssSatelliteSummaryBand1.glonassMaxSnr,
                            galileoNbrOfSat = sample.seenGnssSatelliteSummaryBand1.galileoNbrOfSat,
                            galileoMaxSnr = sample.seenGnssSatelliteSummaryBand1.galileoMaxSnr,
                            beidouNbrOfSat = sample.seenGnssSatelliteSummaryBand1.beidouNbrOfSat,
                            beidouMaxSnr = sample.seenGnssSatelliteSummaryBand1.beidouMaxSnr,
                            nbrOfSat = sample.seenGnssSatelliteSummaryBand1.nbrOfSat,
                            snrTop5Avg = sample.seenGnssSatelliteSummaryBand1.snrTop5Avg,
                        ),
                        usedSatelliteSummaryBand1 = SatelliteSummary(
                            gpsNbrOfSat = sample.usedGnssSatelliteSummaryBand1.gpsNbrOfSat,
                            gpsMaxSnr = sample.usedGnssSatelliteSummaryBand1.gpsMaxSnr,
                            glonassNbrOfSat = sample.usedGnssSatelliteSummaryBand1.glonassNbrOfSat,
                            glonassMaxSnr = sample.usedGnssSatelliteSummaryBand1.glonassMaxSnr,
                            galileoNbrOfSat = sample.usedGnssSatelliteSummaryBand1.galileoNbrOfSat,
                            galileoMaxSnr = sample.usedGnssSatelliteSummaryBand1.galileoMaxSnr,
                            beidouNbrOfSat = sample.usedGnssSatelliteSummaryBand1.beidouNbrOfSat,
                            beidouMaxSnr = sample.usedGnssSatelliteSummaryBand1.beidouMaxSnr,
                            nbrOfSat = sample.usedGnssSatelliteSummaryBand1.nbrOfSat,
                            snrTop5Avg = sample.usedGnssSatelliteSummaryBand1.snrTop5Avg,
                        ),
                        seenSatelliteSummaryBand2 = SatelliteSummary(
                            gpsNbrOfSat = sample.seenGnssSatelliteSummaryBand2.gpsNbrOfSat,
                            gpsMaxSnr = sample.seenGnssSatelliteSummaryBand2.gpsMaxSnr,
                            glonassNbrOfSat = sample.seenGnssSatelliteSummaryBand2.glonassNbrOfSat,
                            glonassMaxSnr = sample.seenGnssSatelliteSummaryBand2.glonassMaxSnr,
                            galileoNbrOfSat = sample.seenGnssSatelliteSummaryBand2.galileoNbrOfSat,
                            galileoMaxSnr = sample.seenGnssSatelliteSummaryBand2.galileoMaxSnr,
                            beidouNbrOfSat = sample.seenGnssSatelliteSummaryBand2.beidouNbrOfSat,
                            beidouMaxSnr = sample.seenGnssSatelliteSummaryBand2.beidouMaxSnr,
                            nbrOfSat = sample.seenGnssSatelliteSummaryBand2.nbrOfSat,
                            snrTop5Avg = sample.seenGnssSatelliteSummaryBand2.snrTop5Avg,
                        ),
                        usedSatelliteSummaryBand2 = SatelliteSummary(
                            gpsNbrOfSat = sample.usedGnssSatelliteSummaryBand2.gpsNbrOfSat,
                            gpsMaxSnr = sample.usedGnssSatelliteSummaryBand2.gpsMaxSnr,
                            glonassNbrOfSat = sample.usedGnssSatelliteSummaryBand2.glonassNbrOfSat,
                            glonassMaxSnr = sample.usedGnssSatelliteSummaryBand2.glonassMaxSnr,
                            galileoNbrOfSat = sample.usedGnssSatelliteSummaryBand2.galileoNbrOfSat,
                            galileoMaxSnr = sample.usedGnssSatelliteSummaryBand2.galileoMaxSnr,
                            beidouNbrOfSat = sample.usedGnssSatelliteSummaryBand2.beidouNbrOfSat,
                            beidouMaxSnr = sample.usedGnssSatelliteSummaryBand2.beidouMaxSnr,
                            nbrOfSat = sample.usedGnssSatelliteSummaryBand2.nbrOfSat,
                            snrTop5Avg = sample.usedGnssSatelliteSummaryBand2.snrTop5Avg,
                        ), maxSnr = sample.maxSnr
                    )
                    locationDataSamples.add(gpsSatelliteSummarySample)
                }
            }
        }
        return PolarLocationData(samples = locationDataSamples)
    }

    fun mapPmdClientEcgDataToPolarEcg(ecgData: EcgData): PolarEcgData {
        val ecgDataSamples = mutableListOf<PolarEcgDataSample>()
        for (sample in ecgData.ecgSamples) {
            when (sample) {
                is EcgData.EcgSample -> {
                    val ecgSample = EcgSample(timeStamp = sample.timeStamp.toLong(), voltage = sample.microVolts)
                    ecgDataSamples.add(ecgSample)
                }
                is EcgData.EcgSampleFrameType3 -> {
                    val ecgSample = FecgSample(timeStamp = sample.timeStamp.toLong(), ecg = sample.data0, bioz = sample.data1, status = sample.status)
                    ecgDataSamples.add(ecgSample)
                }
            }
        }
        return PolarEcgData(ecgDataSamples)
    }

    fun mapPmdClientAccDataToPolarAcc(accData: AccData): PolarAccelerometerData {
        val samples: MutableList<PolarAccelerometerData.PolarAccelerometerDataSample> = mutableListOf()
        for ((timeStamp, x, y, z) in accData.accSamples) {
            samples.add(PolarAccelerometerData.PolarAccelerometerDataSample(timeStamp.toLong(), x, y, z))
        }
        return PolarAccelerometerData(samples)
    }

    fun mapPmdClientGyroDataToPolarGyro(gyroData: GyrData): PolarGyroData {
        val samples: MutableList<PolarGyroData.PolarGyroDataSample> = mutableListOf()
        for ((timeStamp, x, y, z) in gyroData.gyrSamples) {
            samples.add(PolarGyroData.PolarGyroDataSample(timeStamp.toLong(), x, y, z))
        }
        return PolarGyroData(samples)
    }

    fun mapPmdClientMagDataToPolarMagnetometer(magData: MagData): PolarMagnetometerData {
        val samples: MutableList<PolarMagnetometerData.PolarMagnetometerDataSample> = mutableListOf()
        for ((timeStamp, x, y, z) in magData.magSamples) {
            samples.add(PolarMagnetometerData.PolarMagnetometerDataSample(timeStamp.toLong(), x, y, z))
        }
        return PolarMagnetometerData(samples)
    }

    fun mapPmdClientPressureDataToPolarPressure(pressureData: PressureData): PolarPressureData {
        val samples: MutableList<PolarPressureData.PolarPressureDataSample> = mutableListOf()
        for ((timeStamp, pressure) in pressureData.pressureSamples) {
            samples.add(PolarPressureData.PolarPressureDataSample(timeStamp.toLong(), pressure))
        }
        return PolarPressureData(samples)
    }

    fun mapPolarFeatureToPmdClientMeasurementType(polarFeature: PolarBleApi.PolarDeviceDataType): PmdMeasurementType {
        return when (polarFeature) {
            PolarBleApi.PolarDeviceDataType.ECG -> PmdMeasurementType.ECG
            PolarBleApi.PolarDeviceDataType.ACC -> PmdMeasurementType.ACC
            PolarBleApi.PolarDeviceDataType.PPG -> PmdMeasurementType.PPG
            PolarBleApi.PolarDeviceDataType.PPI -> PmdMeasurementType.PPI
            PolarBleApi.PolarDeviceDataType.GYRO -> PmdMeasurementType.GYRO
            PolarBleApi.PolarDeviceDataType.MAGNETOMETER -> PmdMeasurementType.MAGNETOMETER
            PolarBleApi.PolarDeviceDataType.PRESSURE -> PmdMeasurementType.PRESSURE
            PolarBleApi.PolarDeviceDataType.LOCATION -> PmdMeasurementType.LOCATION
            PolarBleApi.PolarDeviceDataType.TEMPERATURE -> PmdMeasurementType.TEMPERATURE
            PolarBleApi.PolarDeviceDataType.SKIN_TEMPERATURE -> PmdMeasurementType.SKIN_TEMP
            PolarBleApi.PolarDeviceDataType.HR -> PmdMeasurementType.OFFLINE_HR
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
            PmdMeasurementType.LOCATION -> PolarBleApi.PolarDeviceDataType.LOCATION
            PmdMeasurementType.PRESSURE -> PolarBleApi.PolarDeviceDataType.PRESSURE
            PmdMeasurementType.TEMPERATURE -> PolarBleApi.PolarDeviceDataType.TEMPERATURE
            PmdMeasurementType.OFFLINE_HR -> PolarBleApi.PolarDeviceDataType.HR
            PmdMeasurementType.OFFLINE_TEMP -> PolarBleApi.PolarDeviceDataType.TEMPERATURE
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

    fun mapPMDClientOfflineTemperatureDataToPolarTemperatureData(offlineTemperatureData: TemperatureData): PolarTemperatureData {
        val samples: MutableList<PolarTemperatureData.PolarTemperatureDataSample> = mutableListOf()
        for (sample in offlineTemperatureData.temperatureSamples) {
            samples.add(
                PolarTemperatureData.PolarTemperatureDataSample(
                    timeStamp = sample.timeStamp.toLong(),
                    temperature = sample.temperature
                )
            )
        }
        return PolarTemperatureData(samples)
    }

    fun mapPmdClientTemperatureDataToPolarTemperature(temperatureData: TemperatureData): PolarTemperatureData {
        val samples: MutableList<PolarTemperatureData.PolarTemperatureDataSample> = mutableListOf()
        for ((timeStamp, temperature) in temperatureData.temperatureSamples) {
            samples.add(PolarTemperatureData.PolarTemperatureDataSample(timeStamp.toLong(), temperature))
        }
        return PolarTemperatureData(samples)
    }

    fun mapPmdClientSkinTemperatureDataToPolarTemperatureData(skinTemperatureData: SkinTemperatureData): PolarTemperatureData {
        val samples: MutableList<PolarTemperatureData.PolarTemperatureDataSample> = mutableListOf()
        for ((timeStamp, temperature) in skinTemperatureData.skinTemperatureSamples) {
            samples.add(PolarTemperatureData.PolarTemperatureDataSample(timeStamp.toLong(), temperature))
        }
        return PolarTemperatureData(samples)
    }
}

