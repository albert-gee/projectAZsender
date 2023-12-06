package ca.bcit.comp7005;

import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.zip.CRC32;

/**
 * This class describes the AZRP reliable protocol.
 */
public class AZRP {

    // 16 bytes = 4 integer fields 4 bytes each: sequence number, length, checksum
    // 4 bytes = array of flags is converted to an integer
    // 20 bytes total
    public static final int PACKET_HEADER_SIZE_IN_BYTES = 19;

    public static final int MAXIMUM_PACKET_SIZE_IN_BYTES = 1500;

    // The data field of the SYN packet contains the file extension
    public static final int FILE_EXTENSION_LENGTH = 20;

    // The flags are used to indicate the type of the packet:
    // 0 - SYN
    // 1 - ACK
    private final boolean[] flags;

    private final int sequenceNumber;

    private final int length;

    private final int checksum;

    private final byte[] data;

    public AZRP(byte[] data, int sequenceNumber, int length, int checksum, boolean[] flags) {

        // Check if the packet size (header + data) is not bigger than the maximum packet size
        if (PACKET_HEADER_SIZE_IN_BYTES + data.length > MAXIMUM_PACKET_SIZE_IN_BYTES) {
            throw new IllegalArgumentException("The packet size is too big.");
        }

        this.flags = flags;
        this.sequenceNumber = sequenceNumber;
        this.length = length;
        this.checksum = checksum;
        this.data = data;
    }

    public AZRP(byte[] data, int sequenceNumber, int length, boolean[] flags) {
        this(
                data,
                sequenceNumber,
                length,
                calculateChecksum(data),
                flags);
    }

    /**
     * Creates a data packet.
     *
     * @param data           - the data.
     * @param sequenceNumber - the sequence number.
     * @param length         - the sequence number.
     * @return the data packet.
     */
    public static AZRP data(byte[] data, int sequenceNumber, int length) {
        boolean[] flags = new boolean[]{false, false, false, false};
        return new AZRP(data, sequenceNumber, length, calculateChecksum(data), flags);
    }


    /**
     * Deserializes AZRP packet from bytes.
     *
     * @param data - array of bytes.
     * @return the AZRP packet.
     */
    public static AZRP fromBytes(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int flagsInt = buffer.getInt();
        int sequenceNumber = buffer.getInt();
        int length = buffer.getInt();
        int checksum = buffer.getInt();

        int payloadLength = flagsInt == 0 ? length : FILE_EXTENSION_LENGTH;

        byte[] payload = new byte[payloadLength]; // Set payload length to 0 if it's a SYN packet
        buffer.get(payload);

        // Convert the flags integer to an array of booleans
        boolean[] flags = new boolean[3];
        for (int i = 0; i < flags.length; i++) {
            // Check if the i-th bit in the flagsInt is set
            flags[i] = (flagsInt & (1 << i)) != 0;
        }

        return new AZRP(payload, sequenceNumber, length, checksum, flags);
    }

    /**
     * Serializes the AZRP packet to bytes.
     *
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

        int payloadLength = isSYN() ? FILE_EXTENSION_LENGTH : length;

        ByteBuffer buffer = ByteBuffer.allocate(PACKET_HEADER_SIZE_IN_BYTES + payloadLength);
        buffer.putInt(flagsToInt);
        buffer.putInt(sequenceNumber);
        buffer.putInt(length);
        buffer.putInt(calculateChecksum(data));
        buffer.put(data);
        return buffer.array();
    }

    /**
     * Calculates the checksum of the data.
     *
     * @param data - the data to calculate the checksum.
     * @return the checksum of the data.
     */
    private static int calculateChecksum(byte[] data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data);
        return (int) crc32.getValue();
    }

    public static AZRP generateSynPacket(final int wholeMessageDataLength, String fileType) throws NoSuchAlgorithmException {
        final int initialSequenceNumber = AZRP.generateInitialSequenceNumber(); // The sequence starts from this number
        final boolean[] synFlags = new boolean[]{true, false}; // SYN, ACK

        byte[] data = new byte[FILE_EXTENSION_LENGTH];
        System.arraycopy(fileType.getBytes(), 0, data, 0, fileType.getBytes().length);

        return new AZRP(data, initialSequenceNumber, wholeMessageDataLength, synFlags);
    }

    public static AZRP generateSynAckPacket(final AZRP synPacket) {
        final boolean[] synAckFlags = new boolean[]{true, true}; // SYN, ACK
        return new AZRP(synPacket.getData(), synPacket.getSequenceNumber(), synPacket.getLength(), synAckFlags);
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
     *
     * @return true if the checksum is valid, false otherwise.
     */
    public boolean isChecksumValid() {
        return calculateChecksum(data) == checksum;
    }

    /**
     * Gets the sequence number.
     *
     * @return the sequence number.
     */
    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public int getLength() {
        return length;
    }

    /**
     * Gets the data.
     *
     * @return the data.
     */
    public byte[] getData() {
        return data;
    }

    public int getCheckSum() {
        return checksum;
    }

    public boolean isSYN() {
        return flags[0];
    }

    public boolean isACK() {
        return flags[1];
    }

    public boolean isValidData() {
        return !isSYN() && !isACK() && isChecksumValid();
    }

    public boolean isValidSyn() {
        return isSYN() && !isACK() && isChecksumValid();
    }

    public boolean isValidSynAck(AZRP synAzrp) {
        return isSYN() && isACK() && getSequenceNumber() == synAzrp.getSequenceNumber() && isChecksumValid();
    }

    @Override
    public String toString() {
        return "AZRP{" +
                "\nflags: SYN=" + flags[0] + ", ACK=" + flags[1] +
                "\n, length=" + length +
                "\n, sequenceNumber=" + sequenceNumber +
                "\n, checksum=" + checksum +
                "\n, data=" + new String(data) +
                "\n}\n";
    }
}
