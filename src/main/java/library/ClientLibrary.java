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
        this.eventBus.register(APLDeliverEvent.class, onTransactionResponse);
    }

    public CompletableFuture<Boolean> appendTransaction(String transactionName) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        pendingTransactions.put(transactionName, future);
        Message transaction = new Message("TRANSACTION###" + transactionName, config.getSelfId());
        // ✅ Publishes event (instead of sending network message)
        eventBus.publish(new APLSendEvent(transaction, DepChainConfig.LEADER));

        return future;
    }


    private final Consumer<APLDeliverEvent> onTransactionResponse = (event) -> {
        Message msg = event.arg;
        String payload = msg.getPayload();

        // ✅ Ensure the message is a transaction confirmation
        if (payload.startsWith("CONFIRMED###") || payload.startsWith("FAILED###")) {
            String transactionName = payload.substring(payload.indexOf("-") + 1);
            boolean success = payload.startsWith("CONFIRMED");

            // ✅ If the transaction was pending, complete it with success/failure
            if (pendingTransactions.containsKey(transactionName)) {
                pendingTransactions.get(transactionName).complete(success);
                pendingTransactions.remove(transactionName);
            }
        }
    };

    public void close() {
        eventBus.unregister(APLDeliverEvent.class, onTransactionResponse);
    }
}
