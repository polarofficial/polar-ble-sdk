// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl

import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.PftpResponseError
import com.polar.sdk.api.PolarLoggingApi
import com.polar.sdk.api.errors.PolarServiceNotAvailable
import com.polar.sdk.api.model.LogConfig
import com.polar.sdk.api.model.PolarDeviceLog
import com.polar.sdk.impl.utils.PolarFileUtils
import com.polar.sdk.impl.utils.PolarServiceClientUtils
import protocol.PftpError.PbPFtpError
import protocol.PftpRequest
import java.io.ByteArrayInputStream

/**
 * Implementation of [PolarLoggingApi].
 *
 * Handles fetching of device diagnostic/trace log files and log configuration
 * management via the PFTP file transfer protocol.
 */
internal class PolarLoggingApiImpl(
    private val listener: BleDeviceListener
) : PolarLoggingApi {

    companion object {
        private const val TAG = "PolarLoggingApiImpl"
    }

    // ---- Internal helpers ---------------------------------------------------------------

    private fun isFileNotFound(throwable: Throwable): Boolean =
        throwable is PftpResponseError &&
        throwable.error == PbPFtpError.NO_SUCH_FILE_OR_DIRECTORY.number

    // ---- PolarLoggingApi ----------------------------------------------------------------

    override suspend fun exportDeviceLogs(identifier: String): List<PolarDeviceLog> {
        val results = mutableListOf<PolarDeviceLog>()

        suspend fun tryFetch(path: String): Boolean {
            return try {
                val data = PolarFileUtils.getFile(identifier, path, listener, TAG)
                results.add(PolarDeviceLog(path = path, data = data))
                BleLogger.d(TAG, "exportDeviceLogs: fetched $path from $identifier")
                true
            } catch (throwable: Throwable) {
                if (isFileNotFound(throwable)) {
                    BleLogger.d(TAG, "exportDeviceLogs: $path not found on device, skipping")
                } else {
                    BleLogger.w(TAG, "exportDeviceLogs: $path skipped (${throwable.message})")
                }
                false
            }
        }

        // Static log files
        for (path in listOf("/ERRORLOG.BPB", "/ERRORLO2.BPB", "/SYSLOG.TXT")) {
            tryFetch(path)
        }

        // Telemetry trace files: TRC1.BIN, TRC2.BIN, … stop at first missing index
        var index = 1
        while (tryFetch("/TRC$index.BIN")) { index++ }

        // Debug trace files: DBGTRC1.BIN, DBGTRC2.BIN, … stop at first missing index
        index = 1
        while (tryFetch("/DBGTRC$index.BIN")) { index++ }

        BleLogger.d(TAG, "exportDeviceLogs: completed, fetched ${results.size} file(s) from $identifier")
        return results
    }

    override suspend fun getLogConfig(identifier: String): LogConfig {
        val byteArray = PolarFileUtils.getFile(identifier, LogConfig.LOG_CONFIG_FILENAME, listener, TAG)
        return try {
            LogConfig.fromBytes(byteArray)
        } catch (e: Exception) {
            BleLogger.e(TAG, "Failed to get LogConfig: $e")
            throw e
        }
    }

    override suspend fun setLogConfig(identifier: String, logConfig: LogConfig) {
        val session = PolarServiceClientUtils.sessionPsFtpClientReady(identifier, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: throw PolarServiceNotAvailable()
        BleLogger.d(TAG, "setLogConfig: writing config to device $identifier path=${LogConfig.LOG_CONFIG_FILENAME}")
        val builder = PftpRequest.PbPFtpOperation.newBuilder()
        builder.command = PftpRequest.PbPFtpOperation.Command.PUT
        builder.path = LogConfig.LOG_CONFIG_FILENAME
        val data = ByteArrayInputStream(logConfig.toProto().toByteArray())
        client.write(builder.build().toByteArray(), data).collect {}
    }
}
