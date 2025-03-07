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
        return last_round.toString() + writeset.toString();
    } 



}





