package depchain.consensus;

import depchain.events.Event;
import depchain.network.message.Transaction;

public class LDCDecideEvent extends Event<Transaction> {
    public LDCDecideEvent(Transaction decision) {
        super(decision);
    }
}
