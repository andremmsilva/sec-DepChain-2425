package depchain.events;

import depchain.config.DepChainConfig;
import depchain.network.fll.FairLossLink;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EventTest {
    private static final DepChainConfig config = new DepChainConfig(0);
    private static final EventBus eventBus = new EventBus(config);
    // Only here so that fll.close() can be called - and the socket can be reused for another test
    private static FairLossLink fll;

    @BeforeAll
    static void setup() {
        fll = new FairLossLink(config);
    }

    @AfterAll
    static void tearDown() {
        fll.close();
    }

    @Test
    @Timeout(5)
    public void testEventSubscribe() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final List<String> received = new ArrayList<>();
        eventBus.register(NOPEvent.class, event -> {
            received.add(event.arg);
            latch.countDown();
        });

        eventBus.publish(new NOPEvent("hello"));
        latch.await();

        assertEquals("hello", received.get(0));
    }

    private static class NOPEvent extends Event<String> {
        NOPEvent(String payload) {
            super(payload);
        }
    }
}
