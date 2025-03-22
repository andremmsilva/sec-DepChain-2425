package depchain.consensus.rw;

import depchain.config.DepChainConfig;
import depchain.consensus.cc.CCCollectedEvent;
import depchain.network.Message;
import depchain.network.apl.APLDeliverEvent;
import depchain.network.apl.APLSendEvent;
import depchain.network.apl.AuthenticatedPerfectLink;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReadWriteTest {
    private final ArrayList<DepChainConfig> configs = new ArrayList<>(DepChainConfig.PROCESSES);
    private final ArrayList<AuthenticatedPerfectLink> links = new ArrayList<>(DepChainConfig.PROCESSES);
    private final ArrayList<ReadWrite> rws = new ArrayList<>(DepChainConfig.PROCESSES);

    @BeforeEach
    void setUp() {
        configs.clear();
        configs.add(new DepChainConfig(0));
        configs.add(new DepChainConfig(1));
        configs.add(new DepChainConfig(2));
        configs.add(new DepChainConfig(3));
        links.add(new AuthenticatedPerfectLink(configs.get(0)));
        links.add(new AuthenticatedPerfectLink(configs.get(1)));
        links.add(new AuthenticatedPerfectLink(configs.get(2)));
        links.add(new AuthenticatedPerfectLink(configs.get(3)));
        rws.add(new ReadWrite(configs.get(0)));
        rws.add(new ReadWrite(configs.get(1)));
        rws.add(new ReadWrite(configs.get(2)));
        rws.add(new ReadWrite(configs.get(3)));
    }

    @AfterEach
    void tearDown() {
        links.forEach(AuthenticatedPerfectLink::close);
        rws.forEach(ReadWrite::close);
    }

    @Test
    void testProposeResultsInBroadcastingRead() throws InterruptedException {
        System.out.println("testProposeResultsInBroadcastingRead()\n");
        CountDownLatch latch = new CountDownLatch(DepChainConfig.PROCESSES);
        List<Message> received = Collections.synchronizedList(new ArrayList<>());
        Consumer<APLDeliverEvent> consumer = e -> {
            if (e.arg.getPayload().startsWith("READ")) {
                received.add(e.arg);
                latch.countDown();
            }
        };
        configs.forEach(config -> {
            // Listen for APL Delivers on all processes
            config.eventBus.register(APLDeliverEvent.class, consumer);
        });

        // Leader proposes "1"
        configs.getFirst().eventBus.publish(new RWProposeEvent("1"));

        latch.await();

        assertEquals((int) DepChainConfig.PROCESSES, received.size());

        // Teardown
        configs.forEach(config -> config.eventBus.unregister(APLDeliverEvent.class, consumer));
    }

    @Test
    void testReadMessageResultsInCCInput() throws InterruptedException {
        System.out.println("testReadMessageResultsInCCInput()\n");
        CountDownLatch latch = new CountDownLatch(1);
        List<Message> received = Collections.synchronizedList(new ArrayList<>());
        Consumer<APLDeliverEvent> consumer = e -> {
            received.add(e.arg);
            latch.countDown();
        };

        configs.getFirst().eventBus.register(APLDeliverEvent.class, consumer);

        // Leader sends "READ" to 1
        // 1 should then send it's state to the leader, via conditional collect
        configs.getFirst().eventBus.publish(new APLSendEvent(new Message("READ", 0), 1));

        latch.await();

        assertEquals(1, received.size());
        assertEquals("SEND###" + rws.get(1).getEpochState().toString(), received.getFirst().getPayload());

        // Teardown
        configs.getFirst().eventBus.unregister(APLDeliverEvent.class, consumer);
    }

    @Test
    @Timeout(8)
    void testProposeResultsInCollectedEvents() throws InterruptedException {
        System.out.println("testProposeResultsInCollectedEvents()\n");
        CountDownLatch latch = new CountDownLatch(DepChainConfig.PROCESSES);
        List<Message> received = Collections.synchronizedList(new ArrayList<>());
        Consumer<CCCollectedEvent> consumer = e -> {
            received.addAll(e.arg);
            latch.countDown();
        };
        configs.forEach(config -> {
            // Listen for APL Delivers on all processes
            config.eventBus.register(CCCollectedEvent.class, consumer);
        });

        // Leader proposes "1"
        configs.getFirst().eventBus.publish(new RWProposeEvent("1"));

        latch.await();
        received.forEach(m -> System.out.println(m.getPayload() + " |||| " + m.getSenderId()));

        // We expect at least 3 messages (states) per process that published CCCollected
        assertTrue(DepChainConfig.PROCESSES * (DepChainConfig.PROCESSES - 1) <= received.size());

        // Teardown
        configs.forEach(config -> config.eventBus.unregister(CCCollectedEvent.class, consumer));
    }

    @Test
    @Timeout(8)
    @Disabled()
    void testOnCCCollectedUnbound() throws InterruptedException {
        System.out.println("testOnCCCollectedUnbound()\n");
        CountDownLatch latch = new CountDownLatch(DepChainConfig.PROCESSES);
        List<Message> received = Collections.synchronizedList(new ArrayList<>());
        Consumer<APLDeliverEvent> aplDeliverConsumer = e -> {
            if (e.arg.getPayload().startsWith("WRITE###")) {
                received.add(e.arg);
                latch.countDown();
            }
        };
        configs.forEach(config -> {
            // Listen for APL Delivers on all processes to confirm WRITE
            config.eventBus.register(APLDeliverEvent.class, aplDeliverConsumer);
        });

        // Leader proposes "1"
        configs.getFirst().eventBus.publish(new RWProposeEvent("1"));

        latch.await();

        // We expect 4 messages (states) per process that published CCCollected
        assertEquals((int) DepChainConfig.PROCESSES, received.size());
        assertTrue(
                received.stream().allMatch(m -> m.getPayload().substring("WRITE###".length()).equals("1")));

        // Teardown
        configs.forEach(config -> config.eventBus.unregister(APLDeliverEvent.class, aplDeliverConsumer));
    }

    @Test
    @Timeout(20)
    @Disabled()
    public void testFullIteration() throws InterruptedException {
        System.out.println("void testFullIteration()\n");
        CountDownLatch latch = new CountDownLatch(DepChainConfig.PROCESSES);
        List<String> received = Collections.synchronizedList(new ArrayList<>());
        Consumer<RWDecideEvent> consumer = e -> {
            System.out.println("Received Decide " + e.arg);
            received.add(e.arg);
            latch.countDown();
        };

        configs.forEach(config -> {
            config.eventBus.register(RWDecideEvent.class, consumer);
        });

        configs.getFirst().eventBus.publish(new RWProposeEvent("1"));

        latch.await();
        received.forEach(m -> System.out.println(m));

        assertEquals((int) DepChainConfig.PROCESSES, received.size());

        configs.forEach(config -> config.eventBus.unregister(RWDecideEvent.class, consumer));
    }
}
