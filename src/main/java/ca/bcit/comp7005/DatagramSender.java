package ca.bcit.comp7005;

import java.io.IOException;
import java.net.*;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A class that sends UDP datagrams to a receiver.
 */
public class DatagramSender {

    private static final int TIMEOUT_MILLISECONDS = 5000;
    private static final int MAX_RESEND_ATTEMPTS = 3;

    private final DatagramSocket socket;
    private final ExecutorService executorService;

    private int sequenceNumber;

    private List<AZRP> temporaryBufferOfSentAzrpPackets;

    private String receiverAddress;
    private int receiverPort;

    private DatagramSender(DatagramSocket datagramSocket) {
        this.socket = datagramSocket;
        this.executorService = Executors.newCachedThreadPool();
        this.temporaryBufferOfSentAzrpPackets = new ArrayList<>();
    }

    /**
     * Builds a sender with a new datagram socket.
     *
     * @return a new sender.
     * @throws SocketException - if the socket could not be opened, or the socket could not bind to the specified local port.
     */
    public static DatagramSender build() throws SocketException, NoSuchAlgorithmException {
        DatagramSocket datagramSocket = new DatagramSocket();
        return new DatagramSender(datagramSocket);
    }

    /**
     * Builds a sender with a new datagram socket bound to the specified port.
     *
     * @param port - local port to use in the bind operation.
     * @return a new sender.
     * @throws SocketException - if the socket could not be opened, or the socket could not bind to the specified local port.
     */
    public static DatagramSender build(int port) throws SocketException, NoSuchAlgorithmException {
        DatagramSocket datagramSocket = new DatagramSocket(port);
        return new DatagramSender(datagramSocket);
    }

    /**
     * Builds a sender with a new datagram socket bound to the specified port and address.
     *
     * @param port         - local port to use in the bind operation.
     * @param localAddress - local address to bind (can be null).
     * @return a new sender.
     * @throws SocketException - if the socket could not be opened, or the socket could not bind to the specified local port.
     */
    public static DatagramSender build(int port, InetAddress localAddress) throws SocketException, NoSuchAlgorithmException {
        DatagramSocket datagramSocket = new DatagramSocket(port, localAddress);
        return new DatagramSender(datagramSocket);
    }

    /**
     * Generates a secure initial sequence number.
     *
     * @return a secure sequence number.
     * @throws NoSuchAlgorithmException - if no Provider supports a SecureRandomSpi implementation for the specified algorithm.
     */
    private int generateInitialSequenceNumber() throws NoSuchAlgorithmException {
        SecureRandom secureRandom = SecureRandom.getInstanceStrong();

        // Choose the number of bits for the random integer (e.g., 32 bits)
        int numBits = 32;

        // Calculate the byte length based on the number of bits
        int byteLength = (numBits + 7) / 8;

        // Generate a random byte array
        byte[] randomBytes = new byte[byteLength];
        secureRandom.nextBytes(randomBytes);

        // Convert the random bytes to an integer
        int secureSequenceNumber = 0;
        for (int i = 0; i < byteLength; i++) {
            secureSequenceNumber = (secureSequenceNumber << 8) | (randomBytes[i] & 0xFF);
        }

        // Ensure the generated integer is non-negative
        secureSequenceNumber &= Integer.MAX_VALUE;

        return secureSequenceNumber;
    }

    /**
     * Establishes a connection with the receiver.
     *
     * @param receiverAddress - the receiver's address.
     * @param receiverPort    - the receiver's port.
     * @throws IOException - if an I/O error occurs.
     */
    public void connectToReceiver(String receiverAddress, int receiverPort) throws IOException, NoSuchAlgorithmException {
        // Generate the initial sequence number
        this.sequenceNumber = generateInitialSequenceNumber();

        this.receiverAddress = receiverAddress;
        this.receiverPort = receiverPort;

        // Send a SYN packet to the receiver with the initial sequence number
        AZRP synPacket = AZRP.syn(sequenceNumber);
        sendAzrpPacket(synPacket);

        // Wait for the receiver to send a SYN-ACK packet
        DatagramPacket synAckPacket = receiveAcknowledgment(synPacket);
        if (synAckPacket == null) {
            throw new IOException("Unable to establish connection with the receiver");
        } else {
            // Send an ACK packet to the receiver with the initial sequence number and the acknowledgement number
            AZRP ackAzrpPacket = AZRP.ack(sequenceNumber, synPacket.getSequenceNumber() + 1);

            sendAzrpPacket(ackAzrpPacket);
        }
    }

    private DatagramPacket receiveAcknowledgment(AZRP azrpPacket) throws IOException {
        DatagramPacket ackPacket = null;

        // Set a timeout for the socket to handle potential ACK losses
        socket.setSoTimeout(TIMEOUT_MILLISECONDS);

        int resendAttempts = 0;

        while (resendAttempts < MAX_RESEND_ATTEMPTS) {
            try {
                // Wait for the receiver to acknowledge the packet
                ackPacket = new DatagramPacket(new byte[AZRP.MAXIMUM_PACKET_SIZE_IN_BYTES], AZRP.MAXIMUM_PACKET_SIZE_IN_BYTES);
                socket.receive(ackPacket);
                break; // Exit the loop if acknowledgment received
            } catch (SocketTimeoutException e) {
                // Resend the packet if acknowledgment not received within the timeout
                sendAzrpPacket(azrpPacket);
                resendAttempts++;
                ackPacket = null;
            }
        }

        // Reset the socket timeout to its original value
        socket.setSoTimeout(0);

        return ackPacket;
    }

    /**
     * Closes the socket.
     */
    public void close() {
        socket.close();
    }

    /**
     * Sends user data to the receiver.
     *
     * @param wholeMessageData - the message to send.
     * @throws IOException - if an I/O error occurs.
     */
    public void sendMessageReliably(byte[] wholeMessageData) throws IOException {

        // Define constants for maximum packet size and header size
        final int MAX_PAYLOAD_SIZE = AZRP.MAXIMUM_PACKET_SIZE_IN_BYTES - AZRP.PACKET_HEADER_SIZE_IN_BYTES;

        // Clear the temporary buffer of sent packets
        temporaryBufferOfSentAzrpPackets.clear();

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
                temporaryBufferOfSentAzrpPackets.add(azrpPacket);
                sendAzrpPacket(azrpPacket);

            }
        } else {
            AZRP azrpPacket = AZRP.data(wholeMessageData, sequenceNumber, 0);
            temporaryBufferOfSentAzrpPackets.add(azrpPacket);
            sendAzrpPacket(azrpPacket);
        }

        // Wait for the receiver to acknowledge the packets
    }

    /**
     * Sends an AZRP packet to the receiver.
     * @param azrpPacket - the AZRP packet to send.
     * @throws IOException - if an I/O error occurs.
     */
    public void sendAzrpPacket(AZRP azrpPacket) throws IOException {
        sendDatagram(azrpPacket.toBytes());
        sequenceNumber += azrpPacket.getData().length;
        temporaryBufferOfSentAzrpPackets.add(azrpPacket);
    }

    /**
     * Sends raw data to the receiver.
     *
     * @param data            - the data to send.
     * @throws IOException - if an I/O error occurs.
     */
    public void sendDatagram(byte[] data) throws IOException {
        DatagramPacket packet = new DatagramPacket(
                data, data.length, InetAddress.getByName(receiverAddress), receiverPort
        );
        socket.send(packet);
    }

}
