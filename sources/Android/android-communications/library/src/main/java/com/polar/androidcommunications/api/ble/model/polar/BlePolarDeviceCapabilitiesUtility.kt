package com.polar.androidcommunications.api.ble.model.polar

import java.util.*

class BlePolarDeviceCapabilitiesUtility {

    enum class FileSystemType {
        UNKNOWN_FILE_SYSTEM,
        H10_FILE_SYSTEM,
        SAGRFC2_FILE_SYSTEM
    }

    companion object {
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
                "h10" -> true
                else -> false
            }
        }
    }
}