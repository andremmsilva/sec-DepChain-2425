package depchain.network.rw;

import depchain.events.Event;
import depchain.network.Message;

public class ProposeEvent extends Event<Message> {
    public ProposeEvent(Message m) {
        super(m);
    }
}
