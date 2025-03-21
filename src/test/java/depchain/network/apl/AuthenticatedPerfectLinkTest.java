package depchain.network.apl;

import depchain.config.DepChainConfig;
import depchain.crypto.CryptoUtils;
import depchain.network.Message;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

public class AuthenticatedPerfectLinkTest {
    private static DepChainConfig receiverConfig;
    private static AuthenticatedPerfectLink receiver;

    private static DepChainConfig senderConfig;
    private static AuthenticatedPerfectLink sender;

    private final ArrayList<Message> receivedMessages = new ArrayList<>();

    @BeforeAll
    public static void beforeAll() {
    }

    @AfterAll
    public static void afterAll() {
    }

    @BeforeEach
    public void beforeEach() {
        senderConfig = new DepChainConfig(0);
        sender = new AuthenticatedPerfectLink(senderConfig);
        receiverConfig = new DepChainConfig(1);
        receiver = new AuthenticatedPerfectLink(receiverConfig);
        receivedMessages.clear();
    }

    @AfterEach
    public void afterEach() {

        sender.close();
        receiver.close();
    }

    @Test
    public void testHandshake() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Consumer<APLDeliverEvent> consumer = event -> {
            receivedMessages.add(event.arg);
            latch.countDown();
        };
        receiverConfig.eventBus.register(APLDeliverEvent.class, consumer);

        senderConfig.eventBus.publish(new APLSendEvent(new Message("Test", senderConfig.selfId),
                receiverConfig.selfId));
        latch.await();

        assertFalse(receivedMessages.isEmpty());
        assertEquals(1, receivedMessages.size());

        assertNotNull(receiverConfig.secretKeys.get(0));
        assertNotNull(senderConfig.secretKeys.get(1));

        assertTrue(CryptoUtils.verifyMAC(receivedMessages.getFirst().getPayload(),
                receivedMessages.getFirst().getMac(), senderConfig.secretKeys.get(1)));
        assertTrue(CryptoUtils.verifyMAC(receivedMessages.getFirst().getPayload(),
                receivedMessages.getFirst().getMac(), receiverConfig.secretKeys.get(0)));

        receiverConfig.eventBus.unregister(APLDeliverEvent.class, consumer);
    }

    @Test
    public void testMultiple() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        DepChainConfig thirdPartyConfig = new DepChainConfig(2);
        List<Message> thirdPartyReceivedMsgs = new ArrayList<>();
        AuthenticatedPerfectLink thirdParty = new AuthenticatedPerfectLink(thirdPartyConfig);
        Consumer<APLDeliverEvent> thirdPartyConsumer = event -> {
            thirdPartyReceivedMsgs.add(event.arg);
            latch.countDown();
        };
        Consumer<APLDeliverEvent> receiverConsumer = event -> {
            receivedMessages.add(event.arg);
            latch.countDown();
        };
        receiverConfig.eventBus.register(APLDeliverEvent.class, receiverConsumer);
        thirdPartyConfig.eventBus.register(APLDeliverEvent.class, thirdPartyConsumer);

        Thread.sleep(500);

        senderConfig.eventBus.publish(new APLSendEvent(new Message("Test", senderConfig.selfId),
                receiverConfig.selfId));
        senderConfig.eventBus.publish(new APLSendEvent(new Message("Test", senderConfig.selfId),
                thirdPartyConfig.selfId));
        latch.await();

        assertFalse(receivedMessages.isEmpty());
        assertFalse(thirdPartyReceivedMsgs.isEmpty());
        assertEquals(1, receivedMessages.size());
        assertEquals(1, thirdPartyReceivedMsgs.size());

        assertNotNull(senderConfig.secretKeys.get(1));
        assertNotNull(receiverConfig.secretKeys.get(0));
        assertNotNull(thirdPartyConfig.secretKeys.get(0));

        assertTrue(CryptoUtils.verifyMAC(receivedMessages.getFirst().getPayload(),
                receivedMessages.getFirst().getMac(), senderConfig.secretKeys.get(1)));
        assertTrue(CryptoUtils.verifyMAC(receivedMessages.getFirst().getPayload(),
                receivedMessages.getFirst().getMac(), receiverConfig.secretKeys.get(0)));
        assertTrue(CryptoUtils.verifyMAC(thirdPartyReceivedMsgs.getFirst().getPayload(),
                thirdPartyReceivedMsgs.getFirst().getMac(), thirdPartyConfig.secretKeys.get(0)));

        receiverConfig.eventBus.unregister(APLDeliverEvent.class, receiverConsumer);
        thirdPartyConfig.eventBus.unregister(APLDeliverEvent.class, thirdPartyConsumer);
    }
}
