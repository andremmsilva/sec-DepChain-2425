package depchain.network.rw;

import depchain.config.DepChainConfig;
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
 * Tests the ProposeEvent handling:
 *  - Leader should broadcast 'READ'
 *  - Non-leader does nothing
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RWProposeTest {

    private DepChainConfig leaderConfig, nonLeaderConfig;
    private EventBus leaderBus, nonLeaderBus;
    private DepChainState leaderState, nonLeaderState;
    private ReadWrite leaderRW, nonLeaderRW;

    @BeforeEach
    void setUp() throws InterruptedException {
        // Leader
        leaderConfig = new DepChainConfig(0);
        leaderBus = new EventBus(leaderConfig);
        leaderState = new DepChainState(leaderConfig, new ArrayList<AuthenticatedPerfectLink>());
        leaderRW = new ReadWrite(leaderState);

        // Non-leader
        nonLeaderConfig = new DepChainConfig(1);
        nonLeaderBus = new EventBus(nonLeaderConfig);
        nonLeaderState = new DepChainState(nonLeaderConfig, new ArrayList<AuthenticatedPerfectLink>());
        nonLeaderRW = new ReadWrite(nonLeaderState);

        Thread.sleep(200);
    }

    @AfterEach
    void tearDown() {
        leaderRW.close();
        nonLeaderRW.close();
        leaderBus.shutdown();
        nonLeaderBus.shutdown();
    }

    @Test
    @Order(1)
    public void testProposeEvent() throws InterruptedException {
        // Track messages from the leader
        List<Message> leaderSent = new ArrayList<>();
        Consumer<APLSendEvent> leaderListener = ev -> leaderSent.add(ev.arg);
        leaderConfig.eventBus.register(APLSendEvent.class, leaderListener);

        // 1) Leader receives ProposeEvent => broadcast "READ"
        Message leaderProposal = new Message("LeaderProposal", 0);
        leaderConfig.eventBus.publish(new ProposeEvent(leaderProposal));
        Thread.sleep(300);

        boolean sawRead = leaderSent.stream().anyMatch(m -> "READ".equals(m.getPayload()));
        assertTrue(sawRead, "Leader must broadcast 'READ' after propose.");

        // 2) Non-leader's Propose => should do nothing
        List<Message> nonLeaderSent = new ArrayList<>();
        Consumer<APLSendEvent> nonLeaderListener = ev -> nonLeaderSent.add(ev.arg);
        nonLeaderConfig.eventBus.register(APLSendEvent.class, nonLeaderListener);

        Message nonLeaderProposal = new Message("NonLeaderProposal", 1);
        nonLeaderConfig.eventBus.publish(new ProposeEvent(nonLeaderProposal));
        Thread.sleep(300);

        assertTrue(nonLeaderSent.isEmpty(), "Non-leader should not broadcast on propose.");

        leaderConfig.eventBus.unregister(APLSendEvent.class, leaderListener);
        nonLeaderConfig.eventBus.unregister(APLSendEvent.class, nonLeaderListener);
    }
}
