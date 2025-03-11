package depchain.network.sl;

import depchain.config.DepChainConfig;
import depchain.network.Message;
import depchain.network.fll.FLLDeliverEvent;
import depchain.network.fll.FLLSendEvent;
import depchain.network.fll.FairLossLink;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class StubbornLink {
    private final DepChainConfig config;
    private final FairLossLink fairLossLink;
    private final Consumer<FLLDeliverEvent> fllDeliverHandler;
    private final Queue<Message> pendingMessages = new ConcurrentLinkedQueue<>();
    private final Consumer<SLSendEvent> sendHandler = e -> {
        try {
            this.sendMessage(e.arg);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    };
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private volatile boolean running = true;

    public StubbornLink(String destAddress, int destPort, DepChainConfig config) {
        this.config = config;
        this.fairLossLink = new FairLossLink(destAddress, destPort, config);
        this.fllDeliverHandler = e -> this.config.eventBus.publish(new SLDeliverEvent(e.arg));

        // Propagate message arrivals
        this.config.eventBus.register(FLLDeliverEvent.class, fllDeliverHandler);
        // Handle send requests
        this.config.eventBus.register(SLSendEvent.class, sendHandler);

        this.startRetransmissionTask();
    }

    public void sendMessage(Message message) {
        this.pendingMessages.add(message);
        this.sendOnce(message);
    }

    private void sendOnce(Message m) {
        this.config.eventBus.publish(new FLLSendEvent(m));
    }

    private void startRetransmissionTask() {
        // Retransmit every 2 seconds
        this.scheduler.scheduleAtFixedRate(() -> {
            if (!running) return;
            for (Message m : pendingMessages) {
                // Retransmit messages
                System.err.println("Retransmitting: " + m);
                this.sendOnce(m);
            }
        }, 1000, 2000, TimeUnit.MILLISECONDS);
    }

    public void acknowledgeMessage(Message m) {
        this.pendingMessages.remove(m);
    }

    public void close() {
        this.running = false;
        this.scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException ignored) {
        }
        this.config.eventBus.unregister(SLSendEvent.class, this.sendHandler);
        this.config.eventBus.unregister(FLLDeliverEvent.class, this.fllDeliverHandler);
        this.fairLossLink.close();
    }
}
