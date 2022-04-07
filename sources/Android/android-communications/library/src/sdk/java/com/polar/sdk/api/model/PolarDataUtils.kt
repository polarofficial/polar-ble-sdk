package com.polar.sdk.api.model

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.EcgData
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.PpgData

object PolarDataUtils {
    const val TAG = "PolarDataUtils"

    @JvmStatic
    fun mapPMDClientOhrDataToPolarOhr(ohrData: PpgData): PolarOhrData {
        var type: PolarOhrData.OHR_DATA_TYPE = PolarOhrData.OHR_DATA_TYPE.UNKNOWN
        val listOfSamples = mutableListOf<PolarOhrData.PolarOhrSample>()
        for (sample in ohrData.ppgSamples) {
            when (sample) {
                is PpgData.PpgDataSampleType0 -> {
                    type = PolarOhrData.OHR_DATA_TYPE.PPG3_AMBIENT1
                    val channelsData = mutableListOf<Int>()
                    channelsData.addAll(sample.ppgDataSamples)
                    channelsData.add(sample.ambientSample)
                    listOfSamples.add(PolarOhrData.PolarOhrSample(channelsData))
                }
                else -> {
                    BleLogger.w(TAG, "Not supported PPG sample type: $sample")
                }
            }
        }
        return PolarOhrData(listOfSamples, type, ohrData.timeStamp)
    }

    @JvmStatic
    fun mapPmdClientEcgDataToPolarEcg(ecgData: EcgData): PolarEcgData {
        val listOfSamples = mutableListOf<Int>()
        for (sample in ecgData.ecgSamples) {
            when (sample) {
                is EcgData.EcgSample -> {
                    listOfSamples.add(sample.microVolts)
                }
                else -> {
                    BleLogger.w(TAG, "Not supported ECG sample type: $sample")
                }
            }
        }
        return PolarEcgData(listOfSamples, ecgData.timeStamp)
    }
}