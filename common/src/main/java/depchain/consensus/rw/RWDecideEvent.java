package depchain.consensus.rw;

import depchain.events.Event;
import depchain.network.message.Transaction;

public class RWDecideEvent extends Event<Transaction> {
    public RWDecideEvent(Transaction v) {
        super(v);
    }
}
