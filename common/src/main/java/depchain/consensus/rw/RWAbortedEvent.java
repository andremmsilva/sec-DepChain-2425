package depchain.consensus.rw;

import depchain.consensus.utils.State;
import depchain.events.Event;

public class RWAbortedEvent extends Event<State> {
    public RWAbortedEvent(State state) {
        super(state);
    }
}
