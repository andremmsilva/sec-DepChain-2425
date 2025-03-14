package depchain.classes;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class StateTest {
    @Test
    public void testSerializationDeserialization() {
        State s = new State();
        System.out.println("Expected: " + s);
        System.out.println("Got: " + State.fromStr(s.toString()));
        assertTrue(State.fromStr(s.toString()).equals(s));

        s.addToWriteSet(new Tuple(1, "aa"));
        assertTrue(State.fromStr(s.toString()).equals(s));

        s.setLastRound(new Tuple(5, "bb"));
        assertTrue(State.fromStr(s.toString()).equals(s));
    }
}