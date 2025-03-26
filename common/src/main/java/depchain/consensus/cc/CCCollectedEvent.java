package depchain.consensus.cc;

import depchain.events.Event;
import depchain.network.message.Message;

import java.util.List;

public class CCCollectedEvent extends Event<List<Message>> {
    public CCCollectedEvent(List<Message> messages) {
        super(messages);
    }
}
