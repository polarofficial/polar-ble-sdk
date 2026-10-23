package com.polar.androidcommunications.api.ble.model.gatt.client.psftp

import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.RFC76_STATUS_LAST
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.RFC76_STATUS_MORE
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.processRfc76MessageFrameHeader
import com.polar.androidcommunications.testrules.BleLoggerTestRule
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import protocol.PftpError.PbPFtpError

internal class BlePsFtpUtilsTest {
    @Rule
    @JvmField
    val bleLoggerTestRule = BleLoggerTestRule()

    @Test
    fun `process first frame of sequence more to come`() {
        //Arrange
        val data = byteArrayOf(0x06.toByte(), 0x0A.toByte(), 0x06.toByte(), 0x08.toByte(), 0x02.toByte(), 0x10.toByte(), 0x04.toByte(), 0x18.toByte(), 0x03.toByte(), 0x12.toByte(), 0x06.toByte(), 0x08.toByte(), 0x00.toByte(), 0x10.toByte(), 0x09.toByte(), 0x18.toByte(), 0x05.toByte(), 0x1A.toByte(), 0x06.toByte(), 0x08.toByte())
        val header = BlePsFtpUtils.PftpRfc76ResponseHeader()
        //Act
        processRfc76MessageFrameHeader(header, data)
        //Assert
        Assert.assertEquals(0, header.next)
        Assert.assertEquals(RFC76_STATUS_MORE, header.status)
        Assert.assertEquals(0, header.sequenceNumber)
        Assert.assertEquals(data.size - 1, header.payload?.size)
        Assert.assertArrayEquals(data.copyOfRange(1, data.size), header.payload)
    }

    @Test
    fun `process middle frame of sequence more to come`() {
        //Arrange
        val data = byteArrayOf(0x97.toByte(), 0x22.toByte(), 0x0A.toByte(), 0x03.toByte(), 0x47.toByte(), 0x50.toByte(), 0x53.toByte(), 0x1A.toByte(), 0x1B.toByte(), 0x08.toByte(), 0x01.toByte(), 0x10.toByte(), 0x00.toByte(), 0x18.toByte(), 0x00.toByte(), 0x22.toByte(), 0x13.toByte(), 0x61.toByte(), 0x32.toByte(), 0x30.toByte())
        val header = BlePsFtpUtils.PftpRfc76ResponseHeader()
        //Act
        processRfc76MessageFrameHeader(header, data)
        //Assert
        Assert.assertEquals(1, header.next)
        Assert.assertEquals(RFC76_STATUS_MORE, header.status)
        Assert.assertEquals(9, header.sequenceNumber)
        Assert.assertEquals(data.size - 1, header.payload?.size)
        Assert.assertArrayEquals(data.copyOfRange(1, data.size), header.payload)
    }

    @Test
    fun `process last frame of sequence no more data`() {
        //Arrange
        val data = byteArrayOf(0xC3.toByte(), 0x5A.toByte(), 0x48.toByte(), 0x5F.toByte(), 0x4A.toByte(), 0x41.toByte(), 0x10.toByte(), 0x09.toByte())
        val header = BlePsFtpUtils.PftpRfc76ResponseHeader()
        //Act
        processRfc76MessageFrameHeader(header, data)
        //Assert
        Assert.assertEquals(1, header.next)
        Assert.assertEquals(RFC76_STATUS_LAST, header.status)
        Assert.assertEquals(12, header.sequenceNumber)
        Assert.assertEquals(data.size - 1, header.payload?.size)
        Assert.assertArrayEquals(data.copyOfRange(1, data.size), header.payload)
    }

    @Test
    fun `PFTP response errors resolve all declared error codes`() {
        val expectedErrors = listOf(
            0 to PbPFtpError.OPERATION_SUCCEEDED,
            1 to PbPFtpError.REBOOTING,
            2 to PbPFtpError.TRY_AGAIN,
            100 to PbPFtpError.UNIDENTIFIED_HOST_ERROR,
            101 to PbPFtpError.INVALID_COMMAND,
            102 to PbPFtpError.INVALID_PARAMETER,
            103 to PbPFtpError.NO_SUCH_FILE_OR_DIRECTORY,
            104 to PbPFtpError.DIRECTORY_EXISTS,
            105 to PbPFtpError.FILE_EXISTS,
            106 to PbPFtpError.OPERATION_NOT_PERMITTED,
            107 to PbPFtpError.NO_SUCH_USER,
            108 to PbPFtpError.TIMEOUT,
            200 to PbPFtpError.UNIDENTIFIED_DEVICE_ERROR,
            201 to PbPFtpError.NOT_IMPLEMENTED,
            202 to PbPFtpError.SYSTEM_BUSY,
            203 to PbPFtpError.INVALID_CONTENT,
            204 to PbPFtpError.CHECKSUM_FAILURE,
            205 to PbPFtpError.DISK_FULL,
            206 to PbPFtpError.PREREQUISITE_NOT_MET,
            207 to PbPFtpError.INSUFFICIENT_BUFFER,
            208 to PbPFtpError.WAIT_FOR_IDLING,
            209 to PbPFtpError.BATTERY_TOO_LOW,
            210 to PbPFtpError.WAIT_FOR_PAIRING
        )

        expectedErrors.forEach { (expectedNumber, expectedError) ->
            val responseError = BlePsFtpUtils.PftpResponseError("PFTP request failed", expectedNumber)

            Assert.assertEquals(expectedNumber, expectedError.number)
            Assert.assertEquals(expectedError, responseError.errorCode)
            Assert.assertEquals(expectedError.name, responseError.errorName)
            Assert.assertEquals(
                "PFTP request failed Error: $expectedNumber (${expectedError.name})",
                responseError.message
            )
        }
    }
}