package ca.bcit.comp7005;

import org.apache.commons.cli.*;

import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;

import static java.lang.System.exit;

/**
 * This program sends messages from command line to a receiver.
 */
public class Main {

    /**
     * The main method of the program builds a sender and sends messages to a receiver.
     * The user can quit the receiver by typing "quit_receiver" and the sender by typing "quit".
     */
    public static void main(String[] args) {

        try {
            SenderCliCommand senderCliCommand = new SenderCliCommand(args);
            senderCliCommand.execute();

        } catch (SocketException e) {
            exitWithError("Error creating DatagramSender", e);
        } catch (UnknownHostException e) {
            exitWithError("Error creating DatagramSender - Unknown host", e);
        } catch (ParseException e) {
            exitWithError("Parsing failed", e);
        } catch (IOException e) {
            exitWithError("Error sending message", e);
        }

    }

    /**
     * Exits the program with an error code and throws a RuntimeException with the specified message and exception.
     *
     * @param message   - error message.
     * @param exception - exception that caused the error.
     */
    private static void exitWithError(String message, Exception exception) {
        exit(1);
        throw new RuntimeException(message, exception);
    }
}