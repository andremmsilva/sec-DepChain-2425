package depchain.consensus.rw;

import depchain.events.Event;

public class RWAbortEvent extends Event<Object> {
    public RWAbortEvent() {
        super(null);
    }
}
