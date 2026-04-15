package com.polar.androidcommunications.api.ble.model.polar

class BlePolarDeviceIdUtility private constructor() {
    init {
        throw IllegalStateException("Utility class")
    }

    companion object {
        fun isValidDeviceId(deviceId: String?): Boolean {
            if (deviceId == null) return false
            if (deviceId.length == 8) {
                return checkSumForDeviceId(
                    deviceId.toLong(16),
                    8
                ).toLong() == (deviceId.toLong(16) and 0x000000000000000FL)
            }
            return checkSumForDeviceId(deviceId.toLong(16), deviceId.length).toInt() != 0
        }

        fun assemblyFullPolarDeviceId(deviceId: String): String {
            try {
                when (deviceId.length) {
                    6 -> {
                        val crc = checkSumForDeviceId(deviceId.toLong(16), 6)
                        return deviceId + "1" + String.format("%01X", crc)
                    }

                    7 -> {
                        val crc = checkSumForDeviceId(deviceId.toLong(16), 7)
                        return deviceId + String.format("%01X", crc)
                    }

                    else -> {
                        return deviceId
                    }
                }
            } catch (ex: NumberFormatException) {
                return ""
            }
        }

        private fun checkSumForDeviceId(deviceId: Long, width: Int): Byte {
            var siftOffset = 0
            var a2: Byte = 0x01
            when (width) {
                8 -> {
                    a2 = ((deviceId shr 4) and 0x0FL).toByte()
                    siftOffset = 8
                }

                7 -> {
                    a2 = ((deviceId) and 0x0FL).toByte()
                    siftOffset = 4
                }

                6 -> {}
            }
            val a3 = ((deviceId shr siftOffset) and 0x0FL).toByte()
            val a4 = ((deviceId shr siftOffset + 4) and 0x0FL).toByte()
            val a5 = ((deviceId shr siftOffset + 8) and 0x0FL).toByte()
            val a6 = ((deviceId shr siftOffset + 12) and 0x0FL).toByte()
            val a7 = ((deviceId shr siftOffset + 16) and 0x0FL).toByte()
            val a8 = ((deviceId shr siftOffset + 20) and 0x0FL).toByte()
            return ((3 * (a2 + a4 + a6 + a8) + a3 + a5 + a7) % 16).toByte()
        }
    }
}
