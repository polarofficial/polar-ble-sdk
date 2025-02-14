package com.polar.sdk.impl.utils

import com.google.gson.Gson
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.sdk.api.RestApiEventPayload
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.ByteArrayOutputStream
import protocol.PftpNotification.PbPFtpDevToHostNotification
import protocol.PftpNotification.PbPftpDHRestApiEvent
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream


fun BlePsFtpClient.receiveRestApiEventData(identifier: String): Flowable<Array<ByteArray>> {
    return waitForNotification(Schedulers.newThread())
        .filter { it: BlePsFtpUtils.PftpNotificationMessage ->
            it.id == PbPFtpDevToHostNotification.REST_API_EVENT_VALUE
        }
        .map { it: BlePsFtpUtils.PftpNotificationMessage ->
            PbPftpDHRestApiEvent.parseFrom(it.byteArrayOutputStream.toByteArray())
        }
        .map { it: PbPftpDHRestApiEvent ->
            if (it.hasUncompressed() && it.uncompressed) {
                it.eventList.map { byteString ->
                    byteString.toByteArray()
                }
            } else {
                it.eventList.map { byteString ->
                    decompressProtobufByteArray(byteString.toByteArray())
                }
            }
        }
        .map { it.toTypedArray() }
}

private fun BlePsFtpClient.decompressProtobufByteArray(input: ByteArray): ByteArray {
    val BUFFER_SIZE = 10 * 1024
    ByteArrayInputStream(input).use { byteArrayInputStream ->
        GZIPInputStream(byteArrayInputStream).use { gzipInputStream ->
            ByteArrayOutputStream().use { byteArrayOutputStream ->
                val buffer = ByteArray(BUFFER_SIZE)
                var len: Int
                while (gzipInputStream.read(buffer).also { len = it } != -1) {
                    byteArrayOutputStream.write(buffer, 0, len)
                }
                return byteArrayOutputStream.toByteArray()
            }
        }
    }
}

fun BlePsFtpClient.receiveRestApiEvents (identifier: String): Flowable<List<String>>{
    val TAG = "BlePsFtpClient"
    return receiveRestApiEventData(identifier)
        .map { array: Array<ByteArray> ->
            array.map { it.toString(kotlin.text.Charsets.UTF_8) }
        }
        .doOnNext { item ->
            BleLogger.d(TAG, "Receive REST API events emitted item: $item")
        }
        .doOnError { error ->
            BleLogger.d(TAG, "Receive REST API events Error occurred: ${error.message}")
        }
        .doOnComplete {
            BleLogger.d(TAG, "Receive REST API events for $identifier completed")
        }
}

/**
 * Parses string to REST API parameter JSON string to data class objects that subclass RestApiEventPayload
 */
inline fun <reified T: RestApiEventPayload> String.toObject(): T = Gson().fromJson(this, T::class.java)