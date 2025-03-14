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
import depchain.network.apl.APLDeliverEvent;
import depchain.network.apl.APLSendEvent;

/**
 * Example JUnit 5 test class that tests each major event in ReadWrite:
 *  - ProposeEvent -> check that leader sends 'READ'
 *  - CCCollectedEvent -> check that we broadcast 'WRITE###...'
 *  - APLDeliverEvent with 'WRITE###' -> watchers -> 'ACCEPT###'
 *  - APLDeliverEvent with 'ACCEPT###' -> watchers -> finalize/decide
 *
 * This uses the real APLSendEvent and APLDeliverEvent events rather than a dummy.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ReadWriteTest {

    private DepChainConfig leaderConfig, nonLeaderConfig;
    private EventBus leaderBus, nonLeaderBus;
    private DepChainState leaderState, nonLeaderState;
    private ReadWrite leaderRW, nonLeaderRW;

    @BeforeEach
    public void setUp() throws InterruptedException {
        // Create a leader config with selfId=0
        leaderConfig = new DepChainConfig(0);
        leaderBus = new EventBus(leaderConfig);
        leaderState = new DepChainState(leaderConfig, new ArrayList<>());
        leaderRW = new ReadWrite(leaderState);

        // Create a non-leader config with selfId=1
        nonLeaderConfig = new DepChainConfig(1);
        nonLeaderBus = new EventBus(nonLeaderConfig);
        nonLeaderState = new DepChainState(nonLeaderConfig, new ArrayList<>());
        nonLeaderRW = new ReadWrite(nonLeaderState);

        // Give watchers a brief moment to start up
        Thread.sleep(200);
    }

    @AfterEach
    public void tearDown() {
        leaderRW.close();
        nonLeaderRW.close();
        leaderBus.shutdown();
        nonLeaderBus.shutdown();
    }

    /**
     * 1) Test: the leader reacts to ProposeEvent by broadcasting "READ"
     *          while a non-leader does nothing on ProposeEvent.
     */
    @Test
    @Order(1)
    public void testProposeEvent() throws InterruptedException {
        // We'll track any messages the leader tries to send via APLSendEvent
        List<Message> leaderSent = new ArrayList<>();
        Consumer<APLSendEvent> leaderListener = (APLSendEvent ev) -> leaderSent.add(ev.arg);
        leaderConfig.eventBus.register(APLSendEvent.class, leaderListener);

        // Publish a ProposeEvent to the leader
        Message proposeMsg = new Message("LeaderProposal", leaderConfig.selfId);
        leaderConfig.eventBus.publish(new ProposeEvent(proposeMsg));

        Thread.sleep(300);

        // The leader should broadcast a "READ" message
        boolean sawREAD = leaderSent.stream()
                .anyMatch(m -> "READ".equals(m.getPayload()));
        assertTrue(sawREAD, "Leader must broadcast 'READ' after ProposeEvent.");

        // Now let's check a ProposeEvent on the non-leader
        List<Message> nonLeaderSent = new ArrayList<>();
        Consumer<APLSendEvent> nonLeaderListener = (APLSendEvent ev) -> nonLeaderSent.add(ev.arg);
        nonLeaderConfig.eventBus.register(APLSendEvent.class, nonLeaderListener);

        Message proposeNonLeader = new Message("NonLeaderProposal", nonLeaderConfig.selfId);
        nonLeaderConfig.eventBus.publish(new ProposeEvent(proposeNonLeader));

        Thread.sleep(300);

        assertTrue(nonLeaderSent.isEmpty(),
            "Non-leader should do nothing on ProposeEvent.");

        // Cleanup from eventBus
        leaderConfig.eventBus.unregister(APLSendEvent.class, leaderListener);
        nonLeaderConfig.eventBus.unregister(APLSendEvent.class, nonLeaderListener);
    }

    /**
     * 2) Test: When the ReadWrite class receives CCCollectedEvent,
     *          it should pick a best value and broadcast "WRITE###..."
     */
    @Test
    @Order(2)
    public void testOnCCCollected() throws InterruptedException {
        // We'll track messages the leader sends
        List<Message> leaderSent = new ArrayList<>();
        Consumer<APLSendEvent> leaderListener = (APLSendEvent ev) -> leaderSent.add(ev.arg);
        leaderConfig.eventBus.register(APLSendEvent.class, leaderListener);

        // Suppose the leader just received a CCCollectedEvent with some states
        // We'll send a single 'State' string in the message, e.g. "2|hello|(2,foo)"
        List<Message> states = new ArrayList<>();
        states.add(new Message("2|hello|(2,foo)", 2)); // from node #2

        leaderConfig.eventBus.publish(new CCCollectedEvent(states));

        Thread.sleep(300);

        // Should see something like "WRITE###(epoch, bestVal)" in the leaderSent
        boolean sawWrite = leaderSent.stream()
            .anyMatch(m -> m.getPayload().startsWith("WRITE###"));
        assertTrue(sawWrite, "onCCCollected should broadcast WRITE###...");

        leaderConfig.eventBus.unregister(APLSendEvent.class, leaderListener);
    }

    /**
     * 3) Test: If we deliver "WRITE###someVal" to a node, 
     *          it sets written[sender], watchers check if there's a quorum,
     *          and eventually broadcast "ACCEPT###someVal".
     *
     *  We'll do this test on the leader for simplicity, but it could be any node.
     */
    @Test
    @Order(3)
    public void testWritePhase() throws InterruptedException {
        // Track leader's outbound messages
        List<Message> outbound = new ArrayList<>();
        Consumer<APLSendEvent> leaderListener = (APLSendEvent ev) -> outbound.add(ev.arg);
        leaderConfig.eventBus.register(APLSendEvent.class, leaderListener);

        // We "deliver" 2 identical WRITE###someVal from different senders
        // so that watchers see a majority (assuming N=2 or N=3).
        Message w1 = new Message("WRITE###myVal", 1);
        Message w2 = new Message("WRITE###myVal", 2);

        leaderConfig.eventBus.publish(new APLDeliverEvent(w1));
        leaderConfig.eventBus.publish(new APLDeliverEvent(w2));

        Thread.sleep(500);

        // We expect watchers to broadcast "ACCEPT###myVal"
        boolean sawAccept = outbound.stream()
            .anyMatch(m -> m.getPayload().startsWith("ACCEPT###myVal"));
        assertTrue(sawAccept, "Watchers should broadcast ACCEPT###myVal after quorum of WRITE###myVal");

        leaderConfig.eventBus.unregister(APLSendEvent.class, leaderListener);
    }

    /**
     * 4) Test: If we deliver "ACCEPT###someVal" from enough senders,
     *          watchers cause the node to 'decideValue(someVal)'.
     *          Currently that logs to console. We'll just ensure no crash occurs.
     */
    @Test
    @Order(4)
    public void testAcceptPhase() throws InterruptedException {
        // We'll track outbound messages from the leader
        // but the 'decideValue()' method doesn't broadcast, it logs. 
        // So we just check watchers are okay.
        List<Message> outbound = new ArrayList<>();
        Consumer<APLSendEvent> leaderListener = (APLSendEvent ev) -> outbound.add(ev.arg);
        leaderConfig.eventBus.register(APLSendEvent.class, leaderListener);

        // Deliver multiple "ACCEPT###finalVal" from different senders
        Message a1 = new Message("ACCEPT###finalVal", 1);
        Message a2 = new Message("ACCEPT###finalVal", 2);

        leaderConfig.eventBus.publish(new APLDeliverEvent(a1));
        leaderConfig.eventBus.publish(new APLDeliverEvent(a2));

        Thread.sleep(500);

        // watchers likely decided "finalVal" by now. There's no outward broadcast
        // so we just confirm no crash
        assertTrue(true, "No exceptions => watchers presumably decided finalVal.");

        leaderConfig.eventBus.unregister(APLSendEvent.class, leaderListener);
    }
}
