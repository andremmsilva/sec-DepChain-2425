package depchain.network.apl;

import depchain.events.Event;
import depchain.network.Message;

public class APLDeliverEvent extends Event<Message> {
    public APLDeliverEvent(Message message) {
        super(message);
    }
}
