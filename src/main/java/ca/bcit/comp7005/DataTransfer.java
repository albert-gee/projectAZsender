package ca.bcit.comp7005;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class describes the data transfer to the receiver.
 */
public class DataTransfer {

    private static final Logger logger = LoggerFactory.getLogger(DataTransfer.class);

    // The socket used to send and receive UDP datagrams
    private final DatagramSocket datagramSocket;
    private final int receiverPort;
    private final InetAddress receiverAddress;
    private final int maxResendAttempts;

    // The following properties are used for statistics:
    private int packetsSent;
    private int packetsReceived;

    public DataTransfer(DatagramSocket datagramSocket, InetAddress receiverAddress, int receiverPort, int maxResendAttempts) {
        this.datagramSocket = datagramSocket;
        this.receiverAddress = receiverAddress;
        this.receiverPort = receiverPort;
        this.maxResendAttempts = maxResendAttempts;
    }

    /**
     * Send data of specific type to the receiver.
     * 1. Send a SYN packet to the receiver. It contains the initial sequence number and the length of the whole message. ToDo: add the window size
     * 2. Wait for a SYN-ACK packet. If the receiver did not respond with the SYN-ACK packet, exit the program.
     * 3. Split the message into AZRP data packets and send them to the receiver one by one.
     * 4. Wait for an ACK packet from the receiver for each AZRP data packet. If the receiver did not respond with the ACK packet, exit the program.
     *
     * @param wholeMessageData - the message to send.
     * @param fileType         - the file type.
     */
    public void send(byte[] wholeMessageData, String fileType) throws NoSuchAlgorithmException {

        // Try to send a datagram containing the SYN packet and wait for a SYN-ACK packet
        AZRP synAckAzrp = null;
        int attempts = 0;
        final AZRP synAzrp = AZRP.generateSynPacket(wholeMessageData.length, fileType);

        while (attempts < this.maxResendAttempts) {
            try {

                // Generate a SYN packet and send to the receiver
                this.sendDatagram(synAzrp.toBytes());
                logger.debug("Sent a SYN packet with sequence number " + synAzrp.getSequenceNumber() + " and length " + synAzrp.getLength());

                // Wait for a SYN-ACK packet
                synAckAzrp = this.receiveSynAck(synAzrp);

                if (synAckAzrp != null) {
                    logger.debug("Received a SYN-ACK packet with sequence number " + synAckAzrp.getSequenceNumber() + " and length " + synAckAzrp.getLength());
                    break;
                }
            } catch (SocketTimeoutException e) {
                logger.debug("Timeout for acknowledgement packet reached");
            } catch (IOException e) {
                logger.debug("Error while sending a packet");
            }

            attempts++;
        }

        // If the receiver responded with a SYN-ACK packet, send the data packets
        if (synAckAzrp != null) {
            sendData(wholeMessageData, synAckAzrp.getSequenceNumber());
        } else {
            // If the receiver did not respond with a SYN-ACK packet, exit the program
            logger.error("The receiver did not respond to the SYN packet");
        }
    }

    /**
     * Receives a SYN-ACK packet.
     * @param synAzrp - the SYN packet to validate the SYN-ACK packet.
     * @return the SYN-ACK packet.
     * @throws IOException - if an I/O error occurs.
     */
    private AZRP receiveSynAck(AZRP synAzrp) throws IOException {
        AZRP synAckAzrp = null;

        // Try to receive a SYN-ACK packet
        // Receive a datagram and convert it to an AZRP packet
        DatagramPacket receivedDatagram = receiveDatagram();
        AZRP receivedAzrp = AZRP.fromBytes(receivedDatagram.getData());
        logger.debug("Received a packet with sequence number " + receivedAzrp.getSequenceNumber() + " and length " + receivedAzrp.getLength());

        // Validate that the AZRP packet is an ACK packet with the correct sequence number
        if (receivedAzrp.isValidSynAck(synAzrp)) {
            logger.debug("Received datagram contains a valid SYN-ACK packet");
            synAckAzrp = receivedAzrp;
        } else {
            logger.debug("Received datagram contains an invalid SYN-ACK packet");
        }

        return synAckAzrp;
    }

    /**
     * Sends the data packets to the receiver.
     *
     * @param wholeMessageData      - the message to send.
     * @param initialSequenceNumber - the initial sequence number.
     */
    private void sendData(byte[] wholeMessageData, int initialSequenceNumber) {

        // Split the message into AZRP packets.
        List<AZRP> buffer = convertMessageToAzrps(wholeMessageData, initialSequenceNumber);

        // Send the AZRP packets to the receiver one by one
        for (int i = 0; i < buffer.size(); i++) {
            AZRP dataAzrp = buffer.get(i);
            AZRP ackAzrp = sendDataAzrp(dataAzrp);

            if (ackAzrp != null) {
                logger.debug("Received an ACK packet with sequence number " + ackAzrp.getSequenceNumber() + " and length " + ackAzrp.getLength());
            } else {
                // If the receiver did not respond to the data packet, try to resend it
                logger.error("The receiver did not respond to the data packet");
                i--;
            }
        }

        logger.info("Sent message");
    }

    /**
     * Sends a data packet to the receiver and waits for an ACK packet.
     *
     * @param azrpPacket - the data packet to send.
     * @return the ACK packet.
     */
    public AZRP sendDataAzrp(AZRP azrpPacket) {
        AZRP ackAzrp = null;

        int attempts = 0;
        while (attempts < this.maxResendAttempts) {

            try {
                // Try to send a datagram packet containing the dataAzrp
                this.sendDatagram(azrpPacket.toBytes());
                logger.debug("Sent an AZRP data packet at " + azrpPacket.getSequenceNumber());

                // Try to receive an ACK packet
                // Receive a datagram packet
                DatagramPacket receiveDatagram = receiveDatagram();
                // Validate that the AZRP packet in the datagram is an ACK packet with the correct sequence number
                AZRP receivedAzrp = AZRP.fromBytes(receiveDatagram.getData());
                if (receivedAzrp.isACK() && receivedAzrp.getSequenceNumber() == (azrpPacket.getSequenceNumber() + azrpPacket.getData().length)) {
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
            AZRP dataAzrp = new AZRP(wholeMessageData, initialSequenceNumber, wholeMessageData.length, dataFlags);
            buffer.add(dataAzrp);
        }

        return buffer;
    }


    /**
     * Receives a UDP datagram.
     *
     * @return the received datagram packet.
     * @throws IOException - if an I/O error occurs.
     */
    private DatagramPacket receiveDatagram() throws IOException {
        byte[] packetBuffer = new byte[AZRP.MAXIMUM_PACKET_SIZE_IN_BYTES];
        DatagramPacket packet = new DatagramPacket(packetBuffer, packetBuffer.length);
        this.datagramSocket.receive(packet);

        this.packetsReceived++;

        return packet;
    }

    /**
     * Sends a UDP datagram.
     *
     * @param data - the data to be sent.
     * @throws IOException - if an I/O error occurs.
     */
    private void sendDatagram(byte[] data) throws IOException {
        DatagramPacket packet = new DatagramPacket(
                data, data.length, this.receiverAddress, this.receiverPort
        );
        datagramSocket.send(packet);

        this.packetsSent++;
    }

    public String getStatistics() {
        return "Packets sent: " + this.packetsSent + "; packets received: " + this.packetsReceived;
    }
}
