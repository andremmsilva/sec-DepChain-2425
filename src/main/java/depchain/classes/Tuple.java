package depchain.classes;

import java.util.Objects;

public class Tuple {
    int timestamp = 0;
    String value = "";

    public Tuple(int timestamp, String value) {
        this.timestamp = timestamp;
        this.value = value;
    }

    public int getTimestamp() {
        return this.timestamp;
    }

    public String getValue() {
        return this.value;
    }

    public static Tuple fromStr(String s) {
        try {
            String tupleStr = s.replaceAll("[()]", "");
            String[] parts = tupleStr.split(",", -1);
            return new Tuple(Integer.parseInt(parts[0]), parts[1]);

        } catch (NumberFormatException e) {
            System.out.println("Tuple fromStr invalid arg: " + s);
            return null;
        }
    }

    @Override
    public String toString() {
        return "(" + String.valueOf(timestamp) + "," + value + ")";
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
