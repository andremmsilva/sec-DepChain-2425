package depchain.consensus.cc;

import depchain.config.DepChainConfig;
import depchain.network.Message;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class ConditionalCollectTest {
    private static final List<DepChainConfig> configs = new ArrayList<>(DepChainConfig.PROCESSES);
    private static final List<ConditionalCollect> ccs = new ArrayList<>(DepChainConfig.PROCESSES);

    @BeforeAll
    public static void init() throws InterruptedException {
        Predicate<List<Message>> mockPredicate = msgs -> true;
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

    @AfterAll
    public static void cleanup() {
        ccs.forEach(ConditionalCollect::close);
    }

    @Timeout(10)
    @Test
    public void testConditionalCollect() throws InterruptedException {
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
}
