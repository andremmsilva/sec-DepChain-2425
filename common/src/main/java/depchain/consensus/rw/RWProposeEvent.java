package depchain.consensus.rw;

import depchain.events.Event;
import depchain.network.message.Transaction;

public class RWProposeEvent extends Event<Transaction> {
    public RWProposeEvent(Transaction v) {
        super(v);
    }
}
