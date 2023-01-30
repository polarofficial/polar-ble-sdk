package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

internal object PmdDataModelUtils {

    fun getTimeStampDelta(previousTimeStamp: Long, newTimeStamp: Long, samplesSize: Int, samplingRate: Int): Long {
        return if (previousTimeStamp <= 0) {
            deltaFromSamplingRate(samplingRate)
        } else {
            deltaFromTimeStamps(previousTimeStamp, newTimeStamp, samplesSize)
        }
    }

    private fun deltaFromSamplingRate(samplingRate: Int): Long {
        return ((1.0 / samplingRate.toDouble()) * 1000 * 1000 * 1000).toLong()
    }

    private fun deltaFromTimeStamps(previousTimeStamp: Long, timeStamp: Long, samples: Int): Long {
        val timeInBetween = timeStamp - previousTimeStamp
        return if (timeInBetween > 0) {
            timeInBetween / samples
        } else {
            0L
        }
    }

    fun firstSampleTimeStamp(lastSampleTimeStamp: Long, timeStampDelta: Long, samplesSize: Int): Long {
        return if (samplesSize > 0) {
            val startTimeStamp = lastSampleTimeStamp - (timeStampDelta * (samplesSize - 1))
            if (startTimeStamp > 0) startTimeStamp else 0L
        } else {
            0L
        }
    }
}