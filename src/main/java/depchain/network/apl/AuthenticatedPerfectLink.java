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
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class AuthenticatedPerfectLink {
    private static final String HANDSHAKE_PREFIX = "HS";
    private static final String PAYLOAD_SEPARATOR = "###";
    private final StubbornLink stubbornLink;
    private final Set<Message> receivedMessages = Collections.synchronizedSet(new HashSet<>());
    private final Lock lock = new ReentrantLock();
    private final Condition handShaken = lock.newCondition();
    private DepChainConfig config;
    private final Consumer<APLSendEvent> sendHandler = e -> {
        try {
            this.sendMessage(e.arg, e.getTargetId());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    };
    private final Consumer<SLDeliverEvent> slDeliverHandler = e -> {
        Message m = e.arg;
        lock.lock();
        if (this.receivedMessages.contains(m)) {
//            System.out.println(this + " received duplicate " + m);
            lock.unlock();
            return;
        }
        this.receivedMessages.add(m);
        lock.unlock();

        // We don't have a key with the sender, so it's most likely an exchange request
        if (!this.config.secretKeys.containsKey(m.getSenderId())) {
            String payload = m.getPayload();
            if (payload.startsWith("OK")) {
                System.out.println(this + " received OK ");
                lock.lock();
                handShaken.signal();
                lock.unlock();
                return;
            } else if (payload.startsWith(HANDSHAKE_PREFIX + PAYLOAD_SEPARATOR)) {
//                System.out.println(this + " received handshake " + m);
                payload = m.getPayload().split(PAYLOAD_SEPARATOR)[1];
                try {
                    byte[] decoded = CryptoUtils.decodeFromBase64(payload);
                    byte[] payloadBytes = CryptoUtils.decipherMessage(decoded, this.config.getSelfPrivateKey());

                    // Verify signature, as no MAC is meant to be in this message
                    if (CryptoUtils.verifySignature(payload, m.getSignature(),
                            this.config.getPublicKey(m.getSenderId()))) {
                        SecretKey k = CryptoUtils.secretKeyFromBytes(payloadBytes);
                        this.lock.lock();
                        this.config.secretKeys.put(m.getSenderId(), k);
                        this.handShaken.signal();

                        // Reply with an OK, authenticated with the new shared secret
                        Message reply = new Message("OK", this.config.selfId);
                        reply.setMac(CryptoUtils.computeMAC(reply.getPayload(),
                                this.config.secretKeys.get(m.getSenderId())));
                        this.config.eventBus.publish(new FLLSendEvent(reply, m.getSenderId()));
                    }
                    return;
                } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
                    throw new RuntimeException(ex);
                } finally {
                    this.lock.unlock();
                }
            } else {
                // Wait until our ok is sent (maybe some message arrived before the handshake)
                System.out.println(this + " received message before handshake " + m);
//                System.out.println(this.config.secretKeys);
                try {
                    this.lock.lock();
                    while (!this.config.secretKeys.containsKey(m.getSenderId())) {
                        this.handShaken.await();
                    }
                    System.out.println(this + " completed handshake");
                } catch (InterruptedException ex) {
                    System.out.println(this + " interrupted while waiting for handshake request.");
                } finally {
                    this.lock.unlock();
                }

                // Now, proceed with the execution, as the MAC verification will most likely work now
            }
        }

        // Verify integrity and authenticity
        if (!CryptoUtils.verifyMAC(m.getPayload(), m.getMac(), this.config.secretKeys.get(m.getSenderId()))) {
            System.out.println("\n" + this + " received invalid message " + m.getPayload() + "\nMAC: " + CryptoUtils.encodeToBase64(m.getMac()) + "\n");
            return;
        }
        System.out.println("\n" + this + " received message " + m.getPayload() + "\nMAC: " + CryptoUtils.encodeToBase64(m.getMac()) + "\n");

        this.config.eventBus.publish(new APLDeliverEvent(m));
    };

    public AuthenticatedPerfectLink(DepChainConfig config) {
        this.config = config;
        this.stubbornLink = new StubbornLink(config);

        // Send Messages
        this.config.eventBus.register(APLSendEvent.class, sendHandler);

        // Listen for message arrivals
        this.config.eventBus.register(SLDeliverEvent.class, slDeliverHandler);
    }

    public void sendMessage(Message msg, int targetId) {
        Message m = new Message(msg);
        // First message exchange - share a key for MACs
        while (!this.config.secretKeys.containsKey(targetId)) {
            try {
                lock.lock();
                SecretKey k = CryptoUtils.generateSharedKey();
                byte[] cipherText = CryptoUtils.cipherMessage(k.getEncoded(), this.config.getPublicKey(targetId));
                Message handshake =
                        new Message(CryptoUtils.encodeToBase64(cipherText),
                                this.config.selfId);
                handshake.setSignature(CryptoUtils.signMessage(handshake.getPayload(),
                        this.config.getSelfPrivateKey()));
                handshake.setPayload(HANDSHAKE_PREFIX + PAYLOAD_SEPARATOR + handshake.getPayload());

                System.out.println(this + " is sending handshake to " + targetId);
                this.config.eventBus.publish(new FLLSendEvent(handshake, targetId));

                handShaken.await();
                System.out.println(this + " completed handshake with " + targetId);
                this.config.secretKeys.put(targetId, k);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                lock.unlock();
            }
        }

        // Already have a shared secret, let's compute the MAC
        byte[] mac = CryptoUtils.computeMAC(m.getPayload(), this.config.secretKeys.get(targetId));
        m.setMac(mac);

        // And send it over the network
        System.out.println("\n" + this + " is sending " + m.getPayload() + "\nto: " + targetId + "\nMAC: " + CryptoUtils.encodeToBase64(m.getMac()) + "\n");
        this.config.eventBus.publish(new SLSendEvent(m, targetId));
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
}
