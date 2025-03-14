package library;

import depchain.events.Event;

public class TransactionConfirmedEvent extends Event {
    private final String transactionId;
    private final boolean success;

    public TransactionConfirmedEvent(String transactionId, boolean success) {
        this.transactionId = transactionId;
        this.success = success;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public boolean isSuccess() {
        return success;
    }
}