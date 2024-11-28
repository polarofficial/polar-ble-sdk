package com.polar.sdk.api.model.utils

import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.impl.utils.PolarFirmwareUpdateUtils
import com.polar.sdk.impl.utils.PolarFirmwareUpdateUtils.FwFileComparator
import fi.polar.remote.representation.protobuf.Device
import fi.polar.remote.representation.protobuf.Structures.PbVersion
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.rxjava3.core.Single
import org.junit.Assert
import org.junit.Test
import protocol.PftpRequest
import java.io.ByteArrayOutputStream
import java.io.File


class PolarFirmwareUpdateUtilsTest {

    private val mockClient = mockk<BlePsFtpClient>()
    private val firmwareFilePath = "/DEVICE.BPB"

    @Test
    fun `readDeviceFirmwareInfo() should return firmware info`() {
        // Arrange
        val deviceId = "123456"
        val firmwareVersion = "1.2.0"
        val modelName = "Model"
        val hardwareCode = "00112233.01"

        val proto = Device.PbDeviceInfo.newBuilder()
                .setDeviceVersion(PbVersion.newBuilder().setMajor(1).setMinor(2).setPatch(0))
                .setModelName(modelName)
                .setHardwareCode(hardwareCode)
                .build()

        val mockResponseContent = ByteArrayOutputStream().apply {
            proto.writeTo(this)
        }

        every { mockClient.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                        .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                        .setPath(firmwareFilePath)
                        .build().toByteArray()
        ) } returns Single.just(mockResponseContent)

        // Act
        val firmwareInfoSingle = PolarFirmwareUpdateUtils.readDeviceFirmwareInfo(mockClient, deviceId)
        val firmwareInfo = firmwareInfoSingle.blockingGet()

        // Assert
        verify {
            mockClient.request(
                    PftpRequest.PbPFtpOperation.newBuilder()
                            .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                            .setPath(firmwareFilePath)
                            .build().toByteArray()
            )
        }
        confirmVerified(mockClient)

        assert(firmwareInfo.deviceFwVersion == firmwareVersion)
        assert(firmwareInfo.deviceModelName == modelName)
        assert(firmwareInfo.deviceHardwareCode == hardwareCode)
    }

    @Test
    fun `isAvailableFirmwareVersionHigher() should return true when current version is smaller than available version`() {
        Assert.assertTrue(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                "1.0.0",
                "2.0.0"
            )
        )
        Assert.assertTrue(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                "2.0.0",
                "2.0.1"
            )
        )
        Assert.assertTrue(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                "2.0.0",
                "2.1.0"
            )
        )
        Assert.assertTrue(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                "2.0.0",
                "3.0.0"
            )
        )
    }

    @Test
    fun `isAvailableFirmwareVersionHigher() should return false when current version is same or higher than available version`() {
        Assert.assertFalse(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                "2.0.0",
                "1.0.0"
            )
        )
        Assert.assertFalse(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                "2.0.1",
                "2.0.0"
            )
        )
        Assert.assertFalse(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                "2.1.0",
                "2.0.0"
            )
        )
        Assert.assertFalse(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                "3.0.0",
                "2.0.0"
            )
        )
        Assert.assertFalse(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                "2.0.0",
                "2.0.0"
            )
        )
    }

    @Test
    fun `FwFileComparator sorts files correctly`() {
        // Arrange
        val btFile = mockFile("BTUPDAT.BIN")
        val sysFile = mockFile("SYSUPDAT.IMG")
        val touchFile = mockFile("TCHUPDAT.BIN")
        val files = mutableListOf(btFile, sysFile, touchFile)

        // Act
        files.sortWith(FwFileComparator())

        // Assert
        Assert.assertEquals(btFile, files[0])
        Assert.assertEquals(touchFile, files[1])
        Assert.assertEquals(sysFile, files[2])
    }

    @Test
    fun `FwFileComparator keeps already sorted files`() {
        // Arrange
        val f1 = mockFile("BTUPDAT.BIN")
        val f2 = mockFile("TCHUPDAT.BIN")
        val f3 = mockFile("SYSUPDAT.IMG")
        val files = mutableListOf(f1, f2, f3)

        // Act
        files.sortWith(FwFileComparator())

        // Assert
        Assert.assertEquals(f1, files[0])
        Assert.assertEquals(f2, files[1])
        Assert.assertEquals(f3, files[2])
    }

    private fun mockFile(name: String): File {
        val file = mockk<File>()
        every { file.name } returns name
        return file
    }
}