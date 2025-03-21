package client;

import depchain.config.DepChainConfig;
import library.ClientLibrary;

import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

public class ClientMain {
    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            System.err.println("Usage: java ClientMain <clientId> (clientId must be 10 or greater)");
            System.exit(1);
        }

        int clientId = Integer.parseInt(args[0]); // Client ID from command line

        if (clientId < 10) {
            System.err.println("Error: Client ID must be 10 or greater.");
            System.exit(1);
        }

        DepChainConfig config = new DepChainConfig(clientId);
        ClientLibrary clientLibrary = new ClientLibrary(config);
        clientLibrary.close();

        final Scanner scanner = new Scanner(System.in);
        System.out.println("Client is running. Type 'append <transaction>' to send a transaction.");
        System.out.println("Type 'exit' to quit.");
        
        while (true) {
            System.out.printf("%n> ");
            String line = scanner.nextLine();

            // Empty command
            if (line.trim().isEmpty()) {
                System.out.println();
            continue;
            }

            String[] tokens = line.split(" ");

            switch (tokens[0]) {

            case "append":
                if (tokens.length == 2) {
                    String transaction = tokens[1];
                    CompletableFuture<Boolean> response = clientLibrary.appendTransaction(transaction);
                    System.out.println("Appending transaction...");

                    // Check if the transaction was successfull
                    response.thenAccept(success -> {
                        if (success) {
                            System.out.printf("Transaction appended successfully: %s%n", transaction);
                        } else {
                            System.err.printf("Transaction failed: %s%n", transaction);
                        }
                    });
                } else {
                    System.err.println("Usage: append <transaction>");
                }

                case "exit":
                System.out.println("Exiting...");
                scanner.close();

                default:
                System.err.println("Unrecognized command: " + line);
            }
        }
    }
}
