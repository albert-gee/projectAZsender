package ca.bcit.comp7005;

import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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

    private static final int MAX_RESEND_ATTEMPTS = 3;
    private static final int SENDER_PORT = 56723;
    private static final int TIMEOUT_MILLISECONDS = 5000;


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
     * The main method parses the arguments from CLI and runs the sender or the help page.
     * The user can quit the receiver by typing "quit".
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

                // Display help message
                displayHelp();
            } else {

                // Get the receiver's address and port from the entered options
                String receiverAddress = enteredCommandLine.getOptionValue(RECEIVER_ADDRESS_OPTION.getOpt());
                int receiverPort = Integer.parseInt(enteredCommandLine.getOptionValue(RECEIVER_PORT_OPTION.getOpt()));

                // Run the sender
                runSender(receiverAddress, receiverPort);
            }
        } catch (ParseException e) {
            exitWithError("Parsing failed", e);
        } catch (Exception e) {
            exitWithError("Error", e);
        }
    }

    /**
     * Displays help message.
     */
    private static void displayHelp() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("Sender", HELP_HEADER, CLI_OPTIONS, HELP_FOOTER, true);
    }

    /**
     * Runs the sender.
     *
     * @param receiverAddress - the address of the receiver.
     * @param receiverPort    - the port of the receiver.
     */
    private static void runSender(String receiverAddress, int receiverPort) {
        try {
            Sender sender = new Sender(SENDER_PORT, MAX_RESEND_ATTEMPTS, TIMEOUT_MILLISECONDS); // Create a DatagramSender

            // Accept input from the user until the user types "quit"
            Scanner sc = new Scanner(System.in);
            String userInput;
            do {
                userInput = sc.nextLine();
                sender.sendUserInput(userInput, receiverPort, receiverAddress);
            } while (!userInput.equals(QUIT_COMMAND));
        } catch (UnknownHostException e) {
            exitWithError("Error creating DatagramSender - Unknown host", e);
        } catch (IOException e) {
            exitWithError("Error creating DatagramSender", e);
        } catch (NoSuchAlgorithmException e) {
            exitWithError("Unable to generate initial sequence number", e);
        }
    }

    /**
     * Exits the program with an error code and throws a RuntimeException with the specified message and exception.
     *
     * @param message   - error message.
     * @param exception - exception that caused the error.
     */
    private static void exitWithError(String message, Exception exception) {
        logger.error(message + ": " + exception.getMessage());
        exit(1);
    }

}