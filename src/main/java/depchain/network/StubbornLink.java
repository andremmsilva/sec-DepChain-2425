package depchain.network;

import java.net.InetAddress;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StubbornLink {
    private final FairLossLink fairLossLink;
    private final Queue<Message> pendingMessages = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private volatile boolean running = true;

    public StubbornLink(int port) throws Exception {
        this.fairLossLink = new FairLossLink(port);
        startRetransmissionTask();
    }

    public void sendMessage(Message message) {
        pendingMessages.add(message);
        sendOnce(message);
    }

    private void sendOnce(Message m) {
        try {
            fairLossLink.sendMessage(m);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startRetransmissionTask() {
        scheduler.scheduleAtFixedRate(() -> {
            if(!running) return;
            for (Message m : pendingMessages) {
                // Retransmit messages
                System.out.println("Retransmitting: " + new String(m.getPayload()));
                this.sendOnce(m);
            }
        }, 1000, 2000, TimeUnit.MILLISECONDS);
    }

    public void acknowledgeMessage(Message m) {
        pendingMessages.remove(m);
    }

    public Message receiveMessage() throws InterruptedException {
        return fairLossLink.receiveMessage();
    }

    public void close() {
        running = false;
        scheduler.shutdown();
        try {
            if(!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException ignored) {
        }
        fairLossLink.close();
    }
}
