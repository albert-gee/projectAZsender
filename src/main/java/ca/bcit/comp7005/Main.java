package ca.bcit.comp7005;

import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.Scanner;

import static java.lang.System.exit;

/**
 * This program sends messages from command line to a receiver.
 */
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final String QUIT_COMMAND = "quit";
    private static final String HELP_HEADER = "Sender sends messages to a receiver.";
    private static final String HELP_FOOTER = "";

    private static final Options CLI_OPTIONS;
    private static final Option RECEIVER_ADDRESS_OPTION;
    private static final Option HELP_OPTION;
    private static final Option RECEIVER_PORT_OPTION;

    static {
        // Set up command line options
        CLI_OPTIONS = new Options();
        RECEIVER_ADDRESS_OPTION = new Option("a", "address", true, "IP address of the receiver");
        CLI_OPTIONS.addOption(RECEIVER_ADDRESS_OPTION);
        HELP_OPTION = new Option("h", "help", false, "Help");
        CLI_OPTIONS.addOption(HELP_OPTION);
        RECEIVER_PORT_OPTION = new Option("p", "port", true, "Port of the receiver");
        CLI_OPTIONS.addOption(RECEIVER_PORT_OPTION);
    }

    /**
     * The main method of the program builds a sender and sends messages to a receiver.
     * The user can quit the receiver by typing "quit_receiver" and the sender by typing "quit".
     */
    public static void main(String[] args) {

        try {
            // Parse the arguments from CLI and convert them to CommandLine object using
            // {@link <a href="https://commons.apache.org/proper/commons-cli/">Apache Commons CLI</a>}.
            // The CommandLine object is used to retrieve the entered options and their values.
            CommandLineParser parser = new DefaultParser();
            CommandLine enteredCommandLine = parser.parse(CLI_OPTIONS, args);

            // Display help message and exit if the "help" option is present
            if (enteredCommandLine.hasOption(HELP_OPTION) ||
                    !enteredCommandLine.hasOption(RECEIVER_PORT_OPTION) ||
                    !enteredCommandLine.hasOption(RECEIVER_ADDRESS_OPTION)) {

                // automatically generate the help statement
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("Main -t 1000", HELP_HEADER, CLI_OPTIONS, HELP_FOOTER);
            } else {

                // Get the receiver's address and port from the entered options
                String receiverAddress = enteredCommandLine.getOptionValue(RECEIVER_ADDRESS_OPTION.getOpt());
                int receiverPort = Integer.parseInt(enteredCommandLine.getOptionValue(RECEIVER_PORT_OPTION.getOpt()));

                Sender sender = new Sender(receiverPort, receiverAddress); // Create a DatagramSender

                // Accept messages from the user
                Scanner sc = new Scanner(System.in);
                String userInputMessage;
                do {
                    System.out.println("\nEnter a message to send or \"" + QUIT_COMMAND + "\" to quit: ");
                    userInputMessage = sc.nextLine();
                    byte[] userInputMessageBytes = userInputMessage.getBytes();
                    logger.debug("Sending message (" + userInputMessageBytes.length + " bytes): " + userInputMessage);

                    // Send the message to the receiver
                    sender.sendMessage(userInputMessageBytes);
                } while (!userInputMessage.equals(QUIT_COMMAND));

            }

        } catch (SocketException e) {
            exitWithError("Error creating DatagramSender", e);
        } catch (UnknownHostException e) {
            exitWithError("Error creating DatagramSender - Unknown host", e);
        } catch (ParseException e) {
            exitWithError("Parsing failed", e);
        } catch (IOException e) {
            exitWithError("IOException", e);
        } catch (NoSuchAlgorithmException e) {
            exitWithError("Unable to generate initial sequence number", e);
        } catch (Exception e) {
            exitWithError("Error", e);
        }
    }

    /**
     * Exits the program with an error code and throws a RuntimeException with the specified message and exception.
     *
     * @param message   - error message.
     * @param exception - exception that caused the error.
     */
    private static void exitWithError(String message, Exception exception) {
        System.err.println(message + ": " + exception.getMessage());
        exit(1);
    }

}