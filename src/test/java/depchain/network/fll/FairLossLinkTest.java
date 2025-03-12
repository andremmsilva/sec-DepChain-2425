package depchain.network.fll;

import depchain.config.DepChainConfig;
import depchain.network.Message;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FairLossLinkTest {
    private static DepChainConfig receiverConfig;
    private static DepChainConfig senderConfig;
    private static FairLossLink sender;
    private static FairLossLink receiver;
    private static List<Message> receivedMessages;

    @BeforeAll
    static void setup() {
        senderConfig = new DepChainConfig(0);
        receiverConfig = new DepChainConfig(1);
        sender = new FairLossLink(senderConfig);
        receiver = new FairLossLink(receiverConfig);
        receivedMessages = new ArrayList<>();
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @AfterAll
    static void tearDown() {
        sender.close();
        receiver.close();
    }

    @AfterEach
    void afterEach() {
        receivedMessages.clear();
    }

    @Test
    public void testFairLossLinkMessaging() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Consumer<FLLDeliverEvent> deliverHandler = e -> {
            receivedMessages.add(e.arg);
            latch.countDown();
        };

        receiverConfig.eventBus.register(FLLDeliverEvent.class, deliverHandler);

        Message message1 = new Message("Message 1", 0);
        sender.sendMessage(message1, receiverConfig.selfId);

        latch.await(); // Wait for message reception

        assertTrue(receivedMessages.getFirst() == null ||
                receivedMessages.getFirst().getPayload().equals("Message 1"), "FLL should sometimes drop messages");

        receiverConfig.eventBus.unregister(FLLDeliverEvent.class, deliverHandler);
    }

    @Test
    public void testFairLossLinkSendEvent() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Consumer<FLLDeliverEvent> deliverHandler = e -> {
            receivedMessages.add(e.arg);
            latch.countDown();
        };
        receiverConfig.eventBus.register(FLLDeliverEvent.class, deliverHandler);

        senderConfig.eventBus.publish(new FLLSendEvent(new Message("SEND|TEST", senderConfig.selfId), receiverConfig.selfId));

        latch.await();

        assertTrue(receivedMessages.getFirst() == null ||
                receivedMessages.getFirst().getPayload().equals("SEND|TEST"), "Expected " + "SEND|TEST" + ", obtained" +
                " " + receivedMessages.getFirst());

        receiverConfig.eventBus.unregister(FLLDeliverEvent.class, deliverHandler);
    }

    @Test
    public void testFairLossLinkMessageDuplication() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        Consumer<FLLDeliverEvent> deliverHandler = e -> {
            receivedMessages.add(e.arg);
            latch.countDown();
        };
        receiverConfig.eventBus.register(FLLDeliverEvent.class, deliverHandler);

        Message message1 = new Message("Message 1", 0);
        sender.sendMessage(message1, receiverConfig.selfId);
        sender.sendMessage(message1, receiverConfig.selfId); // Send twice

        latch.await(); // Wait for 2 messages

        assertEquals(2, receivedMessages.size(), "FLL should not ignore duplicate messages");

        receiverConfig.eventBus.unregister(FLLDeliverEvent.class, deliverHandler);
    }

    @Test
    public void testFairLossLinkMessageReordering() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        final List<Message> receivedMessages = new ArrayList<>();
        Consumer<FLLDeliverEvent> deliverHandler = e -> {
            receivedMessages.add(e.arg);
            latch.countDown();
        };
        receiverConfig.eventBus.register(FLLDeliverEvent.class, deliverHandler);

        Message message1 = new Message("First", 0);
        Message message2 = new Message("Second", 0);

        sender.sendMessage(message1, receiverConfig.selfId);
        sender.sendMessage(message2, receiverConfig.selfId);

        latch.await(); // Wait for both messages

        if (receivedMessages.size() == 2) {
            assertTrue(Objects.equals(receivedMessages.get(0).getPayload(), "First") || Objects.equals(receivedMessages.get(1).getPayload(), "First"), "Messages may arrive out of order due to FLL properties");
        }

        receiverConfig.eventBus.unregister(FLLDeliverEvent.class, deliverHandler);
    }
}
