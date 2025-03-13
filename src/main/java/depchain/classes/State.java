package depchain.classes;

import java.util.Collections;
import java.util.HashSet;
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
        if (parts.length != 3) {
            System.err.println("ConvertMessageToState: malformed state message - " + messageContent);
            return null;
        }

        int lastRoundTimestamp = Integer.parseInt(parts[0].trim());
        String lastRoundValue = parts[1].trim();
        Tuple lastRound = new Tuple(lastRoundTimestamp, lastRoundValue);

        // Extract writeset (last part after "|")
        Set<Tuple> writeSet = new HashSet<>();
        if (!parts[2].isEmpty()) {
            String[] writesetEntries = parts[2].split(";");
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
        return String.valueOf(last_round.getTimestamp()) + '|' + last_round.getValue() + '|' + writeSetToString();
    }

    public String writeSetToString() {
        StringBuilder result = new StringBuilder();
        synchronized (writeset) {
            for (Tuple t : writeset) {
                result.append(t.toString());
                result.append(";");
            }
        }

        if (result.toString().equals(""))
            return "";

        return result.toString().substring(0, result.length() - 1);
    }

}
