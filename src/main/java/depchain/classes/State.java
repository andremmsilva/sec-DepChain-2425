package depchain.classes;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class State {
    private Tuple last_round;
    private final Set<Tuple> writeset;

    public State() {
        this.last_round = new Tuple(0, "");
        this.writeset = Collections.synchronizedSet(new HashSet<>());
    }

    public static State fromStr(String messageContent) {
        if (messageContent.isEmpty()) {
            System.err.println("ConvertMessageToState: Received an empty message.");
            return null;
        }

        String[] parts = messageContent.split("\\|");
        if (parts.length != 2) {
            System.err.println("ConvertMessageToState: malformed state message - " + messageContent);
            return null;
        }

        Tuple lastRound = Tuple.fromStr(parts[0]);   

        // Extract writeset (last part after "|")
        Set<Tuple> writeSet = new HashSet<>();
        if (parts[1].length() > 2) { //Size of "{}"
            String writesetStr = parts[1].replaceAll("[\\{\\}]", "");
            String[] writesetEntries = writesetStr.split(";");
            for (String entry : writesetEntries) {
                Tuple t = Tuple.fromStr(entry);
                if (t != null) {
                    writeSet.add(t);
                }
            }
        }

        return new State(lastRound, writeSet);
    }

    public State(Tuple tuple, Set<Tuple> writeset) {
        this.last_round = tuple;
        this.writeset = writeset;
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

    public void addToWriteSet(Tuple newWriteTuple) {
        this.writeset.add(newWriteTuple);
    }

    @Override
    public String toString() {
        return String.valueOf(last_round.toString() + '|' + writeSetToString());
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
