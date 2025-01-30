package com.polar.androidcommunications.api.ble.model.polar

import java.util.*

class BlePolarDeviceCapabilitiesUtility {

    enum class FileSystemType {
        UNKNOWN_FILE_SYSTEM,
        H10_FILE_SYSTEM,
        SAGRFC2_FILE_SYSTEM
    }

    companion object {
        private const val DEVICE_TYPE_SENSE = "sense"
        private const val DEVICE_TYPE_OH1 = "oh1"
        private const val DEVICE_TYPE_H10 = "h10"
        private const val DEVICE_TYPE_360 = "360"
        private const val DEVICE_TYPE_INW5T = "polar_inw5t"
        private const val DEVICE_TYPE_IGNITE_3 = "Ignite 3"
        private const val DEVICE_TYPE_GRIT_X2_PRO = "Grit X2 Pro"
        private const val DEVICE_TYPE_VANTAGE_V3 = "Vantage V3"

        /**
         * Get type of filesystem the device supports
         *
         * @param deviceType device type
         * @return type of the file system supported or unknown file system type
         */
        @JvmStatic
        fun getFileSystemType(deviceType: String): FileSystemType {
            return when (deviceType.lowercase(Locale.getDefault())) {
                "sense" -> FileSystemType.SAGRFC2_FILE_SYSTEM
                "oh1" -> FileSystemType.SAGRFC2_FILE_SYSTEM
                "h10" -> FileSystemType.H10_FILE_SYSTEM
                else -> FileSystemType.SAGRFC2_FILE_SYSTEM
            }
        }

        /**
         * Check if device is supporting recording start and stop over BLE
         *
         * @param deviceType device type
         * @return true if device supports recoding
         */
        @JvmStatic
        fun isRecordingSupported(deviceType: String): Boolean {
            return when (deviceType.lowercase(Locale.getDefault())) {
                DEVICE_TYPE_H10 -> true
                else -> false
            }
        }

        @JvmStatic
        fun isFirmwareUpdateSupported(deviceType: String): Boolean {
            return when (deviceType.lowercase(Locale.getDefault())) {
                DEVICE_TYPE_OH1 -> false
                else -> true
            }
        }

        @JvmStatic
        fun isDeviceSensor(deviceType: String): Boolean {
            return when (deviceType.lowercase(Locale.getDefault())) {
                DEVICE_TYPE_OH1 -> true
                DEVICE_TYPE_H10 -> true
                DEVICE_TYPE_SENSE -> true
                DEVICE_TYPE_360 -> true
                DEVICE_TYPE_INW5T -> true
                else -> false
            }
        }

        @JvmStatic
        fun isActivityDataSupported(deviceType: String): Boolean {
            return when (deviceType.lowercase(Locale.getDefault())) {
                DEVICE_TYPE_360 -> true
                DEVICE_TYPE_INW5T -> true
                DEVICE_TYPE_IGNITE_3 -> true
                DEVICE_TYPE_GRIT_X2_PRO -> true
                DEVICE_TYPE_VANTAGE_V3 -> true
                else -> false
            }
        }
    }
}