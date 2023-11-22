package ca.bcit.comp7005;

import org.apache.commons.cli.*;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Scanner;

import static java.lang.System.exit;

/**
 * This class describe the sender command with options entered by user.
 * It uses {@link <a href="https://commons.apache.org/proper/commons-cli/">Apache Commons CLI</a>} library to parse the
 * command line options.
 * The DatagramSender class is used to initialize a datagram socket and send messages to a receiver.
 */
public class SenderCliCommand {

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

    private final CommandLine enteredCommandLine;

    public SenderCliCommand(String[] args) throws ParseException, IOException {
        // Parse the arguments from CLI and convert them to CommandLine object using Apache Commons CLI library.
        // The CommandLine object is used to retrieve the entered options and their values.
        CommandLineParser parser = new DefaultParser();
        enteredCommandLine = parser.parse(CLI_OPTIONS, args);
    }

    /**
     * Executes the command.
     *
     * @throws IOException - if an I/O error occurs.
     */
    public void execute() throws IOException, NoSuchAlgorithmException {
        // Display help message and exit if the "help" option is present
        if (enteredCommandLine.hasOption(HELP_OPTION) ||
                !enteredCommandLine.hasOption(RECEIVER_PORT_OPTION) ||
                !enteredCommandLine.hasOption(RECEIVER_ADDRESS_OPTION)) {
            printHelp();
            exit(0);
        } else {
            runDatagramSender(enteredCommandLine.getOptionValue(RECEIVER_ADDRESS_OPTION), Integer.parseInt(enteredCommandLine.getOptionValue(RECEIVER_PORT_OPTION)));
        }
    }

    /**
     * Prints the help message.
     */
    private static void printHelp() {
        // automatically generate the help statement
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("Main -t 1000", HELP_HEADER, CLI_OPTIONS, HELP_FOOTER);
    }

    /**
     * Runs the DatagramSender.
     *
     * @param address - the receiver's address.
     * @param port    - the receiver's port.
     * @throws IOException - if an I/O error occurs.
     */
    private void runDatagramSender(String address, int port) throws IOException, NoSuchAlgorithmException {
        // Accept messages from the user and send them to the receiver
        DatagramSender datagramSender = DatagramSender.build();
        datagramSender.connectToReceiver(address, port);

        Scanner sc = new Scanner(System.in);
        String userInputMessage;
        do {
            System.out.println("\nEnter a message to send or \"" + QUIT_COMMAND + "\" to quit: ");
            userInputMessage = sc.nextLine();

            datagramSender.sendMessageReliably(userInputMessage.getBytes());
        } while (!userInputMessage.equals(QUIT_COMMAND));

        datagramSender.close();
    }

}
