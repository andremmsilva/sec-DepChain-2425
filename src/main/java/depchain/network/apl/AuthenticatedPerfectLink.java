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
    private final int targetId;
    private final StubbornLink stubbornLink;
    private final Set<Message> receivedMessages = Collections.synchronizedSet(new HashSet<>());
    private DepChainConfig config;
    private final Lock lock = new ReentrantLock();
    private final Condition handShaken = lock.newCondition();
    private final Consumer<APLSendEvent> sendHandler = e -> {
        try {
            this.sendMessage(e.arg);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    };
    private final Consumer<SLDeliverEvent> slDeliverHandler = e -> {
        Message m = e.arg;
        System.out.println("APL slDeliverHandler with Id " + this.config.selfId + " received " + m);
        if (this.receivedMessages.contains(m)) {
            return;
        }

        // We don't have a key with the sender, so it's most likely an exchange request
        if (!this.config.secretKeys.containsKey(m.getSenderId())) {
            String payload = m.getPayload();
            if (payload.equals("OK")) {
                lock.lock();
                handShaken.signal();
                lock.unlock();
                return;
            }

            byte[] payloadBytes = CryptoUtils.decipherMessage(CryptoUtils.decodeFromBase64(payload),
                    this.config.getSelfPrivateKey());

            // Verify signature, as no MAC is meant to be in this message
            if (CryptoUtils.verifySignature(payload, m.getSignature(), this.config.getPublicKey(m.getSenderId()))) {
                try {
                    SecretKey k = CryptoUtils.secretKeyFromBytes(payloadBytes);
                    this.config.secretKeys.put(m.getSenderId(), k);
                } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
                    throw new RuntimeException(ex);
                }

                this.receivedMessages.add(m);

                // Reply with an OK, authenticated with the new shared secret
                Message reply = new Message("OK", this.config.selfId);
                reply.setMac(CryptoUtils.computeMAC(reply.getPayload(), this.config.secretKeys.get(m.getSenderId())));
                this.config.eventBus.publish(new FLLSendEvent(reply));
            }
            return;
        }

        // Verify integrity and authenticity
        if (!CryptoUtils.verifyMAC(m.getPayload(), m.getMac(), this.config.secretKeys.get(m.getSenderId()))) {
            return;
        }

        this.receivedMessages.add(m);
        this.config.eventBus.publish(new APLDeliverEvent(m));
    };

    public AuthenticatedPerfectLink(int targetId, DepChainConfig config) {
        String destAddress = DepChainConfig.ADDRESS;
        int destPort = DepChainConfig.BASE_PORT + targetId;
        this.stubbornLink = new StubbornLink(destAddress, destPort, config);
        this.config = config;
        this.targetId = targetId;

        // Send Messages
        this.config.eventBus.register(APLSendEvent.class, sendHandler);

        // Listen for message arrivals
        this.config.eventBus.register(SLDeliverEvent.class, slDeliverHandler);
    }

    public void sendMessage(Message m) {
        System.out.println("APL with ID " + this.config.selfId + " sendMessage " + m);
        // First message exchange - share a key for MACs
        while (!this.config.secretKeys.containsKey(this.targetId)) {
            try {
                lock.lock();
                SecretKey k = CryptoUtils.generateSharedKey();
                byte[] cipherText = CryptoUtils.cipherMessage(k.getEncoded(),
                        this.config.getPublicKey(this.targetId));
                Message handshake = new Message(CryptoUtils.encodeToBase64(cipherText), this.config.selfId);
                handshake.setSignature(CryptoUtils.signMessage(handshake.getPayload(),
                        this.config.getSelfPrivateKey()));
                this.config.eventBus.publish(new FLLSendEvent(handshake));
                handShaken.await();
                this.config.secretKeys.put(this.targetId, k);
                lock.unlock();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        // Already have a shared secret, let's compute the MAC
        byte[] mac = CryptoUtils.computeMAC(m.getPayload(), this.config.secretKeys.get(this.targetId));
        m.setMac(mac);

        // And send it over the network
        this.config.eventBus.publish(new SLSendEvent(m));
    }

    public void close() {
        this.stubbornLink.close();
        this.config.eventBus.unregister(APLSendEvent.class, sendHandler);
        this.config.eventBus.unregister(SLDeliverEvent.class, slDeliverHandler);
    }
}
