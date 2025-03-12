package depchain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import classes.State;
import classes.Tuple;
import depchain.config.DepChainConfig;
import depchain.network.apl.AuthenticatedPerfectLink;

public class DepChainState {
    private volatile List<State> collected = new ArrayList<>(); 
    private volatile List<Tuple> writeList = new ArrayList<>();
    private volatile List<Integer> acceptlist = new ArrayList<>();
    private final DepChainConfig config;
    private volatile List<AuthenticatedPerfectLink>links;
    private volatile State state;
    private int currentEpoch = 0;

    public DepChainState(DepChainConfig config, List<AuthenticatedPerfectLink> links){
        this.config = config;
        this.links = links;
        this.state = new State();
    }

    public List<State> getCollected() {
        return collected;
    }

    public int getEpoch() {
        return currentEpoch;
    }

    public void addStateToCollected(int id, State state){
        collected.add(id, state);
    }

    public void addToWriteList(Tuple tuple){
        writeList.add(tuple);
    }

    public List<Tuple> getWriteList() {
        return writeList;
    }

    public void addToAcceptList(int id){
        acceptlist.add(id, 1);
    }

    public AuthenticatedPerfectLink getLink(int id){
        return links.get(id);
    }

    public DepChainConfig getConfig(){
        return config;
    }

    public State getState(){
        return state;
    }

    public String collectedToMessage() {
        String message = "COLLECTED-(";
        int i = 1;
        for (State state : collected) {
            message = message + state.toString();
            if (i < collected.size()) {
                message = message + '|';
            }
            i++;
        }
        message = message + ")";
        return message;
    }

    public String hasFWriteQuorum() {
        int f = DepChainConfig.PROCESSES  / 3;
        Map<String, Integer> valueOccurrences = new HashMap<>();
    
        for (Tuple tuple : writeList) {
            String val = tuple.getValue();
            valueOccurrences.put(val, valueOccurrences.getOrDefault(val, 0) + 1);
            if (valueOccurrences.get(val) >= f + 1) {
                return val;
            }
        }
    
        return null;
    }
    
}
