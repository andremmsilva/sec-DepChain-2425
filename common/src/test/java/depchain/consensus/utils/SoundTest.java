package depchain.consensus.utils;

import com.google.gson.Gson;
import depchain.network.message.Message;
import depchain.network.message.MessagePayload;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * Tests the sound(S) predicate, according to the Algorithm 5.16-5.18 specification in
 * Introduction to Reliable and Secure Distributed Programming
 * */
public class SoundTest {
    private static final Gson gson = new Gson();

    // Helper to create a Message with a given state string.
    private Message createMessage(String stateStr) {
        // For testing purposes, we can use sender id 0.
        return new Message(new MessagePayload(null, stateStr), 0);
    }

    @Test
    public void testUnbound() {
        // Create exactly 3 messages (which is >= PROCESSES - FAIL_BOUND)
        // All with an initial state (timestamp 0) so that unbound() should return true.
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            messages.add(createMessage(new State().serialize(gson)));
        }

        Sound soundObj = new Sound();
        // Since at least 3 states have timestamp 0, unbound returns true,
        // and thus the sound predicate should evaluate to true.
        assertTrue(soundObj.getPredicate().test(messages), "Unbound condition should yield sound = true");
    }

    @Test
    public void testBindingSuccess() {
        // Create 4 states where a candidate (3,x) is well supported.
        List<Message> messages = new ArrayList<>();
        Tuple lr = new Tuple(3, "x");
        Set<Tuple> ws = new HashSet<>();
        ws.add(lr);
        // Three states have last round (3,x) and include (3,x) in their write set.
        messages.add(createMessage(new State(lr, ws).serialize(gson)));
        messages.add(createMessage(new State(lr, ws).serialize(gson)));
        messages.add(createMessage(new State(new Tuple(2, "y"), ws).serialize(gson))); // Even though last round is
        // lower, it provides certification.
        messages.add(createMessage(new State(lr, ws).serialize(gson)));

        Sound soundObj = new Sound();
        // For candidate (3,x):
        // - quorumHighest: All four states have lastRound <= (3,x) (with three exactly (3,x) and one lower),
        //   so count = 4 which is > (4+1)/2 = 2.5.
        // - certifiedValue: At least three states include (3,x) in their write set, which is > FAIL_BOUND (1).
        // Hence, binds returns true and the sound predicate is true.
        assertTrue(soundObj.getPredicate().test(messages), "Binding candidate should yield sound = true");
    }

    @Test
    public void testBindingFailureDueToCertifiedValue() {
        // In this test, although several states carry (3,x) as lastRound,
        // the candidate is not sufficiently certified because only one state includes (3,x) in its write set.
        List<Message> messages = new ArrayList<>();

        Tuple lr = new Tuple(3, "x");
        Set<Tuple> certifiedWs = new HashSet<>();
        certifiedWs.add(lr);

        Set<Tuple> emptyWs = new HashSet<>();

        messages.add(createMessage(new State(lr, certifiedWs).serialize(gson)));  // Provides certification.
        messages.add(createMessage(new State(lr, emptyWs).serialize(gson)));         // No certification.
        messages.add(createMessage(new State(new Tuple(2, "y"), emptyWs).serialize(gson)));         // No certification.
        messages.add(createMessage(new State(lr, emptyWs).serialize(gson)));         // No certification.

        Sound soundObj = new Sound();
        // Here, quorumHighest may still hold (since enough states have lastRound (3,x) or lower),
        // but certifiedValue fails because only 1 state (≤ FAIL_BOUND) certifies (3,x).
        // Thus, binds returns false and sound returns false.
        assertFalse(soundObj.getPredicate().test(messages), "Insufficient certification should yield sound = false");
    }

    @Test
    public void testInsufficientStates() {
        // If there are fewer than N - f (3) states, then unbound cannot be satisfied
        // and even if a candidate appears, the quorum count will be too low.
        List<Message> messages = new ArrayList<>();

        Tuple lr = new Tuple(3, "x");
        Set<Tuple> certifiedWs = new HashSet<>();
        certifiedWs.add(lr);

        messages.add(createMessage(new State(lr, certifiedWs).serialize(gson)));
        messages.add(createMessage(new State(lr, certifiedWs).serialize(gson)));

        Sound soundObj = new Sound();
        // With only 2 states, both conditions fail.
        assertFalse(soundObj.getPredicate().test(messages), "Insufficient number of states should yield sound = false");
    }
}
