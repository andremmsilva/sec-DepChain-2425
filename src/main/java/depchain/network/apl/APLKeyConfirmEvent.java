package depchain.network.apl;

import depchain.events.Event;
import depchain.network.Message;

public class APLKeyConfirmEvent extends Event<Message> {
    public APLKeyConfirmEvent(Message message) {
        super(message);
    }
}
