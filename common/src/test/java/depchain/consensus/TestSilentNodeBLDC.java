package depchain.consensus;

import com.google.gson.Gson;
import depchain.config.DepChainConfig;
import depchain.consensus.utils.Broadcast;
import depchain.network.apl.AuthenticatedPerfectLink;
import depchain.network.apl.IAuthenticatedPerfectLink;
import depchain.network.message.MessagePayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import utils.SilentAPL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSilentNodeBLDC {
    private static final Gson gson = new Gson();
    private final ArrayList<DepChainConfig> configs = new ArrayList<>(DepChainConfig.PROCESSES);
    private final ArrayList<IAuthenticatedPerfectLink> links = new ArrayList<>(DepChainConfig.PROCESSES);
    private final ArrayList<ByzantineLeaderDrivenConsensus> bldcs = new ArrayList<>(DepChainConfig.PROCESSES);

    @BeforeEach
    void setUp() throws InterruptedException {
        configs.clear();
        links.clear();
        bldcs.clear();
        configs.add(new DepChainConfig(0, 0, "127.0.0.1"));
        configs.add(new DepChainConfig(1, configs.getFirst().getBasePort(), "127.0.0.1"));
        configs.add(new DepChainConfig(2, configs.getFirst().getBasePort(), "127.0.0.1"));
        configs.add(new DepChainConfig(3, configs.getFirst().getBasePort(), "127.0.0.1"));
        links.add(new AuthenticatedPerfectLink(configs.get(0)));
        links.add(new AuthenticatedPerfectLink(configs.get(1)));
        links.add(new AuthenticatedPerfectLink(configs.get(2)));
        // Node 3 does not send messages :)
        links.add(new SilentAPL(configs.get(3)));
        bldcs.add(new ByzantineLeaderDrivenConsensus(configs.get(0)));
        bldcs.add(new ByzantineLeaderDrivenConsensus(configs.get(1)));
        bldcs.add(new ByzantineLeaderDrivenConsensus(configs.get(2)));
        bldcs.add(new ByzantineLeaderDrivenConsensus(configs.get(3)));
        // Cheat to make handshakes work :/
        Thread.sleep(300);
        for (DepChainConfig config : configs) {
            Broadcast.all(new MessagePayload(null, "Hello world!"), config);
            Thread.sleep(200);
        }
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        links.forEach(IAuthenticatedPerfectLink::close);
        bldcs.forEach(ByzantineLeaderDrivenConsensus::shutdown);
        Thread.sleep(300);
    }

    @Test
    @Timeout(10)
    void testBLDCFullIteration() throws InterruptedException {
        System.out.println("\ntestBLDCFullIteration()\n");
        // At least F+1 should still decide
        CountDownLatch latch = new CountDownLatch(DepChainConfig.FAIL_BOUND + 1);
        List<String> received = Collections.synchronizedList(new ArrayList<>());
        Consumer<LDCDecideEvent> consumer = e -> {
            System.out.println("Received Decide " + e.arg);
            received.add(e.arg);
            latch.countDown();
        };

        configs.forEach(config -> {
            config.eventBus.register(LDCDecideEvent.class, consumer);
        });

        configs.getFirst().eventBus.publish(new LDCProposeEvent("1"));

        latch.await();

        assertTrue(DepChainConfig.FAIL_BOUND + 1 <= received.size());

        configs.forEach(config -> config.eventBus.unregister(LDCDecideEvent.class, consumer));
    }
}
