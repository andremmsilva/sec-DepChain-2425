package depchain.network.apl;

import depchain.events.Event;
import depchain.network.Message;

public class APLSendEvent extends Event<Message> {
    private final int targetId;

    public APLSendEvent(Message message, int targetId) {
        super(message);
        this.targetId = targetId;
    }

    public int getTargetId() {
        return targetId;
    }
}
