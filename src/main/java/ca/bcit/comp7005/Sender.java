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

    private final int receiverPort;
    private final InetAddress receiverAddress;

    /**
     * @throws SocketException - if the socket could not be opened.
     */
    public Sender(final int receiverPort, final String receiverAddress) throws IOException {

        this.datagramSocket = new DatagramSocket();
        // Set the timeout for receiving acknowledgement packets
        this.datagramSocket.setSoTimeout(TIMEOUT_MILLISECONDS);
        logger.info("UDP socket created on port " + datagramSocket.getLocalPort());

        this.receiverAddress = InetAddress.getByName(receiverAddress);
        this.receiverPort = receiverPort;
    }

    /**
     * Sends a whole user message to the receiver:
     * 1. Send a SYN packet to the receiver. It contains the initial sequence number and the length of the whole message. ToDo: add the window size
     * 2. Wait for a SYN-ACK packet. If the receiver did not respond with the SYN-ACK packet, exit the program.
     * 3. Split the message into AZRP data packets and send them to the receiver one by one.
     * 4. Wait for an ACK packet from the receiver for each AZRP data packet. If the receiver did not respond with the ACK packet, exit the program.
     * @param wholeMessageData - the message to send.
     * @throws IOException - if an I/O error occurs.
     */
    public void sendMessage(byte[] wholeMessageData) throws IOException, NoSuchAlgorithmException {

        // Generate a SYN packet and send to the receiver
        final AZRP synAzrp = AZRP.generateSynPacket(wholeMessageData.length);
        AZRP synAckAzrp = this.sendSynAzrp(synAzrp);
        logger.debug("Sent a SYN packet with sequence number " + synAzrp.getSequenceNumber() + " and length " + synAzrp.getLength());

        // If the receiver responded with a SYN-ACK packet, send the data packets
        if (synAckAzrp != null) {
            logger.debug("Received a SYN-ACK packet with sequence number " + synAckAzrp.getSequenceNumber() + " and length " + synAckAzrp.getLength());

            // Split the message into AZRP packets.
            List<AZRP> buffer = convertMessageToAzrps(wholeMessageData, synAzrp.getSequenceNumber());
            Collections.shuffle(buffer);

            for (AZRP dataAzrp : buffer) {
                AZRP ackAzrp = sendDataAzrp(dataAzrp);

                if (ackAzrp != null) {
                    logger.debug("Received an ACK packet with sequence number " + ackAzrp.getSequenceNumber() + " and length " + ackAzrp.getLength());
                } else {
                    // If the receiver did not respond to the data packet, exit the program
                    logger.error("The receiver did not respond to the data packet");
                    return;
                }
            }

            logger.info("Sent message: " + new String(wholeMessageData));
        } else {
            // If the receiver did not respond to the SYN packet, exit the program
            logger.error("The receiver did not respond to the SYN packet");
        }
    }


    private AZRP sendSynAzrp(AZRP synAzrp) {
        AZRP synAckAzrp = null;

        int attempts = 0;
        while (attempts < MAX_RESEND_ATTEMPTS) {

            try {
                // Try to send a datagram packet containing the dataAzrp
                this.sendDatagram(synAzrp.toBytes());
                logger.debug("Sent a SYN AZRP packet at " + synAzrp.getSequenceNumber());

                // Try to receive a SYN-ACK packet
                // Receive a datagram packet
                DatagramPacket receiveDatagram = receiveDatagram();
                // Validate that the AZRP packet in the datagram is an ACK packet with the correct sequence number
                AZRP receivedAzrp = AZRP.fromBytes(receiveDatagram.getData());
                if (receivedAzrp.isValidSynAck(synAzrp)) {
                    logger.debug("Received a SYN-ACK packet with sequence number " + receivedAzrp.getSequenceNumber() + " and length " + receivedAzrp.getLength());
                    synAckAzrp = receivedAzrp;
                    break;
                } else {
                    logger.debug("Received an invalid SYN-ACK packet");
                }
            } catch (SocketTimeoutException e) {
                logger.debug("Timeout for acknowledgement packet reached");
            } catch (IOException e) {
                logger.debug("Error while sending a packet");
            }

            attempts++;
        }

        return synAckAzrp;
    }
    public AZRP sendDataAzrp(AZRP azrpPacket) {
        AZRP ackAzrp = null;

        int attempts = 0;
        while (attempts < MAX_RESEND_ATTEMPTS) {

            try {
                // Try to send a datagram packet containing the dataAzrp
                this.sendDatagram(azrpPacket.toBytes());
                logger.debug("Sent an AZRP packet at " + azrpPacket.getSequenceNumber() + ": " + new String(azrpPacket.getData()));

                // Try to receive an ACK packet
                // Receive a datagram packet
                DatagramPacket receiveDatagram = receiveDatagram();
                // Validate that the AZRP packet in the datagram is an ACK packet with the correct sequence number
                AZRP receivedAzrp = AZRP.fromBytes(receiveDatagram.getData());
                if (receivedAzrp.isACK() && receivedAzrp.getSequenceNumber() == (azrpPacket.getSequenceNumber() + azrpPacket.getData().length) && receivedAzrp.isChecksumValid()) {
                    ackAzrp = receivedAzrp;
                    break;
                } else {
                    logger.debug("Received an invalid ACK packet");
                }
            } catch (SocketTimeoutException e) {
                logger.debug("Timeout for acknowledgement packet reached");
            } catch (IOException e) {
                logger.debug("Error while sending a packet");
            }

            attempts++;
        }

        return ackAzrp;
    }

    /**
     * Converts the message to a list of AZRP packets.
     *
     * @param wholeMessageData      - the message to send.
     * @param initialSequenceNumber - the initial sequence number.
     * @return the list of AZRP packets.
     */
    private static List<AZRP> convertMessageToAzrps(byte[] wholeMessageData, int initialSequenceNumber) {
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
                final AZRP dataAzrp = new AZRP(chunk, wholeMessageDataBytePosition + initialSequenceNumber, chunkSize, dataFlags);
                buffer.add(dataAzrp);

                wholeMessageDataBytePosition += chunkSize;
            }
        } else {
            // Send the packet to the receiver
            final boolean[] dataFlags = new boolean[]{false, false}; // SYN, ACK
            AZRP dataAzrp = new AZRP(wholeMessageData, 0, wholeMessageData.length, dataFlags);
            buffer.add(dataAzrp);
        }
        return buffer;
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
        return packet;
    }

    /**
     * Sends a UDP datagram.
     * @param data - the data to be sent.
     * @throws IOException - if an I/O error occurs.
     */
    private void sendDatagram(byte[] data) throws IOException {
        DatagramPacket packet = new DatagramPacket(
                data, data.length, this.receiverAddress, this.receiverPort
        );
        datagramSocket.send(packet);
    }
}
