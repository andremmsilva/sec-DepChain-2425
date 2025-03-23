package depchain.network.apl;

import depchain.config.DepChainConfig;
import depchain.crypto.CryptoUtils;
import depchain.network.Message;
import depchain.network.fll.FLLSendEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AuthenticatedPerfectLinkRobustnessTest {
    private DepChainConfig senderConfig;
    private AuthenticatedPerfectLink sender;
    private List<DepChainConfig> receiverConfigs;
    private List<AuthenticatedPerfectLink> receivers;

    @BeforeEach
    public void setUp() {
        // Create a sender and three receivers
        senderConfig = new DepChainConfig(0, 0, "127.0.0.1");
        int basePort = senderConfig.getBasePort();
        sender = new AuthenticatedPerfectLink(senderConfig);

        receiverConfigs = new ArrayList<>();
        receivers = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            DepChainConfig config = new DepChainConfig(i, basePort, "127.0.0.1");
            receiverConfigs.add(config);
            receivers.add(new AuthenticatedPerfectLink(config));
        }
    }

    @AfterEach
    public void tearDown() {
        sender.close();
        receivers.forEach(AuthenticatedPerfectLink::close);
    }

    /**
     * Test that a message sent concurrently to multiple receivers is delivered to each one,
     * and that each receiver verifies the MAC correctly.
     */
    @Test
    @Timeout(10)
    public void testConcurrentSends() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(receivers.size());
        List<Message> receivedMessages = new ArrayList<>();

        // Each receiver registers a deliver event consumer.
        for (DepChainConfig config : receiverConfigs) {
            config.eventBus.register(APLDeliverEvent.class, event -> {
                Message m = event.arg;
                // Verify that the MAC verifies using the sender's shared key.
                if (senderConfig.secretKeys.get(config.selfId) != null) {
                    boolean valid = CryptoUtils.verifyMAC(
                            m.getPayload(),
                            m.getMac(),
                            senderConfig.secretKeys.get(config.selfId));
                    if (valid) {
                        synchronized (receivedMessages) {
                            receivedMessages.add(m);
                        }
                    }
                }
                latch.countDown();
            });
        }

        // Publish the same message to all receivers.
        Message m = new Message("Concurrent Test", senderConfig.selfId);
        // IMPORTANT: create a fresh copy per recipient (see discussion about MAC overwrites)
        for (DepChainConfig rConfig : receiverConfigs) {
            // cloning the message (or creating a new one with the same content)
            Message copy = new Message(m);
            senderConfig.eventBus.publish(new APLSendEvent(copy, rConfig.selfId));
        }
        latch.await();

        assertEquals(receivers.size(), receivedMessages.size(), "All receivers should deliver the message");
        for (Message rm : receivedMessages) {
            // Check that payload is intact
            assertEquals("Concurrent Test", rm.getPayload());
        }
    }

    /**
     * Test that if one receiver receives a message with an incorrect MAC (simulated by tampering),
     * then its deliver event is not triggered (or flagged invalid).
     */
    @Test
    @Timeout(10)
    public void testTamperedMAC() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final List<Message> validMessages = new ArrayList<>();

        // We register on one receiver.
        DepChainConfig targetConfig = receiverConfigs.getFirst();
        targetConfig.eventBus.register(APLDeliverEvent.class, event -> {
            Message m = event.arg;
            validMessages.add(m);
            latch.countDown();
        });

        // Create a message and tamper with the MAC.
        Message m = new Message("Tamper Test", senderConfig.selfId);
        m.setMac("INVALID MAC".getBytes());

        // Publish via FLL to avoid the APL resetting the MAC.
        senderConfig.eventBus.publish(new FLLSendEvent(m, targetConfig.selfId));

        Thread.sleep(500); // Wait for the handshake and send process.

        // In our implementation, a message with an incorrect MAC should be logged as invalid.
        // Therefore, validMessages should remain empty.
        assertTrue(validMessages.isEmpty(), "Tampered message should not be delivered as valid");
    }

    @Test
    @Timeout(10)
    public void testByzantineNodeSendsTamperedPayload() throws InterruptedException {
        // Byzantine node (ID 0) sends a valid message, then tampers with it mid-flight
        CountDownLatch latch = new CountDownLatch(1);
        List<Message> validMessages = new ArrayList<>();

        // Receiver (non-Byzantine node) listens for messages
        receiverConfigs.getFirst().eventBus.register(APLDeliverEvent.class, e -> {
            Message m = e.arg;
            if (CryptoUtils.verifyMAC(m.getPayload(), m.getMac(), receiverConfigs.getFirst().secretKeys.get(0))) {
                validMessages.add(m);
            }
            latch.countDown();
        });

        // Byzantine node 0 sends a valid message
        Message original = new Message("ValidData", 0);
        senderConfig.eventBus.publish(new APLSendEvent(original, 1)); // Valid send

        while(senderConfig.secretKeys.get(1) == null) {
            // Wait for HS
            Thread.sleep(50);
        }

        // Tamper with the message (simulate network interception)
        Message tampered = new Message(original);
        // Here, tampered will have original's MAC - computed in the APL after the initial handshake
        tampered.setPayload("MaliciousData");
        // Setting a new payload, and sending via FLL or SL will cause the MAC not to be recomputed,
        // making the message "tampered" invalid in node 1's vision
        senderConfig.eventBus.publish(new FLLSendEvent(tampered, 1)); // Tampered send

        latch.await();

        // Only the valid message should be accepted
        assertEquals(1, validMessages.size());
        assertEquals("ValidData", validMessages.getFirst().getPayload());
    }
}
