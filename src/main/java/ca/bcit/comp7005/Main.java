package ca.bcit.comp7005;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {

        // Get the receiver's address and port from the user
        Scanner sc = new Scanner(System.in);
        System.out.println("Enter the receiver's address: ");
        String userInputReceiverAddress = sc.nextLine();
        System.out.println("Enter the receiver's port: ");
        int userInputReceiverPort = sc.nextInt();

        // Create a sender and run it
        Sender sender = new Sender(userInputReceiverAddress, userInputReceiverPort);
        sender.run();

    }
}