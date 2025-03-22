package depchain.consensus.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class StateTest {
    @Test
    public void testSerializationDeserialization() {
        State s = new State();
        assertEquals(State.fromStr(s.toString()), s);

        s.addToWriteSet(new Tuple(1, "aa"));
        assertEquals(State.fromStr(s.toString()), s);

        s.setLastRound(new Tuple(5, "bb"));
        assertEquals(State.fromStr(s.toString()), s);

        s.addToWriteSet(new Tuple(-1000, "byzantine"));
        assertEquals(State.fromStr(s.toString()), s);
    }
}