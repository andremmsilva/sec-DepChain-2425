package depchain.network.rw;

import depchain.config.DepChainConfig;


public class ReadWrite {
    String[] written =  new String[DepChainConfig.PROCESSES];
    String[] accepted =  new String[DepChainConfig.PROCESSES];

    public ReadWrite (DepChainState serverState) {

    }
    
}

/*
public class ReadWrite {   
    DepChainState serverState;
    String[] written =  new String[DepChainConfig.PROCESSES];
    String[] accepted =  new String[DepChainConfig.PROCESSES];
    SignedConditionalCollect collect = new SignedConditionalCollect(null);

    //upon event ⟨ bep, Init | epochstate ⟩
    public ReadWrite (DepChainState serverState) {
        this.serverState=serverState;

        for (int i = 0; i < DepChainConfig.PROCESSES; i++) {
            written[i] = null;
            accepted[i] = null; 
        }
    }

    //upon event ⟨ bep, Propose | v ⟩  Only leader
    public void propose(String val) throws UnknownHostException  {
        if (!Objects.equals(DepChainConfig.SELF_ID, DepChainConfig.LEADER)) {
            return;
        }
        if (this.serverState.getState().getLastRound().getValue() == null) {
            Tuple tuple = new Tuple(this.serverState.getEpoch(), val);
            this.serverState.getState().setLastRound(tuple);
        }
        broadcast(serverState, "READ");
    }

    //upon event ⟨ al, Deliver | p, [ READ ] ⟩ such that p = LEADER
    public void sendState() {
        String MessageString = "STATE-" + this.serverState.getState().toString();
        int leader =  DepChainConfig.LEADER;
        int id = DepChainConfig.SELF_ID;
        Message message = new Message(InetAddress.getLocalHost(), leader, MessageString, DepChainConfig.PRIVATE_KEY.getEncoded(), id);
        SignedConditionalCollect.input(message);
    }

    //upon event ⟨ cc, Collected | states ⟩
    public void collected(List<State> states) {
        String tmpval = null;
    
        // 1. Check if there exists (ts >= 0, v != ⊥) from states that "binds(ts, v, states)"
        //    For simplicity, we’ll look for any non-null state with a valid timestamp/value.
        //    Adjust logic if your "binds(...)" condition is more specific.
        for (State st : states) {
            if (st != null 
                && st.getLastRound() != null 
                && st.getLastRound().getValue() != null) {
                
                // If you store ts in the Tuple, you can check st.getLastRound().getTs() >= 0
                if (st.getLastRound().getTimestamp() >= 0) {
                    tmpval = st.getLastRound().getValue();
                    break;
                }
            }
        }
    
        // 2. If tmpval is still null, check the "unbound(states)" condition
        //    (pseudocode says: else if there exists v != ⊥ such that unbound(states) & states[q] = [STATE, -, v])
        //    This is highly application-specific. Below is a placeholder:
        if (tmpval == null) {
            for (State st : states) {
                if (st != null 
                    && st.getLastRound() != null 
                    && st.getLastRound().getValue() != null) {
                    
                    // If your "unbound" logic is simply that no timestamp was set (ts = -1 or similar)
                    if (st.getLastRound().getTs() < 0) {
                        tmpval = st.getLastRound().getValue();
                        break;
                    }
                }
            }
        }
    
        // 3. If tmpval != ⊥ (not null), do the final steps:
        //    - Possibly update writeset, e.g., writeset.add((ts, tmpval))
        //    - broadcast [WRITE, tmpval]
        if (tmpval != null) {
            // (Optional) If your algorithm maintains a writeset, add the pair (ts, tmpval) here.
            // writeset.add(new Tuple(ts, tmpval));
    
            try {
                // Pseudocode: for all q ∈ P do trigger <al, Send | q, [WRITE, tmpval]>
                broadcast(serverState, "WRITE-" + tmpval);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    

    public void broadcast(DepChainState serverState, String toSend) throws UnknownHostException {
        for (int i = 0; i + DepChainConfig.BASE_PORT < DepChainConfig.BASE_PORT + DepChainConfig.PROCESSES; i++) {
            serverState.getLink(i).sendMessage(InetAddress.getByName(DepChainConfig.ADDRESS), DepChainConfig.BASE_PORT + i, toSend);
            // change to signed Condicional collect -> event ⟨ cc, Input | m ⟩
        }
    }
} */