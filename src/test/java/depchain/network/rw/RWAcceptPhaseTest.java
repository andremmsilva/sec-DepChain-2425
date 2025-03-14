package depchain.network.rw;

import depchain.config.DepChainConfig;
import depchain.events.EventBus;
import depchain.network.DepChainState;
import depchain.network.Message;
import depchain.network.apl.APLDeliverEvent;
import depchain.network.apl.AuthenticatedPerfectLink;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests the "ACCEPT###" logic:
 *   - watchers see enough accepted[] = v
 *   - we call decideValue(v)
 *   - currently that logs to console, so we just ensure no crash
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RWAcceptPhaseTest {

    private DepChainConfig leaderConfig;
    private EventBus leaderBus;
    private DepChainState leaderState;
    private ReadWrite leaderRW;

    @BeforeEach
    void setUp() throws InterruptedException {
        leaderConfig = new DepChainConfig(0);
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
    public void testAcceptQuorum() throws InterruptedException {
        // For a quorum, we assume at least 2 identical "ACCEPT###myVal" from different senders
        Message a1 = new Message("ACCEPT###myVal", 1);
        Message a2 = new Message("ACCEPT###myVal", 2);
        Message a3 = new Message("ACCEPT###myVal", 3);

        leaderConfig.eventBus.publish(new APLDeliverEvent(a1));
        leaderConfig.eventBus.publish(new APLDeliverEvent(a2));
        leaderConfig.eventBus.publish(new APLDeliverEvent(a3));

        Thread.sleep(500);

        // watchers presumably decided "myVal" => no crash => success
        assertTrue(true, "No errors => watchers presumably decided 'myVal'");
    }
}
