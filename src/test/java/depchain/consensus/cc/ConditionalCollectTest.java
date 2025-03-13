package depchain.consensus.cc;

import depchain.config.DepChainConfig;
import depchain.network.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConditionalCollectTest {
    private final List<DepChainConfig> configs = new ArrayList<>(DepChainConfig.PROCESSES);
    private final List<ConditionalCollect> ccs = new ArrayList<>(DepChainConfig.PROCESSES);

    @BeforeEach
    public void beforeEach() throws InterruptedException {
        final Predicate<List<Message>> mockPredicate = msgs -> true;
        configs.clear();
        ccs.clear();
        configs.add(new DepChainConfig(0));
        configs.add(new DepChainConfig(1));
        configs.add(new DepChainConfig(2));
        configs.add(new DepChainConfig(3));
        ccs.add(new ConditionalCollect(configs.get(0), mockPredicate));
        ccs.add(new ConditionalCollect(configs.get(1), mockPredicate));
        ccs.add(new ConditionalCollect(configs.get(2), mockPredicate));
        ccs.add(new ConditionalCollect(configs.get(3), mockPredicate));
        Thread.sleep(500);
    }

    @AfterEach
    public void afterEach() {
        ccs.forEach(ConditionalCollect::close);
    }

    @Timeout(10)
    @Test
    public void testBaseLineConditionalCollect() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        List<Message> messages = new ArrayList<>();
        configs.get(1).eventBus.register(CCCollectedEvent.class, e -> {
            messages.addAll(e.arg);
            latch.countDown();
        });

        configs.get(1).eventBus.publish(new CCInputEvent(new Message("Whats 1!", 1)));
        configs.get(2).eventBus.publish(new CCInputEvent(new Message("Whats 2!", 2)));
        configs.get(3).eventBus.publish(new CCInputEvent(new Message("Whats 3!", 3)));
        latch.await();

        assertFalse(messages.isEmpty());
    }

    /**
     * Test that when every non-leader process sends a CC input message, the leader collects the messages
     * and then all processes eventually receive the same collected vector.
     */
    @Test
    @Timeout(10)
    public void testCollectsAllMessages() throws InterruptedException {
        // Here process 0 is the leader
        CountDownLatch latch = new CountDownLatch(1);
        final List<Message> collectedResult = new ArrayList<>();

        // Register a CCCollectedEvent consumer on one of the non-leader processes (say process 1)
        configs.get(1).eventBus.register(CCCollectedEvent.class, event -> {
            collectedResult.addAll(event.arg);
            latch.countDown();
        });

        // Have processes 1, 2, and 3 send their CC input messages.
        configs.get(1).eventBus.publish(new CCInputEvent(new Message("Msg from 1", 1)));
        configs.get(2).eventBus.publish(new CCInputEvent(new Message("Msg from 2", 2)));
        configs.get(3).eventBus.publish(new CCInputEvent(new Message("Msg from 3", 3)));

        // The leader (process 0) should collect at least 3 messages (assuming DepChainConfig.FAIL_BOUND = 1)
        latch.await();

        assertFalse(collectedResult.isEmpty(), "Collected result should not be empty");
        // Check that each message came from the appropriate sender
        List<Integer> senders = new ArrayList<>();
        for (Message m : collectedResult) {
            senders.add(m.getSenderId());
        }
        // Since leader is 0 and may not have sent an input,
        // we expect messages from processes 1, 2, and 3.
        assertTrue(senders.contains(1));
        assertTrue(senders.contains(2));
        assertTrue(senders.contains(3));
    }
}
