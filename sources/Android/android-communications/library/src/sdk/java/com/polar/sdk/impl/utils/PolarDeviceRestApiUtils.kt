package com.polar.sdk.impl.utils

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.RestApiEventPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onSubscription
import protocol.PftpNotification.PbPFtpDevToHostNotification
import protocol.PftpNotification.PbPftpDHRestApiEvent
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

fun BlePsFtpClient.receiveRestApiEventData(
    identifier: String,
    onSubscribed: suspend () -> Unit = {}
): Flow<Array<ByteArray>> {
    val notifications = waitForNotification()
    // onSubscription runs only after the shared notification subscription is registered, so the
    // caller can send its REST subscribe request without racing ahead and losing the first event.
    val ready = if (notifications is SharedFlow) {
        notifications.onSubscription { onSubscribed() }
    } else {
        notifications.onStart { onSubscribed() }
    }
    return ready
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

fun BlePsFtpClient.receiveRestApiEvents(
    identifier: String,
    onSubscribed: suspend () -> Unit = {},
    eventKey: String? = null
): Flow<List<String>> {
    val tag = "BlePsFtpClient"
    return receiveRestApiEventData(identifier, onSubscribed)
        .map { array -> array.map { it.toString(Charsets.UTF_8) } }
        .map { events ->
            eventKey?.let { key -> events.filter { json -> jsonObjectContainsKey(json, key) } } ?: events
        }
        .filter { events -> eventKey == null || events.isNotEmpty() }
        .onEach { item ->
            BleLogger.d(tag, "Receive REST API events emitted item: $item")
        }
        .catch { error ->
            BleLogger.d(tag, "Receive REST API events Error occurred: ${error.message}")
            throw error
        }
}

private fun jsonObjectContainsKey(json: String, key: String): Boolean {
    val root = runCatching { JsonParser().parse(json) }.getOrNull() ?: return false
    return root.isJsonObject && root.asJsonObject.has(key)
}

/**
 * Parses string to REST API parameter JSON string to data class objects that subclass RestApiEventPayload
 */
inline fun <reified T : RestApiEventPayload> String.toObject(): T = Gson().fromJson(this, T::class.java)
