package depchain.network.rw;

import depchain.config.DepChainConfig;
import depchain.consensus.cc.CCCollectedEvent;
import depchain.consensus.cc.CCInputEvent;
import depchain.consensus.cc.ConditionalCollect;
import depchain.events.EventBus;
import depchain.network.DepChainState;
import depchain.network.Message;
import depchain.network.apl.APLDeliverEvent;
import depchain.network.apl.APLSendEvent;
import depchain.network.apl.AuthenticatedPerfectLink;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * Example test for 4 processes: 0..3. We propose a value from the leader (0),
 * check that each ends up with the same consensus result ("ACCEPT###value"),
 * demonstrating the read-write epoch logic in ReadWrite + ConditionalCollect.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Disabled
public class TestReadWriteConsensus {

    private static final int N = 4;  // total processes
    private final List<DepChainConfig> configs = new ArrayList<>(N);
    private final List<EventBus> buses = new ArrayList<>(N);
    private final List<AuthenticatedPerfectLink> apls = new ArrayList<>(N);
    private final List<DepChainState> states = new ArrayList<>(N);
    private final List<ConditionalCollect> ccs = new ArrayList<>(N);
    private final List<ReadWrite> readWrites = new ArrayList<>(N);

    // We'll track "ACCEPT###..." from each node
    private final List<String> acceptBroadcasts = Collections.synchronizedList(new ArrayList<>());
    private CountDownLatch acceptLatch;

    @BeforeEach
    public void setUp() throws InterruptedException {
        // We create 4 "virtual" processes in one JVM.
        // Each process has:
        //  - DepChainConfig
        //  - EventBus (from config)
        //  - AuthenticatedPerfectLink
        //  - DepChainState (with that single APL)
        //  - ConditionalCollect
        //  - ReadWrite
        for (int i = 0; i < N; i++) {
            // 1) Build config & eventBus
            DepChainConfig cfg = new DepChainConfig(i);
            EventBus eb = cfg.eventBus; // from constructor
            configs.add(cfg);
            buses.add(eb);

            // 2) Build AuthenticatedPerfectLink
            AuthenticatedPerfectLink apl = new AuthenticatedPerfectLink(cfg);
            apls.add(apl);

            // 3) Build DepChainState with a single link
            DepChainState st = new DepChainState(cfg, apl);
            states.add(st);

            // 4) Build ConditionalCollect: pass 'apl' and a trivial predicate
            ConditionalCollect cc = new ConditionalCollect(
                cfg,
                apl, 
                msgs -> (msgs.size() >= (N - DepChainConfig.FAIL_BOUND))
            );
            ccs.add(cc);

            // 5) Build ReadWrite
            ReadWrite rw = new ReadWrite(st);
            readWrites.add(rw);
        }

        // We'll track "ACCEPT###" events from each node
        acceptLatch = new CountDownLatch(N);

        // We register a listener on each process's eventBus for APLSendEvent
        // If we see "ACCEPT###someVal", we increment the latch
        for (int i = 0; i < N; i++) {
            final int nodeId = i;
            buses.get(i).register(APLSendEvent.class, new Consumer<APLSendEvent>() {
                @Override
                public void accept(APLSendEvent e) {
                    String payload = e.arg.getPayload();
                    if (payload.startsWith("ACCEPT###")) {
                        acceptBroadcasts.add("Node" + nodeId + ": " + payload);
                        acceptLatch.countDown();
                    }
                }
            });
        }

        // Let watchers spin up
        Thread.sleep(300);
    }

    @AfterEach
    public void tearDown() {
        for (int i = 0; i < N; i++) {
            readWrites.get(i).close();
            ccs.get(i).close();
            apls.get(i).close();
            buses.get(i).shutdown();
            states.get(i).shutdown();  // calls link.close(), eventBus.shutdown
        }
    }

    /**
     * We propose a value from the leader (0). We expect all processes
     * to eventually broadcast ACCEPT### that value.
     */
    @Test
    @Order(1)
    public void testLeaderProposesAndAllAccept() throws InterruptedException {
        // We propose "TestValue" from the leader
        String proposedValue = "TestValue";
        Message proposeMsg = new Message(proposedValue, 0); // from leader
        buses.get(0).publish(new ProposeEvent(proposeMsg));

        // Wait for all 4 processes to broadcast ACCEPT
        acceptLatch.await();

        assertEquals(N, acceptBroadcasts.size(),
            "Expected 4 ACCEPT### broadcasts (one per node). Found: " + acceptBroadcasts);

        // Optionally, we can check that each node's broadcast has "TestValue"
        boolean allCorrect = acceptBroadcasts.stream()
            .allMatch(s -> s.contains("TestValue"));
        assertTrue(allCorrect, 
            "All ACCEPT### messages should contain 'TestValue'. Found: " + acceptBroadcasts);
    }
}
