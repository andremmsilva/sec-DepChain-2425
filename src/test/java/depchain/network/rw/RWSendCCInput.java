package depchain.network.rw;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import depchain.config.DepChainConfig;
import depchain.consensus.cc.CCInputEvent;
import depchain.events.EventBus;
import depchain.network.DepChainState;
import depchain.network.Message;
import depchain.network.apl.APLDeliverEvent;

/**
 * A single-process test that checks if, upon receiving a "READ" message 
 * from the leader (senderId=0), we publish a CCInputEvent 
 * in the same process's eventBus.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RWSendCCInput {

    private DepChainConfig config;
    private EventBus eventBus;
    private DepChainState state;
    private ReadWrite readWriteInstance;

    @BeforeEach
    void setUp() throws InterruptedException {
        // Create ONE process with selfId=1 (non-leader)
        config = new DepChainConfig(1);
        eventBus = config.eventBus;
        state = new DepChainState(config, new ArrayList<>());

        // Create the ReadWrite instance for this single process
        readWriteInstance = new ReadWrite(state);

        // Let watchers initialize
        Thread.sleep(150);
    }

    @AfterEach
    void tearDown() {
        readWriteInstance.close();
        eventBus.shutdown();
    }

    @Test
    @Order(1)
    public void testOnReadPublishesCCInputEvent() throws InterruptedException {
        // We'll listen for CCInputEvent on the same eventBus
        List<CCInputEvent> ccEvents = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        Consumer<CCInputEvent> ccListener = ev -> {
            ccEvents.add(ev);
            latch.countDown();
        };

        config.eventBus.register(CCInputEvent.class, ccListener);

        // Simulate receiving "READ" from the LEADER (id=0)
        Message readMsg = new Message("READ", 0); // sender=0 => the leader
        // We deliver it to the local process's APLDeliverEvent
        config.eventBus.publish(new APLDeliverEvent(readMsg));

        // Wait for the process to (potentially) publish a CCInputEvent
        latch.await();

        // We expect exactly 1 CCInputEvent if "READ" was handled
        assertFalse(ccEvents.isEmpty(), 
            "Should have published CCInputEvent on receiving READ from leader");
        // Optionally, check the content
        CCInputEvent first = ccEvents.get(0);
        assertNotNull(first.arg);
        System.out.println("SingleProcessReadMessageTest: published CCInputEvent payload: " + first.arg.getPayload());

        config.eventBus.unregister(CCInputEvent.class, ccListener);
    }
}
