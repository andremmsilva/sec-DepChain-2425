package depchain.network.sl;

import depchain.config.DepChainConfig;
import depchain.network.fll.FLLDeliverEvent;
import depchain.network.fll.FLLSendEvent;
import depchain.network.fll.FairLossLink;
import depchain.network.message.Message;
import depchain.network.message.MessagePayload;
import depchain.network.message.MessageType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StubbornLinkTest {
    private static final String ADDRESS = "127.0.0.1";

    private static DepChainConfig receiverConfig;
    private static DepChainConfig senderConfig;
    private static DepChainConfig fllConfig;
    private static StubbornLink sender;
    private static StubbornLink receiver;
    private static FairLossLink fll;
    private static List<Message> receivedMessages;

    @BeforeAll
    static void setup() {
        senderConfig = new DepChainConfig(0, 0, ADDRESS);
        receiverConfig = new DepChainConfig(1, senderConfig.getBasePort(), ADDRESS);
        fllConfig = new DepChainConfig(2, senderConfig.getBasePort(), ADDRESS);
        sender = new StubbornLink(senderConfig);
        receiver = new StubbornLink(receiverConfig);
        fll = new FairLossLink(fllConfig);
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
        fll.close();
    }

    @AfterEach
    void afterEach() {
        receivedMessages.clear();
    }

    @Test
    public void testStubbornLinkEvents() throws InterruptedException {
        System.out.println("\ntestStubbornLinkEvents\n");
        CountDownLatch latch = new CountDownLatch(1);
        Consumer<SLDeliverEvent> deliverHandler = e -> {
            receivedMessages.add(e.arg);
            latch.countDown();
        };

        receiverConfig.eventBus.register(SLDeliverEvent.class, deliverHandler);

        Message message1 = new Message(new MessagePayload(null, "Message 1"), 0);
        senderConfig.eventBus.publish(new SLSendEvent(message1, receiverConfig.selfId));

        latch.await(); // Wait for message reception

        assertEquals("Message 1", receivedMessages.getFirst().getPayload().getData(), "SL should never drop messages");

        receiverConfig.eventBus.unregister(SLDeliverEvent.class, deliverHandler);
    }

    @Test
    public void testStubbornLinkRetransmit() throws InterruptedException {
        System.out.println("\ntestStubbornLinkRetransmit\n");
        Consumer<FLLDeliverEvent> deliverHandler = e -> receivedMessages.add(e.arg);

        fllConfig.eventBus.register(FLLDeliverEvent.class, deliverHandler);

        Message message1 = new Message(new MessagePayload(null, "Message 1"), 0);
        senderConfig.eventBus.publish(new SLSendEvent(message1, fllConfig.selfId));

        Thread.sleep(3500);

        assertEquals("Message 1", receivedMessages.getFirst().getPayload().getData(), "SL should retransmit messages");
        assertEquals("Message 1", receivedMessages.get(1).getPayload().getData(), "SL should retransmit messages");

        receiverConfig.eventBus.unregister(FLLDeliverEvent.class, deliverHandler);
    }

    @Test
    public void testStubbornLinkStopsRetransmitOnAck() throws InterruptedException {
        System.out.println("\ntestStubbornLinkStopsRetransmitOnAck\n");
        Consumer<FLLDeliverEvent> deliverHandler = e -> {
            receivedMessages.add(e.arg);
            // Create and send an acknowledgment message
            MessagePayload ackPayload = new MessagePayload(MessageType.SL_ACK, Integer.toString(0));
            Message ackMessage = new Message(ackPayload, fllConfig.selfId);

            // Send ACK without reliability guarantees (no seq numbers or retransmission)
            fllConfig.eventBus.publish(new FLLSendEvent(ackMessage, senderConfig.selfId));
        };

        fllConfig.eventBus.register(FLLDeliverEvent.class, deliverHandler);

        Message message1 = new Message(new MessagePayload(null, "Message 1"), 0);
        senderConfig.eventBus.publish(new SLSendEvent(message1, fllConfig.selfId));

        // Sleep for long enough to trigger a retransmit
        Thread.sleep(3000);

        assertEquals(1, receivedMessages.size(), "SL should stop retransmitting after ACK");
        assertEquals("Message 1", receivedMessages.getFirst().getPayload().getData());

        receiverConfig.eventBus.unregister(FLLDeliverEvent.class, deliverHandler);
    }
}
