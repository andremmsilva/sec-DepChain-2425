package depchain.network.fll;

import depchain.events.Event;
import depchain.network.Message;

public class FLLSendEvent extends Event<Message> {
    public FLLSendEvent(Message message) {
        super(message);
    }
}
