package com.polar.androidcommunications.api.ble.model.advertisement

import androidx.annotation.VisibleForTesting

class BlePolarHrAdvertisement {
    private var currentData: ByteArray = byteArrayOf()

    val batteryStatus: Int
        get() {
            return if (currentData.isNotEmpty()) {
                currentData[0].toInt() and 0x01
            } else {
                0
            }
        }

    val sensorContact: Int
        get() {
            return if (currentData.isNotEmpty()) {
                currentData[0].toInt() and 0x02 shr 1
            } else {
                0
            }
        }

    @VisibleForTesting
    var advFrameCounter = -1
        private set
        get() {
            return if (currentData.isNotEmpty()) {
                currentData[0].toInt() and 0x1C shr 2
            } else {
                -1
            }
        }

    private var previousAdvFrameCounter = 0

    val broadcastBit: Int
        get() {
            return if (currentData.isNotEmpty()) {
                currentData[0].toInt() and 0x20 shr 5
            } else {
                0
            }
        }

    private val sensorDataType: Int
        get() {
            return if (currentData.isNotEmpty()) {
                currentData[0].toInt() and 0x40 shr 6
            } else {
                0
            }
        }

    val statusFlags: Int
        get() {
            return if (currentData.isNotEmpty()) {
                currentData[0].toInt() and 0x80 shr 7
            } else {
                0
            }
        }

    val khzCode: Int
        get() {
            return if (currentData.isNotEmpty()) {
                currentData[1].toInt() and 0x000000FF
            } else {
                0
            }
        }
    val fastAverageHr: Int
        get() {
            return if (currentData.isNotEmpty()) {
                currentData[2].toInt() and 0x000000FF
            } else {
                0
            }
        }
    val slowAverageHr: Int
        get() {
            return if (currentData.isNotEmpty() && currentData.size == 4) {
                currentData[3].toInt() and 0x000000FF
            } else if (currentData.isNotEmpty() && currentData.size == 3) {
                currentData[2].toInt() and 0x000000FF
            } else {
                0
            }
        }

    var hrForDisplay = 0
        private set
        get() = slowAverageHr
    val isPresent: Boolean
        get() = currentData.isNotEmpty()
    val isHrDataUpdated: Boolean
        get() = previousAdvFrameCounter != advFrameCounter

    fun processPolarManufacturerData(data: ByteArray) {
        previousAdvFrameCounter = advFrameCounter
        currentData = data
    }

    fun resetToDefault() {
        currentData = byteArrayOf()
        advFrameCounter = -1
    }
}