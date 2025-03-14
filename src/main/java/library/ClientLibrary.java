package library;

import depchain.network.sl.*;
import depchain.network.apl.*;
import depchain.network.rw.ProposeEvent;
import depchain.network.Message;
import depchain.config.DepChainConfig;
import depchain.events.EventBus;

import java.net.InetAddress;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class ClientLibrary {
    private final DepChainConfig config;
    private final EventBus eventBus;
    private final ConcurrentHashMap<String, CompletableFuture<Boolean>> pendingTransactions = new ConcurrentHashMap<String, CompletableFuture<Boolean>>();

    public ClientLibrary(DepChainConfig config) {
        this.config = config;
        this.eventBus = config.eventBus;

        // Register listener for transaction confirmations
        // ✅ Listen for transaction confirmation events
        this.eventBus.register(TransactionConfirmedEvent.class, onTransactionConfirmed);
    }

    public CompletableFuture<Boolean> appendTransaction(String transactionName) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        pendingTransactions.put(transactionName, future);
        Message transaction = new Message(transactionName, config.getSelfId());
        // ✅ Publishes event (instead of sending network message)
        eventBus.publish(new ProposeEvent(transaction));

        return future;
    }


    private final Consumer<TransactionConfirmedEvent> onTransactionConfirmed = (event) -> {
        String transactionName = event.getTransactionId();
        boolean success = event.isSuccess();

        if (pendingTransactions.containsKey(transactionName)) {
            pendingTransactions.get(transactionName).complete(success); // Mark as successful
            pendingTransactions.remove(transactionName);
        }
    };

    public void close() {
        eventBus.unregister(TransactionConfirmedEvent.class, onTransactionConfirmed);
    }
}
