package depchain.network.rw;

import depchain.config.DepChainConfig;
import depchain.consensus.cc.CCCollectedEvent;
import depchain.events.EventBus;
import depchain.network.DepChainState;
import depchain.network.Message;
import depchain.network.apl.APLSendEvent;
import depchain.network.apl.AuthenticatedPerfectLink;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Tests the onCCCollected event -> verify we broadcast 'WRITE###...'.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ReadWriteCollectTest {

    private DepChainConfig leaderConfig;
    private EventBus leaderBus;
    private DepChainState leaderState;
    private ReadWrite leaderRW;

    @BeforeEach
    void setUp() throws InterruptedException {
        leaderConfig = new DepChainConfig(0); // leader
        leaderBus = new EventBus(leaderConfig);
        leaderState = new DepChainState(leaderConfig, new ArrayList<AuthenticatedPerfectLink>());
        leaderRW = new ReadWrite(leaderState);

        Thread.sleep(200);
    }

    @AfterEach
    void tearDown() {
        leaderRW.close();
        leaderBus.shutdown();
    }

    @Test
    @Order(1)
    public void testOnCCCollected() throws InterruptedException {
        // We'll track messages the leader sends
        List<Message> leaderSent = new ArrayList<>();
        Consumer<APLSendEvent> leaderListener = ev -> leaderSent.add(ev.arg);
        leaderConfig.eventBus.register(APLSendEvent.class, leaderListener);

        // Simulate a CCCollectedEvent with some states
        List<Message> states = new ArrayList<>();
        states.add(new Message("2|hello|(2,foo)", 2));

        leaderConfig.eventBus.publish(new CCCollectedEvent(states));
        Thread.sleep(300);

        boolean sawWrite = leaderSent.stream()
            .anyMatch(m -> m.getPayload().startsWith("WRITE###"));
        assertTrue(sawWrite, "Should broadcast 'WRITE###' after CCCollectedEvent.");

        leaderConfig.eventBus.unregister(APLSendEvent.class, leaderListener);
    }
}
