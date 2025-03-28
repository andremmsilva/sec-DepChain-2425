package client;

import depchain.config.DepChainConfig;
import depchain.network.message.Message;
import depchain.network.message.MessagePayload;
import depchain.network.message.MessageType;
import depchain.network.sl.SLDeliverEvent;
import depchain.network.sl.SLSendEvent;
import depchain.network.sl.StubbornLink;

import java.util.ArrayList;
import java.util.Collections;
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
        System.out.println("Client with ID " + (clientId - 10) + " is running. Type 'append <transaction>' to send a " +
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
                case "append" -> {
                    if (tokens.length == 2) {
                        String transaction = tokens[1];
                        if (client.append(transaction)) {
                            System.out.println("Successfully appended " + transaction);
                        } else {
                            System.out.println("Failed to append " + transaction);
                        }
                    } else {
                        System.err.println("Usage: append <transaction>");
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

    public boolean append(String payload) {
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
                successes.add(res.getPayload().getType().equals(MessageType.APPEND_CONFIRMED));
            } finally {
                lock.unlock();
            }
        };

        // Register the response handler
        config.eventBus.register(SLDeliverEvent.class, onResponse);

        // Send the actual message to all nodes
        for (int i = 0; i < DepChainConfig.PROCESSES; i++) {
            config.eventBus.publish(new SLSendEvent(new Message(new MessagePayload(MessageType.APPEND_REQ, payload),
                    config.selfId),
                    i));
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
