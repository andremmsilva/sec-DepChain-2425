package depchain.consensus;

import depchain.config.DepChainConfig;
import depchain.consensus.rw.RWDecideEvent;
import depchain.consensus.rw.RWProposeEvent;
import depchain.consensus.rw.ReadWrite;
import depchain.consensus.utils.State;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class ByzantineLeaderDrivenConsensus {
    private final ArrayList<ReadWrite> rwInstances = new ArrayList<>();
    private final AtomicReference<String> val = new AtomicReference<>("");
    private final AtomicReference<String> decision = new AtomicReference<>("");
    private final AtomicBoolean aborted = new AtomicBoolean(false);
    private final AtomicBoolean proposed = new AtomicBoolean(false);
    private final AtomicInteger ets = new AtomicInteger(0);
    private final Lock lock = new ReentrantLock();
    private final Condition proposedCond = lock.newCondition();
    private final Consumer<LDCProposeEvent> onLDCPropose = e -> {
        try {
            this.lock.lock();
            this.val.set(e.arg);
            proposedCond.signalAll();
            System.out.println(this + " received LDCProposeEvent " + e.arg);
        } finally {
            this.lock.unlock();
        }
    };
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private DepChainConfig config;
    private final Consumer<RWDecideEvent> onRWDecide = e -> {
        if (decision.get().isEmpty()) {
            decision.set(e.arg);
            this.config.eventBus.publish(new LDCDecideEvent(decision.get()));
            this.lock.lock();
            try {
                this.rwInstances.getFirst().close();
                this.rwInstances.clear();
            } finally {
                this.lock.unlock();
            }
        }
    };

    public ByzantineLeaderDrivenConsensus(DepChainConfig config) {
        this.config = config;
        // Create the first instance
        rwInstances.add(new ReadWrite(config, new State(), ets.get()));

        config.eventBus.register(LDCProposeEvent.class, onLDCPropose);
        config.eventBus.register(RWDecideEvent.class, onRWDecide);


        executor.submit(this::watchProposal);
        System.out.println(this + " started...");
    }


    public void shutdown() {
        config.eventBus.unregister(LDCProposeEvent.class, onLDCPropose);
        config.eventBus.unregister(RWDecideEvent.class, onRWDecide);

        this.running.set(false);
        try {
            this.lock.lock();
            this.proposedCond.signalAll();
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            throw new RuntimeException(e);
        } finally {
            this.lock.unlock();
        }
        System.out.println(this + " shut down");
    }

    public void watchProposal() {
        while (this.running.get() && config.selfId.equals(DepChainConfig.LEADER) && !proposed.get()) {
            try {
                this.lock.lock();
                while (val.get().isEmpty()) {
                    proposedCond.await();
                }

                if (!this.running.get()) {
                    break;
                }

                proposed.set(true);

                System.out.println(this + " proposed " + val.get());
                // ???????? Works dont remove for now
                Thread.sleep(1000);
                config.eventBus.publish(new RWProposeEvent(val.get()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } finally {
                this.lock.unlock();
            }
        }
    }

    @Override
    public String toString() {
        return "BLDC{id=" + config.selfId + "}";
    }

    public AtomicBoolean getAborted() {
        return aborted;
    }
}
