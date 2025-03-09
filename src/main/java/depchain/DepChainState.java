package depchain;

import java.util.ArrayList;
import java.util.List;

import classes.State;
import classes.Tuple;
import depchain.config.DepChainConfig;
import depchain.network.AuthenticatedPerfectLink;

public class DepChainState {
    private volatile List<State> collected = new ArrayList<>(); 
    private volatile List<Tuple> writeList = new ArrayList<>();
    private volatile List<Integer> acceptlist = new ArrayList<>();
    private final DepChainConfig config;
    private volatile List<AuthenticatedPerfectLink>links;
    private volatile State state;

    public DepChainState(DepChainConfig config, List<AuthenticatedPerfectLink> links){
        this.config = config;
        this.links = links;
        this.state = new State();
    }

    public void addState(int id, State state){
        collected.add(id, state);
    }

    public void addToWriteList(Tuple tuple){
        writeList.add(tuple);
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

}
