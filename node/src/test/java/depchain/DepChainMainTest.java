package depchain;

import depchain.config.DepChainConfig;
import depchain.network.message.Message;
import depchain.network.message.MessagePayload;
import depchain.network.message.MessageType;
import depchain.network.sl.SLDeliverEvent;
import depchain.network.sl.SLSendEvent;
import depchain.network.sl.StubbornLink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DepChainMainTest {
    private final ArrayList<DepChainMain> instances = new ArrayList<>();

    @BeforeEach
    void setUp() {
        instances.add(new DepChainMain(new DepChainConfig(0)));
        instances.add(new DepChainMain(new DepChainConfig(1)));
        instances.add(new DepChainMain(new DepChainConfig(2)));
        instances.add(new DepChainMain(new DepChainConfig(3)));
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        instances.forEach(DepChainMain::shutdown);
        Thread.sleep(200);
        instances.clear();
    }

    @Test
    @Timeout(10)
    public void testDepChain() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        DepChainConfig client = new DepChainConfig(-10);
        StubbornLink clientSl = new StubbornLink(client);
        Consumer<SLDeliverEvent> consumer = e -> {
            Message m = e.arg;
            if (m.getPayload().getType() == MessageType.APPEND_CONFIRMED) {
                System.out.println("Got append confirmed " + m.getPayload().getData());
                latch.countDown();
            }
            assertEquals("1####0", m.getPayload().getData(), String.format("Expected %s, got %s", "1####0",
                    m.getPayload().getData()));
        };

        client.eventBus.register(SLDeliverEvent.class, consumer);

        client.eventBus.publish(new SLSendEvent(new Message(new MessagePayload(MessageType.APPEND_REQ, "1"),
                client.selfId), DepChainConfig.LEADER));

        latch.await();
        client.eventBus.unregister(SLDeliverEvent.class, consumer);
        clientSl.close();
    }
}
