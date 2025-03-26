package depchain.consensus.rw;

import depchain.events.Event;

public class RWProposeEvent extends Event<String> {
    public RWProposeEvent(String v) {
        super(v);
    }
}
