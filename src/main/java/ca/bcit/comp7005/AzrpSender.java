package ca.bcit.comp7005;

import java.io.IOException;
import java.net.*;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * A class that sends UDP datagrams to a receiver.
 */
public class AzrpSender {

//    private static final int TIMEOUT_MILLISECONDS = 5000;
    private static final int MAX_RESEND_ATTEMPTS = 3;

    private int sequenceNumber;
    private int receiverSequenceNumber;

    private InetAddress receiverAddress;
    private int receiverPort;

    private final DatagramSocket datagramSocket;

    /**
     * @throws SocketException - if the socket could not be opened.
     */
    public AzrpSender() throws IOException {
        // Create a socket for sending and receiving UDP packets
        this.datagramSocket = new DatagramSocket();
    }

    /**
     * Establish a connection with the receiver:
     * 1. Send a SYN packet to the receiver with the initial sequence number.
     * 2. Wait for the receiver to send a SYN-ACK packet.
     * 3. Send an ACK packet to the receiver with the initial sequence number and the acknowledgement number.
     *
     * @param receiverAddress - the receiver's address.
     * @param receiverPort    - the receiver's port.
     * @throws IOException - if an I/O error occurs.
     */
    public void connectToReceiver(int receiverPort, InetAddress receiverAddress) throws IOException, NoSuchAlgorithmException {
        System.out.println("Connecting to the receiver: " + receiverAddress + " " + receiverPort);

        // Send a SYN packet to the receiver with the initial sequence number.
        this.sequenceNumber = AZRP.generateInitialSequenceNumber();
        this.receiverAddress = receiverAddress;
        this.receiverPort = receiverPort;

        AZRP synPacket = AZRP.syn(this.sequenceNumber);
        synPacket.send(datagramSocket, receiverAddress, receiverPort);

        // Wait for the receiver to send a SYN-ACK packet
        AZRP synAckPacket = AZRP.receive(datagramSocket);
        System.out.println("Received SYN-ACK packet: " + synAckPacket.toString());
        // Check if the received packet is an SYN-ACK packet
        if (!synAckPacket.isSynAck()) {
            throw new IOException("Unable to connect to the receiver: Receiver responded with invalid packet type");
        }
        // Check if the acknowledgement number is correct
        int receiverAcknowledgementNumber = synAckPacket.getAcknowledgementNumber();
        if (receiverAcknowledgementNumber != sequenceNumber + 1) {
            throw new IOException("Unable to connect to the receiver: Invalid acknowledgement number");
        }

        // Send an ACK packet to the receiver with the initial sequence number and the acknowledgement number
        // Increment the sequence number
        sequenceNumber++;
        // Set the receiver's initial sequence number and increment by 1
        this.receiverSequenceNumber = synAckPacket.getSequenceNumber() + 1;

        AZRP ackAzrpPacket = AZRP.ack(sequenceNumber, this.receiverSequenceNumber);
        ackAzrpPacket.send(datagramSocket, receiverAddress, receiverPort);

    }

    /**
     * Disconnects from the receiver:
     * 1. Send a FIN packet to the receiver.
     * 2. Wait for the receiver to send an ACK packet.
     * 3. Wait for the receiver to send a FIN packet.
     * 4. Send an ACK packet to the receiver.
     *
     * @throws IOException - if an I/O error occurs.
     */
    public void disconnectFromReceiver() throws IOException {
        // Send a FIN packet to the receiver
        AZRP finPacket = AZRP.fin(sequenceNumber, 0);
        finPacket.send(datagramSocket, receiverAddress, receiverPort);

        // Wait for the receiver to send an ACK packet
        AZRP ackPacket = AZRP.receive(datagramSocket);
        System.out.println("Received ACK packet: " + ackPacket);

        if (!ackPacket.isACK() || ackPacket.getAcknowledgementNumber() != sequenceNumber) {
            throw new IOException("Unable to disconnect from the receiver: Invalid acknowledgement number");
        }

        // Wait for the receiver to send a FIN packet
        AZRP receivedFinPacket = AZRP.receive(datagramSocket);
        System.out.println("Received FIN packet: " + receivedFinPacket);

        if (!receivedFinPacket.isFIN()) {
            throw new IOException("Unable to disconnect from the receiver: Invalid packet type");
        }

        // Send an ACK packet to the receiver
        AZRP ackFinPacket = AZRP.ack(sequenceNumber, receivedFinPacket.getSequenceNumber() + 1);
        ackFinPacket.send(datagramSocket, receiverAddress, receiverPort);

        this.sequenceNumber = 0;
        this.receiverAddress = null;
        this.receiverPort = 0;

        this.datagramSocket.close();
    }


    /**
     * Sends user data to the receiver.
     *
     * @param wholeMessageData - the message to send.
     * @throws IOException - if an I/O error occurs.
     */
    public void sendMessage(byte[] wholeMessageData) throws IOException, NoSuchAlgorithmException {

        // Define constants for maximum packet size and header size
        final int MAX_PAYLOAD_SIZE = AZRP.MAXIMUM_PACKET_SIZE_IN_BYTES - AZRP.PACKET_HEADER_SIZE_IN_BYTES;

        // Encapsulate the message in an AZRP packet.
        // If the message is too big, split it into multiple AZRP packets.
        // Add the packets to the temporary buffer of outcoming packets.
        if (wholeMessageData.length > MAX_PAYLOAD_SIZE) {
            // Byte position of the next chunk in the whole message
            int wholeMessageDataBytePosition = 0;

            while (wholeMessageDataBytePosition < wholeMessageData.length) {
                int chunkSize = Math.min(wholeMessageDataBytePosition + MAX_PAYLOAD_SIZE, wholeMessageData.length);
                // Extract the current chunk of data
                byte[] chunk = Arrays.copyOfRange(
                        wholeMessageData,
                        wholeMessageDataBytePosition,
                        chunkSize);
                wholeMessageDataBytePosition += chunkSize;

                AZRP azrpPacket = AZRP.data(chunk, sequenceNumber, 0);
                sendAzrpDataPacket(azrpPacket); // Send the packet to the receiver and resend it until it is acknowledged
            }
        } else {
            AZRP azrpPacket = AZRP.data(wholeMessageData, sequenceNumber, receiverSequenceNumber);
            sendAzrpDataPacket(azrpPacket); // Send the packet to the receiver and resend it until it is acknowledged
        }

    }


    /**
     * Sends an AZRP data packet to the receiver and waits for the receiver to acknowledge it.
     *
     * @param azrpPacket - the AZRP packet to send.
     * @throws IOException - if an I/O error occurs.
     */
    private void sendAzrpDataPacket(AZRP azrpPacket) throws IOException {
        azrpPacket.send(datagramSocket, receiverAddress, receiverPort);

        // Wait for the receiver to respond with acknowledge AZRP packets for each sent packet
        this.datagramSocket.setSoTimeout(5000);

        // Set resend attempts to 0
        int resendAttempts = 0;

        // Loop until the packet is acknowledged
        while (resendAttempts < MAX_RESEND_ATTEMPTS) {
            try {
                AZRP receivedAzrpPacket = AZRP.receive(datagramSocket);

                // Check if the received packet is an ACK packet
                if (receivedAzrpPacket.isACK() && receivedAzrpPacket.getAcknowledgementNumber() == azrpPacket.getSequenceNumber() + azrpPacket.getData().length) {
                    this.receiverSequenceNumber = receivedAzrpPacket.getSequenceNumber() + receivedAzrpPacket.getData().length;
                    break; // Exit the loop if acknowledgment received
                }

            } catch (SocketTimeoutException e) {
                // Resend the packet if acknowledgment not received within the timeout
                azrpPacket.send(datagramSocket, receiverAddress, receiverPort);
                resendAttempts++;
            }
        }
    }

}
