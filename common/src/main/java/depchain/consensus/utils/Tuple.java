package depchain.consensus.utils;

import java.util.Objects;

import depchain.network.message.Transaction;

public class Tuple {
    int timestamp;
    Transaction value;

    public Tuple(int timestamp, Transaction value) {
        this.timestamp = timestamp;
        this.value = value;
    }

    public synchronized int getTimestamp() {
        return this.timestamp;
    }

    public synchronized Transaction getValue() {
        return this.value;
    }

    @Override
    public String toString() {
        return "(" + timestamp + "," + value + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true; // Same object reference
        if (obj == null || getClass() != obj.getClass())
            return false; // Check null and class type

        Tuple tuple = (Tuple) obj;
        return timestamp == tuple.timestamp && value.equals(tuple.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, value);
    }

}
