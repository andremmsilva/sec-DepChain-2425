package depchain.network.apl;

import com.google.gson.Gson;
import depchain.config.DepChainConfig;
import depchain.crypto.CryptoUtils;
import depchain.network.fll.FLLSendEvent;
import depchain.network.message.Message;
import depchain.network.message.MessagePayload;
import depchain.network.message.MessageType;
import depchain.network.sl.SLDeliverEvent;
import depchain.network.sl.SLSendEvent;
import depchain.network.sl.StubbornLink;

import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class AuthenticatedPerfectLink {
    private final StubbornLink stubbornLink;
    // Maintains per-peer connection state (handshake status + pending messages)
    private final ConcurrentHashMap<Integer, PeerConnection> peerConnections = new ConcurrentHashMap<>();
    // Used to detect duplicate messages
    private final Set<Message> receivedMessages = Collections.synchronizedSet(new HashSet<>());
    private DepChainConfig config;
    // Handler for send events (send the message when asked to)
    private final Consumer<APLSendEvent> sendHandler = e -> {
        try {
            this.sendMessage(e.arg, e.getTargetId());
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
            // We have a key with this peer,
            if (conn.sharedKey != null) {
                // Duplicate detection
                if (receivedMessages.contains(m)) {
                    System.out.println(this + " received duplicate " + m.getPayload().serialize(new Gson()));
                    return;
                }
                receivedMessages.add(m);
                if (!CryptoUtils.verifyMAC(m.getPayload().serialize(new Gson()), m.getMac(), conn.sharedKey)) {
                    System.out.println(this + " received invalid message " + m.getPayload().serialize(new Gson()) +
                            " " + "from " + senderId);
                } else {
                    System.out.println(this + " received " + m.serialize(new Gson()));
                    config.eventBus.publish(new APLDeliverEvent(m));
                }

                return;
            }

            // Handshake not complete yet.
            switch (m.getPayload().getType()) {
                case SYN -> {
                    // obtain, decipher and decode key
                    String encodedPayload = m.getPayload().getData();
                    if (encodedPayload == null || encodedPayload.isEmpty()) {
                        System.err.println(this + " got an empty HS payload from " + m.getSenderId());
                        return;
                    }
                    byte[] decoded = CryptoUtils.decodeFromBase64(encodedPayload);
                    byte[] keyBytes = CryptoUtils.decipherMessage(decoded, config.getSelfPrivateKey());

                    // Verify signature (no MAC on handshake)
                    if (CryptoUtils.verifySignature(m.getPayload().serialize(new Gson()), m.getSignature(),
                            config.getPublicKey(senderId))) {
                        SecretKey sharedKey = CryptoUtils.secretKeyFromBytes(keyBytes);

                        System.out.println(this + " received SYN " + encodedPayload + " \nfrom " + m.getSenderId());
                        // Reply with an SYNACK message, authenticated with the new shared key.
                        Message reply = new Message(new MessagePayload(MessageType.SYNACK, ""), config.selfId);
                        reply.setMac(CryptoUtils.computeMAC(reply.getPayload().serialize(new Gson()), sharedKey));
                        config.eventBus.publish(new FLLSendEvent(reply, senderId));

                        // Wait for the ACK message at most 5 secs
                        if (!conn.handshakeComplete.await(5, TimeUnit.SECONDS)) {
                            return;
                        }

                        // Ack arrived, confirm handshake
                        System.out.println(this + " received ACK");
                        conn.sharedKey = sharedKey;
                        config.secretKeys.put(senderId, sharedKey);

                        // Treat all incoming messages now
                        flushIncomingMessages(conn);
                    }
                }
                case SYNACK -> {
                    System.out.println(this + " received SYNACK " + m.getPayload().serialize(new Gson()) + " \nfrom " + m.getSenderId());
                    conn.sharedKey = conn.generatedKey; // complete our handshake
                    config.secretKeys.put(senderId, conn.sharedKey);
                    conn.handshakeComplete.signalAll();

                    // Send an ACK with MAC
                    Message ack = new Message(new MessagePayload(MessageType.ACK, ""), config.selfId);
                    ack.setMac(CryptoUtils.computeMAC(ack.serialize(new Gson()), conn.sharedKey));
                    config.eventBus.publish(new FLLSendEvent(ack, conn.peerId));

                    // Send all pending outgoing messages to this peer
                    flushOutgoingMessages(conn);
                }
                // Signal the threads waiting in the SYN case
                case ACK -> {
                    conn.handshakeComplete.signalAll();
                }
                // Not a handshake message - queue it until the handshake is complete.
                default -> {
                    System.out.println(this + " received message before HS " + m.serialize(new Gson()));
                    conn.incomingMessages.offer(m);
                }
            }
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new RuntimeException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            System.err.println(this + " slDeliverHandler interrupted");
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
            conn.outgoingMessages.offer(msg);

            // No shared key with this peer
            if (conn.sharedKey == null) {
                if (config.selfId <= targetId && conn.generatedKey == null) {
                    // Generate a shared key and initiate handshake.
                    SecretKey key = CryptoUtils.generateSharedKey();
                    conn.generatedKey = key;

                    // Cipher and encode the generated key to b64
                    byte[] cipherText = CryptoUtils.cipherMessage(key.getEncoded(), config.getPublicKey(targetId));
                    String encoded = CryptoUtils.encodeToBase64(cipherText);

                    Message handshake = new Message(new MessagePayload(MessageType.SYN, encoded), config.selfId);
                    // Sign the message
                    handshake.setSignature(CryptoUtils.signMessage(handshake.getPayload().serialize(new Gson()),
                            config.getSelfPrivateKey()));

                    if (targetId == config.getSelfId()) {
                        // If we are "shaking hands with ourselves", just set the key
                        conn.sharedKey = key;
                    } else {
                        // send the HS over the network
                        System.out.println(this + " sending HS " + handshake.getPayload().serialize(new Gson()) + " " +
                                "\nto " + targetId);
                        config.eventBus.publish(new FLLSendEvent(handshake, targetId));
                    }
                }

                // Wait for handshake completion.
                while (conn.sharedKey == null) {
                    conn.handshakeComplete.await();
                }
            }

            // Once handshake is complete, flush all queued messages.
            flushOutgoingMessages(conn);
        } catch (InterruptedException e) {
            System.err.println(this + " sendMessage interrupted");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            conn.lock.unlock();
        }
    }

    /**
     * Flushes the pending outgoing messages of a peer connection, computing the MAC for each
     * and sending it over the network.
     */
    private void flushOutgoingMessages(PeerConnection conn) {
        while (!conn.outgoingMessages.isEmpty()) {
            Message queued = conn.outgoingMessages.poll();
            if (queued != null) {
                queued.setSenderId(config.selfId);
                byte[] mac = CryptoUtils.computeMAC(queued.getPayload().serialize(new Gson()), conn.sharedKey);
                queued.setMac(mac);
//                System.out.println(this + " sending " + queued.serialize(new Gson()) + " to " + conn.peerId);
                config.eventBus.publish(new SLSendEvent(queued, conn.peerId));
            }
        }
    }

    /**
     * Flushes the pending incoming messages of a peer connection
     */
    private void flushIncomingMessages(PeerConnection conn) {
        while (!conn.incomingMessages.isEmpty()) {
            Message queued = conn.incomingMessages.poll();
            if (queued != null) {
//                System.out.println(this + " handling " + queued.serialize(new Gson()));
                config.eventBus.publish(new SLDeliverEvent(queued));
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
        final Queue<Message> incomingMessages = new LinkedList<>();
        final Queue<Message> outgoingMessages = new LinkedList<>();
        final Lock lock = new ReentrantLock();
        final Condition handshakeComplete = lock.newCondition();
        SecretKey sharedKey = null; // becomes non-null once handshake completes
        SecretKey generatedKey = null; // the key we generated when initiating handshake

        PeerConnection(int peerId) {
            this.peerId = peerId;
        }
    }
}
