package depchain.consensus.cc;

import depchain.config.DepChainConfig;
import depchain.crypto.CryptoUtils;
import depchain.network.Message;
import depchain.network.apl.APLDeliverEvent;
import depchain.network.apl.APLSendEvent;
import depchain.network.apl.AuthenticatedPerfectLink;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ConditionalCollect {
    private static final String SEND_PREFIX = "SEND";
    private static final String COLLECTED_PREFIX = "COLLECTED";
    private static final String PAYLOAD_SEPARATOR = "###";
    private final AuthenticatedPerfectLink apl;
    private final Map<Integer, Message> messages = Collections.synchronizedMap(new HashMap<>());
    private final AtomicBoolean collected = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Lock lock = new ReentrantLock();
    private final Condition newMessage = lock.newCondition();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private Predicate<List<Message>> outputPredicate;
    private DepChainConfig config;
    private final Consumer<CCInputEvent> onInput = (event) -> {
        Message m = event.arg;
        byte[] signature = CryptoUtils.signMessage(m.getPayload(), config.getSelfPrivateKey());
        m.setSignature(signature);
        m.setPayload(SEND_PREFIX + PAYLOAD_SEPARATOR + m.getPayload());
        System.out.println(this + " onCCInput " + m);
        this.config.eventBus.publish(new APLSendEvent(m, DepChainConfig.LEADER));
    };

    private final Consumer<APLDeliverEvent> onDeliver = (event) -> {
        Message m = event.arg;
        String payload = m.getPayload();
        if (payload.startsWith(SEND_PREFIX) && this.config.selfId.equals(DepChainConfig.LEADER)) {
            payload = m.getPayload().split(SEND_PREFIX + PAYLOAD_SEPARATOR)[1];
            if (CryptoUtils.verifySignature(payload, m.getSignature(), config.getPublicKey(m.getSenderId()))) {
                messages.put(m.getSenderId(), new Message(payload, m.getSenderId(), m.getMac(), m.getSignature()));
                lock.lock();
                newMessage.signal();
                lock.unlock();
            }
        } else if (payload.startsWith(COLLECTED_PREFIX) && m.getSenderId() == DepChainConfig.LEADER) {
            List<Message> receivedMsgs = new ArrayList<>();
            payload = m.getPayload().split(COLLECTED_PREFIX + PAYLOAD_SEPARATOR)[1];
            for (String s : payload.split(PAYLOAD_SEPARATOR)) {
                Message mi = Message.fromStr(s);
                if (mi == null) {
                    System.err.println("Invalid message received: " + s);
                    continue;
                }

                receivedMsgs.add(mi);
            }
            if (!this.collected.get() && receivedMsgs.size() >= DepChainConfig.PROCESSES - DepChainConfig.FAIL_BOUND && this.outputPredicate.test(receivedMsgs)) {
                for (Message mj : receivedMsgs) {
                    if (!CryptoUtils.verifySignature(mj.getPayload(), mj.getSignature(),
                            config.getPublicKey(mj.getSenderId()))) {
                        System.err.println("CC onAPLDeliver could not verify signature - " + mj);
                        return;
                    }
//                    System.out.println(mj);
                }

                this.collected.set(true);
                this.config.eventBus.publish(new CCCollectedEvent(receivedMsgs));
            }
        }
    };

    public ConditionalCollect(DepChainConfig config, Predicate<List<Message>> outputPredicate) {
        this.config = config;
        this.apl = new AuthenticatedPerfectLink(config);
        this.outputPredicate = outputPredicate;

        this.config.eventBus.register(CCInputEvent.class, onInput);
        this.config.eventBus.register(APLDeliverEvent.class, onDeliver);

        this.executor.submit(this::watchCollectedMessages);
    }

    public void watchCollectedMessages() {
        while (running.get() && this.config.selfId.equals(DepChainConfig.LEADER)) {
            try {
                this.lock.lock();
                while (this.messages.isEmpty()) {
                    this.newMessage.await();
                }

                if (this.messages.size() >= DepChainConfig.PROCESSES - DepChainConfig.FAIL_BOUND && outputPredicate.test(new ArrayList<>(this.messages.values()))) {
                    StringBuilder payload = new StringBuilder(COLLECTED_PREFIX + PAYLOAD_SEPARATOR);
                    for (Message m : this.messages.values()) {
                        payload.append(m);
                        payload.append(PAYLOAD_SEPARATOR);
                    }

                    Message m = new Message(payload.toString(), this.config.selfId);
                    for (int i = 0; i < DepChainConfig.PROCESSES; i++) {
                        this.config.eventBus.publish(new APLSendEvent(m, i));
                    }
                    this.messages.clear();
                }
            } catch (InterruptedException e) {
                System.out.println("CC interrupted while watching collected messages");
                Thread.currentThread().interrupt();
            } finally {
                this.lock.unlock();
            }
        }
    }

    public void close() {
        this.running.set(false);
        this.lock.lock();
        this.newMessage.signalAll();
        this.lock.unlock();
        this.executor.shutdownNow();
        this.apl.close();

        this.config.eventBus.unregister(CCInputEvent.class, onInput);
        this.config.eventBus.unregister(APLDeliverEvent.class, onDeliver);
    }

    @Override
    public String toString() {
        return "ConditionalCollect{" + "id=" + config.selfId + '}';
    }
}
