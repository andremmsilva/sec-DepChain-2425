package depchain.network.fll;

import depchain.events.Event;
import depchain.network.message.Message;

public class FLLSendEvent extends Event<Message> {
    private final int targetId;

    public FLLSendEvent(Message message, int targetId) {
        super(message);
        this.targetId = targetId;
    }

    public int getTargetId() {
        return targetId;
    }
}
