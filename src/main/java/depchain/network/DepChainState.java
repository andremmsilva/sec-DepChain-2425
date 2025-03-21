package depchain.network;

import java.util.List;

import depchain.classes.State;
import depchain.config.DepChainConfig;
import depchain.network.apl.AuthenticatedPerfectLink;

public class DepChainState {
    private final DepChainConfig config;
    private volatile AuthenticatedPerfectLink link;
    private volatile State state;
    private int currentEpoch = 0;

    public DepChainState(DepChainConfig config, AuthenticatedPerfectLink link){
        this.config = config;
        this.state = new State();
        this.link = link;
    }

    public int getEpoch() {
        return currentEpoch;
    }

    public AuthenticatedPerfectLink getLink(int id){
        return link;
    }

    public DepChainConfig getConfig(){
        return config;
    }

    public State getState(){
        return state;
    }

    public void shutdown() {
        link.close();
        config.eventBus.shutdown();
    }
    
}
