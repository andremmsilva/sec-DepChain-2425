package depchain.network.sl;

import depchain.events.Event;
import depchain.network.message.Message;

public class SLDeliverEvent extends Event<Message> {
    public SLDeliverEvent(Message message) {
        super(message);
    }
}
