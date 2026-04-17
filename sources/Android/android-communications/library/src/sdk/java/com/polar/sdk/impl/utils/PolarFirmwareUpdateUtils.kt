package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.PolarFirmwareVersionInfo
import fi.polar.remote.representation.protobuf.Device
import fi.polar.remote.representation.protobuf.Structures
import protocol.PftpRequest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

internal object PolarFirmwareUpdateUtils {

    /**
     * Comparator for sorting FW files so that the order doesn't matter as long as
     * the SYSUPDAT.IMG file is the last one (since it makes the device boot itself).
     */
    class FwFileComparator : Comparator<File> {
        companion object {
            private const val SYSUPDAT_IMG = "SYSUPDAT.IMG"
        }

        override fun compare(f1: File, f2: File): Int {
            return when {
                f1.name.contains(SYSUPDAT_IMG) -> 1
                f2.name.contains(SYSUPDAT_IMG) -> -1
                else -> 0
            }
        }
    }

    const val FIRMWARE_UPDATE_FILE_PATH = "/SYSUPDAT.IMG"
    const val BUFFER_SIZE = 8192

    private const val DEVICE_FIRMWARE_INFO_PATH = "/DEVICE.BPB"
    private const val TAG = "PolarFirmwareUpdateUtils"

    suspend fun readDeviceFirmwareInfo(client: BlePsFtpClient, deviceId: String): PolarFirmwareVersionInfo {
        BleLogger.d(TAG, "readDeviceFirmwareInfo: $deviceId")
        val response = client.request(
            PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                .setPath(DEVICE_FIRMWARE_INFO_PATH)
                .build()
                .toByteArray()
        )
        val proto = Device.PbDeviceInfo.parseFrom(response.toByteArray())
        return PolarFirmwareVersionInfo(
            deviceFwVersion = devicePbVersionToString(proto.deviceVersion),
            deviceModelName = proto.modelName,
            deviceHardwareCode = proto.hardwareCode
        )
    }

    fun isAvailableFirmwareVersionHigher(currentVersion: String, availableVersion: String): Boolean {
        val current = currentVersion.split(".").map { it.toInt() }
        val available = availableVersion.split(".").map { it.toInt() }

        for (i in current.indices) {
            if (available.size > i) {
                if (current[i] < available[i]) {
                    return true
                } else if (current[i] > available[i]) {
                    return false
                }
            }
        }
        return available.size > current.size
    }

    fun unzipFirmwarePackage(zipBytes: ByteArray): ByteArray {
        try {
            val byteArrayInputStream = ByteArrayInputStream(zipBytes)
            val zipInputStream = ZipInputStream(byteArrayInputStream)
            val byteArrayOutputStream = ByteArrayOutputStream()

            zipInputStream.nextEntry
            val buffer = ByteArray(BUFFER_SIZE)
            var length: Int

            while (zipInputStream.read(buffer).also { length = it } != -1) {
                byteArrayOutputStream.write(buffer, 0, length)
            }

            zipInputStream.closeEntry()
            zipInputStream.close()

            return byteArrayOutputStream.toByteArray()
        } catch (e: Exception) {
            BleLogger.e(TAG, "Failed to unzip firmware package: $e")
            throw e
        }
    }

    private fun devicePbVersionToString(pbVersion: Structures.PbVersion): String
            = "${pbVersion.major}.${pbVersion.minor}.${pbVersion.patch}"
}