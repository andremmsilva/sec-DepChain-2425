package classes;

public class Tuple {
    int timestamp=0;
    String value="";

    public Tuple(int timestamp,String value){
        this.timestamp=timestamp;
        this.value = value;
    }

    public int getTimestamp(){
        return this.timestamp;
    }

    public String getValue(){
        return this.value;
    }
    @Override
    public String toString(){
        return "(" + Integer.toString(timestamp)+"," + value + ")";
    }
}
