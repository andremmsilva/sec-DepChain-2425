package depchain.classes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class StateTest {
    @Test
    public void testSerializationDeserialization() {
        State s = new State();
        assertEquals("0||", s.toString());

        s.addToWriteSet(new Tuple(1, "aa"));

        assertEquals("0||(1,aa)", s.toString());

        s.setLastRound(new Tuple(5, "bb"));
        assertEquals("5|bb|(1,aa)", s.toString());
    }
}