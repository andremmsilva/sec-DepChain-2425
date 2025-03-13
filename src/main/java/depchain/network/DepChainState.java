package depchain.network;

import java.util.List;

import depchain.classes.State;
import depchain.config.DepChainConfig;
import depchain.network.apl.AuthenticatedPerfectLink;

public class DepChainState {
    private final DepChainConfig config;
    private volatile List<AuthenticatedPerfectLink>links;
    private volatile State state;
    private int currentEpoch = 0;

    public DepChainState(DepChainConfig config, List<AuthenticatedPerfectLink> links){
        this.config = config;
        this.links = links;
        this.state = new State();
    }

    public int getEpoch() {
        return currentEpoch;
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
