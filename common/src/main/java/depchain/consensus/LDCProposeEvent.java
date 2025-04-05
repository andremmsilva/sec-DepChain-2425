package depchain.consensus;

import depchain.events.Event;
import depchain.network.message.Transaction;

public class LDCProposeEvent extends Event<Transaction> {
    public LDCProposeEvent(Transaction value) {
        super(value);
    }
}
