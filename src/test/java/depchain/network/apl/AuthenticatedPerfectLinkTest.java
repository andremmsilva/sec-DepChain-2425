package depchain.network.apl;

import depchain.config.DepChainConfig;
import depchain.crypto.CryptoUtils;
import depchain.network.Message;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

public class AuthenticatedPerfectLinkTest {
    private static DepChainConfig receiverConfig;
    private static AuthenticatedPerfectLink receiver;

    private static DepChainConfig senderConfig;
    private static AuthenticatedPerfectLink sender;

    private final ArrayList<Message> receivedMessages = new ArrayList<>();

    @BeforeAll
    public static void beforeAll() {
        senderConfig = new DepChainConfig(0);
        sender = new AuthenticatedPerfectLink(1, senderConfig);
        receiverConfig = new DepChainConfig(1);
        receiver = new AuthenticatedPerfectLink(0, receiverConfig);
    }

    @AfterAll
    public static void afterAll() {
        sender.close();
        receiver.close();
    }

    @BeforeEach
    public void beforeEach() {
        receivedMessages.clear();
    }

    @Test
    public void testHandshake() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        receiverConfig.eventBus.register(APLDeliverEvent.class, event -> {
            receivedMessages.add(event.arg);
            latch.countDown();
        });

        senderConfig.eventBus.publish(new APLSendEvent(new Message("Test", senderConfig.selfId)));
        latch.await();

        assertFalse(receivedMessages.isEmpty());
        assertEquals(1, receivedMessages.size());

        assertNotNull(receiverConfig.secretKeys.get(0));
        assertNotNull(senderConfig.secretKeys.get(1));

        assertTrue(CryptoUtils.verifyMAC(receivedMessages.getFirst().getPayload(),
                receivedMessages.getFirst().getMac(), senderConfig.secretKeys.get(1)));
        assertTrue(CryptoUtils.verifyMAC(receivedMessages.getFirst().getPayload(),
                receivedMessages.getFirst().getMac(), receiverConfig.secretKeys.get(0)));
    }
}
