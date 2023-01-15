package com.polar.androidcommunications.api.ble.model.gatt.client.psftp;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * RFC76 and RFC 60 related utils
 */
public class BlePsFtpUtils {
    private static final String TAG = BlePsFtpUtils.class.getSimpleName();
    public static final UUID RFC77_PFTP_SERVICE = UUID.fromString("0000FEEE-0000-1000-8000-00805f9b34fb");
    public static final String PFTP_SERVICE_16BIT_UUID = "FEEE";
    public static final UUID RFC77_PFTP_MTU_CHARACTERISTIC = UUID.fromString("FB005C51-02E7-F387-1CAD-8ACD2D8DF0C8");
    public static final UUID RFC77_PFTP_D2H_CHARACTERISTIC = UUID.fromString("FB005C52-02E7-F387-1CAD-8ACD2D8DF0C8");
    public static final UUID RFC77_PFTP_H2D_CHARACTERISTIC = UUID.fromString("FB005C53-02E7-F387-1CAD-8ACD2D8DF0C8");

    public static final int RFC76_HEADER_SIZE = 1;
    public static final int RFC76_STATUS_MORE = 0x03;
    public static final int RFC76_STATUS_LAST = 0x01;
    public static final int RFC76_STATUS_ERROR_OR_RESPONSE = 0x00;

    public static class Rfc76SequenceNumber {
        long seq = 0;

        public long getSeq() {
            return seq;
        }

        public void increment() {
            if (seq < 0x0F) {
                this.seq += 1;
            } else {
                this.seq = 0;
            }
        }
    }

    /**
     * PSFTP EXCEPTIONS
     */
    public static class PftpOperationTimeout extends Exception {
        public PftpOperationTimeout(String detailMessage) {
            super(detailMessage);
        }
    }

    /**
     * one of PbPftpError codes
     */
    public static class PftpResponseError extends Exception {
        private final int error;

        public PftpResponseError(String detailMessage, int error) {
            super(detailMessage + " Error: " + error);
            this.error = error;
        }

        public int getError() {
            return error;
        }
    }

    public static class PftpNotificationMessage {
        /**
         * One of PbPftpDevToHostNotifications
         */
        public int id = 0;
        public ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    }

    public static class PftpRfc76ResponseHeader {
        public int next;
        public int status;
        public int error;
        public byte[] payload;
        public long sequenceNumber;

        @Override
        public String toString() {
            return "first: " + next + " length: " + status + " error: " + error + " payload: " + (payload != null ? new String(payload) : "null" + " seq: " + sequenceNumber);
        }
    }

    public enum MessageType {
        REQUEST,
        QUERY,
        NOTIFICATION
    }

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
    public static ByteArrayInputStream makeCompleteMessageStream(
            final ByteArrayInputStream header,
            final ByteArrayInputStream data,
            MessageType type,
            int id) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        // for request and query add RFC60 header
        switch (type) {
            case REQUEST: {
                int headerSize = header.available();
                byte[] request = new byte[2];
                // RFC60
                request[1] = (byte) ((headerSize & 0x7F00) >> 8);
                request[0] = (byte) (headerSize & 0x00FF);
                outputStream.write(request, 0, 2);
                IOUtils.copy(header, outputStream);
                if (data != null) {
                    IOUtils.copy(data, outputStream);
                }
                break;
            }
            case QUERY: {
                byte[] request = new byte[2];
                // RFC60
                request[1] = (byte) (((id & 0x7F00) >> 8) | 0x80);
                request[0] = (byte) (id & 0x00FF);
                outputStream.write(request, 0, 2);
                if (header != null) {
                    IOUtils.copy(header, outputStream);
                }
                break;
            }
            case NOTIFICATION: {
                byte[] request = new byte[1];
                request[0] = (byte) id;
                outputStream.write(request, 0, 1);
                if (header != null) {
                    IOUtils.copy(header, outputStream);
                }
                break;
            }
        }

        return new ByteArrayInputStream(outputStream.toByteArray());
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
    public static byte[] buildRfc76MessageFrame(final ByteArrayInputStream data,
                                                final int next,
                                                int mtuSize,
                                                Rfc76SequenceNumber sequenceNumber) {
        int offset = RFC76_HEADER_SIZE;
        byte[] packet;
        if (data.available() > (mtuSize - RFC76_HEADER_SIZE)) {
            packet = new byte[mtuSize];
            packet[0] = (byte) (packet[0] | next | 0x06 | (sequenceNumber.getSeq() << 4)); // 0x06 == MORE
            data.read(packet, offset, mtuSize - offset);
        } else if (data.available() > 0) {
            packet = new byte[data.available() + RFC76_HEADER_SIZE];
            packet[0] = (byte) (packet[0] | next | 0x02 | (sequenceNumber.getSeq() << 4)); // 0x02 == LAST
            data.read(packet, offset, data.available());
        } else {
            packet = new byte[RFC76_HEADER_SIZE];
            packet[0] = (byte) (packet[0] | next | 0x02 | (sequenceNumber.getSeq() << 4));
        }
        sequenceNumber.increment();
        return packet;
    }

    /**
     * Generate list of air packets from data stream
     *
     * @param data           content to be split into air packets
     * @param mtuSize        att mtu size
     * @param sequenceNumber RFC76 ring counter
     * @return list of air packets
     */
    public static List<byte[]> buildRfc76MessageFrameAll(final ByteArrayInputStream data,
                                                         int mtuSize,
                                                         Rfc76SequenceNumber sequenceNumber) {
        List<byte[]> packets = new ArrayList<>();
        int next = 0;
        do {
            int temp = next; // workaround for stupid java translator idiotisim
            byte[] packet = buildRfc76MessageFrame(data, temp, mtuSize, sequenceNumber);
            packets.add(packet);
            next = 1;
        } while (data.available() > 0);
        return packets;
    }

    /**
     * Function to process RFC76 message header check rfc spec for more details
     *
     * @param packet air packet
     * @return @see PftpRfc76ResponseHeader
     */
    public static PftpRfc76ResponseHeader processRfc76MessageFrameHeader(byte[] packet) {
        PftpRfc76ResponseHeader header = new PftpRfc76ResponseHeader();
        processRfc76MessageFrameHeader(header, packet);
        return header;
    }

    /**
     * @param header RF76 header container
     * @param packet air packet
     */
    public static void processRfc76MessageFrameHeader(PftpRfc76ResponseHeader header, byte[] packet) {
        header.next = packet[0] & 0x01;
        header.status = (packet[0] >> 1) & 0x03;
        header.sequenceNumber = (packet[0] >> 4) & 0x0F;
        if (header.status == 0) {
            header.error = ((packet[RFC76_HEADER_SIZE] & 0xFF) | ((packet[RFC76_HEADER_SIZE + 1] << 8) & 0xFF)) & 0x0000FFFF;
        } else {
            header.payload = new byte[packet.length - RFC76_HEADER_SIZE];
            System.arraycopy(packet, RFC76_HEADER_SIZE, header.payload, 0, packet.length - RFC76_HEADER_SIZE);
        }
    }
}
