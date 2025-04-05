// package depchain.consensus.utils;

// import com.google.gson.Gson;
// import org.junit.jupiter.api.Test;

// import static org.junit.jupiter.api.Assertions.assertEquals;

// public class StateTest {
//     private static final Gson gson = new Gson();

//     @Test
//     public void testSerializationDeserialization() {
//         State s = new State();
//         assertEquals(State.deserialize(gson, s.serialize(gson)), s);

//         s.addToWriteSet(new Tuple(1, "aa"));
//         assertEquals(State.deserialize(gson, s.serialize(gson)), s);

//         s.setLastRound(new Tuple(5, "bb"));
//         assertEquals(State.deserialize(gson, s.serialize(gson)), s);

//         s.addToWriteSet(new Tuple(-1000, "byzantine"));
//         assertEquals(State.deserialize(gson, s.serialize(gson)), s);
//     }
// }