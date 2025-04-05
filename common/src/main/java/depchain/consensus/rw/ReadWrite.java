package depchain.consensus.rw;

import com.google.gson.Gson;
import depchain.config.DepChainConfig;
import depchain.consensus.cc.CCCollectedEvent;
import depchain.consensus.cc.CCInputEvent;
import depchain.consensus.cc.ConditionalCollect;
import depchain.consensus.utils.Broadcast;
import depchain.consensus.utils.Sound;
import depchain.consensus.utils.State;
import depchain.consensus.utils.Tuple;
import depchain.network.apl.APLDeliverEvent;
import depchain.network.message.Message;
import depchain.network.message.MessagePayload;
import depchain.network.message.MessageType;
import depchain.network.message.Transaction;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class ReadWrite {
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Lock lock = new ReentrantLock();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Condition writtenCond = lock.newCondition();
    private final Condition acceptedCond = lock.newCondition();
    private final ConditionalCollect cc;
    private final Sound sound = new Sound();
    private final Map<Integer, Transaction> written =
            Collections.synchronizedMap(new HashMap<>(DepChainConfig.PROCESSES));
    private final Map<Integer, Transaction> accepted =
            Collections.synchronizedMap(new HashMap<>(DepChainConfig.PROCESSES));
    private Integer ets;
    private DepChainConfig config;
    private State epochState;
    private final Consumer<CCCollectedEvent> onCCCollected = e -> {
        // Build states map
        Map<Integer, State> states = new HashMap<>();
        e.arg.forEach(m -> {
            State s = State.deserialize(new Gson(), m.getPayload().getData());
            if (s != null) {
                states.put(m.getSenderId(), s);
//                System.out.println(this + " onCCCollected " + s + " from " + m.getSenderId());
            }
        });

        Transaction tmpVal = null;
        for (State state : states.values()) {
            if (this.sound.binds(state.getLastRound(), new ArrayList<>(states.values()))) {
                tmpVal = state.getLastRound().getValue();
                break;
            }
        }

        if (tmpVal.isEmpty()) {
            if (sound.unbound(new ArrayList<>(states.values())) &&
                    !Objects.equals(states.get(DepChainConfig.LEADER).getLastRound().getValue(), "")) {
                tmpVal = states.get(DepChainConfig.LEADER).getLastRound().getValue();
            }
        }

        if (!tmpVal.isEmpty()) {
            Set<Tuple> newWs = new HashSet<>();
            for (Tuple t : this.epochState.getWriteSet()) {
                if (t.getValue().equals(tmpVal)) {
                    newWs.add(new Tuple(ets, tmpVal));
                }
            }

            this.epochState.setWriteSet(newWs);

            Broadcast.all(new MessagePayload(MessageType.WRITE, tmpVal), this.config);
        }
    };

    private final Consumer<RWProposeEvent> onPropose = e -> {
        // Only leader can propose
        if (!Objects.equals(DepChainConfig.LEADER, this.config.selfId)) {
            return;
        }

        if (Objects.equals(this.epochState.getLastRound().getValue(), "")) {
            this.epochState.setLastRound(new Tuple(this.epochState.getLastRound().getTimestamp(), e.arg));
        }
        System.out.println(this + " proposing " + e.arg);

        Broadcast.all(new MessagePayload(MessageType.READ, ""), this.config);
    };

    private final Consumer<APLDeliverEvent> onAPLDeliver = e -> {
        if (e.arg.getPayload().getType().equals(MessageType.READ) && e.arg.getSenderId() == DepChainConfig.LEADER) {
//            System.out.println(this + " got READ, sending CCInput " + this.epochState.serialize(new Gson()));
            this.config.eventBus.publish(
                    new CCInputEvent(new Message(new MessagePayload(null, this.epochState.serialize(new Gson())),
                            this.config.selfId)));
        } else if (e.arg.getPayload().getType().equals(MessageType.WRITE)) {
            Transaction val = e.arg.getPayload().getTransaction();
//            System.out.println(this + " got WRITE " + val + " from " + e.arg.getSenderId());
            try {
                this.lock.lock();
                this.written.put(e.arg.getSenderId(), val);
                this.writtenCond.signal();
            } finally {
                this.lock.unlock();
            }

        } else if (e.arg.getPayload().getType().equals(MessageType.ACCEPT)) {
            Transaction val = e.arg.getPayload().getTransaction();
//            System.out.println(this + " got ACCEPT " + val + " from " + e.arg.getSenderId());
            try {
                this.lock.lock();
                this.accepted.put(e.arg.getSenderId(), val);
                this.acceptedCond.signal();
            } finally {
                this.lock.unlock();
            }
        }
    };

    public ReadWrite(DepChainConfig config) {
        this(config, new State(), 0);
    }

    public ReadWrite(DepChainConfig config, State epochState, Integer ets) {
        this.config = config;
        this.ets = ets;
        this.epochState = epochState;
        this.cc = new ConditionalCollect(this.config, sound.getPredicate());

        this.config.eventBus.register(RWProposeEvent.class, onPropose);
        this.config.eventBus.register(APLDeliverEvent.class, onAPLDeliver);
        this.config.eventBus.register(CCCollectedEvent.class, onCCCollected);

        this.executor.submit(this::watchWritten);
        this.executor.submit(this::watchAccepted);
    }

    public void watchWritten() {
        while (this.running.get()) {
            try {
                this.lock.lock();
                this.writtenCond.await();
                Map<Transaction, Integer> appearances = new HashMap<>();

                this.written.values().forEach(s -> appearances.put(s, appearances.getOrDefault(s, 0) + 1));
                for (Map.Entry<Transaction, Integer> app : appearances.entrySet()) {
                    if (app.getValue() > (DepChainConfig.PROCESSES + DepChainConfig.FAIL_BOUND) / 2) {
                        this.epochState.setLastRound(new Tuple(this.ets, app.getKey()));
                        this.written.clear();
                        Broadcast.all(new MessagePayload(MessageType.ACCEPT, app.getKey()), this.config);
                        break;
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                e.printStackTrace();
            } finally {
                this.lock.unlock();
            }
        }
    }

    public void watchAccepted() {
        while (this.running.get()) {
            try {
                this.lock.lock();
                this.acceptedCond.await();
                Map<Transaction, Integer> appearances = new HashMap<>();

                this.accepted.values().forEach(s -> appearances.put(s, appearances.getOrDefault(s, 0) + 1));
                for (Map.Entry<Transaction, Integer> app : appearances.entrySet()) {
                    if (app.getValue() > (DepChainConfig.PROCESSES + DepChainConfig.FAIL_BOUND) / 2) {
                        this.accepted.clear();
                        this.config.eventBus.publish(new RWDecideEvent(app.getKey()));
                        break;
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                e.printStackTrace();
            } finally {
                this.lock.unlock();
            }
        }
    }

    public State getEpochState() {
        return epochState;
    }

    public void setEpochState(State epochState) {
        this.epochState = epochState;
    }

    public void close() {
        this.cc.close();
        if (this.running.get()) {
            this.config.eventBus.unregister(RWProposeEvent.class, onPropose);
            this.config.eventBus.unregister(APLDeliverEvent.class, onAPLDeliver);
            this.config.eventBus.unregister(CCCollectedEvent.class, onCCCollected);

            this.running.set(false);
            try {
                this.lock.lock();
                this.writtenCond.signal();
                this.acceptedCond.signal();
            } finally {
                this.lock.unlock();
            }
            this.executor.shutdown();
            try {
                if (!this.executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    this.executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                this.executor.shutdownNow();
                throw new RuntimeException(e);
            }
        }
        System.out.println(this + " shut down");
    }

    @Override
    public String toString() {
        return "RW " + this.config.selfId;
    }
}
