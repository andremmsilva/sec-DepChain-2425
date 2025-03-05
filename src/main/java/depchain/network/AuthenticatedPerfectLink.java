package depchain.network;

import depchain.crypto.CryptoUtils;

import java.net.InetAddress;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.HashSet;
import java.util.concurrent.*;

public class AuthenticatedPerfectLink {
    private final StubbornLink stubbornLink;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final BlockingQueue<Message> messageQueue = new LinkedBlockingQueue<>();
    private final HashSet<Message> receivedMessages = new HashSet<>();
    private final ExecutorService executor;
    private volatile boolean running = true;

    public AuthenticatedPerfectLink(int port, PrivateKey privateKey, PublicKey publicKey) throws Exception {
        this.stubbornLink = new StubbornLink(port);
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.executor = Executors.newSingleThreadExecutor();
        this.startListening();
    }

    public void sendMessage(InetAddress dest, int destPort, String message) throws Exception {
        byte[] signature = CryptoUtils.signMessage(message, privateKey);
        Message m = new Message(dest, destPort, message, signature);
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

        if (hasReceivedMessage(m)) {
            return; // Prevent duplicates
        }

        if (CryptoUtils.verifySignature(message, signature, publicKey)) {
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
