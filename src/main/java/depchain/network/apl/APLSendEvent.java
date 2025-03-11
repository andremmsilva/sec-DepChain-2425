package depchain.network.apl;

import depchain.events.Event;
import depchain.network.Message;

public class APLSendEvent extends Event<Message> {
    public APLSendEvent(Message message) {
        super(message);
    }
}
