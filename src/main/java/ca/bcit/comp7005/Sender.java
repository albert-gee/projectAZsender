package ca.bcit.comp7005;

import java.io.IOException;
import java.net.*;
import java.util.Scanner;

/**
 * A class that sends messages to a receiver.
 */
public class Sender {

    private final String receiverAddress;
    private final int receiverPort;
    private static final String QUIT_COMMAND = "quit";

    public Sender(String receiverAddress, int receiverPort) {
        this.receiverAddress = receiverAddress;
        this.receiverPort = receiverPort;
    }

    /**
     * Creates a socket and sends messages to the receiver until the user types "quit".
     */
    public void run() {

        // Create a socket
        try (DatagramSocket socket = new DatagramSocket()) {
            System.out.println("Socket created:");
            printSocketDetails(socket);

            // Accept user input and send it to the receiver until the user types "quit"
            processUserInput(socket);

        } catch (SocketException e) {
            System.out.println("Socket creation failed");
        } catch (UnknownHostException e) {
            System.out.println("Unknown host");
        } catch (IOException e) {
            System.out.println("IO exception");
        }
    }

    private void printSocketDetails(DatagramSocket socket) throws SocketException {
        System.out.println("  Local port: " + socket.getLocalPort());
        System.out.println("  Timeout: " + socket.getSoTimeout());
        System.out.println("  Receive buffer size: " + socket.getReceiveBufferSize());
        System.out.println("  Send buffer size: " + socket.getSendBufferSize());
    }

    private void processUserInput(DatagramSocket socket) throws IOException {
        Scanner sc = new Scanner(System.in);
        String userInputMessage;
        do {
            System.out.println("\nEnter a message to send (or \"quit\" to quit): ");
            userInputMessage = sc.nextLine();

            byte[] buffer = userInputMessage.getBytes();

            DatagramPacket packet = new DatagramPacket(
                    buffer, buffer.length, InetAddress.getByName(this.receiverAddress), this.receiverPort
            );
            socket.send(packet);

        } while (!userInputMessage.equals(QUIT_COMMAND));
    }

}
