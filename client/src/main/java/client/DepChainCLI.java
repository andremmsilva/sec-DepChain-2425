package client;

import java.util.Scanner;

public class DepChainCLI {
    private final IDepChainClient client;
    private final Scanner scanner;

    public DepChainCLI(int clientId) {
        this.client = new DepChainClientCore(clientId + 10); // Match your ID offset
        this.scanner = new Scanner(System.in);
        System.out.println("Client with ID " + clientId + " is running. Type commands...");
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: ClientMain <clientId>");
            System.exit(1);
        }

        int clientId = Integer.parseInt(args[0]);
        new DepChainCLI(clientId).run();
    }

    public void run() {
        while (true) {
            System.out.print("\n> ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty())
                continue;

            try {
                handleCommand(input);
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    private void handleCommand(String input) throws InterruptedException {
        String[] parts = input.split(" ");
        boolean operationSuccess;
        switch (parts[0].toLowerCase()) {
            case "balance":
                if (parts.length < 3) {
                    throw new IllegalArgumentException("Balance command requires at least two arguments");
                }
                long balance = client.getBalance(parts[1], parts[2]);
                if (balance < 0) {
                    System.out.println("Error obtaining balance...");
                } else {
                    System.out.println("Current balance: " + balance);
                }
                break;

            case "transfer":
                if (parts.length < 4)
                    throw new IllegalArgumentException("Invalid arguments");
                operationSuccess = client.transfer(parts[1], Long.parseLong(parts[2]), parts[3]);
                System.out.println("Transfer " + (operationSuccess ? "succeeded" : "failed"));
                break;

            case "transferfrom":
                if (parts.length < 5)
                    throw new IllegalArgumentException("Invalid arguments");
                operationSuccess = client.transferFrom(parts[1], parts[2], Long.parseLong(parts[3]),
                        parts[4]);
                System.out.println("TransferFrom " + (operationSuccess ? "succeeded" : "failed"));
                break;

            case "addblacklist":
                if (parts.length < 2) {
                    throw new IllegalArgumentException("Invalid arguments");
                }
                operationSuccess = client.addToBlacklist(parts[1]);
                System.out.println("AddBlacklist " + (operationSuccess ? "succeeded" : "failed"));
                break;
            case "removeblacklist":
                if (parts.length < 2) {
                    throw new IllegalArgumentException("Invalid arguments");
                }
                operationSuccess = client.removeFromBlacklist(parts[1]);
                System.out.println("AddBlacklist " + (operationSuccess ? "succeeded" : "failed"));
                break;
            case "isblacklisted":
                if (parts.length < 2) {
                    throw new IllegalArgumentException("Invalid arguments");
                }
                operationSuccess = client.isBlacklisted(parts[1]);
                System.out.println("Is blacklisted " + (operationSuccess ? "succeeded" : "failed"));
                break;

            case "exit":
                System.exit(0);

            default:
                System.err.println("Unknown command: " + parts[0]);
        }
    }
}
