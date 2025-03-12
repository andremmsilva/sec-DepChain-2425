package depchain.network.sl;

import depchain.events.Event;
import depchain.network.Message;

public class SLSendEvent extends Event<Message> {
    private final int targetId;

    public SLSendEvent(Message message, int targetId) {
        super(message);
        this.targetId = targetId;
    }

    public int getTargetId() {
        return targetId;
    }
}
