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
    private final Queue<SLSendEvent> pendingMessages = new ConcurrentLinkedQueue<>();
    private final Consumer<SLSendEvent> sendHandler = e -> {
        try {
            this.sendOnce(e.arg, e.getTargetId());
            this.pendingMessages.add(e);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    };
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private volatile boolean running = true;

    public StubbornLink(DepChainConfig config) {
        this.config = config;
        this.fairLossLink = new FairLossLink(config);
        this.fllDeliverHandler = e -> this.config.eventBus.publish(new SLDeliverEvent(e.arg));

        // Propagate message arrivals
        this.config.eventBus.register(FLLDeliverEvent.class, fllDeliverHandler);
        // Handle send requests
        this.config.eventBus.register(SLSendEvent.class, sendHandler);

        this.startRetransmissionTask();
    }

    private void sendOnce(Message m, int targetId) {
        this.config.eventBus.publish(new FLLSendEvent(m, targetId));
    }

    private void startRetransmissionTask() {
        // Retransmit every 2 seconds
        this.scheduler.scheduleAtFixedRate(() -> {
            if (!running) return;
            for (SLSendEvent e : pendingMessages) {
                // Retransmit messages
//                System.err.println("Retransmitting: " + e.);
                this.sendOnce(e.arg, e.getTargetId());
            }
        }, 1000, 2000, TimeUnit.MILLISECONDS);
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
