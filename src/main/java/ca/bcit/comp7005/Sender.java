package ca.bcit.comp7005;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * A class that sends data to a receiver.
 */
public class Sender {
    private static final int MAX_RESEND_ATTEMPTS = 3;
    private static final int TIMEOUT_MILLISECONDS = 5000;
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    // The socket used to send and receive UDP datagrams
    private final DatagramSocket datagramSocket;

    /**
     * @throws SocketException - if the socket could not be opened.
     */
    public Sender() throws IOException {
        this.datagramSocket = new DatagramSocket();
        this.datagramSocket.setSoTimeout(TIMEOUT_MILLISECONDS);
        logger.info("UDP socket created on port " + this.datagramSocket.getLocalPort());
    }

    /**
     * Receives a UDP datagram.
     * @return the received datagram packet.
     * @throws IOException - if an I/O error occurs.
     */
    private DatagramPacket receiveDatagram() throws IOException {
        byte[] packetBuffer = new byte[AZRP.MAXIMUM_PACKET_SIZE_IN_BYTES];
        DatagramPacket packet = new DatagramPacket(packetBuffer, packetBuffer.length);
        this.datagramSocket.receive(packet);
        logger.debug("Received a datagram packet from " + packet.getAddress().getHostAddress() + ":" + packet.getPort());
        return packet;
    }

    /**
     * Sends a UDP datagram.
     * @param data - the data to be sent.
     * @throws IOException - if an I/O error occurs.
     */
    private void sendDatagram(final int receiverPort, final String receiverAddress, byte[] data) throws IOException {
        DatagramPacket packet = new DatagramPacket(
                data, data.length, InetAddress.getByName(receiverAddress), receiverPort
        );
        datagramSocket.send(packet);
        logger.debug("Sent a datagram packet to " + packet.getAddress().getHostAddress() + ":" + packet.getPort());
    }

    /**
     * Sends user data to the receiver.
     *
     * @param wholeMessageData - the message to send.
     * @throws IOException - if an I/O error occurs.
     */
    public void sendMessage(final int receiverPort, final String receiverAddress, byte[] wholeMessageData) throws IOException, NoSuchAlgorithmException {

        // 1. Send a SYN packet to the receiver with the initial sequence number and the length of the whole message.
        final int sequenceNumber = AZRP.generateInitialSequenceNumber();
        final int wholeMessageDataLength = wholeMessageData.length;
        final boolean[] SynFlags = new boolean[]{true, false}; // SYN, ACK
        final AZRP synAzrp = new AZRP(new byte[0], sequenceNumber, wholeMessageDataLength, SynFlags);
        this.sendDatagram(receiverPort, receiverAddress, synAzrp.toBytes());
        logger.debug("Sent a SYN packet with sequence number " + sequenceNumber + " and length " + wholeMessageDataLength);

        // 2. Wait for the receiver to send a SYN-ACK packet
        DatagramPacket packet = receiveDatagram();
        AZRP synAckPacket = AZRP.fromBytes(packet.getData());
        // Validate the SYN-ACK packet
        if (!synAckPacket.isSynAck()) {
            throw new IOException("Unable to connect to the receiver: Receiver responded with invalid packet type");
        } else if (synAckPacket.getSequenceNumber() != sequenceNumber) {
            throw new IOException("Unable to connect to the receiver: Receiver responded with invalid sequence number");
        } else if (!synAckPacket.isChecksumValid()) {
            throw new IOException("Unable to connect to the receiver: Receiver responded with invalid checksum");
        }
        logger.debug("Received a SYN-ACK packet with sequence number " + synAckPacket.getSequenceNumber() + " and length " + synAckPacket.getLength());

        // The receiver has acknowledged the connection and is ready to receive data packets.
        // Define constants for maximum packet size and header size
        final int MAX_PAYLOAD_SIZE = AZRP.MAXIMUM_PACKET_SIZE_IN_BYTES - AZRP.PACKET_HEADER_SIZE_IN_BYTES;

        // 3. Encapsulate the message into AZRP packets and send them.
        // If the message is too big, split it into multiple AZRP packets.
        // Add the packets to the temporary buffer of outcoming packets.
        List<AZRP> buffer = new ArrayList<>();
        if (wholeMessageData.length > MAX_PAYLOAD_SIZE) {

            // Byte position of the next chunk in the whole message
            int wholeMessageDataBytePosition = 0;

            while (wholeMessageDataBytePosition < wholeMessageData.length) {
                // Extract the current chunk of data
                final int chunkSize = Math.min(MAX_PAYLOAD_SIZE, wholeMessageData.length - wholeMessageDataBytePosition);
                final byte[] chunk = Arrays.copyOfRange(
                        wholeMessageData,
                        wholeMessageDataBytePosition,
                        wholeMessageDataBytePosition + chunkSize);

                // Send the packet to the receiver
                final boolean[] dataFlags = new boolean[]{false, false}; // SYN, ACK
                final AZRP dataAzrp = new AZRP(chunk, wholeMessageDataBytePosition + sequenceNumber, chunkSize, dataFlags);
                buffer.add(dataAzrp);

                wholeMessageDataBytePosition += chunkSize;
            }
        } else {
            // Send the packet to the receiver
            final boolean[] dataFlags = new boolean[]{false, false}; // SYN, ACK
            AZRP dataAzrp = new AZRP(wholeMessageData, 0, wholeMessageData.length, dataFlags);
            buffer.add(dataAzrp);
        }

        for (AZRP dataAzrp : buffer) {
            this.sendDatagram(receiverPort, receiverAddress, dataAzrp.toBytes());
            logger.debug("Sent a data packet at " + dataAzrp.getSequenceNumber() + ": " + new String(dataAzrp.getData()));

            // Wait for the receiver to send an ACK packet
            int attempts = 0;
            while (attempts < MAX_RESEND_ATTEMPTS) {
                try {
                    DatagramPacket ackPacket = receiveDatagram();
                    AZRP ackAzrp = AZRP.fromBytes(ackPacket.getData());
                    // Validate the ACK packet
                    if (ackAzrp.isACK() && ackAzrp.getSequenceNumber() == dataAzrp.getSequenceNumber() && ackAzrp.isChecksumValid()) {
                        logger.debug("Received an ACK packet with sequence number " + ackAzrp.getSequenceNumber() + " and length " + ackAzrp.getLength());
                        break;
                    }
                } catch (SocketTimeoutException e) {
                    logger.debug("Timeout: Resending the packet");
                    this.sendDatagram(receiverPort, receiverAddress, dataAzrp.toBytes());
                    logger.debug("Sent a data packet at " + dataAzrp.getSequenceNumber() + ": " + new String(dataAzrp.getData()));
                }
                attempts++;
                if (attempts == MAX_RESEND_ATTEMPTS) {
                    throw new IOException("Unable to send the packet: Maximum number of resend attempts reached");
                }
            }
        }

        logger.info("Sent message: " + new String(wholeMessageData));


        // Receive the acknowledgment from the receiver
        // Update sequence number
    }
}
