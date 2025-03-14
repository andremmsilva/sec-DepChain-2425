package depchain.network.rw;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import depchain.config.DepChainConfig;
import depchain.consensus.cc.CCCollectedEvent;
import depchain.events.EventBus;
import depchain.network.DepChainState;
import depchain.network.Message;
import depchain.network.apl.APLSendEvent;
import depchain.network.apl.AuthenticatedPerfectLink;

/**
 * Tests the onCCCollected event -> verify we broadcast 'WRITE###...'.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RWCollectTest {

    private DepChainConfig leaderConfig;
    private EventBus leaderBus;
    private DepChainState leaderState;
    private ReadWrite leaderRW;

    @BeforeEach
    void setUp() throws InterruptedException {
        leaderConfig = new DepChainConfig(0); // leader
        leaderBus = leaderConfig.eventBus;
        leaderState = new DepChainState(leaderConfig, new AuthenticatedPerfectLink(leaderConfig));
        leaderRW = new ReadWrite(leaderState);

        Thread.sleep(200);
    }

    @AfterEach
    void tearDown() {
        leaderRW.close();
        leaderState.shutdown();
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
        states.add(new Message("(0,x)|{}", 2));
        states.add(new Message("(0,y)|{}", 1));
        states.add(new Message("(0,w)|{}", 3));
        states.add(new Message("(0,z)|{}", 0));
        

        leaderConfig.eventBus.publish(new CCCollectedEvent(states));
        Thread.sleep(300);

        boolean sawWrite = leaderSent.stream()
            .anyMatch(m -> m.getPayload().startsWith("WRITE###"));
        assertTrue(sawWrite, "Should broadcast 'WRITE###' after CCCollectedEvent.");

        leaderConfig.eventBus.unregister(APLSendEvent.class, leaderListener);
    }
}
