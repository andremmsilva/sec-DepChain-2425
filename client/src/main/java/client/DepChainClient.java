package client;

import depchain.config.DepChainConfig;
import depchain.crypto.CryptoUtils;
import depchain.network.message.Message;
import depchain.network.message.MessagePayload;
import depchain.network.message.MessageType;
import depchain.network.message.Transaction;
import depchain.network.sl.SLDeliverEvent;
import depchain.network.sl.SLSendEvent;
import depchain.network.sl.StubbornLink;

import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class DepChainClient {
    private final DepChainConfig config;
    private HashMap<PrivateKey,String> keysToAddresses = new HashMap<>();

    public DepChainClient(int id) {
        this.config = new DepChainConfig(id);
        // We are not concerned with attacks where a compromised
        // library tries to attack an honest application, hence no APL used here
        // The sl is "unused", since we only need it to process events, and never call it directly
        @SuppressWarnings("unused")
        StubbornLink sl = new StubbornLink(this.config);
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: ClientMain <clientId>");
            System.exit(1);
        }

        // Avoid conflicts in ports
        int clientId = 10;
        try {
            clientId += Integer.parseInt(args[0]); // Client ID from command line
        } catch (NumberFormatException e) {
            System.err.println("Usage: ClientMain <clientId>");
            System.exit(1);
        }

        DepChainClient client = new DepChainClient(clientId);

        final Scanner scanner = new Scanner(System.in);
        System.out.println("Client with ID " + (clientId - 10) + " is running. Type '<transaction> <arguments>' to send a " +
                "transaction.");
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
                case "transfer" -> {
                    if (tokens.length == 3) {
                        String account = tokens[1];
                        try{
                            Integer.parseInt(tokens[2]);
                        } catch (NumberFormatException e){
                            System.err.println("Usage: transfer <recieverAddress> <C for ISTCoin, D for Depcoin>");
                        }
                        if(tokens[3].equals("C") || tokens[3].equals("D")){
                            
                            if (client.operation(tokens)) {
                                System.out.println("Successful transaction");
                            } else {
                                System.out.println("Failed transaction");
                            }
                        }
                    } else {
                        System.err.println("Usage: transfer <recieverAddress> <amount> <C for ISTCoin, D for Depcoin>");
                    }
                }
                case "transferFrom" -> {
                    if (tokens.length == 3) {
                        String account = tokens[1];
                        try{
                            Integer.parseInt(tokens[2]);
                        } catch (NumberFormatException e){
                            System.err.println("Usage: transfer <recieverAddress> <C for ISTCoin, D for Depcoin>");
                        }
                        if(tokens[3].equals("C") || tokens[3].equals("D")){
                            
                            if (client.operation(tokens)) {
                                System.out.println("Successful transaction");
                            } else {
                                System.out.println("Failed transaction");
                            }
                        }
                    } else {
                        System.err.println("Usage: transferFrom <recieverAddress> <amount> <C for ISTCoin, D for Depcoin>");
                    }
                }
                case "balance" -> {
                    if (tokens.length == 1) {
                        if (client.operation(tokens)) {
                            System.out.println("Successfully retrieved balance: ");
                        } else {
                            System.out.println("Failed to retrieve balance: ");
                        }
                    } else {
                        System.err.println("Usage: balance");
                    }
                }
                case "exit" -> {
                    System.out.println("Exiting...");
                    scanner.close();
                }

                default -> System.err.println("Unrecognized command: " + line);
            }
        }
    }

    public boolean operation(String[] operation) {
        AtomicInteger replyCount = new AtomicInteger(0);
        List<Boolean> successes = Collections.synchronizedList(new ArrayList<>());
        Lock lock = new ReentrantLock();
        Condition newMessageCond = lock.newCondition();
        // Wait for f + 1 messages from nodes

        Consumer<SLDeliverEvent> onResponse = e -> {
            try {
                lock.lock();
                newMessageCond.signal();
                Message res = e.arg;
                replyCount.incrementAndGet();
                successes.add(res.getPayload().getType().equals(MessageType.TRANSACTION_CONFIRMED));
            } finally {
                lock.unlock();
            }
        };

        // Register the response handler
        config.eventBus.register(SLDeliverEvent.class, onResponse);
        String transType = operation[0];
        String data=null;
        byte[] signature = null;
        MessagePayload payload;
        if(transType.equals("balance")){
            data = operation[0]+"|"+operation[1];
            signature = CryptoUtils.signMessage(data,config.getSelfPrivateKey());
            payload = new MessagePayload(MessageType.BALANCE_REQ, new Transaction(transType, "myaddress", operation[1], signature));

        } else if(transType.equals("transfer")){
            data = operation[0]+"|"+operation[1]+"|"+operation[2]+"|"+operation[3];
            signature = CryptoUtils.signMessage(data,config.getSelfPrivateKey());
            payload = new MessagePayload(MessageType.TRANSFER_REQ, new Transaction(transType, "myaddress", operation[1], Long.parseLong(operation[2]), operation[3], signature));

        } else if(transType.equals("transferFrom")){
            data = operation[0]+"|"+operation[1]+"|"+operation[2]+"|"+operation[3];
            signature = CryptoUtils.signMessage(data,config.getSelfPrivateKey());
            payload = new MessagePayload(MessageType.TRANSFERFROM_REQ, new Transaction(transType, operation[1],"myaddress", Long.parseLong(operation[2]), operation[3], signature));

        } else{
             return false;
        }

        Message message = new Message(payload, config.selfId, signature);
        // Send the actual message to all nodes
        for (int i = 0; i < DepChainConfig.PROCESSES; i++) {
            config.eventBus.publish(new SLSendEvent(message,i));
        }

        try {
            lock.lock();
            // If we get all replies or F + 1 positive replies, don't wait longer
            while (replyCount.get() < DepChainConfig.PROCESSES || successes.size() < DepChainConfig.FAIL_BOUND + 1) {
                // Block until a message arrives
                if (!newMessageCond.await(10, TimeUnit.SECONDS)) {
                    // 10 seconds have passed, giving up on obtaining F + 1 positive replies
                    return false;
                }
            }
            return successes.size() >= DepChainConfig.FAIL_BOUND + 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("DepChainClient@append: " + e.getMessage());
            return false;
        } finally {
            lock.unlock();
            config.eventBus.unregister(SLDeliverEvent.class, onResponse);
        }
    }
}
