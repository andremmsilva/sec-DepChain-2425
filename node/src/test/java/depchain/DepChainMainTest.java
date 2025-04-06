package depchain;

import com.google.gson.Gson;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DepChainMainTest {
    private final ArrayList<DepChainMain> instances = new ArrayList<>();
    private final List<DepChainConfig> configs = new ArrayList<>();

    @BeforeEach
    void setUp() throws InterruptedException {
        configs.clear();
        instances.clear();
        configs.add(new DepChainConfig(0, 0, "127.0.0.1"));
        configs.add(new DepChainConfig(1, configs.getFirst().getBasePort(), "127.0.0.1"));
        configs.add(new DepChainConfig(2, configs.getFirst().getBasePort(), "127.0.0.1"));
        configs.add(new DepChainConfig(3, configs.getFirst().getBasePort(), "127.0.0.1"));
        instances.add(new DepChainMain(configs.get(0)));
        instances.add(new DepChainMain(configs.get(1)));
        instances.add(new DepChainMain(configs.get(2)));
        instances.add(new DepChainMain(configs.get(3)));
        Thread.sleep(300);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        instances.forEach(DepChainMain::shutdown);
        Thread.sleep(200);
    }

    @Test
    @Timeout(10)
    public void testDepChain() throws InterruptedException {
        System.out.println("\ntestDepChain\n");
        CountDownLatch latch = new CountDownLatch(DepChainConfig.PROCESSES - DepChainConfig.FAIL_BOUND);
        DepChainConfig client = new DepChainConfig(10, configs.getFirst().getBasePort(), "127.0.0.1");
        StubbornLink clientSl = new StubbornLink(client);
        Consumer<SLDeliverEvent> consumer = e -> {
            Message m = e.arg;
            if (m.getPayload().getType() == MessageType.TRANSACTION_CONFIRMED) {
                System.out.println("Got append confirmed " + m.getPayload().getData());
                latch.countDown();
            }
            assertEquals("1####0", m.getPayload().getData(), String.format("Expected %s, got %s", "1####0",
                    m.getPayload().getData()));
        };

        client.eventBus.register(SLDeliverEvent.class, consumer);

        for (int i = 0; i < DepChainConfig.PROCESSES; i++) {
            client.eventBus.publish(new SLSendEvent(new Message(new MessagePayload(MessageType.APPEND_REQ, "1"),
                    client.selfId), i));
        }

        latch.await();
        client.eventBus.unregister(SLDeliverEvent.class, consumer);
        clientSl.close();
    }

    @Test
    @Timeout(20)
    public void testDepChainTwiceSequential() throws InterruptedException {
        System.out.println("\ntestDepChainTwiceSequential\n");
        // Expecting two confirmations
        CountDownLatch latch = new CountDownLatch(2 * (DepChainConfig.PROCESSES - DepChainConfig.FAIL_BOUND)); //
        DepChainConfig client = new DepChainConfig(10, configs.getFirst().getBasePort(), "127.0.0.1");
        StubbornLink clientSl = new StubbornLink(client);
        Consumer<SLDeliverEvent> consumer = e -> {
            Message m = e.arg;
            System.out.println("Got message from " + m.getSenderId() + " " + m.serialize(new Gson()));
            if (m.getPayload().getType() == MessageType.TRANSACTION_CONFIRMED) {
                latch.countDown();
                // Verify first and second messages
                // You might need to store received messages and assert separately
            }
        };

        client.eventBus.register(SLDeliverEvent.class, consumer);

        for (int i = 0; i < DepChainConfig.PROCESSES; i++) {
            client.eventBus.publish(new SLSendEvent(new Message(new MessagePayload(MessageType.APPEND_REQ, "1"),
                    client.selfId), i));
        }
        Thread.sleep(200);
        for (int i = 0; i < DepChainConfig.PROCESSES; i++) {
            client.eventBus.publish(new SLSendEvent(new Message(new MessagePayload(MessageType.APPEND_REQ, "2"),
                    client.selfId), i));
        }

        boolean completed = latch.await(20, TimeUnit.SECONDS);
        assertTrue(completed, "Latch timed out waiting for APPEND_CONFIRMED");

        client.eventBus.unregister(SLDeliverEvent.class, consumer);
        clientSl.close();
    }
}
