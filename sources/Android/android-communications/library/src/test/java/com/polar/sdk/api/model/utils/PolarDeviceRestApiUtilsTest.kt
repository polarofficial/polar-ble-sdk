package com.polar.sdk.api.model.utils

import com.google.protobuf.ByteString
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.sdk.api.RestApiEventPayload
import com.polar.sdk.impl.utils.receiveRestApiEventData
import com.polar.sdk.impl.utils.receiveRestApiEvents
import com.polar.sdk.impl.utils.toObject
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import protocol.PftpNotification
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class PolarDeviceRestApiUtilsTest {

    private val client = mockk<BlePsFtpClient>()
    private val identifier = "AABBCC"

    // Helper data classes for toObject tests
    private data class SimplePayload(val value: String?) : RestApiEventPayload()
    private data class InnerData(val count: Int)
    private data class NestedPayload(val inner: InnerData) : RestApiEventPayload()

    private fun makeRestApiNotification(
        vararg payloads: ByteArray,
        uncompressed: Boolean
    ): BlePsFtpUtils.PftpNotificationMessage {
        val builder = PftpNotification.PbPftpDHRestApiEvent.newBuilder()
        payloads.forEach { builder.addEvent(ByteString.copyFrom(it)) }
        builder.setUncompressed(uncompressed)
        return BlePsFtpUtils.PftpNotificationMessage().apply {
            id = PftpNotification.PbPFtpDevToHostNotification.REST_API_EVENT_VALUE
            byteArrayOutputStream.write(builder.build().toByteArray())
        }
    }

    private fun gzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    @Test
    fun `receiveRestApiEventData emits byte array for uncompressed event`() = runTest {
        val payload = "hello".toByteArray(Charsets.UTF_8)
        val notification = makeRestApiNotification(payload, uncompressed = true)
        every { client.waitForNotification() } returns flowOf(notification)

        val result = client.receiveRestApiEventData(identifier).toList()

        Assert.assertEquals(1, result.size)
        Assert.assertEquals(1, result[0].size)
        Assert.assertEquals("hello", result[0][0].toString(Charsets.UTF_8))
    }

    @Test
    fun `receiveRestApiEventData decompresses GZIP-compressed event`() = runTest {
        val payload = "compressed content".toByteArray(Charsets.UTF_8)
        val builder = PftpNotification.PbPftpDHRestApiEvent.newBuilder()
            .addEvent(ByteString.copyFrom(gzip(payload)))
        // uncompressed not set → false → decompress path
        val notification = BlePsFtpUtils.PftpNotificationMessage().apply {
            id = PftpNotification.PbPFtpDevToHostNotification.REST_API_EVENT_VALUE
            byteArrayOutputStream.write(builder.build().toByteArray())
        }
        every { client.waitForNotification() } returns flowOf(notification)

        val result = client.receiveRestApiEventData(identifier).toList()

        Assert.assertEquals(1, result.size)
        Assert.assertEquals("compressed content", result[0][0].toString(Charsets.UTF_8))
    }

    @Test
    fun `receiveRestApiEventData emits multiple events from a single notification`() = runTest {
        val notification = makeRestApiNotification(
            "event1".toByteArray(Charsets.UTF_8),
            "event2".toByteArray(Charsets.UTF_8),
            uncompressed = true
        )
        every { client.waitForNotification() } returns flowOf(notification)

        val result = client.receiveRestApiEventData(identifier).toList()

        Assert.assertEquals(1, result.size)       // one emission per notification
        Assert.assertEquals(2, result[0].size)    // two events inside
        Assert.assertEquals("event1", result[0][0].toString(Charsets.UTF_8))
        Assert.assertEquals("event2", result[0][1].toString(Charsets.UTF_8))
    }

    @Test
    fun `receiveRestApiEventData filters out non-REST_API notifications`() = runTest {
        val irrelevant = BlePsFtpUtils.PftpNotificationMessage().apply {
            id = PftpNotification.PbPFtpDevToHostNotification.BATTERY_STATUS_VALUE
        }
        val real = makeRestApiNotification("data".toByteArray(Charsets.UTF_8), uncompressed = true)
        every { client.waitForNotification() } returns flowOf(irrelevant, real)

        val result = client.receiveRestApiEventData(identifier).toList()

        Assert.assertEquals(1, result.size)
        Assert.assertEquals("data", result[0][0].toString(Charsets.UTF_8))
    }

    @Test
    fun `receiveRestApiEventData emits nothing when no REST_API notifications arrive`() = runTest {
        val irrelevant = BlePsFtpUtils.PftpNotificationMessage().apply {
            id = PftpNotification.PbPFtpDevToHostNotification.BATTERY_STATUS_VALUE
        }
        every { client.waitForNotification() } returns flowOf(irrelevant)

        val result = client.receiveRestApiEventData(identifier).toList()

        Assert.assertEquals(0, result.size)
    }

    @Test
    fun `receiveRestApiEvents decodes event bytes to UTF-8 strings`() = runTest {
        val json = """{"key":"value"}"""
        val notification =
            makeRestApiNotification(json.toByteArray(Charsets.UTF_8), uncompressed = true)
        every { client.waitForNotification() } returns flowOf(notification)

        val result = client.receiveRestApiEvents(identifier).toList()

        Assert.assertEquals(1, result.size)
        Assert.assertEquals(listOf(json), result[0])
    }

    @Test
    fun `receiveRestApiEvents handles multiple events per notification`() = runTest {
        val json1 = """{"a":1}"""
        val json2 = """{"b":2}"""
        val notification = makeRestApiNotification(
            json1.toByteArray(Charsets.UTF_8),
            json2.toByteArray(Charsets.UTF_8),
            uncompressed = true
        )
        every { client.waitForNotification() } returns flowOf(notification)

        val result = client.receiveRestApiEvents(identifier).toList()

        Assert.assertEquals(1, result.size)
        Assert.assertEquals(listOf(json1, json2), result[0])
    }

    @Test
    fun `receiveRestApiEvents propagates upstream errors`() = runTest {
        every { client.waitForNotification() } returns flow { throw RuntimeException("network failure") }

        var caught: Throwable? = null
        try {
            client.receiveRestApiEvents(identifier).toList()
        } catch (e: RuntimeException) {
            caught = e
        }

        Assert.assertEquals("network failure", caught?.message)
    }

    @Test
    fun `receiveRestApiEvents decodes compressed events to strings`() = runTest {
        val json = """{"compressed":true}"""
        val builder = PftpNotification.PbPftpDHRestApiEvent.newBuilder()
            .addEvent(ByteString.copyFrom(gzip(json.toByteArray(Charsets.UTF_8))))
        val notification = BlePsFtpUtils.PftpNotificationMessage().apply {
            id = PftpNotification.PbPFtpDevToHostNotification.REST_API_EVENT_VALUE
            byteArrayOutputStream.write(builder.build().toByteArray())
        }
        every { client.waitForNotification() } returns flowOf(notification)

        val result = client.receiveRestApiEvents(identifier).toList()

        Assert.assertEquals(1, result.size)
        Assert.assertEquals(listOf(json), result[0])
    }

    @Test
    fun `toObject parses simple JSON to data class`() {
        val json = """{"value":"hello"}"""

        val result = json.toObject<SimplePayload>()

        Assert.assertEquals("hello", result.value)
    }

    @Test
    fun `toObject parses nested JSON to data class`() {
        val json = """{"inner":{"count":42}}"""

        val result = json.toObject<NestedPayload>()

        Assert.assertEquals(42, result.inner.count)
    }

    @Test
    fun `toObject sets missing optional fields to null`() {
        val json = """{}"""

        val result = json.toObject<SimplePayload>()

        Assert.assertNull(result.value)
    }

    @Test
    fun `toObject ignores unknown JSON fields`() {
        val json = """{"value":"keep","unknown":"discard"}"""

        val result = json.toObject<SimplePayload>()

        Assert.assertEquals("keep", result.value)
    }
}