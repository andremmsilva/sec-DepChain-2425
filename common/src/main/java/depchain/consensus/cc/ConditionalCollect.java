package depchain.consensus.cc;

import com.google.gson.Gson;
import depchain.config.DepChainConfig;
import depchain.crypto.CryptoUtils;
import depchain.network.apl.APLDeliverEvent;
import depchain.network.apl.APLSendEvent;
import depchain.network.message.Message;
import depchain.network.message.MessagePayload;
import depchain.network.message.MessageType;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ConditionalCollect {
    private final Map<Integer, Message> messages = Collections.synchronizedMap(new HashMap<>());
    private final AtomicBoolean collected = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Lock lock = new ReentrantLock();
    private final Condition newMessage = lock.newCondition();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private Predicate<List<Message>> outputPredicate;
    private DepChainConfig config;

    private final Consumer<CCInputEvent> onInput = (event) -> {
        // Send to leader
        Message m = event.arg;
        m.setPayload(new MessagePayload(MessageType.SEND, m.getPayload().getData()));
        m.setSenderId(this.config.getSelfId());
        byte[] signature = CryptoUtils.signMessage(m.getPayload().serialize(new Gson()), config.getSelfPrivateKey());
        m.setSignature(signature);
        this.config.eventBus.publish(new APLSendEvent(m, DepChainConfig.LEADER));
    };

    private final Consumer<APLDeliverEvent> onDeliver = (event) -> {
        Message m = event.arg;
        MessagePayload payload = m.getPayload();

        if (payload.getType().equals(MessageType.SEND) && this.config.selfId.equals(DepChainConfig.LEADER)) {
            // Leader received SEND...
            if (CryptoUtils.verifySignature(payload.serialize(new Gson()), m.getSignature(),
                    config.getPublicKey(m.getSenderId()))) {
                lock.lock();
                messages.put(m.getSenderId(),
                        new Message(m));
                newMessage.signal();
                lock.unlock();
//                System.out.println(this + " onAPLDeliver SEND " + m.getPayload().serialize(new Gson()));
            } else {
                System.out.println(this + " onAPLDeliver could not verify signature " + m.getPayload().serialize(new Gson()));
            }
        } else if (payload.getType().equals(MessageType.COLLECTED) && m.getSenderId() == DepChainConfig.LEADER) {
//            System.out.println(this + " onAPLDeliver COLLECTED from " + m.getSenderId());

            Map<String, Message> receivedMsgs = new HashMap<>();
            for (Map.Entry<String, String> e : m.getPayload().getParameters().entrySet()) {
                Message mi = Message.deserialize(new Gson(), e.getValue());
                if (mi == null) {
                    System.err.println("Invalid message received: " + e.getValue());
                    continue;
                }
                try {
                    // Check if this is a process ID and not another parameter
                    Integer.parseInt(e.getKey());
                } catch (NumberFormatException ex) {
                    continue;
                }

                receivedMsgs.put(e.getKey(), mi);
            }

            if (!this.collected.get() &&
                    receivedMsgs.size() >= DepChainConfig.PROCESSES - DepChainConfig.FAIL_BOUND &&
                    this.outputPredicate.test(new ArrayList<>(receivedMsgs.values()))) {
                for (Message mj : receivedMsgs.values()) {
                    if (!CryptoUtils.verifySignature(mj.getPayload().serialize(new Gson()), mj.getSignature(),
                            config.getPublicKey(mj.getSenderId()))) {
                        System.err.println("CC onAPLDeliver could not verify signature - " + mj.serialize(new Gson()));
                        return;
                    }
                }

                this.collected.set(true);
                this.config.eventBus.publish(new CCCollectedEvent(new ArrayList<>(receivedMsgs.values())));
            }
        }
    };

    public ConditionalCollect(DepChainConfig config, Predicate<List<Message>> outputPredicate) {
        this.config = config;
        this.outputPredicate = outputPredicate;

        this.config.eventBus.register(CCInputEvent.class, onInput);
        this.config.eventBus.register(APLDeliverEvent.class, onDeliver);

        this.executor.submit(this::watchCollectedMessages);
    }

    public void watchCollectedMessages() {
        while (running.get() && this.config.selfId.equals(DepChainConfig.LEADER)) {
            try {
                this.lock.lock();
                while (this.messages.size() < DepChainConfig.PROCESSES - DepChainConfig.FAIL_BOUND) {
                    this.newMessage.await();
                    if (!running.get()) {
                        return;
                    }
                }

                if (this.messages.size() >= DepChainConfig.PROCESSES - DepChainConfig.FAIL_BOUND &&
                        outputPredicate.test(new ArrayList<>(this.messages.values()))) {
                    this.newMessage.await(2, TimeUnit.SECONDS);
                    MessagePayload payload = new MessagePayload(MessageType.COLLECTED, "", new HashMap<>());
                    for (Map.Entry<Integer, Message> entry : this.messages.entrySet()) {
                        payload.addParameter(String.valueOf(entry.getKey()), entry.getValue().serialize(new Gson()));
                    }

                    Message m = new Message(payload, this.config.selfId);
                    for (int i = 0; i < DepChainConfig.PROCESSES; i++) {
//                        System.out.println(this + " sending COLLECTED to " + i);
                        this.config.eventBus.publish(new APLSendEvent(new Message(m), i));
                    }
                    this.messages.clear();
                }
            } catch (InterruptedException e) {
                System.out.println("CC interrupted while watching collected messages");
                Thread.currentThread().interrupt();
                break;
            } finally {
                this.lock.unlock();
            }
        }
    }

    public void close() {
        this.running.set(false);
        this.lock.lock();
        try {
            this.newMessage.signalAll(); // Wake up the thread immediately
        } finally {
            this.lock.unlock();
        }
        this.executor.shutdownNow(); // Forcefully terminate the thread pool

        this.config.eventBus.unregister(CCInputEvent.class, onInput);
        this.config.eventBus.unregister(APLDeliverEvent.class, onDeliver);
    }

    @Override
    public String toString() {
        return "ConditionalCollect{" + "id=" + config.selfId + '}';
    }
}
