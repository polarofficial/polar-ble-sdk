package com.polar.androidcommunications.api.ble.model.gatt.client.psftp

import org.apache.commons.io.IOUtils
import protocol.PftpError.PbPFtpError
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID

/**
 * RFC76 and RFC 60 related utils
 */
object BlePsFtpUtils {
    private val TAG: String = BlePsFtpUtils::class.java.simpleName
    val RFC77_PFTP_SERVICE: UUID = UUID.fromString("0000FEEE-0000-1000-8000-00805f9b34fb")
    const val PFTP_SERVICE_16BIT_UUID: String = "FEEE"
    val RFC77_PFTP_MTU_CHARACTERISTIC: UUID =
        UUID.fromString("FB005C51-02E7-F387-1CAD-8ACD2D8DF0C8")
    val RFC77_PFTP_D2H_CHARACTERISTIC: UUID =
        UUID.fromString("FB005C52-02E7-F387-1CAD-8ACD2D8DF0C8")
    val RFC77_PFTP_H2D_CHARACTERISTIC: UUID =
        UUID.fromString("FB005C53-02E7-F387-1CAD-8ACD2D8DF0C8")

    const val RFC76_HEADER_SIZE: Int = 1
    const val RFC76_STATUS_MORE: Int = 0x03
    const val RFC76_STATUS_LAST: Int = 0x01
    const val RFC76_STATUS_ERROR_OR_RESPONSE: Int = 0x00

    /**
     * Compines header(protobuf typically) and data(for write operation only, for other operations = null)
     *
     * @param header typically protocol buffer data
     * @param data   content to be transmitted
     * @param type   @see MessageType
     * @param id     for query or notification only
     * @return complete message stream
     * @throws IOException thrown by IOUtils.copy
     */
    @Throws(IOException::class)
    fun makeCompleteMessageStream(
        header: ByteArrayInputStream?,
        data: ByteArrayInputStream?,
        type: MessageType,
        id: Int
    ): ByteArrayInputStream {
        val outputStream = ByteArrayOutputStream()
        // for request and query add RFC60 header
        when (type) {
            MessageType.REQUEST -> {
                val headerSize = header?.available()
                val request = ByteArray(2)
                // RFC60
                if (headerSize != null) {
                    request[1] = ((headerSize and 0x7F00) shr 8).toByte()
                }
                if (headerSize != null) {
                    request[0] = (headerSize and 0x00FF).toByte()
                }
                outputStream.write(request, 0, 2)
                IOUtils.copy(header, outputStream)
                if (data != null) {
                    IOUtils.copy(data, outputStream)
                }
            }

            MessageType.QUERY -> {
                val request = ByteArray(2)
                // RFC60
                request[1] = (((id and 0x7F00) shr 8) or 0x80).toByte()
                request[0] = (id and 0x00FF).toByte()
                outputStream.write(request, 0, 2)
                if (header != null) {
                    IOUtils.copy(header, outputStream)
                }
            }

            MessageType.NOTIFICATION -> {
                val request = ByteArray(1)
                request[0] = id.toByte()
                outputStream.write(request, 0, 1)
                if (header != null) {
                    IOUtils.copy(header, outputStream)
                }
            }
        }

        return ByteArrayInputStream(outputStream.toByteArray())
    }

    /**
     * Generate single air packet from data content
     *
     * @param data           content to be transmitted
     * @param next           bit to indicate 0=first or 1=next air packet
     * @param mtuSize        att mtu size used
     * @param sequenceNumber RFC76 ring counter
     * @return air packet
     */
    fun buildRfc76MessageFrame(
        data: ByteArrayInputStream,
        next: Int,
        mtuSize: Int,
        sequenceNumber: Rfc76SequenceNumber
    ): ByteArray {
        val offset = RFC76_HEADER_SIZE
        val packet: ByteArray
        if (data.available() > (mtuSize - RFC76_HEADER_SIZE)) {
            packet = ByteArray(mtuSize)
            packet[0] =
                ((packet[0].toInt() or next or 0x06).toLong() or (sequenceNumber.seq shl 4)).toByte() // 0x06 == MORE
            data.read(packet, offset, mtuSize - offset)
        } else if (data.available() > 0) {
            packet = ByteArray(data.available() + RFC76_HEADER_SIZE)
            packet[0] =
                ((packet[0].toInt() or next or 0x02).toLong() or (sequenceNumber.seq shl 4)).toByte() // 0x02 == LAST
            data.read(packet, offset, data.available())
        } else {
            packet = ByteArray(RFC76_HEADER_SIZE)
            packet[0] =
                ((packet[0].toInt() or next or 0x02).toLong() or (sequenceNumber.seq shl 4)).toByte()
        }
        sequenceNumber.increment()
        return packet
    }

    /**
     * Generate list of air packets from data stream
     *
     * @param data           content to be split into air packets
     * @param mtuSize        att mtu size
     * @param sequenceNumber RFC76 ring counter
     * @return list of air packets
     */
    fun buildRfc76MessageFrameAll(
        data: ByteArrayInputStream,
        mtuSize: Int,
        sequenceNumber: Rfc76SequenceNumber
    ): MutableList<ByteArray> {
        val packets: MutableList<ByteArray> = ArrayList()
        var next = 0
        do {
            val temp = next // workaround for stupid java translator idiotisim
            val packet = buildRfc76MessageFrame(data, temp, mtuSize, sequenceNumber)
            packets.add(packet)
            next = 1
        } while (data.available() > 0)
        return packets
    }

    /**
     * Function to process RFC76 message header check rfc spec for more details
     *
     * @param packet air packet
     * @return @see PftpRfc76ResponseHeader
     */
    fun processRfc76MessageFrameHeader(packet: ByteArray): PftpRfc76ResponseHeader {
        val header = PftpRfc76ResponseHeader()
        processRfc76MessageFrameHeader(header, packet)
        return header
    }

    /**
     * @param header RF76 header container
     * @param packet air packet
     */
    fun processRfc76MessageFrameHeader(header: PftpRfc76ResponseHeader, packet: ByteArray) {
        header.next = packet[0].toInt() and 0x01
        header.status = (packet[0].toInt() shr 1) and 0x03
        header.sequenceNumber = ((packet[0].toInt() shr 4) and 0x0F).toLong()
        if (header.status == 0) {
            header.error =
                ((packet[RFC76_HEADER_SIZE].toInt() and 0xFF) or ((packet[RFC76_HEADER_SIZE + 1].toInt() shl 8) and 0xFF)) and 0x0000FFFF
        } else {
            header.payload = ByteArray(packet.size - RFC76_HEADER_SIZE)
            System.arraycopy(
                packet,
                RFC76_HEADER_SIZE,
                header.payload,
                0,
                packet.size - RFC76_HEADER_SIZE
            )
        }
    }

    class Rfc76SequenceNumber {
        var seq: Long = 0

        fun increment() {
            if (seq < 0x0F) {
                this.seq += 1
            } else {
                this.seq = 0
            }
        }
    }

    /**
     * PSFTP EXCEPTIONS
     */
    class PftpOperationTimeout(detailMessage: String?) : Exception(detailMessage)

    /**
     * one of PbPftpError codes
     */
    class PftpResponseError(detailMessage: String, val error: Int) :
        Exception(formatMessage(detailMessage, error)) {
        val errorCode: PbPFtpError?
            /**
             * Return typed enum for the error code, or null if the code is unknown
             */
            get() = PbPFtpError.forNumber(error)

        val errorName: String?
            /**
             * Return enum name or null if unknown.
             */
            get() {
                val errorEnum = errorCode
                return errorEnum?.name
            }

        companion object {
            private fun formatMessage(detailMessage: String, error: Int): String {
                val errorEnum = PbPFtpError.forNumber(error)
                if (errorEnum != null) {
                    return detailMessage + " Error: " + error + " (" + errorEnum.name + ")"
                }
                return "$detailMessage Error: $error"
            }
        }
    }

    class PftpNotificationMessage {
        /**
         * One of PbPftpDevToHostNotifications
         */
        @JvmField var id: Int = 0
        @JvmField var byteArrayOutputStream: ByteArrayOutputStream = ByteArrayOutputStream()
    }

    class PftpRfc76ResponseHeader {
        var next: Int = 0
        var status: Int = 0
        var error: Int = 0
        var payload: ByteArray? = null
        var sequenceNumber: Long = 0

        override fun toString(): String {
            return "first: $next length: $status error: $error payload: " + (if (payload != null) String(
                payload!!
            ) else "null seq: $sequenceNumber")
        }
    }

    enum class MessageType {
        REQUEST,
        QUERY,
        NOTIFICATION
    }
}
