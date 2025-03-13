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

import static org.junit.jupiter.api.Assertions.*;

public class AuthenticatedPerfectLinkRobustnessTest {
    private DepChainConfig senderConfig;
    private AuthenticatedPerfectLink sender;
    private List<DepChainConfig> receiverConfigs;
    private List<AuthenticatedPerfectLink> receivers;

    @BeforeEach
    public void setUp() {
        // Create a sender and three receivers
        senderConfig = new DepChainConfig(0);
        sender = new AuthenticatedPerfectLink(senderConfig);

        receiverConfigs = new ArrayList<>();
        receivers = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            DepChainConfig config = new DepChainConfig(i);
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
        DepChainConfig targetConfig = receiverConfigs.get(0);
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
        System.out.println(validMessages);
        assertTrue(validMessages.isEmpty(), "Tampered message should not be delivered as valid");
    }
}
