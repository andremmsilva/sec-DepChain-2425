package depchain.consensus.rw;

import depchain.events.Event;
import depchain.network.Message;

public class AcceptEvent extends Event<Message> {
    public AcceptEvent(Message m) {
        super(m);
    }
}
