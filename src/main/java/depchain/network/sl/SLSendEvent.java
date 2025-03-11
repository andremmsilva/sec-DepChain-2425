package depchain.network.sl;

import depchain.events.Event;
import depchain.network.Message;

public class SLSendEvent extends Event<Message> {
    public SLSendEvent(Message message) {
        super(message);
    }
}
