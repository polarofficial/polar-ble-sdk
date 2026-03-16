package com.polar.androidcommunications.api.ble.model.polar

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.polar.androidcommunications.api.ble.BleLogger
import java.io.File

class BlePolarDeviceCapabilitiesUtility {

    enum class FileSystemType {
        UNKNOWN_FILE_SYSTEM,
        H10_FILE_SYSTEM,
        POLAR_FILE_SYSTEM_V2
    }

    data class DeviceCapabilitiesConfig(
        @SerializedName("version") val version: String = "1.0",
        @SerializedName("devices") val devices: Map<String, DeviceCapabilities> = emptyMap(),
        @SerializedName("defaults") val defaults: DefaultsSection = DefaultsSection()
    )

    data class DeviceCapabilities(
        @SerializedName("fileSystemType") val fileSystemType: String? = null,
        @SerializedName("recordingSupported") val recordingSupported: Boolean? = null,
        @SerializedName("firmwareUpdateSupported") val firmwareUpdateSupported: Boolean? = null,
        @SerializedName("isDeviceSensor") val isDeviceSensor: Boolean? = null,
        @SerializedName("activityDataSupported") val activityDataSupported: Boolean? = null
    )

    data class DefaultsSection(
        @SerializedName("fileSystemType") val fileSystemType: String = "POLAR_FILE_SYSTEM_V2",
        @SerializedName("recordingSupported") val recordingSupported: Boolean = false,
        @SerializedName("firmwareUpdateSupported") val firmwareUpdateSupported: Boolean = true,
        @SerializedName("isDeviceSensor") val isDeviceSensor: Boolean = false,
        @SerializedName("activityDataSupported") val activityDataSupported: Boolean = false
    )

    companion object {

        private const val TAG = "BlePolarDeviceCapabilitiesUtility"
        private const val FILE_NAME = "polar_device_capabilities.json"

        private val gson = Gson()
        private lateinit var config: DeviceCapabilitiesConfig
        private var initialized = false

        /**
         * Initializes the device capabilities configuration.
         *
         * This method first attempts to read the configuration JSON from the public
         * Documents/PolarConfig folder, allowing the config to be overridden at runtime.
         * If that file does not exist, it copies the bundled default from the app's assets.
         *
         * If external storage is unavailable (e.g. permission not granted), the method falls
         * back to reading the bundled asset directly. This guarantees that the SDK is always
         * initialized regardless of whether the user has granted storage permission.
         *
         * The configuration is parsed and stored in memory for subsequent API calls.
         * This method is safe to call multiple times and will only initialize once.
         *
         * @param context application or activity context
         */
        @JvmStatic
        @Synchronized
        fun initialize(context: Context) {
            if (initialized) return

            // Try loading from the user-overridable external config first

            try {

                val assetJson = context.assets.open(FILE_NAME)
                    .bufferedReader()
                    .use { it.readText() }

                val assetConfig =
                    gson.fromJson(assetJson, DeviceCapabilitiesConfig::class.java)

                val folder = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "PolarConfig"
                )
                if (folder.exists() || folder.mkdirs()) {
                    val configFile = File(folder, FILE_NAME)

                    if (!configFile.exists()) {
                        configFile.writeText(assetJson)
                        Log.d(TAG, "Default config copied to $configFile")
                    }

                    val userJson = configFile.inputStream().bufferedReader().use { it.readText() }
                    val userConfig = gson.fromJson(userJson, DeviceCapabilitiesConfig::class.java)

                    if (assetConfig.version != userConfig.version) {
                        BleLogger.d(TAG, "Config version changed ${userConfig.version} → ${assetConfig.version}, migrating")

                        val merged = mergeConfigs(userConfig, assetConfig)

                        configFile.writeText(gson.toJson(merged))
                        config = merged
                    } else {
                        config = userConfig
                    }

                    initialized = true
                    Log.d(TAG, "BlePolarDeviceCapabilitiesUtility initialized successfully")
                    return
                }
            } catch (e: Exception) {
                BleLogger.w(
                    TAG,
                    "Could not load capabilities from external storage, falling back to bundled asset: ${e.message}"
                )
            }

            // Fallback: read directly from the bundled AAR asset (no permissions required)
            try {
                val json = context.assets.open(FILE_NAME).bufferedReader().use { it.readText() }
                config = gson.fromJson(json, DeviceCapabilitiesConfig::class.java)
                initialized = true
                Log.d(TAG, "BlePolarDeviceCapabilitiesUtility initialized from bundled asset")
            } catch (e: Exception) {
                BleLogger.e(TAG, "Failed to initialize capabilities from bundled asset: ${e.message}")
            }
        }

        private fun mergeConfigs(
            user: DeviceCapabilitiesConfig,
            asset: DeviceCapabilitiesConfig
        ): DeviceCapabilitiesConfig {

            val mergedDevices = asset.devices.toMutableMap()

            user.devices.forEach { (key, userDevice) ->
                val assetDevice = asset.devices[key]

                mergedDevices[key] = DeviceCapabilities(
                    fileSystemType = userDevice.fileSystemType ?: assetDevice?.fileSystemType,
                    recordingSupported = userDevice.recordingSupported ?: assetDevice?.recordingSupported,
                    firmwareUpdateSupported = userDevice.firmwareUpdateSupported ?: assetDevice?.firmwareUpdateSupported,
                    isDeviceSensor = userDevice.isDeviceSensor ?: assetDevice?.isDeviceSensor,
                    activityDataSupported = userDevice.activityDataSupported ?: assetDevice?.activityDataSupported
                )
            }

            return DeviceCapabilitiesConfig(
                version = asset.version,
                devices = mergedDevices,
                defaults = asset.defaults
            )
        }

        private fun ensureInitialized(): Boolean {
            if (!initialized) {
                BleLogger.w(
                    TAG,
                    "BlePolarDeviceCapabilitiesUtility used before initialize()"
                )
                return false
            }
            return true
        }

        /**
         * Get type of filesystem the device supports.
         *
         * @param deviceType device type
         * @return type of the file system supported or unknown file system type
         */
        @JvmStatic
        fun getFileSystemType(deviceType: String): FileSystemType {
            if (!ensureInitialized()) return FileSystemType.UNKNOWN_FILE_SYSTEM

            val fs = config.devices[deviceType.lowercase()]?.fileSystemType
                ?: config.defaults.fileSystemType

            val result = when (fs) {
                "H10_FILE_SYSTEM" -> FileSystemType.H10_FILE_SYSTEM
                "POLAR_FILE_SYSTEM_V2" -> FileSystemType.POLAR_FILE_SYSTEM_V2
                else -> FileSystemType.UNKNOWN_FILE_SYSTEM
            }

            BleLogger.d(TAG, "getFileSystemType($deviceType) -> $result")
            return result
        }

        /**
         * Check if device supports recording start and stop.
         *
         * @param deviceType device type
         * @return true if device supports recording
         */
        @JvmStatic
        fun isRecordingSupported(deviceType: String): Boolean {
            if (!ensureInitialized()) return false

            val result = config.devices[deviceType.lowercase()]?.recordingSupported
                ?: config.defaults.recordingSupported
            BleLogger.d(TAG, "isRecordingSupported($deviceType) -> $result")
            return result
        }

        /**
         * Check if device supports firmware update.
         *
         * @param deviceType device type
         * @return true if firmware update is supported
         */
        @JvmStatic
        fun isFirmwareUpdateSupported(deviceType: String): Boolean {
            if (!ensureInitialized()) return false

            val result = config.devices[deviceType.lowercase()]?.firmwareUpdateSupported
                ?: config.defaults.firmwareUpdateSupported

            BleLogger.d(TAG, "isFirmwareUpdateSupported($deviceType) -> $result")
            return result
        }

        /**
         * Check if device is considered a sensor device.
         *
         * @param deviceType device type
         * @return true if device is a sensor
         */
        @JvmStatic
        fun isDeviceSensor(deviceType: String): Boolean {
            if (!ensureInitialized()) return false

            val result = config.devices[deviceType.lowercase()]?.isDeviceSensor
                ?: config.defaults.isDeviceSensor

            BleLogger.d(TAG, "isDeviceSensor($deviceType) -> $result")
            return result
        }

        /**
         * Check if device supports activity data storage and sync.
         *
         * @param deviceType device type
         * @return true if activity data is supported
         */
        @JvmStatic
        fun isActivityDataSupported(deviceType: String): Boolean {
            if (!ensureInitialized()) return false

            val result = config.devices[deviceType.lowercase()]?.activityDataSupported
                ?: config.defaults.activityDataSupported

            BleLogger.d(TAG, "isActivityDataSupported($deviceType) -> $result")
            return result
        }
    }
}