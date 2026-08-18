package com.polar.sdk.api.model.utils

import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.impl.utils.PolarBackupManager
import com.polar.sdk.impl.utils.PolarBackupManager.BackupFileData
import com.polar.sdk.impl.utils.PolarFileUtils
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import protocol.PftpRequest
import protocol.PftpResponse.*
import java.io.ByteArrayOutputStream

class PolarBackupManagerTest {

    private val mockClient = mockk<BlePsFtpClient>()

    @Test
    fun `backupDevice() should read and backup files`() = runTest {
        // Arrange
        val backupManager = PolarBackupManager(mockClient)

        val mockBackupFileContent = ByteArrayOutputStream().apply {
            write(("/SYS/BT/\n" +
                    "/U/*/USERID.BPB\n" +
                    "/RANDOM/FILE.TXT").toByteArray())
        }

        val builder = PbPFtpDirectory.newBuilder()
            .addAllEntries(
                listOf(
                    PbPFtpEntry.newBuilder().setName("BACKUP.TXT").setSize(1234).build(),
                    PbPFtpEntry.newBuilder().setName("BT/").setSize(1234).build(),
                )
            )

        val mockDirectoryContent = ByteArrayOutputStream().apply {
            builder.build().writeTo(this)
        }

        coEvery { mockClient.request(
            PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                .setPath("/SYS/")
                .build().toByteArray()
        )} returns mockDirectoryContent

        coEvery { mockClient.request(
            PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                .setPath("/SYS/BACKUP.TXT")
                .build().toByteArray()
        )} returns mockBackupFileContent

        coEvery { mockClient.request(
            PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                .setPath("/SYS/BT/")
                .build().toByteArray()
        )} returns ByteArrayOutputStream()

        coEvery { mockClient.request(
            PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                .setPath("/U/0/USERID.BPB")
                .build().toByteArray()
        )} returns ByteArrayOutputStream()

        coEvery { mockClient.request(
            PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                .setPath("/RANDOM/FILE.TXT")
                .build().toByteArray()
        )} returns ByteArrayOutputStream()

        // Default backup files
        coEvery { mockClient.request(
            PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                .setPath("/U/0/S/PHYSDATA.BPB")
                .build().toByteArray()
        )} returns ByteArrayOutputStream()

        coEvery { mockClient.request(
            PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                .setPath("/U/0/S/UDEVSET.BPB")
                .build().toByteArray()
        )} returns ByteArrayOutputStream()

        coEvery { mockClient.request(
            PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                .setPath("/U/0/S/PREFS.BPB")
                .build().toByteArray()
        )} returns ByteArrayOutputStream()

        // Act
        backupManager.backupDevice()

        // Assert
        coVerify {
            mockClient.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath("/SYS/")
                    .build().toByteArray()
            )
            mockClient.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath("/SYS/BACKUP.TXT")
                    .build().toByteArray()
            )
            mockClient.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath("/SYS/BT/")
                    .build().toByteArray()
            )
            mockClient.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath("/U/0/USERID.BPB")
                    .build().toByteArray()
            )
            mockClient.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath("/RANDOM/FILE.TXT")
                    .build().toByteArray()
            )
            // Default files
            mockClient.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath("/U/0/S/PHYSDATA.BPB")
                    .build().toByteArray()
            )
            mockClient.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath("/U/0/S/UDEVSET.BPB")
                    .build().toByteArray()
            )
            mockClient.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath("/U/0/S/PREFS.BPB")
                    .build().toByteArray()
            )
        }
        confirmVerified(mockClient)
    }

    @Test
    fun `restoreBackup() should restore files`() = runTest {
        // Arrange
        val backupManager = PolarBackupManager(mockClient)

        val mockFileData = listOf(
            BackupFileData(byteArrayOf(), "/SYS/BT/", "BTDEV.BPB"),
            BackupFileData(byteArrayOf(), "/SYS/BT/", "SVSTATUS.BPB"),
            BackupFileData(byteArrayOf(), "/RANDOM/", "FILE.TXT")
        )

        coEvery { mockClient.write(
            PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.PUT)
                .setPath("/SYS/BT/BTDEV.BPB").build().toByteArray(),
            any()
        )} returns flowOf(0L)

        coEvery { mockClient.write(
            PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.PUT)
                .setPath("/SYS/BT/SVSTATUS.BPB").build().toByteArray(),
            any()
        )} returns flowOf(0L)

        coEvery { mockClient.write(
            PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.PUT)
                .setPath("/RANDOM/FILE.TXT").build().toByteArray(),
            any()
        )} returns flowOf(0L)

        // Act
        backupManager.restoreBackup("testIdentifier", mockFileData, null)

        // Assert
        coVerify {
            mockClient.write(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.PUT)
                    .setPath("/SYS/BT/BTDEV.BPB")
                    .build().toByteArray(), any()
            )
            mockClient.write(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.PUT)
                    .setPath("/SYS/BT/SVSTATUS.BPB")
                    .build().toByteArray(), any()
            )
            mockClient.write(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.PUT)
                    .setPath("/RANDOM/FILE.TXT")
                    .build().toByteArray(), any()
            )
        }
        confirmVerified(mockClient)
    }

    @Test
    fun `restoreBackup() should create folders for paths under user root`() = runTest {
        // Arrange
        val backupManager = PolarBackupManager(mockClient)
        val mockListener = mockk<BleDeviceListener>()

        val mockFileData = listOf(
            BackupFileData(byteArrayOf(), "/U/0/S/", "PHYSDATA.BPB")
        )

        mockkObject(PolarFileUtils)
        coJustRun { PolarFileUtils.createFolder(any(), any(), any(), any()) }

        coEvery { mockClient.write(
            PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.PUT)
                .setPath("/U/0/S/PHYSDATA.BPB").build().toByteArray(),
            any()
        )} returns flowOf(0L)

        try {
            // Act
            backupManager.restoreBackup("testIdentifier", mockFileData, mockListener)

            // Assert: both /U/0/ and /U/0/S/ should be created (paths under ARABICA_USER_ROOT_FOLDER)
            coVerify(exactly = 1) { PolarFileUtils.createFolder("testIdentifier", "/U/0/", mockListener, any()) }
            coVerify(exactly = 1) { PolarFileUtils.createFolder("testIdentifier", "/U/0/S/", mockListener, any()) }
        } finally {
            unmockkObject(PolarFileUtils)
        }
    }

    @Test
    fun `restoreBackup() should skip folder creation when listener is null`() = runTest {
        // Arrange
        val backupManager = PolarBackupManager(mockClient)

        val mockFileData = listOf(
            BackupFileData(byteArrayOf(), "/U/0/S/", "PHYSDATA.BPB")
        )

        mockkObject(PolarFileUtils)
        coJustRun { PolarFileUtils.createFolder(any(), any(), any(), any()) }

        coEvery { mockClient.write(
            PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.PUT)
                .setPath("/U/0/S/PHYSDATA.BPB").build().toByteArray(),
            any()
        )} returns flowOf(0L)

        try {
            // Act
            backupManager.restoreBackup("testIdentifier", mockFileData, null)

            // Assert: no folder creation when listener is null
            coVerify(exactly = 0) { PolarFileUtils.createFolder(any(), any(), any(), any()) }
        } finally {
            unmockkObject(PolarFileUtils)
        }
    }

    @Test
    fun `restoreBackup() should create folders for each file in user directory`() = runTest {
        // Arrange
        val backupManager = PolarBackupManager(mockClient)
        val mockListener = mockk<BleDeviceListener>()

        // Two files in the same /U/0/S/ directory — folder creation is per-file
        val mockFileData = listOf(
            BackupFileData(byteArrayOf(), "/U/0/S/", "PHYSDATA.BPB"),
            BackupFileData(byteArrayOf(), "/U/0/S/", "UDEVSET.BPB")
        )

        mockkObject(PolarFileUtils)
        coJustRun { PolarFileUtils.createFolder(any(), any(), any(), any()) }

        coEvery { mockClient.write(any(), any()) } returns flowOf(0L)

        try {
            // Act
            backupManager.restoreBackup("testIdentifier", mockFileData, mockListener)

            // Assert: /U/0/ and /U/0/S/ are each created once per file (2 times total each)
            coVerify(exactly = 2) { PolarFileUtils.createFolder("testIdentifier", "/U/0/", mockListener, any()) }
            coVerify(exactly = 2) { PolarFileUtils.createFolder("testIdentifier", "/U/0/S/", mockListener, any()) }
        } finally {
            unmockkObject(PolarFileUtils)
        }
    }

    @Test
    fun `restoreBackup() should not create folders for paths outside user root`() = runTest {
        // Arrange
        val backupManager = PolarBackupManager(mockClient)
        val mockListener = mockk<BleDeviceListener>()

        val mockFileData = listOf(
            BackupFileData(byteArrayOf(), "/SYS/BT/", "BTDEV.BPB")
        )

        mockkObject(PolarFileUtils)
        coJustRun { PolarFileUtils.createFolder(any(), any(), any(), any()) }

        coEvery { mockClient.write(
            PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.PUT)
                .setPath("/SYS/BT/BTDEV.BPB").build().toByteArray(),
            any()
        )} returns flowOf(0L)

        try {
            // Act
            backupManager.restoreBackup("testIdentifier", mockFileData, mockListener)

            // Assert: no folder creation for paths outside /U/0/
            coVerify(exactly = 0) { PolarFileUtils.createFolder(any(), any(), any(), any()) }
        } finally {
            unmockkObject(PolarFileUtils)
        }
    }
}
