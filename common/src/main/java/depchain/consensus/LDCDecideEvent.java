package depchain.consensus;

import depchain.events.Event;

public class LDCDecideEvent extends Event<String> {
    public LDCDecideEvent(String decision) {
        super(decision);
    }
}
