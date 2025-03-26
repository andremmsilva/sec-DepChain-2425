package depchain.consensus.utils;

import com.google.gson.Gson;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class State {
    private final Set<Tuple> writeset;
    private Tuple last_round;

    public State() {
        this.last_round = new Tuple(0, "");
        this.writeset = Collections.synchronizedSet(new HashSet<>());
    }

    public State(Tuple tuple, Set<Tuple> writeset) {
        this.last_round = tuple;
        this.writeset = writeset;
    }

    public static State deserialize(Gson gson, String json) {
        return gson.fromJson(json, State.class);
    }

    public String serialize(Gson gson) {
        return gson.toJson(this);
    }

    public Tuple getLastRound() {
        return this.last_round;
    }

    public void setLastRound(Tuple newRound) {
        this.last_round = newRound;
    }

    public Set<Tuple> getWriteSet() {
        return this.writeset;
    }

    public void setWriteSet(Set<Tuple> newWriteSet) {
        this.writeset.clear();
        this.writeset.addAll(newWriteSet);
    }

    public void addToWriteSet(Tuple newWriteTuple) {
        this.writeset.add(newWriteTuple);
    }

    @Override
    public String toString() {
        return last_round.toString() + '|' + writeSetToString();
    }

    private String writeSetToString() {
        if (writeset.isEmpty()) {
            return "{}";  // just empty braces
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        synchronized (writeset) {
            boolean first = true;
            for (Tuple t : writeset) {
                if (!first) {
                    sb.append(";");
                }
                sb.append(t.toString()); // e.g. (2,foo)
                first = false;
            }
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true; // Same object reference
        if (obj == null || getClass() != obj.getClass())
            return false; // Check null and class type

        State state = (State) obj;
        return last_round.equals(state.last_round) && writeset.equals(state.writeset);
    }

    @Override
    public int hashCode() {
        return Objects.hash(last_round, writeset);
    }
}
