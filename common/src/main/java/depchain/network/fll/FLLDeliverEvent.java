package depchain.network.fll;

import depchain.events.Event;
import depchain.network.message.Message;

public class FLLDeliverEvent extends Event<Message> {
    public FLLDeliverEvent(Message message) {
        super(message);
    }
}
