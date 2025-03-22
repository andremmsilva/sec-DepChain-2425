package depchain.consensus.utils;

import depchain.config.DepChainConfig;
import depchain.network.Message;
import depchain.network.apl.APLDeliverEvent;
import depchain.network.apl.AuthenticatedPerfectLink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BroadcastTest {
    private final ArrayList<DepChainConfig> configs = new ArrayList<>(DepChainConfig.PROCESSES);
    private final ArrayList<AuthenticatedPerfectLink> links = new ArrayList<>(DepChainConfig.PROCESSES);

    @BeforeEach
    void setUp() {
        configs.clear();
        links.clear();
        configs.add(new DepChainConfig(0, 0, "127.0.0.1"));
        configs.add(new DepChainConfig(1, configs.getFirst().getBasePort(), "127.0.0.1"));
        configs.add(new DepChainConfig(2, configs.getFirst().getBasePort(), "127.0.0.1"));
        configs.add(new DepChainConfig(3, configs.getFirst().getBasePort(), "127.0.0.1"));
        links.add(new AuthenticatedPerfectLink(configs.get(0)));
        links.add(new AuthenticatedPerfectLink(configs.get(1)));
        links.add(new AuthenticatedPerfectLink(configs.get(2)));
        links.add(new AuthenticatedPerfectLink(configs.get(3)));
    }

    @AfterEach
    void tearDown() {
        links.forEach(AuthenticatedPerfectLink::close);
    }

    @Test
    @Timeout(8)
    void testBroadcastAll() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(DepChainConfig.PROCESSES);
        List<Message> received = Collections.synchronizedList(new ArrayList<>());
        Consumer<APLDeliverEvent> consumer = e -> {
            if (e.arg.getPayload().equals("TEST")) {
                received.add(e.arg);
                latch.countDown();
            }
        };
        configs.forEach(config -> {
            // Listen for APL Delivers on all processes
            config.eventBus.register(APLDeliverEvent.class, consumer);
        });

        // Leader Broadcasts Test
        Broadcast.all("TEST", configs.getFirst());

        latch.await();

        assertEquals((int) DepChainConfig.PROCESSES, received.size());
        assertTrue(received.stream().allMatch(m -> Objects.equals(m.getPayload(), "TEST")));

        // Teardown
        configs.forEach(config -> {
            config.eventBus.unregister(APLDeliverEvent.class, consumer);
        });
    }
}
