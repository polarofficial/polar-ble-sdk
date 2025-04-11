package com.polar.polarsensordatacollector.utils

import android.util.Log

object StreamUtils {
    private const val TAG = "StreamUtils"

    fun calculateSampleRate(timeStampEarlier: Long, timeStampLater: Long): Double {
        val delta = timeStampLater - timeStampEarlier
        return when {
            timeStampEarlier == 0L || timeStampLater == 0L -> {
                // do not calculate if either of timestamps is zero
                0.0
            }
            delta <= 0 -> {
                Log.w(TAG, "Time ($delta) between two samples is suspicious while calculating sampleRate")
                0.0
            }
            else -> {
                1.toDouble() / delta * 1000 * 1000 * 1000
            }
        }
    }

    fun calculateSampleDelta(timeStampPreviousPacket: Long, timeStampCurrentPacket: Long, timeStampNextPacket: Long, sizeCurrentPacket: Int, sizeNextPacket: Int): Long {
        return if (timeStampPreviousPacket == 0L) {
            (timeStampNextPacket - timeStampCurrentPacket) / sizeNextPacket
        } else {
            (timeStampCurrentPacket - timeStampPreviousPacket) / sizeCurrentPacket
        }
    }

    fun estimateFirstSampleTimeStamp(delta: Long, packetTimeStamp: Long, packetSize: Int): Long {
        return packetTimeStamp - (packetSize - 1) * delta
    }
}