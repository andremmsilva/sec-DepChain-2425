package depchain.network;

import depchain.config.DepChainConfig;
import depchain.crypto.CryptoUtils;

import java.net.InetAddress;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.*;

import classes.State;

public class AuthenticatedPerfectLink {
    private final StubbornLink stubbornLink;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final BlockingQueue<Message> messageQueue = new LinkedBlockingQueue<>();
    private final HashSet<Message> receivedMessages = new HashSet<>();
    private final ExecutorService executor;
    private final DepChainConfig config;
    private volatile boolean running = true;

    //Maybe o config não devia estar aqui
    public AuthenticatedPerfectLink(int port, PrivateKey privateKey, PublicKey publicKey, DepChainConfig config) throws Exception {
        this.stubbornLink = new StubbornLink(port);
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.executor = Executors.newSingleThreadExecutor();
        this.config = config;
        this.startListening();
    }

    public void sendMessage(InetAddress dest, int destPort, String message) {
        byte[] signature = CryptoUtils.signMessage(message, privateKey);
        Message m = new Message(dest, destPort, message, signature, config.SELF_ID );
        stubbornLink.sendMessage(m);
    }

    private void startListening() {
        executor.submit(() -> {
            try {
                while (running) {
                    Message m = stubbornLink.receiveMessage();
                    if (m != null) {
                        handleMessage(m);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void handleMessage(Message m) {
        String message = m.getMessage();
        byte[] signature = m.getSignature();
        int id = m.getSenderId();

        if (hasReceivedMessage(m)) {
            return; // Prevent duplicates
        }

        //Isto não me faz sentido, devia ser com a public de quem enviou a mensagem
        if (CryptoUtils.verifySignature(message, signature, config.getPublicKey(id))) {
            receivedMessages.add(m);
            messageQueue.add(m);
            System.out.println("Authenticated message received: " + message + " " + Base64.getEncoder().encodeToString(signature));
        } else {
            System.err.println("Failed authentication for message: " + message);
        }
    }

    public boolean hasReceivedMessage(Message m) {
        return receivedMessages.contains(m);
    }

    public Message getNextMessage() throws InterruptedException {
        return messageQueue.take();
    }

    public void close() {
        running = false;
        stubbornLink.close();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ignored) {
        }
    }
}
