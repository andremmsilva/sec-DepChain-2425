package depchain;

import depchain.network.message.MessagePayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TransactionQueue {
    private final Queue<MessagePayload> pendingTransactions = new ConcurrentLinkedQueue<>();
    private final int batchSize;

    public TransactionQueue(int batchSize) {
        this.batchSize = batchSize;
    }

    public synchronized void add(MessagePayload transaction) {
        pendingTransactions.add(transaction);
    }

    public synchronized List<MessagePayload> takeBatch() {
        List<MessagePayload> batch = new ArrayList<>();
        while (batch.size() < batchSize && !pendingTransactions.isEmpty()) {
            MessagePayload tx = pendingTransactions.poll();
            if (tx != null)
                batch.add(tx);
        }
        return batch;
    }

    public synchronized boolean hasCapacity() {
        return pendingTransactions.size() >= batchSize;
    }

    public synchronized int size() {
        return pendingTransactions.size();
    }

    public synchronized boolean isEmpty() {
        return pendingTransactions.isEmpty();
    }
}
