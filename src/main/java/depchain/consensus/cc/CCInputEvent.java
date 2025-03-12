package depchain.consensus.cc;

import depchain.events.Event;
import depchain.network.Message;

public class CCInputEvent extends Event<Message> {
    public CCInputEvent(Message message) {
        super(message);
    }
}
