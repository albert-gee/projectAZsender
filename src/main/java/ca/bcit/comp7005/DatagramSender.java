package ca.bcit.comp7005;

import java.io.IOException;
import java.net.*;

/**
 * A class that sends UDP datagrams to a receiver.
 */
public class DatagramSender {

    private final DatagramSocket socket;

    private DatagramSender(DatagramSocket datagramSocket) {
        this.socket = datagramSocket;
    }

    /**
     * Builds a sender with a new datagram socket.
     *
     * @return a new sender.
     * @throws SocketException - if the socket could not be opened, or the socket could not bind to the specified local port.
     */
    public static DatagramSender build() throws SocketException {
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
    public static DatagramSender build(int port) throws SocketException {
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
    public static DatagramSender build(int port, InetAddress localAddress) throws SocketException {
        DatagramSocket datagramSocket = new DatagramSocket(port, localAddress);
        return new DatagramSender(datagramSocket);
    }

    /**
     * Enables/disables SO_TIMEOUT with the specified timeout, in milliseconds.
     * A timeout of zero is interpreted as an infinite timeout.
     * @param timeout - the specified timeout, in milliseconds.
     * @throws SocketException if there is an error in the underlying protocol, such as a UDP error.
     */
    public void setSoTimeout(int timeout) throws SocketException {
        socket.setSoTimeout(timeout);
    }

    /**
     * Closes the socket.
     */
    public void close() {
        socket.close();
    }

    public void printSocketDetails() throws SocketException {
        System.out.println("Socket Details:");
        System.out.println("--------------------");
        System.out.println("  Local port: " + socket.getLocalPort());
        System.out.println("  Timeout: " + socket.getSoTimeout());
        System.out.println("  Receive buffer size: " + socket.getReceiveBufferSize());
        System.out.println("  Send buffer size: " + socket.getSendBufferSize());
    }

    /**
     * Sends a String message to the receiver.
     *
     * @param receiverAddress - the receiver's address.
     * @param receiverPort    - the receiver's port.
     * @param userInputMessage - the message to send.
     * @throws IOException - if an I/O error occurs.
     */
    public void sendMessage(String receiverAddress, int receiverPort, String userInputMessage) throws IOException {
        byte[] data = userInputMessage.getBytes();
        sendMessage(receiverAddress, receiverPort, data);
    }

    /**
     * Sends a message to the receiver.
     *
     * @param receiverAddress - the receiver's address.
     * @param receiverPort    - the receiver's port.
     * @param data            - the data to send.
     * @throws IOException - if an I/O error occurs.
     */
    public void sendMessage(String receiverAddress, int receiverPort, byte[] data) throws IOException {

        DatagramPacket packet = new DatagramPacket(
                data, data.length, InetAddress.getByName(receiverAddress), receiverPort
        );
        socket.send(packet);
    }

}
