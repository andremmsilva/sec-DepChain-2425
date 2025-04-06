package client;

import depchain.config.DepChainConfig;
import depchain.crypto.CryptoUtils;
import depchain.network.message.Message;
import depchain.network.message.MessagePayload;
import depchain.network.message.MessageType;
import depchain.network.message.Transaction;
import depchain.network.sl.SLDeliverEvent;
import depchain.network.sl.SLSendEvent;
import depchain.network.sl.StubbornLink;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class DepChainClientCore implements IDepChainClient {
    private final DepChainConfig config;

    public DepChainClientCore(int id) {
        this.config = new DepChainConfig(id);
        // We are not concerned with attacks where a compromised
        // library tries to attack an honest application, hence no APL used here
        // The sl is "unused", since we only need it to process events, and never call it directly
        @SuppressWarnings("unused") StubbornLink sl = new StubbornLink(this.config);
    }

    private boolean operation(Message message, Map<Integer, String> responses) {
        AtomicInteger replyCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        Lock lock = new ReentrantLock();
        Condition newMessageCond = lock.newCondition();

        // Register the response handler
        Consumer<SLDeliverEvent> onResponse = e -> {
            Message res = e.arg;
            lock.lock();
            try {
                newMessageCond.signal();
                replyCount.incrementAndGet();
                boolean success = res.getPayload().getType().equals(MessageType.TRANSACTION_CONFIRMED);
                if (success) {
                    successCount.incrementAndGet();
                    responses.put(res.getSenderId(), res.getPayload().getData());
                }
            } finally {
                lock.unlock();
            }
        };
        config.eventBus.register(SLDeliverEvent.class, onResponse);

        // Sign the message
        message.setSignature(CryptoUtils.signMessage(message.getPayload().serialize(config.gson),
                config.getSelfPrivateKey()));

        // Send the actual message to all nodes
        for (int i = 0; i < DepChainConfig.PROCESSES; i++) {
            config.eventBus.publish(new SLSendEvent(message, i));
        }

        try {
            lock.lock();
            // If we get all replies or F + 1 positive replies, don't wait longer
            while (replyCount.get() < DepChainConfig.PROCESSES ||
                    successCount.get() < DepChainConfig.FAIL_BOUND + 1) {
                // Block until a message arrives
                if (!newMessageCond.await(10, TimeUnit.SECONDS)) {
                    // 10 seconds have passed, giving up on obtaining F + 1 positive replies
                    return false;
                }
            }
            return successCount.get() >= DepChainConfig.FAIL_BOUND + 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("DepChainClient@append: " + e.getMessage());
            return false;
        } finally {
            lock.unlock();
            config.eventBus.unregister(SLDeliverEvent.class, onResponse);
        }
    }

    @Override
    public long getBalance() {
        String senderAddress = config.getSelfAccountAddress();
        Transaction t = new Transaction(senderAddress, null);
        Message m = new Message(new MessagePayload(MessageType.BALANCE_REQ, t.serialize(config.gson)),
                config.selfId);
        Map<Integer, String> responses = new HashMap<>();
        if (operation(m, responses)) {
            // Check if all returned values are the same
            if (responses.values().stream().allMatch(v -> v.equals(responses.get(DepChainConfig.LEADER)))) {
                return Long.parseLong(responses.get(DepChainConfig.LEADER));
            }
        }
        // Got different values or not enough successful replies
        return -1;
    }

    @Override
    public boolean transfer(String receiverAddress, long amount, String currency) {
        String senderAddress = config.getSelfAccountAddress();
        Transaction t = new Transaction(senderAddress, receiverAddress, amount, currency);
        Message m = new Message(new MessagePayload(MessageType.TRANSFER_REQ, t.serialize(config.gson)),
                config.selfId);
        Map<Integer, String> responses = new HashMap<>();
        return operation(m, responses);
    }

    @Override
    public boolean transferFrom(String senderAddress, String receiverAddress, long amount, String currency) {
        Transaction t = new Transaction(senderAddress, receiverAddress, amount, currency);
        Message m = new Message(new MessagePayload(MessageType.TRANSFERFROM_REQ, t.serialize(config.gson)),
                config.selfId);
        Map<Integer, String> responses = new HashMap<>();
        return operation(m, responses);
    }
}
