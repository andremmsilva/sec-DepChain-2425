package depchain.network.apl;

import depchain.config.DepChainConfig;
import depchain.crypto.CryptoUtils;
import depchain.network.Message;
import depchain.network.fll.FLLSendEvent;
import depchain.network.sl.SLDeliverEvent;
import depchain.network.sl.SLSendEvent;
import depchain.network.sl.StubbornLink;

import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class AuthenticatedPerfectLink {
    private static final String HANDSHAKE_PREFIX = "HS";
    private static final String PAYLOAD_SEPARATOR = "###";

    private final StubbornLink stubbornLink;
    // Maintains per-peer connection state (handshake status + pending messages)
    private final ConcurrentHashMap<Integer, PeerConnection> peerConnections = new ConcurrentHashMap<>();
    // Used to detect duplicate messages if needed
    private final Set<Message> receivedMessages = Collections.synchronizedSet(new HashSet<>());
    private DepChainConfig config;
    // Handler for outgoing messages
    private final Consumer<APLSendEvent> sendHandler = e -> {
        try {
            sendMessage(e.arg, e.getTargetId());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    };

    // Handler for messages delivered from the lower-level link
    private final Consumer<SLDeliverEvent> slDeliverHandler = e -> {
        Message m = e.arg;
        int senderId = m.getSenderId();
        PeerConnection conn = peerConnections.computeIfAbsent(senderId, PeerConnection::new);

        conn.lock.lock();
        try {
            // Duplicate detection (if needed)
            if (receivedMessages.contains(m)) {
                return;
            }
            receivedMessages.add(m);

            if (conn.sharedKey == null) {
                // Handshake not complete yet.
                if (m.getPayload().startsWith("OK")) {
                    // This is a handshake reply.
                    conn.sharedKey = conn.generatedKey; // complete our handshake
                    config.secretKeys.put(senderId, conn.sharedKey);
                    conn.handshakeComplete.signalAll();
                    flushPendingMessages(conn);
                    return;
                } else if (m.getPayload().startsWith(HANDSHAKE_PREFIX + PAYLOAD_SEPARATOR)) {
                    // Peer is initiating a handshake.
                    String[] parts = m.getPayload().split(PAYLOAD_SEPARATOR, 2);
                    if (parts.length < 2) return; // invalid handshake message
                    String encodedPayload = parts[1];
                    byte[] decoded = CryptoUtils.decodeFromBase64(encodedPayload);
                    byte[] keyBytes = CryptoUtils.decipherMessage(decoded, config.getSelfPrivateKey());
                    // Verify signature (no MAC on handshake)
                    if (CryptoUtils.verifySignature(encodedPayload, m.getSignature(),
                            config.getPublicKey(senderId))) {
                        SecretKey sharedKey = CryptoUtils.secretKeyFromBytes(keyBytes);
                        conn.sharedKey = sharedKey;
                        config.secretKeys.put(senderId, sharedKey);
                        conn.handshakeComplete.signalAll();
                        // Reply with an OK message, authenticated with the new shared key.
                        Message reply = new Message("OK", config.selfId);
                        reply.setMac(CryptoUtils.computeMAC(reply.getPayload(), sharedKey));
                        config.eventBus.publish(new FLLSendEvent(reply, senderId));
                        flushPendingMessages(conn);
                    }
                    return;
                } else {
                    // Not a handshake message—queue it until the handshake is complete.
                    conn.pendingMessages.offer(m);
                    return;
                }
            }

            // Handshake is complete; verify MAC before delivery.
            if (!CryptoUtils.verifyMAC(m.getPayload(), m.getMac(), conn.sharedKey)) {
                System.out.println(this + " received invalid message " + m.getPayload() + " from " + senderId);
                return; // drop invalid message
            }
            config.eventBus.publish(new APLDeliverEvent(m));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new RuntimeException(ex);
        } finally {
            conn.lock.unlock();
        }
    };

    public AuthenticatedPerfectLink(DepChainConfig config) {
        this.config = config;
        this.stubbornLink = new StubbornLink(config);

        // Register event handlers
        this.config.eventBus.register(APLSendEvent.class, sendHandler);
        this.config.eventBus.register(SLDeliverEvent.class, slDeliverHandler);
    }

    /**
     * Sends a message to a given target. If a handshake is not yet complete with the target,
     * the message is queued and a handshake is initiated.
     */
    public void sendMessage(Message msg, int targetId) {
        PeerConnection conn = peerConnections.computeIfAbsent(targetId, PeerConnection::new);

        conn.lock.lock();
        try {
            // Enqueue the message to ensure ordering.
            conn.pendingMessages.offer(msg);

            if (conn.sharedKey == null) {
                if (config.selfId <= targetId && !conn.handshakeStarted) {
                    conn.handshakeStarted = true;
                    // Generate a shared key and initiate handshake.
                    SecretKey key = CryptoUtils.generateSharedKey();
                    conn.generatedKey = key;
                    byte[] cipherText = CryptoUtils.cipherMessage(key.getEncoded(), config.getPublicKey(targetId));
                    String encoded = CryptoUtils.encodeToBase64(cipherText);
                    Message handshake = new Message(encoded, config.selfId);
                    handshake.setPayload(HANDSHAKE_PREFIX + PAYLOAD_SEPARATOR + encoded);
                    handshake.setSignature(CryptoUtils.signMessage(encoded, config.getSelfPrivateKey()));
                    if (targetId == config.getSelfId()) {
                        conn.sharedKey = key;
                    } else {
                        config.eventBus.publish(new FLLSendEvent(handshake, targetId));
                    }
                }
                // Wait for handshake completion.
                while (conn.sharedKey == null) {
                    conn.handshakeComplete.await();
                }
            }
            // Once handshake is complete, flush all queued messages.
            flushPendingMessages(conn);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            conn.lock.unlock();
        }
    }

    /**
     * Flushes the pending messages of a peer connection, computing the MAC for each
     * and sending it over the network.
     */
    private void flushPendingMessages(PeerConnection conn) {
        while (!conn.pendingMessages.isEmpty()) {
            Message queued = conn.pendingMessages.poll();
            if (queued != null) {
                byte[] mac = CryptoUtils.computeMAC(queued.getPayload(), conn.sharedKey);
                queued.setMac(mac);
                config.eventBus.publish(new SLSendEvent(queued, conn.peerId));
            }
        }
    }

    public void close() {
        this.stubbornLink.close();
        this.config.eventBus.unregister(APLSendEvent.class, sendHandler);
        this.config.eventBus.unregister(SLDeliverEvent.class, slDeliverHandler);
    }

    @Override
    public String toString() {
        return "AuthenticatedPerfectLink{" + "id=" + config.selfId + '}';
    }

    /**
     * Holds per-peer state for the handshake and pending messages.
     */
    private static class PeerConnection {
        final int peerId;
        final Queue<Message> pendingMessages = new LinkedList<>();
        final Lock lock = new ReentrantLock();
        final Condition handshakeComplete = lock.newCondition();
        SecretKey sharedKey = null; // becomes non-null once handshake completes
        boolean handshakeStarted = false;
        SecretKey generatedKey = null; // the key we generated when initiating handshake

        PeerConnection(int peerId) {
            this.peerId = peerId;
        }
    }
}
