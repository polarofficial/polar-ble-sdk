package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import androidx.annotation.VisibleForTesting
import com.polar.androidcommunications.api.ble.exceptions.NegativeTimeStampError
import com.polar.androidcommunications.api.ble.exceptions.SampleSizeMissingError
import com.polar.androidcommunications.api.ble.exceptions.TimeStampAndFrequencyZeroError
import kotlin.math.round

internal object PmdTimeStampUtils {

    fun getTimeStamps(previousFrameTimeStamp: ULong, frameTimeStamp: ULong, samplesSize: Int, sampleRate: Int): List<ULong> {
        // guard
        if (samplesSize <= 0) {
            throw SampleSizeMissingError
        }

        val timeStampDelta = getTimeStampDelta(previousFrameTimeStamp, frameTimeStamp, samplesSize, sampleRate)

        // guard
        if (frameTimeStamp.toDouble() < (timeStampDelta * samplesSize.toDouble())) {
            throw NegativeTimeStampError("Sample time stamp calculation fails. The timestamps are negative, since frameTimeStamp $frameTimeStamp minus ${timeStampDelta * samplesSize} is negative")
        }

        val startTimeStamp = if (previousFrameTimeStamp == 0uL) {
            firstSampleTimeFromSampleRate(frameTimeStamp, timeStampDelta, samplesSize)
        } else {
            firstSampleTimeFromTimeStamps(previousFrameTimeStamp, timeStampDelta)
        }

        val timeStampList = MutableList(size = samplesSize - 1, init = { index ->
            round(startTimeStamp + timeStampDelta * index).toULong()
        })
        timeStampList.add(frameTimeStamp)
        return timeStampList
    }

    private fun getTimeStampDelta(previousFrameTimeStamp: ULong, timeStamp: ULong, samplesSize: Int, sampleRate: Int): Double {
        // guard
        if (previousFrameTimeStamp == 0uL && sampleRate <= 0) {
            throw TimeStampAndFrequencyZeroError("Timestamp delta cannot be calculated for the frame, because previousTimeStamp $previousFrameTimeStamp and sampleRate $sampleRate")
        }

        val delta = if (previousFrameTimeStamp == 0uL) {
            deltaFromSamplingRate(sampleRate)
        } else {
            deltaFromTimeStamps(previousFrameTimeStamp, timeStamp, samplesSize)
        }
        return delta
    }

    @VisibleForTesting
    fun deltaFromSamplingRate(samplingRate: Int): Double {
        return (1.0 / samplingRate.toDouble()) * 1000 * 1000 * 1000
    }

    fun deltaFromTimeStamps(previousTimeStamp: ULong, timeStamp: ULong, samples: Int): Double {
        val timeInBetween = timeStamp - previousTimeStamp
        return if (timeInBetween > 0u) {
            timeInBetween.toDouble() / samples.toDouble()
        } else {
            throw NegativeTimeStampError("Failed to decide delta from when previous timestamp: $previousTimeStamp timestamp: $timeStamp")
        }
    }

    private fun firstSampleTimeFromTimeStamps(previousTimeStamp: ULong, timeStampDelta: Double): Double {
        return previousTimeStamp.toDouble() + timeStampDelta
    }

    private fun firstSampleTimeFromSampleRate(lastSampleTimeStamp: ULong, timeStampDelta: Double, samplesSize: Int): Double {
        return if (samplesSize > 0) {
            val startTimeStamp = lastSampleTimeStamp.toDouble() - (timeStampDelta * (samplesSize - 1))
            if (startTimeStamp > 0) {
                startTimeStamp
            } else {
                throw NegativeTimeStampError("Failed to estimate first sample timestamp when timeStamp: $lastSampleTimeStamp delta: $timeStampDelta size: $samplesSize")
            }
        } else {
            throw NegativeTimeStampError("Failed to estimate first sample timestamp when timeStamp: $lastSampleTimeStamp delta: $timeStampDelta size: $samplesSize")
        }
    }
}