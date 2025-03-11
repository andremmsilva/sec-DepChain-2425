package depchain.network.apl;

import depchain.events.Event;
import depchain.network.Message;

public class APLSendKeyEvent extends Event<Message> {
    public APLSendKeyEvent(Message message) {
        super(message);
    }
}
