package ca.bcit.comp7005;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.zip.CRC32;

/**
 * This class describes the AZRP reliable protocol.
 */
public class AZRP {

    // 16 bytes = 4 integer fields 4 bytes each: sequence number, acknowledgment number, checksum, dataLength
    // 4 bytes = 1 array of 4 boolean values: flags
    // 20 bytes total
    public static final int PACKET_HEADER_SIZE_IN_BYTES = 20;

    public static final int MAXIMUM_PACKET_SIZE_IN_BYTES = 30;

    // The flags are used to indicate the type of the packet:
    // 0 - SYN
    // 1 - ACK
    // 2 - FIN
    private final boolean[] flags;

    private final int sequenceNumber;

    private final int acknowledgementNumber;

    private final int checksum;

    private final byte[] data;

    public AZRP(byte[] data, int sequenceNumber, int acknowledgementNumber, int checksum, boolean[] flags) {
        // Check if the packet size (header + data) is not bigger than the maximum packet size
        if (PACKET_HEADER_SIZE_IN_BYTES + data.length > MAXIMUM_PACKET_SIZE_IN_BYTES) {
            throw new IllegalArgumentException("The packet size is too big.");
        }

        this.flags = flags;
        this.sequenceNumber = sequenceNumber;
        this.acknowledgementNumber = acknowledgementNumber;
        this.checksum = checksum;
        this.data = data;
    }

    /**
     * Creates a data packet.
     * @param data - the data.
     * @param sequenceNumber - the sequence number.
     * @param acknowledgementNumber - the acknowledgement number.
     * @return the data packet.
     */
    public static AZRP data(byte[] data, int sequenceNumber, int acknowledgementNumber) {
        boolean[] flags = new boolean[]{false, false, false, false};
        return new AZRP(data, sequenceNumber, acknowledgementNumber, calculateChecksum(data), flags);
    }

    /**
     * Creates a SYN packet.
     * @param initialSequenceNumber - the initial sequence number.
     * @return the SYN packet.
     */
    public static AZRP syn(int initialSequenceNumber) {
        boolean[] flags = new boolean[]{true, false, false};
        return new AZRP(new byte[0], initialSequenceNumber, 0, calculateChecksum(new byte[0]), flags);
    }

    /**
     * Creates a SYN-ACK packet.
     * @param sequenceNumber - the sequence number.
     * @param acknowledgementNumber - the acknowledgement number.
     * @return the SYN-ACK packet.
     */
    public static AZRP synAck(int sequenceNumber, int acknowledgementNumber) {
        boolean[] flags = new boolean[]{true, true, false};
        return new AZRP(new byte[0], sequenceNumber, acknowledgementNumber, calculateChecksum(new byte[0]), flags);
    }

    /**
     * Creates an ACK packet.
     * @param sequenceNumber - the sequence number.
     * @param acknowledgementNumber - the acknowledgement number.
     * @return the ACK packet.
     */
    public static AZRP ack(int sequenceNumber, int acknowledgementNumber) {
        boolean[] flags = new boolean[]{false, true, false};
        return new AZRP(new byte[0], sequenceNumber, acknowledgementNumber, calculateChecksum(new byte[0]), flags);
    }

    /**
     * Creates a FIN packet.
     * @param sequenceNumber - the sequence number.
     * @param acknowledgementNumber - the acknowledgement number.
     * @return the FIN packet.
     */
    public static AZRP fin(int sequenceNumber, int acknowledgementNumber) {
        boolean[] flags = new boolean[]{false, false, true};
        return new AZRP(new byte[0], sequenceNumber, acknowledgementNumber, calculateChecksum(new byte[0]), flags);
    }

    /**
     * Deserializes AZRP packet from bytes.
     * @param data - array of bytes.
     * @return the AZRP packet.
     */
    public static AZRP fromBytes(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int flagsInt = buffer.getInt();
        int sequenceNumber = buffer.getInt();
        int acknowledgementNumber = buffer.getInt();
        int dataLength = buffer.getInt();
        int checksum = buffer.getInt();
        byte[] payload = new byte[dataLength];
        buffer.get(payload);

        // Convert the flags integer to an array of booleans
        boolean[] flags = new boolean[3];
        for (int i = 0; i < flags.length; i++) {
            // Check if the i-th bit in the flagsInt is set
            flags[i] = (flagsInt & (1 << i)) != 0;
        }

        return new AZRP(payload, sequenceNumber, acknowledgementNumber, checksum, flags);
    }

    /**
     * Serializes the AZRP packet to bytes.
     * @return the array of bytes.
     */
    public byte[] toBytes() {
        // Convert the flags to an integer
        int flagsToInt = 0;
        for (int i = flags.length - 1; i >= 0; i--) {
            // Set the i-th bit in the result if the corresponding boolean is true
            if (flags[i]) {
                flagsToInt |= (1 << i);
            }
        }

        ByteBuffer buffer = ByteBuffer.allocate(PACKET_HEADER_SIZE_IN_BYTES + data.length);
        buffer.putInt(flagsToInt);
        buffer.putInt(sequenceNumber);
        buffer.putInt(acknowledgementNumber);
        buffer.putInt(data.length);
        buffer.putInt(calculateChecksum(data));
        buffer.put(data);
        return buffer.array();
    }

    /**
     * Calculates the checksum of the data.
     * @param data - the data to calculate the checksum.
     * @return the checksum of the data.
     */
    private static int calculateChecksum(byte[] data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data);
        return (int) crc32.getValue();
    }



    /**
     * Generates a secure initial sequence number.
     *
     * @return a secure sequence number.
     * @throws NoSuchAlgorithmException - if no Provider supports a SecureRandomSpi implementation for the specified algorithm.
     */
    public static int generateInitialSequenceNumber() throws NoSuchAlgorithmException {
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
     * Validates the checksum of the data.
     * @return true if the checksum is valid, false otherwise.
     */
    public boolean validateChecksum() {
        return calculateChecksum(data) == checksum;
    }

/**
     * Gets the sequence number.
     * @return the sequence number.
     */
    public boolean[] getFlags() {
        return flags;
    }

    /**
     * Gets the sequence number.
     * @return the sequence number.
     */
    public int getSequenceNumber() {
        return sequenceNumber;
    }

    /**
     * Gets the acknowledgement number.
     * @return the acknowledgement number.
     */
    public int getAcknowledgementNumber() {
        return acknowledgementNumber;
    }

    /**
     * Gets the checksum.
     * @return the checksum.
     */
    public int getChecksum() {
        return checksum;
    }

    /**
     * Gets the data.
     * @return the data.
     */
    public byte[] getData() {
        return data;
    }

    public boolean isACK() {
        return flags[1];
    }

    public boolean isSynAck() {
        return flags[0] && flags[1];
    }

    public boolean isFIN() {
        return flags[2];
    }

    /**
     * Sends the packet to the receiver.
     * @param datagramSocket - the socket to send the packet.
     * @param receiverAddress - the address of the receiver.
     * @param receiverPort - the port of the receiver.
     * @throws IOException - if an I/O error occurs.
     */
    public void send(DatagramSocket datagramSocket, InetAddress receiverAddress, int receiverPort) throws IOException {
        System.out.println("Sending AZRP packet: " + this);
        byte[] data = this.toBytes();
        DatagramPacket packet = new DatagramPacket(
                data, data.length, receiverAddress, receiverPort
        );

        try {
            datagramSocket.send(packet);
        } catch (IOException e) {
            throw new IOException("Unable to send AZRP packet", e);
        }
    }

    public static AZRP receive(DatagramSocket datagramSocket) throws IOException {
        System.out.println("Receiving AZRP packet");
        byte[] packetBuffer = new byte[AZRP.MAXIMUM_PACKET_SIZE_IN_BYTES];
        DatagramPacket packet = new DatagramPacket(packetBuffer, packetBuffer.length);
        datagramSocket.receive(packet);
        return AZRP.fromBytes(packet.getData());
    }

    @Override
    public String toString() {
        return "AZRP{" +
                "\nflags: SYN=" + flags[0] + ", ACK=" + flags[1] + ", FIN=" + flags[2] +
                "\n, sequenceNumber=" + sequenceNumber +
                "\n, acknowledgementNumber=" + acknowledgementNumber +
                "\n, checksum=" + checksum +
                "\n, data=" + new String(data) +
                "\n}\n";
    }
}
