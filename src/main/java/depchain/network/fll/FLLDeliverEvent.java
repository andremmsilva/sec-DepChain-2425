package depchain.network.fll;

import depchain.events.Event;
import depchain.network.Message;

public class FLLDeliverEvent extends Event<Message> {
    public FLLDeliverEvent(Message message) {
        super(message);
    }
}
