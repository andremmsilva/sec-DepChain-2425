package depchain.consensus;

import depchain.events.Event;

public class LDCProposeEvent extends Event<String> {
    public LDCProposeEvent(String value) {
        super(value);
    }
}
