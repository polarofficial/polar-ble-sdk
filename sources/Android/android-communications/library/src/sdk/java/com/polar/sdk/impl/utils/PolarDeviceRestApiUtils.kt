package com.polar.sdk.impl.utils

import com.google.gson.Gson
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.RestApiEventPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import protocol.PftpNotification.PbPFtpDevToHostNotification
import protocol.PftpNotification.PbPftpDHRestApiEvent
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

fun BlePsFtpClient.receiveRestApiEventData(identifier: String): Flow<Array<ByteArray>> {
    return waitForNotification()
        .filter { it.id == PbPFtpDevToHostNotification.REST_API_EVENT_VALUE }
        .map { PbPftpDHRestApiEvent.parseFrom(it.byteArrayOutputStream.toByteArray()) }
        .map { proto ->
            if (proto.hasUncompressed() && proto.uncompressed) {
                proto.eventList.map { it.toByteArray() }
            } else {
                proto.eventList.map { decompressProtobufByteArray(it.toByteArray()) }
            }.toTypedArray()
        }
}

private fun decompressProtobufByteArray(input: ByteArray): ByteArray {
    val bufferSize = 10 * 1024
    ByteArrayInputStream(input).use { byteArrayInputStream ->
        GZIPInputStream(byteArrayInputStream).use { gzipInputStream ->
            ByteArrayOutputStream().use { byteArrayOutputStream ->
                val buffer = ByteArray(bufferSize)
                var len: Int
                while (gzipInputStream.read(buffer).also { len = it } != -1) {
                    byteArrayOutputStream.write(buffer, 0, len)
                }
                return byteArrayOutputStream.toByteArray()
            }
        }
    }
}

fun BlePsFtpClient.receiveRestApiEvents(identifier: String): Flow<List<String>> {
    val tag = "BlePsFtpClient"
    return receiveRestApiEventData(identifier)
        .map { array -> array.map { it.toString(Charsets.UTF_8) } }
        .onEach { item ->
            BleLogger.d(tag, "Receive REST API events emitted item: $item")
        }
        .catch { error ->
            BleLogger.d(tag, "Receive REST API events Error occurred: ${error.message}")
            throw error
        }
}

/**
 * Parses string to REST API parameter JSON string to data class objects that subclass RestApiEventPayload
 */
inline fun <reified T : RestApiEventPayload> String.toObject(): T = Gson().fromJson(this, T::class.java)
