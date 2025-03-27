package depchain.network.sl;

import com.google.gson.Gson;
import depchain.config.DepChainConfig;
import depchain.network.fll.FLLDeliverEvent;
import depchain.network.fll.FLLSendEvent;
import depchain.network.fll.FairLossLink;
import depchain.network.message.Message;
import depchain.network.message.MessagePayload;
import depchain.network.message.MessageType;
import depchain.network.utils.PeerConnection;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class StubbornLink {
    private final DepChainConfig config;
    private final FairLossLink fairLossLink;
    private final Consumer<FLLDeliverEvent> fllDeliverHandler;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Consumer<SLSendEvent> sendHandler;
    private volatile boolean running = true;

    public StubbornLink(DepChainConfig config) {
        this.config = config;
        this.fairLossLink = new FairLossLink(config);
        this.fllDeliverHandler = getFLLDeliverHandler(config);
        this.sendHandler = getSLSendHandler();

        // Propagate message arrivals
        this.config.eventBus.register(FLLDeliverEvent.class, fllDeliverHandler);
        // Handle send requests
        this.config.eventBus.register(SLSendEvent.class, sendHandler);

        // Create a thread to retransmit unacknowledged messages
        this.startRetransmissionTask();
    }

    private Consumer<SLSendEvent> getSLSendHandler() {
        return e -> {
            try {
                sendWithReliability(e.arg, e.getTargetId());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        };
    }

    private Consumer<FLLDeliverEvent> getFLLDeliverHandler(DepChainConfig config) {
        return e -> {
            Message m = e.arg;

            // Obtain the connection or create a new one
            PeerConnection conn = config.peerConnections.computeIfAbsent(m.getSenderId(), PeerConnection::new);
            conn.lock.lock();
            try {
                // Process acknowledgments
                if (m.getPayload().getType() == MessageType.SL_ACK) {
                    int ackedSeqNum = Integer.parseInt(m.getPayload().getData());

                    // Update last acknowledged sequence number
                    if (ackedSeqNum > conn.lastAckedSeqNum) {
                        conn.lastAckedSeqNum = ackedSeqNum;

                        // Remove acknowledged messages from retransmission map
                        for (int seqNum = 0; seqNum <= ackedSeqNum; seqNum++) {
                            conn.unackedMessages.remove(seqNum);
                        }
                    }
                    return;
                }

                // Process regular messages
                int mSeqNum = m.getSequenceNumber();

                // Send ACK for this message (even if it's a duplicate or out of order)
                sendAck(m.getSenderId(), mSeqNum);

                if (mSeqNum < conn.expectedSeqNum) {
                    // Duplicate message, already delivered, ignore
                    System.out.println(this + " received duplicate message with seq " + mSeqNum + " from " + m.getSenderId());
                } else if (mSeqNum == conn.expectedSeqNum) {
                    // In-order message, deliver it immediately
                    conn.expectedSeqNum++;
                    System.out.println(this + " received " + m.serialize(new Gson()) +
                            " from peer " + m.getSenderId());
                    this.config.eventBus.publish(new SLDeliverEvent(m));

                    // Check if we can deliver any buffered future messages
                    deliverBufferedMessages(conn);
                } else {
                    // Out-of-order message, buffer it for later delivery
                    System.out.println(this + " buffering out-of-order message with seq " + mSeqNum);
                    conn.futureMessages.add(m);
                }
            } finally {
                conn.lock.unlock();
            }
        };
    }

    private void deliverBufferedMessages(PeerConnection conn) {
        // Try to deliver any buffered messages that are now in sequence
        while (!conn.futureMessages.isEmpty()) {
            Message nextMsg = conn.futureMessages.peek();
            int nextSeq = nextMsg.getSequenceNumber();

            if (nextSeq == conn.expectedSeqNum) {
                conn.futureMessages.poll(); // Remove from buffer
                conn.expectedSeqNum++;
                // Deliver the message
                System.out.println(this + " received " + nextMsg.serialize(new Gson()) +
                        " from peer " + nextMsg.getSenderId());
                this.config.eventBus.publish(new SLDeliverEvent(nextMsg));
            } else {
                // Gap in sequence, wait for missing messages
                break;
            }
        }
    }

    private void sendAck(int targetId, int seqNum) {
        // Create and send an acknowledgment message
        MessagePayload ackPayload = new MessagePayload(MessageType.SL_ACK, Integer.toString(seqNum));
        Message ackMessage = new Message(ackPayload, config.selfId);

        // Send ACK without reliability guarantees (no seq numbers or retransmission)
        sendOnce(ackMessage, targetId);
    }

    private void sendWithReliability(Message m, int targetId) {
        PeerConnection conn = config.peerConnections.computeIfAbsent(targetId, PeerConnection::new);
        conn.lock.lock();
        try {
            // Assign sequence number
            int seqNum = conn.lastSentSeqNum;
            m.setSequenceNumber(seqNum);
            conn.lastSentSeqNum++;

            // Store message for possible retransmission
            conn.unackedMessages.put(seqNum, m);

            // Send the message
            sendOnce(m, targetId);
        } finally {
            conn.lock.unlock();
        }
    }

    private void sendOnce(Message m, int targetId) {
        // Send without tracking for retransmission (used for ACKs)
        System.out.println(this + " sending " + m.serialize(new Gson()) +
                " to peer " + targetId);
        this.config.eventBus.publish(new FLLSendEvent(m, targetId));
    }

    private void startRetransmissionTask() {
        // Retransmit unacknowledged messages every 2 seconds
        this.scheduler.scheduleAtFixedRate(() -> {
            if (!running) return;

            // Retransmit all unacknowledged messages for all peers
            config.peerConnections.forEach((peerId, conn) -> {
                conn.lock.lock();
                try {
                    if (!conn.unackedMessages.isEmpty()) {

                        conn.unackedMessages.forEach((seqNum, msg) -> {
                            // Retransmit unacknowledged message
                            sendOnce(msg, peerId);
                        });
                    }
                } finally {
                    conn.lock.unlock();
                }
            });
        }, 2000, 2000, TimeUnit.MILLISECONDS);
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

    @Override
    public String toString() {
        return "StubbornLink{" + "id=" + config.selfId + '}';
    }
}
