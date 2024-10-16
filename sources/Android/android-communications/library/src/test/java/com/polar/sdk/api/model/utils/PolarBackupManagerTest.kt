package com.polar.sdk.api.model.utils

import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.impl.utils.PolarBackupManager
import com.polar.sdk.impl.utils.PolarBackupManager.BackupFileData
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import org.junit.Test
import protocol.PftpRequest
import protocol.PftpResponse.*
import java.io.ByteArrayOutputStream

class PolarBackupManagerTest {

    private val mockClient = mockk<BlePsFtpClient>()

    @Test
    fun `backupDevice() should read and backup files`() {
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

        every { mockClient.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                        .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                        .setPath("/SYS/")
                        .build().toByteArray()
        )} returns Single.just(mockDirectoryContent)

        every { mockClient.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                        .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                        .setPath("/SYS/BACKUP.TXT")
                        .build().toByteArray()
        )} returns Single.just(mockBackupFileContent)

        every { mockClient.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                        .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                        .setPath("/SYS/BT/")
                        .build().toByteArray()
        )} returns Single.just(ByteArrayOutputStream())


        every { mockClient.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                        .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                        .setPath("/U/0/USERID.BPB")
                        .build().toByteArray()
        )} returns Single.just(ByteArrayOutputStream())

        every { mockClient.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                        .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                        .setPath("/RANDOM/FILE.TXT")
                        .build().toByteArray()
        )} returns Single.just(ByteArrayOutputStream())

        // Act
        backupManager.backupDevice().blockingGet()

        // Assert
        verify {
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
        }
        confirmVerified(mockClient)
    }

    @Test
    fun `restoreBackup() should restore files`() {
        // Arrange
        val backupManager = PolarBackupManager(mockClient)

        val mockFileData = listOf(
                BackupFileData(byteArrayOf(), "/SYS/BT/", "BTDEV.BPB"),
                BackupFileData(byteArrayOf(), "/SYS/BT/", "SVSTATUS.BPB"),
                BackupFileData(byteArrayOf(), "/RANDOM/", "FILE.TXT"))

        every { mockClient.write(
                PftpRequest.PbPFtpOperation.newBuilder()
                        .setCommand(PftpRequest.PbPFtpOperation.Command.PUT)
                        .setPath("/SYS/BT/BTDEV.BPB").build().toByteArray(),
                any()
        ) } returns Flowable.fromCompletable(Completable.complete())

        every { mockClient.write(
                PftpRequest.PbPFtpOperation.newBuilder()
                        .setCommand(PftpRequest.PbPFtpOperation.Command.PUT)
                        .setPath("/SYS/BT/SVSTATUS.BPB").build().toByteArray(),
                any()
        ) } returns Flowable.fromCompletable(Completable.complete())

        every { mockClient.write(
                PftpRequest.PbPFtpOperation.newBuilder()
                        .setCommand(PftpRequest.PbPFtpOperation.Command.PUT)
                        .setPath("/RANDOM/FILE.TXT").build().toByteArray(),
                any()
        ) } returns Flowable.fromCompletable(Completable.complete())

        // Act
        backupManager.restoreBackup(mockFileData).blockingAwait()

        // Assert
        verify {
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
}
