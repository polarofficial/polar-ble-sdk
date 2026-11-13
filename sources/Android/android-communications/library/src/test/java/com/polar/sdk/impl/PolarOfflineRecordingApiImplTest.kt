package com.polar.sdk.impl

import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility
import com.polar.androidcommunications.testrules.BleLoggerTestRule
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.model.PolarOfflineRecordingEntry
import com.polar.sdk.impl.utils.PolarServiceClientUtils
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import protocol.PftpRequest.PbPFtpOperation
import protocol.PftpRequest.PbPFtpOperation.Command
import protocol.PftpResponse.PbPFtpDirectory
import protocol.PftpResponse.PbPFtpEntry
import java.time.LocalDateTime

class PolarOfflineRecordingApiImplTest {
    @get:Rule
    val bleLoggerTestRule = BleLoggerTestRule()

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `HR deletion succeeds when cleanup checks the directory already removed`() = runBlocking {
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val tx = mockk<BleGattTxInterface>()
        val client = BlePsFtpClient(tx)
        val characteristic = BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC
        val directoryPath = "/U/0/20261020/R/114132/"
        val requests = mutableListOf<Pair<Command, String>>()
        var removed = false

        mockkObject(PolarServiceClientUtils)
        mockkObject(BlePolarDeviceCapabilitiesUtility.Companion)
        every { BlePolarDeviceCapabilitiesUtility.getFileSystemType("360") } returns
            BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2
        every { PolarServiceClientUtils.sessionPsFtpClientReady("E072B42C", listener) } returns session
        every { session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) } returns client
        every { session.polarDeviceType } returns "360"
        every { tx.isConnected() } returns true
        every { tx.gattClientRequestStopScanning() } just runs
        every { tx.gattClientResumeScanning() } just runs
        every { tx.transmitMessages(any(), characteristic, any(), any()) } answers {
            val packets = thirdArg<List<ByteArray>>()
            // Strip RFC76 packet headers and the two-byte RFC60 request length.
            val message = packets.flatMap { it.drop(1) }.toByteArray()
            val request = PbPFtpOperation.parseFrom(message.copyOfRange(2, message.size))
            requests.add(request.command to request.path)
            repeat(packets.size) { client.processServiceDataWritten(characteristic, 0) }

            val response = when {
                request.command == Command.REMOVE && request.path == directoryPath -> {
                    removed = true
                    byteArrayOf(0x00, 0x00, 0x00)
                }
                request.command == Command.GET && request.path == directoryPath && removed ->
                    byteArrayOf(0x00, 103, 0x00)
                request.command == Command.GET -> {
                    // Other recordings/date contents must prevent ancestor removal.
                    val name = if (request.path == directoryPath) "HR0.REC" else "OTHER/"
                    val directory = PbPFtpDirectory.newBuilder()
                        .addEntries(PbPFtpEntry.newBuilder().setName(name).setSize(972))
                        .build()
                    byteArrayOf(0x02) + directory.toByteArray()
                }
                else -> error("Unexpected PFTP request: $request")
            }
            client.processServiceData(characteristic, response, 0, true)
        }
        client.descriptorWritten(characteristic, true, BleGattBase.ATT_SUCCESS)
        val entry = PolarOfflineRecordingEntry(
            path = directoryPath + "HR.REC",
            size = 972,
            date = LocalDateTime.of(2026, 10, 20, 11, 41, 32),
            type = PolarDeviceDataType.HR
        )

        PolarOfflineRecordingApiImpl(listener).removeOfflineRecord("E072B42C", entry)

        assertEquals(
            listOf(
                Command.GET to directoryPath,
                Command.REMOVE to directoryPath,
                Command.GET to directoryPath,
                Command.GET to "/U/0/20261020/R/",
                Command.GET to "/U/0/20261020/"
            ),
            requests
        )
        verify(exactly = 5) { tx.gattClientResumeScanning() }
    }
}


