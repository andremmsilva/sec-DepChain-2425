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
import depchain.events.EventBus;
import depchain.network.DepChainState;
import depchain.network.Message;
import depchain.network.apl.APLDeliverEvent;
import depchain.network.apl.APLSendEvent;
import depchain.network.apl.AuthenticatedPerfectLink;

/**
 * Tests the "WRITE###" logic in onAPLDelivery:
 *   - We set 'written[sender] = v'
 *   - Once watchers see a quorum, they broadcast "ACCEPT###v"
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RWWritePhaseTest {

    private DepChainConfig leaderConfig;
    private EventBus leaderBus;
    private DepChainState leaderState;
    private ReadWrite leaderRW;

    @BeforeEach
    void setUp() throws InterruptedException {
        leaderConfig = new DepChainConfig(0);
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
    public void testWritePhaseMajority() throws InterruptedException {
        // Track leader's outbound messages
        List<Message> out = new ArrayList<>();
        Consumer<APLSendEvent> leaderListener = ev -> out.add(ev.arg);
        leaderConfig.eventBus.register(APLSendEvent.class, leaderListener);

        // Suppose 2 different senders deliver "WRITE###myVal"
        Message w0 = new Message("WRITE###myVal", 0);
        Message w1 = new Message("WRITE###myVal", 1);
        Message w2 = new Message("WRITE###myVal", 2);
        Message w3 = new Message("WRITE###myVal", 3);

        leaderConfig.eventBus.publish(new APLDeliverEvent(w0));
        leaderConfig.eventBus.publish(new APLDeliverEvent(w1));
        leaderConfig.eventBus.publish(new APLDeliverEvent(w2));
        leaderConfig.eventBus.publish(new APLDeliverEvent(w3));
        Thread.sleep(500);

        // If watchers see a quorum of "myVal", they broadcast "ACCEPT###myVal"
        boolean sawAccept = out.stream()
                .anyMatch(m -> m.getPayload().startsWith("ACCEPT###myVal"));
        assertTrue(sawAccept, "Should broadcast ACCEPT###myVal on quorum of WRITE###myVal");

        leaderConfig.eventBus.unregister(APLSendEvent.class, leaderListener);
    }
}
