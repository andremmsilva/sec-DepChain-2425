package classes;

import java.util.ArrayList;
import java.util.List;

public class State {
    Tuple last_round;
    List<Tuple> writeset;

    public State(){
        this.last_round = new Tuple(0,"");
        this.writeset = new ArrayList<>();
    }

    public State(Tuple tuple, List<Tuple> writeset){
        this.last_round = tuple;
        this.writeset = writeset;
    }

    public Tuple getLastRound(){
        return this.last_round;
    }

    public void setLastRound(Tuple newRound){
        this.last_round = newRound;
    }

    public List<Tuple> getWriteSet(){
        return this.writeset;
    }

    public void setWriteSet(List<Tuple> newWriteset){
        this.writeset = newWriteset;
    }

    public void addToWriteSet(Tuple newWriteTuple){
        this.writeset.add(newWriteTuple);
    }
    @Override
    public String toString(){
        return '[' + last_round.getTimestamp() + ',' + last_round.getValue() + ',' + writeSetToString() + ']';
    }

    public String writeSetToString() {
        StringBuilder result = new StringBuilder();
        result.append("{");
    
        for (int i = 0; i < writeset.size(); i++) {
            Tuple tuple = writeset.get(i);
            result.append(tuple.toString());
            if (i < writeset.size() - 1) {
                result.append(";");
            }
        }
    
        result.append("}");
        return result.toString();
    }    

}





