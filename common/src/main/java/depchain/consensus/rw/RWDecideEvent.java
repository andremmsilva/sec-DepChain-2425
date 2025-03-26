package depchain.consensus.rw;

import depchain.events.Event;

public class RWDecideEvent extends Event<String> {
    public RWDecideEvent(String v) {
        super(v);
    }
}
