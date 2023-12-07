package ca.bcit.comp7005;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.*;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * This class sends user input to a receiver.
 * The user input can be a text message or a path to a file. If the input starts with a slash, it's a path to a file.
 * If the input does not start with a slash, it's a text message.
 * The user can quit the sender by typing "quit".
 */
public class Sender {
    private static final Logger logger = LoggerFactory.getLogger(Sender.class);

    // The socket used to send and receive UDP datagrams
    private final DatagramSocket datagramSocket;

    private final int maxResendAttempts;

    private static final String logFilePath = "log.txt";

    /**
     * @throws SocketException - if the socket could not be opened.
     */
    public Sender(int port, int maxResendAttempts, int timeOutMilliseconds) throws IOException {
        this.datagramSocket = new DatagramSocket(port);
        // Set the timeout for receiving acknowledgement packets
        this.datagramSocket.setSoTimeout(timeOutMilliseconds);
        logger.info("UDP socket created on port " + datagramSocket.getLocalPort());

        this.maxResendAttempts = maxResendAttempts;
    }

    /**
     * Sends user input to the receiver.
     * If the user input starts with a slash, it's a path to a file, otherwise it's a text message.
     * @param userInputMessage - the message to send.
     * @param receiverPort - the port to send the message to.
     * @param receiverAddress - the address to send the message to.
     * @throws IOException - if an I/O error occurs.
     */
    public void sendUserInput(String userInputMessage, final int receiverPort, final String receiverAddress) throws IOException, NoSuchAlgorithmException {

        DataTransfer dataTransfer = new DataTransfer(this.datagramSocket, InetAddress.getByName(receiverAddress), receiverPort, this.maxResendAttempts);
        byte[] wholeMessageData;
        String fileType;

        // If user input starts with a slash, it's a path to a file.
        if (userInputMessage.charAt(0) == '/') {
            // Get the file type from the user input.
            fileType = getFileType(userInputMessage);

            // Read the file
            Path path = FileSystems.getDefault().getPath(userInputMessage);
            wholeMessageData = Files.readAllBytes(path);
        } else {
            // If user input does not start with a slash, it's a text message.
            wholeMessageData = userInputMessage.getBytes();
            fileType = "textstring";
        }

        dataTransfer.send(wholeMessageData, fileType);

        this.writeStatistics(dataTransfer.getStatistics());
    }

    /**
     * If the user input starts with a slash, it's a path to a file. This method returns the file type.
     * @param filePath - the path to the file.
     * @return - the file type.
     * @throws IOException - if an I/O error occurs.
     */
    private static String getFileType(String filePath) throws IOException {

        Path path = FileSystems.getDefault().getPath(filePath);
        String fileType = Files.probeContentType(path);

        if (fileType != null) {
            return fileType;
        } else {
            throw new IOException("The file type could not be determined");
        }
    }

    /**
     * Writes the statistics to the log file.
     * The entry in the log file contains the number of sent and received packets
     * @param line - the statistics to write.
     */
    private void writeStatistics(String line) {
        // Get the current timestamp for the log entry
        LocalDateTime currentTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String timestamp = currentTime.format(formatter);

        // Append the log message with timestamp
        String logEntry = timestamp + " - " + line;

        // Write the log entry to the file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFilePath, true))) {
            writer.write(logEntry);
            writer.newLine(); // Add a newline for the next entry
        } catch (IOException e) {
            System.err.println("Error writing to the log file: " + e.getMessage());
        }
    }
}
